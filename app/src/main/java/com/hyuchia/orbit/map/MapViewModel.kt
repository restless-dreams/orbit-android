package com.hyuchia.orbit.map

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.Checkpoint
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.MediaRefusedException
import com.hyuchia.orbit.data.WriteResult
import com.hyuchia.orbit.media.GalleryItem
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.galleryOf
import com.hyuchia.orbit.media.indexOf
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.Diagnostic
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.OrbitPage
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PictureRequest
import com.hyuchia.orbit.page.WebPage
import com.hyuchia.orbit.page.mapBaseUrl
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.ShownMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal

/** How far back undo goes. */
private const val HISTORY = 100

/** How long a map waits to hear what the screen covers before drawing anyway. */
private const val INSETS_WAIT_MILLISECONDS = 1_000L

/** A rehearsal shorter than this was stopped at once, and keeps nothing. */
private const val REHEARSAL_MINIMUM_MILLISECONDS = 3_000L

/** What sits over the map: one menu or panel at a time. */
sealed interface Panel {
    /** The long-press menu, at (`x`, `y`) dp from the map's top left. */
    data class Menu(val id: String, val x: Float, val y: Float) : Panel

    data class Editor(val id: String) : Panel
    data class Details(val id: String) : Panel
    data object Outline : Panel
    data object Search : Panel
    data object Style : Panel
    data object Export : Panel
    data object History : Panel
}

/**
 * The version history, while it's open: the versions kept (null while
 * they're read), the one on the map (null: the map as it is now), and how
 * many ideas the map has now, to compare.
 */
data class HistoryUi(val versions: List<MapVersion>? = null, val showing: Long? = null, val ideasNow: Int = 0)

/** The map as text, while the source editor is open. */
data class SourceState(
    val text: String,
    val yaml: Boolean = false,
    val diagnostics: List<Diagnostic> = emptyList(),

    /** Converting between markdown and YAML. */
    val converting: Boolean = false,
)

/**
 * A presentation: the map's ideas along its path, or in reading order, one
 * at a time, with each note as its caption unless `captions` is off.
 * `startedAt` is [SystemClock.elapsedRealtime] when it began, for the
 * presenter's clock.
 */
data class Presentation(
    val order: List<String>,
    val index: Int,
    val playing: Boolean = false,
    val captions: Boolean = true,
    val startedAt: Long = SystemClock.elapsedRealtime(),

    /** When the idea on screen came up. */
    val stepStartedAt: Long = startedAt,

    /** A rehearsal: each idea's time is kept, for the pace of the next presentation. */
    val rehearsing: Boolean = false,

    /** Seconds the last rehearsal spent on each idea, when there was one. */
    val planned: Map<String, Int>? = null,

    /** Where the presentation began, for its pace. */
    val firstIndex: Int = index,

    /** The idea's picture or video the audience sees big over the map, and where a video is. */
    val media: ShownMedia? = null,
) {
    val current: String get() = order[index]
    val next: String? get() = order.getOrNull(index + 1)

    /** Seconds spent on the idea on screen, at `now` ([SystemClock.elapsedRealtime]). */
    fun onStep(now: Long): Int = ((now - stepStartedAt) / 1_000).toInt()

    /** Seconds the last rehearsal spent on the idea on screen, if it got to it. */
    val plannedForStep: Int? get() = planned?.get(current)

    /**
     * How far behind the last rehearsal's pace the presentation is at `now`,
     * in seconds; ahead when negative, null without a rehearsal. An idea
     * only puts the presenter behind once it runs over its rehearsed time.
     */
    fun behind(now: Long): Int? {
        val planned = planned?.takeIf { times -> order.any { it in times } } ?: return null
        val plannedBefore = (firstIndex until index).sumOf { planned[order[it]] ?: 0 }
        val plannedNow = plannedBefore + minOf(onStep(now), planned[current] ?: 0)
        return ((now - startedAt) / 1_000).toInt() - plannedNow
    }
}

/** How a rehearsal went: the ideas in the order they came up, and the seconds spent on each. */
data class Rehearsal(val order: List<String>, val seconds: Map<String, Int>) {
    val total: Int get() = seconds.values.sum()
}

/** A video on its way into the map from the phone, for idea `nodeId`: how far along the copy is, from 0 to 1, or null while that isn't known. */
data class VideoCopy(val nodeId: String, val progress: Float? = null)

/**
 * Text for the screen to show, read from the app's strings as it's shown, so
 * it's in the app's language: one of its strings, one that changes with a
 * count, or words that come already written, as the page's do.
 */
sealed interface ShownText {
    /** One of the app's strings, with what goes in its placeholders. */
    data class Resource(@StringRes val id: Int, val arguments: List<Any> = emptyList()) : ShownText

    /** One of the app's strings for a count, in the form the language uses for it. */
    data class Plural(@PluralsRes val id: Int, val count: Int, val arguments: List<Any> = listOf(count)) : ShownText

    /** Words that come already written, such as the page's own. */
    data class Plain(val words: String) : ShownText

    /** The text, in the language of `resources`. */
    fun text(resources: Resources): String = when (this) {
        is Resource -> if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *arguments.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, *arguments.toTypedArray())
        is Plain -> words
    }
}

/** Why a picture or video from the phone didn't join idea `nodeId`, for its editor to say. */
data class MediaProblem(val nodeId: String, val video: Boolean, val message: ShownText)

/** The map's pictures and videos full screen, as they were when the viewer opened, from the one at `start`. */
data class Viewing(val items: List<GalleryItem>, val start: Int)

/** Why a map can only be looked at here. */
enum class ReadOnly {
    /** Orbit couldn't read all of the map's text: changing it would lose what wasn't read. */
    Unreadable,

    /** A newer version of Orbit made the map, maybe with things this one doesn't know. */
    Newer,

    /** The map went to the trash while it was open. */
    Deleted,

    /** A newer version of Orbit keeps the app's files. */
    Storage,
}

