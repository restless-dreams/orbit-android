package com.hyuchia.orbit.data

/**
 * Where a map's text has code, as Orbit's own reader finds it: what a note
 * or a title shows as written, so a `media/…` in it is only words, and the
 * map shows no file there ([MediaNames.withoutCode]). It follows the
 * library's parser (`parser-markdown.ts`) and its note reader
 * (`note-markdown.ts`), rule for rule:
 *
 * - **The map as a whole** is read as the parser reads it: without a
 *   byte-order mark at its start, and without the indentation every line
 *   with text shares, so a map indented as a whole reads as one written
 *   flush left, and its titles' code is code.
 * - **Fenced blocks:** in a markdown map, a line of ``` or ~~~ at most three
 *   spaces past where its level starts (an idea's note is its lines indented
 *   under it, as far in as the least indented of them), to the next line of
 *   that level that starts with the same, or, never closed, to the level's
 *   end. A fence indented further is text, and a picture on a line of its
 *   own in it is the idea's. A quote's lines are a note's, read without
 *   their `>`, where a fence at any indent is code to its closing line or
 *   the quote's end. In a YAML map, a note is YAML's block of text, and a
 *   fence in it is code at any indent, to its closing line or the note's
 *   end.
 * - **A note's blocks,** as the note reader reads them: a paragraph, to a
 *   blank line, a line that starts another block, or a table; a heading; a
 *   list item, with the lines indented under it that go on with its text; a
 *   table's cells, each alone, its header's and its rows'. A quote's lines
 *   are a note of their own, read without their `>`. In a YAML map, a note
 *   is a value: YAML's block of text, folded or not, a quoted text, its
 *   `\n`s its lines, or plain text going on over the lines under its key.
 *   What a note shows only as written is code too: a table's cells past its
 *   header's, and the lines after a list under a list item.
 * - **Code spans in a note,** read a block at a time, across its lines,
 *   from left to right as the note reader's one pattern reads them: a run
 *   of backticks is code to the first run of as many after it, even within
 *   a longer run (or, with none, as many fewer as find one); a picture, a
 *   link, a web address in angle brackets, or bold, struck or emphasized
 *   words that start first aren't code, and their own words are read the
 *   same way inside them, so a backtick there pairs only with another there.
 * - **Code spans in a title,** a list item's or a heading's, as a title
 *   reads them: a run of backticks to the next run exactly as long, before
 *   anything else on the line; one after a backslash is a backtick.
 * - **Never code:** an HTML comment, as an idea's attributes are; a picture
 *   or link on a line of its own, which is the idea's; and a fence around
 *   the whole map, which Orbit takes away as it reads it.
 *
 * Each character is looked at a bounded number of times, with searches for
 * what closes a span by binary search, so a long or deep map costs about one
 * pass, however its lines are made.
 */
internal object MapCode {
    /** Where `markdown` has code, in its order. */
    fun ranges(markdown: String): List<IntRange> {
        // A fence around the whole map is Orbit's way of carrying one in another document: what's inside is the map.
        val body = WHOLE_MAP_FENCE.find(markdown)?.groups?.get(3)?.range ?: markdown.indices
        val lines = Lines(markdown, body)
        // Orbit tells YAML from markdown once it took away the indentation every line shares, as it reads the lines.
        val yaml = MapText.isYaml(if (lines.dedented) lines.text() else markdown)
        val fenced = if (yaml) yamlFences(lines) else MarkdownBlocks(lines).fences()
        val ranges = mutableListOf<IntRange>()
        val inFence = BooleanArray(lines.size)

        for (fence in fenced) {
            ranges += lines.start(fence.first) until lines.end(fence.last)

            for (line in fence) {
                inFence[line] = true
            }
        }

        Spans(markdown, lines, inFence, yaml, ranges).read()
        return ranges.sortedBy { it.first }
    }

    /**
     * The lines of `markdown` within `body`, each without its line break, as
     * Orbit reads them: without a byte-order mark at the map's start, and
     * without the indentation every line with text shares, the same spaces
     * and tabs in the same order (`normalizeSource`), so a map indented as a
     * whole, as inside HTML or a template, reads as one written flush left.
     * A blank line loses as much of that as it has. Each line still starts
     * and ends where it does in `markdown`.
     */
    private class Lines(val markdown: String, body: IntRange) {
        private val starts = mutableListOf<Int>()
        private val ends = mutableListOf<Int>()

        /** Whether anything was taken away before the lines: a byte-order mark, or the indentation they share. */
        var dedented = false
            private set

        init {
            var start = body.first

            // At the map's start, after the fence around it when there's one: Orbit reads the text inside that as the map.
            if (start <= body.last && markdown[start] == BYTE_ORDER_MARK) {
                start += 1
                dedented = true
            }

            while (start <= body.last) {
                val end = markdown.indexOf('\n', start).takeIf { it in start..body.last } ?: (body.last + 1)
                starts += start
                ends += end
                start = end + 1
            }

            withoutSharedIndentation()
        }

        /** Moves each line's start past the indentation every line with text shares. */
        private fun withoutSharedIndentation() {
            // The shared indentation, as where it starts in `markdown` and how long it is.
            var from = -1
            var length = 0

            for (line in starts.indices) {
                if (blankAsTrimmed(line)) {
                    continue
                }

                if (from < 0) {
                    from = starts[line]
                    length = indentation(starts[line], ends[line])
                } else {
                    length = minOf(length, same(starts[line], ends[line], from, length))
                }

                if (length == 0) {
                    return
                }
            }

            if (from < 0) {
                return
            }

            for (line in starts.indices) {
                starts[line] += same(starts[line], ends[line], from, length)
            }

            dedented = true
        }

        /**
         * Whether the line is blank as the library's `trim` finds it, which
         * takes away JavaScript's whitespace: a byte-order mark and every
         * space separator too, but not the separators below a space (U+001C
         * to U+001F), which Kotlin's whitespace counts.
         */
        private fun blankAsTrimmed(line: Int): Boolean = (starts[line] until ends[line]).all { isTrimmed(markdown[it]) }

        private fun isTrimmed(char: Char): Boolean =
            char == '\t' || char == '\n' || char == '\u000B' || char == '\u000C' || char == '\r' || char == BYTE_ORDER_MARK ||
                char == '\u2028' || char == '\u2029' || Character.getType(char) == Character.SPACE_SEPARATOR.toInt()

