package com.hyuchia.orbit.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.settings.rememberFolderChooser
import com.hyuchia.orbit.ui.NoticeButton
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.NoticeTextButton
import com.hyuchia.orbit.ui.thisDeviceIcon

/**
 * Says, once, that the maps stay on this device: Orbit has no account or
 * cloud, and only a folder a sync app keeps in step takes them elsewhere.
 * It goes once the person chooses a folder or says they've read it.
 * `onProblem` hears why a folder couldn't be used.
 */
@Composable
fun StorageCard(onDismiss: () -> Unit, onProblem: (String) -> Unit, modifier: Modifier = Modifier) {
    val choose = rememberFolderChooser { problem -> problem?.let(onProblem) }

    NoticeCard(
        icon = thisDeviceIcon(),
        title = stringResource(R.string.sharing_maps_stay_here_title),
        modifier = modifier,
        body = { Text(stringResource(R.string.sharing_maps_stay_here_body)) },
    ) {
        NoticeButton(stringResource(R.string.sharing_choose_a_folder), onClick = choose, icon = Icons.Outlined.CreateNewFolder)
        NoticeTextButton(stringResource(R.string.settings_got_it), onClick = onDismiss)
    }
}
