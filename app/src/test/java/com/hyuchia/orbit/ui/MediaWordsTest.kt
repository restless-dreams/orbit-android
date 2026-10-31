package com.hyuchia.orbit.ui

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.Uncleaned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MediaWordsTest {
    private val english = FileWords(Locale.US)
    private val spanish = FileWords(Locale.forLanguageTag("es-MX"))

    @Test
    fun `a map's files are counted the same way everywhere`() {
        assertEquals("a picture", MediaWords.list(MediaSummary(pictures = 1), english))
        assertEquals("3 pictures and a video", MediaWords.list(MediaSummary(pictures = 3, videos = 1), english))
        assertEquals("3 pictures, 2 videos and a sound", MediaWords.list(MediaSummary(pictures = 3, videos = 2, audio = 1), english))
        assertEquals("", MediaWords.list(MediaSummary(), english))
        assertEquals("3 imágenes, 2 videos y un audio", MediaWords.list(MediaSummary(pictures = 3, videos = 2, audio = 1), spanish))
        assertEquals("una imagen y un video", MediaWords.list(MediaSummary(pictures = 1, videos = 1), spanish))
    }

    @Test
    fun `each way out says what goes with the map`() {
        val media = MediaSummary(pictures = 3, videos = 1, bytes = 12_400_000)

        assertEquals("With 3 pictures and a video\u00A0· 12.4\u00A0MB", MediaWords.with(media, english))
        assertEquals("With 3 pictures inside", MediaWords.inside(media, english))
        assertEquals("With 3 pictures", MediaWords.printed(media, english))
        assertEquals("A video stays on this device", MediaWords.staysHere(media.copy(pictures = 0), english))
        assertEquals("3 pictures and a video stay on this device", MediaWords.staysHere(media, english))
        assertEquals("Un video se queda en este dispositivo", MediaWords.staysHere(media.copy(pictures = 0), spanish))
        assertEquals("3 imágenes y un video se quedan en este dispositivo", MediaWords.staysHere(media, spanish))
    }

    @Test
    fun `a live photo is named as one, and its motion only where it stays behind its picture`() {
        // A live photo is one of the pictures, its motion and all.
        assertEquals("2 pictures, a live photo and a video", MediaWords.list(MediaSummary(pictures = 3, videos = 1, motions = 1), english))
        assertEquals("2 imágenes, una foto en vivo y un video", MediaWords.list(MediaSummary(pictures = 3, videos = 1, motions = 1), spanish))
        assertEquals("With 2 live photos and a video\u00A0· 4.2\u00A0MB", MediaWords.with(MediaSummary(pictures = 2, videos = 1, motions = 2, bytes = 4_200_000), english))

        // A page that holds every motion says its live photos are inside, and nothing stays but the video.
        val media = MediaSummary(pictures = 2, videos = 1, motions = 2)
        assertEquals("With 2 live photos inside", MediaWords.inside(media, english))
        assertEquals("Con 2 fotos en vivo dentro", MediaWords.inside(media, spanish))
        assertEquals("A video stays on this device", MediaWords.staysHere(media.copy(pictures = 0, motions = 0), english))

        // One the page had no room for goes as its still, and its motion stays, as it says.
        assertEquals("With a picture and a live photo inside", MediaWords.inside(media.copy(motions = 1), english))
        assertEquals("A live photo's motion stays on this device", MediaWords.staysHere(MediaSummary(), english, motions = 1))
        assertEquals("A video and 2 live photos' motions stay on this device", MediaWords.staysHere(MediaSummary(videos = 1), english, motions = 2))
        assertEquals("El movimiento de una foto en vivo se queda en este dispositivo", MediaWords.staysHere(MediaSummary(), spanish, motions = 1))
        assertEquals("Un video y los movimientos de 2 fotos en vivo se quedan en este dispositivo", MediaWords.staysHere(MediaSummary(videos = 1), spanish, motions = 2))

        // Paper prints a live photo's still.
        assertEquals("With 2 pictures", MediaWords.printed(media, english))

        // Shared as just its text, the whole live photo stays, and four kinds still read as one list.
        assertEquals("A live photo and a video stay on this device", MediaWords.staysHere(MediaSummary(pictures = 1, videos = 1, motions = 1), english))
        assertEquals(
            "A picture, a live photo, a video and 2 sounds stay on this device",
            MediaWords.staysHere(MediaSummary(pictures = 2, videos = 1, audio = 2, motions = 1), english),
        )
        assertEquals(
            "Una imagen, una foto en vivo, un video y 2 audios se quedan en este dispositivo",
            MediaWords.staysHere(MediaSummary(pictures = 2, videos = 1, audio = 2, motions = 1), spanish),
        )
    }

    @Test
    fun `a map with nothing but text says nothing more`() {
        val text = MediaSummary()
        assertNull(MediaWords.with(text, english))
        assertNull(MediaWords.inside(text, english))
        assertNull(MediaWords.staysHere(text, english))
        // A page with only videos has no pictures inside it to speak of.
        assertNull(MediaWords.inside(MediaSummary(videos = 2), english))
        // Its sounds go inside it too.
        assertEquals("With 3 pictures and a sound inside", MediaWords.inside(MediaSummary(pictures = 3, videos = 1, audio = 1), english))
        assertEquals("With 2 sounds inside", MediaWords.inside(MediaSummary(audio = 2), english))
    }

    @Test
    fun `files that can't go clean are named, with what stays or goes with them`() {
        val video = Uncleaned(videos = setOf("video-tram.mp4"))
        assertEquals("A video stays on this device: Orbit can't take out where it was recorded.", MediaWords.uncleaned(video, english, asTheyAre = false))
        assertEquals("A video goes as it is, with where it was recorded.", MediaWords.uncleaned(video, english, asTheyAre = true))
        assertEquals("Un video se queda en este dispositivo: Orbit no puede quitarle dónde se grabó.", MediaWords.uncleaned(video, spanish, asTheyAre = false))
        assertEquals("Un video va tal como está, con el lugar donde se grabó.", MediaWords.uncleaned(video, spanish, asTheyAre = true))

        val pictures = Uncleaned(pictures = setOf("picture-1.jpg", "picture-2.jpg"))
        assertEquals("2 pictures stay on this device: Orbit can't take out where they were taken.", MediaWords.uncleaned(pictures, english, asTheyAre = false))
        assertEquals("2 imágenes se quedan en este dispositivo: Orbit no puede quitarles dónde se tomaron.", MediaWords.uncleaned(pictures, spanish, asTheyAre = false))

        val both = Uncleaned(pictures = setOf("picture-1.jpg"), sounds = setOf("memo-intro.mp4"))
        assertEquals("A picture and a sound go as they are, with where they were taken or recorded.", MediaWords.uncleaned(both, english, asTheyAre = true))
        assertEquals("Una imagen y un audio van tal como están, con el lugar donde se tomaron o grabaron.", MediaWords.uncleaned(both, spanish, asTheyAre = true))

        // A live photo is named as one, taken; a motion here alone as a live photo's motion, recorded.
        val live = Uncleaned(pictures = setOf("picture-2.jpg"), motions = setOf("picture-2-motion.mp4"))
        assertEquals("A live photo stays on this device: Orbit can't take out where it was taken.", MediaWords.uncleaned(live, english, asTheyAre = false))
        val motion = Uncleaned(motions = setOf("picture-3-motion.mp4"))
        assertEquals("A live photo's motion stays on this device: Orbit can't take out where it was recorded.", MediaWords.uncleaned(motion, english, asTheyAre = false))
        assertEquals(
            "El movimiento de una foto en vivo se queda en este dispositivo: Orbit no puede quitarle dónde se grabó.",
            MediaWords.uncleaned(motion, spanish, asTheyAre = false),
        )

        assertNull(MediaWords.uncleaned(Uncleaned(), english, asTheyAre = false))
    }

    @Test
    fun `file types read as a list`() {
        assertEquals(".mp3", MediaWords.types(listOf("mp3"), english))
        assertEquals(".mp3 and .ogg", MediaWords.types(listOf("ogg", "mp3"), english))
        assertEquals(".flac, .mp3 and .ogg", MediaWords.types(listOf("ogg", "mp3", "flac"), english))
        assertEquals(".flac, .mp3 y .ogg", MediaWords.types(listOf("ogg", "mp3", "flac"), spanish))
    }
}
