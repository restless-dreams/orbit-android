package com.hyuchia.orbit.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.mapsLeftOutOfBackup
import com.hyuchia.orbit.ui.thisDeviceIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the maps are kept, said plainly: on this device, and in the folder
 * they're kept in when there's one. Orbit has no account or cloud of its
 * own, so nothing else takes them anywhere.
 */
@Composable
internal fun WhereMapsAre() {
    val context = LocalContext.current
    val sync by context.container.folderSync.state.collectAsStateWithLifecycle()
    val inFolder = sync.folder != null
    // Android's last backup to the cloud, when it had no room for some maps.
    val leftOut by produceState(0) { value = withContext(Dispatchers.IO) { mapsLeftOutOfBackup(context.filesDir) } }

    ListItem(
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (inFolder) R.string.sharing_where_in_folder_description else R.string.sharing_where_on_this_device_description))

                if (leftOut > 0) {
                    Text(pluralStringResource(R.plurals.sharing_backup_left_out, leftOut, leftOut), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        leadingContent = { Icon(if (inFolder) Icons.Outlined.Sync else thisDeviceIcon(), contentDescription = null) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            if (inFolder) {
                stringResource(R.string.sharing_where_in_folder, sync.name ?: stringResource(R.string.settings_your_folder))
            } else {
                stringResource(R.string.sharing_where_on_this_device)
            },
        )
    }
}
