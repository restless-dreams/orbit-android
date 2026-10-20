package com.hyuchia.orbit.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YAML, as the fake conversions write it: the markdown with this in front. */
private const val YAML = "yaml: "

@OptIn(ExperimentalCoroutinesApi::class)
class SourceEditingTest {
    private val trip = "# Trip\n- Food"
    private val ui = MutableStateFlow(MapUi(source = SourceState(trip)))
    private val drawn = mutableListOf<String>()
    private val kept = mutableListOf<String>()

    /** While true, conversions wait in `waiting` until each is let through. */
    private var holding = false

    private val waiting = mutableListOf<CompletableDeferred<Unit>>()

    /** Orbit's conversions, faked: YAML is the markdown with [YAML] in front, and text with "broken" in it can't be read. */
    private suspend fun convert(text: String, to: String): String? {
        if (holding) {
            CompletableDeferred<Unit>().also { waiting += it }.await()
        }

        return when {
            "broken" in text -> null
            to == "yaml" -> YAML + text.removePrefix(YAML)
            else -> text.removePrefix(YAML)
        }
    }

    private val TestScope.editing: SourceEditing
        get() = SourceEditing(
            scope = backgroundScope,
            ui = ui,
            draw = { drawn += it },
            convert = ::convert,
            keep = { kept += it },
        )

    private val source: SourceState get() = ui.value.source!!

    private fun inYaml() {
        ui.update { it.copy(source = SourceState(YAML + trip, yaml = true)) }
    }

    @Test
    fun typingIsDrawnAndSavedOnceItStops() = runTest {
        val editing = editing
        editing.edit("$trip\n- Tram")
        advanceTimeBy(279)
        assertTrue(kept.isEmpty())
        advanceTimeBy(2)
        assertEquals(listOf("$trip\n- Tram"), drawn)
        assertEquals(listOf("$trip\n- Tram"), kept)
    }

    @Test
    fun yamlIsSavedOnlyOnceOrbitReadsAllOfIt() = runTest {
        inYaml()
        val editing = editing
        editing.edit("$YAML$trip\n- broken")
        advanceTimeBy(300)
        assertTrue(kept.isEmpty())
        editing.edit("$YAML$trip\n- Tram")
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Tram"), kept)
    }

    @Test
    fun aMapThatCantBeChangedIsNotSaved() = runTest {
        ui.update { it.copy(readOnly = ReadOnly.Newer) }
        val editing = editing
        editing.edit("$trip\n- Tram")
        advanceTimeBy(300)
        assertTrue(kept.isEmpty())

        // Editing the text is how a map Orbit couldn't read gets fixed.
        ui.update { it.copy(readOnly = ReadOnly.Unreadable) }
        editing.edit("$trip\n- Tram 28")
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Tram 28"), kept)
    }

    @Test
    fun onlyAMapThatCanChangeOrNeedsFixingIsEditedAsText() {
        assertTrue(canEditText(null))
        // A map from a newer Orbit, in the trash, or kept by a newer Orbit shows its text without editing it.
        assertEquals(listOf(ReadOnly.Unreadable), ReadOnly.entries.filter(::canEditText))
    }

    @Test
    fun closingRightAfterTypingKeepsTheTyping() = runTest {
        val editing = editing
        var closedWith: List<String>? = null
        editing.edit("$trip\n- Tram")
        editing.close { closedWith = kept.toList() }
        runCurrent()
        assertNull(ui.value.source)
        assertEquals(listOf("$trip\n- Tram"), closedWith)
    }

    @Test
    fun closingRightAfterTypingYamlKeepsTheTypingToo() = runTest {
        inYaml()
        val editing = editing
        var closedWith: List<String>? = null
        editing.edit("$YAML$trip\n- Tram")
        advanceTimeBy(100)
        editing.close { closedWith = kept.toList() }
        runCurrent()
        assertEquals(listOf("$trip\n- Tram"), closedWith)
    }

