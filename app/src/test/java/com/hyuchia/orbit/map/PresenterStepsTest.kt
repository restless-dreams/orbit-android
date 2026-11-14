package com.hyuchia.orbit.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The presenter's buttons to step ([PresenterSteps]) at the widths a
 * tablet, a phone, a split screen and the narrowest window Android makes
 * give them: each whole and a touch's size, none over another, and where
 * the presentation is, readable whole.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h900dp-mdpi")
class PresenterStepsTest {
    @get:Rule
    val compose = createComposeRule()

    private val presentation = Presentation((1..12).map { "idea-$it" }, index = 9, startedAt = 0)

    private fun bounds(name: String): DpRect = compose.onNodeWithContentDescription(name).getUnclippedBoundsInRoot()

    @Test
    fun `the buttons are whole and where the presentation is reads whole, however narrow the window`() = steps(fontScale = 1f)

    @Test
    fun `with the largest text, the buttons are whole and where the presentation is reads whole, however narrow the window`() = steps(fontScale = 2f)

    private fun steps(fontScale: Float) {
        var width by mutableStateOf(600.dp)

        compose.setContent {
            OrbitTheme(dark = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.width(width)) {
                        PresenterSteps(presentation, onStep = {}, onPlaying = {})
                    }
                }
            }
        }

        compose.waitForIdle()
        // "10 / 12", as wide as it is with all the room it needs.
        val readable = compose.onNodeWithText("10 / 12").getUnclippedBoundsInRoot().width

        // A tablet's notes beside the stage, a phone's, a split screen's, and the narrowest window Android makes, 220 dp,
        // inside the notes' margins.
        for (shown in listOf<Dp>(600.dp, 380.dp, 330.dp, 300.dp, 260.dp, 220.dp, 188.dp)) {
            width = shown
            compose.waitForIdle()
            val buttons = listOf("Previous idea", "Play on its own", "Next idea").map(::bounds)
            val position = compose.onNodeWithText("10 / 12").getUnclippedBoundsInRoot()

            assertTrue("Where the presentation is doesn't read whole at $shown: $position", position.width >= readable && position.left >= 0.dp && position.right <= shown)

            for (button in buttons) {
                assertTrue("A button is cut off at $shown: $button", button.left >= 0.dp && button.right <= shown)
                assertTrue("A button is smaller than a touch at $shown: $button", button.width >= 48.dp && button.height >= 48.dp)
            }

            for ((index, item) in (buttons + position).withIndex()) {
                for (later in (buttons + position).drop(index + 1)) {
                    val apart = item.right <= later.left || later.right <= item.left || item.bottom <= later.top || later.bottom <= item.top
                    assertTrue("Two of them overlap at $shown: $item, $later", apart)
                }
            }
        }
    }
}
