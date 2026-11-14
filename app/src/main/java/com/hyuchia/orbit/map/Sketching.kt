package com.hyuchia.orbit.map

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MediaRefusedException
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.sketch.Sketch
import com.hyuchia.orbit.sketch.SketchSession
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.sketch.isSketchName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.IOException

/**
 * Sketching on an idea's planet (sketch/): the page brings the planet up
 * close and dims the map around it, the screen lays its canvas over the
 * planet's circle, and Done keeps the drawing as the idea's picture, an SVG
 * in the map's folder, in one change that one Undo takes back. A sketch
 * there already reopens, to draw on further; a picture of another kind is
 * replaced only once the person says so, with Undo after. The drawing
 * itself is a [SketchSession]'s, which outlasts a screen made again, and a
 * page that stops: it waits, whole, for Load again to draw the map anew, and
 * goes back on its planet then.
 */
class Sketching(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,
    private val maps: MapRepository,
    private val id: String,
    private val page: () -> MapPage,

    /**
     * Sets an attribute of an idea, null removing it, and waits for the page
     * to say what came of it: true once the idea has it, its save on the
     * way; false when the idea isn't on the map now. It throws
     * [PageException] when Orbit declined it, or the page didn't say, as one
     * that stopped (see [MapViewModel.change]).
     */
    private val change: suspend (nodeId: String, key: String, value: JsonElement?) -> Boolean,

    /** Says something on the screen. */
    private val say: (MapEvent) -> Unit,

    /** The file in the map's folder a picture's address points at, if it's one. */
    private val file: (src: String) -> File?,

    /** Which change to each idea's picture is its latest: a copy that lands while the planet is sketched on is asked about. */
    private val pictures: PictureTurns = PictureTurns(),
) {
    private val drawing = MutableStateFlow<SketchSession?>(null)

    /** The drawing while a planet is sketched on. */
    val session: StateFlow<SketchSession?> = drawing.asStateFlow()

    /** The sketch drawn on further, as it was when it reopened; null for a new one. */
    private var reopened: Sketch? = null

    /** The panel the sketch began from, the editor, which comes back after it. */
    private var returnTo: Panel? = null

    /** The room the screen's own bars take around the planet while it's sketched on, in dp. */
    private var room = MapInsets()

    /** The picture the idea had as the sketch opened (null for none), which the sketch may take the place of without asking. */
    private var over: String? = null

    /** The turn the idea's picture was at as the sketch opened: a copy that lands on it meanwhile ends it. */
    private var turn: PictureTurns.Turn? = null

    /** The description the sketch is being kept with, while the person is asked whether it takes a new picture's place. */
    private var describedAs = ""

    /** How many times a drawing that waited for a new page has been brought back up close there, until the page holds it. */
    private var bringingBack = 0

    private var work: Job? = null

    /**
     * Sketches on idea `nodeId`'s planet: a new sketch, or the one it has to
     * draw on further. Its picture of another kind is asked about first.
     */
    fun sketch(nodeId: String) {
        val node = ui.value.nodes[nodeId] ?: return

        if (!ui.value.takesEdits || ui.value.sketch != null) {
            return
        }

        returnTo = ui.value.panel?.takeIf { it is Panel.Editor }
        val image = node.image

        when {
            image == null -> {
                ui.update { it.copy(panel = null) }
                open(nodeId, null, replacing = false)
            }
            // Asked first: the dialog says the sketch takes the picture's place.
            !isSketchName(image.src) -> ui.update { it.copy(sketch = SketchUi(nodeId, SketchPhase.Asking), panel = null) }
            else -> {
                ui.update { it.copy(sketch = SketchUi(nodeId, SketchPhase.Reading), panel = null) }

                work = scope.launch {
                    val existing = readSketch(image.src)

                    if (existing != null) {
                        open(nodeId, existing, replacing = false)
                    } else {
                        // Named as Orbit names sketches, but not one it can draw on: another picture, asked about.
                        ui.update { state -> state.copy(sketch = state.sketch?.copy(phase = SketchPhase.Asking)) }
                    }
                }
            }
        }
    }

    /** The person said a sketch may take the place of the idea's picture. */
    fun replaceAndSketch() {
        val asking = ui.value.sketch?.takeIf { it.phase == SketchPhase.Asking } ?: return
        open(asking.nodeId, null, replacing = true)
    }

    /** The room the screen's own bars take around the planet; a change while it's up close holds it in the room there is now. */
    fun room(insets: MapInsets) {
        if (insets == room) {
            return
        }

        room = insets
        val sketching = ui.value.sketch

        // A sketch waiting for the map to be drawn again has no planet up close: the new page brings it up in the room there is then.
        if (sketching != null && !sketching.held && sketching.phase in setOf(SketchPhase.Opening, SketchPhase.Drawing, SketchPhase.Describing)) {
            page().send(MapCommand.Spotlight(sketching.nodeId, insets))
        }
    }

    private fun open(nodeId: String, sketch: Sketch?, replacing: Boolean) {
        val node = ui.value.nodes[nodeId]

        if (node == null || !ui.value.takesEditsBut(sketch = true)) {
            ui.update { it.copy(sketch = null) }
            return
        }

        val paper = sketch?.paper ?: paperOf(node)
        val colors = penColors(ui.value)
        reopened = sketch
        over = node.image?.src
        turn = pictures.now(nodeId)
        drawing.value = SketchSession(paper, sketch?.strokes.orEmpty(), colors, color = inkFor(paper, colors))
        ui.update { it.copy(sketch = SketchUi(nodeId, SketchPhase.Opening, replacing = replacing, description = node.image?.alt.orEmpty().takeIf { sketch != null }.orEmpty())) }
        page().send(MapCommand.Spotlight(nodeId, room))
    }

    /** What the page says of the planet up close: where it rests, and when the map is back as it was. */
    fun spotlight(message: MapMessage.Spotlight) {
        val sketching = ui.value.sketch ?: return

        if (message.phase == "start" || message.phase == "rest") {
            bringingBack = 0
        }

        when (message.phase) {
            "start" -> ui.update { it.copy(sketch = sketching.copy(scrim = scrimColor(message.scrim) ?: sketching.scrim)) }
            "rest" -> ui.update {
                val circle = PlanetCircle(message.x, message.y, message.radius)
                val phase = if (sketching.phase == SketchPhase.Opening) SketchPhase.Drawing else sketching.phase
                it.copy(sketch = sketching.copy(nodeId = message.id, circle = circle, phase = phase, scrim = scrimColor(message.scrim) ?: sketching.scrim))
            }
            "end" -> {
                // A drawing brought back to a new page that isn't laid out yet (slow to draw) hears the planet can't come up:
                // with its idea still there, it waits, and is brought up again in a moment.
                if (bringingBack in 1 until MOST_BRINGS && sketching.phase == SketchPhase.Opening && sketching.nodeId in ui.value.nodes) {
                    bringBackLater(sketching.nodeId)
                    return
                }

                if (sketching.phase != SketchPhase.Closing && drawing.value?.changed == true) {
                    // The idea went, or the map was drawn again, before the sketch was kept.
                    say(MapEvent.Snackbar(ShownText.Resource(R.string.sketch_lost)))
                }

                finish()
            }
        }
    }

    /**
     * Done drawing: the description comes up, which the sketch is kept with.
     * A stroke still being drawn is kept as it is. With nothing drawn there's
     * nothing to describe: a new sketch leaves nothing behind, and one
     * cleared of its strokes comes off its idea.
     */
    fun done() {
        val sketching = ui.value.sketch?.takeIf { it.phase == SketchPhase.Drawing } ?: return
        drawing.value?.finishInk()

        if (drawing.value?.strokes?.isEmpty() == true) {
            save(sketching.description)
            return
        }

        ui.update { state -> state.sketch?.takeIf { it.phase == SketchPhase.Drawing }?.let { state.copy(sketch = it.copy(phase = SketchPhase.Describing)) } ?: state }
    }

    /** Back to drawing: from the description, or from being asked to let the strokes go. */
    fun keepDrawing() {
        ui.update { state ->
            val sketching = state.sketch ?: return@update state
            val phase = if (sketching.phase == SketchPhase.Describing) SketchPhase.Drawing else sketching.phase
            state.copy(sketch = sketching.copy(phase = phase, discarding = false, confirming = false))
        }
    }

    /**
     * Keeps the sketch as the idea's picture, described as `description`
     * says, in one change: a new file, so earlier versions keep the one they
     * point at. A sketch left as it was changes nothing but its description;
     * one cleared of every stroke takes the picture off; a new one left blank
     * leaves nothing behind. Once the page shows the new picture under the
     * canvas, the canvas lifts and the map flies back.
     */
    fun save(description: String) {
        val sketching = ui.value.sketch?.takeIf { it.phase == SketchPhase.Describing || it.phase == SketchPhase.Drawing } ?: return
        val session = drawing.value ?: return
        session.finishInk()
        val nodeId = sketching.nodeId
        val alt = description.trim().ifEmpty { null }
        describedAs = description
        ui.update { it.copy(sketch = sketching.copy(phase = SketchPhase.Saving)) }

        work = scope.launch {
            val node = ui.value.nodes[nodeId]
            val image = node?.image

            when {
                node == null -> Unit
                reopened == null && session.strokes.isEmpty() -> Unit
                session.strokes.isEmpty() -> {
                    if (!taken(nodeId, null)) {
                        return@launch
                    }

                    say(MapEvent.Snackbar(ShownText.Resource(R.string.sketch_taken_off), undo = true))
                }
                reopened != null && !session.changed -> if (alt != image?.alt && !taken(nodeId, mediaChange("alt", alt))) {
                    return@launch
                }
                else -> if (!keep(nodeId, session, alt, image?.caption.takeIf { reopened != null && !sketching.replacing }, sketching.replacing)) {
                    return@launch
                }
            }

            close()
        }
    }

    /**
     * Gives idea `nodeId`'s picture `image` (null takes it off) and waits for
     * the page to take it; false, back to drawing with the drawing whole, when
     * it didn't, as a page that stopped meanwhile.
     */
    private suspend fun taken(nodeId: String, image: JsonElement?): Boolean {
        val taken = try {
            change(nodeId, "image", image)
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (unanswered: PageException) {
            Logs.warn("OrbitMap", "The page didn't take the sketch's change", unanswered)
            false
        }

        if (!taken) {
            backToDrawing(ShownText.Resource(R.string.sketch_not_kept))
        }

        return taken
    }

    /**
     * Writes the sketch and puts it on the idea; false, back to drawing, when
     * it couldn't be kept: one the page says it can't show stays off the
     * idea, which keeps the picture it had. A page that doesn't say in time
     * whether it shows, as one off screen, doesn't keep it off; but it's kept
     * only once the page says the idea took it, so a page that stopped, or
     * an idea gone meanwhile, never closes the canvas on a sketch kept
     * nowhere.
     */
    private suspend fun keep(nodeId: String, session: SketchSession, alt: String?, caption: String?, replacing: Boolean): Boolean {
        if (askedAboutLanding(nodeId)) {
            return false
        }

        val src = try {
            maps.addSketch(id, SketchSvg.write(session.sketch).toByteArray())
        } catch (refused: MediaRefusedException) {
            backToDrawing(ShownText.Plain(refused.message.orEmpty()))
            return false
        } catch (failed: IOException) {
            Logs.warn("OrbitMap", "Couldn't keep the sketch", failed)
            backToDrawing(ShownText.Resource(R.string.sketch_not_kept))
            return false
        }

        // The page loads it first: a planet never trades its picture for one it says it can't show, and a page that stopped shows none.
        if (shown(src) == false) {
            maps.takeBack(id, listOf(src))
            backToDrawing(ShownText.Resource(R.string.sketch_not_kept))
            return false
        }

        // A picture that landed as the page loaded the sketch is asked about too.
        if (askedAboutLanding(nodeId)) {
            maps.takeBack(id, listOf(src))
            return false
        }

        val image = buildJsonObject {
            put("src", JsonPrimitive(src))
            alt?.let { put("alt", JsonPrimitive(it)) }
            caption?.let { put("caption", JsonPrimitive(it)) }
        }

        // Kept once the page says the idea took it, the map's save with it on its way to the app first.
        val taken = try {
            change(nodeId, "image", image)
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (unanswered: PageException) {
            Logs.warn("OrbitMap", "The page didn't take the sketch", unanswered)
            // Asked to take the sketch, the idea's picture turned: the next Done goes from the picture there is then.
            turn = pictures.now(nodeId)

            // Declined, it's nowhere; unanswered, a page that answers late may yet have put it on the idea, so its file
            // stays for the tidying up, which deletes it once nothing points at it.
            if (unanswered.kind == PageException.Kind.Refused) {
                maps.takeBack(id, listOf(src))
            } else {
                maps.letGo(id, listOf(src))
            }

            backToDrawing(ShownText.Resource(R.string.sketch_not_kept))
            return false
        }

        if (!taken) {
            turn = pictures.now(nodeId)
            maps.takeBack(id, listOf(src))
            backToDrawing(ShownText.Resource(R.string.sketch_not_kept))
            return false
        }

        // The canvas lifts once the planet shows the sketch under it, never onto a planet without it.
        shown(src)

        if (replacing) {
            say(MapEvent.Snackbar(ShownText.Resource(R.string.sketch_replaced_the_picture), undo = true))
        }

        return true
    }

    /**
     * Whether a picture landed on idea `nodeId` while it was sketched on, as
     * one chosen before the sketch began that took a while to copy in: the
     * sketch takes its place only once the person says so
     * ([replaceLanded]), so true asks them, back to drawing.
     */
    private fun askedAboutLanding(nodeId: String): Boolean {
        val now = ui.value.nodes[nodeId]?.image?.src
        val landed = turn?.let { !pictures.current(it) } == true || (now != null && now != over)

        if (!landed) {
            return false
        }

        ui.update { state -> state.copy(sketch = state.sketch?.let { it.copy(phase = if (it.held) SketchPhase.Opening else SketchPhase.Drawing, confirming = true) }) }
        return true
    }

    /** The person said the sketch may take the place of the picture that landed on its idea while they drew: it's kept, with Undo. */
    fun replaceLanded() {
        val sketching = ui.value.sketch?.takeIf { it.confirming } ?: return
        over = ui.value.nodes[sketching.nodeId]?.image?.src
        turn = pictures.now(sketching.nodeId)
        ui.update { state -> state.copy(sketch = state.sketch?.copy(confirming = false, replacing = true)) }
        save(describedAs)
    }

    /**
     * Whether the page says the picture at `src` loaded, and is drawn where
     * the map has it: false when it says it can't load, or the page stopped;
     * null when it doesn't say in time, as a page off screen, which draws no
     * frames.
     */
    private suspend fun shown(src: String): Boolean? = try {
        page().request(Boolean.serializer()) { job -> MapCommand.PictureShown(job, src) }
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (unanswered: PageException) {
        Logs.warn("OrbitMap", "The page didn't say the sketch showed", unanswered)
        false.takeIf { unanswered.kind == PageException.Kind.Stopped }
    }

    /** Back to drawing, saying why; a sketch waiting for the map to be drawn again waits on, and the map's place says why. */
    private fun backToDrawing(problem: ShownText) {
        ui.update { state -> state.copy(sketch = state.sketch?.let { it.copy(phase = if (it.held) SketchPhase.Opening else SketchPhase.Drawing) }) }

        if (ui.value.sketch?.held != true) {
            say(MapEvent.Snackbar(problem))
        }
    }

    /**
     * Stops sketching. While it's asked about, or the map still flies to the
     * planet, it just stops; with strokes drawn, the person is asked first,
     * unless they said `letGo` already. Nothing of it stays.
     */
    fun cancel(letGo: Boolean = false) {
        val sketching = ui.value.sketch ?: return

        when (sketching.phase) {
            SketchPhase.Reading, SketchPhase.Asking -> {
                work?.cancel()
                finish()
            }
            // Strokes drawn are asked about as they would be on the planet: while the drawing waits for the map to be drawn
            // again, and while it's brought back up close to it, however long that takes.
            SketchPhase.Opening -> if (drawing.value?.changed == true && !letGo) {
                ui.update { it.copy(sketch = sketching.copy(discarding = true)) }
            } else {
                close()
            }
            SketchPhase.Drawing, SketchPhase.Describing -> {
                // A stroke still being drawn is a change too: kept, it's asked about with the rest.
                drawing.value?.finishInk()

                if (drawing.value?.changed == true && !letGo) {
                    ui.update { it.copy(sketch = sketching.copy(discarding = true)) }
                } else {
                    close()
                }
            }
            SketchPhase.Saving, SketchPhase.Closing -> Unit
        }
    }

    /** The canvas lifts off the planet, then the map flies back; the page says when it's back ([spotlight]). */
    private fun close() {
        // Waiting for the map to be drawn again, there's no planet up close to fly back from.
        if (ui.value.sketch?.held == true) {
            finish()
            return
        }

        ui.update { state -> state.sketch?.let { state.copy(sketch = it.copy(phase = SketchPhase.Closing, discarding = false)) } ?: state }

        scope.launch {
            delay(CANVAS_FADE)
            page().send(MapCommand.EndSpotlight)
            // A page that never says the map is back (its process stopped meanwhile) still lets the screen go on.
            delay(LONGEST_FLIGHT)

            if (ui.value.sketch?.phase == SketchPhase.Closing) {
                finish()
            }
        }
    }

    /** The sketch is over: the map's own bars come back, and the editor if the sketch began there. */
    private fun finish() {
        drawing.value = null
        reopened = null
        bringingBack = 0
        val back = (returnTo as? Panel.Editor)?.takeIf { panel -> ui.value.nodes.containsKey(panel.id) }?.copy(afterSketch = true)
        returnTo = null
        ui.update { it.copy(sketch = null, panel = back ?: it.panel) }
    }

    /**
     * The page that drew the map stopped, or didn't load, or another takes
     * its place (Load again): its planet went with it, but the drawing waits,
     * whole, its description too, and goes back on its planet once a page
     * draws the map again ([pageReady]). One being kept as the page stopped
     * waits too, unless the page took it first. Asked about, or still being
     * read to draw on further, the sketch just stops: nothing was drawn.
     */
    fun pageStopped() {
        val sketching = ui.value.sketch ?: return

        when (sketching.phase) {
            SketchPhase.Reading, SketchPhase.Asking -> {
                work?.cancel()
                finish()
            }
            SketchPhase.Closing -> finish()
            SketchPhase.Opening, SketchPhase.Drawing, SketchPhase.Describing, SketchPhase.Saving -> ui.update { state ->
                state.copy(sketch = state.sketch?.copy(phase = SketchPhase.Opening, circle = null, held = true, discarding = false))
            }
        }
    }

    /**
     * A page drew the map again, after Load again: a drawing that waited
     * goes back on its planet, which the page brings up close again. Its
     * idea gone meanwhile, the sketch ends, saying so if anything was drawn.
     */
    fun pageReady() {
        val sketching = ui.value.sketch?.takeIf { it.held } ?: return

        if (sketching.nodeId !in ui.value.nodes) {
            if (drawing.value?.changed == true) {
                say(MapEvent.Snackbar(ShownText.Resource(R.string.sketch_lost)))
            }

            finish()
            return
        }

        ui.update { state -> state.copy(sketch = state.sketch?.copy(held = false)) }
        bringingBack = 1
        page().send(MapCommand.Spotlight(sketching.nodeId, room))
    }

    /** Brings the drawing that waited up close again on idea `nodeId`'s planet, in a moment, unless the sketch ended or the page stopped meanwhile. */
    private fun bringBackLater(nodeId: String) {
        bringingBack += 1

        scope.launch {
            delay(BRING_BACK_WAIT)
            val sketching = ui.value.sketch

            if (sketching != null && !sketching.held && sketching.phase == SketchPhase.Opening && sketching.nodeId == nodeId) {
                page().send(MapCommand.Spotlight(nodeId, room))
            }
        }
    }

    /** The sketch a picture in the map's folder holds, when it's one of Orbit's own; null for any other picture. */
    private suspend fun readSketch(src: String): Sketch? = withContext(Dispatchers.IO) {
        val picture = file(src)?.takeIf { it.isFile && it.length() <= SketchSvg.LARGEST && it.name.endsWith(".svg", ignoreCase = true) } ?: return@withContext null

        try {
            SketchSvg.read(picture.readBytes())
        } catch (unreadable: IOException) {
            null
        }
    }

    companion object {
        /** How long the canvas takes to lift off the planet, before the map flies back (ms). */
        const val CANVAS_FADE = 180L

        /** The longest the flight back takes, and then some (ms). */
        private const val LONGEST_FLIGHT = 2_000L

        /** How long a drawing that waited for a new page waits before it's brought up close again, the page not laid out yet (ms). */
        private const val BRING_BACK_WAIT = 500L

        /** How many times it's brought up close, ten seconds' worth, before the sketch ends as one whose planet is gone. */
        private const val MOST_BRINGS = 20

        /** The paper when the planet's color can't be read: a light neutral. */
        private const val PLAIN_PAPER = 0xFFECE6F0.toInt()

        private const val BLACK = 0xFF1C1B1F.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** How many of the map's colors the pens offer, beside black and white. */
        private const val MAP_PENS = 6

        /** The paper of a new sketch: the planet's color as the map draws it. */
        fun paperOf(node: MapNode): Int = opaqueHex(node.drawnColor) ?: PLAIN_PAPER

        /** The pens' colors: the map's own, as its center and branches are drawn, then black and white. */
        fun penColors(ui: MapUi): List<Int> {
            val map = ui.map?.nodes.orEmpty().filter { it.depth <= 1 }.mapNotNull { node -> opaqueHex(node.drawnColor) }
            return (map.distinct().take(MAP_PENS) + BLACK + WHITE).distinct()
        }

        /**
         * A color the page drew with, as CSS writes it in hex (`#rgb`, `#rgba`,
         * `#rrggbb`, `#rrggbbaa`), made opaque; null for any other way of
         * writing one, as a theme's variable the page couldn't resolve.
         */
        fun opaqueHex(css: String?): Int? {
            val digits = css?.trim()?.takeIf { it.startsWith("#") }?.drop(1) ?: return null

            val full = when (digits.length) {
                3, 4 -> digits.take(3).map { "$it$it" }.joinToString("")
                6, 8 -> digits.take(6)
                else -> return null
            }

            return full.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
        }

        /** The pen to start with: black on light paper, white on dark. */
        fun inkFor(paper: Int, colors: List<Int>): Int {
            val light = androidx.compose.ui.graphics.Color(paper).luminance() > 0.4f
            return (if (light) BLACK else WHITE).takeIf { it in colors } ?: colors.first()
        }

        /** A CSS color the page resolved, as `rgb(…)` or `rgba(…)`, as ARGB. */
        fun scrimColor(css: String?): Int? {
            val numbers = css?.trim()?.let { Regex("""^rgba?\(([^)]*)\)$""").find(it) }?.groupValues?.get(1)
                ?.split(',', '/', ' ')?.filter { it.isNotBlank() }?.map { it.trim() } ?: return null

            if (numbers.size !in 3..4) {
                return null
            }

            val (red, green, blue) = numbers.take(3).map { it.toFloatOrNull()?.coerceIn(0f, 255f) ?: return null }
            val alpha = numbers.getOrNull(3)?.let { written -> if (written.endsWith('%')) written.dropLast(1).toFloatOrNull()?.div(100) else written.toFloatOrNull() } ?: 1f
            return androidx.compose.ui.graphics.Color(red / 255f, green / 255f, blue / 255f, alpha.coerceIn(0f, 1f)).toArgb()
        }
    }
}

/** Whether a press on the map can lead to an edit, but for a sketch under way when `sketch` says to leave it out. */
private fun MapUi.takesEditsBut(sketch: Boolean): Boolean = if (sketch) copy(sketch = null).takesEdits else takesEdits
