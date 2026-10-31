package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateMapGateTest {
    private fun map(id: String, private: Boolean) = MapSummary(id, MapOutline(id, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    @Test
    fun `before the maps are read, as at a cold start, whether a map is private isn't known, and it counts as locked`() {
        assertNull(mapIsPrivate(null, "journal"))
        assertEquals(Gate.Waiting, gate(private = null, hides = true, shown = false))
        // Even one shown before, in a screen brought back: nothing of it until it's known.
        assertEquals(Gate.Waiting, gate(private = null, hides = false, shown = true))
    }

    @Test
    fun `once known, a locked private map shows its lock alone, and the map only once it was on screen in this run`() {
        val maps = listOf(map("journal", private = true), map("groceries", private = false))
        assertEquals(true, mapIsPrivate(maps, "journal"))
        assertEquals(false, mapIsPrivate(maps, "groceries"))
        // A map that isn't there isn't private.
        assertEquals(false, mapIsPrivate(maps, "gone"))

        assertEquals(Gate.Locked, gate(private = true, hides = true, shown = false))
        assertEquals(Gate.LockedOver, gate(private = true, hides = true, shown = true))
        assertEquals(Gate.Open, gate(private = true, hides = false, shown = false))
        assertEquals(Gate.Open, gate(private = false, hides = true, shown = false))
    }

    @Test
    fun `a screen made again while the map is locked shows the lock alone, so nothing made again comes above it`() {
        // Shown in this run, but not on this screen: a new language or font size made it again.
        assertEquals(Gate.Locked, gate(private = true, hides = true, shown = true, openHere = false))
        assertEquals(Gate.LockedOver, gate(private = true, hides = true, shown = true, openHere = true))
        // Unlocked, it opens on the new screen as on any other.
        assertEquals(Gate.Open, gate(private = true, hides = false, shown = true, openHere = false))
    }
}
