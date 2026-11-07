package com.hyuchia.orbit.privacy

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardsDontLearnTest {
    @get:Rule
    val compose = createComposeRule()

    private fun keyboardsLearn(view: View): Boolean {
        compose.waitForIdle()
        val asked = EditorInfo()
        compose.runOnUiThread { view.onCreateInputConnection(asked) }
        return asked.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
    }

    @Test
    fun `a field asks keyboards not to learn while it's for a private map, and stays as it is when that changes`() {
        var private by mutableStateOf(true)
        lateinit var view: View

        compose.setContent {
            view = LocalView.current

            KeyboardsDontLearn(enabled = private) {
                // Made again, the field would start over from "Call", and lose the keyboard's focus.
                var text by remember { mutableStateOf("Call") }
                BasicTextField(text, { text = it }, Modifier.testTag("field"))
            }
        }

        compose.onNodeWithTag("field").performClick()
        compose.onNodeWithTag("field").performTextReplacement("Call Ana")
        assertFalse(keyboardsLearn(view))

        // For a map that isn't private, as when quick capture's ideas go to another one: keyboards may learn from it again,
        // and the field keeps its words and the keyboard's focus.
        private = false
        assertTrue(keyboardsLearn(view))
        compose.onNodeWithTag("field").assertIsFocused().assertTextEquals("Call Ana")

        private = true
        assertFalse(keyboardsLearn(view))
        compose.onNodeWithTag("field").assertIsFocused().assertTextEquals("Call Ana")
    }
}
