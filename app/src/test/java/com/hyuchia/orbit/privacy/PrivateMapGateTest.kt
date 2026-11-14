package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `a map that was open covers what's over it as it locks, and leaves a tablet's list beside it when nothing is`() {
        assertEquals(LockWindow.None, lockedWindow(locked = false, lockedAway = false, windowsOpen = true))
        // The menu's Lock now, or Ctrl+L, with a sheet, a dialog or a picture full screen open: a window covers them, and asks once tapped.
        assertEquals(LockWindow.Waiting, lockedWindow(locked = true, lockedAway = false, windowsOpen = true))
        // With nothing over the map, its lock in its place is enough: a tablet keeps its list of maps.
        assertEquals(LockWindow.None, lockedWindow(locked = true, lockedAway = false, windowsOpen = false))
        // Away, or with the screen off: whatever was left open is covered, and it asks as Orbit comes back.
        assertEquals(LockWindow.Asking, lockedWindow(locked = true, lockedAway = true, windowsOpen = false))
    }

    @Test
    fun `as private maps lock, the pictures being drawn of those that hide, or of maps not known yet, are called off`() = runTest {
        val lock = PrivateLock(clock = { 0L })
        lock.unlock()
        val maps = listOf(map("journal", private = true), map("groceries", private = false)).associateBy { it.id }
        val asked = mutableListOf<(String) -> Boolean>()
        backgroundScope.callOffPicturesAsPrivateMapsLock(lock, maps::get) { asked += it }
        runCurrent()
        assertEquals(0, asked.size)

        lock.lock()
        runCurrent()

        val hides = asked.single()
        assertTrue(hides("journal"))
        assertFalse(hides("groceries"))
        assertTrue(hides("not known yet"))
    }
}
