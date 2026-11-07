package com.hyuchia.orbit.data

import java.util.Locale

/**
 * A map's tags, kept in its file's frontmatter as `tags:`, where other apps
 * that read markdown (notes apps like Obsidian) look for them too:
 *
 *     tags: [work, japan trip]
 *
 * or, when a tag needs quotes, a tag a line, as Orbit writes them itself:
 *
 *     tags:
 *       - work
 *       - "2027"
 *
 * Words apart with commas (`tags: work, trip`) are read as well. A tag is
 * the same however it's capitalized; the first spelling is kept.
 */
object MapTags {
    /** The `tags:` key and what follows it on its line, read in one pass however long its runs of spaces ([TEXT_BEFORE_SPACES]). */
    private val ENTRY = Regex("""^tags:\s*+($TEXT_BEFORE_SPACES)\s*$""")

    private val YAML_NODES = Regex("""^nodes\s*:.*""")

    /** An item of the list under it (`- work`), read the same way. */
    private val LIST_ITEM = Regex("""^\s*-(?:\s++($TEXT_BEFORE_SPACES))?\s*$""")

    private val SPACES = Regex("""\s+""")

    /** Text other YAML readers take for a number, a boolean or nothing: written in quotes (as Orbit's `needsQuotes`). */
    private val NOT_TEXT_ELSEWHERE = Regex("""^(?:y|n|yes|no|on|off|true|false|null|~)$|^[-+]?(?:\.?\d|\.(?:inf|nan)$)|^0[xob]""", RegexOption.IGNORE_CASE)

    private val SPECIAL = Regex("""[:,#\[\]{}&*!|>'"%@`]""")
    private val CONTROL = Regex("""[\u0000-\u001F\u007F]""")

    /** What the escapes in a double-quoted YAML string stand for, as Orbit reads them; any other is the character itself (`\"`, `\\`, `\/`). */
    private val ESCAPES = mapOf('b' to '\b', 'f' to '\u000C', 'n' to '\n', 'r' to '\r', 't' to '\t', '0' to '\u0000')

    /** What a quote that opens a quoted value comes after, inside a value: a space, or the start of a list, a mapping, an item or a key's value. */
    private const val QUOTE_OPENERS = " \t[{,:"

    /**
     * The map's tags, each once, read as Orbit's YAML reads them: a comment
     * after a value (` # …`) isn't a tag, nor part of one; a quoted tag keeps
     * its commas, quotes and escapes (`"say \"hi\""`, `'it''s'`); and a list
     * on the key's line may go on over the next ones.
     */
    fun read(markdown: String): List<String> {
        val lines = markdown.lines()
        val entry = entry(lines) ?: return emptyList()
        val value = withoutComment(ENTRY.find(lines[entry.first])!!.groupValues[1])

        val tags = when {
            value.startsWith("[") -> flowItems(continuing(lines, entry).fold(value) { list, line -> "$list ${withoutComment(line)}" })
            // Words apart with commas, as Orbit reads a tag's text, quoted or not.
            value.isNotEmpty() -> scalar(value).split(',')
            else -> lines.subList(entry.first + 1, entry.last + 1).mapNotNull { LIST_ITEM.find(it)?.groupValues?.get(1) }.map { scalar(withoutComment(it)) }
        }

        return normalize(tags)
    }

    /**
     * The map with these tags in its frontmatter: in place of the ones it
     * had, or added to its frontmatter, or in a frontmatter of its own. No
     * tags takes the key out, and a frontmatter left empty goes too.
     */
    fun write(markdown: String, tags: List<String>): String {
        val clean = normalize(tags)
        val lines = markdown.lines().toMutableList()
        val entry = entry(lines)
        val written = if (clean.isEmpty()) emptyList() else written(clean)

        if (entry != null) {
            repeat(entry.last - entry.first + 1) { lines.removeAt(entry.first) }
            lines.addAll(entry.first, written)
            return dropEmptyFrontmatter(lines).joinToString("\n")
        }

        if (written.isEmpty()) {
            return markdown
        }

        val closing = closing(lines)

        when {
            closing != null -> lines.addAll(closing, written)
            // A YAML map keeps its tags among its own keys: frontmatter would make it markdown.
            yamlEnd(lines) != null -> lines.addAll(0, written)
            else -> lines.addAll(0, listOf("---") + written + listOf("---", ""))
        }

        return lines.joinToString("\n")
    }

