package com.hyuchia.orbit.data

/**
 * A map as it was at `time`: what the version history lists. An `original`
 * is the file as it came in from elsewhere (a folder, an import, a share),
 * before Orbit ever wrote it: thinning never removes it.
 */
data class MapVersion(val time: Long, val title: String, val ideas: Int, val original: Boolean = false)

/** A version is kept before an edit once the latest is this old: one for every stretch of work. */
const val CHECKPOINT_INTERVAL = 10 * 60 * 1000L

private const val HOUR = 60 * 60 * 1000L
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

/** The most versions a map keeps, however far back they go. */
private const val VERSION_LIMIT = 200

/**
 * Which versions a map keeps as they age: all from the last day, the latest
 * of each hour for a week, of each day for three months, and of each week
 * after that, at most [VERSION_LIMIT] in all, the newest first. Versions
 * dated after `now` (a clock that was wrong when they were kept) count as
 * the newest, but never take the place of the ones before them.
 */
fun versionsToKeep(times: Collection<Long>, now: Long): Set<Long> {
    val (future, past) = times.partition { it > now }
    val kept = past.sortedDescending()
        .groupBy { time ->
            val age = now - time

            when {
                age < DAY -> "all $time"
                age < WEEK -> "hour ${time / HOUR}"
                age < 13 * WEEK -> "day ${time / DAY}"
                else -> "week ${time / WEEK}"
            }
        }
        .values
        .map { bucket -> bucket.first() }
    return (future + kept.sortedDescending().take(VERSION_LIMIT)).toSet()
}

/** Whether it's time to keep a version before writing, given when the versions were kept: [CHECKPOINT_INTERVAL] since the latest that isn't dated in the future. */
fun checkpointDue(times: Collection<Long>, now: Long): Boolean {
    val latest = times.filter { it <= now }.maxOrNull() ?: return true
    return now - latest >= CHECKPOINT_INTERVAL
}
