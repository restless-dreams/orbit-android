package com.hyuchia.orbit.home

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class TagCountsTest {
    private fun map(id: String, vararg tags: String) = MapSummary(id, MapOutline(id, branches = 0, ideas = 0, tags = tags.toList()), 0, MapMetadata(), "", "")

    @Test
    fun `each tag counts its maps, spelled as most of them spell it, in the language's order`() {
        val maps = listOf(map("a", "Work", "japan"), map("b", "work"), map("c", "work", "Árbol"), map("d"))

        assertEquals(
            listOf(TagCount("Árbol", 1), TagCount("japan", 1), TagCount("work", 3)),
            tagCounts(maps, Collator.getInstance(Locale.forLanguageTag("es"))),
        )
    }

    @Test
    fun `a map has a tag whatever its capitals`() {
        assertTrue(map("a", "Japan Trip").hasTag("japan trip"))
        assertTrue(map("a", "Japan Trip").hasTag("#japan  trip"))
        assertFalse(map("a", "Japan Trip").hasTag("japan"))
    }
}
