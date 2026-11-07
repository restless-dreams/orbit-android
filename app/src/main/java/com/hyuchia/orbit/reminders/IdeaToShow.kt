package com.hyuchia.orbit.reminders

import com.hyuchia.orbit.page.MapNode

/** An idea to show as its map opens: a reminder's, by its name, its date and its place. */
data class IdeaToShow(val mapId: String, val name: String, val due: String, val path: List<Int>)

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
 * The idea among `nodes` (the page's, as Orbit read the map) that `wanted`
 * names: the one with its date and name, else the one at its place with its
 * date, else the only one with its date. The reminder came from a light
 * reading of the same text, which agrees with Orbit's but in rare maps; null
 * when the idea isn't there anymore.
 */
fun ideaAmong(nodes: List<MapNode>, wanted: IdeaToShow): MapNode? {
    val dated = nodes.filter { it.date?.text == wanted.due }
    val name = plain(wanted.name)

    return dated.firstOrNull { plain(listOfNotNull(it.icon, it.text).joinToString(" ")) == name || plain(it.text) == name }
        ?: ideaAt(nodes, wanted.path)?.takeIf { it.date?.text == wanted.due }
        ?: dated.singleOrNull()
}
