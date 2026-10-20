package com.hyuchia.orbit.data

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.zip.ZipInputStream

/** The other apps' formats Orbit brings maps in from, by the name people know them by. */
enum class OtherFormat(val label: String) {
    Opml("OPML"),
    FreeMind("FreeMind"),
    XMind("XMind"),
}

/** What a file someone opens is: a map from another app, a zip of Orbit's, or text. */
sealed interface FileKind {
    data class FromOtherApp(val format: OtherFormat) : FileKind

    data object Zip : FileKind

    data object Text : FileKind
}

/**
 * Tells maps from other apps (OPML, FreeMind and Freeplane, XMind) apart
 * from Orbit's own files, by their names and types, then by what they hold:
 * many arrive as `application/octet-stream`, or with no name at all.
 */
object OtherApps {
    /** The types other apps' maps come as: for the file picker, opening files and shares. */
    val TYPES = listOf(
        "text/x-opml",
        "application/x-opml",
        "application/x-freemind",
        "application/vnd.xmind.workbook",
        "application/x-xmind",
        "application/xmind",
    )

    /** The most a workbook from XMind may be: it carries its pictures. */
    const val WORKBOOK_LIMIT = 32L * 1024 * 1024

    /** How much of a file is read to tell what it is. */
    private const val HEAD = 4096

    /** The most entries of a zip read to tell an XMind workbook from a zip of maps. */
    private const val ENTRIES_TO_READ = 2_000

    private val EXTENSIONS = mapOf("opml" to OtherFormat.Opml, "mm" to OtherFormat.FreeMind, "xmind" to OtherFormat.XMind)

    private val FORMATS_BY_TYPE = mapOf(
        "text/x-opml" to OtherFormat.Opml,
        "application/x-opml" to OtherFormat.Opml,
        "application/x-freemind" to OtherFormat.FreeMind,
        "application/vnd.xmind.workbook" to OtherFormat.XMind,
        "application/x-xmind" to OtherFormat.XMind,
        "application/xmind" to OtherFormat.XMind,
    )

    private val ROOTS = mapOf("opml" to OtherFormat.Opml, "map" to OtherFormat.FreeMind, "xmap-content" to OtherFormat.XMind)

    /** The first element of an XML file, after its declaration, comments and DOCTYPE. */
    private val ROOT_ELEMENT = Regex("""^(?:\s|<\?[\s\S]*?\?>|<!--[\s\S]*?-->|<!DOCTYPE[^>]*>)*<([A-Za-z_][\w.:-]*)""")

    /** A format from a file's name or type, without reading it. */
    fun formatByName(name: String, type: String): OtherFormat? =
        EXTENSIONS[name.substringAfterLast('.', "").lowercase()] ?: FORMATS_BY_TYPE[type.substringBefore(';').trim().lowercase()]

    /** A format from the start of a file: the top element of OPML, a FreeMind or Freeplane map, or XMind's content. */
    fun formatOfText(head: String): OtherFormat? = ROOT_ELEMENT.find(head.removePrefix("\uFEFF"))?.groupValues?.get(1)?.let(ROOTS::get)

    /**
     * What a file is, from its name and type, then from what `input` holds:
     * a zip that holds `content.json` or `content.xml` is an XMind workbook,
     * any other zip is Orbit's.
     */
    fun kindOf(name: String, type: String, zipNamed: Boolean, input: InputStream): FileKind {
        formatByName(name, type)?.let { return FileKind.FromOtherApp(it) }

        val head = input.readAtMost(HEAD)

        if (head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()) {
            return if (!zipNamed && holdsWorkbook(SequenceInputStream(ByteArrayInputStream(head), input))) FileKind.FromOtherApp(OtherFormat.XMind) else FileKind.Zip
        }

        formatOfText(asciiOf(head))?.let { return FileKind.FromOtherApp(it) }
        return if (zipNamed) FileKind.Zip else FileKind.Text
    }

    /** Whether a zip holds an XMind workbook's content, reading no more than a workbook may be. */
    private fun holdsWorkbook(input: InputStream): Boolean = try {
        ZipInputStream(Limited(input, WORKBOOK_LIMIT)).use { zip ->
            generateSequence { zip.nextEntry }.take(ENTRIES_TO_READ).any { it.name == "content.json" || it.name == "content.xml" }
        }
    } catch (unreadable: IOException) {
        false
    }

    /** The bytes as text for telling a format: ASCII, without a byte order mark or the zero bytes of UTF-16. */
    private fun asciiOf(bytes: ByteArray): String {
        val unmarked = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> bytes.copyOfRange(3, bytes.size)
            bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() || bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) -> bytes.copyOfRange(2, bytes.size)
            else -> bytes
        }

        return String(unmarked.filter { it != 0.toByte() }.toByteArray(), Charsets.ISO_8859_1)
    }

    /**
     * How many ideas a file from another app holds, for its place in a map's
     * history, counted as the app counts a map's: without its center. Each
     * `<outline>` of OPML, `<node>` of a map or `<topic>` of XMind is an
     * idea, and a single top one is the center.
     */
    fun ideasIn(text: String): Int? {
        val element = when (formatOfText(text.take(HEAD))) {
            OtherFormat.Opml -> "outline"
            OtherFormat.FreeMind -> "node"
            OtherFormat.XMind -> "topic"
            null -> return null
        }

        var depth = 0
        var top = 0
        var ideas = 0

        for (tag in Regex("""<(/?)$element(?:[\s/][^>]*)?>""").findAll(text)) {
            val closing = tag.groupValues[1] == "/"
            val empty = tag.value.endsWith("/>")

            when {
                closing -> depth -= 1
                else -> {
                    ideas += 1

                    if (depth == 0) {
                        top += 1
                    }

                    if (!empty) {
                        depth += 1
                    }
                }
            }
        }

        return if (top == 1) ideas - 1 else ideas
    }

    /** Reads at most `limit` bytes, then ends, as if the stream did. */
    private class Limited(input: InputStream, private var left: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (left <= 0) {
                return -1
            }

            val byte = super.read()

            if (byte >= 0) {
                left -= 1
            }

            return byte
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (left <= 0) {
                return -1
            }

            val read = super.read(buffer, offset, minOf(length.toLong(), left).toInt())

            if (read > 0) {
                left -= read
            }

            return read
        }
    }
}
