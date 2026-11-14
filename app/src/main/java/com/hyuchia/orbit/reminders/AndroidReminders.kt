package com.hyuchia.orbit.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.EXTRA_OPEN_MARK
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OpenMark
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.writeAtomically
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The action of the alarm that rings reminders. */
private const val ACTION_RING = "com.hyuchia.orbit.RING_REMINDERS"

/** Reminders' notification channel. */
const val REMINDERS_CHANNEL = "reminders"

/**
 * Where a reminder's notification carries its idea, for the map to open at
 * it: its name, its date, its place, and which of the map's ideas with that
 * name and date it is.
 */
const val EXTRA_IDEA_NAME = "idea"
const val EXTRA_IDEA_DUE = "due"
const val EXTRA_IDEA_PATH = "path"
const val EXTRA_IDEA_OCCURRENCE = "occurrence"

/**
 * Reminders' notifications, told apart from any other Orbit may post by
 * this tag, and from each other by their reminder's name after it
 * ([AndroidReminderNotices.name]).
 */
private const val NOTICE_TAG = "reminder"

/**
 * What a reminder's notification says of itself, among its extras, which
 * apps with access to notifications read: the map it names, by its name for
 * Orbit alone (a map's id is its own name made a folder's); which of this
 * install's secrets named it, by its name for [SECRET_NAME], so one named
 * with a secret that's gone since is told apart from another map's; and that
 * it says nothing of its map, once it's a private map's (and then names no
 * map), or once its map can't be told.
 */
private const val NOTICE_MAP = "com.hyuchia.orbit.reminder.map"
private const val NOTICE_NAMED_WITH = "com.hyuchia.orbit.reminder.named-with"
private const val NOTICE_PRIVATE = "com.hyuchia.orbit.reminder.private"

/**
 * What a notice names the secret it was named with by ([NOTICE_NAMED_WITH]):
 * no map's id, which is a folder's name, and no reminder's key, which has
 * lines, so none of a notice's other names is ever the same.
 */
private const val SECRET_NAME = "/"

/** The file this install's secret for naming reminders' notices is kept in, in `noBackupFilesDir`. */
const val NOTICE_SECRET = "reminder-notices.key"

/** How many random bytes the secret is: as many as HMAC-SHA256's output. */
private const val NOTICE_SECRET_SIZE = 32

/**
 * This install's secret for naming reminders' notices: random bytes kept in
 * `file`, made the first time, in Orbit's files kept out of backups, so no
 * other install, and no other app, has it. One that can't be read or kept
 * gives a secret for this run alone: which maps the notices named before
 * name can't be told then, so once a map is private they say nothing of any
 * ([AndroidReminderNotices.keepPrivate]).
 */
internal fun noticeSecret(file: File): ByteArray {
    runCatching { file.readBytes() }.getOrNull()?.takeIf { it.size == NOTICE_SECRET_SIZE }?.let { return it }
    val secret = ByteArray(NOTICE_SECRET_SIZE).also(SecureRandom()::nextBytes)

    try {
        file.parentFile?.mkdirs()
        writeAtomically(file, secret)
    } catch (failed: IOException) {
        Logs.warn("Reminders", "Couldn't keep the secret notices are named with", failed)
    }

    return secret
}

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
 * that hides what notifications say, until its map is made private: then it
 * says only what a private map's does, in its place, or only "A reminder"
 * once any map is private, should its map be one Orbit can't tell anymore. A tap opens the map at
 * the idea, behind the screen lock for a locked private map, as the map's own
 * screen asks.
 */
class AndroidReminderNotices(private val context: Context, private val words: Words) : ReminderNotices {
    private val manager = NotificationManagerCompat.from(context)

    private val secret: ByteArray by lazy { noticeSecret(File(context.noBackupFilesDir, NOTICE_SECRET)) }

