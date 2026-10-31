package com.hyuchia.orbit.map

import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The presentation's path, while it's chosen on the map and since it was
 * kept: the ideas to present, in order. Each new idea flown to joins it, and
 * its steps can be added again, taken out, moved and started over. The path
 * is in `ui` too, for the strip that shows it.
 */
class PathMaking(
    private val ui: MutableStateFlow<MapUi>,

    /** Sends the map's page a command. */
    private val send: (MapCommand) -> Unit,

    /** Keeps the finished path with the map. */
    private val keep: (List<String>) -> Unit,

    /** Says something on the screen. */
    private val say: (MapEvent) -> Unit,
) {
    /** The presentation's path, as it's being made or since it was saved. */
    var path: List<String> = emptyList()
        private set

    /** Starts choosing the presentation's path: a new one at the top of the map, one being changed where it ends. */
    fun start() {
        val ideas = ui.value.nodes.keys
        val kept = path.filter { it in ideas }
        val start = if (kept.size > 1) kept.last() else ui.value.map?.root?.id

        if (kept.size < 2) {
            set(listOfNotNull(start))
        }

        ui.update { it.copy(pathMaking = true, panel = null) }
        // Taps fly while the path is made, and never open a new idea by mistake.
        send(MapCommand.Present(true))
        start?.let(::flyTo)
    }

    private fun flyTo(idea: String) {
        if (!ui.value.focused) {
            send(MapCommand.View(ViewMode.FOCUS))
        }

        send(MapCommand.Focus(idea))
    }

    /**
     * Keeps the path, and flies back to where it starts, ready to present; one
     * with fewer than two ideas is none, and the map presents in reading order.
     */
    fun finish() {
        val ideas = ui.value.nodes.keys
        val kept = path.filter { it in ideas }.takeIf { it.size > 1 }.orEmpty()
        set(kept)
        ui.update { it.copy(pathMaking = false) }
        send(MapCommand.Present(false))
        keep(kept)
        kept.firstOrNull()?.let(::flyTo)
    }

    /** Makes `ideas` the path. */
    fun set(ideas: List<String>) {
        path = ideas
        ui.update { it.copy(presentPath = path) }
    }

    /** Adds the idea in focus to the path again, to come back to it there. */
    fun add() {
        val idea = ui.value.sun?.id ?: return

        if (path.lastOrNull() != idea) {
            set(path + idea)
        }
    }

    fun removeStep(index: Int) {
        if (index in path.indices) {
            set(path.filterIndexed { step, _ -> step != index })
        }
    }

    /** Moves the step at `index` earlier (-1) or later (1) in the path. */
    fun moveStep(index: Int, by: Int) {
        val target = index + by

        if (index !in path.indices || target !in path.indices) {
            return
        }

        set(path.toMutableList().apply { add(target, removeAt(index)) })
    }

    /** Starts the path over from the top of the map, with a way back. */
    fun clear() {
        val before = path
        val root = ui.value.map?.root?.id
        set(listOfNotNull(root))
        root?.let(::flyTo)
        say(MapEvent.Snackbar(ShownText.Resource(R.string.map_path_started_over), undo = true, onUndo = { set(before) }))
    }
}
