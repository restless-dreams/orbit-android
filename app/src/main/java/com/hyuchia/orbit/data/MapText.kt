package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.map.GOAL_ATTRIBUTES
import com.hyuchia.orbit.map.MapProgress
import com.hyuchia.orbit.map.Rollup
import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.BreakIterator
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

/** A line of an attribute block, or one attribute of a line's comment: `target: 5`. */
private val ATTRIBUTE = Regex("""^\s*([A-Za-z][\w-]*)\s*:\s*+($TEXT_BEFORE_SPACES)\s*$""")

/** An HTML comment's start and end, as a line has them: `<!-- … -->`. */
private const val COMMENT_OPEN = "<!--"
private const val COMMENT_CLOSE = "-->"

/** The word that starts a comment of a node's attributes: `<!-- orbit status: done -->`. */
private const val ORBIT = "orbit"

/** An attribute comment on a line of its own, under its node. */
private val ATTRIBUTE_LINE = Regex("""^\s*<!--\s*orbit\b(.*?)-->\s*$""")
private val CHECKBOX = Regex("""^\[([ xX/])]\s+""")
private val EMPHASIS = Regex("""[*_~`]+""")
/** Emphasis around words, as an idea's name has it: underscores inside a word (`copy_versions`) are the name's own. */
private val WRAPPING_EMPHASIS = Regex("""[*~`]+|(?<!\w)_+|_+(?!\w)""")
/** A heading's or list item's marker with nothing after it. */
private val BARE_MARKER = Regex("""#{1,6}|[-*+]|\d+[.)]""")

/** A tag as a hashtag at the end of an idea's name writes it, without its `#`: `web`, `sprint-3`, `team/web`. */
private val HASHTAG = Regex("""[\p{L}\p{N}_][\p{L}\p{N}_/-]*""")

/** The attributes a light reading reads: a node's status, what makes it a goal or a Progress idea and a goal's numbers, and its date. */
private val READ_ATTRIBUTES = setOf("status", "type", "due") + GOAL_ATTRIBUTES

/** A type with the version of its fields it was written for, as Orbit reads one: `goal@2`. */
private val VERSIONED_TYPE = Regex("""^(.*\S)@(\d+)$""")

/** The key a YAML node's note is under. */
private const val NOTE = "note"

/** The keys a YAML node's children are under, as Orbit reads them: `children`, or another it takes for it when that one isn't there. */
private val YAML_CHILDREN = listOf("children", "items", "nodes", "subnodes")

/**
 * An idea as a light reading of a map's text has it: its name, and where it
 * sits (its parent's place in the list; none for the map's center). A center
 * no one wrote is `madeUp`: Orbit makes one up for a YAML map with several
 * ideas at its top, or none, named by the map's title.
 */
data class TextIdea(val name: String, val parent: Int?, val madeUp: Boolean = false)

/** A YAML flow value (`[…]`, `{…}`) as a light reading has it: a list, a mapping, or a scalar's text. */
private sealed interface Flow {
    class Scalar(val text: String) : Flow

    class Sequence(val items: List<Flow>) : Flow

    class Mapping(val entries: Map<String, Flow>) : Flow
}

/** A node as the outline reads it: its name, where it sits, and the attributes that count its progress. A center Orbit makes up is `madeUp`. */
private class ScannedNode(val id: String, var parentId: String?, var name: String, val madeUp: Boolean = false) {
    var status: String? = null

    /** Its note's lines, as a YAML map writes it (`note:`), for Home's search. */
    var note: List<String> = emptyList()

    val attributes = mutableMapOf<String, String>()

