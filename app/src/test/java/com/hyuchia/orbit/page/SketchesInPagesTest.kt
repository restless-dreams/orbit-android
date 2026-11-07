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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.Locale

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

    @Test
    fun `a sketch goes to a TV as the PNG it draws, no larger than the TV's pictures are`() {
        val sent = inlinePictures(markdown, folder, maxSide = 640)

        assertFalse(sent.contains("data:image/svg+xml"))
        assertEquals(640, pictureIn(sent).width)
    }
}
