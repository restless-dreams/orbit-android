package com.hyuchia.orbit.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapTags
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.ListRow

/**
 * The tags on the maps, as chips: tapping one shows only its maps, and
 * tapping it again shows them all. Edit tags, at the end, renames or removes
 * them. `shown` is the tag whose maps show, if one is.
 */
@Composable
fun TagFilter(tags: List<TagCount>, shown: String?, onShow: (String?) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val showOnly = stringResource(R.string.home_show_only_its_maps)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp), modifier = modifier) {
        items(tags, key = { MapTags.key(it.name) }) { tag ->
            val on = shown != null && MapTags.key(shown) == MapTags.key(tag.name)

            FilterChip(
                selected = on,
                onClick = { onShow(if (on) null else tag.name) },
                label = { Text(tag.name) },
                leadingIcon = if (on) ({ Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                modifier = if (on) Modifier else Modifier.semantics { onClick(label = showOnly, action = null) },
            )
        }

        item(key = "edit") {
            AssistChip(
                onClick = onEdit,
                label = { Text(stringResource(R.string.home_edit_tags)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

/** Every tag, with how many maps have it, to rename or take out of every map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsSheet(tags: List<TagCount>, onRename: (String, String) -> Unit, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf<TagCount?>(null) }
    var removing by remember { mutableStateOf<TagCount?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        EscapeCloses(onDismiss)

        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.home_edit_tags), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
            }

            tags.forEach { tag ->
                ListRow(
                    headline = { Text(tag.name) },
                    supporting = { Text(pluralStringResource(R.plurals.home_map_count, tag.count, tag.count)) },
                    trailing = {
                        Row {
                            IconButton(onClick = { renaming = tag }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.home_rename_tag)) }
                            IconButton(onClick = { removing = tag }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.home_remove_tag)) }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    renaming?.let { tag ->
        TitleDialog(
            heading = stringResource(R.string.home_rename_tag),
            confirm = stringResource(R.string.common_rename),
            initial = tag.name,
            label = stringResource(R.string.home_tag),
            onConfirm = { name ->
                renaming = null

                if (name != tag.name) {
                    onRename(tag.name, name)
                }
            },
            onDismiss = { renaming = null },
        )
    }

    removing?.let { tag ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = {
                EscapeCloses { removing = null }
                Text(pluralStringResource(R.plurals.home_remove_tag_question, tag.count, tag.name, tag.count))
            },
            text = { Text(stringResource(R.string.home_remove_tag_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        onRemove(tag.name)
                    },
                ) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
