package com.hyuchia.orbit.map

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A list beside the map on a tablet, in the list-detail scaffold's lookahead
 * scope, where every list made is first measured in a lookahead pass: it
 * starts afresh once, a quarter second after the last pass that measures it
 * otherwise than on screen (in the app, its first, before it's on screen),
 * and then stays the same list, which TalkBack and the keyboard keep their
 * place in.
 */
@RunWith(AndroidJUnit4::class)
class PaneListLookaheadTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Each list state the panel's list was made with, in order, with `rows`
     * rows. The list is placed `along` (px) from where it rests, and measured
     * again as it moves: as the panes slide on a tablet, a panel beside the
     * map keeps its size (380 dp wide, the screen's height) and only moves,
     * so a lookahead pass measures it as it is on screen.
     */
    private fun panel(along: () -> Int = { 0 }, rows: () -> Int): List<LazyListState> {
        val lists = mutableListOf<LazyListState>()

        val sliding = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val x = along()
            layout(placeable.width, placeable.height) { placeable.place(x, 0) }
        }

        compose.setContent {
            LookaheadScope {
                val pane = rememberPaneListState()

                key(pane.list) {
                    if (lists.lastOrNull() !== pane.list) {
                        lists += pane.list
                    }

                    LazyColumn(state = pane.list, modifier = sliding.paneList(pane)) {
                        items(rows()) { Text("Idea $it") }
                    }
                }
            }
        }

        return lists
    }

    /** Lets the clock run `millis`, and the list settle. */
    private fun runFor(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun `a list made in a lookahead pass starts afresh once, then stays`() {
        val lists = panel { 40 }

        runFor(10_000)

        // The first, made as the panel opened, and the one made once the panes stopped moving: not one more each quarter second.
        assertEquals(2, lists.size)
    }

    @Test
    fun `rows changing in a list that's settled don't make it again`() {
        var rows by mutableIntStateOf(40)
        val lists = panel { rows }
        runFor(2_000)

        // What the list shows changes: results as a search is typed, the outline as the map changes.
        rows = 12
        runFor(2_000)
        rows = 30
        runFor(2_000)

        assertEquals(2, lists.size)
    }

    @Test
    fun `a list that slides in with the panes starts afresh once, then stays`() {
        var along by mutableIntStateOf(600)
        val lists = panel(along = { along }) { 40 }

        // A slide of a second and a half, as the list of maps steps aside with animations made slower: a frame
        // each 16 ms, the panel measured again each frame, at the same size.
        repeat(90) {
            along -= 6
            runFor(16)
        }

        val slid = lists.size
        runFor(5_000)

        // Made again once, a quarter second after its first lookahead pass, before it was on screen: the passes as
        // it slides measure it as it is on screen, and make it no more.
        assertEquals(2, slid)
        assertEquals(2, lists.size)
    }
}
