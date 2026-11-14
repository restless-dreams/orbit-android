package com.hyuchia.orbit.media

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.hyuchia.orbit.sketch.Sketch
import com.hyuchia.orbit.sketch.SketchStroke
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.sketch.StrokeOutline
import com.hyuchia.orbit.sketch.StrokePoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.zip.CRC32

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

    @Test
    fun `only a drawing that is one of Orbit's sketches from its first element is taken for one`() {
        val written = SketchSvg.write(sketch)
        assertTrue(SketchSvg.startsAsSketch(written))
        // As the outgoing cleaner writes it again, and with a comment before it.
        assertTrue(SketchSvg.startsAsSketch("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- drawn in Orbit -->\n$written"))
        // A photo whose text names the class, or another drawing with an element of that class inside it, isn't.
        assertFalse(SketchSvg.startsAsSketch("\u0089PNG\r\n\u001a\ntEXtComment\u0000class=\"orbit-sketch\""))
        assertFalse(SketchSvg.startsAsSketch("<svg xmlns=\"http://www.w3.org/2000/svg\"><g class=\"orbit-sketch\"></g></svg>"))
        assertFalse(SketchSvg.startsAsSketch("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"orbit-sketches\"></svg>"))
    }

    @Test
    fun `a photo whose text names the sketches' class shows as the photo it is`() = runBlocking {
        val folder = Files.createTempDirectory("orbit-sketch-pictures").toFile()

        try {
            val photo = File(folder, "marked.png").apply { writeBytes(markedPng()) }
            val sketched = File(folder, "sketch.svg").apply { writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + SketchSvg.write(sketch)) }

            val shown = load(photo)
            assertTrue("$shown", shown is SuccessResult)
            assertEquals(PHOTO, (shown as SuccessResult).image.toBitmap().getPixel(4, 4))

            // A sketch, as it comes back from another device, is still drawn as its canvas draws it.
            val drawn = load(sketched)
            assertTrue("$drawn", drawn is SuccessResult)
            assertEquals(paper, (drawn as SuccessResult).image.toBitmap().let { it.getPixel(it.width / 4, it.height / 2) })
        } finally {
            folder.deleteRecursively()
        }
    }

    private suspend fun load(file: File) = ApplicationProvider.getApplicationContext<Context>().let { context ->
        ImageLoader.Builder(context).components { add(SketchPictures()) }.build().execute(ImageRequest.Builder(context).data(file).build())
    }

    /** A small PNG, all [PHOTO], with a text chunk that says `class="orbit-sketch"`, as any app may write in a picture's comment. */
    private fun markedPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(PHOTO)
        val encoded = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val text = "Comment\u0000class=\"orbit-sketch\"".toByteArray()
        val chunk = ByteBuffer.allocate(12 + text.size)
        chunk.putInt(text.size)
        chunk.put("tEXt".toByteArray())
        chunk.put(text)
        val crc = CRC32()
        crc.update("tEXt".toByteArray())
        crc.update(text)
        chunk.putInt(crc.value.toInt())
        // After the signature and the header chunk, as text chunks go.
        return encoded.copyOfRange(0, PNG_HEADER) + chunk.array() + encoded.copyOfRange(PNG_HEADER, encoded.size)
    }

    private companion object {
        const val PHOTO = 0xFF006A60.toInt()

        /** The PNG signature (8 bytes) and its header chunk (25). */
        const val PNG_HEADER = 33
    }
}
