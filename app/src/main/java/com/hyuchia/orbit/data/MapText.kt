package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.map.GOAL_ATTRIBUTES
import com.hyuchia.orbit.map.MapProgress
import com.hyuchia.orbit.map.Rollup
import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.ceil

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

/**
 * A line's text before the spaces it ends with, to put in other patterns
 * where `(.*?)` would come before `\s*$`, read in one pass: a run of spaces
 * is taken whole, and only when more than spaces follows it. The lazy group
 * tried every length in turn and looked along the spaces after each, so a
 * line with a long run of them took as long as their count squared (seconds
 * for 20,000). Its characters are the ones `.` takes, as the lazy group's
 * were, and its spaces the ones `\s` takes: a line separator ends the text
 * all the same.
 */
internal const val TEXT_BEFORE_SPACES = """(?:(?!\s).|(?:(?=\s).)++(?!\s*+$))*+"""

/**
 * A heading: its `#`s, and its text without the `#`s that may close it
 * (`## Tram ##`) and the spaces it ends with, read in one pass as
 * [TEXT_BEFORE_SPACES] reads a line's: a run of spaces is the text's when
 * more than closing `#`s and spaces follows it.
 */
private val HEADING = Regex("""^ {0,3}(#{1,6})\s++((?:(?!\s).|(?:(?=\s).)++(?!\s*+(?:#++\s*+)?$))*+)(?:\s+#+)?\s*$""")

/** A list item: its indentation, and its text. The spaces after its marker are read once: a long run of them costs one pass, even on a line that turns out not to be one. */
private val LIST_ITEM = Regex("""^(\s*)(?:[-*+]|\d+[.)])\s++(.*)$""")

private val FRONTMATTER_ENTRY = Regex("""^([A-Za-z][\w-]*):\s*+($TEXT_BEFORE_SPACES)\s*$""")

/** A YAML map's own list of nodes, and its format's version beside it, at the top of the document. */
private val YAML_NODES = Regex("""^nodes\s*:""", RegexOption.MULTILINE)

/** A first line that makes the text a YAML map, whatever follows, as Orbit reads one: `nodes:` with nothing after it, or a list, a mapping or a comment. */
private val FIRST_NODES = Regex("""^nodes\s*:\s*(?:$|[#\[{])""")

/** A line with one of a map's options (`title: …`, `theme: …`), as a YAML map may start with one. */
private val OPTION_LINE = Regex("""^([A-Za-z][\w-]*)\s*:(?:\s|$)""")

/** The options a map sets at its top, as Orbit knows them (its `DOCUMENT_OPTION_KEYS`). */
private val DOCUMENT_OPTIONS = setOf(
    "title", "theme", "view", "showRings", "interactive", "animation", "speed", "palette", "height", "maxDepth", "className", "baseUrl", "schemaVersion", "flavor",
)

/**
 * A YAML key and what follows it on its line (`text: Cells`, `"src": …`,
 * `children:`): the key is the first group, its value the second. A plain
 * key runs to the first colon with a space or nothing after it, read once,
 * so a long line costs one pass.
 */
private val YAML_ENTRY = Regex("""^((?:"(?:[^"\\]|\\.)*+"|'(?:[^']|'')*+'|[^\s#"'\[\]{},](?:[^:]|:(?=\S))*+))\s*:(?:\s+(.*)|$)""")

/** A YAML list item's dash, before what it holds. */
private val YAML_DASH = Regex("""^-(?:\s+|$)""")

/** A YAML block scalar's start (`note: |`, `>-`): its value is the lines indented under its key. */
private val BLOCK_SCALAR = Regex("""^[|>][-+]?\d?$""")

/** A node in a YAML map's list, by its indentation: `- text: Light reactions`, or a name alone, `- Chlorophyll`. */
private val YAML_ITEM = Regex("""^(\s*)-\s++($TEXT_BEFORE_SPACES)\s*$""")

/** A key of a YAML map, by its indentation: `title: Biology` at the top, `    status: done` on a node. */
private val YAML_KEY = Regex("""^(\s*)([A-Za-z][\w-]*)\s*:\s*+($TEXT_BEFORE_SPACES)\s*$""")
private val YAML_SCHEMA_VERSION = Regex("""^schemaVersion\s*:\s*["']?([\d.]+)["']?\s*$""", RegexOption.MULTILINE)

