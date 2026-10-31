package com.hyuchia.orbit.privacy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.rememberDefaultButton
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Makes maps private, or not, from a map's menu, and says so with `onDone`.
 * A phone without a screen lock has nothing to open private maps with: it
 * asks to set one first, in the phone's settings, and once there's one the
 * map becomes private, as asked.
 */
@Composable
fun rememberMakePrivate(onDone: (String) -> Unit): (MapSummary, Boolean) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val privateMaps = context.container.privateMaps
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    // The map that asked for a screen lock, while it's being set in the phone's settings.
    var waiting by rememberSaveable { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<MapSummary?>(null) }

    fun change(map: MapSummary, private: Boolean) {
        if (private) {
            privateMaps.refreshScreenLock()

            if (!privateMaps.screenLock.value) {
                asking = map
                return
            }
        }

        scope.launch {
            try {
                privateMaps.setPrivate(map, private)
            } catch (failed: IOException) {
                // Storage that's full: the map stays as it was, and that's said rather than that it changed.
                done(resources.getString(R.string.data_could_not_save_change))
                return@launch
            }

            done(resources.getString(if (private) R.string.private_made_private else R.string.private_made_not_private, map.title))
        }
    }

    // Back from the phone's settings: with a screen lock now, the map that waited for one becomes private.
    LifecycleResumeEffect(Unit) {
        val id = waiting

        if (id != null) {
            waiting = null
            privateMaps.refreshScreenLock()
            val map = context.container.maps.summary(id)

            if (map != null && privateMaps.screenLock.value) {
                change(map, private = true)
            }
        }

        onPauseOrDispose {}
    }

    asking?.let { map ->
        AlertDialog(
            onDismissRequest = { asking = null },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = {
                EscapeCloses { asking = null }
                Text(stringResource(R.string.private_needs_screen_lock_title))
            },
            text = { Text(stringResource(R.string.private_needs_screen_lock_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = null
                        waiting = map.id
                        openScreenLockSettings(context)
                    },
                    modifier = Modifier.focusRequester(rememberDefaultButton()),
                ) { Text(stringResource(R.string.private_set_screen_lock)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.private_not_now)) } },
        )
    }

    return remember { { map, private -> change(map, private) } }
}
