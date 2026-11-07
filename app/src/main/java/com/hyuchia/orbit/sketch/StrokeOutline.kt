package com.hyuchia.orbit.sketch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** A point on a stroke's middle line, and how wide the stroke is there, in sketch units. */
data class StrokePoint(val x: Float, val y: Float, val width: Float)

/**
 * A stroke's outline from the line it was drawn along: its two sides, as
 * far from the line as the stroke is wide there, and round ends. Where the
 * line turns too sharply for its sides to follow, the stroke goes on as a
 * run of its own from there, round where the runs meet: the runs overlap,
 * turning the same way, so the nonzero rule fills them as one.
 */
object StrokeOutline {
    /** How close a point may follow the one before, as a share of the stroke's width there: closer ones say nothing new. */
    private const val SPACING = 0.12f

    /** The closest points are kept at, in sketch units, however thin the stroke. */
    private const val CLOSEST = 0.6f

    /** How sharp a turn the sides follow: a sharper one (its directions' dot below this, past about 110 degrees) starts a new run. */
    private const val SHARPEST_TURN = -0.35f

    /** The steps a half circle is drawn in: its curves round them off. */
    private const val CAP_STEPS = 6

    /** The steps a dot's circle is drawn in. */
    private const val DOT_STEPS = 12

    /** How far a point may stray from the outline simplified without it, in sketch units. */
    private const val TOLERANCE = 0.2f

    /** The thinnest a stroke is drawn, in sketch units, however light the touch. */
    const val THINNEST = 1.5f

    /** The outline of a stroke through `points`, as closed contours: one for each run of it. Empty for no points. */
    fun of(points: List<StrokePoint>): List<List<SketchPoint>> {
        val spaced = spaced(points)

        if (spaced.isEmpty()) {
            return emptyList()
        }

        if (spaced.size == 1) {
            return listOf(dot(spaced.single()))
        }

        return runs(spaced).map { run -> simplified(if (run.size == 1) dot(run.single()) else outline(run)) }
    }

    /** The outline of a stroke through `points`, as SVG path data in sketch units (see [pathData]). */
    fun pathData(points: List<StrokePoint>): String = pathData(of(points))

    /**
     * Closed contours as SVG path data: a smooth curve through the middle of
     * each side of each contour, each corner its control point, so the curve
     * rounds the corners off. Numbers are tenths of a unit at most, and the
     * curves after the first are relative, which keeps them short.
     */
    @JvmName("pathDataOfContours")
    fun pathData(contours: List<List<SketchPoint>>): String = buildString {
        for (contour in contours) {
            if (contour.size < 3) {
                continue
            }

            // In tenths of a unit, so every number written is exact and each relative step adds up to where it goes.
            val corners = contour.map { Tenths(it.x.tenths(), it.y.tenths()) }
            val count = corners.size
            val middles = List(count) { index -> corners[index].middle(corners[(index + 1) % count]) }
            // From the middle of the last side, round the first corner to the middle of the first side…
            var at = middles[count - 1]
            append('M')
            number(at.x, first = true)
            number(at.y)
            append('Q')
            number(corners[0].x, first = true)
            number(corners[0].y)
            number(middles[0].x)
            number(middles[0].y)
            at = middles[0]

            // …then each corner after it, its control point the reflection of the one before about the middle between them.
            for (index in 1 until count) {
                val next = middles[index]
                append('t')
                number(next.x - at.x, first = true)
                number(next.y - at.y)
                at = next
            }

            append('Z')
        }
    }

    /** A number of tenths written as a decimal, as short as it goes: `-.5`, `12`, `3.4`. Positive ones but the first after a letter take a space before them. */
    private fun StringBuilder.number(tenths: Int, first: Boolean = false) {
        val negative = tenths < 0
        val whole = abs(tenths) / 10
        val tenth = abs(tenths) % 10

        if (negative) {
            append('-')
        } else if (!first) {
            append(' ')
        }

        if (whole != 0 || tenth == 0) {
            append(whole)
        }

        if (tenth != 0) {
            append('.').append(tenth)
        }
    }

