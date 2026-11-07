package com.hyuchia.orbit.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.HomeKey
import com.hyuchia.orbit.MapKey
import com.hyuchia.orbit.listAndMapDirective
import com.hyuchia.orbit.listBesideMap
import com.hyuchia.orbit.rememberListDetailScenes
import com.hyuchia.orbit.roomForTwoPanes
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A note's tables and checklists as people read them in the details, the
 * presenter's view and study: a table they can scroll and hear as one, and
 * checkboxes that check with a press on the item, or show as they are.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class MarkdownNoteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val trip = """
        Before we go:

        - [ ] Book the **flights**
        - [x] Renew the passport
          - [ ] Photos
        - Pack light

        | Day | Plan | Budget |
        | :-- | :--- | -----: |
        | Mon | Monte Albán, early, before the heat of the day | $40 |
        | Tue | Mercado 20 de Noviembre, then the zócalo at night | $25 |
    """.trimIndent()

    /** A checklist item's row: a checkbox, as screen readers hear it, in that state. */
    private fun box(state: ToggleableState) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    private fun show(note: String = trip, width: Int = 360, checks: Map<Int, Boolean> = emptyMap(), onCheck: ((NoteCheck) -> Unit)? = null) {
        compose.setContent {
            OrbitTheme(dark = false) {
                Box(Modifier.width(width.dp)) {
                    MarkdownNote(note, plain = false, onNodeLink = {}, checks = checks, onCheck = onCheck)
                }
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Mon")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a table shows its cells, its header cells as headings, and is heard as a table`() {
        show()

        compose.onNode(hasText("Day") and isHeading()).assertExists()
        compose.onNode(hasText("Budget") and isHeading()).assertExists()
        compose.onNode(hasText("$40")).assertExists()
        val table = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNode()
        val info = table.config[SemanticsProperties.CollectionInfo]
        assertEquals(3, info.rowCount)
        assertEquals(3, info.columnCount)
    }

    /** How far the note's table scrolls sideways. */
    private fun tableScroll(): Float =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue()

    @Test
    fun `a table wider than the note scrolls sideways inside it`() {
        show(width = 300)

        assertTrue(tableScroll() > 0f)
    }

    @Test
    fun `a table narrower than the note takes its width, and doesn't scroll`() {
        show(note = "| Day | Plan |\n|---|---|\n| Mon | Hike |\n\nMon", width = 600)

        assertEquals(0f, tableScroll())
    }

    @Test
    fun `a checklist item is one checkbox, named by its text, that checks with a press and says which line`() {
        val checks = mutableListOf<NoteCheck>()
        show(onCheck = { checks += it })

        val flights = compose.onNode(hasText("Book the flights") and box(ToggleableState.Off))
        flights.assertExists()
        assertTrue(flights.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        compose.onNode(hasText("Renew the passport") and box(ToggleableState.On)).performClick()
        flights.performClick()

        assertEquals(listOf(NoteCheck(3, false, "Renew the passport"), NoteCheck(2, true, "Book the flights")), checks)
        // The list under an item, and an item without a box, aren't part of its press.
        compose.onNode(hasText("Photos") and box(ToggleableState.Off)).assertExists()
        compose.onNode(hasText("Pack light") and SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState)).assertExists()
    }

    @Test
    fun `a box checked here shows checked until the note says so`() {
        show(checks = mapOf(2 to true, 3 to false), onCheck = {})

        compose.onNode(hasText("Book the flights") and box(ToggleableState.On)).assertExists()
        compose.onNode(hasText("Renew the passport") and box(ToggleableState.Off)).assertExists()
    }

    @Test
    fun `without a way to check them, the boxes show as they are and take no press`() {
        show()

        val passport = compose.onNode(hasText("Renew the passport") and box(ToggleableState.On)).fetchSemanticsNode()
        assertTrue(!passport.config.contains(SemanticsActions.OnClick))
        compose.onNode(hasText("Book the flights") and box(ToggleableState.Off)).assertExists()
    }

    private val backStack = mutableStateListOf<NavKey>(HomeKey, MapKey("trip"))

    /** Whether the map wants the window to itself, as a panel opening beside it has it. */
    private var wantsRoom by mutableStateOf(false)

    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `a note's table and checklist lay out as a panel opens beside the map on a tablet`() {
        compose.setContent {
            val window = LocalWindowInfo.current.containerDpSize
            val directive = listAndMapDirective(currentWindowAdaptiveInfo(), window, wantsRoom)

            OrbitTheme(dark = false) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(rememberListDetailScenes(homeOnTop = false, roomForTwo = roomForTwoPanes(window), listFits = listBesideMap(listAndMapDirective(currentWindowAdaptiveInfo(), window, mapWantsRoom = false)), directive = directive)),
                    entryProvider = entryProvider {
                        entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text("Pick a map") })) { Text("Your maps", Modifier.fillMaxSize()) }

                        entry<MapKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            Box(Modifier.fillMaxSize()) { MarkdownNote(trip, plain = false, onNodeLink = {}, onCheck = {}) }
                        }
                    },
                )
            }
        }

        compose.waitForIdle()
        wantsRoom = true
        compose.waitForIdle()
        wantsRoom = false
        compose.waitForIdle()

        compose.onNode(hasText("Book the flights") and box(ToggleableState.Off)).assertExists()
        compose.onNode(hasText("$25")).assertExists()
    }
}
