package com.hyuchia.orbit.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The windows of their own open over a map that's open: its sheets on a
 * phone, its dialogs and its pictures full screen, each counted while it's
 * there (`EscapeCloses` counts the one it's in). Locked from Orbit's own
 * lock, the map's lock covers them with a window of its own; with none
 * open, the lock in the map's place is enough, and a tablet keeps its list
 * of maps beside it ([lockedWindow]).
 */
@Stable
class WindowsOverMap {
    var open by mutableIntStateOf(0)
        private set

    fun opened() {
        open += 1
    }

    fun closed() {
        open -= 1
    }
}

/** The windows open over the map this is in, while there's one: see [WindowsOverMap]. */
val LocalWindowsOverMap = staticCompositionLocalOf<WindowsOverMap?> { null }

/**
 * Counts the window this is composed in among those over the map, while it's
 * there: for a window of its own without `EscapeCloses`, which counts the
 * dialogs and sheets that have it, as a menu, a picker or a question about
 * an idea. So whatever locks the map covers it with the lock's own window.
 */
@Composable
fun CountsOverTheMap() {
    val windows = LocalWindowsOverMap.current ?: return

    DisposableEffect(windows) {
        windows.opened()
        onDispose { windows.closed() }
    }
}
