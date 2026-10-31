package com.hyuchia.orbit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.imageio.ImageIO

class OutgoingMediaTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** Where the files below were made, as cameras and phones write it into them. */
    private val place = "+38.7139-009.1394/"

    private fun file(name: String, bytes: ByteArray): File = temporary.newFile(name).apply { writeBytes(bytes) }

    private fun sent(outgoing: OutgoingFile): ByteArray = ByteArrayOutputStream().also { outgoing.writeTo(it) }.toByteArray()

    private fun ByteArray.says(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    private fun picture(format: String): ByteArray {
        val image = BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB)

        for (x in 0 until 24) {
            for (y in 0 until 16) {
                image.setRGB(x, y, (x * 9 shl 16) or (y * 13 shl 8) or 0x40)
            }
        }

        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    /** A JPEG with an EXIF block that says where it was taken, after JFIF's segment, as cameras write it. */
    private fun cameraJpeg(): ByteArray {
        val plain = picture("jpg")
        val afterJfif = 4 + (((plain[4].toInt() and 0xFF) shl 8) or (plain[5].toInt() and 0xFF))
        val description = (place + "\u0000").toByteArray(Charsets.ISO_8859_1)
        val tiff = ByteBuffer.allocate(8 + 2 + 12 + 4 + description.size).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put(byteArrayOf(0x49, 0x49, 0x2A, 0x00)).putInt(8).putShort(1)
        tiff.putShort(0x010E).putShort(2).putInt(description.size).putInt(26)
        tiff.putInt(0).put(description)
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.array()
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()) + exif
        return plain.copyOfRange(0, afterJfif) + segment + plain.copyOfRange(afterJfif, plain.size)
    }

    /** A box of ISO's base media file format: its size, type and what it holds. */
    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = content.fold(ByteArray(0)) { all, part -> all + part }
        return ByteBuffer.allocate(8).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).array() + body
    }

    /** A box with a 64-bit size, as files past 4 GB need and some writers use anyway. */
    private fun largeBox(type: String, body: ByteArray): ByteArray =
        ByteBuffer.allocate(16).putInt(1).put(type.toByteArray(Charsets.ISO_8859_1)).putLong(16L + body.size).array() + body

    private fun full(version: Int, vararg content: ByteArray): ByteArray = byteArrayOf(version.toByte(), 0, 0, 0) + content.fold(ByteArray(0)) { all, part -> all + part }

    private fun int(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun short(value: Int): ByteArray = ByteBuffer.allocate(2).putShort(value.toShort()).array()

    private fun text(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)

    /** A movie header, or a track's or its media's, made on 21 October 2026 (seconds since 1904). */
    private fun header(type: String): ByteArray = box(type, full(0, int(0xEC8B0E80.toInt()), int(0xEC8B0E80.toInt()), int(1000), int(400)))

    private val frames = ByteArray(300) { (it * 7).toByte() }

    /** A phone's video: where it was filmed in `udta`, its maker in `meta`, its dates in its headers, and a phone's own trailer after it. */
    private fun cameraVideo(): ByteArray = box("ftyp", text("isom"), int(512), text("isomiso2mp41")) +
        box("mdat", frames) +
        box(
            "moov",
            header("mvhd"),
            box("trak", header("tkhd"), box("mdia", header("mdhd"), box("hdlr", full(0, int(0), text("vide"), ByteArray(12), text("Video\u0000")))), box("udta", box("name", text("Camera")))),
            box("udta", box("©xyz", short(place.length), short(0x15C7), text(place))),
            box("meta", full(0, box("hdlr", full(0, int(0), text("mdta"), ByteArray(12), byteArrayOf(0))), box("keys", full(0, int(1), int(28), text("mdta"), text("com.android.manufacturer"))))),
        ) +
        text("SEFH" + "Samsung's own trailer, $place")

    @Test
    fun `a JPEG goes without where it was taken, with the same picture`() {
        val photo = file("photo.jpg", cameraJpeg())
        val outgoing = OutgoingMedia.of(photo)

        assertTrue(outgoing is OutgoingFile.Cleaned)
        val bytes = sent(outgoing!!)
        assertFalse(bytes.says(place))
        assertTrue(photo.readBytes().says(place))

        val sentImage = ImageIO.read(bytes.inputStream())
        val original = ImageIO.read(photo.inputStream())
        assertEquals(original.width, sentImage.width)
        assertEquals(original.getRGB(5, 5), sentImage.getRGB(5, 5))
        // The map's own file stays as it was.
        assertTrue(photo.readBytes().contentEquals(cameraJpeg()))
    }

    @Test
    fun `a picture that isn't whole doesn't go`() {
        val cut = cameraJpeg().let { it.copyOfRange(0, it.size / 3) }
        assertNull(OutgoingMedia.of(file("cut.jpg", cut)))
        assertNull(OutgoingMedia.picture(cut))
    }

    @Test
    fun `a video keeps every frame where it is, and goes without where and when it was made`() {
        val video = cameraVideo()
        val outgoing = OutgoingMedia.of(file("video.mp4", video))

        assertTrue(outgoing is OutgoingFile.Patched)
        val bytes = sent(outgoing!!)
        val trailer = text("SEFH").size + "Samsung's own trailer, $place".length
        // The phone's trailer after the last box stays out; everything else keeps its place.
        assertEquals(video.size - trailer, bytes.size)
        assertEquals(bytes.size.toLong(), outgoing.size)
        assertFalse(bytes.says(place))
        assertFalse(bytes.says("com.android.manufacturer"))
        assertFalse(bytes.says("Camera"))

        val framesAt = video.indexOf(frames)
        assertArrayEquals(frames, bytes.copyOfRange(framesAt, framesAt + frames.size))
        // The boxes that held all that are free space now, and the headers' dates are zero.
        assertTrue(bytes.says("free"))
        val movieHeader = String(bytes, Charsets.ISO_8859_1).indexOf("mvhd")
        assertArrayEquals(ByteArray(8), bytes.copyOfRange(movieHeader + 8, movieHeader + 16))
        // Nothing outside the movie's boxes changed.
        val movie = String(video, Charsets.ISO_8859_1).indexOf("moov")
        assertTrue(video.indices.filter { it < bytes.size && video[it] != bytes[it] }.all { it > movie })
    }

    @Test
    fun `a video from ffmpeg, with where it was filmed, goes without it and stays a video`() {
        val located = File("src/test/resources/outgoing/located.mp4")
        assertTrue(located.readBytes().says(place))

        val outgoing = OutgoingMedia.of(located)
        val bytes = sent(outgoing!!)
        assertEquals(located.length(), bytes.size.toLong())
        assertFalse(bytes.says(place))
        assertFalse(bytes.says("Pixel"))
        // Its streams are where they were: the same media data, byte for byte.
        val media = located.readBytes().let { String(it, Charsets.ISO_8859_1).indexOf("mdat") }
        assertArrayEquals(located.readBytes().copyOfRange(media, media + 2000), bytes.copyOfRange(media, media + 2000))
    }

    @Test
    fun `a QuickTime movie's media keeps no user data of its own`() {
        // QuickTime lets a track's media, and its media information, carry user data and metadata too.
        val media = box(
            "mdia",
            header("mdhd"),
            box("hdlr", full(0, int(0), text("soun"), ByteArray(12), text("Sound\u0000"))),
            box("minf", box("smhd", full(0, int(0))), box("udta", box("©day", text("2026-10-21")))),
            box("udta", box("©xyz", short(place.length), short(0x15C7), text(place))),
            box("meta", full(0, box("hdlr", full(0, int(0), text("mdta"), ByteArray(12), byteArrayOf(0))), box("keys", full(0, int(1), int(31), text("mdta"), text("com.apple.quicktime.make"))))),
        )
        val video = box("ftyp", text("qt  "), int(0), text("qt  ")) + box("mdat", frames) + box("moov", header("mvhd"), box("trak", header("tkhd"), media))
        val bytes = sent(OutgoingMedia.of(file("movie.mov", video))!!)

        assertEquals(video.size, bytes.size)
        assertFalse(bytes.says(place))
        assertFalse(bytes.says("com.apple.quicktime.make"))
        assertFalse(bytes.says("2026-10-21"))
        // Its handler keeps its type, which players go by, and its name, where cameras name themselves, says nothing.
        assertTrue(bytes.says("soun"))
        assertFalse(bytes.says("Sound"))

        // A track's media that can't be read through might keep them anywhere: the movie doesn't go.
        val unreadable = box("ftyp", text("qt  "), int(0), text("qt  ")) + box("moov", header("mvhd"), box("trak", header("tkhd"), box("mdia", header("mdhd"), ByteArray(3))))
        assertNull(OutgoingMedia.of(file("unreadable.mov", unreadable)))
    }

    @Test
    fun `a video's handlers and sample descriptions don't name the camera that made it`() {
        // As an action camera writes them: its maker in QuickTime's vendor, its name in the compressor's and the handler's.
        val visual = ByteArray(6) + short(1) + short(0) + short(0) + text("GoPr") + ByteArray(8) + short(24) + short(16) + int(0x00480000) + int(0x00480000) +
            int(0) + short(1) + (byteArrayOf(17) + text("GoPro AVC encoder")).copyOf(32) + short(24) + short(-1)
        val sound = ByteArray(6) + short(1) + short(0) + short(0) + text("GoPr") + short(2) + short(16) + short(0) + short(0) + int(48000 shl 16)
        val handler = { type: String -> box("hdlr", full(0, int(0), text(type), ByteArray(12), text("GoPro ${type.uppercase()}\u0000"))) }

        val track = { type: String, description: ByteArray, entry: String ->
            box("trak", header("tkhd"), box("mdia", header("mdhd"), handler(type), box("minf", box("stbl", box("stsd", full(0, int(1)), box(entry, description))))))
        }

        val video = box("ftyp", text("isom"), int(0), text("isom")) + box("moov", header("mvhd"), track("vide", visual, "avc1"), track("soun", sound, "mp4a")) + box("mdat", frames)
        val bytes = sent(OutgoingMedia.of(file("gopro.mp4", video))!!)

        assertEquals(video.size, bytes.size)
        assertFalse(bytes.says("GoPr"))
        // How to decode stays: the size of its pictures, and its sound's rate.
        assertTrue(bytes.indexOf(short(24) + short(16) + int(0x00480000)) > 0)
        assertTrue(bytes.indexOf(int(48000 shl 16)) > 0)
        assertTrue(bytes.says("vide") && bytes.says("soun"))
    }

    @Test
    fun `free space, fragments and when a live stream's fragments were made go without what they kept`() {
        // An editor that freed a box may leave what it said in the free space; fragments may carry their own user data.
        val video = box("ftyp", text("iso6"), int(0), text("iso6")) +
            box("moov", header("mvhd"), box("free", text("udta ©xyz $place")), box("trak", header("tkhd"), box("mdia", header("mdhd"), box("minf", box("vmhd", full(0, ByteArray(8))))))) +
            box("prft", full(0, int(1), ByteArray(8) { 9 }, int(0))) +
            box("moof", box("mfhd", full(0, int(1))), box("traf", box("tfhd", full(0, int(1))), box("udta", box("©day", text("2026-10-21"))))) +
            box("mdat", frames) +
            box("skip", text("Test leftover"))
        val bytes = sent(OutgoingMedia.of(file("fragments.mp4", video))!!)

        assertEquals(video.size, bytes.size)
        assertFalse(bytes.says(place))
        assertFalse(bytes.says("2026-10-21"))
        assertFalse(bytes.says("Test leftover"))
        assertFalse(bytes.says("prft"))
        val framesAt = video.indexOf(frames)
        assertArrayEquals(frames, bytes.copyOfRange(framesAt, framesAt + frames.size))
    }

    @Test
    fun `a box with a 64-bit size is read like any other`() {
        val video = box("ftyp", text("isom"), int(512)) + box("moov", header("mvhd"), largeBox("udta", box("©xyz", text(place)))) + box("mdat", frames)
        val bytes = sent(OutgoingMedia.of(file("large.mp4", video))!!)
        assertEquals(video.size, bytes.size)
        assertFalse(bytes.says(place))
        // Its 64-bit size stays, so every box after it is still where it was.
        val udta = String(video, Charsets.ISO_8859_1).indexOf("udta") - 4
        assertArrayEquals(video.copyOfRange(udta, udta + 4), bytes.copyOfRange(udta, udta + 4))
        assertArrayEquals(video.copyOfRange(udta + 8, udta + 16), bytes.copyOfRange(udta + 8, udta + 16))
    }

    @Test
    fun `a file of boxes without a movie or picture in it doesn't go`() {
        assertNull(OutgoingMedia.of(file("empty.mp4", box("ftyp", text("isom"), int(0)) + box("mdat", frames))))
    }

    @Test
    fun `a video or a voice memo cut short doesn't go, as it can't be played without all of it`() {
        // A video from ffmpeg, its movie after its media as cameras write it, cut in each; and a voice memo, its movie first as phones record it, cut in its media.
        val located = File("src/test/resources/outgoing/located.mp4").readBytes()
        val memo = File("src/test/resources/outgoing/memo.mp4").readBytes()

        val cut = mapOf(
            "movie-cut.mp4" to located.copyOfRange(0, located.size - 100),
            "media-cut.mp4" to located.copyOfRange(0, 1000),
            "memo-cut.mp4" to memo.copyOfRange(0, memo.size - 100),
        )

        for ((name, bytes) in cut) {
            assertNull(name, OutgoingMedia.of(file(name, bytes)))
        }
    }

    private val image = ByteArray(64) { (it * 3).toByte() }

    /**
     * A HEIF picture: its image and its EXIF item in `mdat`, where the item
     * locations say, and, with locations of version 1, an XMP item in `idat`,
     * as phones write them.
     */
    private fun cameraHeif(locationsVersion: Int): ByteArray {
        val exif = int(6) + text("Exif\u0000\u0000") + text(place)
        val xmp = text("<x:xmpmeta>$place</x:xmpmeta>")
        val withXmp = locationsVersion > 0
        val entries = listOfNotNull(
            box("infe", full(2, short(1), short(0), text("hvc1"), text("\u0000"))),
            box("infe", full(2, short(2), short(0), text("Exif"), text("\u0000"))),
            box("infe", full(2, short(3), short(0), text("mime"), text("XMP\u0000"), text("application/rdf+xml\u0000"))).takeIf { withXmp },
        )
        val info = box("iinf", full(0, short(entries.size)), *entries.toTypedArray())

        fun locations(imageAt: Int, exifAt: Int): ByteArray {
            val item = { id: Int, method: Int, offset: Int, length: Int ->
                short(id) + (if (withXmp) short(method) else ByteArray(0)) + short(0) + short(1) + int(offset) + int(length)
            }

            val items = listOfNotNull(item(1, 0, imageAt, image.size), item(2, 0, exifAt, exif.size), item(3, 1, 0, xmp.size).takeIf { withXmp })
            return box("iloc", full(locationsVersion, byteArrayOf(0x44, 0x00), short(items.size), *items.toTypedArray()))
        }

        val type = box("ftyp", text("heic"), int(0), text("mif1heic"))
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), byteArrayOf(0)))
        val meta = { at: ByteArray -> box("meta", full(0, handler, info, at, if (withXmp) box("idat", xmp) else ByteArray(0))) }
        // The locations depend on where the media data lands, after the meta box, whose size doesn't change with them.
        val mediaStart = type.size + meta(locations(0, 0)).size + 8
        return type + meta(locations(mediaStart, mediaStart + image.size)) + box("mdat", image, exif)
    }

    @Test
    fun `a HEIF picture keeps its picture, and goes without its EXIF and XMP`() {
        for (version in listOf(0, 1)) {
            val heif = cameraHeif(version)
            assertTrue(heif.says(place))

            val bytes = OutgoingMedia.picture(heif)!!
            assertEquals(heif.size, bytes.size)
            assertFalse("version $version", bytes.says(place))
            assertTrue(bytes.indexOf(image) >= 0)
        }
    }

    @Test
    fun `an AVIF picture from ffmpeg, with nothing to take out, goes as it is`() {
        val avif = File("src/test/resources/outgoing/plain.avif")
        assertArrayEquals(avif.readBytes(), sent(OutgoingMedia.of(avif)!!))
    }

    @Test
    fun `a file of no kind Orbit knows doesn't go, as what it holds can't be told`() {
        // A GIF and an SVG can carry an editor's comments: they go clean, as SharedPicturesTest shows. Sounds go clean too (OutgoingSoundsTest).
        val unknown = file("picture.jpg", ByteArray(64))
        val noise = file("song.mp3", ByteArray(64) { (it * 37 + 11).toByte() })

        assertNull(OutgoingMedia.of(unknown))
        assertNull(OutgoingMedia.of(noise))
        assertNull(OutgoingMedia.picture(unknown.readBytes()))
        // Unless the person sharing chose to share it as it is.
        assertArrayEquals(unknown.readBytes(), sent(OutgoingMedia.of(unknown, setOf("picture.jpg"))!!))
    }

    /** A BMP as Java writes one, 24 bits a pixel, with what an app might keep around its picture: in the bytes the format leaves to apps, between its header and its pixels, and after them. */
    private fun appBitmap(): ByteArray {
        val plain = picture("bmp")
        val pixels = ByteBuffer.wrap(plain, 10, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val gap = text("Test editor $place")
        val moved = plain.copyOfRange(0, pixels) + gap + plain.copyOfRange(pixels, plain.size) + text("Test leftover")
        ByteBuffer.wrap(moved).order(ByteOrder.LITTLE_ENDIAN).putShort(6, 0x6554).putShort(8, 0x7473).putInt(10, pixels + gap.size).putInt(2, moved.size)
        return moved
    }

    @Test
    fun `a BMP keeps its picture, and what an app keeps around it says nothing`() {
        val bitmap = appBitmap()
        val bytes = sent(OutgoingMedia.of(file("drawing.bmp", bitmap))!!)

        assertFalse(bytes.says(place))
        assertFalse(bytes.says("Test"))
        assertEquals(bitmap.size - "Test leftover".length, bytes.size)
        assertArrayEquals(ByteArray(4), bytes.copyOfRange(6, 10))
        assertEquals(bytes.size, ByteBuffer.wrap(bytes, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int)

        val drawn = ImageIO.read(bytes.inputStream())
        val original = ImageIO.read(picture("bmp").inputStream())
        assertEquals(original.width, drawn.width)
        assertEquals(original.getRGB(5, 5), drawn.getRGB(5, 5))
        assertEquals(original.getRGB(23, 15), drawn.getRGB(23, 15))
        // Inside a page or a drawing too.
        assertArrayEquals(bytes, OutgoingMedia.picture(bitmap))
    }

    /**
     * A BMP of two by two pixels with a version 5 header, and after its
     * pixels a gap, its color profile, `embedded` or a linked file's name
     * (`profile`), and an app's leftovers.
     */
    private fun bitmapWithProfile(embedded: Boolean, profile: ByteArray): ByteArray {
        val pixels = ByteArray(16) { (it * 13 + 1).toByte() }
        val gap = text("Test gap")
        val header = ByteBuffer.allocate(124).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(124).putInt(2).putInt(2).putShort(1).putShort(24).putInt(0).putInt(pixels.size).putInt(2835).putInt(2835).putInt(0).putInt(0)
            .put(ByteArray(16)).putInt(if (embedded) 0x4D424544 else 0x4C494E4B).put(ByteArray(48)).putInt(4)
            .putInt(124 + pixels.size + gap.size).putInt(profile.size).putInt(0)
        val body = header.array() + pixels + gap + profile + text("Test leftover")
        return text("BM") + ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(14 + body.size).putInt(0).putInt(14 + 124).array() + body
    }

    @Test
    fun `a BMP keeps its embedded color profile, and not a linked profile's name`() {
        val profile = text("ICC profile: how the colors look").copyOf(64)
        val embedded = sent(OutgoingMedia.of(file("profiled.bmp", bitmapWithProfile(embedded = true, profile)))!!)

        assertEquals(14 + 124 + 16 + 8 + profile.size, embedded.size)
        assertArrayEquals(profile, embedded.copyOfRange(embedded.size - profile.size, embedded.size))
        assertArrayEquals(ByteArray(8), embedded.copyOfRange(14 + 124 + 16, 14 + 124 + 24))

        val name = text("C:\\Users\\Test person\\colors.icm\u0000")
        val linked = sent(OutgoingMedia.of(file("linked.bmp", bitmapWithProfile(embedded = false, name)))!!)
        assertEquals(14 + 124 + 16, linked.size)
        assertFalse(linked.says("Test"))
    }

    @Test
    fun `a BMP that holds a PNG, or whose pixels aren't all there, doesn't go`() {
        val bitmap = picture("bmp")
        // Its pixels said to be a PNG's, as BI_PNG says.
        val png = bitmap.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(30, 5) }
        // A width and height whose pixels would come to more than a number can say.
        val huge = bitmap.copyOfRange(0, 54).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(18, 0x60000000).putInt(22, 0x7FFFFFFF).putShort(28, 32) }

        assertNull(OutgoingMedia.of(file("png.bmp", png)))
        assertNull(OutgoingMedia.of(file("cut.bmp", bitmap.copyOfRange(0, bitmap.size - 10))))
        assertNull(OutgoingMedia.of(file("huge.bmp", huge)))
        assertNull(OutgoingMedia.picture(huge))
    }

    @Test
    fun `a camera's own box in the movie goes, as Canon's with where it was filmed`() {
        val canon = byteArrayOf(0x85.toByte(), 0xC0.toByte(), 0xB6.toByte(), 0x87.toByte(), 0x82.toByte(), 0x0F, 0x11, 0xE0.toByte(), 0x81.toByte(), 0x11, 0xF4.toByte(), 0xCE.toByte(), 0x46, 0x2B, 0x6A, 0x48)
        val video = box("ftyp", text("isom"), int(0), text("isom")) + box("moov", header("mvhd"), box("uuid", canon, box("CMT4", text("GPS $place")))) + box("mdat", frames)
        val bytes = sent(OutgoingMedia.of(file("canon.mp4", video))!!)

        assertEquals(video.size, bytes.size)
        assertFalse(bytes.says(place))
    }

    @Test
    fun `a drawing's pictures go without where they were taken, and one that can't be read is left out`() {
        val photo = Base64.getEncoder().encodeToString(cameraJpeg())
        val cut = Base64.getEncoder().encodeToString(cameraJpeg().copyOfRange(0, 200))
        val svg = """<svg><image href="data:image/jpeg;base64,$photo"/><image href="data:image/jpeg;base64,$cut"/></svg>"""
        val drawn = OutgoingMedia.drawing(svg)

        val pictures = Regex("""data:image/jpeg;base64,([A-Za-z0-9+/=]+)""").findAll(drawn).map { Base64.getDecoder().decode(it.groupValues[1]) }.toList()
        assertEquals(1, pictures.size)
        assertFalse(pictures.single().says(place))
        assertTrue(drawn.contains("href=\"data:,\""))
    }

    @Test
    fun `copies for the room are made once, again when their file changes, and not for files chosen to go as they are`() {
        var now = 1_000_000_000L
        val folder = File(temporary.root, "outgoing")
        val copies = OutgoingCopies(folder) { now }
        val video = file("video.mp4", cameraVideo())

        val copy = copies.of(video)!!
        assertFalse(copy.readBytes().says(place))
        val made = copy.lastModified()
        assertEquals(copy, copies.of(video))
        assertEquals(made, copies.of(video)!!.lastModified())

        video.writeBytes(cameraVideo() + ByteArray(0))
        video.setLastModified(video.lastModified() + 5_000)
        assertFalse(copies.of(video)!!.readBytes().says(place))

        // A file that can't go clean isn't served, but as the person chose: then it's the file itself.
        val unknown = file("picture.jpg", ByteArray(64))
        assertNull(copies.of(unknown))
        assertSame(unknown, copies.of(unknown, setOf("picture.jpg")))

        // Copies a day old go as the next presentation starts.
        now += 2 * 24 * 60 * 60 * 1000L
        folder.listFiles()!!.forEach { it.setLastModified(now - 25 * 60 * 60 * 1000L) }
        OutgoingCopies(folder) { now }
        assertEquals(0, folder.listFiles()!!.size)
    }

    @Test
    fun `a file that can't go clean goes as it is only when the person chose so, wherever it goes`() {
        val cut = cameraJpeg().let { it.copyOfRange(0, it.size / 3) }
        val picture = file("picture-cut.jpg", cut)
        assertNull(OutgoingMedia.of(picture, emptySet()))
        assertNull(OutgoingMedia.of(picture, setOf("another.jpg")))
        assertArrayEquals(cut, sent(OutgoingMedia.of(picture, setOf("picture-cut.jpg"))!!))

        // One that goes clean goes clean, chosen or not.
        val photo = file("photo.jpg", cameraJpeg())
        assertFalse(sent(OutgoingMedia.of(photo, setOf("photo.jpg"))!!).says(place))

        // In a drawing, a picture that can't be read through stays in, as it is, once such pictures go as they are.
        val inside = Base64.getEncoder().encodeToString(cut)
        val drawn = OutgoingMedia.drawing("<svg><image href=\"data:image/jpeg;base64,$inside\"/></svg>", asTheyAre = true)
        assertTrue(drawn.contains("data:image/jpeg;base64,$inside"))

        // And the room's browsers get the file itself, never a copy.
        val copies = OutgoingCopies(File(temporary.root, "outgoing"))
        assertNull(copies.of(picture))
        assertSame(picture, copies.of(picture, setOf("picture-cut.jpg")))
    }

    /** Where `part` starts in these bytes, or -1. */
    private fun ByteArray.indexOf(part: ByteArray): Int = (0..size - part.size).firstOrNull { at -> part.indices.all { this[at + it] == part[it] } } ?: -1
}
