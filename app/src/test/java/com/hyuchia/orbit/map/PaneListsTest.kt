package com.hyuchia.orbit.map

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class PaneListsTest {
    @Test
    fun `a list starts afresh where it was, as a new state`() {
        val scrolled = LazyListState(firstVisibleItemIndex = 18, firstVisibleItemScrollOffset = 80)
        val pane = PaneListState(scrolled)

        pane.startAfresh()

        // A new state: the list keyed on it is made again, and measures afresh.
        assertNotSame(scrolled, pane.list)
        assertEquals(18, pane.list.firstVisibleItemIndex)
        assertEquals(80, pane.list.firstVisibleItemScrollOffset)
    }

    @Test
    fun `where a list was is kept when the activity is made again`() {
        val pane = PaneListState(LazyListState(firstVisibleItemIndex = 12, firstVisibleItemScrollOffset = 40))
        val saved = with(PaneListState.Saver) { SaverScope { true }.save(pane) }!!
        val restored = PaneListState.Saver.restore(saved)!!

        assertEquals(12, restored.list.firstVisibleItemIndex)
        assertEquals(40, restored.list.firstVisibleItemScrollOffset)
    }

    @Test
    fun `a list that never moved starts afresh at its top`() {
        val pane = PaneListState()

        pane.startAfresh()

        assertEquals(0, pane.list.firstVisibleItemIndex)
        assertEquals(0, pane.list.firstVisibleItemScrollOffset)
    }
}
