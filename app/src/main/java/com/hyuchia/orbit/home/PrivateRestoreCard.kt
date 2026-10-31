package com.hyuchia.orbit.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.privateMapsLeftOutOfRestore
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.NoticeTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many private maps the backup this phone was restored from left out, until Home has said so (0 once it has). */
@Composable
fun rememberPrivateMapsLeftOutOfRestore(): MutableIntState {
    val files = LocalContext.current.filesDir
    val count = remember { mutableIntStateOf(0) }

    LaunchedEffect(files) {
        count.intValue = withContext(Dispatchers.IO) { privateMapsLeftOutOfRestore(files) }
    }

    return count
}

/**
 * Says, once, after a restore from a backup that left private maps out (one
 * that isn't end-to-end encrypted, see `takesPrivateMaps`), how many it left
 * out, and how they come back: from the folder the maps are kept in
 * (`withFolder`), as ordinary maps to make private again, or else from Export
 * all maps, still private.
 */
@Composable
fun PrivateRestoreCard(count: Int, withFolder: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    NoticeCard(
        icon = Icons.Outlined.Lock,
        title = pluralStringResource(R.plurals.home_private_maps_not_restored_title, count, count),
        modifier = modifier,
        body = {
            Text(stringResource(if (withFolder) R.string.home_private_maps_not_restored_folder else R.string.home_private_maps_not_restored_export))
        },
    ) {
        NoticeTextButton(stringResource(R.string.settings_got_it), onClick = onDismiss)
    }
}
