package com.hyuchia.orbit.ui

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * Times on screen, as version history shows them, follow the phone as it
 * changes how it tells time while they're up: into another time zone, as on
 * a trip, and between a 12- and a 24-hour clock in its settings.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class TimeWordsOnScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val phoneZone: TimeZone = TimeZone.getDefault()

    /** 15:49 UTC on 21 October 2026: 9:49 in the morning in Mexico City, 0:49 the next day in Tokyo. */
    private val time = Instant.parse("2026-10-21T15:49:00Z").toEpochMilli()

    /** The words the screen tells times with, as it last drew. */
    private lateinit var shown: TimeWords

    @After
    fun putTheZoneBack() {
        TimeZone.setDefault(phoneZone)
    }

    private fun showTimes() {
        compose.setContent { shown = rememberTimeWords() }
        compose.waitForIdle()
    }

    /** Android tells the app what changed, as it does after setting the process's own zone. */
    private fun androidSays(change: Intent) {
        context.sendBroadcast(change)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun clock(zone: String, is24Hour: Boolean): String = TimeWords(Locale.US, is24Hour, ZoneId.of(zone)).clock(time)

    @Test
    fun `times follow the phone into another time zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
        showTimes()
        assertEquals(clock("America/Mexico_City", is24Hour = false), shown.clock(time))

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        androidSays(Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra("time-zone", "Asia/Tokyo"))

        assertEquals(clock("Asia/Tokyo", is24Hour = false), shown.clock(time))
    }

    @Test
    fun `times follow the phone's clock to 24 hours`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        showTimes()
        assertEquals(clock("America/Mexico_City", is24Hour = false), shown.clock(time))

        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        androidSays(Intent(Intent.ACTION_TIME_CHANGED))

        assertEquals(clock("America/Mexico_City", is24Hour = true), shown.clock(time))
    }
}
