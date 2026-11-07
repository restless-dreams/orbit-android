package com.hyuchia.orbit.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.IdeaDate
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.TimeWords
import com.hyuchia.orbit.ui.rememberTimeWords
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Which part of an idea's date is being picked: its day, or its time of day. */
enum class DatePart { Day, Time }

/** An idea whose date is being picked, and which part of it. */
data class DatePick(val nodeId: String, val part: DatePart)

/** The day Material's date picker holds a date as: its midnight in UTC, in milliseconds. */
private fun pickerMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Material 3's date picker for an idea's day, on the month of its date, or
 * this month for an idea without one. The time it has stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdeaDayPicker(date: IdeaDate?, onPick: (IdeaDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(date?.day ?: LocalDate.now()))

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(IdeaDate(pickerDay(it), date?.time)) } }, enabled = state.selectedDateMillis != null) {
                Text(stringResource(R.string.common_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/**
 * Material 3's time picker for an idea's time of day, on the phone's 12- or
 * 24-hour clock: its time, or 9:00, when a date alone reminds. "No time"
 * takes its time off, leaving its day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdeaTimePicker(date: IdeaDate, onPick: (IdeaDate) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = date.time ?: IdeaDate.REMINDER_TIME
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    // Whether the time is typed rather than set on the dial: kept as a Boolean, which saved state can hold, as the mode can't.
    var typing by rememberSaveable { mutableStateOf(false) }

    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(date.copy(time = LocalTime.of(state.hour, state.minute))) }) { Text(stringResource(R.string.common_done)) } },
        title = { Text(stringResource(R.string.map_date_time_title)) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { typing = !typing },
                displayMode = if (typing) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker,
            )
        },
        dismissButton = {
            Row {
                if (date.time != null) {
                    TextButton(onClick = { onPick(date.copy(time = null)) }) { Text(stringResource(R.string.map_date_no_time)) }
                }

                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    ) {
        if (typing) {
            TimeInput(state = state)
        } else {
            TimePicker(state = state)
        }
    }
}

/** Whether Orbit may post notifications now: on Android 13 and later, once allowed, and with its notifications on. */
internal fun notificationsAllowed(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** [notificationsAllowed], read again each time the screen comes back, as from the phone's settings. */
@Composable
internal fun rememberNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = notificationsAllowed(context)
            }
        }

        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return allowed
}

/**
 * Whether to ask for notifications as a date is set: on Android 13 and later,
 * with reminders on, not allowed yet, and not asked before. Asked once:
 * Settings offers it again.
 */
internal fun asksForNotifications(context: Context, reminders: Boolean, asked: Boolean): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && reminders && !asked && !notificationsAllowed(context)

/**
 * Says why Orbit would post notifications, as the first date is set, and
 * asks Android for them on Allow. Either way it's asked once.
 */
@Composable
internal fun NotificationsAsk(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun asked() {
        scope.launch { context.container.settings.update { it.copy(notificationsAsked = true) } }
        onDone()
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked() }

    AlertDialog(
        onDismissRequest = ::asked,
        icon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
        title = { Text(stringResource(R.string.reminders_ask_title)) },
        text = { Text(stringResource(R.string.reminders_ask_text)) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        asked()
                    }
                },
            ) {
                Text(stringResource(R.string.reminders_ask_allow))
            }
        },
        dismissButton = { TextButton(onClick = ::asked) { Text(stringResource(R.string.reminders_ask_not_now)) } },
    )
}

/** An idea's date as the app's panels write it: "Fri, Nov 20, 9:30 AM", and whether it's overdue now. */
internal data class ShownDate(val text: String, val overdue: Boolean)

internal fun shownDate(node: MapNode, words: TimeWords, now: Instant = Instant.now(), zone: ZoneId = words.zone): ShownDate? {
    val date = node.date ?: return null
    return ShownDate(words.ideaDate(date, now.atZone(zone).toLocalDate()), date.isOverdue(node.status, now, zone))
}

/**
 * The editor's date: a chip for its day and one for its time, which open
 * Material's pickers, a button that takes it off, and a line on what it does:
 * when it reminds, or why it won't.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateEditor(node: MapNode, onPick: (DatePart) -> Unit, onClear: () -> Unit) {
    val words = rememberTimeWords()
    val date = node.date
    val shown = shownDate(node, words)

    if (date == null || shown == null) {
        AssistChip(
            onClick = { onPick(DatePart.Day) },
            label = { Text(stringResource(R.string.map_date_add)) },
            leadingIcon = { Icon(Icons.Outlined.Event, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        )

        return
    }

    val reminders = LocalAppSettings.current.reminders
    val allowed = rememberNotificationsAllowed()
    val error = MaterialTheme.colorScheme.error
    // The day alone: its time has a chip of its own.
    val dayText = words.ideaDate(IdeaDate(date.day), LocalDate.now(words.zone))
    val dayLabel = stringResource(if (shown.overdue) R.string.map_date_overdue_label else R.string.map_date_label, dayText)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The day and its time, which wrap on a narrow screen or in a long language, and the button that takes them off,
        // at the end of the first line whatever they do.
        Row {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                AssistChip(
                    onClick = { onPick(DatePart.Day) },
                    label = { Text(dayText) },
                    leadingIcon = { Icon(Icons.Outlined.Event, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                    colors = if (shown.overdue) AssistChipDefaults.assistChipColors(labelColor = error, leadingIconContentColor = error) else AssistChipDefaults.assistChipColors(),
                    border = if (shown.overdue) AssistChipDefaults.assistChipBorder(enabled = true, borderColor = error) else AssistChipDefaults.assistChipBorder(enabled = true),
                    modifier = Modifier.semantics { contentDescription = dayLabel },
                )

                val time = date.time?.let(words::clock)
                val timeLabel = time?.let { stringResource(R.string.map_date_time_label, it) } ?: stringResource(R.string.map_date_add_time)

                AssistChip(
                    onClick = { onPick(DatePart.Time) },
                    label = { Text(time ?: stringResource(R.string.map_date_add_time)) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                    modifier = Modifier.semantics { contentDescription = timeLabel },
                )
            }

            val remove = stringResource(R.string.map_date_remove)

            Hint(remove) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = remove) }
            }
        }

        val note = when {
            shown.overdue -> stringResource(R.string.map_date_overdue)
            // Done, or its reminder's moment gone by today: nothing more will come of it.
            IdeaDate.isDone(node.status) || !date.remindAt(words.zone).isAfter(Instant.now()) -> null
            !reminders -> stringResource(R.string.map_date_reminders_off)
            !allowed -> stringResource(R.string.map_date_notifications_off)
            date.time == null -> stringResource(R.string.map_date_reminds_at, words.clock(IdeaDate.REMINDER_TIME))
            else -> stringResource(R.string.map_date_reminds_then)
        }

        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (shown.overdue) error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
