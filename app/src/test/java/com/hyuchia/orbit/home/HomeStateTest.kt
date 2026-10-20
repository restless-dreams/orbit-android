package com.hyuchia.orbit.home

import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.SortOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

class HomeStateTest {
    private fun map(id: String, title: String, text: String = "", modified: Long = 0, pinned: Boolean = false, tags: List<String> = emptyList()) =
        MapSummary(id, MapOutline(title, branches = 0, ideas = 0, tags = tags), modified, MapMetadata(pinned = pinned), text, "")

    private val maps = listOf(
        map("garden", "Garden", "Tomatoes\nBasil by the window\n", modified = 1, pinned = true),
        map("talk", "Conference talk", "Opening story about Lisbon\nQuestions\n", modified = 2, tags = listOf("Work")),
        map("trip", "Lisbon in May", "Flights\nPastéis de nata in Belém\n", modified = 3, tags = listOf("travel")),
    )

    @Test
    fun `pinned maps come apart from the others, each in the order chosen`() {
        val edited = homeState(maps, "", AppSettings(sortOrder = SortOrder.Edited), tag = null)
        assertEquals(listOf("garden"), edited.pinned.map { it.id })
        assertEquals(listOf("trip", "talk"), edited.others.map { it.id })
        assertNull(edited.results)

        val byTitle = homeState(maps, "", AppSettings(sortOrder = SortOrder.Title), tag = null)
        assertEquals(listOf("talk", "trip"), byTitle.others.map { it.id })
    }

    @Test
    fun `a search finds the maps with every word, titles first, with the line that matched`() {
        val state = homeState(maps, "lisbon", AppSettings(), tag = null)
        assertEquals(listOf("trip", "talk"), state.results?.map { it.map.id })
        assertEquals(listOf(null, "Opening story about Lisbon"), state.results?.map { it.snippet })
        assertEquals("lisbon", state.searched)
        assertEquals(emptyList<SearchHit>(), homeState(maps, "lisbon basil", AppSettings(), tag = null).results)
    }

    @Test
    fun `a picked tag shows only its maps, whatever its capitals, and one no map has shows them all`() {
        val work = homeState(maps, "", AppSettings(), tag = "work")
        assertEquals("Work", work.tag)
        assertEquals(listOf("talk"), work.others.map { it.id })
        assertEquals(emptyList<MapSummary>(), work.pinned)
        assertEquals(listOf("talk"), homeState(maps, "lisbon", AppSettings(), tag = "work").results?.map { it.map.id })

        val gone = homeState(maps, "", AppSettings(), tag = "renamed")
        assertNull(gone.tag)
        assertEquals(listOf("trip", "talk"), gone.others.map { it.id })
    }

    @Test
    fun `Home is worked out on a dispatcher of its own, away from the one that shows it`() = runTest {
        val working = StandardTestDispatcher(testScheduler, name = "working")
        var collectedOn: ContinuationInterceptor? = null

        val settings = flow {
            collectedOn = currentCoroutineContext()[ContinuationInterceptor]
            emit(AppSettings())
        }

        homeStates(flowOf(maps), flowOf(""), settings, flowOf(null), working).first()

        assertSame(working, collectedOn)
    }

    @Test
    fun `states come in the order the search was typed, ending with the last letter's`() = runTest {
        val query = MutableStateFlow("")
        val states = mutableListOf<HomeState>()
        backgroundScope.launch { homeStates(flowOf(maps), query, flowOf(AppSettings()), flowOf(null), StandardTestDispatcher(testScheduler)).toList(states) }
        runCurrent()

        for (typed in listOf("l", "li", "lis", "lisb", "lisbo", "lisbon")) {
            query.value = typed

            // Letters typed before Home caught up with the one before them are skipped, never shown late.
            if (typed.length % 2 == 0) {
                runCurrent()
            }
        }

        runCurrent()
        val searched = states.map { it.searched }
        assertEquals(searched.sortedBy { it.length }, searched)
        assertEquals("lisbon", searched.last())
        assertEquals(listOf("trip", "talk"), states.last().results?.map { it.map.id })
    }
}
