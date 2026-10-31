package com.hyuchia.orbit.media

import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** The application extensions a GIF keeps: its looping, as two apps write it, and its color profile. */
private val KEPT_APPLICATIONS = setOf("NETSCAPE2.0", "ANIMEXTS1.0", "ICCRGBG1012")

/**
 * A GIF's bytes with only what draws it: its colors, its frames, how each
 * shows and for how long, its looping and its color profile. Comments and
 * other apps' blocks, such as XMP, where an editor can write who made it and
 * where, go, and so does anything after its end. Null when it isn't whole.
 */
internal fun gifWithoutMetadata(bytes: ByteArray): ByteArray? {
    // Its signature and version, then its screen: size, colors and background.
    if (bytes.size < 13) {
        return null
    }

    var position = 13
    val screen = bytes.byteAt(10)

    if (screen and 0x80 != 0) {
        position += 3 * (1 shl ((screen and 0x07) + 1))
    }

    if (position > bytes.size) {
        return null
    }

    val output = ByteArrayOutputStream(bytes.size)
    output.write(bytes, 0, position)

    while (position < bytes.size) {
        when (bytes.byteAt(position)) {
            // The trailer: what follows it isn't the picture's, and stays out.
            0x3B -> {
                output.write(0x3B)
                return output.toByteArray()
            }
            // A frame: where it goes, its own colors, and its data.
            0x2C -> {
                if (position + 11 > bytes.size) {
                    return null
                }

                val frame = bytes.byteAt(position + 9)
                var data = position + 10

                if (frame and 0x80 != 0) {
                    data += 3 * (1 shl ((frame and 0x07) + 1))
                }

                // Past the colors, the code size its data starts with.
                val end = subBlocksEnd(bytes, data + 1) ?: return null
                output.write(bytes, position, end - position)
                position = end
            }
            0x21 -> {
                if (position + 2 > bytes.size) {
                    return null
                }

                val end = subBlocksEnd(bytes, position + 2) ?: return null

                if (keepsExtension(bytes, position)) {
                    output.write(bytes, position, end - position)
                }

                position = end
            }
            else -> return null
        }
    }

    return null
}

/** Where the sub-blocks from `from` end, past the empty one that ends them; null when they run past the file. */
private fun subBlocksEnd(bytes: ByteArray, from: Int): Int? {
    var position = from

    while (position < bytes.size) {
        val size = bytes.byteAt(position)

        if (size == 0) {
            return position + 1
        }

        position += 1 + size
    }

    return null
}

/**
 * Whether the extension at `at` draws the GIF: how a frame shows (its
 * graphic control), text drawn on it, and the applications' blocks that
 * loop it or give its colors' profile. Comments and anything else go.
 */
private fun keepsExtension(bytes: ByteArray, at: Int): Boolean = when (bytes.byteAt(at + 1)) {
    0xF9, 0x01 -> true
    0xFF -> at + 14 <= bytes.size && bytes.byteAt(at + 2) == 11 && String(bytes, at + 3, 11, Charsets.ISO_8859_1) in KEPT_APPLICATIONS
    else -> false
}

private fun ByteArray.byteAt(index: Int): Int = this[index].toInt() and 0xFF

/** The namespaces whose elements draw an SVG: its own, and HTML inside a `foreignObject`. */
private val DRAWING_NAMESPACES = setOf("http://www.w3.org/2000/svg", "http://www.w3.org/1999/xhtml")

/** The namespaces of attributes that draw it: none, XLink's links and XML's own (`xml:space`, `xml:lang`). */
private val DRAWING_ATTRIBUTES = setOf(null, "http://www.w3.org/1999/xlink", "http://www.w3.org/XML/1998/namespace")

private const val XMLNS = "http://www.w3.org/2000/xmlns/"

/**
 * An SVG drawing with only what draws it. Its `metadata` (who made it, its
 * license, often an editor's own copy of where it was saved), comments, and
 * every editor's own elements and attributes, as Inkscape's export path and
 * file name, go. Null for one that can't be read through, or that has a
 * document type: its entities could put anything anywhere.
 */
internal fun svgWithoutMetadata(bytes: ByteArray): ByteArray? {
    val text = bytes.decodeToString()

    if (text.contains("<!DOCTYPE", ignoreCase = true)) {
        return null
    }

    return try {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(text.trimStart('\uFEFF'))))
        val root = document.documentElement ?: return null

        // Comments and instructions beside the drawing go too. A drawing written without SVG's namespace keeps its own.
        document.childNodes.toList().filter { it.nodeType == Node.COMMENT_NODE || it.nodeType == Node.PROCESSING_INSTRUCTION_NODE }.forEach { document.removeChild(it) }
        keepDrawing(root, DRAWING_NAMESPACES + root.namespaceURI)

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        val written = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(written))
        written.toString().toByteArray(Charsets.UTF_8)
    } catch (unreadable: Exception) {
        null
    }
}

/** Takes out of `element` what doesn't draw it: the elements, attributes, comments and instructions of anything but the drawing. */
private fun keepDrawing(element: Element, drawingNamespaces: Set<String?>) {
    val attributes = element.attributes

    for (index in attributes.length - 1 downTo 0) {
        val attribute = attributes.item(index)
        val namespace = attribute.namespaceURI
        // A namespace's declaration stays for the drawing's own namespaces alone.
        val drawing = if (namespace == XMLNS) attribute.nodeValue in drawingNamespaces || attribute.nodeValue in DRAWING_ATTRIBUTES else namespace in DRAWING_ATTRIBUTES

        if (!drawing) {
            element.removeAttributeNode(attribute as Attr)
        }
    }

    for (child in element.childNodes.toList()) {
        when {
            child.nodeType == Node.COMMENT_NODE || child.nodeType == Node.PROCESSING_INSTRUCTION_NODE -> element.removeChild(child)
            child is Element && (child.namespaceURI !in drawingNamespaces || child.localName == "metadata") -> element.removeChild(child)
            child is Element -> keepDrawing(child, drawingNamespaces)
        }
    }
}

private fun NodeList.toList(): List<Node> = (0 until length).map { item(it) }
