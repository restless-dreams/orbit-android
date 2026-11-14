package com.hyuchia.orbit.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.MapNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The caption over a presentation on this screen, laid over its touches as
 * the presentation lays it: a swipe across it steps, as one across the map
 * does, both ways and in a language read right to left; a tap stays the
 * caption's, and a long note still scrolls.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CaptionSwipesTest {
    @get:Rule
    val compose = createComposeRule()

    private val steps = mutableListOf<Int>()
    private var taps = 0

    private val short = MapNode(id = "alfama", text = "Alfama", depth = 1, note = "The old town, on its hill.")
    private val long = MapNode(id = "belem", text = "Belém", depth = 1, note = (1..40).joinToString("\n\n") { "Stop $it by the river." })

    /** A swipe of `distance` pixels across, starting on a node: positive goes right, negative left, whatever the node's width. */
    private fun TouchInjectionScope.across(distance: Float) = swipe(center, Offset(center.x + distance, center.y), durationMillis = 300)

    /** A swipe of `distance` pixels up, starting on a node. */
    private fun TouchInjectionScope.up(distance: Float) = swipe(center, Offset(center.x, center.y - distance), durationMillis = 300)

    private fun present(node: MapNode, rightToLeft: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    // The presentation's own touches, under the caption, as on this screen.
                    Box(Modifier.fillMaxSize().presentationGestures(rightToLeft, onTap = { taps += 1 }, onStep = { steps += it }))

                    Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                        Caption(node, rightToLeft, onStep = { steps += it }, onNodeLink = {})
                    }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { runCatching { compose.onNodeWithText(node.text).assertIsDisplayed() }.isSuccess }
    }

    @Test
    fun `a swipe that starts on a short caption steps on and back, as one on the map does`() {
        present(short)

        // The note, drawn once its markdown is read, which can take a while on a busy machine.
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("The old town, on its hill.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The old town, on its hill.", substring = true).performTouchInput { across(-600f) }
        compose.onNodeWithText("Alfama").performTouchInput { across(600f) }
        compose.waitForIdle()

        assertEquals(listOf(1, -1), steps)
        assertEquals(0, taps)
    }

    @Test
    fun `a swipe that starts on a long caption steps too, and one up or down scrolls its note instead`() {
        present(long)

        // The note, drawn once its markdown is read: a swipe up scrolls it, by its first stop's place.
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Stop 1 by the river.", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val note = compose.onAllNodesWithText("Stop 1 by the river.", substring = true, useUnmergedTree = true).onFirst()
        val before = note.getUnclippedBoundsInRoot().top
        note.performTouchInput { up(500f) }
        compose.waitForIdle()
        assertTrue(note.getUnclippedBoundsInRoot().top < before - 50.dp)
        assertEquals(emptyList<Int>(), steps)

        compose.onNodeWithText("Belém").performTouchInput { across(-600f) }
        compose.waitForIdle()
        assertEquals(listOf(1), steps)
    }

    @Test
    fun `read right to left, a swipe the other way steps on`() {
        present(short, rightToLeft = true)

        compose.onNodeWithText("Alfama").performTouchInput { across(600f) }
        compose.waitForIdle()

        assertEquals(listOf(1), steps)
    }

    @Test
    fun `a tap on the caption stays the caption's`() {
        present(short)

        compose.onNodeWithText("Alfama").performTouchInput { click() }
        compose.waitForIdle()

        assertEquals(0, taps)
        assertEquals(emptyList<Int>(), steps)
    }
}