/** A comment and the spaces before it, from where they start: a long run of spaces with no comment after it is looked along once, rather than once from each of its spaces. */
private val ATTRIBUTE_COMMENT = Regex("""(?<!\s)\s*<!--.*?-->""")

private val INLINE_ATTRIBUTES = Regex("""<!--\s*orbit\b(.*?)-->""")
/** A line of an attribute block, or one attribute of a line's comment: `target: 5`. */
private val ATTRIBUTE = Regex("""^\s*([A-Za-z][\w-]*)\s*:\s*+($TEXT_BEFORE_SPACES)\s*$""")
/** An attribute comment on a line of its own, under its node. */
private val ATTRIBUTE_LINE = Regex("""^\s*<!--\s*orbit\b(.*?)-->\s*$""")
private val CHECKBOX = Regex("""^\[([ xX/])]\s+""")
private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
private val EMPHASIS = Regex("""[*_~`]+""")
/** Emphasis around words, as an idea's name has it: underscores inside a word (`copy_versions`) are the name's own. */
private val WRAPPING_EMPHASIS = Regex("""[*~`]+|(?<!\w)_+|_+(?!\w)""")
/** A heading's or list item's marker with nothing after it. */
private val BARE_MARKER = Regex("""#{1,6}|[-*+]|\d+[.)]""")

/** The attributes the outline reads: a node's status, and what makes it a goal or a Progress idea, and a goal's numbers. */
private val COUNTED_ATTRIBUTES = setOf("status", "type") + GOAL_ATTRIBUTES

/** A type with the version of its fields it was written for, as Orbit reads one: `goal@2`. */
private val VERSIONED_TYPE = Regex("""^(.*\S)@(\d+)$""")

/** An idea as a light reading of a map's text has it: its name, and where it sits (its parent's place in the list; none for the map's center). */
data class TextIdea(val name: String, val parent: Int?)

/** A node as the outline reads it: its name, where it sits, and the attributes that count its progress. */
private class ScannedNode(val id: String, var parentId: String?, var name: String) {
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

    fun toMapNode(): MapNode {
        val type = attributes["type"]?.trim()
        val versioned = type?.let(VERSIONED_TYPE::matchEntire)

        return MapNode(
            id = id,
            text = "",
            parentId = parentId,
            status = status,
            // A type's version, as Orbit reads it (`goal@2`): a goal or Progress written for a newer one counts as an idea like any
            // other (MapNode.isGoal), and so does one for a version past what an Int holds.
            type = versioned?.groupValues?.get(1) ?: type,
            typeVersion = versioned?.groupValues?.get(2)?.let { it.toIntOrNull() ?: Int.MAX_VALUE },
            attrs = JsonObject(attributes.filterKeys { it in GOAL_ATTRIBUTES }.mapValues { JsonPrimitive(it.value) }),
        )
    }
}

/** What a light reading of a map's text found: its first title, its planets and ideas counted, and every node. */
private class Scan(val title: String?, val branches: Int, val ideas: Int, val nodes: List<ScannedNode>)

object MapText {
    /**
     * The map's title (its frontmatter title, else its first heading, else
     * its first line, else "Untitled map" in `words`), how many nodes it has,
     * and how far along its to-dos and goals are. A YAML map is read as Orbit
     * reads one: its own title, else its one node at the top. A light reading
     * for the home screen: the map itself is read by Orbit. Progress is
     * counted by [MapProgress], as on the map.
     */
    fun outline(markdown: String, words: Words): MapOutline {
        val lines = markdown.lines()
        val yaml = isYaml(lines)
        val frontmatter = frontmatter(lines)
        // A YAML map's keys at the top say what frontmatter would.
        val options = if (yaml) yamlKeys(lines) else frontmatter.values
        val scan = if (yaml) scanYaml(lines) else scan(lines, frontmatter.bodyStart)
        val mapNodes = scan.nodes.map { it.toMapNode() }
        val fallback = if (yaml) null else lines.drop(frontmatter.bodyStart).firstOrNull { it.isNotBlank() }?.trim()
        return MapOutline(
            // A title that's a YAML list or map (or the start of one) isn't a name.
            title = options["title"]?.takeUnless { it.startsWith("[") || it.startsWith("{") } ?: scan.title?.ifBlank { null } ?: fallback ?: words.string(R.string.common_untitled_map),
            branches = scan.branches,
            ideas = scan.ideas,
            progress = mapNodes.firstOrNull()?.let { root -> MapProgress(mapNodes).under(root) } ?: Rollup(),
            style = options["theme"]?.takeIf { it != "auto" },
            tags = MapTags.read(markdown),
        )
    }

