package com.hyuchia.orbit.privacy

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.IdeaDate
import com.hyuchia.orbit.map.DiscardSketchDialog
import com.hyuchia.orbit.map.DiscardTakeDialog
import com.hyuchia.orbit.map.IdeaDayPicker
import com.hyuchia.orbit.map.IdeaTimePicker
import com.hyuchia.orbit.map.LiveOffer
import com.hyuchia.orbit.map.LiveOfferDialog
import com.hyuchia.orbit.map.NodeMenu
import com.hyuchia.orbit.map.NotificationsAsk
import com.hyuchia.orbit.map.PanelFrame
import com.hyuchia.orbit.map.PathStepMenu
import com.hyuchia.orbit.map.ReplacePictureDialog
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.ui.EscapeCloses
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * The windows open over a map, as its lock counts them: a phone's sheet and
 * a dialog are windows of their own, which the lock covers with one of its
 * own as it locks from Orbit's own lock; a tablet's panel beside the map is
 * in the map's window, which the lock in the map's place covers, and leaves
 * the list of maps beside it.
 */
@RunWith(AndroidJUnit4::class)
class WindowsOverMapTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showPanel(sheet: Boolean): WindowsOverMap {
        val windows = WindowsOverMap()

        compose.setContent {
            CompositionLocalProvider(LocalWindowsOverMap provides windows) {
                PanelFrame(title = "Outline", sheet = sheet, onDismiss = {}) { Text("Trip to Japan") }
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Trip to Japan")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return windows
    }

    @Test
    fun `a phone's sheet over the map is a window its lock covers, Ctrl+L or Lock now`() {
        val windows = showPanel(sheet = true)

        assertEquals(1, windows.open)
        assertEquals(LockWindow.Waiting, lockedWindow(locked = true, lockedAway = false, windowsOpen = windows.open > 0))
    }

    @Test
    fun `a tablet's panel beside the map is no window, so the list of maps stays beside the lock`() {
        val windows = showPanel(sheet = false)

        assertEquals(0, windows.open)
        assertEquals(LockWindow.None, lockedWindow(locked = true, lockedAway = false, windowsOpen = windows.open > 0))
    }

    @Test
    fun `a dialog over the map counts while it's open, and no more once it closes`() {
        val windows = WindowsOverMap()
        var open by mutableStateOf(true)

        compose.setContent {
            CompositionLocalProvider(LocalWindowsOverMap provides windows) {
                if (open) {
                    AlertDialog(
                        onDismissRequest = { open = false },
                        title = {
                            EscapeCloses { open = false }
                            Text("Rename the idea")
                        },
                        confirmButton = { Text("Rename") },
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Rename the idea")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, windows.open)

        open = false
        compose.waitForIdle()
        assertEquals(0, windows.open)
    }

    @Test
    fun `a menu, a picker or a question about an idea over the map counts while it's open`() {
        val windows = WindowsOverMap()
        var shown by mutableStateOf<(@Composable () -> Unit)?>(null)
        val day = IdeaDate(LocalDate.of(2026, 11, 20), LocalTime.of(9, 30))
        val over: Map<String, @Composable () -> Unit> = mapOf(
            "the idea's menu" to { NodeMenu(MapNode("trip", "Trip to Japan"), 0f, 0f, parent = null, onDismiss = {}, onAction = {}) },
            "replacing its picture with a sketch" to { ReplacePictureDialog("Trip to Japan", onSketch = {}, onCancel = {}) },
            "letting a sketch go" to { DiscardSketchDialog(onDiscard = {}, onKeep = {}) },
            "its day" to { IdeaDayPicker(day, onPick = {}, onDismiss = {}) },
            "its time" to { IdeaTimePicker(day, onPick = {}, onDismiss = {}) },
            "notifications" to { NotificationsAsk(onDone = {}) },
            "a live photo" to { LiveOfferDialog(LiveOffer("beach.jpg", "beach.mov"), onAnswer = {}) },
            "a step of the path" to { PathStepMenu(expanded = true, first = false, last = false, onDismiss = {}, onMove = {}, onRemove = {}) },
            "letting a voice memo's take go" to { DiscardTakeDialog(onDiscard = {}, onKeep = {}) },
        )

        compose.setContent {
            CompositionLocalProvider(LocalWindowsOverMap provides windows) {
                shown?.invoke()
            }
        }

        for ((name, window) in over) {
            shown = window
            compose.waitForIdle()
            assertEquals(name, 1, windows.open)

            shown = null
            compose.waitForIdle()
            assertEquals(name, 0, windows.open)
        }
    }
}
