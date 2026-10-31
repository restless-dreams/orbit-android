package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitSliderTest {
    // The drift's speed: a turn every 20 to 180 seconds.
    private val range = 20f..180f

    @Test
    fun `a finger's place is how far along the track it is, the thumb's middle going so far in from each side`() {
        assertEquals(0f, fractionAt(x = 0f, width = 200f, inset = 10f, rtl = false), 0.0001f)
        assertEquals(0f, fractionAt(x = 10f, width = 200f, inset = 10f, rtl = false), 0.0001f)
        assertEquals(0.5f, fractionAt(x = 100f, width = 200f, inset = 10f, rtl = false), 0.0001f)
        assertEquals(1f, fractionAt(x = 195f, width = 200f, inset = 10f, rtl = false), 0.0001f)
        // Right to left, the start is on the right.
        assertEquals(0.75f, fractionAt(x = 55f, width = 200f, inset = 10f, rtl = true), 0.0001f)
        // With no room for the thumb to move, it's at the start.
        assertEquals(0f, fractionAt(x = 5f, width = 10f, inset = 10f, rtl = false), 0.0001f)
    }

    @Test
    fun `values and how far along they are go both ways, kept within the range`() {
        assertEquals(100f, valueAt(0.5f, range), 0.0001f)
        assertEquals(0.5f, fractionOf(100f, range), 0.0001f)
        assertEquals(0f, fractionOf(0f, range), 0.0001f)
        assertEquals(1f, fractionOf(500f, range), 0.0001f)
        assertEquals(0f, fractionOf(5f, 5f..5f), 0.0001f)
    }

    @Test
    fun `keys move a hundredth, Page Up and Down a tenth, and Home and End to the ends`() {
        assertEquals(101.6f, stepped(100f, range, SliderStep.Forward), 0.001f)
        assertEquals(98.4f, stepped(100f, range, SliderStep.Back), 0.001f)
        assertEquals(116f, stepped(100f, range, SliderStep.PageForward), 0.001f)
        assertEquals(84f, stepped(100f, range, SliderStep.PageBack), 0.001f)
        assertEquals(20f, stepped(100f, range, SliderStep.Start), 0f)
        assertEquals(180f, stepped(100f, range, SliderStep.End), 0f)
        // Never past the ends.
        assertEquals(180f, stepped(179f, range, SliderStep.PageForward), 0f)
        assertEquals(20f, stepped(21f, range, SliderStep.PageBack), 0f)
    }

    @Test
    fun `the arrow keys move as far as a slider says, as a sound's timeline moves five seconds`() {
        // A three-minute sound, from its start to its end: five seconds is a 36th of the way.
        val fiveSeconds = 5f / 180f
        assertEquals(0.5f + fiveSeconds, stepped(0.5f, 0f..1f, SliderStep.Forward, fiveSeconds), 0.0001f)
        assertEquals(0.5f - fiveSeconds, stepped(0.5f, 0f..1f, SliderStep.Back, fiveSeconds), 0.0001f)
        // Page Up and Page Down still go a tenth, and nothing goes past the ends.
        assertEquals(0.6f, stepped(0.5f, 0f..1f, SliderStep.PageForward, fiveSeconds), 0.0001f)
        assertEquals(1f, stepped(0.99f, 0f..1f, SliderStep.Forward, fiveSeconds), 0f)
        assertEquals(0f, stepped(0.01f, 0f..1f, SliderStep.Back, fiveSeconds), 0f)
    }

    @Test
    fun `a finger passes a stop each tenth of the way, or each sixteenth for the drift's 10 seconds`() {
        assertEquals(0, stopOf(20f, range, 16))
        assertEquals(0, stopOf(29.9f, range, 16))
        assertEquals(1, stopOf(30f, range, 16))
        assertEquals(8, stopOf(100f, range, 16))
        assertEquals(16, stopOf(180f, range, 16))
        assertEquals(5, stopOf(0.5f, 0f..1f, 10))
        // No stops, no ticks.
        assertEquals(0, stopOf(100f, range, 0))
    }
}
