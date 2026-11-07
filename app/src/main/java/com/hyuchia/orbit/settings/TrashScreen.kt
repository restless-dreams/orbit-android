package com.hyuchia.orbit.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.TRASH_DAYS
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.home.timeAgo
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.PrivateView
import com.hyuchia.orbit.privacy.ShowPrivateMapsButton
import com.hyuchia.orbit.privacy.WhenPrivateMapsLock
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.coverTitle
import com.hyuchia.orbit.privacy.rememberUnlocking
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.rememberDefaultButton
import kotlinx.coroutines.launch

private const val DAY = 24 * 60 * 60 * 1000L

/**
 * The trash as it shows now: every deleted map, but private ones while Settings hides private maps
 * and they're locked.
 */
internal fun shownTrash(trash: List<TrashedMap>, privacy: PrivateView, hiding: Boolean): List<TrashedMap> =
    if (privacy.conceals(hiding)) trash.filterNot { it.isPrivate && privacy.hides(it.id) } else trash

/**
 * Deleted maps, kept with their history for [TRASH_DAYS] days: each can be
 * restored, or deleted for good.
 */
@Composable
fun TrashScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val maps = context.container.maps
    val trash by maps.trash.collectAsStateWithLifecycle()
    // While private maps are locked, the trash doesn't say their names; while they hide, it leaves them out.
    val privacy by context.container.privateMaps.view.collectAsStateWithLifecycle()
    val hiding = LocalAppSettings.current.hideLockedPrivateMaps
    val items = shownTrash(trash, privacy, hiding)
    val unlocking = rememberUnlocking()

    // A locked private map shows its cover, or "Private map", in place of its name.
    fun TrashedMap.shownTitle(): String = if (isPrivate && privacy.hides(id)) resources.coverTitle(cover) else title

    PrivateOnScreen(items.any { it.isPrivate && !privacy.hides(it.id) })
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var emptying by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TrashedMap?>(null) }
    // "Restored" names a private map while it's unlocked: the message goes when they lock.
    WhenPrivateMapsLock { snackbars.currentSnackbarData?.dismiss() }

    fun restore(item: TrashedMap) {
        scope.launch {
            val id = maps.restore(item)
            snackbars.currentSnackbarData?.dismiss()

            if (id == null) {
                snackbars.showSnackbar(resources.getString(R.string.settings_could_not_restore, item.shownTitle()))
                return@launch
            }

            val result = snackbars.showSnackbar(
                resources.getString(R.string.settings_restored, item.shownTitle()),
                actionLabel = resources.getString(R.string.common_open),
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed) {
                onOpen(id)
            }
        }
    }

    fun say(text: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        snackbars.currentSnackbarData?.dismiss()
        scope.launch { snackbars.showSnackbar(text, duration = duration) }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_trash),
        onBack = onBack,
        snackbars = snackbars,
        actions = {
            // While private maps hide, the way back to them, there whether any is in the trash or not.
            if (privacy.conceals(hiding)) {
                ShowPrivateMapsButton(onShow = { unlocking.ask() })
            }

            if (items.isNotEmpty()) {
                TextButton(onClick = { emptying = true }) { Text(stringResource(R.string.settings_empty_trash)) }
            }
        },
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

            return@SettingsScaffold
        }

        val now = remember(items) { System.currentTimeMillis() }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // On a tablet, the list keeps to a column, as Settings does.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = ReadingWidth),
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
                val locked = item.isPrivate && privacy.hides(item.id)

                TrashRow(
                    title = item.shownTitle(),
                    summary = describe(resources, item, now, locked),
                    lock = if (item.isPrivate) {
                        if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen
                    } else {
                        null
                    },
                    onRestore = { restore(item) },
                    onDelete = { deleting = item },
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
                    // What the trash shows goes; private maps it leaves out while they hide wait for their own time.
                    val shown = items.mapTo(HashSet()) { it.folder.name }

                    scope.launch {
                        val stayed = maps.emptyTrash(keeping = { it.folder.name !in shown })
                        // Only what went is counted: nothing does while a newer version keeps the maps.
                        val gone = shown.count { name -> maps.trash.value.none { it.folder.name == name } }

                        // Maps whose files didn't all delete are still there, and that's what's said: never that they went.
                        if (stayed > 0) {
                            say(resources.getQuantityString(R.plurals.settings_trash_not_emptied, stayed, stayed), SnackbarDuration.Long)
                        } else if (gone > 0) {
                            say(resources.getQuantityString(R.plurals.settings_trash_emptied, gone, gone))
                        }
                    }
                }) { Text(stringResource(R.string.settings_empty_trash)) }
            },
            // Enter keeps the maps, here and for one map below: neither can be undone.
            dismissButton = { TextButton(onClick = { emptying = false }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                EscapeCloses { deleting = null }
                Text(stringResource(R.string.settings_delete_named_for_good_question, item.shownTitle()))
            },
            text = { Text(stringResource(R.string.settings_delete_for_good_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null

                    scope.launch {
                        // Nothing goes while a newer version keeps the maps, and nothing's said; a map whose files didn't all delete is still there, and that's said.
                        if (maps.deleteForever(item)) {
                            say(resources.getString(R.string.settings_deleted_for_good, item.shownTitle()))
                        } else if (maps.writable.value) {
                            say(resources.getString(R.string.settings_could_not_delete_for_good, item.shownTitle()), SnackbarDuration.Long)
                        }
                    }
                }) { Text(stringResource(R.string.settings_delete_for_good)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** How wide a trash row has to be for its buttons to sit beside its words without squeezing them. */
private val ButtonsBeside = 640.dp

/**
 * A map in the trash: its name and what's left of its time there, with
 * Restore and Delete for good beside them where there's room, or under
 * them on a phone. TalkBack hears which map each button acts on.
 */
@Composable
private fun TrashRow(title: String, summary: String, lock: ImageVector?, onRestore: () -> Unit, onDelete: () -> Unit) {
    val restoreIt = stringResource(R.string.settings_restore_named, title)
    val deleteIt = stringResource(R.string.settings_delete_named_for_good, title)

    val buttons = @Composable {
        TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteIt }) {
            Text(stringResource(R.string.settings_delete_for_good), modifier = Modifier.clearAndSetSemantics {})
        }

        FilledTonalButton(
            onClick = onRestore,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.semantics { contentDescription = restoreIt },
        ) {
            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.common_restore), modifier = Modifier.clearAndSetSemantics {})
        }
    }

    BoxWithConstraints(Modifier.padding(horizontal = 8.dp)) {
        val beside = maxWidth >= ButtonsBeside

        Column {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(summary) },
                leadingContent = lock?.let { icon -> { Icon(icon, contentDescription = stringResource(R.string.private_badge)) } },
                trailingContent = if (beside) {
                    { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons() } }
                } else {
                    null
                },
            )

            if (!beside) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    buttons()
                }
            }
        }
    }
}

