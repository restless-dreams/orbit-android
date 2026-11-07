package com.hyuchia.orbit.sketch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StrokeOutlineTest {
    private fun line(vararg points: Pair<Float, Float>, width: Float = 20f) = points.map { (x, y) -> StrokePoint(x, y, width) }

    private fun shape(points: List<StrokePoint>): StrokeShape = requireNotNull(StrokeShape.parse(StrokeOutline.pathData(points))) { "No shape for $points" }

    @Test
    fun `a straight stroke covers its line, as wide as it is, round at both ends`() {
        val stroke = shape(line(100f to 500f, 300f to 500f, 500f to 500f, 700f to 500f))

        // Along the line and across it, inside the width; past it, outside.
        for (x in listOf(100f, 250f, 500f, 690f)) {
            assertTrue("$x on the line", stroke.contains(SketchPoint(x, 500f)))
            assertTrue("$x near its edge", stroke.contains(SketchPoint(x, 508f)))
            assertFalse("$x past its edge", stroke.contains(SketchPoint(x, 513f)))
        }

        // The round ends reach half the width past the first and last points, and no further.
        assertTrue(stroke.contains(SketchPoint(92f, 500f)))
        assertFalse(stroke.contains(SketchPoint(86f, 500f)))
        assertTrue(stroke.contains(SketchPoint(708f, 500f)))
        assertFalse(stroke.contains(SketchPoint(714f, 500f)))
        assertEquals(SketchBounds(90f, 490f, 710f, 510f).left, stroke.bounds.left, 1.5f)
    }

    @Test
    fun `a stroke is as wide as each of its points says`() {
        val stroke = shape(listOf(StrokePoint(200f, 500f, 10f), StrokePoint(400f, 500f, 30f), StrokePoint(600f, 500f, 50f)))

        assertTrue(stroke.contains(SketchPoint(200f, 503f)))
        assertFalse(stroke.contains(SketchPoint(200f, 508f)))
        assertTrue(stroke.contains(SketchPoint(600f, 522f)))
        assertFalse(stroke.contains(SketchPoint(600f, 528f)))
    }

    @Test
    fun `a tap is a dot as wide as the pen`() {
        val outline = StrokeOutline.of(line(500f to 500f, width = 30f))
        assertEquals(1, outline.size)
        val dot = shape(line(500f to 500f, width = 30f))
        assertTrue(dot.contains(SketchPoint(500f, 500f)))
        assertTrue(dot.contains(SketchPoint(512f, 500f)))
        assertFalse(dot.contains(SketchPoint(518f, 500f)))
    }

    @Test
    fun `points too close together say nothing new, but the stroke still ends where it was let go`() {
        val jitter = line(100f to 100f, 100.1f to 100f, 100.2f to 100.1f, 200f to 100f, 200.1f to 100f)
        val stroke = shape(jitter)
        assertTrue(stroke.contains(SketchPoint(208f, 100f)))
        assertFalse(stroke.contains(SketchPoint(213f, 100f)))
    }

    @Test
    fun `a sharp turn starts a new run, round where they meet, and both fill as one`() {
        val vee = line(100f to 100f, 300f to 500f, 500f to 100f)
        assertEquals(2, StrokeOutline.of(vee).size)
        val stroke = shape(vee)

        // Both arms, and the point of the V, rounded.
        assertTrue(stroke.contains(SketchPoint(200f, 300f)))
        assertTrue(stroke.contains(SketchPoint(400f, 300f)))
        assertTrue(stroke.contains(SketchPoint(300f, 508f)))
        assertFalse(stroke.contains(SketchPoint(300f, 515f)))
        // Inside the V, away from both arms, nothing is filled.
        assertFalse(stroke.contains(SketchPoint(300f, 300f)))
    }

    @Test
    fun `a curl tighter than the stroke is wide fills without holes`() {
        val curl = (0..24).map { step ->
            val angle = step * PI.toFloat() / 6
            StrokePoint(500f + 12f * cos(angle), 500f + 12f * sin(angle), 40f)
        }

        val stroke = shape(curl)

        // Everywhere within the curl and its width is ink.
        for (step in 0 until 36) {
            val angle = step * PI.toFloat() / 18

            for (distance in listOf(0f, 6f, 12f, 20f, 28f)) {
                assertTrue("$distance at $step", stroke.contains(SketchPoint(500f + distance * cos(angle), 500f + distance * sin(angle))))
            }
        }
    }

    @Test
    fun `path data is short, exact to a tenth, and a smooth curve through the outline`() {
        val data = StrokeOutline.pathData(line(100f to 500f, 700f to 500f))
        assertTrue(data, data.startsWith("M"))
        assertTrue(data, data.contains("Q"))
        assertTrue(data, data.contains("t"))
        assertTrue(data, data.endsWith("Z"))
        // Numbers are tenths at most, never longer.
        assertFalse(data, Regex("""\d\.\d\d""").containsMatchIn(data))
        assertTrue(data.length < 400)
    }

    @Test
    fun `no points, or points that aren't numbers, make no stroke`() {
        assertEquals("", StrokeOutline.pathData(emptyList<StrokePoint>()))
        assertEquals("", StrokeOutline.pathData(line(Float.NaN to 1f)))
    }
}
