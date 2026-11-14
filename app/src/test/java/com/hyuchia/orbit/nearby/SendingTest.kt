package com.hyuchia.orbit.nearby

import android.content.ContextWrapper
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MAP_FORMAT
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.data.TestMedia
import com.hyuchia.orbit.data.carriedMediaTypes
import com.hyuchia.orbit.page.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Sending maps to a device taking them, both ends real: [sendMap] and [NearbyHost] with its [LocalServer], over 127.0.0.1. */
class SendingTest {
    /** What a phone in English shows. */
    private val english = FileWords(Locale.US)

    private val files: File = Files.createTempDirectory("orbit-sending").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Folders, and no system services: the advertisement is left out, as on a phone without network service discovery. */
    private val context = object : ContextWrapper(null) {
        override fun getCacheDir(): File = File(files, "cache").apply { mkdirs() }

        override fun getFilesDir(): File = File(files, "files").apply { mkdirs() }

        override fun getSystemService(name: String): Any? = null
    }

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome() = StarterMap("# Welcome\n")
    }

    private val maps = MapRepository(File(files, "maps"), File(files, "trash"), starters, english)
    private lateinit var host: NearbyHost

    private val address: String get() = "127.0.0.1:${host.port}"
    private val offersFolder: File get() = File(context.cacheDir, "offers")

    @Before
    fun start() = runBlocking<Unit> {
        maps.load()
        host = newHost()
    }

    /**
     * A device taking maps, listening on the loopback, with its offers'
     * `times`, its `clock`, a map's `answerTime` and the `room` it says it
     * has as a test needs them.
     */
    private suspend fun newHost(
        times: OfferTimes = OfferTimes(),
        clock: () -> Long = { System.nanoTime() / 1_000_000 },
        room: () -> Long = { files.usableSpace },
        mapLimit: Long = OFFER_LIMIT,
        answerTime: suspend () -> Unit = { delay(times.waiting) },
    ): NearbyHost {
        val made = NearbyHost(context, maps, scope, anyone, english, listenOn = { null }, times = times, clock = clock, room = room, mapLimit = mapLimit, answerTime = answerTime, asset = { name -> File("src/main/assets/map/$name").readText() })
        // It clears what an earlier launch left in its folder as it starts: that's done before anything arrives.
        scope.coroutineContext.job.children.toList().joinAll()
        made.start(accepting = true)
        return made
    }

    /** The test's host, made again as the test says, before anything was offered to it. */
    private fun restart(
        times: OfferTimes = OfferTimes(),
        clock: () -> Long = { System.nanoTime() / 1_000_000 },
        room: () -> Long = { files.usableSpace },
        mapLimit: Long = OFFER_LIMIT,
        answerTime: suspend () -> Unit = { delay(times.waiting) },
    ) = runBlocking {
        host.stop()
        host = newHost(times, clock, room, mapLimit, answerTime)
    }

    @After
    fun stop() {
        host.stop()
        scope.cancel()
        files.deleteRecursively()
    }

    private fun bundle(markdown: String, pictures: Map<String, ByteArray> = emptyMap()): File {
        val file = File(files, "${UUID.randomUUID()}.zip")

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // Pictures are already compressed: storing them keeps big bundles quick to make.
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry("map.md"))
            zip.write(markdown.toByteArray())
            zip.closeEntry()

            pictures.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("media/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        return file
    }

    private fun picture(size: Int, seed: Long = 1): ByteArray = ByteArray(size).also { Random(seed).nextBytes(it) }

    /** What the person sending says about the codes: each code they're shown, and their answer. */
    private class Checker(private val answer: suspend (String) -> Boolean = { true }) {
        val shown = CopyOnWriteArrayList<String>()

        suspend fun check(code: String): Boolean {
            shown += code
            return answer(code)
        }
    }

    /** Sends in the background, keeping every state the sender passes through. */
    private fun CoroutineScope.send(
        key: String?,
        bundle: File,
        version: Int? = OFFER_VERSION,
        from: String = "Ana's Pixel",
        to: String = address,
        checker: Checker = Checker(),
    ): Pair<Job, CopyOnWriteArrayList<SendState>> {
        val states = CopyOnWriteArrayList<SendState>()
        val job = launch(Dispatchers.IO) { sendMap(to, key, version, from, bundle, checker::check) { states += it } }
        return job to states
    }

    private suspend fun waitFor(states: List<SendState>, predicate: (SendState) -> Boolean): SendState = withTimeout(15_000) {
        while (states.none(predicate)) {
            delay(20)
        }

        states.first(predicate)
    }

    private suspend fun incoming(): Incoming = withTimeout(15_000) { host.incoming.first { it.isNotEmpty() }.single() }

    private suspend fun arrived(): Offer = withTimeout(15_000) { host.offers.first { it.isNotEmpty() }.single() }

    /** One request over a plain socket, as another device would make it, from `from` when it's given. */
    private fun request(method: String, path: String, headers: Map<String, String> = emptyMap(), body: ByteArray = ByteArray(0), from: String? = null): Pair<Int, String> =
        Socket().use { socket ->
            from?.let { socket.bind(InetSocketAddress(it, 0)) }
            socket.connect(InetSocketAddress("127.0.0.1", host.port), 5_000)

            val head = buildString {
                append("$method $path HTTP/1.1\r\nHost: $address\r\nConnection: close\r\n")
                headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Length: ${body.size}\r\n\r\n")
            }

            socket.getOutputStream().apply {
                write(head.toByteArray())
                write(body)
                flush()
            }

            val response = socket.getInputStream().readBytes().decodeToString()
            response.substringBefore("\r\n").split(' ')[1].toInt() to response.substringAfter("\r\n\r\n")
        }

    /** A request's head alone, saying its body is `length` bytes, and the answer: one that comes before any body was read. */
    private fun headOnly(path: String, length: Long, headers: Map<String, String> = emptyMap(), from: String? = null): Int = Socket().use { socket ->
        from?.let { socket.bind(InetSocketAddress(it, 0)) }
        socket.connect(InetSocketAddress("127.0.0.1", host.port), 5_000)
        socket.soTimeout = 5_000

        val head = buildString {
            append("POST $path HTTP/1.1\r\nHost: $address\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Length: $length\r\n\r\n")
        }

        socket.getOutputStream().write(head.toByteArray())
        socket.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
    }

    /** An offer's hello sealed to the host as a sender seals it: its session, its half of the check code, and the request. */
    private class Hello(val session: OfferSession, val senderNonce: ByteArray, val headers: Map<String, String>, val body: ByteArray)

    private fun hello(from: String = "Ana's Pixel"): Hello {
        val session = OfferSession.forSender(host.key)
        val senderNonce = randomBytes(CHECK_NONCE_BYTES)
        val payload = OfferHead(from, session.commitment(senderNonce)).bytes()
        val body = ByteArrayOutputStream().also { session.seal(payload.inputStream(), payload.size.toLong(), it) }.toByteArray()
        return Hello(session, senderNonce, mapOf(VERSION_HEADER to "$OFFER_VERSION", KEY_HEADER to session.senderKey.text), body)
    }

    private fun greet(hello: Hello, from: String? = null): Pair<Int, OfferAnswer?> {
        val (status, body) = request("POST", "/offer", hello.headers, hello.body, from)
        return status to if (status == 200) offerJson.decodeFromString(OfferAnswer.serializer(), body) else null
    }

    private fun reveal(id: String, nonce: ByteArray, from: String? = null): Pair<Int, String> =
        request("POST", "/offer/$id", body = offerJson.encodeToString(Reveal.serializer(), Reveal(base64Url(nonce))).toByteArray(), from = from)

    /** A hello revealed, so both devices show the code: its id, and what the sender has. From `from`, when it's given. */
    private fun agreed(from: String? = null): Pair<String, Hello> {
        val hello = hello()
        val (_, answer) = greet(hello, from)
        assertEquals(200, reveal(answer!!.id, hello.senderNonce, from).first)
        return answer.id to hello
    }

    private fun sealedMap(hello: Hello, markdown: String): ByteArray {
        val zip = bundle(markdown).readBytes()
        return ByteArrayOutputStream().also { hello.session.sealMap(zip.inputStream(), zip.size.toLong(), it) }.toByteArray()
    }

    private fun state(id: String): String = offerJson.decodeFromString(StateAnswer.serializer(), request("GET", "/offer/$id").second).state

    /**
     * A device between the sender and the host, as one answering in the
     * host's place on the Wi-Fi: it answers a request itself when `instead`
     * says how, else passes it on to the host, and the host's answer back,
     * as `change` makes it (as it is for null).
     */
    private fun between(instead: (Request) -> Response? = { null }, change: (Request, Answer) -> Response? = { _, _ -> null }): LocalServer = LocalServer({ _, _ -> 64 * 1024 }, policy = anyone) { request ->
        instead(request)?.let { return@LocalServer it }

        val head = buildString {
            append("${request.method} ${request.path} HTTP/1.1\r\nHost: $address\r\nConnection: close\r\n")
            request.headers.filterKeys { it !in setOf("host", "connection", "content-length") }.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Length: ${request.body.size}\r\n\r\n")
        }

        val answer = exchange(host.port, head + String(request.body, Charsets.ISO_8859_1))
        change(request, answer) ?: Response.Bytes(answer.status, answer.header("content-type") ?: "text/plain", answer.body.toByteArray())
    }

    /** The host's answer as JSON changed by `change`, or as it is when it isn't JSON. */
    private fun Answer.json(change: (JsonObject) -> JsonObject): Response {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        return Response.Bytes(status, header("content-type") ?: "text/plain", (json?.let(change)?.toString() ?: body).toByteArray())
    }

    /**
     * Orbit 0.3.0 taking maps, as far as its answers go: this host's, passed
     * on without what of a map it opens, which 0.3.0 doesn't say, without
     * proofs, and with its half of the check code all random, as 0.3.0 and
     * 0.4.0 make it. The codes then differ, as only this stand-in makes them.
     */
    private fun orbit030(): LocalServer = between { _, answer ->
        answer.json { json ->
            val random = json["nonce"]?.let { mapOf("nonce" to JsonPrimitive(base64Url(randomBytes(CHECK_NONCE_BYTES)))) }.orEmpty()
            JsonObject(json - "mapFormat" - "mediaTypes" - "mapSize" - "proof" + random)
        }
    }

    /**
     * Orbit 0.4.0 taking maps, as far as its answers go: this host's,
     * without how big a map it takes, which 0.4.0 doesn't say, and without
     * proofs. Its codes differ from the sender's, as [orbit030]'s do.
     */
    private fun orbit040(): LocalServer = between { _, answer ->
        answer.json { json ->
            val random = json["nonce"]?.let { mapOf("nonce" to JsonPrimitive(base64Url(randomBytes(CHECK_NONCE_BYTES)))) }.orEmpty()
            JsonObject(json - "mapSize" - "proof" + random)
        }
    }

    /** A file of zeros that says it's `size` bytes, on a disk that keeps none of them: a map's zip as big as a test needs, never written. */
    private fun sparse(size: Long): File = File(files, "${UUID.randomUUID()}.zip").also { file -> RandomAccessFile(file, "rw").use { it.setLength(size) } }

    @Test
    fun `a map sent to a typed address arrives whole, once both devices showed the same code`() = runBlocking<Unit> {
        val photo = picture(1_500_000)
        val checker = Checker()
        val (_, states) = send(key = null, bundle = bundle("# Trip\n- Day one\n  - Lisbon\n", mapOf("photo.jpg" to photo)), version = null, checker = checker)

        val offer = arrived()
        assertEquals("Ana's Pixel", offer.from)
        assertEquals("Trip", offer.bundle.title)
        assertEquals(1, offer.bundle.media.pictures)
        assertEquals("127.0.0.1", offer.address)
        val waiting = waitFor(states) { it is SendState.Waiting } as SendState.Waiting
        assertTrue(offer.check, Regex("[0-9]{4}").matches(offer.check))
        assertEquals(offer.check, waiting.check)
        assertEquals(listOf(offer.check), checker.shown.toList())
        assertEquals(listOf(SendState.Sending, SendState.Confirming(offer.check), waiting), states.toList())

        val id = (host.accept(offer, keepBoth = false) as Accepted.Opened).id
        assertEquals(SendState.Opened, waitFor(states) { it == SendState.Opened })
        assertTrue(maps.read(id).contains("Lisbon"))
        assertArrayEquals(photo, File(maps.folder(id), "media/photo.jpg").readBytes())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `nothing of the map leaves before the person sending says the codes match, and nothing at all when they don't`() = runBlocking<Unit> {
        val answer = CompletableDeferred<Boolean>()
        val checker = Checker { answer.await() }
        val (_, states) = send(host.key.text, bundle("# Our secret plan\n- Buy the island\n"), checker = checker)

        // Both devices show the code, and that's all there is here: no map, nothing waiting to be taken.
        val coming = incoming()
        assertEquals((waitFor(states) { it is SendState.Confirming } as SendState.Confirming).check, coming.check)
        assertEquals("Ana's Pixel", coming.from)
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())

        answer.complete(false)
        assertEquals(SendState.Cancelled, waitFor(states) { it == SendState.Cancelled })
        assertTrue(withTimeout(5_000) { host.incoming.first { it.isEmpty() } }.isEmpty())
        assertEquals("declined", state(coming.id))
        assertTrue(host.offers.value.isEmpty())
    }

    @Test
    fun `the device taking the map can decline while the codes are compared, and then nothing is sent`() = runBlocking<Unit> {
        val checker = Checker { awaitCancellation() }
        val (_, states) = send(host.key.text, bundle("# Plan\n"), checker = checker)
        host.decline(incoming())
        assertEquals(SendState.Declined, waitFor(states) { it == SendState.Declined })
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `closing the sheet while the codes are compared tells the other device the map won't come`() = runBlocking<Unit> {
        val (job, _) = send(host.key.text, bundle("# Plan\n"), checker = Checker { awaitCancellation() })
        val coming = incoming()
        job.cancelAndJoin()
        assertTrue(withTimeout(5_000) { host.incoming.first { it.isEmpty() } }.isEmpty())
        assertEquals("declined", state(coming.id))
    }

    @Test
    fun `a device found nearby is sent to with the key and version it advertises, and can say no`() = runBlocking<Unit> {
        val advertised = serviceAttributes(host.selfId, host.key).mapValues { it.value.toByteArray() }
        val device = nearbyDevice("Ana's tablet", advertised, "127.0.0.1", host.port, selfId = "another launch")!!
        assertEquals(OFFER_VERSION, device.version)
        val (_, states) = send(device.key, bundle("# Plan\n- Ask\n"), version = device.version, to = device.address)

        val offer = arrived()
        assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
        host.decline(offer)
        assertEquals(SendState.Declined, waitFor(states) { it == SendState.Declined })
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `maps arrive with the name chosen for the device, cleaned to show`() = runBlocking<Unit> {
        // A right-to-left override would show the name backwards.
        host.rename("  Ana's‮ tablet ")
        assertEquals("Ana's tablet", host.name)
        assertEquals("Ana's tablet", host.nameFor("Ana's tablet"))
        val (_, states) = send(host.key.text, bundle("# Plan\n"), from = host.name)
        assertEquals("Ana's tablet", incoming().from)
        val offer = arrived()
        assertEquals("Ana's tablet", offer.from)
        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
    }

    @Test
    fun `check codes differ from one send to the next`() = runBlocking<Unit> {
        val codes = mutableListOf<String>()

        repeat(4) {
            val (_, states) = send(host.key.text, bundle("# Plan $it\n"), checker = Checker { awaitCancellation() })
            val coming = incoming()
            assertEquals(coming.check, (waitFor(states) { it is SendState.Confirming } as SendState.Confirming).check)
            codes += coming.check
            host.decline(coming)
            waitFor(states) { it == SendState.Declined }
        }

        assertTrue(codes.toString(), codes.toSet().size >= 3)
    }

    @Test
    fun `an advertised key from before the device started again gives way to the one it has now`() = runBlocking<Unit> {
        val earlier = DeviceKey.of(newKeyPair()).text
        val (_, states) = send(earlier, bundle("# Plan\n- Still arrives\n"))
        val offer = arrived()
        assertEquals("Plan", offer.bundle.title)
        assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
    }

    @Test
    fun `the key and its version are there to ask for while the device takes maps, and the advertisement has the same`() {
        val (status, body) = request("GET", "/key")
        assertEquals(200, status)
        val answer = offerJson.decodeFromString(KeyAnswer.serializer(), body)
        assertEquals(OFFER_VERSION, answer.version)
        assertEquals(host.key.text, answer.key)
        assertEquals(serviceAttributes(host.selfId, host.key)[SERVICE_KEY], answer.key)
        assertEquals("$OFFER_VERSION", serviceAttributes(host.selfId, host.key)[SERVICE_VERSION])
        assertNotNull(DeviceKey.read(answer.key))

        // Showing a map to a computer keeps it listening, without taking maps.
        host.share("map", AppTheme(dark = false, tokens = emptyMap()))
        host.start(accepting = false)
        assertEquals(403, request("GET", "/key").first)
        val hello = hello()
        assertEquals(403, request("POST", "/offer", hello.headers, hello.body).first)
    }

    @Test
    fun `the device taking maps says what of a map it opens, and how big a map it takes, with its key and in its answer to a hello`() {
        val key = offerJson.decodeFromString(KeyAnswer.serializer(), request("GET", "/key").second)
        assertEquals(MAP_FORMAT, key.mapFormat)
        assertEquals(carriedMediaTypes().sorted(), key.mediaTypes)
        assertEquals(OFFER_LIMIT, key.mapSize)

        val (status, answer) = greet(hello())
        assertEquals(200, status)
        assertEquals(MAP_FORMAT, answer!!.mapFormat)
        assertEquals(carriedMediaTypes().sorted(), answer.mediaTypes)
        assertEquals(OFFER_LIMIT, answer.mapSize)
    }

    @Test
    fun `a map bigger than an older Orbit takes goes no further than its hello, and the person sending hears to update Orbit there`() = runBlocking<Unit> {
        val older = orbit040()
        older.start(0)

        try {
            // A map just past the 64 MiB Orbit 0.4.0 takes, sealed: it never needs to be read.
            val big = sparse(OFFER_LIMIT_UP_TO_0_4_0 - sealedLength(0) + 1)
            val checker = Checker()
            val (job, states) = send(host.key.text, big, to = "127.0.0.1:${older.port}", checker = checker)
            job.join()

            assertEquals(listOf(SendState.Sending, SendState.TooBigThere(sealedLength(big.length()))), states.toList())
            // Its code never showed, there or here, and the offer it said hello with is gone.
            assertTrue(checker.shown.isEmpty())
            assertTrue(host.incoming.value.isEmpty())
            assertTrue(eventually { host.incoming.value.isEmpty() && host.offers.value.isEmpty() })

            // One as big as 0.4.0 takes goes to it as before: its code shows.
            val (fits, fitting) = send(host.key.text, bundle("# Plan\n", mapOf("photo.jpg" to picture(5_000))), to = "127.0.0.1:${older.port}", checker = Checker { false })
            waitFor(fitting) { it is SendState.Confirming }
            fits.join()
        } finally {
            older.stop()
        }
    }

    @Test
    fun `a device's word on how big a map it takes is believed, and what it leaves unsaid is as an older Orbit had it`() {
        assertEquals(OFFER_LIMIT_UP_TO_0_4_0, DeviceReads.of(1, null).mapSize)
        assertEquals(OFFER_LIMIT_UP_TO_0_4_0, DeviceReads.of(1, null, mapSize = 0).mapSize)
        assertEquals(OFFER_LIMIT, DeviceReads.of(1, null, mapSize = OFFER_LIMIT).mapSize)
        assertEquals(OFFER_LIMIT_UP_TO_0_4_0, DeviceReads.RELEASE_0_3_0.mapSize)
    }

    @Test
    fun `a map opens as it arrives, straight from the connection, and needs room for itself once`() = runBlocking<Unit> {
        val photo = picture(3_000_000, seed = 4)
        val zip = bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to photo))
        // Room for the map once and what's always left free, but not twice: a sealed copy and an opened one wouldn't fit.
        restart(room = { sealedLength(zip.length()) * 3 / 2 + SPARE_SPACE })

        val (_, states) = send(host.key.text, zip)
        val offer = arrived()
        assertEquals(1, offer.bundle.media.pictures)
        waitFor(states) { it is SendState.Waiting }
        // Nothing of it was spooled: it opened from the connection into its own files.
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
        assertArrayEquals(photo, offer.bundle.pictures.getValue("beach.jpg").readBytes())

        val id = (host.accept(offer, keepBoth = false) as Accepted.Opened).id
        assertArrayEquals(photo, maps.picture(id, "beach.jpg")!!.readBytes())
        assertEquals(emptyList<String>(), File(files, "unpacking").list().orEmpty().toList())
        waitFor(states) { it == SendState.Opened }
    }

    @Test
    fun `a map the storage runs out of room for as it opens is refused, and leaves nothing behind`() = runBlocking<Unit> {
        val zip = bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to picture(4_000_000, seed = 5)))
        // Room enough as it's admitted and starts, gone a couple of megabytes in.
        val looks = AtomicInteger()
        restart(room = { if (looks.incrementAndGet() <= 3) Long.MAX_VALUE / 4 else SPARE_SPACE / 2 })

        val (job, states) = send(host.key.text, zip)
        job.join()

        assertEquals(SendState.Failed(R.string.nearby_send_no_room_there), states.last())
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), File(files, "unpacking").list().orEmpty().toList())
    }

    @Test
    fun `a declined map's files go at once`() = runBlocking<Unit> {
        val (_, states) = send(host.key.text, bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to picture(2_000_000, seed = 6))))
        val offer = arrived()
        val unpacked = offer.bundle.pictures.getValue("beach.jpg")
        assertTrue(unpacked.isFile)

        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
        assertFalse(unpacked.exists())
        assertEquals(emptyList<String>(), File(files, "unpacking").list().orEmpty().toList())
    }

    @Test
    fun `Decline tapped on a map on its way as it arrives declines the map it became, so its sender never hears otherwise`() = runBlocking<Unit> {
        // The dialog of the map on its way is still up as the map arrives, and its Decline is tapped then.
        val compared = CompletableDeferred<Unit>()

        val (_, states) = send(host.key.text, bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to picture(200_000, seed = 11))), checker = Checker {
            compared.await()
            true
        })

        val stale = incoming()
        compared.complete(Unit)
        val offer = arrived()
        waitFor(states) { it is SendState.Waiting }
        val unpacked = offer.bundle.pictures.getValue("beach.jpg")
        val before = maps.maps.value.orEmpty().size

        host.decline(stale)

        assertEquals(SendState.Declined, waitFor(states) { it == SendState.Declined || it == SendState.Opened })
        assertTrue(host.offers.value.isEmpty())
        assertEquals(Accepted.AnsweredAlready, host.accept(offer, keepBoth = false))
        assertEquals("declined", state(offer.id))
        assertEquals(before, maps.maps.value.orEmpty().size)
        assertFalse(unpacked.exists())
    }

    @Test
    fun `Decline tapped on a map on its way once it was opened changes nothing`() = runBlocking<Unit> {
        val compared = CompletableDeferred<Unit>()

        val (_, states) = send(host.key.text, bundle("# Trip\n- Beach\n"), checker = Checker {
            compared.await()
            true
        })

        val stale = incoming()
        compared.complete(Unit)
        val offer = arrived()
        val opened = host.accept(offer, keepBoth = false) as Accepted.Opened

        host.decline(stale)

        assertEquals("opened", state(offer.id))
        assertEquals(SendState.Opened, waitFor(states) { it == SendState.Declined || it == SendState.Opened })
        assertNotNull(maps.summary(opened.id))
    }

    @Test
    fun `an offer takes one answer, and tapping Open again, or Decline after it, changes nothing`() = runBlocking<Unit> {
        val (_, states) = send(host.key.text, bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to picture(200_000, seed = 7))))
        val offer = arrived()
        val before = maps.maps.value.orEmpty().size

        val opened = host.accept(offer, keepBoth = false) as Accepted.Opened
        assertEquals(Accepted.AnsweredAlready, host.accept(offer, keepBoth = true))
        host.decline(offer)

        assertEquals(SendState.Opened, waitFor(states) { it == SendState.Opened || it == SendState.Declined })
        assertEquals("opened", state(offer.id))
        assertEquals(before + 1, maps.maps.value.orEmpty().size)
        assertTrue(File(maps.folder(opened.id), "media/beach.jpg").isFile)
    }

    @Test
    fun `a map whose time to be answered ran out is declined, can't be opened after, and its sender hears so`() = runBlocking<Unit> {
        // The map's time runs out when the test says, once it has seen the map arrive: never before, however slow the machine.
        val timeUp = CompletableDeferred<Unit>()
        restart(answerTime = { timeUp.await() })
        val (_, states) = send(host.key.text, bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to picture(200_000, seed = 9))))
        val offer = arrived()
        val before = maps.maps.value.orEmpty().size
        timeUp.complete(Unit)

        assertEquals(SendState.Declined, waitFor(states) { it == SendState.Declined || it is SendState.Failed })
        assertEquals(Accepted.AnsweredAlready, host.accept(offer, keepBoth = false))
        assertEquals("declined", state(offer.id))
        assertEquals(before, maps.maps.value.orEmpty().size)
        assertEquals(emptyList<String>(), File(files, "unpacking").list().orEmpty().toList())
    }

    @Test
    fun `a sender waits for the answer as long as any release keeps a map waiting for one, and this one keeps it no longer than any sender waits`() {
        // So a sender never says no answer came while the map can still be opened there: it hears the device decline it.
        assertTrue(ANSWER_WAIT_MILLISECONDS > OfferTimes().waiting)
        assertTrue(ANSWER_WAIT_MILLISECONDS > ANSWER_LIFETIME_UP_TO_0_5_0)
        // Orbit 0.5.0 and earlier wait three minutes for the answer: a map here is declined by then.
        assertTrue(OfferTimes().waiting <= 3 * 60 * 1000L)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a sender's waits are measured in time, however long each ask of the other device takes`() = runTest {
        val times = SendTimes()
        val waiting = setOf("opened", "declined")

        // An ask that never comes back, and one that takes as long as a read used to be let take, past a minute.
        listOf<suspend () -> String?>({ awaitCancellation() }, { delay(61_000L); "waiting" }).forEach { ask ->
            val started = testScheduler.currentTime
            assertNull(waitForState(times.answer, times.check, waiting, ask))
            assertEquals(ANSWER_WAIT_MILLISECONDS, testScheduler.currentTime - started)

            val showing = testScheduler.currentTime
            assertNull(waitForState(times.confirming, times.check, setOf("declined"), ask))
            assertEquals(CONFIRMING_LIFETIME_MILLISECONDS, testScheduler.currentTime - showing)
        }

        // An answer in time is heard at the next check.
        val started = testScheduler.currentTime
        var asks = 0
        assertEquals("opened", waitForState(times.answer, times.check, waiting) { if (++asks == 3) "opened" else "waiting" })
        assertEquals(3 * times.check, testScheduler.currentTime - started)
    }

    /**
     * A device between the sender and the host that passes each request on,
     * but holds those `held` picks by their first line open, unanswered, or
     * answers them a byte at a time when `drip` says so, never to the end:
     * a device that stopped answering, or answers as slowly as it can.
     * `passed` hears each request passed on.
     */
    private inner class Stalling(
        private val held: (String) -> Boolean,
        private val drip: Boolean = false,
        private val passed: (String) -> Unit = {},
    ) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val threads = Executors.newCachedThreadPool()

        /** How many requests it held. */
        val holding = AtomicInteger()

        /** How many of those the sender closed as it stopped waiting for them. */
        val closed = AtomicInteger()

        val address: String get() = "127.0.0.1:${server.localPort}"

        init {
            threads.execute {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    threads.execute { runCatching { pass(socket) } }
                }
            }
        }

        private fun pass(socket: Socket) {
            socket.use {
                val input = socket.getInputStream()
                val head = ByteArrayOutputStream()

                while (!head.toString(Charsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                    val byte = input.read()

                    if (byte < 0) {
                        return
                    }

                    head.write(byte)
                }

                val text = head.toString(Charsets.ISO_8859_1)
                val line = text.substringBefore("\r\n")
                val body = input.readNBytes(Regex("(?im)^content-length: *([0-9]+)").find(text)?.groupValues?.get(1)?.toInt() ?: 0)

                if (held(line)) {
                    holding.incrementAndGet()

                    try {
                        if (drip) {
                            val output = socket.getOutputStream()
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n".toByteArray())

                            while (true) {
                                output.write(' '.code)
                                output.flush()
                                Thread.sleep(100)
                            }
                        }

                        // The sender sends nothing more: the read ends only as it closes the connection.
                        input.read()
                    } catch (gone: Exception) {
                        // Closed by the sender, as it stopped waiting.
                    }

                    closed.incrementAndGet()
                    return
                }

                Socket("127.0.0.1", host.port).use { upstream ->
                    val forwarded = text.replace(Regex("(?im)^host: [^\r]*\r\n"), "Host: 127.0.0.1:${host.port}\r\n")
                    upstream.getOutputStream().write(forwarded.toByteArray(Charsets.ISO_8859_1) + body)
                    socket.getOutputStream().write(upstream.getInputStream().readBytes())
                }

                passed(line)
            }
        }

        override fun close() {
            server.close()
            threads.shutdownNow()
        }
    }

    /** Sends through `to` with `times`, keeping every state the sender passes through, and when, in milliseconds. */
    private fun CoroutineScope.timedSend(to: String, times: SendTimes, checker: Checker = Checker()): Pair<Job, CopyOnWriteArrayList<Pair<SendState, Long>>> {
        val states = CopyOnWriteArrayList<Pair<SendState, Long>>()
        val job = launch(Dispatchers.IO) { sendMap(to, host.key.text, OFFER_VERSION, "Ana's Pixel", bundle("# Plan\n"), checker::check, times) { states += it to System.nanoTime() / 1_000_000 } }
        return job to states
    }

    /** How long the sender took from the first state `from` picks to the first `to` picks, waiting for both. */
    private suspend fun took(states: List<Pair<SendState, Long>>, from: (SendState) -> Boolean, to: (SendState) -> Boolean): Pair<SendState, Long> = withTimeout(15_000) {
        while (states.none { to(it.first) }) {
            delay(20)
        }

        val (ended, at) = states.first { to(it.first) }
        val since = states.firstOrNull { from(it.first) } ?: throw AssertionError("Never there: ${states.map { it.first }}")
        ended to at - since.second
    }

    @Test
    fun `a device that stops answering once the map is there is waited for as long as the sender says, and no longer`() = runBlocking<Unit> {
        val mapThere = AtomicBoolean(false)

        listOf(false, true).forEach { drip ->
            mapThere.set(false)

            // After the map arrives, every ask of where it is is held open, or answered a byte every tenth of a second.
            Stalling(held = { mapThere.get() && it.startsWith("GET /offer/") }, drip = drip, passed = { if (it.contains("/map ")) mapThere.set(true) }).use { device ->
                val times = SendTimes(check = 50, answer = 1_500, ask = 300)
                val (_, states) = timedSend(device.address, times)
                val (ended, waited) = took(states, { it is SendState.Waiting }, { it is SendState.Failed || it == SendState.Declined || it == SendState.Opened })

                assertEquals("drip $drip", SendState.Failed(R.string.nearby_send_no_answer), ended)
                assertTrue("drip $drip: $waited", waited >= times.answer && waited < times.answer + times.ask + 5_000)
                // Each ask was let go of in its time: none waits on.
                assertTrue(device.holding.get() > 1)
                assertTrue(eventually { device.closed.get() == device.holding.get() })
            }

            // Its place under the host's limits goes, for the next.
            host.offers.value.forEach { host.decline(it) }
        }
    }

    @Test
    fun `a device that stops answering while the codes show is waited for as long as it waits for the map`() = runBlocking<Unit> {
        Stalling(held = { it.startsWith("GET /offer/") }).use { device ->
            val times = SendTimes(check = 50, confirming = 1_500, ask = 300)
            // The person sending never says whether the codes match.
            val (_, states) = timedSend(device.address, times, Checker { awaitCancellation() })
            val (ended, waited) = took(states, { it is SendState.Confirming }, { it == SendState.Declined || it is SendState.Failed })

            assertEquals(SendState.Declined, ended)
            assertTrue("$waited", waited >= times.confirming && waited < times.confirming + times.ask + 5_000)
            assertTrue(eventually { device.closed.get() == device.holding.get() })
        }
    }

    @Test
    fun `closing the sheet while an ask is held stops sending there and then`() = runBlocking<Unit> {
        val mapThere = AtomicBoolean(false)

        Stalling(held = { mapThere.get() && it.startsWith("GET /offer/") }, passed = { if (it.contains("/map ")) mapThere.set(true) }).use { device ->
            // The ask has the ten seconds every release gives it: the sheet closes well within them.
            val (job, states) = timedSend(device.address, SendTimes(check = 50))
            assertTrue(eventually { device.holding.get() == 1 })

            job.cancel()
            withTimeout(2_000) { job.join() }
            assertTrue(eventually { device.closed.get() == 1 })
            assertFalse(states.toString(), states.any { it.first is SendState.Failed })
        }
    }

    @Test
    fun `closing the sheet while the codes show tells the other device for no longer than it may`() = runBlocking<Unit> {
        // Nothing but the hello and the half of the code gets an answer: the asks, and the word that the map won't come, are held.
        Stalling(held = { it.startsWith("GET /offer/") || it.contains("/cancel ") }).use { device ->
            val times = SendTimes(check = 50, cancel = 500)
            val (job, _) = timedSend(device.address, times, Checker { awaitCancellation() })
            assertTrue(eventually { device.holding.get() >= 1 })

            val closing = System.nanoTime()
            job.cancel()
            withTimeout(times.cancel + 2_000) { job.join() }
            assertTrue((System.nanoTime() - closing) / 1_000_000 >= times.cancel)
            assertTrue(eventually { device.closed.get() == device.holding.get() })
        }
    }

    @Test
    fun `closing the sheet while the hello is held stops sending there and then`() = runBlocking<Unit> {
        Stalling(held = { it.startsWith("POST /offer ") }).use { device ->
            val (job, states) = timedSend(device.address, SendTimes())
            assertTrue(eventually { device.holding.get() == 1 })

            job.cancel()
            withTimeout(2_000) { job.join() }
            assertTrue(eventually { device.closed.get() == 1 })
            assertEquals(listOf<SendState>(SendState.Sending), states.map { it.first })
        }
    }

    @Test
    fun `declining a map as it arrives stops it at its next piece, and leaves nothing behind`() = runBlocking<Unit> {
        val (id, hello) = agreed()
        val map = sealedMap(hello, "# Plan\n- Slow\n")

        Socket("127.0.0.1", host.port).use { slow ->
            slow.soTimeout = 10_000
            val output = slow.getOutputStream()
            output.write("POST /offer/$id/map HTTP/1.1\r\nHost: $address\r\nContent-Length: ${map.size}\r\n\r\n".toByteArray())
            output.write(map, 0, 10)
            output.flush()
            assertTrue(eventually { host.incoming.value.singleOrNull { it.id == id }?.uploading == true })

            host.decline(host.incoming.value.single())
            // The next piece to arrive stops it: the device answers without waiting for the rest.
            output.write(map, 10, 10)
            output.flush()
            val status = slow.getInputStream().bufferedReader().readLine()
            assertTrue(status, status.startsWith("HTTP/1.1 404"))
        }

        assertEquals("declined", state(id))
        assertTrue(host.incoming.value.isEmpty())
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
        assertEquals(emptyList<String>(), File(files, "unpacking").list().orEmpty().toList())
    }

    @Test
    fun `closing the sheet while the map is on its way stops it there and then, and the other device stops waiting for it`() = runBlocking<Unit> {
        val looks = AtomicInteger(0)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)

        // The device stops reading a megabyte into the map, as a slow one would: the sender's connection fills up.
        restart(
            room = {
                if (looks.incrementAndGet() == 3) {
                    held.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }

                files.usableSpace
            },
        )

        try {
            val (job, states) = send(host.key.text, bundle("# Trip\n- Beach ![](media/beach.jpg)\n", mapOf("beach.jpg" to picture(48_000_000, seed = 8))))
            assertTrue(held.await(15, TimeUnit.SECONDS))
            val coming = host.incoming.value.single()

            // The sheet closes: the map stops going at once, though the device reads none of what's on its way.
            withTimeout(10_000) { job.cancelAndJoin() }
            assertTrue(eventually { host.incoming.value.isEmpty() })
            assertEquals("declined", state(coming.id))
            assertFalse(states.toString(), states.any { it is SendState.Waiting || it is SendState.Failed })
        } finally {
            release.countDown()
        }

        assertTrue(eventually { File(files, "unpacking").list().orEmpty().isEmpty() && offersFolder.list().orEmpty().isEmpty() })
    }

    @Test
    fun `a map on its way stops as sending is cancelled, even before a thread was free to stop it`() = runBlocking<Unit> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val socket = Socket()
            socket.connect(server.localSocketAddress, 5_000)
            // A device that reads none of it: the map's write fills the connection, and waits there.
            val device = server.accept()
            // Every thread that could close the connection is busy, as on a phone doing much at once.
            val busy = Executors.newSingleThreadExecutor()
            val free = CountDownLatch(1)
            busy.execute { free.await(30, TimeUnit.SECONDS) }
            val writing = CountDownLatch(1)
            val stopped = CountDownLatch(1)

            try {
                val sending = launch(Dispatchers.IO) {
                    closedOnCancel(socket, busy.asCoroutineDispatcher()) {
                        writing.countDown()

                        try {
                            socket.getOutputStream().write(ByteArray(64 * 1024 * 1024))
                        } catch (closed: IOException) {
                            // As the connection closes, the write under way ends.
                            stopped.countDown()
                        }
                    }
                }

                assertTrue(writing.await(15, TimeUnit.SECONDS))
                // The sheet closes before anything could run to close the connection; then a thread is free.
                sending.cancel()
                free.countDown()
                assertTrue(stopped.await(30, TimeUnit.SECONDS))
                assertTrue(socket.isClosed)
                withTimeout(10_000) { sending.join() }
            } finally {
                free.countDown()
                device.close()
                socket.close()
                busy.shutdown()
            }
        }
    }

    @Test
    fun `before a map goes to an older Orbit, the person sending hears which of its files stay here`() = runBlocking<Unit> {
        val markdown = "# Trip\n\n- Lisbon\n\n  ![Tram](media/photo.jpg)\n- Fado <!-- orbit audio: media/sound-1.mp3 -->\n" +
            "- Waves <!-- orbit audio: media/sound-2.ogg -->\n- Notes <!-- orbit audio: media/memo-1.mp4 -->\n"
        val media = mapOf(
            "photo.jpg" to picture(2_000),
            "clip.mp4" to picture(3_000, seed = 2),
            "clip.jpg" to picture(1_000, seed = 3),
            "sound-1.mp3" to picture(4_000, seed = 4),
            "sound-2.ogg" to picture(5_000, seed = 5),
            "memo-1.mp4" to picture(6_000, seed = 6),
        )

        // This version opens all of it: the code comes alone.
        val (here, toThisVersion) = send(host.key.text, bundle(markdown, media), checker = Checker { false })
        assertNull((waitFor(toThisVersion) { it is SendState.Confirming } as SendState.Confirming).older)
        here.join()

        val older = orbit030()
        older.start(0)

        try {
            val (there, toOrbit030) = send(host.key.text, bundle(markdown, media), to = "127.0.0.1:${older.port}", checker = Checker { false })
            val confirming = waitFor(toOrbit030) { it is SendState.Confirming } as SendState.Confirming
            // Its pictures and videos go, a voice memo's .mp4 too; the sounds of the kinds 0.3.0 doesn't keep stay.
            assertEquals(OlderThere(MediaSummary(audio = 2, bytes = 9_000), showsOnly = false), confirming.older)
            there.join()
            assertEquals(SendState.Cancelled, toOrbit030.last())
        } finally {
            older.stop()
        }
    }

    @Test
    fun `a map written for a newer format than the other device reads is said to only show there, when it opens here`() {
        val newer = bundle("---\nschemaVersion: 2\n---\n# Plan\n- Ask <!-- orbit audio: media/sound-1.mp3 -->\n", mapOf("sound-1.mp3" to picture(1_000)))
        val formatOne = DeviceReads(mapFormat = 1, mediaTypes = carriedMediaTypes())
        assertEquals(OlderThere(MediaSummary(), showsOnly = true), olderThere(newer, formatOne, readsHere = 2))
        assertEquals(OlderThere(MediaSummary(audio = 1, bytes = 1_000), showsOnly = true), olderThere(newer, DeviceReads.RELEASE_0_3_0, readsHere = 2))

        // A map this device can only show itself shows only there too, as the person sees here already.
        assertNull(olderThere(newer, formatOne, readsHere = 1))
        assertNull(olderThere(newer, DeviceReads(mapFormat = 2, mediaTypes = carriedMediaTypes()), readsHere = 2))
        assertNull(olderThere(bundle("# Plan\n"), formatOne))
        // One that can't be read here says nothing.
        assertNull(olderThere(File(files, "not-a-zip.zip").apply { writeText("# Plan\n") }, formatOne))
    }

    @Test
    fun `the code's dialog says what an older Orbit there won't open, in words for each case`() {
        val spanish = FileWords(Locale.forLanguageTag("es-MX"))
        val sounds = OlderThere(MediaSummary(audio = 2), showsOnly = false)
        val sound = OlderThere(MediaSummary(audio = 1), showsOnly = false)
        val showsOnly = OlderThere(MediaSummary(), showsOnly = true)
        val both = OlderThere(MediaSummary(audio = 1), showsOnly = true)

        assertEquals("It has an older Orbit, so 2 sounds stay on this device. Update Orbit there to send everything.", wontOpenThere(sounds, english))
        assertEquals("It has an older Orbit, so a sound stays on this device. Update Orbit there to send everything.", wontOpenThere(sound, english))
        assertEquals("It has an older Orbit, so it can only show this map. Update Orbit there to edit it there too.", wontOpenThere(showsOnly, english))
        assertEquals(
            "It has an older Orbit, so it can only show this map, and a sound stays on this device. Update Orbit there to send everything and edit it there.",
            wontOpenThere(both, english),
        )
        assertEquals("Tiene un Orbit más antiguo, así que 2 audios se quedan en este dispositivo. Actualiza Orbit allí para enviarlo todo.", wontOpenThere(sounds, spanish))
        assertEquals("Tiene un Orbit más antiguo, así que un audio se queda en este dispositivo. Actualiza Orbit allí para enviarlo todo.", wontOpenThere(sound, spanish))
        assertEquals("Tiene un Orbit más antiguo, así que solo puede mostrar este mapa. Actualiza Orbit allí para poder editarlo también allí.", wontOpenThere(showsOnly, spanish))
    }

    @Test
    fun `an offer from an older Orbit is refused before any of it is read, and the phone says so`() {
        assertEquals(0L, host.olderSender.value)
        // Orbit 0.2.0 sent the whole map with its first request: none of it is read.
        assertEquals(400, headOnly("/offer", 40_000_000, mapOf(VERSION_HEADER to "1", KEY_HEADER to hello().session.senderKey.text)))
        assertTrue(host.olderSender.value > 0)
        assertEquals(400, headOnly("/offer", 40_000_000))
        // Again and again, it's said once: another device can't keep the dialog up.
        val said = host.olderSender.value
        assertEquals(400, headOnly("/offer", 40_000_000, mapOf(VERSION_HEADER to "1")))
        assertEquals(said, host.olderSender.value)

        // A newer version is refused too, but this device is the one to update: it says nothing.
        val before = host.olderSender.value
        assertEquals(400, headOnly("/offer", 1_000, mapOf(VERSION_HEADER to "${OFFER_VERSION + 1}")))
        assertEquals(before, host.olderSender.value)

        val zip = bundle("# Plan\n- In plain sight\n").readBytes()
        val plain = request("POST", "/offer", mapOf("Content-Type" to "application/zip", "X-Orbit-From" to "Ana"), zip)
        assertEquals(400, plain.first)
        assertTrue(plain.second, plain.second.contains("Update Orbit on the other device"))
        assertEquals(400, request("POST", "/offer", mapOf(VERSION_HEADER to "$OFFER_VERSION", KEY_HEADER to "not a key"), hello().body).first)
        assertTrue(host.incoming.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `a hello changed on the way is refused, and leaves its place free`() {
        val hello = hello()
        val changed = hello.body.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        val (status, body) = request("POST", "/offer", hello.headers, changed)
        assertEquals(400, status)
        assertTrue(body, body.contains("didn't arrive intact"))
        assertEquals(200, greet(hello()).first)
    }

    @Test
    fun `the code shows only once the sender's half opens its commitment`() = runBlocking<Unit> {
        val hello = hello()
        val (status, answer) = greet(hello)
        assertEquals(200, status)
        assertTrue(host.incoming.value.isEmpty())
        val (revealed, body) = reveal(answer!!.id, hello.senderNonce)
        assertEquals(200, revealed)
        assertEquals("confirming", offerJson.decodeFromString(StateAnswer.serializer(), body).state)
        val shown = incoming()
        assertEquals(hello.session.checkCode(fromBase64Url(answer.nonce)!!, hello.senderNonce), shown.check)
        assertEquals("confirming", state(answer.id))
    }

    @Test
    fun `a half that isn't the one committed to drops the offer, which keeps its place until its time is up`() {
        val hello = hello()
        val (status, answer) = greet(hello)
        assertEquals(200, status)
        assertEquals(400, reveal(answer!!.id, randomBytes(CHECK_NONCE_BYTES)).first)
        assertEquals("declined", state(answer.id))
        assertTrue(host.incoming.value.isEmpty())
        assertEquals(404, reveal(answer.id, hello.senderNonce).first)
        // Otherwise a device could say hello after hello, keeping only one whose code matches what the sender shows.
        assertEquals(429, greet(hello()).first)
    }

    @Test
    fun `an offer holds its place from its hello on, so codes can't be tried one after another`() {
        assertEquals(200, greet(hello()).first)
        assertEquals(429, greet(hello()).first)
    }

    @Test
    fun `the same sealed hello sent again is refused`() {
        val hello = hello()
        val (status, answer) = greet(hello)
        assertEquals(200, status)
        assertEquals(200, reveal(answer!!.id, hello.senderNonce).first)
        val (again, body) = request("POST", "/offer", hello.headers, hello.body)
        assertEquals(400, again)
        assertTrue(body, body.contains("sent here already"))
    }

    @Test
    fun `a map is taken only from the device that said hello, while it waits for one`() = runBlocking<Unit> {
        // No offer by that id: refused before any of the map is read.
        assertEquals(404, headOnly("/offer/${UUID.randomUUID()}/map", 40_000_000))

        val (id, hello) = agreed()
        val map = sealedMap(hello, "# Plan\n")

        if (canBindFrom("127.0.0.2")) {
            assertEquals(404, request("POST", "/offer/$id/map", body = map, from = "127.0.0.2").first)
        }

        // A map sealed with the hello's key, not the map's, doesn't open: the offer goes with it.
        val zip = bundle("# Plan\n").readBytes()
        val wrongKey = ByteArrayOutputStream().also { hello.session.seal(zip.inputStream(), zip.size.toLong(), it) }.toByteArray()
        assertEquals(400, request("POST", "/offer/$id/map", body = wrongKey).first)
        assertEquals("declined", state(id))
        assertEquals(404, request("POST", "/offer/$id/map", body = map).first)
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `a map arrives in one upload at a time, and one there's no room for is refused before it's read`() = runBlocking<Unit> {
        val (id, hello) = agreed()
        // More than any disk has room for.
        assertEquals(507, headOnly("/offer/$id/map", 1L shl 50))

        Socket("127.0.0.1", host.port).use { slow ->
            slow.getOutputStream().write("POST /offer/$id/map HTTP/1.1\r\nHost: $address\r\nContent-Length: 1000000\r\n\r\nstarting".toByteArray())
            slow.getOutputStream().flush()
            // While one is on its way, another for the same offer is turned away. Only once the first is taken, or on a
            // busy machine the second can get there first, and wait for a body that never comes.
            assertTrue(eventually { host.incoming.value.single { it.id == id }.uploading })
            assertEquals(409, headOnly("/offer/$id/map", 1_000))
        }

        // Once it's gone, the map can come.
        assertTrue(eventually { request("POST", "/offer/$id/map", body = sealedMap(hello, "# Plan\n")).first == 200 })
        assertEquals("Plan", arrived().bundle.title)
        assertEquals("waiting", state(id))
    }

    @Test
    fun `nothing of the map or the name travels in plain sight`() {
        val hello = hello(from = "Ana's Pixel")
        val map = sealedMap(hello, "# Our secret plan\n- Buy the island\n")
        val wire = hello.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" } + String(hello.body, Charsets.ISO_8859_1) + String(map, Charsets.ISO_8859_1)
        assertFalse(wire.contains("secret"))
        assertFalse(wire.contains("island"))
        assertFalse(wire.contains("Ana"))
        assertFalse(wire.contains("map.md"))
    }

    @Test
    fun `a map nearly as big as the device takes arrives`() = runBlocking<Unit> {
        // A device that takes maps of up to 8 MiB stands in for one at its gigabyte: the map's zip and sealing alike.
        restart(mapLimit = 8L * 1024 * 1024)
        val photo = picture(8 * 1024 * 1024 - 200_000, seed = 9)
        val (_, states) = send(host.key.text, bundle("# Big\n", mapOf("big.png" to photo)))
        val offer = arrived()
        assertEquals(1, offer.bundle.media.pictures)
        assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
    }

    @Test
    fun `a map too big to send is refused before anything is sent`() = runBlocking<Unit> {
        val big = sparse(OFFER_LIMIT)
        val states = CopyOnWriteArrayList<SendState>()
        sendMap(address, host.key.text, OFFER_VERSION, "Ana's Pixel", big, { true }) { states += it }
        assertEquals(listOf(SendState.Failed(R.string.nearby_send_too_big)), states.toList())
        assertTrue(host.incoming.value.isEmpty())
    }

    @Test
    fun `an address typed with a port no device can have isn't taken, and sending to one fails rather than stopping the app`() = runBlocking<Unit> {
        assertEquals("192.168.1.5:8766", typedAddress(" 192.168.1.5:8766 "))
        assertEquals("tablet.local:65535", typedAddress("tablet.local:65535"))
        assertNull(typedAddress("192.168.1.5:65536"))
        assertNull(typedAddress("192.168.1.5:99999"))
        assertNull(typedAddress("192.168.1.5:00"))
        assertNull(typedAddress("192.168.1.5"))

        val states = CopyOnWriteArrayList<SendState>()
        sendMap("127.0.0.1:99999", null, null, "Ana's Pixel", bundle("# Plan\n"), { true }) { states += it }
        assertEquals(listOf(SendState.Sending, SendState.Failed(R.string.nearby_send_could_not_reach)), states.toList())
    }

    /** A shared map's zip as `browser` downloads it from the share at `code`: the answer's head, and the zip. */
    private fun download(browser: Browser, code: String): Pair<String, ByteArray> = Socket("127.0.0.1", host.port).use { socket ->
        val cookies = browser.cookieHeaders.joinToString("; ")
        socket.getOutputStream().write("GET /$code/map.zip HTTP/1.1\r\nHost: $address\r\nCookie: $cookies\r\n\r\n".toByteArray())
        val answer = socket.getInputStream().readBytes()
        val end = String(answer, Charsets.ISO_8859_1).indexOf("\r\n\r\n")
        String(answer, 0, end, Charsets.ISO_8859_1) to answer.copyOfRange(end + 4, answer.size)
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> = ZipInputStream(zip.inputStream()).use { input -> generateSequence { input.nextEntry }.associate { it.name to input.readBytes() } }

    private val sharedZips: List<String> get() = File(context.cacheDir, "shared-maps").list().orEmpty().toList()

    @Test
    fun `every browser downloading a shared map is sent one zip, made once for each version of the map, gone as the share ends`() = runBlocking<Unit> {
        val photo = TestMedia.picture(600, 400, noise = 1)
        val id = maps.add("# Trip\n\n- Lisbon\n\n  ![Tram](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photo))
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        val browsers = List(3) { Browser(host.port).also { browser -> assertEquals(303, browser.post("/$code/join", emptyMap()).status) } }

        val (head, zip) = download(browsers[0], code)
        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(head, "Content-Disposition: attachment; filename=\"Trip.zip\"" in head)
        assertArrayEquals(OutgoingMedia.picture(photo), entries(zip)["media/photo.jpg"])
        assertTrue(entries(zip).getValue("map.md").decodeToString().startsWith("# Trip"))

        // The next browsers, two at once, are sent the same zip: one file for the room, not one for each.
        val others = browsers.drop(1).map { browser -> async(Dispatchers.IO) { download(browser, code) } }.awaitAll()
        others.forEach { (_, theirs) -> assertArrayEquals(zip, theirs) }
        assertEquals(1, sharedZips.size)

        // A new version of the map makes a new zip; the one before stays for downloads still going.
        maps.write(id, "# Trip\n\n- Lisbon\n- Porto\n\n  ![Tram](media/photo.jpg)\n")
        assertTrue(entries(download(browsers[0], code).second).getValue("map.md").decodeToString().contains("Porto"))
        assertEquals(2, sharedZips.size)
        maps.write(id, "# Trip\n\n- Lisbon\n- Porto\n- Faro\n\n  ![Tram](media/photo.jpg)\n")
        download(browsers[0], code)
        assertEquals(2, sharedZips.size)

        // The share ended: its zips go.
        host.unshare(code)
        assertEquals(emptyList<String>(), sharedZips)
    }

    @Test
    fun `a shared map's zip is made only once there's room for all of it`() = runBlocking<Unit> {
        restart(room = { 0L })
        val id = maps.add("# Trip\n\n- Lisbon\n\n  ![Tram](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to TestMedia.picture(600, 400, noise = 1)))
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        val browser = Browser(host.port)
        assertEquals(303, browser.post("/$code/join", emptyMap()).status)

        val full = browser.get("/$code/map.zip")
        assertEquals(507, full.status)
        assertEquals("There isn't room on the phone to make the map's zip. Free up some space there, and download it again.", full.body)
        assertEquals(emptyList<String>(), sharedZips)
    }

    @Test
    fun `another site's page can't open a shared map for anyone, nor use up the tries at its code`() = runBlocking<Unit> {
        val id = maps.add("# Trip\n\n- Lisbon\n")
        val shared = host.share(id, AppTheme(dark = false, tokens = emptyMap()))
        val code = shared.code
        val crossSite = mapOf("Sec-Fetch-Site" to "cross-site")

        // The same answer at the share's code as at any other, and no one in.
        val page = Browser(host.port).get("/$code/", crossSite)
        assertEquals(200, page.status)
        assertNull(page.header("set-cookie"))
        assertTrue(page.body.contains("join__onward"))
        assertEquals(page.body, Browser(host.port).get("/000000/", crossSite).body)
        assertEquals(403, Browser(host.port).get("/$code/map.zip", crossSite).status)
        assertEquals(403, Browser(host.port).post("/$code/join", emptyMap(), crossSite).status)
        assertTrue(shared.joining.members.value.isEmpty())

        // Wrong codes from another site, past every limit, count for nothing.
        repeat(110) { index -> Browser(host.port).get("/${100_000 + index}/", crossSite) }
        assertEquals(0L, host.lockedOut.value)

        // The page's way on comes from the phone's own address: the room answers it as it answers anyone, here with its form.
        shared.joining.change(JoiningOptions(askNames = true))
        assertTrue(Browser(host.port).get("/$code/", mapOf("Sec-Fetch-Site" to "same-origin")).body.contains("Your name"))
        val browser = Browser(host.port)
        assertEquals(303, browser.post("/$code/join", mapOf("name" to "Ana"), mapOf("Sec-Fetch-Site" to "same-origin")).status)
        assertEquals(200, browser.get("/$code/map.zip", mapOf("Sec-Fetch-Site" to "same-origin")).status)
        assertEquals(listOf("Ana"), shared.joining.members.value.map { it.name })
    }

    @Test
    fun `a zip being made as Orbit stops listening stops, and leaves nothing behind`() = runBlocking<Unit> {
        // The room is looked at as the zip is about to be made: there, Orbit leaves the screen, and the server stops.
        val asked = CountDownLatch(1)
        val stopped = CountDownLatch(1)

        restart(
            room = {
                asked.countDown()
                stopped.await(10, TimeUnit.SECONDS)
                1L shl 40
            },
        )

        val id = maps.add("# Trip\n\n- Lisbon\n\n  ![Tram](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to TestMedia.picture(600, 400, noise = 1)))
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        val browser = Browser(host.port)
        assertEquals(303, browser.post("/$code/join", emptyMap()).status)
        val downloading = async(Dispatchers.IO) { runCatching { download(browser, code) } }

        assertTrue(asked.await(10, TimeUnit.SECONDS))
        host.stop()
        stopped.countDown()
        downloading.await()

        // Nothing goes on being made for a browser no one answers.
        Thread.sleep(1_000)
        assertEquals(emptyList<String>(), sharedZips)
    }

    @Test
    fun `a computer's page is made once for each version of the map, its files included, into a file of its own`() = runBlocking<Unit> {
        val id = maps.add("# Trip\n\n- Lisbon\n\n  ![Tram](media/tram.jpg)\n")
        val share = host.share(id, AppTheme(dark = false, tokens = emptyMap()))
        val folder = maps.folder(id)
        val pages = File(context.cacheDir, "shared-pages")
        var builds = 0

        suspend fun page(): SharedPage = share.page(pageVersion(maps.summary(id), folder)) { file ->
            builds += 1
            file.writeText("page $builds")
            listOf("script $builds")
        }

        val first = page()
        assertEquals("page 1", first.file.readText())
        assertEquals(listOf("script 1"), first.scripts)
        assertEquals(first, page())

        // The picture reaches the folder late, as from a sync app: the page is made again with it.
        File(folder, "media").apply { mkdirs() }.resolve("tram.jpg").writeBytes(picture(2_000))
        val second = page()
        assertEquals("page 2", second.file.readText())
        assertEquals(second, page())
        // The page before stays, for an answer already on its way with it.
        assertTrue(first.file.isFile)

        maps.write(id, "# Trip\n\n- Lisbon\n- Porto\n\n  ![Tram](media/tram.jpg)\n")
        val third = page()
        assertEquals("page 3", third.file.readText())
        assertEquals(3, builds)
        // Two versions on, it goes.
        assertFalse(first.file.exists())
        assertEquals(setOf(second.file, third.file), pages.listFiles()!!.toSet())

        // A page that couldn't be made leaves nothing behind, and the last one stays.
        maps.write(id, "# Trip\n\n- Lisbon\n- Porto\n- Sintra\n\n  ![Tram](media/tram.jpg)\n")
        assertTrue(runCatching { share.page(pageVersion(maps.summary(id), folder)) { file -> file.writeText("half a page"); throw IOException("A picture went") } }.isFailure)
        assertEquals(setOf(second.file, third.file), pages.listFiles()!!.toSet())

        // As the share ends, so do its pages; one made for an answer as it ended goes once it's sent.
        host.unshare(share.code)
        assertEquals(emptyList<File>(), pages.listFiles()!!.toList())
        assertTrue(page().temporary)
    }

    @Test
    fun `a private map shown to computers stops being served as it locks, and its share ends`() = runBlocking<Unit> {
        val id = maps.add("# Diary\n\n- Plans\n")
        val locked = AtomicBoolean(false)
        host.hidden = { mapId -> mapId == id && locked.get() }
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        val browser = Browser(host.port)
        assertEquals(303, browser.post("/$code/join", emptyMap()).status)
        assertEquals(200, browser.get("/$code/map.zip").status)

        locked.set(true)
        // The browser that was in gets nothing more, and the room says it ended (its pages need the app's assets).
        val zip = browser.get("/$code/map.zip")
        assertNotEquals(200, zip.status)
        assertFalse(zip.body.contains("Diary"))
        assertTrue(browser.get("/$code/status").body.contains("\"ended\""))
        // Unlocked again, the share stays ended: it's shown again only when asked to.
        locked.set(false)
        assertNotEquals(200, browser.get("/$code/map.zip").status)
        assertTrue(browser.get("/$code/status").body.contains("\"ended\""))
    }

    @Test
    fun `a private map that locks while its zip is made for a computer gives out none of it`() = runBlocking<Unit> {
        val id = maps.add("# Diary\n\n- Plans\n")
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        val browser = Browser(host.port)
        assertEquals(303, browser.post("/$code/join", emptyMap()).status)

        // Shown as the browser asks, locked by the time the zip is made, as a big map's takes a while.
        val looks = AtomicInteger(0)
        host.hidden = { mapId -> mapId == id && looks.incrementAndGet() > 1 }
        assertNotEquals(200, browser.get("/$code/map.zip").status)
        assertTrue(browser.get("/$code/status").body.contains("\"ended\""))
        // The zip made for it doesn't stay behind.
        assertTrue(eventually { File(context.cacheDir, "shared-maps").list().isNullOrEmpty() })
    }

    @Test
    fun `a private map that locked while Orbit was away isn't served again as it comes back`() = runBlocking<Unit> {
        val id = maps.add("# Diary\n\n- Plans\n")
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        host.hidden = { mapId -> mapId == id }
        // Orbit back on screen.
        host.start(accepting = true)

        assertTrue(Browser(host.port).get("/$code/status").body.contains("\"ended\""))
    }

    @Test
    fun `a device that isn't there, or speaks another version, says which device to update`() = runBlocking<Unit> {
        suspend fun sent(to: String, key: String? = null, version: Int? = null): SendState {
            val states = CopyOnWriteArrayList<SendState>()
            sendMap(to, key, version, "Ana's Pixel", bundle("# Plan\n"), { true }) { states += it }
            return states.last()
        }

        assertEquals(SendState.Failed(R.string.nearby_send_could_not_reach), sent("127.0.0.1:1"))
        // An advertisement's version settles it at once, without a word to the device.
        assertEquals(SendState.Failed(R.string.nearby_send_update_the_other_device), sent("127.0.0.1:1", host.key.text, version = 1))
        assertEquals(SendState.Failed(R.string.nearby_send_update_this_device), sent("127.0.0.1:1", host.key.text, version = OFFER_VERSION + 1))

        // An Orbit before sealing answers every address it doesn't know with its page for codes; Orbit 0.2.0 has a key of version 1.
        val versions = mapOf(
            "old" to Response.text(200, "<html>The code on the other device</html>", "text/html"),
            "1" to Response.json(200, offerJson.encodeToString(KeyAnswer.serializer(), KeyAnswer(1, host.key.text))),
            "next" to Response.json(200, offerJson.encodeToString(KeyAnswer.serializer(), KeyAnswer(OFFER_VERSION + 1, host.key.text))),
        )

        for ((name, keyAnswer) in versions) {
            val other = LocalServer({ _, _ -> 1_000 }, policy = anyone) { keyAnswer }
            other.start(0)

            try {
                val expected = if (name == "next") R.string.nearby_send_update_this_device else R.string.nearby_send_update_the_other_device
                assertEquals(name, SendState.Failed(expected), sent("127.0.0.1:${other.port}"))
                // An advertisement with a key but no version, as Orbit 0.2.0's, is asked for its version.
                assertEquals(name, SendState.Failed(expected), sent("127.0.0.1:${other.port}", host.key.text))
            } finally {
                other.stop()
            }
        }
    }

    @Test
    fun `a sender believes only the device that showed its code about whether the map opened`() = runBlocking<Unit> {
        val forged = AtomicInteger(0)

        // A device in between passes everything on, but says a map waiting for an answer was opened, its proof kept.
        val relay = between { request, answer ->
            if (request.method == "GET" && request.path.startsWith("/offer/") && answer.body.contains("\"waiting\"")) {
                forged.incrementAndGet()
                answer.json { JsonObject(it + ("state" to JsonPrimitive("opened"))) }
            } else {
                null
            }
        }

        relay.start(0)

        try {
            val (_, states) = send(host.key.text, bundle("# Plan\n"), to = "127.0.0.1:${relay.port}")
            val offer = arrived()
            assertTrue(eventually { forged.get() >= 2 })
            assertFalse(states.toString(), SendState.Opened in states)

            // The device itself says it declined: that's believed.
            host.decline(offer)
            assertEquals(SendState.Declined, waitFor(states) { it == SendState.Declined || it == SendState.Opened })
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `a sender says the map was declined only when the device itself says so`() = runBlocking<Unit> {
        // A device in between turns the map away in the device's place, which never sees it.
        val relay = between(instead = { request -> if (request.path.endsWith("/map")) Response.notFound() else null })
        relay.start(0)

        try {
            val (_, states) = send(host.key.text, bundle("# Plan\n"), to = "127.0.0.1:${relay.port}")
            assertEquals(SendState.Failed(R.string.nearby_send_could_not_reach), waitFor(states) { it is SendState.Failed || it == SendState.Declined })
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `the device taking a map proves what it says of the offer, and its half of the code says so`() = runBlocking<Unit> {
        val hello = hello()
        val (_, offered) = greet(hello)
        val id = offered!!.id

        // Its half of the check code says it proves its answers; Orbit 0.3.0's and 0.4.0's, all random, don't.
        assertTrue(provesStates(fromBase64Url(offered.nonce)!!))
        assertFalse(provesStates(randomBytes(CHECK_NONCE_BYTES)))

        assertEquals(200, reveal(id, hello.senderNonce).first)
        val answer = offerJson.decodeFromString(StateAnswer.serializer(), request("GET", "/offer/$id").second)
        assertEquals("confirming", answer.state)
        assertTrue(hello.session.isProof(id, "confirming", fromBase64Url(answer.proof)!!))
        assertFalse(hello.session.isProof(id, "opened", fromBase64Url(answer.proof)!!))

        // An Orbit that doesn't know the proof reads the answer as it always has.
        val older = Json { ignoreUnknownKeys = true }.decodeFromString(StateAnswer030.serializer(), request("GET", "/offer/$id").second)
        assertEquals("confirming", older.state)
    }

    @Test
    fun `no one in between can take the mark off the device's half of the code without the codes differing`() = runBlocking<Unit> {
        val relay = between { request, answer ->
            if (request.path == "/offer") {
                // Its half, as Orbit 0.4.0 would send it: all random.
                answer.json { JsonObject(it + ("nonce" to JsonPrimitive(base64Url(randomBytes(CHECK_NONCE_BYTES))))) }
            } else {
                null
            }
        }

        relay.start(0)

        try {
            val (_, states) = send(host.key.text, bundle("# Plan\n"), to = "127.0.0.1:${relay.port}", checker = Checker { false })
            val coming = incoming()
            val shown = waitFor(states) { it is SendState.Confirming } as SendState.Confirming
            assertNotEquals(coming.check, shown.check)
            waitFor(states) { it == SendState.Cancelled }
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `an older Orbit's word about the offer is taken as it is`() = runBlocking<Unit> {
        val older = orbit030()
        older.start(0)

        try {
            val (_, states) = send(host.key.text, bundle("# Plan\n- From an older Orbit\n"), to = "127.0.0.1:${older.port}")
            val offer = arrived()
            host.accept(offer, keepBoth = false)
            assertEquals(SendState.Opened, waitFor(states) { it == SendState.Opened || it == SendState.Declined })
        } finally {
            older.stop()
        }
    }

    @Test
    fun `a map cut short once its offer's time is up ends the offer, and can't start coming again`() = runBlocking<Unit> {
        restart(times = OfferTimes(confirming = 1_500))
        val (id, hello) = agreed()

        Socket("127.0.0.1", host.port).use { slow ->
            slow.getOutputStream().write("POST /offer/$id/map HTTP/1.1\r\nHost: $address\r\nContent-Length: 1000000\r\n\r\nstarting".toByteArray())
            slow.getOutputStream().flush()
            assertTrue(eventually { host.incoming.value.singleOrNull { it.id == id }?.uploading == true })

            // Its time is up while the map is on its way: it may still finish.
            Thread.sleep(2_500)
            assertEquals("confirming", state(id))
        }

        // Cut short: the offer is over, and its place free.
        assertTrue(eventually { host.incoming.value.isEmpty() })
        assertEquals("declined", state(id))
        assertEquals(404, request("POST", "/offer/$id/map", body = sealedMap(hello, "# Plan\n")).first)
        assertEquals(200, greet(hello()).first)
    }

    @Test
    fun `a map still coming as its offer's time is up arrives`() = runBlocking<Unit> {
        restart(times = OfferTimes(confirming = 1_500))
        val (id, hello) = agreed()
        val map = sealedMap(hello, "# Plan\n- Slow\n")

        Socket("127.0.0.1", host.port).use { slow ->
            slow.soTimeout = 15_000
            val output = slow.getOutputStream()
            output.write("POST /offer/$id/map HTTP/1.1\r\nHost: $address\r\nContent-Length: ${map.size}\r\n\r\n".toByteArray())
            output.write(map, 0, 10)
            output.flush()
            assertTrue(eventually { host.incoming.value.singleOrNull { it.id == id }?.uploading == true })
            Thread.sleep(2_500)
            output.write(map, 10, map.size - 10)
            output.flush()
            val status = slow.getInputStream().bufferedReader().readLine()
            assertTrue(status, status.startsWith("HTTP/1.1 200"))
        }

        assertEquals("Plan", arrived().bundle.title)
        assertEquals("waiting", state(id))
    }

    @Test
    fun `a map is taken only with room for it as it opens, besides the maps on their way`() = runBlocking<Unit> {
        assumeTrue("Needs another loopback address to send from", canBindFrom("127.0.0.2"))
        val left = AtomicLong(Long.MAX_VALUE)
        restart(room = { left.get() })
        val (first, firstHello) = agreed()
        val one = sealedMap(firstHello, "# One\n")

        // Room for half the map: refused before any of it is read.
        left.set(SPARE_SPACE + one.size / 2L)
        assertEquals(507, headOnly("/offer/$first/map", one.size.toLong()))

        // Room for one map as it opens, not two: while one is on its way, another device's has to wait.
        left.set(SPARE_SPACE + one.size * 3L / 2)
        val (second, secondHello) = agreed(from = "127.0.0.2")
        val two = sealedMap(secondHello, "# Two\n")

        Socket("127.0.0.1", host.port).use { slow ->
            slow.getOutputStream().write("POST /offer/$first/map HTTP/1.1\r\nHost: $address\r\nContent-Length: ${one.size}\r\n\r\n".toByteArray())
            slow.getOutputStream().write(one, 0, 10)
            slow.getOutputStream().flush()
            assertTrue(eventually { host.incoming.value.single { it.id == first }.uploading })
            assertEquals(507, headOnly("/offer/$second/map", two.size.toLong(), from = "127.0.0.2"))
        }

        // Once the first stopped coming, the second has room.
        assertTrue(eventually { !host.incoming.value.single { it.id == first }.uploading })
        assertEquals(200, request("POST", "/offer/$second/map", body = two, from = "127.0.0.2").first)
        assertEquals("Two", arrived().bundle.title)
    }

    @Test
    fun `a map the storage runs out of room for as it opens leaves nothing behind, and ends its offer`() = runBlocking<Unit> {
        // Plenty of room as the map is let in, none by the time it opens: another app filled the storage meanwhile.
        val looks = AtomicInteger(0)
        restart(room = { if (looks.getAndIncrement() == 0) 1L shl 40 else 0L })
        val (id, hello) = agreed()

        assertEquals(507, request("POST", "/offer/$id/map", body = sealedMap(hello, "# Plan\n")).first)
        assertEquals("declined", state(id))
        assertTrue(host.incoming.value.isEmpty())
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
        assertEquals(200, greet(hello()).first)
    }

    @Test
    fun `offers answered long ago, or past the newest kept, are forgotten`() = runBlocking<Unit> {
        val now = AtomicLong(1_000_000)
        restart(times = OfferTimes(settled = 60_000), clock = { now.get() })

        // A device offering again and again, and cancelling each time.
        val ids = (1..SETTLED_OFFERS_KEPT + 4).map {
            val (status, answer) = greet(hello())
            assertEquals(200, status)
            assertEquals(200, request("POST", "/offer/${answer!!.id}/cancel").first)
            answer.id
        }

        ids.take(4).forEach { id -> assertEquals(404, request("GET", "/offer/$id").first) }
        assertEquals("declined", state(ids[4]))
        assertEquals("declined", state(ids.last()))

        // Past the time this host says answers for, a minute here, the sender has long heard: even the newest are forgotten.
        now.addAndGet(60_001)
        assertEquals(404, request("GET", "/offer/${ids.last()}").first)
    }

    @Test
    fun `an offer going on is never forgotten for answered ones`() = runBlocking<Unit> {
        assumeTrue("Needs another loopback address to send from", canBindFrom("127.0.0.2"))
        val now = AtomicLong(1_000_000)
        restart(times = OfferTimes(settled = 60_000), clock = { now.get() })
        val (id, _) = agreed()

        repeat(SETTLED_OFFERS_KEPT + 4) {
            val (_, answer) = greet(hello(), from = "127.0.0.2")
            assertEquals(200, request("POST", "/offer/${answer!!.id}/cancel", from = "127.0.0.2").first)
        }

        now.addAndGet(10 * 60_000)
        assertEquals("confirming", state(id))
    }

    private companion object {
        fun canBindFrom(address: String): Boolean = runCatching { Socket().use { it.bind(InetSocketAddress(address, 0)) } }.isSuccess

        /** Waits up to five seconds for `condition`. */
        fun eventually(condition: () -> Boolean): Boolean {
            val until = System.nanoTime() + 30_000_000_000L

            while (System.nanoTime() < until) {
                if (runCatching(condition).getOrDefault(false)) {
                    return true
                }

                Thread.sleep(20)
            }

            return false
        }
    }

    @Test
    fun `an ideas files go to a device that keeps them, and stay here, named as files, for one from before them`() {
        val map = bundle(
            "# Trip\n## Tickets\n<!-- orbit\nfiles:\n  - src: media/file-pass.pdf\n    name: Pass.pdf\n  - src: media/file-scan.png\n-->\n",
            mapOf("file-pass.pdf" to picture(1_000), "file-scan.png" to picture(2_000, seed = 2)),
        )

        // This version says it keeps ideas' files of every kind: they all open there.
        assertNull(olderThere(map, DeviceReads.of(1, carriedMediaTypes().toList(), files = true)))
        // Orbit 0.4.0 says nothing of them, and keeps only its own kinds: the PDF stays here, counted as a file; the PNG goes, a kind it keeps.
        assertEquals(OlderThere(MediaSummary(files = 1, bytes = 1_000), showsOnly = false), olderThere(map, DeviceReads.of(1, carriedMediaTypes().toList())))
    }
}

/** What Orbit 0.3.0 and 0.4.0 read of an answer about an offer. */
@Serializable
private class StateAnswer030(val state: String)