    /**
     * Every idea in the map's text, its center first, each after its parent:
     * the same light reading as [outline], with the ideas' names, for telling
     * what changed between versions. The map itself is read by Orbit.
     */
    fun ideas(markdown: String): List<TextIdea> {
        val lines = markdown.lines()
        val nodes = (if (isYaml(lines)) scanYaml(lines) else scan(lines, frontmatter(lines).bodyStart)).nodes
        val places = nodes.withIndex().associate { (index, node) -> node.id to index }
        return nodes.map { TextIdea(it.name, it.parentId?.let(places::get)) }
    }

    /** The headings and list items of the map's text after its frontmatter, as the outline reads them. */
    private fun scan(lines: List<String>, bodyStart: Int): Scan {
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
            val node = ScannedNode("node-${nodes.size}", parent?.id, name(text))
            INLINE_ATTRIBUTES.find(text)?.let { node.read(inlineAttributes(it.groupValues[1])) }
            // A checkbox says the status, before any in the line's comment.
            nodeStatus(text)?.let { node.status = it }
            nodes += node
            return node
        }

        for (line in lines.drop(bodyStart)) {
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

        return Scan(title, branches, ideas, nodes)
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
     * The version of the format `markdown` is written for: its `schemaVersion`,
     * in the frontmatter or at the top of a YAML map, and 1 when it says none,
     * as Orbit reads it. Orbit takes any number above 0, so one between two
     * versions (`1.5`) counts as the later, newer than the earlier's maps.
     */
    fun formatVersion(markdown: String): Int {
        val declared = frontmatter(markdown.lines()).values["schemaVersion"]
            ?: YAML_SCHEMA_VERSION.find(markdown)?.takeIf { YAML_NODES.containsMatchIn(markdown) }?.groupValues?.get(1)

        return declared?.trim()?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.let { ceil(it).toInt() } ?: 1
    }

    /**
     * Whether the text holds anything of a map: a named idea, a note, any
     * line beyond frontmatter, comments and nameless headings. A YAML map is
     * read as one ([yamlHasContent]): its keys and empty lists are only its
     * structure. Text without any never replaces a map that has some (see
     * [MapRepository.write]).
     */
    fun hasContent(markdown: String): Boolean {
        val lines = markdown.lines()

        if (isYaml(lines)) {
            return yamlHasContent(lines)
        }

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

    /** A YAML map's own keys, at the top beside `nodes:`, which say what a frontmatter's do (`title`, `theme`). */
    private fun yamlKeys(lines: List<String>): Map<String, String> =
        lines.mapNotNull { line -> YAML_KEY.find(line)?.takeIf { it.groupValues[1].isEmpty() } }.associate { it.groupValues[2] to unquote(it.groupValues[3]) }

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

    /**
     * Whether `lines` are a YAML map, as Orbit tells one from markdown: its
     * frontmatter says `flavor: yaml`, or its first line is `nodes:` with a
     * list or nothing after it, or it reads as YAML with a list of nodes, or
     * it starts with one of a map's options and reads as YAML. So markdown
     * with a note that starts with "nodes:" stays markdown, as for Orbit.
     */
    private fun isYaml(lines: List<String>): Boolean {
        val first = lines.indexOfFirst { it.isNotBlank() }

        if (first < 0) {
            return false
        }

        if (lines[first].trim() == "---") {
            val close = (first + 1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" }

            if (close != null) {
                return (first + 1 until close).any { index -> FRONTMATTER_ENTRY.find(lines[index])?.takeIf { it.groupValues[1] == "flavor" }?.let { unquote(withoutComment(it.groupValues[2])) } == "yaml" }
            }
        }

        if (FIRST_NODES.containsMatchIn(lines[first])) {
            return true
        }

        val option = OPTION_LINE.find(lines[first])?.groupValues?.get(1) in DOCUMENT_OPTIONS
        val nodes = lines.indexOfFirst { YAML_NODES.containsMatchIn(it) }

        if (!option && nodes < 0) {
            return false
        }

        // Not YAML at all: markdown, unless it starts as a YAML map does, with a mistake further down.
        if (!readsAsYaml(lines)) {
            return option && nodes >= 0
        }

        return option || listsNodes(lines, nodes)
    }

    /**
     * Whether `lines` read as YAML, as far as their top level tells: each
     * line there is a key (`title: Biology`, `nodes:`), a comment, or an item
     * of a list right under a key with nothing after it, and the lines
     * indented under them are their values. Markdown's paragraphs and lists
     * aren't, nor is a document's start (`---`) past its first line.
     */
    private fun readsAsYaml(lines: List<String>): Boolean {
        var listing = false
        var started = false

        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                continue
            }

            if (!started && line.trimEnd() == "---") {
                started = true
                continue
            }

            started = true

            if (line.first().isWhitespace()) {
                continue
            }

            if (YAML_DASH.containsMatchIn(line)) {
                if (!listing) {
                    return false
                }

                continue
            }

            val entry = YAML_ENTRY.find(line) ?: return false
            listing = withoutComment(entry.groupValues[2].trim()).isEmpty()
        }

        return true
    }

    /** Whether a YAML map's `nodes:`, on the line at `index`, is a list: written on its line (`[…]`), or as the items under it. */
    private fun listsNodes(lines: List<String>, index: Int): Boolean {
        if (index < 0) {
            return false
        }

        val value = withoutComment(lines[index].substringAfter(':').trim())

        if (value.isNotEmpty()) {
            return value.startsWith("[")
        }

        val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("#") } ?: return false
        return YAML_DASH.containsMatchIn(next.trimStart())
    }

