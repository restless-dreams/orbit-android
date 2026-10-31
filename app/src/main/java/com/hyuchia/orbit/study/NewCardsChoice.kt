package com.hyuchia.orbit.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R

/**
 * How many new cards a day, as a connected group of [NEW_CARDS_CHOICES]:
 * `selected` is the learner's choice, which may be none of them when a newer
 * version chose another number. Each says the whole of its choice aloud,
 * "20 new cards a day".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewCardsADayChoice(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.selectableGroup(),
    ) {
        NEW_CARDS_CHOICES.forEachIndexed { index, count ->
            val said = pluralStringResource(R.plurals.study_new_cards_a_day_choice, count, count)

            ToggleButton(
                checked = count == selected,
                onCheckedChange = { onPick(count) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    NEW_CARDS_CHOICES.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = height)
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = said
                    },
            ) {
                Text("$count", maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

/**
 * The same choice, compact, for a short screen: a button with the number
 * chosen, "20 a day", that opens the others in a menu.
 */
@Composable
fun NewCardsADayMenu(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.study_new_cards_a_day)
    val chosen = pluralStringResource(R.plurals.study_new_cards_a_day_choice, selected, selected)

    Box(modifier) {
        FilledTonalButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .semantics { contentDescription = "$label: $chosen" },
        ) {
            Text(pluralStringResource(R.plurals.study_new_cards_a_day_short, selected, selected), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NEW_CARDS_CHOICES.forEach { count ->
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.study_new_cards_a_day_choice, count, count)) },
                    trailingIcon = {
                        if (count == selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        open = false
                        onPick(count)
                    },
                    modifier = Modifier.semantics { this.selected = count == selected },
                )
            }
        }
    }
}
