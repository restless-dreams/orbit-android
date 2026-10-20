package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The longest request line and headers the server reads, together. */
private const val HEADERS_LIMIT = 16 * 1024
/** A body bigger than this goes to a file instead of memory, when the server has a folder for them. */
private const val IN_MEMORY_BODY = 256 * 1024
/**
 * The stack each of the server's threads gets. They read requests, answer
 * and write; what a handler does that's heavy runs on the app's own
 * dispatchers, so a small stack is plenty.
 */
private const val THREAD_STACK_BYTES = 256L * 1024
/** Threads beyond one for each connection, for one connection's last moments overlapping the next one's first. */
private const val SPARE_THREADS = 8
/** Writes go out in pieces this big, so a reader that stopped reading is noticed between them. */
private const val WRITE_PIECE = 8 * 1024
/**
 * How long an answered connection reads what's left of its request before
 * it closes: closing with unread bytes resets the connection, and can take
 * the answer down with it before the other side reads it.
 */
private const val LINGER_MILLISECONDS = 500
/**
 * Events one stream may have waiting to be written. A reader further behind
 * is dropped: it reconnects, and catches up from the latest show, captions
 * and step.
 */
internal const val EVENT_BACKLOG = 64
private const val TAG = "LocalServer"

/**
 * What every answer carries, unless its handler says otherwise: no guessing
 * types, no referrer, no framing, no caching.
 */
private val SAFETY_HEADERS = listOf(
    "X-Content-Type-Options" to "nosniff",
    "Referrer-Policy" to "no-referrer",
    "X-Frame-Options" to "DENY",
    "Cache-Control" to "no-store",
)

/**
 * The content policy of every answer that isn't a page: a picture or video
 * opened on its own, an SVG with a script inside say, runs nothing, loads
 * nothing and stays apart from the phone's pages.
 */
internal const val FILE_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox"

/** How many connections the server holds at once. Past either, it answers 503 at once and hangs up. */
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

/** A request from a device on the local network. */
class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,

    /** Header names in lower case. */
    val headers: Map<String, String>,

    /** The body, when it's small enough to keep in memory; a bigger one is in [bodyFile]. */
    val body: ByteArray,

    /** The address the request came from. */
    val from: String,

    /**
     * A body too big for memory, in a file: deleted once the request is
     * answered, unless the handler moves it somewhere of its own.
     */
    val bodyFile: File? = null,
) {
    /** The body, wherever it is. */
    fun bodyStream(): InputStream = bodyFile?.inputStream() ?: body.inputStream()

    /** Every value the request's cookies give `name`. */
    fun cookies(name: String): List<String> =
        headers["cookie"].orEmpty().split(';').map { it.trim() }.filter { it.substringBefore('=') == name }.map { it.substringAfter('=') }
}

/** A request before its body is read: enough to refuse it without taking the body. */
class RequestHead(
    val method: String,
    val path: String,

    /** Header names in lower case. */
    val headers: Map<String, String>,

    /** The address the request came from. */
    val from: String,

    /** How long its body says it is. */
    val length: Long,
)

/** What the server does with a request before reading its body. */
sealed interface Admission {
    /** Reads the body and answers the request; `release` runs once it's done with, whether or not the body arrived whole. */
    class Read(val release: () -> Unit = {}) : Admission

    /** Answers at once, without reading any of the body. */
    class Refuse(val response: Response) : Admission
}

sealed interface Response {
    class Bytes(val status: Int, val type: String, val body: ByteArray, val headers: Map<String, String> = emptyMap()) : Response {
        fun withHeaders(more: Map<String, String>) = Bytes(status, type, body, headers + more)
    }

    /**
     * A file, read as it's written, never all at once. The server answers
     * a request for part of it, as a video asks, with that part (see [byteRange]).
     * A `temporary` file, made for this answer alone, goes whole, and is
     * deleted once it's written, or once writing it fails.
     */
    class FromFile(val type: String, val file: File, val headers: Map<String, String> = emptyMap(), val temporary: Boolean = false) : Response

    /** Server-sent events: the connection stays open and gets each event as it's sent, until either side closes. */
    class Events(val stream: EventStream) : Response

