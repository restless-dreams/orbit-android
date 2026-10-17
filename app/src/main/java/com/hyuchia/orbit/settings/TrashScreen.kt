package com.hyuchia.orbit.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.TRASH_DAYS
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.home.timeAgo
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import kotlinx.coroutines.launch

private const val DAY = 24 * 60 * 60 * 1000L

/**
 * Deleted maps, kept with their history for [TRASH_DAYS] days: each can be
 * restored, or deleted for good.
 */
@Composable
fun TrashScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val maps = context.container.maps
    val items by maps.trash.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var emptying by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TrashedMap?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun restore(item: TrashedMap) {
        scope.launch {
            val id = maps.restore(item)
            snackbars.currentSnackbarData?.dismiss()

            if (id == null) {
                snackbars.showSnackbar(resources.getString(R.string.settings_could_not_restore, item.title))
                return@launch
            }

            val result = snackbars.showSnackbar(
                resources.getString(R.string.settings_restored, item.title),
                actionLabel = resources.getString(R.string.common_open),
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed) {
                onOpen(id)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_trash)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { emptying = true }) { Text(stringResource(R.string.settings_empty_trash)) }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.settings_nothing_in_trash), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.settings_trash_empty_body, TRASH_DAYS, TRASH_DAYS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            return@Scaffold
        }

        val now = remember(items) { System.currentTimeMillis() }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // On a tablet, the list keeps to a column, as Settings does.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
        ) {
            item {
                Text(
                    pluralStringResource(R.plurals.settings_trash_explanation, TRASH_DAYS, TRASH_DAYS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            items(items, key = { it.folder.name }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(describe(resources, item, now)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val deleteForGood = stringResource(R.string.settings_delete_named_for_good, item.title)

                            Hint(deleteForGood) {
                                IconButton(onClick = { deleting = item }) {
                                    Icon(Icons.Outlined.DeleteForever, contentDescription = deleteForGood)
                                }
                            }

                            FilledTonalButton(onClick = { restore(item) }) {
                                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                                Text(stringResource(R.string.common_restore))
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    if (emptying) {
        AlertDialog(
            onDismissRequest = { emptying = false },
            title = {
                EscapeCloses { emptying = false }
                Text(stringResource(R.string.settings_empty_the_trash_question))
            },
            text = { Text(pluralStringResource(R.plurals.settings_empty_trash_body, items.size, items.size)) },
            confirmButton = {
                TextButton(onClick = {
                    emptying = false
                    scope.launch { maps.emptyTrash() }
                }) { Text(stringResource(R.string.settings_empty_trash)) }
            },
            dismissButton = { TextButton(onClick = { emptying = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                EscapeCloses { deleting = null }
                Text(stringResource(R.string.settings_delete_named_for_good_question, item.title))
            },
            text = { Text(stringResource(R.string.settings_delete_for_good_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { maps.deleteForever(item) }
                }) { Text(stringResource(R.string.settings_delete_for_good)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** "12 ideas · deleted 2 days ago · 28 days left" */
private fun describe(resources: Resources, item: TrashedMap, now: Long): String {
    val deleted = timeAgo(resources, item.deleted, now)
    val left = (TRASH_DAYS - ((now - item.deleted) / DAY).toInt()).coerceAtLeast(1)
    val daysLeft = resources.getQuantityString(R.plurals.settings_trash_days_left, left, left)
    val summary = if (deleted.isDate) R.plurals.settings_trash_map_summary_on_date else R.plurals.settings_trash_map_summary
    return resources.getQuantityString(summary, item.ideas, item.ideas, deleted.text, daysLeft)
}
