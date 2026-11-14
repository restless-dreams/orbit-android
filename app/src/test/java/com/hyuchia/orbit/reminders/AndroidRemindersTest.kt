package com.hyuchia.orbit.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.EXTRA_OPEN_MARK
import com.hyuchia.orbit.OpenMark
import com.hyuchia.orbit.ResourceWords
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/** Reminders as Android shows them: their channel, what each says, a private map's saying nothing of it, and the alarms that ring them. */
@RunWith(AndroidJUnit4::class)
class AndroidRemindersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val notices = AndroidReminderNotices(context, ResourceWords(context))
    private val moment = Instant.parse("2026-11-18T18:30:00Z")
    private val flights = Reminder("trip", "Lisbon trip", private = false, idea = "✈️ Book the flights", path = listOf(0), due = "2026-11-18 18:30")

    @Before
    fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun shown(): Notification = shadowOf(notifications).allNotifications.single()

    /** The notification titled `title`, or the one whose title ends so (an idea's name after its icon). */
    private fun shown(title: String): Notification = shadowOf(notifications).allNotifications.single { it.extras.getString(NotificationCompat.EXTRA_TITLE).orEmpty().endsWith(title) }

    @Test
    fun `a reminder names its idea and its map, and only "A reminder" on a lock screen that hides what notifications say`() {
        notices.post(flights, moment)
        val notice = shown()

        assertEquals("✈️ Book the flights", notice.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("Lisbon trip", notice.extras.getString(NotificationCompat.EXTRA_TEXT))
        assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
        assertEquals("A reminder", notice.publicVersion.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(moment.toEpochMilli(), notice.`when`)
        assertEquals(Notification.CATEGORY_REMINDER, notice.category)
        assertEquals(REMINDERS_CHANNEL, notice.channelId)
        assertEquals("Reminders", notifications.getNotificationChannel(REMINDERS_CHANNEL).name)
        assertTrue(notice.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `a private map's reminder says only that it's from a private map, anywhere`() {
        notices.post(flights.copy(private = true), moment)
        val notice = shown()

        assertEquals("A reminder from a private map", notice.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertNull(notice.extras.getCharSequence(NotificationCompat.EXTRA_TEXT))
        assertEquals(Notification.VISIBILITY_PUBLIC, notice.visibility)
        assertNull(notice.publicVersion)

        // Nor in what Android lists of notifications and taps, or apps with access to notifications read: not the map's
        // id, which is its name, nor the idea's.
        val listed = notifications.activeNotifications.single()
        val said = listOf(listed.tag, notice.extras.toString(), shadowOf(notice.contentIntent).savedIntent.identifier.orEmpty())
        assertTrue(said.none { "trip" in it || "flights" in it })
        assertFalse(notice.extras.containsKey("com.hyuchia.orbit.reminder.map"))
    }

    @Test
    fun `what names a reminder's notice can't be worked back to its idea or map, and a new run of Orbit still finds it`() {
        notices.post(flights, moment)
        val listed = notifications.activeNotifications.single()
        val sha256 = { text: String -> MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) } }

        // An app reading notifications could hash likely names, "trip" or a reminder's key, and find them: a secret of
        // this install's own goes into each name, so it can't.
        assertFalse(sha256(flights.key) in listed.tag)
        assertFalse(sha256(flights.key) in shadowOf(shown().contentIntent).savedIntent.identifier.orEmpty())
        val map = shown().extras.getString("com.hyuchia.orbit.reminder.map").orEmpty()
        assertTrue(map.isNotEmpty())
        assertFalse(map == sha256("trip"))
        assertTrue(File(context.noBackupFilesDir, NOTICE_SECRET).isFile)

        // As Orbit starts again: the secret is still this install's, so the map made private is found.
        AndroidReminderNotices(context, ResourceWords(context)).keepPrivate(setOf("trip"), emptyList())
        assertEquals("A reminder from a private map", shown().extras.getString(NotificationCompat.EXTRA_TITLE))
    }

    @Test
    fun `one named with a secret that's gone since says nothing of its map once a map is private, as Orbit can't tell which it is`() {
        val secret = File(context.noBackupFilesDir, NOTICE_SECRET)
        notices.post(flights, moment)
        notices.post(flights.copy(mapId = "lisbon", mapTitle = "Lisbon", idea = "Night markets"), moment)
        val opens = shadowOf(shown("Book the flights").contentIntent).savedIntent

        // The secret is lost (its file gone, or unreadable), and Orbit starts again with another: it can't find the map
        // either notice names.
        assertTrue(secret.delete())
        val restarted = AndroidReminderNotices(context, ResourceWords(context))
        restarted.keepPrivate(setOf("trip"), listOf(flights.copy(private = true)))
        assertEquals(32, secret.readBytes().size)

        // Neither names its idea or its map anymore, the private map's included, wherever they show; each still opens its
        // own idea, came when it came, and says only what any reminder says on a lock screen.
        assertEquals(2, shadowOf(notifications).allNotifications.size)

        for (notice in shadowOf(notifications).allNotifications) {
            assertEquals("A reminder", notice.extras.getString(NotificationCompat.EXTRA_TITLE))
            assertNull(notice.extras.getCharSequence(NotificationCompat.EXTRA_TEXT))
            assertEquals(Notification.VISIBILITY_PUBLIC, notice.visibility)
            assertNull(notice.publicVersion)
            assertEquals(moment.toEpochMilli(), notice.`when`)
            assertFalse(notice.extras.containsKey("com.hyuchia.orbit.reminder.map"))
        }

        assertTrue(shadowOf(notifications).allNotifications.any { shadowOf(it.contentIntent).savedIntent.filterEquals(opens) })

        // And so they stay, through another start with the new secret.
        AndroidReminderNotices(context, ResourceWords(context)).keepPrivate(setOf("trip"), listOf(flights.copy(private = true)))
        assertTrue(shadowOf(notifications).allNotifications.all { it.extras.getString(NotificationCompat.EXTRA_TITLE) == "A reminder" })
    }

    @Test
    fun `one named with this install's secret, of a map that isn't private, stays as it is`() {
        notices.post(flights.copy(mapId = "lisbon", mapTitle = "Lisbon", idea = "Night markets"), moment)

        AndroidReminderNotices(context, ResourceWords(context)).keepPrivate(setOf("trip"), listOf(flights.copy(private = true)))

        assertEquals("Night markets", shown().extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("Lisbon", shown().extras.getString(NotificationCompat.EXTRA_TEXT))
    }

    @Test
    fun `a tap opens the map at the idea, as Orbit's own intent`() {
        // The second idea of its name and date in the map.
        notices.post(flights.copy(occurrence = 1), moment)
        val opened = shadowOf(shown().contentIntent).savedIntent

        assertEquals(ACTION_OPEN_MAP, opened.action)
        assertEquals("trip", opened.getStringExtra(EXTRA_MAP_ID))
        assertEquals(OpenMark.of(context), opened.getStringExtra(EXTRA_OPEN_MARK))
        assertEquals("✈️ Book the flights", opened.getStringExtra(EXTRA_IDEA_NAME))
        assertEquals("2026-11-18 18:30", opened.getStringExtra(EXTRA_IDEA_DUE))
        assertArrayEquals(intArrayOf(0), opened.getIntArrayExtra(EXTRA_IDEA_PATH))
        assertEquals(1, opened.getIntExtra(EXTRA_IDEA_OCCURRENCE, 0))
    }

    @Test
    fun `a reminder showing as its map is made private says only that it's from a private map, in its place, and still opens its idea`() {
        notices.post(flights, moment)
        notices.post(flights.copy(mapId = "lisbon", mapTitle = "Lisbon", idea = "Night markets"), moment)
        val opens = shadowOf(shown("Book the flights").contentIntent).savedIntent

        notices.keepPrivate(setOf("trip"), listOf(flights.copy(private = true)))

        val notice = shown("A reminder from a private map")
        assertEquals(2, shadowOf(notifications).allNotifications.size)
        assertNull(notice.extras.getCharSequence(NotificationCompat.EXTRA_TEXT))
        assertEquals(Notification.VISIBILITY_PUBLIC, notice.visibility)
        assertNull(notice.publicVersion)
        assertEquals(moment.toEpochMilli(), notice.`when`)
        assertTrue(shadowOf(notice.contentIntent).savedIntent.filterEquals(opens))
        assertEquals("✈️ Book the flights", shadowOf(notice.contentIntent).savedIntent.getStringExtra(EXTRA_IDEA_NAME))
        // Another map's stays as it was.
        assertEquals("Lisbon", shown("Night markets").extras.getString(NotificationCompat.EXTRA_TEXT))
    }

    @Test
    fun `one Orbit 0_5_0 showed, by its key's hash, is made to say nothing of a map made private too`() {
        val earlier = NotificationCompat.Builder(context, REMINDERS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✈️ Book the flights")
            .setContentText("Lisbon trip")
            .setWhen(moment.toEpochMilli())
            .build()
        notifications.notify("reminder", flights.key.hashCode(), earlier)

        notices.keepPrivate(setOf("trip"), listOf(flights.copy(private = true)))

        val notice = shadowOf(notifications).allNotifications.single()
        assertEquals("A reminder from a private map", notice.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertNull(notice.extras.getCharSequence(NotificationCompat.EXTRA_TEXT))
    }

    @Test
    fun `two reminders whose keys share a hash each show, and each opens its own idea`() {
        // "Aa" and "BB" make one hash, and so do their reminders' keys.
        val first = Reminder("trip", "Lisbon trip", private = false, idea = "Aa", path = listOf(0), due = "2026-11-18 18:30")
        val second = first.copy(idea = "BB", path = listOf(3))
        assertEquals(first.key.hashCode(), second.key.hashCode())

        notices.post(first, moment)
        notices.post(second, moment)

        assertEquals(2, shadowOf(notifications).allNotifications.size)
        assertEquals("Aa", shadowOf(shown("Aa").contentIntent).savedIntent.getStringExtra(EXTRA_IDEA_NAME))
        val other = shadowOf(shown("BB").contentIntent).savedIntent
        assertEquals("BB", other.getStringExtra(EXTRA_IDEA_NAME))
        assertArrayEquals(intArrayOf(3), other.getIntArrayExtra(EXTRA_IDEA_PATH))
    }

    @Test
    fun `without notifications allowed, nothing shows`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notices.post(flights, moment)
        assertEquals(0, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun `in an application that keeps no reminders, as tests run on, the clock changing rings nothing`() {
        ReminderReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        assertEquals(0, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun `reminders ring within ten minutes of their moment, and while the phone dozes, without exact alarms`() {
        val manager = context.getSystemService(AlarmManager::class.java)
        val alarms = AndroidReminderAlarms(context)
        alarms.set(moment.toEpochMilli())

        val scheduled = shadowOf(manager).scheduledAlarms
        assertEquals(2, scheduled.size)
        assertTrue(scheduled.all { it.triggerAtMs == moment.toEpochMilli() && it.type == AlarmManager.RTC_WAKEUP })
        assertTrue(scheduled.any { it.windowLengthMs == 10 * 60 * 1000L && !it.isAllowWhileIdle })
        assertTrue(scheduled.any { it.isAllowWhileIdle })

        // Set again, the same two alarms move.
        alarms.set(moment.toEpochMilli() + 60_000)
        assertEquals(2, shadowOf(manager).scheduledAlarms.size)

        alarms.cancel()
        assertEquals(0, shadowOf(manager).scheduledAlarms.size)
    }
}
