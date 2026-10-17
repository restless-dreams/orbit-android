package com.hyuchia.orbit.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.EscapeCloses

/** The path's steps that are still on the map, each with its place in the path. */
private fun MapUi.pathSteps(): List<Pair<Int, MapNode>> =
    presentPath.mapIndexedNotNull { index, id -> nodes[id]?.let { index to it } }

/** While the path is made: what it is, a way to find ideas for it and to start it over, and Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathTopBar(ui: MapUi, background: Color, onSearch: () -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    val steps = ui.pathSteps().size
    TopAppBar(
        title = { Text(stringResource(R.string.present_path), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = {
            Text(
                if (steps == 0) stringResource(R.string.present_path_no_ideas) else pluralStringResource(R.plurals.present_path_ideas, steps, steps),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.present_path_find_an_idea)) }

            AnimatedVisibility(steps > 1, enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.present_path_start_over)) }
            }

            Button(onClick = onDone, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) { Text(stringResource(R.string.common_done)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
    )
}

/**
 * The path while it's made, over the map's bottom edge: its steps,
 * numbered, in order. Tap a step to fly to it, long-press it to move it, or
 * take it off with its ×. The idea in focus can join the path again at the
 * end, to come back to it there.
 */
@Composable
fun PathBar(
    ui: MapUi,
    onFly: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = ui.pathSteps()
    val sun = ui.sun?.takeIf { ui.mode == "focus" }
    val canAdd = sun != null && ui.presentPath.lastOrNull() != sun.id
    val list = rememberLazyListState()

    // The newest step comes into view as it joins, and the idea in focus's step as the map flies back to it.
    LaunchedEffect(steps.size) {
        if (steps.isNotEmpty()) {
            list.animateScrollToItem(steps.lastIndex)
        }
    }

    LaunchedEffect(sun?.id) {
        val place = steps.indexOfLast { (_, node) -> node.id == sun?.id }

        if (place >= 0) {
            list.animateScrollToItem(place)
        }
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier.widthIn(max = 840.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.animateContentSize()) {
            // Until the path has somewhere to go, it says how it's made.
            AnimatedVisibility(steps.size < 2, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(
                    stringResource(R.string.present_path_how_to),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    state = list,
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    itemsIndexed(steps, key = { _, (index, node) -> "$index-${node.id}" }) { place, (index, node) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                            if (place > 0) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 2.dp).size(18.dp),
                                )
                            }

                            PathStep(
                                number = place + 1,
                                node = node,
                                current = node.id == sun?.id,
                                first = place == 0,
                                last = place == steps.lastIndex,
                                onFly = { onFly(node.id) },
                                onRemove = { onRemove(index) },
                                onMove = { by -> onMove(index, by) },
                            )
                        }
                    }
                }

                // Always in reach at the end, however long the path: the idea in focus, onto the path again.
                AnimatedVisibility(canAdd, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    FilledTonalButton(
                        onClick = onAdd,
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                        modifier = Modifier.padding(end = 12.dp).height(40.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (sun?.id in ui.presentPath) stringResource(R.string.present_path_add_again) else stringResource(R.string.present_path_add_idea, sun?.label.orEmpty()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 168.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One step of the path: its number in the idea's color, its name, and an × to take it off. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PathStep(
    number: Int,
    node: MapNode,
    current: Boolean,
    first: Boolean,
    last: Boolean,
    onFly: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val badge = parseCssColor(node.drawnColor) ?: colors.primary

    // The × sits over the step's end, a full 48 dp to tap however slim the step is drawn.
    Box(contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = CircleShape,
            color = if (current) colors.secondaryContainer else colors.surfaceContainerHighest,
            contentColor = if (current) colors.onSecondaryContainer else colors.onSurface,
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.present_path_fly_to_idea, node.label),
                    onLongClickLabel = stringResource(R.string.present_path_move_idea, node.label),
                    onLongClick = { menu = true },
                    onClick = onFly,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                // A fine ring keeps the number's disc apart from the step, whatever the idea's color.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(badge, CircleShape)
                        .border(1.dp, LocalContentColor.current.copy(alpha = 0.2f), CircleShape),
                ) {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = if (badge.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }

                Text(
                    node.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(max = 168.dp),
                )
                Spacer(Modifier.width(44.dp))
            }
        }

        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.present_path_take_idea_off, node.label), modifier = Modifier.size(18.dp))
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!first) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.present_path_earlier)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) },
                    onClick = { menu = false; onMove(-1) },
                )
            }

            if (!last) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.present_path_later)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    onClick = { menu = false; onMove(1) },
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.present_path_take_off)) },
                leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null) },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}

/**
 * How a rehearsal went: how long it took, and each idea's time as a bar,
 * the longest marked, so the presenter sees where the time goes. The times
 * set the pace for presenting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RehearsalSheet(rehearsal: Rehearsal, nodes: Map<String, MapNode>, onAgain: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val longest = rehearsal.seconds.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        EscapeCloses(onDismiss)

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.present_rehearsed_in), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(clock(rehearsal.total), style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"))
                Text(
                    stringResource(R.string.present_rehearsal_sets_the_pace),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(rehearsal.order, key = { _, id -> id }) { index, id ->
                    val seconds = rehearsal.seconds[id] ?: 0
                    val node = nodes[id]
                    val isLongest = seconds == longest && rehearsal.order.size > 1

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                node?.label ?: stringResource(R.string.present_idea_no_longer_on_the_map),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            if (isLongest) {
                                Text(stringResource(R.string.present_longest), style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                            }

                            Text(clock(seconds), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                        }

                        LinearProgressIndicator(
                            progress = { seconds.toFloat() / longest },
                            color = if (isLongest) colors.tertiary else parseCssColor(node?.drawnColor) ?: colors.primary,
                            trackColor = colors.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp)
                                .height(6.dp),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                OutlinedButton(onClick = onAgain) {
                    Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.present_rehearse_again))
                }

                Button(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
            }
        }
    }
}
