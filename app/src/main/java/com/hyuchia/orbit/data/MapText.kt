package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.map.GOAL_ATTRIBUTES
import com.hyuchia.orbit.map.MapProgress
import com.hyuchia.orbit.map.Rollup
import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What the home screen shows about a map, read from its orbit-md text. */
data class MapOutline(
    val title: String,

    /** The first level of the map: the planets around its sun. */
    val branches: Int,

    /** Every node below the root. */
    val ideas: Int,

    /** The map's to-dos and goals, counted the way its rings and outline count them (see [MapProgress]). */
    val progress: Rollup = Rollup(),

    /** The style the map picks in its frontmatter, if any. */
    val style: String? = null,

    /** Its tags, from its frontmatter (see [MapTags]). */
    val tags: List<String> = emptyList(),
)

private val HEADING = Regex("""^ {0,3}(#{1,6})\s+(.*?)(?:\s+#+)?\s*$""")
private val LIST_ITEM = Regex("""^(\s*)(?:[-*+]|\d+[.)])\s+(.*)$""")
private val FRONTMATTER_ENTRY = Regex("""^([A-Za-z][\w-]*):\s*(.*?)\s*$""")
private val ATTRIBUTE_COMMENT = Regex("""\s*<!--.*?-->""")
private val INLINE_ATTRIBUTES = Regex("""<!--\s*orbit\b(.*?)-->""")
/** A line of an attribute block, or one attribute of a line's comment: `target: 5`. */
private val ATTRIBUTE = Regex("""^\s*([A-Za-z][\w-]*)\s*:\s*(.*?)\s*$""")
/** An attribute comment on a line of its own, under its node. */
private val ATTRIBUTE_LINE = Regex("""^\s*<!--\s*orbit\b(.*?)-->\s*$""")
private val CHECKBOX = Regex("""^\[([ xX/])]\s+""")
private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
private val EMPHASIS = Regex("""[*_~`]+""")
/** A heading's or list item's marker with nothing after it. */
private val BARE_MARKER = Regex("""#{1,6}|[-*+]|\d+[.)]""")

/** The attributes the outline reads: a node's status, and what makes it a goal or a Progress idea, and a goal's numbers. */
private val COUNTED_ATTRIBUTES = setOf("status", "type") + GOAL_ATTRIBUTES

/** A node as the outline reads it: where it sits, and the attributes that count its progress. */
private class ScannedNode(val id: String, val parentId: String?) {
    var status: String? = null
    val attributes = mutableMapOf<String, String>()

    fun read(attributes: Map<String, String>) {
        for ((key, value) in attributes) {
            if (key in COUNTED_ATTRIBUTES) {
                this.attributes[key] = value
            }
        }

        attributes["status"]?.let { status = it }
    }

    fun toMapNode(): MapNode = MapNode(
        id = id,
        text = "",
        parentId = parentId,
        status = status,
        // A type's version (`goal@2`) doesn't change how it counts.
        type = attributes["type"]?.substringBefore('@')?.trim(),
        attrs = JsonObject(attributes.filterKeys { it in GOAL_ATTRIBUTES }.mapValues { JsonPrimitive(it.value) }),
    )
}

