package com.hyuchia.orbit.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.WhatGoes
import com.hyuchia.orbit.ui.rememberAsTheyAre

/**
 * Before a map with pictures, videos or audio goes to Android's share sheet:
 * what goes with it, and the choice of sending just its text, a markdown
 * file, or a YAML one for a map whose text is YAML (`yaml`). All its own
 * files are `media`; the zip carries those that go `clean`, and asks before
 * it carries those that can't (`uncleaned`) as they are. `onShare` says
 * which way, with its files or not, and those of them that go as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMapSheet(
    title: String,
    media: MediaSummary,
    clean: MediaSummary,
    uncleaned: Uncleaned,
    onShare: (withFiles: Boolean, asTheyAre: Set<String>) -> Unit,
    onDismiss: () -> Unit,
    yaml: Boolean = false,
) {
    val words = rememberWords()
    val asTheyAre = rememberAsTheyAre()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        EscapeCloses(onDismiss)

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.sharing_share_map, title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
            ShareChoice(
                icon = Icons.Outlined.FolderZip,
                // What goes clean is counted; what can't is named under it, and asked about as it's shared.
                headline = if (clean.isEmpty) stringResource(R.string.sharing_map_alone) else stringResource(R.string.sharing_map_with, MediaWords.list(clean, words)),
                supporting = listOfNotNull(
                    stringResource(R.string.sharing_map_with_description, MediaWords.size(clean.bytes, words)),
                    MediaWords.uncleaned(uncleaned, words, asTheyAre = false),
                ),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { asTheyAre.then(uncleaned) { names -> onShare(true, names) } }
            ShareChoice(
                icon = Icons.Outlined.Description,
                headline = stringResource(R.string.sharing_just_the_text),
                supporting = listOfNotNull(stringResource(if (yaml) R.string.sharing_yaml_file else R.string.sharing_markdown_file), MediaWords.staysHere(media, words)),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurface,
            ) { onShare(false, emptySet()) }

            if (clean.pictures > 0 || clean.videos > 0 || clean.audio > 0) {
                WhatGoes(
                    stringResource(R.string.sharing_without_where),
                    Icons.Outlined.LocationOff,
                    goes = false,
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** One way to share, as a card to tap: its icon in a circle of its own, what goes, and how. */
@Composable
private fun ShareChoice(icon: ImageVector, headline: String, supporting: List<String>, container: Color, content: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                Surface(shape = CircleShape, color = content.copy(alpha = 0.10f), modifier = Modifier.size(44.dp)) {}
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                supporting.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.78f)) }
            }
        }
    }
}