data class MapUi(
    val ready: Boolean = false,

    /** The page's process stopped: the map is gone until it loads again. */
    val stopped: Boolean = false,

    val error: ShownText? = null,
    val map: MapState? = null,

    /** The field for typing on the map is open. */
    val editing: Boolean = false,

    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val panel: Panel? = null,
    val source: SourceState? = null,
    val presentation: Presentation? = null,

    /** Choosing the presentation's path: each new idea flown to joins it. */
    val pathMaking: Boolean = false,

    /** The ideas to present, in order; empty presents them all in reading order. */
    val presentPath: List<String> = emptyList(),

    /** The rehearsal just finished, until its summary is closed. */
    val rehearsal: Rehearsal? = null,

    /** The node a search or the outline just flew to, to point out on the map. */
    val found: String? = null,

    /** Goes up with every change to the map, for previews of it. */
    val revision: Int = 0,

    /** The version history, while it's open. */
    val history: HistoryUi? = null,

    /** Why the map can only be looked at, when it can't be changed. */
    val readOnly: ReadOnly? = null,

    /** What Orbit couldn't read, in its words, when that's why. */
    val readProblem: String? = null,

    /** A video being copied in from the phone. */
    val videoCopy: VideoCopy? = null,

    /** Why the last picture or video from the phone didn't join its idea. */
    val mediaProblem: MediaProblem? = null,

    /** The map's pictures and videos, full screen. */
    val viewing: Viewing? = null,

    /** The video playing full screen, over the pictures when it was started from them. */
    val playing: GalleryItem? = null,
) {
    /** Whether a picture or video is full screen on this phone. */
    val fullScreen: Boolean get() = viewing != null || playing != null

    val nodes: Map<String, MapNode> by lazy { map?.nodes?.associateBy { it.id }.orEmpty() }

    /** How the map's progress rings count, and which ideas show one. */
    val progress: MapProgress by lazy { MapProgress.of(map) }

    /** Whether the idea's details have something to show beyond its title: its ring's progress counts. */
    fun hasDetails(node: MapNode): Boolean = node.hasDetails || progress.followsItsIdeas(node)

    /** Whether any idea has a picture or a video. */
    val hasMedia: Boolean by lazy { map?.nodes?.any { it.image != null || it.video != null } == true }

    val path: List<MapNode> by lazy { map?.path?.mapNotNull { nodes[it] }.orEmpty() }
    val sun: MapNode? get() = path.lastOrNull()
    val mode: String get() = map?.mode ?: "focus"
}

/** One-off things the screen does for the map. */
sealed interface MapEvent {
    data object Tick : MapEvent
    data object LongPress : MapEvent
    data object Added : MapEvent

    /** An idea dragged somewhere else landed there. */
    data object Moved : MapEvent

    /** A goal just got where it was going. */
    data object GoalReached : MapEvent

    /** A message; with `undo`, it offers to undo the last change, or to run `onUndo` instead when there is one. */
    data class Snackbar(val message: ShownText, val undo: Boolean = false, val onUndo: (() -> Unit)? = null) : MapEvent

    data class Open(val url: String) : MapEvent
    data object Deleted : MapEvent
}

/** A text to save, and the catch-up it was made before: one made before the map changed elsewhere isn't written. */
private class Save(val text: String, val generation: Int)

class MapViewModel(private val container: AppContainer, val id: String) : ViewModel() {
    private val maps = container.maps
    private val state = MutableStateFlow(MapUi())
    private val events = Channel<MapEvent>(Channel.BUFFERED)

    val ui: StateFlow<MapUi> = state.asStateFlow()
    val effects: Flow<MapEvent> = events.receiveAsFlow()

    /** The WebView drawing the map; it stays with the map while the screen comes and goes. */
    var page: OrbitPage = container.pages.take()
        private set

    /** Bumped when the page is replaced, so the screen shows the new one. */
    val pageGeneration = MutableStateFlow(0)

    /** The map as it is now. */
    private var markdown = ""

    /** The map's file as this screen last wrote or read it: a save is only written while the file is still that. */
    @Volatile
    private var written = ""

    private var deleted = false
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private val saves = Channel<Save>(Channel.CONFLATED)

    /** Goes up whenever the screen catches up with a change made elsewhere: saves made before it aren't written. */
    private var generation = 0

    /**
     * The next save keeps the map as it was in its history first: the first
     * one of a visit, and the ones before big changes (an idea removed with
     * the ones under it, a version brought back, the text edited by hand).
     */
    private var checkpointNext = true

    private var theme: AppTheme? = null
    private var settings: MapSettings? = null
    private var insets = MapInsets()

    /** Done once the screen has said what it covers, so the map's first view fits around it from the start. */
    private val insetsKnown = CompletableDeferred<Unit>()

    private var loadedOnce = false

    /** The map before the source editor opened, to undo it in one step. */
    private var sourceStart: String? = null

    private var sourceJob: Job? = null

    /** The presentation as it goes: its steps, its timer, what the audience sees over the map, and the audiences. */
    private val control = PresentationControl(
        scope = viewModelScope,
        ui = state,
        clock = SystemClock::elapsedRealtime,
        focus = { idea -> page.send(MapCommand.Focus(idea)) },
        map = { presentation -> theme?.let { AudienceCommand.Show(markdown, it, mapBaseUrl(id), presentation.current, presentation.captions) } },
        leaving = { presentation, now ->
            if (presentation.rehearsing) {
                rehearsed[presentation.current] = (rehearsed[presentation.current] ?: 0L) + now - presentation.stepStartedAt
            }
        },
        length = ::videoLength,
    )

    /** The presentation's path, as it's being made or since it was saved. */
    private var path: List<String> = emptyList()

    /** A rehearsal's time on each idea so far, in milliseconds. */
    private val rehearsed = mutableMapOf<String, Long>()

    /** Where an idea being dragged came from: under which idea, to tell it moved somewhere else. */
    private var dragFrom: String? = null

    /** Ideas said in the talk going on or just over, and how many of them the map has taken in. */
    private var spokenSent = 0

    private var spokenSaved = 0

    /** The video being copied in from the phone, and how many copies have started, so one replaced doesn't clear the next one's progress. */
    private var videoJob: Job? = null

    private var videoCopies = 0

