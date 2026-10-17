package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapVersionsTest {
    private val minute = 60 * 1000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 400 * day

    @Test
    fun everyVersionFromTheLastDayStays() {
        val times = (1..100).map { now - it * 10 * minute }.filter { now - it < day }
        assertEquals(times.toSet(), versionsToKeep(times, now))
    }

    @Test
    fun aWeekBackTheLatestOfEachHourStays() {
        val twoDaysAgo = now - 2 * day
        // Three versions in the same hour, two days ago: the latest stays.
        val hourStart = twoDaysAgo - twoDaysAgo % hour
        val times = listOf(hourStart + 5 * minute, hourStart + 25 * minute, hourStart + 45 * minute)
        assertEquals(setOf(hourStart + 45 * minute), versionsToKeep(times, now))
    }

    @Test
    fun monthsBackTheLatestOfEachDayStays() {
        val monthAgo = now - 30 * day
        val dayStart = monthAgo - monthAgo % day
        val times = listOf(dayStart + 2 * hour, dayStart + 9 * hour, dayStart + 20 * hour, dayStart - day + 3 * hour)
        assertEquals(setOf(dayStart + 20 * hour, dayStart - day + 3 * hour), versionsToKeep(times, now))
    }

    @Test
    fun longAgoTheLatestOfEachWeekStays() {
        val week = 7 * day
        val longAgo = now - 30 * week
        val weekStart = longAgo - longAgo % week
        val times = listOf(weekStart + day, weekStart + 3 * day, weekStart + 6 * day)
        assertEquals(setOf(weekStart + 6 * day), versionsToKeep(times, now))
    }

    @Test
    fun atMostTwoHundredStayTheNewest() {
        val times = (1..500).map { now - it * minute }
        val kept = versionsToKeep(times, now)
        assertEquals(200, kept.size)
        assertTrue(kept.all { it >= now - 200 * minute })
    }

    @Test
    fun versionsDatedInTheFutureStayButDontCrowdOutTheRest() {
        val future = now + 2 * day
        val times = listOf(future, now - minute, now - 2 * minute)
        assertEquals(times.toSet(), versionsToKeep(times, now))
    }

    @Test
    fun aVersionDatedInTheFutureDoesntStopNewOnes() {
        // A clock that ran ahead once mustn't keep the map from ever getting a version again.
        assertTrue(checkpointDue(listOf(now + day), now))
        assertTrue(checkpointDue(listOf(now - 11 * minute), now))
        assertTrue(!checkpointDue(listOf(now - 9 * minute, now + day), now))
        assertTrue(checkpointDue(emptyList(), now))
    }
}
