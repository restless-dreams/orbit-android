package com.hyuchia.orbit.settings

import com.hyuchia.orbit.data.FolderNotes
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.privacy.PrivateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class PrivateMapsHidingTest {
    private fun trashed(id: String, private: Boolean = false) = TrashedMap(id, id, deleted = 0, ideas = 0, isPrivate = private, folder = File(id))

    @Test
    fun `the trash leaves private maps out while they hide, and shows them locked otherwise`() {
        val trash = listOf(trashed("journal", private = true), trashed("groceries"))
        assertEquals(listOf("groceries"), shownTrash(trash, PrivateView(locked = true), hiding = true).map { it.id })
        assertEquals(trash, shownTrash(trash, PrivateView(locked = true), hiding = false))
        assertEquals(trash, shownTrash(trash, PrivateView(locked = false), hiding = true))
    }

    @Test
    fun `while private maps hide, the folder's notes leave them out, so a note only about them isn't there`() {
        val notes = FolderNotes(keptBoth = listOf("Journal (changed elsewhere)", "Trip (changed elsewhere)"), restored = listOf("Journal"), tooBig = listOf("scan.jpg, in “Journal”"))
        val privateMap = { title: String -> title.startsWith("Journal") }
        val privateFile = { entry: String -> entry.endsWith("“Journal”") }

        val shown = notes.shown(concealing = true, privateMap, privateFile)
        assertEquals(listOf("Trip (changed elsewhere)"), shown.keptBoth)
        assertEquals(emptyList<String>(), shown.restored)
        assertEquals(emptyList<String>(), shown.tooBig)
        assertEquals(false, shown.isEmpty)
        assertEquals(true, FolderNotes(restored = listOf("Journal")).shown(concealing = true, privateMap, privateFile).isEmpty)

        // Not hiding: every note, with private maps counted rather than named (see noteEntries).
        assertSame(notes, notes.shown(concealing = false, privateMap, privateFile))
    }
}
