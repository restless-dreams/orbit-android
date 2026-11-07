package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Ideas' dates, read as Orbit reads them (its `src/core/dates.ts` and `tests/dates.test.ts`), so the app and the map agree. */
class IdeaDateTest {
    private val mexico = ZoneId.of("America/Mexico_City")

    @Test
    fun `a day, or a day and a time of day, as people and programs write them`() {
        assertEquals(IdeaDate(LocalDate.of(2026, 11, 20)), IdeaDate.parse("2026-11-20"))
        assertEquals(IdeaDate(LocalDate.of(2026, 11, 20), LocalTime.of(9, 30)), IdeaDate.parse("2026-11-20 09:30"))
        assertEquals(IdeaDate(LocalDate.of(2026, 11, 20), LocalTime.of(9, 30)), IdeaDate.parse(" 2026-11-20  09:30 "))
        assertEquals(IdeaDate(LocalDate.of(2026, 11, 20), LocalTime.of(9, 30)), IdeaDate.parse("2026-11-20T09:30"))
        assertEquals(IdeaDate(LocalDate.of(2026, 1, 5), LocalTime.of(9, 5)), IdeaDate.parse("2026-1-5 9:05"))
    }

    @Test
    fun `never a day or a time that doesn't exist, nor anything else`() {
        val notDates = listOf(
            "2026-02-29", "2026-02-30", "2026-13-01", "2026-00-10", "2026-04-31", "2026-11-20 24:00", "2026-11-20 09:60",
            "tomorrow", "20261120", "2026-11-20 9", "2026-11-20T09:30:00", "2026-11-20T09:30Z", "26-11-20", "", "2026/11/20", "0000-01-01",
        )

        for (value in notDates) {
            assertNull(value, IdeaDate.parse(value))
        }

        assertNull(IdeaDate.parse(null))
        // Leap years are the Gregorian calendar's.
        assertEquals(LocalDate.of(2028, 2, 29), IdeaDate.parse("2028-02-29")?.day)
        assertEquals(LocalDate.of(2000, 2, 29), IdeaDate.parse("2000-02-29")?.day)
        assertNull(IdeaDate.parse("2100-02-29"))
    }

    @Test
    fun `written in Orbit's own form, in figures of any language`() {
        assertEquals("2026-03-07", IdeaDate(LocalDate.of(2026, 3, 7)).text)
        assertEquals("2026-03-07 08:05", IdeaDate(LocalDate.of(2026, 3, 7), LocalTime.of(8, 5)).text)
        assertEquals("2026-01-05 09:05", IdeaDate.parse("2026-1-5T9:05")?.text)
        assertEquals("0033-03-07", IdeaDate(LocalDate.of(33, 3, 7)).text)
    }

    @Test
    fun `a date alone reminds at nine that day, one with a time at its time, in local time`() {
        val day = IdeaDate.parse("2026-11-20")!!
        val time = IdeaDate.parse("2026-11-20 18:30")!!
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0).atZone(mexico).toInstant(), day.remindAt(mexico))
        assertEquals(LocalDateTime.of(2026, 11, 20, 18, 30).atZone(mexico).toInstant(), time.remindAt(mexico))
        // The same date, in another time zone, is that zone's 9:00: a date is local time.
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant(), day.remindAt(ZoneId.of("Europe/Madrid")))
    }

    @Test
    fun `a day is past once it's over, a time once it comes, and a done idea is never overdue`() {
        val day = IdeaDate.parse("2026-11-20")!!
        val time = IdeaDate.parse("2026-11-20 18:30")!!
        val evening = LocalDateTime.of(2026, 11, 20, 23, 59).atZone(mexico).toInstant()
        val nextMorning = LocalDateTime.of(2026, 11, 21, 0, 0).atZone(mexico).toInstant()
        assertFalse(day.isPast(evening, mexico))
        assertTrue(day.isPast(nextMorning, mexico))
        assertTrue(time.isPast(evening, mexico))
        assertFalse(time.isPast(LocalDateTime.of(2026, 11, 20, 18, 29).atZone(mexico).toInstant(), mexico))

        assertTrue(day.isOverdue(null, nextMorning, mexico))
        assertTrue(day.isOverdue("todo", nextMorning, mexico))
        assertTrue(day.isOverdue("hecho", nextMorning, mexico))

        for (done in listOf("done", "Done", " complete ", "completed", "shipped", "finished", "closed")) {
            assertFalse(done, day.isOverdue(done, nextMorning, mexico))
            assertTrue(done, IdeaDate.isDone(done))
        }
    }
}
