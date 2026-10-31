package com.hyuchia.orbit.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A map as it was at `time`: what the version history lists. An `original`
 * is the file as it came in from elsewhere (a folder, an import, a share),
 * before Orbit ever wrote it: thinning never removes it. `reason` says why
 * it was kept, where Orbit knows: versions kept before reasons were, or for
 * a reason a newer Orbit knows and this one doesn't, have none.
 */
data class MapVersion(val time: Long, val original: Boolean = false, val reason: VersionReason? = null)

/**
 * Why a version was kept. It's kept beside the map's history, by the
 * version's file (MapRepository's reasons file), as JSON: `why`, and what
 * else that reason has. A `why` this version doesn't know reads as none.
 */
sealed interface VersionReason {
    /** The map as it was when it was opened, kept as the visit's first change was saved. */
    data object Opened : VersionReason

    /** One of a stretch of editing: a version every ten minutes while the map changes. */
    data object Editing : VersionReason

    /** Before an idea was removed with the ideas under it: its name, and how many went with it. */
    data class Removing(val idea: String, val under: Int) : VersionReason

    /** Before an earlier version (kept at `from`) became the map again. */
    data class Restoring(val from: Long?) : VersionReason

    /** Before the map's text was edited by hand. */
    data object Text : VersionReason

    /** Before ideas caught on the go went into the map. */
    data object Caught : VersionReason

    /** Before the map's tags changed, away from its screen. */
    data object Tags : VersionReason

    /** Before the map was renamed, away from its screen. */
    data object Renaming : VersionReason

    /** Before another device's version of the map replaced it. */
    data class FromDevice(val device: String) : VersionReason

    /** Before a file's version of the map replaced it (Update, on a zip of it): its name, when it had one. */
    data class FromFile(val file: String?) : VersionReason

    /** Before the version in the synced folder replaced it. */
    data object FromFolder : VersionReason

    /** The map as a change made elsewhere left it, kept before the next edit went on top. */
    data object Elsewhere : VersionReason

    /** Changes that couldn't be saved over a change made elsewhere: kept, so nothing is lost. */
    data object Aside : VersionReason

    /** A file as it came in, before Orbit wrote it its own way: its name, when it had one. */
    data class CameIn(val file: String?) : VersionReason

    /** The reason as it's kept. */
    fun toJson(): JsonObject = when (this) {
        Opened -> why("opened")
        Editing -> why("editing")
        is Removing -> why("removing", "idea" to JsonPrimitive(idea), "under" to JsonPrimitive(under))
        is Restoring -> why("restoring", "from" to from?.let(::JsonPrimitive))
        Text -> why("text")
        Caught -> why("caught")
        Tags -> why("tags")
        Renaming -> why("renaming")
        is FromDevice -> why("device", "device" to JsonPrimitive(device))
        is FromFile -> why("file", "file" to file?.let(::JsonPrimitive))
        FromFolder -> why("folder")
        Elsewhere -> why("elsewhere")
        Aside -> why("aside")
        is CameIn -> why("came-in", "file" to file?.let(::JsonPrimitive))
    }

    companion object {
        private fun why(why: String, vararg details: Pair<String, JsonPrimitive?>): JsonObject =
            JsonObject(mapOf("why" to JsonPrimitive(why)) + details.mapNotNull { (key, value) -> value?.let { key to it } })

        /** A kept reason, or null for one this version doesn't know or can't read. */
        fun read(json: JsonObject): VersionReason? {
            fun text(key: String): String? = runCatching { json[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

            return when (text("why")) {
                "opened" -> Opened
                "editing" -> Editing
                "removing" -> text("idea")?.let { Removing(it, runCatching { json["under"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0) }
                "restoring" -> Restoring(runCatching { json["from"]?.jsonPrimitive?.longOrNull }.getOrNull())
                "text" -> Text
                "caught" -> Caught
                "tags" -> Tags
                "renaming" -> Renaming
                "device" -> text("device")?.let(::FromDevice)
                "file" -> FromFile(text("file"))
                "folder" -> FromFolder
                "elsewhere" -> Elsewhere
                "aside" -> Aside
                "came-in" -> CameIn(text("file"))
                else -> null
            }
        }
    }
}

/** A version is kept before an edit once the latest is this old: one for every stretch of work. */
const val CHECKPOINT_INTERVAL = 10 * 60 * 1000L

private const val HOUR = 60 * 60 * 1000L
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

/** The most versions a map keeps, however far back they go. */
private const val VERSION_LIMIT = 200

/** The newest versions a map always keeps, whatever their age: the latest work can always be brought back. */
private const val NEWEST_KEPT = 20

/** The most versions dated after now (a clock that was wrong as they were kept) a map keeps. */
private const val FUTURE_LIMIT = 10

/** The versions of the last day kept all; past them, one is kept for each [CHECKPOINT_INTERVAL]. */
private const val LAST_DAY_ALL = 50

/**
 * Which versions a map keeps as they age: the newest [NEWEST_KEPT] whatever
 * their age (not counting those dated ahead); from the last day, the newest [LAST_DAY_ALL], then one for each
 * ten minutes; the latest of each hour for a week, of each day for three
 * months, and of each week after that. A busy day never takes the place of
 * those older ones: it's what's thinned to stay within [VERSION_LIMIT].
 * Versions dated after `now` (a clock that was wrong when they were kept)
 * count among them, at most [FUTURE_LIMIT]. In each stretch, a `preferred`
 * version (one kept before a big change) is the one kept over newer ones.
 */
fun versionsToKeep(times: Collection<Long>, now: Long, preferred: Set<Long> = emptySet()): Set<Long> {
    val sorted = times.distinct().sortedDescending()
    val (future, past) = sorted.partition { it > now }
    val ahead = future.take(FUTURE_LIMIT)

    /** One version for each stretch `stretch` names: a preferred one when the stretch has one, else its newest. */
    fun oneEach(versions: List<Long>, stretch: (Long) -> String): List<Long> =
        versions.groupBy(stretch).values.map { group -> group.firstOrNull { it in preferred } ?: group.first() }

    val (lastDay, older) = past.partition { now - it < DAY }

    val olderKept = oneEach(older) { time ->
        val age = now - time

        when {
            age < WEEK -> "hour ${time / HOUR}"
            age < 13 * WEEK -> "day ${time / DAY}"
            else -> "week ${time / WEEK}"
        }
    }.sortedDescending().take(VERSION_LIMIT - NEWEST_KEPT - ahead.size)

    val lastDayKept = (lastDay.take(LAST_DAY_ALL) + oneEach(lastDay.drop(LAST_DAY_ALL)) { "ten minutes ${it / CHECKPOINT_INTERVAL}" }).sortedDescending()
    val room = (VERSION_LIMIT - ahead.size - olderKept.size).coerceAtLeast(NEWEST_KEPT)
    return (past.take(NEWEST_KEPT) + ahead + lastDayKept.take(room) + olderKept).toSet()
}

/** Whether it's time to keep a version before writing, given when the versions were kept: [CHECKPOINT_INTERVAL] since the latest that isn't dated in the future. */
fun checkpointDue(times: Collection<Long>, now: Long): Boolean {
    val latest = times.filter { it <= now }.maxOrNull() ?: return true
    return now - latest >= CHECKPOINT_INTERVAL
}
