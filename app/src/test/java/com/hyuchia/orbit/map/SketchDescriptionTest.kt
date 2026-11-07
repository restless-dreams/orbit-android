package com.hyuchia.orbit.map

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The sketch's description as Done is tapped, with the keyboard up: its
 * title, its field and its Done all show between the status bar and the
 * keyboard, on a phone upright and on its side, where the keyboard takes most
 * of the height and the description once went under it.
 */
@RunWith(AndroidJUnit4::class)
class SketchDescriptionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The description over the whole window, with a status bar `statusBar` dp tall and a keyboard `keyboard` dp tall. */
    private fun describing(short: Boolean, statusBar: Int, keyboard: Int) {
        compose.setContent {
            OrbitTheme(dark = false) {
                DescriptionLayer(shown = true, short = short, description = "", onChange = {}, onBack = {}, onDone = {})
            }
        }

        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val density = content.resources.displayMetrics.density

            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusBar * density).toInt(), 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboard * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()

            ViewCompat.dispatchApplyWindowInsets(content, insets)
        }

        // The description slides in.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private val SemanticsNodeInteraction.bounds: Rect get() = fetchSemanticsNode().boundsInRoot

    /** Whether the title, the field and Done each lie whole between the status bar and the keyboard. */
    private fun allAboveTheKeyboard(statusBar: Int, keyboard: Int) {
        val window = compose.onRoot().bounds
        val density = compose.activity.resources.displayMetrics.density
        val top = statusBar * density
        val keyboardTop = window.bottom - keyboard * density
        val title = compose.onNodeWithText(compose.activity.getString(R.string.sketch_describe_title)).bounds
        val field = compose.onNode(hasSetTextAction()).bounds
        val done = compose.onNodeWithText(compose.activity.getString(R.string.common_done), substring = true).bounds

        for ((name, bounds) in listOf("title" to title, "field" to field, "Done" to done)) {
            assertTrue("$name at $bounds, the room is from $top to $keyboardTop", bounds.top >= top && bounds.bottom <= keyboardTop)
            assertTrue("$name at $bounds, the window is $window", bounds.left >= window.left && bounds.right <= window.right)
        }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `on a phone on its side the keyboard leaves it room in one row`() {
        // Gboard on a Pixel on its side: 261 dp of 411.
        describing(short = true, statusBar = 24, keyboard = 261)
        allAboveTheKeyboard(statusBar = 24, keyboard = 261)
    }

    @Test
    @Config(qualifiers = "es-w891dp-h411dp-land")
    fun `in Spanish too, whose words are longer`() {
        describing(short = true, statusBar = 24, keyboard = 261)
        allAboveTheKeyboard(statusBar = 24, keyboard = 261)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `on a smaller phone on its side`() {
        describing(short = true, statusBar = 24, keyboard = 220)
        allAboveTheKeyboard(statusBar = 24, keyboard = 220)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `on a phone upright it keeps its column`() {
        describing(short = false, statusBar = 24, keyboard = 300)
        allAboveTheKeyboard(statusBar = 24, keyboard = 300)
        // Upright, the field has a row of its own, under the title.
        val title = compose.onNodeWithText(compose.activity.getString(R.string.sketch_describe_title)).bounds
        val field = compose.onNode(hasSetTextAction()).bounds
        assertTrue("field at $field, title at $title", field.top >= title.bottom)
    }

    @Test
    @Config(qualifiers = "w600dp-h540dp-land")
    fun `wherever the keyboard leaves too little room for its column, it's one row`() {
        // Not a short window, but the keyboard takes most of it, as in a window beside another app's.
        describing(short = false, statusBar = 24, keyboard = 330)
        allAboveTheKeyboard(statusBar = 24, keyboard = 330)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `a phone turned while the field is in use keeps it, and the keyboard`() {
        var short by mutableStateOf(false)

        compose.setContent {
            OrbitTheme(dark = false) {
                DescriptionLayer(shown = true, short = short, description = "A fish", onChange = {}, onBack = {}, onDone = {})
            }
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused()

        // On its side, the description takes one row: the same field, still in use.
        short = true
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsFocused()

        short = false
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }
}