    /** Tags as the app keeps them: trimmed, single spaces, no leading `#`, each once whatever its capitals. */
    fun normalize(tags: List<String>): List<String> {
        val seen = mutableSetOf<String>()

        return tags.map { it.trim().removePrefix("#").trim().replace(SPACES, " ") }
            .filter { it.isNotEmpty() && seen.add(key(it)) }
    }

    /** What two spellings of the same tag have in common. */
    fun key(tag: String): String = tag.trim().removePrefix("#").trim().replace(SPACES, " ").lowercase(Locale.ROOT)

    /** As Orbit writes a list: on one line when no tag needs quotes, else a tag a line. */
    private fun written(tags: List<String>): List<String> {
        if (tags.none(::needsQuotes)) {
            return listOf("tags: [${tags.joinToString(", ")}]")
        }

        return listOf("tags:") + tags.map { "  - ${if (needsQuotes(it)) quote(it) else it}" }
    }

    private fun needsQuotes(tag: String): Boolean =
        tag.isEmpty() || NOT_TEXT_ELSEWHERE.containsMatchIn(tag) || SPECIAL.containsMatchIn(tag) || tag.startsWith("-") ||
            tag != tag.trim() || CONTROL.containsMatchIn(tag)

    private fun quote(text: String): String = buildString {
        append('"')

        for (character in text) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }

