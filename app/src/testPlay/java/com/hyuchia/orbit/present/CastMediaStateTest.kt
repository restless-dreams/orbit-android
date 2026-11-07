package com.hyuchia.orbit.present

import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.map.Presentation
import com.hyuchia.orbit.map.PresentationControl
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.PageWords
import com.hyuchia.orbit.page.protocolJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A video shown on a TV while a map is presented: what the TV's receiver says of it
 * (web/cast-receiver.ts) reaches the presentation, corrects where the phone reckons the
 * video is, and so where a browser joining halfway opens it; and once the presentation
 * is over, nothing the TV says reaches it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CastMediaStateTest {
    /** The TV's end of the Cast channel: what it was sent, and what it says back, as the session hands it over. */
    private class Tv : TvChannel {
        val sent = mutableListOf<String>()
        var hears: ((String) -> Unit)? = null

        override val name: String = "Living room TV"

        override fun send(message: String) {
            sent += message
        }

        override fun listen(listener: ((String) -> Unit)?) {
            hears = listener
        }

        /** The receiver says `message`, as JSON on the app's channel. */
        fun says(message: String) {
            hears?.invoke(message)
        }

        /** Its video at `time` seconds, `playing` or not, as web/audience.ts reports it. */
        fun video(time: Double, playing: Boolean = true, ended: Boolean = false) {
            says("""{"type":"mediaState","id":"tram","playing":$playing,"time":$time,"duration":189.5,"ended":$ended,"muted":false}""")
        }

        /** The kinds of commands the TV got, in order. */
        fun heard(): List<String> = sent.map { (protocolJson.parseToJsonElement(it).jsonObject["type"] as JsonPrimitive).content }
    }

    /** Browsers following the presentation: what they're sent, and where the phone tells them the video is. */
    private class Browsers : Audience {
        override val name = "browsers"
        val heard = mutableListOf<AudienceCommand>()
        var reckoned: ShownMedia? = null

        override fun send(command: AudienceCommand) {
            heard += command
        }

        override fun reckon(media: ShownMedia?) {
            reckoned = media
        }

        override fun close() = Unit
    }

    private val words = PageWords.from("en") { "" }
    private val video = Media("media/tram.mp4", provider = "native", thumbnail = "media/tram.jpg")
    private val ui = MutableStateFlow(
        MapUi(
            map = MapState("Talk", listOf(MapNode("talk", "Talk"), MapNode("tram", "Tram 28", parentId = "talk", video = video))),
            presentation = Presentation(listOf("talk", "tram"), index = 1, startedAt = 0),
        ),
    )

    private val presentation: Presentation get() = ui.value.presentation!!

    private fun TestScope.control(): PresentationControl = PresentationControl(
        scope = backgroundScope,
        ui = ui,
        clock = { testScheduler.currentTime },
        focus = {},
        map = { presentation -> AudienceCommand.Show("# Talk\n## Tram 28", AppTheme(dark = false, tokens = emptyMap()), focus = presentation.current) },
    )

    /**
     * Waits until the TV got a command of `kind`: a map goes to it once its pictures are put inside, on a thread of
     * Android's own (Dispatchers.IO), so what follows it waits for that too.
     */
    private fun TestScope.delivered(tv: Tv, kind: String) {
        val deadline = System.nanoTime() + 10_000_000_000L

        while (kind !in tv.heard()) {
            check(System.nanoTime() < deadline) { "The TV never got a $kind command: ${tv.heard()}" }
            runCurrent()
            Thread.sleep(10)
        }
    }

    /** A TV whose receiver answered the phone's hello, as today's receivers do, so it gets every command. */
    private fun TestScope.connected(tv: Tv): CastAudience {
        val audience = CastAudience(tv, backgroundScope, words) { it }
        tv.says("""{"type":"receiver","protocol":2,"format":1,"orbit":"abc1234"}""")
        return audience
    }

    @Test
    fun `the TV's word on its video reaches the presentation, and nothing else it says does`() = runTest {
        val tv = Tv()
        val audience = connected(tv)
        val heard = mutableListOf<MapMessage.MediaState>()
        audience.listen { heard += it }

        tv.says("not JSON")
        tv.says("""{"type":"somethingNewer","id":"tram"}""")
        tv.says("""{"type":"mediaState","id":"tram"}""")
        tv.video(12.5)

        assertEquals(listOf(MapMessage.MediaState("tram", playing = true, time = 12.5, duration = 189.5)), heard)
        audience.close()
    }

    @Test
    fun `what the TV says of its video corrects where the phone reckons it is, so a browser joining halfway opens it there`() = runTest {
        val control = control()
        val tv = Tv()
        val browsers = Browsers()
        control.setScreen(connected(tv))
        control.setBrowsers(browsers)
        control.showMedia(MediaKind.Video)
        delivered(tv, "media")
        assertEquals(listOf("hello", "language", "show", "step", "media"), tv.heard())

        // It took the TV a moment to start the video: three seconds in after five.
        advanceTimeBy(5_000)
        tv.video(3.0)
        val corrected = presentation.media!!
        assertEquals(3.0, corrected.position(testScheduler.currentTime), 0.0)
        assertEquals(189.5, corrected.duration)
        assertEquals(corrected, browsers.reckoned)

        // It plays on from there, and a browser joining later opens it where the TV is.
        advanceTimeBy(10_000)
        val late = Browsers()
        control.setBrowsers(late)
        assertEquals(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 13.0), late.heard.last())

        // The TV played it to its end: a browser joining then opens it there, stopped.
        tv.video(189.5, playing = false, ended = true)
        val ended = presentation.media!!
        assertFalse(ended.playingAt(testScheduler.currentTime))
        assertEquals(ended, late.reckoned)
        val last = Browsers()
        control.setBrowsers(last)
        assertEquals(AudienceCommand.Media("tram", MediaKind.Video, playing = false, at = 189.5), last.heard.last())
    }

    @Test
    fun `a word the TV sent before it heard the presenter's pause doesn't undo it`() = runTest {
        val control = control()
        val tv = Tv()
        control.setScreen(connected(tv))
        control.showMedia(MediaKind.Video)
        delivered(tv, "media")
        advanceTimeBy(8_000)

        control.playMedia(false)
        // Sent as the presenter paused, before the TV heard it: still playing, a moment on.
        advanceTimeBy(300)
        tv.video(8.2)
        assertFalse(presentation.media!!.playingAt(testScheduler.currentTime))
        assertEquals(8.0, presentation.media!!.position(testScheduler.currentTime), 0.0)
        // Its length counts all the same.
        assertEquals(189.5, presentation.media!!.duration)

        // Once it heard the pause, what it says is where the video is.
        advanceTimeBy(2_000)
        tv.video(8.1, playing = false)
        assertEquals(8.1, presentation.media!!.position(testScheduler.currentTime + 60_000), 0.0)
    }

    @Test
    fun `once the presentation is over, the phone stops listening to the TV, which goes back to waiting`() = runTest {
        val control = control()
        val tv = Tv()
        val audience = connected(tv)
        control.setScreen(audience)
        control.showMedia(MediaKind.Video)
        runCurrent()

        control.stop()
        audience.close()
        control.setScreen(null)

        assertNull(tv.hears)
        assertNull(presentation.media)
        assertEquals("end", tv.heard().last())
    }
}
