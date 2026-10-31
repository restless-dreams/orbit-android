package com.hyuchia.orbit.home

import com.hyuchia.orbit.data.DropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDropsTest {
    @Test
    fun `the list of maps takes map files, and text to make a map of, but not pictures alone`() {
        assertEquals(HomeDrop.Maps, homeDrop(setOf(DropKind.Maps)))
        assertEquals(HomeDrop.Maps, homeDrop(setOf(DropKind.Maps, DropKind.Ideas)))
        assertEquals(HomeDrop.Text, homeDrop(setOf(DropKind.Ideas)))
        assertNull(homeDrop(setOf(DropKind.Pictures)))
        assertNull(homeDrop(setOf(DropKind.Videos)))
        // A sound isn't a map, and the list has nothing else to make of it.
        assertNull(homeDrop(setOf(DropKind.Sounds)))
        assertEquals(HomeDrop.Maps, homeDrop(setOf(DropKind.Sounds, DropKind.Maps)))
        assertNull(homeDrop(emptySet()))
    }
}
