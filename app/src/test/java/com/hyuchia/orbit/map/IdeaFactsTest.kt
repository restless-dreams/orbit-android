package com.hyuchia.orbit.map

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What an idea is, at the top of its details: its kind, status, priority,
 * date and tags read as words, to screen readers too, and none of them looks or acts
 * like something to tap, as the details' Edit changes them.
 */
@RunWith(AndroidJUnit4::class)
class IdeaFactsTest {
    @get:Rule
    val compose = createComposeRule()

    private val beds = MapNode("beds", "Beds", status = " done ", priority = 1, tags = listOf("spring", "soil"), type = "decision")

    private fun show(node: MapNode) {
        compose.setContent { OrbitTheme(dark = false) { IdeaFacts(node) } }
        compose.waitForIdle()
    }

    /** What screen readers say of each fact, in order. */
    private fun said(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.ContentDescription] }

    @Test
    fun `each fact is words, and none is a button`() {
        show(beds)

        assertEquals(listOf("Decision", "Status: Done", "Priority 1", "Tags: spring, soil"), said())
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().size)
    }

    @Test @Config(qualifiers = "es")
    fun `a status with spaces around it reads as the status, in the app's language`() {
        show(beds)

        assertEquals("Estado: Hecho", said()[1])
    }

    @Test
    fun `a date reads as its day, and one past and not done says it's overdue`() {
        show(MapNode("beds", "Beds", due = "2020-03-07"))

        assertEquals(listOf("Overdue: Sat, Mar 7, 2020"), said())
    }

    @Test
    fun `a done idea's date is only its date`() {
        show(MapNode("beds", "Beds", status = "done", due = "2020-03-07"))

        assertEquals(listOf("Status: Done", "Date: Sat, Mar 7, 2020"), said())
    }

    @Test
    fun `a status of nothing but spaces is none`() {
        show(MapNode("beds", "Beds", status = "   "))

        assertEquals(emptyList<String>(), said())
    }

    @Test
    fun `a kind reads as the map draws it, whatever its spaces and capitals`() {
        show(MapNode("beds", "Beds", type = " Milestone "))

        assertEquals(listOf("Milestone"), said())
    }

    @Test
    fun `a kind written for a newer version than the map draws is none it says`() {
        show(MapNode("beds", "Beds", type = "milestone", typeVersion = 2))

        assertEquals(emptyList<String>(), said())
    }
}
