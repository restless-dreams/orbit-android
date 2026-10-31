package com.hyuchia.orbit

import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.MapBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ArrivalsTest {
    private fun otherVersion(title: String, opens: Boolean = false) =
        OtherVersion(ImportResult.AlreadyHere(MapBundle("# $title\n", title, pictures = emptyMap(), uuid = "uuid-$title", focus = null, view = null), existing = title.lowercase()), opens)

    private val trip = otherVersion("Trip")
    private val plan = otherVersion("Plan")
    private val ideas = otherVersion("Ideas")

    @Test
    fun `other versions are asked about one after another, each saying where it is among them`() {
        var queue = OtherVersions() + listOf(trip, plan, ideas)
        assertSame(trip, queue.asked)
        assertEquals(1 to 3, queue.number to queue.count)

        queue = queue.after(trip)
        assertSame(plan, queue.asked)
        assertEquals(2 to 3, queue.number to queue.count)

        queue = queue.after(plan)
        assertSame(ideas, queue.asked)
        assertEquals(3 to 3, queue.number to queue.count)

        queue = queue.after(ideas)
        assertNull(queue.asked)
    }

    @Test
    fun `files that come while others are asked about wait their turn, and counting starts again once all are answered`() {
        var queue = OtherVersions() + listOf(trip, plan)
        queue = queue.after(trip)
        queue += listOf(ideas)
        assertSame(plan, queue.asked)
        assertEquals(2 to 3, queue.number to queue.count)

        queue = queue.after(plan).after(ideas)
        assertEquals(OtherVersions(), queue)

        val single = otherVersion("Lisbon", opens = true)
        queue += listOf(single)
        assertEquals(1 to 1, queue.number to queue.count)
    }

    @Test
    fun `a second answer to a question already answered asks nothing of the next one`() {
        val queue = (OtherVersions() + listOf(trip, plan)).after(trip)
        assertSame(queue, queue.after(trip))
        assertSame(plan, queue.after(trip).asked)
    }
}
