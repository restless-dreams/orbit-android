package com.hyuchia.orbit.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.Release
import com.hyuchia.orbit.data.UpdateState
import com.hyuchia.orbit.ui.openLink
import kotlinx.coroutines.launch

/**
 * The new version to tell people about, in the copies no store updates: the
 * home screen looks for one once a day, on the app's own scope so leaving
 * the screen doesn't cut a look short. Null while there's none.
 */
@Composable
fun rememberAnnouncedRelease(): Release? {
    val container = LocalContext.current.container
    val updates = container.updates

    // The builds that stores update never look.
    if (!updates.available) {
        return null
    }

    LaunchedEffect(updates) {
        container.applicationScope.launch { updates.checkIfDue() }
    }

    val state by updates.state.collectAsStateWithLifecycle(UpdateState())
    return updates.announced(state)
}

/** Says a new version of Orbit is out, with a button to its download page, or puts it away until the next one. */
@Composable
fun UpdateCard(release: Release, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.container

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Text(stringResource(R.string.home_update_title, release.version), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }

            Text(stringResource(R.string.home_update_body), style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { openLink(context, release.page) }) { Text(stringResource(R.string.home_update_download)) }

                TextButton(onClick = { container.applicationScope.launch { container.updates.dismiss(release.version) } }) {
                    Text(stringResource(R.string.home_update_not_now))
                }
            }
        }
    }
}