        /** How many spaces and tabs start the text from `start` to `end`. */
        private fun indentation(start: Int, end: Int): Int {
            var index = start

            while (index < end && (markdown[index] == ' ' || markdown[index] == '\t')) {
                index += 1
            }

            return index - start
        }

        /** How many characters the text from `start` to `end` starts with that are the same as the `length` from `from`. */
        private fun same(start: Int, end: Int, from: Int, length: Int): Int {
            var count = 0

            while (count < length && start + count < end && markdown[start + count] == markdown[from + count]) {
                count += 1
            }

            return count
        }

        val size: Int get() = starts.size

        /** The lines as Orbit reads them, one after another. */
        fun text(): String = starts.indices.joinToString("\n") { text(it) }

        fun start(line: Int): Int = starts[line]

        fun end(line: Int): Int = ends[line]

        fun text(line: Int): String = markdown.substring(starts[line], ends[line])

        fun blank(line: Int): Boolean = (starts[line] until ends[line]).all { markdown[it].isWhitespace() }

        /** How far in the line's text starts, a tab counting four. */
        fun indent(line: Int): Int {
            var columns = 0

            for (index in starts[line] until ends[line]) {
                when (markdown[index]) {
                    ' ' -> columns += 1
                    '\t' -> columns += 4
                    else -> return columns
                }
            }

            return columns
        }

        /** The line's text after its indentation. */
        fun content(line: Int): String = text(line).trimStart()

        /** The line's text after its indentation and any quote's `>`, as a quote's note reads it. */
        fun quoted(line: Int): String = text(line).trimStart(' ', '\t', '>')
    }

    /**
     * The fenced code blocks of a markdown map, as line ranges, read level by
     * level as Orbit's parser reads its blocks: a list item's lines indented
     * under it are its own level, whose text starts as far in as the least
     * indented of them, and a fence opens only at most three spaces past
     * that. Deeper than [MOST_LEVELS], nothing more is taken for code.
     */
    private class MarkdownBlocks(private val lines: Lines) {
        private val fences = mutableListOf<IntRange>()

        fun fences(): List<IntRange> {
            level(0, lines.size, base = 0, depth = 0)
            return fences.sortedBy { it.first }
        }

        /** Reads the lines from `from` to `until`, a level whose text starts `base` columns in. */
        private fun level(from: Int, until: Int, base: Int, depth: Int) {
            if (depth > MOST_LEVELS) {
                return
            }

            var line = from

            while (line < until) {
                if (lines.blank(line)) {
                    line += 1
                    continue
                }

                val content = lines.content(line)

                if (content.startsWith("<!--")) {
                    line = afterComment(line, until)
                    continue
                }

                if (content.startsWith(">")) {
                    line = quote(line, until)
                    continue
                }

                if (isListItem(line, base)) {
                    line = list(line, until, base, depth)
                    continue
                }

                val fence = fenceOf(content)

                if (fence != null && lines.indent(line) - base <= 3) {
                    // Never closed, it's code to the level's end, as the parser reads it.
                    val closing = (line + 1 until until).firstOrNull { lines.content(it).startsWith(fence) } ?: (until - 1)
                    fences += line..closing
                    line = closing + 1
                    continue
                }

                line += 1
            }
        }

        /** Reads a quote from `start`, its lines a note's without their `>`: a fence in it is code at any indent, to its end. */
        private fun quote(start: Int, until: Int): Int {
            var end = start

            while (end < until && lines.content(end).startsWith(">")) {
                end += 1
            }

            var line = start

            while (line < end) {
                val fence = fenceOf(lines.quoted(line))

                if (fence == null) {
                    line += 1
                    continue
                }

                val closing = (line + 1 until end).firstOrNull { lines.quoted(it).startsWith(fence) } ?: (end - 1)
                fences += line..closing
                line = closing + 1
            }

            return end
        }

        /** Reads a list from its first item at `start`: each item's lines indented under it are a level of their own. */
        private fun list(start: Int, until: Int, base: Int, depth: Int): Int {
            val listIndent = lines.indent(start)
            var line = start

            while (line < until) {
                if (lines.blank(line)) {
                    line += 1
                    continue
                }

                if (!isListItem(line, base) || lines.indent(line) < listIndent) {
                    break
                }

                line += 1
                val contentStart = line
                var least = Int.MAX_VALUE
                var continues = true

                while (line < until) {
                    if (lines.blank(line)) {
                        continues = false
                        line += 1
                        continue
                    }

                    if (lines.indent(line) > listIndent) {
                        least = minOf(least, lines.indent(line))
                        line += 1
                        continue
                    }

                    if (continues && continuesItem(line, base)) {
                        line += 1
                        continue
                    }

                    break
                }

                if (least != Int.MAX_VALUE) {
                    level(contentStart, line, least, depth + 1)
                }
            }

            return line
        }

        /** The line after an HTML comment that starts at `start`, where it closes. */
        private fun afterComment(start: Int, until: Int): Int {
            val opener = lines.text(start)

            if (opener.indexOf("-->", opener.indexOf("<!--") + 4) >= 0) {
                return start + 1
            }

            return ((start + 1 until until).firstOrNull { "-->" in lines.text(it) } ?: (until - 1)) + 1
        }

        private fun isListItem(line: Int, base: Int): Boolean = LIST_ITEM.matches(lines.text(line)) && !isBreak(line, base)

        private fun isBreak(line: Int, base: Int): Boolean = lines.indent(line) - base <= 3 && THEMATIC_BREAK.matches(lines.content(line))

        /** Whether a line right after a list item, not indented under it, still belongs to it, as CommonMark's lazy continuation has it. */
        private fun continuesItem(line: Int, base: Int): Boolean {
            val content = lines.content(line)
            val near = lines.indent(line) - base <= 3

            return !(
                isListItem(line, base) ||
                    (near && HEADING.containsMatchIn(content)) ||
                    content.startsWith(">") ||
                    (near && fenceOf(content) != null) ||
                    isBreak(line, base) ||
                    content.startsWith("<!--")
                )
        }
    }

