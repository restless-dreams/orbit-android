package com.hyuchia.orbit.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R

/**
 * Asks about the maps whose files left the folder all in one sync, more
 * than a sync sends to the trash on its own: they follow their files into
 * the trash, or stay and are written back. `maps` names them as the folder's
 * notes do (a locked private map only counted), and there are `count`.
 */
@Composable
fun FolderRemovalsCard(maps: String, count: Int, folder: String, onKeep: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    FolderCard(
        icon = Icons.Outlined.FolderOff,
        title = pluralStringResource(R.plurals.home_folder_files_gone_title, count, count),
        body = pluralStringResource(R.plurals.home_folder_files_gone, count, maps, folder),
        modifier = modifier,
    ) {
        FilledTonalButton(onClick = onKeep) { Text(stringResource(R.string.home_folder_files_gone_keep)) }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.home_folder_files_gone_remove)) }
    }
}

/**
 * Says which maps (`maps`, `count` of them) the folder sent to the trash as
 * their files were deleted from it, with the way to the trash: they stay
 * there until this is read.
 */
@Composable
fun FolderRemovedCard(maps: String, count: Int, folder: String, onOpenTrash: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    FolderCard(
        icon = Icons.Outlined.RestoreFromTrash,
        title = pluralStringResource(R.plurals.home_folder_removed_title, count, count),
        body = pluralStringResource(R.plurals.home_folder_removed, count, maps, folder),
        modifier = modifier,
    ) {
        FilledTonalButton(onClick = onOpenTrash) { Text(stringResource(R.string.home_folder_open_trash)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_got_it)) }
    }
}

@Composable
private fun FolderCard(icon: ImageVector, title: String, body: String, modifier: Modifier, buttons: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            }

            Text(body, style = MaterialTheme.typography.bodyMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                buttons()
            }
        }
    }
}