    private data class Tenths(val x: Int, val y: Int) {
        /** The middle of the two, in tenths: rounded halves go up, the same way each time. */
        fun middle(other: Tenths): Tenths = Tenths(Math.floorDiv(x + other.x + 1, 2), Math.floorDiv(y + other.y + 1, 2))
    }

    private fun Float.tenths(): Int = (this * 10).roundToInt()

    /** The points far enough apart to say something, the last always kept, so the stroke ends where it was let go. */
    private fun spaced(points: List<StrokePoint>): List<StrokePoint> {
        val kept = mutableListOf<StrokePoint>()

        for ((index, point) in points.withIndex()) {
            if (!point.x.isFinite() || !point.y.isFinite() || !point.width.isFinite()) {
                continue
            }

            val shown = point.copy(width = max(point.width, THINNEST))
            val last = kept.lastOrNull()

            if (last == null) {
                kept += shown
                continue
            }

            val distance = hypot(shown.x - last.x, shown.y - last.y)
            val enough = distance >= max(CLOSEST, SPACING * minOf(shown.width, last.width))

            if (enough) {
                kept += shown
            } else if (index == points.lastIndex && distance > 0f) {
                // The last point replaces one too close before it, unless that's where the stroke began.
                if (kept.size > 1) {
                    kept[kept.lastIndex] = shown
                } else {
                    kept += shown
                }
            }
        }

        return kept
    }

    /** The stroke cut where it turns too sharply, or where a side would fold back on itself, each run sharing its first point with the one before. */
    private fun runs(points: List<StrokePoint>): List<List<StrokePoint>> {
        val runs = mutableListOf<List<StrokePoint>>()
        var start = 0

        for (index in 1 until points.lastIndex) {
            val before = points[index - 1]
            val here = points[index]
            val after = points[index + 1]
            val inX = here.x - before.x
            val inY = here.y - before.y
            val outX = after.x - here.x
            val outY = after.y - here.y
            val turn = (inX * outX + inY * outY) / (hypot(inX, inY) * hypot(outX, outY))

            if (turn < SHARPEST_TURN || folds(before, here, after)) {
                runs += points.subList(start, index + 1)
                start = index
            }
        }

        runs += points.subList(start, points.size)
        return runs
    }

    /**
     * Whether the stroke's sides fold back between `here` and `after`: a turn
     * tighter than the stroke is wide, where the inner side would run
     * backwards.
     */
    private fun folds(before: StrokePoint, here: StrokePoint, after: StrokePoint): Boolean {
        val segmentX = after.x - here.x
        val segmentY = after.y - here.y
        val length = hypot(segmentX, segmentY)

        if (length == 0f) {
            return false
        }

        val (hereX, hereY) = direction(after.x - before.x, after.y - before.y)
        val (afterX, afterY) = direction(segmentX, segmentY)

        for (sign in listOf(1f, -1f)) {
            val fromX = here.x - hereY * here.width / 2 * sign
            val fromY = here.y + hereX * here.width / 2 * sign
            val toX = after.x - afterY * after.width / 2 * sign
            val toY = after.y + afterX * after.width / 2 * sign

            if ((toX - fromX) * segmentX + (toY - fromY) * segmentY <= 0f) {
                return true
            }
        }

        return false
    }

    /** A run's outline: its start's round end, one side forwards, its end's round end, the other side back. */
    private fun outline(run: List<StrokePoint>): List<SketchPoint> {
        val last = run.lastIndex

        val directions = run.indices.map { index ->
            val from = run[maxOf(index - 1, 0)]
            val to = run[minOf(index + 1, last)]
            direction(to.x - from.x, to.y - from.y)
        }

        val left = run.indices.map { index -> side(run[index], directions[index], 1f) }
        val right = run.indices.map { index -> side(run[index], directions[index], -1f) }
        val contour = mutableListOf<SketchPoint>()
        contour += halfCircle(run[0], right[0], directions[0], back = true)
        contour += left
        contour += halfCircle(run[last], left[last], directions[last], back = false)
        contour += right.asReversed()
        return contour
    }

