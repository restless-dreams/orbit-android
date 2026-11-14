package com.hyuchia.orbit.data

import android.webkit.MimeTypeMap
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer
import java.util.Locale

/**
 * The files an idea holds, as a map's text names them: PDFs, spreadsheets,
 * tickets, any file, in its media folder beside its pictures, videos and
 * sounds. Each idea lists them in its `files` attribute, each by its
 * address (`- src: media/file-boarding-pass.pdf`, as Orbit writes it, or
 * `files: [media/plan.pdf]`), in its `<!-- orbit … -->` attributes or among
 * a YAML map's keys.
 *
 * They're of any kind, so a file is the map's because its text names it in
 * `files`, not by its kind: zips, sending, backups and the folder maps are
 * kept in carry those ([carriedNames]), and nothing else of a kind Orbit
 * doesn't know. They never go to a map's page or a room's browsers, which
 * get only the pictures and videos the map shows ([withoutFiles]).
 */

/** What Orbit names a file it keeps for an idea: `file-` and the name it came with, made safe (`file-boarding-pass.pdf`). */
internal const val FILE_PREFIX = "file-"

/** Whether `name` in a map's media folder is one Orbit gave an idea's file: the earlier versions' files go once nothing points at them. */
internal fun isIdeaFileName(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.length > FILE_PREFIX.length && MediaNames.NAME.matches(name)

/**
 * The names of the map's own files its ideas hold, each once, in the order
 * the text gives them: the `src` of each file in their `files` attributes,
 * or the address a file is written as alone. A file on the web is no file
 * of the map's, and a file's other fields are only words.
 */
fun fileNamesIn(text: String): List<String> {
    val names = LinkedHashSet<String>()

    for (range in fileValues(text)) {
        FileAddress.findAll(text.substring(range.first, range.last + 1)).mapTo(names) { it.groupValues[1] }
    }

    return names.toList()
}

/**
 * The names the map's own files came with, by the name each is kept under:
 * the `name` beside a file's `src` in its idea's `files` entry, which is
 * what the person knows it by. A file written as its address alone, or
 * without a name, has none here.
 */
fun fileLabelsIn(text: String): Map<String, String> {
    val labels = LinkedHashMap<String, String>()

    for (range in fileValues(text)) {
        val value = text.substring(range.first, range.last + 1)
        val starts = FileEntryStart.findAll(value).toList()
        val entries = if (starts.isEmpty()) listOf(value to false) else starts.mapIndexed { index, start -> value.substring(start.range.last + 1, starts.getOrNull(index + 1)?.range?.first ?: value.length) to start.value.endsWith('{') }

        for ((entry, flow) in entries) {
            val address = FileSource.find(entry)?.groupValues?.get(1) ?: continue
            val label = FileLabel.find(entry)?.groupValues?.get(1)?.let { scalar(it, flow) }?.takeIf { it.isNotBlank() } ?: continue
            labels.putIfAbsent(address, label)
        }
    }

    return labels
}

/**
 * A YAML scalar's text, from where it starts to its line's end: a quoted
 * one to its closing quote, read as YAML reads it, and a plain one to a
 * comment, or in a flow object (`flow`) to its next field or its end.
 */
private fun scalar(raw: String, flow: Boolean): String? = when {
    raw.startsWith('"') -> DoubleQuoted.find(raw)?.value?.let { written -> runCatching { Json.decodeFromString(String.serializer(), written) }.getOrElse { written.substring(1, written.length - 1) } }

    raw.startsWith('\'') -> SingleQuoted.find(raw)?.groupValues?.get(1)?.replace("''", "'")

    flow -> raw.substringBefore(" #").substringBefore(',').substringBefore('}').trim()

    else -> raw.substringBefore(" #").trim()
}

/** A double-quoted scalar, to its closing quote. */
private val DoubleQuoted = Regex("""^"(?:[^"\\]|\\.)*"""")

/** A single-quoted scalar's text, to its closing quote, where two quotes are one. */
private val SingleQuoted = Regex("""^'((?:[^']|'')*)'""")

/**
 * `text` with its ideas' `files` values blanked out, spaces for each of
 * their characters, so what's left points only at the pictures, videos and
 * sounds the map shows, where they were (see [MediaNames.references]).
 */
fun withoutFiles(text: String): String {
    val ranges = fileValues(text)

    if (ranges.isEmpty()) {
        return text
    }

    val blanked = StringBuilder(text)

    for (range in ranges) {
        for (index in range) {
            if (blanked[index] != '\n') {
                blanked.setCharAt(index, ' ')
            }
        }
    }

    return blanked.toString()
}

/**
 * The names a map's zip, sending, backups and the folder carry of what its
 * text points at: its pictures, videos and sounds, of the kinds this
 * version keeps ([isMediaName]), and its ideas' files, of any kind
 * ([fileNamesIn]), each a safe name in its media folder.
 */
fun carriedNames(text: String): List<String> {
    val files = fileNamesIn(text)
    val media = MediaNames.references(text).filter(::isMediaName)
    return (media + files).distinct()
}

/**
 * The names of the map's own files that go with it to other people, in a
 * shared zip, sending, a web page or a room's browsers: those it carries
 * ([carriedNames]), but those its text names only in code, which the map
 * shows as words ([MediaNames.withoutCode]). The map keeps them all here,
 * and its backups and the folder maps are kept in take them all.
 */
fun sharedNames(text: String): List<String> = carriedNames(MediaNames.withoutCode(text))

/** Whether a name in a map's media folder is one the map carries, by its kind or as one of its ideas' files: see [carriedNames]. */
internal fun isCarriedName(name: String, fileNames: Collection<String>): Boolean = isMediaName(name) || (name in fileNames && MediaNames.NAME.matches(name))

/**
 * An idea's `files` attribute for `files`, as Orbit writes it, in a
 * `<!-- orbit … -->` block of its own: each file with its address first,
 * as `src`, then its name, type and size, its text quoted as JSON is, which
 * YAML reads as it's written, and nothing in it can end the comment.
 */
internal fun filesAttribute(files: List<AddedFile>): String = buildString {
    append("<!-- orbit\nfiles:\n")

    for (file in files) {
        append("  - src: ").append(file.src).append('\n')
        append("    name: ").append(quoted(file.name)).append('\n')
        file.type?.let { append("    type: ").append(quoted(it)).append('\n') }
        append("    size: ").append(file.size).append('\n')
    }

    append("-->")
}

/** Text quoted as JSON writes it, which YAML reads the same, with `>` written as its escape so it never ends a comment. */
private fun quoted(text: String): String = JsonPrimitive(text).toString().replace(">", "\\u003e")

/** The longest name Orbit gives an idea's file, without its extension: a whole file name stays well inside what file systems take. */
private const val STORED_NAME_LENGTH = 60

/**
 * The name Orbit keeps an idea's file under in its map's media folder:
 * `file-` and the name it came with, in lower case, its letters' accents
 * left out and anything else that isn't a letter or a digit a dash
 * (`Boarding pass (final).PDF` becomes `file-boarding-pass-final.pdf`),
 * with its extension, or the one its `type` gives when it has none. Only
 * names a map's file can have come out of it ([MediaNames.NAME]); the
 * person knows it by the name it came with, which its `files` entry keeps.
 */
fun ideaFileName(name: String, type: String?): String {
    val dot = name.lastIndexOf('.')
    val given = name.substring(dot + 1).lowercase(Locale.ROOT).takeIf { dot > 0 && it.matches(Regex("[a-z0-9]{1,10}")) }
    val extension = given ?: type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it.substringBefore(';').trim().lowercase(Locale.ROOT)) }?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
    val stem = if (given != null) name.substring(0, dot) else name
    val plain = Normalizer.normalize(stem, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    val slug = plain.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(STORED_NAME_LENGTH).trim('-').ifEmpty { "file" }
    return "$FILE_PREFIX$slug${extension?.let { ".$it" }.orEmpty()}"
}

/** A stored name's extension with its dot (`.pdf`), or nothing without one. */
internal fun String.extensionPart(): String = substringAfterLast('.', "").let { if (it.isEmpty() || it == this) "" else ".$it" }

/** Why a file of `size` bytes can't join a map whose pictures, videos, sounds and files come to `mapMedia`, in `words`, or null when it can. */
fun fileRefusal(size: Long, mapMedia: Long, words: Words): String? = when {
    size > MEDIA_FILE_LIMIT -> words.string(R.string.data_file_too_big, readableSize(size, words), readableSize(MEDIA_FILE_LIMIT, words))
    mapMedia + size > MAP_MEDIA_LIMIT -> words.string(R.string.data_file_past_map_room, readableSize(mapMedia, words), readableSize(MAP_MEDIA_LIMIT, words))
    else -> null
}

/**
 * Copies a file into `output` a piece at a time, telling `onProgress` how
 * far along it is, from 0 to 1, when its `size` is known (0 or less when it
 * isn't). One that turns out bigger than a file may be, or than what's left
 * of the room of a map whose pictures, videos, sounds and files come to
 * `mapMedia`, whatever size it said it was, is refused as soon as it does,
 * saying why in `words`. `keepGoing` throws to stop the copy.
 */
internal fun copyFile(input: InputStream, output: OutputStream, size: Long, mapMedia: Long, words: Words, onProgress: (Float) -> Unit, keepGoing: () -> Unit = {}) {
    val buffer = ByteArray(FILE_COPY_BUFFER)
    var copied = 0L
    var reported = -1

    while (true) {
        keepGoing()
        val read = input.read(buffer)

        if (read < 0) {
            break
        }

        copied += read

        if (copied > MEDIA_FILE_LIMIT) {
            throw MediaRefusedException(words.string(R.string.data_file_past_limit, readableSize(MEDIA_FILE_LIMIT, words)))
        }

        if (mapMedia + copied > MAP_MEDIA_LIMIT) {
            throw MediaRefusedException(words.string(R.string.data_file_past_map_room, readableSize(mapMedia, words), readableSize(MAP_MEDIA_LIMIT, words)))
        }

        output.write(buffer, 0, read)

        if (size > 0) {
            val percent = (copied * 100 / size).toInt().coerceAtMost(100)

            if (percent != reported) {
                reported = percent
                onProgress(percent / 100f)
            }
        }
    }
}

private const val FILE_COPY_BUFFER = 256 * 1024

/**
 * The media type a file in a map's media folder is written to the folder
 * maps are kept in as: a picture's, video's or sound's by its kind, an
 * idea's file the one its extension gives, so the folder's app keeps its
 * name as it is.
 */
internal fun storedType(name: String): String {
    val type = mediaType(name)
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return type.takeIf { it != "application/octet-stream" } ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: type
}

/** Where each `files` value is in `text`, from its first character to its last: in a markdown map's attributes, or among a YAML map's keys. */
private fun fileValues(text: String): List<IntRange> {
    if (!text.contains("files")) {
        return emptyList()
    }

    if (MapText.isYaml(text)) {
        val lines = text.split('\n')
        return valuesIn(yamlKeys(lines), startsOf(lines, 0), text, text.length)
    }

    return orbitAttributes(text).flatMap { attributes ->
        val content = attributes.groups[1] ?: return@flatMap emptyList()
        val lines = content.value.split('\n')
        valuesIn(lines, startsOf(lines, content.range.first), text, content.range.last + 1)
    }.toList()
}

/** Where each of `lines` starts in the text, the first at `from`. */
private fun startsOf(lines: List<String>, from: Int): IntArray {
    val starts = IntArray(lines.size)
    var at = from

    lines.forEachIndexed { index, line ->
        starts[index] = at
        at += line.length + 1
    }

    return starts
}

/**
 * Where each `files` value among `lines` is in `text`: on its key's line
 * (an address, a flow list or object, to where it closes, before `limit`),
 * or on the lines under it, further in than its key or list items as far in.
 */
private fun valuesIn(lines: List<String>, starts: IntArray, text: String, limit: Int): List<IntRange> {
    val ranges = ArrayList<IntRange>()

    lines.forEachIndexed { index, line ->
        val key = FilesKey.find(line) ?: return@forEachIndexed
        val valueStart = key.range.last + 1
        val rest = line.substring(valueStart)
        val indent = line.indexOfFirst { !it.isWhitespace() }

        if (rest.isNotBlank()) {
            val first = starts[index] + valueStart + rest.indexOfFirst { !it.isWhitespace() }
            ranges += first..inlineEnd(text, first, limit)
            return@forEachIndexed
        }

        var last = index

        for (next in index + 1 until lines.size) {
            val nextLine = lines[next]
            val nextIndent = nextLine.indexOfFirst { !it.isWhitespace() }

            if (nextIndent < 0) {
                continue
            }

            if (nextIndent > indent || (nextIndent == indent && nextLine.substring(nextIndent).startsWith("- "))) {
                last = next
            } else {
                break
            }
        }

        if (last > index) {
            ranges += starts[index + 1]..(starts[last] + lines[last].length - 1).coerceAtLeast(starts[index + 1])
        }
    }

    return ranges
}

/**
 * Where a value written on its key's line ends: a flow list or object at
 * its closing bracket, wherever it is before `limit`; anything else at the
 * line's end or the comma after it, where an attribute written inline ends.
 */
private fun inlineEnd(text: String, first: Int, limit: Int): Int {
    val opening = text[first]

    if (opening == '[' || opening == '{') {
        var depth = 0
        var quote: Char? = null

        for (index in first until minOf(limit, text.length)) {
            val character = text[index]

            when {
                quote != null -> if (character == quote) quote = null
                character == '"' || character == '\'' -> quote = character
                character == '[' || character == '{' -> depth += 1
                character == ']' || character == '}' -> {
                    depth -= 1

                    if (depth == 0) {
                        return index
                    }
                }
            }
        }

        return minOf(limit, text.length) - 1
    }

    var end = first

    while (end + 1 < minOf(limit, text.length) && text[end + 1] != '\n' && text[end + 1] != ',') {
        end += 1
    }

    return end
}

/** An idea's files key, and where its value starts. */
private val FilesKey = Regex("""(?:^|[\s,{])files\s*:\s?""")

/**
 * A file of the map's in a `files` value: a list item's or a flow list's
 * address, the value alone, or a file's `src`. An address among a file's
 * other fields (its `name`) is only words.
 */
private val FileAddress = Regex("""(?:(?:^|[\[,{])\s*(?:-\s+)?|src\s*:\s*)["']?(?:\./)?$MEDIA_DIRECTORY/(${MediaNames.PATTERN})""", RegexOption.MULTILINE)

/** Where each file's entry starts in a `files` value: a list item, or a flow object. */
private val FileEntryStart = Regex("""^[ \t]*-[ \t]+|\{""", RegexOption.MULTILINE)

/** A file's own address in its entry, as its `src`. */
private val FileSource = Regex("""(?:^|[\s,{])src\s*:\s*["']?(?:\./)?$MEDIA_DIRECTORY/(${MediaNames.PATTERN})""")

/** A file's name in its entry, from where its value starts to its line's end. */
private val FileLabel = Regex("""(?:^|[\s,{])name\s*:[ \t]*([^\n]+)""")
