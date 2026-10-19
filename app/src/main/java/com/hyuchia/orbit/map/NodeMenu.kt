package com.hyuchia.orbit.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ControlPointDuplicate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.ColorChoices

/** What the long-press menu does with an idea. */
sealed interface NodeAction {
    data object AddChild : NodeAction
    data object AddSibling : NodeAction
    data object Rename : NodeAction
    data object Edit : NodeAction
    data object PickIcon : NodeAction

    /** Any color, from the color picker. */
    data object PickColor : NodeAction

    data class SetStatus(val status: String?) : NodeAction
    data class SetColor(val color: String?) : NodeAction
    data class SetCollapsed(val collapsed: Boolean) : NodeAction

    /** Copies it with everything under it, for pasting here, in another map or in another app. */
    data object Copy : NodeAction

    /** Pastes what was copied into it: text as ideas under it, a picture as its picture. */
    data object Paste : NodeAction

    data object Duplicate : NodeAction
    data object MoveEarlier : NodeAction
    data object MoveLater : NodeAction
    data object MoveOut : NodeAction
    data object MoveIn : NodeAction
    data object Delete : NodeAction

    /** Adds to a counted goal, or takes off below 0. The menu stays open for the next step. */
    data class StepGoal(val by: Int) : NodeAction

    /** Adds an amount to a goal, takes one off, or pays one, in a small dialog. */
    data class ChangeGoal(val change: GoalChange) : NodeAction
}

private enum class MenuPage { Main, Status, Color, Move }

/** Orbit's status words, as people say them. */
val Statuses = listOf(
    Triple("todo", R.string.map_status_to_do, Icons.Outlined.RadioButtonUnchecked),
    Triple("active", R.string.map_status_in_progress, Icons.Outlined.Timelapse),
    Triple("done", R.string.map_status_done, Icons.Outlined.CheckCircle),
    Triple("blocked", R.string.map_status_blocked, Icons.Outlined.Block),
)

/**
 * Things to do with an idea, in a menu where it was pressed. `parent` and
 * `previous` (the sibling before it) decide where it can move; `under` is
 * the goals and to-dos under it, which a goal without a target follows.
 * `mapColors` are the map's own colors, to give it again, and `pasteable`
 * is what the clipboard holds that the idea can take, if anything.
 */
