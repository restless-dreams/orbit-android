package com.hyuchia.orbit.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.MapNode
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * The map's search as the screen turns: its panel moves between a sheet over
 * the map and the map's side, a phone's or a tablet's either way, and is made
 * anew there, while the search itself is kept above it. And Search pressed on
 * the keyboard before the map's ideas are folded, on a slow phone with a big
 * map, waits for them away from the main thread.
 */
@RunWith(AndroidJUnit4::class)
class SearchTurnsTest {
    @get:Rule
    val compose = createComposeRule()

    /** Where the map's ideas are folded and searched: one thread of its own, which a test can hold up. */
    private val executor = Executors.newSingleThreadExecutor()

    private val background = executor.asCoroutineDispatcher()

    /** A solar system with sixty moons, each a row of the results. */
    private val nodes = listOf(MapNode("solar-system", "Solar system")) + (1..60).map { MapNode("moon-$it", "Moon $it", parentId = "solar-system") }

    @After
    fun stopBackground() {
        executor.shutdownNow()
    }

    /** Waits for a row of the results that says `text`, in the sheet's window or beside the map. */
    private fun waitForRow(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The field says `typed`, has the focus, and the cursor at the end, where the next key goes, as it had before the turn. */
    private fun typingGoesOnAfter(typed: String) {
        val field = compose.onNode(hasSetTextAction())

        field.assert(hasText(typed))
        field.assertIsFocused()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(typed.length)))
    }

    @Test
    fun `turning the screen keeps the search's words, results, folded ideas and place in the list, both ways`() {
        var sheet by mutableStateOf(true)
        lateinit var search: MapSearch

        compose.setContent {
            search = rememberMapSearch(nodes, open = true, background = background)

            // As MapScreen has the panel: a sheet over the map, or beside it, wherever the screen's shape puts it.
            if (sheet) {
                Box { SearchPanel(search, sheet = true, onOpen = {}, onDismiss = {}) }
            } else {
                Row { SearchPanel(search, sheet = false, onOpen = {}, onDismiss = {}) }
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNode(hasSetTextAction()).performTextInput("moon")
        waitForRow("Moon 1")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        compose.waitForIdle()
        waitForRow("Moon 31")
        val folded = search.index

        // A phone or tablet turned on its side: the sheet goes, and the panel opens beside the map.
        compose.runOnIdle { sheet = false }
        waitForRow("Moon 31")

        typingGoesOnAfter("moon")
        compose.onNode(hasText("Moon 31")).assertIsDisplayed()
        assertTrue("The list starts where it was, not at its top", compose.onAllNodes(hasText("Moon 1")).fetchSemanticsNodes().isEmpty())
        assertSame("The map's ideas aren't folded again", folded, search.index)

        // And upright again: the panel beside the map goes, and the sheet comes back.
        compose.runOnIdle { sheet = true }
        waitForRow("Moon 31")

        typingGoesOnAfter("moon")
        compose.onNode(hasText("Moon 31")).assertIsDisplayed()
        assertTrue("The list starts where it was, not at its top", compose.onAllNodes(hasText("Moon 1")).fetchSemanticsNodes().isEmpty())
        assertSame("The map's ideas aren't folded again", folded, search.index)
    }

    @Test
    fun `a search closed and opened again starts empty`() {
        var open by mutableStateOf(true)
        lateinit var search: MapSearch

        compose.setContent {
            search = rememberMapSearch(nodes, open = open, background = background)

            if (open) {
                Row { SearchPanel(search, sheet = false, onOpen = {}, onDismiss = { open = false }) }
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("moon")
        waitForRow("Moon 1")

        compose.runOnIdle { open = false }
        compose.runOnIdle { open = true }
        compose.waitForIdle()

        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals("", search.query)
        assertEquals(0, search.place.index)
    }

    /**
     * Shows the search beside the map with the thread that folds the map's
     * ideas held up, as on a slow phone with a map of thousands of ideas, and
     * types `typed`: the latch lets the thread go on.
     */
    private fun searchWhileFolding(typed: String, onOpen: (MapNode) -> Unit): Pair<MapSearch, CountDownLatch> {
        val held = CountDownLatch(1)
        executor.execute { held.await() }
        lateinit var search: MapSearch

        compose.setContent {
            search = rememberMapSearch(nodes, open = true, background = background)
            Row { SearchPanel(search, sheet = false, onOpen = onOpen, onDismiss = {}) }
        }

        compose.onNode(hasSetTextAction()).performTextInput(typed)
        return search to held
    }

    @Test
    fun `Search pressed before the map's ideas are folded opens the first match once they are, not on the main thread`() {
        var opened: MapNode? = null
        val (search, held) = searchWhileFolding("moon 7") { opened = it }

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()

        // Folding the map here, on the main thread, to open a match at once, held a slow phone up.
        assertNull(opened)
        assertNull(search.index)

        held.countDown()
        compose.waitUntil(timeoutMillis = 10_000) { opened != null }
        assertEquals("moon-7", opened?.id)
    }

    @Test
    fun `Search pressed, then more typed before the search ends, opens nothing`() {
        var opened: MapNode? = null
        val (search, held) = searchWhileFolding("moon 7") { opened = it }

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).performTextInput("0")
        held.countDown()
        compose.waitUntil(timeoutMillis = 10_000) { search.found.query == "moon 70" }
        compose.waitForIdle()

        assertNull(opened)
    }

    @Test
    fun `Search pressed, then more typed and taken back before the search ends, opens nothing`() {
        var opened: MapNode? = null
        val (search, held) = searchWhileFolding("moon 7") { opened = it }

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).performTextInput("0")
        // Back to the words Search was pressed for: the press was for them before, not now.
        compose.onNode(hasSetTextAction()).performTextReplacement("moon 7")
        held.countDown()
        compose.waitUntil(timeoutMillis = 10_000) { search.found.query == "moon 7" }
        compose.waitForIdle()

        assertNull(opened)
    }

    @Test
    fun `Search pressed, then the field cleared and the words typed again before the search ends, opens nothing`() {
        var opened: MapNode? = null
        val (search, held) = searchWhileFolding("moon 7") { opened = it }

        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("moon 7")
        held.countDown()
        compose.waitUntil(timeoutMillis = 10_000) { search.found.query == "moon 7" }
        compose.waitForIdle()

        assertNull(opened)
    }

    @Test
    fun `Search pressed once the search has ended opens its first match at once`() {
        var opened: MapNode? = null

        compose.setContent {
            val search = rememberMapSearch(nodes, open = true, background = background)
            Row { SearchPanel(search, sheet = false, onOpen = { opened = it }, onDismiss = {}) }
        }

        compose.onNode(hasSetTextAction()).performTextInput("moon 4")
        waitForRow("Moon 4")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertEquals("moon-4", opened?.id)
    }
}
