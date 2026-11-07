package com.hyuchia.orbit.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapEditResult
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.MapTags
import com.hyuchia.orbit.data.SortOrder
import com.hyuchia.orbit.data.TagRename
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.privacy.PrivateView
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.ui.foldCase
import com.hyuchia.orbit.ui.searchWords
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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

/**
 * The tag whose maps Home shows once `from` was renamed `to` in every map,
 * or taken out of them (`to` null), while `shown` was: the maps shown follow
 * it to its new name, or every map shows. When some kept the old tag
 * (`stayed`), Orbit couldn't change them, and they stay shown, as Home says.
 */
internal fun shownAfterRename(shown: String?, from: String, to: String?, stayed: Boolean): String? =
    if (!stayed && shown?.let(MapTags::key) == MapTags.key(from)) to else shown

/**
 * What Home says of the maps it shows that kept a tag renamed or taken out
 * in every map, `kept`, each with why: one is named, unless it's private,
 * and several are counted. The maps can only be read here (`readOnly`, as a
 * newer Orbit keeps them) says so alone. Null when every map changed.
 */
internal fun keptTagMessage(kept: List<Pair<MapSummary, MapEditResult>>, words: Words, readOnly: Boolean): String? {
    if (kept.isEmpty()) {
        return null
    }

    val why = kept.map { it.second }
    val named = kept.singleOrNull()?.first?.takeUnless { it.metadata.isPrivate }?.title
    val count = kept.size

    return when {
        why.all { it == MapEditResult.Newer || it == MapEditResult.Unreadable } ->
            named?.let { words.string(R.string.home_tag_stays_on_map_only_shown, it) } ?: words.plural(R.plurals.home_tag_left_on_maps, count, count)
        readOnly && MapEditResult.Refused in why -> words.string(R.string.data_newer_version_keeps_maps)
        MapEditResult.NotWritten in why ->
            named?.let { words.string(R.string.home_tag_stays_on_map_not_saved, it) } ?: words.plural(R.plurals.home_tag_stays_on_maps_not_saved, count, count)
        else -> named?.let { words.string(R.string.home_tag_stays_on_map_not_changed, it) } ?: words.plural(R.plurals.home_tag_stays_on_maps_not_changed, count, count)
    }
}

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

    /** The private maps that show locked: no title, picture, tags or progress, and left out of searches and tags. */
    val locked: Set<String> = emptySet(),

    /** Private maps show unlocked, and can be locked again now. */
    val canLock: Boolean = false,

    /**
     * Private maps are locked and hidden, as Settings can choose: Home offers to show them, which
     * unlocks them. It offers whether any are there or not, so it never gives away that they are.
     */
    val canShowPrivate: Boolean = false,
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

    val state: StateFlow<HomeState> = homeStates(maps.maps, search, container.settings.settings, container.shownTag, container.privateMaps.view)
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
        viewModelScope.launch { said(container.edits.setTags(id, tags)) }
    }

    /** Renames a tag in every map; the maps shown stay the same, unless some kept it (see [shownAfterRename]). */
    fun renameTag(from: String, to: String) {
        viewModelScope.launch {
            val stayed = said(container.edits.renameTag(from, to))
            container.shownTag.value = shownAfterRename(container.shownTag.value, from, to, stayed)
        }
    }

    /** Takes a tag out of every map, and shows every map unless some kept it (see [shownAfterRename]). */
    fun removeTag(tag: String) {
        viewModelScope.launch {
            val stayed = said(container.edits.renameTag(tag, null))
            container.shownTag.value = shownAfterRename(container.shownTag.value, tag, null, stayed)
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            val copy = try {
                container.edits.duplicate(id)
            } catch (failed: IOException) {
                problemMessages.send(maps.whyNotAdded())
                return@launch
            }

            said(copy.result)
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { said(container.edits.rename(id, title)) }
    }

    /** Says why a change from Home left a map as it was, when it did: [problems] says it. */
    private suspend fun said(result: MapEditResult) {
        val words = container.words

        val why = when (result) {
            MapEditResult.Newer -> words.string(R.string.home_map_from_newer_orbit)
            MapEditResult.Unreadable -> words.string(R.string.home_map_unreadable)
            MapEditResult.KeptChanging -> words.string(R.string.home_map_kept_changing)
            MapEditResult.Refused -> maps.whyNotChanged()
            MapEditResult.NotWritten -> maps.whyNotWritten()
            MapEditResult.Written, MapEditResult.Missing -> null
        }

        why?.let { problemMessages.send(it) }
    }

    /**
     * Says which of the maps Home shows kept a tag renamed or taken out in
     * every map, and why; returns whether any did. A locked private map that
     * kept it goes unsaid, as its tags do on Home.
     */
    private suspend fun said(renamed: TagRename): Boolean {
        val privacy = container.privateMaps.view.value
        val kept = renamed.kept.mapNotNull { (id, why) -> maps.summary(id)?.takeUnless(privacy::hides)?.let { it to why } }
        keptTagMessage(kept, container.words, readOnly = !maps.writable.value)?.let { problemMessages.send(it) }
        return kept.isNotEmpty()
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            try {
                maps.setPinned(id, pinned)
            } catch (failed: IOException) {
                problemMessages.send(maps.whyNotWritten())
            }
        }
    }

    /** Locks every private map now. */
    fun lockPrivateMaps() {
        container.privateMaps.lock.lock()
    }

    /** Moves a map to the trash, for [restore] to bring back. */
    suspend fun delete(id: String): TrashedMap? {
        container.pictures.forget(id)
        return maps.delete(id)
    }

    fun restore(item: TrashedMap) {
        viewModelScope.launch {
            try {
                maps.restore(item)
            } catch (failed: IOException) {
                problemMessages.send(maps.whyNotWritten())
            }
        }
    }

    fun sort(order: SortOrder) {
        viewModelScope.launch { container.settings.update { it.copy(sortOrder = order) } }
    }
}

