package com.hyuchia.orbit.nearby

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

class OfferProtocolTest {
    private val key = DeviceKey.of(newKeyPair())
    private val selfId = UUID.randomUUID().toString()

    @Test
    fun `the advertisement's key fits its TXT record, and reads back`() {
        val attributes = serviceAttributes(selfId, key)
        assertEquals(setOf(SERVICE_ID, SERVICE_KEY), attributes.keys)
        // Android refuses an attribute of 255 bytes or more, and warns of truncation past 400 bytes in all.
        attributes.forEach { (name, value) -> assertTrue("$name is ${name.length + value.length} bytes", name.length + value.toByteArray().size < 255) }
        val record = attributes.entries.sumOf { (name, value) -> 1 + name.length + 1 + value.toByteArray().size }
        assertTrue("$record bytes", record <= 400)

        val advertised = attributes.mapValues { it.value.toByteArray() }
        val device = nearbyDevice("Ana's tablet", advertised, "192.168.1.20", 8766, selfId = UUID.randomUUID().toString())!!
        assertEquals(NearbyDevice("Ana's tablet", "192.168.1.20:8766", key.text), device)
        assertArrayEquals(key.key.encoded, DeviceKey.read(device.key)!!.key.encoded)
    }

    @Test
    fun `a device doesn't list itself, and one from an older Orbit has no key`() {
        val advertised = serviceAttributes(selfId, key).mapValues { it.value.toByteArray() }
        assertNull(nearbyDevice("This phone", advertised, "192.168.1.5", 8766, selfId))
        assertNull(nearbyDevice("Ana's tablet", advertised, null, 8766, UUID.randomUUID().toString()))
        val older = nearbyDevice("Old tablet", mapOf(SERVICE_ID to "other".toByteArray()), "192.168.1.21", 8766, selfId)!!
        assertNull(older.key)
    }

    @Test
    fun `an offer's head carries the name and the commitment, and leaves the map whole`() {
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
    fun `offer ids can't change the requests they go in`() {
        assertTrue(isOfferId(UUID.randomUUID().toString()))
        assertFalse(isOfferId(""))
        assertFalse(isOfferId("../key"))
        assertFalse(isOfferId("1 HTTP/1.1\r\nHost: elsewhere"))
        assertFalse(isOfferId("a".repeat(65)))
    }
}
