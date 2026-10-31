package com.hyuchia.orbit.privacy

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.COVER_NAME_LIMIT
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.ui.EmojiPickerSheet
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.hideShortcut
import kotlinx.coroutines.launch

/**
 * What a locked private map shows in words where its name would be: its
 * cover's emoji and name, the emoji before `privateMap` ("Private map")
 * without a name, or `privateMap` alone without a cover.
 */
fun coverTitle(cover: LockedCover?, privateMap: String): String =
    listOfNotNull(cover?.icon?.takeIf { it.isNotBlank() }, cover?.name?.takeIf { it.isNotBlank() } ?: privateMap).joinToString(" ")

/** What a locked private map shows in words where its name would be (see [coverTitle]). */
fun Resources.coverTitle(cover: LockedCover?): String = coverTitle(cover, getString(R.string.private_map))

/** What a locked private map shows in words where its name would be (see [coverTitle]). */
@Composable
fun coverTitle(cover: LockedCover?): String = coverTitle(cover, stringResource(R.string.private_map))

/**
 * "Cover while locked", beside the Private switch in a private map's menu:
 * the emoji and name the map shows instead of its own while it's locked.
 * Its emoji, once chosen, shows at the end.
 */
@Composable
fun CoverMenuItem(cover: LockedCover?, onClick: () -> Unit) {
    val icon = cover?.icon?.takeIf { it.isNotBlank() }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.private_cover_while_locked)) },
        leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
        trailingIcon = icon?.let { { Text(it, style = MaterialTheme.typography.titleMedium) } },
        onClick = onClick,
    )
}

/**
 * Chooses a private map's cover: an emoji, in the map's own lock as its
 * locked card shows it, and a short name, either or both. Nothing of the map
 * goes into it: its owner writes it. Save keeps it, also as the name of the
 * map's shortcut pinned to the home screen; Remove cover takes it away.
 */
@Composable
fun CoverDialog(map: MapSummary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val current = map.metadata.cover
    val look = remember(map.id) { lockedLook(map.id) }
    var icon by rememberSaveable { mutableStateOf(current?.icon) }
    var name by rememberSaveable { mutableStateOf(current?.name.orEmpty()) }
    var picking by remember { mutableStateOf(false) }

    fun save(cover: LockedCover?) {
        val kept = cover?.takeUnless(LockedCover::isEmpty)

        // Saved after the dialog has closed, so the menu and card it came from show the cover at once.
        container.applicationScope.launch {
            container.maps.setCover(map.id, kept)
            hideShortcut(context, map.id, context.resources.coverTitle(kept))
        }

        onDismiss()
    }

    fun chosen(): LockedCover = (current ?: LockedCover()).copy(icon = icon?.takeIf { it.isNotBlank() }, name = name.trim().ifBlank { null })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(stringResource(R.string.private_cover_while_locked))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.private_cover_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val choose = if (icon.isNullOrBlank()) {
                        stringResource(R.string.private_cover_choose_emoji)
                    } else {
                        stringResource(R.string.private_cover_change_emoji, icon.orEmpty())
                    }

                    // The map's own lock, as its locked card will show it, named as the field beside it is: a tap chooses its emoji.
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clickable(interactionSource = null, indication = ripple(bounded = false, radius = 36.dp)) { picking = true }
                                .clearAndSetSemantics {
                                    contentDescription = choose
                                    role = Role.Button
                                    onClick { picking = true; true }
                                },
                        ) {
                            LockEmblem(open = false, Modifier.fillMaxSize(), iconSize = 28.dp, look = look, icon = icon)
                        }

                        Text(
                            stringResource(R.string.private_cover_emoji),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(COVER_NAME_LIMIT) },
                        label = { Text(stringResource(R.string.private_cover_name)) },
                        placeholder = { Text(stringResource(R.string.private_cover_name_example)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save(chosen()) }),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(chosen()) }) { Text(stringResource(R.string.private_cover_save)) } },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { save(null) }) { Text(stringResource(R.string.private_cover_remove)) }
                }

                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )

    if (picking) {
        EmojiPickerSheet(
            title = stringResource(R.string.private_cover_emoji_title),
            hasIcon = !icon.isNullOrBlank(),
            onPick = { emoji ->
                picking = false
                icon = emoji
            },
            onDismiss = { picking = false },
        )
    }
}
