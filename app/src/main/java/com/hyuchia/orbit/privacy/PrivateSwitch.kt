package com.hyuchia.orbit.privacy

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the switch slides before the menu closes on it: long enough to see it go over. */
private const val SWITCH_SLIDES_MILLISECONDS = 280L

/** What tapping "Private" does. */
internal sealed interface PrivateSwitchStep {
    /** The switch goes over to `private`, and the map with it. */
    data class Switch(val private: Boolean) : PrivateSwitchStep

    /** A phone without a screen lock is asked for one first: the switch stays off until there is one. */
    data object AskForScreenLock : PrivateSwitchStep
}

/** What tapping "Private" does while its switch shows `private`, on a phone with or without a `screenLock`. */
internal fun privateSwitchStep(private: Boolean, screenLock: Boolean): PrivateSwitchStep = when {
    !private && !screenLock -> PrivateSwitchStep.AskForScreenLock
    else -> PrivateSwitchStep.Switch(!private)
}

/**
 * "Private" in a map's menu, open or on its card: a switch that shows whether the map is private, and
 * turns it on or off with `onChange`. Screen readers hear a switch, with its state. The switch slides
 * over before the menu closes; a phone without a screen lock is asked for one at once instead, the menu
 * closing for the question.
 */
@Composable
fun PrivateMenuItem(private: Boolean, onChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val privateMaps = LocalContext.current.container.privateMaps
    val scope = rememberCoroutineScope()
    // Over at once as it's tapped, ahead of the map, which follows a moment later.
    var checked by remember(private) { mutableStateOf(private) }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.private_switch)) },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        trailingIcon = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) ({ Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }) else null,
            )
        },
        onClick = {
            // The screen lock as it is now: it can be set or removed while Orbit is away.
            privateMaps.refreshScreenLock()

            when (val step = privateSwitchStep(checked, privateMaps.screenLock.value)) {
                PrivateSwitchStep.AskForScreenLock -> {
                    onDismiss()
                    onChange(true)
                }
                is PrivateSwitchStep.Switch -> {
                    checked = step.private
                    onChange(step.private)

                    scope.launch {
                        delay(SWITCH_SLIDES_MILLISECONDS)
                        onDismiss()
                    }
                }
            }
        },
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
    )
}
