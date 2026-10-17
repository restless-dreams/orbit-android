package com.hyuchia.orbit.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.hyuchia.orbit.data.RefusedZipException
import com.hyuchia.orbit.data.TRASH_DAYS
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/**
 * Keeping maps safe: the trash, and every map in one zip to keep anywhere
 * and bring back on any device (Android's own backup needs Google's, and
 * stops at 25 MB).
 */
@Composable
internal fun MapSafety(onTrash: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val maps = context.container.maps
    val trash by maps.trash.collectAsStateWithLifecycle()
    val writable by maps.writable.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            note = try {
                val count = context.contentResolver.openOutputStream(uri, "wt")?.use { maps.exportAll(it) } ?: throw IOException()
                resources.getQuantityString(R.plurals.settings_exported_maps, count, count) to false
            } catch (failed: IOException) {
                resources.getString(R.string.settings_could_not_write_file) to true
            } catch (failed: SecurityException) {
                resources.getString(R.string.settings_not_allowed_to_write) to true
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            note = try {
                val imported = context.contentResolver.openInputStream(uri)?.use { maps.importAll(it) } ?: throw IOException()

                when {
                    imported.added == 0 && imported.alreadyHere == 0 -> resources.getString(R.string.settings_no_maps_in_file) to true
                    imported.added == 0 -> resources.getString(R.string.settings_every_map_in_file_already_here) to false
                    imported.alreadyHere == 0 -> resources.getQuantityString(R.plurals.common_added_maps, imported.added, imported.added) to false
                    imported.alreadyHere == 1 ->
                        resources.getQuantityString(R.plurals.common_added_maps_with_one_already_here, imported.added, imported.added) to false
                    else ->
                        resources.getQuantityString(R.plurals.common_added_maps_with_several_already_here, imported.added, imported.added, imported.alreadyHere) to false
                }
            } catch (refused: RefusedZipException) {
                refused.message.orEmpty() to true
            } catch (failed: IOException) {
                val reason = failed.message

                val problem = if (reason != null) {
                    resources.getString(R.string.settings_could_not_read_file, reason)
                } else {
                    resources.getString(R.string.settings_could_not_read_file_not_zip)
                }

                problem to true
            } catch (failed: SecurityException) {
                resources.getString(R.string.common_not_allowed_to_read_that_file) to true
            }
        }
    }

    if (!writable) {
        Note(
            stringResource(R.string.settings_newer_version_keeps_maps),
            color = MaterialTheme.colorScheme.error,
        )
    }

    ListItem(
        onClick = onTrash,
        supportingContent = {
            Text(
                if (trash.isEmpty()) {
                    pluralStringResource(R.plurals.settings_trash_wait_there, TRASH_DAYS, TRASH_DAYS)
                } else {
                    pluralStringResource(
                        R.plurals.settings_trash_maps_kept,
                        trash.size,
                        trash.size,
                        pluralStringResource(R.plurals.settings_day_count, TRASH_DAYS, TRASH_DAYS),
                    )
                },
            )
        },
        leadingContent = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.settings_trash))
    }

    ListItem(
        // The name the file picker offers, in the app's language; the extension isn't translated.
        onClick = { exporter.launch("${resources.getString(R.string.settings_all_maps_file_name, LocalDate.now().toString())}.zip") },
        supportingContent = { Text(stringResource(R.string.settings_export_all_maps_description)) },
        leadingContent = { Icon(Icons.Outlined.Archive, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.settings_export_all_maps))
    }

    ListItem(
        onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) },
        supportingContent = { Text(stringResource(R.string.settings_import_maps_description)) },
        leadingContent = { Icon(Icons.Outlined.Unarchive, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.settings_import_maps))
    }

    note?.let { (text, problem) -> Note(text, color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified) }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}
