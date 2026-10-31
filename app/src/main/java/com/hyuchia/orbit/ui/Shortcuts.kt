package com.hyuchia.orbit.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.EXTRA_OPEN_MARK
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OpenMark
import com.hyuchia.orbit.R

private fun shortcutId(mapId: String) = "map:$mapId"

/** Offers a map among the launcher icon's shortcuts, most recently opened first. */
fun offerShortcut(context: Context, mapId: String, title: String) {
    // A launcher shortcut needs a name: a map without one (one Orbit couldn't read, say) gets none.
    if (title.isBlank()) {
        return
    }

    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut(context, mapId, title))
}

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
