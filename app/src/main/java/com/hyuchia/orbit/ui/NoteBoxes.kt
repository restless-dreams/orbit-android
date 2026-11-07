package com.hyuchia.orbit.ui

/**
 * A box of a note's checklist, checked or unchecked: its line in the note
 * (from 0, as the map's page counts them), whether it's checked now, and its
 * item's words as they read, for what's said about it.
 */
data class NoteCheck(val line: Int, val checked: Boolean, val label: String)

/** A line break in a note, as Orbit counts lines: `\r\n`, `\r` or `\n`. */
private val LINE_BREAK = Regex("\r\n?|\n")

/**
 * A checklist item's line: quote markers and indentation, a list marker, a
 * space, then its box, `[ ]`, `[x]` or `[X]`, and a space or the line's end.
 */
private val BOX_LINE = Regex("""^((?:[ \t]*>[ \t]?)*[ \t]*(?:[-*+]|\d{1,9}[.)])[ \t]+)\[([ xX])](?=[ \t]|$)""")

/** The line of `text`, from 0, that the character at `offset` is on. */
fun lineAt(text: String, offset: Int): Int = LINE_BREAK.findAll(text.substring(0, offset.coerceIn(0, text.length))).count()

/** Whether the box on `line` of `note` is checked; null when that line holds no box. */
fun noteBoxOn(note: String, line: Int): Boolean? {
    val text = note.split(LINE_BREAK).getOrNull(line) ?: return null
    return BOX_LINE.find(text)?.let { it.groupValues[2] != " " }
}

/**
 * The note with the box on `line` checked or unchecked, and every other
 * character as it was; null when that line holds no box.
 */
fun withNoteBox(note: String, line: Int, checked: Boolean): String? {
    if (line < 0) {
        return null
    }

    var start = 0

    repeat(line) {
        start = LINE_BREAK.find(note, start)?.range?.last?.plus(1) ?: return null
    }

    val end = LINE_BREAK.find(note, start)?.range?.first ?: note.length
    val box = BOX_LINE.find(note.substring(start, end)) ?: return null

    if ((box.groupValues[2] != " ") == checked) {
        return note
    }

    // The character inside the brackets, after the marker and its space.
    val mark = start + box.groupValues[1].length + 1
    return note.substring(0, mark) + (if (checked) "x" else " ") + note.substring(mark + 1)
}