/**
 * Home as the maps, the search, the settings, the picked tag and private maps make it, from the
 * latest of each, in order. Sorting, grouping and searching every map takes a while on a big
 * library, so it runs on `dispatcher`, never on the main thread, where it would hold up each
 * letter typed.
 */
internal fun homeStates(
    maps: Flow<List<MapSummary>?>,
    query: Flow<String>,
    settings: Flow<AppSettings>,
    tag: Flow<String?>,
    privacy: Flow<PrivateView>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<HomeState> {
    val home = combine(privacy, hidingPrivateMaps(privacy)) { view, hiding -> view to hiding }

    return combine(maps, query, settings, tag, home) { list, searched, chosen, picked, (view, hiding) ->
        homeState(list, searched, chosen, picked, view, hiding)
    }.flowOn(dispatcher)
}

/** How long private maps that lock on screen stay in their place, locked, before they go: long enough to see them lock. */
internal const val PRIVATE_MAPS_GO_AFTER = 320L

/**
 * Whether private maps that Settings hides while locked are gone from Home now. Locking while Home
 * shows, they lock where they are first, and go [PRIVATE_MAPS_GO_AFTER] later; Home's first look,
 * and one after they locked while Orbit was away, has them gone already. Unlocked, they're back at once.
 */
internal fun hidingPrivateMaps(privacy: Flow<PrivateView>): Flow<Boolean> = flow {
    var first = true

    emitAll(
        privacy.distinctUntilChangedBy { it.locked }.transformLatest { view ->
            if (view.locked && !first && !view.lockedAway) {
                delay(PRIVATE_MAPS_GO_AFTER)
            }

            first = false
            emit(view.locked)
        },
    )
}

/**
 * Home with these maps: the pinned ones and the others in the chosen order, only the picked tag's,
 * and the ones that match `query`. Private maps that are locked keep their place, but give nothing
 * away: their tags aren't among Home's, a picked tag leaves them out, and searches never find them.
 * Or, when Settings hides them, they aren't on Home at all until they're unlocked: not in the list,
 * searches, tags or counts, once they're `hiding` (see [hidingPrivateMaps]).
 */
internal fun homeState(
    maps: List<MapSummary>?,
    query: String,
    settings: AppSettings,
    tag: String?,
    privacy: PrivateView = PrivateView(locked = false),
    hiding: Boolean = privacy.locked,
): HomeState {
    if (maps == null) {
        return HomeState()
    }

    val list = if (settings.hideLockedPrivateMaps && hiding) maps.filterNot(privacy::hides) else maps
    val open = list.filterNot(privacy::hides)
    val tags = tagCounts(open)
    val shown = shownTag(tags, tag)

    val ordered = when (settings.sortOrder) {
        SortOrder.Edited -> list.sortedByDescending { it.modified }
        SortOrder.Opened -> list.sortedByDescending { maxOf(it.metadata.opened, it.metadata.created) }
        SortOrder.Created -> list.sortedByDescending { it.metadata.created }
        // Locked private maps go after the others, by when they were made: placed by their titles, they'd hint at them.
        SortOrder.Title -> list.sortedWith(compareBy<MapSummary> { privacy.hides(it) }.thenBy { if (privacy.hides(it)) "" else it.title.lowercase() }.thenBy { it.metadata.created })
    }

    val sorted = if (shown == null) ordered else ordered.filter { it.hasTag(shown) && !privacy.hides(it) }

    val words = searchWords(query)
    return HomeState(
        loading = false,
        pinned = sorted.filter { it.metadata.pinned },
        others = sorted.filterNot { it.metadata.pinned },
        results = if (words.isEmpty()) null else search(sorted.filterNot(privacy::hides), words),
        searched = query,
        sortOrder = settings.sortOrder,
        tags = tags,
        tag = shown,
        locked = sorted.filter(privacy::hides).mapTo(mutableSetOf()) { it.id },
        canLock = privacy.lockable && list.any { it.metadata.isPrivate && !privacy.hides(it) },
        canShowPrivate = privacy.conceals(settings.hideLockedPrivateMaps),
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
