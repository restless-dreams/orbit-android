package com.hyuchia.orbit.map

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.ui.KeyPress
import com.hyuchia.orbit.ui.ScreenShortcuts
import com.hyuchia.orbit.ui.Shortcut
import com.hyuchia.orbit.ui.ShortcutGroup
import com.hyuchia.orbit.ui.ShortcutPriority
import com.hyuchia.orbit.ui.ShortcutRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The map's Undo and Redo keys, as the map's screen gives them to the keyboard. */
@RunWith(AndroidJUnit4::class)
class MapShortcutsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun press(keyCode: Int, ctrl: Boolean = true, shift: Boolean = false) = KeyPress(keyCode, KeyEvent.ACTION_DOWN, ctrl = ctrl, shift = shift)

    private fun release(keyCode: Int) = KeyPress(keyCode, KeyEvent.ACTION_UP, ctrl = true)

    @Test
    fun `a map that can only be looked at neither lists Undo and Redo nor takes their keys, and takes them again once it can change`() {
        var changeable by mutableStateOf(false)
        var shortcuts = emptyList<Shortcut>()
        val ran = mutableListOf<String>()
        compose.setContent { shortcuts = undoShortcuts(changeable, undo = { ran += "undo" }, redo = { ran += "redo" }) }
        compose.waitForIdle()

        val map = ScreenShortcuts(ShortcutPriority.Map)
        val registry = ShortcutRegistry().apply { add(map) }
        map.groups = listOf(ShortcutGroup("This map", shortcuts))

        // Nothing to list, and Ctrl+Z, Ctrl+Shift+Z and Ctrl+Y go on to what else has a use for them.
        assertEquals(emptyList<String>(), shortcuts.map { it.label })
        assertFalse(registry.dispatch(press(KeyEvent.KEYCODE_Z), anywhere = false))
        assertFalse(registry.dispatch(press(KeyEvent.KEYCODE_Z, shift = true), anywhere = false))
        assertFalse(registry.dispatch(press(KeyEvent.KEYCODE_Y), anywhere = false))
        assertEquals(emptyList<String>(), ran)

        changeable = true
        compose.waitForIdle()
        map.groups = listOf(ShortcutGroup("This map", shortcuts))

        assertEquals(listOf("Undo", "Redo"), shortcuts.map { it.label })
        assertTrue(registry.dispatch(press(KeyEvent.KEYCODE_Z), anywhere = false))
        registry.dispatch(release(KeyEvent.KEYCODE_Z), anywhere = false)
        assertTrue(registry.dispatch(press(KeyEvent.KEYCODE_Y), anywhere = false))
        assertEquals(listOf("undo", "redo"), ran)
    }
}