/**
 * "12 ideas · deleted 2 days ago · 28 days left", or "Orbit can't read it · …"
 * for a map whose file it can't read. A locked private map says only when
 * it goes, even when its file can't be read: nothing about what it holds.
 */
private fun describe(resources: Resources, item: TrashedMap, now: Long, locked: Boolean): String {
    val deleted = timeAgo(resources, item.deleted, now)
    val left = (TRASH_DAYS - ((now - item.deleted) / DAY).toInt()).coerceAtLeast(1)
    val daysLeft = resources.getQuantityString(R.plurals.settings_trash_days_left, left, left)

    // A locked private map says nothing of what it holds, as its card on Home doesn't: only when it goes.
    if (locked) {
        return resources.getString(if (deleted.isDate) R.string.settings_trash_private_summary_on_date else R.string.settings_trash_private_summary, deleted.text, daysLeft)
    }

    if (item.unreadable) {
        val unreadable = if (deleted.isDate) R.string.settings_trash_unreadable_summary_on_date else R.string.settings_trash_unreadable_summary
        return resources.getString(unreadable, deleted.text, daysLeft)
    }

    val summary = if (deleted.isDate) R.plurals.settings_trash_map_summary_on_date else R.plurals.settings_trash_map_summary
    return resources.getQuantityString(summary, item.ideas, item.ideas, deleted.text, daysLeft)
}
