package com.hyuchia.orbit.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.SortOrder
import com.hyuchia.orbit.data.TrashedMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A map that matches a search, with the line that matched when it isn't the title. */
data class SearchHit(val map: MapSummary, val snippet: String?)

data class HomeState(
    val loading: Boolean = true,
    val pinned: List<MapSummary> = emptyList(),

    /** Every map that isn't pinned, in the chosen order. */
    val others: List<MapSummary> = emptyList(),

    /** Maps opened lately, newest first, for the shortcuts row. */
    val recent: List<MapSummary> = emptyList(),

    /** While searching: the maps that match. */
    val results: List<SearchHit>? = null,

    val sortOrder: SortOrder = SortOrder.Edited,
) {
    val isEmpty: Boolean get() = !loading && pinned.isEmpty() && others.isEmpty()
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val maps = container.maps
    private val search = MutableStateFlow("")

    val query: StateFlow<String> = search.asStateFlow()

    val state: StateFlow<HomeState> = combine(maps.maps, search, container.settings.settings) { list, query, settings ->
        homeState(list, query, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun search(text: String) {
        search.value = text
    }

    fun create(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val style = container.settings.settings.first().newMapStyle
            onCreated(maps.create(title, style))
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch { maps.duplicate(id) }
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

    private fun homeState(list: List<MapSummary>?, query: String, settings: AppSettings): HomeState {
        if (list == null) {
            return HomeState()
        }

        val sorted = when (settings.sortOrder) {
            SortOrder.Edited -> list.sortedByDescending { it.modified }
            SortOrder.Opened -> list.sortedByDescending { maxOf(it.metadata.opened, it.metadata.created) }
            SortOrder.Created -> list.sortedByDescending { it.metadata.created }
            SortOrder.Title -> list.sortedBy { it.title.lowercase() }
        }

        val words = query.lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return HomeState(
            loading = false,
            pinned = sorted.filter { it.metadata.pinned },
            others = sorted.filterNot { it.metadata.pinned },
            recent = list.filter { it.metadata.opened > 0 }.sortedByDescending { it.metadata.opened }.take(6),
            results = if (words.isEmpty()) null else search(sorted, words),
            sortOrder = settings.sortOrder,
        )
    }

    /** Maps with every word in their title or text; title matches first. */
    private fun search(maps: List<MapSummary>, words: List<String>): List<SearchHit> {
        val hits = maps.mapNotNull { map ->
            val title = map.title.lowercase()
            val text = map.searchText.lowercase()
            val inTitle = words.all { it in title }

            if (!inTitle && !words.all { it in title || it in text }) {
                return@mapNotNull null
            }

            val snippet = if (inTitle) {
                null
            } else {
                map.searchText.lineSequence().firstOrNull { line -> words.any { it in line.lowercase() } }
            }

            SearchHit(map, snippet)
        }

        return hits.sortedByDescending { it.snippet == null }
    }
}
