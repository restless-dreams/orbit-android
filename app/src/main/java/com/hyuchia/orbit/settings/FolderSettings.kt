package com.hyuchia.orbit.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.DocumentFolder
import com.hyuchia.orbit.data.FolderNotes
import com.hyuchia.orbit.data.FolderSyncState
import com.hyuchia.orbit.data.MAP_MEDIA_LIMIT
import com.hyuchia.orbit.data.MAP_TEXT_LIMIT
import com.hyuchia.orbit.data.MEDIA_FILE_LIMIT
import com.hyuchia.orbit.data.readableSize
import com.hyuchia.orbit.home.timeAgo
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.coverTitle
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.Hint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeping the maps in a folder: choosing one (its markdown files become
 * maps, after asking), how it's going, the other devices that keep their
 * maps there, and stopping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderSettings() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val words = rememberWords()
    val container = context.container
    val folderSync = container.folderSync
    val sync by folderSync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pickProblem by remember { mutableStateOf<String?>(null) }
    val choose = rememberFolderChooser { pickProblem = it }

    fun stop() {
        val folder = sync.folder ?: return

        scope.launch {
            container.settings.update { it.copy(syncFolder = null) }
            DocumentFolder.releaseAccess(context.contentResolver, folder)
        }
    }

    val folder = sync.folder

    if (folder == null) {
        ListItem(
            onClick = choose,
            supportingContent = {
                Text(stringResource(R.string.settings_keep_maps_in_folder_description))
            },
            leadingContent = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.settings_keep_maps_in_folder))
        }
    } else {
        val now by produceState(System.currentTimeMillis(), sync.synced) {
            while (true) {
                value = System.currentTimeMillis()
                delay(30_000)
            }
        }

        ListItem(
            headlineContent = { Text(sync.name ?: stringResource(R.string.settings_your_folder)) },
            supportingContent = {
                Text(status(resources, sync, now), color = if (sync.problem != null && !sync.syncing) MaterialTheme.colorScheme.error else Color.Unspecified)
            },
            leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
            trailingContent = {
                if (sync.syncing) {
                    LoadingIndicator(Modifier.size(40.dp))
                } else {
                    Hint(stringResource(R.string.settings_check_folder_now)) {
                        IconButton(onClick = folderSync::request) {
                            Icon(Icons.Outlined.Sync, contentDescription = stringResource(R.string.settings_check_folder_now))
                        }
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Note(stringResource(R.string.settings_folder_how_it_works))
        val notes = sync.notes
        // While private maps are locked, the notes don't name them, nor their pictures and videos: they're counted instead.
        val privacy by container.privateMaps.view.collectAsStateWithLifecycle()
        val summaries by container.maps.maps.collectAsStateWithLifecycle()
        val trash by container.maps.trash.collectAsStateWithLifecycle()

        val privateTitles = remember(summaries, trash) {
            summaries.orEmpty().filter { it.metadata.isPrivate }.mapTo(mutableSetOf()) { it.title } + trash.filter { it.isPrivate }.map { it.title }
        }

        // A locked private map whose owner chose a cover is named by it, rather than counted.
        val privateCovers = remember(summaries, trash) {
            summaries.orEmpty().filter { it.metadata.isPrivate }.mapNotNull { map -> map.metadata.cover?.let { map.title to it } }.toMap() +
                trash.filter { it.isPrivate }.mapNotNull { map -> map.cover?.let { map.title to it } }
        }

        fun quoted(title: String): String = resources.getString(R.string.settings_quoted_map_title, title)

        fun hiddenMap(title: String): Boolean = privacy.locked && (title in notes.private || title in privateTitles)

        fun coveredOrQuoted(title: String): String = quoted(privateCovers[title]?.takeIf { hiddenMap(title) }?.let(resources::coverTitle) ?: title)

        fun hiddenFile(entry: String): Boolean = privacy.locked && (entry in notes.private || privateTitles.any { entry.endsWith(quoted(it)) })

        // A file too big to be a map is named by its file, which a private map's would give away.
        fun hiddenLarge(file: String): Boolean = privacy.locked && file in notes.private

        // The maps a note is about: “Trip”, “Groceries”, and while they're locked, “📓 Journal” and 2 private maps.
        fun titles(maps: List<String>): String =
            noteEntries(maps, { hiddenMap(it) && it !in privateCovers }, ::coveredOrQuoted) { count -> resources.getQuantityString(R.plurals.private_maps_in_a_note, count, count) }

        // While private maps hide, the notes about them aren't there at all, rather than counted.
        val concealing = privacy.conceals(LocalAppSettings.current.hideLockedPrivateMaps)
        val shown = notes.shown(concealing, ::hiddenMap, ::hiddenFile, ::hiddenLarge)
        // The maps an older device in the folder keeps only part of, as the notes name maps.
        val partly = sync.partly.mapValues { (_, maps) -> maps.filterNot { concealing && hiddenMap(it) } }.filterValues { it.isNotEmpty() }
        // Unlocked, the notes name private maps, their pictures and videos: Recents gets no picture of Settings then, as of the maps.
        // Worked out locked or not: Compose remembers `::quoted` where it's written, with no group of its own after an `&&`, so
        // skipping it as they lock put what Settings remembered after it out of step, and Orbit closed.
        val namesPrivateMaps = shown.namesPrivateMaps(notes.private, privateTitles, ::quoted) || partly.values.flatten().any { it in privateTitles }
        PrivateOnScreen(!privacy.locked && namesPrivateMaps)

        if (shown.keptBoth.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_kept_both, shown.keptBoth.size, titles(shown.keptBoth)))
        }

        if (shown.removed.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_removed, shown.removed.size, titles(shown.removed)))
        }

        if (shown.restored.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_restored, shown.restored.size, titles(shown.restored)))
        }

        if (shown.tooBig.isNotEmpty()) {
            Note(
                pluralStringResource(
                    R.plurals.settings_folder_too_big,
                    shown.tooBig.size,
                    noteEntries(shown.tooBig, ::hiddenFile, { it }, separator = "; ") { count ->
                        resources.getQuantityString(R.plurals.private_files_in_a_note, count, count)
                    },
                    readableSize(MEDIA_FILE_LIMIT, words),
                    readableSize(MAP_MEDIA_LIMIT, words),
                ),
            )
        }

        if (shown.large.isNotEmpty()) {
            Note(
                pluralStringResource(
                    R.plurals.settings_folder_too_large,
                    shown.large.size,
                    noteEntries(shown.large, ::hiddenLarge, ::quoted) { count -> resources.getQuantityString(R.plurals.private_files_in_a_note, count, count) },
                    readableSize(MAP_TEXT_LIMIT, words),
                ),
            )
        }

        if (shown.newer.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_newer_format, shown.newer.size, titles(shown.newer)))
        }

        if (shown.cameInPrivate.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_came_in_private, shown.cameInPrivate.size, titles(shown.cameInPrivate)))
        }

        if (!shown.isEmpty) {
            // Only what shows is read: while private maps hide, the notes about them stay, and their maps wait in the trash.
            TextButton(
                onClick = { folderSync.dismissNotes(shown, hidden = { concealing && it.isPrivate && privacy.hides(it.id) }) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.settings_got_it))
            }
        }

        FolderDevices(sync, now) { device ->
            partly[device.id]?.let { maps ->
                resources.getQuantityString(R.plurals.folder_devices_keeps_part, maps.size, titles(maps), readableSize(device.keepsFile, words), readableSize(device.keepsMapMedia, words))
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            OutlinedButton(onClick = choose) { Text(stringResource(R.string.settings_change_folder)) }
            TextButton(onClick = ::stop) { Text(stringResource(R.string.settings_stop_keeping_maps_there)) }
        }
    }

    pickProblem?.let { Note(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** How the folder is doing, in a line. */
private fun status(resources: Resources, sync: FolderSyncState, now: Long): String {
    val synced = sync.synced

    return when {
        sync.syncing && synced == null -> resources.getString(R.string.settings_folder_syncing)
        sync.problem != null -> sync.problem
        synced != null -> {
            val checked = timeAgo(resources, synced, now)
            resources.getString(if (checked.isDate) R.string.settings_folder_in_step_on_date else R.string.settings_folder_in_step, checked.text)
        }
        else -> resources.getString(R.string.settings_folder_checking)
    }
}

/**
 * Whether these notes name a private map, one of `privateTitles` or of the
 * notes' own `private` ones, or a picture or video too big for one, which
 * an entry gives `quoted` after its name.
 */
internal fun FolderNotes.namesPrivateMaps(private: List<String>, privateTitles: Set<String>, quoted: (String) -> String): Boolean {
    fun privateMap(title: String): Boolean = title in private || title in privateTitles

    return (keptBoth + removed + restored + cameInPrivate).any(::privateMap) ||
        tooBig.any { entry -> entry in private || privateTitles.any { entry.endsWith(quoted(it)) } } ||
        large.any { it in private }
}

/**
 * The notes as Settings shows them: while private maps hide (`concealing`), without the entries about
 * them (`hiddenMap` for maps, `hiddenFile` for pictures and videos too big for one), so a note only
 * about them isn't there at all. Otherwise, all of them.
 */
internal fun FolderNotes.shown(concealing: Boolean, hiddenMap: (String) -> Boolean, hiddenFile: (String) -> Boolean, hiddenLarge: (String) -> Boolean = { false }): FolderNotes =
    if (concealing) {
        copy(
            keptBoth = keptBoth.filterNot(hiddenMap),
            removed = removed.filterNot(hiddenMap),
            restored = restored.filterNot(hiddenMap),
            tooBig = tooBig.filterNot(hiddenFile),
            large = large.filterNot(hiddenLarge),
            newer = newer.filterNot(hiddenMap),
            cameInPrivate = cameInPrivate.filterNot(hiddenMap),
        )
    } else {
        this
    }

/**
 * What a folder note lists: each entry `named`, but the ones `hidden` (a
 * locked private map's, and its pictures and videos) only `counted`, together,
 * at the end. The entries are joined by `separator`, the last as `last` puts
 * it after the others.
 */
internal fun <T> noteEntries(
    entries: List<T>,
    hidden: (T) -> Boolean,
    named: (T) -> String,
    separator: String = ", ",
    last: (String, String) -> String = { before, end -> before + separator + end },
    counted: (Int) -> String,
): String {
    val privateCount = entries.count(hidden)
    val names = entries.filterNot(hidden).map(named) + listOfNotNull(privateCount.takeIf { it > 0 }?.let(counted))
    return if (names.size < 2) names.joinToString(separator) else last(names.dropLast(1).joinToString(separator), names.last())
}