    /**
     * Whether a YAML map holds anything of a map, as [hasContent] tells of
     * markdown: a value among its nodes beyond their structure, as a named
     * idea, a note or any attribute has. Their keys, their `id`s and empty
     * values (`""`, `~`, `null`, `[]`, `{}`) don't count, nor does a `nodes:`
     * that isn't a list, nor do the map's options beside it, as a
     * frontmatter's don't.
     */
    private fun yamlHasContent(lines: List<String>): Boolean {
        val start = lines.indexOfFirst { YAML_NODES.containsMatchIn(it) }

        if (start < 0) {
            return false
        }

        val nodes = withoutComment(lines[start].substringAfter(':').trim())

        if (nodes.isNotEmpty() && !nodes.startsWith("[")) {
            return false
        }

        if (flowHolds(nodes)) {
            return true
        }

        // A flow list or mapping that goes on over the next lines: how many of its brackets are still open.
        var open = brackets(nodes)
        // A block scalar's key's column (`note: |`): the lines indented further are its value, which counts unless it's an id's.
        var scalar: Int? = null
        var scalarCounts = false

        for (line in lines.drop(start + 1)) {
            val indent = line.length - line.trimStart().length

            if (scalar != null && (line.isBlank() || indent > scalar)) {
                if (scalarCounts && line.isNotBlank()) {
                    return true
                }

                continue
            }

            scalar = null

            if (line.isBlank()) {
                continue
            }

            if (open > 0) {
                if (flowHolds(line)) {
                    return true
                }

                open += brackets(line)
                continue
            }

            if (line.trimStart().startsWith("#")) {
                continue
            }

            // The next key at the top: the nodes are over.
            if (indent == 0 && !YAML_DASH.containsMatchIn(line)) {
                break
            }

            var item = line.trimStart()
            var column = indent

            while (true) {
                val dash = YAML_DASH.find(item) ?: break
                column += dash.value.length
                item = item.substring(dash.value.length)
            }

            val entry = YAML_ENTRY.find(item)
            val key = entry?.groupValues?.get(1)?.let(::unquote)
            val value = withoutComment((entry?.groupValues?.get(2) ?: item).trim())

            when {
                BLOCK_SCALAR.matches(value) -> {
                    scalar = column
                    scalarCounts = key != "id"
                }
                key == "id" -> Unit
                holds(value) -> return true
                else -> open = brackets(value)
            }
        }

        return false
    }

