package com.hyuchia.orbit.settings

import com.hyuchia.orbit.data.FolderNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNotesTest {
    private fun quoted(title: String) = "“$title”"

    private fun privateMaps(count: Int) = if (count == 1) "a private map" else "$count private maps"

    @Test
    fun `a note names its maps, and counts the locked private ones at the end instead`() {
        val titles = listOf("Journal", "Groceries", "Health", "Trip")
        val private = setOf("Journal", "Health")
        assertEquals("“Groceries”, “Trip”, 2 private maps", noteEntries(titles, { it in private }, ::quoted, counted = ::privateMaps))
        assertEquals("a private map", noteEntries(listOf("Journal"), { it in private }, ::quoted, counted = ::privateMaps))
        // Unlocked, every map is named.
        assertEquals("“Journal”, “Groceries”, “Health”, “Trip”", noteEntries(titles, { false }, ::quoted, counted = ::privateMaps))
    }

    @Test
    fun `pictures too big for a private map aren't named either`() {
        val entries = listOf("scan.jpg, in “Journal”", "beach.mp4, in “Trip”")
        assertEquals(
            "beach.mp4, in “Trip”; a picture or video in a private map",
            noteEntries(entries, { it.endsWith("“Journal”") }, { it }, separator = "; ") { "a picture or video in a private map" },
        )
    }

    @Test
    fun `notes that name a private map, or a picture too big for one, keep Settings out of Recents while it's unlocked`() {
        val privateTitles = setOf("Journal")
        assertFalse(FolderNotes(keptBoth = listOf("Trip"), tooBig = listOf("beach.mp4, in “Trip”")).namesPrivateMaps(emptyList(), privateTitles, ::quoted))
        assertTrue(FolderNotes(removed = listOf("Journal")).namesPrivateMaps(emptyList(), privateTitles, ::quoted))
        assertTrue(FolderNotes(tooBig = listOf("scan.jpg, in “Journal”")).namesPrivateMaps(emptyList(), privateTitles, ::quoted))
        // A private map the sync noted before it went to the trash, say, is in the notes' own list.
        assertTrue(FolderNotes(restored = listOf("Health"), private = listOf("Health")).namesPrivateMaps(listOf("Health"), privateTitles, ::quoted))
        assertTrue(FolderNotes(large = listOf("Health.md"), private = listOf("Health.md")).namesPrivateMaps(listOf("Health.md"), privateTitles, ::quoted))
        // A map that came in private, as the sync couldn't tell it from a private map's file, is one.
        assertTrue(FolderNotes(cameInPrivate = listOf("Diary"), private = listOf("Diary")).namesPrivateMaps(listOf("Diary"), privateTitles, ::quoted))
    }

    @Test
    fun `maps that came in private are noted, read away, and left out while private maps hide`() {
        val notes = FolderNotes(cameInPrivate = listOf("Diary"), private = listOf("Diary"))
        assertFalse(notes.isEmpty)
        assertEquals(FolderNotes(cameInPrivate = listOf("Diary", "Plans"), private = listOf("Diary", "Plans")), notes + FolderNotes(cameInPrivate = listOf("Plans"), private = listOf("Plans")))
        assertTrue((notes - FolderNotes(cameInPrivate = listOf("Diary"))).isEmpty)
        assertEquals(emptyList<String>(), notes.shown(concealing = true, hiddenMap = { it == "Diary" }, hiddenFile = { false }).cameInPrivate)
        assertEquals(listOf("Diary"), notes.shown(concealing = false, hiddenMap = { it == "Diary" }, hiddenFile = { false }).cameInPrivate)
    }
}
