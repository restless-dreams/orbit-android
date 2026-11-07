package com.hyuchia.orbit.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import com.hyuchia.orbit.sketch.PathStep
import com.hyuchia.orbit.sketch.SKETCH_SIZE
import com.hyuchia.orbit.sketch.Sketch
import com.hyuchia.orbit.sketch.SketchSvg
import java.io.ByteArrayOutputStream

/**
 * Orbit's own sketches (sketch/SketchSvg.kt) in the app's own views: the
 * details, the editor and the pictures full screen, which Coil draws and
 * which read no SVG otherwise. A sketch is drawn as its canvas draws it, its
 * round paper and its strokes, at the size the view asks for; any other
 * drawing goes on to Coil's decoders, as before.
 */
class SketchPictures : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val source = result.source.source()
        val peek = source.peek()
        peek.request(START)
        val start = peek.readByteString(minOf(START, peek.buffer.size)).utf8()

        if ("class=\"${SketchSvg.SKETCH}\"" !in start) {
            return null
        }

        return Decoder {
            // No bigger than a sketch Orbit reopens: past that, it's another drawing.
            check(!source.request(SketchSvg.LARGEST + 1)) { "Too big for one of Orbit's sketches" }
            val sketch = SketchSvg.read(source.readByteArray()) ?: error("Not one of Orbit's sketches")
            val size = sizeFor(options)
            DecodeResult(draw(sketch, size).asImage(), isSampled = false)
        }
    }

    companion object {
        /** How much of a file tells a sketch from another drawing: as far as its first element's class. */
        private const val START = 512L

        /** The sizes a sketch is drawn at, in px: never smaller than this, nor bigger. */
        private const val SMALLEST = 64

        private const val LARGEST = 2048
        private const val USUAL = 1024

        private fun sizeFor(options: Options): Int {
            val asked = listOf(options.size.width, options.size.height).filterIsInstance<Dimension.Pixels>().maxOfOrNull { it.px }
            return (asked ?: USUAL).coerceIn(SMALLEST, LARGEST)
        }

        /** The sketch on a square of `size` px: its paper round, its strokes clipped to it, outside it nothing. */
        fun draw(sketch: Sketch, size: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = size / SKETCH_SIZE
            val paper = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.clipPath(paper)
            paint.color = sketch.paper
            canvas.drawPath(paper, paint)

            for (stroke in sketch.strokes) {
                val shape = stroke.shape ?: continue
                paint.color = stroke.color
                canvas.drawPath(androidPath(shape.steps, scale), paint)
            }

            return bitmap
        }

        /**
         * The sketch as a PNG of `size` px, for where a picture goes inside a
         * page as a data: URL, which Orbit takes no SVG as, since an SVG can
         * do more than draw: web pages, their prints, and a TV's.
         */
        fun png(sketch: Sketch, size: Int): ByteArray = ByteArrayOutputStream().also { output ->
            val bitmap = draw(sketch, size)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
        }.toByteArray()

        private fun androidPath(steps: List<PathStep>, scale: Float): Path = Path().also { path ->
            path.fillType = Path.FillType.WINDING

            for (step in steps) {
                when (step) {
                    is PathStep.Move -> path.moveTo(step.x * scale, step.y * scale)
                    is PathStep.Line -> path.lineTo(step.x * scale, step.y * scale)
                    is PathStep.Quad -> path.quadTo(step.controlX * scale, step.controlY * scale, step.x * scale, step.y * scale)
                    is PathStep.Cubic -> path.cubicTo(step.firstX * scale, step.firstY * scale, step.secondX * scale, step.secondY * scale, step.x * scale, step.y * scale)
                    PathStep.Close -> path.close()
                }
            }
        }
    }
}
