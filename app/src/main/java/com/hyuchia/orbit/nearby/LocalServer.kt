package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The stack each of the server's threads gets. They read requests, answer
 * and write; what a handler does that's heavy runs on the app's own
 * dispatchers, so a small stack is plenty.
 */
private const val THREAD_STACK_BYTES = 256L * 1024
/** Threads beyond one for each connection, for one connection's last moments overlapping the next one's first. */
private const val SPARE_THREADS = 8

/** How long stopping waits, at most and for them all, for the threads taking connections to let go of their sockets. */
private const val ACCEPT_EXIT_MILLISECONDS = 500L
/** Writes go out in pieces this big, so a reader that stopped reading is noticed between them. */
private const val WRITE_PIECE = 8 * 1024
/**
 * How long an answered connection reads what's left of its request before
 * it closes: closing with unread bytes resets the connection, and can take
 * the answer down with it before the other side reads it.
 */
private const val LINGER_MILLISECONDS = 500
private const val TAG = "LocalServer"

/**
 * How many connections the server holds at once. Past either, it answers 503
 * at once and hangs up. A device with many addresses, as an IPv6 network can
 * give one, takes `perAddress` at each: the room's devices share the
 * network's prefix, so connections can't be counted by it, and one device on
 * the Wi-Fi can fill `connections` and keep the rest out while it does, as on
 * any network it's on.
 */
data class ServerLimits(val connections: Int = 128, val perAddress: Int = 8)

/** The server's clocks, in milliseconds. */
data class ServerTimings(
    /** How long a connection may take to send its request line and headers, all told. */
    val head: Long = 15_000,

    /** How long one read may wait for the next bytes of a request. */
    val read: Int = 15_000,

    /**
     * How long a connection may take to send a request's body, all told,
     * besides [bodyPerMegabyte] for each megabyte it says it holds: a body
     * dripping in a byte at a time can't hold a connection for long.
     */
    val body: Long = 15_000,

    /** How much longer a body may take for each megabyte it holds: it has to come at 33 KB a second on the whole. */
    val bodyPerMegabyte: Long = 30_000,

    /** How long a write may go without progress before its connection is dropped: a reader that stopped reading. */
    val stall: Long = 30_000,

    /** Event streams send a comment this often, so idle connections stay open and a reader that left is noticed. */
    val heartbeat: Long = 15_000,

    /** How often the server looks at its connections' clocks. */
    val tick: Long = 1_000,

    /** How long a thread with nothing to do waits for more before it ends. */
    val idleThread: Long = 30_000,

    /** How often the server looks whether the addresses it listens on changed, as the device joins and leaves networks. */
    val addresses: Long = 5_000,
)

/**
 * A small web server on the local network, for the devices in the room: a
 * browser following a presentation, or another device sending a map. Each
 * connection carries one request, except event streams, which stay open.
 *
 * It keeps to itself: connections run on its own few small threads, never
 * the app's, and an event stream holds no thread while it waits. It listens
 * only on the addresses it's started with, takes only connections its
 * `policy` admits (from the room, by default), holds at most `limits` of
 * them, and answers only requests that name the address they arrived at.
 * `bodyLimit` caps what a request may upload, by its method and path, and
 * `admit` can refuse a request before any of its body is read; a body
 * bigger than fits in memory goes to a file in `spool`, when there is one.
 */
