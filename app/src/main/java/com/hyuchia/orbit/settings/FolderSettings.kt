package com.hyuchia.orbit.settings

import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
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
import com.hyuchia.orbit.data.FolderSyncState
import com.hyuchia.orbit.data.MAP_MEDIA_LIMIT
import com.hyuchia.orbit.data.VIDEO_SIZE_LIMIT
import com.hyuchia.orbit.data.megabytes
import com.hyuchia.orbit.home.timeAgo
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A folder just chosen that has markdown files already, waiting for a yes before they become maps. */
private class FolderOffer(val tree: Uri, val name: String, val files: Int)

/**
 * Keeping the maps in a folder: choosing one (its markdown files become
 * maps, after asking), how it's going, and stopping.
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
    var offer by remember { mutableStateOf<FolderOffer?>(null) }
    var pickProblem by remember { mutableStateOf<String?>(null) }

    fun keep(tree: Uri) {
        val resolver = context.contentResolver

        if (runCatching { DocumentFolder.keepAccess(resolver, tree) }.isFailure) {
            pickProblem = resources.getString(R.string.settings_folder_access_refused)
            return
        }

        val previous = sync.folder

        scope.launch {
            container.settings.update { it.copy(syncFolder = tree.toString()) }

            if (previous != null && previous != tree.toString()) {
                DocumentFolder.releaseAccess(resolver, previous)
            }
        }
    }

    fun stop() {
        val folder = sync.folder ?: return

        scope.launch {
            container.settings.update { it.copy(syncFolder = null) }
            DocumentFolder.releaseAccess(context.contentResolver, folder)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        pickProblem = null

        when {
            tree == null -> Unit
            tree.toString() == sync.folder -> folderSync.request()
            else -> scope.launch {
                val inspected = runCatching { folderSync.inspect(tree.toString()) }.getOrNull()

                when {
                    inspected == null -> pickProblem = resources.getString(R.string.settings_folder_could_not_open)
                    inspected.second == 0 -> keep(tree)
                    else -> offer = FolderOffer(tree, inspected.first, inspected.second)
                }
            }
        }
    }

    val folder = sync.folder

    if (folder == null) {
        ListItem(
            onClick = { picker.launch(null) },
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
        // The maps a note is about: “Trip”, “Groceries”.
        fun titles(maps: List<String>): String = maps.joinToString { resources.getString(R.string.settings_quoted_map_title, it) }

        if (notes.keptBoth.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_kept_both, notes.keptBoth.size, titles(notes.keptBoth)))
        }

        if (notes.removed.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_removed, notes.removed.size, titles(notes.removed)))
        }

        if (notes.restored.isNotEmpty()) {
            Note(pluralStringResource(R.plurals.settings_folder_restored, notes.restored.size, titles(notes.restored)))
        }

        if (notes.tooBig.isNotEmpty()) {
            Note(
                pluralStringResource(
                    R.plurals.settings_folder_too_big,
                    notes.tooBig.size,
                    notes.tooBig.joinToString("; "),
                    megabytes(VIDEO_SIZE_LIMIT, words),
                    megabytes(MAP_MEDIA_LIMIT, words),
                ),
            )
        }

        if (!notes.isEmpty) {
            TextButton(onClick = folderSync::dismissNotes, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.settings_got_it)) }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            OutlinedButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.settings_change_folder)) }
            TextButton(onClick = ::stop) { Text(stringResource(R.string.settings_stop_keeping_maps_there)) }
        }
    }

    pickProblem?.let { Note(it, color = MaterialTheme.colorScheme.error) }

    offer?.let { chosen ->
        AlertDialog(
            onDismissRequest = { offer = null },
            title = {
                EscapeCloses { offer = null }
                Text(stringResource(R.string.settings_keep_maps_in_named_folder, chosen.name))
            },
            text = {
                Text(pluralStringResource(R.plurals.settings_folder_has_markdown_files, chosen.files, chosen.files))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        keep(chosen.tree)
                        offer = null
                    },
                ) { Text(stringResource(R.string.settings_keep_maps_here)) }
            },
            dismissButton = { TextButton(onClick = { offer = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
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
