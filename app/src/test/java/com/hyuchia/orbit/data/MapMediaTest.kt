package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.nearby.OFFER_LIMIT
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class MapMediaTest {
    private val locale = Locale.getDefault()
    private val words = FileWords(Locale.US)

    @Before
    fun englishNumbers() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(locale)
    }

    @Test
    fun `a video keeps the extension its type has`() {
        assertEquals("mp4", videoExtension("video/mp4", "VID_20261012.mp4"))
        assertEquals("mov", videoExtension("video/quicktime", "IMG_0042.MOV"))
        assertEquals("webm", videoExtension("video/webm", null))
        assertEquals("m4v", videoExtension("video/x-m4v", "clip.m4v"))
        assertEquals("mp4", videoExtension("Video/MP4; codecs=avc1", "clip"))
        // The type wins over a name that says otherwise.
        assertEquals("mp4", videoExtension("video/mp4", "clip.webm"))
    }

    @Test
    fun `a video whose type says nothing is known by its name, and only as one Orbit keeps`() {
        assertEquals("mp4", videoExtension(null, "clip.MP4"))
        assertEquals("mov", videoExtension("application/octet-stream", "clip.mov"))
        assertNull(videoExtension(null, "clip.mkv"))
        assertNull(videoExtension(null, "clip"))
        assertNull(videoExtension(null, null))
        assertNull(videoExtension("application/octet-stream", "page.html"))
    }

    @Test
    fun `videos of kinds Orbit can't keep in a map have no extension`() {
        assertNull(videoExtension("video/3gpp", "clip.3gp"))
        assertNull(videoExtension("video/x-matroska", "clip.mkv"))
        assertNull(videoExtension("image/jpeg", "photo.jpg"))
        // A name can't sneak a kind in past a type that says what it is.
        assertNull(videoExtension("video/3gpp", "clip.mp4"))
    }

    @Test
    fun `a video up to the limit fits, and one past it is refused, saying how big it is`() {
        assertNull(videoRefusal(MEDIA_FILE_LIMIT, 0, words))
        assertEquals(250_000_000L, MEDIA_FILE_LIMIT)
        val refusal = videoRefusal(264_461_559, 0, words)!!
        assertTrue(refusal, refusal.startsWith("That video is 264.5\u00A0MB, and a map keeps videos of up to 250\u00A0MB"))
    }

    @Test
    fun `a video that would take the map past what it can carry is refused`() {
        assertNull(videoRefusal(40_000_000, 960_000_000, words))
        assertEquals(1_000_000_000L, MAP_MEDIA_LIMIT)
        val refusal = videoRefusal(40_000_000, 960_000_001, words)!!
        assertTrue(refusal, refusal.startsWith("This map's pictures, videos and sounds come to 960.1\u00A0MB already. With this video they'd be more than the 1\u00A0GB a map can carry"))
    }

    @Test
    fun `refusals are in the app's language`() {
        val spanish = FileWords(Locale.forLanguageTag("es-MX"))
        val refusal = videoRefusal(264_461_559, 0, spanish)!!
        assertTrue(refusal, refusal.startsWith("Ese video pesa 264.5\u00A0MB, y un mapa admite videos de hasta 250\u00A0MB"))
        assertTrue(videoRefusal(40_000_000, 970_000_000, spanish)!!.contains("pasarían del máximo de 1\u00A0GB que un mapa puede llevar"))
    }

    @Test
    fun `a picture that would take the map past what it can carry is refused`() {
        assertNull(pictureRefusal(500_000, 999_500_000, words))
        assertTrue(pictureRefusal(500_001, 999_500_000, words)!!.startsWith("This map's pictures, videos and sounds come to 999.5\u00A0MB"))
    }

    @Test
    fun `a picture or video from the folder gets the room one from the phone would`() {
        assertEquals(MEDIA_FILE_LIMIT, mediaRoom(0))
        assertEquals(MEDIA_FILE_LIMIT, mediaRoom(750_000_000))
        assertEquals(MAP_MEDIA_LIMIT - 800_000_000, mediaRoom(800_000_000))
        assertEquals(0L, mediaRoom(MAP_MEDIA_LIMIT + 1))
    }

    @Test
    fun `a copy capped at its room passes up to it, and refuses past it`() {
        val output = ByteArrayOutputStream()
        val capped = CappedOutput(output, 10, words)
        capped.write(ByteArray(9))
        capped.write(1)
        assertEquals(10, output.size())

        try {
            capped.write(ByteArray(1))
            fail("The copy should have been refused")
        } catch (refused: MediaRefusedException) {
            assertEquals(10, output.size())
        }
    }

    @Test
    fun `sizes read in whole or tenths of megabytes, rounded up, and from 1,000 MB on in gigabytes`() {
        assertEquals("50\u00A0MB", readableSize(50_000_000, words))
        assertEquals("50.1\u00A0MB", readableSize(50_000_001, words))
        assertEquals("0.1\u00A0MB", readableSize(1, words))
        assertEquals("999.9\u00A0MB", readableSize(999_900_000, words))
        // What would round up to 1,000 MB reads as the gigabyte it is.
        assertEquals("1\u00A0GB", readableSize(999_950_001, words))
        assertEquals("1\u00A0GB", readableSize(1_000_000_000, words))
        assertEquals("1.1\u00A0GB", readableSize(1_000_000_001, words))
        assertEquals("1.2\u00A0GB", readableSize(1_200_000_000, words))
        assertEquals("12.5\u00A0GB", readableSize(12_480_000_000, words))
        assertEquals("1.2\u00A0GB", readableSize(1_200_000_000, FileWords(Locale.forLanguageTag("es-MX"))))
    }

    @Test
    fun `a map within its limits always travels whole`() {
        // Each file fits in a zip's file, and a live photo coming in is read whole.
        assertTrue(MEDIA_FILE_LIMIT < ZIP_ENTRY_LIMIT)
        assertTrue(MEDIA_FILE_LIMIT <= INCOMING_LIMIT)
        // All of a map's pictures, videos and sounds, with its text, fit in what a zip of one map unpacks to, and in what nearby devices take.
        assertTrue(MAP_MEDIA_LIMIT + ZIP_TEXT_LIMIT < BUNDLE_SIZE)
        assertTrue(BUNDLE_SIZE < OFFER_LIMIT)
    }

    @Test
    fun `a video copies whole, telling how far along it is in whole percents`() {
        val video = ByteArray(1_000_000) { it.toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Float>()
        copyVideo(ByteArrayInputStream(video), output, video.size.toLong(), mapMedia = 0, words, { progress += it })
        assertArrayEquals(video, output.toByteArray())
        assertEquals(1f, progress.last())
        assertEquals(progress, progress.sorted())
        assertEquals(progress.size, progress.distinct().size)
    }

    @Test
    fun `a video that turns out bigger than it said is refused as it copies`() {
        val endless = object : InputStream() {
            override fun read(): Int = 0

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = length
        }

        try {
            copyVideo(endless, OutputStream.nullOutputStream(), size = -1, mapMedia = 0, words = words, onProgress = {})
            fail("The copy should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!.startsWith("That video is more than 250\u00A0MB"))
        }
    }

    @Test
    fun `a video that turns out bigger than what's left of its map's room is refused as it copies, nothing past the room written`() {
        // A storage app that says a video is a kilobyte, and gives two megabytes, to a map one megabyte short of full.
        val output = ByteArrayOutputStream()

        try {
            copyVideo(ByteArrayInputStream(ByteArray(2_000_000)), output, 1_000, mapMedia = MAP_MEDIA_LIMIT - 1_000_000, words, {})
            fail("The copy should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!.startsWith("This map's pictures, videos and sounds come to 999\u00A0MB already. With this video they'd be more than the 1\u00A0GB"))
        }

        assertTrue(output.size() <= 1_000_000)
    }

    @Test
    fun `a copy no longer wanted stops`() {
        var reads = 0
        val stopped = IllegalStateException("stopped")

        try {
            copyVideo(ByteArrayInputStream(ByteArray(2_000_000)), ByteArrayOutputStream(), 2_000_000, mapMedia = 0, words, {}) {
                reads += 1

                if (reads > 2) {
                    throw stopped
                }
            }

            fail("The copy should have stopped")
        } catch (thrown: IllegalStateException) {
            assertEquals(stopped, thrown)
        }
    }

    @Test
    fun `media types follow the names a map's folder keeps`() {
        assertEquals("video/mp4", mediaType("video-abc.mp4"))
        assertEquals("video/mp4", mediaType("clip.M4V"))
        assertEquals("video/quicktime", mediaType("clip.mov"))
        assertEquals("video/webm", mediaType("clip.webm"))
        assertEquals("image/jpeg", mediaType("picture-1.jpg"))
        assertEquals("image/svg+xml", mediaType("diagram.svg"))
        assertEquals("application/octet-stream", mediaType("notes.txt"))
        assertTrue(isVideoName("video-abc.mp4"))
        assertTrue(!isVideoName("video-abc.jpg"))
    }
}
