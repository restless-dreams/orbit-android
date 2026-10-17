package com.hyuchia.orbit.map

import com.hyuchia.orbit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class HistoryPanelTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val locale: Locale = Locale.US
    private val now = LocalDateTime.of(2026, 10, 12, 15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** The patterns Android gives American English for a month and day, and for a weekday, month and day. */
    private fun pattern(fields: String): String = when (fields) {
        "MMMMd" -> "MMMM d"
        "EEEEMMMMd" -> "EEEE, MMMM d"
        else -> error("No pattern for $fields")
    }

    /** The message, with the narrow space newer formats put before AM and PM as a plain one. */
    private fun restored(time: Long): ShownText {
        val message = restoredMessage(time, now, zone, locale, ::pattern) as ShownText.Resource
        return message.copy(arguments = message.arguments.map { it.toString().replace(' ', ' ') })
    }

    @Test
    fun aRestoredVersionIsNamedByHowLongAgoItWas() {
        assertEquals(ShownText.Resource(R.string.map_restored_today, listOf("2:32 PM")), restored(at(2026, 10, 12, 14, 32)))
        assertEquals(ShownText.Resource(R.string.map_restored_yesterday, listOf("6:05 PM")), restored(at(2026, 10, 11, 18, 5)))
        assertEquals(ShownText.Resource(R.string.map_restored_on_weekday, listOf("Thursday", "9:14 AM")), restored(at(2026, 10, 8, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_restored_on_date_at, listOf("October 3", "9:14 AM")), restored(at(2026, 10, 3, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_restored_on_date, listOf("October 3, 2025")), restored(at(2025, 10, 3, 9, 14)))
    }

    @Test
    fun daysAreHeadedTheSameWay() {
        val today = LocalDate.of(2026, 10, 12)
        assertEquals(ShownText.Resource(R.string.map_history_today), dayHeading(today, today, locale, ::pattern))
        assertEquals(ShownText.Resource(R.string.map_history_yesterday), dayHeading(today.minusDays(1), today, locale, ::pattern))
        assertEquals(ShownText.Plain("Thursday"), dayHeading(today.minusDays(4), today, locale, ::pattern))
        assertEquals(ShownText.Plain("Saturday, October 3"), dayHeading(LocalDate.of(2026, 10, 3), today, locale, ::pattern))
        assertEquals(ShownText.Plain("October 3, 2025"), dayHeading(LocalDate.of(2025, 10, 3), today, locale, ::pattern))
    }

    @Test
    fun headingsStartWithACapitalInLanguagesThatWriteDaysInSmallLetters() {
        val spanish = Locale.forLanguageTag("es")
        val today = LocalDate.of(2026, 10, 12)
        val pattern = { _: String -> "EEEE, d 'de' MMMM" }
        assertEquals(ShownText.Plain("Jueves"), dayHeading(today.minusDays(4), today, spanish, pattern))
        assertEquals(ShownText.Plain("Sábado, 3 de octubre"), dayHeading(LocalDate.of(2026, 10, 3), today, spanish, pattern))
    }

    @Test
    fun aVersionComparesItsIdeasWithNow() {
        assertEquals(ShownText.Plural(R.plurals.map_history_fewer_than_now, 3), difference(21, 24))
        assertEquals(ShownText.Plural(R.plurals.map_history_more_than_now, 2), difference(26, 24))
        assertNull(difference(24, 24))
    }
}
