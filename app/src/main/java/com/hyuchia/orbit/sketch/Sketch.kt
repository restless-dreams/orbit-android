package com.hyuchia.orbit.sketch

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** How many sketch units the paper is across: strokes are kept in them, whatever size the canvas has on screen. */
const val SKETCH_SIZE = 1000f

/** The paper's middle, and its radius, in sketch units. */
const val PAPER_CENTER = SKETCH_SIZE / 2

/**
 * A sketch on a planet: its paper, in the color the planet had as it began,
 * and its strokes, oldest first, in sketch units: [SKETCH_SIZE] across the
 * paper's circle, from its top left. Orbit keeps what a sketch draws, each
 * stroke's outline and color, so a sketch reopens as it shows (see
 * [SketchSvg]).
 */
data class Sketch(val paper: Int, val strokes: List<SketchStroke> = emptyList())

/** A point in sketch units. */
data class SketchPoint(val x: Float, val y: Float)

/**
 * One stroke of a sketch: its color (ARGB, opaque) and its outline, as SVG
 * path data in sketch units, filled with the nonzero rule. The outline is
 * what's kept: a stroke from a file is the stroke as it was drawn.
 */
class SketchStroke(val color: Int, val outline: String) {
    /** The outline read once, for drawing and for the eraser. Null when its path data can't be read. */
    val shape: StrokeShape? by lazy { StrokeShape.parse(outline) }

    /**
     * Whether an eraser of `radius`, moving from `from` to `to`, touches the
     * stroke: its edge comes within `radius` of the eraser's way, or the
     * eraser is inside it.
     */
    fun touches(from: SketchPoint, to: SketchPoint, radius: Float): Boolean {
        val shape = shape ?: return false
        val bounds = shape.bounds

        if (max(from.x, to.x) + radius < bounds.left || min(from.x, to.x) - radius > bounds.right) {
            return false
        }

        if (max(from.y, to.y) + radius < bounds.top || min(from.y, to.y) - radius > bounds.bottom) {
            return false
        }

        if (shape.contains(to) || shape.contains(from)) {
            return true
        }

        return shape.contours.any { contour ->
            contour.indices.any { index ->
                val start = contour[index]
                val end = contour[(index + 1) % contour.size]
                segmentDistance(from, to, start, end) <= radius
            }
        }
    }
}

/** A box in sketch units. */
data class SketchBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The shortest distance between segments `a`–`b` and `c`–`d`. */
internal fun segmentDistance(a: SketchPoint, b: SketchPoint, c: SketchPoint, d: SketchPoint): Float {
    if (segmentsCross(a, b, c, d)) {
        return 0f
    }

    return min(min(pointDistance(a, c, d), pointDistance(b, c, d)), min(pointDistance(c, a, b), pointDistance(d, a, b)))
}

/** The distance from `point` to the segment `from`–`to`. */
internal fun pointDistance(point: SketchPoint, from: SketchPoint, to: SketchPoint): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = dx * dx + dy * dy

    if (length == 0f) {
        return hypot(point.x - from.x, point.y - from.y)
    }

    val along = (((point.x - from.x) * dx + (point.y - from.y) * dy) / length).coerceIn(0f, 1f)
    return hypot(point.x - (from.x + along * dx), point.y - (from.y + along * dy))
}

private fun segmentsCross(a: SketchPoint, b: SketchPoint, c: SketchPoint, d: SketchPoint): Boolean {
    val one = side(c, d, a)
    val two = side(c, d, b)
    val three = side(a, b, c)
    val four = side(a, b, d)
    return one * two < 0 && three * four < 0
}

private fun side(from: SketchPoint, to: SketchPoint, point: SketchPoint): Float = (to.x - from.x) * (point.y - from.y) - (to.y - from.y) * (point.x - from.x)

/** Whether a picture's address is a sketch's name, as Orbit names one (`media/sketch-….svg`): what the menu offers to draw on further. */
fun isSketchName(src: String): Boolean {
    val name = src.removePrefix("./").substringAfterLast('/')
    return src.removePrefix("./").startsWith("media/") && name.startsWith("sketch-") && name.endsWith(".svg", ignoreCase = true)
}
