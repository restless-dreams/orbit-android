package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.ui.searchWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val ui = MapUi(
        map = MapState(
            "Cities and words",
            listOf(
                MapNode("cities-and-words", "Cities and words"),
                MapNode("istanbul", "İstanbul", parentId = "cities-and-words", note = "Kadıköy is across the Bosphorus."),
                MapNode("lisbon", "Lisbon", parentId = "cities-and-words", note = "The İstiklal of Lisbon is the Chiado."),
                MapNode("strasse", "Straße", parentId = "cities-and-words"),
                MapNode("odos", "ΟΔΟΣ", parentId = "cities-and-words"),
                MapNode("is-plani", "İş planı 🙂", parentId = "cities-and-words", tags = listOf("İş")),
            ),
        ),
    )

    private fun found(query: String): List<String> = search(ui, query).map { it.node.id }

    /** The text in the ranges a search highlights. */
    private fun highlighted(text: String, query: String): List<String> = wordRanges(text, searchWords(query)).map { text.substring(it.first, it.last + 1) }

    @Test
    fun ideasAreFoundWhateverTheCase() {
        assertEquals(listOf("istanbul"), found("istanbul"))
        assertEquals(listOf("istanbul"), found("İSTANBUL"))
        assertEquals(listOf("odos"), found("οδος"))
        assertEquals(listOf("strasse"), found("STRAẞE"))
        assertEquals(listOf("istanbul"), found("kadiköy"))
        assertEquals(listOf("is-plani"), found("#iş"))
    }

    @Test
    fun textMatchesComeBeforeNoteMatches() {
        assertEquals(listOf("istanbul", "lisbon"), found("ist"))
        assertEquals("The İstiklal of Lisbon is the Chiado.", search(ui, "istiklal").single().snippet)
    }

    @Test
    fun highlightsLandOnTheWordsInTheText() {
        assertEquals(listOf("bul"), highlighted("İstanbul", "bul"))
        assertEquals(listOf("ş"), highlighted("İş planı 🙂", "ş"))
        assertEquals(listOf("İş", "planı"), highlighted("İş planı 🙂", "plani iş"))
        assertEquals(listOf("İstanbul"), highlighted("İstanbul", "istanbul"))
        assertEquals(listOf("ΟΔΟΣ"), highlighted("ΟΔΟΣ", "οδος"))
        assertEquals(listOf("🚋"), highlighted("🙂 Lisbon 🚋", "🚋"))
    }

    @Test
    fun everyQueryTypedLetterByLetterHighlightsInsideTheText() {
        val texts = ui.map!!.nodes.flatMap { listOfNotNull(it.text, it.note) }

        for (text in texts) {
            for (query in texts) {
                for (end in 1..query.length) {
                    for (found in wordRanges(text, searchWords(query.take(end)))) {
                        assertTrue(found.first >= 0 && found.last < text.length)
                    }
                }
            }
        }
    }
}
