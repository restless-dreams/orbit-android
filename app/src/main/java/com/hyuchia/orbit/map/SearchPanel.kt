package com.hyuchia.orbit.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.ui.ListRow
import com.hyuchia.orbit.ui.foldCase
import com.hyuchia.orbit.ui.searchWords

/** An idea that matches a search, and where it matched. */
internal data class Match(val node: MapNode, val path: String, val snippet: String?)

/** The ideas with every word of `query` in their text, tags or note, whatever the case; text matches first. */
internal fun search(ui: MapUi, query: String): List<Match> {
    val words = searchWords(query)

    if (words.isEmpty()) {
        return emptyList()
    }

    return ui.map?.nodes.orEmpty().mapNotNull { node ->
        val title = foldCase("${node.text} ${node.tags.joinToString(" ") { "#$it" }}")
        val note = node.note.orEmpty()
        val inTitle = words.all { it in title }

        if (!inTitle && !words.all { it in title || it in foldCase(note) }) {
            return@mapNotNull null
        }

        val snippet = if (inTitle) null else note.lineSequence().firstOrNull { line -> words.any { it in foldCase(line) } }?.trim()
        Match(node, pathOf(ui, node), snippet)
    }.sortedBy { if (it.snippet == null) 0 else 1 }
}

/** "Trip › Food", the way to an idea. */
private fun pathOf(ui: MapUi, node: MapNode): String {
    val names = mutableListOf<String>()
    var current = node.parentId?.let { ui.nodes[it] }

    while (current != null) {
        names.add(0, current.text)
        current = current.parentId?.let { ui.nodes[it] }
    }

    return names.joinToString(" › ")
}

/** Finds ideas by their text, tags or notes, and flies to the one picked. */
@Composable
fun SearchPanel(model: MapViewModel, ui: MapUi, sheet: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val matches = remember(query, ui.map) { search(ui, query) }

    fun open(node: MapNode) {
        model.show(null)
        model.focus(node.id, point = true)
    }

    PanelFrame(title = stringResource(R.string.common_search_the_map), sheet = sheet, onDismiss = { model.show(null) }) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.map_clear)) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { matches.firstOrNull()?.let { open(it.node) } }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focus),
        )

        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            modifier = Modifier.imePadding(),
        ) {
            if (query.isNotBlank() && matches.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.map_nothing_matches, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            items(matches, key = { it.node.id }) { match ->
                ListRow(
                    headline = { Text(highlight(match.node.label, query), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    overline = if (match.path.isNotEmpty()) ({ Text(match.path, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                    supporting = match.snippet?.let { snippet -> { Text(highlight(snippet, query), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    leading = { NodeDot(match.node, 16.dp) },
                    modifier = Modifier.clickable { open(match.node) },
                )
            }
        }
    }
}

/** The text with the searched words in bold. */
@Composable
private fun highlight(text: String, query: String) = buildAnnotatedString {
    var index = 0

    for (found in wordRanges(text, searchWords(query))) {
        append(text.substring(index, found.first))

        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
            append(text.substring(found.first, found.last + 1))
        }

        index = found.last + 1
    }

    append(text.substring(index))
}

/**
 * Where the search's `words` (from [searchWords]) are in the text, first to
 * last and without overlapping, as ranges of the text itself.
 */
internal fun wordRanges(text: String, words: List<String>): List<IntRange> {
    val folded = foldCase(text)
    val found = mutableListOf<IntRange>()
    var index = 0

    while (index < folded.length) {
        val next = words.mapNotNull { word -> folded.indexOf(word, index).takeIf { it >= 0 }?.let { it to word } }.minByOrNull { it.first } ?: break
        found.add(next.first until next.first + next.second.length)
        index = next.first + next.second.length
    }

    return found
}
