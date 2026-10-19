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
    private val ENTRY = Regex("""^tags:\s*(.*?)\s*$""")
    private val LIST_ITEM = Regex("""^\s*-(?:\s+(.*?))?\s*$""")
    private val SPACES = Regex("""\s+""")

    /** Text other YAML readers take for a number, a boolean or nothing: written in quotes (as Orbit's `needsQuotes`). */
    private val NOT_TEXT_ELSEWHERE = Regex("""^(?:y|n|yes|no|on|off|true|false|null|~)$|^[-+]?(?:\.?\d|\.(?:inf|nan)$)|^0[xob]""", RegexOption.IGNORE_CASE)

    private val SPECIAL = Regex("""[:,#\[\]{}&*!|>'"%@`]""")
    private val CONTROL = Regex("""[\u0000-\u001F\u007F]""")

    /** The map's tags, each once. */
    fun read(markdown: String): List<String> {
        val lines = markdown.lines()
        val entry = entry(lines) ?: return emptyList()
        val value = ENTRY.find(lines[entry.first])!!.groupValues[1]

        val tags = when {
            value.startsWith("[") -> split(value.removePrefix("[").removeSuffix("]"))
            value.isNotEmpty() -> split(value)
            else -> lines.subList(entry.first + 1, entry.last + 1).mapNotNull { LIST_ITEM.find(it)?.groupValues?.get(1) }
        }

        return normalize(tags.map(::unquote))
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

        if (closing != null) {
            lines.addAll(closing, written)
        } else {
            lines.addAll(0, listOf("---") + written + listOf("---", ""))
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

    private fun unquote(value: String): String {
        val trimmed = value.trim()

        return when {
            trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"') ->
                trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'') ->
                trimmed.substring(1, trimmed.length - 1).replace("''", "'")
            else -> trimmed
        }
    }

    /** A list's items, apart at commas outside quotes. */
    private fun split(text: String): List<String> {
        val items = mutableListOf<String>()
        val item = StringBuilder()
        var quote: Char? = null

        for (character in text) {
            when {
                quote != null && character == quote -> quote = null
                quote == null && (character == '"' || character == '\'') -> quote = character
                quote == null && character == ',' -> {
                    items.add(item.toString())
                    item.clear()
                    continue
                }
            }

            item.append(character)
        }

        items.add(item.toString())
        return items
    }

    /** The line closing the frontmatter, when the text starts with one. */
    private fun closing(lines: List<String>): Int? {
        if (lines.firstOrNull()?.trim() != "---") {
            return null
        }

        return (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
    }

    /** The lines of the `tags:` entry in the frontmatter: its own, then its list's. */
    private fun entry(lines: List<String>): IntRange? {
        val closing = closing(lines) ?: return null
        val start = (1 until closing).firstOrNull { ENTRY.matches(lines[it]) } ?: return null

        // A list written on the key's own line has nothing under it.
        if (ENTRY.find(lines[start])!!.groupValues[1].isNotEmpty()) {
            return start..start
        }

        var end = start

        // A list under it: its items, as deep as the key or deeper.
        while (end + 1 < closing && (LIST_ITEM.matches(lines[end + 1]) || lines[end + 1].isBlank())) {
            end += 1
        }

        while (end > start && lines[end].isBlank()) {
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