    fun read(attributes: Map<String, String>) {
        for ((key, value) in attributes) {
            if (key in READ_ATTRIBUTES) {
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
            due = attributes["due"],
            // A type's version, as Orbit reads it (`goal@2`): a goal or Progress written for a newer one counts as an idea like any
            // other (MapNode.isGoal), and so does one for a version past what an Int holds.
            type = versioned?.groupValues?.get(1) ?: type,
            typeVersion = versioned?.groupValues?.get(2)?.let { it.toIntOrNull() ?: Int.MAX_VALUE },
            attrs = JsonObject(attributes.filterKeys { it in GOAL_ATTRIBUTES }.mapValues { JsonPrimitive(it.value) }),
        )
    }
}

/**
 * An idea with a date, as a light reading of a map's text finds it: what
 * reminders come from, read without a page.
 */
data class DatedIdea(
    /** Its name as it reads, its icon included. */
    val name: String,

    /** Where it is: its place among its parent's ideas at each level, from the center's (none for the center itself). */
    val path: List<Int>,

    val date: IdeaDate,

    /** Its status, which says whether it's done: a done idea doesn't remind. */
    val status: String?,
)

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
        val scan = if (yaml) scanYaml(lines, titleOf(options)) else scan(lines, frontmatter.bodyStart)
        val mapNodes = scan.nodes.map { it.toMapNode() }
        val fallback = if (yaml) null else lines.drop(frontmatter.bodyStart).firstOrNull { it.isNotBlank() }?.trim()
        return MapOutline(
            title = titleOf(options) ?: scan.title?.ifBlank { null } ?: fallback ?: words.string(R.string.common_untitled_map),
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
        val nodes = (if (isYaml(lines)) scanYaml(lines, titleOf(yamlKeys(lines))) else scan(lines, frontmatter(lines).bodyStart)).nodes
        val places = nodes.withIndex().associate { (index, node) -> node.id to index }
        return nodes.map { TextIdea(it.name, it.parentId?.let(places::get), it.madeUp) }
    }

    /**
     * Every idea in the map's text with a date Orbit reads (its `due`), with
     * its name, its place and its status, in the order the text has them: the
     * same light reading as [outline]. A `due` Orbit can't read isn't a date.
     */
    fun dates(markdown: String): List<DatedIdea> {
        val lines = markdown.lines()
        val nodes = (if (isYaml(lines)) scanYaml(lines, titleOf(yamlKeys(lines))) else scan(lines, frontmatter(lines).bodyStart)).nodes
        val paths = mutableMapOf<String, List<Int>>()
        val children = mutableMapOf<String, Int>()
        val dated = mutableListOf<DatedIdea>()

        // Each node comes after its parent, and after the siblings before it.
        for (node in nodes) {
            val parent = node.parentId
            val path = if (parent == null) emptyList() else paths[parent].orEmpty() + children.getOrDefault(parent, 0)

            if (parent != null) {
                children[parent] = children.getOrDefault(parent, 0) + 1
            }

            paths[node.id] = path
            IdeaDate.parse(node.attributes["due"])?.let { dated += DatedIdea(node.name, path, it, node.status) }
        }

        return dated
    }

    /** The title a map's options give it: one that's a YAML list or map (or the start of one) isn't a name. */
    private fun titleOf(options: Map<String, String>): String? = options["title"]?.takeUnless { it.startsWith("[") || it.startsWith("{") }

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
            orbitComment(text)?.let { node.read(inlineAttributes(it)) }
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

