package com.hyuchia.orbit.widget

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.PrivateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapWidgetTest {
    private fun map(id: String, opened: Long, pinned: Boolean = false, private: Boolean = false) =
        MapSummary(id, MapOutline(id, branches = 0, ideas = 0), 0, MapMetadata(opened = opened, pinned = pinned, isPrivate = private), "", "")

    @Test
    fun `a widget shows its own map, or else the pinned one, or the one opened last`() {
        val maps = listOf(map("trip", opened = 3), map("garden", opened = 1, pinned = true), map("talk", opened = 2))
        assertEquals("talk", shownMap(maps, "talk")?.id)
        assertEquals("garden", shownMap(maps, null)?.id)
        assertEquals("trip", shownMap(maps.filterNot { it.metadata.pinned }, "gone")?.id)
    }

    @Test
    fun `a private map shows only on a widget that chose it`() {
        val maps = listOf(map("journal", opened = 9, pinned = true, private = true), map("garden", opened = 1))
        assertEquals("garden", shownMap(maps, null)?.id)
        assertEquals("journal", shownMap(maps, "journal")?.id)
        assertNull(shownMap(listOf(map("journal", opened = 9, private = true)), null))
    }

    @Test
    fun `the widget's list puts locked private maps last, nameless, or leaves them out while they hide`() {
        val maps = listOf(map("journal", opened = 4, private = true), map("trip", opened = 3), map("garden", opened = 1, pinned = true), map("talk", opened = 2))
        assertEquals(listOf("garden", "trip", "talk", "journal"), widgetChoices(maps, PrivateView(locked = true), hiding = false).map { it.id })
        assertEquals(listOf("garden", "trip", "talk"), widgetChoices(maps, PrivateView(locked = true), hiding = true).map { it.id })

        // Unlocked, a private map takes its place as any other.
        assertEquals(listOf("garden", "journal", "trip", "talk"), widgetChoices(maps, PrivateView(locked = false), hiding = true).map { it.id })
        assertEquals(listOf("garden", "journal", "trip", "talk"), widgetChoices(maps, PrivateView(locked = false), hiding = false).map { it.id })
    }

    @Test
    fun `the widget's list names private maps only while they're unlocked, which keeps it out of Recents`() {
        val maps = listOf(map("journal", opened = 4, private = true), map("trip", opened = 3))
        assertEquals(false, widgetNamesPrivateMaps(widgetChoices(maps, PrivateView(locked = true), hiding = false), PrivateView(locked = true)))
        assertEquals(true, widgetNamesPrivateMaps(widgetChoices(maps, PrivateView(locked = false), hiding = false), PrivateView(locked = false)))
        assertEquals(false, widgetNamesPrivateMaps(listOf(map("trip", opened = 3)), PrivateView(locked = false)))
    }

    @Test
    fun `a one-row widget keeps its progress's words up to Android's large text`() {
        assertEquals(true, rowHoldsWords(1f))
        assertEquals(true, rowHoldsWords(1.15f))
        assertEquals(false, rowHoldsWords(1.3f))
        assertEquals(false, rowHoldsWords(2f))
    }
}
