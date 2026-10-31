package com.hyuchia.orbit.map

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BesideRoomTest {
    @Test
    fun `with nothing beside the map, messages keep where they are`() {
        assertEquals(0.dp, besideRoom(beside = 0.dp, scaffoldEnd = 48.dp, rail = 80.dp))
    }

    @Test
    fun `a panel beside the map keeps messages clear of it, less the bars the scaffold keeps them from already`() {
        assertEquals(381.dp, besideRoom(beside = 381.dp, scaffoldEnd = 0.dp, rail = 0.dp))
        assertEquals(333.dp, besideRoom(beside = 381.dp, scaffoldEnd = 48.dp, rail = 0.dp))
        // Bars wider than what's beside: the scaffold's room is enough.
        assertEquals(0.dp, besideRoom(beside = 20.dp, scaffoldEnd = 48.dp, rail = 0.dp))
    }

    @Test
    fun `on a short screen the rail at the map's side is kept clear too`() {
        assertEquals(461.dp, besideRoom(beside = 381.dp, scaffoldEnd = 0.dp, rail = 80.dp))
    }
}