    /**
     * The map's words without markup, a line per node or paragraph, to search
     * in. A YAML map's are its ideas' names and notes (see [yamlSearchText]).
     */
    fun searchText(markdown: String): String {
        val lines = markdown.lines()

        if (isYaml(lines)) {
            return yamlSearchText(lines)
        }

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
     * A YAML map's words to search in, as [searchText] has a markdown map's:
     * each idea's name, then its note's lines, a line each, without markup.
     * Never the words of its structure, its keys (`children`, `text`,
     * `status`), nor values no one writes to be read, such as a status, a
     * color or a picture's address: every YAML map would match on those.
     */
    private fun yamlSearchText(lines: List<String>): String {
        val words = StringBuilder()

        for (node in YamlNodes(lines).read()) {
            for (line in listOf(node.name) + node.note) {
                clean(line).takeIf { it.isNotEmpty() }?.let { words.append(it).append('\n') }
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

            rest = withoutComments(rest)
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

    /**
     * `text` cut to at most `length` characters at a word's end, with "…" when it was cut, as the pages shorten
     * names (`shorten` in web/paste.ts): both hold to the cases in the tests' shortened-names.json.
     */
    fun shorten(text: String, length: Int): String {
        val single = text.replace(Regex("""\s+"""), " ").trim()

        if (single.length <= length) {
            return single
        }

        // Never through an emoji, a flag or a letter with its accent: only whole characters, as people see them
        // (Unicode's grapheme clusters, an emoji with its skin tone or a family joined by zero-width joiners included).
        val characters = BreakIterator.getCharacterInstance().also { it.setText(single) }
        val end = if (characters.isBoundary(length - 1)) length - 1 else characters.preceding(length - 1).coerceAtLeast(0)
        val cut = single.substring(0, end)
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

            val attributes = commentsIn(line).firstOrNull()?.let(line::substring).orEmpty()

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

    /** Whether a map's text is YAML, as Orbit tells one from markdown (see the reading of its lines below). */
    fun isYaml(text: String): Boolean = isYaml(text.lines())

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
     * attributes that count its progress; written in flow style too, as
     * `children: [Stamens, { text: Petals, status: done }]` or a whole item
     * in braces, on one line or over several (see [YamlNodes]). Several at the
     * top share a center named by the map's `title`, as Orbit makes one up: a
     * center no one wrote, which [VersionChanges] tells apart.
     */
    private fun scanYaml(lines: List<String>, title: String?): Scan {
        val nodes = YamlNodes(lines).read()
        val top = nodes.filter { it.parentId == null }

        if (top.size == 1) {
            return Scan(top.single().name, nodes.count { it.parentId == top.single().id }, nodes.size - 1, nodes)
        }

        // Several at the top, or none: a center of their own, named by the map's title, as Orbit makes one up.
        val center = ScannedNode("center", null, title.orEmpty(), madeUp = true)
        top.forEach { it.parentId = center.id }
        return Scan(null, top.size, nodes.size, listOf(center) + nodes)
    }

    /**
     * A YAML map's nodes, read line by line from its `nodes:`: the items of
     * that list and of each node's `children:`, by their dashes' indentation,
     * and the values written in flow style, read whole, each with its note
     * for Home's search. What a node's other keys hold (a list of tags, a
     * picture's fields) is passed over, as are lists in the list, which Orbit
     * takes for ideas without a name, and the lines after them. Each line is
     * read once.
     */
    private class YamlNodes(private val lines: List<String>) {
        private val nodes = mutableListOf<ScannedNode>()

        /** The items a line may belong to, by the indentation of their dash. */
        private val items = ArrayDeque<Pair<Int, ScannedNode>>()

        /** The column of a key whose value holds no ideas: the lines indented further are its, and so are items at its column right under it. */
        private var skipping: Int? = null

        /** The next line to read. */
        private var next = 0

        fun read(): List<ScannedNode> {
            val start = lines.indexOfFirst { YAML_NODES.containsMatchIn(it) }

            if (start < 0) {
                return nodes
            }

            next = start + 1
            val listed = withoutComment(lines[start].substringAfter(':').trim())

            // The list of nodes on its own line, `nodes: […]`, which may go on over the next ones.
            if (listed.startsWith("[")) {
                (flow(start, listed) as? Flow.Sequence)?.items?.forEach { idea(it, null) }
            }

            while (next < lines.size) {
                val line = lines[next]
                next += 1

                if (line.isBlank()) {
                    continue
                }

                val indent = line.length - line.trimStart().length
                val dash = YAML_DASH.containsMatchIn(line.trimStart())
                val passing = skipping

                if (passing != null) {
                    if (indent > passing || indent == passing && dash) {
                        continue
                    }

                    skipping = null
                }

                if (line.trimStart().startsWith("#")) {
                    continue
                }

                // The next key at the top: the nodes are over.
                if (indent == 0 && !dash) {
                    break
                }

                val item = YAML_ITEM.find(line)

                if (item != null) {
                    item(item, indent)
                    continue
                }

                // A key of the deepest item the line is indented under.
                val key = YAML_KEY.find(line) ?: continue
                val owner = items.lastOrNull { it.first < indent }?.second ?: continue
                key(owner, key.groupValues[2], key.groupValues[3], indent)
            }

            return nodes
        }

        /** An item of a list of nodes, indented `indent`: an idea under the item it's indented under, or at the top. */
        private fun item(item: MatchResult, indent: Int) {
            while (items.isNotEmpty() && items.last().first >= indent) {
                items.removeLast()
            }

            val parent = items.lastOrNull()?.second
            val content = item.groupValues[2]
            val value = withoutComment(content)
            val key = YAML_KEY.find(content)

            val node = when {
                // A whole idea in braces.
                value.startsWith("{") -> idea(flow(next - 1, value), parent)
                // A list in the list: Orbit takes it for an idea without a name, and what's in it for nothing.
                value.startsWith("[") -> add(parent, "").also { flow(next - 1, value) }
                key == null -> add(parent, unquote(value))
                else -> add(parent, "").also { key(it, key.groupValues[2], key.groupValues[3], item.groups[2]!!.range.first) }
            }

            items.addLast(indent to node)
        }

        /** A key of `owner`'s, at `column`: its text, its children written in flow style, what it counts, or a value with no ideas in it. */
        private fun key(owner: ScannedNode, key: String, written: String, column: Int) {
            val value = withoutComment(written.trim())

            when {
                key == "text" && BLOCK_SCALAR.matches(value) -> block(column).let { text -> if (owner.name.isEmpty()) owner.name = text }
                key == "text" -> if (owner.name.isEmpty()) owner.name = unquote(value)
                key == NOTE && BLOCK_SCALAR.matches(value) -> owner.note = blockLines(column)
                key == NOTE && value.isNotEmpty() && !value.startsWith("[") && !value.startsWith("{") -> owner.note = listOf(unquote(value))
                key in YAML_CHILDREN && value.startsWith("[") -> (flow(next - 1, value) as? Flow.Sequence)?.items?.forEach { idea(it, owner) }
                // Its children, as the items under it.
                key in YAML_CHILDREN && value.isEmpty() -> Unit
                // Another key's list or mapping, or a block of text.
                value.isEmpty() || BLOCK_SCALAR.matches(value) -> skipping = column
                value.startsWith("[") || value.startsWith("{") -> flow(next - 1, value)
                else -> owner.read(mapOf(key to unquote(value)))
            }
        }

        /** The idea a flow value is, under `parent`, with those under it: a mapping's `text` and `children`, a scalar's text, or a list's nothing. */
        private fun idea(value: Flow?, parent: ScannedNode?): ScannedNode {
            if (value !is Flow.Mapping) {
                return add(parent, (value as? Flow.Scalar)?.text.orEmpty())
            }

            val node = add(parent, (value.entries["text"] as? Flow.Scalar)?.text.orEmpty())
            node.read(value.entries.mapNotNull { (key, entry) -> (entry as? Flow.Scalar)?.let { key to it.text } }.toMap())
            (value.entries[NOTE] as? Flow.Scalar)?.let { note -> node.note = listOf(note.text) }
            YAML_CHILDREN.firstNotNullOfOrNull { value.entries[it] as? Flow.Sequence }?.items?.forEach { idea(it, node) }
            return node
        }

        private fun add(parent: ScannedNode?, name: String): ScannedNode = ScannedNode("node-${nodes.size}", parent?.id, name).also { nodes += it }

        /**
         * A flow value that starts on line `at` with `first` and goes on over
         * the lines after it until its brackets close, read whole; the lines it
         * takes are passed.
         */
        private fun flow(at: Int, first: String): Flow? {
            var open = brackets(first)
            var last = at
            val text = StringBuilder(first)

            while (open > 0 && last + 1 < lines.size) {
                last += 1
                val line = lines[last].trim()

                // A comment on a line of its own.
                if (line.startsWith("#")) {
                    continue
                }

                val part = withoutComment(line)
                text.append(' ').append(part)
                open += brackets(part)
            }

            next = maxOf(next, last + 1)
            return FlowReader(text.toString()).value()
        }

        /** A block of text under a key at `column` (`text: |`): its lines, one after another, which are passed. */
        private fun block(column: Int): String = blockLines(column).joinToString(" ")

        /** A block of text's lines under a key at `column` (`note: |`), each trimmed, without blank ones, which are passed. */
        private fun blockLines(column: Int): List<String> {
            val block = mutableListOf<String>()

            while (next < lines.size) {
                val line = lines[next]

                if (line.isNotBlank() && line.length - line.trimStart().length <= column) {
                    break
                }

                if (line.isNotBlank()) {
                    block += line.trim()
                }

                next += 1
            }

            return block
        }
    }

    /**
     * Reads a YAML flow value in one pass: lists, mappings, and scalars,
     * quoted or plain, unquoted. What doesn't close ends with the text.
     */
    private class FlowReader(private val text: String) {
        private var index = 0

        fun value(): Flow? {
            spaces()

            if (index >= text.length) {
                return null
            }

            return when (text[index]) {
                '[' -> sequence()
                '{' -> mapping()
                ']', '}', ',' -> null
                else -> scalar(key = false)
            }
        }

        private fun sequence(): Flow.Sequence {
            index += 1
            val items = mutableListOf<Flow>()

            while (true) {
                spaces()

                if (index >= text.length) {
                    break
                }

                when (text[index]) {
                    ']' -> {
                        index += 1
                        break
                    }
                    ',', '}' -> index += 1
                    else -> items += value() ?: break
                }
            }

            return Flow.Sequence(items)
        }

        private fun mapping(): Flow.Mapping {
            index += 1
            val entries = LinkedHashMap<String, Flow>()

            while (true) {
                spaces()

                if (index >= text.length) {
                    break
                }

                when (text[index]) {
                    '}' -> {
                        index += 1
                        break
                    }
                    ',', ']' -> index += 1
                    else -> {
                        val key = key()
                        spaces()

                        val entry = if (index < text.length && text[index] == ':') {
                            index += 1
                            value() ?: Flow.Scalar("")
                        } else {
                            Flow.Scalar("")
                        }

                        entries[key] = entry
                    }
                }
            }

            return Flow.Mapping(entries)
        }

        /** A mapping's key: a scalar, as a list or mapping as a key holds nothing a map reads. */
        private fun key(): String {
            if (text[index] == '[' || text[index] == '{') {
                value()
                return ""
            }

            return scalar(key = true).text
        }

        /** A scalar from here: quoted, or plain up to a comma, a closing bracket or, for a `key`, its colon. */
        private fun scalar(key: Boolean): Flow.Scalar {
            val start = index
            index = if (text[index] == '"' || text[index] == '\'') quoteEnd(text, index) else plainEnd(text, index, key)

            // A plain value never stops before its first character: only a key does, at its colon.
            if (index == start && !key) {
                index += 1
            }

            return Flow.Scalar(unquote(text.substring(start, index)))
        }

        private fun spaces() {
            while (index < text.length && text[index].isWhitespace()) {
                index += 1
            }
        }
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

    /** An idea's name as it reads: without its attributes, checkbox, tags, link targets or emphasis. */
    private fun name(text: String): String =
        linkWords(withoutHashtags(withoutComments(text).replace(CHECKBOX, "")))
            .replace(WRAPPING_EMPHASIS, "")
            .replace("\\", "")
            .trim()

    /**
     * `text` without the hashtags at its end, which Orbit reads as the idea's
     * tags rather than its name: each after a space, and with a letter in it
     * (`#web`, `#sprint-3`), so `C#` and `Fix #42` stay as written. Each is
     * found from the end, so a long line is read once for each.
     */
    private fun withoutHashtags(text: String): String {
        var rest = text.trimEnd()

        while (true) {
            val hash = rest.lastIndexOf('#')
            val tag = rest.substring(hash + 1)

            if (hash <= 0 || !rest[hash - 1].isWhitespace() || !HASHTAG.matches(tag) || tag.none(Char::isLetter)) {
                return rest
            }

            rest = rest.substring(0, hash).trimEnd()
        }
    }

    private fun clean(title: String): String =
        linkWords(withoutComments(title).replace(CHECKBOX, ""))
            .replace(EMPHASIS, "")
            .replace("\\", "")
            .trim()

    /**
     * Where the line's HTML comments are (`<!-- … -->`), each with the spaces
     * right before it, first to last. Read in one pass: a `<!--` with no
     * `-->` after it starts none, and nor can any after it, so a line of
     * thousands of them is read once, rather than once from each.
     */
    internal fun commentsIn(text: String): Sequence<IntRange> = sequence {
        var from = 0

        while (true) {
            val open = text.indexOf(COMMENT_OPEN, from)
            val close = if (open < 0) -1 else text.indexOf(COMMENT_CLOSE, open + COMMENT_OPEN.length)

            if (close < 0) {
                break
            }

            var start = open

            while (start > from && text[start - 1].isWhitespace()) {
                start -= 1
            }

            yield(start until close + COMMENT_CLOSE.length)
            from = close + COMMENT_CLOSE.length
        }
    }

    /** The line without its HTML comments, nor the spaces right before each (see [commentsIn]). */
    internal fun withoutComments(text: String): String {
        val kept = StringBuilder()
        var written = 0

        for (comment in commentsIn(text)) {
            kept.append(text, written, comment.first)
            written = comment.last + 1
        }

        return kept.append(text, written, text.length).toString()
    }

    /**
     * What the line's first `<!-- orbit … -->` comment holds: its attributes,
     * ` status: done, priority: 1 `. Null when it has none. Read in one pass,
     * as [commentsIn] reads them: a comment that isn't Orbit's is passed by.
     */
    internal fun orbitComment(text: String): String? {
        var from = 0
        var close = -1

        while (true) {
            val open = text.indexOf(COMMENT_OPEN, from)

            if (open < 0) {
                return null
            }

            if (close < open + COMMENT_OPEN.length) {
                close = text.indexOf(COMMENT_CLOSE, open + COMMENT_OPEN.length)
            }

            if (close < 0) {
                return null
            }

            var word = open + COMMENT_OPEN.length

            while (word < close && text[word].isWhitespace()) {
                word += 1
            }

            val after = word + ORBIT.length

            // `orbit` as a word of its own: what comes after it, inside the comment, isn't a letter, a digit or `_`.
            if (after <= close && text.startsWith(ORBIT, word) && !text[after].isLetterOrDigit() && text[after] != '_') {
                return text.substring(after, close)
            }

            from = open + 1
        }
    }

    /**
     * Where the line's links written in markdown are (`[words](address)`),
     * first to last, each from its `[` to its `)`: from a `[` to the first
     * `]` after it, with `(` right after that, to the first `)`. Read in one
     * pass: a `[` with no `]` after it, or a `](` with no `)` after it,
     * starts nothing, and nor can any after it, so a line of thousands of
     * `[` is read once, rather than once from each (seconds for 20,000).
     */
    internal fun links(text: String): Sequence<IntRange> = sequence {
        var from = 0
        var close = -1
        var paren = -1

        while (true) {
            val open = text.indexOf('[', from)

            if (open < 0) {
                break
            }

            if (close < open) {
                close = text.indexOf(']', open)
            }

            if (close < 0) {
                break
            }

            if (close + 1 >= text.length || text[close + 1] != '(') {
                from = open + 1
                continue
            }

            if (paren < close + 2) {
                paren = text.indexOf(')', close + 2)
            }

            if (paren < 0) {
                break
            }

            yield(open..paren)
            from = paren + 1
        }
    }

    /**
     * The line with each link and picture written in markdown (`[words](address)`,
     * `![words](address)`) as its words alone, the links as [links] finds
     * them.
     */
    internal fun linkWords(text: String): String {
        val words = StringBuilder()
        var written = 0

        for (link in links(text)) {
            // A picture's `!` goes with it, unless the link before it ends there.
            val start = if (link.first > written && text[link.first - 1] == '!') link.first - 1 else link.first
            words.append(text, written, start).append(text, link.first + 1, text.indexOf(']', link.first))
            written = link.last + 1
        }

        return words.append(text, written, text.length).toString()
    }

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
