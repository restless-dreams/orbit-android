package com.hyuchia.orbit.sketch

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot

/** The pens' widths, in sketch units: fine, medium and bold. */
enum class PenSize(val units: Float) {
    Fine(10f),
    Medium(20f),
    Bold(40f),
}

/** What touches the canvas. */
enum class TouchTool {
    Finger,
    Stylus,

    /** A stylus's other end, or the stylus with its button held: it erases whatever tool is chosen. */
    StylusEraser,

    Mouse,
}

/** A touch on the canvas, as the screen hands it on, in sketch units. */
sealed interface SketchTouch {
    /**
     * A pointer touched down. `palm` says the screen took it for one (its
     * contact too big for a finger). `first` says no other is down: one the
     * session still has down was let go where it didn't see.
     */
    data class Down(val pointer: Int, val tool: TouchTool, val sample: InkSample, val palm: Boolean = false, val first: Boolean = false) : SketchTouch

    /** A pointer moved through `samples`, oldest first, and is `predicted` to go on through those. */
    data class Move(val pointer: Int, val samples: List<InkSample>, val predicted: List<InkSample> = emptyList()) : SketchTouch

    /** A pointer let go, at `sample` when the screen says where. */
    data class Up(val pointer: Int, val sample: InkSample?) : SketchTouch

    /** The system took a pointer back, as a palm it recognized late: what it drew goes. Null takes them all. */
    data class Cancel(val pointer: Int?) : SketchTouch

    /** A stylus came near the screen, or left it. */
    data class Hover(val near: Boolean, val time: Long) : SketchTouch
}

/** The stroke being drawn, to draw over the sketch: its color and outline; or where the eraser is. */
sealed interface InkInProgress {
    data class Drawing(val color: Int, val outline: List<List<SketchPoint>>) : InkInProgress

    data class Erasing(val at: SketchPoint, val radius: Float) : InkInProgress
}

/**
 * Drawing on a planet: the sketch as it is, the pen or the eraser, and the
 * stroke being drawn, with undo and redo. It turns the canvas's touches into
 * strokes, a stylus's taking over from fingers: while a stylus is near the
 * screen, or was a moment ago, touches of a finger or a palm draw nothing,
 * and a stroke a palm began just before the stylus came down goes.
 */
