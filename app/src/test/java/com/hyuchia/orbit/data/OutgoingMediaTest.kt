package com.hyuchia.orbit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
    fun `how a file goes is worked out once while it's the same file, and again once it's written over`() {
        val file = temporary.newFile("video-1.mp4").apply { writeBytes(cameraVideo()) }
        val first = OutgoingMedia.of(file)!!
        assertSame(first, OutgoingMedia.of(file))

        // Written over, as a sync or an import replaces a file: another size, or another time, is another file.
        file.writeBytes(cameraVideo() + ByteArray(8))
        file.setLastModified(file.lastModified() + 2_000)
        val again = OutgoingMedia.of(file)!!
        assertNotSame(first, again)
        assertEquals(first.size, again.size)
    }

    @Test
    fun `a drawing bigger than any a map has isn't read whole to be cleaned, and goes only as it is`() {
        val drawing = temporary.newFile("plan.svg")
        drawing.writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>")
        assertTrue(OutgoingMedia.of(drawing) is OutgoingFile.Cleaned)

        // 33 MiB that start as a drawing does: never read past its start.
        java.io.RandomAccessFile(drawing, "rw").use { it.setLength(33L * 1024 * 1024) }
        assertNull(OutgoingMedia.of(drawing))
        assertTrue(OutgoingMedia.of(drawing, chosenAsTheyAre(drawing)) is OutgoingFile.AsIs)
    }

    @Test
    fun `a JPEG goes without where it was taken, with the same picture`() {
        val photo = file("photo.jpg", cameraJpeg())
        val outgoing = OutgoingMedia.of(photo)

        // Written from its file as it's read, so a big one is never whole in memory, and the same as cleaned in memory.
        assertTrue(outgoing is OutgoingFile.Pieced)
        val bytes = sent(outgoing!!)
        assertEquals(bytes.size.toLong(), outgoing.size)
        assertArrayEquals(OutgoingMedia.picture(cameraJpeg()), bytes)
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

    @Test
    fun `a movie cut short in its media data after a whole one doesn't go, as its index points at frames that aren't there`() {
        // As the review's probe made one: the movie's frames in a second media data box, cut short after a whole first one.
        val type = box("ftyp", text("isom"), int(0), text("isom"))
        val movie = { chunkAt: Int -> box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stco", full(0, int(1), int(chunkAt)))))))) }
        val first = box("mdat", text("Test complete frames"))
        val later = box("mdat", text("Test later frames that aren't whole"))
        val laterAt = type.size + movie(0).size + first.size
        val whole = type + movie(laterAt + 8) + first + later
        val cut = whole.copyOfRange(0, laterAt + 13)

        assertEquals(whole.size, sent(OutgoingMedia.of(file("whole.mp4", whole))!!).size)
        assertNull(OutgoingMedia.of(file("cut.mp4", cut)))
        assertNull(OutgoingMedia.picture(cut))
        // Cut in the later box's header, before its type: the movie's chunk is past the end all the same.
        assertNull(OutgoingMedia.of(file("cut-in-header.mp4", whole.copyOfRange(0, laterAt + 6))))

        // A fragmented movie, its last fragment's media data cut short: that fragment's frames aren't there either.
        // Each fragment's track says its base is the fragment's start, and its run of one sample where its data is from there.
        val fragmentAt = { offset: Int ->
            val run = box("trun", byteArrayOf(0, 0, 0x02, 0x01), int(1), int(offset), int(frames.size))
            box("moof", box("mfhd", full(0, int(1))), box("traf", box("tfhd", byteArrayOf(0, 0x02, 0, 0), int(1)), run))
        }

        val fragment =fragmentAt(fragmentAt(0).size + 8) + box("mdat", frames)
        val movieOfFragments = type + box("moov", header("mvhd"), box("mvex", box("trex", full(0, int(1), int(1), int(0), int(0), int(0)))))
        val fragmented = movieOfFragments + fragment + fragment
        assertEquals(fragmented.size, sent(OutgoingMedia.of(file("fragmented.mp4", fragmented))!!).size)
        assertNull(OutgoingMedia.of(file("fragment-cut.mp4", fragmented.copyOfRange(0, fragmented.size - 100))))
        assertNull(OutgoingMedia.of(file("fragment-header-cut.mp4", fragmented.copyOfRange(0, fragmented.size - frames.size - 4))))
        // A fragment after the media data it points back at, as some writers put it, is whole all the same.
        val dataFirst = movieOfFragments + box("mdat", frames) + fragmentAt(-frames.size)
        assertEquals(dataFirst.size, sent(OutgoingMedia.of(file("data-first.mp4", dataFirst))!!).size)

        // A HEIF picture whose media data says it goes on past the end of the file: its items are in a box that isn't whole.
        val heif = cameraHeif(locationsVersion = 0) + text("Test frames cut away")
        val mediaAt = String(heif, Charsets.ISO_8859_1).indexOf("mdat") - 4
        val longer = heif.copyOf().also { ByteBuffer.wrap(it).putInt(mediaAt, heif.size - mediaAt + 100) }
        assertNull(OutgoingMedia.of(file("cut.heic", longer)))
        assertNull(OutgoingMedia.picture(longer))
    }

    private val image = ByteArray(64) { (it * 3).toByte() }

    /**
     * A HEIF picture: its image and its EXIF item in `mdat`, where the item
     * locations say, and, with locations of version 1, an XMP item in `idat`,
     * as phones write them.
     */
    private fun cameraHeif(locationsVersion: Int, xmpName: String = "XMP", xmpType: String = "application/rdf+xml"): ByteArray {
        val exif = int(6) + text("Exif\u0000\u0000") + text(place)
        val xmp = text("<x:xmpmeta>$place</x:xmpmeta>")
        val withXmp = locationsVersion > 0
        val entries = listOfNotNull(
            box("infe", full(2, short(1), short(0), text("hvc1"), text("\u0000"))),
            box("infe", full(2, short(2), short(0), text("Exif"), text("\u0000"))),
            box("infe", full(2, short(3), short(0), text("mime"), text("$xmpName\u0000"), text("$xmpType\u0000"))).takeIf { withXmp },
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
    fun `a HEIF picture's XMP goes however long its name, and any item with a content type goes`() {
        // As the review's probe named it: past the first 512 bytes of the item's information, its content type went unread.
        for ((name, type) in listOf("X".repeat(600) to "application/rdf+xml", "XMP" to "application/x-camera-notes", "XMP" to "")) {
            val heif = cameraHeif(locationsVersion = 1, xmpName = name, xmpType = type)
            val bytes = OutgoingMedia.picture(heif)!!

            assertEquals(heif.size, bytes.size)
            assertFalse("${name.length}, $type", bytes.says(place))
            assertTrue(bytes.indexOf(image) >= 0)
        }
    }

    @Test
    fun `a HEIF picture whose items say they're in more places than any picture is can't be read through, nor fill the memory`() {
        // Item locations whose places take no bytes to say, each item in 65,535 of them: the review's probe's allocation.
        val items = (1..64).fold(ByteArray(0)) { all, id -> all + short(id) + short(0) + short(0) + short(65_535) }
        val locations = box("iloc", full(1, byteArrayOf(0x00, 0x00), short(64), items))
        val entries = (1..64).map { box("infe", full(2, short(it), short(0), text("hvc1"), text("\u0000"))) }
        val info = box("iinf", full(0, short(entries.size)), *entries.toTypedArray())
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), byteArrayOf(0)))
        val heif = box("ftyp", text("heic"), int(0), text("mif1heic")) + box("meta", full(0, handler, info, locations)) + box("mdat", image)
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes

        assertNull(OutgoingMedia.of(file("places.heic", heif)))
        assertNull(OutgoingMedia.picture(heif))
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue("allocated $allocated bytes", allocated < 16L * 1024 * 1024)
    }

    @Test(timeout = 10_000)
    fun `a HEIF picture with tens of thousands of boxes and item places is read in about a pass`() {
        // An item in 65,535 places, each a byte of the media data, and 90,000 boxes before it that none of them is in.
        val boxes = 90_000
        val places = 65_535
        val entries = box("infe", full(2, short(1), short(0), text("hvc1"), text("\u0000")))
        val info = box("iinf", full(0, short(1)), entries)
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), byteArrayOf(0)))
        val type = box("ftyp", text("heic"), int(0), text("mif1heic"))

        val locations = { mediaAt: Int ->
            val extents = ByteBuffer.allocate(8 * places)

            for (place in 0 until places) {
                extents.putInt(mediaAt + 8 + place % image.size).putInt(1)
            }

            box("iloc", full(1, byteArrayOf(0x44, 0x00), short(1), short(1), short(0), short(0), short(places), extents.array()))
        }

        val meta = { mediaAt: Int -> box("meta", full(0, handler, info, locations(mediaAt))) }
        val free = ByteBuffer.allocate(8 * boxes)

        repeat(boxes) {
            free.putInt(8).put(text("free"))
        }

        val mediaAt = type.size + meta(0).size + free.capacity()
        val heif = type + meta(mediaAt) + free.array() + box("mdat", image)

        val bytes = sent(OutgoingMedia.of(file("boxes.heic", heif))!!)
        assertEquals(heif.size, bytes.size)
        assertTrue(bytes.indexOf(image) >= 0)
    }

    /**
     * A HEIF picture, or an AVIF one, whose items are named where it was
     * taken, as an app may name them: its picture (`coded`), and its EXIF
     * in the media data and XMP in the item data, which say nothing
     * themselves. The XMP's entry, of version 3, names it in letters of
     * more than a byte, and says how its content is encoded.
     */
    private fun namedPicture(brand: String, coded: String): ByteArray {
        val exif = int(6) + text("Exif\u0000\u0000") + ByteArray(8)
        val xmp = text("<x:xmpmeta/>")
        val named = "Lisboa, Café $place".toByteArray(Charsets.UTF_8)
        val entries = arrayOf(
            box("infe", full(2, short(1), short(0), text(coded), text("Picture taken at $place\u0000"))),
            box("infe", full(2, short(2), short(0), text("Exif"), text("$place\u0000"))),
            box("infe", full(3, int(3), short(0), text("mime"), named, byteArrayOf(0), text("application/rdf+xml\u0000"), text("gzip\u0000"))),
        )
        val info = box("iinf", full(0, short(entries.size)), *entries)

        fun locations(imageAt: Int, exifAt: Int): ByteArray {
            val item = { id: Int, method: Int, offset: Int, length: Int -> short(id) + short(method) + short(0) + short(1) + int(offset) + int(length) }
            return box("iloc", full(1, byteArrayOf(0x44, 0x00), short(3), item(1, 0, imageAt, image.size), item(2, 0, exifAt, exif.size), item(3, 1, 0, xmp.size)))
        }

        val type = box("ftyp", text(brand), int(0), text("mif1$brand"))
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), byteArrayOf(0)))
        val meta = { at: ByteArray -> box("meta", full(0, handler, info, at, box("idat", xmp))) }
        val mediaStart = type.size + meta(locations(0, 0)).size + 8
        return type + meta(locations(mediaStart, mediaStart + image.size)) + box("mdat", image, exif)
    }

    @Test
    fun `a HEIF or AVIF picture goes without what its items are named, every item where it was`() {
        for ((brand, coded) in listOf("heic" to "hvc1", "avif" to "av01")) {
            val picture = namedPicture(brand, coded)
            assertTrue(picture.says(place))

            val bytes = sent(OutgoingMedia.of(file("named.$brand", picture))!!)
            assertArrayEquals(brand, OutgoingMedia.picture(picture), bytes)
            assertFalse(brand, bytes.says(place))
            assertFalse(brand, bytes.says("Picture taken"))
            assertEquals(picture.size, bytes.size)

            // Every box where it was, the item locations byte for byte, and the picture where they say.
            val (before, _) = IsoBoxes.top(ArrayBytes(picture))
            val (after, _) = IsoBoxes.top(ArrayBytes(bytes))
            assertEquals(before.map { it.type to it.start }, after.map { it.type to it.start })
            val parts = { bytes: ByteArray, top: List<IsoBox> -> IsoBoxes.children(ArrayBytes(bytes), top.single { it.type == "meta" }, skip = 4)!! }
            val locations = parts(picture, before).single { it.type == "iloc" }
            assertArrayEquals(picture.copyOfRange(locations.start.toInt(), locations.end.toInt()), bytes.copyOfRange(locations.start.toInt(), locations.end.toInt()))
            assertEquals(picture.indexOf(image), bytes.indexOf(image))

            // Each item reads as it did, with no name: its type, and the XMP's content type and encoding, where readers look for them.
            val items = IsoBoxes.items(ArrayBytes(bytes), parts(bytes, after))!!
            assertEquals(listOf(1L to coded, 2L to "Exif", 3L to "mime"), items.map { it.id to it.type })
            assertEquals("application/rdf+xml", items.single { it.type == "mime" }.contentType)
            assertEquals(IsoBoxes.items(ArrayBytes(picture), parts(picture, before))!!.map { it.ranges }, items.map { it.ranges })
            val entries = IsoBoxes.children(ArrayBytes(bytes), parts(bytes, after).single { it.type == "iinf" }, skip = 6)!!
            val entriesBefore = IsoBoxes.children(ArrayBytes(picture), parts(picture, before).single { it.type == "iinf" }, skip = 6)!!
            assertEquals(entriesBefore.map { it.start to it.end }, entries.map { it.start to it.end })

            for (entry in entries.take(2)) {
                assertTrue(bytes.copyOfRange(entry.body.toInt() + 12, entry.end.toInt()).all { it == 0.toByte() })
            }

            assertArrayEquals(text("mime") + byteArrayOf(0) + text("application/rdf+xml\u0000gzip\u0000"), bytes.copyOfRange(entries[2].body.toInt() + 10, entries[2].body.toInt() + 10 + 30))
            assertTrue(bytes.copyOfRange(entries[2].body.toInt() + 40, entries[2].end.toInt()).all { it == 0.toByte() })
        }
    }

    /**
     * A HEIF picture as the review's probe made one: its picture and its
     * EXIF in the media data, the picture last, its entries of `version`,
     * which name them. Entries of versions 0 and 1, which have no type, come
     * after two of version 2, as a third item's.
     */
    private fun probedHeif(version: Int): ByteArray {
        val exif = int(6) + text("Exif\u0000\u0000") + ByteArray(8)

        val entries = if (version >= 2) {
            val id = { value: Int -> if (version == 3) int(value) else short(value) }
            arrayOf(
                box("infe", full(version, id(1), short(0), text("hvc1"), text("Name $place\u0000"))),
                box("infe", full(version, id(2), short(0), text("Exif"), text("\u0000"))),
            )
        } else {
            arrayOf(
                box("infe", full(2, short(1), short(0), text("hvc1"), text("\u0000"))),
                box("infe", full(2, short(2), short(0), text("Exif"), text("\u0000"))),
                box("infe", full(version, short(3), short(0), text("Old $place\u0000"), text("text/plain\u0000"))),
            )
        }

        val info = box("iinf", full(0, short(entries.size)), *entries)

        fun locations(imageAt: Int, exifAt: Int): ByteArray {
            val item = { id: Int, offset: Int, length: Int -> short(id) + short(0) + short(1) + int(offset) + int(length) }
            return box("iloc", full(0, byteArrayOf(0x44, 0x00), short(2), item(1, imageAt, image.size), item(2, exifAt, exif.size)))
        }

        val type = box("ftyp", text("heic"), int(0), text("mif1heic"))
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), byteArrayOf(0)))
        val meta = { at: ByteArray -> box("meta", full(0, handler, info, at)) }
        val mediaStart = type.size + meta(locations(0, 0)).size + 8
        return type + meta(locations(mediaStart + exif.size, mediaStart)) + box("mdat", exif, image)
    }

    /** A HEIF picture of its picture alone, in the media data at its end. */
    private fun onePicture(): ByteArray {
        val info = box("iinf", full(0, short(1)), box("infe", full(2, short(1), short(0), text("hvc1"), text("\u0000"))))
        val locations = { imageAt: Int -> box("iloc", full(0, byteArrayOf(0x44, 0x00), short(1), short(1), short(0), short(1), int(imageAt), int(image.size))) }
        val type = box("ftyp", text("heic"), int(0), text("mif1heic"))
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), byteArrayOf(0)))
        val meta = { at: ByteArray -> box("meta", full(0, handler, info, at)) }
        val mediaStart = type.size + meta(locations(0)).size + 8
        return type + meta(locations(mediaStart)) + box("mdat", image)
    }

    @Test
    fun `a HEIF picture cut short in the picture itself doesn't go, as it can't be shown`() {
        // As the review's probe found: the item's place goes on past the end of the file, so where it is can't be told, and it went as the boxes before it.
        for ((name, whole) in listOf("one.heic" to onePicture(), "probed.heic" to probedHeif(2))) {
            assertEquals(name, whole.size, sent(OutgoingMedia.of(file("whole-$name", whole))!!).size)
            val cut = whole.copyOfRange(0, whole.size - 10)
            assertNull(name, OutgoingMedia.of(file("cut-$name", cut)))
            assertNull(name, OutgoingMedia.picture(cut))
        }
    }

    @Test
    fun `an item's name goes from entries of every version, those without a type too`() {
        for (version in listOf(0, 1, 2, 3)) {
            val picture = probedHeif(version)
            assertTrue(picture.says(place))

            val bytes = sent(OutgoingMedia.of(file("v$version.heic", picture))!!)
            assertEquals(picture.size, bytes.size)
            assertFalse("version $version", bytes.says(place))
            assertEquals(picture.indexOf(image), bytes.indexOf(image))
        }
    }

    /**
     * A HEIF picture whose handler names the app that wrote it, and whose
     * properties describe it, with where it was taken, and say when it was
     * made, changed and taken, by the TAI clock, beside one that says how
     * big it is.
     */
    private fun describedPicture(): ByteArray {
        val info = box("iinf", full(0, short(1)), box("infe", full(2, short(1), short(0), text("hvc1"), text("\u0000"))))
        val locations = { imageAt: Int -> box("iloc", full(0, byteArrayOf(0x44, 0x00), short(1), short(1), short(0), short(1), int(imageAt), int(image.size))) }
        val size = box("ispe", full(0, int(24), int(16)))
        val description = box("udes", full(0, text("pt-BR\u0000"), text("Taken at $place\u0000"), text("Description $place\u0000"), text("tags,$place\u0000")))
        val dated = listOf("crtt", "mdft", "itai").map { type -> box(type, full(0, ByteArray(8) { (0x51 + it).toByte() }), if (type == "itai") byteArrayOf(0x40) else ByteArray(0)) }
        val properties = box("iprp", box("ipco", size, description, *dated.toTypedArray()), box("ipma", full(0, int(1), short(1), byteArrayOf(5), byteArrayOf(1, 2, 3, 4, 5))))
        val type = box("ftyp", text("heic"), int(0), text("mif1heic"))
        val handler = box("hdlr", full(0, int(0), text("pict"), ByteArray(12), text("Test writer at $place\u0000")))
        val meta = { at: ByteArray -> box("meta", full(0, handler, info, at, properties)) }
        val mediaStart = type.size + meta(locations(0)).size + 8
        return type + meta(locations(mediaStart)) + box("mdat", image)
    }

    @Test
    fun `a HEIF picture goes without what its handler and properties say beyond what it shows, every box where it was`() {
        val picture = describedPicture()
        val bytes = sent(OutgoingMedia.of(file("described.heic", picture))!!)

        assertArrayEquals(OutgoingMedia.picture(picture), bytes)
        assertEquals(picture.size, bytes.size)
        assertFalse(bytes.says(place))
        assertFalse(bytes.says("Test writer"))
        assertFalse(bytes.says("pt-BR"))
        assertEquals(picture.indexOf(image), bytes.indexOf(image))

        // Every box where it was: the handler keeps its type, how big the picture is stays, and the properties' boxes are as long as they were.
        val (before, _) = IsoBoxes.top(ArrayBytes(picture))
        val (after, _) = IsoBoxes.top(ArrayBytes(bytes))
        val parts = { bytes: ByteArray, top: List<IsoBox> -> IsoBoxes.children(ArrayBytes(bytes), top.single { it.type == "meta" }, skip = 4)!! }
        assertEquals(parts(picture, before).map { it.type to it.start }, parts(bytes, after).map { it.type to it.start })
        assertTrue(bytes.says("pict"))
        val properties = { bytes: ByteArray, top: List<IsoBox> -> IsoBoxes.children(ArrayBytes(bytes), IsoBoxes.children(ArrayBytes(bytes), parts(bytes, top).single { it.type == "iprp" })!!.single { it.type == "ipco" })!! }
        assertEquals(properties(picture, before).map { it.type to it.end }, properties(bytes, after).map { it.type to it.end })

        for (property in properties(bytes, after)) {
            val body = bytes.copyOfRange(property.body.toInt() + 4, property.end.toInt())

            when (property.type) {
                "ispe" -> assertArrayEquals(int(24) + int(16), body)
                // Its four texts, each empty, and zeros after them.
                "udes" -> assertTrue(body.all { it == 0.toByte() })
                // When, zero; the TAI clock's flags after it stay.
                "itai" -> assertArrayEquals(ByteArray(8) + byteArrayOf(0x40), body)
                else -> assertArrayEquals(property.type, ByteArray(8), body)
            }
        }
    }

    @Test
    fun `an item's bytes in tens of thousands of places are gathered once`() {
        val data = ByteArray(100) { it.toByte() }
        val item = HeifItem(1, "Exif", "", List(65_535) { (it % 100).toLong() to 1L })
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes

        val bytes = IsoBoxes.itemBytes(ArrayBytes(data), item, most = 1 shl 20)!!
        val allocated = threads.currentThreadAllocatedBytes - before

        assertEquals(65_535, bytes.size)
        assertEquals(42, bytes[142].toInt())
        assertTrue("allocated $allocated bytes", allocated < 16L * 1024 * 1024)
    }

    @Test
    fun `a JPEG goes without JFIF's thumbnail, or its extension's`() {
        val plain = picture("jpg")
        val jfifEnd = 4 + (((plain[4].toInt() and 0xFF) shl 8) or (plain[5].toInt() and 0xFF))
        // JFIF's fields, then a thumbnail 4 by 4 pixels, its RGB saying where the photo was taken; then JFXX's JPEG thumbnail.
        val thumbnail = text(place.padEnd(48, '.'))
        val jfif = plain.copyOfRange(4, 4 + 2 + 12).also { it[1] = (2 + 12 + 2 + 48).toByte() } + byteArrayOf(4, 4) + thumbnail
        val jfxx = text("JFXX\u0000") + byteArrayOf(0x10) + text("Test thumbnail, $place")
        val segment = { marker: Int, data: ByteArray -> byteArrayOf(0xFF.toByte(), marker.toByte(), ((data.size + 2) shr 8).toByte(), (data.size + 2).toByte()) + data }
        val photo = plain.copyOfRange(0, 2) + byteArrayOf(0xFF.toByte(), 0xE0.toByte()) + jfif + segment(0xE0, jfxx) + plain.copyOfRange(jfifEnd, plain.size)
        val bytes = sent(OutgoingMedia.of(file("thumbnail.jpg", photo))!!)

        assertFalse(bytes.says(place))
        assertFalse(bytes.says("Test"))
        // JFIF's segment as long as its fields, a thumbnail of no size, as the plain picture's; the rest of the picture as it was.
        assertEquals(20, jfifEnd)
        assertArrayEquals(plain, bytes)
        assertEquals(24, ImageIO.read(bytes.inputStream()).width)
    }

    @Test
    fun `a file written again under its name, at the same size and time, goes as it is now`() {
        // The same photo with its EXIF in two places: what goes of one keeps the other's EXIF.
        val first = cameraJpeg()
        val afterJfif = 4 + (((first[4].toInt() and 0xFF) shl 8) or (first[5].toInt() and 0xFF))
        val exifLength = 2 + (((first[afterJfif + 2].toInt() and 0xFF) shl 8) or (first[afterJfif + 3].toInt() and 0xFF))
        val exif = first.copyOfRange(afterJfif, afterJfif + exifLength)
        val rest = first.copyOfRange(afterJfif + exifLength, first.size)
        val tablesEnd = 2 + (((rest[2].toInt() and 0xFF) shl 8) or (rest[3].toInt() and 0xFF))
        val second = first.copyOfRange(0, afterJfif) + rest.copyOfRange(0, tablesEnd) + exif + rest.copyOfRange(tablesEnd, rest.size)
        assertEquals(first.size, second.size)

        // Written in place, its time set back as a sync app sets it.
        val photo = file("photo.jpg", first)
        val time = photo.lastModified()
        assertFalse(sent(OutgoingMedia.of(photo)!!).says(place))
        Thread.sleep(20)
        java.io.RandomAccessFile(photo, "rw").use { it.write(second) }
        photo.setLastModified(time)
        assertFalse(sent(OutgoingMedia.of(photo)!!).says(place))

        // Another file put in its place under its name.
        val other = file("other.jpg", first)
        other.setLastModified(time)
        assertFalse(sent(OutgoingMedia.of(other)!!).says(place))
        val replacement = file("replacement.jpg", second)
        replacement.setLastModified(time)
        java.nio.file.Files.move(replacement.toPath(), other.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertFalse(sent(OutgoingMedia.of(other)!!).says(place))

        // And the room's copy is made again for it.
        val copies = OutgoingCopies(File(temporary.root, "outgoing"))
        val again = file("again.jpg", first).also { it.setLastModified(time) }
        val copy = copies.of(again)!!
        Thread.sleep(20)
        java.io.RandomAccessFile(again, "rw").use { it.write(second) }
        again.setLastModified(time)
        val newCopy = copies.of(again)!!
        assertNotEquals(copy, newCopy)
        assertFalse(newCopy.readBytes().says(place))
    }

    @Test
    fun `an AVIF picture from ffmpeg goes as it is, but for its picture's and its handler's names`() {
        val avif = File("src/test/resources/outgoing/plain.avif")
        assertArrayEquals(ffmpegUnnamed(avif.readBytes()), sent(OutgoingMedia.of(avif)!!))
    }

    /**
     * An AVIF from ffmpeg as it goes when there's nothing else to take out:
     * the name ffmpeg gives its picture, "Color", and its picture's handler,
     * "PictureHandler", zeros.
     */
    private fun ffmpegUnnamed(avif: ByteArray): ByteArray {
        val name = avif.indexOf(text("av01Color\u0000")) + 4
        val handler = avif.indexOf(text("pict") + ByteArray(12) + text("PictureHandler")) + 16
        assertTrue(name > 4 && handler > 16)
        return avif.copyOf().also { it.fill(0, name, name + 5); it.fill(0, handler, handler + 14) }
    }

    /**
     * A Pixel's HEIC motion photo: its coded picture at 464 (2,149 bytes)
     * and its XMP, saying where the video is, at 2,613 (854 bytes), as its
     * item locations put them, then the video in an `mpvd` box.
     */
    private val motionPhoto = File("src/test/resources/live/google-motion-photo.heic")

    /** Where its `mpvd` box starts, and its picture's file ends. */
    private val motionPhotoVideo = 3467

    @Test
    fun `a HEIC motion photo goes without the video it keeps, and its picture as it was`() {
        val photo = motionPhoto.readBytes()
        val bytes = sent(OutgoingMedia.of(motionPhoto)!!)

        assertEquals(motionPhotoVideo, String(photo, Charsets.ISO_8859_1).indexOf("mpvd") - 4)
        // Up to where the video started: its type, its items and their properties, and its coded picture, byte for byte.
        assertEquals(motionPhotoVideo, bytes.size)
        assertArrayEquals(photo.copyOfRange(0, 2613), bytes.copyOfRange(0, 2613))
        // Its XMP is zeros.
        assertArrayEquals(ByteArray(854), bytes.copyOfRange(2613, motionPhotoVideo))
        assertFalse(bytes.says("mpvd"))
        assertFalse(bytes.says("moov"))
        // Inside a page or a drawing too.
        assertArrayEquals(bytes, OutgoingMedia.picture(photo))
    }

    @Test
    fun `what follows a motion photo's video in its box goes with it`() {
        // As the review's probe made one: the motion photo, with something after its video's last box, inside the `mpvd` box.
        val photo = motionPhoto.readBytes()
        val trailer = text("Test trailer, $place")
        val video = photo.copyOfRange(motionPhotoVideo, photo.size)
        val longer = photo.copyOfRange(0, motionPhotoVideo) + box("mpvd", video.copyOfRange(8, video.size), trailer)
        val bytes = sent(OutgoingMedia.of(file("probe.heic", longer))!!)

        assertFalse(bytes.says(place))
        assertFalse(bytes.says("Test"))
        assertArrayEquals(sent(OutgoingMedia.of(motionPhoto)!!), bytes)
    }

    @Test
    fun `an AVIF motion photo, with Samsung's trailer after its video, goes as the picture alone`() {
        val avif = File("src/test/resources/outgoing/plain.avif").readBytes()
        val video = File("src/test/resources/live/motion.mp4").readBytes()
        val samsung = box("sefd", text("MotionPhoto_Data"), video, text("Image_UTC_Data1761040800000"), text("SEFH Test trailer, $place SEFT"))
        val photo = avif + box("mpvd", video) + samsung

        assertArrayEquals(ffmpegUnnamed(avif), sent(OutgoingMedia.of(file("motion.avif", photo))!!))
    }

    @Test
    fun `boxes beside a HEIF picture's own become free space where they are, and a box its items are in stays`() {
        val heif = cameraHeif(locationsVersion = 0)
        val mediaAt = String(heif, Charsets.ISO_8859_1).indexOf("mdat") - 4
        // Content credentials and a video between its items and their data, in no box of a kind a picture is made of; an editor's leftovers after it.
        val credentials = box("uuid", ByteArray(16) { (0xD8 - it).toByte() }, text("c2pa Test editor, $place"))
        val between = credentials + box("mpvd", text("ftypisom $place"))
        val moved = heif.copyOfRange(0, mediaAt) + between + heif.copyOfRange(mediaAt, heif.size) + box("free", text("Test leftover"))
        // The item locations (version 0: an id, a data reference, an extent count, then its offset and length), moved with the media data.
        val items = String(moved, Charsets.ISO_8859_1).indexOf("iloc") + 4 + 4 + 2 + 2
        val imageAt = ByteBuffer.wrap(moved, items + 6, 4).int + between.size
        ByteBuffer.wrap(moved).putInt(items + 6, imageAt).putInt(items + 14 + 6, imageAt + image.size)
        val bytes = sent(OutgoingMedia.of(file("credentials.heic", moved))!!)

        assertFalse(bytes.says(place))
        assertFalse(bytes.says("Test"))
        // Free space where they were, so the picture's data stays where its items say; the leftovers after it, left out.
        assertEquals(moved.size - box("free", text("Test leftover")).size, bytes.size)
        assertEquals(credentials.size, ByteBuffer.wrap(bytes, mediaAt, 4).int)
        assertEquals("free", String(bytes, mediaAt + 4, 4, Charsets.ISO_8859_1))
        assertEquals("free", String(bytes, mediaAt + credentials.size + 4, 4, Charsets.ISO_8859_1))
        assertEquals(imageAt, bytes.indexOf(image))

        // A picture whose data is in a box of another kind keeps it: what it shows is there.
        val elsewhere = heif.copyOfRange(0, mediaAt) + box("abcd", heif.copyOfRange(mediaAt + 8, heif.size))
        val kept = sent(OutgoingMedia.of(file("elsewhere.heic", elsewhere))!!)
        assertEquals(elsewhere.size, kept.size)
        assertEquals(mediaAt + 8, kept.indexOf(image))
        assertFalse(kept.says(place))
    }

    @Test
    fun `an animated AVIF keeps every frame where it is, and goes without when it was made`() {
        // As ffmpeg writes one, made on 21 October 2026: its movie's, track's and media's headers say so.
        val avif = File("src/test/resources/outgoing/animated.avif")
        val original = avif.readBytes()
        val made = int(0xE6FE3DA0.toInt())
        assertTrue(original.indexOf(made) > 0)

        val bytes = sent(OutgoingMedia.of(avif)!!)
        assertEquals(original.size, bytes.size)
        assertEquals(-1, bytes.indexOf(made))
        // The encoder named in its frames' description, and its track's handler and the picture's own named, say nothing.
        assertFalse(bytes.says("Lavc"))
        assertEquals(2, Regex("PictureHandler").findAll(String(original, Charsets.ISO_8859_1)).count())
        assertFalse(bytes.says("PictureHandler"))
        // Its picture, but for its and its handler's names, its frames, and how they play, byte for byte.
        val movie = String(original, Charsets.ISO_8859_1).indexOf("moov") - 4
        assertArrayEquals(ffmpegUnnamed(original).copyOfRange(0, movie), bytes.copyOfRange(0, movie))
        val media = String(original, Charsets.ISO_8859_1).indexOf("mdat") - 4
        assertArrayEquals(original.copyOfRange(media, original.size), bytes.copyOfRange(media, original.size))

        for (table in listOf("stts", "stss", "stsc", "stsz", "stco")) {
            val at = String(original, Charsets.ISO_8859_1).indexOf(table) - 4
            val size = ByteBuffer.wrap(original, at, 4).int
            assertArrayEquals(table, original.copyOfRange(at, at + size), bytes.copyOfRange(at, at + size))
        }
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
        assertArrayEquals(unknown.readBytes(), sent(OutgoingMedia.of(unknown, chosenAsTheyAre(unknown))!!))
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
    fun `a drawing keeps only the pictures chosen to go as they are`() {
        // As the review's probe asked: one picture that can't go clean shared as it is, then another one left out.
        val approvedBytes = "approved picture metadata, $place".toByteArray()
        val declinedBytes = "declined picture metadata, $place".toByteArray()
        val approvedFile = file("approved.jpg", approvedBytes)
        file("declined.jpg", declinedBytes)
        val declinedFile = File(temporary.root, "declined.jpg")
        val stamps = mapOf("approved.jpg" to FileStamp.of(approvedFile)!!, "declined.jpg" to FileStamp.of(declinedFile)!!)
        val consent = com.hyuchia.orbit.ui.AsTheyAre()
        var answers = AsTheyAreFiles()
        consent.then(Uncleaned(pictures = setOf("approved.jpg"), stamps = stamps)) { answers = it }
        consent.answer(consent.question!!, true)
        consent.then(Uncleaned(pictures = setOf("approved.jpg", "declined.jpg"), stamps = stamps)) { answers = it }
        consent.answer(consent.question!!, false)
        val names = setOf("approved.jpg", "declined.jpg").filter { stamps.getValue(it) in answers.chosen }.toSet()
        assertEquals(setOf("approved.jpg"), names)

        val approved = Base64.getEncoder().encodeToString(approvedBytes)
        val declined = Base64.getEncoder().encodeToString(declinedBytes)
        // A picture from the web that can't go clean, which no one was asked about, and the approved one again, drawn twice.
        val fromTheWeb = Base64.getEncoder().encodeToString("a web picture, $place".toByteArray())
        val svg = "<svg><image href=\"data:image/jpeg;base64,$approved\"/><image href=\"data:image/jpeg;base64,$declined\"/>" +
            "<image href=\"data:image/png;base64,$fromTheWeb\"/><image href=\"data:image/jpeg;base64,$approved\"/></svg>"
        val drawn = OutgoingMedia.drawing(svg, asTheyAre = answers)
        val observed = "approved=${approved in drawn}, declined=${declined in drawn}, names=$names"

        assertEquals(observed, 2, Regex(Regex.escape(approved)).findAll(drawn).count())
        assertFalse(observed, declined in drawn)
        assertFalse(observed, fromTheWeb in drawn)
        assertEquals(2, Regex("href=\"data:,\"").findAll(drawn).count())
        // Chosen or not, nothing goes as it is when nothing was chosen.
        assertFalse(approved in OutgoingMedia.drawing(svg))
    }

    @Test
    fun `copies for the room are made once, again when their file changes, and not for files chosen to go as they are`() {
        var now = 1_000_000_000L
        val folder = File(temporary.root, "outgoing")
        val copies = OutgoingCopies(folder, now = { now }, deleting = { it() })
        val video = file("video.mp4", cameraVideo())

        val copy = copies.of(video)!!
        assertFalse(copy.readBytes().says(place))
        val made = copy.lastModified()
        assertEquals(copy, copies.of(video))
        assertEquals(made, copies.of(video)!!.lastModified())

        video.writeBytes(cameraVideo() + ByteArray(0))
        video.setLastModified(video.lastModified() + 5_000)
        assertFalse(copies.of(video)!!.readBytes().says(place))

        // A file that can't go clean isn't served, but as the person chose: then it's a copy of that very file.
        val unknown = file("picture.jpg", ByteArray(64))
        assertNull(copies.of(unknown))
        val asItIs = copies.of(unknown, chosenAsTheyAre(unknown))!!
        assertNotEquals(unknown, asItIs)
        assertArrayEquals(unknown.readBytes(), asItIs.readBytes())

        // Copies a day old go as the next presentation starts, and so do those of files as they are.
        now += 2 * 24 * 60 * 60 * 1000L
        folder.listFiles()!!.filter { it.isFile }.forEach { it.setLastModified(now - 25 * 60 * 60 * 1000L) }
        OutgoingCopies(folder, now = { now }, deleting = { it() })
        assertEquals(emptyList<File>(), folder.walk().filter { it.isFile }.toList())
    }

    @Test
    fun `a file that can't go clean goes as it is only when the person chose so, wherever it goes`() {
        val cut = cameraJpeg().let { it.copyOfRange(0, it.size / 3) }
        val picture = file("picture-cut.jpg", cut)
        assertNull(OutgoingMedia.of(picture, AsTheyAreFiles.NONE))
        assertNull(OutgoingMedia.of(picture, chosenAsTheyAre(file("another.jpg", ByteArray(64))).lasting()))
        assertArrayEquals(cut, sent(OutgoingMedia.of(picture, chosenAsTheyAre(picture))!!))

        // One that goes clean goes clean, chosen or not.
        val photo = file("photo.jpg", cameraJpeg())
        assertFalse(sent(OutgoingMedia.of(photo, chosenAsTheyAre(photo))!!).says(place))

        // In a drawing, a picture that can't be read through stays in, as it is, once it was chosen to go as it is.
        val inside = Base64.getEncoder().encodeToString(cut)
        val drawn = OutgoingMedia.drawing("<svg><image href=\"data:image/jpeg;base64,$inside\"/></svg>", asTheyAre = chosenAsTheyAre(picture))
        assertTrue(drawn.contains("data:image/jpeg;base64,$inside"))

        // And the room's browsers get a copy of that very file, never the map's own.
        val copies = OutgoingCopies(File(temporary.root, "outgoing"))
        assertNull(copies.of(picture))
        val copy = copies.of(picture, chosenAsTheyAre(picture))!!
        assertNotEquals(picture, copy)
        assertArrayEquals(cut, copy.readBytes())
    }

    @Test
    fun `an answer to share a file as it is holds for that very file, never another put in its place under its name`() {
        // The review's probe: a file Orbit can't clean, chosen to go as it is, then another written in its place, the same size and time.
        val file = file("consent-name-only.bin", "ORIGINAL-UNCLEANABLE-SECRET".toByteArray())
        val stamp = OutgoingMedia.uncleanable(file)!!
        val answers = AsTheyAreFiles(asked = setOf(stamp), chosen = setOf(stamp))
        val planned = OutgoingMedia.of(file, answers)!!
        assertTrue(planned is OutgoingFile.AsIs)
        assertArrayEquals("ORIGINAL-UNCLEANABLE-SECRET".toByteArray(), sent(planned))

        val time = file.lastModified()
        writeAtomically(file, "REPLACED!-UNCLEANABLE-SECRET".toByteArray().copyOf(27))
        file.setLastModified(time)

        // A way out that can ask stops for it, so the person is asked about it; one that can't leaves it here.
        assertThrows(FileChanged::class.java) { OutgoingMedia.of(file, answers) }
        assertNull(OutgoingMedia.of(file, answers.lasting()))
        // And what was planned for the first writes nothing of the second.
        val output = ByteArrayOutputStream()
        assertThrows(FileChanged::class.java) { planned.writeTo(output) }
        assertEquals(0, output.size())
    }

    @Test
    fun `a plan made before its file was replaced writes nothing, and the new file is planned afresh`() {
        // The review's probe: a WAV planned, then another of the same size put in its place, with a secret where the first had its tags.
        val format = waveChunk("fmt ", le16(1) + le16(1) + le32(8000) + le32(8000) + le16(1) + le16(8))
        val data = waveChunk("data", byteArrayOf(42, 43))
        val first = wave(waveChunk("LIST", text("OLD-SECRET-12345")) + format + data)
        val second = wave(format + data + waveChunk("LIST", text("NEW-SECRET-67890")))
        assertEquals(first.size, second.size)
        val file = file("plan-write-race.wav", first)
        val stale = OutgoingMedia.of(file)!!

        writeAtomically(file, second)
        val output = ByteArrayOutputStream()
        assertThrows(FileChanged::class.java) { stale.writeTo(output) }
        assertEquals(0, output.size())

        val fresh = sent(OutgoingMedia.of(file)!!)
        assertFalse(fresh.says("NEW-SECRET-67890"))
        assertEquals("fmt ", String(fresh, 12, 4, Charsets.ISO_8859_1))
    }

    @Test
    fun `what a file open to be read is, is told by its descriptor, never by its path`() {
        val file = file("photo.jpg", cameraJpeg())
        val stamp = FileStamp.of(file)!!

        java.io.RandomAccessFile(file, "r").use { opened ->
            assertTrue(stamp.isOpenAt(opened.fd))

            // Another file in its place: its path says the new one, which isn't the one open.
            writeAtomically(file, "another file".toByteArray())
            val now = FileStamp.of(file)!!
            assertNotEquals(stamp, now)
            assertFalse(now.isOpenAt(opened.fd))
        }
    }

    @Test
    fun `a file written in place as it's copied is thrown away, with all that was written of it`() {
        val unknown = file("picture.jpg", ByteArray(64))
        val outgoing = OutgoingMedia.of(unknown, chosenAsTheyAre(unknown))!!
        val exports = temporary.newFolder("exports")

        // As its first bytes are written, something writes more into the file itself.
        val made = runCatching {
            kotlinx.coroutines.runBlocking {
                com.hyuchia.orbit.ui.makeExport(exports, "picture.jpg") { output ->
                    outgoing.writeTo(
                        object : java.io.FilterOutputStream(output) {
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                java.io.FileOutputStream(unknown, true).use { it.write(ByteArray(8)) }
                                out.write(bytes, offset, length)
                            }
                        },
                    )

                    true
                }
            }
        }

        assertTrue(made.exceptionOrNull() is FileChanged)
        assertEquals(emptyList<File>(), exports.walk().filter { it.isFile }.toList())
    }

    @Test
    fun `the room gets a copy of the file chosen to go as it is, made when it's first asked for, and gone as the presentation ends`() {
        val folder = File(temporary.root, "outgoing")
        val copies = OutgoingCopies(folder, deleting = { it() })
        val cut = cameraJpeg().let { it.copyOfRange(0, it.size / 3) }
        val picture = file("picture-cut.jpg", cut)
        val answers = chosenAsTheyAre(picture)
        assertFalse(File(folder, "as-they-are").exists())

        val copy = copies.of(picture, answers)!!
        assertArrayEquals(cut, copy.readBytes())
        assertEquals(copy, copies.of(picture, answers))

        // Another picture Orbit can't clean in its place isn't served for it, and one it can clean goes clean.
        writeAtomically(picture, cut.copyOfRange(0, cut.size / 2))
        assertNull(copies.of(picture, answers))
        writeAtomically(picture, cameraJpeg())
        assertFalse(copies.of(picture, answers)!!.readBytes().says(place))

        // The presentation ends: the copies of files as they are go, and the clean ones stay for the next.
        copies.forgetAsTheyAre()
        assertFalse(copy.exists())
        assertEquals(emptyList<File>(), File(folder, "as-they-are").walk().filter { it.isFile }.toList())
    }

    @Test
    fun `a copy for the room is begun only where there's room for it`() {
        val folder = File(temporary.root, "outgoing")
        val copies = OutgoingCopies(folder, room = { _, size -> throw OutputFailed(java.io.IOException("No room for $size bytes")) }, deleting = { it() })
        val picture = file("picture-cut.jpg", cameraJpeg().copyOfRange(0, 300))

        assertThrows(OutputFailed::class.java) { copies.of(picture, chosenAsTheyAre(picture)) }
        assertEquals(emptyList<File>(), folder.walk().filter { it.isFile }.toList())
    }

    @Test
    fun `a drawing's picture chosen to go as it is goes only while it's that very file`() {
        val cut = cameraJpeg().let { it.copyOfRange(0, it.size / 3) }
        val picture = file("picture-cut.jpg", cut)
        val answers = chosenAsTheyAre(picture).copy(stops = true)

        // Another picture in its place, which the map's page draws: it isn't the one chosen.
        val other = cut.copyOfRange(0, cut.size - 10)
        writeAtomically(picture, other)
        val svg = "<svg><image href=\"data:image/jpeg;base64,${Base64.getEncoder().encodeToString(other)}\"/></svg>"

        assertThrows(FileChanged::class.java) { OutgoingMedia.drawing(svg, answers) }
        val drawn = OutgoingMedia.drawing(svg, answers.lasting())
        assertFalse(drawn.contains(Base64.getEncoder().encodeToString(other)))
        assertTrue(drawn.contains("href=\"data:,\""))
    }

    @Test
    fun `a drawing goes once a chosen picture was asked about again, went, or goes clean now`() {
        // The review's case: a picture chosen as it is, replaced, asked about again and chosen again.
        val cut = cameraJpeg().let { it.copyOfRange(0, it.size / 3) }
        val picture = file("picture-cut.jpg", cut)
        val first = FileStamp.of(picture)!!
        val other = cut.copyOfRange(0, cut.size - 10)
        writeAtomically(picture, other)
        val second = FileStamp.of(picture)!!
        val answers = AsTheyAreFiles(asked = setOf(first, second), chosen = setOf(first, second))
        val inside = Base64.getEncoder().encodeToString(other)
        val svg = "<svg><image href=\"data:image/jpeg;base64,$inside\"/></svg>"
        assertTrue(OutgoingMedia.drawing(svg, answers).contains(inside))

        // One that goes clean now goes clean, chosen as it was or not.
        writeAtomically(picture, cameraJpeg())
        assertFalse(OutgoingMedia.drawing(svg, answers).contains(inside))

        // And one that went holds nothing, and stops nothing.
        picture.delete()
        assertTrue(OutgoingMedia.drawing(svg, answers).contains("href=\"data:,\""))
    }

    @Test
    fun `a link put in a file's place is another file, never read as the one it leads to`() {
        val photo = file("photo.jpg", cameraJpeg())
        val link = File(temporary.root, "link.jpg")
        java.nio.file.Files.createSymbolicLink(link.toPath(), photo.toPath())

        assertNotEquals(FileStamp.of(photo)!!.identity, FileStamp.of(link)!!.identity)
        assertNull(OutgoingMedia.judge(link))
        assertNotNull(OutgoingMedia.judge(photo)?.clean)
    }

    @Test
    fun `a copy of a file as it is begun as the presentation ends goes too`() {
        val folder = File(temporary.root, "outgoing")
        val copying = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val copies = OutgoingCopies(
            folder,
            room = { _, _ ->
                copying.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            },
            deleting = { it() },
        )
        val picture = file("picture-cut.jpg", cameraJpeg().copyOfRange(0, 300))
        val asked = java.util.concurrent.CompletableFuture.supplyAsync { copies.of(picture, chosenAsTheyAre(picture)) }

        // The presentation ends, as a private map locks, while the copy is being made.
        assertTrue(copying.await(5, java.util.concurrent.TimeUnit.SECONDS))
        copies.forgetAsTheyAre()
        release.countDown()

        assertNull(asked.get(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(emptyList<File>(), folder.walk().filter { it.isFile }.toList())
    }

    @Test
    fun `browsers asking for the same file at once wait for one copy`() {
        val folder = File(temporary.root, "outgoing")
        val copying = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val begun = java.util.concurrent.atomic.AtomicInteger()
        val made = mutableListOf<File>()
        val copies = OutgoingCopies(
            folder,
            room = { _, _ ->
                begun.incrementAndGet()
                copying.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            },
            madeAsItIs = { synchronized(made) { made += it } },
            deleting = { it() },
        )
        val video = file("video.mp4", cameraVideo())
        val first = java.util.concurrent.CompletableFuture.supplyAsync { copies.of(video) }
        assertTrue(copying.await(5, java.util.concurrent.TimeUnit.SECONDS))

        // Three more browsers ask while it's being made: they wait for it, rather than make three more.
        val others = (1..3).map { Thread { copies.of(video) }.apply { start() } }
        val deadline = System.currentTimeMillis() + 5_000

        while (others.any { it.state != Thread.State.BLOCKED } && System.currentTimeMillis() < deadline) {
            Thread.yield()
        }

        assertTrue(others.all { it.state == Thread.State.BLOCKED })
        release.countDown()
        others.forEach { it.join(5_000) }

        assertEquals(1, begun.get())
        assertEquals(1, folder.walk().filter { it.isFile }.count())
        assertFalse(first.get(5, java.util.concurrent.TimeUnit.SECONDS)!!.readBytes().says(place))

        // A copy of a file as it is is told of as it's made, for a private map's to be kept with the copies that go as maps lock.
        val cut = file("picture-cut.jpg", cameraJpeg().copyOfRange(0, 300))
        val copy = copies.of(cut, chosenAsTheyAre(cut))!!
        assertEquals(listOf(copy), made)
        assertEquals("picture-cut.jpg", copy.name)
    }

    @Test
    fun `browsers still waiting for a copy of a file as it is as the presentation ends make none again`() {
        val folder = File(temporary.root, "outgoing")
        val copying = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val begun = java.util.concurrent.atomic.AtomicInteger()
        val copies = OutgoingCopies(
            folder,
            room = { _, _ ->
                begun.incrementAndGet()
                copying.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            },
            // What the end deletes, off the thread that asks, reaches the copy right as it's made.
            madeAsItIs = { made -> made.parentFile?.deleteRecursively() },
            deleting = { it() },
        )
        val picture = file("picture-cut.jpg", cameraJpeg().copyOfRange(0, 300))
        val answers = chosenAsTheyAre(picture)
        val first = java.util.concurrent.CompletableFuture.supplyAsync { copies.of(picture, answers) }
        assertTrue(copying.await(5, java.util.concurrent.TimeUnit.SECONDS))

        // Two more browsers wait for its copy, and the presentation ends: none of them copies it again, only for it to go.
        val served = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val waiting = (1..2).map { Thread { served += copies.of(picture, answers)?.path ?: "nothing" }.apply { start() } }
        val deadline = System.currentTimeMillis() + 5_000

        while (waiting.any { it.state != Thread.State.BLOCKED } && System.currentTimeMillis() < deadline) {
            Thread.yield()
        }

        assertTrue(waiting.all { it.state == Thread.State.BLOCKED })
        copies.forgetAsTheyAre()
        release.countDown()

        assertNull(first.get(5, java.util.concurrent.TimeUnit.SECONDS))
        waiting.forEach { it.join(5_000) }
        assertEquals(listOf("nothing", "nothing"), served.toList())
        assertEquals(1, begun.get())
        assertEquals(emptyList<File>(), folder.walk().filter { it.isFile }.toList())
    }

    @Test
    fun `once the room stops, no copy of a file as it is is made`() {
        val folder = File(temporary.root, "outgoing")
        val begun = java.util.concurrent.atomic.AtomicInteger()
        val copies = OutgoingCopies(folder, room = { _, _ -> begun.incrementAndGet() }, deleting = { it() })
        val picture = file("picture-cut.jpg", cameraJpeg().copyOfRange(0, 300))
        val video = file("video.mp4", cameraVideo())

        copies.close()

        // A request still on its way as it stopped: refused before anything is copied. A clean copy, which says nothing, still goes.
        assertNull(copies.of(picture, chosenAsTheyAre(picture)))
        assertEquals(0, begun.get())
        assertEquals(emptyList<File>(), File(folder, "as-they-are").walk().filter { it.isFile }.toList())
        assertNotNull(copies.of(video))
    }

    private fun le16(value: Int): ByteArray = byteArrayOf(value.toByte(), (value shr 8).toByte())

    private fun le32(value: Int): ByteArray = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun waveChunk(type: String, content: ByteArray): ByteArray = text(type) + le32(content.size) + content

    private fun wave(chunks: ByteArray): ByteArray = (text("WAVE") + chunks).let { body -> text("RIFF") + le32(body.size) + body }

    /** Where `part` starts in these bytes, or -1. */
    private fun ByteArray.indexOf(part: ByteArray): Int = (0..size - part.size).firstOrNull { at -> part.indices.all { this[at + it] == part[it] } } ?: -1
}
