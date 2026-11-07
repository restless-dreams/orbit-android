package com.hyuchia.orbit.ui

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.map.PanelFrame
import com.hyuchia.orbit.media.FullScreenWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Keys pressed while a sheet or a dialog is open go to its window, not the
 * activity's: the screen's keyboard shortcuts still work through it, as they
 * do with nothing over the screen, and Android's list of shortcuts has them.
 * A picture full screen keeps the keys to itself. The tests run as Orbit's
 * own app: a picture full screen follows private maps through its container
 * (`privateWindowPolicy`).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class)
class ShortcutsOverWindowsTest {
    @get:Rule
    val compose = createComposeRule()

    private val registry = ShortcutRegistry()
    private var searched = 0
    private var undone = 0
    private var dismissed = 0

    /** The window of the sheet or dialog on screen, once it's up. */
    private var window: Window? = null

    /** The map's shortcuts, as far as these tests need them: Ctrl+F wherever the focus is, Ctrl+Z when nothing else took it. */
    @Composable
    private fun MapShortcuts() {
        KeyboardShortcuts(
            listOf(
                ShortcutGroup(
                    "This map",
                    listOf(
                        Shortcut("Search the map", listOf(Keys(KeyEvent.KEYCODE_F, ctrl = true)), anywhere = true) { searched += 1 },
                        Shortcut("Undo", listOf(Keys(KeyEvent.KEYCODE_Z, ctrl = true))) { undone += 1 },
                    ),
                ),
            ),
            priority = ShortcutPriority.Map,
        )
    }

    /** Keeps the window it's placed in. */
    @Composable
    private fun KeepWindow() {
        val view = LocalView.current

        SideEffect {
            window = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().first().window
        }
    }

    /** The map's shortcuts on screen, with `over` over them. */
    private fun show(over: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalShortcuts provides registry) {
                MapShortcuts()
                over()
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) { window != null }
        compose.waitForIdle()
    }

    /** `keyCode` pressed and let go, with Ctrl held when `ctrl`, as a keyboard sends it to the window with the focus. */
    private fun press(keyCode: Int, ctrl: Boolean = true) {
        val meta = if (ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0

        compose.runOnIdle {
            val decor = requireNotNull(window).decorView
            decor.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            decor.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
    }

    @Test
    fun `a panel's sheet over the map lets the map's shortcuts through, and Escape still closes it`() {
        show {
            PanelFrame(title = "Outline", sheet = true, onDismiss = { dismissed += 1 }) {
                KeepWindow()
                Text("Trip to Japan")
            }
        }

        press(KeyEvent.KEYCODE_F)
        press(KeyEvent.KEYCODE_Z)
        press(KeyEvent.KEYCODE_ESCAPE, ctrl = false)

        compose.runOnIdle {
            assertEquals(1, searched)
            assertEquals(1, undone)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun `a dialog lets the screen's shortcuts through, and Android's list of shortcuts has them over it`() {
        show {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    EscapeCloses {}
                    KeepWindow()
                    Text("Rename the map")
                },
                confirmButton = {},
            )
        }

        press(KeyEvent.KEYCODE_F)

        compose.runOnIdle {
            assertEquals(1, searched)

            val listed = mutableListOf<KeyboardShortcutGroup>()
            requireNotNull(window).callback.onProvideKeyboardShortcuts(listed, null, 0)

            assertTrue("Android's list over the dialog: ${listed.map { it.label }}", listed.any { it.label?.toString() == "This map" })
        }
    }

    @Test
    fun `a picture full screen keeps the keys to itself`() {
        show {
            FullScreenWindow(onClosed = {}) {
                KeepWindow()
                Text("Tokyo Tower")
            }
        }

        press(KeyEvent.KEYCODE_F)

        compose.runOnIdle { assertEquals(0, searched) }
    }
}