    /** A point `width / 2` off the line at `point`, to its left (`sign` 1) or right (-1) as it goes in `direction`. */
    private fun side(point: StrokePoint, direction: Pair<Float, Float>, sign: Float): SketchPoint {
        val (x, y) = direction
        return SketchPoint(point.x - y * point.width / 2 * sign, point.y + x * point.width / 2 * sign)
    }

    /**
     * The round end around `point`, from `from` (on one side) to the other
     * side: behind the point as the stroke goes (`back`, at its start), or
     * ahead of it (at its end). The points between, not `from` itself.
     */
    private fun halfCircle(point: StrokePoint, from: SketchPoint, direction: Pair<Float, Float>, back: Boolean): List<SketchPoint> {
        val radius = point.width / 2
        val start = atan2(from.y - point.y, from.x - point.x)
        // Turning from one side to the other the way that passes behind the start, or ahead of the end.
        val (x, y) = direction
        val ahead = atan2(y, x)
        val through = if (back) ahead + PI.toFloat() else ahead
        val clockwise = normalized(through - start) > 0
        val steps = max(CAP_STEPS, ceil(radius / 3).toInt().coerceAtMost(16))
        val sweep = PI.toFloat() * if (clockwise) 1 else -1

        return (1 until steps).map { step ->
            val angle = start + sweep * step / steps
            SketchPoint(point.x + radius * cos(angle), point.y + radius * sin(angle))
        }
    }

    /** A dot where the stroke is a tap: a circle as wide as the stroke there. */
    private fun dot(point: StrokePoint): List<SketchPoint> {
        val radius = point.width / 2

        return (0 until DOT_STEPS).map { step ->
            val angle = 2 * PI.toFloat() * step / DOT_STEPS
            SketchPoint(point.x + radius * cos(angle), point.y + radius * sin(angle))
        }
    }

    /** An angle brought within half a turn either way. */
    private fun normalized(angle: Float): Float {
        var turned = angle

        while (turned > PI) {
            turned -= 2 * PI.toFloat()
        }

        while (turned < -PI) {
            turned += 2 * PI.toFloat()
        }

        return turned
    }

    private fun direction(x: Float, y: Float): Pair<Float, Float> {
        val length = hypot(x, y)
        return if (length == 0f) 1f to 0f else (x / length) to (y / length)
    }

    /** A closed contour with the points it can do without left out (Ramer, Douglas and Peucker's way), keeping at least three. */
    private fun simplified(contour: List<SketchPoint>): List<SketchPoint> {
        if (contour.size <= 4) {
            return contour
        }

        val keep = BooleanArray(contour.size + 1)
        val loop = contour + contour.first()
        keep[0] = true
        keep[loop.lastIndex] = true
        // The farthest point from the first splits the loop in two, so neither half is a line from a point to itself.
        val far = contour.indices.maxBy { hypot(contour[it].x - contour[0].x, contour[it].y - contour[0].y) }
        keep[far] = true
        simplify(loop, 0, far, keep)
        simplify(loop, far, loop.lastIndex, keep)
        val kept = contour.indices.filter { keep[it] }.map { contour[it] }
        return if (kept.size >= 3) kept else contour
    }

    private fun simplify(points: List<SketchPoint>, first: Int, last: Int, keep: BooleanArray) {
        if (last - first < 2) {
            return
        }

        var farthest = -1
        var distance = TOLERANCE

        for (index in first + 1 until last) {
            val away = pointDistance(points[index], points[first], points[last])

            if (away > distance) {
                distance = away
                farthest = index
            }
        }

        if (farthest >= 0) {
            keep[farthest] = true
            simplify(points, first, farthest, keep)
            simplify(points, farthest, last, keep)
        }
    }
}
