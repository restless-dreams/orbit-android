package com.hyuchia.orbit.map

import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.OtherApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The encoding an XML file's declaration names, which a file kept as Unicode text no longer has. */
private val XML_ENCODING = Regex("""^(\uFEFF?\s*<\?xml[^>]*?\bencoding\s*=\s*)["'][^"']*["']""")

/** The version a row compares its own with, among `versions` (the newest first): the newest older one that isn't a file as it came in. */
private fun before(time: Long, versions: List<MapVersion>): Long? =
    versions.dropWhile { it.time != time }.drop(1).firstOrNull { !it.original }?.time

/**
 * The map's version history, while it's open: the versions kept, what each
 * one holds or changed, worked out as its row asks, and a version on the map
 * to look at. The history lives in `ui`.
 */
class HistoryControl(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,

    /** The versions of the map kept, newest first. */
    private val listVersions: suspend () -> List<MapVersion>,

    /** A version's text as it was kept, by its time; null when it can't be read. */
    private val readVersion: suspend (time: Long) -> String?,

    /** The map's text as it is now. */
    private val markdown: () -> String,

    /** Draws text on the map: a version, only to look at, or the map as it is now, to change. */
    private val draw: (text: String, editable: Boolean) -> Unit,

    /** A file another app wrote (OPML, a FreeMind or Freeplane map) as orbit-md, read the way it was brought in; null when it can't be. */
    private val fromOtherApp: suspend (file: String) -> String?,
) {
    /** The history's rows' asks for what their versions changed, while it's open (see [open]). */
    private var summaryAsks: Channel<Long>? = null

    /** The versions being listed, and what they changed worked out, while the history is open. */
    private var listing: Job? = null

    /** The rows that asked what their versions changed since the history opened: asked again of a listing that starts over. */
    private val asked = mutableSetOf<Long>()

    /** A version from the history being read to show on the map (see [show]). */
    private var versionShowing: Job? = null

    /**
     * Closes the version history, if it's open: a version on the map goes,
     * and the map as it is now comes back, for whatever is done to it next.
     */
    fun close() {
        val history = ui.value.history ?: return
        versionShowing?.cancel()

        if (history.showing != null) {
            draw(markdown(), true)
        }

        closeSummaries()
        ui.update { it.copy(history = null) }
    }

    /**
     * Brings back the map as it is now, if an earlier version is on it, for
     * a change: changes are always the map's as it is now, never an earlier
     * version's. The history stays open, on the map as it is now.
     */
    fun showNow() {
        versionShowing?.cancel()

        if (!ui.value.showingVersion) {
            return
        }

        ui.update { state -> state.copy(history = state.history?.copy(showing = null)) }
        draw(markdown(), true)
    }

    /** A version being read to show on the map won't land on it: the page draws the map as it is now. */
    fun stopShowing() {
        versionShowing?.cancel()
    }

    /**
     * Opens the version history: the versions kept, newest first, with the
     * map as it is now showing. What each one changed is worked out as its
     * row asks ([summarize]), one at a time, off the main thread.
     */
    fun open() {
        ui.update { it.copy(panel = Panel.History, history = HistoryUi()) }
        asked.clear()
        list()
    }

    /**
     * The map changed, or its history did, while the history is open: a save,
     * a version kept, a change from elsewhere. The versions are listed again,
     * and what the map as it is now changed from the newest is worked out
     * again, as is what each row already on screen says, where the version
     * it compares with is another now. Until then the rows say what they did.
     */
    fun refresh() {
        val history = ui.value.history ?: return
        list(history.versions, history.summaries.keys)
    }

    /**
     * Lists the versions and works out what the map as it is now changed,
     * then what each row asks for. Listed again (`listed` being the versions
     * the rows on screen were worked out among, `summarized` theirs), the new
     * list lands at once with what the map is now and what changed for the
     * rows whose version compares with another now, so the panel never
     * shows one list with what another said; a listing that starts over
     * meanwhile lands nothing more.
     */
    private fun list(listed: List<MapVersion>? = null, summarized: Set<Long> = emptySet()) {
        val asks = Channel<Long>(Channel.UNLIMITED)
        summaryAsks?.close()
        summaryAsks = asks
        listing?.cancel()
        val now = markdown()
        asked.forEach { asks.trySend(it) }

        listing = scope.launch {
            val versions = listVersions()
            val summaries = VersionSummaries { time -> readVersion(time) }

            if (listed == null) {
                update { it.copy(versions = versions) }
            }

            withContext(Dispatchers.Default) {
                val summaryNow = summaries.now(now, versions)
                val kept = versions.mapTo(HashSet()) { it.time }
                val again = summarized.filter { it in kept && listed != null && before(it, listed) != before(it, versions) }.associateWith { summaries.of(it, versions) }

                // A version thinned out as a newer one was kept goes, with what its row said.
                update { history -> history.copy(versions = versions, now = summaryNow, summaries = history.summaries.filterKeys { it in kept } + again) }

                for (time in asks) {
                    if (ui.value.history?.summaries?.containsKey(time) != false) {
                        continue
                    }

                    val summary = summaries.of(time, versions)
                    update { it.copy(summaries = it.summaries + (time to summary)) }
                }
            }
        }
    }

    /** A row of the history asks what its version holds or changed: worked out in turn, once. */
    fun summarize(time: Long) {
        if (ui.value.history?.summaries?.containsKey(time) == false) {
            asked += time
            summaryAsks?.trySend(time)
        }
    }

    /** Changes the history, if it's still open, unless the listing changing it was let go: one that started over has its own to say. */
    private suspend fun update(change: (HistoryUi) -> HistoryUi) {
        val listing = currentCoroutineContext()

        ui.update { state -> state.history?.takeIf { listing.isActive }?.let { state.copy(history = change(it)) } ?: state }
    }

    /** The history closed: its rows' asks go with it, and what was being worked out for them. */
    fun closeSummaries() {
        summaryAsks?.close()
        summaryAsks = null
        listing?.cancel()
        listing = null
        asked.clear()
    }

    /**
     * Shows a version on the map, to look at; null shows the map as it is
     * now. The last one chosen is the one shown, however long each takes to
     * read, and none is once the history has closed.
     */
    fun show(time: Long?) {
        if (time == null) {
            showNow()
            return
        }

        versionShowing?.cancel()

        if (ui.value.history?.showing == time) {
            return
        }

        versionShowing = scope.launch {
            val text = read(time) ?: return@launch

            if (ui.value.history == null) {
                return@launch
            }

            ui.update { state -> state.history?.let { state.copy(history = it.copy(showing = time)) } ?: state }
            draw(text, false)
        }
    }

    /** A version as orbit-md; null, with its row in the history saying so until it's read, when it can't be read. */
    suspend fun read(time: Long): String? {
        val text = readVersion(time)?.let { markdownOf(it) }

        ui.update { state ->
            val history = state.history ?: return@update state
            state.copy(history = history.copy(unreadable = if (text == null) history.unreadable + time else history.unreadable - time))
        }

        return text
    }

    /**
     * A version as orbit-md. The file another app wrote (OPML, a FreeMind or
     * Freeplane map), kept as it came in, is read the way it was brought in;
     * null when it can't be. It's kept as Unicode text, so the encoding its
     * declaration names no longer holds.
     */
    private suspend fun markdownOf(text: String): String? {
        if (OtherApps.formatOfText(text.take(4096)) == null) {
            return text
        }

        val unicode = text.replaceFirst(XML_ENCODING, "$1\"UTF-8\"")
        return fromOtherApp(unicode)
    }
}
