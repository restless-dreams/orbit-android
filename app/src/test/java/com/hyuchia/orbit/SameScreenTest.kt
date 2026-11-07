package com.hyuchia.orbit

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A map on a tablet that takes the whole window, or gives the list its room
 * back, changes scene with the same screen in front, which shows in the new
 * scene at once; a step to another screen still animates.
 */
class SameScreenTest {
    private class Panes(override val key: Any, override val entries: List<NavEntry<String>>) : Scene<String> {
        override val previousEntries: List<NavEntry<String>> = entries.dropLast(1)

        override val content: @Composable () -> Unit = {}
    }

    private fun entry(key: String) = NavEntry(key) {}

    @Test
    fun `a map leaving the list's side, or coming back beside it, is the same screen`() {
        val home = entry("home")
        val map = entry("map plans")
        val beside = Panes("list and map", listOf(home, map))
        val alone = Panes("map", listOf(map))

        assertTrue(showsSameScreen(beside, alone))
        assertTrue(showsSameScreen(alone, beside))
    }

    @Test
    fun `opening another map, or going back to the list, is a step`() {
        val home = entry("home")
        val list = Panes("list", listOf(home))
        val plans = Panes("list and map", listOf(home, entry("map plans")))
        val trip = Panes("list and map", listOf(home, entry("map trip")))

        assertFalse(showsSameScreen(plans, trip))
        assertFalse(showsSameScreen(plans, list))
        assertFalse(showsSameScreen(list, plans))
    }
}
