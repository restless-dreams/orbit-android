package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImporterTest {
    @Test
    fun orbitMarkdownIsKeptAsWritten() {
        val markdown = "# Trip\n\n## Food\n- Tacos\n"
        assertEquals(markdown, Importer.markdownFor(markdown))
        assertEquals("- Groceries\n  - Milk\n", Importer.markdownFor("- Groceries\n  - Milk"))
    }

    @Test
    fun plainLinesBecomeAListUnderTheFirst() {
        assertEquals("# Groceries\n\n- Milk\n- Eggs\n- Bread\n", Importer.markdownFor("Groceries\nMilk\n\n• Eggs\nBread\n"))
    }

    @Test
    fun aSingleLineIsAMapsTitle() {
        assertEquals("# Ideas for the weekend\n", Importer.markdownFor("  Ideas for the weekend  "))
    }

    @Test
    fun aLongLineIsKeptWholeAsTheNote() {
        val line = "Remember to ask the venue about the projector, the stage lights and whether the sound check can move to Friday morning"
        val markdown = Importer.markdownFor(line)
        assertEquals("# Remember to ask the venue about the projector, the stage lights and whether…\n\n$line\n", markdown)
    }

    @Test
    fun sharedTextWithASubjectIsNamedByIt() {
        assertEquals(
            "# A long read about maps\n\nhttps://example.com/articles/2026/10/maps-and-minds\n",
            Importer.markdownFor("https://example.com/articles/2026/10/maps-and-minds", "A long read about maps"),
        )
        assertEquals("# Groceries\n\n- Milk\n- Eggs\n", Importer.markdownFor("Milk\nEggs", "Groceries"))
    }
}
