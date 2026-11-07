package com.hyuchia.orbit.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.IdeaDate
import com.hyuchia.orbit.map.notificationsAllowed
import com.hyuchia.orbit.map.rememberNotificationsAllowed
import com.hyuchia.orbit.ui.NoticeButton
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.rememberTimeWords
import kotlinx.coroutines.launch

/**
 * Reminders, on or off on this device, and, while they're on and Orbit may
 * not post notifications, a card that says so and asks for them: Android's
 * own question the first time, and Orbit's notification settings after that,
 * where only the phone's owner can turn them back on.
 */
@Composable
internal fun RemindersSetting(settings: AppSettings, change: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowed = rememberNotificationsAllowed()

    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { context.container.settings.update { it.copy(notificationsAsked = true) } }

        // Turned down for good, Android asks no more: its settings are where they're turned on.
        if (!granted && !notificationsAllowed(context)) {
            openSettings()
        }
    }

    SettingToggle(
        stringResource(R.string.settings_reminders),
        stringResource(R.string.settings_reminders_description, rememberTimeWords().clock(IdeaDate.REMINDER_TIME)),
        settings.reminders,
        onChange = { on -> change { it.copy(reminders = on) } },
    )

    if (settings.reminders && !allowed) {
        NoticeCard(
            icon = Icons.Outlined.NotificationsOff,
            title = stringResource(R.string.settings_reminders_notifications_off),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            body = { Text(stringResource(R.string.settings_reminders_notifications_off_description)) },
            actions = {
                NoticeButton(stringResource(R.string.reminders_ask_allow), onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !settings.notificationsAsked) {
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openSettings()
                    }
                })
            },
        )
    }
}