    init {
        page.listener = ::receive

        viewModelScope.launch {
            for (save in saves) {
                write(save)
            }
        }

        maps.showing(id, true)
        viewModelScope.launch { maps.markOpened(id) }

        // A newer version from elsewhere (another device, the folder, ideas caught on the go) replaced this map:
        // it shows at once, and undo brings this one back.
        viewModelScope.launch {
            maps.replaced.filter { it == id }.collect {
                runCatching { maps.read(id) }.getOrNull()?.let(::catchUp)
            }
        }

        // Deleted while it's open (from the list beside it, or by the folder): nothing more is changed, until it's restored.
        viewModelScope.launch {
            maps.maps.filterNotNull().map { list -> list.any { it.id == id } }.distinctUntilChanged().collect { here ->
                if (!here && !deleted) {
                    page.send(MapCommand.Lock(LOCK_DELETED))
                } else if (here && state.value.readOnly == ReadOnly.Deleted) {
                    page.send(MapCommand.Lock(null))
                    load()
                }
            }
        }

        // A newer Orbit keeps the app's files: maps are only shown.
        viewModelScope.launch {
            maps.writable.collect { writable ->
                if (!writable) {
                    page.send(MapCommand.Lock(LOCK_STORAGE))
                }
            }
        }
    }

    /**
     * Writes a save, unless the map changed elsewhere since it was made: its
     * text then goes into the history, and the screen shows the map as it is.
     */
    private suspend fun write(save: Save) {
        if (save.generation != generation) {
            maps.keepAside(id, save.text)
            return
        }

        val checkpoint = if (checkpointNext) Checkpoint.Always else Checkpoint.Interval
        checkpointNext = false

        when (maps.write(id, save.text, base = written, checkpoint = checkpoint)) {
            WriteResult.Written -> written = save.text
            WriteResult.Changed -> {
                events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_changed_elsewhere)))
                runCatching { maps.read(id) }.getOrNull()?.let(::catchUp)
            }
            WriteResult.Refused -> {
                // Text that would have emptied the map: the map stays as it was, and so does the screen.
                events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_change_not_saved)))
                runCatching { maps.read(id) }.getOrNull()?.let(::catchUp)
            }
            WriteResult.Missing -> if (!deleted) {
                page.send(MapCommand.Lock(LOCK_DELETED))
            }
        }
    }

    /** Shows the map's file as it is now, after a change made elsewhere: what was on screen goes on the undo stack. */
    private fun catchUp(text: String) {
        generation += 1

        if (text == markdown) {
            written = text
            return
        }

        remember(markdown)
        markdown = text
        written = text
        checkpointNext = true
        state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        page.send(MapCommand.Restore(text))
    }

    /** Saves text the map has now: written soon, once the saves before it are. */
    private fun save(text: String) {
        saves.trySend(Save(text, generation))
    }

    /** The parts of the map the screen covers with its toolbar, sheets or captions: Orbit fits views around them. */
    fun setInsets(insets: MapInsets) {
        val changed = insets != this.insets
        this.insets = insets
        insetsKnown.complete(Unit)

        if (changed) {
            page.send(MapCommand.Insets(insets))
        }
    }

    /** The app's colors and settings; the first call draws the map. */
    fun configure(theme: AppTheme, settings: MapSettings) {
        val themeChanged = theme != this.theme
        val settingsChanged = settings != this.settings
        this.theme = theme
        this.settings = settings

        if (!loadedOnce) {
            loadedOnce = true
            viewModelScope.launch { load() }
            return
        }

        if (themeChanged) {
            page.send(MapCommand.Theme(theme))
        }

        if (settingsChanged) {
            page.send(MapCommand.Settings(settings))
        }
    }

    private suspend fun load() {
        val text = try {
            maps.read(id)
        } catch (missing: java.io.FileNotFoundException) {
            state.update { it.copy(error = ShownText.Resource(R.string.map_missing)) }
            return
        } catch (unreadable: java.io.IOException) {
            state.update { it.copy(error = ShownText.Resource(R.string.map_unreadable_file)) }
            return
        }

        generation += 1
        markdown = text
        written = text
        checkpointNext = true
        val metadata = maps.summary(id)?.metadata
        val pathMaking = state.value.pathMaking

        if (!pathMaking) {
            setPath(metadata?.path.orEmpty())
        }

        // Drawn before the screen says what it covers, the map would glide out from under the toolbar once it did.
        withTimeoutOrNull(INSETS_WAIT_MILLISECONDS) { insetsKnown.await() }
        page.send(
            MapCommand.Load(
                markdown = text,
                theme = theme ?: return,
                settings = settings ?: return,
                insets = insets,
                baseUrl = mapBaseUrl(id),
                focus = metadata?.focus,
                view = metadata?.view,
                mapId = id,
            ),
        )

        if (!maps.writable.value) {
            page.send(MapCommand.Lock(LOCK_STORAGE))
        }

        // A page drawn again while the path is made stays still to edits, like the one before it.
        if (pathMaking) {
            page.send(MapCommand.Present(true))
        }
    }

    /** After the page's process stopped: a new page, and the map drawn again. */
    fun reload() {
        container.pages.give(page)
        page = container.pages.take()
        page.listener = ::receive
        pageGeneration.value += 1
        state.update { it.copy(stopped = false, ready = false, editing = false, panel = null) }
        viewModelScope.launch { load() }
    }

    private fun receive(message: MapMessage) {
        when (message) {
            MapMessage.Hello -> Unit
            is MapMessage.Ready -> state.update { it.copy(ready = true, map = message.state, error = null).reading(message.state) }
            is MapMessage.Saved -> saved(message)
            is MapMessage.State -> state.update { it.copy(map = message.state).reading(message.state) }
            is MapMessage.Focus -> {
                state.update { ui -> ui.copy(map = ui.map?.copy(path = message.path, mode = message.mode)) }
                rememberPlace(message.path.lastOrNull(), message.mode)
                // Making a path, each new idea flown to joins it; flying back to one on it already, to go on from there, doesn't.
                val reached = message.path.lastOrNull()

                if (state.value.pathMaking && message.mode == "focus" && reached != null && reached !in path) {
                    setPath(path + reached)
                }
            }
            // Making a path, or on a map that can't be changed, the map stays still to edits, and has no menu of them.
            MapMessage.Held -> if (!state.value.pathMaking && state.value.readOnly == null) {
                events.trySend(MapEvent.LongPress)
            }
            is MapMessage.Context -> if (!state.value.pathMaking && state.value.readOnly == null) {
                state.update { it.copy(panel = Panel.Menu(message.id, message.x, message.y)) }
            }
            is MapMessage.Drag -> dragged(message)
            is MapMessage.Details -> state.update { it.copy(panel = Panel.Details(message.id)) }
            is MapMessage.EditorOpen -> state.update { it.copy(editing = true, panel = null) }
            MapMessage.EditorClose -> state.update { it.copy(editing = false) }
            is MapMessage.Source -> state.update { ui -> ui.copy(source = ui.source?.copy(diagnostics = message.diagnostics)) }
            is MapMessage.Open -> events.trySend(MapEvent.Open(message.url))
            MapMessage.Haptic -> events.trySend(MapEvent.Tick)
            is MapMessage.Error -> {
                Logs.warn("OrbitMap", "The page had a problem: ${message.message}")
                state.update { it.copy(error = ShownText.Plain(message.message)) }
            }
            MapMessage.Stopped -> state.update { it.copy(ready = false, editing = false, panel = null, stopped = true) }
            // Only an audience's screen says how its video goes.
            is MapMessage.Result, is MapMessage.MediaState -> Unit
        }
    }

    private fun saved(message: MapMessage.Saved) {
        // The page never saves a map that can't be changed; this screen doesn't either.
        if (message.state.readOnly != null || state.value.readOnly != null) {
            return
        }

        // A talk's ideas go back with one undo: only the first keeps the map as it was before them.
        val spoken = message.kind == "add" && spokenSaved < spokenSent

        if (spoken) {
            spokenSaved += 1
        }

        if (!(spoken && spokenSaved > 1)) {
            remember(markdown)
        }

        // An idea removed with ideas under it: the map as it was goes into the history first.
        val before = state.value.map?.nodes?.size ?: 0

        if (message.kind == "remove" && before - message.state.nodes.size > 1) {
            checkpointNext = true
        }

        message.renamed?.let { (from, to) -> follow(from, to) }
        val reached = message.kind == "update" && reachedGoal(state.value.nodes, message.state.nodes)
        markdown = message.markdown
        save(message.markdown)

        if (message.kind == "add") {
            events.trySend(MapEvent.Added)
        }

        if (reached) {
            events.trySend(MapEvent.GoalReached)
        }

        // Deleted from the keyboard: the same Undo as the menu's Delete.
        message.removed?.let { text -> events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_deleted_idea, listOf(text)), undo = true)) }

        state.update { ui ->
            ui.copy(
                map = message.state,
                revision = ui.revision + 1,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                // The source editor follows edits made on the map.
                source = ui.source?.takeIf { !it.yaml }?.copy(text = message.markdown) ?: ui.source,
            )
        }
    }

    /**
     * An idea dragged somewhere else on the map: it ticks over each place it
     * could land, and lands with a tap; moved under another idea, where it
     * may no longer show, it says so, with Undo. (The long press's menu
     * never opened: it waits for the finger to lift without moving.)
     */
    private fun dragged(message: MapMessage.Drag) {
        when (message.phase) {
            "start" -> dragFrom = message.parentId
            "over" -> if (message.parentId != null) {
                events.trySend(MapEvent.Tick)
            }
            "end" -> {
                val from = dragFrom
                dragFrom = null
                val into = message.parentId ?: return
                events.trySend(MapEvent.Moved)

                if (into != from) {
                    val nodes = state.value.nodes
                    val idea = nodes[message.id]?.label(untitled())
                    val parent = nodes[into]?.label(untitled())

                    val moved = when {
                        idea != null && parent != null -> ShownText.Resource(R.string.map_moved_idea_into_idea, listOf(idea, parent))
                        parent != null -> ShownText.Resource(R.string.map_moved_the_idea_into_idea, listOf(parent))
                        idea != null -> ShownText.Resource(R.string.map_moved_idea_into_another_idea, listOf(idea))
                        else -> ShownText.Resource(R.string.map_moved_the_idea_into_another_idea)
                    }

                    events.trySend(MapEvent.Snackbar(moved, undo = true))
                }
            }
        }
    }

    /** What an idea without text is called, in the app's language, for messages and the viewer. */
    private fun untitled(): String = container.words.string(R.string.common_untitled)

    /** An open panel, the path and the rehearsal keep their idea when new text gives the idea a new id. */
    private fun follow(from: String, to: String) {
        if (from in path) {
            setPath(path.map { if (it == from) to else it })
        }

        val metadata = maps.summary(id)?.metadata

        if (from in metadata?.path.orEmpty() || metadata?.rehearsal?.containsKey(from) == true) {
            viewModelScope.launch { maps.renameIdea(id, from, to) }
        }

        state.update { ui ->
            val panel = when (val current = ui.panel) {
                is Panel.Editor -> if (current.id == from) Panel.Editor(to) else current
                is Panel.Details -> if (current.id == from) Panel.Details(to) else current
                is Panel.Menu -> if (current.id == from) current.copy(id = to) else current
                else -> current
            }

            ui.copy(panel = panel)
        }
    }

    /** Keeps a version to come back to with undo. */
    private fun remember(version: String) {
        if (version.isEmpty() || undoStack.lastOrNull() == version) {
            return
        }

        undoStack.addLast(version)

        while (undoStack.size > HISTORY) {
            undoStack.removeFirst()
        }

        redoStack.clear()
    }

    private var placeJob: Job? = null

    private fun rememberPlace(focus: String?, view: String) {
        placeJob?.cancel()

        placeJob = viewModelScope.launch {
            delay(600)
            maps.savePlace(id, focus, view)
        }
    }

    // What the screen asks for

    fun send(command: MapCommand) = page.send(command)

    fun focus(nodeId: String, point: Boolean = false) {
        page.send(MapCommand.Focus(nodeId))

        if (point) {
            state.update { it.copy(found = nodeId) }

            viewModelScope.launch {
                delay(1_600)
                state.update { ui -> if (ui.found == nodeId) ui.copy(found = null) else ui }
            }
        }
    }

    fun goUp() {
        val ui = state.value

        if (ui.mode == "all") {
            page.send(MapCommand.View("focus"))
        } else {
            ui.path.getOrNull(ui.path.size - 2)?.let { page.send(MapCommand.Focus(it.id)) }
        }
    }

    fun toggleView() {
        page.send(MapCommand.View(if (state.value.mode == "all") "focus" else "all"))
    }

    /** Opens the field for a new idea in the orbit of `parentId` (default: the node in focus). */
    fun addIdea(parentId: String? = null, index: Int? = null) {
        val parent = parentId ?: state.value.sun?.id ?: state.value.map?.root?.id ?: return
        state.update { it.copy(panel = null) }
        page.send(MapCommand.EditNew(parent, index))
    }

    fun rename(nodeId: String) {
        state.update { it.copy(panel = null) }
        page.send(MapCommand.Edit(nodeId))
    }

    /** Sets attributes; `null` removes one. */
    fun update(nodeId: String, changes: Map<String, JsonElement?>) {
        page.send(MapCommand.Update(nodeId, JsonObject(changes.mapValues { it.value ?: JsonNull })))
    }

    fun update(nodeId: String, key: String, value: JsonElement?) = update(nodeId, mapOf(key to value))

    /** Sets a goal's numbers or unit, keeping the idea's other attributes; `JsonNull` removes one. */
    fun setGoal(nodeId: String, changes: Map<String, JsonElement>) {
        if (changes.isNotEmpty()) {
            update(nodeId, "attrs", JsonObject(changes))
        }
    }

    /** Adds to a goal's number, or takes off below 0: an edit like any other, saved and undone the same way. */
    fun addToGoal(nodeId: String, amount: BigDecimal) {
        page.send(MapCommand.AddToGoal(nodeId, number(amount)))
    }

    fun remove(nodeId: String) {
        val node = state.value.nodes[nodeId] ?: return
        state.update { it.copy(panel = null) }
        page.send(MapCommand.Remove(nodeId))
        events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_deleted_idea, listOf(node.label(untitled()))), undo = true))
    }

    fun duplicate(nodeId: String) {
        state.update { it.copy(panel = null) }
        page.send(MapCommand.Duplicate(nodeId))
    }

    fun move(nodeId: String, parentId: String, index: Int?) {
        page.send(MapCommand.Move(nodeId, parentId, index))
    }

    fun setOptions(changes: Map<String, JsonElement?>) {
        page.send(MapCommand.Options(JsonObject(changes.mapValues { it.value ?: JsonNull })))
    }

    fun undo() {
        if (state.value.readOnly != null) {
            return
        }

        val version = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(markdown)
        restore(version)
    }

    fun redo() {
        if (state.value.readOnly != null) {
            return
        }

        val version = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(markdown)
        restore(version)
    }

    /** Makes `version` the map again: drawn, and saved exactly as it is. */
    private fun restore(version: String) {
        state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        markdown = version
        page.send(MapCommand.Restore(version))
        save(version)
    }

    fun show(panel: Panel?) {
        if (panel != state.value.panel) {
            state.update { it.copy(mediaProblem = null) }
        }

        // Leaving the version history: the map as it is now comes back.
        val history = state.value.history

        if (history != null && panel !is Panel.History) {
            if (history.showing != null) {
                page.send(MapCommand.Preview(markdown, editable = true))
            }

            state.update { it.copy(history = null) }
        }

        state.update { it.copy(panel = panel) }
    }

    // Version history

    /** Opens the version history: the versions kept, newest first, with the map as it is now showing. */
    fun openHistory() {
        stopEditing(save = true)
        state.update { it.copy(panel = Panel.History, history = HistoryUi(ideasNow = MapText.outline(markdown, container.words).ideas)) }

        viewModelScope.launch {
            val versions = maps.versions(id)
            state.update { ui -> ui.history?.let { ui.copy(history = it.copy(versions = versions)) } ?: ui }
        }
    }

    /** Shows a version on the map, to look at; null shows the map as it is now. */
    fun showVersion(time: Long?) {
        if (state.value.history?.showing == time) {
            return
        }

        viewModelScope.launch {
            val text = if (time == null) markdown else maps.readVersion(id, time) ?: return@launch
            state.update { ui -> ui.history?.let { ui.copy(history = it.copy(showing = time)) } ?: ui }
            page.send(MapCommand.Preview(text, editable = time == null))
        }
    }

    /** Makes a version the map again: the map as it is now goes into the history first, and Undo brings it back. */
    fun restoreVersion(time: Long) {
        viewModelScope.launch {
            val text = maps.readVersion(id, time) ?: return@launch

            // What's on screen and not written yet goes into the history too, so nothing is lost on the way.
            if (written != markdown) {
                maps.keepAside(id, markdown)
            }

            maps.keepCurrentVersion(id)
            remember(markdown)
            state.update { it.copy(panel = null, history = null, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
            markdown = text
            page.send(MapCommand.Restore(text))
            save(text)
            events.trySend(MapEvent.Snackbar(restoredMessage(time), undo = true))
        }
    }

    fun stopEditing(save: Boolean = true) = page.send(MapCommand.StopEditing(save))

    /** Copies a picture into the map's folder and puts it on the node. */
    fun addPicture(nodeId: String, uri: Uri, resolver: ContentResolver) {
        state.update { it.copy(mediaProblem = null) }

        viewModelScope.launch {
            val path = try {
                maps.addPicture(id, uri, resolver)
            } catch (refused: MediaRefusedException) {
                mediaProblem(MediaProblem(nodeId, video = false, ShownText.Plain(refused.message.orEmpty())))
                return@launch
            } catch (unreadable: Exception) {
                Logs.warn("OrbitMap", "Couldn't read the picture", unreadable)
                mediaProblem(MediaProblem(nodeId, video = false, ShownText.Resource(R.string.map_picture_unreadable)))
                return@launch
            }

            update(nodeId, "image", buildJsonObject { put("src", kotlinx.serialization.json.JsonPrimitive(path)) })
        }
    }

    /** Says why a picture or video didn't join its idea: in its editor while that's open, as a message otherwise. */
    private fun mediaProblem(problem: MediaProblem) {
        val editing = state.value.panel.let { it is Panel.Editor && it.id == problem.nodeId }

        if (editing) {
            state.update { it.copy(mediaProblem = problem) }
        } else {
            events.trySend(MapEvent.Snackbar(problem.message))
        }
    }

    /**
     * Copies a video from the phone into the map's folder, with a frame from
     * it as its poster, and puts it on the node, keeping the caption a video
     * there had. The editor shows how far along the copy is.
     */
    fun addVideo(nodeId: String, uri: Uri, resolver: ContentResolver) {
        videoJob?.cancel()
        val copy = ++videoCopies
        state.update { it.copy(videoCopy = VideoCopy(nodeId), mediaProblem = null) }

        videoJob = viewModelScope.launch {
            try {
                val added = maps.addVideo(id, uri, resolver) { progress ->
                    state.update { ui -> if (copy == videoCopies) ui.copy(videoCopy = VideoCopy(nodeId, progress)) else ui }
                }

                val caption = state.value.nodes[nodeId]?.video?.caption
                update(
                    nodeId,
                    "video",
                    buildJsonObject {
                        put("src", kotlinx.serialization.json.JsonPrimitive(added.src))
                        put("provider", kotlinx.serialization.json.JsonPrimitive("native"))
                        added.thumbnail?.let { put("thumbnail", kotlinx.serialization.json.JsonPrimitive(it)) }
                        caption?.let { put("caption", kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            } catch (refused: MediaRefusedException) {
                mediaProblem(MediaProblem(nodeId, video = true, ShownText.Plain(refused.message.orEmpty())))
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (unreadable: Exception) {
                Logs.warn("OrbitMap", "Couldn't copy the video", unreadable)
                mediaProblem(MediaProblem(nodeId, video = true, ShownText.Resource(R.string.map_video_unreadable)))
            } finally {
                if (copy == videoCopies) {
                    state.update { it.copy(videoCopy = null) }
                }
            }
        }
    }

    /** Stops the video being copied in: nothing of it stays. */
    fun cancelVideo() {
        videoJob?.cancel()
    }

    // Pictures and videos, full screen

    /**
     * Shows the map's pictures and videos full screen, from `nodeId`'s
     * picture, or its video; while presenting, only that idea's.
     */
    fun view(nodeId: String, kind: MediaKind = MediaKind.Picture) {
        val nodes = state.value.map?.nodes.orEmpty()
        val items = galleryOf(if (state.value.presentation != null) nodes.filter { it.id == nodeId } else nodes, untitled())

        if (items.isNotEmpty()) {
            onThisPhone { it.copy(viewing = Viewing(items, items.indexOf(nodeId, kind))) }
        }
    }

    fun closeViewer() {
        onThisPhone { it.copy(viewing = null) }
    }

    /** Plays `nodeId`'s video full screen. */
    fun play(nodeId: String) {
        val node = state.value.nodes[nodeId] ?: return
        val video = node.video ?: return
        play(GalleryItem(node.id, MediaKind.Video, video, node.label(untitled())))
    }

    fun play(item: GalleryItem) {
        onThisPhone { it.copy(playing = item) }
    }

    fun stopPlaying() {
        onThisPhone { it.copy(playing = null) }
    }

    /** Opens or closes a picture or video full screen: a presentation that plays waits while one shows, and starts the idea's time over after. */
    private fun onThisPhone(change: (MapUi) -> MapUi) {
        val before = state.value.fullScreen
        state.update(change)

        if (state.value.presentation != null && state.value.fullScreen != before) {
            control.retime()
        }
    }

    // Talking ideas onto the map

    /** A talk begins: what's said joins the orbit in focus, and one undo takes it all back. */
    fun startTalking() {
        stopEditing()
        spokenSent = 0
        spokenSaved = 0
        state.update { it.copy(panel = null) }
    }

    /** Ideas just said, into the orbit of the idea in focus. */
    fun addSpoken(ideas: List<String>) {
        val parent = state.value.sun?.id ?: state.value.map?.root?.id ?: return

        ideas.forEach { idea ->
            spokenSent += 1
            page.send(MapCommand.Add(parent, buildJsonObject { put("text", kotlinx.serialization.json.JsonPrimitive(idea)) }))
        }
    }

    /** The talk is over: how many ideas it brought, with a way to take them all back. */
    fun stopTalking() {
        if (spokenSent > 0) {
            events.trySend(MapEvent.Snackbar(ShownText.Plural(R.plurals.map_added_ideas, spokenSent), undo = true))
        }
    }

    // The source editor

    fun openSource() {
        stopEditing()
        sourceStart = markdown
        // Text edited by hand can change anything: the map as it was goes into the history first.
        checkpointNext = true
        state.update { it.copy(panel = null, source = SourceState(markdown)) }
        page.send(MapCommand.Source(markdown))
    }

    /** Draws the text as it's typed, a moment after the typing stops, and saves it when Orbit reads it. */
    fun editSource(text: String) {
        val source = state.value.source ?: return
        state.update { it.copy(source = source.copy(text = text)) }
        sourceJob?.cancel()

        sourceJob = viewModelScope.launch {
            delay(280)
            page.send(MapCommand.Source(text))
            // Typed markdown is saved as typed. YAML only once Orbit reads all of it: its conversion would lose the rest.
            val converted = if (source.yaml) convert(text, "markdown") else text

            if (converted != null && converted.isNotBlank() && state.value.readOnly.let { it == null || it == ReadOnly.Unreadable }) {
                markdown = converted
                save(converted)
            }
        }
    }

    fun setSourceYaml(yaml: Boolean) {
        val source = state.value.source ?: return

        if (source.yaml == yaml) {
            return
        }

        state.update { it.copy(source = source.copy(converting = true)) }

        viewModelScope.launch {
            val text = convert(source.text, if (yaml) "yaml" else "markdown")

            state.update { ui ->
                ui.copy(source = ui.source?.copy(text = text ?: ui.source.text, yaml = if (text != null) yaml else ui.source.yaml, converting = false))
            }
        }
    }

    fun closeSource() {
        sourceJob?.cancel()
        val start = sourceStart
        sourceStart = null
        state.update { it.copy(source = null) }

        viewModelScope.launch {
            // The map as the last text says, drawn; that text was saved as it was typed.
            page.send(MapCommand.Restore(markdown))

            if (start != null && start != markdown) {
                remember(start)
                state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
            }
        }
    }

    private suspend fun convert(text: String, to: String): String? = try {
        page.request(String.serializer()) { job -> MapCommand.Convert(job, text, to) }
    } catch (failed: PageException) {
        null
    }

    // Presenting

    /**
     * Presents the map along its path, or every idea in reading order without
     * one: from the idea in focus, or `fromStart`, from the top. A rehearsal
     * keeps each idea's time, for the pace of the next presentation.
     */
    fun startPresentation(seconds: Int, fromStart: Boolean = false, rehearse: Boolean = false) {
        val map = state.value.map ?: return
        stopEditing()
        val ideas = map.nodes.map { it.id }
        val order = path.filter { it in ideas }.ifEmpty { ideas }
        val start = if (fromStart || rehearse) 0 else order.indexOf(state.value.sun?.id).coerceAtLeast(0)
        rehearsed.clear()
        val planned = if (rehearse) null else maps.summary(id)?.metadata?.rehearsal

        state.update {
            it.copy(panel = null, pathMaking = false, presentation = Presentation(order, start, playing = seconds > 0, rehearsing = rehearse, planned = planned))
        }

        page.send(MapCommand.Present(true))
        page.send(MapCommand.View("focus"))
        page.send(MapCommand.Focus(order[start]))
        control.begin(seconds)
    }

    /**
     * The screen an audience watches while this map is presented, a second
     * screen or a TV, or null for this one. It gets the map, then each step,
     * and pictures and videos go to it rather than full screen here.
     */
    fun setAudience(audience: Audience?) {
        leaveFullScreen(audience)
        control.setScreen(audience)
    }

    /** Browsers on the local network following the presentation, or null; they get the map, then each step. */
    fun setBrowserAudience(audience: Audience?) {
        leaveFullScreen(audience)
        control.setBrowsers(audience)
    }

    /** A picture or video full screen here closes as an audience arrives elsewhere: the presenter shows it to them instead. */
    private fun leaveFullScreen(audience: Audience?) {
        if (audience != null && state.value.presentation != null && state.value.fullScreen) {
            onThisPhone { it.copy(viewing = null, playing = null) }
        }
    }

    /**
     * Keeps the rehearsal's times for the pace of the next presentation, over
     * the last ones for the ideas it came to, so a part can be rehearsed on
     * its own, and sums it up. A rehearsal stopped at once keeps nothing.
     */
    private fun finishRehearsal(presentation: Presentation) {
        val current = presentation.current
        rehearsed[current] = (rehearsed[current] ?: 0L) + SystemClock.elapsedRealtime() - presentation.stepStartedAt

        if (rehearsed.values.sum() < REHEARSAL_MINIMUM_MILLISECONDS) {
            return
        }

        val seconds = rehearsed.mapValues { (_, milliseconds) -> ((milliseconds + 500) / 1_000).toInt() }
        val ideas = state.value.nodes.keys
        val earlier = maps.summary(id)?.metadata?.rehearsal.orEmpty().filterKeys { it in ideas }
        viewModelScope.launch { maps.saveRehearsal(id, earlier + seconds) }
        state.update { it.copy(rehearsal = Rehearsal(presentation.order.filter { idea -> idea in seconds }, seconds)) }
    }

    fun closeRehearsal() {
        state.update { it.copy(rehearsal = null) }
    }

    // The presentation's path

    /** Starts choosing the presentation's path: a new one at the top of the map, one being changed where it ends. */
    fun startPath() {
        stopEditing()
        val ideas = state.value.nodes.keys
        val kept = path.filter { it in ideas }
        val start = if (kept.size > 1) kept.last() else state.value.map?.root?.id

        if (kept.size < 2) {
            setPath(listOfNotNull(start))
        }

        state.update { it.copy(pathMaking = true, panel = null) }
        // Taps fly while the path is made, and never open a new idea by mistake.
        page.send(MapCommand.Present(true))
        start?.let(::flyTo)
    }

    private fun flyTo(idea: String) {
        if (state.value.mode == "all") {
            page.send(MapCommand.View("focus"))
        }

        page.send(MapCommand.Focus(idea))
    }

    /**
     * Keeps the path, and flies back to where it starts, ready to present; one
     * with fewer than two ideas is none, and the map presents in reading order.
     */
    fun finishPath() {
        val ideas = state.value.nodes.keys
        val kept = path.filter { it in ideas }.takeIf { it.size > 1 }.orEmpty()
        setPath(kept)
        state.update { it.copy(pathMaking = false) }
        page.send(MapCommand.Present(false))
        viewModelScope.launch { maps.savePath(id, kept) }
        kept.firstOrNull()?.let(::flyTo)
    }

    private fun setPath(ideas: List<String>) {
        path = ideas
        state.update { it.copy(presentPath = path) }
    }

    /** Adds the idea in focus to the path again, to come back to it there. */
    fun addToPath() {
        val idea = state.value.sun?.id ?: return

        if (path.lastOrNull() != idea) {
            setPath(path + idea)
        }
    }

    fun removePathStep(index: Int) {
        if (index in path.indices) {
            setPath(path.filterIndexed { step, _ -> step != index })
        }
    }

    /** Moves the step at `index` earlier (-1) or later (1) in the path. */
    fun movePathStep(index: Int, by: Int) {
        val target = index + by

        if (index !in path.indices || target !in path.indices) {
            return
        }

        setPath(path.toMutableList().apply { add(target, removeAt(index)) })
    }

    /** Starts the path over from the top of the map, with a way back. */
    fun clearPath() {
        val before = path
        val root = state.value.map?.root?.id
        setPath(listOfNotNull(root))
        root?.let(::flyTo)
        events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_path_started_over), undo = true, onUndo = { setPath(before) }))
    }

    /** Shows or hides the captions, for the map alone. */
    fun setCaptions(on: Boolean) = control.setCaptions(on)

    /** Steps back or on through the presentation, closing a picture or video the audience sees or this phone shows. */
    fun step(by: Int, seconds: Int = 0) {
        if (state.value.fullScreen) {
            onThisPhone { it.copy(viewing = null, playing = null) }
        }

        control.step(by, seconds)
    }

    fun setPlaying(playing: Boolean, seconds: Int) = control.setPlaying(playing, seconds)

    /** Shows the audience the picture of the idea on screen, or plays its video, big over the map. */
    fun showMedia(kind: MediaKind) = control.showMedia(kind)

    /** Plays the video the audience sees, or pauses it. */
    fun playMedia(playing: Boolean) = control.playMedia(playing)

    /** Plays the video the audience sees from its start. */
    fun startMediaOver() = control.startMediaOver()

    /** Takes the audience back to the map from a picture or video. */
    fun closeMedia() = control.closeMedia()

    /** How long a video in the map's folder is, in seconds, or null when it can't be read: the phone's own reckoning stops at its end. */
    private suspend fun videoLength(video: Media): Double? {
        val file = mediaModel(video.src) as? java.io.File ?: return null

        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(file.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1_000.0 }
            } catch (unreadable: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    fun stopPresentation() {
        state.value.presentation?.takeIf { it.rehearsing }?.let(::finishRehearsal)
        control.stop()
        state.update { it.copy(presentation = null) }
        page.send(MapCommand.Present(false))
    }

    // The map as a whole

    fun summary(): MapSummary? = maps.summary(id)

    suspend fun currentMarkdown(): String = markdown.ifEmpty { maps.read(id) }

    /** A picture of the map: as on screen, or of its text in another view. */
    suspend fun picture(request: PictureRequest): ByteArray? = try {
        if (request.markdown == null) {
            container.pictures.draw(page, request)
        } else {
            container.pictures.draw(request.copy(baseUrl = request.baseUrl ?: mapBaseUrl(id), theme = request.theme ?: theme))
        }
    } catch (failed: PageException) {
        events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_picture_not_drawn)))
        null
    }

    suspend fun yaml(): String? = convert(currentMarkdown(), "yaml")

    fun hasPictures(): Boolean = maps.hasPictures(id)

    /**
     * What Coil loads for a picture or thumbnail: a web address, or a file in
     * the map's folder. A path that leads out of the folder loads nothing.
     */
    fun mediaModel(src: String): Any? {
        if (src.contains("://") || src.startsWith("data:")) {
            return src
        }

        val folder = maps.folder(id).canonicalFile
        val file = java.io.File(folder, src.removePrefix("./")).canonicalFile
        return file.takeIf { it.path.startsWith(folder.path + java.io.File.separator) }
    }

    suspend fun writeZip(output: java.io.OutputStream) = maps.writeZip(id, output)

    /** The map's folder, where its pictures are. */
    fun folder(): java.io.File = maps.folder(id)

    /** The map as one web page that flies in any browser, offline (see [WebPage]). */
    suspend fun webPage(context: Context): String? {
        val theme = theme ?: return null
        val title = state.value.map?.title ?: summary()?.title ?: context.getString(R.string.map_web_page_title)
        val ideas = state.value.nodes.keys
        val presentPath = path.filter { it in ideas }.takeIf { it.size > 1 }.orEmpty()
        return WebPage.build(context, title, currentMarkdown(), theme, maps.folder(id), path = presentPath)
    }

    /** Moves the map to the trash; the home screen offers it back with Undo. */
    fun deleteMap() {
        deleted = true

        viewModelScope.launch {
            container.pictures.forget(id)
            maps.delete(id)?.let { container.deletedMaps.trySend(it) }
            events.send(MapEvent.Deleted)
        }
    }

    /** Brings the map back from the trash, after it was deleted while open. */
    fun restoreFromTrash() {
        viewModelScope.launch {
            val item = maps.trash.value.firstOrNull { it.id == id } ?: return@launch
            maps.restore(item)
        }
    }

    override fun onCleared() {
        maps.showing(id, false)
        val unsaved = markdown
        val base = written
        val unchangeable = state.value.readOnly != null

        // The last edit is saved even as the screen goes, and pictures taken off the map go with it.
        if (!deleted && !unchangeable && unsaved.isNotEmpty()) {
            container.applicationScope.launch {
                if (unsaved != base) {
                    maps.write(id, unsaved, base = base)
                }

                maps.removeUnusedPictures(id)
            }
        }

        container.pages.give(page)
    }
}

/** The page's word for a map deleted while it's open. */
private const val LOCK_DELETED = "deleted"

/** The page's word for maps a newer Orbit keeps. */
private const val LOCK_STORAGE = "storage"

/** The screen's state with what the page says about whether the map can be changed. */
private fun MapUi.reading(page: MapState): MapUi {
    val readOnly = when (page.readOnly) {
        "unreadable" -> ReadOnly.Unreadable
        "newer" -> ReadOnly.Newer
        LOCK_DELETED -> ReadOnly.Deleted
        LOCK_STORAGE -> ReadOnly.Storage
        else -> null
    }

    return copy(readOnly = readOnly, readProblem = page.problem, panel = if (readOnly != null && panel is Panel.Menu) null else panel)
}

/** Whether an edit took a goal to its target: one that wasn't reached before it is now. */
internal fun reachedGoal(before: Map<String, MapNode>, after: List<MapNode>): Boolean = after.any { node ->
    Goal.of(node)?.reached == true && before[node.id]?.let { Goal.of(it) }?.reached == false
}

/** Seconds as a clock reads them: 0:42, 12:05, 1:02:03. */
fun clock(seconds: Int): String {
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}
