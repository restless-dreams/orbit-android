package com.hyuchia.orbit.sketch

import kotlin.math.max
import kotlin.math.min

/** One step of a stroke's outline, in absolute sketch units. */
sealed interface PathStep {
    data class Move(val x: Float, val y: Float) : PathStep

    data class Line(val x: Float, val y: Float) : PathStep

    data class Quad(val controlX: Float, val controlY: Float, val x: Float, val y: Float) : PathStep

    data class Cubic(val firstX: Float, val firstY: Float, val secondX: Float, val secondY: Float, val x: Float, val y: Float) : PathStep

    data object Close : PathStep
}

/**
 * A stroke's outline, read from its SVG path data: the steps that draw it,
 * and its contours as points, its curves followed closely enough to tell
 * where the eraser touches it.
 */
class StrokeShape(val steps: List<PathStep>, val contours: List<List<SketchPoint>>, val bounds: SketchBounds) {
    /** Whether `point` is inside the outline, by the nonzero rule it's filled with. */
    fun contains(point: SketchPoint): Boolean {
        if (point.x < bounds.left || point.x > bounds.right || point.y < bounds.top || point.y > bounds.bottom) {
            return false
        }

        var winding = 0

        for (contour in contours) {
            for (index in contour.indices) {
                val start = contour[index]
                val end = contour[(index + 1) % contour.size]

                if (start.y <= point.y) {
                    if (end.y > point.y && cross(start, end, point) > 0) {
                        winding += 1
                    }
                } else if (end.y <= point.y && cross(start, end, point) < 0) {
                    winding -= 1
                }
            }
        }

        return winding != 0
    }

