package com.hyuchia.orbit.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SdCardAlert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R

/**
 * What the device's storage couldn't take yet, a map's changes or study
 * mode's answers, over the map: `title`, then why and when they're saved
 * (`body`), with the way to the phone's storage settings (`storage`, when
 * it has them) and to try again now (`onTryAgain`, unless trying again
 * can't help). `compact`, where room is short (a phone on its side), it's
 * the title and Try again alone, in one row.
 */
@Composable
fun UnsavedNote(title: String, body: String, onTryAgain: (() -> Unit)?, modifier: Modifier = Modifier, storage: Boolean = true, compact: Boolean = false) {
    val context = LocalContext.current
    val settings = remember(storage) { if (storage) storageSettings(context) else null }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
        modifier = modifier,
    ) {
        if (compact) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Icon(Icons.Outlined.SdCardAlert, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(vertical = 10.dp))

                if (onTryAgain != null) {
                    TextButton(onClick = onTryAgain) { Text(stringResource(R.string.map_try_again)) }
                }
            }

            return@Card
        }

        // Without buttons under it, the note ends as far below its words as it starts above its title.
        val buttons = settings != null || onTryAgain != null

        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = if (buttons) 4.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SdCardAlert, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }

            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))

            // Side by side where they fit, and one above the other where they don't, as in Spanish: never a button in two lines.
            if (buttons) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (settings != null) {
                        TextButton(onClick = { runCatching { context.startActivity(settings) } }) { Text(stringResource(R.string.map_storage_settings)) }
                    }

                    if (onTryAgain != null) {
                        TextButton(onClick = onTryAgain) { Text(stringResource(R.string.map_try_again)) }
                    }
                }
            }
        }
    }
}

/** `value` while there is one, and after, the last one there was: what a note keeps saying as it fades away. */
@Composable
fun <T : Any> lastOf(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }

    if (value != null) {
        last[0] = value
    }

    @Suppress("UNCHECKED_CAST")
    return value ?: last[0] as T?
}

/** The phone's settings for its storage, where space is freed up, when it has them. */
private fun storageSettings(context: Context): Intent? =
    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .takeIf { it.resolveActivity(context.packageManager) != null }
