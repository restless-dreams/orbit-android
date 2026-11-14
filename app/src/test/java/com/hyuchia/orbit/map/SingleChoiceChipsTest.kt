package com.hyuchia.orbit.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor's Status and Kind, and the Style panel's levels, as TalkBack
 * hears them: one choice among several, so radio buttons in a group, each
 * selected or not, never checkboxes, which would say any number of them can
 * be on at once.
 */
@RunWith(AndroidJUnit4::class)
class SingleChoiceChipsTest {
    @get:Rule
    val compose = createComposeRule()

    /** A chip as TalkBack hears it: its name, its role, and whether it's the one chosen. */
    private data class Heard(val name: String, val role: Role?, val selected: Boolean?)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { OrbitTheme(dark = false) { content() } }
        compose.waitForIdle()
    }

    /** Every chip, in order, and how many chips the group around them holds. */
    private fun heard(): Pair<List<Heard>, Int> {
        val chips = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().map { node ->
            assertEquals(null, node.config.getOrNull(SemanticsProperties.ToggleableState))
            Heard(node.config[SemanticsProperties.Text].joinToString { it.text }, node.config.getOrNull(SemanticsProperties.Role), node.config[SemanticsProperties.Selected])
        }

        val groups = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).fetchSemanticsNodes()
        assertEquals(1, groups.size)
        return chips to groups.single().children.size
    }

    private fun radios(names: List<String>, selected: String?): Pair<List<Heard>, Int> = names.map { Heard(it, Role.RadioButton, it == selected) } to names.size

    @Test
    fun `an idea's status is one of them, as radio buttons`() {
        var picked: String? = "nothing yet"
        show { StatusChoices(MapNode("beds", "Beds", status = " Done ")) { picked = it } }

        assertEquals(radios(listOf("None", "To do", "In progress", "Done", "Blocked"), "Done"), heard())

        compose.onNodeWithText("Blocked").performClick()
        assertEquals("blocked", picked)
        compose.onNodeWithText("None").performClick()
        assertEquals(null, picked)
    }

    @Test
    fun `an idea's kind is one of them, as radio buttons, and none of them for a kind the app doesn't know`() {
        val kinds = listOf("Idea", "Milestone", "Question", "Decision", "Risk", "Source", "Person", "Progress", "Goal")
        var picked: NodeKind? = null
        var node by mutableStateOf(MapNode("beds", "Beds", type = "milestone"))
        show { KindChoices(node) { picked = it } }

        assertEquals(radios(kinds, "Milestone"), heard())

        compose.onNodeWithText("Goal").performClick()
        assertEquals("goal", picked?.type)

        node = MapNode("beds", "Beds", type = "task", typeVersion = 3)
        compose.waitForIdle()
        assertEquals(radios(kinds, null), heard())

        // One written for a newer version of a kind is that kind, chosen, though the map draws it as none.
        node = MapNode("beds", "Beds", type = "Goal", typeVersion = 2)
        compose.waitForIdle()
        assertEquals(radios(kinds, "Goal"), heard())
    }

    @Test
    fun `the levels the map shows are all of them or one number, as radio buttons`() {
        var picked: Int? = 0
        show { LevelChoices(2) { picked = it } }

        assertEquals(radios(listOf("All", "1", "2", "3", "4"), "2"), heard())

        compose.onNodeWithText("All").performClick()
        assertEquals(null, picked)
        compose.onNodeWithText("4").performClick()
        assertEquals(4, picked)
    }
}
