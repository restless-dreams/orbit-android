package com.hyuchia.orbit.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.CrashLog
import com.hyuchia.orbit.data.CrashReport
import com.hyuchia.orbit.ui.NoticeButton
import com.hyuchia.orbit.ui.NoticeCard
import com.hyuchia.orbit.ui.NoticeTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The latest crash the home screen hasn't shown yet, read off the main thread: null once it's dealt with. */
@Composable
fun rememberUnseenCrash(): MutableState<CrashReport?> {
    val crashes = LocalContext.current.container.crashes
    val report = remember { mutableStateOf<CrashReport?>(null) }

    LaunchedEffect(crashes) {
        report.value = withContext(Dispatchers.IO) { runCatching { crashes.unseen() }.getOrNull() }
    }

    return report
}

/**
 * Says Orbit closed unexpectedly last time, once: its report can be shared
 * (with any app: it never leaves the phone otherwise) or deleted, or the
 * card put away. `onDone` hears when it's dealt with, and `say` what to
 * tell when it can't be, as a report that won't delete.
 */
@Composable
fun CrashCard(
    report: CrashReport,
    onDone: () -> Unit,
    say: (String) -> Unit,
    modifier: Modifier = Modifier,
    crashes: CrashLog = LocalContext.current.container.crashes,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.home_share_the_crash_report)
    val notDeleted = stringResource(R.string.home_crash_report_not_deleted)

    fun close(action: (CrashReport) -> Unit) {
        onDone()
        context.container.applicationScope.launch(Dispatchers.IO) { runCatching { action(report) } }
    }

    NoticeCard(
        icon = Icons.Outlined.BugReport,
        title = stringResource(R.string.home_crash_title),
        modifier = modifier,
        onDismiss = { close(crashes::markSeen) },
        body = { Text(stringResource(R.string.home_crash_body)) },
    ) {
        NoticeButton(
            stringResource(R.string.home_share_report),
            onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { runCatching { report.text() }.getOrNull() } ?: return@launch
                    // The subject stays in English, like the report: both are for whoever fixes Orbit.
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Orbit crash report")
                        .putExtra(Intent.EXTRA_TEXT, text)

                    try {
                        context.startActivity(Intent.createChooser(send, shareTitle))
                        close(crashes::markSeen)
                    } catch (missing: ActivityNotFoundException) {
                        // Nothing can take text: the report stays, and so does the card.
                    }
                }
            },
        )
        NoticeTextButton(
            stringResource(R.string.common_delete),
            onClick = {
                scope.launch {
                    // The card goes once the report has: one that won't delete stays, and so does the card, to try again.
                    if (withContext(Dispatchers.IO) { runCatching { crashes.delete(report) }.getOrDefault(false) }) {
                        onDone()
                    } else {
                        say(notDeleted)
                    }
                }
            },
        )
    }
}
