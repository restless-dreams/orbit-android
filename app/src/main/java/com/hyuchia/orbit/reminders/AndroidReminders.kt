package com.hyuchia.orbit.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.EXTRA_OPEN_MARK
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OpenMark
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/** The action of the alarm that rings reminders. */
private const val ACTION_RING = "com.hyuchia.orbit.RING_REMINDERS"

/** Reminders' notification channel. */
const val REMINDERS_CHANNEL = "reminders"

/** Where a reminder's notification carries its idea, for the map to open at it: its name, its date and its place. */
const val EXTRA_IDEA_NAME = "idea"
const val EXTRA_IDEA_DUE = "due"
const val EXTRA_IDEA_PATH = "path"

/** Reminders' notifications, told apart from any other Orbit may post by this tag. */
private const val NOTICE_TAG = "reminder"

/**
 * Reminders ring through Android's alarms, without asking for exact ones,
 * which Android 12 and later keep for alarm clocks and calendars: within ten
 * minutes of their moment, the shortest window it gives other apps
 * (`setWindow`), and a second alarm for a phone dozing then, which holds
 * windowed alarms back until it wakes (`setAndAllowWhileIdle`, within an hour
 * at worst). Whichever rings first shows the reminder; the other finds it
 * shown. WorkManager would wait for the phone's maintenance windows, hours
 * apart on a phone left on a desk.
 */
class AndroidReminderAlarms(private val context: Context) : ReminderAlarms {
    private val manager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override fun set(at: Long) {
        manager?.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW, ring(WINDOWED))
        manager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring(WHILE_DOZING))
    }

    override fun cancel() {
        manager?.cancel(ring(WINDOWED))
        manager?.cancel(ring(WHILE_DOZING))
    }

    private fun ring(code: Int): PendingIntent =
        PendingIntent.getBroadcast(context, code, Intent(context, ReminderReceiver::class.java).setAction(ACTION_RING), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private companion object {
        /** Ten minutes: Android 12 and later make any shorter window this long for apps without exact alarms. */
        const val WINDOW = 10 * 60 * 1000L

        const val WINDOWED = 1
        const val WHILE_DOZING = 2
    }
}

/**
 * Reminders as notifications, on their own channel. A private map's says only
 * that it's from a private map, wherever it shows, the lock screen included;
 * any other names the idea and its map, and only "A reminder" on a lock screen
 * that hides what notifications say. A tap opens the map at the idea, behind
 * the screen lock for a locked private map, as the map's own screen asks.
 */
class AndroidReminderNotices(private val context: Context, private val words: Words) : ReminderNotices {
    override fun post(reminder: Reminder, at: Instant) {
        val manager = NotificationManagerCompat.from(context)
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (!allowed || !manager.areNotificationsEnabled()) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(REMINDERS_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(words.string(R.string.reminders_channel))
                .setDescription(words.string(R.string.reminders_channel_description))
                .build(),
        )

        val notice = NotificationCompat.Builder(context, REMINDERS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(ContextCompat.getColor(context, R.color.reminder))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(at.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open(reminder))

        if (reminder.private) {
            // Nothing of the map or the idea, wherever it shows.
            notice.setContentTitle(words.string(R.string.reminders_from_a_private_map)).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        } else {
            val hidden = NotificationCompat.Builder(context, REMINDERS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setColor(ContextCompat.getColor(context, R.color.reminder))
                .setContentTitle(words.string(R.string.reminders_a_reminder))
                .build()

            notice.setContentTitle(reminder.idea.ifBlank { words.string(R.string.common_untitled) })
                .setContentText(reminder.mapTitle)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(hidden)
        }

        try {
            manager.notify(NOTICE_TAG, reminder.key.hashCode(), notice.build())
        } catch (refused: SecurityException) {
            // Taken back between the check and now.
        }
    }

    /** Opens the reminder's map at its idea, as Orbit's own intent: a hidden private map opens too, behind its lock. */
    private fun open(reminder: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_MAP)
            .putExtra(EXTRA_MAP_ID, reminder.mapId)
            .putExtra(EXTRA_OPEN_MARK, OpenMark.of(context))
            .putExtra(EXTRA_IDEA_NAME, reminder.idea)
            .putExtra(EXTRA_IDEA_DUE, reminder.due)
            .putExtra(EXTRA_IDEA_PATH, reminder.path.toIntArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(context, reminder.key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Rings reminders: when an alarm goes off, as the phone starts again, when
 * its clock or time zone changes, and once Orbit is updated, which clears its
 * alarms. Each runs with the reminders kept from the last reading of the maps.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Orbit's own application keeps the reminders. Another one (the plain one tests run on, say) has none to ring.
        val app = (context.applicationContext as? OrbitApplication)?.container ?: return
        val pending = goAsync()

        app.applicationScope.launch {
            try {
                app.reminders.ring(app.settings.settings.first().reminders)
            } finally {
                pending.finish()
            }
        }
    }
}
