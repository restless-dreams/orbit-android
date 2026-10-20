package com.hyuchia.orbit.nearby

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LocalServerTest {
    private val servers = mutableListOf<LocalServer>()
    private val sockets = mutableListOf<Socket>()
    private val stream = EventStream()
    private val spool: File = Files.createTempDirectory("orbit-spool").toFile()
    private val kept: File = Files.createTempDirectory("orbit-kept").toFile()

    private fun server(
        bodyLimit: Long = 1_000,
        spool: File? = null,
        policy: ConnectionPolicy = anyone,
        limits: ServerLimits = ServerLimits(),
        timings: ServerTimings = ServerTimings(),
        handle: suspend (Request) -> Response = ::answer,
    ): LocalServer = LocalServer({ _, _ -> bodyLimit }, spool, policy, limits, timings, handle = handle).also {
        servers += it
        it.start(0)
    }

    private fun answer(request: Request): Response = when (request.path) {
        "/hello" -> Response.text(200, "hello ${request.query["name"]}")
        "/upload" -> Response.text(200, "${request.method} ${request.body.size} bytes from ${request.headers["x-from"]}")
        "/events" -> Response.Events(stream)
        "/1634" -> Response.redirect("/1634/")
        "/page" -> Response.page(200, PAGE)
        else -> Response.notFound()
    }

    private fun spooling(): LocalServer = server(bodyLimit = 4L * 1024 * 1024, spool = spool) { request ->
        val file = request.bodyFile

        when {
            file == null -> Response.text(200, "${request.body.size} bytes in memory")
            request.path == "/keep" -> {
                file.renameTo(File(kept, "offer.zip"))
                Response.text(200, "kept")
            }
            else -> Response.text(200, "${file.length()} bytes in a file, ${request.bodyStream().use { it.readBytes() }.size} read")
        }
    }

    @After
    fun stop() {
        sockets.forEach { runCatching { it.close() } }
        servers.forEach { it.stop() }
        spool.deleteRecursively()
        kept.deleteRecursively()
    }

    private fun open(server: LocalServer, path: String): HttpURLConnection =
        URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection

    private fun upload(server: LocalServer, path: String, size: Int): HttpURLConnection {
        val connection = open(server, path)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(size)
        connection.outputStream.use { it.write(ByteArray(size)) }
        return connection
    }

    /** Opens an event stream from the loopback, and waits for its first line. */
    private fun follow(server: LocalServer, from: String? = null): Socket = connect(server.port, from).also { socket ->
        sockets += socket
        socket.getOutputStream().write("GET /events HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\n\r\n".toByteArray())
        assertEquals("HTTP/1.1 200 OK", socket.getInputStream().bufferedReader().readLine())
    }

    @Test
    fun sendsAPageTypedWithoutItsLastSlashToItsFolder() {
        assertEquals("/1634/", folderRedirect("/1634"))
        assertEquals(null, folderRedirect("/1634/"))
        assertEquals(null, folderRedirect("/1634/browser.js"))
        assertEquals(null, folderRedirect("/"))

        val connection = open(server(), "/1634").apply { instanceFollowRedirects = false }
        assertEquals(302, connection.responseCode)
        assertEquals("/1634/", connection.getHeaderField("Location"))
    }

    @Test
    fun answersRequestsWithTheirQuery() {
        val server = server()
        val connection = open(server, "/hello?name=Orbit%20map")
        assertEquals(200, connection.responseCode)
        assertEquals("hello Orbit map", connection.inputStream.bufferedReader().readText())
        assertEquals(404, open(server, "/elsewhere").responseCode)
    }

    @Test
    fun readsUploadsUpToTheLimit() {
        val server = server()

        val upload = open(server, "/upload").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("X-From", "tablet")
        }

        upload.outputStream.use { it.write(ByteArray(600)) }
        assertEquals("POST 600 bytes from tablet", upload.inputStream.bufferedReader().readText())

        val tooBig = open(server, "/upload").apply {
            requestMethod = "POST"
            doOutput = true
        }

        tooBig.outputStream.use { it.write(ByteArray(2_000)) }
        assertEquals(413, tooBig.responseCode)
    }

    @Test
    fun theLimitOnUploadsCanDependOnWhereTheyGo() {
        val server = LocalServer({ _, path -> if (path == "/upload") 1_000 else 0 }, policy = anyone, handle = ::answer).also {
            servers += it
            it.start(0)
        }

        assertEquals(200, request(server.port, "/upload", "POST", body = "x".repeat(600)).status)
        assertEquals(413, request(server.port, "/hello", "POST", body = "x").status)
    }

    @Test
    fun takesBigUploadsInFilesThatGoOnceAnswered() {
        val spooling = spooling()
        assertEquals("1000 bytes in memory", upload(spooling, "/upload", 1_000).inputStream.bufferedReader().readText())
        assertEquals("3000000 bytes in a file, 3000000 read", upload(spooling, "/upload", 3_000_000).inputStream.bufferedReader().readText())
        // The server lets the file go once it has written the answer, which can be a moment after the answer is read here.
        assertTrue("${spool.list()!!.toList()} left", eventually { spool.list()!!.isEmpty() })
        // Too big is refused from its length alone, before anything's read.
        val answer = exchange(spooling.port, "POST /upload HTTP/1.1\r\nHost: 127.0.0.1:${spooling.port}\r\nContent-Length: 5000000\r\n\r\n")
        assertEquals(413, answer.status)
        assertEquals(emptyList<String>(), spool.list()!!.toList())
    }

    @Test
    fun aHandlerCanKeepAnUploadsFile() {
        assertEquals("kept", upload(spooling(), "/keep", 1_000_000).inputStream.bufferedReader().readText())
        assertEquals(1_000_000L, File(kept, "offer.zip").length())
    }

    @Test
    fun streamsEventsAsTheyAreSent() {
        stream.send("{\"type\":\"step\"}")
        stream.send("two\nlines")
        val connection = open(server(), "/events")
        assertEquals("text/event-stream; charset=utf-8", connection.contentType)
        val lines = connection.inputStream.bufferedReader()
        assertEquals("data: {\"type\":\"step\"}", lines.readLine())
        assertEquals("", lines.readLine())
        assertEquals("data: two", lines.readLine())
        assertEquals("data: lines", lines.readLine())
        assertEquals("", lines.readLine())
        stream.send("last")
        stream.close()
        assertTrue(stream.isClosed)
        // Closing writes what was sent before it, then ends the connection.
        assertEquals("data: last", lines.readLine())
        assertEquals("", lines.readLine())
        assertNull(lines.readLine())
    }

    @Test
    fun eventStreamsNeverHoldTheAppsThreadsNorOneOfTheirOwn() {
        val count = 150
        val streams = List(count) { EventStream() }
        val next = AtomicInteger()
        val server = server(
            limits = ServerLimits(connections = 200, perAddress = 200),
            timings = ServerTimings(idleThread = 200),
        ) { Response.Events(streams[next.getAndIncrement()]) }
        repeat(count) { follow(server) }

        // The rest of the app's work on Dispatchers.IO, such as saving a map, starts at once.
        @OptIn(DelicateCoroutinesApi::class)
        val waited = runBlocking {
            val started = CompletableDeferred<Long>()
            val asked = System.nanoTime()
            GlobalScope.launch(Dispatchers.IO) { started.complete(System.nanoTime()) }
            (started.await() - asked) / 1_000_000
        }

        assertTrue("Work on Dispatchers.IO waited $waited ms", waited < 200)

        // Once the requests are answered, no thread waits on an idle stream: only the server's clock and its door stay.
        val threads = { Thread.getAllStackTraces().keys.count { it.isAlive && it.name.startsWith("Orbit server ${server.port}") } }
        assertTrue("${threads()} threads stay", eventually { threads() <= 2 })
        assertEquals(count, next.get())
        streams.forEach { assertFalse(it.isClosed) }
    }

    @Test
    fun requestsWithControlCharactersAreRefused() {
        val port = server().port
        val host = "Host: 127.0.0.1:$port\r\n"
        val requests = listOf(
            "GET /hello\u0001 HTTP/1.1\r\n$host\r\n",
            "GET /hello HTTP/1.1\r\n${host}X-Note: one\u0000two\r\n\r\n",
            "GET /hello HTTP/1.1\r\n${host}X-Note: one\ntwo\r\n\r\n",
            "GET /hello HTTP/1.1\r\n${host}X-Note: one\rtwo\r\n\r\n",
            "GET /hello%0d%0aSet-Cookie:%20a=b HTTP/1.1\r\n$host\r\n",
            "GET /hello?name=%00 HTTP/1.1\r\n$host\r\n",
            "GET /hello\u007F HTTP/1.1\r\n$host\r\n",
            "GET /héllo HTTP/1.1\r\n$host\r\n",
            // Not a path, a folded header, a nameless one, and a body of unknown length.
            "GET http://127.0.0.1:$port/hello HTTP/1.1\r\n$host\r\n",
            "GET /hello HTTP/1.1\r\n${host}X-Note: one\r\n two\r\n\r\n",
            "GET /hello HTTP/1.1\r\n$host: nameless\r\n\r\n",
            "POST /upload HTTP/1.1\r\n${host}Transfer-Encoding: chunked\r\n\r\n0\r\n\r\n",
            "GET /hello HTTP/1.1 extra\r\n$host\r\n",
        )
        requests.forEach { text -> assertEquals(text, 400, exchange(port, text).status) }
        assertEquals(200, exchange(port, "GET /hello?name=you HTTP/1.1\r\n${host}X-Note: tabs\tare fine\r\n\r\n").status)
    }

    @Test
    fun onlyTheAddressARequestArrivedAtIsItsHost() {
        val port = server().port
        assertEquals(200, request(port, "/hello").status)
        listOf("attacker.example", "attacker.example:$port", "127.0.0.1", "127.0.0.1:1", "127.0.0.2:$port", "localhost:$port", "[::1]:$port", "127.0.0.1.nip.io:$port")
            .forEach { host -> assertEquals(host, 421, request(port, "/hello", host = host).status) }
        assertEquals(400, request(port, "/hello", host = null).status)
        assertEquals(400, exchange(port, "GET /hello HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nHost: attacker.example\r\n\r\n").status)

        // IPv6 is written in brackets.
        val loopback6 = runCatching { Socket().use { it.connect(InetSocketAddress("::1", port), 1_000) } }.isSuccess

        if (loopback6) {
            val answer = Socket("::1", port).use { socket ->
                socket.getOutputStream().write("GET /hello HTTP/1.1\r\nHost: [::1]:$port\r\n\r\n".toByteArray())
                parse(socket.getInputStream().readBytes())
            }

            assertEquals(200, answer.status)
        }
    }

    @Test
    fun hostsAreIpLiteralsNeverNames() {
        val wifi = InetAddress.getByName("192.168.1.5")
        assertTrue(hostMatches("192.168.1.5:8765", wifi, 8765))
        assertFalse(hostMatches("192.168.1.5:8766", wifi, 8765))
        assertFalse(hostMatches("192.168.1.5", wifi, 8765))
        assertFalse(hostMatches("192.168.001.005:8765", wifi, 8765))
        assertFalse(hostMatches("0xc0.168.1.5:8765", wifi, 8765))
        assertFalse(hostMatches("3232235781:8765", wifi, 8765))
        assertFalse(hostMatches("phone.local:8765", wifi, 8765))
        assertFalse(hostMatches("192.168.1.5.nip.io:8765", wifi, 8765))
        assertFalse(hostMatches("192.168.1.5:+8765", wifi, 8765))
        assertFalse(hostMatches(null, wifi, 8765))

        val linkLocal = InetAddress.getByName("fe80::1c2:3ff:fe04:506")
        assertTrue(hostMatches("[fe80::1c2:3ff:fe04:506]:8766", linkLocal, 8766))
        assertTrue(hostMatches("[FE80:0:0:0:1C2:3FF:FE04:506%wlan0]:8766", linkLocal, 8766))
        // As a Java client writes it, as another device sending a map does.
        assertTrue(hostMatches("fe80:0:0:0:1c2:3ff:fe04:506%wlan0:8766", linkLocal, 8766))
        assertFalse(hostMatches("[fe80::1]:8766", linkLocal, 8766))
        assertFalse(hostMatches("[fe80::1c2:3ff:fe04:506]", linkLocal, 8766))
        // An IPv4 connection to a dual-stack socket can show up as IPv6.
        val mapped = Inet6Address.getByAddress(null, ByteArray(10) + byteArrayOf(-1, -1, -64, -88, 1, 5), null)
        assertTrue(hostMatches("192.168.1.5:8765", mapped, 8765))

        assertEquals(InetAddress.getByName("::ffff:10.0.0.1"), ipLiteral("::ffff:10.0.0.1"))
        assertEquals(InetAddress.getByName("2001:db8::8:800:200c:417a"), ipLiteral("[2001:db8::8:800:200c:417a]"))
        assertEquals(InetAddress.getByName("::"), ipLiteral("::"))

        listOf("", ":", ":::", "1::2::3", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "12345::", "g::1", "example.com", "::1.2.3").forEach {
            assertNull(it, ipLiteral(it))
        }
    }

    @Test
    fun onlyTheRoomsAddressesAreInTheRoom() {
        val room = listOf("192.168.1.5", "10.0.0.2", "172.16.0.1", "169.254.1.1", "fe80::1", "fd12:3456::1", "::ffff:192.168.1.5")
        val elsewhere = listOf("127.0.0.1", "::1", "0.0.0.0", "8.8.8.8", "100.64.0.1", "203.0.113.9", "2001:4860:4860::8888", "::ffff:8.8.8.8", "224.0.0.251")
        room.forEach { assertTrue(it, isRoomAddress(InetAddress.getByName(it))) }
        elsewhere.forEach { assertFalse(it, isRoomAddress(InetAddress.getByName(it))) }
        assertFalse(isRoomAddress(null))
    }

    @Test
    fun theRoomIsWiFiAHotspotACableOrUsbNeverMobileDataOrThisDevice() {
        val wifi = InetAddress.getByName("192.168.1.5")
        val linkLocal = InetAddress.getByName("fe80::1")
        val laptop = InetAddress.getByName("192.168.1.20")
        // This device's own addresses are on the interface named; other devices' are on none of its.
        fun on(name: String?) = RoomNetworks { address -> if (address == wifi || address == linkLocal) name else null }

        listOf("wlan0", "wlan1", "swlan0", "softap0", "ap0", "eth0", "rndis0", "ncm0", "usb0").forEach {
            assertTrue(it, on(it).admits(wifi, laptop))
        }

        listOf("rmnet_data0", "rmnet0", "ccmni1", "v4-rmnet_data0", "seth_lte0", "tun0", "p2p0", "lo", "dummy0", null).forEach {
            assertFalse("$it", on(it).admits(wifi, laptop))
        }

        // Neither end may be this device's loopback, which any app on the phone can reach.
        assertFalse(on("wlan0").admits(InetAddress.getByName("127.0.0.1"), laptop))
        assertFalse(on("wlan0").admits(wifi, InetAddress.getByName("127.0.0.1")))
        // Nor from outside the room, even on Wi-Fi.
        assertFalse(on("wlan0").admits(wifi, InetAddress.getByName("8.8.8.8")))
        assertFalse(on("wlan0").admits(wifi, InetAddress.getByName("100.64.0.7")))
        assertTrue(on("wlan0").admits(linkLocal, InetAddress.getByName("fe80::2")))
        // Another app on the phone itself, coming in through the Wi-Fi's own address, isn't in the room.
        assertFalse(on("wlan0").admits(wifi, wifi))
        assertFalse(on("wlan0").admits(wifi, linkLocal))
        assertFalse(on("wlan0").admits(wifi, InetAddress.getByName("::ffff:192.168.1.5")))
    }

    @Test
    fun itListensOnlyAtTheAddressesItsGiven() {
        assumeTrue("Needs other loopback addresses to connect to", canBindFrom("127.0.0.2"))
        val server = LocalServer({ _, _ -> 0 }, policy = anyone, handle = ::answer).also { servers += it }
        server.start(0, listenOn = { listOf(InetAddress.getByName("127.0.0.1")) })
        assertTrue(server.listening.value)
        assertEquals(200, request(server.port, "/hello?name=here").status)
        // The same port at another of this machine's addresses has no one listening.
        assertTrue(runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.2", server.port), 2_000) } }.isFailure)
    }

    @Test
    fun itFollowsItsAddressesAsTheDeviceJoinsAndLeavesNetworks() {
        assumeTrue("Needs other loopback addresses to connect to", canBindFrom("127.0.0.2"))
        val addresses = AtomicReference(listOf(InetAddress.getByName("127.0.0.1")))
        val changes = mutableListOf<Boolean>()
        val server = LocalServer({ _, _ -> 0 }, policy = anyone, timings = ServerTimings(addresses = 20), handle = ::answer).also { servers += it }
        server.start(0, listenOn = { addresses.get() }) { listening -> synchronized(changes) { changes += listening } }
        val port = server.port

        // No network to listen on: nothing listens.
        addresses.set(emptyList())
        assertTrue(eventually { !server.listening.value })
        assertTrue(runCatching { request(port, "/hello") }.isFailure)

        // Another network: it listens there, on the port people were given.
        addresses.set(listOf(InetAddress.getByName("127.0.0.2")))
        assertTrue(eventually { server.listening.value })
        assertEquals(port, server.port)

        val answer = Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.2", port), 2_000)
            socket.getOutputStream().write("GET /hello?name=there HTTP/1.1\r\nHost: 127.0.0.2:$port\r\n\r\n".toByteArray())
            parse(socket.getInputStream().readBytes())
        }

        assertEquals("hello there", answer.body)
        assertEquals(listOf(false, true), synchronized(changes) { changes.toList() })
        server.stop()
        assertFalse(server.listening.value)
    }

    @Test
    fun aRequestCanBeRefusedBeforeAnyOfItsBodyIsRead() {
        val released = CountDownLatch(2)

        val server = LocalServer(
            { _, _ -> 10_000_000 },
            policy = anyone,
            admit = { head ->
                if (head.path == "/refused") {
                    Admission.Refuse(Response.text(403, "Not from ${head.from}, not ${head.length} bytes"))
                } else {
                    Admission.Read { released.countDown() }
                }
            },
            handle = ::answer,
        ).also {
            servers += it
            it.start(0)
        }

        // Only the head is sent: the answer comes without waiting for a body that never does.
        val refused = connect(server.port).use { socket ->
            socket.getOutputStream().write("POST /refused HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Length: 9000000\r\n\r\n".toByteArray())
            parse(socket.getInputStream().readBytes())
        }

        assertEquals(403, refused.status)
        assertEquals("Not from 127.0.0.1, not 9000000 bytes", refused.body)

        // An admitted request lets go of what it held, answered or not.
        assertEquals(200, request(server.port, "/upload", method = "POST", body = "abc").status)

        connect(server.port).use { socket ->
            socket.getOutputStream().write("POST /upload HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Length: 100\r\n\r\nshort".toByteArray())
        }

        assertTrue(released.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun interfaceNamesAreLookedUpAgainOnlyNowAndThen() {
        var now = 0L
        var lookups = 0
        var table = mapOf("192.168.1.5" to "wlan0")

        val names = InterfaceNames(clock = { now }) {
            lookups += 1
            table
        }

        val wifi = InetAddress.getByName("192.168.1.5")
        val carrier = InetAddress.getByName("10.20.30.40")
        assertEquals("wlan0", names.of(wifi))
        assertEquals("wlan0", names.of(wifi))
        assertEquals(1, lookups)
        // An address it doesn't know is looked for again, though not more than once a second.
        assertNull(names.of(carrier))
        assertEquals(1, lookups)
        now += 1_000
        table = mapOf("192.168.1.5" to "wlan0", "10.20.30.40" to "rmnet_data0")
        assertEquals("rmnet_data0", names.of(carrier))
        assertEquals(2, lookups)
        // And what it knows is looked at again every few seconds.
        now += 10_000
        table = mapOf("192.168.1.5" to "rmnet_data0")
        assertEquals("rmnet_data0", names.of(wifi))
        assertEquals(3, lookups)
    }

    @Test
    fun theRoomsPolicyTurnsAwayThisDevicesLoopback() {
        val server = server(policy = RoomNetworks())
        val answer = runCatching { request(server.port, "/hello") }
        // Closed without a word: no status line comes back.
        assertTrue(answer.isFailure || answer.getOrThrow().status == 0)
    }

    @Test
    fun connectionsPastTheLimitsAreRefusedAtOnce() {
        val streams = List(10) { EventStream() }
        val next = AtomicInteger()
        val server = server(limits = ServerLimits(connections = 4, perAddress = 2)) { Response.Events(streams[next.getAndIncrement()]) }
        fun refused(from: String? = null): Answer = connect(server.port, from).use { parse(it.getInputStream().readBytes()) }

        follow(server)
        follow(server)
        val third = refused()
        assertEquals(503, third.status)
        assertEquals("5", third.header("Retry-After"))
        assertEquals("nosniff", third.header("X-Content-Type-Options"))

        // Once one ends, there's room again.
        streams[0].close()
        assertTrue(eventually { runCatching { follow(server) }.isSuccess })

        assumeTrue("Needs other loopback addresses to connect from", canBindFrom("127.0.0.2") && canBindFrom("127.0.0.3"))
        follow(server, "127.0.0.2")
        follow(server, "127.0.0.2")
        // Four in all: a device with none of its own is turned away too.
        assertEquals(503, refused("127.0.0.3").status)
    }

    @Test
    fun everyAnswerCarriesSafetyHeadersAndPagesAPolicy() {
        val port = server().port
        val safety = mapOf(
            "x-content-type-options" to "nosniff",
            "referrer-policy" to "no-referrer",
            "x-frame-options" to "DENY",
            "cache-control" to "no-store",
        )
        val text = request(port, "/hello")
        val missing = request(port, "/elsewhere")
        val page = request(port, "/page")
        val bad = exchange(port, "GET /hello HTTP/1.1\r\n\r\n")

        listOf(text, missing, page, bad).forEach { answer ->
            safety.forEach { (name, value) -> assertEquals(name, value, answer.header(name)) }
        }

        // What isn't a page runs nothing, an SVG's script say, even opened on its own.
        assertEquals(FILE_POLICY, text.header("content-security-policy"))
        val policy = page.header("content-security-policy")!!

        listOf("default-src 'none'", "frame-ancestors 'none'", "base-uri 'none'", "form-action 'self'").forEach {
            assertTrue(it, policy.contains(it))
        }

        // The page's own inline scripts run, by their hashes, and nothing else inline does; its data block isn't a script.
        val scripts = policy.substringAfter("script-src").substringBefore(";")
        assertTrue(scripts.contains("'${scriptHash("console.log('mine');")}'"))
        assertTrue(scripts.contains("'${scriptHash("\nimport('./x.js');\n")}'"))
        assertFalse(scripts.contains(scriptHash("{\"data\":1}")))
        assertFalse(scripts.contains("unsafe"))

        val (socket, lines) = Browser(port).follow("/events")
        sockets += socket
        val head = lines.head()
        assertEquals("HTTP/1.1 200 OK", head.first())
        safety.forEach { (name, value) -> assertTrue(name, head.any { it.equals("$name: $value", ignoreCase = true) }) }
    }

    @Test
    fun headersAHandlerMakesCantAddLinesOfTheirOwn() {
        val server = server { Response.Bytes(200, "text/plain", "ok".toByteArray(), mapOf("X-Name" to "a\r\nSet-Cookie: b=c", "X-Fine" to "yes")) }
        val answer = request(server.port, "/")
        assertNull(answer.header("set-cookie"))
        assertNull(answer.header("x-name"))
        assertEquals("yes", answer.header("x-fine"))
    }

    @Test
    fun filesAreServedInThePartsAVideoAsksFor() {
        val video = File(kept, "clip.mp4").apply { writeText(CLIP) }
        val port = server { Response.FromFile("video/mp4", video) }.port
        fun asked(vararg headers: Pair<String, String>) = request(port, "/media/clip.mp4", headers = mapOf(*headers))

        val whole = asked()
        assertEquals(200, whole.status)
        assertEquals("bytes", whole.header("accept-ranges"))
        assertEquals(FILE_POLICY, whole.header("content-security-policy"))
        assertEquals("10000", whole.header("content-length"))
        assertEquals(CLIP, whole.body)

        val first = asked("Range" to "bytes=0-99")
        assertEquals(206, first.status)
        assertEquals("video/mp4", first.header("content-type"))
        assertEquals("bytes 0-99/10000", first.header("content-range"))
        assertEquals("100", first.header("content-length"))
        assertEquals("bytes", first.header("accept-ranges"))
        assertEquals(CLIP.substring(0, 100), first.body)
        // Safe like every answer.
        assertEquals("nosniff", first.header("x-content-type-options"))
        assertEquals("no-store", first.header("cache-control"))

        // Seeking: the middle, the rest from somewhere, and the last bytes, as a video reads its index at the end.
        mapOf("bytes=4321-5678" to (4321 until 5679), "bytes=9990-" to (9990 until 10000), "bytes=-5" to (9995 until 10000)).forEach { (range, bytes) ->
            val part = asked("Range" to range)
            assertEquals(range, 206, part.status)
            assertEquals(range, "bytes ${bytes.first}-${bytes.last}/10000", part.header("content-range"))
            assertEquals(range, CLIP.substring(bytes.first, bytes.last + 1), part.body)
        }

        // Past the end: nothing, and how long the file is.
        val beyond = asked("Range" to "bytes=10000-")
        assertEquals(416, beyond.status)
        assertEquals("bytes */10000", beyond.header("content-range"))
        assertEquals("", beyond.body)

        // Several ranges at once, or a copy kept from before, get the whole file.
        assertEquals(200, asked("Range" to "bytes=0-1,5-6").status)
        val older = asked("Range" to "bytes=0-99", "If-Range" to "\"v1\"")
        assertEquals(200, older.status)
        assertEquals(CLIP, older.body)
    }

    @Test
    fun aPartOfAHugeFileIsReadFromWhereItIsNeverAllOfIt() {
        // Two gigabytes, which no answer could hold in memory, and which take no room on disk.
        val huge = File(kept, "huge.mp4")

        RandomAccessFile(huge, "rw").use { file ->
            file.setLength(HUGE)
            file.seek(HUGE - 10)
            file.write("the end...".toByteArray())
        }

        val port = server { Response.FromFile("video/mp4", huge) }.port
        val tail = request(port, "/media/huge.mp4", headers = mapOf("Range" to "bytes=-10"))
        assertEquals(206, tail.status)
        assertEquals("bytes ${HUGE - 10}-${HUGE - 1}/$HUGE", tail.header("content-range"))
        assertEquals("the end...", tail.body)
    }

    @Test
    fun aHandlerCanSayOtherwiseOnce() {
        // As the shared map's page does, so web videos in it play.
        val server = server { Response.page(200, "<p>page</p>", mapOf("referrer-policy" to "strict-origin")) }
        val answer = request(server.port, "/")
        assertEquals(listOf("strict-origin"), answer.headers["referrer-policy"])
        assertEquals("nosniff", answer.header("x-content-type-options"))
    }

    @Test
    fun inlineScriptsAreFoundAsABrowserReadsThem() {
        val html = "<p>a</p><SCRIPT>one()</SCRIPT><script type=\"module\">two()</script><script src=\"x.js\"></script>" +
            "<script type=\"application/json\" id=\"d\">{}</script><scripts>no</scripts><script async>three()\r\n</script>"
        assertEquals(listOf("one()", "two()", "three()\r\n"), inlineScripts(html))
        // HTML reads a carriage return and line feed as a line feed alone.
        assertEquals(scriptHash("three()\n"), scriptHash("three()\r\n"))
        assertEquals("sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", scriptHash(""))
    }

    @Test
    fun aReaderTooFarBehindIsDropped() {
        val dropped = CountDownLatch(1)
        val behind = EventStream { dropped.countDown() }
        repeat(EVENT_BACKLOG) { behind.send("step $it") }
        assertFalse(behind.isClosed)
        behind.send("one too many")
        assertTrue(behind.isClosed)
        assertTrue(dropped.await(1, TimeUnit.SECONDS))

        // Over a connection whose reader stopped reading, the server stops waiting on it.
        val gone = CountDownLatch(1)
        val slow = EventStream { gone.countDown() }
        val server = server { Response.Events(slow) }

        val socket = Socket().apply {
            receiveBufferSize = 4 * 1024
            connect(InetSocketAddress("127.0.0.1", server.port), 5_000)
        }

        sockets += socket
        socket.getOutputStream().write("GET /events HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\n\r\n".toByteArray())
        val big = "x".repeat(64 * 1024)
        var sent = 0

        while (!slow.isClosed && sent < 10_000) {
            slow.send(big)
            sent += 1
            Thread.sleep(1)
        }

        assertTrue("Still open after $sent events", slow.isClosed)
        assertTrue(gone.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun aBrowserThatLeftIsNoticedWhenItsHeartbeatFails() {
        val gone = CountDownLatch(1)
        val left = EventStream { gone.countDown() }
        val server = server(timings = ServerTimings(heartbeat = 50, tick = 10)) { Response.Events(left) }
        val socket = connect(server.port)
        socket.getOutputStream().write("GET /events HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\n\r\n".toByteArray())
        val lines = socket.getInputStream().bufferedReader()
        assertEquals("HTTP/1.1 200 OK", lines.readLine())
        // It hears heartbeats while it's there.
        assertTrue(generateSequence { lines.readLine() }.take(40).any { it == ": still here" })
        socket.close()
        assertTrue(gone.await(5, TimeUnit.SECONDS))
        assertTrue(left.isClosed)
    }

    @Test
    fun aRequestThatTakesTooLongToArriveIsCutOff() {
        val server = server(timings = ServerTimings(head = 300, tick = 20))
        val socket = connect(server.port).also { sockets += it }
        val output = socket.getOutputStream()
        output.write("GET /hello HTTP/1.1\r\n".toByteArray())
        val started = System.nanoTime()

        val cut = try {
            repeat(50) {
                output.write('X'.code)
                output.flush()
                Thread.sleep(100)
            }

            socket.getInputStream().read() < 0
        } catch (closed: IOException) {
            true
        }

        assertTrue(cut)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    @Test
    fun aFileMadeForOneAnswerGoesWholeAndIsDeletedOnceWritten() {
        spool.mkdirs()
        val made = File(spool, "made.zip").apply { writeText(CLIP) }

        val server = server { _ ->
            Response.FromFile("application/zip", made, mapOf("Content-Disposition" to "attachment; filename=\"made.zip\""), temporary = true)
        }

        // The next request makes the file again, so a part of this one is never offered, nor sent when asked for.
        val answer = exchange(server.port, "GET /made HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nRange: bytes=0-9\r\n\r\n")
        assertEquals(200, answer.status)
        assertEquals(CLIP, answer.body)
        assertEquals("attachment; filename=\"made.zip\"", answer.header("content-disposition"))
        assertNull(answer.header("accept-ranges"))
        assertTrue(eventually { !made.exists() })
    }

    @Test
    fun aBodyThatDripsInIsCutOffThoughEachByteComesInTime() {
        val server = server(timings = ServerTimings(body = 300, bodyPerMegabyte = 1_000, tick = 20))
        val socket = connect(server.port).also { sockets += it }
        val output = socket.getOutputStream()
        output.write("POST /upload HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Length: 1000\r\n\r\n".toByteArray())
        val started = System.nanoTime()

        // A byte every tenth of a second never waits long enough for a read to give up, but the body as a whole does.
        val cut = try {
            repeat(50) {
                output.write('X'.code)
                output.flush()
                Thread.sleep(100)
            }

            socket.getInputStream().read() < 0
        } catch (closed: IOException) {
            true
        }

        assertTrue(cut)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    @Test
    fun aBigBodyHasTimeForItsSize() {
        // 300 ms, and a second for each megabyte: two megabytes coming in over a second arrive whole.
        val server = server(bodyLimit = 4L * 1024 * 1024, spool = spool, timings = ServerTimings(body = 300, bodyPerMegabyte = 1_000, tick = 20)) { request ->
            Response.text(200, "${request.bodyFile?.length()} bytes")
        }

        val socket = connect(server.port).also { sockets += it }
        val output = socket.getOutputStream()
        output.write("POST /upload HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Length: 2000000\r\n\r\n".toByteArray())

        repeat(10) {
            output.write(ByteArray(200_000))
            output.flush()
            Thread.sleep(100)
        }

        val answer = socket.getInputStream().bufferedReader().readText()
        assertTrue(answer, answer.startsWith("HTTP/1.1 200 OK"))
        assertTrue(answer, answer.endsWith("2000000 bytes"))
    }

    private companion object {
        /** Ten thousand bytes a test can read back as text. */
        val CLIP = "0123456789".repeat(1_000)

        const val HUGE = 2L * 1024 * 1024 * 1024

        val PAGE = """
            <!doctype html><title>Page</title>
            <script>console.log('mine');</script>
            <script type="application/json" id="orbit-map">{"data":1}</script>
            <script type="module">
            import('./x.js');
            </script>
            <script src="page.js"></script>
        """.trimIndent()

        fun canBindFrom(address: String): Boolean = runCatching { Socket().use { it.bind(InetSocketAddress(address, 0)) } }.isSuccess

        /** Waits up to five seconds for `condition`. */
        fun eventually(condition: () -> Boolean): Boolean {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)

            while (System.nanoTime() < until) {
                if (condition()) {
                    return true
                }

                Thread.sleep(10)
            }

            return condition()
        }
    }
}
