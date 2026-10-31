package com.hyuchia.orbit

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOpeningTest {
    @Test
    fun `a map open beside the list stays to present or study, whatever its key asked for before`() {
        // Studied once, then stopped: its key still says "study", and its screen started that already.
        val studied = MapKey("trip", study = true)
        val backStack = listOf(HomeKey, studied)

        for ((present, study) in listOf(false to true, true to false)) {
            val opening = mapOpening(backStack, "trip", present, study)
            assertEquals(backStack, opening.backStack)
            assertSame(studied, opening.backStack.last())
            assertTrue(opening.startsInPlace)
        }
    }

    @Test
    fun `screens over the map close, and the map stays to start there`() {
        val open = MapKey("trip", present = true)
        val opening = mapOpening(listOf(HomeKey, open, SettingsKey, AboutKey), "trip", present = false, study = true)
        assertEquals(listOf(HomeKey, open), opening.backStack)
        assertTrue(opening.startsInPlace)
    }

    @Test
    fun `another map takes the open one's place, and starts once it's drawn`() {
        val opening = mapOpening(listOf(HomeKey, MapKey("trip")), "notes", present = false, study = true)
        assertEquals(listOf(HomeKey, MapKey("notes", study = true)), opening.backStack)
        assertFalse(opening.startsInPlace)
    }

    @Test
    fun `with no map open, the map comes beside the list`() {
        val opening = mapOpening(listOf(HomeKey, NewMapKey), "trip", present = true, study = false)
        assertEquals(listOf(HomeKey, MapKey("trip", present = true)), opening.backStack)
        assertFalse(opening.startsInPlace)
    }

    @Test
    fun `only showing a map works as it did`() {
        val shown = listOf(HomeKey, MapKey("trip"))
        assertEquals(shown, mapOpening(shown, "trip", present = false, study = false).backStack)
        assertEquals(shown, mapOpening(listOf(HomeKey, MapKey("trip", study = true)), "trip", present = false, study = false).backStack)
        assertFalse(mapOpening(shown, "trip", present = false, study = false).startsInPlace)
    }

    @Test
    fun `a back stack becomes the one wanted, keeping the keys both have`() {
        val open = MapKey("trip", study = true)
        val backStack = mutableListOf<NavKey>(HomeKey, open, SettingsKey)
        backStack.becomes(listOf(HomeKey, open))
        assertEquals(listOf(HomeKey, open), backStack)
        assertSame(open, backStack.last())

        backStack.becomes(listOf(HomeKey, MapKey("notes")))
        assertEquals(listOf(HomeKey, MapKey("notes")), backStack)
    }
}
