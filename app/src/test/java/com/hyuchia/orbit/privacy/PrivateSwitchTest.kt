package com.hyuchia.orbit.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateSwitchTest {
    @Test
    fun `the switch turns a map private, and back, showing which it is`() {
        assertEquals(PrivateSwitchStep.Switch(private = true), privateSwitchStep(private = false, screenLock = true))
        assertEquals(PrivateSwitchStep.Switch(private = false), privateSwitchStep(private = true, screenLock = true))
    }

    @Test
    fun `without a screen lock, turning it on asks for one first, while turning it off never asks`() {
        assertEquals(PrivateSwitchStep.AskForScreenLock, privateSwitchStep(private = false, screenLock = false))
        // A screen lock removed after the map was made private: it can still be made not private.
        assertEquals(PrivateSwitchStep.Switch(private = false), privateSwitchStep(private = true, screenLock = false))
    }
}
