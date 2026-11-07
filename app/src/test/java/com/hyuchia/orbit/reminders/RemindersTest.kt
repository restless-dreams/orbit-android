package com.hyuchia.orbit.reminders

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reminders as they come, with a clock, alarms and notifications of the
 * test's own: once each, at their moment or as the phone starts again, never
 * for a date first seen after its moment, a done idea or a map that's gone,
 * and only while they're on.
 */
class RemindersTest {
    private val folder: File = Files.createTempDirectory("orbit-reminders").toFile()
    private val file = File(folder, "reminders.json")
    private var zone: ZoneId = ZoneId.of("America/Mexico_City")
    private var now: Long = at("2026-11-18T08:00")

    /** The next alarm set, or null once cancelled. */
    private var alarm: Long? = null

    /** What showed, as "map: idea", in order. */
    private val shown = mutableListOf<String>()

    private val shownReminders = mutableListOf<Reminder>()

    /** How many times each map's text was read. */
    private val reads = mutableMapOf<String, Int>()

    private val texts = mutableMapOf(
        "trip" to """
            # Trip
            ## Book the flights <!-- orbit due: "2026-11-18 18:30" -->
            ## Pack the bags <!-- orbit due: 2026-11-19 -->
            ## Visa photos <!-- orbit due: 2026-11-16 -->
            - [x] Insurance <!-- orbit due: 2026-11-20 -->
        """.trimIndent(),
    )

    private val reminders = Reminders(
        file,
        alarms = object : ReminderAlarms {
            override fun set(at: Long) {
                alarm = at
            }

            override fun cancel() {
                alarm = null
            }
        },
        notices = object : ReminderNotices {
            override fun post(reminder: Reminder, at: Instant) {
                shown += "${reminder.mapTitle}: ${reminder.idea}"
                shownReminders += reminder
            }
        },
        clock = { now },
        zone = { zone },
    )

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun at(local: String): Long = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun map(id: String, title: String = id.replaceFirstChar(Char::titlecase), private: Boolean = false, key: String = texts[id].hashCode().toString(), unreadable: Boolean = false) =
        MapSummary(id, MapOutline(title, 0, 0), modified = 0, metadata = MapMetadata(isPrivate = private), searchText = "", contentKey = key, unreadable = unreadable)

    private suspend fun follow(vararg maps: MapSummary, on: Boolean = true) = reminders.follow(maps.toList(), { id -> reads.merge(id, 1, Int::plus); texts[id] }, on)

    @Test
    fun `a date reminds when it comes, once, and the next one is set`() = runTest {
        follow(map("trip"))
        // Seen now, before the moment: the flights at 18:30 today come first.
        assertEquals(emptyList<String>(), shown)
        assertEquals(at("2026-11-18T18:30"), alarm)

        now = at("2026-11-18T18:31")
        reminders.ring(on = true)
        assertEquals(listOf("Trip: Book the flights"), shown)
        // A date alone reminds at 9:00 on its day.
        assertEquals(at("2026-11-19T09:00"), alarm)

        // The other alarm, or a restart, rings again: nothing comes twice.
        reminders.ring(on = true)
        assertEquals(listOf("Trip: Book the flights"), shown)

        now = at("2026-11-19T09:04")
        reminders.ring(on = true)
        assertEquals(listOf("Trip: Book the flights", "Trip: Pack the bags"), shown)
        // Done ideas don't remind, and the past ones are past: nothing more to set.
        assertNull(alarm)
    }

    @Test
    fun `a reminder whose moment passed while the phone was off comes as it starts again`() = runTest {
        follow(map("trip"))
        now = at("2026-11-19T10:30")
        reminders.ring(on = true)
        assertEquals(listOf("Trip: Book the flights", "Trip: Pack the bags"), shown)
    }

    @Test
    fun `a date first seen after its moment never comes`() = runTest {
        // A date set in the past, or a map arriving with past dates: the visa photos (the 16th) never remind.
        follow(map("trip"))
        texts["trip"] = texts.getValue("trip") + "\n## Call the embassy <!-- orbit due: \"2026-11-18 07:00\" -->"
        now = at("2026-11-18T08:30")
        follow(map("trip"))
        reminders.ring(on = true)
        assertEquals(emptyList<String>(), shown)
    }

