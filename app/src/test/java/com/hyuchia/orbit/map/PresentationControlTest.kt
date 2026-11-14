package com.hyuchia.orbit.map

import com.hyuchia.orbit.media.GalleryItem
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.MediaMotion
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.ScreenTrouble
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
    /** A screen that keeps what it's told, can say how its video goes, and goes wrong when a test says. */
    private class Screen : Audience {
        override val name = "Screen"
        val heard = mutableListOf<AudienceCommand>()
        var says: ((MapMessage.MediaState) -> Unit)? = null
        var reckoned: ShownMedia? = null
        override val trouble = MutableStateFlow<ScreenTrouble?>(null)

        /** How many times it was loaded again. */
        var loadedAgain = 0

        override fun loadAgain() {
            loadedAgain += 1
            trouble.value = null
            heard.clear()
        }

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
    fun aLivePhotosMotionPlaysAgainOnEveryScreenAndOnlyALivePhotos() = runTest {
        val control = control
        val screen = Screen()
        val browsers = Screen()
        control.setScreen(screen)
        control.setBrowsers(browsers)

        // A plain picture has no motion: nothing goes out.
        control.showMedia(MediaKind.Picture)
        val heard = screen.heard.size
        control.replayMotion()
        assertEquals(heard, screen.heard.size)

        // The same idea's picture as a live photo plays its motion again on every screen, and stays shown here.
        val live = Media("media/tram.jpg", motion = MediaMotion("media/tram.motion.mp4", still = 1.2))
        ui.update { state -> state.copy(map = state.map!!.copy(nodes = state.map!!.nodes.map { if (it.id == "tram") it.copy(image = live) else it })) }
        val shown = presentation.media
        control.replayMotion()
        assertEquals(AudienceCommand.Playback(true), screen.heard.last())
        assertEquals(AudienceCommand.Playback(true), browsers.heard.last())
        assertEquals(shown, presentation.media)

        // Back on the map, or with the video shown, there's no picture to play.
        control.closeMedia()
        control.replayMotion()
        assertEquals(AudienceCommand.CloseMedia, screen.heard.last())
        control.showMedia(MediaKind.Video)
        control.replayMotion()
        assertTrue(screen.heard.last() is AudienceCommand.Media)
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
    fun pausedAndPlayedAgainAPresentationGoesOnAtThePaceChosen() = runTest {
        val control = control
        // Five seconds an idea, chosen in Settings: the play button passes it on as it is.
        control.begin(5)
        control.setPlaying(true, 5)
        advanceTimeBy(2_000)
        control.setPlaying(false, 5)
        advanceTimeBy(60_000)
        assertEquals(1, presentation.index)

        control.setPlaying(true, 5)
        advanceTimeBy(4_900)
        assertEquals(1, presentation.index)
        advanceTimeBy(200)
        assertEquals(2, presentation.index)
    }

    @Test
    fun withNoPaceChosenAPresentationPlayedByItselfGoesOnEveryEightSecondsPausedOrNot() = runTest {
        val control = control
        // "Only when I tap": nothing steps by itself until play is pressed.
        control.begin(0)
        control.step(-1)
        advanceTimeBy(60_000)
        assertEquals(0, presentation.index)

        control.setPlaying(true, 0)
        advanceTimeBy(7_900)
        assertEquals(0, presentation.index)
        advanceTimeBy(200)
        assertEquals(1, presentation.index)

        control.setPlaying(false, 0)
        advanceTimeBy(60_000)
        assertEquals(1, presentation.index)
        control.setPlaying(true, 0)
        advanceTimeBy(8_100)
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
    fun playAtTheVideosEndStartsItOverOnEveryScreenPausedThereOrNot() = runTest {
        val control = control
        val screen = Screen()
        control.setBrowsers(screen)
        control.showMedia(MediaKind.Video)
        runCurrent()

        // Paused right as it ends, by the phone's reckoning: 190 seconds of 190.
        advanceTimeBy(190_000)
        control.playMedia(false)
        assertTrue(presentation.media!!.atEnd(testScheduler.currentTime))
        control.playMedia(true)
        assertEquals(listOf(AudienceCommand.Seek(0.0), AudienceCommand.Playback(true)), screen.heard.takeLast(2))
        assertTrue(presentation.media!!.playingAt(testScheduler.currentTime))
        advanceTimeBy(5_000)
        assertEquals(5.0, presentation.media!!.position(testScheduler.currentTime), 0.0)

        // Ended by itself, the same: every screen starts it over, as the phone does.
        advanceTimeBy(200_000)
        control.playMedia(true)
        assertEquals(listOf(AudienceCommand.Seek(0.0), AudienceCommand.Playback(true)), screen.heard.takeLast(2))
        assertEquals(0.0, presentation.media!!.position(testScheduler.currentTime), 0.0)
    }

    @Test
    fun aStepBackAtTheFirstIdeaOrOnAtTheLastTakesTheAudienceBackToTheMap() = runTest {
        val control = control
        val screen = Screen()
        control.setScreen(screen)

        ui.update { it.copy(presentation = Presentation(listOf("tram", "end"), index = 0, startedAt = 0)) }
        control.showMedia(MediaKind.Picture)
        control.step(-1)
        assertNull(presentation.media)
        assertEquals(0, presentation.index)
        assertEquals(AudienceCommand.CloseMedia, screen.heard.last())

        ui.update { it.copy(presentation = Presentation(listOf("talk", "tram"), index = 1, startedAt = 0)) }
        control.showMedia(MediaKind.Video)
        control.step(1)
        assertNull(presentation.media)
        assertEquals(1, presentation.index)
        assertEquals(AudienceCommand.CloseMedia, screen.heard.last())

        // With the map back, a step past the end has nothing more to do.
        val heard = screen.heard.size
        control.step(1)
        assertEquals(heard, screen.heard.size)
        assertTrue(focused.isEmpty())
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
    fun aScreenThatWentWrongIsLoadedAgainAndCaughtUpAndOneThatsWellIsLeftAlone() = runTest {
        val control = control
        val screen = Screen()
        control.setScreen(screen)
        control.showMedia(MediaKind.Video)
        advanceTimeBy(20_000)

        control.loadScreenAgain()
        assertEquals(0, screen.loadedAgain)

        // Its page stopped: a new one gets the map, the idea on screen and the video where it is.
        screen.trouble.value = ScreenTrouble.Stopped
        control.loadScreenAgain()
        assertEquals(1, screen.loadedAgain)
        assertEquals(
            listOf(
                AudienceCommand.Show("# Talk", AppTheme(dark = false, tokens = emptyMap()), focus = "tram"),
                AudienceCommand.Step("tram", 1, 3),
                AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 20.0),
            ),
            screen.heard,
        )
    }

    @Test
    fun aNewMapReachesEveryAudienceAtTheIdeaOnScreenAndThePresentationFollowsANewId() = runTest {
        var markdown = "# Talk"
        val control = PresentationControl(
            scope = backgroundScope,
            ui = ui,
            clock = { testScheduler.currentTime },
            focus = { focused += it },
            map = { presentation -> AudienceCommand.Show(markdown, AppTheme(dark = false, tokens = emptyMap()), focus = presentation.current) },
        )
        val screen = Screen()
        val browsers = Screen()
        control.begin(seconds = 0)
        control.setScreen(screen)
        control.setBrowsers(browsers)
        control.showMedia(MediaKind.Picture)

        // Nothing new: the audiences have the map as it is.
        control.mapChanged(listOf("talk", "tram", "end"))
        assertEquals(1, screen.heard.count { it is AudienceCommand.Show })

        // Tram 28 is renamed elsewhere, so it has a new id: the presentation keeps to it, its picture still over the map.
        markdown = "# Talk, renamed"
        control.renamed("tram", "tram-28")
        ui.update { state -> state.copy(map = state.map?.let { map -> map.copy(nodes = map.nodes.map { if (it.id == "tram") it.copy(id = "tram-28") else it }, path = listOf("talk", "tram-28")) }) }
        control.mapChanged(listOf("talk", "tram-28", "end"))
        assertEquals(listOf("talk", "tram-28", "end"), presentation.order)
        assertEquals("tram-28", presentation.media?.id)

        listOf(screen, browsers).forEach { audience ->
            assertEquals(
                listOf(
                    AudienceCommand.Show("# Talk, renamed", AppTheme(dark = false, tokens = emptyMap()), focus = "tram-28"),
                    AudienceCommand.Step("tram-28", 1, 3),
                    AudienceCommand.Media("tram-28", MediaKind.Picture, playing = false, at = 0.0),
                ),
                audience.heard.takeLast(3),
            )
        }

        // Then it goes from the map elsewhere: the presentation goes on from the step in its place, and the phone's map flies there.
        markdown = "# Talk, shorter"
        ui.update { state -> state.copy(map = state.map?.let { map -> map.copy(nodes = map.nodes.filter { it.id != "tram-28" }) }) }
        control.mapChanged(listOf("talk", "end"))
        assertEquals(listOf("talk", "end"), presentation.order)
        assertEquals(1, presentation.index)
        assertNull(presentation.media)
        assertEquals(listOf("end"), focused)
        assertEquals(listOf(AudienceCommand.Show("# Talk, shorter", AppTheme(dark = false, tokens = emptyMap()), focus = "end"), AudienceCommand.Step("end", 1, 2)), screen.heard.takeLast(2))

        // New text that left the phone's map on the idea's parent, as Orbit does when an id it was on goes: it flies back.
        markdown = "# Talk, again"
        ui.update { state -> state.copy(map = state.map?.copy(path = listOf("talk"))) }
        control.mapChanged(listOf("talk", "end"))
        assertEquals(listOf("end", "end"), focused)
    }

    @Test
    fun anIdeaRenamedElsewhereIsTheNewIdeaAtItsPlaceAndARemovedOneIsNone() {
        val before = listOf(
            MapNode("trip", "Trip", childCount = 2),
            MapNode("old", "Old", parentId = "trip", depth = 1, childCount = 1),
            MapNode("tram", "Tram", parentId = "old", depth = 2),
            MapNode("next", "Next", parentId = "trip", depth = 1, index = 1),
        )

        fun renaming(vararg names: Pair<String, String>) = before.map { node ->
            val to = names.toMap()
            node.copy(id = to[node.id] ?: node.id, parentId = node.parentId?.let { to[it] ?: it })
        }

        // Renamed on another device: the new idea at its place, under the same parent, at the same position.
        assertEquals("lisbon", renamedInPlace("old", before, renaming("old" to "lisbon")))
        // Its parent renamed too, or the map's center: followed the same way.
        assertEquals("tram-28", renamedInPlace("tram", before, renaming("old" to "lisbon", "tram" to "tram-28")))
        assertEquals("journey", renamedInPlace("trip", before, renaming("trip" to "journey")))

        // Removed, the idea after it moves into its place: that one was there before, so it isn't the idea.
        val removed = listOf(before[0], before[3].copy(index = 0))
        assertNull(renamedInPlace("old", before, removed))
        // Nor is a new idea somewhere else, or an idea the map didn't have before.
        assertNull(renamedInPlace("old", before, removed + MapNode("porto", "Porto", parentId = "trip", depth = 1, index = 1)))
        assertNull(renamedInPlace("gone", before, before))
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
