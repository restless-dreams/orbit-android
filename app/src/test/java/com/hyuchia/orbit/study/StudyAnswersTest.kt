package com.hyuchia.orbit.study

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

private const val DAY = 24 * 60 * 60 * 1000L

/**
 * The review's two probes, step by step: a session's answers kept in a map's
 * details on files while the folder the maps are kept in is left, and while
 * Keep going begins the session again, each answer held on its way until the
 * test lets it through. Every answer is kept, and every other device's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyAnswersTest {
    private val root: File = Files.createTempDirectory("orbit-study-answers").toFile()

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val words = FileWords(Locale.US)
    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val today = 20_000L
    private val now = 1_800_000_000_000L

    private val verbs = listOf(
        MapNode("verbs", "Spanish verbs", depth = 0, childCount = 2),
        MapNode("ser", "Ser", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("soy", "soy", parentId = "ser", depth = 2),
        MapNode("estar", "Estar", parentId = "verbs", depth = 1, childCount = 1),
        MapNode("estoy", "estoy", parentId = "estar", depth = 2),
    )

    private val ui = MutableStateFlow(MapUi(ready = true, map = MapState("Spanish verbs", verbs)))

    /** While it's there, each answer waits for it on its way to the map's details, as on a slow storage. */
    private var holding: CompletableDeferred<Unit>? = null

    private val study: Study get() = ui.value.study!!

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** Studying map `id`, as its screen does: its details are where the answers come from and go. */
    private fun TestScope.studying(id: String, newADay: Int = NEW_CARDS_A_DAY) = StudyControl(
        scope = backgroundScope,
        ui = ui,
        send = {},
        progress = { maps.summary(id)?.metadata?.study.orEmpty() },
        keep = { change ->
            holding?.await()
            maps.changeStudy(id, change)
        },
        today = { today },
        elsewhere = { maps.summary(id)?.metadata?.studyElsewhere.orEmpty() },
        newADay = { newADay },
        now = { now },
    )

    @Test
    fun `leaving the folder while an answer is on its way keeps the other devices' answers and the new one`() = runTest {
        maps.load()
        val id = maps.add("# Spanish verbs\n\n## Ser\n\n- soy\n\n## Estar\n\n- estoy\n")
        val tablet = StudyCard(box = 2, due = today + 3, first = today - 9, at = now - DAY)
        maps.saveStudy(id, mapOf("ser" to StudyCard(box = 1, due = today, first = today - 9, at = now - 2 * DAY)))
        maps.saveStudyElsewhere(id, mapOf("tablet" to mapOf("estar" to tablet)))
        val control = studying(id)
        control.start("verbs")
        assertEquals(listOf("ser"), study.queue)

        holding = CompletableDeferred()
        control.reveal()
        control.answer(knew = true)
        runCurrent()

        // The folder is left while the answer is on its way: the tablet's answers join this device's (as FolderSync does).
        maps.keepStudyHere()
        holding!!.complete(Unit)
        control.written()

        val kept = mapOf("ser" to StudyCard(box = 2, due = today + 3, first = today - 9, at = now), "estar" to tablet)
        assertEquals(kept, maps.summary(id)?.metadata?.study)
        assertNull(maps.summary(id)?.metadata?.studyElsewhere)
        // As Orbit reads the maps again.
        val again = MapRepository(File(root, "maps"), File(root, "trash"), starters, words).also { it.load() }
        assertEquals(kept, again.summary(id)?.metadata?.study)
    }

    @Test
    fun `Keep going while an answer is on its way goes on from it, and keeps every answer`() = runTest {
        maps.load()
        val id = maps.add("# Top\n\n- Idea 1\n- Idea 2\n- Idea 3\n")
        val ideas = listOf(MapNode("top", "Top", depth = 0, childCount = 3)) + (1..3).map { MapNode("idea-$it", "Idea $it", parentId = "top", depth = 1, note = "Note $it") }
        ui.value = MapUi(ready = true, map = MapState("Top", ideas))
        val control = studying(id, newADay = 1)
        control.start("top")
        assertEquals(listOf("idea-1"), study.queue)

        holding = CompletableDeferred()
        control.reveal()
        control.answer(knew = true)
        runCurrent()
        assertEquals(StudyEnding.Finished, study.ending)
        assertNull(maps.summary(id)?.metadata?.study)

        // Keep going, while Idea 1's answer is still on its way: the session knows it, and brings the next new card.
        control.keepGoing()
        assertEquals(listOf("idea-2"), study.queue)
        control.reveal()
        control.answer(knew = true)
        holding!!.complete(Unit)
        control.written()

        val study = maps.summary(id)?.metadata?.study.orEmpty()
        assertEquals(setOf("idea-1", "idea-2"), study.keys)
        assertEquals(StudyCard(box = 1, due = today + 1, first = today, at = now), study["idea-1"])
    }

    @Test
    fun `an answer taken back after the folder was left gives the card back as the other devices left it`() = runTest {
        maps.load()
        val id = maps.add("# Spanish verbs\n\n## Ser\n\n- soy\n\n## Estar\n\n- estoy\n")
        val tablet = StudyCard(box = 2, due = today, first = today - 9, at = now - DAY)
        maps.saveStudyElsewhere(id, mapOf("tablet" to mapOf("ser" to tablet)))
        val control = studying(id)
        control.start("verbs")
        control.reveal()
        control.answer(knew = false)
        control.written()

        maps.keepStudyHere()
        control.undo()
        control.written()
        assertEquals(mapOf("ser" to tablet), maps.summary(id)?.metadata?.study)
    }
}
