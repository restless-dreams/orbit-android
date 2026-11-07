package com.hyuchia.orbit.sketch

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A pen: its color (ARGB, opaque) and how wide it draws at an even touch, in sketch units. */
data class SketchPen(val color: Int, val size: Float)

/**
 * One moment of a touch on the canvas, in sketch units: where, how hard
 * (from 0 to 1, where the hardware says; null where it doesn't, as a
 * finger's), how far a stylus leans from upright (radians, 0 without one),
 * and when (ms).
 */
data class InkSample(val x: Float, val y: Float, val pressure: Float?, val tilt: Float, val time: Long)

/**
 * A stroke as it's drawn: its samples smoothed into the line it follows,
 * and how wide it is along it. A stylus's pressure and lean shape the width;
 * a finger, which has neither, draws a little thinner the faster it goes,
 * as ink does.
 */
class StrokeInk(val pen: SketchPen) {
    private val points = mutableListOf<StrokePoint>()
    private var latest: InkSample? = null
    private var x = 0f
    private var y = 0f
    private var width = 0f

    /** How fast the touch moves, in sketch units a millisecond, smoothed. */
    private var speed = 0f

    /** Whether the touch says how hard it presses: a stylus, rather than a finger. */
    private var pressing = false

    val isEmpty: Boolean get() = latest == null

    /** Takes the next sample of the touch. */
    fun add(sample: InkSample) {
        if (!sample.x.isFinite() || !sample.y.isFinite()) {
            return
        }

        val previous = latest
        latest = sample

        if (previous == null) {
            pressing = sample.pressure != null
            x = sample.x
            y = sample.y
            width = widthAt(sample)
            points += StrokePoint(x, y, width)
            return
        }

        val elapsed = (sample.time - previous.time).coerceAtLeast(1)
        val moved = hypot(sample.x - previous.x, sample.y - previous.y)
        speed += (moved / elapsed - speed) * SPEED_EASING
        val easing = if (pressing) STYLUS_EASING else FINGER_EASING
        x += (sample.x - x) * easing
        y += (sample.y - y) * easing
        width += (widthAt(sample) - width) * WIDTH_EASING
        points += StrokePoint(x, y, width)
    }

    /**
     * The stroke so far, to draw while it's drawn: the smoothed line, then
     * the touch's latest sample, which the smoothing trails a little, then
     * where the touch is `predicted` to go next, so the ink keeps up with it.
     */
    fun line(predicted: List<InkSample> = emptyList()): List<StrokePoint> {
        val sample = latest ?: return emptyList()
        val line = ArrayList<StrokePoint>(points.size + predicted.size + 1)
        line += points

        if (points.size > 1) {
            line += StrokePoint(sample.x, sample.y, width)
        }

        for (ahead in predicted) {
            if (ahead.x.isFinite() && ahead.y.isFinite()) {
                line += StrokePoint(ahead.x, ahead.y, width)
            }
        }

        return line
    }

    /** The finished stroke: the smoothed line, ending where the touch let go. */
    fun finished(): List<StrokePoint> = line()

    /** How wide the stroke is at `sample`: by its pressure and lean, or, without them, by how fast it goes. */
    private fun widthAt(sample: InkSample): Float {
        val pressure = sample.pressure

        val share = if (pressure != null) {
            val pressed = pressure.coerceIn(0f, 1f)
            (LIGHTEST + (HEAVIEST - LIGHTEST) * pressed.pow(PRESSURE_CURVE)) * (1 + LEAN * sin(sample.tilt.coerceIn(0f, 1.6f)).pow(2))
        } else {
            SLOWEST - (SLOWEST - FASTEST) * min(1f, speed / FAST)
        }

        return max(StrokeOutline.THINNEST, pen.size * share)
    }

    companion object {
        /** How much of the way to each new sample the line goes: a stylus samples finely, and is followed closer. */
        private const val STYLUS_EASING = 0.62f

        private const val FINGER_EASING = 0.5f
        private const val WIDTH_EASING = 0.38f
        private const val SPEED_EASING = 0.3f

        /** A stylus's width, as a share of the pen's, at the lightest and heaviest press, and how it grows in between. */
        private const val LIGHTEST = 0.3f

        private const val HEAVIEST = 1.5f
        private const val PRESSURE_CURVE = 0.85f

        /** How much wider a stylus draws laid flat. */
        private const val LEAN = 0.4f

        /** A finger's width, as a share of the pen's, still and at speed, and the speed that's fast (units a millisecond). */
        private const val SLOWEST = 1.1f

        private const val FASTEST = 0.8f
        private const val FAST = 1.5f
    }
}
