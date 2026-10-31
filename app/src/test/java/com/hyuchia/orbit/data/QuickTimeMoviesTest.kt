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
 * iPhones write it, and one as older ones do, without an `ftyp` and with its
 * sound described as `lpcm`.
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
    fun `a movie whose sound is compressed has none read here`() {
        assertNull(QuickTimeMovies.sound(sample("motion.mp4")))
    }
}
