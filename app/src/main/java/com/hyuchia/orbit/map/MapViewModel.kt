package com.hyuchia.orbit.map

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.Checkpoint
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.MediaRefusedException
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.data.WriteResult
import com.hyuchia.orbit.media.GalleryItem
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.SoundState
import com.hyuchia.orbit.media.galleryOf
import com.hyuchia.orbit.media.indexOf
import com.hyuchia.orbit.media.soundOf
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.CheckedMap
import com.hyuchia.orbit.page.CopiedIdea
import com.hyuchia.orbit.page.ImportedFile
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PastedIdeas
import com.hyuchia.orbit.page.PictureRequest
import com.hyuchia.orbit.page.PrintRequest
import com.hyuchia.orbit.page.ViewMode
import com.hyuchia.orbit.page.WebPage
import com.hyuchia.orbit.page.mapBaseUrl
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.privacy.PrivateView
import com.hyuchia.orbit.study.NEW_CARDS_A_DAY
import com.hyuchia.orbit.study.StudyControl
import com.hyuchia.orbit.ui.isWebPicture
import com.hyuchia.orbit.ui.pictureAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Base64

/**
 * Whether a save the page made at `revision` (null from a page before
 * revisions) was made on text older than the screen's `generation`.
 */
internal fun isStale(revision: Int?, generation: Int): Boolean = revision != null && revision != generation

/** How far back undo goes. */
private const val HISTORY = 100

/**
 * How much text undo keeps, all its versions together: about 8 MB in
 * memory. A big map goes back fewer steps, but always one.
 */
private const val HISTORY_CHARACTERS = 4_000_000

/** Lets the oldest of undo's `versions` go while there are more than `limit`, or more text than `characters` in more than one. */
internal fun trimUndo(versions: ArrayDeque<String>, limit: Int = HISTORY, characters: Int = HISTORY_CHARACTERS) {
    var total = versions.sumOf { it.length }

    while (versions.size > limit || versions.size > 1 && total > characters) {
        total -= versions.removeFirst().length
    }
}

/** How long a map waits to hear what the screen covers before drawing anyway. */
private const val INSETS_WAIT_MILLISECONDS = 1_000L

/** How long after a save the storage couldn't take the map's changes are tried again, while they're still unsaved. */
private const val SAVE_RETRY_MILLISECONDS = 15_000L

/** How many times changes the storage couldn't take are tried again once their map has closed: ten minutes' worth, while Orbit runs. */
private const val CLOSED_SAVE_TRIES = 40

/** How long leaving a map waits for the page to keep what was being typed. */
private const val FINISH_EDITING_MILLISECONDS = 1_000L

/**
 * How long a closing map waits for its page to answer, everything it said
 * before with it, before the page goes back; and as long again for it to
 * convert YAML typed in the source editor.
 */
private const val LAST_WORD_MILLISECONDS = 2_000L

/** A rehearsal shorter than this was stopped at once, and keeps nothing. */
private const val REHEARSAL_MINIMUM_MILLISECONDS = 3_000L

/** A text to save, and the catch-up it was made before: one made before the map changed elsewhere isn't written. */
private class Save(val text: String, val generation: Int)

/**
 * Map `id` on its screen: the page drawing it, what's said and done on it,
 * and its saving. It works with the maps, the app's settings and the rest of
 * the app it needs ([MapApp]), so a test can make one with its own.
 */
