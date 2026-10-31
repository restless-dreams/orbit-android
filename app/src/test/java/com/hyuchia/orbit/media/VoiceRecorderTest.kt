package com.hyuchia.orbit.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecorderTest {
    @Test
    fun `the waveform hears levels as ears do, from a quiet room to the loudest`() {
        assertEquals(0f, level(0), 0f)
        assertEquals(1f, level(32_767), 0.001f)
        // A third of the loudest is still most of the way up: a voice, not a whisper.
        assertTrue(level(10_000) > 0.75f)
        assertTrue(level(100) < level(1_000))
        // The noise of a quiet room draws nothing.
        assertEquals(0f, level(90), 0.05f)
    }

    @Test
    fun `a take drawn in fewer bars keeps each part's loudest, so no word is lost`() {
        val levels = listOf(0.1f, 0.9f, 0.2f, 0.3f, 0.8f, 0.1f, 0.4f, 0.5f)
        assertEquals(listOf(0.9f, 0.3f, 0.8f, 0.5f), barsOf(levels, 4))
        assertEquals(levels, barsOf(levels, 8))
        assertEquals(emptyList<Float>(), barsOf(emptyList(), 4))
        assertEquals(emptyList<Float>(), barsOf(levels, 0))
        assertEquals(3, barsOf(levels, 3).size)
    }

    @Test
    fun `a short take spreads across the bars there are`() {
        assertEquals(listOf(0.1f, 0.1f, 0.9f, 0.9f, 0.4f, 0.4f), barsOf(listOf(0.1f, 0.9f, 0.4f), 6))
        assertEquals(List(5) { 0.7f }, barsOf(listOf(0.7f), 5))
    }

    @Test
    fun `players write times as people read them`() {
        assertEquals("0:00", soundTime(0))
        assertEquals("0:42", soundTime(42_900))
        assertEquals("3:05", soundTime(185_000))
        assertEquals("1:02:09", soundTime(3_729_000))
        assertEquals("--:--", soundTime(null))
        assertEquals("--:--", soundTime(-1))
    }
}
