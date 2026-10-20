package com.hyuchia.orbit.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.MapTags
import com.hyuchia.orbit.data.SortOrder
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.ui.foldCase
import com.hyuchia.orbit.ui.searchWords
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.Collator

/** A map that matches a search, with the line that matched when it isn't the title. */
data class SearchHit(val map: MapSummary, val snippet: String?)

/** A tag as Home lists it: spelled as most maps spell it, and on how many maps. */
data class TagCount(val name: String, val count: Int)

/** Every tag the maps have, once each whatever its capitals, in the order of the phone's language. */
internal fun tagCounts(maps: List<MapSummary>, collator: Collator = Collator.getInstance()): List<TagCount> =
    maps.flatMap { it.outline.tags }
        .groupBy(MapTags::key)
        .values
        .map { spellings -> TagCount(spellings.groupingBy { it }.eachCount().maxBy { it.value }.key, spellings.size) }
        .sortedWith(compareBy(collator) { it.name })

/** Whether a map has a tag, whatever its capitals. */
internal fun MapSummary.hasTag(tag: String): Boolean = outline.tags.any { MapTags.key(it) == MapTags.key(tag) }

/** The tag whose maps Home shows, as most maps spell it; null when no map has it any more (renamed elsewhere, say), so every map shows. */
internal fun shownTag(tags: List<TagCount>, picked: String?): String? =
    picked?.let { tag -> tags.firstOrNull { MapTags.key(it.name) == MapTags.key(tag) }?.name }

data class HomeState(
    val loading: Boolean = true,
    val pinned: List<MapSummary> = emptyList(),

    /** Every map that isn't pinned, in the chosen order. */
    val others: List<MapSummary> = emptyList(),

    /** While searching: the maps that match. */
    val results: List<SearchHit>? = null,

    /** What [results] match: the search field can be a letter ahead while they're found. */
    val searched: String = "",

    val sortOrder: SortOrder = SortOrder.Edited,

    /** Every tag on the maps, with how many maps have it. */
    val tags: List<TagCount> = emptyList(),

    /** The tag whose maps show, when one is picked: the others are hidden. */
    val tag: String? = null,
) {
    val isEmpty: Boolean get() = !loading && pinned.isEmpty() && others.isEmpty()
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val maps = container.maps
    private val search = MutableStateFlow("")
    private val problemMessages = Channel<String>(Channel.BUFFERED)

    val query: StateFlow<String> = search.asStateFlow()

    /** Why something asked of the maps didn't happen, to say on screen: a map that couldn't be added, say. */
    val problems: Flow<String> = problemMessages.receiveAsFlow()

    val state: StateFlow<HomeState> = homeStates(maps.maps, search, container.settings.settings, container.shownTag)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun search(text: String) {
        search.value = text
    }

    /** Shows only the maps with `tag`, or every map with null. */
    fun showTag(tag: String?) {
        container.shownTag.value = tag
    }

    /** A new map, with the tag whose maps show, if one is picked. */
    fun create(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val style = container.settings.settings.first().newMapStyle
            added({ maps.create(title, style, tags = newMapTags()) }, onCreated)
        }
    }

    /** A new map from a template's text, with the tag whose maps show, if one is picked. */
    fun start(markdown: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { added({ maps.add(MapTags.write(markdown, newMapTags())) }, onCreated) }
    }

    /**
     * The tags a new map starts with: the one whose maps Home shows. Read
     * from the app rather than [state], which only follows the maps while a
     * screen shows them: the screen that starts maps doesn't.
     */
    private fun newMapTags(): List<String> = listOfNotNull(shownTag(tagCounts(maps.maps.value.orEmpty()), container.shownTag.value))

    /** Adds a map with `add`, then hands `then` its id; when it can't be added (a newer Orbit keeps the maps, a full phone), [problems] says why. */
    private suspend fun added(add: suspend () -> String, then: (String) -> Unit) {
        val id = try {
            add()
        } catch (failed: IOException) {
            problemMessages.send(maps.whyNotAdded())
            return
        }

        then(id)
    }

    fun setTags(id: String, tags: List<String>) {
        viewModelScope.launch { maps.setTags(id, tags) }
    }

    /** Renames a tag in every map; the maps shown stay the same. */
    fun renameTag(from: String, to: String) {
        viewModelScope.launch {
            maps.renameTag(from, to)

            if (container.shownTag.value?.let(MapTags::key) == MapTags.key(from)) {
                container.shownTag.value = to
            }
        }
    }

    /** Takes a tag out of every map. */
    fun removeTag(tag: String) {
        viewModelScope.launch {
            maps.renameTag(tag, null)

            if (container.shownTag.value?.let(MapTags::key) == MapTags.key(tag)) {
                container.shownTag.value = null
            }
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch { added({ maps.duplicate(id) }) {} }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { maps.rename(id, title) }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { maps.setPinned(id, pinned) }
    }

    /** Moves a map to the trash, for [restore] to bring back. */
    suspend fun delete(id: String): TrashedMap? {
        container.pictures.forget(id)
        return maps.delete(id)
    }

    fun restore(item: TrashedMap) {
        viewModelScope.launch { maps.restore(item) }
    }

    fun sort(order: SortOrder) {
        viewModelScope.launch { container.settings.update { it.copy(sortOrder = order) } }
    }
}

