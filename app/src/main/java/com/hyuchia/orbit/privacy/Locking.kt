package com.hyuchia.orbit.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.container

/**
 * Runs `onLock` each time private maps lock while this screen is there:
 * from Orbit's own lock, the screen turning off or Orbit being away. What
 * the screen shows that names one of them outside its map, such as a
 * message or a dialog about it, then goes with them.
 */
@Composable
fun WhenPrivateMapsLock(onLock: () -> Unit) {
    val lock = LocalContext.current.container.privateMaps.lock
    val latest by rememberUpdatedState(onLock)

    LaunchedEffect(lock) {
        lock.newlyLocked.collect { latest() }
    }
}
