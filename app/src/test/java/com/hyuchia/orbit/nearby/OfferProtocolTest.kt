package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.data.carriedMediaTypes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.UUID

/** What Orbit 0.3.0 reads of the answer to `GET /key` (OfferProtocol.kt at 4db0723). */
@Serializable
private class KeyAnswerIn030(val version: Int, val key: String)

/** What Orbit 0.3.0 reads of the answer to a hello. */
@Serializable
private class OfferAnswerIn030(val id: String, val nonce: String)

class OfferProtocolTest {
    private val key = DeviceKey.of(newKeyPair())
    private val selfId = UUID.randomUUID().toString()

    @Test
    fun `the advertisement's key and version fit its TXT record, and read back`() {
        val attributes = serviceAttributes(selfId, key)
        assertEquals(setOf(SERVICE_ID, SERVICE_KEY, SERVICE_VERSION), attributes.keys)
        // Android refuses an attribute of 255 bytes or more, and warns of truncation past 400 bytes in all.
        attributes.forEach { (name, value) -> assertTrue("$name is ${name.length + value.length} bytes", name.length + value.toByteArray().size < 255) }
        val record = attributes.entries.sumOf { (name, value) -> 1 + name.length + 1 + value.toByteArray().size }
        assertTrue("$record bytes", record <= 400)

        val advertised = attributes.mapValues { it.value.toByteArray() }
        val device = nearbyDevice("Ana's tablet", advertised, "192.168.1.20", 8766, selfId = UUID.randomUUID().toString())!!
        assertEquals(NearbyDevice("Ana's tablet", "192.168.1.20:8766", key.text, OFFER_VERSION), device)
        assertArrayEquals(key.key.encoded, DeviceKey.read(device.key)!!.key.encoded)
    }

    @Test
    fun `a device doesn't list itself, and one from an older Orbit has no key or no version`() {
        val advertised = serviceAttributes(selfId, key).mapValues { it.value.toByteArray() }
        assertNull(nearbyDevice("This phone", advertised, "192.168.1.5", 8766, selfId))
        assertNull(nearbyDevice("Ana's tablet", advertised, null, 8766, UUID.randomUUID().toString()))
        val oldest = nearbyDevice("Old tablet", mapOf(SERVICE_ID to "other".toByteArray()), "192.168.1.21", 8766, selfId)!!
        assertNull(oldest.key)
        assertNull(oldest.version)
        // Orbit 0.2.0 advertised a key, and no version.
        val older = nearbyDevice("Old phone", mapOf(SERVICE_ID to "other".toByteArray(), SERVICE_KEY to key.text.toByteArray()), "192.168.1.22", 8766, selfId)!!
        assertEquals(key.text, older.key)
        assertNull(older.version)
    }

    @Test
    fun `a device shows the name it advertises without what hides or reorders text, or its address when nothing's left`() {
        val advertised = mapOf(SERVICE_ID to "other".toByteArray())
        // A right-to-left override would show "Ana's tablet" with its last letters reversed, passing for another device.
        assertEquals("Ana's elbat", nearbyDevice("Ana's ‮elbat", advertised, "192.168.1.20", 8766, selfId)!!.name)
        assertEquals("Ana's tablet", nearbyDevice("Ana's​ tablet\n\n", advertised, "192.168.1.20", 8766, selfId)!!.name)
        assertEquals("192.168.1.20:8766", nearbyDevice("​‮", advertised, "192.168.1.20", 8766, selfId)!!.name)
    }

    @Test
    fun `an offer's hello carries the name and the commitment, and reads no further`() {
        val commitment = randomBytes(COMMITMENT_BYTES)
        val head = OfferHead("Téléphone de Zoë 📱", commitment).bytes()
        val input = ByteArrayInputStream(head + "zip".toByteArray())
        val read = OfferHead.read(input)
        assertEquals("Téléphone de Zoë 📱", read.from)
        assertArrayEquals(commitment, read.commitment)
        assertEquals("zip", input.readBytes().decodeToString())

        assertThrows(UnreadableOfferException::class.java) { OfferHead.read(ByteArrayInputStream(head.copyOf(head.size - 1))) }
        val tooLong = ByteBuffer.allocate(2).putShort(5_000).array()
        assertThrows(UnreadableOfferException::class.java) { OfferHead.read(ByteArrayInputStream(tooLong + ByteArray(5_100))) }
        assertNotNull(OfferHead.read(ByteArrayInputStream(OfferHead("", commitment).bytes())))
    }

    @Test
    fun `answers say what of a map a device opens, which the release before reads past, and its own answers are read as it opens`() {
        val key = offerJson.encodeToString(KeyAnswer.serializer(), KeyAnswer(OFFER_VERSION, "key", mapFormat = 2, mediaTypes = listOf("jpg", "mp3")))
        val offer = offerJson.encodeToString(OfferAnswer.serializer(), OfferAnswer("id", "nonce", mapFormat = 2, mediaTypes = listOf("jpg", "mp3")))
        assertTrue(offer, offer.contains("\"mapFormat\":2") && offer.contains("\"mediaTypes\":[\"jpg\",\"mp3\"]"))

        // Orbit 0.3.0 reads them as it always has, leaving out what it doesn't know.
        val orbit030 = Json { ignoreUnknownKeys = true }
        assertEquals(OFFER_VERSION, orbit030.decodeFromString(KeyAnswerIn030.serializer(), key).version)
        assertEquals("nonce", orbit030.decodeFromString(OfferAnswerIn030.serializer(), offer).nonce)

        // Its own answers say nothing of it: it opens maps of format 1, with their pictures and videos.
        val fromOrbit030 = offerJson.decodeFromString(OfferAnswer.serializer(), "{\"id\":\"id\",\"nonce\":\"nonce\"}")
        assertEquals(DeviceReads.RELEASE_0_3_0, DeviceReads.of(fromOrbit030.mapFormat, fromOrbit030.mediaTypes))
        assertEquals(DeviceReads(2, setOf("jpg", "mp3")), DeviceReads.of(2, listOf("JPG", "mp3")))
        // Kinds of file are only ever added: every one 0.3.0 keeps, this version keeps too.
        assertTrue(carriedMediaTypes().containsAll(DeviceReads.RELEASE_0_3_0.mediaTypes))
    }

    @Test
    fun `offer ids can't change the requests they go in`() {
        assertTrue(isOfferId(UUID.randomUUID().toString()))
        assertFalse(isOfferId(""))
        assertFalse(isOfferId("../key"))
        assertFalse(isOfferId("1 HTTP/1.1\r\nHost: elsewhere"))
        assertFalse(isOfferId("a".repeat(65)))
    }
}
