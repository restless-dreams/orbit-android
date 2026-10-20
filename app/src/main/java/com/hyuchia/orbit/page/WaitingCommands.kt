package com.hyuchia.orbit.page

/**
 * Commands sent to a page before it said hello, which it hears in order once
 * it does. Only what still matters waits, so a page that's slow to start, or
 * never does, doesn't pile up work: the page's words wait as the latest, in
 * the place of the first; a map loaded or unloaded makes what waited about
 * the map before it moot, since it sets everything about the page's map
 * again; and a job's command leaves with its job.
 */
internal class WaitingCommands {
    /** What a command is about, which decides how long it waits. */
    enum class Kind {
        /** The page's language and words. */
        Words,

        /** A job, whose answer someone waits for. */
        Job,

        /** The map the page shows: an edit, its view, its colors or the room around it. */
        Map,

        /** Another map, or none: loading or unloading one. */
        NewMap,
    }

    private class Waiting(val encoded: String, val kind: Kind, val job: Int?)

    private val waiting = mutableListOf<Waiting>()

    /** How many commands wait. */
    val size: Int get() = waiting.size

    /** Adds a command, encoded: `job` is its number, for a job's. */
    fun add(encoded: String, kind: Kind, job: Int? = null) {
        val words = waiting.indexOfFirst { it.kind == Kind.Words }

        if (kind == Kind.Words && words >= 0) {
            waiting[words] = Waiting(encoded, kind, job)
            return
        }

        if (kind == Kind.NewMap) {
            waiting.removeAll { it.kind == Kind.Map || it.kind == Kind.NewMap }
        }

        waiting += Waiting(encoded, kind, job)
    }

    /** Takes job `job`'s command out: it was answered, given up on, or no longer wanted. */
    fun forget(job: Int) {
        waiting.removeAll { it.job == job }
    }

    /** The commands, in the order they came, which wait no more. */
    fun take(): List<String> = waiting.map { it.encoded }.also { waiting.clear() }

    fun clear() {
        waiting.clear()
    }
}