class LocalServer(
    private val bodyLimit: (method: String, path: String) -> Long,
    private val spool: File? = null,
    private val policy: ConnectionPolicy = RoomNetworks(),
    private val limits: ServerLimits = ServerLimits(),
    private val timings: ServerTimings = ServerTimings(),

    /** What it says in plain text to a browser it can't serve, in the phone's language; in English without. */
    private val words: Words? = null,

    private val admit: (RequestHead) -> Admission = { Admission.Read() },
    private val handle: suspend (Request) -> Response,
) {
    @Volatile private var running: Running? = null
    private var listenOn: () -> List<InetAddress>? = { null }
    private var onChange: (Boolean) -> Unit = {}
    private var preferredPort = 0

    /** The port it last listened on: the address people were given keeps it while the network changes. */
    private var lastPort = 0

    private var follower: ScheduledThreadPoolExecutor? = null
    private val listeningNow = MutableStateFlow(false)

    /** The port it listens on, while it does. */
    val port: Int get() = running?.sockets?.firstOrNull()?.localPort ?: 0

    val isRunning: Boolean get() = running?.sockets?.any { !it.isClosed } == true

    /** Whether it listens now: started, and on a network it listens on. */
    val listening: StateFlow<Boolean> = listeningNow.asStateFlow()

    /**
     * Listens on `preferredPort`, or any free port if that one's taken, at
     * the addresses `listenOn` gives: every address this device has when it
     * gives null, and nowhere while it gives none. It follows them as the
     * device joins and leaves networks, starting again at the new addresses,
     * on the same port when it can; `onChange` hears whenever it starts or
     * stops listening that way.
     */
    @Synchronized
    fun start(preferredPort: Int, listenOn: () -> List<InetAddress>? = { null }, onChange: (Boolean) -> Unit = {}) {
        if (follower != null) {
            return
        }

        this.listenOn = listenOn
        this.onChange = onChange
        this.preferredPort = preferredPort
        open(listenOn(), preferredPort)

        follower = ScheduledThreadPoolExecutor(1, threads("Orbit server addresses")).also {
            it.scheduleWithFixedDelay({ runCatching { follow() } }, timings.addresses, timings.addresses, TimeUnit.MILLISECONDS)
        }
    }

    /** Starts again at the addresses `listenOn` gives now, when they changed. */
    @Synchronized
    private fun follow() {
        if (follower == null) {
            return
        }

        val addresses = listenOn()
        val current = running

        if (current != null && sameAddresses(current.addresses, addresses)) {
            return
        }

        if (current == null && addresses != null && addresses.isEmpty()) {
            return
        }

        // The address people were given keeps its port: only the address itself changed.
        val port = lastPort.takeIf { it > 0 } ?: preferredPort
        current?.stop()
        running = null
        open(addresses, port)
        onChange(running != null)
    }

    /** Listens at `addresses` on `port`, or any free port; nowhere when there are none. */
    private fun open(addresses: List<InetAddress>?, port: Int) {
        val sockets = if (addresses != null && addresses.isEmpty()) null else bind(addresses, port) ?: bind(addresses, 0)
        running = sockets?.let { Running(it, addresses).also { started -> started.start() } }
        sockets?.firstOrNull()?.localPort?.let { lastPort = it }
        listeningNow.value = running != null
    }

    private fun sameAddresses(first: List<InetAddress>?, second: List<InetAddress>?): Boolean =
        first?.map { it.hostAddress }?.toSet() == second?.map { it.hostAddress }?.toSet()

    /**
     * A socket at each address, all on `port` (any free one for 0, the same
     * for every address), or null when one of them can't have it.
     */
    private fun bind(addresses: List<InetAddress>?, port: Int): List<ServerSocket>? {
        val sockets = ArrayList<ServerSocket>()

        try {
            for (address in addresses ?: listOf(null)) {
                val chosen = sockets.firstOrNull()?.localPort ?: port

                sockets += ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(address, chosen))
                }
            }
        } catch (taken: IOException) {
            sockets.forEach { runCatching { it.close() } }
            return null
        }

        return sockets
    }

    @Synchronized
    fun stop() {
        follower?.shutdownNow()
        follower = null
        running?.stop()
        running = null
        lastPort = 0
        listeningNow.value = false
    }

    /** Where a connection is: its clocks run differently in each. */
    private enum class Phase { Head, Body, Answering, Streaming }

    /** One start of the server: its sockets, threads and connections, all ended together. */
    private inner class Running(val sockets: List<ServerSocket>, val addresses: List<InetAddress>?) {
        private val name = "Orbit server ${sockets.first().localPort}"
        private val workers = ThreadPoolExecutor(
            0,
            limits.connections + SPARE_THREADS,
            timings.idleThread,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            threads(name),
        )
        private val clock = ScheduledThreadPoolExecutor(1, threads("$name clock"))
        private val connections: MutableSet<Connection> = ConcurrentHashMap.newKeySet()
        private val perAddress = HashMap<String, Int>()
        private var open = 0

        /** The threads waiting for connections, one for each socket. */
        private val accepting = ArrayList<Thread>()

        fun start() {
            clock.scheduleWithFixedDelay({ runCatching { tick() } }, timings.tick, timings.tick, TimeUnit.MILLISECONDS)

            sockets.forEach { socket ->
                accepting += threads("$name accepting").newThread { accept(socket) }.also { it.start() }
            }
        }

        /**
         * Stops taking connections, and ends the ones it has. A socket closed
         * while a thread waits on it is let go of only once that thread is out,
         * and until then its port still takes connections: so stopping waits
         * for them, and nothing listens once it's done.
         */
        fun stop() {
            sockets.forEach { runCatching { it.close() } }
            // One deadline for them all: stopping can run on the main thread, which mustn't wait long.
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ACCEPT_EXIT_MILLISECONDS)

            for (thread in accepting.filter { it != Thread.currentThread() }) {
                val left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())

                if (left <= 0) {
                    break
                }

                runCatching { thread.join(left) }
            }

            clock.shutdownNow()
            connections.forEach { it.close() }
            workers.shutdown()
        }

        private fun accept(socket: ServerSocket) {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (closed: IOException) {
                    break
                }

                // Nothing one connection brings may stop the server taking the next, nor the app.
                try {
                    admit(connection)
                } catch (failure: Exception) {
                    Logs.warn(TAG, "Couldn't take a connection", failure)
                    runCatching { connection.close() }
                }
            }
        }

        private fun admit(socket: Socket) {
            val remote = socket.inetAddress

            // Only devices in the room, on its Wi-Fi or this phone's hotspot: never the internet, mobile data or other apps here.
            if (remote == null || !policy.admits(socket.localAddress, remote)) {
                runCatching { socket.close() }
                return
            }

            val address = unmapped(remote).hostAddress.orEmpty()

            if (!reserve(address)) {
                refuse(socket)
                return
            }

            val connection = Connection(socket, address)
            connections += connection

            try {
                workers.execute { connection.serve() }
            } catch (full: RejectedExecutionException) {
                connection.close()
            }
        }

        private fun reserve(address: String): Boolean = synchronized(perAddress) {
            val fromThere = perAddress[address] ?: 0

            if (open >= limits.connections || fromThere >= limits.perAddress) {
                false
            } else {
                open += 1
                perAddress[address] = fromThere + 1
                true
            }
        }

        private fun release(address: String) {
            synchronized(perAddress) {
                open -= 1
                val left = (perAddress[address] ?: 1) - 1

                if (left <= 0) {
                    perAddress.remove(address)
                } else {
                    perAddress[address] = left
                }
            }
        }

        /** Too many connections: a short answer on a fresh connection, which can't keep the accepting thread waiting. */
        private fun refuse(socket: Socket) {
            runCatching {
                val body = (words?.string(R.string.nearby_server_busy) ?: "Too many connections. Try again in a moment.").toByteArray()
                val output = socket.getOutputStream()
                output.write(answerHead(503, "text/plain; charset=utf-8", body.size.toLong(), mapOf("Retry-After" to "5")))
                output.write(body)
                output.flush()
                socket.shutdownOutput()
            }

            runCatching { socket.close() }
        }

        private fun tick() {
            val now = monotonic()

            connections.forEach { connection ->
                when {
                    connection.phase == Phase.Head && now - connection.since > timings.head -> connection.close()
                    connection.phase == Phase.Body && now - connection.since > connection.bodyTime -> connection.close()
                    connection.writing && now - connection.progress > timings.stall -> connection.close()
                    connection.phase == Phase.Streaming && now - connection.lastWrite >= timings.heartbeat -> connection.heartbeat()
                }
            }
        }

        private inner class Connection(val socket: Socket, val address: String) : EventStream.Pump {
            @Volatile var phase = Phase.Head
            @Volatile var since = monotonic()
            @Volatile var writing = false
            @Volatile var progress = 0L
            @Volatile var lastWrite = monotonic()

            /** How long the request's body may take to arrive, by its length. */
            @Volatile var bodyTime = 0L

            @Volatile private var stream: EventStream? = null
            private val closed = AtomicBoolean(false)
            private val draining = AtomicBoolean(false)
            private val heartbeatDue = AtomicBoolean(false)

            fun serve() {
                var request: Request? = null
                var answer: Response? = null
                var streaming = false
                var release: () -> Unit = {}

                try {
                    socket.soTimeout = timings.read
                    val input = BufferedInputStream(socket.getInputStream())
                    val head = readHead(input)
                    val parsed = head?.let(::parseHead)

                    if (parsed == null) {
                        write(Response.text(400, "Bad request"))
                        return
                    }

                    val problem = when {
                        parsed.headers["host"] == null -> Response.text(400, "Bad request")
                        !hostMatches(parsed.headers["host"], socket.localAddress, socket.localPort) ->
                            Response.text(421, words?.string(R.string.nearby_server_wrong_address) ?: "Open this with the address the phone shows.")
                        else -> null
                    }

                    if (problem != null) {
                        write(problem)
                        return
                    }

                    val length = bodyLength(parsed)

                    if (length == null) {
                        write(Response.text(400, "Bad request"))
                        return
                    }

                    when (val admission = admit(RequestHead(parsed.method, parsed.path, parsed.headers, address, length))) {
                        is Admission.Refuse -> {
                            write(admission.response)
                            return
                        }
                        is Admission.Read -> release = admission.release
                    }

                    if (length > bodyLimit(parsed.method, parsed.path)) {
                        throw TooBigException()
                    }

                    bodyTime = timings.body + length * timings.bodyPerMegabyte / 1_000_000
                    moveTo(Phase.Body)
                    request = readBody(input, parsed, address, length, spool)

                    if (request == null) {
                        write(Response.text(400, "Bad request"))
                        return
                    }

                    val asked = request
                    moveTo(Phase.Answering)

                    val response = try {
                        runBlocking { handle(asked) }
                    } catch (failure: Exception) {
                        Logs.warn(TAG, "Couldn't answer a request", failure)
                        Response.text(500, "Something went wrong")
                    }

                    answer = response

                    if (response is Response.Events) {
                        streaming = stream(response.stream)
                    } else {
                        write(response, asked)
                    }
                } catch (gone: IOException) {
                    // The other side went away, or took too long: nothing to answer.
                } catch (tooBig: TooBigException) {
                    runCatching { write(Response.text(413, "Too big")) }
                } catch (failure: Exception) {
                    // A thread of the server's that ended this way would take the app with it.
                    Logs.warn(TAG, "Couldn't serve a connection", failure)
                } finally {
                    runCatching { release() }
                    // A body the handler didn't take for itself, and a file made for this answer alone.
                    request?.bodyFile?.delete()
                    (answer as? Response.FromFile)?.takeIf { it.temporary }?.file?.delete()

                    if (!streaming) {
                        finish()
                    }
                }
            }

            /** Ends an answered connection once the other side has its answer and what's left of its request is read. */
            private fun finish() {
                runCatching {
                    socket.shutdownOutput()
                    socket.soTimeout = LINGER_MILLISECONDS
                    val input = socket.getInputStream()
                    val buffer = ByteArray(1024)
                    val until = monotonic() + LINGER_MILLISECONDS
                    var count = 0

                    while (count >= 0 && monotonic() < until) {
                        count = input.read(buffer)
                    }
                }

                close()
            }

            private fun moveTo(next: Phase) {
                phase = next
                since = monotonic()
            }

            /** Starts streaming; false when the stream ended before it began. */
            private fun stream(events: EventStream): Boolean {
                // Kept first, so a connection that fails from here on ends the stream too.
                stream = events

                // Closed before it was kept, as the server stopped: the stream ends with it.
                if (closed.get()) {
                    events.finish()
                    return false
                }

                send(eventsHead())
                moveTo(Phase.Streaming)

                if (!events.attach(this)) {
                    return false
                }

                wake()
                return true
            }

            override fun wake() {
                if (closed.get() || !draining.compareAndSet(false, true)) {
                    return
                }

                try {
                    workers.execute { drain() }
                } catch (full: RejectedExecutionException) {
                    close()
                }
            }

            override fun hangUp() = close()

            fun heartbeat() {
                heartbeatDue.set(true)
                wake()
            }

            /** Writes what the stream has waiting, one writer at a time, and lets go once there's nothing left. */
            private fun drain() {
                val events = stream ?: return

                try {
                    while (true) {
                        val waiting = events.take()

                        when {
                            waiting == null -> {
                                close()
                                return
                            }
                            waiting.isNotEmpty() -> send(waiting.joinToString("") { eventText(it) }.toByteArray())
                            heartbeatDue.getAndSet(false) -> send(": still here\n\n".toByteArray())
                            else -> {
                                draining.set(false)

                                // Something may have come in between the last look and letting go.
                                if (!(events.hasWork || heartbeatDue.get()) || !draining.compareAndSet(false, true)) {
                                    return
                                }
                            }
                        }
                    }
                } catch (gone: IOException) {
                    // A write that fails is how a browser that left is noticed.
                    close()
                } catch (failure: Exception) {
                    Logs.warn(TAG, "Couldn't write to a stream", failure)
                    close()
                }
            }

            fun write(response: Response, request: Request? = null) {
                when (response) {
                    is Response.Bytes -> {
                        val policy = if (response.type.startsWith("text/html")) {
                            mapOf("Content-Security-Policy" to pagePolicy(response.body.decodeToString()))
                        } else {
                            mapOf("Content-Security-Policy" to FILE_POLICY)
                        }

                        send(answerHead(response.status, response.type, response.body.size.toLong(), policy + response.headers))
                        send(response.body)
                    }
                    is Response.FromFile -> writeFile(response, request)
                    is Response.Events -> Unit
                }
            }

            /** A file, or the part of it the request asks for, as it reads: a big video never sits in memory. */
            private fun writeFile(response: Response.FromFile, request: Request?) {
                val length = response.file.length()

                if (response.temporary) {
                    // The next request makes the file again, so no part of this one is offered on its own.
                    send(answerHead(200, response.type, length, mapOf("Content-Security-Policy" to FILE_POLICY) + response.headers))
                    sendFile(response.file.inputStream())
                    return
                }

                val ranges = mapOf("Accept-Ranges" to "bytes", "Content-Security-Policy" to FILE_POLICY) + response.headers

                when (val range = byteRange(request?.headers?.get("range"), length, request?.headers?.get("if-range"))) {
                    ByteRange.Whole -> {
                        send(answerHead(200, response.type, length, ranges))
                        sendFile(response.file.inputStream())
                    }
                    is ByteRange.Part -> {
                        send(answerHead(206, response.type, range.count, ranges + ("Content-Range" to range.contentRange)))
                        sendFile(openRange(response.file, range.first, range.count))
                    }
                    is ByteRange.Unsatisfiable -> send(answerHead(416, "text/plain; charset=utf-8", 0, ranges + ("Content-Range" to range.contentRange)))
                }
            }

            private fun sendFile(input: InputStream) {
                input.use { file ->
                    val buffer = ByteArray(WRITE_PIECE)

                    while (true) {
                        val count = file.read(buffer)

                        if (count < 0) {
                            break
                        }

                        send(buffer, count)
                    }
                }
            }

            /** Writes in pieces, marking progress after each, so a reader that stopped reading is noticed. */
            private fun send(bytes: ByteArray, length: Int = bytes.size) {
                val output: OutputStream = socket.getOutputStream()
                writing = true

                try {
                    var offset = 0

                    while (offset < length) {
                        progress = monotonic()
                        val count = minOf(WRITE_PIECE, length - offset)
                        output.write(bytes, offset, count)
                        offset += count
                    }

                    output.flush()
                } finally {
                    writing = false
                    lastWrite = monotonic()
                }
            }

            fun close() {
                if (!closed.compareAndSet(false, true)) {
                    return
                }

                runCatching { socket.close() }
                connections -= this
                release(address)
                stream?.finish()
            }
        }
    }

    private companion object {
        fun monotonic(): Long = System.nanoTime() / 1_000_000

        /** Small daemon threads, named for the server, so a stack trace says whose they are. */
        fun threads(name: String): ThreadFactory {
            val count = AtomicInteger()

            return ThreadFactory { work ->
                Thread(null, work, "$name ${count.incrementAndGet()}", THREAD_STACK_BYTES).apply { isDaemon = true }
            }
        }
    }
}
