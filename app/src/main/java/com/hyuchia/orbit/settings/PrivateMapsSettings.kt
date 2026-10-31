package com.hyuchia.orbit.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.PrivateLockDelay
import com.hyuchia.orbit.privacy.openScreenLockSettings
import com.hyuchia.orbit.ui.ConnectedChoice

/**
 * Private maps: what they are, when they lock again, whether they hide on
 * Home while locked, and what they aren't: locked in Orbit, not encrypted.
 * A phone without a screen lock can't lock them, and says how to set one.
 */
@Composable
internal fun PrivateMapsSettings(settings: AppSettings, change: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val privateMaps = context.container.privateMaps
    val screenLock by privateMaps.screenLock.collectAsStateWithLifecycle()
    val view by privateMaps.view.collectAsStateWithLifecycle()
    val maps by context.container.maps.maps.collectAsStateWithLifecycle()
    val count = maps.orEmpty().count { it.metadata.isPrivate }

    // Back from the phone's settings, a screen lock set there counts at once.
    LifecycleResumeEffect(Unit) {
        privateMaps.refreshScreenLock()
        onPauseOrDispose {}
    }

    // Hidden while locked, they aren't counted here either until they're unlocked.
    val hidden = settings.hideLockedPrivateMaps && view.lockable

    Text(
        when {
            count == 0 || (hidden && view.locked) -> stringResource(if (hidden) R.string.private_settings_description_hidden else R.string.private_settings_description)
            hidden -> pluralStringResource(R.plurals.private_settings_count_hidden, count, count)
            else -> pluralStringResource(R.plurals.private_settings_count, count, count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )

    if (!screenLock) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        stringResource(if (count == 0) R.string.private_settings_no_screen_lock else R.string.private_settings_no_screen_lock_with_maps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                // Filled, so it reads as a button on the card's own tone.
                Button(onClick = { openScreenLockSettings(context) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.private_set_screen_lock))
                }
            }
        }

        return
    }

    Text(
        stringResource(R.string.private_settings_lock_again),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    )

    ConnectedChoice(
        choices = PrivateLockDelay.entries,
        selected = settings.lockPrivateMapsAfter,
        onPick = { delay -> change { it.copy(lockPrivateMapsAfter = delay) } },
        label = { delay ->
            if (delay == PrivateLockDelay.RightAway) {
                stringResource(R.string.private_lock_right_away)
            } else {
                val minutes = (delay.seconds / 60).toInt()
                pluralStringResource(R.plurals.private_lock_minutes, minutes, minutes)
            }
        },
        modifier = Modifier.padding(vertical = 8.dp),
    )

    Text(
        stringResource(R.string.private_settings_screen_off),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )

    // Locked cards keep their place on Home unless this hides them, until the lock in Home's bar shows them.
    SettingToggle(
        stringResource(R.string.private_settings_hide),
        stringResource(R.string.private_settings_hide_description),
        settings.hideLockedPrivateMaps,
        onChange = { on -> change { it.copy(hideLockedPrivateMaps = on) } },
        modifier = Modifier.padding(top = 8.dp),
    )

    // Private maps that show now can be locked at once, before handing the phone to someone.
    if (count > 0 && (!view.locked || view.keptOpen.isNotEmpty())) {
        FilledTonalButton(
            onClick = { privateMaps.lock.lock() },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.private_lock_now))
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.private_settings_not_encrypted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
