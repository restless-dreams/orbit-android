package com.hyuchia.orbit.study

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.page.MapNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DueCountsTest {
    private val today = 20_000L
    private val due = StudyCard(box = 1, due = today, first = today - 9)

    private val verbs = listOf(
        MapNode("verbs", "Spanish verbs", depth = 0, childCount = 2),
        MapNode("ser", "Ser", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("soy", "soy", parentId = "ser", depth = 2),
        MapNode("estar", "Estar", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("estoy", "estoy", parentId = "estar", depth = 2),
    )

    @Test
    fun `the ideas with answers that aren't cards are noted once the page reads the map whole, and again only as they change`() = runTest {
        val ideas = MutableStateFlow<List<MapNode>?>(null)
        // Soy was a card once; Ir went from the map; the tablet studied Estar.
        val details = MutableStateFlow<MapMetadata?>(MapMetadata(study = mapOf("ser" to due, "soy" to due, "ir" to due), studyElsewhere = mapOf("tablet" to mapOf("estar" to due))))
        val noted = mutableListOf<Set<String>>()

        // As the maps note them: in the map's details, as they are then.
        noteCards(backgroundScope, ideas, details) { cards ->
            noted += cards
            details.update { it?.copy(studyNotCards = notCards(it.studyProgress, cards).sorted()) }
        }

        runCurrent()
        assertTrue(noted.isEmpty())

        ideas.value = verbs
        runCurrent()
        assertEquals(listOf(setOf("ser", "estar")), noted)
        assertEquals(listOf("ir", "soy"), details.value?.studyNotCards)
        assertEquals(setOf("ser", "estar"), details.value?.studyCounted?.keys)
        assertEquals(2, dueCount(details.value?.studyCounted, today))

        // The same cards again, or details changed elsewhere that agree with them: nothing more is noted.
        ideas.value = verbs.toList()
        details.update { it?.copy(opened = 5) }
        runCurrent()
        assertEquals(1, noted.size)

        // Soy gets a note: a card again, it counts.
        ideas.value = verbs.map { if (it.id == "soy") it.copy(note = "I am") else it }
        runCurrent()
        assertEquals(listOf("ir"), details.value?.studyNotCards)
        assertEquals(3, dueCount(details.value?.studyCounted, today))

        // A map Orbit can't read whole says nothing of which ideas are cards.
        ideas.value = null
        runCurrent()
        assertEquals(2, noted.size)
    }

    @Test
    fun `a map with no answers notes nothing`() = runTest {
        val noted = mutableListOf<Set<String>>()
        noteCards(backgroundScope, MutableStateFlow(verbs), MutableStateFlow(MapMetadata())) { noted += it }
        runCurrent()
        assertTrue(noted.isEmpty())
    }
}
