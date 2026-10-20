package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeMenuTest {
    private val trip = listOf(
        MapNode("trip", "Trip"),
        MapNode("food", "Food", parentId = "trip", childCount = 2),
        MapNode("bakeries", "Bakeries", parentId = "food", childCount = 2),
        MapNode("nata", "Pastéis de nata", parentId = "bakeries"),
        MapNode("bolo", "Bolo de arroz", parentId = "bakeries"),
        MapNode("sardines", "Sardines", parentId = "food"),
        MapNode("museums", "Museums", parentId = "trip"),
    )

    @Test
    fun deleteCountsEveryIdeaItTakesWithIt() {
        assertEquals(4, ideasUnder(trip, "food"))
        assertEquals(2, ideasUnder(trip, "bakeries"))
        assertEquals(0, ideasUnder(trip, "museums"))
        assertEquals(6, ideasUnder(trip, "trip"))
    }

    @Test
    fun aMapOfAnyDepthCounts() {
        val chain = listOf(MapNode("top", "Top")) + (1..100_000).map { MapNode("step-$it", "Step $it", parentId = if (it == 1) "top" else "step-${it - 1}") }
        assertEquals(100_000, ideasUnder(chain, "top"))
    }
}