        append('"')
    }

    /**
     * A YAML scalar's text: a double-quoted one with its escapes read
     * (`\"`, `\\`, `\n`, `é`…), a single-quoted one with its `''` as one
     * quote, and a plain one as it's written, as Orbit reads each.
     */
    private fun scalar(value: String): String {
        val trimmed = value.trim()

        return when {
            trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"') -> unescaped(trimmed.substring(1, trimmed.length - 1))
            trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'') -> trimmed.substring(1, trimmed.length - 1).replace("''", "'")
            else -> trimmed
        }
    }

    /** A double-quoted YAML string's text with its escapes read, as Orbit reads them: one it doesn't know is the character after the `\`. */
    private fun unescaped(text: String): String = buildString {
        var index = 0

        while (index < text.length) {
            val character = text[index]

            if (character != '\\' || index + 1 >= text.length) {
                append(character)
                index += 1
                continue
            }

            val escape = text[index + 1]
            val hex = text.substring(index + 2, minOf(index + 6, text.length))

            if (escape == 'u' && hex.length == 4 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                append(hex.toInt(16).toChar())
                index += 6
                continue
            }

            append(ESCAPES[escape] ?: escape)
            index += 2
        }
    }

    /**
     * A YAML list written on one line or several (`[work, "japan trip"]`),
     * as its items' text: each quoted, or plain up to the next comma or the
     * list's end, as Orbit reads one.
     */
    private fun flowItems(text: String): List<String> {
        val items = mutableListOf<String>()
        var index = 1

        while (index < text.length) {
            val character = text[index]

            when {
                character.isWhitespace() || character == ',' -> index += 1
                character == ']' -> break
                else -> {
                    val end = if (character == '"' || character == '\'') quoteEnd(text, index) else plainEnd(text, index)
                    items += scalar(text.substring(index, end))
                    index = end
                }
            }
        }

        return items
    }

    /** Where a quoted string starting at `start` ends: past its closing quote (not an escaped one, nor `''`), or at the text's end. */
    private fun quoteEnd(text: String, start: Int): Int {
        val quote = text[start]
        var index = start + 1

        while (index < text.length) {
            when {
                quote == '"' && text[index] == '\\' -> index += 2
                quote == '\'' && text[index] == '\'' && text.getOrNull(index + 1) == '\'' -> index += 2
                text[index] == quote -> return index + 1
                else -> index += 1
            }
        }

        return text.length
    }

    /** Where a plain item of a list starting at `start` ends: at a comma or the list's end. */
    private fun plainEnd(text: String, start: Int): Int {
        var index = start

        while (index < text.length && text[index] != ',' && text[index] != ']' && text[index] != '}') {
            index += 1
        }

        return index
    }

    /**
     * A YAML value without the comment after it (` # …`), as Orbit reads one:
     * a `#` that starts the value, or comes inside its quotes, is the value's
     * own (`C#`, `"#1 # first"`), and a quote opens only at the value's start
     * or after a space or `[`, `{`, `,`, `:`, so `it's` is plain text.
     */
    private fun withoutComment(value: String): String {
        var index = 0

        while (index < value.length) {
            val character = value[index]

            when {
                (character == '"' || character == '\'') && (index == 0 || value[index - 1] in QUOTE_OPENERS) -> index = quoteEnd(value, index)
                character == '#' && index > 0 && value[index - 1].isWhitespace() -> return value.substring(0, index).trimEnd()
                else -> index += 1
            }
        }

        return value
    }

    /** How many more `[` than `]` a list's line opens, outside its quotes and comment: a list that hasn't closed goes on over the next line. */
    private fun opened(line: String): Int {
        val text = withoutComment(line)
        var open = 0
        var index = 0

        while (index < text.length) {
            val character = text[index]

            when {
                (character == '"' || character == '\'') && (index == 0 || text[index - 1] in QUOTE_OPENERS) -> index = quoteEnd(text, index)
                else -> {
                    if (character == '[') {
                        open += 1
                    } else if (character == ']') {
                        open -= 1
                    }

                    index += 1
                }
            }
        }

        return open
    }

    /** The lines a list on the `tags:` line goes on over, trimmed, without the comments on lines of their own. */
    private fun continuing(lines: List<String>, entry: IntRange): List<String> =
        lines.subList(entry.first + 1, entry.last + 1).map { it.trim() }.filterNot { it.startsWith("#") }

    /** Where the map's own keys start: after the frontmatter's opening line, or at the top of a YAML map. */
    private fun frontmatterStart(lines: List<String>): Int = if (closing(lines) != null) 1 else 0

    /**
     * How far a YAML map's own keys go, as frontmatter's do (the whole text:
     * `tags:` is one of its keys at the top, beside `nodes:`), when the text
     * is a YAML map rather than markdown.
     */
    private fun yamlEnd(lines: List<String>): Int? = lines.size.takeIf { lines.any { YAML_NODES.matches(it.trimEnd()) } }

    /** The line closing the frontmatter, when the text starts with one. */
    private fun closing(lines: List<String>): Int? {
        if (lines.firstOrNull()?.trim() != "---") {
            return null
        }

        return (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
    }

    /** The lines of the `tags:` entry in the frontmatter: its own, then its list's. */
    private fun entry(lines: List<String>): IntRange? {
        val closing = closing(lines) ?: yamlEnd(lines) ?: return null
        val start = (frontmatterStart(lines) until closing).firstOrNull { ENTRY.matches(lines[it]) } ?: return null
        val value = ENTRY.find(lines[start])!!.groupValues[1]
        var end = start

        // A list written on the key's own line has nothing under it, but may go on over the next lines until it closes.
        if (value.isNotEmpty()) {
            if (withoutComment(value).startsWith("[")) {
                var open = opened(value)

                while (open > 0 && end + 1 < closing) {
                    end += 1
                    open += lines[end].trim().takeUnless { it.startsWith("#") }?.let(::opened) ?: 0
                }
            }

            return start..end
        }

        // A list under it: its items, as deep as the key or deeper, with comments and blank lines among them.
        while (end + 1 < closing && (LIST_ITEM.matches(lines[end + 1]) || lines[end + 1].isBlank() || lines[end + 1].trimStart().startsWith("#"))) {
            end += 1
        }

        while (end > start && (lines[end].isBlank() || lines[end].trimStart().startsWith("#"))) {
            end -= 1
        }

        return start..end
    }

    /** Without its frontmatter when nothing's left in it, and the blank line after it. */
    private fun dropEmptyFrontmatter(lines: MutableList<String>): List<String> {
        val closing = closing(lines) ?: return lines

        if ((1 until closing).any { lines[it].isNotBlank() }) {
            return lines
        }

        repeat(closing + 1) { lines.removeAt(0) }

        if (lines.firstOrNull()?.isBlank() == true) {
            lines.removeAt(0)
        }

        return lines
    }
}
