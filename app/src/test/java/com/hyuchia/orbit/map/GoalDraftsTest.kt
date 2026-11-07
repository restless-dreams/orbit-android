package com.hyuchia.orbit.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * What's typed into a goal's fields a moment before it changes how it
 * counts, or stops being a goal: saved first, for what it was typed as, and
 * never after the change, over it. Each field saves 700 ms after typing
 * stops, so the clock is held still: the change comes within that wait.
 */
@RunWith(AndroidJUnit4::class)
class GoalDraftsTest {
    @get:Rule
    val compose = createComposeRule()

    /** An idea as the map's page keeps it: each change merged into its attributes, a null taking one out. */
    private class Idea(start: MapNode) {
        var node by mutableStateOf(start)
        val changes = mutableListOf<Map<String, JsonElement>>()

        fun change(made: Map<String, JsonElement>) {
            changes += made
            node = node.copy(attrs = JsonObject((node.attrs.orEmpty() + made).filterValues { it !is JsonNull }))
        }

        val goal: Goal get() = Goal.read(node.attrs)
    }

    private fun goal(vararg attributes: Pair<String, Any>) =
        Idea(MapNode("savings", "Savings", type = GOAL_TYPE, attrs = JsonObject(attributes.associate { (key, value) -> key to if (value is Number) JsonPrimitive(value) else JsonPrimitive(value.toString()) })))

    /** Lets the screen catch up (a frame, then another), well within a field's wait to save. */
    private fun frames() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `a number typed a moment before the goal counts another way is what it turns around from`() {
        val idea = goal(GOAL_TARGET to 3000, GOAL_CURRENT to 1250, GOAL_UNIT to "USD")
        val drafts = FieldDrafts()
        compose.setContent { OrbitTheme(dark = false) { GoalSection(idea.node, Rollup(), drafts, idea::change) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        // $4,000 is the target now, typed; before the field saves it, the goal becomes a debt to pay off.
        compose.onNodeWithText("Target").performTextReplacement("4000")
        compose.onNodeWithText("Paying off").performClick()
        frames()

        // $4,000 owed at the start, $1,250 of it paid: $2,750 to go. The field leaving saves nothing more over it.
        assertEquals(GoalWay.PayingOff, idea.goal.way)
        assertEquals(0, BigDecimal(4000).compareTo(idea.goal.start))
        assertEquals(0, BigDecimal(2750).compareTo(idea.goal.current))
        assertEquals(0, BigDecimal.ZERO.compareTo(idea.goal.target))
    }

    @Test
    fun `a word typed a moment before counting turns into money doesn't take the money back`() {
        val idea = goal(GOAL_TARGET to 10, GOAL_CURRENT to 3, GOAL_UNIT to "apples")
        val drafts = FieldDrafts()
        compose.setContent { OrbitTheme(dark = false) { GoalSection(idea.node, Rollup(), drafts, idea::change) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("What's counted (optional)").performTextReplacement("pears")
        compose.onNodeWithText("Saving up").performClick()
        frames()

        assertEquals(GoalWay.SavingUp, idea.goal.way)
        assertEquals("USD", idea.goal.unit)
        assertEquals(0, BigDecimal(10).compareTo(idea.goal.target))
    }

    @Test
    fun `a number typed a moment before the idea stops being a goal is saved before the change, never over it`() {
        val idea = goal(GOAL_TARGET to 3000, GOAL_CURRENT to 1250, GOAL_UNIT to "USD")
        val drafts = FieldDrafts()
        var isGoal by mutableStateOf(true)

        compose.setContent {
            OrbitTheme(dark = false) {
                if (isGoal) {
                    GoalSection(idea.node, Rollup(), drafts, idea::change)
                }
            }
        }

        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Target").performTextReplacement("4000")

        // As the editor makes the idea a milestone: what's typed is saved first, then its numbers go with the goal.
        drafts.saveNow()
        idea.change(Goal.removed + ("type" to JsonPrimitive("milestone")))
        isGoal = false
        frames()

        assertEquals(Goal.removed + ("type" to JsonPrimitive("milestone")), idea.changes.last())
        assertFalse(idea.node.attrs.orEmpty().keys.any { it in GOAL_ATTRIBUTES })
    }
}
