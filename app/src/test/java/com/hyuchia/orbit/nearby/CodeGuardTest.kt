package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CodeGuardTest {
    /** What a phone in English shows. */
    private val english = FileWords(Locale.US)

    private var now = 1_000_000L
    private val guard = CodeGuard(clock = { now })
    private val second = 1_000L

    @Test
    fun `codes have six digits`() {
        repeat(1_000) {
            val code = newCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
            assertTrue(looksLikeCode(code))
        }

        assertFalse(looksLikeCode("favicon.ico"))
        assertFalse(looksLikeCode(null))
        assertFalse(looksLikeCode(""))
    }

    @Test
    fun `a device gets five wrong codes, then waits longer after each one`() {
        repeat(4) { assertEquals(0L, guard.wrong("192.168.1.20")) }
        assertEquals(0L, guard.lockedOut.value)
        assertEquals(30 * second, guard.wrong("192.168.1.20"))
        assertEquals(now, guard.lockedOut.value)
        assertEquals("Too many wrong codes. Try again in a minute, with the code on the phone.", guard.waitingMessage("192.168.1.20", english))

        now += 30 * second
        assertEquals(0L, guard.waitFor("192.168.1.20"))
        assertEquals(60 * second, guard.wrong("192.168.1.20"))
        now += 60 * second
        assertEquals(120 * second, guard.wrong("192.168.1.20"))
        assertEquals("Too many wrong codes. Try again in 2 minutes, with the code on the phone.", guard.waitingMessage("192.168.1.20", english))

        // Other devices aren't kept waiting for it.
        assertEquals(0L, guard.waitFor("192.168.1.21"))
    }

    @Test
    fun `waits stop growing at an hour`() {
        repeat(40) { guard.wrong("192.168.1.20") }
        assertEquals(60 * 60 * second, guard.waitFor("192.168.1.20"))
    }

    @Test
    fun `the right code starts a device over`() {
        repeat(4) { guard.wrong("192.168.1.20") }
        guard.right("192.168.1.20")
        repeat(4) { assertEquals(0L, guard.wrong("192.168.1.20")) }
    }

    @Test
    fun `guessing from address after address keeps every device waiting`() {
        // A room's worth of mistakes is fine.
        for (device in 1..99) {
            assertEquals(0L, guard.wrong("10.0.0.$device"))
        }

        assertEquals(30 * second, guard.wrong("10.0.1.1"))
        assertEquals(30 * second, guard.waitFor("10.0.2.2"))
    }

    @Test
    fun `a guard without a shared budget only keeps each device waiting`() {
        val pins = CodeGuard(clock = { now }, together = null)

        for (device in 1..200) {
            assertEquals(0L, pins.wrong("10.0.0.$device"))
        }

        assertEquals(0L, pins.waitFor("10.0.9.9"))
        repeat(4) { pins.wrong("10.0.9.9") }
        assertEquals(30 * second, pins.wrong("10.0.9.9"))
        assertEquals(
            "Too many wrong PINs. Try again in a minute, with the PIN on the presenter's phone.",
            pins.waitingMessage("10.0.9.9", english, R.plurals.nearby_too_many_wrong_pins_for_the_presentation),
        )
    }

    @Test
    fun `a new code starts everyone over`() {
        repeat(100) { guard.wrong("10.0.0.${it % 50}") }
        assertTrue(guard.waitFor("10.0.3.3") > 0)
        guard.reset()
        assertEquals(0L, guard.waitFor("10.0.3.3"))
        assertEquals(0L, guard.waitFor("10.0.0.1"))
        assertEquals(0L, guard.lockedOut.value)
    }

    @Test
    fun `pins have four digits`() {
        repeat(1_000) {
            assertTrue(Regex("[0-9]{4}").matches(newPin()))
        }
    }
}