class MapViewModel(
    val id: String,
    private val maps: MapRepository,
    private val appSettings: SettingsRepository,
    private val app: MapApp,
) : ViewModel() {
    private val state = MutableStateFlow(MapUi())
    private val events = Channel<MapEvent>(Channel.BUFFERED)

    val ui: StateFlow<MapUi> = state.asStateFlow()
    val effects: Flow<MapEvent> = events.receiveAsFlow()

    /** The page drawing the map, a WebView in the app; it stays with the map while the screen comes and goes. */
    var page: MapPage = app.takePage()
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

    /** The saves being written one after another, until the screen goes. */
    private var saving: Job? = null

    /** Goes up whenever the screen catches up with a change made elsewhere: saves made before it aren't written. */
    private var generation = 0

    /**
     * Why the next save keeps the map as it was in its history first, when
     * it does: the first one of a visit, the first after a change made
     * elsewhere, and the ones before big changes (an idea removed with the
     * ones under it, the text edited by hand). Null: one is kept every so
     * often while editing.
     */
    private var nextReason: VersionReason? = VersionReason.Opened

    private var theme: AppTheme? = null
    private var settings: MapSettings? = null
    private var insets = MapInsets()

    /** Done once the screen has said what it covers, so the map's first view fits around it from the start. */
    private val insetsKnown = CompletableDeferred<Unit>()

    private var loadedOnce = false

    /** The map before the source editor opened, to undo it in one step. */
    private var sourceStart: String? = null

    /** Why the next save would have kept a version before the source editor opened: its reason again if nothing is typed there. */
    private var reasonBeforeSource: VersionReason? = null

    /** How many times the idea editor has opened on an idea (see [Panel.Editor]). */
    private var editorOpenings = 0

    /** The map edited as text, while the source editor is open. */
    private val sourceEditing = SourceEditing(
        scope = viewModelScope,
        ui = state,
        draw = { text -> page.send(MapCommand.Source(text)) },
        convert = ::convert,
        keep = { text ->
            markdown = text
            save(text)
        },
    )

    /** The version history, while it's open. */
    private val versionHistory = HistoryControl(
        scope = viewModelScope,
        ui = state,
        listVersions = { maps.versions(id) },
        readVersion = { time -> maps.readVersion(id, time) },
        markdown = { markdown },
        draw = { text, editable -> page.send(MapCommand.Preview(text, editable = editable)) },
        fromOtherApp = { file ->
            try {
                page.request(ImportedFile.serializer()) { job -> MapCommand.ImportFile(job, Base64.getEncoder().encodeToString(file.toByteArray()), null) }.maps.firstOrNull()?.markdown
            } catch (failed: PageException) {
                null
            }
        },
    )

    /** The presentation as it goes: its steps, its timer, what the audience sees over the map, and the audiences. */
    private val control = PresentationControl(
        scope = viewModelScope,
        ui = state,
        clock = SystemClock::elapsedRealtime,
        focus = { idea -> page.send(MapCommand.Focus(idea)) },
        map = { presentation -> theme?.let { AudienceCommand.Show(markdown, it, mapBaseUrl(id), presentation.current, presentation.captions) } },
        leaving = { presentation, now ->
            if (presentation.rehearsing) {
                rehearsed[presentation.index] = (rehearsed[presentation.index] ?: 0L) + now - presentation.stepStartedAt
            }
        },
        length = ::videoLength,
    )

    /** How many new cards a day the learner chose: as Settings has it, or as just chosen while studying, before Settings does. */
    private val newCards = MutableStateFlow(NEW_CARDS_A_DAY)

    val newCardsADay: StateFlow<Int> = newCards.asStateFlow()

    /**
     * Study mode as it goes: the page hides each card's ideas until they're
     * revealed, and the answers are kept in the map's details, beside the
     * other devices' that the folder the maps are kept in brings.
     */
    private val studying = StudyControl(
        scope = viewModelScope,
        ui = state,
        send = { command -> page.send(command) },
        progress = { maps.summary(id)?.metadata?.study.orEmpty() },
        keep = { cards -> remembering { maps.saveStudy(id, cards) } },
        today = { LocalDate.now().toEpochDay() },
        elsewhere = { maps.summary(id)?.metadata?.studyElsewhere.orEmpty() },
        newADay = { newCards.value },
    )

    /** The presentation's path, as it's being made or since it was saved. */
    private val presentationPath = PathMaking(
        ui = state,
        send = { command -> page.send(command) },
        keep = { path -> viewModelScope.launch { remembering { maps.savePath(id, path) } } },
        say = { event -> events.trySend(event) },
    )

    /** A rehearsal's time on each step of its path so far, by place, in milliseconds: an idea the path comes back to has a time for each visit. */
    private val rehearsed = mutableMapOf<Int, Long>()

    /** Where an idea being dragged came from: under which idea, to tell it moved somewhere else. */
    private var dragFrom: String? = null

    /** Ideas said in the talk going on or just over, and how many of them the map has taken in. */
    private var spokenSent = 0

    private var spokenSaved = 0

    /**
     * Whether this map is private while private maps are locked. Behind its
     * lock, its page is out of sight and out of reach: of screen readers,
     * of the keyboard, and of anything else that reads the screen. It takes
     * part in no drag either: nothing from other apps lands in it, and none
     * of its ideas leaves it.
     */
    val locked: StateFlow<Boolean> = combine(app.privateLock.view, maps.maps) { view, summaries -> lockedNow(view, summaries) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, lockedNow(app.privateLock.view.value, maps.maps.value))

    /** What presenting does as this map locks and unlocks, when it's private (see [PresentationLocking]). */
    private val presentationLocking = PresentationLocking()

    /** Whether this map is private and locked, given private maps' `view` and the maps' `summaries`: until they're read, it counts as locked. */
    private fun lockedNow(view: PrivateView, summaries: List<MapSummary>?): Boolean = summaries?.firstOrNull { it.id == id }?.let(view::hides) ?: (summaries == null)

    /** What other apps drag onto the map, and ideas dragged out and back (see `MapDrops`). */
    /** Pictures, live photos' movies, videos, sounds and voice memos from the phone, joining the map's ideas. */
    private val mediaAdding = MediaAdding(
        scope = viewModelScope,
        ui = state,
        maps = maps,
        id = id,
        update = { nodeId, key, value -> update(nodeId, key, value) },
        say = { event -> events.trySend(event) },
        untitled = ::untitled,
    )

    val drops = MapDrops(
        scope = viewModelScope,
        mapId = id,
        maps = maps,
        importer = { app.importer },
        words = app.words,
        ui = ui,
        locked = locked,
        page = { page },
        update = { nodeId, key, value -> update(nodeId, key, value) },
        tell = { events.trySend(it) },
    )

    /** The map's sounds, one at a time: they go on as panels and the viewer come and go, and stop with the map. */
    val sounds = app.soundPlayback(viewModelScope, ::mediaModel)

    /** The sound playing or paused, for the small player over the map, the details, the editor and the viewer. */
    val sound: StateFlow<SoundState?> = sounds.sound

    /** How long each of the map's sound files is, once read, by address. */
    private val soundLengths = HashMap<String, Long?>()

    init {
        // A private map presented as private maps lock: its audience stops seeing it, in browsers, on a TV or a second
        // screen, as the phone shows the lock. Unlocked, presenting it again is offered, rather than resumed unasked.
        viewModelScope.launch {
            locked.collect { now ->
                val presentation = state.value.presentation

                when (presentationLocking.changed(now, presenting = presentation != null, rehearsing = presentation?.rehearsing == true)) {
                    LockingStep.Stop -> stopPresentation()
                    LockingStep.OfferAgain -> events.trySend(MapEvent.PresentAgain)
                    LockingStep.Nothing -> Unit
                }
            }
        }

        page.listener = ::receive

        // What's typed into a private map stays out of keyboards' learning, from the map's page as from its fields.
        viewModelScope.launch {
            maps.maps.map { summaries -> summaries?.firstOrNull { it.id == id }?.metadata?.isPrivate == true }.distinctUntilChanged().collect { private ->
                page.incognito = private
            }
        }

        // A private map that locks has its sound stop: nothing of it plays, or shows, outside the lock.
        viewModelScope.launch {
            locked.collect { hidden ->
                if (hidden) {
                    sounds.stop()
                }
            }
        }

        saving = viewModelScope.launch {
            for (save in saves) {
                // A write that's begun ends, and says what it wrote, even as the screen goes: its last edit is written over it.
                withContext(NonCancellable) { write(save) }
            }
        }

        maps.showing(id, true)
        viewModelScope.launch { remembering { maps.markOpened(id) } }

        // A newer version from elsewhere (another device, the folder, ideas caught on the go) replaced this map:
        // it shows at once, and undo brings this one back, with what was just typed as text.
        viewModelScope.launch {
            maps.replaced.filter { it == id }.collect {
                runCatching { maps.read(id) }.getOrNull()?.let { text -> sourceEditing.afterTyping { catchUp(text) } }
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

        viewModelScope.launch {
            appSettings.settings.map { it.newCardsADay }.distinctUntilChanged().collect { newCards.value = it }
        }
    }

    /**
     * Writes a save, unless the map changed elsewhere since it was made: its
     * text then goes into the history, and the screen shows the map as it is.
     */
    private suspend fun write(save: Save) {
        if (save.generation != generation) {
            maps.keepAside(id, save.text)
            events.trySend(MapEvent.KeptInHistory)
            return
        }

        val reason = nextReason
        nextReason = null
        val checkpoint = if (reason != null) Checkpoint.Always else Checkpoint.Interval

        when (maps.write(id, save.text, base = written, checkpoint = checkpoint, reason = reason ?: VersionReason.Editing)) {
            WriteResult.Written -> {
                written = save.text

                if (state.value.unsaved) {
                    savingAgain?.cancel()
                    state.update { it.copy(unsaved = false) }
                    events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_saved_after_all)))
                }
            }
            WriteResult.NotWritten -> {
                // The storage couldn't take it: the text stays here, the file as it was, and the whole text is tried again.
                nextReason = nextReason ?: reason
                state.update { it.copy(unsaved = true) }
                savingAgain?.cancel()

                savingAgain = viewModelScope.launch {
                    delay(SAVE_RETRY_MILLISECONDS)
                    save(markdown)
                }
            }
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

    /**
     * The idea removed with ideas under it, going from `before` to `after`:
     * the removed idea whose parent is still there, and how many went with
     * it. Null when one idea alone went: no version is kept for that.
     */
    private fun removedBranch(before: List<MapNode>, after: List<MapNode>): VersionReason.Removing? {
        val left = after.mapTo(HashSet()) { it.id }
        val gone = before.filter { it.id !in left }

        if (gone.size < 2) {
            return null
        }

        val top = gone.firstOrNull { it.parentId == null || it.parentId in left } ?: gone.first()
        return VersionReason.Removing(top.label(app.words.string(R.string.common_untitled)), gone.size - 1)
    }

    /** Shows the map's file as it is now, after a change made elsewhere: what was on screen goes on the undo stack. */
    private fun catchUp(text: String) {
        // Text the screen has already: saves made on it stay good, and the page's revision with them.
        if (text == markdown) {
            written = text
            return
        }

        generation += 1

        remember(markdown)
        markdown = text
        written = text
        nextReason = VersionReason.Elsewhere
        // The page draws the map as it is now, over a version on show.
        versionHistory.stopShowing()
        state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty(), history = it.history?.copy(showing = null)) }
        page.send(MapCommand.Restore(text, revision = generation))
        sourceEditing.follow(text)
    }

    /** Saves text the map has now: written soon, once the saves before it are. */
    private fun save(text: String) {
        saves.trySend(Save(text, generation))
    }

    /** The next try at saving changes the storage couldn't take (see [MapUi.unsaved]). */
    private var savingAgain: Job? = null

    /**
     * Keeps what's being typed on the map, and waits for the page to say it
     * did (a second at most): what's done before leaving the map, so the last
     * idea or word typed is the map's.
     */
    suspend fun finishEditing() {
        if (!state.value.editing) {
            return
        }

        page.send(MapCommand.StopEditing(true))
        withTimeoutOrNull(FINISH_EDITING_MILLISECONDS) { state.first { !it.editing } }
    }

    /** Tries again now to save changes the storage couldn't take. */
    fun saveAgain() {
        savingAgain?.cancel()
        save(markdown)
    }

    /**
     * Writes what the map's screen remembers about the map, besides its text:
     * where the reader was, its path, its rehearsal, its cards. Storage that's
     * full or gone leaves it unwritten, which the log says, rather than
     * stopping Orbit.
     */
    private suspend fun remembering(write: suspend () -> Unit) {
        try {
            write()
        } catch (failed: IOException) {
            Logs.warn("OrbitMap", "Couldn't keep the map's details", failed)
        }
    }

    /**
     * The parts of the map the screen covers with its toolbar, sheets or captions: Orbit fits views around them.
     * With `glide`, the change is made once (the talk's panel coming or going): a fitted map glides to its new place.
     */
    fun setInsets(insets: MapInsets, glide: Boolean = false) {
        val changed = insets != this.insets
        this.insets = insets
        insetsKnown.complete(Unit)

        if (changed) {
            page.send(MapCommand.Insets(insets, glide))
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
        } catch (missing: FileNotFoundException) {
            state.update { it.copy(error = ShownText.Resource(R.string.map_missing)) }
            return
        } catch (unreadable: IOException) {
            state.update { it.copy(error = ShownText.Resource(R.string.map_unreadable_file)) }
            return
        }

        generation += 1
        markdown = text
        written = text
        nextReason = VersionReason.Opened
        val metadata = maps.summary(id)?.metadata
        val pathMaking = state.value.pathMaking

        if (!pathMaking) {
            presentationPath.set(metadata?.path.orEmpty())
        }

        // Drawn before the screen says what it covers, the map would glide out from under the toolbar once it did.
        withTimeoutOrNull(INSETS_WAIT_MILLISECONDS) { insetsKnown.await() }
        // The map drawn anew is the map as it is now, over a version that was on show.
        versionHistory.stopShowing()
        state.update { it.copy(history = it.history?.copy(showing = null)) }
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
                revision = generation,
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
        app.givePage(page)
        page = app.takePage()
        page.incognito = maps.summary(id)?.metadata?.isPrivate == true
        page.listener = ::receive
        pageGeneration.value += 1
        versionHistory.stopShowing()
        versionHistory.closeSummaries()
        state.update { it.copy(stopped = false, ready = false, drawn = false, editing = false, panel = null, history = null) }
        viewModelScope.launch { load() }
    }

    private fun receive(message: MapMessage) {
        when (message) {
            MapMessage.Hello -> Unit
            is MapMessage.Ready -> {
                state.update { it.copy(ready = true, map = message.state, error = null).reading(message.state) }
                studying.pageLoaded()

                if (!state.value.drawn) {
                    val drawing = page

                    viewModelScope.launch {
                        drawing.drawn()

                        // A page made again meanwhile says when it's drawn itself.
                        if (drawing === page) {
                            state.update { it.copy(drawn = true) }
                        }
                    }
                }
            }
            is MapMessage.Saved -> saved(message)
            // While an earlier version is on show, only whether the map can be changed is the map's: the rest is the version's.
            is MapMessage.State -> state.update { ui ->
                if (ui.showingVersion) ui.reading(message.state) else ui.copy(map = message.state).reading(message.state).withoutPanelOnGoneIdea()
            }
            is MapMessage.Focus -> {
                state.update { ui -> ui.copy(map = ui.map?.copy(path = message.path, mode = message.mode)) }
                rememberPlace(message.path.lastOrNull(), message.mode)
                // Making a path, each new idea flown to joins it; flying back to one on it already, to go on from there, doesn't.
                val reached = message.path.lastOrNull()

                if (state.value.pathMaking && message.mode == ViewMode.FOCUS && reached != null && reached !in presentationPath.path) {
                    presentationPath.set(presentationPath.path + reached)
                }
            }
            // Making a path, on a map that can't be changed, or on an earlier version, the map stays still to edits, and has no menu of them.
            MapMessage.Held -> if (takesEdits()) {
                events.trySend(MapEvent.LongPress)
            }
            is MapMessage.Context -> if (takesEdits()) {
                versionHistory.close()
                state.update { it.copy(panel = Panel.Menu(message.id, message.x, message.y)) }
            }
            is MapMessage.Drag -> dragged(message)
            // An earlier version's ideas are only looked at: their details, here, would be the map's as it is now.
            is MapMessage.Details -> if (!state.value.showingVersion) {
                versionHistory.close()
                state.update { it.copy(panel = Panel.Details(message.id)) }
            }
            is MapMessage.Clipboard -> when (message.action) {
                "copy" -> copy(message.id)
                "cut" -> copy(message.id, cut = true)
                "paste" -> paste(message.id)
            }
            is MapMessage.EditorOpen -> {
                versionHistory.close()
                state.update { it.copy(editing = true, panel = null) }
            }
            MapMessage.EditorClose -> state.update { it.copy(editing = false) }
            is MapMessage.Source -> state.update { ui -> ui.copy(source = ui.source?.copy(diagnostics = message.diagnostics)) }
            is MapMessage.Open -> events.trySend(MapEvent.Open(message.url))
            MapMessage.Haptic -> events.trySend(MapEvent.Tick)
            is MapMessage.Error -> {
                Logs.warn("OrbitMap", "The page had a problem: ${message.message}")
                state.update { it.copy(error = ShownText.Plain(message.message)) }
            }
            is MapMessage.Declined -> {
                // A value the app sent that Orbit can't read: what's wrong is for the log, and the person hears it didn't happen.
                Logs.warn("OrbitMap", "Orbit didn't make the ${message.change}: ${message.message}")
                state.update { it.copy(error = ShownText.Resource(R.string.map_change_declined)) }
            }
            MapMessage.Stopped -> state.update { it.copy(ready = false, drawn = false, editing = false, panel = null, stopped = true) }
            is MapMessage.DropTarget -> drops.targeted(message.id)
            is MapMessage.DragOut -> drops.draggedOut(message.id)
            // Only an audience's screen says how its video goes.
            is MapMessage.Result, is MapMessage.MediaState -> Unit
        }
    }

    /** Whether a press on the map can lead to an edit: not while a path is made, on a map that can't be changed, or on an earlier version. */
    private fun takesEdits(): Boolean = !state.value.pathMaking && state.value.readOnly == null && !state.value.showingVersion

    private fun saved(message: MapMessage.Saved) {
        // The page never saves a map that can't be changed; this screen doesn't either.
        if (message.state.readOnly != null || state.value.readOnly != null) {
            return
        }

        // Made on the map as it was before a change from elsewhere reached the page: it goes into the history
        // (see write), and the screen stays on the map as it is now, which the page is drawing.
        if (isStale(message.revision, generation)) {
            saves.trySend(Save(message.markdown, message.revision ?: generation))
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

        // An idea removed with ideas under it: the map as it was goes into the history first, saying which.
        if (message.kind == "remove") {
            removedBranch(state.value.map?.nodes.orEmpty(), message.state.nodes)?.let { nextReason = it }
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
            ).withoutPanelOnGoneIdea()
        }

        // The source editor follows edits made on the map.
        sourceEditing.follow(message.markdown)
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
    private fun untitled(): String = app.words.string(R.string.common_untitled)

    /** An open panel, the path, the rehearsal and the study keep their idea when new text gives the idea a new id. */
    private fun follow(from: String, to: String) {
        if (from in presentationPath.path) {
            presentationPath.set(presentationPath.path.map { if (it == from) to else it })
        }

        if (maps.summary(id)?.metadata?.remembers(from) == true) {
            viewModelScope.launch { remembering { maps.renameIdea(id, from, to) } }
        }

        state.update { ui ->
            val panel = when (val current = ui.panel) {
                is Panel.Editor -> if (current.id == from) current.copy(id = to) else current
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
        trimUndo(undoStack)
        redoStack.clear()
    }

    private var placeJob: Job? = null

    private fun rememberPlace(focus: String?, view: String) {
        placeJob?.cancel()

        placeJob = viewModelScope.launch {
            delay(600)
            remembering { maps.savePlace(id, focus, view) }
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

        if (!ui.focused) {
            page.send(MapCommand.View(ViewMode.FOCUS))
        } else {
            ui.path.getOrNull(ui.path.size - 2)?.let { page.send(MapCommand.Focus(it.id)) }
        }
    }

    fun toggleView() {
        page.send(MapCommand.View(if (state.value.focused) ViewMode.OVERVIEW else ViewMode.FOCUS))
    }

    /** Opens the field for a new idea in the orbit of `parentId` (default: the node in focus). */
    fun addIdea(parentId: String? = null, index: Int? = null) {
        val parent = parentId ?: state.value.sun?.id ?: state.value.map?.root?.id ?: return
        versionHistory.close()
        state.update { it.copy(panel = null) }
        page.send(MapCommand.EditNew(parent, index))
    }

    fun rename(nodeId: String) {
        versionHistory.close()
        state.update { it.copy(panel = null) }
        page.send(MapCommand.Edit(nodeId))
    }

    /** Sets attributes; `null` removes one. */
    fun update(nodeId: String, changes: Map<String, JsonElement?>) {
        versionHistory.showNow()
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
        versionHistory.showNow()
        page.send(MapCommand.AddToGoal(nodeId, number(amount)))
    }

    /** Deletes an idea, with Undo; `cut` says it was copied first. */
    fun remove(nodeId: String, cut: Boolean = false) {
        val node = state.value.nodes[nodeId] ?: return
        versionHistory.close()
        state.update { it.copy(panel = null) }
        page.send(MapCommand.Remove(nodeId))
        val message = if (cut) R.string.map_cut_idea else R.string.map_deleted_idea
        events.trySend(MapEvent.Snackbar(ShownText.Resource(message, listOf(node.label(untitled()))), undo = true))
    }

    /** Whether this map is private: what's copied from it is marked sensitive. */
    fun isPrivate(): Boolean = maps.summary(id)?.metadata?.isPrivate == true

    /** Whether map `mapId` is private and locked now. */
    private fun hiddenPrivate(mapId: String): Boolean = maps.summary(mapId)?.let { app.privateLock.view.value.hides(it) } == true

    /**
     * Copies an idea and everything under it, to paste here, in another map
     * or in another app; with `cut`, the idea goes too, with Undo. The root
     * stays: cutting it copies it.
     */
    fun copy(nodeId: String, cut: Boolean = false) {
        val node = state.value.nodes[nodeId] ?: return

        viewModelScope.launch {
            val copied = try {
                page.request(CopiedIdea.serializer()) { job -> MapCommand.Copy(job, nodeId) }
            } catch (failed: PageException) {
                events.trySend(MapEvent.Snackbar(ShownText.Plain(failed.message.orEmpty())))
                return@launch
            }

            if (!app.clipboard.copy(node.text, copied.text, copied.markdown, id, sensitive = isPrivate())) {
                events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_too_much_to_copy)))
                return@launch
            }

            if (cut && !node.isRoot && state.value.readOnly == null && !state.value.showingVersion) {
                remove(nodeId, cut = true)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // From Android 13 on, Android shows what was copied itself.
                events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_copied_idea, listOf(node.label(untitled())))))
            }
        }
    }

    /** What pasting would add to an idea now, if anything; this doesn't read what was copied. */
    fun pasteable(): Pasteable? = app.clipboard.pasteable()

    /**
     * Pastes what was copied into an idea: text becomes ideas under it (see
     * web/paste.ts), with one Undo, and a picture becomes its picture. An idea
     * copied from another map brings the pictures and videos it points to.
     */
    fun paste(parentId: String) {
        if (state.value.readOnly != null || state.value.showingVersion || state.value.nodes[parentId] == null) {
            return
        }

        when (val clip = app.clipboard.read()) {
            null -> events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_nothing_to_paste)))
            is Clip.Picture -> addPicture(parentId, clip.uri, app.clipboard.resolver, pasted = true)
            is Clip.Ideas -> viewModelScope.launch {
                try {
                    // A locked private map's pictures, videos and sounds stay in it: what was copied from it pastes as text. Those that
                    // come may come under names of their own, which the pasted text points at.
                    val text = clip.sourceMap?.takeUnless { source -> hiddenPrivate(source) }?.let { source -> maps.copyMedia(source, id, clip.text) } ?: clip.text
                    val pasted = page.request(PastedIdeas.serializer()) { job -> MapCommand.Paste(job, parentId, text) }

                    if (pasted.count == 0) {
                        events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_nothing_to_paste)))
                    } else {
                        events.trySend(MapEvent.Snackbar(ShownText.Plural(R.plurals.map_pasted_ideas, pasted.count), undo = true))
                    }
                } catch (refused: MediaRefusedException) {
                    events.trySend(MapEvent.Snackbar(ShownText.Plain(refused.message.orEmpty())))
                } catch (failed: PageException) {
                    events.trySend(MapEvent.Snackbar(ShownText.Plain(failed.message.orEmpty())))
                }
            }
        }
    }

    fun duplicate(nodeId: String) {
        versionHistory.close()
        state.update { it.copy(panel = null) }
        page.send(MapCommand.Duplicate(nodeId))
    }

    fun move(nodeId: String, parentId: String, index: Int?) {
        versionHistory.showNow()
        page.send(MapCommand.Move(nodeId, parentId, index))
    }

    fun setOptions(changes: Map<String, JsonElement?>) {
        versionHistory.showNow()
        page.send(MapCommand.Options(JsonObject(changes.mapValues { it.value ?: JsonNull })))
    }

    fun undo() {
        // Under its lock, a private map takes no change, from a key or anywhere else.
        if (state.value.readOnly != null || locked.value) {
            return
        }

        // Text just typed in the source editor is saved first: undone, redo brings it back.
        sourceEditing.afterTyping {
            val version = undoStack.removeLastOrNull() ?: return@afterTyping
            redoStack.addLast(markdown)
            restore(version)
        }
    }

    fun redo() {
        if (state.value.readOnly != null || locked.value) {
            return
        }

        sourceEditing.afterTyping {
            val version = redoStack.removeLastOrNull() ?: return@afterTyping
            undoStack.addLast(markdown)
            restore(version)
        }
    }

    /** Makes `version` the map again: drawn, saved exactly as it is, and in the source editor if it's open. */
    private fun restore(version: String) {
        // The page draws it over a version on show: the history, if it's open, is on the map as it is now again.
        versionHistory.stopShowing()
        state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty(), history = it.history?.copy(showing = null)) }
        markdown = version
        page.send(MapCommand.Restore(version, revision = generation))
        save(version)
        sourceEditing.follow(version)
    }

    fun show(panel: Panel?) {
        // Under its lock, nothing of a private map opens; what's open may still close.
        if (panel != null && locked.value) {
            return
        }

        // The editor opening on an idea starts its fields over; asked for the idea it's on, it stays as it is.
        val shown = when {
            panel !is Panel.Editor -> panel
            (state.value.panel as? Panel.Editor)?.id == panel.id -> state.value.panel
            else -> {
                editorOpenings += 1
                panel.copy(opened = editorOpenings)
            }
        }

        if (shown != state.value.panel) {
            state.update { it.copy(mediaProblem = null) }
        }

        // Leaving the version history: the map as it is now comes back.
        if (shown !is Panel.History) {
            versionHistory.close()
        }

        state.update { it.copy(panel = shown) }
    }

    // Version history

    /**
     * Opens the version history: the versions kept, newest first, with the
     * map as it is now showing. What each one changed is worked out as its
     * row asks ([summarizeVersion]), one at a time, off the main thread.
     */
    fun openHistory() {
        // Opened again, the history starts over from the map as it is now.
        versionHistory.close()
        stopEditing(save = true)
        versionHistory.open()
    }

    /** A row of the history asks what its version holds or changed: worked out in turn, once. */
    fun summarizeVersion(time: Long) = versionHistory.summarize(time)

    /**
     * Shows a version on the map, to look at; null shows the map as it is
     * now. The last one chosen is the one shown, however long each takes to
     * read, and none is once the history has closed.
     */
    fun showVersion(time: Long?) = versionHistory.show(time)

    /** Makes a version the map again: the map as it is now goes into the history first, and Undo brings it back. */
    fun restoreVersion(time: Long) {
        viewModelScope.launch {
            val text = versionHistory.read(time) ?: return@launch

            // What's on screen and not written yet goes into the history too, so nothing is lost on the way.
            if (written != markdown) {
                maps.keepAside(id, markdown)
            }

            maps.keepCurrentVersion(id, restoring = time)
            remember(markdown)
            versionHistory.closeSummaries()
            state.update { it.copy(panel = null, history = null, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
            markdown = text
            page.send(MapCommand.Restore(text, revision = generation))
            save(text)
            events.trySend(MapEvent.Snackbar(restoredMessage(time, app.timeWords()), undo = true))
        }
    }

    fun stopEditing(save: Boolean = true) = page.send(MapCommand.StopEditing(save))

    /**
     * Locks every private map now, as Orbit being away for their delay would:
     * from this one's menu, its lock button or its shortcut. What's being
     * typed on the map is saved first; a field in a panel saves itself as the
     * lock closes it.
     */
    fun lockPrivateMapsNow() {
        stopEditing(save = true)
        app.privateLock.lock()
    }

    /** Gives an idea a picture from the phone; `pasted` says so once it's there, with Undo. */
    fun addPicture(nodeId: String, uri: Uri, resolver: ContentResolver, pasted: Boolean = false) = mediaAdding.addPicture(nodeId, uri, resolver, pasted)

    /**
     * Makes an idea's picture a live photo with the movie an iPhone took with
     * it: the movie is kept beside the picture as its motion, the picture's
     * own fields as they were.
     */
    fun addMotion(nodeId: String, movie: Uri, resolver: ContentResolver) = mediaAdding.addMotion(nodeId, movie, resolver)

    /**
     * Copies a video from the phone into the map's folder, with a frame from
     * it as its poster, and puts it on the node, keeping the caption a video
     * there had. The editor shows how far along the copy is.
     */
    fun addVideo(nodeId: String, uri: Uri, resolver: ContentResolver) = mediaAdding.addVideo(nodeId, uri, resolver)

    /** Stops the video being copied in: nothing of it stays. */
    fun cancelVideo() = mediaAdding.cancelVideo()

    // Sounds

    /** `nodeId`'s sound, as the players show it, while it has one. */
    fun soundItem(nodeId: String): GalleryItem? = state.value.nodes[nodeId]?.let { soundOf(it, untitled()) }

    /** Plays `nodeId`'s sound, or pauses it when it's playing. */
    fun toggleSound(nodeId: String) {
        soundItem(nodeId)?.let(sounds::toggle)
    }

    fun pauseSound() = sounds.pause()

    /** Stops the sound and closes the small player. */
    fun stopSound() = sounds.stop()

    /** The sound playing shows full screen, among the map's pictures, videos and sounds, where it goes on. */
    fun viewSound(nodeId: String) = view(nodeId, MediaKind.Sound)

    /**
     * Copies a sound from the phone into the map's folder and puts it on the
     * idea, its caption from the file's name when the idea's sound had none.
     * The editor shows how far along the copy is.
     */
    fun addSound(nodeId: String, uri: Uri, resolver: ContentResolver) = mediaAdding.addSound(nodeId, uri, resolver)

    /** Stops the sound being copied in: nothing of it stays. */
    fun cancelSound() = mediaAdding.cancelSound()

    /** Opens the recorder for a voice memo on `nodeId`. A talk going on ends first: the microphone is the recorder's. */
    fun recordMemo(nodeId: String) {
        versionHistory.close()
        stopEditing()
        sounds.pause()
        state.update { it.copy(recording = nodeId, panel = it.panel.takeIf { panel -> panel is Panel.Editor }) }
    }

    fun closeRecorder() {
        state.update { it.copy(recording = null) }
    }

    /**
     * Keeps a voice memo, `recording`, on `nodeId`, keeping the caption its
     * sound had, with Undo. Returns why it couldn't, or null once it's on.
     */
    suspend fun saveRecording(nodeId: String, recording: File): ShownText? = mediaAdding.saveRecording(nodeId, recording)

    /** What the map's pictures, videos and sounds come to, in bytes: a new voice memo may take what's left of a map's room. */
    suspend fun mediaSize(): Long = withContext(Dispatchers.IO) { maps.mediaSize(id) }

    /** How long a sound in the map's folder is (ms), or null when it can't be read; read once. */
    suspend fun soundLength(src: String): Long? {
        if (soundLengths.containsKey(src)) {
            return soundLengths[src]
        }

        val file = mediaModel(src) as? File ?: return null

        val length = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(file.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
            } catch (unreadable: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

        soundLengths[src] = length
        return length
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

    /** A talk begins: what's said joins the orbit in focus, and one undo takes it all back. A sound playing stops: the phone listens. */
    fun startTalking() {
        versionHistory.close()
        stopEditing()
        sounds.stop()
        spokenSent = 0
        spokenSaved = 0
        state.update { it.copy(panel = null) }
    }

    /** Ideas just said, into the orbit of the idea in focus. */
    fun addSpoken(ideas: List<String>) {
        val parent = state.value.sun?.id ?: state.value.map?.root?.id ?: return

        ideas.forEach { idea ->
            spokenSent += 1
            page.send(MapCommand.Add(parent, buildJsonObject { put("text", JsonPrimitive(idea)) }))
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
        versionHistory.close()
        stopEditing()

        // Closed a moment ago, the editor may still be saving its last typing: it opens again on the map as that leaves it.
        sourceEditing.afterTyping {
            sourceStart = markdown
            // Text edited by hand can change anything: the map as it was goes into the history first.
            reasonBeforeSource = nextReason
            nextReason = VersionReason.Text
            state.update { it.copy(panel = null, source = SourceState(markdown)) }
            page.send(MapCommand.Source(markdown))
        }
    }

    /** Draws the text as it's typed, a moment after the typing stops, and saves it when Orbit reads it. */
    fun editSource(text: String) = sourceEditing.edit(text)

    fun setSourceYaml(yaml: Boolean) = sourceEditing.setYaml(yaml)

    fun closeSource() {
        val start = sourceStart
        sourceStart = null

        // Text typed a moment before is saved first, so closing never loses it.
        sourceEditing.close {
            // The map as the last text says, drawn; that text was saved as it was typed.
            page.send(MapCommand.Restore(markdown, revision = generation))

            if (start != null && start != markdown) {
                remember(start)
                state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
            } else if (nextReason == VersionReason.Text) {
                // Nothing was typed: the next change isn't one made by hand.
                nextReason = reasonBeforeSource
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
        // Under its lock, a private map isn't presented: its audience would see it.
        if (locked.value) {
            return
        }

        val map = state.value.map ?: return
        versionHistory.close()
        stopEditing()
        // A sound playing over the map stops: in a presentation, each idea's plays from its own button.
        sounds.stop()
        val ideas = map.nodes.map { it.id }
        val order = presentationPath.path.filter { it in ideas }.ifEmpty { ideas }
        val start = if (fromStart || rehearse) 0 else order.indexOf(state.value.sun?.id).coerceAtLeast(0)
        rehearsed.clear()
        val planned = if (rehearse) null else maps.summary(id)?.metadata?.rehearsal

        state.update {
            it.copy(panel = null, pathMaking = false, presentation = Presentation(order, start, playing = seconds > 0, rehearsing = rehearse, planned = planned))
        }

        page.send(MapCommand.Present(true))
        page.send(MapCommand.View(ViewMode.FOCUS))
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
        val index = presentation.index
        rehearsed[index] = (rehearsed[index] ?: 0L) + SystemClock.elapsedRealtime() - presentation.stepStartedAt

        if (rehearsed.values.sum() < REHEARSAL_MINIMUM_MILLISECONDS) {
            return
        }

        val rehearsal = Rehearsal.of(presentation.order, rehearsed)
        val earlier = maps.summary(id)?.metadata?.rehearsal.orEmpty()
        val kept = rehearsal.keptOver(earlier, state.value.nodes.keys, readWhole = state.value.readOnly == null)
        viewModelScope.launch { remembering { maps.saveRehearsal(id, kept) } }
        state.update { it.copy(rehearsal = rehearsal) }
    }

    fun closeRehearsal() {
        state.update { it.copy(rehearsal = null) }
    }

    // The presentation's path

    /** Starts choosing the presentation's path: a new one at the top of the map, one being changed where it ends. */
    fun startPath() {
        versionHistory.close()
        stopEditing()
        presentationPath.start()
    }

    /**
     * Keeps the path, and flies back to where it starts, ready to present; one
     * with fewer than two ideas is none, and the map presents in reading order.
     */
    fun finishPath() = presentationPath.finish()

    /** Adds the idea in focus to the path again, to come back to it there. */
    fun addToPath() = presentationPath.add()

    fun removePathStep(index: Int) = presentationPath.removeStep(index)

    /** Moves the step at `index` earlier (-1) or later (1) in the path. */
    fun movePathStep(index: Int, by: Int) = presentationPath.moveStep(index, by)

    /** Starts the path over from the top of the map, with a way back. */
    fun clearPath() = presentationPath.clear()

    /** Shows or hides the captions, for the map alone. */
    fun setCaptions(on: Boolean) = control.setCaptions(on)

    /** Steps back or on through the presentation, closing a picture or video the audience sees or this phone shows, and stopping a sound. */
    fun step(by: Int, seconds: Int = 0) {
        if (state.value.fullScreen) {
            onThisPhone { it.copy(viewing = null, playing = null) }
        }

        sounds.stop()

        control.step(by, seconds)
    }

    fun setPlaying(playing: Boolean, seconds: Int) = control.setPlaying(playing, seconds)

    /** Shows the audience the picture of the idea on screen, or plays its video, big over the map. */
    fun showMedia(kind: MediaKind) = control.showMedia(kind)

    /** Plays the video the audience sees, or pauses it. */
    fun playMedia(playing: Boolean) = control.playMedia(playing)

    /** Plays the video the audience sees from its start. */
    fun startMediaOver() = control.startMediaOver()

    /** Plays the live photo the audience sees through once more, on every screen. */
    fun replayMotion() = control.replayMotion()

    /** Takes the audience back to the map from a picture or video. */
    fun closeMedia() = control.closeMedia()

    /** How long a video in the map's folder is, in seconds, or null when it can't be read: the phone's own reckoning stops at its end. */
    private suspend fun videoLength(video: Media): Double? {
        val file = mediaModel(video.src) as? File ?: return null

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
        sounds.stop()
        state.update { it.copy(presentation = null) }
        page.send(MapCommand.Present(false))
    }

    /**
     * Presents the map from its start, or with `study`, studies it from the
     * top, whatever it was doing: what a map's card on Home asks of the map
     * open beside it. A presentation or session going ends first, and a
     * panel beside the map closes, the version history with it.
     */
    fun startFromCard(study: Boolean, seconds: Int) {
        if (state.value.presentation != null) {
            stopPresentation()
        }

        if (state.value.study != null) {
            stopStudy()
        }

        show(null)

        if (study) {
            startStudy(fromTop = true)
        } else {
            startPresentation(seconds, fromStart = true)
        }
    }

    // Studying

    /** Studies the cards under the idea in focus, or `fromTop`, under the top of the map. A sound playing stops: the session has the screen to itself. */
    fun startStudy(fromTop: Boolean = false) {
        if (locked.value) {
            return
        }

        val map = state.value.map ?: return
        versionHistory.close()
        stopEditing()
        sounds.stop()
        val start = if (fromTop || !state.value.focused) map.root?.id else state.value.sun?.id
        studying.start(start ?: return)
    }

    /** Shows what's under the card on screen. */
    fun revealCard() = studying.reveal()

    /** Answers the card on screen: known, or Again. Its sound stops as the next card comes. */
    fun answerCard(knew: Boolean) {
        sounds.stop()
        studying.answer(knew)
    }

    fun undoAnswer() {
        sounds.stop()
        studying.undo()
    }

    /** Studies every card of the session's branch, due or not. */
    fun studyAnyway() = studying.studyAnyway()

    /** Studies the new cards left for another day now. */
    fun keepStudying() = studying.keepGoing()

    /** How many new cards a map brings each day, chosen where a session runs out of them: it's Settings' choice too. */
    fun chooseNewCardsADay(count: Int) {
        newCards.value = count
        viewModelScope.launch { appSettings.update { it.copy(newCardsADay = count) } }
    }

    fun stopStudy() {
        sounds.stop()
        studying.stop()
        // The session's answers go to the folder the maps are kept in soon, for the other devices there.
        app.folderSync.request()
    }

    // The map as a whole

    fun summary(): MapSummary? = maps.summary(id)

    suspend fun currentMarkdown(): String = markdown.ifEmpty { maps.read(id) }

    /** A picture of the map: as on screen, or of its text in another view. */
    suspend fun picture(request: PictureRequest): ByteArray? = try {
        if (request.markdown == null) {
            app.pictures.draw(page, request)
        } else {
            app.pictures.draw(request.copy(baseUrl = request.baseUrl ?: mapBaseUrl(id), theme = request.theme ?: theme))
        }
    } catch (failed: PageException) {
        events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.map_picture_not_drawn)))
        null
    }

    suspend fun yaml(): String? = convert(currentMarkdown(), "yaml")

    /** The map as OPML, for outliners and other mind map apps. */
    suspend fun opml(): String? = convert(currentMarkdown(), "opml")

    /** What goes with the map when it's shared: its own pictures, videos and audio, but those `leavingOut`. */
    suspend fun mediaSummary(leavingOut: Set<String> = emptySet()): MediaSummary = maps.mediaSummary(id, leavingOut)

    /** The map's own files that can't go clean: see `MapRepository.uncleaned`. */
    suspend fun uncleaned(): Uncleaned = maps.uncleaned(id)

    /** How many of the map's sounds a web page of it carries inside, with those of `asTheyAre`: see `MapRepository.soundsInPage`. */
    suspend fun soundsInPage(asTheyAre: Set<String> = emptySet()): Int = maps.soundsInPage(id, asTheyAre)

    /** How many of the map's live photos' motions a web page of it carries inside, with those of `asTheyAre`: see `MapRepository.motionsInPage`. */
    suspend fun motionsInPage(asTheyAre: Set<String> = emptySet()): Int = maps.motionsInPage(id, asTheyAre)

    /**
     * What Coil loads for a picture or thumbnail: a web address (an http one
     * over https, see [pictureAddress]), a picture inside the map's text, or
     * a file in the map's folder. An address of any other kind, as a file: or
     * content: one, or a path that leads out of the folder, loads nothing, as
     * on the map's page.
     */
    fun mediaModel(src: String): Any? {
        if (src.contains("://") || src.startsWith("data:")) {
            return pictureAddress(src).takeIf { isWebPicture(src) }
        }

        val folder = maps.folder(id).canonicalFile
        val file = File(folder, src.removePrefix("./")).canonicalFile
        return file.takeIf { it.path.startsWith(folder.path + File.separator) }
    }

    /** The map's zip, with those of its files that can't go clean that are in `asTheyAre` going as they are. */
    suspend fun writeZip(output: java.io.OutputStream, asTheyAre: Set<String> = emptySet()) = maps.writeZip(id, output, asTheyAre)

    /** The map's folder, where its pictures are. */
    fun folder(): File = maps.folder(id)

    /** The map as one web page that flies in any browser, offline (see [WebPage]), with the files of `asTheyAre` as they are. */
    suspend fun webPage(context: Context, asTheyAre: Set<String> = emptySet()): String? {
        val theme = theme ?: return null
        val ideas = state.value.nodes.keys
        val presentPath = presentationPath.path.filter { it in ideas }.takeIf { it.size > 1 }.orEmpty()
        return WebPage.build(context, pageTitle(context), currentMarkdown(), theme, maps.folder(id), path = presentPath, asTheyAre = asTheyAre)
    }

    /**
     * The map's web page laid out for paper only, for printing (MapPrinter),
     * with the pictures of `asTheyAre` as they are: `theme` is the app's
     * colors in light. Null when it couldn't be made.
     */
    suspend fun printPage(context: Context, theme: AppTheme, request: PrintRequest, asTheyAre: Set<String> = emptySet()): String? = try {
        WebPage.build(context, pageTitle(context), currentMarkdown(), theme, maps.folder(id), print = request, asTheyAre = asTheyAre)
    } catch (failed: IOException) {
        null
    }

    /** Says the map couldn't be laid out for printing. */
    fun printFailed() {
        events.trySend(MapEvent.Snackbar(ShownText.Resource(R.string.export_print_failed)))
    }

    /** The title a page of the map has, and a print job. */
    fun pageTitle(context: Context): String = state.value.map?.title ?: summary()?.title ?: context.getString(R.string.map_web_page_title)

    /** Moves the map to the trash; the home screen offers it back with Undo. */
    fun deleteMap() {
        deleted = true

        viewModelScope.launch {
            app.pictures.forget(id)
            maps.delete(id)?.let { app.deletedMaps.trySend(it) }
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
        sounds.release()
        // A presentation is over once its map goes: a TV or browsers still watching go back to waiting.
        control.stop()
        val readOnly = state.value.readOnly
        val unchangeable = readOnly != null
        // A map Orbit couldn't read all of is changed only by hand, in its text: what was typed there is kept too.
        val typable = canEditText(readOnly)
        val closing = page

        app.applicationScope.launch(Dispatchers.Main) {
            if (!deleted && !unchangeable) {
                // What's still being typed on the page is kept, and so is what a field of the screen had left to send.
                // The page answers a check only after everything it said before it: then its last save has come.
                closing.send(MapCommand.StopEditing(true))

                withTimeoutOrNull(LAST_WORD_MILLISECONDS) {
                    try {
                        closing.request(CheckedMap.serializer()) { job -> MapCommand.Check(job, "") }
                    } catch (stopped: PageException) {
                        // A page that stopped has nothing more to say.
                    }
                }
            }

            // Text typed in the source editor a moment ago, its pause not over or its YAML still converting, is
            // saved now, while the page is still there to convert it.
            if (!deleted && typable) {
                withTimeoutOrNull(LAST_WORD_MILLISECONDS) { sourceEditing.flush() }
            }

            app.givePage(closing)
            // A save being written lands first: the last edit is written over it, from what it left.
            saving?.join()
            val unsaved = markdown
            val base = written

            // The last edit is saved even as the screen goes, and pictures taken off the map go with it.
            if (!deleted && typable && unsaved.isNotEmpty()) {
                try {
                    var result: WriteResult = WriteResult.Written
                    var tries = 0

                    // Storage that's full is tried again a while, as long as Orbit runs, for room to free up.
                    while (unsaved != base && tries <= CLOSED_SAVE_TRIES) {
                        result = maps.write(id, unsaved, base = base)

                        if (result != WriteResult.NotWritten) {
                            break
                        }

                        tries += 1
                        delay(SAVE_RETRY_MILLISECONDS)
                    }

                    // Pictures only an edit the storage never took points at stay where they are: nothing of it goes while it isn't written.
                    if (result != WriteResult.NotWritten) {
                        maps.removeUnusedPictures(id)
                    }
                } catch (failed: IOException) {
                    // Storage that's full or gone: the edit can't be kept, and the screen that would say so is gone.
                    Logs.warn("OrbitMap", "Couldn't save the last edit as the map closed", failed)
                }
            }
        }
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

/**
 * The screen's state without a panel on an idea the map no longer has, which
 * would show nothing: an undo took back the text that gave the idea its id,
 * say, or the idea was deleted.
 */
internal fun MapUi.withoutPanelOnGoneIdea(): MapUi {
    val idea = when (val shown = panel) {
        is Panel.Editor -> shown.id
        is Panel.Details -> shown.id
        is Panel.Menu -> shown.id
        else -> return this
    }

    return if (map?.nodes?.any { it.id == idea } == true) this else copy(panel = null)
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
