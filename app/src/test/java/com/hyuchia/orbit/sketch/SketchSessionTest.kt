package com.hyuchia.orbit.sketch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SketchSessionTest {
    private val paper = 0xFF6750A4.toInt()
    private val black = 0xFF1C1B1F.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun session(start: List<SketchStroke> = emptyList()) = SketchSession(paper, start, listOf(black, white))

    private fun sample(x: Float, y: Float, time: Long, pressure: Float? = null) = InkSample(x, y, pressure, 0f, time)

    /** A stroke from `from` to `to`, a sample every 10 units, as a pointer of `tool` draws it. */
    private fun SketchSession.draw(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        tool: TouchTool = TouchTool.Finger,
        pointer: Int = 0,
        start: Long = 0,
        pressure: Float? = null,
        lift: Boolean = true,
    ) {
        val steps = 20
        touch(SketchTouch.Down(pointer, tool, sample(from.first, from.second, start, pressure)))

        val samples = (1..steps).map { step ->
            val share = step.toFloat() / steps
            sample(from.first + (to.first - from.first) * share, from.second + (to.second - from.second) * share, start + step * 8L, pressure)
        }

        touch(SketchTouch.Move(pointer, samples))

        if (lift) {
            touch(SketchTouch.Up(pointer, samples.last()))
        }
    }

    @Test
    fun `a stroke drawn joins the sketch in the pen's color, with undo and redo`() {
        val sketching = session()
        assertFalse(sketching.changed)

        sketching.draw(200f to 500f, 800f to 500f)

        assertEquals(1, sketching.strokes.size)
        assertEquals(black, sketching.strokes.single().color)
        assertTrue(sketching.strokes.single().shape!!.contains(SketchPoint(500f, 500f)))
        assertTrue(sketching.changed)
        assertTrue(sketching.canUndo)
        assertNull(sketching.ink)

        sketching.undo()
        assertEquals(0, sketching.strokes.size)
        assertFalse(sketching.changed)
        assertTrue(sketching.canRedo)

        sketching.redo()
        assertEquals(1, sketching.strokes.size)
    }

    @Test
    fun `the stroke being drawn shows as it goes, ahead to where the touch is going`() {
        val sketching = session()
        sketching.touch(SketchTouch.Down(0, TouchTool.Finger, sample(300f, 500f, 0)))
        sketching.touch(SketchTouch.Move(0, listOf(sample(400f, 500f, 8)), predicted = listOf(sample(600f, 500f, 24))))

        val drawing = sketching.ink as InkInProgress.Drawing
        val shape = StrokeShape.parse(StrokeOutline.pathData(drawing.outline))!!
        assertTrue(shape.contains(SketchPoint(590f, 500f)))
        assertEquals(0, sketching.strokes.size)
    }

    @Test
    fun `the eraser takes the strokes it passes over, in one step undo brings back`() {
        val sketching = session()
        sketching.draw(200f to 300f, 800f to 300f)
        sketching.draw(200f to 700f, 800f to 700f)
        sketching.erasing = true

        sketching.draw(500f to 200f, 500f to 400f)

        assertEquals(1, sketching.strokes.size)
        assertTrue(sketching.strokes.single().shape!!.contains(SketchPoint(500f, 700f)))

        sketching.undo()
        assertEquals(2, sketching.strokes.size)
    }

    @Test
    fun `an eraser that touches nothing changes nothing, and leaves nothing to undo`() {
        val sketching = session()
        sketching.draw(200f to 300f, 800f to 300f)
        sketching.erasing = true
        sketching.draw(200f to 800f, 300f to 800f)
        sketching.undo()
        assertEquals(0, sketching.strokes.size)
    }

    @Test
    fun `a stylus's other end erases whichever tool is chosen`() {
        val sketching = session()
        sketching.draw(200f to 500f, 800f to 500f)
        sketching.draw(500f to 400f, 500f to 600f, tool = TouchTool.StylusEraser, start = 1000)
        assertEquals(0, sketching.strokes.size)
    }

    @Test
    fun `clear takes every stroke, with undo`() {
        val sketching = session()
        sketching.draw(200f to 500f, 800f to 500f)
        sketching.draw(500f to 200f, 500f to 800f)
        sketching.clear()
        assertEquals(0, sketching.strokes.size)
        sketching.undo()
        assertEquals(2, sketching.strokes.size)
    }

    @Test
    fun `a sketch reopened keeps its strokes, draws on top of them, and goes back to them`() {
        val earlier = listOf(SketchStroke(white, StrokeOutline.pathData(listOf(StrokePoint(500f, 500f, 30f)))))
        val sketching = session(earlier)
        assertFalse(sketching.changed)
        sketching.draw(200f to 500f, 800f to 500f)
        assertEquals(listOf(white, black), sketching.strokes.map { it.color })
        sketching.undo()
        assertSame(earlier, sketching.strokes)
        assertFalse(sketching.changed)
    }

    @Test
    fun `a stylus's pressure makes its stroke thinner or wider`() {
        val light = session().apply { draw(200f to 500f, 800f to 500f, tool = TouchTool.Stylus, pressure = 0.1f) }.strokes.single().shape!!
        val heavy = session().apply { draw(200f to 500f, 800f to 500f, tool = TouchTool.Stylus, pressure = 0.9f) }.strokes.single().shape!!
        assertTrue(heavy.bounds.bottom - heavy.bounds.top > 2 * (light.bounds.bottom - light.bounds.top))
    }

    @Test
    fun `a finger draws nothing while a stylus is near, or just after it leaves`() {
        val sketching = session()
        sketching.touch(SketchTouch.Hover(near = true, time = 0))
        sketching.draw(200f to 500f, 800f to 500f, start = 100)
        assertEquals(0, sketching.strokes.size)

        sketching.touch(SketchTouch.Hover(near = false, time = 1000))
        sketching.draw(200f to 500f, 800f to 500f, start = 1000 + SketchSession.STYLUS_GRACE / 2)
        assertEquals(0, sketching.strokes.size)

        sketching.draw(200f to 500f, 800f to 500f, start = 1000 + SketchSession.STYLUS_GRACE + 1)
        assertEquals(1, sketching.strokes.size)
    }

    @Test
    fun `a palm resting before the stylus comes down draws nothing`() {
        val sketching = session()
        // The palm lands first, and drags a little…
        sketching.draw(600f to 700f, 650f to 720f, tool = TouchTool.Finger, pointer = 1, lift = false)
        // …then the stylus draws.
        sketching.draw(200f to 300f, 400f to 300f, tool = TouchTool.Stylus, pointer = 2, start = 200, pressure = 0.5f)
        sketching.touch(SketchTouch.Up(1, null))

        assertEquals(1, sketching.strokes.size)
        assertTrue(sketching.strokes.single().shape!!.contains(SketchPoint(300f, 300f)))
    }

    @Test
    fun `a touch the screen takes for a palm, or takes back as one, draws nothing`() {
        val sketching = session()
        sketching.touch(SketchTouch.Down(0, TouchTool.Finger, sample(500f, 500f, 0), palm = true))
        sketching.touch(SketchTouch.Up(0, sample(500f, 500f, 10)))
        assertEquals(0, sketching.strokes.size)

        sketching.draw(200f to 500f, 800f to 500f, lift = false)
        sketching.touch(SketchTouch.Cancel(0))
        assertEquals(0, sketching.strokes.size)
        assertNull(sketching.ink)
    }

    @Test
    fun `one stroke at a time, and none from far off the paper`() {
        val sketching = session()
        sketching.draw(200f to 500f, 800f to 500f, pointer = 0, lift = false)
        sketching.draw(200f to 300f, 800f to 300f, pointer = 1)
        sketching.touch(SketchTouch.Up(0, null))
        assertEquals(1, sketching.strokes.size)

        sketching.draw(-200f to 500f, 300f to 500f)
        assertEquals(1, sketching.strokes.size)
    }

    @Test
    fun `undo goes back a hundred changes`() {
        val sketching = session()

        repeat(SketchSession.MOST_STEPS + 5) { index ->
            sketching.draw(200f to 100f + index * 5, 800f to 100f + index * 5, start = index * 1000L)
        }

        repeat(SketchSession.MOST_STEPS + 5) { sketching.undo() }
        assertEquals(5, sketching.strokes.size)
    }
}
