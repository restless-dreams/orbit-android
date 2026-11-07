package com.hyuchia.orbit.ui

import com.hyuchia.orbit.data.IdeaDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.Chronology
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** The order the JDK takes a template's fields in, as Android's patterns take any order. */
private const val CANONICAL = "GyuQqMLwWEcdDFgBhHjJkKmsSAvVzZOXx"

/**
 * The JDK's pattern for a template (Java 19's), which Android's java.time
 * hasn't: tests compile against Android's classes and run on the JDK.
 */
private val templatePattern = DateTimeFormatterBuilder::class.java.getMethod("getLocalizedDateTimePattern", String::class.java, Chronology::class.java, Locale::class.java)

/** A language's pattern for some fields, from the JDK's CLDR data, as Android's `DateFormat.getBestDateTimePattern` gives them from ICU's. */
private val jdkPatterns: PatternsFor = { locale, fields ->
    val template = fields.toList().sortedBy { CANONICAL.indexOf(it) }.joinToString("")
    templatePattern.invoke(null, template, IsoChronology.INSTANCE, locale) as String
}

/** The patterns Android's ICU gives for days in English and Spanish (Android 15 to 17), whose widths the JDK's templates don't follow. */
private val androidDays: PatternsFor = { locale, fields ->
    val spanish = locale.language == "es"

    when (fields) {
        "yMMMd" -> if (spanish) "d MMM y" else "MMM d, y"
        "MMMMd" -> if (spanish) "d 'de' MMMM" else "MMMM d"
        "EEEEMMMMd" -> if (spanish) "EEEE, d 'de' MMMM" else "EEEE, MMMM d"
        "yMMMMd" -> if (spanish) "d 'de' MMMM 'de' y" else "MMMM d, y"
        "EEEMMMd" -> if (spanish) "EEE, d MMM" else "EEE, MMM d"
        "EEEyMMMd" -> if (spanish) "EEE, d MMM y" else "EEE, MMM d, y"
        else -> jdkPatterns(locale, fields)
    }
}

/** Spaces as one kind: languages put a narrow or no-break one before "PM", and CLDR's versions differ on which. */
private fun String.spaced(): String = replace('\u202F', ' ').replace('\u00A0', ' ')

class TimeWordsTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val fourFortyNine = ZonedDateTime.of(2026, 10, 21, 16, 49, 0, 0, zone).toInstant().toEpochMilli()
    private val nineFive = ZonedDateTime.of(2026, 10, 21, 9, 5, 0, 0, zone).toInstant().toEpochMilli()

    private fun words(tag: String, is24Hour: Boolean, patterns: PatternsFor = jdkPatterns) = TimeWords(Locale.forLanguageTag(tag), is24Hour, zone, patterns)

    @Test
    fun `a time follows the phone's 12- or 24-hour clock, in every language`() {
        assertEquals("4:49 PM", words("en-US", is24Hour = false).clock(fourFortyNine).spaced())
        assertEquals("16:49", words("en-US", is24Hour = true).clock(fourFortyNine).spaced())
        assertEquals("16:49", words("es-MX", is24Hour = true).clock(fourFortyNine).spaced())
        assertEquals("9:05", words("es-MX", is24Hour = true).clock(nineFive))
        assertEquals("16:49", words("en-GB", is24Hour = true).clock(fourFortyNine).spaced())
        assertEquals("16:49", words("de-DE", is24Hour = true).clock(fourFortyNine).spaced())

        for (tag in listOf("en-US", "en-GB", "es-MX", "es-ES", "es-419", "de-DE", "fr-FR", "ja-JP", "ko-KR", "hi-IN", "pt-BR")) {
            val twelve = words(tag, is24Hour = false).clock(fourFortyNine)
            val twentyFour = words(tag, is24Hour = true).clock(fourFortyNine)
            assertTrue("$tag on a 12-hour clock: $twelve", "4" in twelve && "49" in twelve && "16" !in twelve)
            assertTrue("$tag on a 24-hour clock: $twentyFour", "16" in twentyFour && "49" in twentyFour)
        }
    }

    @Test
    fun `the phone's setting wins over an hour cycle the language was given`() {
        // Android 14's regional preferences put an hour cycle in the language: the phone's own clock setting decides.
        assertEquals("4:49 PM", words("en-US-u-hc-h23", is24Hour = false).clock(fourFortyNine).spaced())
        assertEquals("16:49", words("en-US-u-hc-h12", is24Hour = true).clock(fourFortyNine).spaced())
        assertEquals("16:49", words("es-MX-u-hc-h12", is24Hour = true).clock(fourFortyNine).spaced())
        assertFalse(words("es-MX-u-hc-h23", is24Hour = false).clock(fourFortyNine).contains("16"))
    }

    @Test
    fun `a pattern ICU padded, or one without what was asked, is never used`() {
        val padded: PatternsFor = { _, _ -> "H ├'minute': mm┤" }
        assertEquals("16:49", words("en-US", is24Hour = true, padded).clock(fourFortyNine).spaced())
        assertEquals("4:49 PM", words("en-US", is24Hour = false, padded).clock(fourFortyNine).spaced())

        // An hour of the wrong kind, no minutes, nothing at all, or a failing lookup: the plain clock.
        assertEquals("4:49 PM", words("en-US", is24Hour = false) { _, _ -> "HH:mm" }.clock(fourFortyNine).spaced())
        assertEquals("16:49", words("en-US", is24Hour = true) { _, _ -> "H" }.clock(fourFortyNine).spaced())
        assertEquals("16:49", words("en-US", is24Hour = true) { _, _ -> "" }.clock(fourFortyNine).spaced())
        assertEquals("16:49", words("en-US", is24Hour = true) { _, _ -> error("no data") }.clock(fourFortyNine).spaced())

        // A field java.time can't write gives way too.
        assertEquals("16:49", words("en-US", is24Hour = true) { _, _ -> "H:mm U" }.clock(fourFortyNine).spaced())

        // Quoted words aren't fields: "HH 'h' mm" holds an hour and minutes.
        assertEquals("16 h 49", words("fr-CA", is24Hour = true) { _, _ -> "HH 'h' mm" }.clock(fourFortyNine).spaced())
    }

    @Test
    fun `a calendar the language was given doesn't change Orbit's dates or times`() {
        assertEquals("16:49", words("es-MX-u-ca-chinese-hc-h23", is24Hour = true).clock(fourFortyNine).spaced())
        assertEquals("21 oct 2026", words("es-MX-u-ca-hebrew", is24Hour = false).date(fourFortyNine))
    }

    @Test
    fun `an idea's date reads with its weekday, its year when it's another year's, and its time on the phone's clock`() {
        val today = LocalDate.of(2026, 11, 18)
        fun words(tag: String, is24Hour: Boolean) = words(tag, is24Hour, androidDays)

        assertEquals("Fri, Nov 20", words("en-US", is24Hour = false).ideaDate(IdeaDate.parse("2026-11-20")!!, today))
        assertEquals("Fri, Nov 20, 6:30 PM", words("en-US", is24Hour = false).ideaDate(IdeaDate.parse("2026-11-20 18:30")!!, today).spaced())
        assertEquals("Fri, Nov 20, 18:30", words("en-US", is24Hour = true).ideaDate(IdeaDate.parse("2026-11-20 18:30")!!, today))
        assertEquals("Tue, Jan 5, 2027", words("en-US", is24Hour = false).ideaDate(IdeaDate.parse("2027-01-05")!!, today))
        assertEquals("vie, 20 nov, 18:30", words("es-MX", is24Hour = true).ideaDate(IdeaDate.parse("2026-11-20 18:30")!!, today).spaced())
    }

    @Test
    fun `days read the language's way`() {
        val day = LocalDate.of(2026, 10, 21)
        val lastYear = LocalDate.of(2025, 12, 14)
        fun words(tag: String, is24Hour: Boolean) = words(tag, is24Hour, androidDays)

        assertEquals("Oct 21, 2026", words("en-US", is24Hour = false).date(day))
        assertEquals("21 oct 2026", words("es-MX", is24Hour = true).date(day))
        assertEquals("October 21", words("en-US", is24Hour = false).dayOfMonth(day))
        assertEquals("21 de octubre", words("es-MX", is24Hour = true).dayOfMonth(day))
        assertEquals("Wednesday, October 21", words("en-US", is24Hour = false).weekdayAndDay(day))
        assertEquals("miércoles, 21 de octubre", words("es-MX", is24Hour = true).weekdayAndDay(day))
        assertEquals("December 14, 2025", words("en-US", is24Hour = false).longDate(lastYear))
        assertEquals("14 de diciembre de 2025", words("es-MX", is24Hour = true).longDate(lastYear))
        assertEquals("Wednesday", words("en-US", is24Hour = false).weekday(day))
        assertEquals("miércoles", words("es-MX", is24Hour = true).weekday(day))

        // Without the language's pattern, its own date takes its place.
        assertEquals("Oct 21, 2026", words("en-US", is24Hour = false) { _, _ -> "├" }.date(day))
    }

    @Test
    fun `digits are the language's own`() {
        assertEquals("١٦:٤٩", words("ar-EG", is24Hour = true).clock(fourFortyNine).spaced())
    }
}