    /**
     * The fenced code blocks of a YAML map, as line ranges: a note is YAML's
     * block of text, and a note shows a fence at any indent, a quote's
     * without its `>` too, closed by the next line that starts with the same,
     * or, never closed, running to the note's end, before the next line less
     * indented than it. An HTML comment holds none.
     */
    private fun yamlFences(lines: Lines): List<IntRange> {
        val fences = mutableListOf<IntRange>()
        var inComment = false
        var line = 0

        while (line < lines.size) {
            val text = lines.text(line)

            if (inComment) {
                inComment = "-->" !in text
                line += 1
                continue
            }

            val fence = fenceOf(lines.quoted(line))

            if (fence != null) {
                val closing = closingFence(lines, line + 1, fence, lines.indent(line))
                fences += line..closing
                line = closing + 1
                continue
            }

            val opened = text.lastIndexOf("<!--")
            inComment = opened >= 0 && text.indexOf("-->", opened + 4) < 0
            line += 1
        }

        return fences
    }

    /** The line from `from` that closes a fence of `fence` opened `indent` columns in, or, with none, the last line of its note, before a line less indented. */
    private fun closingFence(lines: Lines, from: Int, fence: String, indent: Int): Int {
        var last = from - 1

        for (line in from until lines.size) {
            if (lines.blank(line)) {
                continue
            }

            if (lines.quoted(line).startsWith(fence)) {
                return line
            }

            if (lines.indent(line) < indent) {
                return last
            }

            last = line
        }

        return last
    }

    /** The fence a line's content starts with, ``` or ~~~, or null. */
    private fun fenceOf(content: String): String? = when {
        content.startsWith("```") -> "```"
        content.startsWith("~~~") -> "~~~"
        else -> null
    }

    /**
     * Reads the lines outside fenced blocks for code spans: a title as a title
     * reads it, and a note's paragraph, as Orbit's parser gathers one, or its
     * quote block by block, as the note reader does ([NoteBlocks]). In a YAML
     * map, each line is read as a note's, but a value over several lines, or
     * quoted, which is read as a note ([YamlNotes]).
     */
    private class Spans(
        private val markdown: String,
        private val lines: Lines,
        private val inFence: BooleanArray,
        private val yaml: Boolean,
        private val ranges: MutableList<IntRange>,
    ) {
        private val blocks = NoteBlocks(markdown, { it >= 0 && inFence[it] }, ranges)
        private val yamlNotes = YamlNotes(markdown, lines, blocks, ranges)

        fun read() {
            var line = 0

            while (line < lines.size) {
                if (inFence[line] || lines.blank(line)) {
                    line += 1
                    continue
                }

                val content = lines.content(line)

                when {
                    content.startsWith("<!--") -> line = afterComment(line)

                    yaml -> line = yamlNotes.read(line)

                    content.startsWith(">") -> line = quote(line)

                    isMedia(line) -> line += 1

                    isTitle(line) -> {
                        title(line)
                        line += 1
                    }

                    else -> {
                        var last = line

                        // A heading in a note is a block of its own; any other line goes on with the paragraph's.
                        while (!HEADING.containsMatchIn(content) && last + 1 < lines.size && goesOn(last + 1)) {
                            last += 1
                        }

                        blocks.read((line..last).map { NoteLine(it, lines.start(it), lines.end(it)) }, depth = 0)
                        line = last + 1
                    }
                }
            }
        }

        /** Reads a quote from `start`, which Orbit's parser takes for a note: its lines without their `>`, read as the note reader reads a note. */
        private fun quote(start: Int): Int {
            val inside = mutableListOf<NoteLine>()
            var line = start

            while (line < lines.size && lines.content(line).startsWith(">")) {
                // After the `>`, and a space after it.
                var after = lines.end(line) - lines.content(line).length + 1

                if (after < lines.end(line) && markdown[after] == ' ') {
                    after += 1
                }

                inside += NoteLine(line, after, lines.end(line))
                line += 1
            }

            blocks.read(inside, depth = 0)
            return line
        }

        /** The line after a comment that starts a line, where it closes; its lines hold no code. */
        private fun afterComment(start: Int): Int {
            val opener = lines.text(start)

            if (opener.indexOf("-->", opener.indexOf("<!--") + 4) >= 0) {
                return start + 1
            }

            return ((start + 1 until lines.size).firstOrNull { "-->" in lines.text(it) } ?: (lines.size - 1)) + 1
        }

        /** A picture or link on a line of its own, which Orbit's parser takes for an idea's media. */
        private fun isMedia(line: Int): Boolean = STANDALONE_MEDIA.any { it.matches(lines.content(line).trimEnd()) }

        /** A list item or a heading, whose text is a title. */
        private fun isTitle(line: Int): Boolean =
            (LIST_ITEM.matches(lines.text(line)) && !THEMATIC_BREAK.matches(lines.content(line))) || (lines.indent(line) <= 3 && HEADING.containsMatchIn(lines.content(line)))

        /** Whether `line` goes on with the paragraph before it, as the parser's paragraphs do. */
        private fun goesOn(line: Int): Boolean {
            if (inFence[line] || lines.blank(line)) {
                return false
            }

            val content = lines.content(line)

            return !content.startsWith("<!--") &&
                !isMedia(line) &&
                !isTitle(line) &&
                fenceOf(content) == null &&
                !THEMATIC_BREAK.matches(content) &&
                !HEADING.containsMatchIn(content) &&
                !content.startsWith(">")
        }

        /**
         * A title's code spans, as a title reads them before anything else: a
         * run of backticks to the next run exactly as long, a backtick after a
         * backslash being one, and the attributes at its end none.
         */
        private fun title(line: Int) {
            val text = lines.text(line)
            val start = lines.start(line)
            val after = TITLE_START.find(text)?.range?.last?.plus(1) ?: 0
            val attributes = text.trimEnd().takeIf { it.endsWith("-->") }?.lastIndexOf("<!--")?.takeIf { it >= after }
            val end = start + (attributes ?: text.length)
            val runs = mutableListOf<IntRange>()
            var position = start + after

            while (position < end) {
                if (markdown[position] == '\\') {
                    position += 2
                    continue
                }

                if (markdown[position] != '`') {
                    position += 1
                    continue
                }

                var runEnd = position

                while (runEnd < end && markdown[runEnd] == '`') {
                    runEnd += 1
                }

                runs += position until runEnd
                position = runEnd
            }

            val nextAsLong = IntArray(runs.size) { -1 }
            val latest = HashMap<Int, Int>()

            for (index in runs.indices.reversed()) {
                val length = runs[index].last - runs[index].first + 1
                nextAsLong[index] = latest[length] ?: -1
                latest[length] = index
            }

            var index = 0

            while (index < runs.size) {
                val closing = nextAsLong[index]

                if (closing < 0) {
                    index += 1
                    continue
                }

                ranges += runs[index].first..runs[closing].last
                index = closing + 1
            }
        }
    }

