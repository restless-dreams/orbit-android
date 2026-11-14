package com.hyuchia.orbit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * iPhones' movies as Android's reader can't take them, made small by
 * `scripts/live-photos/make-samples.py` in `resources/live/`: one as newer
 * iPhones write it, one as older ones do, without an `ftyp` and with its
 * sound described as `lpcm`, and the first with its sound described in
 * QuickTime's second version, four frames to a packet.
 */
class QuickTimeMoviesTest {
    private fun sample(name: String) = File(javaClass.getResource("/live/$name")!!.toURI())

    private val folder: File = Files.createTempDirectory("orbit-movies").toFile()

    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `an older iPhone's movie is given an ftyp, and its chunks are found where they were`() {
        val older = sample("iphone-live-older.mov")
        val typed = File(folder, "typed.mov")

        assertTrue(QuickTimeMovies.withFileType(older, typed))
        assertEquals(older.length() + 20, typed.length())
        assertEquals("ftypqt  ", typed.readBytes().copyOfRange(4, 12).toString(Charsets.ISO_8859_1))
        assertArrayEquals(QuickTimeMovies.sound(older)!!.samples, QuickTimeMovies.sound(typed)!!.samples)
        assertEquals("C0633EC8-48F1-43C9-84DC-23C480CA1FA4", LivePhotos.liveVideo(typed)?.identifier)
    }

    @Test
    fun `a movie that starts with its ftyp is left as it is`() {
        val typed = File(folder, "typed.mov")

        assertFalse(QuickTimeMovies.withFileType(sample("iphone-live.mov"), typed))
        assertFalse(typed.exists())
    }

    @Test
    fun `an iPhone's uncompressed sound reads the same in each way QuickTime describes it`() {
        val sowt = QuickTimeMovies.sound(sample("iphone-live.mov"))!!
        val lpcm = QuickTimeMovies.sound(sample("iphone-live-older.mov"))!!

        assertEquals(8000, sowt.rate)
        assertEquals(1, sowt.channels)
        // A second and a half of it, each sample as ffmpeg decodes it.
        assertEquals(12_000 * 2, sowt.samples.size)
        assertEquals("042d9b75969efa68f6aea78c381fba8a", md5(sowt.samples))
        assertArrayEquals(sowt.samples, lpcm.samples)
        assertEquals(8000, lpcm.rate)
    }

    @Test
    fun `sound described in QuickTime's second version reads whole, its samples counted a packet of frames at a time or a frame at a time`() {
        val whole = QuickTimeMovies.sound(sample("iphone-live.mov"))!!.samples
        // Each of the track's samples a packet of four frames, lasting four ticks: every frame, in order, as ffmpeg decodes them.
        val packets = QuickTimeMovies.sound(sample("iphone-live-packets.mov"))!!
        assertEquals(12_000 * 2, packets.samples.size)
        assertEquals("042d9b75969efa68f6aea78c381fba8a", md5(packets.samples))
        assertEquals(8000, packets.rate)
        // The same description with the samples counted a frame at a time, as QuickTime long wrote uncompressed sound.
        assertArrayEquals(whole, QuickTimeMovies.sound(sample("iphone-live-frames.mov"))!!.samples)
    }

    @Test
    fun `a movie whose sound is compressed has none read here`() {
        assertNull(QuickTimeMovies.sound(sample("motion.mp4")))
    }

    @Test
    fun `stereo sound cut short partway through a frame reads as far as its last whole frame`() {
        // Three frames of two channels, of which a frame and a half are there: one sample of the second frame, and a byte of the next.
        val samples = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val sound = QuickTimeMovies.sound(ArrayBytes(TestMedia.liveMovie(samples, frames = 3)))!!

        assertEquals(2, sound.channels)
        assertEquals(48_000, sound.rate)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), sound.samples)

        // Nor does a sound whose first frame is cut short have any frame to read.
        assertNull(QuickTimeMovies.sound(ArrayBytes(TestMedia.liveMovie(byteArrayOf(1, 2, 3), frames = 3))))
    }
}
