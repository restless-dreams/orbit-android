package com.hyuchia.orbit.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The outline as the screen turns: its panel moves between a sheet over the
 * map and the map's side, a phone's or a tablet's either way, and is made
 * anew there, while the outline's folds and its list's place are kept above
 * it. Closed and opened again, it starts afresh.
 */
@RunWith(AndroidJUnit4::class)
class OutlineTurnsTest {
    @get:Rule
    val compose = createComposeRule()

    /** A solar system: forty planets, the first with two moons. */
    private val ui = MapUi(
        map = MapState(
            "Solar system",
            listOf(MapNode("solar-system", "Solar system", childCount = 40)) +
                (1..40).flatMap { number ->
                    val planet = MapNode("planet-$number", "Planet $number", parentId = "solar-system", depth = 1, childCount = if (number == 1) 2 else 0)
                    val moons = if (number == 1) listOf("a", "b").map { MapNode("moon-$it", "Moon $it", parentId = "planet-1", depth = 2) } else emptyList()
                    listOf(planet) + moons
                },
        ),
    )

    private val actions = OutlineActions(onDismiss = {}, onFocus = {}, onEdit = {}, onCheck = { _, _ -> }, onDragOut = { _, _ -> false })

    /** Waits for a row that says `text`, in the sheet's window or beside the map. */
    private fun waitForRow(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shows(text: String): Boolean = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `turning the screen keeps the outline's folds and place in the list, both ways`() {
        var sheet by mutableStateOf(false)
        lateinit var outline: MapOutline

        compose.setContent {
            outline = rememberMapOutline(open = true)

            // As MapScreen has the panel: beside the map, or a sheet over it, wherever the screen's shape puts it.
            if (sheet) {
                Box { OutlinePanel(ui, sheet = true, outline, actions) }
            } else {
                Row { OutlinePanel(ui, sheet = false, outline, actions) }
            }
        }

        waitForRow("Moon a")

        // Planet 1's moons folded away, by the second fold button (the sun's is the first), and the list scrolled on.
        compose.onAllNodesWithContentDescription("Fold its ideas")[1].performClick()
        compose.waitUntil(timeoutMillis = 10_000) { !shows("Moon a") }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        waitForRow("Planet 30")

        fun keptAsItWas() {
            waitForRow("Planet 30")
            compose.onNode(hasText("Planet 30")).assertIsDisplayed()
            assertTrue("The list starts where it was, not at its top", !shows("Solar system"))
            assertEquals(mapOf("planet-1" to true), outline.folded.toMap())

            // Back at the top, Planet 1's moons are still folded away.
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            waitForRow("Planet 1")
            assertTrue("The fold stays", !shows("Moon a"))
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
            waitForRow("Planet 30")
        }

        // A tablet turned upright: the panel beside the map goes, and a sheet comes up.
        compose.runOnIdle { sheet = true }
        keptAsItWas()

        // And on its side again: the sheet goes, and the panel opens beside the map.
        compose.runOnIdle { sheet = false }
        keptAsItWas()
    }

    @Test
    fun `an outline closed and opened again starts afresh`() {
        var open by mutableStateOf(true)
        lateinit var outline: MapOutline

        compose.setContent {
            outline = rememberMapOutline(open = open)

            if (open) {
                Row { OutlinePanel(ui, sheet = false, outline, actions) }
            }
        }

        waitForRow("Moon a")
        compose.onAllNodesWithContentDescription("Fold its ideas")[1].performClick()
        compose.waitUntil(timeoutMillis = 10_000) { !shows("Moon a") }

        compose.runOnIdle { open = false }
        compose.runOnIdle { open = true }
        waitForRow("Moon a")

        assertEquals(emptyMap<String, Boolean>(), outline.folded.toMap())
        assertEquals(0, outline.place.index)
    }
}
