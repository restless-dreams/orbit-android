package com.hyuchia.orbit.present

import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShownMediaTest {
    /** A video that started playing from its start at 10 seconds on the clock. */
    private val playing = ShownMedia("tram", MediaKind.Video, playing = true, at = 0.0, since = 10_000)

    @Test
    fun aPlayingVideoMovesOnWithTheClockAndAPausedOneStays() {
        assertEquals(0.0, playing.position(10_000), 0.0)
        assertEquals(42.5, playing.position(52_500), 0.0)
        val paused = playing.played(false, 52_500)
        assertEquals(42.5, paused.position(90_000), 0.0)
        assertFalse(paused.playingAt(90_000))
        // Played again, from where it paused.
        assertEquals(50.5, paused.played(true, 100_000).position(108_000), 0.0)
    }

    @Test
    fun aVideoWhoseLengthIsKnownStopsAtItsEndAndPlaysAgainFromTheStart() {
        val known = playing.copy(duration = 30.0)
        assertTrue(known.playingAt(39_000))
        assertEquals(30.0, known.position(70_000), 0.0)
        assertTrue(known.atEnd(70_000))
        assertFalse(known.playingAt(70_000))
        // A screen joining then opens it at its end, paused.
        assertEquals(AudienceCommand.Media("tram", MediaKind.Video, playing = false, at = 30.0), known.command(70_000))
        val again = known.played(true, 70_000)
        assertEquals(0.0, again.position(70_000), 0.0)
        assertEquals(5.0, again.position(75_000), 0.0)
    }

    @Test
    fun startingOverPlaysFromTheStartAsTheCommandsSay() {
        val paused = playing.played(false, 40_000)
        val over = paused.after(AudienceCommand.Seek(0.0), 41_000).after(AudienceCommand.Playback(true), 41_000)!!
        assertTrue(over.playingAt(41_000))
        assertEquals(2.0, over.position(43_000), 0.0)
    }

    @Test
    fun aStepANewMapTheEndOrClosingGoBackToTheMap() {
        listOf(
            AudienceCommand.Step("next", 1, 4),
            AudienceCommand.CloseMedia,
            AudienceCommand.End,
            AudienceCommand.Show("# Map", AppTheme(dark = false, tokens = emptyMap())),
        ).forEach { command ->
            assertNull(command.toString(), playing.after(command, 20_000))
        }

        assertEquals(playing, playing.after(AudienceCommand.Captions(false), 20_000))
        // Playing or seeking with nothing shown shows nothing.
        assertNull(null.after(AudienceCommand.Playback(true), 20_000))
        assertNull(null.after(AudienceCommand.Seek(3.0), 20_000))
        assertEquals(
            ShownMedia("alfama", MediaKind.Picture, playing = false, at = 0.0, since = 20_000),
            null.after(AudienceCommand.Media("alfama", MediaKind.Picture), 20_000),
        )
    }

    @Test
    fun aScreensWordCorrectsTheReckoningOnceItHadTimeToHearThePresenter() {
        val asked = playing.played(false, 20_000)
        // Sent before the screen heard the pause: only the length it gives counts.
        val early = asked.reported(MapMessage.MediaState("tram", playing = true, time = 10.4, duration = 190.0), 20_200)
        assertFalse(early.playingAt(20_200))
        assertEquals(10.0, early.position(25_000), 0.0)
        assertEquals(190.0, early.duration)
        // Later, what it says is where the video is: it stalled at 9.6 seconds, say.
        val later = early.reported(MapMessage.MediaState("tram", playing = false, time = 9.6, duration = 190.0), 22_000)
        assertEquals(9.6, later.position(30_000), 0.0)
        // And when it plays on, the phone's reckoning goes on from there.
        val going = later.reported(MapMessage.MediaState("tram", playing = true, time = 9.6, duration = 190.0), 30_000)
        assertEquals(11.6, going.position(32_000), 0.000_1)
        // At its end.
        val ended = going.reported(MapMessage.MediaState("tram", playing = false, time = 190.0, duration = 190.0, ended = true), 40_000)
        assertTrue(ended.atEnd(40_000))
        assertFalse(ended.playingAt(40_000))
    }

    @Test
    fun wordsAboutAnotherVideoOrAPictureChangeNothing() {
        val later = 60_000L
        assertEquals(playing, playing.reported(MapMessage.MediaState("bus", playing = false, time = 3.0), later))
        val picture = ShownMedia("alfama", MediaKind.Picture, playing = false, at = 0.0, since = 0)
        assertEquals(picture, picture.reported(MapMessage.MediaState("alfama", playing = true, time = 3.0), later))
    }
}