object MapText {
    /**
     * The map's title (its frontmatter title, else its first heading, else
     * its first line, else "Untitled map" in `words`), how many nodes it has,
     * and how far along its to-dos and goals are. A light reading for the
     * home screen: the map itself is read by Orbit. Progress is counted by
     * [MapProgress], as on the map.
     */
    fun outline(markdown: String, words: Words): MapOutline {
        val lines = markdown.lines()
        val frontmatter = frontmatter(lines)
        var title: String? = null
        var rootLevel: Int? = null
        var rootIsListItem = false
        var headingLevel: Int? = null
        var listBase: Int? = null
        var childIndent: Int? = null
        var branches = 0
        var ideas = 0
        var inCode = false
        var inAttributes = false
        val nodes = mutableListOf<ScannedNode>()
        // Where the next node goes: the headings it's under, by level, and the list items, by indentation.
        val headings = ArrayDeque<Pair<Int, ScannedNode>>()
        val items = ArrayDeque<Pair<Int, ScannedNode>>()

        fun addNode(parent: ScannedNode?, text: String): ScannedNode {
            val node = ScannedNode("node-${nodes.size}", parent?.id)
            INLINE_ATTRIBUTES.find(text)?.let { node.read(inlineAttributes(it.groupValues[1])) }
            // A checkbox says the status, before any in the line's comment.
            nodeStatus(text)?.let { node.status = it }
            nodes += node
            return node
        }

        for (line in lines.drop(frontmatter.bodyStart)) {
            if (inAttributes) {
                if (line.contains("-->")) {
                    inAttributes = false
                }

                ATTRIBUTE.find(line.substringBefore("-->"))?.let { attribute ->
                    nodes.lastOrNull()?.read(mapOf(attribute.groupValues[1] to unquote(attribute.groupValues[2])))
                }

                continue
            }

            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                continue
            }

            if (inCode || line.trimStart().startsWith(">")) {
                continue
            }

            if (line.trimStart().startsWith("<!--") && line.contains("orbit") && !line.contains("-->")) {
                inAttributes = true
                continue
            }

            ATTRIBUTE_LINE.find(line)?.let { comment ->
                nodes.lastOrNull()?.read(inlineAttributes(comment.groupValues[1]))
                continue
            }

            val heading = HEADING.find(line)
            val item = if (heading == null) LIST_ITEM.find(line) else null

            if (heading == null && item == null) {
                continue
            }

            val text = heading?.groupValues?.get(2) ?: item!!.groupValues[2]

            if (heading != null) {
                val level = heading.groupValues[1].length

                if (title == null) {
                    title = clean(text)
                    rootLevel = level
                    headings.addLast(level to addNode(null, text))
                } else {
                    ideas += 1

                    if (level == rootLevel?.plus(1)) {
                        branches += 1
                    }

                    // The root stays: a heading as high as it still goes in its orbit.
                    while (headings.size > 1 && headings.last().first >= level) {
                        headings.removeLast()
                    }

                    headings.addLast(level to addNode(headings.lastOrNull()?.second, text))
                }

                items.clear()
                headingLevel = level
                listBase = null
                continue
            }

            val indent = item!!.groupValues[1].length

            if (title == null) {
                // A map that is only a list: its first item is the root.
                title = clean(text)
                rootIsListItem = true
                listBase = indent
                headings.addLast(Int.MIN_VALUE to addNode(null, text))
                continue
            }

            while (items.isNotEmpty() && items.last().first >= indent) {
                items.removeLast()
            }

            items.addLast(indent to addNode(items.lastOrNull()?.second ?: headings.lastOrNull()?.second, text))
            ideas += 1
            val base = listBase ?: indent.also { listBase = it }

            if (rootIsListItem) {
                // The root item's children are indented under it; other top items share its orbit.
                val firstChildIndent = childIndent ?: indent.takeIf { it > base }?.also { childIndent = it }

                if (indent == base || indent == firstChildIndent) {
                    branches += 1
                }
            } else if (headingLevel == rootLevel && indent == base) {
                // A list right under the root heading: its top items are planets.
                branches += 1
            }
        }

