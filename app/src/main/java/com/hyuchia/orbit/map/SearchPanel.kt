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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.ListRow
import com.hyuchia.orbit.ui.foldCase
import com.hyuchia.orbit.ui.searchWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** An idea that matches a search, and where it matched. */
internal data class Match(val node: MapNode, val path: String, val snippet: String?)

/** What a search found, and for which query. */
internal data class Found(val query: String, val matches: List<Match>)

/**
 * A map's ideas, ready to be searched as each key goes in: their text, tags
 * and notes case folded with `fold`, and the way to each, once for the map
 * rather than once for every key. A search then only looks for its words.
 */
internal class SearchIndex(nodes: List<MapNode>, fold: (String) -> String = ::foldCase) {
    /** An idea, its text and tags folded, its note folded (line for line as written: folding keeps each letter's length), and the way to it. */
    private class Entry(val node: MapNode, val title: String, val note: String, val path: String)

    private val entries: List<Entry>

    init {
        val byId = nodes.associateBy { it.id }
        entries = nodes.map { node -> Entry(node, fold("${node.text} ${node.tags.joinToString(" ") { "#$it" }}"), fold(node.note.orEmpty()), pathOf(byId, node)) }
    }

    /** The ideas with every word of `query` in their text, tags or note, whatever the case; text matches first. */
    fun find(query: String): List<Match> {
        val words = searchWords(query)

        if (words.isEmpty()) {
            return emptyList()
        }

        val inText = mutableListOf<Match>()
        val inNote = mutableListOf<Match>()

        for (entry in entries) {
            if (words.all { it in entry.title }) {
                inText.add(Match(entry.node, entry.path, null))
            } else if (words.all { it in entry.title || it in entry.note }) {
                // The note's first line with one of the words, as written.
                val line = entry.node.note.orEmpty().lineSequence().zip(entry.note.lineSequence()).firstOrNull { (_, folded) -> words.any { it in folded } }
                inNote.add(Match(entry.node, entry.path, line?.first?.trim()))
            }
        }

        return inText + inNote
    }
}

/** The ideas with every word of `query` in their text, tags or note, whatever the case; text matches first. */
internal fun search(ui: MapUi, query: String): List<Match> = SearchIndex(ui.map?.nodes.orEmpty()).find(query)

/**
 * What the search's list says nothing matches, with `query` typed and
 * `found` the last search that ended, or null while something matches.
 * Until the search for a new key ends, it keeps saying so for the query
 * before it, rather than blinking out with every key typed.
 */
internal fun nothingMatches(query: String, found: Found): String? = found.query.takeIf { query.isNotBlank() && it.isNotBlank() && found.matches.isEmpty() }

/** "Trip › Food", the way to an idea. */
private fun pathOf(nodes: Map<String, MapNode>, node: MapNode): String {
    val names = mutableListOf<String>()
    var current = node.parentId?.let { nodes[it] }

    while (current != null) {
        names.add(0, current.text)
        current = current.parentId?.let { nodes[it] }
    }

    return names.joinToString(" › ")
}

/**
 * A search of the map while its panel is open, kept above the panel: what's
 * typed, the map's ideas folded for searching ([SearchIndex]), what the last
 * search found, and where the list of results is. The panel moves between a
 * sheet over the map and the map's side as a phone or tablet turns, and is
 * made anew there: the search goes on where it was, rather than empty.
 */
@Stable
class MapSearch internal constructor(typed: TextFieldValue = TextFieldValue(), val place: ListPlace = ListPlace()) {
    /** What's typed in the search's field, and where the cursor is in it. */
    var typed by mutableStateOf(typed)

    /** What's typed. */
    val query: String get() = typed.text

    /** The map's ideas, folded for searching away from the main thread; null until they are. */
    internal var index by mutableStateOf<SearchIndex?>(null)

    /** The last search that ended. */
    internal var found by mutableStateOf(Found("", emptyList()))

    /**
     * What was typed as the keyboard's Search was pressed, before its search
     * had ended: its first match opens once it has. Any change to what's
     * typed forgets it, even back to the same words.
     */
    internal var opening by mutableStateOf<String?>(null)

    /** Types `value` in the field: a change to what's typed forgets Search pressed for what was typed before. */
    fun type(value: TextFieldValue) {
        if (value.text != typed.text) {
            opening = null
        }

        typed = value
    }

    companion object {
        /** Keeps what's typed, the cursor and where the list is when the activity is made again: the search runs again for it. */
        val Saver: Saver<MapSearch, Any> = listSaver(
            save = { listOf(it.query, it.typed.selection.start, it.typed.selection.end, it.place.index, it.place.offset) },
            restore = { MapSearch(TextFieldValue(it[0] as String, TextRange(it[1] as Int, it[2] as Int)), ListPlace(it[3] as Int, it[4] as Int)) },
        )
    }
}

/**
 * The map's search, for its panel ([SearchPanel]): a new one each time it
 * opens (`open`), which folds `nodes` and searches them for each key typed,
 * on `background`, while it's open.
 */
@Composable
fun rememberMapSearch(nodes: List<MapNode>, open: Boolean, background: CoroutineContext = Dispatchers.Default): MapSearch {
    val search = rememberSaveable(open, saver = MapSearch.Saver) { MapSearch() }

    if (open) {
        // The map's ideas are folded for searching once, not for every key, and each search runs away from the main
        // thread: keys go in at once, on a map of thousands of ideas too. A key typed before a search ends drops it.
        LaunchedEffect(search, nodes) {
            search.index = withContext(background) { SearchIndex(nodes) }
        }

        LaunchedEffect(search) {
            snapshotFlow { search.index to search.query }.collectLatest { (index, query) ->
                if (index != null) {
                    search.found = withContext(background) { Found(query, index.find(query)) }
                }
            }
        }
    }

    return search
}

/** Finds ideas by their text, tags or notes, and flies to the one picked. */
@Composable
fun SearchPanel(model: MapViewModel, search: MapSearch, sheet: Boolean) {
    SearchPanel(
        search,
        sheet,
        onOpen = { node ->
            model.show(null)
            model.focus(node.id, point = true)
        },
        onDismiss = { model.show(null) },
    )
}

/** The search's panel, as a sheet over the map or beside it (`sheet` false): `onOpen` flies to the idea picked. */
@Composable
internal fun SearchPanel(search: MapSearch, sheet: Boolean, onOpen: (MapNode) -> Unit, onDismiss: () -> Unit) {
    val query = search.query
    val found = search.found

    // Until the search for what's typed ends, the list stays as it was: its ideas, or that nothing matches what was typed before.
    val matches = if (query.isBlank()) emptyList() else found.matches
    val known = found.query == query
    val unmatched = nothingMatches(query, found)
    val open by rememberUpdatedState(onOpen)

    // Search pressed before the search for what's typed had ended opens its first match once it has, if nothing is typed
    // or cleared meanwhile: the map's ideas are never folded or searched on the main thread for it.
    LaunchedEffect(search) {
        snapshotFlow { search.opening?.takeIf { it == search.query && it == search.found.query } }.filterNotNull().collect {
            search.opening = null
            search.found.matches.firstOrNull()?.let { open(it.node) }
        }
    }

    PanelFrame(title = stringResource(R.string.common_search_the_map), sheet = sheet, onDismiss = onDismiss) {
        val focus = remember { FocusRequester() }

        // The field takes the focus as the search opens, and again as it moves with the screen turning, once it's there:
        // in a sheet's own window, that's after the sheet's. A word the keyboard was still making in the field it leaves
        // is the field's as it stands: this one's keyboard starts afresh.
        LaunchedEffect(Unit) {
            search.typed = search.typed.copy(composition = null)
            focus.requestFocus()
        }

        TextField(
            value = search.typed,
            onValueChange = search::type,
            placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    val clear = stringResource(R.string.map_clear)

                    Hint(clear) {
                        IconButton(onClick = { search.type(TextFieldValue()) }) { Icon(Icons.Outlined.Close, contentDescription = clear) }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (known) {
                        matches.firstOrNull()?.let { onOpen(it.node) }
                    } else {
                        search.opening = query
                    }
                },
            ),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            // Beside the map, the panel keeps it above the keyboard, rising over the top bar on a phone on its side.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .keptAboveKeyboard(LocalKeptField.current)
                .focusRequester(focus),
        )

        // Beside the map on a tablet, the list starts afresh once the panes stop moving (see PaneListState). Moved
        // between a sheet and the map's side, it starts where it was.
        val pane = rememberPaneListState(search.place)

        key(pane.list) {
            LazyColumn(
                state = pane.list,
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                modifier = Modifier.paneList(pane).imePadding(),
            ) {
                if (unmatched != null) {
                    item {
                        Text(
                            stringResource(R.string.map_nothing_matches, unmatched),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                items(matches, key = { it.node.id }) { match ->
                    ListRow(
                        headline = { Text(highlight(match.node.label, found.query), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        overline = if (match.path.isNotEmpty()) ({ Text(match.path, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                        supporting = match.snippet?.let { snippet -> { Text(highlight(snippet, found.query), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                        leading = { NodeDot(match.node, 16.dp) },
                        modifier = Modifier.clickable { onOpen(match.node) },
                    )
                }
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
