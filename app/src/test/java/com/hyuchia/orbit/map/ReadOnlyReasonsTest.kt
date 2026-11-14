package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Why the map's screen takes no edits, as the page says it: each reason it gives, and one the screen doesn't know. */
class ReadOnlyReasonsTest {
    private fun read(reason: String?): MapUi = MapUi(ready = true).reading(MapState("Trip", listOf(MapNode("trip", "Trip")), readOnly = reason))

    @Test
    fun `each reason the page gives locks the map for it`() {
        assertNull(read(null).readOnly)
        assertTrue(read(null).takesEdits)
        assertEquals(ReadOnly.Unreadable, read("unreadable").readOnly)
        assertEquals(ReadOnly.Newer, read("newer").readOnly)
        assertEquals(ReadOnly.Deleted, read("deleted").readOnly)
        assertEquals(ReadOnly.Storage, read("storage").readOnly)
    }

    @Test
    fun `a reason the screen doesn't know keeps the map locked, rather than offering edits the page refuses`() {
        val ui = read("archived")

        assertEquals(ReadOnly.Newer, ui.readOnly)
        assertFalse(ui.takesEdits)
        assertFalse(canEditText(ui.readOnly))
    }
}
