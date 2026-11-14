package com.hyuchia.orbit.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.ui.ControlsStepAside
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A presentation with a keyboard ([PresentationKeys]): its own keys step
 * through it, but a control Tab went to takes Space and Enter, as it takes
 * a tap. Its controls step aside after a moment ([ControlsStepAside]), and
 * stay while one of them has the keyboard's focus: gone, they'd take it
 * with them.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w900dp-h900dp-mdpi")
class PresentationKeysTest {
    @get:Rule
    val compose = createComposeRule()

    /** The keys the presentation took, as stepKeys takes them. */
    private val stepped = mutableListOf<Key>()

    private var stops = 0

    private fun steps(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }

        return when (event.key) {
            Key.Spacebar, Key.Enter, Key.DirectionRight, Key.PageDown, Key.DirectionLeft, Key.PageUp -> {
                stepped += event.key
                true
            }
            else -> false
        }
    }

    /** A presentation on this screen, with its controls over it as they are there: they show, then step aside. */
    private fun present() {
        compose.setContent {
            OrbitTheme(dark = false) {
                PresentationKeys(keys = ::steps, modifier = Modifier.fillMaxSize()) { focus ->
                    var controls by remember { mutableStateOf(true) }
                    ControlsStepAside(controls, linger = 3_500, focused = focus.controlFocused, running = true, moment = null) { controls = it }

                    AnimatedVisibility(controls) {
                        Row {
                            TextButton(onClick = { stops += 1 }) { Text("Stop presenting") }
                            TextButton(onClick = {}) { Text("Captions") }
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
    }

    /** A key on the keyboard, to whatever has the focus: something has to. */
    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focused(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Focused) == true }

    /** Whether the presentation itself has the focus, rather than one of its controls. */
    private fun onThePresentation(): Boolean {
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        return focused.size == 1 && SemanticsActions.OnClick !in focused[0].config
    }

    /** Tab, until the control that reads `text` has the focus. */
    private fun tabTo(text: String) {
        repeat(8) {
            if (focused(text)) {
                return
            }

            press(Key.Tab)
        }

        compose.onNodeWithText(text).assertIsFocused()
    }

    @Test
    fun `Enter and Space on a focused control press it, and don't step`() {
        present()
        tabTo("Stop presenting")

        press(Key.Enter)
        assertEquals(1, stops)
        press(Key.Spacebar)
        assertEquals(2, stops)
        assertEquals(emptyList<Key>(), stepped)
    }

    @Test
    fun `with the presentation itself focused, Space and Enter step`() {
        present()
        assertTrue(onThePresentation())

        press(Key.Spacebar)
        press(Key.Enter)
        assertEquals(listOf(Key.Spacebar, Key.Enter), stepped)
        assertEquals(0, stops)
    }

    @Test
    fun `with a control focused, the arrows and page keys still step`() {
        present()
        tabTo("Captions")

        press(Key.DirectionRight)
        press(Key.PageUp)
        assertEquals(listOf(Key.DirectionRight, Key.PageUp), stepped)
        compose.onNodeWithText("Captions").assertIsFocused()
    }

    @Test
    fun `the controls stay while one of them has the keyboard's focus, and step aside once it's back on the presentation`() {
        present()
        tabTo("Stop presenting")

        // Long after they'd have gone, Stop is there, with the focus: Enter still presses it.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        compose.onNodeWithText("Stop presenting").assertIsFocused()
        press(Key.Enter)
        assertEquals(1, stops)

        // Tab goes on through the controls and back to the presentation: after a moment, they step aside.
        repeat(8) {
            if (!onThePresentation()) {
                press(Key.Tab)
            }
        }

        assertTrue(onThePresentation())
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Stop presenting").fetchSemanticsNodes().isEmpty())

        // The keys are still the presentation's.
        press(Key.Spacebar)
        assertEquals(listOf(Key.Spacebar), stepped)
    }

    @Test
    fun `the controls step aside after a moment, with the keys on the presentation`() {
        present()

        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Stop presenting").fetchSemanticsNodes().isEmpty())
        press(Key.Enter)
        assertEquals(listOf(Key.Enter), stepped)
    }
}
