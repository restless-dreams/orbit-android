package com.hyuchia.orbit.reminders

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A reminder's idea its map hasn't shown yet, through Orbit stopped while
 * it's away (as the screen lock is asked for a private map's) and its screens
 * brought back: each run of Orbit has its own, and the one Android starts
 * again has none until its screens come back with their state.
 */
@RunWith(AndroidJUnit4::class)
class KeepIdeaToShowTest {
    @get:Rule
    val compose = createComposeRule()

    private val restoration = StateRestorationTester(compose)
    private val bags = IdeaToShow("trip", "Bags", "2026-11-19", listOf(2, 0), occurrence = 1)

    /** The idea to show of each run of Orbit, the first one's first; the run on screen now takes the next. */
    private fun runs(first: IdeaToShow?): ArrayDeque<MutableStateFlow<IdeaToShow?>> = ArrayDeque(listOf(MutableStateFlow(first), MutableStateFlow(null)))

    @Test
    fun `an idea not shown yet comes back with the screens, to show once its map is read`() {
        val runs = runs(first = bags)
        var now: MutableStateFlow<IdeaToShow?>? = null

        restoration.setContent {
            val idea = remember { runs.removeFirst() }
            now = idea
            KeepIdeaToShow(idea)
        }

        restoration.emulateSavedInstanceStateRestore()
        assertEquals(bags, now?.value)
    }

    @Test
    fun `one its map took already doesn't come back`() {
        val runs = runs(first = bags)
        var now: MutableStateFlow<IdeaToShow?>? = null

        restoration.setContent {
            val idea = remember { runs.removeFirst() }
            now = idea
            KeepIdeaToShow(idea)
        }

        // The map was read, and pointed at it.
        now?.value = null
        restoration.emulateSavedInstanceStateRestore()
        assertNull(now?.value)
    }
}
