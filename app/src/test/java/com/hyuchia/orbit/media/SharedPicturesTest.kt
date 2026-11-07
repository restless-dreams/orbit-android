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
import java.io.File
import java.io.RandomAccessFile
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

    /** `png` with a chunk of `type` holding `data` put before its first chunk of type `before`. */
    private fun withChunk(png: ByteArray, before: String, type: String, data: ByteArray): ByteArray {
        val at = chunkStarts(png).first { text(png.copyOfRange(it + 4, it + 8)) == before }
        return png.copyOfRange(0, at) + chunk(type, data) + png.copyOfRange(at, png.size)
    }

    /** Where each of the PNG's chunks starts. */
    private fun chunkStarts(png: ByteArray): List<Int> {
        val starts = mutableListOf<Int>()
        var position = 8

        while (position + 12 <= png.size) {
            starts += position
            position += 12 + ByteBuffer.wrap(png, position, 4).int
        }

        return starts
    }

    /** Content credentials as an editor writes them into a PNG: a JUMBF box saying who made the picture, where and when. */
    private val credentials = latin("jumb c2pa.claim: made by Test person with Test editor, $place, 2026-10-21")

    @Test
    fun `a PNG keeps only the chunks that draw it, not those apps keep their own in`() {
        // As ImageMagick writes a picture: its colors' chromaticities and background, when it was made, and its dates as text.
        val written = File("src/test/resources/outgoing/edited.png").readBytes()
        val withPrivate = withChunk(withChunk(written, "IDAT", "prVt", latin("Test editor's own, $place")), "IEND", "caBX", credentials)
        val shared = SharedPictures.withoutMetadata(withPrivate)!!

        assertFalse(text(shared).contains("Lisbon"))
        assertFalse(text(shared).contains("Test"))
        assertArrayEquals(shared, streamed(withPrivate))
        assertEquals(listOf("IHDR", "cHRM", "bKGD", "IDAT", "IEND"), chunks(shared).map { it.first })
        // Every chunk it keeps is the written one, byte for byte, and so are its pixels.
        val kept = chunks(written).filter { it.first in setOf("IHDR", "cHRM", "bKGD", "IDAT", "IEND") }
        assertEquals(kept.map { it.first to text(it.second) }, chunks(shared).map { it.first to text(it.second) })
        assertEquals(pixels(written), pixels(shared))
    }

    @Test
    fun `an animated PNG keeps every frame as it was, without an app's chunks between them`() {
        // As ffmpeg writes an animation: three frames of 24 by 16, looping.
        val animation = File("src/test/resources/outgoing/animated.png").readBytes()
        val withPrivate = withChunk(withChunk(animation, "fdAT", "prVt", latin("Test editor's own, $place")), "IEND", "caBX", credentials)

        // Its header, how big it prints, the animation's control, and each frame's control and data, as they were, from memory or a piece at a time.
        assertArrayEquals(animation, SharedPictures.withoutMetadata(withPrivate))
        assertArrayEquals(animation, streamed(withPrivate))
        assertArrayEquals(animation, SharedPictures.withoutMetadata(animation))
    }

    @Test
    fun `a PNG with a chunk it can't be read without, of a kind Orbit doesn't know, doesn't go clean`() {
        val png = File("src/test/resources/outgoing/edited.png").readBytes()

        // Apple's CgBI, before the header, changes how everything after it reads: what it holds can't be told.
        assertNull(SharedPictures.withoutMetadata(withChunk(png, "IHDR", "CgBI", ByteArray(4))))
        assertNull(streamed(withChunk(png, "IHDR", "CgBI", ByteArray(4))))
        assertNull(SharedPictures.withoutMetadata(withChunk(png, "IDAT", "Te5t", latin(place))))
    }

    /** A chunk of a WebP's RIFF: its type, its size, little-endian, what it holds, and a byte of padding after an odd size. */
    private fun webPChunk(type: String, data: ByteArray): ByteArray {
        val padded = if (data.size % 2 == 1) data + 0.toByte() else data
        return latin(type) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array() + padded
    }

    /** A WebP of these chunks. */
    private fun webP(vararg chunks: ByteArray): ByteArray {
        val body = latin("WEBP") + chunks.fold(ByteArray(0)) { all, chunk -> all + chunk }
        return latin("RIFF") + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array() + body
    }

    /** A WebP's chunks, each as its type and its bytes, header and padding included. */
    private fun webPChunks(webP: ByteArray, from: Int = 12, until: Int = webP.size): List<Pair<String, ByteArray>> {
        val found = mutableListOf<Pair<String, ByteArray>>()
        var position = from

        while (position + 8 <= until) {
            val size = ByteBuffer.wrap(webP, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val end = minOf(until, position + 8 + size + size % 2)
            found += text(webP.copyOfRange(position, position + 4)) to webP.copyOfRange(position, end)
            position = end
        }

        return found
    }

    @Test
    fun `an animated WebP keeps every frame as it was, without an app's chunks beside or inside them`() {
        val animation = File("src/test/resources/animations/orbit.webp").readBytes()
        val chunks = webPChunks(animation)
        val first = chunks.indexOfFirst { it.first == "ANMF" }
        val frame = chunks[first].second
        // An app's chunk inside the first frame, after its image data, as the format lets frames carry.
        val frameWithPrivate = webPChunk("ANMF", frame.copyOfRange(8, frame.size) + webPChunk("TEST", latin("Test editor's own, $place")))
        val withPrivate = webP(
            *chunks.take(first).map { it.second }.toTypedArray(),
            webPChunk("C2PA", credentials),
            frameWithPrivate,
            *chunks.drop(first + 1).map { it.second }.toTypedArray(),
            webPChunk("XMP ", latin("<x:xmpmeta>$place</x:xmpmeta>")),
        )
        assertTrue(text(withPrivate).contains("Lisbon"))

        // Its features, its animation and each of its 48 frames, byte for byte, from memory or a piece at a time.
        assertArrayEquals(animation, SharedPictures.withoutMetadata(withPrivate))
        assertArrayEquals(animation, streamed(withPrivate))
        assertArrayEquals(animation, SharedPictures.withoutMetadata(animation))
    }

    @Test
    fun `a WebP with transparency keeps it, and what follows its RIFF isn't the picture's`() {
        val still = File("src/test/resources/animations/still-alpha.webp").readBytes()
        // A chunk that would draw, after where the RIFF says the file ends: it isn't the picture's.
        val after = still + webPChunk("ICCP", latin("Test profile, $place"))

        assertArrayEquals(still, SharedPictures.withoutMetadata(webP(*webPChunks(still).map { it.second }.toTypedArray(), webPChunk("TEST", latin(place)))))
        assertArrayEquals(still, SharedPictures.withoutMetadata(after))
        assertArrayEquals(still, streamed(after))
        // A RIFF that says it goes on past the file isn't whole.
        assertNull(SharedPictures.withoutMetadata(still.copyOfRange(0, still.size - 20)))
    }

    /** The picture `bytes` go as when they're read from their file and written a piece at a time, as a map's go in its zip and its page. */
    private fun streamed(bytes: ByteArray): ByteArray? {
        val file = File.createTempFile("picture", ".bin").apply { writeBytes(bytes) }

        try {
            return RandomAccessFile(file, "r").use { input ->
                SharedPictures.plan(PictureInFile(input))?.let { pieces ->
                    ByteArrayOutputStream().also { pieces.writeTo(it, input) }.toByteArray().also { assertEquals(pieces.size, it.size.toLong()) }
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a picture goes the same read from its file a piece at a time as from memory`() {
        val pictures = listOf(
            cameraJpeg(orientation = null),
            cameraJpeg(orientation = 6),
            cameraPng(orientation = null),
            cameraPng(orientation = 8),
            cameraWebP(orientation = null),
            cameraWebP(orientation = 3),
            // Bigger than what's read of its file at a time, its odd size padded.
            cameraWebP(orientation = 5, pictureSize = 700_001),
            editorGif(),
        )

        for (picture in pictures) {
            val inMemory = SharedPictures.withoutMetadata(picture)
            assertTrue(inMemory != null)
            assertArrayEquals(inMemory, streamed(picture))
            assertFalse(text(streamed(picture)!!).contains("Lisbon"))
        }

        // One that isn't whole, or of a kind that isn't read through so, isn't planned at all.
        val camera = cameraJpeg(orientation = null)
        assertNull(streamed(camera.copyOfRange(0, camera.size / 2)))
        assertNull(streamed(latin("<svg xmlns=\"http://www.w3.org/2000/svg\"/>")))
        assertNull(streamed(latin("BM")))
    }

    @Test
    fun `a picture that isn't whole isn't taken for one without metadata`() {
        val camera = cameraJpeg(orientation = null)
        val png = cameraPng(orientation = null)

        assertNull(SharedPictures.withoutMetadata(camera.copyOfRange(0, camera.size / 2)))
        assertNull(SharedPictures.withoutMetadata(png.copyOfRange(0, png.size - 20)))
    }

    /** `data` in a GIF's sub-blocks, of at most 255 bytes each, and the empty one that ends them. */
    private fun subBlocks(data: ByteArray): ByteArray = data.toList().chunked(255).flatMap { listOf(it.size.toByte()) + it }.toByteArray() + 0

    /** A GIF with a comment and XMP saying where it was made, looping, and something after its end. */
    private fun editorGif(): ByteArray {
        val plain = picture("gif")
        val screen = plain[10].toInt() and 0xFF
        val afterColors = 13 + if (screen and 0x80 != 0) 3 * (1 shl ((screen and 0x07) + 1)) else 0
        val application = byteArrayOf(0x21, 0xFF.toByte(), 11)
        val loop = application + latin("NETSCAPE2.0") + byteArrayOf(3, 1, 0, 0, 0)
        val xmp = application + latin("XMP DataXMP") + subBlocks(latin("<x:xmpmeta>$place</x:xmpmeta>"))
        val comment = byteArrayOf(0x21, 0xFE.toByte()) + subBlocks(latin(place))
        return plain.copyOfRange(0, afterColors) + loop + comment + xmp + plain.copyOfRange(afterColors, plain.size) + latin("after $place")
    }

    private fun latin(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `a GIF keeps its frames and looping, without comments, other apps' blocks or what follows its end`() {
        val gif = editorGif()
        val shared = SharedPictures.withoutMetadata(gif)!!

        assertFalse(text(shared).contains("Lisbon"))
        assertFalse(text(shared).contains("XMP DataXMP"))
        assertTrue(text(shared).contains("NETSCAPE2.0"))
        assertEquals(0x3B, shared.last().toInt())
        assertEquals(pixels(picture("gif")), pixels(shared))
        // Cut short, it isn't whole, and isn't taken for one without metadata.
        assertNull(SharedPictures.withoutMetadata(gif.copyOfRange(0, gif.size / 2)))
    }

    @Test
    fun `an SVG keeps what draws it, without its metadata, comments or an editor's own`() {
        val drawing = """<?xml version="1.0" encoding="UTF-8"?>
            <!-- Made by Ana in /home/ana/Lisbon/drawing.svg -->
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" xmlns:sodipodi="http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"
                width="10" height="10" inkscape:export-filename="/home/ana/Lisbon/drawing.png" sodipodi:docname="Lisbon.svg">
              <metadata><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description>$place</rdf:Description></rdf:RDF></metadata>
              <sodipodi:namedview inkscape:current-layer="Lisbon"/>
              <title>A circle</title>
              <circle cx="5" cy="5" r="4" fill="#4F5B92"/>
              <use xlink:href="#dot"/>
            </svg>"""
        val shared = text(SharedPictures.withoutMetadata(drawing.toByteArray())!!)

        assertFalse(shared, shared.contains("Lisbon"))
        assertFalse(shared, shared.contains("ana"))
        assertFalse(shared, shared.contains("inkscape"))
        assertTrue(shared, shared.contains("<circle"))
        assertTrue(shared, shared.contains("<title>A circle</title>"))
        assertTrue(shared, shared.contains("xlink:href=\"#dot\""))
        // A drawing with a document type could put anything anywhere: it doesn't go.
        assertNull(SharedPictures.withoutMetadata("<!DOCTYPE svg [<!ENTITY a \"$place\">]><svg xmlns=\"http://www.w3.org/2000/svg\">&a;</svg>".toByteArray()))
    }

    @Test
    fun `lengths made to wrap around aren't followed out of the picture`() {
        val plain = picture("png")
        val afterHeader = 8 + 25
        // A chunk, an EXIF block and a WebP chunk whose lengths, added to where they are, pass 2³¹ and come back negative.
        val chunkLength = plain.copyOfRange(0, afterHeader) + ByteBuffer.allocate(8).putInt(Int.MAX_VALUE - 8).put("tEXt".toByteArray(Charsets.ISO_8859_1)).array() +
            ByteArray(40)
        val exifOffset = plain.copyOfRange(0, afterHeader) +
            chunk("eXIf", byteArrayOf(0x4D, 0x4D, 0x00, 0x2A) + ByteBuffer.allocate(4).putInt(Int.MAX_VALUE - 1).array() + ByteArray(8)) +
            plain.copyOfRange(afterHeader, plain.size)
        val webP = cameraWebP(orientation = null)
        val webPSize = webP.copyOf().also { ByteBuffer.wrap(it, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(Int.MAX_VALUE - 4) }

        assertNull(SharedPictures.withoutMetadata(chunkLength))
        assertNull(SharedPictures.withoutMetadata(webPSize))
        // The EXIF that can't be read says nothing about which way up; the rest of the picture goes as it is.
        assertFalse(text(SharedPictures.withoutMetadata(exifOffset)!!).contains("eXIf"))
    }

    @Test
    fun `pictures are told apart by what they hold`() {
        assertEquals(Kind.Jpeg, SharedPictures.kindOf(picture("jpg")))
        assertEquals(Kind.Png, SharedPictures.kindOf(picture("png")))
        assertEquals(Kind.WebP, SharedPictures.kindOf(cameraWebP(orientation = null)))
        assertEquals(Kind.Gif, SharedPictures.kindOf(picture("gif")))
        assertEquals(Kind.Svg, SharedPictures.kindOf("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertEquals(Kind.Plain, SharedPictures.kindOf(picture("bmp")))
        assertEquals(Kind.Other, SharedPictures.kindOf(byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray(Charsets.ISO_8859_1)))
        assertNull(SharedPictures.withoutMetadata(picture("bmp")))
    }
}
