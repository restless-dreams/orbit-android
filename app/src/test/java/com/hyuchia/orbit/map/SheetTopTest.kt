package com.hyuchia.orbit.map

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a sheet over the map rests, which keeps the map's messages out from
 * under it: a sheet taken away at once, as the search's is when a result is
 * picked, never says it's on its way out, and the map's messages and their
 * Undo stayed hidden under no sheet at all until another sheet came and went.
 */
@RunWith(AndroidJUnit4::class)
class SheetTopTest {
    @get:Rule
    val compose = createComposeRule()

    /** What's open over the map, as MapScreen has it: the panel, and whether it's over the map. */
    private var panel by mutableStateOf<String?>("Search the map")

    /** Where the map hears the sheet rests, as MapScreen does, each time it's drawn. */
    private val tops = mutableListOf<Float?>()

    private val top: Float? get() = tops.lastOrNull()

    private fun showPanels() {
        compose.setContent {
            val sheetTop = rememberSheetTop(over = panel != null)
            tops += sheetTop.value

            CompositionLocalProvider(LocalSheetRest provides { sheetTop.value = it }) {
                panel?.let { title ->
                    // Each panel is a sheet of its own, as in MapPanels.
                    key(title) {
                        PanelFrame(title = title, sheet = true, onDismiss = { panel = null }) { Text("Jupiter") }
                    }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { top != null }
    }

    @Test
    fun `a sheet taken away at once, as a result is picked, leaves no top behind`() {
        showPanels()

        // Picking a result closes the search at once: the sheet goes with no slide down, and says nothing as it goes.
        compose.runOnIdle { panel = null }
        compose.waitForIdle()

        assertNull(top)
    }

    @Test
    fun `a sheet that takes another's place keeps the top until it says its own`() {
        showPanels()
        val shown = tops.size

        // The map stays where it was over the sheet that went until the next one says where it rests: no glide down and up.
        compose.runOnIdle { panel = "Outline" }
        compose.waitForIdle()

        assertEquals(emptyList<Float?>(), tops.drop(shown).filter { it == null })
        assertNotNull(top)
    }
}
