package com.hyuchia.orbit.sketch

import com.hyuchia.orbit.media.SharedPictures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SketchSvgTest {
    private val paper = 0xFF6750A4.toInt()
    private val ink = 0xFF1C1B1F.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private val sketch = Sketch(
        paper,
        listOf(
            SketchStroke(ink, StrokeOutline.pathData(listOf(StrokePoint(200f, 300f, 20f), StrokePoint(800f, 700f, 20f)))),
            SketchStroke(white, StrokeOutline.pathData(listOf(StrokePoint(500f, 500f, 40f)))),
        ),
    )

    private fun read(svg: String): Sketch? = SketchSvg.read(svg.toByteArray())

    private fun same(expected: Sketch, actual: Sketch?) {
        assertNotNull(actual)
        assertEquals(expected.paper, actual!!.paper)
        assertEquals(expected.strokes.map { it.color to it.outline }, actual.strokes.map { it.color to it.outline })
    }

    @Test
    fun `a sketch reads back as it was written`() {
        same(sketch, read(SketchSvg.write(sketch)))
    }

    @Test
    fun `an empty sketch is its paper`() {
        same(Sketch(paper), read(SketchSvg.write(Sketch(paper))))
    }

    @Test
    fun `it's a plain drawing, its paper, clipped strokes, and nothing else`() {
        val svg = SketchSvg.write(sketch)
        assertTrue(svg, svg.startsWith("""<svg xmlns="http://www.w3.org/2000/svg" class="orbit-sketch" viewBox="0 0 1000 1000" width="1000" height="1000">"""))
        assertTrue(svg, svg.contains("""<circle class="orbit-sketch__paper" cx="500" cy="500" r="500" fill="#6750a4"/>"""))
        assertTrue(svg, svg.contains("""<g clip-path="url(#paper)">"""))
        assertTrue(svg, svg.contains("""<path class="orbit-sketch__stroke" fill="#1c1b1f" d="M"""))
        assertTrue(svg, svg.contains("""fill="#ffffff""""))
    }

    @Test
    fun `it goes out clean as it is, and reopens on the other side`() {
        val svg = SketchSvg.write(sketch).toByteArray()
        val cleaned = SharedPictures.withoutMetadata(svg)
        assertNotNull(cleaned)
        // Everything a sketch is survives the cleaner: what another device reads is the same sketch.
        same(sketch, SketchSvg.read(cleaned!!))
    }

    @Test
    fun `drawings that aren't Orbit's own sketches show as pictures, never reopen`() {
        val written = SketchSvg.write(sketch)
        // Another app's drawing.
        assertNull(read("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><circle r="5"/></svg>"""))
        // Another size of paper.
        assertNull(read(written.replace("viewBox=\"0 0 1000 1000\"", "viewBox=\"0 0 500 500\"")))
        // Something more in it, as another app's edit would leave.
        assertNull(read(written.replace("</g>", """<rect width="10" height="10"/></g>""")))
        assertNull(read(written.replace("<g clip-path", """<text>Hi</text><g clip-path""")))
        // A stroke Orbit can't read.
        assertNull(read(written.replace(Regex("""d="M[^"]*""""), """d="M0 0A5 5 0 1 0 10 10Z"""")))
        assertNull(read(written.replace("fill=\"#1c1b1f\"", "fill=\"red\"")))
        // A document type, whose entities could put anything anywhere.
        assertNull(read("<!DOCTYPE svg [<!ENTITY a \"b\">]>$written"))
        // Not XML at all.
        assertNull(read("""<svg class="orbit-sketch" """))
        // A later version's sketch, drawn some other way, named otherwise.
        assertNull(read(written.replace("class=\"orbit-sketch\"", "class=\"orbit-sketch-2\"")))
    }

    @Test
    fun `colors are read as written, short or long`() {
        assertEquals(0xFF112233.toInt(), SketchSvg.color("#112233"))
        assertEquals(0xFF112233.toInt(), SketchSvg.color("#123"))
        assertEquals(0xFFAABBCC.toInt(), SketchSvg.color("#abc"))
        assertNull(SketchSvg.color("red"))
        assertNull(SketchSvg.color("#12345"))
        assertNull(SketchSvg.color(null))
        assertEquals("#6750a4", SketchSvg.hex(paper))
    }
}
