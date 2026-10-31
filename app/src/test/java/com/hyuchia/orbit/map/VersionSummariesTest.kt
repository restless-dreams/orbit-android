package com.hyuchia.orbit.map

import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.VersionChanges
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionSummariesTest {
    private val opml = """<?xml version="1.0"?><opml version="2.0"><head><title>Trip</title></head><body><outline text="Trip"><outline text="Flights"/><outline text="Hotels"/></outline></body></opml>"""

    /** A history, the newest first: an edit, a file as it came in, an earlier edit, one that can't be read, and the oldest. */
    private val texts = mapOf(
        50L to "# Trip\n## Flights\n## Hotels\n- Alfama\n",
        40L to opml,
        30L to "# Trip\n## Flights\n## Food\n",
        20L to null,
        10L to "# Trip\n## Flights\n",
    )

    private val versions = listOf(MapVersion(50), MapVersion(40, original = true), MapVersion(30), MapVersion(20), MapVersion(10))

    /** The versions read, in order. */
    private val reads = mutableListOf<Long>()

    private val summaries = VersionSummaries { time ->
        reads += time
        texts[time]
    }

    @Test
    fun `a version is compared with the one before it, past a file as it came in`() = runTest {
        assertEquals(VersionSummary.Changed(VersionChanges(added = listOf("Hotels", "Alfama"), removed = listOf("Food"))), summaries.of(50, versions))
    }

    @Test
    fun `the oldest version, one after a version that can't be read, and a file as it came in say what they hold`() = runTest {
        assertEquals(VersionSummary.Holds(1), summaries.of(10, versions))
        assertEquals(VersionSummary.Holds(2), summaries.of(30, versions))
        // Counted the way the file's app counts a map's ideas: without its center.
        assertEquals(VersionSummary.Holds(2), summaries.of(40, versions))
    }

    @Test
    fun `a version that can't be read, or isn't there, says so`() = runTest {
        assertEquals(VersionSummary.Unreadable, summaries.of(20, versions))
        assertEquals(VersionSummary.Unreadable, summaries.of(99, versions))
    }

    @Test
    fun `each version is read once while the rows around it ask`() = runTest {
        summaries.of(50, versions)
        summaries.of(30, versions)
        summaries.of(50, versions)
        summaries.of(20, versions)

        assertEquals(listOf(50L, 30L, 20L), reads)
    }

    @Test
    fun `only the versions around the rows on screen stay read`() = runTest {
        val long = (1L..40L).reversed().map { MapVersion(it) }

        val many = VersionSummaries { time ->
            reads += time
            "# Map\n" + (1..time).joinToString("") { "- Idea $it\n" }
        }

        for (version in long) {
            val expected = if (version.time > 1) VersionSummary.Changed(VersionChanges(added = listOf("Idea ${version.time}"))) else VersionSummary.Holds(1)
            assertEquals(expected, many.of(version.time, long))
        }

        // Scrolling down the whole history read each version once…
        assertEquals((1L..40L).reversed().toList(), reads)

        // …and the newest, long scrolled past, is read again.
        many.of(40, long)
        assertEquals(listOf(40L, 39L), reads.drop(40))
    }

    @Test
    fun `the map as it is now is compared with the newest version that isn't a file as it came in`() = runTest {
        assertEquals(VersionSummary.Changed(VersionChanges(added = listOf("Baixa"))), summaries.now("# Trip\n## Flights\n## Hotels\n- Alfama\n- Baixa\n", versions))
        assertEquals(VersionSummary.Changed(VersionChanges()), summaries.now(texts.getValue(50)!!, versions))
    }

    @Test
    fun `with no version to compare it with, the map as it is now says what it holds`() = runTest {
        assertEquals(VersionSummary.Holds(0), summaries.now("# Trip\n", emptyList()))
        assertEquals(VersionSummary.Holds(2), summaries.now("# Trip\n## Flights\n## Hotels\n", listOf(MapVersion(40, original = true))))
    }
}
