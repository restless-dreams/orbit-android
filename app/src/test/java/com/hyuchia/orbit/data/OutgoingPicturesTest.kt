package com.hyuchia.orbit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Each kind of picture as it leaves, both ways it can: from its file, as a
 * zip, a device or the room's browsers take it ([OutgoingMedia.of]), and
 * from its bytes, as a page or a drawing does ([OutgoingMedia.picture]).
 * What another app keeps in it goes either way, and the picture, its
 * animation included, comes out the same.
 */
class OutgoingPicturesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val place = "Taken at 38.7139 N, 9.1394 W, Lisbon"

    private fun resource(path: String): ByteArray = File("src/test/resources/$path").readBytes()

    private fun latin(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    /** How `bytes` leave from a file, and from memory, which have to agree. */
    private fun leaving(name: String, bytes: ByteArray): ByteArray? {
        val file = temporary.newFile(name).apply { writeBytes(bytes) }
        val fromFile = OutgoingMedia.of(file)?.let { outgoing -> ByteArrayOutputStream().also { outgoing.writeTo(it) }.toByteArray() }
        val fromMemory = OutgoingMedia.picture(bytes)

        assertArrayEquals(name, fromMemory, fromFile)
        // And what a file says it comes to is what it writes.
        OutgoingMedia.of(file)?.let { assertEquals(name, fromFile!!.size.toLong(), it.size) }
        return fromFile
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typed = latin(type) + data
        return ByteBuffer.allocate(4).putInt(data.size).array() + typed + ByteBuffer.allocate(4).putInt(CRC32().apply { update(typed) }.value.toInt()).array()
    }

    /** `png` with `chunk` put before its first chunk of type `before`. */
    private fun withChunk(png: ByteArray, before: String, chunk: ByteArray): ByteArray {
        var position = 8

        while (String(png, position + 4, 4, Charsets.ISO_8859_1) != before) {
            position += 12 + ByteBuffer.wrap(png, position, 4).int
        }

        return png.copyOfRange(0, position) + chunk + png.copyOfRange(position, png.size)
    }

    private fun webPChunk(type: String, data: ByteArray): ByteArray =
        latin(type) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array() + data + (if (data.size % 2 == 1) byteArrayOf(0) else ByteArray(0))

    @Test
    fun `a PNG and an animated PNG leave without other apps' chunks, either way`() {
        val written = resource("outgoing/edited.png")
        val png = withChunk(withChunk(written, "IDAT", pngChunk("prVt", latin("Test editor's own, $place"))), "IEND", pngChunk("caBX", latin("c2pa $place")))
        val cleaned = leaving("edited.png", png)!!
        assertFalse(String(cleaned, Charsets.ISO_8859_1).contains("Lisbon"))
        assertEquals(written.size - (7 + 12) - 3 * 12 - (37 + 37 + 40), cleaned.size)

        val animation = resource("outgoing/animated.png")
        assertArrayEquals(animation, leaving("animated.png", withChunk(animation, "fdAT", pngChunk("prVt", latin("Test editor's own, $place")))))
    }

    @Test
    fun `an animated WebP leaves without other apps' chunks, either way`() {
        val animation = resource("animations/orbit.webp")
        // Content credentials after its last frame, in a chunk of 50 bytes, as the RIFF now says.
        val withCredentials = animation.copyOfRange(0, 4) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(animation.size - 8 + 50).array() +
            animation.copyOfRange(8, animation.size) + webPChunk("C2PA", latin("c2pa $place").copyOf(50 - 8))
        assertArrayEquals(animation, leaving("orbit.webp", withCredentials))
        assertArrayEquals(resource("animations/still-alpha.webp"), leaving("still-alpha.webp", resource("animations/still-alpha.webp")))
    }

    @Test
    fun `a HEIC motion photo and an animated AVIF leave as their pictures, either way`() {
        val photo = resource("live/google-motion-photo.heic")
        val heic = leaving("motion.heic", photo)!!
        assertEquals(3467, heic.size)
        assertArrayEquals(photo.copyOfRange(0, 2613), heic.copyOfRange(0, 2613))

        val avif = resource("outgoing/animated.avif")
        assertEquals(avif.size, leaving("animated.avif", avif)!!.size)
    }

    @Test
    fun `a drawing leaves passive, either way`() {
        val probe = resource("outgoing/drawings/active.svg")
        val drawing = String(leaving("active.svg", probe)!!, Charsets.UTF_8)

        for (word in listOf("tracker.example", "<script", "onload", "javascript:", "@import")) {
            assertFalse(word, drawing.contains(word))
        }

        assertNotNull(leaving("mermaid.svg", resource("outgoing/drawings/mermaid.svg")))
    }

    @Test
    fun `a picture that can't be read through doesn't leave, either way`() {
        val png = resource("outgoing/edited.png")
        // Apple's CgBI: a chunk it can't be read without, of a kind Orbit doesn't know.
        assertNull(leaving("crushed.png", png.copyOfRange(0, 8) + pngChunk("CgBI", ByteArray(4)) + png.copyOfRange(8, png.size)))
        // A WebP whose RIFF says it goes on past the file.
        val webP = resource("animations/still-alpha.webp")
        assertNull(leaving("cut.webp", webP.copyOfRange(0, webP.size - 20)))
    }
}
