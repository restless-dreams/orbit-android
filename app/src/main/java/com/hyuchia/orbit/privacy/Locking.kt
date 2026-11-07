package com.hyuchia.orbit.privacy

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
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

/**
 * A message about a map that says whether it names a private map, by its
 * title while it's unlocked. One that does goes as private maps lock
 * ([dismissAsPrivateMapsLock]); one that doesn't, about a map that isn't
 * private or worded as they are while locked, stays, with what it offers,
 * such as Undo.
 */
class MapMessage(
    override val message: String,

    /** Whether it names a private map: those go as they lock. */
    val namesPrivateMap: Boolean,

    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
}

/**
 * As private maps lock, the message showing goes, unless it's a [MapMessage]
 * that names none of them. A screen hears that they locked only after they
 * did ([WhenPrivateMapsLock]), so a message worded in between, as they are
 * now, can show first: it has nothing of theirs for the lock to take.
 */
fun SnackbarHostState.dismissAsPrivateMapsLock() {
    val showing = currentSnackbarData ?: return

    if ((showing.visuals as? MapMessage)?.namesPrivateMap != false) {
        showing.dismiss()
    }
}
