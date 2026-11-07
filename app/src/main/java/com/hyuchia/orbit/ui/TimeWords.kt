package com.hyuchia.orbit.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.hyuchia.orbit.data.IdeaDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** A language's pattern for some fields of a date or a time, as Android's `DateFormat.getBestDateTimePattern` gives it. */
typealias PatternsFor = (Locale, String) -> String

/** A quoted part of a pattern, which is words and not fields. */
private val QUOTED = Regex("'[^']*'")

/** What a pattern can't hold: ICU pads a field it couldn't fit into a pattern with "├…┤" ("16 ├minute: 49┤"). */
private val PADDED = Regex("[├┤{}]")

/** A pattern's fields: a month (on its own, `L`, in some languages), a weekday (`c` on its own), a year. */
private val MONTH = Regex("[ML]")
private val WEEKDAY = Regex("[Ec]")
private val YEAR = Regex("[yu]")

/**
 * Times and dates as people read them: in the app's language, and on the
 * phone's own 12- or 24-hour clock. Every time and date Orbit shows comes
 * from here.
 *
 * A language's usual time (`java.time`'s `ofLocalizedTime`, ICU's short
 * style) follows the language and not the phone's 12- or 24-hour setting, and
 * an hour cycle a language doesn't use (Android 14's regional preferences,
 * `-u-hc-h23`) can leave ICU padding a field it couldn't fit ("16 ├minute: 49┤").
 * So a time is the language's own pattern for an hour and its minutes, "hm"
 * or "Hm" as the phone's setting says, asked of the language without an hour
 * cycle or a calendar of its own (dates here are Gregorian); a pattern that
 * doesn't hold what was asked is never used, and a plain one takes its place.
 *
 * `patterns` gives a language's pattern for some fields; tests give the JDK's.
 */
class TimeWords(
    private val locale: Locale,
    private val is24Hour: Boolean,

    /** The time zone times are told in, and days begin in. */
    val zone: ZoneId = ZoneId.systemDefault(),

    private val patterns: PatternsFor = { language, fields -> DateFormat.getBestDateTimePattern(language, fields) },
) {
    /** The language without an hour cycle or calendar of its own, which only the phone's setting and Gregorian dates decide here. */
    private val plain: Locale = Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("hc", null).setUnicodeLocaleKeyword("ca", null).build()

    /** Digits as the language writes them: Arabic's own, say, as its counts are. */
    private val digits: DecimalStyle = runCatching { DecimalStyle.of(locale) }.getOrDefault(DecimalStyle.STANDARD)

    /** "4:49 PM", or "16:49" on a 24-hour clock. */
    fun clock(time: Long): String = clockFormatter().format(Instant.ofEpochMilli(time).atZone(zone))

    /** A time of day on the phone's clock: "9:30 AM", or "09:30" on a 24-hour clock. */
    fun clock(time: LocalTime): String = clockFormatter().format(time)

    private fun clockFormatter(): DateTimeFormatter {
        // A 12-hour pattern has an hour from 1 to 12 or 0 to 11; a 24-hour one, from 0 to 23 or 1 to 24.
        val hour = if (is24Hour) Regex("[Hk]") else Regex("[hK]")
        val plainClock = if (is24Hour) "HH:mm" else "h:mm a"
        val pattern = pattern(if (is24Hour) "Hm" else "hm") { hour.containsMatchIn(it) && 'm' in it } ?: plainClock
        // A field this phone's java.time can't write (a day period `B` before Android 14, say) gives way to the plain clock.
        return formatter(pattern) ?: formatter(plainClock)!!
    }

    /** "Oct 21, 2026": a day on its own, short. */
    fun date(time: Long): String = date(Instant.ofEpochMilli(time).atZone(zone).toLocalDate())

    fun date(date: LocalDate): String = day(date, "yMMMd", FormatStyle.MEDIUM) { YEAR.containsMatchIn(it) && MONTH.containsMatchIn(it) && 'd' in it }

    /** "October 21": a day this year. */
    fun dayOfMonth(date: LocalDate): String = day(date, "MMMMd", FormatStyle.LONG) { MONTH.containsMatchIn(it) && 'd' in it }

    /** "Wednesday, October 21": a day this year, with its weekday. */
    fun weekdayAndDay(date: LocalDate): String = day(date, "EEEEMMMMd", FormatStyle.FULL) { WEEKDAY.containsMatchIn(it) && MONTH.containsMatchIn(it) && 'd' in it }

    /** "October 21, 2025": a day in another year. */
    fun longDate(date: LocalDate): String = day(date, "yMMMMd", FormatStyle.LONG) { YEAR.containsMatchIn(it) && MONTH.containsMatchIn(it) && 'd' in it }

    /** "Wednesday" */
    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, plain)

    /** "Wed, Oct 21": a day this year, with its weekday, short. */
    fun shortWeekdayAndDay(date: LocalDate): String = day(date, "EEEMMMd", FormatStyle.MEDIUM) { WEEKDAY.containsMatchIn(it) && MONTH.containsMatchIn(it) && 'd' in it }

    /** "Wed, Oct 21, 2025": a day in another year, with its weekday, short. */
    fun shortWeekdayAndDate(date: LocalDate): String =
        day(date, "EEEyMMMd", FormatStyle.MEDIUM) { WEEKDAY.containsMatchIn(it) && YEAR.containsMatchIn(it) && MONTH.containsMatchIn(it) && 'd' in it }

    /**
     * An idea's date as the app's panels show it: "Fri, Nov 20, 9:30 AM",
     * with the year when it's another year's than `today`'s, and the time on
     * the phone's clock when it has one.
     */
    fun ideaDate(date: IdeaDate, today: LocalDate): String {
        val day = if (date.day.year == today.year) shortWeekdayAndDay(date.day) else shortWeekdayAndDate(date.day)
        return date.time?.let { time -> "$day, ${clock(time)}" } ?: day
    }

    /**
     * The language's pattern for `fields`, if it holds what `holds` asks
     * (checked outside its quoted words) and nothing ICU padded; else null.
     */
    private fun pattern(fields: String, holds: (String) -> Boolean): String? {
        val pattern = runCatching { patterns(plain, fields) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val unquoted = pattern.replace(QUOTED, "")
        return pattern.takeIf { !PADDED.containsMatchIn(unquoted) && holds(unquoted) }
    }

    /** A formatter for `pattern` in the language, with its digits; null for a pattern this phone's java.time can't read. */
    private fun formatter(pattern: String): DateTimeFormatter? = runCatching { DateTimeFormatter.ofPattern(pattern, plain).withDecimalStyle(digits) }.getOrNull()

    /** A day with the language's pattern for `fields`, or else its own date in `style`, which has no hours to go wrong. */
    private fun day(date: LocalDate, fields: String, style: FormatStyle, holds: (String) -> Boolean): String {
        val formatter = pattern(fields, holds)?.let(::formatter) ?: DateTimeFormatter.ofLocalizedDate(style).withLocale(plain).withDecimalStyle(digits)
        return formatter.format(date)
    }

    companion object {
        /** Times and dates for `context`: its language, and the phone's 12- or 24-hour setting. */
        fun of(context: Context): TimeWords = TimeWords(context.resources.configuration.locales[0], DateFormat.is24HourFormat(context))
    }
}

