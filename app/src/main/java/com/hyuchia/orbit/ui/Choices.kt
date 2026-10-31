package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A toggle button's padding on each side of its label, as Material's small buttons have it. */
private val ButtonPadding = 16.dp

/** How wide a group of connected buttons grows, unless its labels need more: wider, it reads as one long bar. */
private val ConnectedChoiceWidth = 420.dp

/**
 * One of a few choices (two to four), as Material 3 Expressive's connected
 * buttons, all as wide as the widest label needs. TalkBack hears radio
 * buttons in a group, each saying `spoken` when that says more than its
 * label. When the labels don't fit on one line across the width (a narrow
 * screen, larger text, longer words in another language), the choices show
 * as a list of radio buttons instead. Both keep `inset` from the sides.
 */
@Composable
fun <T> ConnectedChoice(
    choices: List<T>,
    selected: T?,
    onPick: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    spoken: @Composable (T) -> String = label,
    inset: Dp = 24.dp,
) {
    val labels = choices.map { label(it) }
    val said = choices.map { spoken(it) }
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val widest = remember(labels, style, density) {
        with(density) { labels.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }.toDp() }
    }

    val space = ButtonGroupDefaults.ConnectedSpaceBetween
    val needed = (widest + ButtonPadding * 2) * choices.size + space * (choices.size - 1)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val room = maxWidth - inset * 2

        if (needed <= room) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space),
                modifier = Modifier
                    .padding(horizontal = inset)
                    .width(minOf(room, maxOf(needed, ConnectedChoiceWidth)))
                    .selectableGroup(),
            ) {
                choices.forEachIndexed { index, choice ->
                    ToggleButton(
                        checked = choice == selected,
                        onCheckedChange = { onPick(choice) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics {
                                role = Role.RadioButton
                                this.selected = choice == selected
                                contentDescription = said[index]
                            },
                    ) {
                        Text(labels[index], maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        } else {
            Column(Modifier.selectableGroup()) {
                choices.forEachIndexed { index, choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == selected, role = Role.RadioButton) { onPick(choice) }
                            .semantics { contentDescription = said[index] }
                            .padding(horizontal = inset, vertical = 8.dp),
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(labels[index], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }
}

/**
 * One of many choices, as chips that wrap onto as many lines as they need.
 * Material's filter chips are checkboxes to TalkBack, for picking several:
 * these are radio buttons in a group, for picking one.
 */
@Composable
fun <T> ChoiceChips(choices: List<T>, selected: T?, onPick: (T) -> Unit, label: @Composable (T) -> String, modifier: Modifier = Modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.selectableGroup()) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onPick(choice) },
                label = { Text(label(choice)) },
                // Outside the chip's own checkbox role, so it's the one TalkBack hears.
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}
