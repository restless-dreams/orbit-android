package com.hyuchia.orbit.map

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationLockingTest {
    @Test
    fun `a presentation stops as private maps lock, and unlocked, presenting again is offered once`() {
        val locking = PresentationLocking()
        assertEquals(LockingStep.Nothing, locking.changed(locked = false, presenting = true))
        assertEquals(LockingStep.Stop, locking.changed(locked = true, presenting = true))
        // Stopped: no presentation any more while they're locked.
        assertEquals(LockingStep.Nothing, locking.changed(locked = true, presenting = false))
        assertEquals(LockingStep.OfferAgain, locking.changed(locked = false, presenting = false))
        assertEquals(LockingStep.Nothing, locking.changed(locked = false, presenting = false))
    }

    @Test
    fun `a rehearsal stops too, but isn't offered again, and locking with nothing presented does nothing`() {
        val locking = PresentationLocking()
        assertEquals(LockingStep.Stop, locking.changed(locked = true, presenting = true, rehearsing = true))
        assertEquals(LockingStep.Nothing, locking.changed(locked = false, presenting = false))

        assertEquals(LockingStep.Nothing, locking.changed(locked = true, presenting = false))
        assertEquals(LockingStep.Nothing, locking.changed(locked = false, presenting = false))
    }
}
