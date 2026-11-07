package com.hyuchia.orbit.sketch

import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.input.motionprediction.MotionEventPredictor
import kotlin.math.roundToInt

/** A contact this big (its longer side) is a palm resting on the screen, not a finger. */
private val PALM = 30.dp

/** `MotionEvent.FLAG_CANCELED`, which Android 13 sets on a pointer it takes back as a palm; earlier versions never set it. */
private const val FLAG_CANCELED = 0x20

/**
 * The canvas over a planet: its paper and strokes in a circle of `radius`
 * px around `center` (px, from this canvas's top left, which lies over the
 * map's page), and touches anywhere over it, which `session` turns into
 * strokes. The strokes kept are a layer of their own, drawn again only as
 * they change; the stroke being drawn is drawn over them with each move,
 * a frame or two ahead of the touch where it's going, and the touches come
 * as they happen, not a frame at a time, so the ink keeps up with a stylus.
 *
 * `shown` fades the canvas in over the planet and out again, onto the
 * planet with the sketch as its picture, and it takes touches while
 * `drawing`. `description` names the canvas for screen readers, and
 * `strokesSaid` says how much is on it; drawing is by touch. The paper
 * takes `paperModifier` too, as the keyboard's focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SketchCanvas(
    session: SketchSession,
    center: Offset,
    radius: Float,
    shown: Boolean,
    description: String,
    strokesSaid: String,
    modifier: Modifier = Modifier,
    drawing: Boolean = shown,
    paperModifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val predictor = remember(view) { MotionEventPredictor.newInstance(view) }
    val palm = with(density) { PALM.toPx() }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) CANVAS_IN else CANVAS_OUT), label = "canvas")
    val place by rememberUpdatedState(Placed(center, radius))
    val taking by rememberUpdatedState(drawing)
    val elevation = with(density) { 6.dp.toPx() }
    val inkPath = remember { Path() }
    val cache = remember { StrokePaths() }

    Box(
        modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                if (taking) {
                    read(event, session, view, predictor, place, palm)
                }

                true
            }
            // A stylus near the screen: until it leaves, a palm resting by it draws nothing.
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val stylus = event.changes.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }

                        if (stylus && (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) && event.changes.none { it.pressed }) {
                            session.touch(SketchTouch.Hover(near = true, time = event.changes.first().uptimeMillis))
                        } else if (stylus && event.type == PointerEventType.Exit) {
                            session.touch(SketchTouch.Hover(near = false, time = event.changes.first().uptimeMillis))
                        }
                    }
                }
            },
    ) {
        val size = with(density) { (radius * 2).toDp() }
        val corner = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt())

        // The paper and the strokes kept: a layer of its own, with the planet's lift.
        Box(
            Modifier
                .offset { corner }
                .size(size)
                .graphicsLayer {
                    this.alpha = alpha
                    shadowElevation = elevation * alpha
                    shape = CircleShape
                    clip = true
                }
                .drawWithCache {
                    val paths = cache.of(session.strokes, this.size.width / SKETCH_SIZE)

                    onDrawBehind {
                        drawRect(Color(session.paper))

                        for ((path, color) in paths) {
                            drawPath(path, color)
                        }
                    }
                }
                .semantics {
                    contentDescription = description
                    stateDescription = strokesSaid
                }
                .then(paperModifier),
        )

        // The stroke being drawn, or the eraser at work: drawn again with each move.
        Box(
            Modifier
                .offset { corner }
                .size(size)
                .graphicsLayer {
                    this.alpha = alpha
                    shape = CircleShape
                    clip = true
                }
                .drawBehind {
                    val scale = this.size.width / SKETCH_SIZE

                    when (val ink = session.ink) {
                        is InkInProgress.Drawing -> drawPath(contoursPath(ink.outline, scale, inkPath), Color(ink.color))
                        is InkInProgress.Erasing -> {
                            val at = Offset(ink.at.x * scale, ink.at.y * scale)
                            drawCircle(Color.Black.copy(alpha = 0.18f), ink.radius * scale, at)
                            drawCircle(Color.White, ink.radius * scale, at, style = Stroke(width = 2.dp.toPx()))
                            drawCircle(Color.Black.copy(alpha = 0.6f), ink.radius * scale + 1.dp.toPx(), at, style = Stroke(width = 1.dp.toPx()))
                        }
                        null -> Unit
                    }
                },
        )
    }
}

/** How long the canvas takes to come over the planet, and to lift off it (ms). */
const val CANVAS_IN = 160
const val CANVAS_OUT = 180

