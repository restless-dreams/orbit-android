package com.hyuchia.orbit.sketch

import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * Orbit's own SVG for a sketch: a plain drawing any app shows, and what
 * Orbit reads back to draw on further.
 *
 * ```
 * <svg xmlns="http://www.w3.org/2000/svg" class="orbit-sketch" viewBox="0 0 1000 1000" width="1000" height="1000">
 *   <defs><clipPath id="paper"><circle cx="500" cy="500" r="500"/></clipPath></defs>
 *   <circle class="orbit-sketch__paper" cx="500" cy="500" r="500" fill="#6750a4"/>
 *   <g clip-path="url(#paper)">
 *     <path class="orbit-sketch__stroke" fill="#1c1b1f" d="…"/>
 *   </g>
 * </svg>
 * ```
 *
 * The paper is a circle in the planet's color, as the canvas was; each
 * stroke is its outline, filled, oldest first, clipped to the paper. That's
 * all a sketch is, so it needs nothing kept beside the drawing: every
 * element and attribute is one the outgoing cleaner keeps
 * (`media/PassiveDrawings.kt`), and a sketch sent to another device
 * reopens there too. The class `orbit-sketch` on the drawing is how Orbit
 * knows its own: a later version that draws sketches another way, one this
 * version would misread, names its drawings otherwise, and this version
 * shows them as any picture.
 */
object SketchSvg {
    const val SVG = "http://www.w3.org/2000/svg"

    /** The class Orbit's sketches carry, and their paper's and strokes'. */
    const val SKETCH = "orbit-sketch"

    private const val PAPER = "orbit-sketch__paper"
    private const val STROKE = "orbit-sketch__stroke"

    /** The most strokes a sketch reopens with, and the biggest file: past them, it's a picture like any other. */
    private const val MOST_STROKES = 20_000

    const val LARGEST = 8L * 1024 * 1024

    /** The drawing of `sketch`, as Orbit keeps it. */
    fun write(sketch: Sketch): String = buildString {
        val size = SKETCH_SIZE.toInt()
        val half = size / 2
        append("""<svg xmlns="$SVG" class="$SKETCH" viewBox="0 0 $size $size" width="$size" height="$size">""")
        append('\n')
        append("""<defs><clipPath id="paper"><circle cx="$half" cy="$half" r="$half"/></clipPath></defs>""")
        append('\n')
        append("""<circle class="$PAPER" cx="$half" cy="$half" r="$half" fill="${hex(sketch.paper)}"/>""")
        append('\n')
        append("""<g clip-path="url(#paper)">""")
        append('\n')

        for (stroke in sketch.strokes) {
            if (stroke.outline.isNotEmpty()) {
                append("""<path class="$STROKE" fill="${hex(stroke.color)}" d="${stroke.outline}"/>""")
                append('\n')
            }
        }

        append("</g>\n</svg>\n")
    }

    /**
     * The sketch `bytes` hold, when they're one of Orbit's own, written as
     * [write] writes them; null for any other drawing or file, which shows as
     * a picture like any other and can't be drawn on further.
     */
    fun read(bytes: ByteArray): Sketch? {
        if (bytes.size > LARGEST || !looksLikeSketch(bytes)) {
            return null
        }

        val reader = Reader()

        return try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }

