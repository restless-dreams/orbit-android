package com.hyuchia.orbit.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.theme.MapPalettes
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The style's color sets show every color of each, whole, in a window as
 * narrow as a small phone's (320 dp) or a common one's (360 dp), in English
 * and in Spanish, whose names run longer, and with larger text; and each set's
 * name on a line of its own, never broken before its last letter. Text is
 * laid out as a phone lays it out (native graphics), for its true widths.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaletteRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun everySwatchShowsWhole() {
        compose.setContent {
            OrbitTheme(dark = false) {
                Box(Modifier.fillMaxWidth()) { PaletteRows(picked = null, onPick = {}) }
            }
        }

        compose.waitForIdle()
        val width = compose.onRoot().getBoundsInRoot().right
        // A row ends inside the panel's side, and what it holds inside the row's own.
        val rowEnd = width - 24.dp - 12.dp
        val swatches = compose.onAllNodesWithTag(PALETTE_SWATCH, useUnmergedTree = true).fetchSemanticsNodes()

        assertEquals(MapPalettes.sumOf { it.colors?.size ?: 0 }, swatches.size)

        with(compose.density) {
            for (swatch in swatches) {
                assertTrue("A swatch ends at ${swatch.boundsInRoot.right.toDp()}, past the row's end at $rowEnd", swatch.boundsInRoot.right.toDp() <= rowEnd + 0.5.dp)
                assertTrue("A swatch is ${swatch.size.width.toDp()} wide", swatch.size.width.toDp() >= 14.dp - 0.5.dp)
            }
        }

        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

        for (palette in MapPalettes.filter { it.colors != null }) {
            val name = resources.getString(palette.nameResource)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals("“$name” takes more than one line", 1, layouts.single().lineCount)
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun onASmallPhone() = everySwatchShowsWhole()

    @Test @Config(qualifiers = "es-w320dp-h640dp-port-mdpi")
    fun onASmallPhoneInSpanish() = everySwatchShowsWhole()

    @Test @Config(qualifiers = "w360dp-h740dp-port-mdpi")
    fun onACommonPhone() = everySwatchShowsWhole()

    @Test @Config(qualifiers = "es-w360dp-h740dp-port-mdpi")
    fun onACommonPhoneInSpanish() = everySwatchShowsWhole()

    @Test @Config(qualifiers = "es-w320dp-h640dp-port-mdpi")
    fun onASmallPhoneWithLargerText() {
        RuntimeEnvironment.setFontScale(1.3f)
        everySwatchShowsWhole()
    }
}
