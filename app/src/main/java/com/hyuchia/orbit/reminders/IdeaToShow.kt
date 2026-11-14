package com.hyuchia.orbit.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.hyuchia.orbit.page.MapNode
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An idea to show as its map opens: a reminder's, by its name, its date and
 * its place, and which of the map's ideas with that name and date it is
 * (`occurrence`, 0 for the first, in the order the map has them).
 */
data class IdeaToShow(val mapId: String, val name: String, val due: String, val path: List<Int>, val occurrence: Int = 0)

/** A name by its letters and figures alone, in lowercase: the light reading and Orbit may keep other marks of it. */
private fun plain(name: String): String = name.filter(Char::isLetterOrDigit).lowercase()

/** The idea at `path` among `nodes`: its place among its parent's ideas at each level, from the center's. */
private fun ideaAt(nodes: List<MapNode>, path: List<Int>): MapNode? {
    var idea = nodes.firstOrNull { it.isRoot } ?: return null

    for (index in path) {
        idea = nodes.firstOrNull { it.parentId == idea.id && it.index == index } ?: return null
    }

    return idea
}

/**
 * The idea among `nodes` (the page's, as Orbit read the map, in its order)
 * that `wanted` names: the one with its date and name; of several, the one
 * at its place, else the one that many along; else the one at its place
 * with its date, else the only one with its date. The reminder came from a
 * light reading of the same text, which agrees with Orbit's but in rare
 * maps; null when the idea isn't there anymore.
 */
fun ideaAmong(nodes: List<MapNode>, wanted: IdeaToShow): MapNode? {
    val dated = nodes.filter { it.date?.text == wanted.due }
    val name = plain(wanted.name)
    val named = dated.filter { plain(listOfNotNull(it.icon, it.text).joinToString(" ")) == name || plain(it.text) == name }
    val placed = ideaAt(nodes, wanted.path)?.takeIf { it.date?.text == wanted.due }

    return named.singleOrNull()
        ?: placed?.takeIf { it in named }
        ?: named.getOrNull(wanted.occurrence)
        ?: named.firstOrNull()
        ?: placed
        ?: dated.singleOrNull()
}

/**
 * Keeps `idea`, a reminder's idea its map hasn't shown yet, with the screens'
 * state: Android may stop Orbit while it's away, as the phone's screen lock is
 * asked for a private map's, and brings the screens back after, their map's
 * idea with them, to show once the map is read. One the map already took
 * isn't kept.
 */
@Composable
fun KeepIdeaToShow(idea: MutableStateFlow<IdeaToShow?>) {
    rememberSaveable(
        saver = listSaver(
            save = { flow -> flow.value?.let { listOf(it.mapId, it.name, it.due, it.path.toIntArray(), it.occurrence) }.orEmpty() },
            restore = { saved ->
                val path = (saved[3] as IntArray).toList()
                idea.compareAndSet(null, IdeaToShow(saved[0] as String, saved[1] as String, saved[2] as String, path, saved[4] as Int))
                idea
            },
        ),
    ) { idea }
}