private data class Placed(val center: Offset, val radius: Float)

/** The strokes' paths at the canvas's scale, each made once: a new stroke adds one, and the eraser or undo makes none. */
private class StrokePaths {
    private var scale = 0f
    private val paths = HashMap<SketchStroke, Path>()

    fun of(strokes: List<SketchStroke>, scale: Float): List<Pair<Path, Color>> {
        if (scale != this.scale) {
            paths.clear()
            this.scale = scale
        }

        val drawn = strokes.mapNotNull { stroke -> stroke.shape?.let { shape -> paths.getOrPut(stroke) { shape.toPath(scale) } to Color(stroke.color) } }

        // Strokes erased or undone a while ago are let go of.
        if (paths.size > strokes.size * 2 + 16) {
            paths.keys.retainAll(strokes.toSet())
        }

        return drawn
    }
}

/**
 * Hands a touch on the canvas to `session`, in sketch units: each pointer's
 * every sample since the last (a stylus reports several a frame), where the
 * touch is predicted to go, what touches the screen (finger, stylus, its
 * eraser, or a palm), and what the system takes back as a palm.
 */
private fun read(event: MotionEvent, session: SketchSession, view: View, predictor: MotionEventPredictor, place: Placed, palm: Float) {
    if (place.radius <= 0f) {
        return
    }

    val toUnits = SKETCH_SIZE / (place.radius * 2)
    val left = place.center.x - place.radius
    val top = place.center.y - place.radius

    fun sample(from: MotionEvent, index: Int, history: Int?): InkSample {
        val stylus = from.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS || from.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER
        val x = if (history == null) from.getX(index) else from.getHistoricalX(index, history)
        val y = if (history == null) from.getY(index) else from.getHistoricalY(index, history)
        val pressure = if (history == null) from.getPressure(index) else from.getHistoricalPressure(index, history)
        val tilt = if (history == null) from.getAxisValue(MotionEvent.AXIS_TILT, index) else from.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, history)
        val time = if (history == null) from.eventTime else from.getHistoricalEventTime(history)
        return InkSample((x - left) * toUnits, (y - top) * toUnits, pressure.takeIf { stylus }, if (stylus) tilt else 0f, time)
    }

    predictor.record(event)

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            // The touches as they come, not a frame at a time: the stroke's tip is where the stylus is.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                view.requestUnbufferedDispatch(event)
            }

            val index = event.actionIndex
            val tool = toolOf(event, index)
            val big = tool == TouchTool.Finger && maxOf(event.getTouchMajor(index), event.getToolMajor(index)) > palm
            session.touch(SketchTouch.Down(event.getPointerId(index), tool, sample(event, index, null), palm = big))
        }
        MotionEvent.ACTION_MOVE -> {
            val predicted = predictor.predict()

            for (index in 0 until event.pointerCount) {
                val pointer = event.getPointerId(index)
                val samples = (0 until event.historySize).map { history -> sample(event, index, history) } + sample(event, index, null)

                val ahead = predicted?.findPointerIndex(pointer)?.takeIf { it >= 0 }?.let { at ->
                    (0 until predicted.historySize).map { history -> sample(predicted, at, history) } + sample(predicted, at, null)
                }.orEmpty()

                session.touch(SketchTouch.Move(pointer, samples, ahead))
            }

            predicted?.recycle()
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
            val index = event.actionIndex
            val pointer = event.getPointerId(index)

            // The system took it back as a palm, after all (Android 13 and later say so; earlier ones never set the flag).
            if (event.flags and FLAG_CANCELED != 0) {
                session.touch(SketchTouch.Cancel(pointer))
            } else {
                session.touch(SketchTouch.Up(pointer, sample(event, index, null)))
            }
        }
        MotionEvent.ACTION_CANCEL -> session.touch(SketchTouch.Cancel(null))
    }
}

/** What touches the screen at pointer `index`: a stylus's other end, or its button held, erases. */
private fun toolOf(event: MotionEvent, index: Int): TouchTool = when (event.getToolType(index)) {
    MotionEvent.TOOL_TYPE_STYLUS -> if (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) TouchTool.StylusEraser else TouchTool.Stylus
    MotionEvent.TOOL_TYPE_ERASER -> TouchTool.StylusEraser
    MotionEvent.TOOL_TYPE_MOUSE -> TouchTool.Mouse
    else -> TouchTool.Finger
}
