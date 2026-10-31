package com.hyuchia.orbit.home

import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.SortOrder
import com.hyuchia.orbit.privacy.PrivateView
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

class HomeStateTest {
    private fun map(id: String, title: String, text: String = "", modified: Long = 0, pinned: Boolean = false, tags: List<String> = emptyList(), private: Boolean = false) =
        MapSummary(id, MapOutline(title, branches = 0, ideas = 0, tags = tags), modified, MapMetadata(pinned = pinned, isPrivate = private), text, "")

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
    fun `sorted by title, locked private maps go after the others, by when they were made`() {
        val locked = PrivateView(locked = true)
        val maps = listOf(
            MapSummary("zoo", MapOutline("Zoo visit", branches = 0, ideas = 0), 0, MapMetadata(isPrivate = true, created = 2), "", ""),
            map("apple", "Apple pie"),
            MapSummary("diary", MapOutline("Diary", branches = 0, ideas = 0), 0, MapMetadata(isPrivate = true, created = 1), "", ""),
            map("mango", "Mango tree"),
        )

        val state = homeState(maps, "", AppSettings(sortOrder = SortOrder.Title), tag = null, privacy = locked, hiding = false)
        assertEquals(listOf("apple", "mango", "diary", "zoo"), state.others.map { it.id })
        // Unlocked, every map takes its place by its title.
        val open = homeState(maps, "", AppSettings(sortOrder = SortOrder.Title), tag = null)
        assertEquals(listOf("apple", "diary", "mango", "zoo"), open.others.map { it.id })
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

    private val journal = map("journal", "Therapy notes", "Lisbon, and what it brought back\n", modified = 4, tags = listOf("Health", "travel"), private = true)

    @Test
    fun `locked private maps keep their place, but searches, tags and picked tags never give them away`() {
        val withJournal = maps + journal
        val locked = homeState(withJournal, "", AppSettings(), tag = null, privacy = PrivateView(locked = true))
        assertEquals(listOf("journal", "trip", "talk"), locked.others.map { it.id })
        assertEquals(setOf("journal"), locked.locked)
        assertEquals(listOf("travel", "Work"), locked.tags.map { it.name })
        assertEquals(1, locked.tags.first { it.name == "travel" }.count)
        assertEquals(false, locked.canLock)

        assertEquals(listOf("trip", "talk"), homeState(withJournal, "lisbon", AppSettings(), tag = null, privacy = PrivateView(locked = true)).results?.map { it.map.id })
        assertEquals(emptyList<SearchHit>(), homeState(withJournal, "therapy", AppSettings(), tag = null, privacy = PrivateView(locked = true)).results)
        assertEquals(listOf("trip"), homeState(withJournal, "", AppSettings(), tag = "travel", privacy = PrivateView(locked = true)).others.map { it.id })
        assertNull(homeState(withJournal, "", AppSettings(), tag = "health", privacy = PrivateView(locked = true)).tag)
    }

    @Test
    fun `unlocked, private maps are found, tagged and shown as any other, and can be locked again`() {
        val unlocked = homeState(maps + journal, "lisbon", AppSettings(), tag = null, privacy = PrivateView(locked = false))
        assertEquals(listOf("trip", "journal", "talk"), unlocked.results?.map { it.map.id })
        assertEquals(emptySet<String>(), unlocked.locked)
        assertEquals(listOf("Health", "travel", "Work"), unlocked.tags.map { it.name })
        assertEquals(true, unlocked.canLock)

        // A map made private while the others were locked stays open, and can be locked with them.
        val kept = homeState(maps + journal, "", AppSettings(), tag = null, privacy = PrivateView(locked = true, keptOpen = setOf("journal")))
        assertEquals(emptySet<String>(), kept.locked)
        assertEquals(true, kept.canLock)

        // Without a screen lock there's nothing to lock them with.
        assertEquals(false, homeState(maps + journal, "", AppSettings(), tag = null, privacy = PrivateView(locked = false, lockable = false)).canLock)
    }

    @Test
    fun `hidden while locked, private maps aren't on Home at all, nor in searches, tags or counts`() {
        val hiding = AppSettings(hideLockedPrivateMaps = true)
        val pinnedJournal = journal.copy(metadata = journal.metadata.copy(pinned = true))
        val locked = homeState(maps + pinnedJournal, "", hiding, tag = null, privacy = PrivateView(locked = true))
        assertEquals(listOf("garden"), locked.pinned.map { it.id })
        assertEquals(listOf("trip", "talk"), locked.others.map { it.id })
        assertEquals(emptySet<String>(), locked.locked)
        assertEquals(listOf("travel", "Work"), locked.tags.map { it.name })
        assertEquals(1, locked.tags.first { it.name == "travel" }.count)
        assertEquals(true, locked.canShowPrivate)
        assertEquals(false, locked.canLock)
        assertEquals(emptyList<SearchHit>(), homeState(maps + pinnedJournal, "therapy", hiding, tag = null, privacy = PrivateView(locked = true)).results)

        // Every map private: Home is as empty as a new one, and still offers to show them.
        val onlyPrivate = homeState(listOf(journal), "", hiding, tag = null, privacy = PrivateView(locked = true))
        assertEquals(true, onlyPrivate.isEmpty)
        assertEquals(true, onlyPrivate.canShowPrivate)
        // It offers with no private map too, so offering gives nothing away.
        assertEquals(true, homeState(maps, "", hiding, tag = null, privacy = PrivateView(locked = true)).canShowPrivate)

        // Unlocked, they're back where they were, and can be locked again.
        val unlocked = homeState(maps + pinnedJournal, "", hiding, tag = null, privacy = PrivateView(locked = false))
        assertEquals(listOf("journal", "garden"), unlocked.pinned.map { it.id })
        assertEquals(false, unlocked.canShowPrivate)
        assertEquals(true, unlocked.canLock)

        // A map made private while the others hide stays until they lock again.
        val kept = homeState(maps + journal, "", hiding, tag = null, privacy = PrivateView(locked = true, keptOpen = setOf("journal")))
        assertEquals(listOf("journal", "trip", "talk"), kept.others.map { it.id })
        assertEquals(true, kept.canLock)

        // Without a screen lock nothing locks, so nothing hides.
        val noLock = homeState(maps + journal, "", hiding, tag = null, privacy = PrivateView(locked = false, lockable = false))
        assertEquals(listOf("journal", "trip", "talk"), noLock.others.map { it.id })
        assertEquals(false, noLock.canShowPrivate)
    }

    @Test
    fun `hidden private maps lock where they are first, and go a moment later, but are gone at once on a fresh look`() = runTest {
        val privacy = MutableStateFlow(PrivateView(locked = true))
        val heard = mutableListOf<Boolean>()
        val listening = launch { hidingPrivateMaps(privacy).collect { heard += it } }
        runCurrent()
        // Home's first look: gone already.
        assertEquals(listOf(true), heard)

        privacy.value = PrivateView(locked = false)
        runCurrent()
        assertEquals(listOf(true, false), heard)

        // Locked from Home: they stay, locked, then go.
        privacy.value = PrivateView(locked = true)
        runCurrent()
        assertEquals(listOf(true, false), heard)
        advanceTimeBy(PRIVATE_MAPS_GO_AFTER + 1)
        assertEquals(listOf(true, false, true), heard)

        // Locked while Orbit was away: gone at once.
        privacy.value = PrivateView(locked = false)
        runCurrent()
        privacy.value = PrivateView(locked = true, lockedAway = true)
        runCurrent()
        assertEquals(listOf(true, false, true, false, true), heard)
        listening.cancel()
    }

    @Test
    fun `while they stay a moment, locked private maps show locked in their place`() {
        val staying = homeState(maps + journal, "", AppSettings(hideLockedPrivateMaps = true), tag = null, privacy = PrivateView(locked = true), hiding = false)
        assertEquals(listOf("journal", "trip", "talk"), staying.others.map { it.id })
        assertEquals(setOf("journal"), staying.locked)
        assertEquals(true, staying.canShowPrivate)
    }

    @Test
    fun `not hidden, locked private maps keep their place, and Home offers nothing more`() {
        val locked = homeState(maps + journal, "", AppSettings(), tag = null, privacy = PrivateView(locked = true))
        assertEquals(listOf("journal", "trip", "talk"), locked.others.map { it.id })
        assertEquals(false, locked.canShowPrivate)
    }

    @Test
    fun `Home is worked out on a dispatcher of its own, away from the one that shows it`() = runTest {
        val working = StandardTestDispatcher(testScheduler, name = "working")
        var collectedOn: ContinuationInterceptor? = null

        val settings = flow {
            collectedOn = currentCoroutineContext()[ContinuationInterceptor]
            emit(AppSettings())
        }

        homeStates(flowOf(maps), flowOf(""), settings, flowOf(null), flowOf(PrivateView()), working).first()

        assertSame(working, collectedOn)
    }

    @Test
    fun `states come in the order the search was typed, ending with the last letter's`() = runTest {
        val query = MutableStateFlow("")
        val states = mutableListOf<HomeState>()
        backgroundScope.launch { homeStates(flowOf(maps), query, flowOf(AppSettings()), flowOf(null), flowOf(PrivateView()), StandardTestDispatcher(testScheduler)).toList(states) }
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
