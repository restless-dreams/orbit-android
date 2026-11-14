package com.hyuchia.orbit.map

import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.after
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How long each idea stays up when a presentation is played by itself without a pace chosen in Settings, in seconds. */
const val PLAY_SECONDS = 8

/**
 * The idea now at the place idea `id` had among `before` (under the same
 * parent, followed the same way if its id went too, and at the same position
 * among its siblings) with an id `before` didn't have: `id` under a new name,
 * as an idea renamed on another device arrives, which says nothing of the
 * rename. Null when there's none: an idea removed, the one after it moving
 * into its place, isn't one, as that one's id was there before.
 */
internal fun renamedInPlace(id: String, before: List<MapNode>, after: List<MapNode>): String? {
    val was = before.firstOrNull { it.id == id } ?: return null
    val known = before.mapTo(HashSet()) { it.id }
    val parent = was.parentId?.let { parentId -> if (after.any { it.id == parentId }) parentId else renamedInPlace(parentId, before, after) ?: return null }
    return after.firstOrNull { it.parentId == parent && it.index == was.index && it.id !in known }?.id
}

/**
 * A presentation as it goes: it steps through its ideas on the presenter's
 * word, and by itself while it plays; it shows the audience an idea's
 * picture or video big over the map, and reckons where a video is from what
 * it tells the screens and what they say back; and it tells every audience
 * each change, catching up the ones that arrive halfway.
 *
 * Nothing steps by itself while a picture or video shows, to the audience
 * or full screen on this one: the idea's time starts over once the map is
 * back. The presentation lives in `ui`; times are milliseconds on `clock`.
 *
 * The map's text can change while it's presented, as a change from
 * elsewhere arrives: the presentation follows it ([mapChanged]), and every
 * audience gets the map as it is now.
 */
