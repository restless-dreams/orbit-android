package com.hyuchia.orbit.map

import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.VersionChanges
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.ui.PatternsFor
import com.hyuchia.orbit.ui.TimeWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** The patterns Android gives English and Spanish (Android 15 to 17) for the fields the history asks for, with a plain space before AM and PM. */
private val androidPatterns: PatternsFor = { locale, fields ->
    val spanish = locale.language == "es"

    when (fields) {
        "hm" -> "h:mm a"
        "Hm" -> if (spanish) "H:mm" else "HH:mm"
        "MMMMd" -> if (spanish) "d 'de' MMMM" else "MMMM d"
        "EEEEMMMMd" -> if (spanish) "EEEE, d 'de' MMMM" else "EEEE, MMMM d"
        "yMMMMd" -> if (spanish) "d 'de' MMMM 'de' y" else "MMMM d, y"
        else -> error("No pattern for $fields")
    }
}

class HistoryPanelTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = at(2026, 10, 12, 15, 0)
    private val minute = 60_000L
    private val english = TimeWords(Locale.US, is24Hour = false, zone, androidPatterns)
    private val spanish = TimeWords(Locale.forLanguageTag("es-MX"), is24Hour = true, zone, androidPatterns)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `a version within the hour says how long ago it was, and its time after that`() {
        assertEquals(ShownText.Resource(R.string.map_history_just_now), whenWords(now - 20_000, now, english))
        assertEquals(ShownText.Plural(R.plurals.map_history_minutes_ago, 1), whenWords(now - minute, now, english))
        assertEquals(ShownText.Plural(R.plurals.map_history_minutes_ago, 59), whenWords(now - 59 * minute - 59_000, now, english))
        assertEquals(ShownText.Plain("2:00 PM"), whenWords(now - 60 * minute, now, english))
        assertEquals(ShownText.Plain("14:00"), whenWords(now - 60 * minute, now, spanish))

        // One kept while the clock ran ahead says its time.
        assertEquals(ShownText.Plain("3:05 PM"), whenWords(now + 5 * minute, now, english))
    }

    @Test
    fun `days are headed by how long ago they were`() {
        val today = LocalDate.of(2026, 10, 12)

        assertEquals(ShownText.Resource(R.string.map_history_today), dayHeading(today, today, english))
        assertEquals(ShownText.Resource(R.string.map_history_yesterday), dayHeading(today.minusDays(1), today, english))
        assertEquals(ShownText.Plain("Thursday"), dayHeading(today.minusDays(4), today, english))
        assertEquals(ShownText.Plain("Saturday, October 3"), dayHeading(LocalDate.of(2026, 10, 3), today, english))
        assertEquals(ShownText.Plain("October 3, 2025"), dayHeading(LocalDate.of(2025, 10, 3), today, english))
    }

    @Test
    fun `headings start with a capital in languages that write days in small letters`() {
        val today = LocalDate.of(2026, 10, 12)

        assertEquals(ShownText.Plain("Jueves"), dayHeading(today.minusDays(4), today, spanish))
        assertEquals(ShownText.Plain("Sábado, 3 de octubre"), dayHeading(LocalDate.of(2026, 10, 3), today, spanish))
        assertEquals(ShownText.Plain("3 de octubre de 2025"), dayHeading(LocalDate.of(2025, 10, 3), today, spanish))
    }

    @Test
    fun `a restored version is named by how long ago it was, on the phone's clock`() {
        fun restored(time: Long, words: TimeWords = english) = restoredMessage(time, words, now, zone)

        assertEquals(ShownText.Resource(R.string.map_restored_today, listOf("2:32 PM")), restored(at(2026, 10, 12, 14, 32)))
        assertEquals(ShownText.Resource(R.string.map_restored_yesterday, listOf("6:05 PM")), restored(at(2026, 10, 11, 18, 5)))
        assertEquals(ShownText.Resource(R.string.map_restored_on_weekday, listOf("Thursday", "9:14 AM")), restored(at(2026, 10, 8, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_restored_on_date_at, listOf("October 3", "9:14 AM")), restored(at(2026, 10, 3, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_restored_on_date, listOf("October 3, 2025")), restored(at(2025, 10, 3, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_restored_today, listOf("14:32")), restored(at(2026, 10, 12, 14, 32), spanish))
        assertEquals(ShownText.Resource(R.string.map_restored_on_weekday, listOf("jueves", "9:14")), restored(at(2026, 10, 8, 9, 14), spanish))
    }

    @Test
    fun `each reason a version was kept has its words, and versions from before reasons were kept have none`() {
        fun words(reason: VersionReason?) = reasonWords(reason, now, english, zone)

        assertNull(words(null))
        assertEquals(ShownText.Resource(R.string.map_history_why_opened), words(VersionReason.Opened))
        assertEquals(ShownText.Resource(R.string.map_history_why_editing), words(VersionReason.Editing))
        assertEquals(ShownText.Resource(R.string.map_history_why_removing_alone, listOf("Food")), words(VersionReason.Removing("Food", 0)))
        assertEquals(ShownText.Plural(R.plurals.map_history_why_removing, 3, listOf(3, "Food")), words(VersionReason.Removing("Food", 3)))
        assertEquals(ShownText.Resource(R.string.map_history_why_text), words(VersionReason.Text))
        assertEquals(ShownText.Resource(R.string.map_history_why_caught), words(VersionReason.Caught))
        assertEquals(ShownText.Resource(R.string.map_history_why_tags), words(VersionReason.Tags))
        assertEquals(ShownText.Resource(R.string.map_history_why_renaming), words(VersionReason.Renaming))
        assertEquals(ShownText.Resource(R.string.map_history_why_from, listOf("Pixel 9")), words(VersionReason.FromDevice("Pixel 9")))
        assertEquals(ShownText.Resource(R.string.map_history_why_from, listOf("trip.zip")), words(VersionReason.FromFile("trip.zip")))
        assertEquals(ShownText.Resource(R.string.map_history_why_from_a_file), words(VersionReason.FromFile(null)))
        assertEquals(ShownText.Resource(R.string.map_history_why_folder), words(VersionReason.FromFolder))
        assertEquals(ShownText.Resource(R.string.map_history_why_elsewhere), words(VersionReason.Elsewhere))
        assertEquals(ShownText.Resource(R.string.map_history_why_aside), words(VersionReason.Aside))
        assertEquals(ShownText.Resource(R.string.map_history_why_came_in, listOf("trip.opml")), words(VersionReason.CameIn("trip.opml")))
        assertEquals(ShownText.Resource(R.string.map_history_why_came_in_unnamed), words(VersionReason.CameIn(null)))
    }

    @Test
    fun `a file as it came in says so, even from before reasons were kept`() {
        assertEquals(VersionReason.CameIn(null), knownReason(MapVersion(1, original = true)))
        assertEquals(VersionReason.CameIn("Trip.opml"), knownReason(MapVersion(1, original = true, reason = VersionReason.CameIn("Trip.opml"))))
        assertEquals(VersionReason.Text, knownReason(MapVersion(1, reason = VersionReason.Text)))
        assertNull(knownReason(MapVersion(1)))
    }

    @Test
    fun `a version kept before a restore says when the version brought back was from`() {
        fun words(from: Long?) = reasonWords(VersionReason.Restoring(from), now, english, zone)

        assertEquals(ShownText.Resource(R.string.map_history_why_restoring), words(null))
        assertEquals(ShownText.Resource(R.string.map_history_why_restoring_today, listOf("2:32 PM")), words(at(2026, 10, 12, 14, 32)))
        assertEquals(ShownText.Resource(R.string.map_history_why_restoring_yesterday, listOf("6:05 PM")), words(at(2026, 10, 11, 18, 5)))
        assertEquals(ShownText.Resource(R.string.map_history_why_restoring_on, listOf("Thursday", "9:14 AM")), words(at(2026, 10, 8, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_history_why_restoring_on, listOf("October 3", "9:14 AM")), words(at(2026, 10, 3, 9, 14)))
        assertEquals(ShownText.Resource(R.string.map_history_why_restoring_on_date, listOf("October 3, 2025")), words(at(2025, 10, 3, 9, 14)))
    }

    @Test
    fun `ideas are named when they're few and counted when they're many`() {
        fun names(count: Int) = (1..count).map { "Idea $it" }

        assertEquals(listOf(ShownText.Resource(R.string.map_history_added_one, listOf("Idea 1"))), changeWords(VersionChanges(added = names(1))))
        assertEquals(listOf(ShownText.Resource(R.string.map_history_added_two, listOf("Idea 1", "Idea 2"))), changeWords(VersionChanges(added = names(2))))
        assertEquals(listOf(ShownText.Plural(R.plurals.map_history_added_more, 2, listOf(2, "Idea 1"))), changeWords(VersionChanges(added = names(3))))
        assertEquals(listOf(ShownText.Plural(R.plurals.map_history_added_more, 8, listOf(8, "Idea 1"))), changeWords(VersionChanges(added = names(9))))
        assertEquals(listOf(ShownText.Plural(R.plurals.map_history_added_many, 10)), changeWords(VersionChanges(added = names(10))))
        assertEquals(listOf(ShownText.Plural(R.plurals.map_history_removed_more, 2, listOf(2, "Idea 1"))), changeWords(VersionChanges(removed = names(3))))
        assertEquals(listOf(ShownText.Plural(R.plurals.map_history_removed_many, 24)), changeWords(VersionChanges(removed = names(24))))
    }

    @Test
    fun `each kind of change has its part, in order, and a change of details alone says so`() {
        val changes = VersionChanges(added = listOf("Belém"), removed = listOf("Food", "Bifana"), renamed = listOf("Hotels" to "Where to stay"), moved = listOf("Window seat"))

        assertEquals(
            listOf(
                ShownText.Resource(R.string.map_history_added_one, listOf("Belém")),
                ShownText.Resource(R.string.map_history_removed_two, listOf("Food", "Bifana")),
                ShownText.Resource(R.string.map_history_renamed_one, listOf("Hotels", "Where to stay")),
                ShownText.Resource(R.string.map_history_moved_one, listOf("Window seat")),
            ),
            changeWords(changes),
        )

        assertEquals(
            listOf(ShownText.Plural(R.plurals.map_history_renamed_many, 2), ShownText.Plural(R.plurals.map_history_moved_many, 3)),
            changeWords(VersionChanges(renamed = listOf("A" to "B", "C" to "D"), moved = listOf("E", "F", "G"))),
        )

        assertEquals(listOf(ShownText.Resource(R.string.map_history_details)), changeWords(VersionChanges(details = true)))
        assertEquals(ShownText.Resource(R.string.map_history_same), summaryWords(VersionChanges()))
        assertEquals(ShownText.Joined(changeWords(changes), " · "), summaryWords(changes))
        // TalkBack reads each kind of change as a sentence of its own.
        assertEquals(ShownText.Joined(changeWords(changes), ". "), summaryWords(changes, ". "))
    }

    @Test
    fun `a long name is cut short, in what changed and in why`() {
        val long = "Pack the camera, the charger, two lenses and the tripod"
        val added = (changeWords(VersionChanges(added = listOf(long))).single() as ShownText.Resource).arguments.single() as String
        val removing = (reasonWords(VersionReason.Removing(long, 0), now, english, zone) as ShownText.Resource).arguments.single() as String

        assertTrue(added, added.length <= 40 && added.endsWith("…") && long.startsWith(added.removeSuffix("…")))
        assertEquals(added, removing)
    }
}
