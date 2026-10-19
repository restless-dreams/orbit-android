package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapTags
import java.text.Collator

/** A phone on its side: with the keyboard up, the sheet keeps only what it needs. */
private val ShortScreen = 480.dp

/**
 * A map's tags (`tags`), to pick from every tag the maps have (`known`) or
 * add new ones. Each change is kept as it's made, through `onChange`; the
 * sheet shows it at once, before the map's file says so.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagsSheet(title: String, tags: List<String>, known: List<String>, onChange: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(MapTags.normalize(tags)) }
    var typed by rememberSaveable { mutableStateOf("") }
    val collator = remember { Collator.getInstance() }

    // Every tag there is, and the ones only this map has (just added, say), once each, in the language's order.
    val choices = remember(known, chosen) { MapTags.normalize(known + chosen).sortedWith(collator) }

    // Above a keyboard on a phone on its side there's room for a line of tags and the field, without the title.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val typingOnShortScreen = windowHeight < ShortScreen && WindowInsets.isImeVisible

    fun change(next: List<String>) {
        chosen = MapTags.normalize(next)
        onChange(chosen)
    }

    fun add() {
        val tag = MapTags.normalize(listOf(typed)).firstOrNull() ?: return
        typed = ""

        if (chosen.none { MapTags.key(it) == MapTags.key(tag) }) {
            change(chosen + tag)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        dragHandle = if (typingOnShortScreen) null else ({ BottomSheetDefaults.DragHandle() }),
    ) {
        EscapeCloses(onDismiss)

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            if (!typingOnShortScreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_tags_for, title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
                }
            }

            @Composable
            fun Choice(tag: String) {
                val on = chosen.any { MapTags.key(it) == MapTags.key(tag) }

                FilterChip(
                    selected = on,
                    onClick = { change(if (on) chosen.filterNot { MapTags.key(it) == MapTags.key(tag) } else chosen + tag) },
                    label = { Text(tag) },
                    leadingIcon = if (on) ({ Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                )
            }

            when {
                choices.isEmpty() && !typingOnShortScreen -> Text(stringResource(R.string.ui_tags_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                typingOnShortScreen -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(choices) { tag -> Choice(tag) } }
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { tag -> Choice(tag) } }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.ui_new_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )

                FilledTonalButton(onClick = ::add, enabled = typed.isNotBlank()) { Text(stringResource(R.string.ui_add_tag)) }
            }
        }
    }
}
