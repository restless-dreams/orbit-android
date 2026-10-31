package com.hyuchia.orbit.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext

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
