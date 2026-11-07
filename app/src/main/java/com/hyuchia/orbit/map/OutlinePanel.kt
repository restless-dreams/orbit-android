package com.hyuchia.orbit.map

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.WavyProgress
import com.hyuchia.orbit.ui.rememberTimeWords
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The map as a native list: every idea, its color and status, with checkboxes
 * to tick tasks off and progress for each branch. Tap a row to fly there; it
 * is also how screen readers and keyboards get around the map. On a map that
 * can only be looked at, rows open no editor and checkboxes only show.
 */
@Composable
fun OutlinePanel(model: MapViewModel, ui: MapUi, sheet: Boolean) {
    val nodes = ui.map?.nodes.orEmpty()
    val progress = ui.progress
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    // One for the list: each idea's date is written with it.
    val timeWords = rememberTimeWords()
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
    // Beside the map on a tablet, the list starts afresh once the panes stop moving (see PaneListState).
    val pane = rememberPaneListState()
    val sunId = ui.sun?.id
    var shownSun by remember { mutableStateOf<String?>(null) }

    // The idea in focus is in view as the outline opens, and comes into view each time it changes.
    LaunchedEffect(sunId) {
        val index = visible.indexOfFirst { it.id == sunId }
        val opening = shownSun == null
        shownSun = sunId

        if (index < 0) {
            return@LaunchedEffect
        }

        val list = pane.list

        if (opening) {
            if (index > 2) {
                list.scrollToItem(index - 2)
            }
        } else if (!list.showsWhole(index)) {
            list.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    val total = nodes.firstOrNull()?.let { progress.under(it) }
    val untitled = stringResource(R.string.common_untitled)
    val changeable = ui.readOnly == null
    val fallbackColor = MaterialTheme.colorScheme.primaryContainer.toArgb()

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

        key(pane.list) {
            LazyColumn(state = pane.list, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), modifier = Modifier.paneList(pane)) {
                items(visible, key = { it.id }) { node ->
                    OutlineRow(
                        node = node,
                        current = node.id == sunId,
                        folded = folded[node.id] == true,
                        under = progress.under(node),
                        ring = progress.followsItsIdeas(node),
                        date = shownDate(node, timeWords),
                        onFold = { folded[node.id] = !(folded[node.id] ?: false) },
                        onOpen = {
                            if (sheet) {
                                model.show(null)
                            }

                            model.focus(node.id, point = true)
                        },
                        // A map that can only be looked at has no editor to open, and its to-dos stay as they are.
                        onEdit = if (changeable) ({ model.show(Panel.Editor(node.id)) }) else null,
                        onCheck = if (changeable) ({ checked -> model.update(node.id, "status", JsonPrimitive(if (checked) "done" else "todo")) }) else null,
                        onDragOut = { view ->
                            val started = model.drops.canDragOut && startIdeaDrag(view, model.id, nodes, node.id, untitled, parseCssColor(node.drawnColor)?.toArgb() ?: fallbackColor)

                            if (started) {
                                model.drops.wentOut()
                            }

                            // A sheet is a window over the map: it steps aside, so the idea can land on the map as well as in another app.
                            if (started && sheet) {
                                model.show(null)
                            }

                            started
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * A row of the outline. Tapped, it flies to its idea; held, it lifts, as an
 * idea does on the map: let go, its editor opens (`onEdit`, none on a map
 * that can only be looked at), and moved on, the idea is dragged out to
 * other apps (or back onto the map, into another idea).
 */
@Composable
private fun OutlineRow(
    node: MapNode,
    current: Boolean,
    folded: Boolean,
    under: Rollup,
    ring: Boolean,
    date: ShownDate?,
    onFold: () -> Unit,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onCheck: ((Boolean) -> Unit)?,
    onDragOut: (View) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val done = node.isDone
    val goal = remember(node) { Goal.of(node) }
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    var lifted by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "lift")
    val edit by rememberUpdatedState(onEdit)
    val dragOut by rememberUpdatedState(onDragOut)
    // A press on the fold's button is the button's: held, it shows its hint, rather than lifting the idea to edit it.
    var pressOnFold by remember { mutableStateOf(false) }

    val textColor = when {
        current -> colors.onSecondaryContainer
        done -> colors.onSurfaceVariant
        else -> colors.onSurface
    }

    val mutedColor = if (current) colors.onSecondaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant
    val editLabel = stringResource(R.string.common_edit)

    val ideas = when {
        node.childCount == 0 -> null
        folded -> pluralStringResource(R.plurals.map_folded_ideas, node.childCount, node.childCount)
        else -> pluralStringResource(R.plurals.map_ideas, node.childCount, node.childCount)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Lifted, it rises off the list, as an idea held on the map does.
            .graphicsLayer {
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
                shadowElevation = 8.dp.toPx() * lift
                shape = RoundedCornerShape(16.dp)
                clip = true
            }
            .background(
                when {
                    lifted -> colors.surfaceContainerHighest
                    current -> colors.secondaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable(onClickLabel = stringResource(R.string.map_fly_to_it), onClick = onOpen)
            .pointerInput(node.id) {
                liftThenDrag(
                    onLift = { up ->
                        lifted = up

                        if (up) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                    onLetGo = { edit?.invoke() },
                    onDragOut = { dragOut(view) },
                    ours = { !pressOnFold.also { pressOnFold = false } },
                )
            }
            .semantics {
                if (ideas != null) {
                    stateDescription = ideas
                }

                onEdit?.let { open ->
                    onLongClick(label = editLabel) {
                        open()
                        true
                    }

                    customActions = listOf(CustomAccessibilityAction(editLabel) { open(); true })
                }
            }
            .padding(start = (node.depth * 18).dp, end = 4.dp),
    ) {
        if (node.childCount > 0) {
            val fold = stringResource(if (folded) R.string.map_show_its_ideas else R.string.map_fold_its_ideas)

            Hint(fold) {
                // The button hears a press before the row does, and tells the row the press is its own.
                val heard = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressOnFold = true
                    }
                }

                IconButton(onClick = onFold, modifier = Modifier.size(48.dp).then(heard)) {
                    Icon(if (folded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown, contentDescription = fold)
                }
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
                node.shownStatus?.takeIf { !node.isToDo }?.let { statusLabel(it) },
                node.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                node.note?.lineSequence()?.map { it.trim().trimStart('#', '>', '-', '*', ' ') }?.firstOrNull { it.isNotEmpty() },
            )

            // Its date first, past and not done in the error color, saying so in words too.
            val dateText = date?.let { if (it.overdue) stringResource(R.string.map_date_overdue_label, it.text) else it.text }

            if (dateText != null || details.isNotEmpty()) {
                val line = buildAnnotatedString {
                    if (date != null && dateText != null) {
                        withStyle(SpanStyle(color = if (date.overdue) colors.error else mutedColor, fontWeight = if (date.overdue) FontWeight.SemiBold else null)) {
                            append(dateText)
                        }

                        if (details.isNotEmpty()) {
                            append(" · ")
                        }
                    }

                    append(details.joinToString(" · "))
                }

                Text(line, style = MaterialTheme.typography.bodySmall, color = mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (node.isToDo) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                // On a map that can only be looked at, it shows whether the to-do is done, and takes no tap.
                Checkbox(checked = done, onCheckedChange = { checked -> onCheck?.invoke(checked) }, enabled = onCheck != null)
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