            factory.newSAXParser().parse(ByteArrayInputStream(bytes), reader)
            reader.sketch()
        } catch (unreadable: SAXException) {
            null
        } catch (unreadable: IOException) {
            null
        } catch (unreadable: ParserConfigurationException) {
            null
        } catch (unreadable: IllegalArgumentException) {
            null
        }
    }

    /** Whether `bytes` start as Orbit's sketches do, without a document type, whose entities could put anything anywhere. */
    private fun looksLikeSketch(bytes: ByteArray): Boolean {
        val text = String(bytes, Charsets.UTF_8)
        return startsAsSketch(text.take(START)) && !text.contains("<!DOCTYPE", ignoreCase = true) && !text.contains("<!ENTITY", ignoreCase = true)
    }

    /** How much of a file tells one of Orbit's sketches from anything else: as far as its drawing's own class. */
    const val START = 512

    /**
     * Whether `start`, a file's first [START] characters, is the start of one
     * of Orbit's sketches: an SVG drawing whose own element carries the class
     * `orbit-sketch`, after nothing but an XML declaration (as the outgoing
     * cleaner writes one) and comments. A picture of another kind that merely
     * holds those words, in a PNG's text say, isn't one.
     */
    fun startsAsSketch(start: String): Boolean = SKETCH_START.containsMatchIn(start)

    private val SKETCH_START = Regex("""^\uFEFF?\s*(<\?xml[^>]*>\s*)?(<!--.*?-->\s*)*<svg\b[^>]*\sclass="$SKETCH"[\s/>]""", RegexOption.DOT_MATCHES_ALL)

    /** `#rrggbb`, for an opaque color. */
    fun hex(color: Int): String = "#%06x".format(color and 0xFFFFFF)

    /** An opaque color from `#rrggbb` or `#rgb`; null for anything else. */
    fun color(value: String?): Int? {
        val digits = value?.trim()?.removePrefix("#")?.takeIf { value.trim().startsWith("#") } ?: return null

        val full = when (digits.length) {
            6 -> digits
            3 -> digits.map { "$it$it" }.joinToString("")
            else -> return null
        }

        return full.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    }

    /** Reads a drawing element by element, and gives up at the first one a sketch doesn't have. */
    private class Reader : DefaultHandler() {
        private var depth = 0
        private var paper: Int? = null
        private val strokes = mutableListOf<SketchStroke>()
        private var readable = true

        /** Where the reader is: in the drawing, its definitions and their clip, or the group of strokes. */
        private val open = ArrayDeque<String>()

        fun sketch(): Sketch? = paper?.takeIf { readable && open.isEmpty() && depth == 0 }?.let { Sketch(it, strokes.toList()) }

        override fun startElement(uri: String, localName: String, qualifiedName: String, attributes: Attributes) {
            depth += 1

            if (!readable || uri != SVG) {
                readable = false
                return
            }

            val parent = open.lastOrNull()
            val classes = attributes.getValue("", "class")?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()

            readable = when (localName) {
                "svg" -> parent == null && SKETCH in classes && sizeIsSketchSize(attributes.getValue("", "viewBox"))
                "defs" -> parent == "svg"
                "clipPath" -> parent == "defs"
                "circle" -> when (parent) {
                    "clipPath" -> true
                    "svg" -> PAPER in classes && paper == null && color(attributes.getValue("", "fill"))?.also { paper = it } != null
                    else -> false
                }
                "g" -> parent == "svg"
                "path" -> (parent == "g" || parent == "svg") && STROKE in classes && strokes.size < MOST_STROKES && addStroke(attributes)
                else -> false
            }

            open.addLast(localName)
        }

        override fun endElement(uri: String, localName: String, qualifiedName: String) {
            depth -= 1
            open.removeLastOrNull()
        }

        private fun addStroke(attributes: Attributes): Boolean {
            val color = color(attributes.getValue("", "fill")) ?: return false
            val outline = attributes.getValue("", "d") ?: return false
            val stroke = SketchStroke(color, outline)

            // A stroke Orbit couldn't draw or erase isn't one of its own.
            if (stroke.shape == null) {
                return false
            }

            strokes += stroke
            return true
        }

        private fun sizeIsSketchSize(viewBox: String?): Boolean {
            val numbers = viewBox?.trim()?.split(Regex("[\\s,]+"))?.map { it.toFloatOrNull() } ?: return false
            return numbers == listOf(0f, 0f, SKETCH_SIZE, SKETCH_SIZE)
        }
    }
}
