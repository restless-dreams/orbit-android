package com.hyuchia.orbit.map

import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.OtherApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The encoding an XML file's declaration names, which a file kept as Unicode text no longer has. */
private val XML_ENCODING = Regex("""^(\uFEFF?\s*<\?xml[^>]*?\bencoding\s*=\s*)["'][^"']*["']""")

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
        val asks = Channel<Long>(Channel.UNLIMITED)
        summaryAsks?.close()
        summaryAsks = asks
        val now = markdown()

        scope.launch {
            val versions = listVersions()
            update { it.copy(versions = versions) }
            val summaries = VersionSummaries { time -> readVersion(time) }

            withContext(Dispatchers.Default) {
                val summaryNow = summaries.now(now, versions)
                update { it.copy(now = summaryNow) }

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
            summaryAsks?.trySend(time)
        }
    }

    private fun update(change: (HistoryUi) -> HistoryUi) {
        ui.update { state -> state.history?.let { state.copy(history = change(it)) } ?: state }
    }

    /** The history closed: its rows' asks go with it. */
    fun closeSummaries() {
        summaryAsks?.close()
        summaryAsks = null
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
