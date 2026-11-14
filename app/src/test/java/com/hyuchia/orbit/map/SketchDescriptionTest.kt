package com.hyuchia.orbit.map

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * The sketch's description as Done is tapped, with the keyboard up: its
 * title, its field and its Done all show between the status bar and the
 * keyboard, on a phone upright and on its side, where the keyboard takes most
 * of the height and the description once went under it; and in a window
 * beside another app's, and in larger words, its field keeps room to type in,
 * and its buttons their words whole, on one line. Words are drawn as Android
 * draws them, to be as wide as they are.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SketchDescriptionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * The description over the whole window, with a status bar `statusBar` dp tall and a keyboard `keyboard` dp tall,
     * its words at `fontScale` times their size.
     */
    private fun describing(short: Boolean, statusBar: Int, keyboard: Int, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OrbitTheme(dark = false) {
                    DescriptionLayer(shown = true, short = short, description = "", onChange = {}, onBack = {}, onDone = {})
                }
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

    /**
     * Where the part shows, once it's checked that all of it does: a panel taller than the room above the keyboard
     * once had Done cut off at its foot, and the field's label at its top.
     */
    private val SemanticsNodeInteraction.bounds: Rect get() {
        val node = fetchSemanticsNode()
        val shown = node.boundsInRoot

        assertTrue("A part at $shown shows ${shown.width}x${shown.height} px of its ${node.size}", shown.width >= node.size.width - 1f && shown.height >= node.size.height - 1f)
        return shown
    }

    /** Whether the title, the field and Done each lie whole between the status bar and the keyboard. */
    private fun allAboveTheKeyboard(statusBar: Int, keyboard: Int) {
        val window = compose.onRoot().bounds
        val density = compose.activity.resources.displayMetrics.density
        val top = statusBar * density
        val keyboardTop = window.bottom - keyboard * density
        val title = compose.onNodeWithText(compose.activity.getString(R.string.sketch_describe_title)).bounds
        val field = compose.onNode(hasSetTextAction()).bounds
        val done = compose.onNodeWithText(compose.activity.getString(R.string.common_done), substring = true).bounds

        buttonsWhole()

        for ((name, bounds) in listOf("title" to title, "field" to field, "Done" to done)) {
            assertTrue("$name at $bounds, the room is from $top to $keyboardTop", bounds.top >= top && bounds.bottom <= keyboardTop)
            assertTrue("$name at $bounds, the window is $window", bounds.left >= window.left && bounds.right <= window.right)
        }
    }

    /**
     * Whether Keep drawing's and Done's words each lie on one line, whole: a row that left a button the room for its
     * longest word alone once showed Keep drawing as "Continuar" or "Seguir".
     */
    private fun buttonsWhole() {
        for (words in listOf(compose.activity.getString(R.string.sketch_keep_drawing), compose.activity.getString(R.string.common_done))) {
            val label = compose.onNodeWithText(words, substring = true, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            label.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.single()
            // As wide as its words on one line: a narrower label breaks them, and shows the first line alone.
            val oneLine = ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()

            assertFalse("\"$words\" goes past its one line", layout.didOverflowHeight)
            assertTrue("\"$words\" is ${layout.size.width} px wide, and needs $oneLine px on one line", layout.size.width >= oneLine)
        }
    }

    /**
     * Whether the field, Keep drawing and Done lie whole between the status bar and the keyboard, apart from each other
     * and from the title where it shows, the field at least `least` dp wide to type in.
     */
    private fun roomToType(statusBar: Int, keyboard: Int, least: Float) {
        val window = compose.onRoot().bounds
        val density = compose.activity.resources.displayMetrics.density
        val keyboardTop = window.bottom - keyboard * density
        val field = compose.onNode(hasSetTextAction()).bounds
        val keep = compose.onNodeWithText(compose.activity.getString(R.string.sketch_keep_drawing)).bounds
        val done = compose.onNodeWithText(compose.activity.getString(R.string.common_done), substring = true).bounds
        val title = compose.onAllNodesWithText(compose.activity.getString(R.string.sketch_describe_title)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.takeIf { it.width > 0f }
        val parts = listOfNotNull("field" to field, "Keep drawing" to keep, "Done" to done, title?.let { "title" to it })

        assertTrue("The field is ${field.width / density} dp wide, under $least", field.width / density >= least)
        buttonsWhole()

        for ((index, part) in parts.withIndex()) {
            val (name, bounds) = part
            assertTrue("$name at $bounds, the room is from ${statusBar * density} to $keyboardTop", bounds.top >= statusBar * density && bounds.bottom <= keyboardTop)
            assertTrue("$name at $bounds, the window is $window", bounds.left >= window.left && bounds.right <= window.right)

            for ((other, otherBounds) in parts.drop(index + 1)) {
                assertTrue("$name at $bounds overlaps $other at $otherBounds", !bounds.overlaps(otherBounds))
            }
        }
    }

    @Test
    @Config(qualifiers = "w445dp-h411dp-land")
    fun `on a phone on its side beside another app's, the field keeps room to type in`() {
        // Half of a Pixel on its side, with the keyboard as wide as the screen.
        describing(short = true, statusBar = 0, keyboard = 261)
        roomToType(statusBar = 0, keyboard = 261, least = 160f)
    }

    @Test
    @Config(qualifiers = "w445dp-h411dp-land")
    fun `beside another app's, in larger words, the field keeps room to type in`() {
        describing(short = true, statusBar = 0, keyboard = 261, fontScale = 1.3f)
        roomToType(statusBar = 0, keyboard = 261, least = 160f * 1.3f)
    }

    @Test
    @Config(qualifiers = "es-w445dp-h411dp-land")
    fun `beside another app's, in Spanish and larger words, the field keeps room to type in`() {
        // Too short a room for the field to have a row of its own, and too narrow for it to keep its 160 dp beside
        // the buttons: it narrows, rather than the buttons' words.
        describing(short = true, statusBar = 0, keyboard = 261, fontScale = 1.3f)
        roomToType(statusBar = 0, keyboard = 261, least = 112f)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w445dp-h411dp-land")
    fun `beside another app's, in Portuguese and larger words, the field keeps room to type in`() {
        describing(short = true, statusBar = 0, keyboard = 261, fontScale = 1.3f)
        roomToType(statusBar = 0, keyboard = 261, least = 112f)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w445dp-h411dp-land")
    fun `there under the status bar too, all of it shows`() {
        // As a phone on its side shows it beside another app's: the status bar over both, and the keyboard under both.
        describing(short = true, statusBar = 24, keyboard = 261, fontScale = 1.3f)
        roomToType(statusBar = 24, keyboard = 261, least = 112f)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w380dp-h411dp-port")
    fun `in a narrower window under the status bar, in Portuguese, all of it shows`() {
        describing(short = true, statusBar = 24, keyboard = 261)
        roomToType(statusBar = 24, keyboard = 261, least = 112f)
    }

    @Test
    @Config(qualifiers = "w320dp-h360dp-land")
    fun `on a small phone on its side beside another app's, the field keeps room to type in`() {
        // Half of a 640 by 360 phone on its side, the keyboard over the lower part of the screen.
        describing(short = true, statusBar = 0, keyboard = 190)
        roomToType(statusBar = 0, keyboard = 190, least = 160f)
    }

    @Test
    @Config(qualifiers = "es-w320dp-h360dp-land")
    fun `there in Spanish too`() {
        describing(short = true, statusBar = 0, keyboard = 190)
        roomToType(statusBar = 0, keyboard = 190, least = 112f)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w320dp-h360dp-land")
    fun `there in Portuguese too`() {
        // Its longer words leave the field too little room beside the buttons, and the room above the keyboard is
        // too short for them under it, so the field narrows beside them rather than Done going under the keyboard.
        describing(short = true, statusBar = 0, keyboard = 190)
        roomToType(statusBar = 0, keyboard = 190, least = 112f)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w380dp-h360dp-land")
    fun `on a phone a little wider, in Portuguese, Keep drawing keeps both its words`() {
        // Where the buttons side by side leave the field too little room: Keep drawing was once given its longest word's.
        describing(short = true, statusBar = 0, keyboard = 190)
        roomToType(statusBar = 0, keyboard = 190, least = 112f)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w891dp-h411dp-land")
    fun `on a phone on its side in Portuguese and larger words, all of it shows in one row`() {
        describing(short = true, statusBar = 24, keyboard = 261, fontScale = 1.3f)
        roomToType(statusBar = 24, keyboard = 261, least = 160f * 1.3f)
    }

    @Test
    @Config(qualifiers = "es-w360dp-h740dp-port")
    fun `on a small phone upright in Spanish and larger words, it keeps its column`() {
        describing(short = false, statusBar = 24, keyboard = 280, fontScale = 1.3f)
        allAboveTheKeyboard(statusBar = 24, keyboard = 280)
        roomToType(statusBar = 24, keyboard = 280, least = 160f * 1.3f)
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