@Composable
fun NodeMenu(
    node: MapNode,
    x: Float,
    y: Float,
    parent: MapNode?,
    under: Rollup = Rollup(),
    mapColors: List<String> = emptyList(),
    pasteable: Pasteable? = null,
    onDismiss: () -> Unit,
    onAction: (NodeAction) -> Unit,
) {
    var page by remember(node.id) { mutableStateOf(MenuPage.Main) }

    Box(Modifier.offset(x.dp, y.dp)) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, modifier = Modifier.width(260.dp)) {
            when (page) {
                MenuPage.Main -> {
                    Text(
                        node.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    // A goal's quick changes come first, near the idea.
                    Goal.of(node)?.let { goal ->
                        GoalMenuItems(
                            goal = goal,
                            under = under,
                            onStep = { by -> onAction(NodeAction.StepGoal(by)) },
                            onChange = { change -> onAction(NodeAction.ChangeGoal(change)) },
                            onEdit = { onAction(NodeAction.Edit) },
                        )
                        HorizontalDivider()
                    }

                    Item(stringResource(R.string.map_add_an_idea), Icons.Outlined.AddCircleOutline) { onAction(NodeAction.AddChild) }

                    if (!node.isRoot) {
                        Item(stringResource(R.string.map_add_one_next_to_it), Icons.AutoMirrored.Outlined.PlaylistAdd) { onAction(NodeAction.AddSibling) }
                    }

                    Item(stringResource(R.string.common_rename), Icons.Outlined.Edit) { onAction(NodeAction.Rename) }
                    Item(stringResource(R.string.map_edit_details), Icons.Outlined.Tune) { onAction(NodeAction.Edit) }
                    HorizontalDivider()
                    Item(stringResource(R.string.common_status), Icons.Outlined.CheckCircle, more = true) { page = MenuPage.Status }
                    Item(stringResource(R.string.common_color), Icons.Outlined.Palette, more = true) { page = MenuPage.Color }
                    Item(stringResource(if (node.icon == null) R.string.map_add_an_icon else R.string.map_change_the_icon), Icons.Outlined.EmojiEmotions) { onAction(NodeAction.PickIcon) }

                    if (node.childCount > 0 && !node.isRoot) {
                        if (node.collapsed) {
                            Item(stringResource(R.string.map_show_its_ideas), Icons.Outlined.UnfoldMore) { onAction(NodeAction.SetCollapsed(false)) }
                        } else {
                            Item(stringResource(R.string.map_fold_its_ideas_away), Icons.Outlined.UnfoldLess) { onAction(NodeAction.SetCollapsed(true)) }
                        }
                    }

                    HorizontalDivider()
                    Item(stringResource(R.string.map_copy), Icons.Outlined.ContentCopy) { onAction(NodeAction.Copy) }

                    when (pasteable) {
                        Pasteable.Ideas -> Item(stringResource(R.string.map_paste_as_ideas), Icons.Outlined.ContentPaste) { onAction(NodeAction.Paste) }
                        Pasteable.Picture -> Item(stringResource(R.string.map_paste_the_picture), Icons.Outlined.ContentPaste) { onAction(NodeAction.Paste) }
                        null -> Unit
                    }

                    if (!node.isRoot) {
                        Item(stringResource(R.string.map_move), Icons.Outlined.OpenWith, more = true) { page = MenuPage.Move }
                        Item(stringResource(R.string.common_duplicate), Icons.Outlined.ControlPointDuplicate) { onAction(NodeAction.Duplicate) }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (node.childCount == 0) {
                                        stringResource(R.string.common_delete)
                                    } else {
                                        pluralStringResource(R.plurals.map_delete_with_its_ideas, node.childCount, node.childCount)
                                    },
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                            onClick = { onAction(NodeAction.Delete) },
                        )
                    }
                }
                MenuPage.Status -> {
                    PageHeader(stringResource(R.string.common_status)) { page = MenuPage.Main }

                    Statuses.forEach { (word, label, icon) ->
                        Item(stringResource(label), icon, checked = node.status.equals(word, ignoreCase = true)) { onAction(NodeAction.SetStatus(word)) }
                    }

                    Item(stringResource(R.string.map_no_status), Icons.Outlined.Clear, checked = node.status == null) { onAction(NodeAction.SetStatus(null)) }
                }
                MenuPage.Color -> {
                    PageHeader(stringResource(R.string.common_color)) { page = MenuPage.Main }
                    ColorChoices(
                        current = node.color,
                        automatic = parseCssColor(node.drawnColor).takeIf { node.color == null },
                        onPick = { color -> onAction(NodeAction.SetColor(color)) },
                        size = 34.dp,
                        mapColors = mapColors,
                        onCustom = { onAction(NodeAction.PickColor) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                MenuPage.Move -> {
                    PageHeader(stringResource(R.string.map_move)) { page = MenuPage.Main }

                    if (node.index > 0) {
                        Item(stringResource(R.string.map_earlier_in_its_orbit), Icons.Outlined.ArrowUpward) { onAction(NodeAction.MoveEarlier) }
                        Item(stringResource(R.string.map_under_the_idea_before_it), Icons.AutoMirrored.Outlined.FormatIndentIncrease) { onAction(NodeAction.MoveIn) }
                    }

                    if (parent != null && node.index < parent.childCount - 1) {
                        Item(stringResource(R.string.map_later_in_its_orbit), Icons.Outlined.ArrowDownward) { onAction(NodeAction.MoveLater) }
                    }

                    if (parent != null && !parent.isRoot) {
                        Item(stringResource(R.string.map_out_next_to, parent.text), Icons.AutoMirrored.Outlined.FormatIndentDecrease) { onAction(NodeAction.MoveOut) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Item(label: String, icon: ImageVector, more: Boolean = false, checked: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = when {
            more -> ({ Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) })
            checked -> ({ Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.map_current)) })
            else -> null
        },
        onClick = onClick,
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.titleSmall) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) },
        onClick = onBack,
    )
    HorizontalDivider()
}