    @Test
    fun `a changed date is a new reminder, and a map read again only when its text changes`() = runTest {
        follow(map("trip"))
        follow(map("trip"))
        assertEquals(1, reads["trip"])

        texts["trip"] = texts.getValue("trip").replace("2026-11-18 18:30", "2026-11-18 12:00")
        follow(map("trip"))
        assertEquals(2, reads["trip"])
        assertEquals(at("2026-11-18T12:00"), alarm)
    }

    @Test
    fun `a map that can't be read just now keeps its reminders, and is read again next time`() = runTest {
        follow(map("trip"))
        val text = texts.remove("trip")
        follow(map("trip", key = "changed"))
        assertEquals(at("2026-11-18T18:30"), alarm)

        texts["trip"] = text.orEmpty().replace("2026-11-18 18:30", "2026-11-18 12:00")
        follow(map("trip", key = "changed"))
        assertEquals(3, reads["trip"])
        assertEquals(at("2026-11-18T12:00"), alarm)
    }

    @Test
    fun `a private map's reminder is marked private, and its title comes from the map's details as they are now`() = runTest {
        follow(map("trip", private = true))
        now = at("2026-11-18T18:30")
        reminders.ring(on = true)
        assertEquals(listOf(true), shownReminders.map { it.private })

        // Made private later, without its text changing: its next reminder is private too.
        shownReminders.clear()
        follow(map("trip", title = "Lisbon", private = false))
        follow(map("trip", title = "Lisbon", private = true))
        now = at("2026-11-19T09:00")
        reminders.ring(on = true)
        assertEquals(listOf(true), shownReminders.map { it.private })
        assertEquals("Lisbon", shownReminders.single().mapTitle)
    }

    @Test
    fun `a map that's gone, or can't be read, reminds of nothing`() = runTest {
        follow(map("trip"))
        follow()
        assertNull(alarm)

        follow(map("trip", unreadable = true))
        now = at("2026-11-19T10:00")
        reminders.ring(on = true)
        assertEquals(emptyList<String>(), shown)
    }

    @Test
    fun `turned off, none come, and turned on again, only those still to come`() = runTest {
        follow(map("trip"), on = false)
        assertNull(alarm)

        now = at("2026-11-18T20:00")
        reminders.ring(on = false)
        assertEquals(emptyList<String>(), shown)

        // The flights came while reminders were off: they stay away.
        follow(map("trip"), on = true)
        assertEquals(emptyList<String>(), shown)
        assertEquals(at("2026-11-19T09:00"), alarm)
    }

    @Test
    fun `a date is local time, so in a new time zone it reminds at that zone's hour`() = runTest {
        follow(map("trip"))
        // Flown to Madrid, landing at four in the afternoon there: the flights' 18:30 is Madrid's now.
        zone = ZoneId.of("Europe/Madrid")
        now = LocalDateTime.parse("2026-11-18T16:00").atZone(zone).toInstant().toEpochMilli()
        reminders.ring(on = true)
        assertEquals(LocalDateTime.parse("2026-11-18T18:30").atZone(zone).toInstant().toEpochMilli(), alarm)
    }

    @Test
    fun `what was read is kept, and a file that can't be read starts over`() = runTest {
        follow(map("trip"))
        assertTrue(file.isFile)

        // A new run of Orbit, as for an alarm, rings from what was kept, without reading a map.
        val again = Reminders(file, alarms = object : ReminderAlarms {
            override fun set(at: Long) {
                alarm = at
            }

            override fun cancel() {
                alarm = null
            }
        }, notices = object : ReminderNotices {
            override fun post(reminder: Reminder, at: Instant) {
                shown += reminder.idea
            }
        }, clock = { now }, zone = { zone })

        now = at("2026-11-18T18:35")
        again.ring(on = true)
        assertEquals(listOf("Book the flights"), shown)

        file.writeText("{ not json")
        again.ring(on = true)
        assertNull(alarm)
    }
}
