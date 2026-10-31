package com.hyuchia.orbit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapVersionsTest {
    private val minute = 60 * 1000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 400 * day

    /** Twenty versions from the last hour, which always stay: older versions are thinned past them. */
    private val recent = (1..20).map { now - it * minute }

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
        assertEquals(setOf(hourStart + 45 * minute), versionsToKeep(times + recent, now) - recent.toSet())
    }

    @Test
    fun monthsBackTheLatestOfEachDayStays() {
        val monthAgo = now - 30 * day
        val dayStart = monthAgo - monthAgo % day
        val times = listOf(dayStart + 2 * hour, dayStart + 9 * hour, dayStart + 20 * hour, dayStart - day + 3 * hour)
        assertEquals(setOf(dayStart + 20 * hour, dayStart - day + 3 * hour), versionsToKeep(times + recent, now) - recent.toSet())
    }

    @Test
    fun longAgoTheLatestOfEachWeekStays() {
        val week = 7 * day
        val longAgo = now - 30 * week
        val weekStart = longAgo - longAgo % week
        val times = listOf(weekStart + day, weekStart + 3 * day, weekStart + 6 * day)
        assertEquals(setOf(weekStart + 6 * day), versionsToKeep(times + recent, now) - recent.toSet())
    }

    @Test
    fun aBusyDayKeepsItsNewestFiftyThenOneEveryTenMinutes() {
        val times = (1..500).map { now - it * minute }
        val kept = versionsToKeep(times, now)
        assertTrue(kept.containsAll(times.take(50)))
        // The other 450 minutes, ten at a time.
        assertEquals(50 + 45, kept.size)
    }

    @Test
    fun aBusyDayNeverTakesThePlaceOfOlderVersions() {
        // 200 versions in the last day, and history from 3, 20 and 200 days ago.
        val older = listOf(now - 3 * day, now - 20 * day, now - 200 * day)
        val busy = (1..200).map { now - it * 5 * minute }
        val kept = versionsToKeep(busy + older, now)
        assertTrue(kept.containsAll(older))
        assertTrue(kept.size <= 200)
    }

    @Test
    fun atMostTwoHundredStay() {
        // Ten years of a version a week, besides a busy day.
        val years = (1..520).map { now - 100 * day - it * 7 * day }
        val busy = (1..300).map { now - it * minute }
        val kept = versionsToKeep(years + busy, now)
        assertTrue(kept.size <= 200)
        assertTrue(kept.containsAll(busy.take(20)))
    }

    @Test
    fun versionsDatedInTheFutureStayButDontCrowdOutTheRest() {
        val future = now + 2 * day
        val times = listOf(future, now - minute, now - 2 * minute)
        assertEquals(times.toSet(), versionsToKeep(times, now))
    }

    @Test
    fun atMostTenVersionsDatedInTheFutureStay() {
        // A clock a year ahead for a while: they don't all stay, and the rest still do.
        val ahead = (1..1000).map { now + 365 * day + it }
        val kept = versionsToKeep(ahead + listOf(now - day - hour), now)
        assertEquals(10, kept.count { it > now })
        assertTrue(now - day - hour in kept)
    }

    @Test
    fun aClockFarAheadStillLeavesTheNewestTwenty() {
        // A day of ten-minute versions and a week of hourly ones, thinned with the clock a year ahead.
        val times = (1..144).map { now - it * 10 * minute } + (1..168).map { now - day - it * hour }
        val kept = versionsToKeep(times, now + 365 * day)
        assertTrue(kept.containsAll(times.sortedDescending().take(20)))
    }

    @Test
    fun theVersionKeptBeforeABigChangeOutlastsTheNewerOnesOfItsStretch() {
        // The version kept before a branch was removed, then another 10 minutes later in the same hour, two days on.
        val hourStart = now - 2 * day - (now - 2 * day) % hour
        val beforeRemoval = hourStart + 5 * minute
        val later = hourStart + 15 * minute
        val kept = versionsToKeep(listOf(beforeRemoval, later) + recent, now, preferred = setOf(beforeRemoval))
        assertTrue(beforeRemoval in kept)
        assertTrue(later !in kept)
    }

    @Test
    fun aVersionDatedInTheFutureDoesntStopNewOnes() {
        // A clock that ran ahead once mustn't keep the map from ever getting a version again.
        assertTrue(checkpointDue(listOf(now + day), now))
        assertTrue(checkpointDue(listOf(now - 11 * minute), now))
        assertTrue(!checkpointDue(listOf(now - 9 * minute, now + day), now))
        assertTrue(checkpointDue(emptyList(), now))
    }

    @Test
    fun everyReasonReadsBackAsItWasKept() {
        val reasons = listOf(
            VersionReason.Opened,
            VersionReason.Editing,
            VersionReason.Removing("Food", 3),
            VersionReason.Restoring(123L),
            VersionReason.Restoring(null),
            VersionReason.Text,
            VersionReason.Caught,
            VersionReason.Tags,
            VersionReason.Renaming,
            VersionReason.FromDevice("Pixel 9"),
            VersionReason.FromFile("trip.zip"),
            VersionReason.FromFile(null),
            VersionReason.FromFolder,
            VersionReason.Elsewhere,
            VersionReason.Aside,
            VersionReason.CameIn("Trip.opml"),
            VersionReason.CameIn(null),
        )

        for (reason in reasons) {
            assertEquals(reason, VersionReason.read(reason.toJson()))
        }
    }

    @Test
    fun aReasonThisVersionDoesntKnowOrCantReadIsNone() {
        fun read(json: String) = VersionReason.read(Json.parseToJsonElement(json).jsonObject)

        assertNull(read("""{"why":"dreamt"}"""))
        assertNull(read("""{}"""))
        assertNull(read("""{"why":{"nested":true}}"""))
        // Without the idea it was about, a removal can't be told.
        assertNull(read("""{"why":"removing"}"""))
        // What it can't read of a reason it knows is left out.
        assertEquals(VersionReason.Removing("Food", 0), read("""{"why":"removing","idea":"Food","under":"many"}"""))
        assertEquals(VersionReason.Restoring(null), read("""{"why":"restoring","from":"yesterday"}"""))
    }
}