    /** Whether a YAML value, as written on its line, holds anything: not empty, `~` or `null`, nor a string of spaces, nor a list or mapping of nothing. */
    private fun holds(value: String): Boolean = when {
        value.isEmpty() || value == "~" || value == "null" -> false
        value.startsWith("[") || value.startsWith("{") -> flowHolds(value)
        value.startsWith("\"") || value.startsWith("'") -> quotedHolds(value)
        else -> true
    }

    /** Whether a quoted YAML string holds anything but spaces. One that doesn't close on its line goes on over the next: it's taken to. */
    private fun quotedHolds(value: String): Boolean {
        val end = quoteEnd(value, 0)
        val closed = end > 1 && value[end - 1] == value.first()
        return !closed || value.substring(1, end - 1).isNotBlank()
    }

    /**
     * Whether a flow list or mapping (`[…]`, `{…}`), or a line of one that
     * goes on over several, holds a value: one that isn't empty, nor an
     * `id`'s. A mapping's keys never count.
     */
    private fun flowHolds(text: String): Boolean {
        // For each bracket open, whether it's a mapping's, where a key comes first and then its value.
        val mappings = ArrayDeque<Boolean>()
        var key = false
        var ofId = false
        var index = 0

        while (index < text.length) {
            val character = text[index]

            when {
                character.isWhitespace() -> index += 1
                character == '{' || character == '[' -> {
                    mappings.addLast(character == '{')
                    key = character == '{'
                    index += 1
                }
                character == '}' || character == ']' -> {
                    mappings.removeLastOrNull()
                    key = false
                    index += 1
                }
                character == ',' -> {
                    key = mappings.lastOrNull() == true
                    ofId = false
                    index += 1
                }
                character == ':' -> {
                    key = false
                    index += 1
                }
                // A comment, to the end of the line.
                character == '#' && index > 0 && text[index - 1].isWhitespace() -> return false
                else -> {
                    val end = if (character == '"' || character == '\'') quoteEnd(text, index) else plainEnd(text, index, key)
                    val written = text.substring(index, end).trim()

                    if (key) {
                        ofId = unquote(written) == "id"
                    } else if (!ofId && holds(written)) {
                        return true
                    }

                    index = end
                }
            }
        }

        return false
    }

    /** Where a quoted string in a flow value, starting at `start`, ends: just past its closing quote, or at the end of the text. */
    private fun quoteEnd(text: String, start: Int): Int {
        val quote = text[start]
        var index = start + 1

        while (index < text.length) {
            if (quote == '"' && text[index] == '\\') {
                index += 2
                continue
            }

            if (text[index] == quote && quote == '\'' && text.getOrNull(index + 1) == '\'') {
                index += 2
                continue
            }

            if (text[index] == quote) {
                return index + 1
            }

            index += 1
        }

        return text.length
    }

    /** Where a plain value in a flow value, starting at `start`, ends: at a comma or a closing bracket, a comment, or for a `key`, its colon. */
    private fun plainEnd(text: String, start: Int, key: Boolean): Int {
        var index = start

        while (index < text.length) {
            val character = text[index]
            val next = text.getOrNull(index + 1)

            if (character == ',' || character == ']' || character == '}') {
                return index
            }

            if (key && character == ':' && (next == null || next.isWhitespace() || next in ",]}")) {
                return index
            }

            if (character == '#' && index > start && text[index - 1].isWhitespace()) {
                return index
            }

            index += 1
        }

        return index
    }

    /** How many more flow brackets (`[`, `{`) `text` opens than it closes, outside quotes. */
    private fun brackets(text: String): Int {
        var open = 0
        var index = 0

        while (index < text.length) {
            val character = text[index]

            if (opensQuote(text, index)) {
                index = quoteEnd(text, index)
                continue
            }

            if (character == '[' || character == '{') {
                open += 1
            } else if (character == ']' || character == '}') {
                open -= 1
            }

            index += 1
        }

        return open
    }

