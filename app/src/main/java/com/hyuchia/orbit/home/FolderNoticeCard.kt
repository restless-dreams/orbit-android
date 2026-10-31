package com.hyuchia.orbit.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.FolderNotice
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.NoticeTextButton

/**
 * Says, once, what another device that keeps its maps in the same folder
 * means here, when it runs another version of Orbit and that matters: files
 * one of them can't open, or maps one of them can only show.
 */
@Composable
fun FolderNoticeCard(notice: FolderNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val words = rememberWords()

    val (title, body) = when (notice) {
        is FolderNotice.Newer -> stringResource(R.string.folder_notice_newer_title, notice.device.name) to listOfNotNull(
            notice.unopened.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.folder_notice_newer_files, MediaWords.types(it, words)) },
            stringResource(R.string.folder_notice_newer_format).takeIf { notice.newerFormat },
        )
        is FolderNotice.Older -> stringResource(R.string.folder_notice_older_title, notice.device.name) to listOfNotNull(
            notice.unopened.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.folder_notice_older_files, MediaWords.types(it, words)) },
            stringResource(R.string.folder_notice_older_format).takeIf { notice.showsOnly },
        )
    }

    NoticeCard(
        icon = Icons.Outlined.Devices,
        title = title,
        modifier = modifier,
        body = { body.forEach { line -> Text(line) } },
    ) {
        NoticeTextButton(stringResource(R.string.settings_got_it), onClick = onDismiss)
    }
}