    companion object {
        fun text(status: Int, text: String, type: String = "text/plain; charset=utf-8") = Bytes(status, type, text.toByteArray())
        fun page(status: Int, html: String, headers: Map<String, String> = emptyMap()) = Bytes(status, "text/html; charset=utf-8", html.toByteArray(), headers)
        fun json(status: Int, json: String) = Bytes(status, "application/json", json.toByteArray())
        fun notFound() = text(404, "Not found")
        fun redirect(location: String) = Bytes(302, "text/plain; charset=utf-8", "Moved to $location".toByteArray(), mapOf("Location" to location))
    }
}

/**
 * Where a request for a page at a code goes first, if anywhere: the page at
 * `/1634` is the folder `/1634/`, whose script and files its links name
 * relative to it, so an address typed without the last slash gets it added.
 */
internal fun folderRedirect(path: String): String? {
    val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
    return if (parts.size == 1 && !path.endsWith("/")) "/${parts.single()}/" else null
}

/**
 * One browser's event stream: what's sent arrives in order, and closing it
 * ends the connection once what was sent before is written. `onClose` hears
 * once that it's over, whichever side ended it. A reader that falls more
 * than [EVENT_BACKLOG] events behind is dropped.
 */
class EventStream(private val onClose: () -> Unit = {}) {
    /** The connection writing a stream. */
    internal interface Pump {
        /** There's something to write. */
        fun wake()

        /** End the connection now, written or not. */
        fun hangUp()
    }

    private val lock = Any()
    private val waiting = ArrayDeque<String>()
    private var closing = false
    private var over = false
    private var pump: Pump? = null

    /** Whether it's closed, or closing once what was sent is written: nothing more goes out. */
    val isClosed: Boolean get() = synchronized(lock) { closing }

    fun send(data: String) {
        val behind: Boolean
        val attached: Pump?

        synchronized(lock) {
            if (closing) {
                return
            }

            behind = waiting.size >= EVENT_BACKLOG

            if (behind) {
                waiting.clear()
                closing = true
            } else {
                waiting.addLast(data)
            }

            attached = pump
        }

        when {
            behind && attached != null -> attached.hangUp()
            behind -> finish()
            else -> attached?.wake()
        }
    }

    fun close() {
        val attached = synchronized(lock) {
            if (closing) {
                return
            }

            closing = true
            pump
        }

        if (attached != null) {
            attached.wake()
        } else {
            finish()
        }
    }

    /** A connection starts writing it; false when it closed first. */
    internal fun attach(pump: Pump): Boolean = synchronized(lock) {
        if (closing) {
            false
        } else {
            this.pump = pump
            true
        }
    }

    /** Everything waiting to be written, or null once it's closing with nothing left, when its connection ends. */
    internal fun take(): List<String>? = synchronized(lock) {
        when {
            waiting.isNotEmpty() -> ArrayList(waiting).also { waiting.clear() }
            closing -> null
            else -> emptyList()
        }
    }

    internal val hasWork: Boolean get() = synchronized(lock) { waiting.isNotEmpty() || closing }