        val mapNodes = nodes.map { it.toMapNode() }
        val fallback = lines.drop(frontmatter.bodyStart).firstOrNull { it.isNotBlank() }?.trim()
        return MapOutline(
            // A title that's a YAML list or map (or the start of one) isn't a name.
            title = frontmatter.values["title"]?.takeUnless { it.startsWith("[") || it.startsWith("{") } ?: title?.ifBlank { null } ?: fallback ?: words.string(R.string.common_untitled_map),
            branches = branches,
            ideas = ideas,
            progress = mapNodes.firstOrNull()?.let { root -> MapProgress(mapNodes).under(root) } ?: Rollup(),
            style = frontmatter.values["theme"]?.takeIf { it != "auto" },
            tags = MapTags.read(markdown),
        )
    }

    /** The map's words without markup, a line per node or paragraph, to search in. */
    fun searchText(markdown: String): String {
        val lines = markdown.lines()
        val body = lines.drop(frontmatter(lines).bodyStart)
        val words = StringBuilder()
        var inAttributes = false

        for (line in body) {
            if (inAttributes) {
                if (line.contains("-->")) {
                    inAttributes = false
                }

                continue
            }

            if (line.trimStart().startsWith("<!--") && !line.contains("-->")) {
                inAttributes = true
                continue
            }

            val text = HEADING.find(line)?.groupValues?.get(2)
                ?: LIST_ITEM.find(line)?.groupValues?.get(2)
                ?: line.trimStart('>', ' ', '\t')
            val cleaned = clean(text)

            if (cleaned.isNotEmpty() && !cleaned.startsWith("```")) {
                words.append(cleaned).append('\n')
            }
        }

        return words.toString()
    }

    /**
     * Whether the text holds anything of a map: a named idea, a note, any
     * line beyond frontmatter, comments and nameless headings. Text without
     * any never replaces a map that has some (see [MapRepository.write]).
     */
    fun hasContent(markdown: String): Boolean {
        val lines = markdown.lines()
        var inComment = false

        for (line in lines.drop(frontmatter(lines).bodyStart)) {
            var rest = line

            if (inComment) {
                val end = rest.indexOf("-->")

                if (end < 0) {
                    continue
                }

                inComment = false
                rest = rest.substring(end + 3)
            }

            rest = rest.replace(ATTRIBUTE_COMMENT, "")
            val opened = rest.indexOf("<!--")

            if (opened >= 0) {
                inComment = true
                rest = rest.substring(0, opened)
            }

            if (BARE_MARKER.matches(rest.trim())) {
                continue
            }

            val text = HEADING.find(rest)?.groupValues?.get(2) ?: LIST_ITEM.find(rest)?.groupValues?.get(2) ?: rest

            if (clean(text).isNotEmpty()) {
                return true
            }
        }

        return false
    }

    /** `text` cut to at most `length` characters at a word's end, with "…" when it was cut. */
    fun shorten(text: String, length: Int): String {
        val single = text.replace(Regex("""\s+"""), " ").trim()

        if (single.length <= length) {
            return single
        }

        val cut = single.substring(0, length - 1)
        val space = cut.lastIndexOf(' ')
        return (if (space >= length / 2) cut.substring(0, space) else cut).trimEnd(' ', ',', ';', ':', '.', '-') + "…"
    }

    /** A new map: just its root, "Untitled map" in `words` without a title, and the style it picks, if any. */
    fun newMap(title: String, words: Words, style: String? = null): String {
        val root = "# ${escapeTitle(title.trim().ifEmpty { words.string(R.string.common_untitled_map) })}\n"
        return if (style == null) root else "---\ntheme: $style\n---\n\n$root"
    }

    /**
     * The map with a new title: its root heading (or first item), and its
     * frontmatter title if it has one; "Untitled map" in `words` for a blank
     * one. Attributes on the root's line stay.
     */
    fun retitle(markdown: String, title: String, words: Words): String {
        val lines = markdown.lines().toMutableList()
        val frontmatter = frontmatter(lines)
        val newTitle = title.replace(Regex("""\s+"""), " ").trim().ifEmpty { words.string(R.string.common_untitled_map) }

        for (index in 1 until frontmatter.bodyStart - 1) {
            val entry = FRONTMATTER_ENTRY.find(lines[index]) ?: continue

            if (entry.groupValues[1] == "title") {
                lines[index] = "title: ${quoteIfNeeded(newTitle)}"
            }
        }

        for (index in frontmatter.bodyStart until lines.size) {
            val line = lines[index]
            val heading = HEADING.find(line)
            val item = if (heading == null) LIST_ITEM.find(line) else null

            if (heading == null && item == null) {
                continue
            }

            val attributes = ATTRIBUTE_COMMENT.find(line)?.value.orEmpty()

            lines[index] = if (heading != null) {
                "${heading.groupValues[1]} ${escapeTitle(newTitle)}$attributes"
            } else {
                "${item!!.groupValues[1]}- ${escapeTitle(newTitle)}$attributes"
            }

            break
        }

        return lines.joinToString("\n")
    }

    /** A folder name for a map called `title`: lowercase words joined by hyphens. */
    fun folderName(title: String, taken: Set<String>): String {
        val slug = title.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "-")
            .trim('-')
            .take(40)
            .trimEnd('-')
            .ifEmpty { "map" }
        var name = slug
        var suffix = 2

        while (name in taken) {
            name = "$slug-$suffix"
            suffix += 1
        }

        return name
    }

    /**
     * A markdown file's name for a map called `title`, in the folder maps are
     * kept in: the title without what file names can't hold ("Untitled map"
     * in `words` when that's all of it), unlike any name `taken` (ignoring
     * case, as most storage does).
     */
    fun fileName(title: String, taken: Collection<String>, words: Words): String {
        val base = title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart('.')
            .take(80)
            .trim()
            .ifEmpty { words.string(R.string.common_untitled_map) }
        val lowercase = taken.mapTo(HashSet()) { it.lowercase() }
        var name = "$base.md"
        var suffix = 2

        while (name.lowercase() in lowercase) {
            name = "$base ($suffix).md"
            suffix += 1
        }

        return name
    }

    private class Frontmatter(val values: Map<String, String>, val bodyStart: Int)

    private fun frontmatter(lines: List<String>): Frontmatter {
        if (lines.firstOrNull()?.trim() != "---") {
            return Frontmatter(emptyMap(), 0)
        }

        val values = mutableMapOf<String, String>()
        var index = 1

        while (index < lines.size && lines[index].trim() != "---") {
            FRONTMATTER_ENTRY.find(lines[index])?.let { values[it.groupValues[1]] = unquote(it.groupValues[2]) }
            index += 1
        }

        return Frontmatter(values, minOf(index + 1, lines.size))
    }

    /** The status a node's checkbox sets, if it has one. Statuses in attributes are read with the rest of them. */
    private fun nodeStatus(text: String): String? {
        val match = CHECKBOX.find(text.trim()) ?: return null

        return when (match.groupValues[1]) {
            "x", "X" -> "done"
            "/" -> "active"
            else -> "todo"
        }
    }

    /** The attributes in a line's comment, `type: goal, target: 5`: split at commas outside quotes. */
    private fun inlineAttributes(body: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val part = StringBuilder()
        var quoted = false
        var escaped = false

        fun finishPart() {
            ATTRIBUTE.find(part.toString())?.let { attributes[it.groupValues[1]] = unquote(it.groupValues[2]) }
            part.clear()
        }

        for (character in body) {
            when {
                escaped -> escaped = false
                character == '\\' && quoted -> escaped = true
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    finishPart()
                    continue
                }
            }

            part.append(character)
        }

        finishPart()
        return attributes
    }

    private fun clean(title: String): String =
        title.replace(ATTRIBUTE_COMMENT, "")
            .replace(CHECKBOX, "")
            .replace(LINK) { it.groupValues[1] }
            .replace(EMPHASIS, "")
            .replace("\\", "")
            .trim()

    /** Keeps a title from reading as markdown structure, the way Orbit's serializer does. */
    private fun escapeTitle(title: String): String =
        title.replace(Regex("""^([#>\[]|\d+[.)]\s|[-*+]\s)""")) { "\\${it.value}" }

    private fun quoteIfNeeded(value: String): String =
        if (value.any { it in ":#'\"[]{}," } || value != value.trim()) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            value
        }

    private fun unquote(value: String): String {
        val trimmed = value.trim()

        if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            return trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }

        return trimmed.removeSurrounding("'")
    }
}
