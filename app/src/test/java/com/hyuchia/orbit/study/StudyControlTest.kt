package com.hyuchia.orbit.study

import com.hyuchia.orbit.data.FolderStudy
import com.hyuchia.orbit.data.MetadataFile
import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.map.ReadOnly
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val DAY = 24 * 60 * 60 * 1000L

@OptIn(ExperimentalCoroutinesApi::class)
class StudyControlTest {
    private val today = 20_000L
    private val nodes = listOf(
        MapNode("verbs", "Spanish verbs", depth = 0, childCount = 2),
        MapNode("ser", "Ser", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("soy", "soy", parentId = "ser", depth = 2),
        MapNode("estar", "Estar", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("estoy", "estoy", parentId = "estar", depth = 2),
    )
    private val ui = MutableStateFlow(MapUi(ready = true, map = MapState("Spanish verbs", nodes)))

    /** The map once Ser and the idea under it went, as the page says. */
    private val withoutSer = listOf(
        MapNode("verbs", "Spanish verbs", depth = 0, childCount = 1),
        MapNode("estar", "Estar", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("estoy", "estoy", parentId = "estar", depth = 2),
    )

    private val sent = mutableListOf<MapCommand>()
    private var now = 1_800_000_000_000L
    private var progress: Map<String, StudyCard> = emptyMap()
    private var elsewhere: Map<String, Map<String, StudyCard>> = emptyMap()
    private var newADay = NEW_CARDS_A_DAY

    /** This device's answers after each change was written, as the map's details had them then. */
    private val kept = mutableListOf<Map<String, StudyCard>>()

    /** While it's there, each change waits for it before it's written, as one does while the storage is slow. */
    private var holding: CompletableDeferred<Unit>? = null

    /** While true, the storage takes no change, as a full one: each fails as it's written. */
    private var full = false

    /** How many times a change was tried, written or not. */
    private var tries = 0

    /** How many times the screen was told answers that waited are written after all. */
    private var savedAfterAll = 0

    /** Writes a change as the maps do: to this device's answers as they are when it's written, beside the other devices'. */
    private val keep: suspend (StudyChange) -> Unit = { change ->
        holding?.await()
        tries += 1

        if (full) {
            throw IOException("No space left on device")
        }

        progress = change.applyTo(progress, elsewhere)
        kept += progress
    }

    /** Studying the map on a screen of its own, in `scope`, with the map's `answers`. */
    private fun TestScope.studying(scope: CoroutineScope = backgroundScope, answers: StudyWriting = StudyWriting(backgroundScope, keep)) = StudyControl(
        scope = scope,
        ui = ui,
        send = { sent += it },
        progress = { progress },
        answers = answers,
        today = { today },
        elsewhere = { elsewhere },
        newADay = { newADay },
        now = { now },
        saved = { savedAfterAll += 1 },
    )

    private val TestScope.control: StudyControl get() = studying()

    private val study: Study get() = ui.value.study!!

    @Test
    fun `a session hides every card's ideas, flies to the first, and reveals it when asked`() = runTest {
        val control = control
        control.start("verbs")
        assertEquals(
            listOf(MapCommand.Present(true), MapCommand.View("focus"), MapCommand.HideChildren(listOf("ser", "estar")), MapCommand.Focus("ser")),
            sent,
        )
        assertEquals(listOf("ser", "estar"), study.queue)
        sent.clear()
        control.reveal()
        assertEquals(listOf<MapCommand>(MapCommand.RevealChildren(listOf("ser"))), sent)
        assertTrue(study.revealed)
    }

    @Test
    fun `an answer is kept at once, and the next card comes with its ideas hidden`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        sent.clear()
        control.answer(knew = true)
        runCurrent()
        assertEquals(mapOf("ser" to StudyCard(box = 1, due = today + 1, first = today, at = now)), kept.last())
        // Estar's ideas are hidden still: the map only flies there.
        assertEquals(listOf<MapCommand>(MapCommand.Focus("estar")), sent)
        assertEquals("estar", study.current)
        assertEquals(1L, study.ifKnown)
    }

    @Test
    fun `a card answered with Again comes back hidden, and the session ends once both are known`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        control.answer(knew = false)
        control.reveal()
        sent.clear()
        control.answer(knew = true)
        // Ser comes back after Estar, hidden again.
        assertEquals(listOf(MapCommand.HideChildren(listOf("ser")), MapCommand.Focus("ser")), sent)
        control.reveal()
        sent.clear()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyEnding.Finished, study.ending)
        assertEquals(setOf("ser"), study.missed)
        assertEquals(1L, study.nextIn)
        // Every card was revealed on the way: the map flies back to where studying began, for the summary.
        assertEquals(listOf<MapCommand>(MapCommand.Focus("verbs")), sent)
        assertEquals(StudyCard(box = 1, due = today + 1, first = today, at = now), kept.last()["ser"])
    }