    companion object {
        /** How many pieces a curve is followed in, for the eraser. */
        private const val QUAD_PIECES = 4

        private const val CUBIC_PIECES = 6

        /** The most numbers a stroke's path data may hold: what a sketch's file can't take is no sketch Orbit reopens. */
        private const val MOST_NUMBERS = 200_000

        /**
         * Reads SVG path data: moves, lines (and horizontal and vertical ones),
         * quadratic and cubic curves (and their smooth forms), and closes,
         * absolute or relative. Null for anything else, as arcs, or for data
         * that isn't path data at all.
         */
        fun parse(data: String): StrokeShape? {
            val tokens = tokenize(data) ?: return null
            val steps = mutableListOf<PathStep>()
            var x = 0f
            var y = 0f
            var startX = 0f
            var startY = 0f
            // The control point a smooth curve reflects: the last curve's, when the step before was one of its kind.
            var lastQuad: SketchPoint? = null
            var lastCubic: SketchPoint? = null
            var index = 0
            var command = ' '

            fun number(): Float? = (tokens.getOrNull(index) as? Token.Number)?.value?.also { index += 1 }

            while (index < tokens.size) {
                val token = tokens[index]

                if (token is Token.Command) {
                    command = token.letter
                    index += 1
                } else if (command == ' ' || command == 'Z' || command == 'z') {
                    // Numbers before any command, or after a close, aren't path data.
                    return null
                }

                val relative = command.isLowerCase()
                val dx = if (relative) x else 0f
                val dy = if (relative) y else 0f

                when (command.uppercaseChar()) {
                    'M' -> {
                        val nextX = (number() ?: return null) + dx
                        val nextY = (number() ?: return null) + dy
                        steps += PathStep.Move(nextX, nextY)
                        x = nextX
                        y = nextY
                        startX = nextX
                        startY = nextY
                        lastQuad = null
                        lastCubic = null
                        // Pairs after a move's first are lines.
                        command = if (relative) 'l' else 'L'
                    }
                    'L' -> {
                        val nextX = (number() ?: return null) + dx
                        val nextY = (number() ?: return null) + dy
                        steps += PathStep.Line(nextX, nextY)
                        x = nextX
                        y = nextY
                        lastQuad = null
                        lastCubic = null
                    }
                    'H' -> {
                        x = (number() ?: return null) + dx
                        steps += PathStep.Line(x, y)
                        lastQuad = null
                        lastCubic = null
                    }
                    'V' -> {
                        y = (number() ?: return null) + dy
                        steps += PathStep.Line(x, y)
                        lastQuad = null
                        lastCubic = null
                    }
                    'Q', 'T' -> {
                        val control = if (command.uppercaseChar() == 'Q') {
                            SketchPoint((number() ?: return null) + dx, (number() ?: return null) + dy)
                        } else {
                            lastQuad?.let { SketchPoint(2 * x - it.x, 2 * y - it.y) } ?: SketchPoint(x, y)
                        }

                        val nextX = (number() ?: return null) + dx
                        val nextY = (number() ?: return null) + dy
                        steps += PathStep.Quad(control.x, control.y, nextX, nextY)
                        x = nextX
                        y = nextY
                        lastQuad = control
                        lastCubic = null
                    }
                    'C', 'S' -> {
                        val first = if (command.uppercaseChar() == 'C') {
                            SketchPoint((number() ?: return null) + dx, (number() ?: return null) + dy)
                        } else {
                            lastCubic?.let { SketchPoint(2 * x - it.x, 2 * y - it.y) } ?: SketchPoint(x, y)
                        }

                        val second = SketchPoint((number() ?: return null) + dx, (number() ?: return null) + dy)
                        val nextX = (number() ?: return null) + dx
                        val nextY = (number() ?: return null) + dy
                        steps += PathStep.Cubic(first.x, first.y, second.x, second.y, nextX, nextY)
                        x = nextX
                        y = nextY
                        lastCubic = second
                        lastQuad = null
                    }
                    'Z' -> {
                        steps += PathStep.Close
                        x = startX
                        y = startY
                        lastQuad = null
                        lastCubic = null
                    }
                    else -> return null
                }
            }

            if (steps.isEmpty() || steps.first() !is PathStep.Move) {
                return null
            }

            val contours = contoursOf(steps)
            val points = contours.flatten()

            if (points.isEmpty()) {
                return null
            }

            val bounds = SketchBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
            return StrokeShape(steps, contours, bounds)
        }

        private sealed interface Token {
            data class Command(val letter: Char) : Token

            data class Number(val value: Float) : Token
        }

        private val COMMANDS = "MmLlHhVvQqTtCcSsZz".toSet()

        private fun tokenize(data: String): List<Token>? {
            val tokens = mutableListOf<Token>()
            var index = 0
            var numbers = 0

            while (index < data.length) {
                val letter = data[index]

                when {
                    letter.isWhitespace() || letter == ',' -> index += 1
                    letter in COMMANDS -> {
                        tokens += Token.Command(letter)
                        index += 1
                    }
                    letter == '-' || letter == '+' || letter == '.' || letter.isDigit() -> {
                        val end = numberEnd(data, index)
                        val value = data.substring(index, end).toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
                        tokens += Token.Number(value)
                        index = end
                        numbers += 1

                        if (numbers > MOST_NUMBERS) {
                            return null
                        }
                    }
                    else -> return null
                }
            }

            return tokens
        }

        /** Where the number starting at `start` ends: a sign, digits with one point at most, and an exponent. */
        private fun numberEnd(data: String, start: Int): Int {
            var index = start

            if (data[index] == '-' || data[index] == '+') {
                index += 1
            }

            var point = false

            while (index < data.length && (data[index].isDigit() || (data[index] == '.' && !point))) {
                if (data[index] == '.') {
                    point = true
                }

                index += 1
            }

            if (index < data.length && (data[index] == 'e' || data[index] == 'E')) {
                var after = index + 1

                if (after < data.length && (data[after] == '-' || data[after] == '+')) {
                    after += 1
                }

                if (after < data.length && data[after].isDigit()) {
                    index = after

                    while (index < data.length && data[index].isDigit()) {
                        index += 1
                    }
                }
            }

            return index
        }

        /** The steps' contours as points, each curve followed in a few straight pieces. */
        private fun contoursOf(steps: List<PathStep>): List<List<SketchPoint>> {
            val contours = mutableListOf<List<SketchPoint>>()
            var current = mutableListOf<SketchPoint>()
            var x = 0f
            var y = 0f

            fun finish() {
                if (current.size >= 2) {
                    contours += current
                }

                current = mutableListOf()
            }

            for (step in steps) {
                when (step) {
                    is PathStep.Move -> {
                        finish()
                        current += SketchPoint(step.x, step.y)
                        x = step.x
                        y = step.y
                    }
                    is PathStep.Line -> {
                        current += SketchPoint(step.x, step.y)
                        x = step.x
                        y = step.y
                    }
                    is PathStep.Quad -> {
                        for (piece in 1..QUAD_PIECES) {
                            val t = piece.toFloat() / QUAD_PIECES
                            val u = 1 - t
                            current += SketchPoint(u * u * x + 2 * u * t * step.controlX + t * t * step.x, u * u * y + 2 * u * t * step.controlY + t * t * step.y)
                        }

                        x = step.x
                        y = step.y
                    }
                    is PathStep.Cubic -> {
                        for (piece in 1..CUBIC_PIECES) {
                            val t = piece.toFloat() / CUBIC_PIECES
                            val u = 1 - t
                            current += SketchPoint(
                                u * u * u * x + 3 * u * u * t * step.firstX + 3 * u * t * t * step.secondX + t * t * t * step.x,
                                u * u * u * y + 3 * u * u * t * step.firstY + 3 * u * t * t * step.secondY + t * t * t * step.y,
                            )
                        }

                        x = step.x
                        y = step.y
                    }
                    PathStep.Close -> {
                        val start = current.firstOrNull()
                        finish()

                        if (start != null) {
                            x = start.x
                            y = start.y
                        }
                    }
                }
            }

            finish()
            return contours
        }

        private fun cross(start: SketchPoint, end: SketchPoint, point: SketchPoint): Float =
            (end.x - start.x) * (point.y - start.y) - (point.x - start.x) * (end.y - start.y)
    }
}

/** The smallest box around both. */
internal fun SketchBounds.union(other: SketchBounds): SketchBounds =
    SketchBounds(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
