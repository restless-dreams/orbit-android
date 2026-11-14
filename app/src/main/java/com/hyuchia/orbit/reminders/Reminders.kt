package com.hyuchia.orbit.reminders

import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.data.IdeaDate
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * A reminder an idea's date makes: the map it's in, and the idea, by its
 * name and its place, to open the map at it.
 */
@Serializable
data class Reminder(
    /** The map's folder, which opening it takes. */
    val mapId: String,

    /** The map's title, which a reminder from a map that isn't private names. */
    val mapTitle: String,

    /** A private map's reminder says only that it's from one: nothing of the map or the idea. */
    val private: Boolean,

    /** The idea's name, its icon included. */
    val idea: String,

    /** Where the idea is: its place among its parent's ideas at each level, from the center's. */
    val path: List<Int>,

    /** The idea's date, as Orbit writes it. */
    val due: String,

    /** Which of the map's ideas with this name and date it is, when there are several: 0 for the first. */
    val occurrence: Int = 0,
) {
    /**
     * What tells the reminder apart through the map's other changes: its map,
     * date and idea. A new date, or a renamed idea, is a new reminder.
     */
    val key: String get() = listOf(mapId, due, idea, "$occurrence").joinToString("\n")

    /** When it comes, in `zone`: its date's time, or 9:00 on its day. */
    fun at(zone: ZoneId): Instant? = IdeaDate.parse(due)?.remindAt(zone)
}

/**
 * What [Reminders] keeps between runs, in `reminders.json` beside the maps.
 * Orbit 0.5.0 kept which reminders came by a moment alone ([handledUpTo]),
 * which a new time zone moves the dates past or back over; it still reads
 * the file, so this version writes what it goes by too.
 */
@Serializable
data class ReminderBook(
    /** Every reminder the maps make, as they were last read. */
    val reminders: List<Reminder> = emptyList(),

    /**
     * When each reminder was first seen, by its key: one first seen after its
     * moment, as a date set in the past or a map arriving with past dates,
     * never comes. Kept for Orbit 0.5.0, which goes by it; this version
     * marks such a reminder [handled] as it's first seen.
     */
    val seen: Map<String, Long> = emptyMap(),

    /** Reminders whose moment came up to this one (ms) have come, or were let go: Orbit 0.5.0's record of them (see [handled]). */
    val handledUpTo: Long = 0,

    /** Each map's text when its reminders were read, by its content key: a map is read again only once its text changes. */
    val read: Map<String, String> = emptyMap(),

    /** Whether reminders were on as they were last rung: turned on again, they start from then, not from when they went off. */
    val on: Boolean = true,

    /**
     * The reminders that came, or never will, by key: shown, let go as they
     * came while reminders were off, or first seen after their moment. Each
     * comes once, whatever time zone the phone was in as it came or is in
     * since: a date is local time, so a new time zone moves its moment.
     * Null in a file Orbit 0.5.0 wrote, which says it by [handledUpTo]
     * alone ([handledOf]).
     */
    val handled: Set<String>? = null,
) {
    /**
     * Which of `reminders` came, or never will, with dates in `zone`: as
     * [handled] says, or, from Orbit 0.5.0's file, those whose moment came
     * by [handledUpTo] and those first seen after their moment. One this file
     * hasn't seen yet isn't among them.
     */
    fun handledOf(reminders: List<Reminder>, zone: ZoneId): Set<String> {
        handled?.let { return it }

        return reminders.filter { reminder ->
            val seen = this.seen[reminder.key]
            val moment = reminder.at(zone)?.toEpochMilli()
            seen != null && moment != null && (moment <= handledUpTo || seen > moment)
        }.mapTo(HashSet()) { it.key }
    }
}

/** Where reminders ring: Android's alarms, or a test's own. */
interface ReminderAlarms {
    /** Rings at `at` (ms since 1970), or a little after: the next reminder's moment. */
    fun set(at: Long)

    fun cancel()
}

/** Where reminders show: Android's notifications, or a test's own. */
interface ReminderNotices {
    /** Shows `reminder`, which came at `at`. */
    fun post(reminder: Reminder, at: Instant)

    /**
     * The maps `private` are private: their reminders showing now that name
     * their idea and their map say only that they're from a private map, as
     * one shown now would. `reminders` are those maps' reminders as they're
     * read now.
     */
    fun keepPrivate(private: Set<String>, reminders: List<Reminder>)
}

/**
 * Ideas' dates, reminding on this device when they come, like a calendar:
 * at their time, or at 9:00 for a date without one.
 *
 * The maps are read lightly ([MapText.dates]), a map again only when its
 * text changes, wherever the change came from: an edit, the folder maps are
 * kept in, a map sent or imported, a version restored. Done ideas don't
 * remind, and neither do maps in the trash. What was read is kept in `file`,
 * so an alarm, a restart or a new time zone rings without reading a map.
 *
 * A reminder comes once, whatever time zones the phone goes through: those
 * whose moment passed while the phone was off come when it starts again,
 * but one first seen after its moment (a date set in the past, a map
 * arriving with past dates) never does. Turned off, none come, and turned on
 * again, only those still to come will. A map made private has its
 * reminders that show say nothing more of it.
 */