    /** Whether a quote at `index` opens a quoted string, as YAML has it: at a value's start, or after a space or `[`, `{`, `,`, `:`. "It's" is plain text. */
    private fun opensQuote(text: String, index: Int): Boolean =
        (text[index] == '"' || text[index] == '\'') && (index == 0 || text[index - 1] in " \t[{,:")

    /**
     * A YAML value without the comment after it (` # …`), as Orbit reads
     * one: a `#` that starts the value, or comes inside its quotes, is the
     * value's own (`color: #FF6B6B`).
     */
    private fun withoutComment(value: String): String {
        var quote: Char? = null
        var index = 0

        while (index < value.length) {
            val character = value[index]

            if (quote != null) {
                if (character == '\\' && quote == '"') {
                    index += 1
                } else if (character == quote && quote == '\'' && value.getOrNull(index + 1) == '\'') {
                    index += 1
                } else if (character == quote) {
                    quote = null
                }
            } else if (opensQuote(value, index)) {
                quote = character
            } else if (character == '#' && index > 0 && value[index - 1].isWhitespace()) {
                return value.substring(0, index).trimEnd()
            }

            index += 1
        }

        return value
    }

    /**
     * A YAML map's nodes, as [scan] reads a markdown map's: each item of
     * `nodes:` and of its `children:`, named by its `text:` (or by the item
     * itself, `- Chlorophyll`), under the item it's indented under, with the
     * attributes that count its progress. Several at the top share a center
     * named by the map's `title:`, as Orbit makes one up.
     */
    private fun scanYaml(lines: List<String>): Scan {
        val start = lines.indexOfFirst { YAML_NODES.containsMatchIn(it) }
        val nodes = mutableListOf<ScannedNode>()
        // The items a line may belong to, by the indentation of their dash.
        val items = ArrayDeque<Pair<Int, ScannedNode>>()

        for (line in lines.drop(start + 1)) {
            if (line.isBlank()) {
                continue
            }

            val indent = line.length - line.trimStart().length

            if (indent == 0) {
                // The next key at the top: the nodes are over.
                break
            }

            val item = YAML_ITEM.find(line)

            if (item != null) {
                while (items.isNotEmpty() && items.last().first >= indent) {
                    items.removeLast()
                }

                val key = YAML_KEY.find(item.groupValues[2])
                val name = if (key == null) unquote(item.groupValues[2]) else if (key.groupValues[2] == "text") unquote(key.groupValues[3]) else ""
                val node = ScannedNode("node-${nodes.size}", items.lastOrNull()?.second?.id, name)
                key?.takeIf { it.groupValues[2] != "text" }?.let { node.read(mapOf(it.groupValues[2] to unquote(it.groupValues[3]))) }
                nodes += node
                items.addLast(indent to node)
                continue
            }

            // A key of the deepest item the line is indented under: its text, when it came after another key, or what it counts.
            val key = YAML_KEY.find(line) ?: continue
            val owner = items.lastOrNull { it.first < indent }?.second ?: continue

            if (key.groupValues[2] == "text" && owner.name.isEmpty()) {
                owner.name = unquote(key.groupValues[3])
            } else {
                owner.read(mapOf(key.groupValues[2] to unquote(key.groupValues[3])))
            }
        }

        val top = nodes.filter { it.parentId == null }

        if (top.size == 1) {
            return Scan(top.single().name, nodes.count { it.parentId == top.single().id }, nodes.size - 1, nodes)
        }

        // Several at the top, or none: a center of their own, named by the map's title.
        val center = ScannedNode("center", null, "")
        top.forEach { it.parentId = center.id }
        return Scan(null, top.size, nodes.size, listOf(center) + nodes)
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

    /** An idea's name as it reads: without its attributes, checkbox, link targets or emphasis. */
    private fun name(text: String): String =
        text.replace(ATTRIBUTE_COMMENT, "")
            .replace(CHECKBOX, "")
            .replace(LINK) { it.groupValues[1] }
            .replace(WRAPPING_EMPHASIS, "")
            .replace("\\", "")
            .trim()

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
