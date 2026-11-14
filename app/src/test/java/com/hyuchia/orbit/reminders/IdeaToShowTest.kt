package com.hyuchia.orbit.reminders

import com.hyuchia.orbit.page.MapNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The idea a reminder opens its map at, found among the ideas Orbit read, as the light reading named it. */
class IdeaToShowTest {
    private val nodes = listOf(
        MapNode("trip", "Trip"),
        MapNode("flights", "Book the flights", parentId = "trip", depth = 1, index = 0, icon = "✈️", due = "2026-11-18 18:30"),
        MapNode("packing", "Packing", parentId = "trip", depth = 1, index = 1, childCount = 2),
        MapNode("bags", "Bags", parentId = "packing", depth = 2, index = 0, due = "2026-11-19"),
        MapNode("bags-2", "Bags", parentId = "packing", depth = 2, index = 1, due = "2026-11-20"),
    )

    @Test
    fun `by its date and its name, as written with its icon or marks the light reading keeps`() {
        assertEquals("flights", ideaAmong(nodes, IdeaToShow("map", "✈️ Book the flights", "2026-11-18 18:30", listOf(9)))?.id)
        assertEquals("flights", ideaAmong(nodes, IdeaToShow("map", "Book *the* flights", "2026-11-18 18:30", emptyList()))?.id)
        // Two ideas of the same name: the one with the date.
        assertEquals("bags-2", ideaAmong(nodes, IdeaToShow("map", "Bags", "2026-11-20", emptyList()))?.id)
    }

    @Test
    fun `renamed since, by its place with its date, or by its date alone`() {
        assertEquals("bags", ideaAmong(nodes, IdeaToShow("map", "Suitcases", "2026-11-19", listOf(1, 0)))?.id)
        assertEquals("bags", ideaAmong(nodes, IdeaToShow("map", "Suitcases", "2026-11-19", listOf(4)))?.id)
    }

    @Test
    fun `of several with its name and date, the one at its place, else the one as many along as the reminder's`() {
        val twice = nodes + listOf(
            MapNode("errands", "Errands", parentId = "trip", depth = 1, index = 2, childCount = 1),
            MapNode("bags-3", "Bags", parentId = "errands", depth = 2, index = 0, due = "2026-11-19"),
        )

        assertEquals("bags-3", ideaAmong(twice, IdeaToShow("map", "Bags", "2026-11-19", listOf(2, 0), occurrence = 1))?.id)
        assertEquals("bags", ideaAmong(twice, IdeaToShow("map", "Bags", "2026-11-19", listOf(1, 0), occurrence = 0))?.id)
        // Moved since: the second of them still.
        assertEquals("bags-3", ideaAmong(twice, IdeaToShow("map", "Bags", "2026-11-19", listOf(7, 7), occurrence = 1))?.id)
        assertEquals("bags", ideaAmong(twice, IdeaToShow("map", "Bags", "2026-11-19", listOf(7, 7), occurrence = 4))?.id)
    }

    @Test
    fun `none when no idea has its date anymore`() {
        assertNull(ideaAmong(nodes, IdeaToShow("map", "Book the flights", "2026-12-01", listOf(0))))
        assertNull(ideaAmong(emptyList(), IdeaToShow("map", "Book the flights", "2026-11-18 18:30", listOf(0))))
    }
}
