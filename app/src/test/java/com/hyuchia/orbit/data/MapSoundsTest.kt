package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class MapSoundsTest {
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
    fun `a sound keeps the extension its type has, and an m4a is kept as the mp4 it is`() {
        assertEquals("mp3", soundExtension("audio/mpeg", "Morning birds.mp3"))
        assertEquals("mp4", soundExtension("audio/mp4", "Interview with Ana.m4a"))
        assertEquals("mp4", soundExtension("audio/x-m4a", "memo.m4a"))
        assertEquals("aac", soundExtension("audio/aac", null))
        assertEquals("ogg", soundExtension("audio/ogg", "song.ogg"))
        assertEquals("opus", soundExtension("audio/ogg", "AUD-20261021-WA0004.opus"))
        assertEquals("opus", soundExtension("audio/opus", null))
        assertEquals("wav", soundExtension("audio/x-wav", "take.wav"))
        assertEquals("flac", soundExtension("audio/flac", "album.flac"))
        assertEquals("weba", soundExtension("audio/webm", null))
        assertEquals("mp3", soundExtension("Audio/MPEG; charset=binary", "song"))
    }

    @Test
    fun `a sound whose type says nothing is known by its name, and only as one Orbit keeps`() {
        assertEquals("mp3", soundExtension(null, "song.MP3"))
        assertEquals("mp4", soundExtension("application/octet-stream", "talk.m4a"))
        assertEquals("flac", soundExtension("audio/*", "album.flac"))
        assertNull(soundExtension(null, "talk.amr"))
        assertNull(soundExtension(null, "talk"))
        assertNull(soundExtension(null, null))
        assertNull(soundExtension("application/octet-stream", "page.html"))
    }

    @Test
    fun `sounds browsers can't play, and things that aren't sounds, have no extension`() {
        assertNull(soundExtension("audio/amr", "call.amr"))
        assertNull(soundExtension("audio/3gpp", "memo.3gp"))
        assertNull(soundExtension("video/mp4", "clip.mp4"))
        // A name can't sneak a kind in past a type that says what it is.
        assertNull(soundExtension("audio/amr", "call.mp3"))
    }

    @Test
    fun `a sound's first bytes say what it is, over a type Android got wrong`() {
        // Android's own types call an m4a "audio/mpeg".
        assertEquals("mp4", soundExtension("audio/mpeg", "Interview with Ana.m4a", box("M4A ")))
        assertEquals("mp4", soundExtension(null, "talk", box("isom")))
        assertEquals("mp3", soundExtension("audio/mpeg", "song.mp3", ascii("ID3") + ByteArray(8)))
        assertEquals("aac", soundExtension("audio/aac", "tagged.aac", ascii("ID3") + ByteArray(8)))
        assertEquals("mp3", soundExtension("application/octet-stream", "song", bytes(0xFF, 0xFB, 0x90, 0x64)))
        assertEquals("aac", soundExtension("audio/*", null, bytes(0xFF, 0xF1, 0x50, 0x80)))
        assertEquals("opus", soundExtension("audio/ogg", "voice.ogg", ogg("OpusHead")))
        assertEquals("ogg", soundExtension("audio/ogg", "song.opus", ogg("\u0001vorbis\u0000")))
        assertEquals("flac", soundExtension("audio/x-flac", "album", ascii("fLaC") + ByteArray(4)))
        assertEquals("wav", soundExtension("audio/x-wav", "take", ascii("RIFF") + ByteArray(4) + ascii("WAVE")))
        assertEquals("weba", soundExtension("audio/webm", null, bytes(0x1A, 0x45, 0xDF, 0xA3)))
        // Bytes that don't say leave it to the type, then to the name.
        assertEquals("flac", soundExtension("audio/flac", "album.flac", ByteArray(SOUND_HEAD)))
        assertEquals("mp3", soundExtension(null, "song.mp3", ByteArray(0)))
    }

    @Test
    fun `bytes browsers can't play are refused whatever the sound is called, and other kinds aren't heard out`() {
        assertNull(soundExtension("audio/mpeg", "call.mp3", ascii("#!AMR\n")))
        assertNull(soundExtension("audio/mp4", "memo.m4a", box("3gp4")))
        assertNull(soundExtension("video/mp4", "clip.mp4", box("isom")))
        assertNull(soundExtension("image/png", "cover.mp3", ascii("ID3")))
    }

    @Test
    fun `a sound's first bytes are read without losing them`() {
        val bytes = ByteArray(100) { it.toByte() }
        val input = ByteArrayInputStream(bytes).buffered()
        assertEquals(SOUND_HEAD, input.peek(SOUND_HEAD).size)
        assertEquals(bytes.toList(), input.readBytes().toList())
        assertEquals(3, ByteArrayInputStream(ByteArray(3)).buffered().peek(SOUND_HEAD).size)
    }

    @Test
    fun `a file's name is its sound's caption, unless a recorder made it up`() {
        assertEquals("Interview with Ana", soundCaption("Interview with Ana.m4a"))
        assertEquals("voice memo for Lisbon", soundCaption("voice_memo_for_Lisbon.mp3"))
        assertNull(soundCaption("AUD-20261021-WA0004.opus"))
        assertNull(soundCaption("Recording_12.m4a"))
        assertNull(soundCaption("PTT-20261021-WA0001.opus"))
        assertNull(soundCaption(".mp3"))
        assertNull(soundCaption(null))
    }

    @Test
    fun `a map's media folder keeps sounds, under names that can't step outside it`() {
        assertTrue(isMediaName("memo-mf2k1x.mp4"))
        assertTrue(isMediaName("sound-mf2k1x.mp3"))
        assertTrue(isMediaName("album.flac"))
        assertTrue(isMediaName("talk.m4a"))
        assertFalse(isMediaName("page.html"))
        assertFalse(isMediaName("../memo.mp3"))
        assertFalse(isMediaName("call.amr"))
    }

    @Test
    fun `sounds are served as sounds, and a voice memo's mp4 as audio where it plays as one`() {
        assertEquals("audio/mpeg", mediaType("song.mp3"))
        assertEquals("audio/ogg", mediaType("talk.opus"))
        assertEquals("audio/flac", mediaType("album.FLAC"))
        assertEquals("video/mp4", mediaType("memo-mf2k1x.mp4"))
        assertEquals("audio/mp4", soundType("memo-mf2k1x.mp4"))
        assertEquals("audio/mpeg", soundType("song.mp3"))
    }

    @Test
    fun `a map's sounds are the files its ideas play as sounds, whatever they're named, in the map's order`() {
        val markdown = """
            # Trip <!-- orbit audio: media/memo-intro.mp4 -->
            ## Alfama
            <!-- orbit
            audio:
              caption: Fado at night
              src: ./media/sound-fado.mp3
            -->
            A note that says audio: media/nothing.mp3 is only words.
            ## Belém <!-- orbit type: goal, sound: { src: media/bells.ogg, caption: Bells } -->
            ## Tram <!-- orbit video: media/video-tram.mp4 -->
            ## Radio <!-- orbit audio: https://example.com/media/live.mp3 -->
            ## Again <!-- orbit audio: media/memo-intro.mp4 -->
            - Kept as text, not as a sound: media/notes.mp3
        """.trimIndent()

        assertEquals(listOf("memo-intro.mp4", "sound-fado.mp3", "bells.ogg"), soundNamesIn(markdown))
        assertEquals(emptyList<String>(), soundNamesIn("# Trip\n## Tram <!-- orbit video: media/video-tram.mp4 -->"))
    }

    @Test(timeout = 5_000)
    fun `a sound's line with a long run of spaces is read in one pass`() {
        val spaces = " ".repeat(100_000)
        // A line separator after the spaces makes a line no sound's: that's told in one pass too.
        val markdown = "# Trip <!-- orbit audio:${spaces}media/memo-intro.mp4 -->\n## Tram\n<!-- orbit\nsound:$spaces\u2028later\n-->\n"

        assertEquals(listOf("memo-intro.mp4"), soundNamesIn(markdown))
    }

    @Test
    fun `this version carries sounds in the folder, and says so in its record there`() {
        assertTrue(carriedMediaTypes().containsAll(listOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "weba")))
        // A voice memo is an mp4, which every version carries.
        assertTrue("mp4" in carriedMediaTypes())
    }

    @Test
    fun `a web page takes the map's sounds in its order while they fit, skipping one too big for what's left`() {
        val folder = java.nio.file.Files.createTempDirectory("orbit-sounds").toFile()

        try {
            val media = File(folder, "media").apply { mkdirs() }
            val memo = TestMedia.movie(500).also { File(media, "memo-one.mp4").writeBytes(it) }
            val big = TestMedia.flac().also { File(media, "sound-big.flac").writeBytes(it) }
            val two = TestMedia.mp3().also { File(media, "sound-two.mp3").writeBytes(it) }
            // Room for the memo and the MP3, but not for the FLAC after the memo.
            val limit = memo.size + two.size + 100L
            assertTrue(memo.size + big.size > limit)
            val markdown = "# A <!-- orbit audio: media/memo-one.mp4 -->\n## B <!-- orbit audio: media/sound-big.flac -->\n## C <!-- orbit audio: ./media/sound-two.mp3 -->\n## D <!-- orbit audio: media/gone.mp3 -->"
            assertEquals(listOf("memo-one.mp4", "sound-two.mp3"), soundsFitting(markdown, folder, limit = limit).map { it.file.name })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `Orbit's own names tell its sounds from its videos`() {
        assertTrue(isSoundFile("memo-mf2k1x.mp4"))
        assertTrue(isSoundFile("sound-mf2k1x.mp4"))
        assertTrue(isSoundFile("song.mp3"))
        assertFalse(isSoundFile("video-mf2k1x.mp4"))
        assertFalse(isSoundFile("picture-mf2k1x.jpg"))
        assertTrue(isVoiceMemo("media/memo-mf2k1x.mp4"))
        assertFalse(isVoiceMemo("media/sound-mf2k1x.mp3"))
    }

    @Test
    fun `a sound past what a map keeps is refused, saying why`() {
        assertEquals(
            "That sound is 60 MB, and a map keeps sounds of up to 50 MB, so it can still go to your other devices. Trim it, or add it as a web address.",
            soundRefusal(60_000_000, 0, words),
        )
        assertEquals(
            "This map's pictures, videos and sounds come to 80 MB already. With this sound they'd be more than the 90 MB a map can carry when it's shared or backed up. Choose a shorter one, or add it as a web address.",
            soundRefusal(15_000_000, 80_000_000, words),
        )
        assertNull(soundRefusal(5_000_000, 80_000_000, words))
    }

    @Test
    fun `copying a sound says how far along it is, a whole percent at a time`() {
        val heard = mutableListOf<Float>()
        val sink = DiscardingOutput()
        copySound(ByteArrayInputStream(ByteArray(1_000_000)), sink, 1_000_000, words, onProgress = { heard += it })
        assertEquals(1_000_000L, sink.written)
        assertEquals(1f, heard.last())
        assertEquals(heard.distinct(), heard)
    }

    @Test
    fun `a sound that turns out bigger than it said is refused as it's copied`() {
        try {
            copySound(Zeros(VIDEO_SIZE_LIMIT + 1), DiscardingOutput(), 1_000, words, onProgress = {})
            fail("It should have been refused")
        } catch (refused: MediaRefusedException) {
            assertEquals("That sound is more than 50 MB, and a map keeps sounds of up to that, so it can still go to your other devices. Trim it, or add it as a web address.", refused.message)
        }
    }

    private fun ascii(text: String) = text.toByteArray(Charsets.ISO_8859_1)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** The start of an MP4 file of `brand`: its first box, `ftyp`. */
    private fun box(brand: String) = bytes(0, 0, 0, 0x20) + ascii("ftyp$brand") + ByteArray(20)

    /** The start of an Ogg file whose first page holds `header`. */
    private fun ogg(header: String) = ascii("OggS") + ByteArray(24) + ascii(header)

    private class DiscardingOutput : OutputStream() {
        var written = 0L

        override fun write(byte: Int) {
            written += 1
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            written += length
        }
    }

    /** `count` zeros, read without holding them. */
    private class Zeros(private var count: Long) : InputStream() {
        override fun read(): Int = if (count-- > 0) 0 else -1

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (count <= 0) {
                return -1
            }

            val read = minOf(length.toLong(), count).toInt()
            count -= read
            return read
        }
    }
}