    /** Its connection ended, for whatever reason. */
    internal fun finish() {
        val first = synchronized(lock) {
            closing = true
            waiting.clear()
            val first = !over
            over = true
            first
        }

        if (first) {
            onClose()
        }
    }
}

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

    private class TooBigException : Exception()

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

        fun start() {
            clock.scheduleWithFixedDelay({ runCatching { tick() } }, timings.tick, timings.tick, TimeUnit.MILLISECONDS)

            sockets.forEach { socket ->
                threads("$name accepting").newThread { accept(socket) }.start()
            }
        }

        fun stop() {
            sockets.forEach { runCatching { it.close() } }
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
                val body = "Too many connections. Try again in a moment.".toByteArray()
                val output = socket.getOutputStream()
                output.write(head(503, "text/plain; charset=utf-8", body.size.toLong(), mapOf("Retry-After" to "5")))
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
                            Response.text(421, "Open this with the address the phone shows.")
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
                    request = readBody(input, parsed, address, length)

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

                        send(head(response.status, response.type, response.body.size.toLong(), policy + response.headers))
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
                    send(head(200, response.type, length, mapOf("Content-Security-Policy" to FILE_POLICY) + response.headers))
                    sendFile(response.file.inputStream())
                    return
                }

                val ranges = mapOf("Accept-Ranges" to "bytes", "Content-Security-Policy" to FILE_POLICY) + response.headers

                when (val range = byteRange(request?.headers?.get("range"), length, request?.headers?.get("if-range"))) {
                    ByteRange.Whole -> {
                        send(head(200, response.type, length, ranges))
                        sendFile(response.file.inputStream())
                    }
                    is ByteRange.Part -> {
                        send(head(206, response.type, range.count, ranges + ("Content-Range" to range.contentRange)))
                        sendFile(openRange(response.file, range.first, range.count))
                    }
                    is ByteRange.Unsatisfiable -> send(head(416, "text/plain; charset=utf-8", 0, ranges + ("Content-Range" to range.contentRange)))
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

    /** The parts of a request before its body. */
    private class Head(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

    /**
     * The request line and headers, read strictly: a request with control
     * characters in its target or headers, a target that isn't a path,
     * folded or nameless headers, or two Hosts or lengths, isn't one.
     */
    private fun parseHead(head: String): Head? {
        val lines = head.split("\r\n")
        val requestLine = lines.first()
        val parts = requestLine.split(' ')

        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) {
            return null
        }

        val (method, target) = parts

        if (method.isEmpty() || !method.all(::isTokenCharacter) || !target.startsWith("/") || !target.all { it in '!'..'~' }) {
            return null
        }

        val headers = LinkedHashMap<String, String>()

        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')

            if (colon <= 0 || line.any { it != '\t' && (it < ' ' || it == '\u007F') }) {
                return null
            }

            val name = line.substring(0, colon)

            if (!name.all(::isTokenCharacter)) {
                return null
            }

            val key = name.lowercase()
            val value = line.substring(colon + 1).trim(' ', '\t')
            val earlier = headers[key]

            headers[key] = when {
                earlier == null -> value
                // One request, one address and one length: two of either is a request to refuse.
                key == "host" || key == "content-length" -> return null
                key == "cookie" -> "$earlier; $value"
                else -> "$earlier, $value"
            }
        }

        if (headers.containsKey("transfer-encoding")) {
            return null
        }

        val path = decode(target.substringBefore('?'))

        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
        }

        if (path.any(Char::isISOControl) || query.any { (name, value) -> name.any(Char::isISOControl) || value.any(Char::isISOControl) }) {
            return null
        }

        return Head(method, path, query, headers)
    }

    private fun isTokenCharacter(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in "!#$%&'*+-.^_`|~"

    /** How long a request's body says it is: none without a length, and null when the length isn't a number. */
    private fun bodyLength(head: Head): Long? {
        val lengthText = head.headers["content-length"] ?: "0"

        if (lengthText.isEmpty() || lengthText.length > 18 || !lengthText.all { it in '0'..'9' }) {
            return null
        }

        return lengthText.toLong()
    }

    /** The body of a request within its limit. */
    private fun readBody(input: InputStream, head: Head, from: String, length: Long): Request? {
        if (length > IN_MEMORY_BODY && spool != null) {
            spool.mkdirs()
            val file = File.createTempFile("body", ".part", spool)

            val complete = try {
                file.outputStream().use { output -> copyExactly(input, output, length) }
            } catch (failure: IOException) {
                file.delete()
                throw failure
            }

            if (!complete) {
                file.delete()
                return null
            }

            return Request(head.method, head.path, head.query, head.headers, ByteArray(0), from, file)
        }

        if (length > IN_MEMORY_BODY) {
            throw TooBigException()
        }

        val body = ByteArray(length.toInt())
        var read = 0

        while (read < body.size) {
            val count = input.read(body, read, body.size - read)

            if (count < 0) {
                return null
            }

            read += count
        }

        return Request(head.method, head.path, head.query, head.headers, body, from)
    }

    /** Copies exactly `length` bytes; false when the input ends first. */
    private fun copyExactly(input: InputStream, output: OutputStream, length: Long): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var left = length

        while (left > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())

            if (count < 0) {
                return false
            }

            output.write(buffer, 0, count)
            left -= count
        }

        return true
    }

    /** The request line and headers, up to the blank line after them. */
    private fun readHead(input: InputStream): String? {
        val head = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())

        while (matched < end.size) {
            val byte = input.read()

            if (byte < 0 || head.size() >= HEADERS_LIMIT) {
                return null
            }

            head.write(byte)
            matched = if (byte.toByte() == end[matched]) matched + 1 else if (byte.toByte() == end[0]) 1 else 0
        }

        return head.toString(StandardCharsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
    }

    private fun eventsHead(): ByteArray = (
        "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nConnection: keep-alive\r\nX-Accel-Buffering: no\r\n" +
            SAFETY_HEADERS.joinToString("") { (name, value) -> "$name: $value\r\n" } + "\r\n"
        ).toByteArray()

    private fun eventText(event: String): String = event.lines().joinToString("\n", postfix = "\n\n") { "data: $it" }

    private fun head(status: Int, type: String, length: Long, headers: Map<String, String>): ByteArray {
        val reason = REASONS[status] ?: "OK"
        val all = LinkedHashMap<String, String>()
        SAFETY_HEADERS.forEach { (name, value) -> all[name] = value }

        // A header a handler made from something it was sent can't add lines of its own.
        headers.filter { (name, value) -> name.all(::isTokenCharacter) && value.none { it < ' ' || it == '\u007F' } }.forEach { (name, value) ->
            all.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(all::remove)
            all[name] = value
        }

        val lines = all.entries.joinToString("") { (name, value) -> "$name: $value\r\n" }
        return ("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: $length\r\nConnection: close\r\n$lines\r\n").toByteArray()
    }

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, StandardCharsets.UTF_8.name())
    } catch (malformed: IllegalArgumentException) {
        text
    }

    private companion object {
        val REASONS = mapOf(
            200 to "OK", 206 to "Partial Content", 302 to "Found", 303 to "See Other", 400 to "Bad Request", 403 to "Forbidden",
            404 to "Not Found", 405 to "Method Not Allowed", 409 to "Conflict", 410 to "Gone", 413 to "Payload Too Large",
            416 to "Range Not Satisfiable", 421 to "Misdirected Request", 429 to "Too Many Requests", 500 to "Internal Server Error",
            503 to "Service Unavailable",
        )

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

/**
 * The Content-Security-Policy for a page: scripts only from the phone or
 * inline ones it wrote, by their hashes, so nothing a map's text slips into
 * the page can run; pictures, videos and embeds from the phone, data and
 * https; and never framed, never rebased, forms only to the phone.
 */
internal fun pagePolicy(html: String): String {
    val scripts = inlineScripts(html).joinToString("") { " '${scriptHash(it)}'" }
    return listOf(
        "default-src 'none'",
        "script-src 'self'$scripts",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data: blob: https:",
        "media-src 'self' data: blob: https:",
        "frame-src https:",
        "font-src 'self' data:",
        "connect-src 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'",
        "base-uri 'none'",
    ).joinToString("; ")
}

/** The text of each script element in a page that runs without a src: its own code, or a module. Data blocks aren't scripts. */
internal fun inlineScripts(html: String): List<String> {
    val scripts = ArrayList<String>()
    var position = 0

    while (true) {
        val open = html.indexOf("<script", position, ignoreCase = true)

        if (open < 0) {
            break
        }

        val tagEnd = html.indexOf('>', open)

        if (tagEnd < 0) {
            break
        }

        val attributes = html.substring(open + "<script".length, tagEnd)

        if (attributes.isNotEmpty() && !attributes[0].isWhitespace()) {
            position = open + 1
            continue
        }

        val close = html.indexOf("</script", tagEnd + 1, ignoreCase = true)

        if (close < 0) {
            break
        }

        val type = SCRIPT_TYPE.find(attributes)?.groupValues?.get(1)?.lowercase()

        if (!SCRIPT_SOURCE.containsMatchIn(attributes) && (type == null || type in RUNNABLE_TYPES)) {
            scripts += html.substring(tagEnd + 1, close)
        }

        position = close + "</script".length
    }

    return scripts
}

private val SCRIPT_TYPE = Regex("""\btype\s*=\s*["']?([^"'\s>]*)""", RegexOption.IGNORE_CASE)
private val SCRIPT_SOURCE = Regex("""(^|\s)src\s*=""", RegexOption.IGNORE_CASE)
private val RUNNABLE_TYPES = setOf("", "module", "text/javascript", "application/javascript")

/** A script's CSP hash, of its text as a browser reads it: HTML turns every line break into a line feed first. */
internal fun scriptHash(script: String): String {
    val text = script.replace("\r\n", "\n").replace('\r', '\n')
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
    return "sha256-" + Base64.getEncoder().encodeToString(digest)
}