    @Test
    fun `nothing it tells the page changes the map`() = runTest {
        val control = control
        control.start("verbs")

        repeat(4) {
            control.reveal()
            control.answer(knew = it % 2 == 0)
        }

        control.undo()
        control.stop()
        val allowed = setOf(MapCommand.Present::class, MapCommand.View::class, MapCommand.Focus::class, MapCommand.HideChildren::class, MapCommand.RevealChildren::class)
        assertTrue(sent.all { it::class in allowed })
        assertEquals(MapCommand.Present(false), sent.last())
        assertNull(ui.value.study)
    }

    @Test
    fun `Undo takes the last answer back, its card revealed again and its progress as it was`() = runTest {
        val control = control
        progress = mapOf("ser" to StudyCard(box = 2, due = today, first = today - 9))
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyCard(box = 3, due = today + 7, first = today - 9, at = now), kept.last()["ser"])
        control.undo()
        runCurrent()
        assertEquals(StudyCard(box = 2, due = today, first = today - 9), kept.last()["ser"])
        assertEquals("ser", study.current)
        assertTrue(study.revealed)
        assertNull(study.last)
        assertEquals(MapCommand.Focus("ser"), sent.last())
    }

    @Test
    fun `with nothing due it opens on the summary, and studying anyway takes every card without moving known ones`() = runTest {
        val control = control
        progress = mapOf(
            "ser" to StudyCard(box = 3, due = today + 7, first = today - 20),
            "estar" to StudyCard(box = 2, due = today + 3, first = today - 20),
        )
        control.start("verbs")
        assertEquals(StudyEnding.CaughtUp, study.ending)
        assertEquals(3L, study.nextIn)
        assertEquals(MapCommand.Focus("verbs"), sent.last())
        control.studyAnyway()
        assertTrue(study.anyway)
        assertEquals(listOf("ser", "estar"), study.queue)
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyCard(box = 3, due = today + 7, first = today - 20), kept.last()["ser"])
    }

    @Test
    fun `progress for ideas the map no longer has is dropped as a session begins`() = runTest {
        val control = control
        progress = mapOf("gone" to StudyCard(box = 1, due = today), "ser" to StudyCard(box = 1, due = today + 1))
        control.start("verbs")
        runCurrent()
        assertEquals(setOf("ser"), kept.last().keys)
    }

    @Test
    fun `a map that wasn't read whole keeps the progress of ideas it doesn't show, and studies the ones it does`() = runTest {
        val ser = StudyCard(box = 1, due = today, first = today - 3)
        val estar = StudyCard(box = 2, due = today + 3, first = today - 3)

        for (readOnly in ReadOnly.entries) {
            // Orbit read only Ser's branch, say, or a newer Orbit gave Estar's another id.
            ui.value = MapUi(ready = true, map = MapState("Spanish verbs", nodes.take(3)), readOnly = readOnly)
            progress = mapOf("ser" to ser, "estar" to estar)
            kept.clear()
            val control = control
            control.start("verbs")
            runCurrent()
            assertTrue(kept.isEmpty())
            assertEquals(listOf("ser"), study.queue)

            // An answer keeps Estar's progress beside it, for when the map is read whole again.
            control.reveal()
            control.answer(knew = true)
            runCurrent()
            assertEquals(estar, kept.last()["estar"])
            assertEquals(StudyCard(box = 2, due = today + 3, first = today - 3, at = now), kept.last()["ser"])
        }
    }

    @Test
    fun `another device's later answers decide which cards are due, and only this device's own answers are kept here`() = runTest {
        val control = control
        // Studied here last week; known on the tablet this morning, so it isn't due until tomorrow.
        progress = mapOf("ser" to StudyCard(box = 1, due = today, first = today - 7, at = now - 7 * DAY))
        elsewhere = mapOf("tablet" to mapOf("ser" to StudyCard(box = 2, due = today + 1, first = today - 7, at = now - 1_000)))
        control.start("verbs")
        assertEquals(listOf("estar"), study.queue)
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        // The tablet's answer stays the tablet's: this device keeps its own, with the new one.
        assertEquals(
            mapOf("ser" to StudyCard(box = 1, due = today, first = today - 7, at = now - 7 * DAY), "estar" to StudyCard(box = 1, due = today + 1, first = today, at = now)),
            kept.last(),
        )
        assertEquals(StudyEnding.Finished, study.ending)
    }

    @Test
    fun `Undo gives this device's own answer back, and the card goes as the other devices left it`() = runTest {
        val control = control
        elsewhere = mapOf("tablet" to mapOf("ser" to StudyCard(box = 2, due = today, first = today - 9, at = now - 1_000)))
        control.start("verbs")
        assertEquals(listOf("ser", "estar"), study.queue)
        control.reveal()
        control.answer(knew = false)
        runCurrent()
        assertEquals(mapOf("ser" to StudyCard(box = 0, due = today, first = today - 9, at = now)), kept.last())
        control.undo()
        runCurrent()
        assertEquals(emptyMap<String, StudyCard>(), kept.last())
        // Known now, it builds on the tablet's answer.
        assertEquals(7L, study.ifKnown)
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyCard(box = 3, due = today + 7, first = today - 9, at = now), kept.last()["ser"])
    }

    @Test
    fun `caught up with new cards waiting, Keep going brings what the day has room for once more a day is chosen, or else another day's`() = runTest {
        val control = control
        val many = listOf(MapNode("top", "Top", depth = 0)) + (1..12).map { MapNode("idea-$it", "Idea $it", parentId = "top", depth = 1, note = "Note $it") }
        ui.value = MapUi(ready = true, map = MapState("Top", many))
        newADay = 5
        // Five studied today already, three of them on the tablet.
        progress = (1..2).associate { "idea-$it" to StudyCard(box = 1, due = today + 1, first = today, at = now - 10_000) }
        elsewhere = mapOf("tablet" to (3..5).associate { "idea-$it" to StudyCard(box = 1, due = today + 1, first = today, at = now - 5_000) })
        control.start("top")
        assertEquals(StudyEnding.CaughtUp, study.ending)
        assertEquals(7, study.newLeft)

        // Ten a day leaves room for five more today.
        newADay = 10
        control.keepGoing()
        assertEquals((6..10).map { "idea-$it" }, study.queue)
        assertEquals(0, study.newLeft)

        // At five a day, with no room left, it brings another day's five.
        newADay = 5
        control.stop()
        control.start("top")
        control.keepGoing()
        assertEquals((6..10).map { "idea-$it" }, study.queue)
    }

    @Test
    fun `Keep going while the last answer is still being written goes on from it, and keeps every answer`() = runTest {
        val control = control
        val ideas = listOf(MapNode("top", "Top", depth = 0, childCount = 3)) + (1..3).map { MapNode("idea-$it", "Idea $it", parentId = "top", depth = 1, note = "Note $it") }
        ui.value = MapUi(ready = true, map = MapState("Top", ideas))
        newADay = 1
        control.start("top")
        assertEquals(listOf("idea-1"), study.queue)

        // The storage takes its time: the answer is still being written as the session ends, and Keep going is pressed.
        holding = CompletableDeferred()
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyEnding.Finished, study.ending)
        assertTrue(kept.isEmpty())
        control.keepGoing()

        // The session begins again from the answers as they'll be: Idea 1 is studied, so another day's new card comes.
        assertEquals(listOf("idea-2"), study.queue)
        control.reveal()
        control.answer(knew = true)
        holding!!.complete(Unit)
        runCurrent()
        assertEquals(setOf("idea-1", "idea-2"), progress.keys)
    }

    @Test
    fun `leaving the folder while an answer is still being written keeps the other devices' answers and the new one`() = runTest {
        val control = control
        val tablet = StudyCard(box = 2, due = today + 3, first = today - 9, at = now - DAY)
        progress = mapOf("ser" to StudyCard(box = 1, due = today, first = today - 9, at = now - 2 * DAY))
        elsewhere = mapOf("tablet" to mapOf("estar" to tablet))
        control.start("verbs")
        holding = CompletableDeferred()
        control.reveal()
        control.answer(knew = true)
        runCurrent()

        // The folder is left meanwhile: the tablet's answers join this device's (MapRepository.keepStudyHere).
        progress = mergedProgress(progress, elsewhere)
        elsewhere = emptyMap()
        holding!!.complete(Unit)
        runCurrent()
        assertEquals(mapOf("ser" to StudyCard(box = 2, due = today + 3, first = today - 9, at = now), "estar" to tablet), progress)
    }

    @Test
    fun `an answer taken back once the folder was left gives the card back as the other devices left it`() = runTest {
        val control = control
        val tablet = StudyCard(box = 2, due = today, first = today - 9, at = now - 1_000)
        elsewhere = mapOf("tablet" to mapOf("ser" to tablet))
        control.start("verbs")
        control.reveal()
        control.answer(knew = false)
        runCurrent()

        // The folder is left: the tablet's answers join this device's, where Again, the latest, stands for the card.
        progress = mergedProgress(progress, elsewhere)
        elsewhere = emptyMap()
        control.undo()
        runCurrent()
        assertEquals(mapOf("ser" to tablet), progress)
    }

    @Test
    fun `an answer taken back after another device's later one came in leaves that one`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        runCurrent()

        // The tablet answered Ser later, and the folder, left since, folded its answer in over this one.
        val later = StudyCard(box = 0, due = today, first = today, at = now + 60_000)
        progress = progress + ("ser" to later)
        control.undo()
        runCurrent()
        assertEquals(later, progress["ser"])
    }

    @Test
    fun `an answer taken back after another device's later one came in shows that one, and goes on from it`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        runCurrent()

        // The tablet answered Ser later, well along, and the folder, left since, folded its answer in over this one.
        val later = StudyCard(box = 4, due = today, first = today - 30, at = now + 60_000)
        progress = progress + ("ser" to later)
        control.undo()
        runCurrent()
        assertEquals(later, progress["ser"])
        assertEquals(later.daysIfKnown(today), study.ifKnown)

        // Known now, it goes on from the tablet's box, not from where this device had it.
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyCard(box = 5, due = today + 35, first = today - 30, at = now), progress["ser"])
    }

    @Test
    fun `a card answered again in a session keeps the new answer, even with the clock set back meanwhile`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        control.answer(knew = false)
        control.reveal()
        control.answer(knew = true)
        assertEquals("ser", study.current)

        // The phone's clock goes back a minute before Ser comes round again.
        now -= 60_000
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyCard(box = 1, due = today + 1, first = today, at = now), progress["ser"])
    }

    @Test
    fun `a card the map no longer has, as it changed elsewhere, leaves the session at once, and the next one comes hidden`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        sent.clear()

        // Ser went from the map on another device, and the folder brought the change while it's studied.
        ui.update { it.copy(map = MapState("Spanish verbs", withoutSer)) }
        runCurrent()
        assertEquals(listOf("estar"), study.queue)
        assertEquals(1, study.total)
        assertFalse(study.revealed)
        assertEquals(listOf<MapCommand>(MapCommand.Focus("estar")), sent)

        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyEnding.Finished, study.ending)
        assertEquals(setOf("estar"), progress.keys)
    }

    @Test
    fun `a card gone before the session followed the map takes no answer`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()

        // The map changed elsewhere a moment ago, and Knew it is pressed before the session has followed it.
        ui.value = ui.value.copy(map = MapState("Spanish verbs", withoutSer))
        control.answer(knew = true)
        assertEquals(listOf("estar"), study.queue)
        assertFalse(study.revealed)
        runCurrent()
        assertTrue(kept.isEmpty())
        assertNull(study.last)
    }

    @Test
    fun `an idea that's no longer a card, its ideas and note gone, leaves the session too`() = runTest {
        val control = control
        control.start("verbs")
        ui.update { it.copy(map = MapState("Spanish verbs", nodes.filter { node -> node.id != "soy" }.map { node -> if (node.id == "ser") node.copy(childCount = 0) else node })) }
        runCurrent()
        assertEquals(listOf("estar"), study.queue)
        control.reveal()
        assertTrue(study.revealed)
    }

    @Test
    fun `the session's last cards going end it on its summary, and the answer to take back goes with its card`() = runTest {
        val control = control
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        assertEquals("ser", study.last?.card)
        sent.clear()

        ui.update { it.copy(map = MapState("Spanish verbs", listOf(MapNode("verbs", "Spanish verbs", depth = 0)))) }
        runCurrent()
        assertEquals(StudyEnding.Finished, study.ending)
        assertEquals(1, study.total)
        assertNull(study.last)
        assertEquals(listOf<MapCommand>(MapCommand.Focus("verbs")), sent)
        control.undo()
        assertEquals(StudyEnding.Finished, study.ending)
    }

    @Test
    fun `odd boxes in this device's details or another device's record in the folder don't stop a session`() = runTest {
        val control = control
        // A box under the first, as this device's details hold it, and one past the last, as the tablet's record says.
        progress = MetadataFile.decode("""{"study":{"ser":{"box":-1,"due":$today,"first":${today - 9}}}}""").study.orEmpty()
        elsewhere = mapOf("tablet" to FolderStudy.decode("""{"map":"Verbs.md","cards":{"estar":{"box":${Int.MAX_VALUE},"due":$today,"first":${today - 9},"at":1}}}""")!!.cards)
        control.start("verbs")
        assertEquals(listOf("ser", "estar"), study.queue)
        // Each goes on from the nearest box: Ser's first, Estar's last.
        assertEquals(1L, study.ifKnown)
        control.reveal()
        control.answer(knew = true)
        assertEquals(180L, study.ifKnown)
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(1, progress.getValue("ser").box)
        assertEquals(STUDY_INTERVALS.size, progress.getValue("estar").box)
    }

    @Test
    fun `answers given right before the map closes are still written once it has`() = runTest {
        // The screen's scope goes as the map closes; the answers are written on the app's, which stays.
        val screen = CoroutineScope(backgroundScope.coroutineContext + Job())
        val control = studying(scope = screen)
        holding = CompletableDeferred()
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        control.reveal()
        control.answer(knew = false)
        control.close()
        screen.cancel()
        holding!!.complete(Unit)
        control.written()
        assertEquals(setOf("ser", "estar"), progress.keys)
        assertEquals(2, kept.size)
    }

    @Test
    fun `an idea with nothing under it has no cards`() = runTest {
        val control = control
        control.start("soy")
        assertEquals(StudyEnding.NoCards, study.ending)
        assertTrue(sent.none { it is MapCommand.HideChildren })
    }

    @Test
    fun `a page that draws the map again gets the hidden ideas and the card back`() = runTest {
        val control = control
        control.start("verbs")
        sent.clear()
        control.pageLoaded()
        assertEquals(listOf(MapCommand.Present(true), MapCommand.HideChildren(listOf("ser", "estar")), MapCommand.Focus("ser")), sent)
    }

    @Test
    fun `an answer the storage can't take isn't counted as kept, the map says so, and a session begun again still counts it`() = runTest {
        val control = control
        full = true
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(AnswersUnsaved.Storage, ui.value.answersUnsaved)
        assertTrue(progress.isEmpty())

        // However long it's waited for, it isn't written: the folder maps are kept in isn't told it is.
        var written = false

        backgroundScope.launch {
            control.written()
            written = true
        }

        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(written)

        // A session begun again goes on from it: Ser is known, so only Estar is new today.
        control.stop()
        control.start("verbs")
        assertEquals(listOf("estar"), study.queue)
        assertTrue(progress.isEmpty())
    }

    @Test
    fun `an answer the storage couldn't take is tried again until it takes it, and written once`() = runTest {
        val control = control
        full = true
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(1, tries)
        control.stop()

        // Room again: the next try writes it, and the map says so, as the session is over.
        full = false
        advanceTimeBy(ANSWER_RETRY_MILLISECONDS - 1)
        runCurrent()
        assertTrue(progress.isEmpty())
        assertEquals(AnswersUnsaved.Storage, ui.value.answersUnsaved)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(setOf("ser"), progress.keys)
        assertEquals(1, kept.size)
        assertNull(ui.value.answersUnsaved)
        assertEquals(1, savedAfterAll)
        control.written()
    }

    @Test
    fun `answers given while one can't be kept wait behind it, each new one tries again, and Try again writes them all in order`() = runTest {
        val control = control
        full = true
        control.start("verbs")
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(1, tries)
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(2, tries)
        // Estar's answer taken back: written before the answer, it would leave the answer in.
        control.undo()
        runCurrent()
        assertEquals(3, tries)
        assertTrue(progress.isEmpty())

        full = false
        control.tryAgain()
        runCurrent()
        assertEquals(listOf(setOf("ser"), setOf("ser", "estar"), setOf("ser")), kept.map { it.keys })
        assertNull(ui.value.answersUnsaved)
        // Still in the session, which shows they're written by the note going: nothing more is said.
        assertEquals(0, savedAfterAll)
    }

    @Test
    fun `answers a screen that closed couldn't keep count in the next screen's session, which says so, and are written once`() = runTest {
        val writes = StudyWrites(backgroundScope, { _, change -> keep(change) })
        assertSame(writes.of("verbs"), writes.of("verbs"))
        val screen = CoroutineScope(backgroundScope.coroutineContext + Job())
        val first = studying(scope = screen, answers = writes.of("verbs"))
        full = true
        first.start("verbs")
        first.reveal()
        first.answer(knew = true)
        runCurrent()
        first.stop()
        first.close()
        screen.cancel()

        // The map opens again, on a new screen.
        ui.value = MapUi(ready = true, map = MapState("Spanish verbs", nodes))
        val second = studying(answers = writes.of("verbs"))
        runCurrent()
        assertEquals(AnswersUnsaved.Storage, ui.value.answersUnsaved)
        second.start("verbs")
        assertEquals(listOf("estar"), study.queue)

        full = false
        advanceTimeBy(ANSWER_RETRY_MILLISECONDS)
        runCurrent()
        assertEquals(setOf("ser"), progress.keys)
        assertEquals(1, kept.size)
        assertNull(ui.value.answersUnsaved)
    }

    @Test
    fun `a newer Orbit keeping the maps is why answers wait, as the maps say`() = runTest {
        val writing = StudyWriting(backgroundScope, keep, problemOf = { AnswersUnsaved.Newer })
        val control = studying(answers = writing)
        full = true
        control.start("verbs")
        control.reveal()
        control.answer(knew = false)
        runCurrent()
        assertEquals(AnswersUnsaved.Newer, ui.value.answersUnsaved)
        assertEquals(AnswersUnsaved.Newer, writing.problem.value)
    }
}
