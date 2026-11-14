package com.hyuchia.orbit.map

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The map's messages over a sheet, as a box checked in a note's checklist
 * says it with its Undo: in the sheet's own window, so a keyboard's Tab
 * reaches the Undo from the sheet and Enter presses it, and screen readers
 * find it with the sheet's own content.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SheetMessagesTest {
    @get:Rule
    val compose = createComposeRule()

    private val messages = SnackbarHostState()
    private var result: SnackbarResult? = null

    private fun showSheetWithAnUndo() {
        compose.setContent {
            CompositionLocalProvider(LocalSheetMessages provides messages) {
                PanelFrame(title = "Packing", sheet = true, onDismiss = {}) {
                    TextButton(onClick = {}) { Text("Passport") }
                }
            }

            LaunchedEffect(Unit) {
                result = messages.showSnackbar("Passport is done", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a keyboard's Tab reaches the Undo over a sheet, and Enter presses it`() {
        showSheetWithAnUndo()

        // Tab goes through the sheet, the Passport box, then on to the message's Undo.
        repeat(8) {
            val focused = compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Focused) == true }

            if (!focused) {
                compose.onNodeWithText("Passport").performKeyInput { pressKey(Key.Tab) }
                compose.waitForIdle()
            }
        }

        compose.onNodeWithText("Undo").assertIsFocused()
        compose.onNodeWithText("Undo").performKeyInput { pressKey(Key.Enter) }
        compose.waitUntil(timeoutMillis = 5_000) { result != null }
        assertEquals(SnackbarResult.ActionPerformed, result)
    }

    @Test
    fun `the message is in the sheet's own window, after its content`() {
        showSheetWithAnUndo()

        // The sheet's content and the message share the sheet's window: the message has none of its own.
        val sheet = compose.onNodeWithText("Passport").fetchSemanticsNode().layoutInfo.let { generateSequence(it) { node -> node.parentInfo }.last() }
        val message = compose.onNodeWithText("Undo").fetchSemanticsNode().layoutInfo.let { generateSequence(it) { node -> node.parentInfo }.last() }

        assertTrue("The message's Undo is in a window of its own", sheet === message)
    }
}
