package com.hyuchia.orbit.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
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
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val patterns: PatternsFor = { language, fields -> DateFormat.getBestDateTimePattern(language, fields) },
) {
    /** The language without an hour cycle or calendar of its own, which only the phone's setting and Gregorian dates decide here. */
    private val plain: Locale = Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("hc", null).setUnicodeLocaleKeyword("ca", null).build()

    /** Digits as the language writes them: Arabic's own, say, as its counts are. */
    private val digits: DecimalStyle = runCatching { DecimalStyle.of(locale) }.getOrDefault(DecimalStyle.STANDARD)

    /** "4:49 PM", or "16:49" on a 24-hour clock. */
    fun clock(time: Long): String {
        // A 12-hour pattern has an hour from 1 to 12 or 0 to 11; a 24-hour one, from 0 to 23 or 1 to 24.
        val hour = if (is24Hour) Regex("[Hk]") else Regex("[hK]")
        val plainClock = if (is24Hour) "HH:mm" else "h:mm a"
        val pattern = pattern(if (is24Hour) "Hm" else "hm") { hour.containsMatchIn(it) && 'm' in it } ?: plainClock
        val moment = Instant.ofEpochMilli(time).atZone(zone)
        // A field this phone's java.time can't write (a day period `B` before Android 14, say) gives way to the plain clock.
        return (formatter(pattern) ?: formatter(plainClock)!!).format(moment)
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

/** [TimeWords] for the screen: the app's language, and the phone's 12- or 24-hour setting. */
@Composable
fun rememberTimeWords(): TimeWords {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) { TimeWords(locale, is24Hour) }
}
