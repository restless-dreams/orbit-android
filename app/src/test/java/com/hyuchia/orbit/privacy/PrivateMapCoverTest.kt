package com.hyuchia.orbit.privacy

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateMapCoverTest {
    @Test
    fun `the lock fits every window a phone gives it, whole or split, either way up`() {
        assertEquals(CoverLayout.Whole, coverLayout(411.dp, 830.dp))
        // On its side, the words stand beside the lock.
        assertEquals(CoverLayout.Beside, coverLayout(840.dp, 370.dp))
        // Half a phone in split screen: upright, and on its side, side by side.
        assertEquals(CoverLayout.Small, coverLayout(411.dp, 400.dp))
        assertEquals(CoverLayout.Small, coverLayout(440.dp, 370.dp))
        // A third of one, where Android lets the divider go.
        assertEquals(CoverLayout.Small, coverLayout(411.dp, 260.dp))
    }

    @Test
    fun `either half of a tablet has room for the lock above its words`() {
        assertEquals(CoverLayout.Whole, coverLayout(1280.dp, 752.dp))
        // On its side, side by side.
        assertEquals(CoverLayout.Whole, coverLayout(636.dp, 752.dp))
        // Upright, one above the other.
        assertEquals(CoverLayout.Stacked, coverLayout(800.dp, 600.dp))
    }
}
