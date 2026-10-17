package com.hyuchia.orbit.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.UpdateState
import com.hyuchia.orbit.ui.openLink
import kotlinx.coroutines.launch

/**
 * About's row for new versions, in the copies no store updates: what the
 * last look found. Tapped, it opens a newer version's download page, or
 * looks again now.
 */
@Composable
fun UpdatesItem(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updates = context.container.updates
    val scope = rememberCoroutineScope()
    val state by updates.state.collectAsStateWithLifecycle(UpdateState())
    var checking by remember { mutableStateOf(false) }
    val newer = updates.newer(state)

    val status = when {
        checking -> stringResource(R.string.settings_updates_checking)
        newer != null -> stringResource(R.string.settings_updates_available, newer.version)
        state.failed -> stringResource(R.string.settings_updates_failed)
        state.checkedAt == 0L -> stringResource(R.string.settings_updates_never)
        else -> stringResource(R.string.settings_updates_latest)
    }

    ListItem(
        onClick = {
            when {
                newer != null -> openLink(context, newer.page)

                !checking -> {
                    checking = true

                    scope.launch {
                        try {
                            updates.check()
                        } finally {
                            checking = false
                        }
                    }
                }
            }
        },
        supportingContent = { Text(status) },
        leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        modifier = modifier,
    ) {
        Text(stringResource(R.string.settings_updates))
    }
}