    /** A line of a note, from `start` to `end` in the text it's read from, and the map's line it's on, or -1 when it has none of its own. */
    private class NoteLine(val line: Int, val start: Int, val end: Int)

    /** A list item's start, as the note reader reads one: how far in its bullet or number is, whether it's a number, and where its text starts. */
    private class ItemStart(val indent: Int, val ordered: Boolean, val text: Int)

    /**
     * A note's lines read into blocks as the note reader reads them, rule for
     * rule (`readBlocks`, `readList`, `readTable` and `splitRow`), and each
     * block's words read for code ([NoteSpans]): a paragraph across its
     * lines; a heading; a quote's lines without their `>`, as a note of their
     * own; a list item's text, with the lines indented under it, up to a list
     * under it, after which they show as written; a table's cells, each
     * alone, any past its header's showing nowhere. A fence is code to its
     * closing line, or the note's end. A map's line `fenced` says the map's
     * fences took is passed over, and a comment that starts a line holds no
     * text, as in the map. Quotes and lists deeper than [MOST_LEVELS] are
     * read as text, as the note reader shows them.
     */
    private class NoteBlocks(private val text: String, private val fenced: (Int) -> Boolean, private val ranges: MutableList<IntRange>) {
        fun read(lines: List<NoteLine>, depth: Int) {
            var index = 0

            while (index < lines.size) {
                val line = lines[index]

                index = when {
                    blank(line) || fenced(line.line) -> index + 1
                    startsComment(line) -> afterComment(lines, index)
                    fenceAt(line) != null -> fence(lines, index)

                    heading(line) -> {
                        spans(line.start, line.end)
                        index + 1
                    }

                    quoted(line) -> quote(lines, index, depth)
                    itemStart(line) != null -> list(lines, index, depth)
                    else -> table(lines, index) ?: paragraph(lines, index)
                }
            }
        }

        /** Reads a paragraph from `start`, to a blank line or one that starts another block or a table: where to read on from. */
        private fun paragraph(lines: List<NoteLine>, start: Int): Int {
            var last = start

            while (last + 1 < lines.size && goesOn(lines, last + 1)) {
                last += 1
            }

            spans(lines[start].start, lines[last].end)
            return last + 1
        }

        private fun goesOn(lines: List<NoteLine>, index: Int): Boolean {
            val line = lines[index]
            return !blank(line) && !fenced(line.line) && !startsComment(line) && !endsParagraph(line) && itemStart(line) == null && header(lines, index) == null
        }

        /** Takes a fence from `start` for code, to the line that starts with the same, or the note's end: where to read on from. */
        private fun fence(lines: List<NoteLine>, start: Int): Int {
            val fence = fenceAt(lines[start]) ?: return start + 1
            var closing = start + 1

            while (closing < lines.size && !startsWith(fence, firstWord(lines[closing]), lines[closing])) {
                closing += 1
            }

            ranges += lines[start].start until lines[minOf(closing, lines.size - 1)].end
            return closing + 1
        }

        /** Reads a quote from `start`, its lines without their `>`: where to read on from. */
        private fun quote(lines: List<NoteLine>, start: Int, depth: Int): Int {
            val inside = mutableListOf<NoteLine>()
            var next = start

            while (next < lines.size && quoted(lines[next])) {
                val line = lines[next]
                // After the `>`, and a space after it.
                var after = firstWord(line) + 1

                if (after < line.end && text[after].isWhitespace()) {
                    after += 1
                }

                inside += NoteLine(line.line, after, line.end)
                next += 1
            }

            if (depth >= MOST_LEVELS) {
                spans(inside.first().start, inside.last().end)
            } else {
                read(inside, depth + 1)
            }

            return next
        }

        /**
         * Reads a list from its first item at `start`: each item's text with
         * the lines indented further than its bullet that go on with it, until
         * a list indented under it, read as a list of its own; lines after
         * that list show as written. Where to read on from.
         */
        private fun list(lines: List<NoteLine>, start: Int, depth: Int): Int {
            val first = itemStart(lines[start]) ?: return start + 1
            var index = start
            var textStart = -1
            var textEnd = -1
            // Whether a list is under the item, which leaves its text as it is.
            var under = false

            while (index < lines.size) {
                val line = lines[index]

                if (blank(line)) {
                    index += 1
                    continue
                }

                val item = itemStart(line)

                if (item != null && item.indent == first.indent) {
                    if (item.ordered != first.ordered) {
                        break
                    }

                    if (textStart >= 0 && !under) {
                        spans(textStart, textEnd)
                    }

                    textStart = item.text
                    textEnd = line.end
                    under = false
                    index += 1
                    continue
                }

                if (item != null && item.indent > first.indent && textStart >= 0) {
                    if (!under) {
                        spans(textStart, textEnd)
                        under = true
                    }

                    if (depth >= MOST_LEVELS) {
                        ranges += line.start until line.end
                        index += 1
                    } else {
                        index = list(lines, index, depth + 1)
                    }

                    continue
                }

                if (item == null && firstWord(line) - line.start > first.indent && textStart >= 0) {
                    if (under) {
                        ranges += line.start until line.end
                    } else {
                        textEnd = line.end
                    }

                    index += 1
                    continue
                }

                break
            }

            if (textStart >= 0 && !under) {
                spans(textStart, textEnd)
            }

            return index
        }

        /** Reads a table from `start`, if one starts there, each cell alone: where to read on from, or null. */
        private fun table(lines: List<NoteLine>, start: Int): Int? {
            val head = header(lines, start) ?: return null
            head.forEach(::spans)
            var row = start + 2

            while (row < lines.size) {
                val line = lines[row]

                if (blank(line) || fenced(line.line) || startsComment(line) || endsParagraph(line) || itemStart(line) != null) {
                    break
                }

                // A row's cells past its header's show nowhere.
                cells(line).forEachIndexed { column, cell -> if (column < head.size) spans(cell) else ranges += cell }
                row += 1
            }

            return row
        }

        /** A table's header's cells, when one starts at `index`: a row and a row of dashes as many, each line with a `|`. */
        private fun header(lines: List<NoteLine>, index: Int): List<IntRange>? {
            if (index + 1 >= lines.size || !hasPipe(lines[index]) || !hasPipe(lines[index + 1])) {
                return null
            }

            val head = cells(lines[index])
            val dashes = cells(lines[index + 1])
            return head.takeIf { dashes.size == head.size && dashes.all(::isDashes) }
        }

