package com.hyuchia.orbit.sketch

import android.view.KeyEvent
import com.hyuchia.orbit.ui.Keys

/** The sketch's keys, with a keyboard: its buttons' hints say them too. */
internal object SketchKeys {
    val Done = Keys(KeyEvent.KEYCODE_ENTER, ctrl = true)
    val Pen = Keys(KeyEvent.KEYCODE_P)
    val Eraser = Keys(KeyEvent.KEYCODE_E)
    val Undo = Keys(KeyEvent.KEYCODE_Z, ctrl = true)
    val Redo = Keys(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)
    val Close = Keys(KeyEvent.KEYCODE_ESCAPE)
}
