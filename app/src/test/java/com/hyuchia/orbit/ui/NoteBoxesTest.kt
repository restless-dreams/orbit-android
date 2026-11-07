package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The boxes of a note's checklists, as the app finds and changes them: one line at a time, the rest of the note as it was. */
class NoteBoxesTest {
    private val packing = "Packing:\n\n- [ ] Boots\n- [x] Map\n  * [X] Compass\n\n> 1. [ ] Quoted\n> > - [ ] Deeper"

    @Test
    fun `a line's box says whether it's checked, and a line without one says nothing`() {
        assertEquals(listOf(null, null, false, true, true, null, false, false), (0..7).map { noteBoxOn(packing, it) })
        assertNull(noteBoxOn(packing, 99))
        assertNull(noteBoxOn("- see [x] here\n- [x]Not a box\n```\nnot a list [ ]\n```", 0))
        assertNull(noteBoxOn("- [x]Not a box", 0))
    }

    @Test
    fun `checking a box changes that one character, wherever the line is`() {
        assertEquals(packing.replace("- [ ] Boots", "- [x] Boots"), withNoteBox(packing, 2, true))
        assertEquals(packing.replace("* [X] Compass", "* [ ] Compass"), withNoteBox(packing, 4, false))
        assertEquals(packing.replace("> > - [ ] Deeper", "> > - [x] Deeper"), withNoteBox(packing, 7, true))
        assertEquals(packing.replace("> 1. [ ] Quoted", "> 1. [x] Quoted"), withNoteBox(packing, 6, true))
    }

    @Test
    fun `a box already that way, or a line without one, leaves the note as it is`() {
        assertEquals(packing, withNoteBox(packing, 3, true))
        assertEquals(packing, withNoteBox(packing, 4, true))
        assertNull(withNoteBox(packing, 0, true))
        assertNull(withNoteBox(packing, 1, true))
        assertNull(withNoteBox(packing, 42, true))
        assertNull(withNoteBox(packing, -1, true))
    }

    @Test
    fun `lines end at any line break, which the note keeps`() {
        val note = "Plan\r\n- [ ] One\r- [ ] Two\n- [ ] Three"
        assertEquals("Plan\r\n- [ ] One\r- [x] Two\n- [ ] Three", withNoteBox(note, 2, true))
        assertEquals(false, noteBoxOn(note, 3))
        assertEquals(listOf(0, 1, 2, 3), listOf(0, 6, 16, 26).map { lineAt(note, it) })
    }
}
