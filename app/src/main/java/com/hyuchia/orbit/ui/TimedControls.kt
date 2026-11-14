package com.hyuchia.orbit.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Whether a screen reader explores the screen by touch, as TalkBack does,
 * following it as it's turned on and off.
 */
@Composable
fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var exploring by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }

    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }

    return exploring
}

/**
 * How long controls that hide by themselves stay after a touch, in
 * milliseconds: `millis`, or longer when the phone's accessibility settings
 * ask for more time to act. Null while a screen reader explores the screen
 * by touch: a tap that brings hidden controls back isn't one it can make,
 * so they stay.
 */
@Composable
fun controlsLinger(millis: Long): Long? {
    val exploring = rememberTouchExploration()
    val accessibility = LocalAccessibilityManager.current

    if (exploring) {
        return null
    }

    val recommended = accessibility?.calculateRecommendedTimeoutMillis(millis, containsIcons = true, containsText = true, containsControls = true) ?: millis
    return recommended.takeIf { it < Int.MAX_VALUE }
}

/**
 * Hides controls that go by themselves, as a video's and a presentation's
 * do: `onShown(false)` once they've shown for `linger` milliseconds (see
 * [controlsLinger]) while `running`, counted afresh each time `moment`
 * changes, with a touch or a key. While a screen reader explores the screen
 * (`linger` null), they show and stay. While one of them has the keyboard's
 * focus (`focused`: Tab went to it), they stay too: gone, they'd take the
 * focus with them, and the keys would go nowhere.
 */
@Composable
fun ControlsStepAside(shown: Boolean, linger: Long?, focused: Boolean, running: Boolean, moment: Any?, onShown: (Boolean) -> Unit) {
    val show by rememberUpdatedState(onShown)

    LaunchedEffect(shown, linger, focused, running, moment) {
        if (linger == null) {
            show(true)
        } else if (shown && running && !focused) {
            delay(linger)
            show(false)
        }
    }
}
