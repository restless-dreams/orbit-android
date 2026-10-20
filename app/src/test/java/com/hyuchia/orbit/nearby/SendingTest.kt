package com.hyuchia.orbit.nearby

import android.content.ContextWrapper
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.page.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
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

        override fun welcome() = "# Welcome\n"
    }

    private val maps = MapRepository(File(files, "maps"), File(files, "trash"), starters, english)
    private lateinit var host: NearbyHost

    private val address: String get() = "127.0.0.1:${host.port}"
    private val offersFolder: File get() = File(context.cacheDir, "offers")

    @Before
    fun start() = runBlocking<Unit> {
        maps.load()
        host = NearbyHost(context, maps, scope, anyone, english) { null }
        // It clears what an earlier launch left in its folder as it starts: that's done before anything arrives.
        scope.coroutineContext.job.children.toList().joinAll()
        host.start(accepting = true)
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
    private fun headOnly(path: String, length: Long, headers: Map<String, String> = emptyMap()): Int = Socket("127.0.0.1", host.port).use { socket ->
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

    private fun greet(hello: Hello): Pair<Int, OfferAnswer?> {
        val (status, body) = request("POST", "/offer", hello.headers, hello.body)
        return status to if (status == 200) offerJson.decodeFromString(OfferAnswer.serializer(), body) else null
    }

    private fun reveal(id: String, nonce: ByteArray): Pair<Int, String> =
        request("POST", "/offer/$id", body = offerJson.encodeToString(Reveal.serializer(), Reveal(base64Url(nonce))).toByteArray())

    /** A hello revealed, so both devices show the code: its id, and what the sender has. */
    private fun agreed(): Pair<String, Hello> {
        val hello = hello()
        val (_, answer) = greet(hello)
        assertEquals(200, reveal(answer!!.id, hello.senderNonce).first)
        return answer.id to hello
    }

    private fun sealedMap(hello: Hello, markdown: String): ByteArray {
        val zip = bundle(markdown).readBytes()
        return ByteArrayOutputStream().also { hello.session.sealMap(zip.inputStream(), zip.size.toLong(), it) }.toByteArray()
    }

    private fun state(id: String): String = offerJson.decodeFromString(StateAnswer.serializer(), request("GET", "/offer/$id").second).state

    @Test
    fun `a map sent to a typed address arrives whole, once both devices showed the same code`() = runBlocking<Unit> {
        val photo = picture(1_500_000)
        val checker = Checker()
        val (_, states) = send(key = null, bundle = bundle("# Trip\n- Day one\n  - Lisbon\n", mapOf("photo.jpg" to photo)), version = null, checker = checker)

        val offer = arrived()
        assertEquals("Ana's Pixel", offer.from)
        assertEquals("Trip", offer.bundle.title)
        assertEquals(1, offer.bundle.pictureCount)
        assertEquals("127.0.0.1", offer.address)
        val waiting = waitFor(states) { it is SendState.Waiting } as SendState.Waiting
        assertTrue(offer.check, Regex("[0-9]{4}").matches(offer.check))
        assertEquals(offer.check, waiting.check)
        assertEquals(listOf(offer.check), checker.shown.toList())
        assertEquals(listOf(SendState.Sending, SendState.Confirming(offer.check), waiting), states.toList())

        val id = host.accept(offer, keepBoth = false)!!
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
    fun `an offer from an older Orbit is refused before any of it is read, and the phone says so`() {
        assertEquals(0L, host.olderSender.value)
        // Orbit 0.2.0 sent the whole map with its first request: none of it is read.
        assertEquals(400, headOnly("/offer", 40_000_000, mapOf(VERSION_HEADER to "1", KEY_HEADER to hello().session.senderKey.text)))
        assertTrue(host.olderSender.value > 0)
        assertEquals(400, headOnly("/offer", 40_000_000))

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
    fun `a map nearly as big as the limit arrives`() = runBlocking<Unit> {
        val photo = picture(OFFER_LIMIT.toInt() - 200_000, seed = 9)
        val (_, states) = send(host.key.text, bundle("# Big\n", mapOf("big.png" to photo)))
        val offer = arrived()
        assertEquals(1, offer.bundle.pictureCount)
        assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
    }

    @Test
    fun `a map too big to send is refused before anything is sent`() = runBlocking<Unit> {
        val big = File(files, "big.zip")
        RandomAccessFile(big, "rw").use { it.setLength(OFFER_LIMIT) }
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

    @Test
    fun `a shared map downloads from a file made for each browser, gone once it's sent`() = runBlocking<Unit> {
        val photo = picture(300_000)
        val id = maps.add("# Trip\n\n- Lisbon\n\n  ![Tram](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photo))
        val code = host.share(id, AppTheme(dark = false, tokens = emptyMap())).code
        val browser = Browser(host.port)
        assertEquals(303, browser.post("/$code/join", emptyMap()).status)

        val (head, zip) = Socket("127.0.0.1", host.port).use { socket ->
            val cookies = browser.cookieHeaders.joinToString("; ")
            socket.getOutputStream().write("GET /$code/map.zip HTTP/1.1\r\nHost: $address\r\nCookie: $cookies\r\n\r\n".toByteArray())
            val answer = socket.getInputStream().readBytes()
            val end = String(answer, Charsets.ISO_8859_1).indexOf("\r\n\r\n")
            String(answer, 0, end, Charsets.ISO_8859_1) to answer.copyOfRange(end + 4, answer.size)
        }

        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(head, "Content-Disposition: attachment; filename=\"Trip.zip\"" in head)
        val entries = ZipInputStream(zip.inputStream()).use { input -> generateSequence { input.nextEntry }.associate { it.name to input.readBytes() } }
        assertArrayEquals(photo, entries["media/photo.jpg"])
        assertTrue(entries.getValue("map.md").decodeToString().startsWith("# Trip"))
        assertTrue(eventually { File(context.cacheDir, "shared-maps").list().isNullOrEmpty() })
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

    private companion object {
        fun canBindFrom(address: String): Boolean = runCatching { Socket().use { it.bind(InetSocketAddress(address, 0)) } }.isSuccess

        /** Waits up to five seconds for `condition`. */
        fun eventually(condition: () -> Boolean): Boolean {
            val until = System.nanoTime() + 5_000_000_000L

            while (System.nanoTime() < until) {
                if (runCatching(condition).getOrDefault(false)) {
                    return true
                }

                Thread.sleep(20)
            }

            return false
        }
    }
}