        /**
         * A table row's cells, as `splitRow` takes them: without the pipes at
         * either end, a `|` after a backslash, or in code, ending no cell.
         */
        private fun cells(line: NoteLine): List<IntRange> {
            var start = firstWord(line)
            var end = line.end

            while (end > start && text[end - 1].isWhitespace()) {
                end -= 1
            }

            if (start < end && text[start] == '|') {
                start += 1
            }

            if (end > start && text[end - 1] == '|' && !(end - 2 >= start && text[end - 2] == '\\')) {
                end -= 1
            }

            val backticks = Backticks(text, start, end)
            val cells = mutableListOf<IntRange>()
            var cell = start
            var at = start

            while (at < end) {
                when (text[at]) {
                    '\\' -> at += if (at + 1 < end && text[at + 1] == '|') 2 else 1

                    '`' -> {
                        val code = backticks.codeEnd(at, end)

                        if (code >= 0) {
                            at = code
                        } else {
                            while (at < end && text[at] == '`') {
                                at += 1
                            }
                        }
                    }

                    '|' -> {
                        cells += trimmed(cell, at)
                        cell = at + 1
                        at += 1
                    }

                    else -> at += 1
                }
            }

            cells += trimmed(cell, end)
            return cells
        }

        private fun trimmed(from: Int, until: Int): IntRange {
            var start = from
            var end = until

            while (start < end && text[start].isWhitespace()) {
                start += 1
            }

            while (end > start && text[end - 1].isWhitespace()) {
                end -= 1
            }

            return start until end
        }

        /** A cell of a table's row of dashes: dashes, with a colon at either end. */
        private fun isDashes(cell: IntRange): Boolean {
            val start = if (!cell.isEmpty() && text[cell.first] == ':') cell.first + 1 else cell.first
            val end = if (cell.last >= start && text[cell.last] == ':') cell.last else cell.last + 1
            return end > start && (start until end).all { text[it] == '-' }
        }

        /** Where a list item starts on `line`, as the note reader's `^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$` finds one, or null. */
        private fun itemStart(line: NoteLine): ItemStart? {
            val first = firstWord(line)
            var at = first

            if (at >= line.end) {
                return null
            }

            val ordered = text[at] in '0'..'9'

            if (ordered) {
                while (at < line.end && text[at] in '0'..'9' && at - first < 10) {
                    at += 1
                }

                if (at - first > 9 || at >= line.end || (text[at] != '.' && text[at] != ')')) {
                    return null
                }
            } else if (text[at] != '-' && text[at] != '*' && text[at] != '+') {
                return null
            }

            at += 1

            if (at >= line.end || !text[at].isWhitespace()) {
                return null
            }

            while (at < line.end && text[at].isWhitespace()) {
                at += 1
            }

            return ItemStart(first - line.start, ordered, at)
        }

        /** Whether `line` is a heading, as the note reader's `^\s{0,3}(#{1,6})\s+` finds one. */
        private fun heading(line: NoteLine): Boolean {
            val first = firstWord(line)
            return first - line.start <= 3 && marks(first, line)
        }

        /** Whether `line` ends a paragraph, as the note reader's `^\s*(```|~~~|>|#{1,6}\s)` has it. */
        private fun endsParagraph(line: NoteLine): Boolean {
            val first = firstWord(line)
            return fenceAt(line) != null || quoted(line) || marks(first, line)
        }

        /** Whether one to six `#` start at `at`, then a space. */
        private fun marks(at: Int, line: NoteLine): Boolean {
            var end = at

            while (end < line.end && text[end] == '#' && end - at < 7) {
                end += 1
            }

            return end - at in 1..6 && end < line.end && text[end].isWhitespace()
        }

        private fun quoted(line: NoteLine): Boolean = firstWord(line) < line.end && text[firstWord(line)] == '>'

        private fun fenceAt(line: NoteLine): String? = listOf("```", "~~~").firstOrNull { startsWith(it, firstWord(line), line) }

        private fun startsComment(line: NoteLine): Boolean = startsWith("<!--", firstWord(line), line)

        /** The line after a comment that starts the line at `start`, where it closes. */
        private fun afterComment(lines: List<NoteLine>, start: Int): Int {
            if (holds("-->", firstWord(lines[start]) + 4, lines[start])) {
                return start + 1
            }

            var next = start + 1

            while (next < lines.size && !holds("-->", lines[next].start, lines[next])) {
                next += 1
            }

            return minOf(next + 1, lines.size)
        }

        private fun hasPipe(line: NoteLine): Boolean = (line.start until line.end).any { text[it] == '|' }

        private fun blank(line: NoteLine): Boolean = firstWord(line) == line.end

        /** Where the line's text starts, after its spaces. */
        private fun firstWord(line: NoteLine): Int {
            var at = line.start

            while (at < line.end && text[at].isWhitespace()) {
                at += 1
            }

            return at
        }

        private fun startsWith(prefix: String, at: Int, line: NoteLine): Boolean = at + prefix.length <= line.end && text.startsWith(prefix, at)

        private fun holds(part: String, from: Int, line: NoteLine): Boolean = (from..line.end - part.length).any { text.startsWith(part, it) }

        private fun spans(range: IntRange) = spans(range.first, range.last + 1)

        private fun spans(start: Int, end: Int) {
            if (start < end) {
                NoteSpans(text, start, end, ranges).read()
            }
        }
    }

    /**
     * A YAML map's values read as Orbit's YAML reader makes them, each a note
     * the note reader reads ([NoteBlocks]): YAML's block of text after a `|`,
     * its lines as far in as the first of them, or after a `>`, folded, each
     * paragraph a line; a quoted text, its escapes read, so its `\n`s are its
     * lines; plain text going on over the lines indented under its key,
     * folded. Any other line is read alone, as a note's.
     */
    private class YamlNotes(private val markdown: String, private val lines: Lines, private val blocks: NoteBlocks, private val ranges: MutableList<IntRange>) {
        /** Reads the line `line`, and the lines of the value that starts on it: where to read on from. */
        fun read(line: Int): Int {
            val text = lines.text(line)
            // Where its key starts, after its list's dashes: the lines of its value are further in.
            var key = spaces(line)

            while (text.startsWith("- ", key)) {
                key += 2
            }

            val colon = colon(text, key)
            var value = colon + 1

            while (colon >= 0 && value < text.length && (text[value] == ' ' || text[value] == '\t')) {
                value += 1
            }

            val written = if (colon < 0) "" else text.substring(value).trimEnd()

            return when {
                BLOCK_TEXT.matches(written) -> {
                    alone(line)
                    block(line, key, folded = written.startsWith('>'))
                }

                written.startsWith('"') && quoted(lines.start(line) + value, lines.end(line)) -> line + 1
                written.isNotEmpty() && written[0] !in PLAIN_NOT -> plain(line, key, lines.start(line) + value)

                else -> {
                    alone(line)
                    line + 1
                }
            }
        }

