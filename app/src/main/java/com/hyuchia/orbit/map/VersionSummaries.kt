package com.hyuchia.orbit.map

import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.OtherApps
import com.hyuchia.orbit.data.TextIdea
import com.hyuchia.orbit.data.VersionChanges

/** How many versions' texts are kept read while rows ask: the ones around those on screen. */
private const val VERSIONS_KEPT = 8

/** What a version's row says about what it holds. */
sealed interface VersionSummary {
    /** What it changed from the version before it. */
    data class Changed(val changes: VersionChanges) : VersionSummary

    /** How many ideas it holds: the oldest version kept, a file as it came in, or one with no version before it to compare with. */
    data class Holds(val ideas: Int) : VersionSummary

    /** Its file can't be read. */
    data object Unreadable : VersionSummary
}

/**
 * Works out what each version in a map's history changed from the one
 * before it, as its row asks: against the newest version older than it
 * that isn't a file as it came in (another app's file reads differently).
 * Each version is read and its ideas found once while rows around it ask,
 * so a long history, or a map of thousands of ideas, costs only the rows on
 * screen. `read` reads a version's text, null when it can't be.
 */
class VersionSummaries(private val read: suspend (Long) -> String?) {
    private class Read(val text: String, val ideas: List<TextIdea>)

    private val kept = object : LinkedHashMap<Long, Read?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Read?>): Boolean = size > VERSIONS_KEPT
    }

    private suspend fun version(time: Long): Read? {
        if (kept.containsKey(time)) {
            return kept[time]
        }

        val read = read(time)?.let { text -> Read(text, MapText.ideas(text)) }
        kept[time] = read
        return read
    }

    /** What the version kept at `time` holds, or changed, among `versions` (the newest first). */
    suspend fun of(time: Long, versions: List<MapVersion>): VersionSummary {
        val place = versions.indexOfFirst { it.time == time }
        val version = versions.getOrNull(place) ?: return VersionSummary.Unreadable
        val read = version(time) ?: return VersionSummary.Unreadable

        // A file as it came in counts its own ideas, as another app's map (OPML, say) has them.
        if (version.original) {
            return VersionSummary.Holds(OtherApps.ideasIn(read.text) ?: (read.ideas.size - 1).coerceAtLeast(0))
        }

        val before = versions.drop(place + 1).firstOrNull { !it.original }?.let { version(it.time) } ?: return VersionSummary.Holds((read.ideas.size - 1).coerceAtLeast(0))
        return VersionSummary.Changed(VersionChanges.between(before.ideas, read.ideas, textChanged = before.text != read.text))
    }

    /**
     * What the map as it is now (`markdown`) changed from its newest version
     * that isn't a file as it came in, or, with none to compare it with, how
     * many ideas it holds.
     */
    suspend fun now(markdown: String, versions: List<MapVersion>): VersionSummary {
        val ideas = MapText.ideas(markdown)
        val newest = versions.firstOrNull { !it.original }?.let { version(it.time) } ?: return VersionSummary.Holds((ideas.size - 1).coerceAtLeast(0))
        return VersionSummary.Changed(VersionChanges.between(newest.ideas, ideas, textChanged = newest.text != markdown))
    }
}
