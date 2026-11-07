package com.hyuchia.orbit.media

import com.hyuchia.orbit.data.OutgoingMedia
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.ext.DefaultHandler2
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.Base64
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * An SVG drawing with only what draws it, as a picture's own sandbox draws
 * it (an `<img>`'s, which runs nothing and loads nothing): its shapes, text,
 * styles, gradients, patterns, markers, masks, filters and animations, and
 * the text a `foreignObject` lays out, as diagrams' labels are. Everything
 * else goes: its metadata, comments and instructions, every editor's own
 * elements and attributes, scripts, event handlers and links, and every
 * reference outside it, in an attribute or a style (an address on the web,
 * another file, `@import`), which a drawing opened on its own could fetch
 * or run. Pictures inside it go as [OutgoingMedia.picture] makes them.
 * Null for one that can't be read through: one with a document type, whose
 * entities could put anything anywhere, or with a picture inside that can't
 * go clean.
 */
internal fun svgWithoutMetadata(bytes: ByteArray): ByteArray? = PassiveDrawings.of(bytes)

/** How SVG drawings are made passive as they leave: see [svgWithoutMetadata]. */
internal object PassiveDrawings {
    const val SVG = "http://www.w3.org/2000/svg"
    const val XHTML = "http://www.w3.org/1999/xhtml"
    const val XLINK = "http://www.w3.org/1999/xlink"
    const val XML = "http://www.w3.org/XML/1998/namespace"

    /** How many drawings deep a drawing inside a drawing is read: what one deeper holds can't be told. */
    private const val DEEPEST = 3

    /** How deep a style's blocks are read: what's deeper goes. */
    private const val DEEPEST_STYLE = 32

    /** The drawing at `depth` drawings inside another, or null when it can't be read through. */
    fun of(bytes: ByteArray, depth: Int = 0): ByteArray? {
        if (depth > DEEPEST || hasDocumentType(bytes)) {
            return null
        }

        val writer = Writer(depth)

        return try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }

            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = writer
            reader.errorHandler = writer
            reader.entityResolver = writer

            try {
                // What says where a document type starts, which stops the reading; without it, hasDocumentType has already.
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", writer)
            } catch (unsupported: SAXException) {
                Unit
            }

            reader.parse(InputSource(ByteArrayInputStream(bytes)))
            writer.written()
        } catch (unreadable: SAXException) {
            null
        } catch (unreadable: IOException) {
            null
        } catch (unreadable: ParserConfigurationException) {
            null
        } catch (malformed: RuntimeException) {
            // A file made to trip the reader is one it can't read through, as a broken one is.
            null
        }
    }

    /** Whether `<!DOCTYPE` is anywhere in the drawing, in any case. */
    private fun hasDocumentType(bytes: ByteArray): Boolean {
        val mark = "<!DOCTYPE"

        for (at in 0..bytes.size - mark.length) {
            if (bytes[at] == '<'.code.toByte() && mark.indices.all { (bytes[at + it].toInt() and 0xFF).toChar().uppercaseChar() == mark[it] }) {
                return true
            }
        }

        return false
    }

    /** What an element's children are read as: SVG, or the XHTML a `foreignObject` lays out. */
    private enum class Mode { Svg, Html }

    /** An element written: its name, what its children are read as, whether its start tag is still open, and its style sheet as it's read, for a `style`. */
    private class Kept(val name: String, val children: Mode, var open: Boolean = true, val style: StringBuilder? = null)

    /**
     * Writes the drawing's passive parts, in UTF-8, as the reader goes
     * through it. An element that isn't one goes with everything inside it.
     */
    private class Writer(private val depth: Int) : DefaultHandler2() {
        private val bytes = ByteArrayOutputStream()
        private val output = BufferedWriter(OutputStreamWriter(bytes, Charsets.UTF_8)).apply { append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n") }
        private val kept = ArrayDeque<Kept>()

        /** The namespaces the next element declares, as prefixes and their names. */
        private val declared = mutableListOf<Pair<String, String>>()

        /** How deep inside an element that goes the reader is: 0 outside any. */
        private var leaving = 0

        /** The namespace the drawing's elements are in: SVG's, or none for a drawing written without it. */
        private var drawing: String? = null

        /** Whether a picture inside it can't go clean, so neither can the drawing. */
        private var unclean = false

        fun written(): ByteArray? {
            output.flush()
            return bytes.toByteArray().takeIf { drawing != null && !unclean }
        }

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("A drawing with a document type")
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("A drawing that reads another file")

        override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?): InputSource = throw SAXException("A drawing that reads another file")

        override fun fatalError(error: SAXParseException) {
            throw error
        }

        override fun error(error: SAXParseException) {
            throw error
        }

        override fun startPrefixMapping(prefix: String, uri: String) {
            declared += prefix to uri
        }

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            val namespaces = declared.toList()
            declared.clear()

            if (leaving > 0) {
                leaving += 1
                return
            }

            val parent = kept.lastOrNull()

            // The drawing itself: an svg element, in SVG's namespace or, as older drawings were written, in none.
            if (parent == null) {
                if (drawing != null || localName != "svg" || (uri != SVG && uri.isNotEmpty())) {
                    throw SAXException("Not a drawing")
                }

                drawing = uri
            }

            val html = uri == XHTML
            val mode = childrenOf(uri, localName, parent?.children ?: Mode.Svg)

            if (mode == null || !passiveElement(localName, html, attributes)) {
                leaving = 1
                return
            }

            parent?.let(::closeStart)
            output.append('<').append(qName)

            for ((prefix, name) in namespaces) {
                if (name in DECLARED || name.isEmpty()) {
                    output.append(if (prefix.isEmpty()) " xmlns" else " xmlns:$prefix").append("=\"").attribute(name).append('"')
                }
            }

            for (index in 0 until attributes.length) {
                val value = passiveAttribute(localName, html, attributes.getURI(index), attributes.getLocalName(index), attributes.getValue(index)) ?: continue
                output.append(' ').append(attributes.getQName(index)).append("=\"").attribute(value).append('"')
            }

            kept.addLast(Kept(qName, mode, style = StringBuilder().takeIf { localName == "style" }))
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (leaving > 0) {
                leaving -= 1
                return
            }

            val element = kept.removeLast()
            val style = element.style?.let { passiveCss(it.toString(), 0) }

            if (!style.isNullOrBlank()) {
                closeStart(element)
                output.text(style)
            }

            if (element.open) {
                output.append("/>")
            } else {
                output.append("</").append(element.name).append('>')
            }
        }

        override fun characters(text: CharArray, start: Int, length: Int) {
            if (leaving > 0) {
                return
            }

            val element = kept.lastOrNull() ?: return

            if (element.style != null) {
                element.style.appendRange(text, start, start + length)
            } else {
                closeStart(element)
                output.text(String(text, start, length))
            }
        }

        override fun ignorableWhitespace(text: CharArray, start: Int, length: Int) = characters(text, start, length)

        private fun closeStart(element: Kept) {
            if (element.open) {
                output.append('>')
                element.open = false
            }
        }

        /** What the children of an element of `namespace` read as `mode` are read as, or null when it goes. */
        private fun childrenOf(namespace: String, name: String, mode: Mode): Mode? = when {
            namespace == SVG || (namespace.isEmpty() && drawing?.isEmpty() == true) -> when {
                name !in SVG_ELEMENTS -> null
                // Inside a foreignObject's text, only a drawing of its own.
                mode == Mode.Html && name != "svg" -> null
                name == "foreignObject" -> Mode.Html
                else -> Mode.Svg
            }
            namespace == XHTML && mode == Mode.Html && name in HTML_ELEMENTS -> Mode.Html
            else -> null
        }

        /**
         * Whether an element that may be passive is: a style of another kind
         * than CSS isn't, and nor is an animation of something that isn't
         * how the drawing looks, as its links or its handlers, or that would
         * point outside it.
         */
        private fun passiveElement(name: String, html: Boolean, attributes: Attributes): Boolean {
            if (name == "style") {
                return attributes.getValue("", "type")?.trim()?.lowercase().let { it == null || it.isEmpty() || it == "text/css" }
            }

            if (html || name !in ANIMATIONS) {
                return true
            }

            val target = attributes.getValue("", "attributeName")?.trim()?.substringAfter(':') ?: return true
            val values = listOf("values", "from", "to", "by").mapNotNull { attributes.getValue("", it) }.flatMap { it.split(';') }.map { it.trim() }.filter { it.isNotEmpty() }

            return when (target) {
                // Which of the drawing's own parts a use shows, say: only ever another of its own.
                "href" -> values.all { it.startsWith("#") }
                "style" -> false
                else -> target in SVG_ATTRIBUTES && values.all { !it.contains("data:", ignoreCase = true) && passiveCssValue(it) != null }
            }
        }

        /** What an attribute keeps of `value`, or null when it goes. */
        private fun passiveAttribute(element: String, html: Boolean, namespace: String, name: String, value: String): String? = when {
            namespace == XML -> value.takeIf { name == "space" || name == "lang" }
            namespace == XLINK -> when (name) {
                "href" -> reference(element, value)
                "title" -> value
                else -> null
            }
            namespace.isNotEmpty() -> null
            name == "style" -> passiveCss(value, 0).takeIf { it.isNotBlank() }
            name.startsWith("aria-") -> value
            html && name == "src" && element == "img" -> picture(value)
            html -> if (name in HTML_ATTRIBUTES) passiveCssValue(value) else null
            name == "href" -> reference(element, value)
            name in SVG_ATTRIBUTES -> passiveCssValue(value)
            else -> null
        }

        /**
         * Where an element points, when it's the drawing's own (`#…`), or
         * the picture inside it of an `image` or `feImage`, made clean.
         * Anything outside it goes.
         */
        private fun reference(element: String, value: String): String? {
            val address = value.trim { it <= ' ' }.replace(Regex("[\\t\\n\\r]"), "")

            return when {
                address.startsWith("#") -> address
                element == "image" || element == "feImage" -> picture(address)
                else -> null
            }
        }

        /**
         * A picture inside the drawing (`data:image/…`) as it leaves: as
         * [OutgoingMedia.picture] makes it, or as this makes a drawing.
         * Null for anything else; one that can't go clean leaves the drawing
         * unclean.
         */
        private fun picture(address: String): String? {
            val (type, bytes) = dataOf(address) ?: return null

            if (!type.startsWith("image/")) {
                return null
            }

            val cleaned = bytes?.let { if (SharedPictures.kindOf(it) == SharedPictures.Kind.Svg) of(it, depth + 1) else OutgoingMedia.picture(it) }

            if (cleaned == null) {
                unclean = true
                return null
            }

            return "data:$type;base64,${Base64.getEncoder().encodeToString(cleaned)}"
        }

        /**
         * A style sheet, or a style attribute's declarations, with only what
         * draws: rules and declarations whose values point nowhere outside
         * the drawing ([passiveCssValue]), inside blocks of the kinds that
         * only say where and when they apply. `@import` and the rest go.
         */
        private fun passiveCss(css: String, level: Int): String {
            val text = withoutComments(css)
            val output = StringBuilder()
            var position = 0

            while (position < text.length) {
                val end = statementEnd(text, position)
                val head = text.substring(position, end).trim()

                if (end < text.length && text[end] == '{') {
                    val close = blockEnd(text, end)

                    if (level < DEEPEST_STYLE && passivePrelude(head)) {
                        output.append(head).append(" {").append(passiveCss(text.substring(end + 1, close), level + 1)).append("}\n")
                    }

                    position = close + 1
                } else {
                    passiveDeclaration(head)?.let { output.append(it).append(';') }
                    position = end + 1
                }
            }

            return output.toString()
        }

        /** A declaration as it stays, or null when it goes: one of a property that runs something, or whose value isn't passive. */
        private fun passiveDeclaration(declaration: String): String? {
            val colon = declaration.indexOf(':')

            if (colon <= 0) {
                return null
            }

            val property = declaration.substring(0, colon).trim()

            if (property.lowercase() in ACTIVE_PROPERTIES || !property.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return null
            }

            return passiveCssValue(declaration.substring(colon + 1))?.let { "$property:$it" }
        }

        /** Whether a block is of a kind that only says where and when what's inside it applies, as a selector or `@media` does. */
        private fun passivePrelude(prelude: String): Boolean {
            if (!prelude.startsWith("@")) {
                return prelude.isNotEmpty()
            }

            val name = prelude.drop(1).takeWhile { it.isLetterOrDigit() || it == '-' }.lowercase()
            return name in BLOCK_RULES && '\\' !in prelude && !prelude.contains("url(", ignoreCase = true)
        }

        /**
         * A value of a style or of a presentation attribute as it stays, or
         * null when it goes: one with an escape, which could spell anything,
         * or that runs or fetches something, or with a `url()` outside the
         * drawing. A picture inside it is made clean, a font stays.
         */
        private fun passiveCssValue(value: String): String? {
            if ('\\' in value || ACTIVE_CSS.containsMatchIn(value)) {
                return null
            }

            val output = StringBuilder()
            var position = 0

            while (true) {
                val url = URL.find(value, position) ?: break
                output.append(value, position, url.range.first)
                val (address, end) = urlAt(value, url.range.last + 1) ?: return null
                val kept = cssReference(address) ?: return null

                // As it was written, unless it's a picture made clean.
                if (kept == address) {
                    output.append(value, url.range.first, end)
                } else {
                    output.append("url(\"").append(kept).append("\")")
                }

                position = end
            }

            return output.append(value, position, value.length).toString()
        }

        /** What a style's `url()` keeps: one of the drawing's own parts, a picture inside it made clean, or a font inside it. */
        private fun cssReference(address: String): String? {
            if (address.startsWith("#")) {
                return address
            }

            val type = dataOf(address)?.first ?: return null

            return when {
                type.startsWith("image/") -> picture(address)
                type.startsWith("font/") || type in FONT_TYPES -> address
                else -> null
            }
        }

        /** A data address's media type, and its bytes, null when they can't be read; null when it isn't one. */
        private fun dataOf(address: String): Pair<String, ByteArray?>? {
            if (!address.startsWith("data:", ignoreCase = true)) {
                return null
            }

            val comma = address.indexOf(',')

            if (comma < 0) {
                return null
            }

            val header = address.substring(5, comma).split(';').map { it.trim() }
            val type = header.first().lowercase()
            val data = address.substring(comma + 1)

            val bytes = try {
                if (header.drop(1).any { it.equals("base64", ignoreCase = true) }) Base64.getMimeDecoder().decode(data) else percentDecoded(data)
            } catch (unreadable: IllegalArgumentException) {
                null
            }

            return type to bytes
        }
    }

    /** The bytes `%xx` escapes and plain characters stand for in a data address that isn't base64. */
    private fun percentDecoded(data: String): ByteArray {
        val bytes = ByteArrayOutputStream(data.length)
        var position = 0

        while (position < data.length) {
            val character = data[position]

            if (character == '%' && position + 2 < data.length) {
                bytes.write(data.substring(position + 1, position + 3).toInt(16))
                position += 3
            } else {
                bytes.write(character.toString().toByteArray(Charsets.UTF_8))
                position += 1
            }
        }

        return bytes.toByteArray()
    }

    /** A style without its comments. A comment that doesn't end takes the rest with it. */
    private fun withoutComments(css: String): String {
        val output = StringBuilder(css.length)
        var position = 0

        while (position < css.length) {
            val character = css[position]

            when {
                character == '"' || character == '\'' -> {
                    val end = stringEnd(css, position)
                    output.append(css, position, end)
                    position = end
                }
                css.startsWith("/*", position) -> {
                    val end = css.indexOf("*/", position + 2)
                    position = if (end < 0) css.length else end + 2
                }
                else -> {
                    output.append(character)
                    position += 1
                }
            }
        }

        return output.toString()
    }

    /** Where the string that opens at `at` ends, past its closing quote, or at the end. */
    private fun stringEnd(css: String, at: Int): Int {
        val quote = css[at]
        var position = at + 1

        while (position < css.length && css[position] != quote) {
            position += if (css[position] == '\\') 2 else 1
        }

        return minOf(css.length, position + 1)
    }

    /** Where the statement from `from` ends: at its `;`, `{` or `}`, outside strings and brackets, or at the end. */
    private fun statementEnd(css: String, from: Int): Int {
        var position = from
        var brackets = 0

        while (position < css.length) {
            when (css[position]) {
                '"', '\'' -> {
                    position = stringEnd(css, position)
                    continue
                }
                '(', '[' -> brackets += 1
                ')', ']' -> brackets = maxOf(0, brackets - 1)
                ';', '{', '}' -> if (brackets == 0) {
                    return position
                }
            }

            position += 1
        }

        return css.length
    }

    /** Where the block that opens at `open` closes, or the end. */
    private fun blockEnd(css: String, open: Int): Int {
        var position = open + 1
        var blocks = 1

        while (position < css.length) {
            when (css[position]) {
                '"', '\'' -> {
                    position = stringEnd(css, position)
                    continue
                }
                '{' -> blocks += 1
                '}' -> {
                    blocks -= 1

                    if (blocks == 0) {
                        return position
                    }
                }
            }

            position += 1
        }

        return css.length
    }

    /** The address of a `url(` whose bracket opens before `from`, and where it ends, past its `)`; null when it doesn't end. */
    private fun urlAt(value: String, from: Int): Pair<String, Int>? {
        var position = from

        while (position < value.length && value[position].isWhitespace()) {
            position += 1
        }

        if (position < value.length && (value[position] == '"' || value[position] == '\'')) {
            val quote = value[position]
            val end = value.indexOf(quote, position + 1)

            if (end < 0) {
                return null
            }

            val close = value.indexOf(')', end + 1)

            if (close < 0 || value.substring(end + 1, close).isNotBlank()) {
                return null
            }

            return value.substring(position + 1, end).trim() to close + 1
        }

        val close = value.indexOf(')', position)
        return if (close < 0) null else value.substring(position, close).trim() to close + 1
    }

    private fun Appendable.text(value: String): Appendable {
        for (character in value) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\r' -> append("&#13;")
                else -> append(character)
            }
        }

        return this
    }

    private fun Appendable.attribute(value: String): Appendable {
        for (character in value) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '"' -> append("&quot;")
                '\n' -> append("&#10;")
                '\r' -> append("&#13;")
                '\t' -> append("&#9;")
                else -> append(character)
            }
        }

        return this
    }

    /** The namespaces a passive drawing declares: SVG's, XLink's for its references, and XHTML's for a foreignObject's text. */
    private val DECLARED = setOf(SVG, XLINK, XHTML)

    /** Where a style's `url(` starts. */
    private val URL = Regex("""(?i)(?<![\w-])url\(""")

    /** What runs or fetches something from a style: old browsers' scripts, script addresses, and the image functions that take one by its name. */
    private val ACTIVE_CSS = Regex("""(?i)expression\(|javascript:|vbscript:|@import|(?<![\w-])(?:-webkit-)?(?:image-set|image|cross-fade|src)\(""")

    /** Properties that run something, as old browsers had them. */
    private val ACTIVE_PROPERTIES = setOf("behavior", "-ms-behavior", "-moz-binding")

    /** The blocks a style sheet keeps: those that say where or when what's inside applies, fonts, animations' steps, and layers. */
    private val BLOCK_RULES = setOf(
        "media", "supports", "font-face", "keyframes", "-webkit-keyframes", "layer", "container", "page", "scope", "starting-style", "property", "counter-style",
        "font-feature-values",
    )

    /** The media types fonts are written into a style with, besides `font/…`. */
    private val FONT_TYPES = setOf(
        "application/font-woff", "application/font-woff2", "application/x-font-woff", "application/font-sfnt", "application/font-ttf", "application/x-font-ttf",
        "application/x-font-truetype", "application/x-font-opentype", "application/vnd.ms-fontobject", "application/octet-stream",
    )

    /** SVG's animations, which a passive drawing keeps when they change only how it looks. */
    private val ANIMATIONS = setOf("animate", "set", "animateTransform", "animateMotion")

    /**
     * The SVG elements that draw: the drawing and its groups, shapes, text,
     * paint, clips, masks, markers and filters, pictures inside it, the
     * text a foreignObject lays out, styles, animations, and a title and
     * description for screen readers. Scripts, handlers, metadata, outside
     * fonts and color profiles, media and frames aren't among them.
     */
    private val SVG_ELEMENTS = setOf(
        "svg", "g", "defs", "symbol", "use", "switch", "a", "view", "title", "desc", "style",
        "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "text", "tspan", "textPath", "image", "foreignObject",
        "linearGradient", "radialGradient", "stop", "pattern", "solidcolor", "meshgradient", "meshrow", "meshpatch", "hatch", "hatchpath",
        "clipPath", "mask", "marker",
        "filter", "feBlend", "feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting", "feDisplacementMap",
        "feDistantLight", "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG", "feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode",
        "feMorphology", "feOffset", "fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence",
        "animate", "set", "animateTransform", "animateMotion", "mpath", "discard",
    )

    /**
     * The attributes of SVG elements that draw: where and how big things
     * are, their paths and transforms, presentation (fill, stroke, fonts,
     * filters and the rest of what styles say), what gradients, patterns,
     * markers, clips, masks and filters do, when animations run and what
     * they change, and what a drawing picks by language or feature. `href`
     * is a reference of its own, and `style` a style sheet's declarations.
     */
    private val SVG_ATTRIBUTES = setOf(
        "id", "class", "lang", "tabindex", "role", "focusable", "media", "title", "type",
        "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "fx", "fy", "fr", "width", "height", "d", "points", "pathLength",
        "viewBox", "preserveAspectRatio", "version", "baseProfile", "zoomAndPan", "transform",
        "dx", "dy", "rotate", "textLength", "lengthAdjust", "startOffset", "method", "spacing", "side", "path",
        "offset", "gradientUnits", "gradientTransform", "spreadMethod", "patternUnits", "patternContentUnits", "patternTransform", "hatchUnits",
        "hatchContentUnits", "pitch", "clipPathUnits", "maskUnits", "maskContentUnits", "markerUnits", "markerWidth", "markerHeight", "refX", "refY",
        "orient", "filterUnits", "primitiveUnits", "filterRes", "in", "in2", "result", "mode", "values", "operator", "k1", "k2", "k3", "k4", "order",
        "kernelMatrix", "divisor", "bias", "targetX", "targetY", "edgeMode", "kernelUnitLength", "preserveAlpha", "surfaceScale", "diffuseConstant",
        "specularConstant", "specularExponent", "azimuth", "elevation", "pointsAtX", "pointsAtY", "pointsAtZ", "limitingConeAngle", "z", "scale",
        "xChannelSelector", "yChannelSelector", "stdDeviation", "radius", "baseFrequency", "numOctaves", "seed", "stitchTiles", "tableValues", "slope",
        "intercept", "amplitude", "exponent", "requiredFeatures", "requiredExtensions", "systemLanguage",
        "attributeName", "attributeType", "begin", "dur", "end", "min", "max", "restart", "repeatCount", "repeatDur", "calcMode", "keyTimes",
        "keySplines", "keyPoints", "from", "to", "by", "additive", "accumulate", "origin",
        "alignment-baseline", "baseline-shift", "clip", "clip-path", "clip-rule", "color", "color-interpolation", "color-interpolation-filters",
        "color-rendering", "cursor", "direction", "display", "dominant-baseline", "enable-background", "fill", "fill-opacity", "fill-rule", "filter",
        "flood-color", "flood-opacity", "font", "font-family", "font-feature-settings", "font-kerning", "font-size", "font-size-adjust",
        "font-stretch", "font-style", "font-variant", "font-variant-caps", "font-variant-ligatures", "font-variant-numeric", "font-weight",
        "glyph-orientation-horizontal", "glyph-orientation-vertical", "image-rendering", "inline-size", "isolation", "kerning", "letter-spacing",
        "lighting-color", "line-height", "marker", "marker-end", "marker-mid", "marker-start", "mask", "mask-type", "mix-blend-mode", "opacity",
        "overflow", "paint-order", "pointer-events", "shape-inside", "shape-padding", "shape-rendering", "shape-subtract", "solid-color",
        "solid-opacity", "stop-color", "stop-opacity", "stroke", "stroke-dasharray", "stroke-dashoffset", "stroke-linecap", "stroke-linejoin",
        "stroke-miterlimit", "stroke-opacity", "stroke-width", "text-anchor", "text-decoration", "text-orientation", "text-rendering",
        "transform-box", "transform-origin", "unicode-bidi", "vector-effect", "visibility", "white-space", "word-spacing", "writing-mode",
    )

    /** The XHTML elements a foreignObject's text is laid out with: blocks, lines, lists, tables and how words look. No forms, frames, media, links or scripts. */
    private val HTML_ELEMENTS = setOf(
        "div", "span", "p", "br", "hr", "b", "strong", "i", "em", "u", "s", "strike", "del", "ins", "sub", "sup", "small", "big", "font", "center",
        "code", "pre", "kbd", "samp", "var", "tt", "mark", "q", "cite", "abbr", "dfn", "time", "bdi", "bdo", "wbr", "ruby", "rt", "rp", "a", "label",
        "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd", "section", "article", "header", "footer", "main",
        "aside", "nav", "address", "figure", "figcaption", "table", "caption", "colgroup", "col", "thead", "tbody", "tfoot", "tr", "td", "th",
        "img", "style",
    )

    /** The attributes of that XHTML that lay its text out; an `img`'s `src` is a picture inside the drawing, or goes. */
    private val HTML_ATTRIBUTES = setOf(
        "id", "class", "title", "lang", "dir", "role", "align", "valign", "width", "height", "colspan", "rowspan", "span", "border", "cellpadding",
        "cellspacing", "color", "face", "size", "bgcolor", "alt", "start", "reversed", "type", "value", "nowrap", "media",
    )
}