    /**
     * What names a reminder's notice for Orbit alone (its notification's
     * tag, its tap, the map it's from): `text`'s HMAC-SHA256, keyed with this
     * install's secret. So two reminders never share a name, as two keys with
     * one hash shared a number, and an app reading notifications can't work
     * back from it to the idea or the map, as from a plain hash by trying
     * likely names.
     */
    private fun name(text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    override fun post(reminder: Reminder, at: Instant) {
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

        val notice = if (reminder.private) {
            namelessNotice(at.toEpochMilli(), open(reminder), R.string.reminders_from_a_private_map)
        } else {
            val hidden = NotificationCompat.Builder(context, REMINDERS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setColor(ContextCompat.getColor(context, R.color.reminder))
                .setContentTitle(words.string(R.string.reminders_a_reminder))
                .build()

            notice(at.toEpochMilli(), open(reminder))
                .addExtras(
                    Bundle().apply {
                        putString(NOTICE_MAP, name(reminder.mapId))
                        putString(NOTICE_NAMED_WITH, name(SECRET_NAME))
                    },
                )
                .setContentTitle(reminder.idea.ifBlank { words.string(R.string.common_untitled) })
                .setContentText(reminder.mapTitle)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(hidden)
        }

        show("$NOTICE_TAG:${name(reminder.key)}", notice)
    }

    override fun keepPrivate(private: Set<String>, reminders: List<Reminder>) {
        val showing = try {
            context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
        } catch (failed: RuntimeException) {
            // Android couldn't say what shows: the next reading of the maps looks again.
            return
        }

        val maps = private.associateBy(::name)
        val secret = name(SECRET_NAME)
        // Orbit 0.5.0's, still showing since, carry neither their map nor a key of their own: their number is their key's hash.
        val earlier = reminders.associate { it.key.hashCode() to it.mapId }

        for (shown in showing) {
            val extras = shown.notification.extras
            val tag = shown.tag ?: continue

            if (extras.getBoolean(NOTICE_PRIVATE)) {
                continue
            }

            // Those the version before this one showed name no secret: they were named with this one.
            val namedWith = extras.getString(NOTICE_NAMED_WITH) ?: secret

            val says = when {
                // Named with a secret that's gone since (its file lost, or unreadable): which map it names can't be told,
                // so it says nothing of any.
                tag.startsWith("$NOTICE_TAG:") && namedWith != secret -> R.string.reminders_a_reminder
                tag.startsWith("$NOTICE_TAG:") && maps[extras.getString(NOTICE_MAP)] != null -> R.string.reminders_from_a_private_map
                tag == NOTICE_TAG && earlier[shown.id] != null -> R.string.reminders_from_a_private_map
                else -> null
            }

            if (says != null) {
                // Said again, in its place, without a sound: it came already.
                show(tag, namelessNotice(shown.notification.`when`, shown.notification.contentIntent, says).setSilent(true), shown.id)
            }
        }
    }

    /** A reminder's notification, saying what's added to it: it came at `at`, and opens its map with `open`. */
    private fun notice(at: Long, open: PendingIntent?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, REMINDERS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(ContextCompat.getColor(context, R.color.reminder))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(at)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)

    /**
     * A reminder that says nothing of its map or its idea, wherever it shows, not even which map it is: only `says`, "A
     * reminder from a private map" for a private map's, or "A reminder" for one whose map can't be told.
     */
    private fun namelessNotice(at: Long, open: PendingIntent?, @StringRes says: Int): NotificationCompat.Builder =
        notice(at, open)
            .setContentTitle(words.string(says))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(Bundle().apply { putBoolean(NOTICE_PRIVATE, true) })

    private fun show(tag: String, notice: NotificationCompat.Builder, id: Int = 0) {
        try {
            manager.notify(tag, id, notice.build())
        } catch (refused: SecurityException) {
            // Taken back between the check and now.
        }
    }

    /**
     * Opens the reminder's map at its idea, as Orbit's own intent: a hidden
     * private map opens too, behind its lock. Each reminder's is its own
     * ([name]), so two never open one idea.
     */
    private fun open(reminder: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_MAP)
            .setIdentifier(name(reminder.key))
            .putExtra(EXTRA_MAP_ID, reminder.mapId)
            .putExtra(EXTRA_OPEN_MARK, OpenMark.of(context))
            .putExtra(EXTRA_IDEA_NAME, reminder.idea)
            .putExtra(EXTRA_IDEA_DUE, reminder.due)
            .putExtra(EXTRA_IDEA_PATH, reminder.path.toIntArray())
            .putExtra(EXTRA_IDEA_OCCURRENCE, reminder.occurrence)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
