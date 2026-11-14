package com.hyuchia.orbit.map

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * The map's messages, over it or over a sheet, in `host`: one at a time,
 * each new one in place of the one showing. [clear] takes away the one
 * showing and any still waiting, with what they offer, such as Undo, as a
 * private map locks: what was done stays done, and nothing waits under the
 * lock to offer it back once the map is unlocked, long after.
 */
internal class MapMessages(val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** The messages showing and waiting, each while it does: they go with the screen, and with [clear]. */
    private val messages = SupervisorJob(scope.coroutineContext[Job])

    /** Shows `visuals` in place of the message showing; `then` hears how it went, unless it was taken away. */
    fun show(visuals: SnackbarVisuals, then: (SnackbarResult) -> Unit = {}) {
        scope.launch(messages) {
            host.currentSnackbarData?.dismiss()
            then(host.showSnackbar(visuals))
        }
    }

    /** Shows `message`, with `action` if there's one, for long enough to take it; `then` hears how it went, unless it was taken away. */
    fun show(message: String, action: String? = null, then: (SnackbarResult) -> Unit = {}) {
        scope.launch(messages) {
            host.currentSnackbarData?.dismiss()
            then(host.showSnackbar(message, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short))
        }
    }

    /** Takes away the message showing, and those waiting, without what they offer. */
    fun clear() {
        messages.cancelChildren()
    }
}
