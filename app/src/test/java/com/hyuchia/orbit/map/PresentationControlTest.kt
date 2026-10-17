package com.hyuchia.orbit.map

import com.hyuchia.orbit.media.GalleryItem
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.ShownMedia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationControlTest {
    /** A screen that keeps what it's told, and can say how its video goes. */
    private class Screen : Audience {
        override val name = "Screen"
        val heard = mutableListOf<AudienceCommand>()
        var says: ((MapMessage.MediaState) -> Unit)? = null
        var reckoned: ShownMedia? = null

        override fun send(command: AudienceCommand) {
            heard += command
        }

        override fun listen(listener: (MapMessage.MediaState) -> Unit) {
            says = listener
        }

        override fun reckon(media: ShownMedia?) {
            reckoned = media
        }

        override fun close() = Unit
    }

    private val video = Media("media/tram.mp4", provider = "native", thumbnail = "media/tram.jpg")
    private val nodes = listOf(
        MapNode("talk", "Talk"),
        MapNode("tram", "Tram 28", parentId = "talk", image = Media("media/tram.jpg"), video = video),
        MapNode("end", "The end", parentId = "talk"),
    )
    private val ui = MutableStateFlow(
        MapUi(map = MapState("Talk", nodes), presentation = Presentation(listOf("talk", "tram", "end"), index = 1, startedAt = 0)),
    )
    private val focused = mutableListOf<String>()

    private val TestScope.control: PresentationControl
        get() = PresentationControl(
            scope = backgroundScope,
            ui = ui,
            clock = { testScheduler.currentTime },
            focus = { focused += it },
            map = { presentation -> AudienceCommand.Show("# Talk", AppTheme(dark = false, tokens = emptyMap()), focus = presentation.current) },
            length = { media -> if (media == video) 190.0 else null },
        )

    private val presentation: Presentation get() = ui.value.presentation!!

    @Test
    fun steppingClosesThePictureOrVideoHereAndOnEveryScreen() = runTest {
        val control = control
        val screen = Screen()
        control.setScreen(screen)
        control.showMedia(MediaKind.Video)
        assertEquals(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 0.0), screen.heard.last())
        assertEquals("tram", presentation.media?.id)

        control.step(1)
        assertNull(presentation.media)
        assertEquals(2, presentation.index)
        assertEquals(listOf("end"), focused)
        // The screens close it as they step: no other word needed.
        assertEquals(AudienceCommand.Step("end", 2, 3), screen.heard.last())
        assertTrue(AudienceCommand.CloseMedia !in screen.heard)
    }

    @Test
    fun whileAPictureShowsThePresentationDoesntStepByItselfAndTheIdeasTimeStartsOverAfter() = runTest {
        val control = control
        control.setPlaying(true, 10)
        advanceTimeBy(4_000)
        control.showMedia(MediaKind.Picture)
        advanceTimeBy(30_000)
        assertEquals(1, presentation.index)

        control.closeMedia()
        advanceTimeBy(9_900)
        assertEquals(1, presentation.index)
        advanceTimeBy(200)
        assertEquals(2, presentation.index)
    }

    @Test
    fun whileThisPhoneShowsAPictureFullScreenThePresentationWaitsToo() = runTest {
        val control = control
        control.setPlaying(true, 10)
        ui.update { it.copy(viewing = Viewing(listOf(GalleryItem("tram", MediaKind.Picture, Media("media/tram.jpg"), "Tram 28")), 0)) }
        control.retime()
        advanceTimeBy(60_000)
        assertEquals(1, presentation.index)
        ui.update { it.copy(viewing = null) }
        control.retime()
        advanceTimeBy(10_100)
        assertEquals(2, presentation.index)
    }

    @Test
    fun aStepByHandKeepsThePaceThePresentationPlaysAt() = runTest {
        val control = control
        control.step(-1)
        control.setPlaying(true, 8)
        // The settings say nothing of a pace: stepping by hand keeps the one it plays at.
        control.step(1, 0)
        advanceTimeBy(8_100)
        assertEquals(2, presentation.index)
    }

    @Test
    fun theVideoPlaysPausesAndStartsOverOnEveryScreenAndThePhoneKeepsTime() = runTest {
        val control = control
        val screen = Screen()
        control.setBrowsers(screen)
        control.showMedia(MediaKind.Video)
        runCurrent()
        // The phone reads the length of a video file in the map's folder itself.
        assertEquals(190.0, presentation.media?.duration)
        advanceTimeBy(12_000)
        control.playMedia(false)
        assertEquals(AudienceCommand.Playback(false), screen.heard.last())
        advanceTimeBy(30_000)
        assertEquals(12.0, presentation.media!!.position(testScheduler.currentTime), 0.0)

        control.startMediaOver()
        assertEquals(listOf(AudienceCommand.Seek(0.0), AudienceCommand.Playback(true)), screen.heard.takeLast(2))
        advanceTimeBy(5_000)
        assertEquals(5.0, presentation.media!!.position(testScheduler.currentTime), 0.0)

        control.closeMedia()
        assertEquals(AudienceCommand.CloseMedia, screen.heard.last())
        assertNull(presentation.media)
    }

    @Test
    fun whatTheSecondScreenSaysCorrectsWhereThePhoneReckonsTheVideoIsForBrowsersToo() = runTest {
        val control = control
        val screen = Screen()
        val browsers = Screen()
        control.setScreen(screen)
        control.setBrowsers(browsers)
        control.showMedia(MediaKind.Video)
        advanceTimeBy(5_000)
        // It took a moment to start: three seconds in after five.
        screen.says!!(MapMessage.MediaState("tram", playing = true, time = 3.0, duration = 189.5))
        advanceTimeBy(2_000)
        val media = presentation.media!!
        assertEquals(5.0, media.position(testScheduler.currentTime), 0.0)
        assertEquals(189.5, media.duration)
        // A browser that joins now starts there, not where the phone's own clock would put it.
        assertEquals(media, browsers.reckoned)
    }

    @Test
    fun aScreenThatArrivesHalfwayCatchesUpWithTheMapTheIdeaAndTheVideo() = runTest {
        val control = control
        control.setScreen(Screen())
        control.showMedia(MediaKind.Video)
        advanceTimeBy(20_000)
        val late = Screen()
        control.setBrowsers(late)
        assertEquals(
            listOf(
                AudienceCommand.Show("# Talk", AppTheme(dark = false, tokens = emptyMap()), focus = "tram"),
                AudienceCommand.Step("tram", 1, 3),
                AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 20.0),
            ),
            late.heard,
        )
    }

    @Test
    fun withNoOneWatchingElsewhereThePictureGoesAndThePresentationGoesOn() = runTest {
        val control = control
        control.setPlaying(true, 10)
        control.setScreen(Screen())
        control.showMedia(MediaKind.Picture)
        advanceTimeBy(20_000)
        control.setScreen(null)
        assertNull(presentation.media)
        advanceTimeBy(10_100)
        assertEquals(2, presentation.index)
    }

    @Test
    fun anIdeaWithoutAPictureShowsNone() = runTest {
        val control = control
        val screen = Screen()
        control.setScreen(screen)
        control.step(1)
        control.showMedia(MediaKind.Picture)
        control.showMedia(MediaKind.Video)
        assertNull(presentation.media)
        assertTrue(screen.heard.none { it is AudienceCommand.Media })
    }

    @Test
    fun theEndSendsTheScreensBackToWaiting() = runTest {
        val control = control
        val screen = Screen()
        control.setScreen(screen)
        control.setPlaying(true, 10)
        control.stop()
        assertEquals(AudienceCommand.End, screen.heard.last())
        ui.update { it.copy(presentation = null) }
        advanceTimeBy(60_000)
        assertTrue(focused.isEmpty())
    }
}
