package com.hyuchia.orbit.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * An idea's date: the `due` attribute Orbit reads, a day (`2026-11-20`) or a
 * day and a time of day (`2026-11-20 09:30`). It's local time, with no time
 * zone: 9:30 is 9:30 wherever the phone is, as a date in a paper diary is.
 * Read by the same rules as Orbit's `parseDue` (its `src/core/dates.ts`), so
 * the app, its light reading of maps and the map's page agree on every date.
 */
data class IdeaDate(val day: LocalDate, val time: LocalTime? = null) {
    /** As Orbit writes it: `2026-11-20`, or `2026-11-20 09:30`. */
    val text: String
        get() = if (time == null) "$day" else "$day ${String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)}"

    /** When it reminds: at its time, or at [REMINDER_TIME] on its day, in `zone`. */
    fun remindAt(zone: ZoneId): Instant = LocalDateTime.of(day, time ?: REMINDER_TIME).atZone(zone).toInstant()

    /** When it's past, in `zone`: its time, or the end of its day, when the next one starts. */
    fun end(zone: ZoneId): Instant = if (time == null) day.plusDays(1).atStartOfDay(zone).toInstant() else LocalDateTime.of(day, time).atZone(zone).toInstant()

    /** Whether it's past at `now`: its day is over, or its time has come. */
    fun isPast(now: Instant, zone: ZoneId): Boolean = !now.isBefore(end(zone))

    /**
     * Whether it's overdue at `now`: past, while the idea isn't done (its
     * `status` is one of Orbit's words for done). The map's page draws it in
     * its attention color then, and the app's panels say so.
     */
    fun isOverdue(status: String?, now: Instant, zone: ZoneId): Boolean = isPast(now, zone) && !isDone(status)

    companion object {
        /** A date without a time reminds at 9:00 on its day: early enough to act on it, late enough not to wake anyone. */
        val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        /**
         * A year of four figures, a month and a day, then optionally a time after
         * a space or a `T` (as ISO 8601 writes it): hours and minutes. Seconds
         * and time zones aren't part of it.
         */
        private val WRITTEN = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})(?:(?:[Tt]|[ \t]+)(\d{1,2}):(\d{2}))?$""")

        /**
         * Reads a `due` value: `2026-11-20`, or `2026-11-20 09:30` (or
         * `2026-11-20T09:30`, as programs write it; a single figure for the
         * month, day or hour works too). Null for anything else, and for a day
         * or a time that doesn't exist, such as February 30th or 24:00, which
         * Orbit keeps as written and doesn't use.
         */
        fun parse(value: String?): IdeaDate? {
            val written = value?.trim()?.let(WRITTEN::matchEntire) ?: return null
            val (year, month, day, hours, minutes) = written.destructured

            if (year.toInt() < 1) {
                return null
            }

            val date = try {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            } catch (missing: DateTimeException) {
                return null
            }

            if (hours.isEmpty()) {
                return IdeaDate(date)
            }

            if (hours.toInt() > 23 || minutes.toInt() > 59) {
                return null
            }

            return IdeaDate(date, LocalTime.of(hours.toInt(), minutes.toInt()))
        }

        /** Orbit's words for a done idea, in any case: its `statusTone` calls these done. */
        private val DONE = Regex("""^(done|complete|completed|shipped|finished|closed)$""", RegexOption.IGNORE_CASE)

        /** Whether a status is one of Orbit's words for done: a done idea is never overdue, and doesn't remind. */
        fun isDone(status: String?): Boolean = status != null && DONE.matches(status.trim())
    }
}
