package com.hyuchia.orbit.nearby

import android.content.ContextWrapper
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.page.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.Deflater
import java.util.zip.ZipEntry
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
        host = NearbyHost(context, maps, scope, anyone, english)
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

    /** Sends in the background, keeping every state the sender passes through. */
    private fun CoroutineScope.send(key: String?, bundle: File, from: String = "Ana's Pixel", to: String = address): CopyOnWriteArrayList<SendState> {
        val states = CopyOnWriteArrayList<SendState>()
        launch(Dispatchers.IO) { sendMap(to, key, from, bundle) { states += it } }
        return states
    }

    private suspend fun waitFor(states: List<SendState>, predicate: (SendState) -> Boolean): SendState = withTimeout(15_000) {
        while (states.none(predicate)) {
            delay(20)
        }

        states.first(predicate)
    }

    private suspend fun arrived(): Offer = withTimeout(15_000) { host.offers.first { it.isNotEmpty() }.single() }

    /** One request over a plain socket, as another device would make it. */
    private fun request(method: String, path: String, headers: Map<String, String> = emptyMap(), body: ByteArray = ByteArray(0)): Pair<Int, String> =
        Socket("127.0.0.1", host.port).use { socket ->
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

    /** A map sealed to the host as a sender seals it: its session, its half of the check code, and the request. */
    private class Sealed(val session: OfferSession, val senderNonce: ByteArray, val headers: Map<String, String>, val body: ByteArray)

    private fun sealed(markdown: String, from: String = "Ana's Pixel"): Sealed {
        val session = OfferSession.forSender(host.key)
        val senderNonce = randomBytes(CHECK_NONCE_BYTES)
        val payload = OfferHead(from, session.commitment(senderNonce)).bytes() + bundle(markdown).readBytes()
        val body = ByteArrayOutputStream().also { session.seal(payload.inputStream(), payload.size.toLong(), it) }.toByteArray()
        return Sealed(session, senderNonce, mapOf(VERSION_HEADER to "$OFFER_VERSION", KEY_HEADER to session.senderKey.text), body)
    }

    private fun offer(sealed: Sealed): Pair<Int, OfferAnswer?> {
        val (status, body) = request("POST", "/offer", sealed.headers, sealed.body)
        return status to if (status == 200) offerJson.decodeFromString(OfferAnswer.serializer(), body) else null
    }

    private fun reveal(id: String, nonce: ByteArray): Pair<Int, String> =
        request("POST", "/offer/$id", body = offerJson.encodeToString(Reveal.serializer(), Reveal(base64Url(nonce))).toByteArray())

    @Test
    fun `a map sent to a typed address arrives whole, and both devices show the same check code`() = runBlocking<Unit> {
        val photo = picture(1_500_000)
        val states = send(key = null, bundle = bundle("# Trip\n- Day one\n  - Lisbon\n", mapOf("photo.jpg" to photo)))

        val offer = arrived()
        assertEquals("Ana's Pixel", offer.from)
        assertEquals("Trip", offer.bundle.title)
        assertEquals(1, offer.bundle.pictureCount)
        assertEquals("127.0.0.1", offer.address)
        val waiting = waitFor(states) { it is SendState.Waiting } as SendState.Waiting
        assertTrue(offer.check, Regex("[0-9]{4}").matches(offer.check))
        assertEquals(offer.check, waiting.check)
        assertEquals(listOf(SendState.Sending, waiting), states.toList())

        val id = host.accept(offer, keepBoth = false)!!
        assertEquals(SendState.Opened, waitFor(states) { it == SendState.Opened })
        assertTrue(maps.read(id).contains("Lisbon"))
        assertArrayEquals(photo, File(maps.folder(id), "media/photo.jpg").readBytes())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `a device found nearby is sent to with the key it advertises, and can say no`() = runBlocking<Unit> {
        val advertised = serviceAttributes(host.selfId, host.key).mapValues { it.value.toByteArray() }
        val device = nearbyDevice("Ana's tablet", advertised, "127.0.0.1", host.port, selfId = "another launch")!!
        val states = send(device.key, bundle("# Plan\n- Ask\n"), to = device.address)

        val offer = arrived()
        assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
        host.decline(offer)
        assertEquals(SendState.Declined, waitFor(states) { it == SendState.Declined })
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `maps arrive with the name chosen for the device, cleaned to show`() = runBlocking<Unit> {
        // A right-to-left override would show the name backwards.
        host.rename("  Ana's\u202E tablet ")
        assertEquals("Ana's tablet", host.name)
        assertEquals("Ana's tablet", host.nameFor("Ana's tablet"))
        val states = send(host.key.text, bundle("# Plan\n"), from = host.name)
        val offer = arrived()
        assertEquals("Ana's tablet", offer.from)
        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
    }

    @Test
    fun `check codes differ from one send to the next`() = runBlocking<Unit> {
        val codes = mutableListOf<String>()

        repeat(4) {
            val states = send(host.key.text, bundle("# Plan $it\n"))
            val offer = arrived()
            assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
            codes += offer.check
            host.decline(offer)
            waitFor(states) { it == SendState.Declined }
        }

        assertTrue(codes.toString(), codes.toSet().size >= 3)
    }

    @Test
    fun `an advertised key from before the device started again gives way to the one it has now`() = runBlocking<Unit> {
        val earlier = DeviceKey.of(newKeyPair()).text
        val states = send(earlier, bundle("# Plan\n- Still arrives\n"))
        val offer = arrived()
        assertEquals("Plan", offer.bundle.title)
        assertEquals(offer.check, (waitFor(states) { it is SendState.Waiting } as SendState.Waiting).check)
        host.decline(offer)
        waitFor(states) { it == SendState.Declined }
    }

    @Test
    fun `the key is there to ask for while the device takes maps, and the advertisement has the same one`() {
        val (status, body) = request("GET", "/key")
        assertEquals(200, status)
        val answer = offerJson.decodeFromString(KeyAnswer.serializer(), body)
        assertEquals(OFFER_VERSION, answer.version)
        assertEquals(host.key.text, answer.key)
        assertEquals(serviceAttributes(host.selfId, host.key)[SERVICE_KEY], answer.key)
        assertNotNull(DeviceKey.read(answer.key))

        // Showing a map to a computer keeps it listening, without taking maps.
        host.share("map", AppTheme(dark = false, tokens = emptyMap()))
        host.start(accepting = false)
        assertEquals(403, request("GET", "/key").first)
        val sealed = sealed("# Plan\n")
        assertEquals(403, request("POST", "/offer", sealed.headers, sealed.body).first)
    }

    @Test
    fun `an offer that isn't sealed is refused`() {
        val zip = bundle("# Plan\n- In plain sight\n").readBytes()
        val plain = request("POST", "/offer", mapOf("Content-Type" to "application/zip", "X-Orbit-From" to "Ana"), zip)
        assertEquals(400, plain.first)
        assertTrue(plain.second, plain.second.contains("Update Orbit on the other device"))

        val sealed = sealed("# Plan\n")
        val versions = listOf(mapOf(VERSION_HEADER to "2", KEY_HEADER to sealed.session.senderKey.text), mapOf(KEY_HEADER to sealed.session.senderKey.text))

        for (headers in versions) {
            assertEquals(400, request("POST", "/offer", headers, sealed.body).first)
        }

        assertEquals(400, request("POST", "/offer", mapOf(VERSION_HEADER to "1", KEY_HEADER to "not a key"), sealed.body).first)
        assertTrue(host.offers.value.isEmpty())
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
    }

    @Test
    fun `a sealed offer changed on the way is refused, and leaves nothing behind`() {
        val sealed = sealed("# Plan\n")
        val changed = sealed.body.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        val (status, body) = request("POST", "/offer", sealed.headers, changed)
        assertEquals(400, status)
        assertTrue(body, body.contains("didn't arrive intact"))
        assertEquals(emptyList<String>(), offersFolder.list().orEmpty().toList())
        // Its place under the limits is free again.
        assertEquals(200, offer(sealed("# Another\n")).first)
    }

    @Test
    fun `an offer shows only once the sender's half of the code opens its commitment`() = runBlocking<Unit> {
        val sealed = sealed("# Plan\n")
        val (status, answer) = offer(sealed)
        assertEquals(200, status)
        assertTrue(host.offers.value.isEmpty())
        assertEquals(200, reveal(answer!!.id, sealed.senderNonce).first)
        val shown = arrived()
        assertEquals("Plan", shown.bundle.title)
        assertEquals(sealed.session.checkCode(fromBase64Url(answer.nonce)!!, sealed.senderNonce), shown.check)
    }

    @Test
    fun `a half that isn't the one committed to drops the offer, which keeps its place until its time is up`() {
        val sealed = sealed("# Plan\n")
        val (status, answer) = offer(sealed)
        assertEquals(200, status)
        assertEquals(400, reveal(answer!!.id, randomBytes(CHECK_NONCE_BYTES)).first)
        assertEquals("declined", offerJson.decodeFromString(StateAnswer.serializer(), request("GET", "/offer/${answer.id}").second).state)
        assertTrue(host.offers.value.isEmpty())
        assertEquals(404, reveal(answer.id, sealed.senderNonce).first)
        // Otherwise a device could send offer after offer, keeping only one whose code matches what the sender shows.
        assertEquals(429, offer(sealed("# Plan, again\n")).first)
    }

    @Test
    fun `an offer waiting for its check code holds its place, so codes can't be tried one after another`() {
        val (status, _) = offer(sealed("# One\n"))
        assertEquals(200, status)
        assertEquals(429, offer(sealed("# Two\n")).first)
    }

    @Test
    fun `the same sealed offer sent again is refused`() {
        val sealed = sealed("# Plan\n")
        val (status, answer) = offer(sealed)
        assertEquals(200, status)
        assertEquals(200, reveal(answer!!.id, sealed.senderNonce).first)
        val (again, body) = request("POST", "/offer", sealed.headers, sealed.body)
        assertEquals(400, again)
        assertTrue(body, body.contains("sent here already"))
    }

    @Test
    fun `nothing of the map or the name travels in plain sight`() {
        val sealed = sealed("# Our secret plan\n- Buy the island\n", from = "Ana's Pixel")
        val wire = sealed.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" } + String(sealed.body, Charsets.ISO_8859_1)
        assertFalse(wire.contains("secret"))
        assertFalse(wire.contains("island"))
        assertFalse(wire.contains("Ana"))
        assertFalse(wire.contains("map.md"))
    }

    @Test
    fun `a map nearly as big as the limit arrives`() = runBlocking<Unit> {
        val photo = picture(OFFER_LIMIT.toInt() - 200_000, seed = 9)
        val states = send(host.key.text, bundle("# Big\n", mapOf("big.png" to photo)))
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
        sendMap(address, host.key.text, "Ana's Pixel", big) { states += it }
        assertEquals(listOf(SendState.Failed(R.string.nearby_send_too_big)), states.toList())
        assertTrue(host.offers.value.isEmpty())
    }

    @Test
    fun `a device that isn't there, or is too old to seal maps, says so`() = runBlocking<Unit> {
        val nowhere = CopyOnWriteArrayList<SendState>()
        sendMap("127.0.0.1:1", null, "Ana's Pixel", bundle("# Plan\n")) { nowhere += it }
        assertEquals(SendState.Failed(R.string.nearby_send_could_not_reach), nowhere.last())

        // An older Orbit answers every address it doesn't know with its page for codes.
        val older = LocalServer({ _, _ -> 1_000 }, policy = anyone) { Response.text(200, "<html>The code on the other device</html>", "text/html") }
        older.start(0)

        try {
            val states = CopyOnWriteArrayList<SendState>()
            sendMap("127.0.0.1:${older.port}", null, "Ana's Pixel", bundle("# Plan\n")) { states += it }
            assertEquals(SendState.Failed(R.string.nearby_send_update_the_other_device), states.last())
        } finally {
            older.stop()
        }
    }
}
