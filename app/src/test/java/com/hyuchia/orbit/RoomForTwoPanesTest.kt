package com.hyuchia.orbit

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomForTwoPanesTest {
    @Test
    fun `a tablet has room for the list beside a map whichever way up it is`() {
        assertTrue(roomForTwoPanes(DpSize(1280.dp, 800.dp)))
        assertTrue(roomForTwoPanes(DpSize(800.dp, 1280.dp)))
        // A small tablet, and a foldable's inner screen, just wide enough on their sides.
        assertTrue(roomForTwoPanes(DpSize(600.dp, 960.dp)))
        assertTrue(roomForTwoPanes(DpSize(841.dp, 701.dp)))
    }

    @Test
    fun `a phone has none, upright or on its side, too short for two panes there`() {
        assertFalse(roomForTwoPanes(DpSize(411.dp, 914.dp)))
        assertFalse(roomForTwoPanes(DpSize(914.dp, 411.dp)))
        assertFalse(roomForTwoPanes(DpSize(360.dp, 640.dp)))
        // Half a tablet, in split screen.
        assertFalse(roomForTwoPanes(DpSize(640.dp, 800.dp)))
    }
}
