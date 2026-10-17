package com.hyuchia.orbit.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.CrashLog
import com.hyuchia.orbit.data.CrashReport
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
 * card put away. `onDone` hears when it's dealt with.
 */
@Composable
fun CrashCard(report: CrashReport, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val crashes: CrashLog = context.container.crashes
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.home_share_the_crash_report)

    fun close(action: (CrashReport) -> Unit) {
        onDone()
        context.container.applicationScope.launch(Dispatchers.IO) { runCatching { action(report) } }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, contentDescription = null)
                Text(stringResource(R.string.home_crash_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))

                IconButton(onClick = { close(crashes::markSeen) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_dismiss))
                }
            }

            Text(
                stringResource(R.string.home_crash_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
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
                ) { Text(stringResource(R.string.home_share_report)) }
                TextButton(onClick = { close(crashes::delete) }) { Text(stringResource(R.string.common_delete)) }
            }
        }
    }
}
