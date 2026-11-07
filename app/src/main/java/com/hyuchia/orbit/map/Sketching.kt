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
 * itself is a [SketchSession]'s, which outlasts a screen made again.
 */
class Sketching(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,
    private val maps: MapRepository,
    private val id: String,
    private val page: () -> MapPage,

    /** Sets an attribute of an idea; null removes it. */
    private val update: (nodeId: String, key: String, value: JsonElement?) -> Unit,

    /** Says something on the screen. */
    private val say: (MapEvent) -> Unit,

    /** The file in the map's folder a picture's address points at, if it's one. */
    private val file: (src: String) -> File?,
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

        if (sketching != null && sketching.phase in setOf(SketchPhase.Opening, SketchPhase.Drawing, SketchPhase.Describing)) {
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
        drawing.value = SketchSession(paper, sketch?.strokes.orEmpty(), colors, color = inkFor(paper, colors))
        ui.update { it.copy(sketch = SketchUi(nodeId, SketchPhase.Opening, replacing = replacing, description = node.image?.alt.orEmpty().takeIf { sketch != null }.orEmpty())) }
        page().send(MapCommand.Spotlight(nodeId, room))
    }

    /** What the page says of the planet up close: where it rests, and when the map is back as it was. */
    fun spotlight(message: MapMessage.Spotlight) {
        val sketching = ui.value.sketch ?: return

        when (message.phase) {
            "start" -> ui.update { it.copy(sketch = sketching.copy(scrim = scrimColor(message.scrim) ?: sketching.scrim)) }
            "rest" -> ui.update {
                val circle = PlanetCircle(message.x, message.y, message.radius)
                val phase = if (sketching.phase == SketchPhase.Opening) SketchPhase.Drawing else sketching.phase
                it.copy(sketch = sketching.copy(nodeId = message.id, circle = circle, phase = phase, scrim = scrimColor(message.scrim) ?: sketching.scrim))
            }
            "end" -> {
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
     * With nothing drawn there's nothing to describe: a new sketch leaves
     * nothing behind, and one cleared of its strokes comes off its idea.
     */
    fun done() {
        val sketching = ui.value.sketch?.takeIf { it.phase == SketchPhase.Drawing } ?: return

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
            state.copy(sketch = sketching.copy(phase = phase, discarding = false))
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
        val nodeId = sketching.nodeId
        val alt = description.trim().ifEmpty { null }
        ui.update { it.copy(sketch = sketching.copy(phase = SketchPhase.Saving)) }

        work = scope.launch {
            val node = ui.value.nodes[nodeId]
            val image = node?.image

            when {
                node == null -> Unit
                reopened == null && session.strokes.isEmpty() -> Unit
                session.strokes.isEmpty() -> {
                    update(nodeId, "image", null)
                    say(MapEvent.Snackbar(ShownText.Resource(R.string.sketch_taken_off), undo = true))
                }
                reopened != null && !session.changed -> if (alt != image?.alt) {
                    update(nodeId, "image", mediaChange("alt", alt))
                }
                else -> if (!keep(nodeId, session, alt, image?.caption.takeIf { reopened != null }, sketching.replacing)) {
                    return@launch
                }
            }

            close()
        }
    }

    /** Writes the sketch and puts it on the idea; false, back to drawing, when it couldn't be kept. */
    private suspend fun keep(nodeId: String, session: SketchSession, alt: String?, caption: String?, replacing: Boolean): Boolean {
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

        update(
            nodeId,
            "image",
            buildJsonObject {
                put("src", JsonPrimitive(src))
                alt?.let { put("alt", JsonPrimitive(it)) }
                caption?.let { put("caption", JsonPrimitive(it)) }
            },
        )

        // The canvas lifts once the planet shows the sketch under it, never onto a planet without it.
        try {
            page().request(Boolean.serializer()) { job -> MapCommand.PictureShown(job, src) }
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (unanswered: PageException) {
            Logs.warn("OrbitMap", "The page didn't say the sketch showed", unanswered)
        }

        if (replacing) {
            say(MapEvent.Snackbar(ShownText.Resource(R.string.sketch_replaced_the_picture), undo = true))
        }

        return true
    }

    private fun backToDrawing(problem: ShownText) {
        ui.update { state -> state.copy(sketch = state.sketch?.copy(phase = SketchPhase.Drawing)) }
        say(MapEvent.Snackbar(problem))
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
            SketchPhase.Opening -> close()
            SketchPhase.Drawing, SketchPhase.Describing -> if (drawing.value?.changed == true && !letGo) {
                ui.update { it.copy(sketch = sketching.copy(discarding = true)) }
            } else {
                close()
            }
            SketchPhase.Saving, SketchPhase.Closing -> Unit
        }
    }

    /** The canvas lifts off the planet, then the map flies back; the page says when it's back ([spotlight]). */
    private fun close() {
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
        val back = (returnTo as? Panel.Editor)?.takeIf { panel -> ui.value.nodes.containsKey(panel.id) }?.copy(afterSketch = true)
        returnTo = null
        ui.update { it.copy(sketch = null, panel = back ?: it.panel) }
    }

    /** The page went, and its planet with it: the sketch ends where it is. */
    fun drop() {
        work?.cancel()

        if (ui.value.sketch != null) {
            finish()
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
