package com.hyuchia.orbit.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.R

private fun shortcutId(mapId: String) = "map:$mapId"

/** Offers a map among the launcher icon's shortcuts, most recently opened first. */
fun offerShortcut(context: Context, mapId: String, title: String) {
    // A launcher shortcut needs a name: a map without one (one Orbit couldn't read, say) gets none.
    if (title.isBlank()) {
        return
    }

    val intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_MAP)
        .putExtra(EXTRA_MAP_ID, mapId)
    val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(mapId))
        .setShortLabel(title.take(24))
        .setLongLabel(title.take(48))
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_map))
        .setIntent(intent)
        .build()
    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
}

/** Takes a deleted map out of the shortcuts, and turns off any pinned to the home screen. */
fun withdrawShortcut(context: Context, mapId: String) {
    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(mapId)))
    ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId(mapId)), context.getString(R.string.ui_map_deleted))
}