class Reminders(
    private val file: File,
    private val alarms: ReminderAlarms,
    private val notices: ReminderNotices,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    /**
     * The maps' reminders, read again where their text changed (`read` gives
     * a map's text by its id, null when it can't be read), then rung: the
     * ones whose moment came show, and the next alarm is set. `on` is the
     * setting. The private maps' reminders that show say nothing of them.
     */
    suspend fun follow(maps: List<MapSummary>, read: suspend (String) -> String?, on: Boolean): Unit = mutex.withLock {
        val book = load()
        val now = clock()
        val kept = book.reminders.groupBy { it.mapId }
        val reminders = mutableListOf<Reminder>()
        val texts = mutableMapOf<String, String>()

        for (map in maps) {
            if (map.unreadable) {
                continue
            }

            // A map whose text is as it was keeps its reminders, as its title and its privacy now say. So does one
            // that can't be read just now, which is read again next time.
            val text = if (book.read[map.id] == map.contentKey) null else read(map.id)
            val ideas = text?.let { remindersIn(map.id, it) } ?: kept[map.id].orEmpty()
            reminders += ideas.map { it.copy(mapTitle = map.title, private = map.metadata.isPrivate) }

            if (text != null || book.read[map.id] == map.contentKey) {
                texts[map.id] = map.contentKey
            }
        }

        val zone = zone()
        val seen = reminders.associate { it.key to (book.seen[it.key] ?: now) }
        // First seen after its moment (a date set in the past, a map arriving with past dates): it never comes.
        val late = reminders.filter { it.key !in book.seen && (it.at(zone)?.toEpochMilli() ?: Long.MAX_VALUE) <= now }.map { it.key }
        val handled = (book.handledOf(reminders, zone) + late).filterTo(HashSet()) { it in seen }
        save(ring(book.copy(reminders = reminders, seen = seen, read = texts, handled = handled), now, on))

        // A map made private since its reminder showed: what shows says nothing more of it either.
        val private = maps.filter { it.metadata.isPrivate }.mapTo(HashSet()) { it.id }

        if (private.isNotEmpty()) {
            notices.keepPrivate(private, reminders.filter { it.mapId in private })
        }
    }

    /** An alarm went off, the phone started again or its clock changed: the reminders whose moment came show, and the next alarm is set. */
    suspend fun ring(on: Boolean): Unit = mutex.withLock {
        save(ring(load(), clock(), on))
    }

    /** The reminders the map with this text makes, in the order it has them: none for done ideas. */
    private fun remindersIn(mapId: String, text: String): List<Reminder> {
        val occurrences = mutableMapOf<Pair<String, String>, Int>()

        return MapText.dates(text).filterNot { IdeaDate.isDone(it.status) }.map { idea ->
            val due = idea.date.text
            val occurrence = occurrences.getOrDefault(due to idea.name, 0)
            occurrences[due to idea.name] = occurrence + 1
            Reminder(mapId, mapTitle = "", private = false, idea = idea.name, path = idea.path, due = due, occurrence = occurrence)
        }
    }

    private fun ring(book: ReminderBook, now: Long, on: Boolean): ReminderBook {
        val zone = zone()
        val handled = book.handledOf(book.reminders, zone).toMutableSet()

        for (reminder in book.reminders) {
            val at = reminder.at(zone) ?: continue

            if (reminder.key in handled || at.toEpochMilli() > now) {
                continue
            }

            // Its moment came: it shows while reminders are on. Turned on again, those that came while they were off stay away.
            if (on && book.on) {
                notices.post(reminder, at)
            }

            handled += reminder.key
        }

        val next = if (on) book.reminders.filter { it.key !in handled }.mapNotNull { it.at(zone)?.toEpochMilli() }.filter { it > now }.minOrNull() else null

        if (next == null) {
            alarms.cancel()
        } else {
            alarms.set(next)
        }

        return book.copy(handled = handled, handledUpTo = maxOf(book.handledUpTo, now), on = on)
    }

    private suspend fun load(): ReminderBook = withContext(Dispatchers.IO) {
        if (!file.isFile) {
            return@withContext ReminderBook()
        }

        try {
            JSON.decodeFromString<ReminderBook>(file.readText())
        } catch (unreadable: SerializationException) {
            Logs.warn("Reminders", "Couldn't read the reminders: starting over", unreadable)
            ReminderBook()
        } catch (unreadable: IllegalArgumentException) {
            Logs.warn("Reminders", "Couldn't read the reminders: starting over", unreadable)
            ReminderBook()
        } catch (failed: IOException) {
            Logs.warn("Reminders", "Couldn't read the reminders: starting over", failed)
            ReminderBook()
        }
    }

    // Kept even when the change that rang is overtaken by another: the reminders it showed are shown.
    private suspend fun save(book: ReminderBook) = withContext(NonCancellable + Dispatchers.IO) {
        try {
            writeAtomically(file, JSON.encodeToString(ReminderBook.serializer(), book))
        } catch (failed: IOException) {
            Logs.warn("Reminders", "Couldn't keep the reminders", failed)
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