        private fun alone(line: Int) = NoteSpans(markdown, lines.start(line), lines.end(line), ranges).read()

        /** Reads a block of text, the lines after `line` further in than `key`, as far in as the first of them: where to read on from. */
        private fun block(line: Int, key: Int, folded: Boolean): Int {
            var first = line + 1

            while (first < lines.size && lines.blank(first)) {
                first += 1
            }

            if (first >= lines.size || spaces(first) <= key) {
                return line + 1
            }

            val indent = spaces(first)
            var end = first

            while (end < lines.size && (lines.blank(end) || spaces(end) >= indent)) {
                end += 1
            }

            val note = (line + 1 until end).map { NoteLine(it, minOf(lines.start(it) + indent, lines.end(it)), lines.end(it)) }
            blocks.read(if (folded) folds(note) else note, depth = 0)
            return end
        }

        /** Plain text from `start` on `line`, going on over the lines after it further in than `key`, folded: where to read on from. */
        private fun plain(line: Int, key: Int, start: Int): Int {
            var last = line
            var next = line + 1

            while (next < lines.size) {
                if (!lines.blank(next)) {
                    if (spaces(next) <= key || lines.content(next).startsWith("#")) {
                        break
                    }

                    last = next
                }

                next += 1
            }

            if (last == line) {
                alone(line)
                return line + 1
            }

            val note = listOf(NoteLine(-1, start, lines.end(line))) + (line + 1..last).map { NoteLine(it, lines.end(it) - lines.content(it).length, lines.end(it)) }
            blocks.read(folds(note), depth = 0)
            return last + 1
        }

        /** Folded lines, as YAML folds them: each paragraph's lines one line, across the map's. */
        private fun folds(note: List<NoteLine>): List<NoteLine> {
            val folded = mutableListOf<NoteLine>()
            var first: NoteLine? = null
            var last: NoteLine? = null

            for (line in note) {
                if ((line.start until line.end).all { markdown[it].isWhitespace() }) {
                    first?.let { folded += NoteLine(-1, it.start, last!!.end) }
                    first = null
                    continue
                }

                first = first ?: line
                last = line
            }

            first?.let { folded += NoteLine(-1, it.start, last!!.end) }
            return folded
        }

        /**
         * Reads a quoted text from its `"` at `start`, closed before `end`, its
         * escapes read as YAML's, so its `\n`s are its lines; what's code in it
         * is where its escapes are written. False when it doesn't close there.
         */
        private fun quoted(start: Int, end: Int): Boolean {
            var close = start + 1

            while (close < end && markdown[close] != '"') {
                close += if (markdown[close] == '\\') 2 else 1
            }

            if (close >= end) {
                return false
            }

            val note = StringBuilder()
            // Where each of the note's characters is written, from and until.
            val from = IntArray(close - start)
            val until = IntArray(close - start)
            var at = start + 1

            while (at < close) {
                var next = at + 1
                var char = markdown[at]

                if (char == '\\') {
                    val escaped = markdown[at + 1]
                    val hex = if (escaped == 'u' && at + 6 <= close) markdown.substring(at + 2, at + 6).takeIf { digits -> digits.all { it in HEX_DIGITS } } else null
                    char = hex?.toInt(16)?.toChar() ?: ESCAPES[escaped] ?: escaped
                    next = if (hex != null) at + 6 else at + 2
                }

                from[note.length] = at
                until[note.length] = next
                note.append(char)
                at = next
            }

            val lineStarts = mutableListOf<NoteLine>()
            var lineStart = 0
            var index = 0

            while (index <= note.length) {
                if (index == note.length || note[index] == '\n' || note[index] == '\r') {
                    lineStarts += NoteLine(-1, lineStart, index)

                    if (index + 1 < note.length && note[index] == '\r' && note[index + 1] == '\n') {
                        index += 1
                    }

                    lineStart = index + 1
                }

                index += 1
            }

            val found = mutableListOf<IntRange>()
            NoteBlocks(note.toString(), { false }, found).read(lineStarts, depth = 0)

            for (range in found) {
                if (!range.isEmpty()) {
                    ranges += from[range.first] until until[range.last]
                }
            }

            return true
        }

        /** Where a YAML line's key ends, as Orbit's YAML reader finds it from `start`: a `:` then a space or the line's end, outside quotes and brackets; -1 when there's none. */
        private fun colon(text: String, start: Int): Int {
            var quote: Char? = null
            var depth = 0
            var at = start

            while (at < text.length) {
                val char = text[at]

                when {
                    quote != null -> {
                        if (char == '\\' && quote == '"') {
                            at += 1
                        } else if (char == quote) {
                            quote = null
                        }
                    }

                    (char == '"' || char == '\'') && (at == start || text[at - 1].isWhitespace() || text[at - 1] in "[{,") -> quote = char
                    char == '[' || char == '{' -> depth += 1
                    char == ']' || char == '}' -> depth -= 1
                    char == ':' && depth == 0 && (at + 1 >= text.length || text[at + 1] == ' ' || text[at + 1] == '\t') -> return at
                }

                at += 1
            }

            return -1
        }

        private fun spaces(line: Int): Int = lines.text(line).let { it.length - it.trimStart(' ').length }
    }

    /** The runs of backticks from `start` to `end` of a text, to find where code from one of them ends. */
    private class Backticks(text: String, start: Int, end: Int) {
        private val starts = Places()
        private val lengths = Places()
        private val longRuns: RunsAtLeast

        init {
            var position = start

            while (position < end) {
                if (text[position] != '`') {
                    position += 1
                    continue
                }

                var after = position

                while (after < end && text[after] == '`') {
                    after += 1
                }

                starts += position
                lengths += after - position
                position = after
            }

            longRuns = RunsAtLeast(lengths.toArray())
        }

