package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateLockTest {
    private var now = 1_000_000L
    private val minute = 60_000L
    private val lock = PrivateLock(clock = { now })

    private fun map(id: String, private: Boolean) = MapSummary(id, MapOutline(id, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    @Test
    fun `private maps start locked, and one unlock opens them all until they lock again`() {
        assertTrue(lock.view.value.locked)
        assertTrue(lock.view.value.hides(map("journal", private = true)))
        assertFalse(lock.view.value.hides(map("groceries", private = false)))

        lock.unlock()
        assertFalse(lock.view.value.hides(map("journal", private = true)))
        assertFalse(lock.view.value.hides(map("health", private = true)))

        lock.lock()
        assertTrue(lock.view.value.hides(map("health", private = true)))
    }

    @Test
    fun `they lock again once Orbit was away for the chosen time, and not before`() {
        lock.delay = minute
        lock.unlock()

        lock.left()
        now += minute - 1
        lock.returned()
        assertFalse(lock.view.value.locked)

        lock.left()
        now += minute
        lock.returned()
        assertTrue(lock.view.value.locked)
    }

    @Test
    fun `right away locks them on any return, and coming back without leaving changes nothing`() {
        lock.delay = 0
        lock.unlock()
        lock.returned()
        assertFalse(lock.view.value.locked)

        lock.left()
        lock.returned()
        assertTrue(lock.view.value.locked)
    }

    @Test
    fun `a clock set back while Orbit was away locks them, whatever the setting`() {
        lock.delay = 15 * minute
        lock.unlock()

        lock.left()
        now -= 60 * minute
        lock.returned()
        assertTrue(lock.view.value.locked)
    }

    @Test
    fun `turning the screen off locks them at once, whatever the setting`() {
        lock.delay = 15 * minute
        lock.unlock()
        lock.screenOff()
        assertTrue(lock.view.value.locked)
    }

    @Test
    fun `the phone asking for its screen lock isn't leaving`() {
        lock.delay = 0
        lock.asking = true
        lock.left()
        now += 5 * minute
        lock.asking = false
        lock.unlock()
        lock.returned()
        assertFalse(lock.view.value.locked)
    }

    @Test
    fun `a map made private while the others are locked stays open until they lock again`() {
        lock.keepOpen("journal")
        assertFalse(lock.view.value.hides(map("journal", private = true)))
        assertTrue(lock.view.value.hides(map("health", private = true)))

        lock.lock()
        assertTrue(lock.view.value.hides(map("journal", private = true)))
    }

    @Test
    fun `unlocked, a map made private needs nothing kept, and the next lock covers it`() {
        lock.unlock()
        lock.keepOpen("journal")
        assertEquals(emptySet<String>(), lock.view.value.keptOpen)
        lock.lock()
        assertTrue(lock.view.value.hides(map("journal", private = true)))
    }

    @Test
    fun `without a screen lock nothing locks, and once there is one they lock`() {
        val open = PrivateLock(clock = { now }, screenLock = false)
        assertFalse(open.view.value.locked)
        assertFalse(open.view.value.lockable)
        open.lock()
        assertFalse(open.view.value.hides(map("journal", private = true)))

        open.screenLockIs(true)
        assertTrue(open.view.value.locked)
        assertTrue(open.view.value.lockable)
        assertTrue(open.view.value.hides(map("journal", private = true)))
    }

    @Test
    fun `a map made private while the others were locked locks with them, and screens hear it`() = runTest {
        var heard = 0
        val listening = launch { lock.newlyLocked.collect { heard += 1 } }
        runCurrent()

        // Locked from the start; a map made private now stays as it is, on screen.
        lock.keepOpen("journal")
        runCurrent()
        assertFalse(lock.view.value.hides(map("journal", private = true)))

        lock.screenOff()
        runCurrent()
        assertTrue(lock.view.value.hides(map("journal", private = true)))
        assertEquals(1, heard)
        listening.cancel()
    }

    @Test
    fun `screens hear each time private maps lock, but not that they were locked already`() = runTest {
        var heard = 0
        val listening = launch { lock.newlyLocked.collect { heard += 1 } }
        runCurrent()
        assertEquals(0, heard)

        lock.unlock()
        runCurrent()
        lock.lock()
        runCurrent()
        assertEquals(1, heard)

        // Locked already: nothing changes.
        lock.screenOff()
        runCurrent()
        assertEquals(1, heard)

        lock.unlock()
        runCurrent()
        lock.left()
        now += 5 * minute
        lock.returned()
        runCurrent()
        assertEquals(2, heard)
        listening.cancel()
    }
}
