package com.hyuchia.orbit.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.Release
import com.hyuchia.orbit.data.UpdateState
import com.hyuchia.orbit.ui.NoticeButton
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.NoticeTextButton
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

    NoticeCard(
        icon = Icons.Outlined.SystemUpdate,
        title = stringResource(R.string.home_update_title, release.version),
        modifier = modifier,
        body = { Text(stringResource(R.string.home_update_body)) },
    ) {
        NoticeButton(stringResource(R.string.home_update_download), onClick = { openLink(context, release.page) })
        NoticeTextButton(stringResource(R.string.home_update_not_now), onClick = { container.applicationScope.launch { container.updates.dismiss(release.version) } })
    }
}