@Stable
class SketchSession(
    /** The planet's color, which the paper is. */
    val paper: Int,

    /** The strokes it starts with: none for a new sketch, or one's to draw on further. */
    private val start: List<SketchStroke>,

    /** The pens' colors. */
    val colors: List<Int>,

    /** The pen's color to begin with. */
    color: Int = colors.firstOrNull() ?: DEFAULT_INK,
) {
    var strokes by mutableStateOf(start)
        private set

    var color by mutableIntStateOf(color)

    var size by mutableStateOf(PenSize.Medium)

    /** The eraser's on, rather than the pen. */
    var erasing by mutableStateOf(false)

    /** The stroke being drawn, or the eraser at work, to draw over the sketch; null between strokes. */
    var ink by mutableStateOf<InkInProgress?>(null)
        private set

    private val undone = ArrayDeque<List<SketchStroke>>()
    private val redone = ArrayDeque<List<SketchStroke>>()
    private var steps by mutableIntStateOf(0)

    val canUndo: Boolean get() = steps.let { undone.isNotEmpty() }
    val canRedo: Boolean get() = steps.let { redone.isNotEmpty() }

    /** Whether the sketch differs from how it began. */
    val changed: Boolean get() = strokes !== start

    /** The sketch as it is now. */
    val sketch: Sketch get() = Sketch(paper, strokes)

    private var active: Active? = null

    /** Until when touches of a finger draw nothing, a stylus being near (ms). */
    private var stylusNearUntil = Long.MIN_VALUE

    private class Active(val pointer: Int, val tool: TouchTool, val startedAt: Long, val before: List<SketchStroke>, val ink: StrokeInk?, var at: SketchPoint)

    /** Takes a touch on the canvas. */
    fun touch(touch: SketchTouch) {
        when (touch) {
            is SketchTouch.Down -> down(touch)
            is SketchTouch.Move -> move(touch)
            is SketchTouch.Up -> up(touch)
            is SketchTouch.Cancel -> if (touch.pointer == null || touch.pointer == active?.pointer) {
                cancel()
            }
            is SketchTouch.Hover -> stylusNearUntil = if (touch.near) Long.MAX_VALUE else touch.time + STYLUS_GRACE
        }
    }

    private fun down(touch: SketchTouch.Down) {
        val sample = touch.sample
        val stylus = touch.tool == TouchTool.Stylus || touch.tool == TouchTool.StylusEraser

        // Nothing else is down: a stroke still going was let go unseen, as while the canvas took no touches. It's kept as drawn.
        if (touch.first) {
            finishInk()
        }

        if (stylus) {
            // Near until it lifts: if it draws nothing here, as off the paper, for a moment at least.
            stylusNearUntil = maxOf(stylusNearUntil, sample.time + STYLUS_GRACE)
            val current = active

            // A finger or a palm was already drawing: the hand resting before the stylus came down. What it drew goes.
            if (current != null && current.tool != TouchTool.Stylus && current.tool != TouchTool.StylusEraser) {
                cancel()
            }
        } else if (touch.palm || (touch.tool == TouchTool.Finger && sample.time < stylusNearUntil)) {
            return
        }

        // One stroke at a time, and only on the paper or right by it.
        if (active != null || hypot(sample.x - PAPER_CENTER, sample.y - PAPER_CENTER) > PAPER_CENTER + REACH) {
            return
        }

        val erases = erasing || touch.tool == TouchTool.StylusEraser
        val ink = if (erases) null else StrokeInk(SketchPen(color, size.units)).also { it.add(sample) }
        active = Active(touch.pointer, touch.tool, sample.time, strokes, ink, SketchPoint(sample.x, sample.y))

        // A stylus drawing is near until it lifts.
        if (stylus) {
            stylusNearUntil = Long.MAX_VALUE
        }

        if (ink != null) {
            this.ink = InkInProgress.Drawing(color, StrokeOutline.of(ink.line()))
        } else {
            erase(SketchPoint(sample.x, sample.y))
        }
    }

    private fun move(touch: SketchTouch.Move) {
        val current = active?.takeIf { it.pointer == touch.pointer } ?: return

        if (current.ink != null) {
            touch.samples.forEach(current.ink::add)
            touch.samples.lastOrNull()?.let { current.at = SketchPoint(it.x, it.y) }
            ink = InkInProgress.Drawing(current.ink.pen.color, StrokeOutline.of(current.ink.line(touch.predicted)))
        } else {
            for (sample in touch.samples) {
                erase(SketchPoint(sample.x, sample.y))
            }
        }
    }

    private fun up(touch: SketchTouch.Up) {
        val current = active?.takeIf { it.pointer == touch.pointer } ?: return
        active = null
        ink = null

        if (current.tool == TouchTool.Stylus || current.tool == TouchTool.StylusEraser) {
            stylusNearUntil = (touch.sample?.time ?: current.startedAt) + STYLUS_GRACE
        }

        if (current.ink != null) {
            touch.sample?.let(current.ink::add)
            val outline = StrokeOutline.pathData(current.ink.finished())

            if (outline.isNotEmpty()) {
                record(current.before)
                strokes = strokes + SketchStroke(current.ink.pen.color, outline)
            }
        } else {
            touch.sample?.let { erase(SketchPoint(it.x, it.y)) }

            if (strokes !== current.before) {
                record(current.before)
            }
        }
    }

    /**
     * Keeps the stroke being drawn as it is, as if its pointer were let go
     * where it last was, and what the eraser took so far: before the drawing
     * changes hands, as Done or closing take it while a pointer is still
     * down. That pointer draws nothing more, and the next stroke can start.
     */
    fun finishInk() {
        val current = active ?: return
        up(SketchTouch.Up(current.pointer, null))
    }

    /** The stroke being drawn goes, and what an eraser took comes back. */
    private fun cancel() {
        val current = active ?: return
        active = null
        ink = null
        strokes = current.before

        // A stylus taken back is near no more than its hovering says from here.
        if (current.tool == TouchTool.Stylus || current.tool == TouchTool.StylusEraser) {
            stylusNearUntil = Long.MIN_VALUE
        }
    }

    /** The eraser moves on to `to`: every stroke it passes over goes. */
    private fun erase(to: SketchPoint) {
        val current = active ?: return
        val from = current.at
        val kept = strokes.filterNot { it.touches(from, to, ERASER_RADIUS) }

        if (kept.size != strokes.size) {
            strokes = kept
        }

        current.at = to
        ink = InkInProgress.Erasing(to, ERASER_RADIUS)
    }

    /** Takes back the last change: a stroke, what one sweep of the eraser took, or a clear. */
    fun undo() {
        val previous = undone.removeLastOrNull() ?: return
        redone.addLast(strokes)
        strokes = previous
        steps += 1
    }

    fun redo() {
        val next = redone.removeLastOrNull() ?: return
        undone.addLast(strokes)
        strokes = next
        steps += 1
    }

    /** Takes every stroke off the paper, with undo. */
    fun clear() {
        if (strokes.isEmpty()) {
            return
        }

        cancel()
        record(strokes)
        strokes = emptyList()
    }

    private fun record(before: List<SketchStroke>) {
        undone.addLast(before)

        while (undone.size > MOST_STEPS) {
            undone.removeFirst()
        }

        redone.clear()
        steps += 1
    }

    companion object {
        /** How long after a stylus leaves that a finger draws again (ms): a hand lifting with it is no stroke. */
        const val STYLUS_GRACE = 600L

        /** How far outside the paper a stroke may start, in sketch units: aiming at its edge, a touch may land just past it. */
        const val REACH = 40f

        /** How far around it the eraser takes strokes, in sketch units. */
        const val ERASER_RADIUS = 36f

        /** How many changes undo goes back. */
        const val MOST_STEPS = 100

        /** A pen's color when the map gives none. */
        const val DEFAULT_INK = 0xFF1C1B1F.toInt()
    }
}
