package com.hyuchia.orbit.study

import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.map.ReadOnly
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    private val sent = mutableListOf<MapCommand>()
    private val now = 1_800_000_000_000L
    private var progress: Map<String, StudyCard> = emptyMap()
    private var elsewhere: Map<String, Map<String, StudyCard>> = emptyMap()
    private var newADay = NEW_CARDS_A_DAY
    private val kept = mutableListOf<Map<String, StudyCard>>()

    private val TestScope.control: StudyControl
        get() = StudyControl(
            scope = backgroundScope,
            ui = ui,
            send = { sent += it },
            progress = { progress },
            keep = { cards -> kept += cards },
            today = { today },
            elsewhere = { elsewhere },
            newADay = { newADay },
            now = { now },
        )

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
}