/**
 * Home as the maps, the search, the settings and the picked tag make it, from the latest of each,
 * in order. Sorting, grouping and searching every map takes a while on a big library, so it runs
 * on `dispatcher`, never on the main thread, where it would hold up each letter typed.
 */
internal fun homeStates(
    maps: Flow<List<MapSummary>?>,
    query: Flow<String>,
    settings: Flow<AppSettings>,
    tag: Flow<String?>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<HomeState> = combine(maps, query, settings, tag, ::homeState).flowOn(dispatcher)

/** Home with these maps: the pinned ones and the others in the chosen order, only the picked tag's, and the ones that match `query`. */
internal fun homeState(list: List<MapSummary>?, query: String, settings: AppSettings, tag: String?): HomeState {
    if (list == null) {
        return HomeState()
    }

    val tags = tagCounts(list)
    val shown = shownTag(tags, tag)

    val ordered = when (settings.sortOrder) {
        SortOrder.Edited -> list.sortedByDescending { it.modified }
        SortOrder.Opened -> list.sortedByDescending { maxOf(it.metadata.opened, it.metadata.created) }
        SortOrder.Created -> list.sortedByDescending { it.metadata.created }
        SortOrder.Title -> list.sortedBy { it.title.lowercase() }
    }

    val sorted = if (shown == null) ordered else ordered.filter { it.hasTag(shown) }

    val words = searchWords(query)
    return HomeState(
        loading = false,
        pinned = sorted.filter { it.metadata.pinned },
        others = sorted.filterNot { it.metadata.pinned },
        results = if (words.isEmpty()) null else search(sorted, words),
        searched = query,
        sortOrder = settings.sortOrder,
        tags = tags,
        tag = shown,
    )
}

/** Maps with every word in their title, text or tags; title matches first. */
private fun search(maps: List<MapSummary>, words: List<String>): List<SearchHit> {
    val hits = maps.mapNotNull { map ->
        val title = foldCase(map.title)
        val text = foldCase(map.searchText)
        val tags = foldCase(map.outline.tags.joinToString("\n"))
        val inTitle = words.all { it in title }

        if (!inTitle && !words.all { it in title || it in text || it in tags }) {
            return@mapNotNull null
        }

        val snippet = if (inTitle) {
            null
        } else {
            map.searchText.lineSequence().firstOrNull { line -> words.any { it in foldCase(line) } }
        }

        SearchHit(map, snippet)
    }

    return hits.sortedByDescending { it.snippet == null }
}
