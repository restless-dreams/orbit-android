package com.hyuchia.orbit.nearby

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/**
 * Says a device was kept from guessing the code or the PIN, when one was at
 * or after `since`: someone in the room mistyping, or someone else trying
 * their luck. `message` says so, and what to do about the second case.
 */
@Composable
fun LockoutNote(lockedOut: Long, since: Long, @StringRes message: Int) {
    if (lockedOut == 0L || lockedOut < since) {
        return
    }

    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}
