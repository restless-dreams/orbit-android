package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VersionChangesTest {
    private val trip = """
        # Trip
        ## Flights
        - Window seat
        ## Hotels
        - Alfama
        - Baixa
        ## Food
        - Pastéis de nata
        - Bifana
    """.trimIndent() + "\n"

    @Test
    fun `ideas added and removed are named, each branch's highest idea first`() {
        val after = trip.replace("## Food\n- Pastéis de nata\n- Bifana\n", "") + "## Sights\n- Belém\n"

        val changes = VersionChanges.between(trip, after)

        assertEquals(listOf("Sights", "Belém"), changes.added)
        assertEquals(listOf("Food", "Pastéis de nata", "Bifana"), changes.removed)
        assertTrue(changes.renamed.isEmpty())
        assertTrue(changes.moved.isEmpty())
    }

    @Test
    fun `an idea renamed in place is a rename, not one added and one removed`() {
        val changes = VersionChanges.between(trip, trip.replace("- Alfama\n", "- Alfama, by the castle\n"))

        assertEquals(listOf("Alfama" to "Alfama, by the castle"), changes.renamed)
        assertTrue(changes.added.isEmpty())
        assertTrue(changes.removed.isEmpty())
    }

    @Test
    fun `a branch renamed keeps its ideas, and the map's center can be renamed too`() {
        val changes = VersionChanges.between(trip, trip.replace("## Hotels\n", "## Where to stay\n").replace("# Trip\n", "# Lisbon trip\n"))

        assertEquals(listOf("Trip" to "Lisbon trip", "Hotels" to "Where to stay"), changes.renamed)
        assertTrue(changes.added.isEmpty())
        assertTrue(changes.removed.isEmpty())
    }

    @Test
    fun `an idea that went to another branch moved, with what's under it`() {
        val after = trip.replace("- Window seat\n", "").replace("- Baixa\n", "- Baixa\n- Window seat\n")

        val changes = VersionChanges.between(trip, after)

        assertEquals(listOf("Window seat"), changes.moved)
        assertTrue(changes.added.isEmpty())
        assertTrue(changes.removed.isEmpty())
    }

    @Test
    fun `one added at the top and another removed at the bottom isn't mistaken for a rename`() {
        val after = trip.replace("## Hotels\n- Alfama\n- Baixa\n", "## Hotels\n- Chiado\n- Alfama\n")

        val changes = VersionChanges.between(trip, after)

        assertEquals(listOf("Chiado"), changes.added)
        assertEquals(listOf("Baixa"), changes.removed)
        assertTrue(changes.renamed.isEmpty())
    }

    @Test
    fun `ideas with the same name are told apart by where they are`() {
        val before = "# Week\n## Monday\n- Gym\n## Tuesday\n- Gym\n"
        val after = "# Week\n## Monday\n- Gym\n## Tuesday\n- Gym\n## Wednesday\n- Gym\n"

        val changes = VersionChanges.between(before, after)

        assertEquals(listOf("Wednesday", "Gym"), changes.added)
        assertTrue(changes.removed.isEmpty())
        assertTrue(changes.moved.isEmpty())
    }

    @Test
    fun `text that changed with no idea changing is a change of details, and the same text is none`() {
        val details = VersionChanges.between(trip, trip.replace("- Bifana\n", "- Bifana\n  The best one is near the market.\n"))

        assertTrue(details.details)
        assertTrue(details.added.isEmpty() && details.removed.isEmpty() && details.renamed.isEmpty())
        assertTrue(VersionChanges.between(trip, trip).none)
    }

    @Test
    fun `statuses, links and notes don't change an idea's name`() {
        val before = "# Plan\n- [ ] Book flights\n- Read [the guide](https://example.com)\n"
        val after = "# Plan\n- [x] Book flights\n- Read [the guide](https://example.com/new)\n"

        val changes = VersionChanges.between(before, after)

        assertTrue(changes.details)
        assertTrue(changes.renamed.isEmpty())
    }

    @Test
    fun `a YAML map that gains a second idea at its top added that idea, and losing it removed it`() {
        // Orbit's YAML has one idea at the top; with two, it makes up a center named by the title, with both under it.
        val one = "title: Biology\nnodes:\n  - text: Biology\n    children:\n      - Cells\n"
        val two = one + "  - text: Plants\n    children: [Leaves]\n"

        assertEquals(VersionChanges(added = listOf("Plants", "Leaves")), VersionChanges.between(one, two))
        assertEquals(VersionChanges(removed = listOf("Plants", "Leaves")), VersionChanges.between(two, one))

        // Without a title, the same.
        assertEquals(VersionChanges(added = listOf("Plants", "Leaves")), VersionChanges.between(one.removePrefix("title: Biology\n"), two.removePrefix("title: Biology\n")))
    }

    @Test
    fun `the first idea of an empty YAML map is added, and a center named by the title is the same as markdown's`() {
        assertEquals(VersionChanges(added = listOf("Tokyo")), VersionChanges.between("title: Trip\nnodes: []\n", "title: Trip\nnodes:\n  - Tokyo\n"))
        assertEquals(VersionChanges(added = listOf("Tokyo", "Kyoto")), VersionChanges.between("", "nodes:\n  - Tokyo\n  - Kyoto\n"))
        assertEquals(VersionChanges(details = true), VersionChanges.between("# Trip\n- Tokyo\n- Kyoto\n", "title: Trip\nnodes:\n  - Tokyo\n  - Kyoto\n"))
    }

    @Test
    fun `a YAML map's made-up center written down as markdown is still its center, whatever its top ideas are called`() {
        // Several ideas at the top, one named as the title names the made-up center; then the map's page writes it as markdown.
        val yaml = "title: Roadmap\nnodes:\n  - text: Roadmap\n    children: [Research]\n  - text: Marketing\n"
        val markdown = "# Roadmap\n## Roadmap\n- Research\n## Marketing\n## Press kit\n"

        assertEquals(VersionChanges(added = listOf("Press kit")), VersionChanges.between(yaml, markdown))
        assertEquals(VersionChanges(removed = listOf("Press kit")), VersionChanges.between(markdown, yaml))
    }

    @Test
    fun `ideas added in flow style to a YAML map are named`() {
        val before = "nodes:\n  - text: Trip\n    children: [Tokyo]\n"

        assertEquals(VersionChanges(added = listOf("Kyoto")), VersionChanges.between(before, "nodes:\n  - text: Trip\n    children: [Tokyo, Kyoto]\n"))
        assertEquals(VersionChanges(added = listOf("Kyoto", "Gion")), VersionChanges.between(before, before + "  - { text: Kyoto, children: [Gion] }\n"))
    }

    @Test
    fun `tags added to an idea, or a YAML map's tags written as hashtags, change its details, not its name`() {
        assertEquals(VersionChanges(details = true), VersionChanges.between("# Trip\n- Launch\n", "# Trip\n- Launch #web\n"))

        // A YAML map written as markdown, as the map's page writes it.
        val yaml = "nodes:\n  - text: Trip\n    children:\n      - text: Launch\n        tags: [web, ads]\n"
        assertEquals(VersionChanges(details = true), VersionChanges.between(yaml, "# Trip\n- Launch #web #ads\n"))
    }

    @Test
    fun `a map of 2,400 ideas compares in a moment`() {
        val library = File("src/debug/assets/samples/city-library.md").readText()
        val lines = library.lines()
        // Rename a planet, remove a branch, and add ideas: a long edit's worth.
        val catalog = lines.indexOfFirst { it.startsWith("## ") && "Catalog" in it }
        val branch = lines.indexOfFirst { it.startsWith("### ") }
        val nextBranch = lines.withIndex().first { (index, line) -> index > branch && line.startsWith("### ") }.index

        val edited = lines.toMutableList().apply {
            this[catalog] = "## 📚 The catalog"
            subList(branch, nextBranch).clear()
            add("## 🆕 New wing")
            repeat(30) { add("- Shelf $it") }
        }.joinToString("\n")

        val removedBranch = lines[branch].removePrefix("### ").trim()

        val started = System.nanoTime()
        val changes = VersionChanges.between(library, edited)
        val took = (System.nanoTime() - started) / 1_000_000

        assertTrue("It took $took ms", took < 2_000)
        assertEquals(listOf("📚 Catalog" to "📚 The catalog"), changes.renamed)
        assertEquals(removedBranch, changes.removed.first())
        // The branch's ideas: its heading and what's under it, read the same way as the map.
        val branchIdeas = MapText.ideas("# Branch\n" + lines.subList(branch, nextBranch).joinToString("\n")).size - 1
        assertEquals(branchIdeas, changes.removed.size)
        assertEquals("🆕 New wing", changes.added.first())
        assertEquals(31, changes.added.size)
    }
}