        /**
         * Where code from the backticks at `position` ends, before `until`: as
         * many as there are, to the first run of as many after them, else one
         * fewer, and so on, as the note reader's `(`+)([\s\S]*?)\1` finds it;
         * -1 when none closes.
         */
        fun codeEnd(position: Int, until: Int): Int {
            val run = starts.lastAtOrBefore(position)
            val length = minOf(starts[run] + lengths[run], until) - position

            for (backticks in length downTo 1) {
                // As many again right after them, in the same run: the earliest any can close.
                if (length >= 2 * backticks) {
                    return position + 2 * backticks
                }

                val closing = longRuns.first(run + 1, backticks)

                if (closing >= 0 && starts[closing] + backticks <= until) {
                    return starts[closing] + backticks
                }
            }

            return -1
        }
    }

    /**
     * A note's paragraph from `start` to `end`, read for its code spans as the
     * note reader's one pattern reads it, from left to right: at each place,
     * code, then a picture, a link, a web address in angle brackets, bold,
     * struck and emphasized words, the first that matches taking it; the words
     * of a link and of bold, struck or emphasized ones are read the same way.
     * What closes each is found by binary search among the places it could.
     */
    private class NoteSpans(private val text: String, private val start: Int, private val end: Int, private val ranges: MutableList<IntRange>) {
        private val backticks = Backticks(text, start, end)
        private val brackets = Places()
        private val stars = Places()
        private val underscores = Places()
        private val starPairs = Places()
        private val underscorePairs = Places()
        private val tildePairs = Places()
        private val commentEnds = Places()
        private val angles = Places()
        private val spaces = Places()

        /** The `]` whose picture or link was found not to be one, so the next `[` with it isn't read again. */
        private var failedBracket = -1

        init {
            var position = start

            while (position < end) {
                val char = text[position]

                when {
                    char == ']' -> brackets += position
                    char == '*' -> stars += position
                    char == '_' -> underscores += position
                    char == '>' -> angles += position
                    char.isWhitespace() -> spaces += position
                }

                if (position + 1 < end) {
                    val next = text[position + 1]

                    when {
                        char == '*' && next == '*' -> starPairs += position
                        char == '_' && next == '_' -> underscorePairs += position
                        char == '~' && next == '~' -> tildePairs += position
                        char == '-' && next == '-' && position + 2 < end && text[position + 2] == '>' -> commentEnds += position
                    }
                }

                position += 1
            }
        }

        fun read() = inline(start, end, depth = 0)

        /** Reads from `from` to `until`, a paragraph or the words inside a span, `depth` spans deep. */
        private fun inline(from: Int, until: Int, depth: Int) {
            var position = from

            while (position < until) {
                position = when (text[position]) {
                    '`' -> code(position, until)
                    '!' -> picture(position, until) ?: (position + 1)
                    '[' -> link(position, until, depth) ?: (position + 1)
                    '<' -> angled(position, until) ?: (position + 1)
                    '*', '_' -> emphasis(position, until, depth) ?: (position + 1)
                    '~' -> struck(position, until, depth) ?: (position + 1)
                    else -> position + 1
                }
            }
        }

        /** Code from the backticks at `position` ([Backticks.codeEnd]): where it ends, or the next place when none closes. */
        private fun code(position: Int, until: Int): Int {
            val end = backticks.codeEnd(position, until)

            if (end < 0) {
                return position + 1
            }

            ranges += position until end
            return end
        }

        /** Where a picture at `position` ends, after its `)`: `![words](address)`. */
        private fun picture(position: Int, until: Int): Int? {
            if (position + 1 >= until || text[position + 1] != '[') {
                return null
            }

            val closing = brackets.firstAtOrAfter(position + 2).takeIf { it in 0 until until } ?: return null
            return address(closing, until)?.plus(1)
        }

        /** Where a link at `position` ends, after its `)`: `[words](address)`, its words read too. */
        private fun link(position: Int, until: Int, depth: Int): Int? {
            val closing = brackets.firstAtOrAfter(position + 1).takeIf { it in position + 2 until until } ?: return null
            val end = address(closing, until) ?: return null
            words(position + 1, closing, depth)
            return end + 1
        }

        /** Where the address after the `]` at `closing` ends, at its `)`, without spaces and with parentheses of its own one deep; null when there's none. */
        private fun address(closing: Int, until: Int): Int? {
            if (closing == failedBracket || closing + 1 >= until || text[closing + 1] != '(') {
                return null
            }

            var position = closing + 2
            var parts = 0

            while (position < until) {
                val char = text[position]

                when {
                    char == ')' -> return position.takeIf { parts > 0 } ?: break
                    char == '(' -> {
                        var inner = position + 1

                        while (inner < until && text[inner] != ')' && text[inner] != '(' && !text[inner].isWhitespace()) {
                            inner += 1
                        }

                        if (inner >= until || text[inner] != ')') {
                            break
                        }

                        parts += 1
                        position = inner + 1
                    }
                    char.isWhitespace() -> break
                    else -> {
                        parts += 1
                        position += 1
                    }
                }
            }

            failedBracket = closing
            return null
        }

        /** Where a comment or a web address in angle brackets at `position` ends: neither is code, nor holds any. */
        private fun angled(position: Int, until: Int): Int? {
            if (text.startsWith("<!--", position)) {
                val closed = commentEnds.firstAtOrAfter(position + 4).takeIf { it >= 0 && it + 3 <= until } ?: return null
                return closed + 3
            }

            val prefix = ADDRESS_STARTS.firstOrNull { text.startsWith(it, position) } ?: return null
            val from = position + prefix.length
            val closing = angles.firstAtOrAfter(from).takeIf { it in from + 1 until until } ?: return null
            val space = spaces.firstAtOrAfter(from)
            return if (space < 0 || space > closing) closing + 1 else null
        }

        /** Where bold or emphasized words at `position` end: `**…**` or `__…__`, else `*…*` or `_…_` starting with neither a space nor either mark. */
        private fun emphasis(position: Int, until: Int, depth: Int): Int? {
            val mark = text[position]

            if (position + 1 < until && text[position + 1] == mark) {
                val closing = (if (mark == '*') starPairs else underscorePairs).firstAtOrAfter(position + 3)

                if (closing >= 0 && closing + 2 <= until) {
                    words(position + 2, closing, depth)
                    return closing + 2
                }
            }

            if (position + 1 >= until || text[position + 1].isWhitespace() || text[position + 1] == '*' || text[position + 1] == '_') {
                return null
            }

            val closing = (if (mark == '*') stars else underscores).firstAtOrAfter(position + 2).takeIf { it in 0 until until } ?: return null
            words(position + 1, closing, depth)
            return closing + 1
        }

