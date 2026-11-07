package com.hyuchia.orbit.study

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

@OptIn(ExperimentalCoroutinesApi::class)
class StudyDayTest {
    private fun day(year: Int, month: Int, day: Int): Long = LocalDate.of(year, month, day).toEpochDay()

    @Test
    fun `the day moves on at midnight, at once as the time zone or the clock changes, and not before`() = runTest {
        var zone = ZoneId.of("America/Mexico_City")
        val start = ZonedDateTime.of(2026, 11, 5, 23, 30, 0, 0, zone).toInstant()
        // The phone's clock, which the test can set: it goes on as the test's time does.
        var set = 0L
        val now = { ZonedDateTime.ofInstant(Instant.ofEpochMilli(start.toEpochMilli() + testScheduler.currentTime + set), zone) }
        val changes = Channel<Unit>(Channel.CONFLATED)
        val days = mutableListOf<Long>()
        backgroundScope.launch { followDays(now, changes) { days += it } }
        runCurrent()
        assertEquals(listOf(day(2026, 11, 5)), days)

        advanceTimeBy(29 * MINUTE)
        runCurrent()
        assertEquals(listOf(day(2026, 11, 5)), days)

        // Midnight in Mexico City.
        advanceTimeBy(MINUTE + 1)
        runCurrent()
        assertEquals(day(2026, 11, 6), days.last())

        // At eight in the evening, the phone lands in Tokyo, where it's eleven on the 7th's morning.
        advanceTimeBy(20 * HOUR)
        runCurrent()
        assertEquals(day(2026, 11, 6), days.last())
        zone = ZoneId.of("Asia/Tokyo")
        changes.trySend(Unit)
        runCurrent()
        assertEquals(day(2026, 11, 7), days.last())

        // Midnight there comes thirteen hours later, not Mexico City's.
        advanceTimeBy(13 * HOUR - MINUTE)
        runCurrent()
        assertEquals(day(2026, 11, 7), days.last())
        advanceTimeBy(MINUTE + 1)
        runCurrent()
        assertEquals(day(2026, 11, 8), days.last())

        // The clock is set two days on.
        set += 2 * 24 * HOUR
        changes.trySend(Unit)
        runCurrent()
        assertEquals(day(2026, 11, 10), days.last())
    }
}
