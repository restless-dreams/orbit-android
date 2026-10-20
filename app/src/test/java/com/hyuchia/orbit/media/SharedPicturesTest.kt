package com.hyuchia.orbit.media

import com.hyuchia.orbit.media.SharedPictures.Kind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import javax.imageio.ImageIO

class SharedPicturesTest {
    /** Where the photos below were taken, as a camera writes it into their metadata. */
    private val place = "Taken at 38.7139 N, 9.1394 W, Lisbon"

    private fun picture(format: String): ByteArray {
        val image = BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB)

        for (x in 0 until 48) {
            for (y in 0 until 32) {
                image.setRGB(x, y, (x * 5 shl 16) or (y * 7 shl 8) or 0x40)
            }
        }

        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    private fun pixels(bytes: ByteArray): List<Int> {
        val image = ImageIO.read(bytes.inputStream())
        return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) } }
    }

    /** An EXIF block (TIFF, little-endian, as many cameras write it) with a description that says where, and `orientation` when given. */
    private fun exif(orientation: Int?): ByteArray {
        val description = (place + "\u0000").toByteArray(Charsets.ISO_8859_1)
        val entries = listOfNotNull(0x010E, orientation?.let { 0x0112 })
        val textAt = 8 + 2 + entries.size * 12 + 4
        val buffer = ByteBuffer.allocate(textAt + description.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf(0x49, 0x49, 0x2A, 0x00)).putInt(8).putShort(entries.size.toShort())
        buffer.putShort(0x010E).putShort(2).putInt(description.size).putInt(textAt)

        if (orientation != null) {
            buffer.putShort(0x0112).putShort(3).putInt(1).putShort(orientation.toShort()).putShort(0)
        }

        buffer.putInt(0).put(description)
        return buffer.array()
    }

    private fun segment(marker: Int, data: ByteArray): ByteArray =
        byteArrayOf(0xFF.toByte(), marker.toByte(), ((data.size + 2) shr 8).toByte(), (data.size + 2).toByte()) + data

    /** A camera's JPEG: EXIF and XMP after JFIF's segment, a comment, and a picture stored after the end, all saying where it was taken. */
    private fun cameraJpeg(orientation: Int?): ByteArray {
        val plain = picture("jpg")
        val afterJfif = 4 + (((plain[4].toInt() and 0xFF) shl 8) or (plain[5].toInt() and 0xFF))
        val exif = segment(0xE1, "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + exif(orientation))
        val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>$place</x:xmpmeta>".toByteArray(Charsets.ISO_8859_1))
        val comment = segment(0xFE, place.toByteArray(Charsets.ISO_8859_1))
        val trailer = "MPF $place".toByteArray(Charsets.ISO_8859_1)
        return plain.copyOfRange(0, afterJfif) + exif + xmp + comment + plain.copyOfRange(afterJfif, plain.size) + trailer
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typed = type.toByteArray(Charsets.ISO_8859_1) + data
        val crc = CRC32().apply { update(typed) }.value.toInt()
        return ByteBuffer.allocate(4).putInt(data.size).array() + typed + ByteBuffer.allocate(4).putInt(crc).array()
    }

    /** A PNG with EXIF and a text chunk before its picture data, saying where it was taken. */
    private fun cameraPng(orientation: Int?): ByteArray {
        val plain = picture("png")
        val afterHeader = 8 + 25
        return plain.copyOfRange(0, afterHeader) + chunk("eXIf", exif(orientation)) + chunk("tEXt", "Location\u0000$place".toByteArray(Charsets.ISO_8859_1)) +
            plain.copyOfRange(afterHeader, plain.size)
    }

    /** The PNG's chunks, by type, each with its data and whether its checksum is right. */
    private fun chunks(png: ByteArray): List<Triple<String, ByteArray, Boolean>> {
        val found = mutableListOf<Triple<String, ByteArray, Boolean>>()
        val buffer = ByteBuffer.wrap(png)
        buffer.position(8)

        while (buffer.remaining() >= 12) {
            val length = buffer.int
            val typed = ByteArray(4 + length).also { buffer.get(it) }
            val crc = buffer.int
            found += Triple(String(typed, 0, 4, Charsets.ISO_8859_1), typed.copyOfRange(4, typed.size), CRC32().apply { update(typed) }.value.toInt() == crc)
        }

        return found
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    /** The EXIF block that says only which way up a picture is, as SharedPictures writes it. */
    private fun orientationOnly(orientation: Int): ByteArray =
        byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, 0, 0, 0, 0)

    @Test
    fun `a camera's JPEG is shared as the same picture, without where it was taken`() {
        val camera = cameraJpeg(orientation = null)
        val shared = SharedPictures.withoutMetadata(camera)!!

        assertTrue(text(camera).contains("Lisbon"))
        assertFalse(text(shared).contains("Lisbon"))
        assertFalse(text(shared).contains("Exif"))
        assertEquals(pixels(picture("jpg")), pixels(shared))
        // Up to its end: what came after it is gone.
        assertEquals(0xD9, shared.last().toInt() and 0xFF)
    }

    @Test
    fun `a JPEG keeps which way up it is, and nothing else of its EXIF`() {
        val shared = SharedPictures.withoutMetadata(cameraJpeg(orientation = 6))!!
        val exif = text(shared).indexOf("Exif\u0000\u0000")

        assertFalse(text(shared).contains("Lisbon"))
        assertTrue(exif > 0)
        assertArrayEquals(orientationOnly(6), shared.copyOfRange(exif + 6, exif + 6 + 26))
        assertEquals(pixels(picture("jpg")), pixels(shared))
    }

    @Test
    fun `an upright JPEG says nothing about which way up it is`() {
        assertFalse(text(SharedPictures.withoutMetadata(cameraJpeg(orientation = 1))!!).contains("Exif"))
    }

    @Test
    fun `a PNG is shared as the same picture, without its EXIF and text`() {
        val shared = SharedPictures.withoutMetadata(cameraPng(orientation = null))!!

        assertFalse(text(shared).contains("Lisbon"))
        assertEquals(listOf("IHDR", "IDAT", "IEND"), chunks(shared).map { it.first }.distinct())
        assertEquals(pixels(picture("png")), pixels(shared))
    }

    @Test
    fun `a PNG keeps which way up it is, before its picture data`() {
        val shared = SharedPictures.withoutMetadata(cameraPng(orientation = 8))!!
        val found = chunks(shared)
        val exif = found.single { it.first == "eXIf" }

        assertFalse(text(shared).contains("Lisbon"))
        assertArrayEquals(orientationOnly(8), exif.second)
        assertTrue(exif.third)
        assertTrue(found.indexOf(exif) < found.indexOfFirst { it.first == "IDAT" })
        assertEquals(pixels(picture("png")), pixels(shared))
    }

    /** A WebP in its extended format, its flags saying it has EXIF and XMP, with a picture of `pictureSize` bytes. */
    private fun cameraWebP(orientation: Int?, pictureSize: Int = 13): ByteArray {
        val webPChunk = { type: String, data: ByteArray ->
            val padded = if (data.size % 2 == 1) data + 0.toByte() else data
            type.toByteArray(Charsets.ISO_8859_1) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array() + padded
        }

        val features = byteArrayOf((0x10 or 0x08 or 0x04).toByte(), 0, 0, 0, 47, 0, 0, 31, 0, 0)
        val body = "WEBP".toByteArray(Charsets.ISO_8859_1) + webPChunk("VP8X", features) + webPChunk("VP8L", ByteArray(pictureSize) { it.toByte() }) +
            webPChunk("EXIF", exif(orientation)) + webPChunk("XMP ", "<x:xmpmeta>$place</x:xmpmeta>".toByteArray(Charsets.ISO_8859_1))
        return "RIFF".toByteArray(Charsets.ISO_8859_1) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array() + body
    }

    @Test
    fun `a WebP loses its EXIF and XMP, and its header says so`() {
        val shared = SharedPictures.withoutMetadata(cameraWebP(orientation = null))!!
        val size = ByteBuffer.wrap(shared, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

        assertFalse(text(shared).contains("Lisbon"))
        assertFalse(text(shared).contains("EXIF"))
        assertEquals(shared.size - 8, size)
        assertEquals(0x10, shared[20].toInt())
        assertEquals("WEBPVP8X", text(shared.copyOfRange(8, 16)))
        // The picture, its odd size padded, then nothing.
        assertEquals("VP8L", text(shared.copyOfRange(30, 34)))
        assertEquals(30 + 8 + 14, shared.size)
    }

    @Test
    fun `a WebP keeps which way up it is`() {
        val shared = SharedPictures.withoutMetadata(cameraWebP(orientation = 3))!!
        val exif = text(shared).indexOf("EXIF")

        assertFalse(text(shared).contains("Lisbon"))
        assertEquals(0x10 or 0x08, shared[20].toInt())
        assertArrayEquals(orientationOnly(3), shared.copyOfRange(exif + 8, exif + 8 + 26))
        assertEquals(shared.size - 8, ByteBuffer.wrap(shared, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test
    fun `a picture that isn't whole isn't taken for one without metadata`() {
        val camera = cameraJpeg(orientation = null)
        val png = cameraPng(orientation = null)

        assertNull(SharedPictures.withoutMetadata(camera.copyOfRange(0, camera.size / 2)))
        assertNull(SharedPictures.withoutMetadata(png.copyOfRange(0, png.size - 20)))
    }

    @Test
    fun `pictures are told apart by what they hold`() {
        assertEquals(Kind.Jpeg, SharedPictures.kindOf(picture("jpg")))
        assertEquals(Kind.Png, SharedPictures.kindOf(picture("png")))
        assertEquals(Kind.WebP, SharedPictures.kindOf(cameraWebP(orientation = null)))
        assertEquals(Kind.Plain, SharedPictures.kindOf(picture("gif")))
        assertEquals(Kind.Plain, SharedPictures.kindOf("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertEquals(Kind.Other, SharedPictures.kindOf(byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray(Charsets.ISO_8859_1)))
        assertNull(SharedPictures.withoutMetadata(picture("gif")))
    }
}