        /** Where struck words at `position` end: `~~…~~`. */
        private fun struck(position: Int, until: Int, depth: Int): Int? {
            if (position + 1 >= until || text[position + 1] != '~') {
                return null
            }

            val closing = tildePairs.firstAtOrAfter(position + 3).takeIf { it >= 0 && it + 2 <= until } ?: return null
            words(position + 2, closing, depth)
            return closing + 2
        }

        /** Reads the words inside a span as the note reader does, a level deeper; past [MOST_LEVELS], they're taken for no code. */
        private fun words(from: Int, until: Int, depth: Int) {
            if (depth < MOST_LEVELS) {
                inline(from, until, depth + 1)
            }
        }
    }

    /** Places in a text, in order, kept in an array that grows. */
    private class Places {
        private var places = IntArray(16)
        private var count = 0

        operator fun plusAssign(place: Int) {
            if (count == places.size) {
                places = places.copyOf(count * 2)
            }

            places[count] = place
            count += 1
        }

        operator fun get(index: Int): Int = places[index]

        fun toArray(): IntArray = places.copyOf(count)

        /** The first place at `from` or after, or -1. */
        fun firstAtOrAfter(from: Int): Int {
            var low = 0
            var high = count

            while (low < high) {
                val middle = (low + high) ushr 1

                if (places[middle] < from) {
                    low = middle + 1
                } else {
                    high = middle
                }
            }

            return if (low < count) places[low] else -1
        }

        /** Where among the places the last one at `place` or before is. */
        fun lastAtOrBefore(place: Int): Int {
            var low = 0
            var high = count

            while (low < high) {
                val middle = (low + high) ushr 1

                if (places[middle] <= place) {
                    low = middle + 1
                } else {
                    high = middle
                }
            }

            return low - 1
        }
    }

    /** Runs of backticks by their lengths, to find the first run from a place among them at least so long. */
    private class RunsAtLeast(private val lengths: IntArray) {
        private val longest = IntArray(4 * maxOf(1, lengths.size))

        init {
            if (lengths.isNotEmpty()) {
                build(1, 0, lengths.size - 1)
            }
        }

        private fun build(node: Int, low: Int, high: Int) {
            if (low == high) {
                longest[node] = lengths[low]
                return
            }

            val middle = (low + high) ushr 1
            build(2 * node, low, middle)
            build(2 * node + 1, middle + 1, high)
            longest[node] = maxOf(longest[2 * node], longest[2 * node + 1])
        }

        /** The first run from `from` on with at least `least` backticks, by its place among the runs, or -1. */
        fun first(from: Int, least: Int): Int = if (from >= lengths.size) -1 else first(1, 0, lengths.size - 1, from, least)

        private fun first(node: Int, low: Int, high: Int, from: Int, least: Int): Int {
            if (high < from || longest[node] < least) {
                return -1
            }

            if (low == high) {
                return low
            }

            val middle = (low + high) ushr 1
            val left = first(2 * node, low, middle, from, least)
            return if (left >= 0) left else first(2 * node + 1, middle + 1, high, from, least)
        }
    }

    /** A byte-order mark, which Orbit reads no map's text with. */
    private const val BYTE_ORDER_MARK = '\uFEFF'

    /**
     * A fence around a whole map, as Orbit unwraps it: its opening line, of
     * ``` or ~~~ and `orbit`, then the map, then a closing fence of the same
     * kind. The map is its third group.
     */
    private val WHOLE_MAP_FENCE = Regex("""^(\s*(```|~~~)orbit(?:[^\S\n][^\n]*)?\n)([\s\S]*?)(?:\r?\n)?[^\S\n]*\2\s*$""")

    /** A picture, a picture in a link, or a link on a line of its own, which Orbit's parser takes for an idea's media. */
    private val STANDALONE_MEDIA = listOf(
        Regex("""^\[!\[([^\]]*)\]\(([^)]+)\)\]\(([^)]+)\)$"""),
        Regex("""^!\[([^\]]*)\]\(([^)]+)\)$"""),
        Regex("""^\[([^\]]*)\]\(([^)]+)\)$"""),
    )

    /**
     * A list item, as Orbit's parser reads one: a bullet or a number, then its
     * text. Its spaces are taken whole, never given back, so a bullet followed
     * by nothing but spaces is read in one pass, matching as the parser's does.
     */
    private val LIST_ITEM = Regex("""^\s*+(?:[-*+]|\d{1,9}[.)])\s++\S[\s\S]*+""")

    /** Where a title's text starts: after its bullet or number, or its heading's marks, and the spaces after them. */
    private val TITLE_START = Regex("""^\s*+(?:[-*+]|\d{1,9}[.)]|#{1,6})\s++""")

    /** A thematic break, which isn't a list item: three or more of `-`, `*` or `_`. */
    private val THEMATIC_BREAK = Regex("""^([-*_])(?:\s*\1){2,}\s*$""")

    /** A heading's start. */
    private val HEADING = Regex("""^#{1,6}\s+""")

    /** How a web address in angle brackets starts, as the note reader takes one. */
    private val ADDRESS_STARTS = listOf("<http://", "<https://", "<mailto:")

    /** A YAML value that's a block of text on the lines after it, as Orbit's YAML reader takes one: `|` or `>`, as it's to end, how far in, and a comment. */
    private val BLOCK_TEXT = Regex("""^[|>][-+]?\d?(?:\s+#.*)?$""")

    /** How a YAML value starts that isn't plain text: a quote, a list, a map, an anchor, an alias, a tag, a block of text or a comment. */
    private const val PLAIN_NOT = "\"'[{&*!|>#"

    /** What a YAML quoted text's escapes stand for, as Orbit's YAML reader reads them; `\u` and four hexadecimal digits stand for that character. */
    private val ESCAPES = mapOf('"' to '"', '\\' to '\\', '/' to '/', 'b' to '\b', 'f' to '\u000C', 'n' to '\n', 'r' to '\r', 't' to '\t', '0' to '\u0000')

    private const val HEX_DIGITS = "0123456789abcdefABCDEF"

    /** How many levels of lists in lists, or spans in spans, are read for code: deeper, nothing more is taken for code. */
    private const val MOST_LEVELS = 100
}