    @Test
    fun closingAfterTheTypingWasSavedSavesNothingMore() = runTest {
        val editing = editing
        var closed = false
        editing.edit("$trip\n- Tram")
        advanceTimeBy(300)
        editing.close { closed = true }
        runCurrent()
        assertTrue(closed)
        assertEquals(listOf("$trip\n- Tram"), kept)
    }

    @Test
    fun typingWhileTheTextSwitchesIsConvertedToo() = runTest {
        val editing = editing
        holding = true
        editing.setYaml(true)
        runCurrent()
        assertTrue(source.converting)
        editing.edit("$trip\n- Tram")
        waiting.removeFirst().complete(Unit)
        runCurrent()

        // The conversion of the text before the typing came back: the text as typed is converted again.
        assertTrue(source.converting)
        assertEquals("$trip\n- Tram", source.text)
        waiting.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(SourceState("$YAML$trip\n- Tram", yaml = true), source)
    }

    @Test
    fun textOrbitCantConvertStaysAsItIs() = runTest {
        val editing = editing
        editing.edit("$trip\n- broken")
        editing.setYaml(true)
        runCurrent()
        assertEquals(SourceState("$trip\n- broken"), source)
    }

    @Test
    fun theTextFollowsChangesMadeOutsideIt() = runTest {
        val editing = editing
        editing.follow("# Trip\n- Museums")
        assertEquals("# Trip\n- Museums", source.text)

        // The next keystroke edits the map as it is now.
        editing.edit("# Trip\n- Museums\n- Tram")
        advanceTimeBy(300)
        assertEquals(listOf("# Trip\n- Museums\n- Tram"), kept)
    }

    @Test
    fun yamlFollowsChangesMadeOutsideItOnceConverted() = runTest {
        inYaml()
        val editing = editing
        editing.follow("# Trip\n- Museums")
        runCurrent()
        assertEquals(SourceState("$YAML# Trip\n- Museums", yaml = true), source)
    }

    @Test
    fun typingWhileAChangeConvertsToYamlWins() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("# Trip\n- Museums")
        runCurrent()
        editing.edit("$YAML$trip\n- Tram")
        waiting.removeFirst().complete(Unit)
        runCurrent()
        assertEquals("$YAML$trip\n- Tram", source.text)
    }

    @Test
    fun onlyTheNewestChangeLands() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("# Trip\n- Museums")
        editing.follow("# Trip\n- Museums\n- Tram")
        runCurrent()
        waiting[1].complete(Unit)
        runCurrent()
        waiting[0].complete(Unit)
        runCurrent()
        assertEquals("$YAML# Trip\n- Museums\n- Tram", source.text)
    }

    @Test
    fun aChangeThatLandsAfterASwitchToMarkdownShowsAsMarkdown() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("# Trip\n- Museums")
        editing.setYaml(false)
        runCurrent()
        // The switch lands first, from the YAML before the change.
        waiting[1].complete(Unit)
        runCurrent()
        assertEquals(SourceState(trip), source)
        waiting[0].complete(Unit)
        runCurrent()
        assertEquals(SourceState("# Trip\n- Museums"), source)
    }

    @Test
    fun typingNotYetSavedIsSavedBeforeAnUndo() = runTest {
        val editing = editing
        var keptBeforeTheUndo: List<String>? = null
        editing.edit("$trip\n- Tram")
        editing.afterTyping { keptBeforeTheUndo = kept.toList() }
        runCurrent()
        assertEquals(listOf("$trip\n- Tram"), keptBeforeTheUndo)

        // Saved, it isn't saved again.
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Tram"), kept)
    }

    @Test
    fun withNothingWaitingAnUndoHappensAtOnce() = runTest {
        val editing = editing
        var undone = false
        editing.afterTyping { undone = true }
        assertTrue(undone)
        assertTrue(kept.isEmpty())
    }
}
