package com.hyuchia.orbit.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.DocumentFolder
import com.hyuchia.orbit.privacy.rememberUnlockedForEveryMap
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.rememberDefaultButton
import kotlinx.coroutines.launch

/** A folder just chosen that has markdown files already, waiting for a yes before they become maps. */
private class FolderOffer(val tree: Uri, val name: String, val files: Int)

/**
 * Choosing the folder the maps are kept in, from Settings or Home's card:
 * Android's folder picker, then, for a folder with markdown files in it
 * already, a yes before they become maps. Returns what opens the picker;
 * `onProblem` hears why a folder couldn't be used, and null as the next
 * one is picked.
 */
@Composable
internal fun rememberFolderChooser(onProblem: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.container
    val folderSync = container.folderSync
    val sync by folderSync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val problem by rememberUpdatedState(onProblem)
    var offer by remember { mutableStateOf<FolderOffer?>(null) }
    val unlockedForEveryMap = rememberUnlockedForEveryMap { reason -> problem(reason) }

    fun keep(tree: Uri) {
        val resolver = context.contentResolver

        if (runCatching { DocumentFolder.keepAccess(resolver, tree) }.isFailure) {
            problem(resources.getString(R.string.settings_folder_access_refused))
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        problem(null)

        when {
            tree == null -> Unit
            tree.toString() == sync.folder -> {
                // Chosen again, as after a restore or a revoked permission: its access is kept again too, so it lasts past a restart.
                runCatching { DocumentFolder.keepAccess(context.contentResolver, tree) }
                folderSync.request()
            }
            else -> scope.launch {
                val inspected = runCatching { folderSync.inspect(tree.toString()) }.getOrNull()

                when {
                    inspected == null -> problem(resources.getString(R.string.settings_folder_could_not_open))
                    inspected.second == 0 -> keep(tree)
                    else -> offer = FolderOffer(tree, inspected.first, inspected.second)
                }
            }
        }
    }

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
                    modifier = Modifier.focusRequester(rememberDefaultButton()),
                ) { Text(stringResource(R.string.settings_keep_maps_here)) }
            },
            dismissButton = { TextButton(onClick = { offer = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // The folder gets every map, private ones as plain text: while one is locked, the phone's owner says so first.
    return { unlockedForEveryMap { picker.launch(null) } }
}
