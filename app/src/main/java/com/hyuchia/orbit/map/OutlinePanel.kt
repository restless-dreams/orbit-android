package com.hyuchia.orbit.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.WavyProgress
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The map as a native list: every idea, its color and status, with checkboxes
 * to tick tasks off and progress for each branch. Tap a row to fly there; it
 * is also how screen readers and keyboards get around the map.
 */
@Composable
fun OutlinePanel(model: MapViewModel, ui: MapUi, sheet: Boolean) {
    val nodes = ui.map?.nodes.orEmpty()
    val progress = ui.progress
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    val folded: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }

    val hidden = remember(nodes, folded.toMap()) {
        val hiddenIds = mutableSetOf<String>()

        nodes.forEach { node ->
            val parent = node.parentId

            if (parent != null && (parent in hiddenIds || folded[parent] == true)) {
                hiddenIds += node.id
            }
        }

        hiddenIds
    }

    val visible = nodes.filter { it.id !in hidden }
    val list = rememberLazyListState()
    val sunId = ui.sun?.id

    LaunchedEffect(Unit) {
        val index = visible.indexOfFirst { it.id == sunId }

        if (index > 2) {
            list.scrollToItem(index - 2)
        }
    }

    val total = nodes.firstOrNull()?.let { progress.under(it) }

    PanelFrame(title = stringResource(R.string.common_outline), sheet = sheet, onDismiss = { model.show(null) }, partial = true, scrim = false) {
        if (total != null && total.total > 0) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    GoalNumbers.progress(total, words, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WavyProgress(progress = { total.share.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }

        LazyColumn(state = list, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
            items(visible, key = { it.id }) { node ->
                OutlineRow(
                    node = node,
                    current = node.id == sunId,
                    folded = folded[node.id] == true,
                    under = progress.under(node),
                    ring = progress.followsItsIdeas(node),
                    onFold = { folded[node.id] = !(folded[node.id] ?: false) },
                    onOpen = {
                        if (sheet) {
                            model.show(null)
                        }

                        model.focus(node.id, point = true)
                    },
                    onEdit = { model.show(Panel.Editor(node.id)) },
                    onCheck = { checked -> model.update(node.id, "status", JsonPrimitive(if (checked) "done" else "todo")) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineRow(
    node: MapNode,
    current: Boolean,
    folded: Boolean,
    under: Rollup,
    ring: Boolean,
    onFold: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = node.isDone
    val goal = remember(node) { Goal.of(node) }
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    val colors = MaterialTheme.colorScheme

    val textColor = when {
        current -> colors.onSecondaryContainer
        done -> colors.onSurfaceVariant
        else -> colors.onSurface
    }

    val mutedColor = if (current) colors.onSecondaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant
    val edit = stringResource(R.string.common_edit)

    val ideas = when {
        node.childCount == 0 -> null
        folded -> pluralStringResource(R.plurals.map_folded_ideas, node.childCount, node.childCount)
        else -> pluralStringResource(R.plurals.map_ideas, node.childCount, node.childCount)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClickLabel = stringResource(R.string.map_fly_to_it), onLongClickLabel = edit, onClick = onOpen, onLongClick = onEdit)
            .semantics {
                if (ideas != null) {
                    stateDescription = ideas
                }

                customActions = listOf(CustomAccessibilityAction(edit) { onEdit(); true })
            }
            .padding(start = (node.depth * 18).dp, end = 4.dp),
    ) {
        if (node.childCount > 0) {
            IconButton(onClick = onFold, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (folded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (folded) R.string.map_show_its_ideas else R.string.map_fold_its_ideas),
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }

        NodeDot(node, 14.dp)
        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                node.label,
                style = if (node.depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (node.depth <= 1) FontWeight.SemiBold else null,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                // A goal without a target that follows the ideas under it shows their share instead, as its ring does.
                goal?.takeIf { it.target != null || under.total == 0 }?.let { GoalNumbers.brief(it, words, locale) },
                under.takeIf { ring && it.total > 0 }?.let { NumberFormat.getPercentInstance(locale).format((it.share * 100).roundToInt() / 100.0) },
                under.takeIf { it.total > 0 }?.let { pluralStringResource(R.plurals.map_done_of_total, it.finished, it.finished, it.total) },
                // A status that isn't a to-do's, such as "primary key", is a label: it reads with the tags.
                node.status?.trim()?.takeIf { it.isNotEmpty() && !node.isToDo }?.let { statusLabel(it) },
                node.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                node.note?.lineSequence()?.map { it.trim().trimStart('#', '>', '-', '*', ' ') }?.firstOrNull { it.isNotEmpty() },
            )

            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (node.isToDo) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = done, onCheckedChange = onCheck)
            }
        } else if (goal?.target != null) {
            // A goal's ring, as on the map: a check once it's reached.
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (goal.reached) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.map_reached), tint = colors.primary)
                } else {
                    val ofTheWay = stringResource(R.string.map_of_the_way, GoalNumbers.percent(goal, locale))
                    CircularProgressIndicator(
                        progress = { goal.share.toFloat() },
                        strokeWidth = 3.dp,
                        modifier = Modifier
                            .size(22.dp)
                            .semantics { contentDescription = ofTheWay },
                    )
                }
            }
        }
    }
}
