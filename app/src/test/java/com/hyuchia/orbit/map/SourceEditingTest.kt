package com.hyuchia.orbit.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

    /** What was kept in the map's history: text typed on the map as it was before a change made outside the editor. */
    private val setAside = mutableListOf<String>()

    /** While true, conversions wait in `waiting` until each is let through. */
    private var holding = false

    private val waiting = mutableListOf<CompletableDeferred<Unit>>()

    /**
     * Orbit's conversions, faked: YAML is the markdown with [YAML] in front,
     * and text with "broken" in it can't be read. Markdown written from YAML
     * is in Orbit's own form, as the real one is: without comments, and its
     * lists with dashes.
     */
    private suspend fun convert(text: String, to: String): String? {
        if (holding) {
            CompletableDeferred<Unit>().also { waiting += it }.await()
        }

        return when {
            "broken" in text -> null
            to == "yaml" -> YAML + text.removePrefix(YAML)
            else -> text.removePrefix(YAML).lines().filterNot { it.startsWith("<!--") }.joinToString("\n") { it.replace(Regex("^\\* "), "- ") }
        }
    }

    private val TestScope.editing: SourceEditing
        get() = SourceEditing(
            scope = backgroundScope,
            ui = ui,
            draw = { drawn += it },
            convert = ::convert,
            keep = { kept += it },
            keepAside = { setAside += it },
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
    fun aFlushSavesTheTypingAtOnceAndOnlyOnce() = runTest {
        val editing = editing
        editing.flush()
        assertTrue(kept.isEmpty())
        editing.edit("$trip\n- Tram")
        editing.flush()
        assertEquals(listOf("$trip\n- Tram"), kept)

        // Saved, it isn't saved again once the pause is over.
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Tram"), kept)
        assertTrue(drawn.isEmpty())
    }

    @Test
    fun aFlushSavesYamlStillConvertingOnceTheScreensWorkHasStopped() = runTest {
        inYaml()
        val screen = CoroutineScope(StandardTestDispatcher(testScheduler))
        val editing = SourceEditing(screen, ui, draw = { drawn += it }, convert = ::convert, keep = { kept += it }, keepAside = { setAside += it })
        holding = true
        editing.edit("$YAML$trip\n- Tram")
        advanceTimeBy(300)
        assertEquals(1, waiting.size)

        // The map closes as the typing converts: the screen's work stops, that conversion with it, and the map asks
        // for the typing while its page is still there.
        screen.cancel()
        val flushing = launch { editing.flush() }
        runCurrent()
        waiting.last().complete(Unit)
        runCurrent()
        assertTrue(flushing.isCompleted)
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
    fun switchingBackWithoutAYamlEditGivesTheMarkdownBackAsWritten() = runTest {
        val written = "# Trip\n<!-- Booked in May -->\n* Food"
        ui.update { it.copy(source = SourceState(written)) }
        val editing = editing
        editing.setYaml(true)
        runCurrent()
        assertEquals(SourceState("$YAML$written", yaml = true), source)
        editing.setYaml(false)
        runCurrent()
        assertEquals(SourceState(written), source)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun switchingBackAfterAYamlEditShowsOrbitsOwnMarkdown() = runTest {
        ui.update { it.copy(source = SourceState("# Trip\n<!-- Booked in May -->\n* Food")) }
        val editing = editing
        editing.setYaml(true)
        runCurrent()
        editing.edit("$YAML# Trip\n<!-- Booked in May -->\n* Food\n* Tram")
        advanceTimeBy(300)
        editing.setYaml(false)
        runCurrent()
        assertEquals(SourceState("# Trip\n- Food\n- Tram"), source)
        assertEquals(listOf("# Trip\n- Food\n- Tram"), kept)
    }

    @Test
    fun switchingBackAfterTheMapChangedShowsTheMapAsItIsNow() = runTest {
        ui.update { it.copy(source = SourceState("# Trip\n<!-- Booked in May -->\n* Food")) }
        val editing = editing
        editing.setYaml(true)
        runCurrent()

        // An undo, or an edit on the map beside the text.
        editing.follow("# Trip\n* Food\n* Museums")
        runCurrent()
        editing.setYaml(false)
        runCurrent()
        assertEquals(SourceState("# Trip\n- Food\n- Museums"), source)
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

    /** Lets every conversion waiting through, each in turn, the oldest first. */
    private fun TestScope.letConversionsThrough() {
        while (waiting.isNotEmpty()) {
            waiting.removeFirst().complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun `a change that comes as the text switches to markdown is what's typed on, never the text from before it`() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.setYaml(false)
        runCurrent()

        // The map changes outside the editor as the switch converts: an edit on the map beside it, say.
        editing.follow("$trip\n- Museums")
        runCurrent()

        // The switch, from the YAML before the change, comes back first, and the next keystroke goes on from what the text shows.
        waiting.removeFirst().complete(Unit)
        runCurrent()
        editing.edit(source.text + "\n- Typed")
        letConversionsThrough()
        advanceTimeBy(300)
        letConversionsThrough()

        assertEquals(listOf("$trip\n- Museums\n- Typed"), kept)
        assertEquals(SourceState("$trip\n- Museums\n- Typed"), source)
    }

    @Test
    fun `a switch to markdown as a change converts to YAML shows the change, and typing goes on from it`() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("$trip\n- Museums")
        runCurrent()
        editing.setYaml(false)
        runCurrent()

        // The newest conversion comes back first: the switch's, of the YAML from before the change, when it makes one.
        waiting.removeLast().complete(Unit)
        runCurrent()
        editing.edit(source.text + "\n- Typed")
        letConversionsThrough()
        advanceTimeBy(300)
        letConversionsThrough()

        assertEquals(listOf("$trip\n- Museums\n- Typed"), kept)
        assertEquals(SourceState("$trip\n- Museums\n- Typed"), source)
    }

    @Test
    fun `typing as a change converts to YAML is never saved over the change`() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("$trip\n- Museums")
        runCurrent()

        // Typed on the YAML from before the change, which the text still shows as it converts.
        editing.edit("$YAML$trip\n- Tram")
        letConversionsThrough()
        advanceTimeBy(300)
        letConversionsThrough()

        // The change shows, and what was typed on the text before it is in the map's history, not drawn over the change.
        assertTrue(kept.isEmpty())
        assertEquals(SourceState("$YAML$trip\n- Museums", yaml = true), source)
        assertEquals(listOf("$trip\n- Tram"), setAside)
        assertTrue(drawn.isEmpty())
    }

    @Test
    fun `typing in its pause when the map changes outside the editor goes into its history, not over the change`() = runTest {
        val editing = editing
        editing.edit("$trip\n- Tram")
        // An edit on the map beside the text before the typing's pause is over.
        editing.follow("$trip\n- Museums")
        advanceTimeBy(300)

        assertTrue(kept.isEmpty())
        assertEquals(listOf("$trip\n- Tram"), setAside)
        assertEquals(SourceState("$trip\n- Museums"), source)
        assertTrue(drawn.isEmpty())

        // The next keystroke edits the map as it is now, and is saved.
        editing.edit("$trip\n- Museums\n- Tram")
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Museums\n- Tram"), kept)
        assertEquals(listOf("$trip\n- Tram"), setAside)
    }

    @Test
    fun `typing on the text before a change, then on the change, keeps both`() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("$trip\n- Museums")
        runCurrent()
        editing.edit("$YAML$trip\n- Tram")
        letConversionsThrough()

        // Typed again on the text the change brought, before the pause after the first typing was over.
        editing.edit(source.text + "\n- Fado")
        advanceTimeBy(300)
        letConversionsThrough()

        assertEquals(listOf("$trip\n- Museums\n- Fado"), kept)
        assertEquals(listOf("$trip\n- Tram"), setAside)
    }

    @Test
    fun `the editor opened again starts on the map as it is, whatever was converting`() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.follow("$trip\n- Museums")
        runCurrent()
        editing.close { }
        editing.open("$trip\n- Museums")
        letConversionsThrough()

        // The change converting as the editor closed doesn't land over the text it opened on, and typing there is saved.
        assertEquals(SourceState("$trip\n- Museums"), source)
        editing.edit("$trip\n- Museums\n- Fado")
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Museums\n- Fado"), kept)
        assertTrue(setAside.isEmpty())
    }

    @Test
    fun `a switch still converting as the editor closes lands nothing in the editor opened again`() = runTest {
        val editing = editing
        holding = true
        editing.setYaml(true)
        runCurrent()

        // Closed and opened again as the switch converts, after the map changed meanwhile.
        editing.close { }
        editing.follow("$trip\n- Museums")
        editing.open("$trip\n- Museums")
        letConversionsThrough()

        assertEquals(SourceState("$trip\n- Museums"), source)
        editing.edit("$trip\n- Museums\n- Fado")
        advanceTimeBy(300)
        assertEquals(listOf("$trip\n- Museums\n- Fado"), kept)
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
    fun undosWhileTypedYamlConvertsComeAfterIt() = runTest {
        inYaml()
        val editing = editing
        holding = true
        val undone = mutableListOf<List<String>>()
        editing.edit("$YAML$trip\n- Tram")
        editing.afterTyping { undone += kept.toList() }
        editing.afterTyping { undone += kept.toList() }
        runCurrent()

        // Both wait for the typing to be converted and saved, then come in turn.
        assertTrue(undone.isEmpty())
        waiting.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(listOf(listOf("$trip\n- Tram"), listOf("$trip\n- Tram")), undone)
    }

    @Test
    fun typedYamlStillConvertingIsntSavedOverAChangeMadeSince() = runTest {
        inYaml()
        val editing = editing
        holding = true
        var closed = false
        editing.edit("$YAML$trip\n- Tram")
        editing.close { closed = true }
        runCurrent()

        // An edit on the map, an undo or the file changed elsewhere, while the typing converts.
        editing.follow("# Trip\n- Museums")
        waiting.removeFirst().complete(Unit)
        runCurrent()
        assertTrue(kept.isEmpty())
        assertTrue(closed)
        // Kept in the map's history instead, so it isn't lost.
        assertEquals(listOf("$trip\n- Tram"), setAside)
    }

    @Test
    fun anOlderConversionNeverLandsAfterANewerOne() = runTest {
        inYaml()
        val editing = editing
        holding = true
        editing.edit("$YAML$trip\n- Tram")
        // An undo asked for: the typing converts first.
        editing.afterTyping { }
        runCurrent()
        editing.edit("$YAML$trip\n- Tram 28")
        advanceTimeBy(300)

        // The newer text's conversion comes back first.
        while (waiting.isNotEmpty()) {
            waiting.removeLast().complete(Unit)
            runCurrent()
        }

        assertEquals("$trip\n- Tram 28", kept.last())
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
