package com.hyuchia.orbit.map

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A panel's frame as screen readers find it: its title is a heading to jump
 * to, and names the panel's pane, so the title is heard as the panel opens,
 * beside the map and as a sheet over it alike, as a sheet's page is.
 */
@RunWith(AndroidJUnit4::class)
class PanelFrameTest {
    @get:Rule
    val compose = createComposeRule()

    /** A pane screen readers hear as "Outline". */
    private val outlinePane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Outline")

    private fun showOutline(sheet: Boolean) {
        compose.setContent {
            PanelFrame(title = "Outline", sheet = sheet, onDismiss = {}) { Text("Trip to Japan") }
        }

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Trip to Japan")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `beside the map, the panel's title is a heading, and the pane's name`() {
        showOutline(sheet = false)

        compose.onNode(hasText("Outline") and isHeading()).assertExists()
        compose.onNode(outlinePane).assertExists()
    }

    @Test
    fun `as a sheet over the map, the panel's title is a heading, and the pane's name`() {
        showOutline(sheet = true)

        compose.onNode(hasText("Outline") and isHeading()).assertExists()
        compose.onNode(outlinePane).assertExists()
    }
}
