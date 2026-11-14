package com.hyuchia.orbit.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A map in the trash, at the widths a small phone, a phone, a phone on its
 * side beside another app's, a tablet and a tablet on its side give it, in
 * the font's own size and larger, in each language: its buttons whole, their
 * words on one line, apart from each other and from the map's words. Words
 * are drawn as Android draws them, to be as wide as they are.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h900dp-mdpi")
class TrashRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val title = "Weekend in CDMX"
    private val summary = "22 ideas · deleted 2 days ago · 28 days left"
    private val widths = listOf<Dp>(320.dp, 360.dp, 411.dp, 445.dp, 600.dp, 640.dp, 700.dp, 800.dp, 1280.dp)

    private fun bounds(description: String): Rect = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    private fun everyWidthAndSize() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val restore = context.getString(R.string.settings_restore_named, title)
        val delete = context.getString(R.string.settings_delete_named_for_good, title)
        var width by mutableStateOf(1280.dp)
        var fontScale by mutableFloatStateOf(1f)

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OrbitTheme(dark = false) {
                    Box(Modifier.width(width)) {
                        TrashRow(title, summary, lock = null, onRestore = {}, onDelete = {})
                    }
                }
            }
        }

        for (scale in listOf(1f, 1.3f)) {
            fontScale = scale
            width = 1280.dp
            compose.waitForIdle()
            // As tall as each button is with its words on one line, where there's room for them all.
            val oneLine = listOf(restore, delete).associateWith { bounds(it).height }

            for (shown in widths) {
                width = shown
                compose.waitForIdle()
                // The words themselves, as the row's item holds them: it reads them out with its buttons.
                val words = listOf(title, summary).map { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode() }
                val density = words.first().layoutInfo.density.density
                val right = shown.value * density
                val wordBounds = words.map { it.boundsInRoot }
                val buttons = listOf(restore, delete).associateWith(::bounds)
                val at = "at $shown, the words at $scale times their size"

                for ((name, button) in buttons) {
                    assertTrue("$name is cut off $at: $button", button.left >= 0f && button.right <= right + 0.5f)
                    assertTrue("$name takes more than a line of words $at: ${button.height} against ${oneLine.getValue(name)}", button.height <= oneLine.getValue(name) + 0.5f)

                    for (word in wordBounds) {
                        assertTrue("$name covers the map's words $at: $button, $word", !button.overlaps(word))
                    }
                }

                assertTrue("The buttons overlap $at: $buttons", !buttons.getValue(restore).overlaps(buttons.getValue(delete)))
            }
        }
    }

    @Test
    fun `in English, its buttons are whole at every width and size`() = everyWidthAndSize()

    @Test
    @Config(qualifiers = "es-w1280dp-h900dp-mdpi")
    fun `in Spanish, its buttons are whole at every width and size`() = everyWidthAndSize()

    @Test
    @Config(qualifiers = "pt-rBR-w1280dp-h900dp-mdpi")
    fun `in Brazilian Portuguese, its buttons are whole at every width and size`() = everyWidthAndSize()
}