/**
 * [TimeWords] for the screen: the app's language, and the phone's time zone
 * and 12- or 24-hour setting, followed as the phone changes them while the
 * screen is up, as on a trip abroad or in its settings.
 */
@Composable
fun rememberTimeWords(): TimeWords {
    val locale = LocalConfiguration.current.locales[0]
    val clock = rememberPhoneClock()
    return remember(locale, clock) { TimeWords(locale, clock.is24Hour, clock.zone) }
}

/** How the phone tells time: its time zone, and whether its clock counts 24 hours. */
private data class PhoneClock(val zone: ZoneId, val is24Hour: Boolean) {
    companion object {
        /**
         * The phone's clock as it is now. A time zone change says the new
         * zone (`time-zone`, Intent.EXTRA_TIMEZONE from Android 11), which
         * comes first: Android sets the process's own zone before it says so,
         * but `changed` holds it either way.
         */
        fun of(context: Context, changed: Intent? = null): PhoneClock {
            val zone = changed?.getStringExtra("time-zone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            return PhoneClock(zone, DateFormat.is24HourFormat(context))
        }
    }
}

/** The phone's clock, read again whenever its time zone or 12- or 24-hour setting changes while the screen is up. */
@Composable
private fun rememberPhoneClock(): PhoneClock {
    val context = LocalContext.current
    var clock by remember { mutableStateOf(PhoneClock.of(context)) }

    DisposableEffect(context) {
        val changes = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                clock = PhoneClock.of(context, intent)
            }
        }

        // Android says so for a new time zone, and for the clock set or switched between 12 and 24 hours.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }

        ContextCompat.registerReceiver(context, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // A change while the screen was away from composition, before the receiver listened.
        clock = PhoneClock.of(context)
        onDispose { context.unregisterReceiver(changes) }
    }

    return clock
}
