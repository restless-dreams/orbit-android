package com.hyuchia.orbit.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Whether a key presses the button that has the keyboard's focus, as a tap
 * on it would: Space and Enter. A view with keys of its own, as a video, a
 * sound or a presentation has, leaves these to a button Tab went to.
 */
fun KeyEvent.pressesButton(): Boolean = key == Key.Spacebar || key == Key.Enter || key == Key.NumPadEnter

/**
 * The keys of a view full screen with keys of its own, `keys`: each comes
 * to it before what's in the view hears it, except Space and Enter, which
 * go first to what has the keyboard's focus, so the button Tab is on takes
 * them as it takes a tap. They come to `keys` when nothing there took them:
 * with the focus on the view itself, or on a picture or a slider, which
 * have no use for them.
 */
fun Modifier.viewKeys(keys: (KeyEvent) -> Boolean): Modifier = this
    .onPreviewKeyEvent { event -> !event.pressesButton() && keys(event) }
    .onKeyEvent { event -> event.pressesButton() && keys(event) }
