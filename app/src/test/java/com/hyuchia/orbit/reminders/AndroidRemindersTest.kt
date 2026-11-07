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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
    }

    @Test
    fun `a tap opens the map at the idea, as Orbit's own intent`() {
        notices.post(flights, moment)
        val opened = shadowOf(shown().contentIntent).savedIntent

        assertEquals(ACTION_OPEN_MAP, opened.action)
        assertEquals("trip", opened.getStringExtra(EXTRA_MAP_ID))
        assertEquals(OpenMark.of(context), opened.getStringExtra(EXTRA_OPEN_MARK))
        assertEquals("✈️ Book the flights", opened.getStringExtra(EXTRA_IDEA_NAME))
        assertEquals("2026-11-18 18:30", opened.getStringExtra(EXTRA_IDEA_DUE))
        assertArrayEquals(intArrayOf(0), opened.getIntArrayExtra(EXTRA_IDEA_PATH))
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
