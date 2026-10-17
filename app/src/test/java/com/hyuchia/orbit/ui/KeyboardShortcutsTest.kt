package com.hyuchia.orbit.ui

import android.view.KeyEvent
import com.hyuchia.orbit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardShortcutsTest {
    private fun down(keyCode: Int, ctrl: Boolean = false, shift: Boolean = false, repeatCount: Int = 0, typed: Int = 0) =
        KeyPress(keyCode, KeyEvent.ACTION_DOWN, repeatCount, ctrl = ctrl, shift = shift, typed = typed)

    private fun up(keyCode: Int, ctrl: Boolean = false) = KeyPress(keyCode, KeyEvent.ACTION_UP, ctrl = ctrl)

    /** A registry with one screen's shortcuts, as `KeyboardShortcuts` registers them. */
    private fun registry(vararg screens: Pair<Int, List<Shortcut>>): ShortcutRegistry = ShortcutRegistry().apply {
        screens.forEach { (priority, shortcuts) -> add(ScreenShortcuts(priority).also { it.groups = listOf(ShortcutGroup("Screen $priority", shortcuts)) }) }
    }

    @Test
    fun modifiersMustMatchExactly() {
        val undo = Keys(KeyEvent.KEYCODE_Z, ctrl = true)
        assertTrue(undo.matches(down(KeyEvent.KEYCODE_Z, ctrl = true)))
        assertFalse(undo.matches(down(KeyEvent.KEYCODE_Z)))
        assertFalse(undo.matches(down(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)))
    }

    @Test
    fun aCharacterMatchesKeyboardsThatNeedShiftForIt() {
        val shortcuts = Keys(KeyEvent.KEYCODE_SLASH, ctrl = true, character = '/')
        // `/` is Shift+7 on a German keyboard.
        assertTrue(shortcuts.matches(down(KeyEvent.KEYCODE_7, ctrl = true, shift = true, typed = '/'.code)))
        assertFalse(shortcuts.matches(down(KeyEvent.KEYCODE_7, ctrl = true, shift = true, typed = '&'.code)))
    }

    @Test
    fun keysShowAsCaps() {
        val control = KeyLabel.Named(R.string.ui_key_control)
        assertEquals(listOf(control, KeyLabel.Named(R.string.ui_key_shift), KeyLabel.Printed("Z")), Keys(KeyEvent.KEYCODE_Z, ctrl = true, shift = true).capLabels)
        assertEquals(listOf(KeyLabel.Printed("F5")), Keys(KeyEvent.KEYCODE_F5).capLabels)
        assertEquals(listOf(control, KeyLabel.Printed("1")), Keys(KeyEvent.KEYCODE_1, ctrl = true).capLabels)
        assertEquals(listOf(control, KeyLabel.Named(R.string.ui_key_enter)), Keys(KeyEvent.KEYCODE_ENTER, ctrl = true).capLabels)
    }

    @Test
    fun shortcutsForAnywhereRunFirstAndTheOthersOnlyWithKeysNothingUsed() {
        val ran = mutableListOf<String>()
        val shortcuts = registry(
            ShortcutPriority.Map to listOf(
                Shortcut("Search", listOf(Keys(KeyEvent.KEYCODE_F, ctrl = true)), anywhere = true) { ran += "search" },
                Shortcut("Undo", listOf(Keys(KeyEvent.KEYCODE_Z, ctrl = true))) { ran += "undo" },
            ),
        )
        assertTrue(shortcuts.dispatch(down(KeyEvent.KEYCODE_F, ctrl = true), anywhere = true))
        // Before the focused field has seen Ctrl+Z, it isn't the map's: the field may undo its own typing.
        assertFalse(shortcuts.dispatch(down(KeyEvent.KEYCODE_Z, ctrl = true), anywhere = true))
        assertTrue(shortcuts.dispatch(down(KeyEvent.KEYCODE_Z, ctrl = true), anywhere = false))
        assertEquals(listOf("search", "undo"), ran)
    }

    @Test
    fun aKeysReleaseGoesWhereItsPressWent() {
        val shortcuts = registry(ShortcutPriority.Map to listOf(Shortcut("Back", listOf(Keys(KeyEvent.KEYCODE_ESCAPE))) {}))
        // A press the screen used elsewhere: its release isn't the shortcut's either.
        assertFalse(shortcuts.dispatch(up(KeyEvent.KEYCODE_ESCAPE), anywhere = false))
        assertTrue(shortcuts.dispatch(down(KeyEvent.KEYCODE_ESCAPE), anywhere = false))
        assertTrue(shortcuts.dispatch(up(KeyEvent.KEYCODE_ESCAPE), anywhere = true))
        assertFalse(shortcuts.dispatch(up(KeyEvent.KEYCODE_ESCAPE), anywhere = true))
    }

    @Test
    fun aHeldKeyRunsItsShortcutOnce() {
        var presents = 0
        val shortcuts = registry(ShortcutPriority.Map to listOf(Shortcut("Present", listOf(Keys(KeyEvent.KEYCODE_F5))) { presents += 1 }))
        shortcuts.dispatch(down(KeyEvent.KEYCODE_F5), anywhere = false)
        shortcuts.dispatch(down(KeyEvent.KEYCODE_F5, repeatCount = 1), anywhere = false)
        shortcuts.dispatch(down(KeyEvent.KEYCODE_F5, repeatCount = 2), anywhere = false)
        assertEquals(1, presents)
    }

    @Test
    fun aMapBesideTheListWinsWhereBothUseAKey() {
        val ran = mutableListOf<String>()
        val search = Keys(KeyEvent.KEYCODE_F, ctrl = true)
        // The list registers last, as it lays itself out after the map, and still loses.
        val shortcuts = registry(
            ShortcutPriority.Map to listOf(Shortcut("Search the map", listOf(search), anywhere = true) { ran += "map" }),
            ShortcutPriority.List to listOf(Shortcut("Search your maps", listOf(search), anywhere = true) { ran += "list" }),
        )
        shortcuts.dispatch(down(KeyEvent.KEYCODE_F, ctrl = true), anywhere = true)
        assertEquals(listOf("map"), ran)
        assertEquals(listOf("Screen 1", "Screen 0", "Orbit"), shortcuts.groups.map { it.title })
    }

    @Test
    fun keysThePageAnswersAreListedButNeverTaken() {
        val shortcuts = registry(ShortcutPriority.Map to listOf(Shortcut("Add an idea under it", listOf(Keys(KeyEvent.KEYCODE_TAB)))))
        assertFalse(shortcuts.dispatch(down(KeyEvent.KEYCODE_TAB), anywhere = false))
    }

    @Test
    fun ctrlSlashOpensTheListEverywhere() {
        val shortcuts = registry()
        assertTrue(shortcuts.dispatch(down(KeyEvent.KEYCODE_SLASH, ctrl = true, typed = '/'.code), anywhere = true))
        assertTrue(shortcuts.listOpen)
    }
}
