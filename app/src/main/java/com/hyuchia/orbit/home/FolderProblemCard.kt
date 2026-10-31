package com.hyuchia.orbit.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.NoticeButton
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.NoticeTextButton

/**
 * Says the folder the maps are kept in has had a problem for more than one
 * sync in a row (see [LASTING_FOLDER_FAILURES]): what it is, as Settings
 * says it, with a way to try again and to Settings. It goes once a sync
 * goes well.
 */
@Composable
fun FolderProblemCard(problem: String, onTryAgain: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    NoticeCard(
        icon = Icons.Outlined.SyncProblem,
        title = stringResource(R.string.home_folder_problem_title),
        modifier = modifier,
        problem = true,
        body = { Text(problem) },
    ) {
        NoticeButton(stringResource(R.string.home_folder_problem_try_again), onClick = onTryAgain, problem = true)
        NoticeTextButton(stringResource(R.string.common_settings), onClick = onSettings, problem = true)
    }
}

/** How many syncs in a row have to have a problem for Home to show it: one alone, as a provider's hiccup, doesn't. */
const val LASTING_FOLDER_FAILURES = 2
