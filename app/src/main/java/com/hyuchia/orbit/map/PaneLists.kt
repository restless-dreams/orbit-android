package com.hyuchia.orbit.map

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

/** How long without a lookahead pass means the panes around a list have stopped moving. */
private const val SETTLED_MILLISECONDS = 250L

/**
 * The state of a lazy list in a panel beside the map, which starts afresh,
 * where it was, once the panes around it stop moving.
 *
 * On a tablet the list of maps steps aside as a panel opens beside the map,
 * and the list-detail scaffold moves the panes in a lookahead pass. A lazy
 * list first measured during that pass goes on measuring only the rows it
 * had then: it scrolls to their edge and no further, by touch, wheel or keys
 * (Compose 1.12, Material 3 adaptive 1.3). One made once the panes are still
 * scrolls as any other, so the list is made again then, at the same place:
 * [list] is a new state, and the list is keyed on it.
 *
 * Every list in the scaffold is measured in a lookahead pass first, the one
 * made again too, and again each time its rows change (results as a search
 * is typed, the outline as the map changes). Those passes measure it as it
 * already is on screen, so only a pass that measures it otherwise counts as
 * the panes moving: counting them all made the list again every quarter
 * second for as long as the panel was open, which took TalkBack's place in
 * it, and the keyboard's.
 */
@Stable
class PaneListState internal constructor(list: LazyListState = LazyListState()) {
    /** The list's state now: a new one, where the last one was, each time the panes stop moving. */
    var list by mutableStateOf(list)
        private set

    /**
     * Each lookahead pass the list is measured in, while the panes around it
     * move. The first comes before anything listens, so it waits to be heard.
     */
    internal val lookahead = Channel<Unit>(Channel.CONFLATED)

    /** How the list was last measured on screen, which a lookahead pass that isn't the panes moving measures it as. */
    internal var onScreen: Constraints? = null

    internal fun startAfresh() {
        list = LazyListState(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
    }

    companion object {
        /** Keeps where the list was when the activity is made again, as rememberLazyListState does. */
        val Saver: Saver<PaneListState, Any> = listSaver(
            save = { listOf(it.list.firstVisibleItemIndex, it.list.firstVisibleItemScrollOffset) },
            restore = { PaneListState(LazyListState(it[0], it[1])) },
        )
    }
}

/** A [PaneListState]: put [paneList] on the list, and key the list on its [PaneListState.list]. */
@Composable
fun rememberPaneListState(): PaneListState {
    val state = rememberSaveable(saver = PaneListState.Saver) { PaneListState() }
    StartsAfreshOnceStill(state)
    return state
}

/**
 * Where a list in a panel is, kept above the panel ([rememberPaneListState]):
 * the first row in view, and how far into it. A panel moves between a sheet
 * over the map and the map's side as a phone or tablet turns, and its list is
 * made anew there: made from this, it starts where the last one was. The list
 * writes it as it scrolls, and nothing is drawn again for that.
 */
class ListPlace(index: Int = 0, offset: Int = 0) {
    var index = index
        internal set

    var offset = offset
        internal set
}

/** A [PaneListState] that starts at `place`, and keeps it where the list is. */
@Composable
fun rememberPaneListState(place: ListPlace): PaneListState {
    val state = remember(place) { PaneListState(LazyListState(place.index, place.offset)) }

    LaunchedEffect(state) {
        snapshotFlow { state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            place.index = index
            place.offset = offset
        }
    }

    StartsAfreshOnceStill(state)
    return state
}

/** Makes [state]'s list again once the panes around it stop moving (see [PaneListState]). */
@Composable
private fun StartsAfreshOnceStill(state: PaneListState) {
    LaunchedEffect(state) {
        state.lookahead.receiveAsFlow().collectLatest {
            delay(SETTLED_MILLISECONDS)
            state.startAfresh()
        }
    }
}

/**
 * Tells [state] each time the list is measured in a lookahead pass as the
 * panes around it move: one that measures it otherwise than on screen now,
 * or before it's been on screen.
 */
fun Modifier.paneList(state: PaneListState): Modifier = layout { measurable, constraints ->
    if (!isLookingAhead) {
        state.onScreen = constraints
    } else if (constraints != state.onScreen) {
        state.lookahead.trySend(Unit)
    }

    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Whether the row at [index] shows whole in the list now. */
internal fun LazyListState.showsWhole(index: Int): Boolean {
    val info = layoutInfo
    val row = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset
}