class PresentationControl(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,
    private val clock: () -> Long,

    /** Flies the phone's map to an idea. */
    private val focus: (String) -> Unit,

    /** The map for an audience new to it, opening on the idea on screen; null while there's none to show. */
    private val map: (Presentation) -> AudienceCommand.Show?,

    /** The presentation leaves the idea on screen at a time on `clock`, for a rehearsal's times. */
    private val leaving: (Presentation, Long) -> Unit = { _, _ -> },

    /** How long a video is, in seconds, when the phone can tell by itself, as for a file in the map's folder. */
    private val length: suspend (Media) -> Double? = { null },
) {
    private var timer: Job? = null

    /** How long each idea stays up while the presentation plays by itself, in seconds. */
    private var seconds = 0

    /** The screen an audience watches the presentation on, when it isn't this one: a second screen or a TV. */
    private var screen: Audience? = null

    /** Browsers on the local network following the presentation. */
    private var browsers: Audience? = null

    val audiences: List<Audience> get() = listOfNotNull(screen, browsers)

    /** The map's text each audience was last shown: one that hasn't seen the map as it is now gets it ([mapChanged]). */
    private val shownTexts = mutableMapOf<Audience, String>()

    /** The map's text the presentation last followed, here and to every audience. */
    private var presentedText: String? = null

    private val presentation: Presentation? get() = ui.value.presentation

    private fun update(change: (Presentation) -> Presentation) {
        ui.update { state -> state.presentation?.let { state.copy(presentation = change(it)) } ?: state }
    }

    /** The presentation began, playing by itself every `seconds` when that's more than 0: the audiences see the map. */
    fun begin(seconds: Int) {
        this.seconds = seconds
        presentedText = presentation?.let(map)?.markdown
        audiences.forEach(::catchUp)
        retime()
    }

    /** The second screen or TV the audience watches on, or null for none. */
    fun setScreen(audience: Audience?) {
        screen?.let(shownTexts::remove)
        screen = audience
        audience?.listen(::reported)
        audienceChanged(audience)
    }

    /** Browsers following the presentation, or null for none. */
    fun setBrowsers(audience: Audience?) {
        browsers?.let(shownTexts::remove)
        browsers = audience
        audienceChanged(audience)
    }

    private fun audienceChanged(added: Audience?) {
        added?.let(::catchUp)

        // With no one watching elsewhere, what they were shown over the map is gone: this screen shows the map.
        if (audiences.isEmpty() && presentation?.media != null) {
            update { it.copy(media = null) }
            retime()
        }
    }

    /** Shows an audience the presentation as it is: the map, the idea on screen, and a picture or video over it. */
    private fun catchUp(audience: Audience) {
        val presentation = presentation ?: return
        val show = map(presentation) ?: return
        audience.send(show)
        shownTexts[audience] = show.markdown
        audience.send(AudienceCommand.Step(presentation.current, presentation.index, presentation.order.size))
        presentation.media?.let { audience.send(it.command(clock())) }
    }

    /**
     * Loads the presentation again on the second screen or TV after something
     * went wrong there ([Audience.trouble]): it's caught up as a screen that
     * just arrived, at the idea on screen. One that's well is left as it is.
     */
    fun loadScreenAgain() {
        val troubled = screen?.takeIf { it.trouble.value != null } ?: return
        troubled.loadAgain()
        catchUp(troubled)
    }

    /** An idea got a new id from new text: the presentation's steps, and what shows over the map, keep to it. */
    fun renamed(from: String, to: String) {
        update { presentation ->
            presentation.copy(
                order = presentation.order.map { if (it == from) to else it },
                media = presentation.media?.let { shown -> if (shown.id == from) shown.copy(id = to) else shown },
            )
        }
    }

    /**
     * The map as its page has it now, while it's presented, after its text
     * may have changed: a save that crossed the presentation's start, a
     * change from elsewhere, or the map drawn again from its file. The
     * presentation goes on along `order`, its path on the map as it is now:
     * from the idea on screen (its new id followed already, [renamed]), or,
     * when that one's gone, from the step in its place. The phone's map flies
     * there, as it does when new text left it elsewhere, as on the idea's
     * parent once its id changed. The picture or video over the map stays
     * while the idea still has it. Every audience that hasn't seen the map as
     * it is now gets it there.
     */
    fun mapChanged(order: List<String>) {
        val before = presentation ?: return

        if (order.isEmpty()) {
            return
        }

        val index = order.indices.filter { order[it] == before.current }.minByOrNull { abs(it - before.index) } ?: before.index.coerceAtMost(order.lastIndex)
        val current = order[index]
        val node = ui.value.nodes[current]
        val media = before.media?.takeIf { shown -> shown.id == current && (if (shown.isVideo) node?.video else node?.image) != null }
        update { it.copy(order = order, index = index, media = media) }
        val show = presentation?.let(map)
        val newText = show != null && show.markdown != presentedText
        presentedText = show?.markdown ?: presentedText

        if (current != before.current || (newText && ui.value.map?.path?.lastOrNull() != current)) {
            focus(current)
        }

        audiences.forEach { audience ->
            if (show != null && shownTexts[audience] != show.markdown) {
                catchUp(audience)
            } else if (media == null && before.media != null) {
                audience.send(AudienceCommand.CloseMedia)
            }
        }

        if (media != before.media) {
            retime()
        }
    }

    fun setCaptions(on: Boolean) {
        presentation ?: return
        update { it.copy(captions = on) }
        tell(AudienceCommand.Captions(on))
    }

    /**
     * Steps `by` ideas back or on, and the audience flies there, leaving any
     * picture or video for the map. `seconds`, when more than 0, is how long
     * each idea stays up from now on while the presentation plays by itself.
     */
    fun step(by: Int, seconds: Int = 0) {
        val presentation = presentation ?: return
        val index = (presentation.index + by).coerceIn(0, presentation.order.lastIndex)

        // Past the path's first idea or its last, there's nowhere to go: a picture or video over the map still goes.
        if (index == presentation.index) {
            closeMedia()
            return
        }

        if (seconds > 0) {
            this.seconds = seconds
        }

        val now = clock()
        leaving(presentation, now)
        update { it.copy(index = index, stepStartedAt = now, media = null) }
        focus(presentation.order[index])
        // The audience's screens close a picture or video as they step.
        tell(AudienceCommand.Step(presentation.order[index], index, presentation.order.size))
        retime()
    }

    /**
     * Plays the presentation by itself, each idea up for `seconds`, the pace
     * chosen in Settings, or stops it from going on alone. With none chosen
     * (0), it plays at the pace it played at before, or [PLAY_SECONDS]: a
     * pause never changes the pace it goes on at.
     */
    fun setPlaying(playing: Boolean, seconds: Int) {
        presentation ?: return

        if (seconds > 0) {
            this.seconds = seconds
        } else if (playing && this.seconds <= 0) {
            this.seconds = PLAY_SECONDS
        }

        update { it.copy(playing = playing) }
        retime()
    }

    /** Shows the audience the picture of the idea on screen, or plays its video, big over the map. */
    fun showMedia(kind: MediaKind) {
        val presentation = presentation ?: return
        val node = ui.value.nodes[presentation.current] ?: return
        val media = (if (kind == MediaKind.Picture) node.image else node.video) ?: return
        val command = AudienceCommand.Media(node.id, kind, playing = kind == MediaKind.Video)
        update { it.copy(media = it.media.after(command, clock())) }
        tell(command)
        retime()

        if (kind == MediaKind.Video) {
            scope.launch {
                val duration = length(media) ?: return@launch

                // Unless a screen said first, or the presenter moved on.
                update { current ->
                    val shown = current.media?.takeIf { it.id == node.id && it.isVideo && it.duration == null }
                    if (shown == null) current else current.copy(media = shown.copy(duration = duration))
                }
            }
        }
    }

    /** Plays the video shown, or pauses it, where it is: played at its end, it starts over, on every screen alike. */
    fun playMedia(playing: Boolean) {
        val media = presentation?.media?.takeIf { it.isVideo } ?: return

        if (playing && media.atEnd(clock())) {
            startMediaOver()
        } else {
            change(AudienceCommand.Playback(playing))
        }
    }

    /** Plays the video shown again from its start. */
    fun startMediaOver() {
        if (presentation?.media?.isVideo == true) {
            change(AudienceCommand.Seek(0.0))
            change(AudienceCommand.Playback(true))
        }
    }

    /**
     * Plays the live photo shown over the map through once more, on every
     * screen, from its start and muted, as it played when it showed. The
     * phone doesn't reckon with it: the picture stays shown, and each screen
     * settles back on it once the motion ends. A plain picture has none.
     */
    fun replayMotion() {
        val shown = presentation?.media?.takeIf { !it.isVideo } ?: return

        if (ui.value.nodes[shown.id]?.image?.motion != null) {
            tell(AudienceCommand.Playback(true))
        }
    }

    /** Back to the map: the audience sees it again, and a presentation that plays goes on by itself, the idea's time from the start. */
    fun closeMedia() {
        if (presentation?.media == null) {
            return
        }

        change(AudienceCommand.CloseMedia)
        retime()
    }

    /** Tells the audiences of a change to what shows over the map, and reckons with it. */
    private fun change(command: AudienceCommand) {
        val now = clock()
        update { it.copy(media = it.media.after(command, now)) }
        tell(command)
    }

    /** What a screen says of its video corrects where the phone reckons it is, for browsers that join halfway too. */
    private fun reported(report: MapMessage.MediaState) {
        val now = clock()
        update { it.copy(media = it.media?.reported(report, now)) }
        val media = presentation?.media
        audiences.forEach { it.reckon(media) }
    }

    /** The presentation is over: the audiences go back to waiting. */
    fun stop() {
        timer?.cancel()
        tell(AudienceCommand.End)
        shownTexts.clear()
        presentedText = null
    }

    private fun tell(command: AudienceCommand) {
        audiences.forEach { it.send(command) }
    }

    /**
     * Starts the idea's time over while the presentation plays by itself and
     * the map shows, to the audience and here; stops it while a picture or
     * video shows over the map. Call it when either changes.
     */
    fun retime() {
        timer?.cancel()
        val state = ui.value
        val presentation = state.presentation ?: return

        if (!presentation.playing || seconds <= 0 || presentation.media != null || state.fullScreen) {
            return
        }

        timer = scope.launch {
            delay(seconds * 1_000L)
            val current = this@PresentationControl.presentation ?: return@launch

            if (current.index < current.order.lastIndex) {
                step(1)
            } else {
                setPlaying(false, 0)
            }
        }
    }
}
