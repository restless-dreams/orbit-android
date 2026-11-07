package com.hyuchia.orbit.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.sketch.Sketch
import com.hyuchia.orbit.sketch.SketchStroke
import com.hyuchia.orbit.sketch.StrokeOutline
import com.hyuchia.orbit.sketch.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * A sketch in the app's own views (the details, the editor, full screen) is
 * drawn as its canvas draws it: round paper in the planet's color, its
 * strokes over it, clipped to the paper, and nothing outside the circle.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SketchPicturesTest {
    private val paper = 0xFF6750A4.toInt()
    private val ink = 0xFFFFFFFF.toInt()

    /** A white stroke from corner to corner, through the middle, past the paper's edge at both ends. */
    private val sketch = Sketch(paper, listOf(SketchStroke(ink, StrokeOutline.pathData(listOf(StrokePoint(-100f, -100f, 60f), StrokePoint(1100f, 1100f, 60f))))))

    @Test
    fun `a sketch is its round paper and its strokes, at the size asked for`() {
        val bitmap = SketchPictures.draw(sketch, 200)

        assertEquals(200, bitmap.width)
        assertEquals(200, bitmap.height)
        // The stroke through the middle, white; the paper beside it.
        assertEquals(ink, bitmap.getPixel(100, 100))
        assertEquals(paper, bitmap.getPixel(150, 50))
        assertEquals(paper, bitmap.getPixel(50, 150))
        // In the corners the stroke runs on past the paper, and nothing of it shows there.
        assertEquals(0, bitmap.getPixel(5, 5) ushr 24)
        assertEquals(0, bitmap.getPixel(195, 195) ushr 24)
    }
}
