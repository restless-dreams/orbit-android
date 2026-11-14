package com.hyuchia.orbit.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.EXTRA_OPEN_MARK
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OpenMark
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.coverTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the ids of shortcuts to maps start with, before the map's. */
private const val MAP_SHORTCUT = "map:"

private fun shortcutId(mapId: String) = "$MAP_SHORTCUT$mapId"

/**
 * Offers a map among the launcher icon's shortcuts, most recently opened
 * first. One pinned to the home screen that was turned off as the map was
 * deleted is on again: the map is back.
 */
fun offerShortcut(context: Context, mapId: String, title: String) {
    // A launcher shortcut needs a name: a map without one (one Orbit couldn't read, say) gets none.
    if (title.isBlank()) {
        return
    }

    val shortcut = shortcut(context, mapId, title)
    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

    if (pinnedShortcuts(context).any { it.id == shortcut.id }) {
        ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
    }
}

/** Orbit's shortcuts pinned to the home screen, those turned off included. */
private fun pinnedShortcuts(context: Context): List<ShortcutInfo> = context.getSystemService(ShortcutManager::class.java)?.pinnedShortcuts.orEmpty()

/**
 * Takes a private map out of the launcher's shortcuts. One pinned to the
 * home screen stays there, named `label` (its cover's name, or "Private
 * map"), and still opens it, to its lock: [offerShortcut] gives it its name
 * back once it isn't private.
 */
fun hideShortcut(context: Context, mapId: String, label: String = context.getString(R.string.private_map)) {
    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(mapId)))
    val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).any { it.id == shortcutId(mapId) }

    if (pinned) {
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut(context, mapId, label)))
    }
}

private fun shortcut(context: Context, mapId: String, title: String): ShortcutInfoCompat {
    val intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_MAP)
        .putExtra(EXTRA_MAP_ID, mapId)
        .putExtra(EXTRA_OPEN_MARK, OpenMark.of(context))
    return ShortcutInfoCompat.Builder(context, shortcutId(mapId))
        .setShortLabel(title.take(24))
        .setLongLabel(title.take(48))
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_map))
        .setIntent(intent)
        .build()
}

/** Takes a deleted map out of the shortcuts, and turns off any pinned to the home screen. */
fun withdrawShortcut(context: Context, mapId: String) {
    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(mapId)))
    ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId(mapId)), context.getString(R.string.ui_map_deleted))
}

/** What each map's shortcuts say now, by the map's id: its title, or a private map's cover's name ("Private map" without one). */
fun shortcutLabels(resources: Resources, maps: List<MapSummary>): Map<String, String> =
    maps.associate { map -> map.id to if (map.metadata.isPrivate) resources.coverTitle(map.metadata.cover) else map.title }

/**
 * Brings the launcher's shortcuts to maps in line with `maps` (by id),
 * whatever changed the maps: each says what [shortcutLabels] says of its map
 * now, a map renamed on Home say, and a private map's only its cover's name,
 * however it became private ([com.hyuchia.orbit.privacy.PrivateMaps.setPrivate]
 * takes it out of the launcher icon's); one whose map is gone (deleted, by Orbit or in the folder
 * maps are kept in) leaves them, and one pinned is turned off, saying so; and
 * one pinned whose map is back (restored from the trash) is on again. Only
 * opening a map puts it among the launcher icon's ([offerShortcut]). Whether
 * the launcher took it all: `update` sends the new labels.
 */
fun matchShortcuts(
    context: Context,
    maps: Map<String, String>,
    update: (Context, List<ShortcutInfoCompat>) -> Boolean = ShortcutManagerCompat::updateShortcuts,
): Boolean {
    try {
        val dynamic = context.getSystemService(ShortcutManager::class.java)?.dynamicShortcuts.orEmpty()
        val pinned = pinnedShortcuts(context)
        val pinnedIds = pinned.mapTo(HashSet()) { it.id }
        val gone = mutableListOf<String>()
        val on = mutableListOf<ShortcutInfoCompat>()
        val renamed = mutableListOf<ShortcutInfoCompat>()

        for (shortcut in (dynamic + pinned).filter { it.id.startsWith(MAP_SHORTCUT) }.distinctBy { it.id }) {
            val mapId = shortcut.id.removePrefix(MAP_SHORTCUT)
            val label = maps[mapId]

            if (label == null) {
                gone += shortcut.id
                continue
            }

            // A launcher shortcut needs a name: one whose map has none (one Orbit couldn't read, say) keeps its own.
            if (label.isBlank()) {
                continue
            }

            val now = shortcut(context, mapId, label)

            if (shortcut.id in pinnedIds) {
                on += now
            }

            // Both labels: a new title that starts as the old one did can have the short label it had.
            if (shortcut.shortLabel?.toString() != now.shortLabel.toString() || shortcut.longLabel?.toString() != now.longLabel?.toString()) {
                renamed += now
            }
        }

        // Only what changes: Android counts each update against the few it allows Orbit in the background, even an empty one.
        if (gone.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, gone)
            ShortcutManagerCompat.disableShortcuts(context, gone, context.getString(R.string.ui_map_deleted))
        }

        if (on.isNotEmpty()) {
            ShortcutManagerCompat.enableShortcuts(context, on)
        }

        // Android takes a few updates a while from an app in the background, and refuses the others.
        return renamed.isEmpty() || update(context, renamed)
    } catch (refused: RuntimeException) {
        // Over the few updates Android takes in the background, or the phone not unlocked since it started, say.
        Logs.warn("Shortcuts", "Couldn't bring the launcher's shortcuts in line with the maps", refused)
        return false
    }
}

/**
 * Keeps the launcher's shortcuts to maps in line with the maps ([matchShortcuts]
 * with `match`, which says whether the launcher took it all), as what they say
 * changes ([follow]). What the launcher didn't take (Android takes a few
 * updates a while from an app in the background) goes again as Orbit comes back
 * on screen ([cameBack]), where it takes them: a map made private elsewhere,
 * synced while Orbit was away, never keeps its name on a pinned shortcut.
 */
class ShortcutMatching(private val match: (Map<String, String>) -> Boolean) {
    private val matching = Mutex()

    /** What the shortcuts should say now, by map, once the maps are read. */
    private var latest: Map<String, String>? = null

    /** Whether the launcher didn't take all of [latest]. */
    private var behind = false

    /** Matches the shortcuts each time `labels` ([shortcutLabels]) changes, until it's cancelled. */
    suspend fun follow(labels: Flow<Map<String, String>>) = labels.distinctUntilChanged().collect { now ->
        matching.withLock {
            latest = now
            behind = !match(now)
        }
    }

    /** Orbit came back on screen: what the launcher didn't take goes again, as the maps are now. */
    suspend fun cameBack() = matching.withLock {
        latest?.takeIf { behind }?.let { behind = !match(it) }
    }
}
