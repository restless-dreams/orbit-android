package com.hyuchia.orbit.media

import com.hyuchia.orbit.data.OutgoingCopies
import com.hyuchia.orbit.data.OutgoingFile
import com.hyuchia.orbit.data.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

class PassiveDrawingsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun drawing(name: String): ByteArray = File("src/test/resources/outgoing/drawings/$name").readBytes()

    private fun passive(svg: ByteArray): String = String(SharedPictures.withoutMetadata(svg)!!, Charsets.UTF_8)

    private fun passive(svg: String): String = passive(svg.toByteArray())

    /** What a drawing's elements are, in order, as an XML reader reads them back: the passive drawing is whole XML. */
    private fun elements(svg: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(svg.byteInputStream())
        val all = document.getElementsByTagNameNS("*", "*")
        return (0 until all.length).map { all.item(it).localName }
    }

    /** What would run or fetch something, as the probe below puts it in every way the review found. */
    private val active = listOf(
        "tracker.example", "<script", "onload", "onclick", "onmouseover", "onerror", "javascript:", "@import", "xml:base", "xml-stylesheet",
        "iframe", "handler", "behavior", "Test person", "attributeName=\"href\"",
    )

    @Test
    fun `a drawing keeps nothing that runs or fetches, and everything that draws`() {
        val probe = drawing("active.svg")
        assertTrue(active.all { String(probe, Charsets.UTF_8).contains(it) })
        val shared = passive(probe)

        for (word in active) {
            assertFalse("$word in $shared", shared.contains(word))
        }

        // Its shapes, paint, filter, text, the use of its own dot and how that dot moves, and the label its foreignObject lays out.
        assertEquals(
            listOf(
                "svg", "title", "style", "defs", "linearGradient", "stop", "stop", "filter", "feGaussianBlur", "filter", "feImage", "rect", "circle",
                "circle", "a", "text", "a", "text", "use", "use", "circle", "animate", "a", "text", "image", "foreignObject", "div", "b", "img", "a",
            ),
            elements(shared),
        )
        assertTrue(shared, shared.contains("<use href=\"#dot\" x=\"200\" y=\"120\"/>"))
        assertTrue(shared, shared.contains("<animate attributeName=\"r\" values=\"6;9;6\" dur=\"2s\" repeatCount=\"indefinite\"/>"))
        assertTrue(shared, shared.contains("filter=\"url(#soft)\""))
        assertTrue(shared, shared.contains(">Label <b>bold</b>"))
        assertTrue(shared, shared.contains(">Tap here</text>"))
        // The style keeps every rule's drawing, and none of what fetched.
        assertTrue(shared, shared.contains(".shape {fill: url(#sky);stroke: #4F5B92;stroke-width: 4;}"))
        assertTrue(shared, shared.contains(".tracked {fill: #E8A33D;}"))
        assertTrue(shared, shared.contains(".old {fill: #2E7D5B;}"))
        assertTrue(shared, shared.contains("style=\"opacity: 0.9;\""))
    }

    @Test
    fun `Mermaid's diagram keeps its labels, shapes and styles, without its link`() {
        // As Mermaid 11 draws a flowchart in a browser, its labels laid out in foreignObjects, and a click that opens a page.
        val diagram = String(drawing("mermaid.svg"), Charsets.UTF_8)
        val shared = passive(diagram)

        assertFalse(shared.contains("https://example.com"))
        // Every element is there, in its place.
        assertEquals(elements(diagram), elements(shared))
        assertEquals(7, Regex("<foreignObject").findAll(shared).count())
        assertTrue(shared.contains("<p>An <strong>idea</strong></p>"))
        // Its style sheet keeps all its rules.
        val rules = { svg: String -> Regex("""\{""").findAll(svg.substringAfter("<style>").substringBefore("</style>")).count() }
        assertEquals(rules(diagram), rules(shared))
        assertTrue(shared.contains("#orbit-diagram {font-family:\"trebuchet ms\",verdana,arial,sans-serif;font-size:16px;fill:#333;}"))
    }

    @Test
    fun `Inkscape's drawing goes without its metadata and editor's own, and its photo without where it was taken`() {
        val inkscape = drawing("inkscape.svg")
        val shared = passive(inkscape)
        // Its words, without the photo's base64, whose letters could spell anything.
        val words = shared.replace(Regex("""data:[^"]*"""), "data:")

        for (word in listOf("Lisbon", "Test person", "/home/test", "inkscape", "sodipodi", "namedview", "metadata", "rdf", "2026-10-21")) {
            assertFalse("$word in $words", words.contains(word))
        }

        assertEquals(listOf("svg", "defs", "linearGradient", "stop", "stop", "linearGradient", "filter", "feGaussianBlur", "g", "rect", "image", "text", "tspan"), elements(shared))
        assertTrue(shared.contains("xlink:href=\"#linearGradient2\""))
        assertTrue(shared.contains("style=\"fill:url(#linearGradient3);stroke:#1d1b20;stroke-width:0.5;filter:url(#filter1);\""))
        assertTrue(shared.contains(">Our trip</tspan>"))

        // The photo inside is the same picture, without its EXIF.
        val photo = { svg: String -> Base64.getDecoder().decode(Regex("""data:image/jpeg;base64,([A-Za-z0-9+/=]+)""").find(svg)!!.groupValues[1]) }
        val original = photo(String(inkscape, Charsets.UTF_8))
        val cleaned = photo(shared)
        assertTrue(String(original, Charsets.ISO_8859_1).contains("Lisbon"))
        assertFalse(String(cleaned, Charsets.ISO_8859_1).contains("Exif"))
        assertEquals(pixels(original), pixels(cleaned))
    }

    private fun pixels(bytes: ByteArray): List<Int> {
        val image = ImageIO.read(bytes.inputStream())
        return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) } }
    }

    @Test
    fun `references outside the drawing go, and its own stay`() {
        val shared = passive(
            """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
              <use href="other.svg#a"/><use xlink:href=" #a "/><use href="data:image/svg+xml,%3Csvg/%3E"/>
              <image href="file:///sdcard/DCIM/photo.jpg"/><image xlink:href="//tracker.example/p.png"/>
              <a href="#top"><circle r="1"/></a><a href="mailto:test@example.com"><circle r="2"/></a>
              <textPath href="#curve">Along</textPath><feImage href="#a"/>
              <path d="M0 0" fill="url(https://tracker.example/x.svg#g)" stroke="url(#own)" cursor="url(cursor.png), auto" marker-end="url('#arrow')"/>
            </svg>""",
        )

        assertEquals(listOf("#a", "#top", "#curve", "#a"), Regex("""href="([^"]*)"""").findAll(shared).map { it.groupValues[1] }.toList())
        assertFalse(shared.contains("tracker.example"))
        assertFalse(shared.contains("cursor"))
        assertFalse(shared.contains("mailto"))
        assertTrue(shared.contains("stroke=\"url(#own)\""))
        assertTrue(shared.contains("marker-end=\"url('#arrow')\""))
    }

    @Test
    fun `animations go on changing how it looks, never what it runs or where it points`() {
        val shared = passive(
            """<svg xmlns="http://www.w3.org/2000/svg">
              <rect width="4" height="4"><animate attributeName="fill" values="red;blue" dur="1s"/><set attributeName="onmouseover" to="alert(1)"/>
              <animate attributeName="style" to="background:url(https://tracker.example/)"/><animateTransform attributeName="transform" type="rotate" from="0" to="90" dur="1s"/>
              <set attributeName="fill" to="url(https://tracker.example/x#y)"/></rect>
              <use href="#a"><set attributeName="href" to="#b" begin="1s"/><set attributeName="xlink:href" to="https://tracker.example/"/></use>
            </svg>""",
        )

        assertEquals(listOf("svg", "rect", "animate", "animateTransform", "use", "set"), elements(shared))
        assertFalse(shared.contains("tracker.example"))
        assertTrue(shared.contains("<set attributeName=\"href\" to=\"#b\" begin=\"1s\"/>"))
    }

    @Test
    fun `a picture inside goes clean, and one that can't makes the drawing unclean`() {
        val png = picture()
        val located = withPlace(png)
        val inside = { bytes: ByteArray -> """<svg xmlns="http://www.w3.org/2000/svg"><image href="data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"/></svg>""" }
        val shared = passive(inside(located))
        val cleaned = Base64.getDecoder().decode(Regex("""base64,([A-Za-z0-9+/=]+)""").find(shared)!!.groupValues[1])

        assertFalse(String(cleaned, Charsets.ISO_8859_1).contains("Lisbon"))
        assertEquals(pixels(png), pixels(cleaned))
        // In a style, a background in a foreignObject's text, too.
        val styled = passive(
            """<svg xmlns="http://www.w3.org/2000/svg"><foreignObject><div xmlns="http://www.w3.org/1999/xhtml" style="background: url('data:image/png;base64,${Base64.getEncoder().encodeToString(located)}')">A</div></foreignObject></svg>""",
        )
        assertFalse(String(Base64.getDecoder().decode(Regex("""base64,([A-Za-z0-9+/=]+)""").find(styled)!!.groupValues[1]), Charsets.ISO_8859_1).contains("Lisbon"))

        // A picture cut short can't go clean, and nor can the drawing it's in.
        assertNull(SharedPictures.withoutMetadata(inside(located.copyOfRange(0, located.size / 2)).toByteArray()))
    }

    /** A PNG with a text chunk saying where it was taken, before its picture data. */
    private fun withPlace(png: ByteArray): ByteArray {
        val data = "Location\u0000Taken in Lisbon".toByteArray(Charsets.ISO_8859_1)
        val typed = "tEXt".toByteArray(Charsets.ISO_8859_1) + data
        val chunk = ByteBuffer.allocate(4).putInt(data.size).array() + typed + ByteBuffer.allocate(4).putInt(CRC32().apply { update(typed) }.value.toInt()).array()
        return png.copyOfRange(0, 8 + 25) + chunk + png.copyOfRange(8 + 25, png.size)
    }

    private fun picture(): ByteArray {
        val image = java.awt.image.BufferedImage(12, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)

        for (x in 0 until 12) {
            for (y in 0 until 8) {
                image.setRGB(x, y, (x * 20 shl 16) or (y * 30 shl 8) or 0x40)
            }
        }

        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `a drawing inside a drawing is made passive too, but only a few deep`() {
        val script = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><circle r="3" onload="alert(2)"/></svg>"""
        val nest = { svg: String -> """<svg xmlns="http://www.w3.org/2000/svg"><image href="data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(svg.toByteArray())}"/></svg>""" }
        val shared = passive(nest(script))
        val inner = String(Base64.getDecoder().decode(Regex("""base64,([A-Za-z0-9+/=]+)""").find(shared)!!.groupValues[1]), Charsets.UTF_8)

        assertTrue(inner.contains("<circle r=\"3\"/>"))
        assertFalse(inner.contains("alert"))
        // Written as a data address of text, too.
        assertFalse(passive("""<svg xmlns="http://www.w3.org/2000/svg"><image href="data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg'%3E%3Cscript%3Ealert(1)%3C/script%3E%3C/svg%3E"/></svg>""").contains("alert"))
        // Five deep, what's inside can't be told.
        assertNull(SharedPictures.withoutMetadata(nest(nest(nest(nest(script)))).toByteArray()))
    }

    @Test
    fun `every way out gets the passive drawing`() {
        val probe = drawing("active.svg")
        val file = temporary.newFile("drawing.svg").apply { writeBytes(probe) }
        val sent = { outgoing: OutgoingFile -> String(ByteArrayOutputStream().also { outgoing.writeTo(it) }.toByteArray(), Charsets.UTF_8) }
        val inDrawing = OutgoingMedia.drawing("""<svg xmlns="http://www.w3.org/2000/svg"><image href="data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(probe)}"/></svg>""")
        val drawn = String(Base64.getDecoder().decode(Regex("""base64,([A-Za-z0-9+/=]+)""").find(inDrawing)!!.groupValues[1]), Charsets.UTF_8)

        val ways = mapOf(
            "shared from the viewer" to passive(probe),
            "in a zip or sent" to sent(OutgoingMedia.of(file)!!),
            "in a page" to String(OutgoingMedia.picture(probe)!!, Charsets.UTF_8),
            "inside a drawing" to drawn,
            "to the room's browsers" to OutgoingCopies(File(temporary.root, "copies")).of(file)!!.readText(),
        )

        for ((way, svg) in ways) {
            assertTrue(way, active.none { svg.contains(it) })
            assertEquals(way, passive(probe), svg)
        }
    }

    @Test
    fun `a drawing that can't be read through, or isn't one, doesn't go clean`() {
        val ways = listOf(
            // A document type's entities could put anything anywhere, in any case.
            """<!doctype svg [<!ENTITY a "x">]><svg xmlns="http://www.w3.org/2000/svg">&a;</svg>""",
            """<?xml version="1.0"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"><svg xmlns="http://www.w3.org/2000/svg"/>""",
            // Not whole.
            """<svg xmlns="http://www.w3.org/2000/svg"><circle r="1">""",
            // An svg element of another kind.
            """<svg xmlns="http://tracker.example/svg"><circle r="1"/></svg>""",
        )

        for (svg in ways) {
            assertNull(svg, SharedPictures.withoutMetadata(svg.toByteArray()))
        }

        // One written without SVG's namespace, as older drawings were, keeps its own.
        assertNotNull(SharedPictures.withoutMetadata("""<svg width="2"><circle r="1" onclick="x()"/></svg>""".toByteArray()))
        assertEquals(listOf("svg", "circle"), elements(passive("""<svg width="2"><circle r="1" onclick="x()"/><script>x()</script></svg>""")))
    }

    @Test
    fun `what a style says stays, as far as it draws`() {
        val shared = passive(
            """<svg xmlns="http://www.w3.org/2000/svg"><style><![CDATA[
              /* Made by Test person */
              @charset "utf-8";
              @media (prefers-color-scheme: dark) { .a { fill: #fff } }
              @keyframes spin { from { transform: rotate(0deg) } to { transform: rotate(360deg) } }
              @font-face { font-family: Own; src: url(data:font/woff2;base64,d09GMgABAAAA) format("woff2") }
              .b > .c::after { content: "a; b"; fill: red !important }
              .d { fill: expression(alert(1)); stroke: blue }
              .e { mask-image: image-set("https://tracker.example/1x.png" 1x); fill: green }
              .f { -moz-binding: url(https://tracker.example/x.xml#x); fill: yellow }
              @document url(https://tracker.example/) { .g { fill: pink } }
            ]]></style></svg>""",
        )

        assertFalse(shared, shared.contains("Test person"))
        assertFalse(shared, shared.contains("tracker.example"))
        assertFalse(shared, shared.contains("expression"))
        assertFalse(shared, shared.contains("charset"))
        assertTrue(shared, shared.contains("@media (prefers-color-scheme: dark) {.a {fill: #fff;}"))
        assertTrue(shared, shared.contains("@keyframes spin {from {transform: rotate(0deg);}"))
        assertTrue(shared, shared.contains("src: url(data:font/woff2;base64,d09GMgABAAAA) format(\"woff2\");"))
        assertTrue(shared, shared.contains(".b &gt; .c::after {content: \"a; b\";fill: red !important;}"))
        assertTrue(shared, shared.contains(".d {stroke: blue;}"))
        assertTrue(shared, shared.contains(".e {fill: green;}"))
        assertTrue(shared, shared.contains(".f {fill: yellow;}"))
        assertFalse(shared, shared.contains("pink"))
    }
}
