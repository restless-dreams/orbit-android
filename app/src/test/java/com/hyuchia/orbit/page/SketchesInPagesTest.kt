package com.hyuchia.orbit.page

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.sketch.Sketch
import com.hyuchia.orbit.sketch.SketchStroke
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.sketch.StrokeOutline
import com.hyuchia.orbit.sketch.StrokePoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.Locale
import java.util.Random

/**
 * A sketch inside a page goes as the PNG it draws: Orbit takes no SVG inside
 * a page as a data: URL, so as an SVG it would show nowhere there.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SketchesInPagesTest {
    private val folder: File = Files.createTempDirectory("orbit-sketch-page").toFile()

    private val paper = 0xFF6750A4.toInt()

    private val ink = 0xFFFFFFFF.toInt()

    /** A white stroke across the middle of the paper. */
    private val sketch = Sketch(paper, listOf(SketchStroke(ink, StrokeOutline.pathData(listOf(StrokePoint(100f, 500f, 60f), StrokePoint(900f, 500f, 60f))))))

    private val markdown = "# Trip\n![A white line](media/sketch-line.svg)\n"

    @Before
    fun write() {
        File(folder, "media").mkdirs()
        File(folder, "media/sketch-line.svg").writeText(SketchSvg.write(sketch))
    }

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    /** The picture of the first PNG data: URL in `text`. */
    private fun pictureIn(text: String): Bitmap {
        val base64 = text.substringAfter("data:image/png;base64,").takeWhile { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        val bytes = Base64.getDecoder().decode(base64)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Test
    fun `a sketch goes inside a web page, and its print, as the PNG it draws`() {
        val output = ByteArrayOutputStream()
        val template = File("src/main/assets/map/web-page.html").readText()
        WebPage.write(output, template, "", "Trip", markdown, AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, FileWords(Locale.US).pageWords(), null, PageMedia(folder), "Orbit")
        val page = output.toString(Charsets.UTF_8.name())

        assertFalse(page.contains("data:image/svg+xml"))
        val picture = pictureIn(page)
        assertEquals(1024, picture.width)
        // The paper above the stroke, the stroke across the middle, and nothing outside the paper.
        assertEquals(paper, picture.getPixel(512, 200))
        assertEquals(ink, picture.getPixel(512, 512))
        assertEquals(0, picture.getPixel(4, 4) ushr 24)
    }

    /** Thousands of strokes of their own colors, dotted all over the paper: as near noise as a sketch gets. */
    private fun busy(): Sketch {
        val random = Random(84721)

        val strokes = (1..20_000).map {
            val dots = (1..4).joinToString(" ") {
                val x = random.nextInt(997)
                val y = random.nextInt(997)
                "M$x $y L${x + 3} $y L${x + 3} ${y + 3} L$x ${y + 3} Z"
            }

            SketchStroke(0xFF000000.toInt() or random.nextInt(0x1000000), dots)
        }

        return Sketch(paper, strokes)
    }

    /** The page of a map with the sketch `media/sketch-line.svg`, and the most it said it may come to; `room` hears that first. */
    private fun pageOfSketch(room: () -> Unit = {}): Pair<String, Long> {
        val output = ByteArrayOutputStream()
        val template = File("src/main/assets/map/web-page.html").readText()
        var most = 0L

        WebPage.write(output, template, "", "Trip", markdown, AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, FileWords(Locale.US).pageWords(), null, PageMedia(folder), "Orbit") { size ->
            most = size
            room()
        }

        assertTrue("${output.size()} of $most", output.size() <= most)
        return output.toString(Charsets.UTF_8.name()) to most
    }

    @Test
    fun `a busy sketch goes 1024 pixels across, as big as its PNG is, and the page makes room for all of it`() {
        File(folder, "media/sketch-line.svg").writeText(SketchSvg.write(busy()))
        val (page, _) = pageOfSketch()

        val png = Base64.getDecoder().decode(page.substringAfter("data:image/png;base64,").takeWhile { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' })
        assertEquals(1024, pictureIn(page).width)
        // Past half a megabyte, which a page once took for the most any sketch came to.
        assertTrue("${png.size}", png.size > 512 * 1024)
    }

    @Test
    fun `a sketch drawn on after its page made room for it keeps to that room`() {
        // The white line as the page counts it, then thousands of strokes more before it's written, as another device's sync brings.
        val (page, _) = pageOfSketch { File(folder, "media/sketch-line.svg").writeText(SketchSvg.write(busy())) }

        // Smaller to fit, or out of the page, its idea without it: never bigger than the page said.
        if (page.contains("data:image/png;base64,")) {
            assertTrue(pictureIn(page).width < 1024)
        } else {
            assertTrue(page.contains("media/sketch-line.svg"))
        }
    }

    @Test
    fun `a sketch goes to a TV as the PNG it draws, no larger than the TV's pictures are`() {
        val sent = inlinePictures(markdown, folder, maxSide = 640)

        assertFalse(sent.contains("data:image/svg+xml"))
        assertEquals(640, pictureIn(sent).width)
    }
}
