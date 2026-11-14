package com.hyuchia.orbit.widget

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** When the home screen's widgets are drawn again as the maps change. */
class WidgetRedrawsTest {
    private fun map(id: String, title: String = id, private: Boolean = false) =
        MapSummary(id, MapOutline(title, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    @Test
    fun `an edit waits for the maps to settle, and a map made private is drawn at once`() = runTest {
        val maps = MutableStateFlow<List<MapSummary>?>(null)
        val drawn = mutableListOf<Long>()
        val drawing = launch { widgetRedraws(maps, 2.seconds).collect { drawn += currentTime } }
        runCurrent()

        // The maps as they're first read: the widgets drew them already.
        maps.value = listOf(map("journal"), map("garden"))
        advanceTimeBy(5_000)
        assertEquals(emptyList<Long>(), drawn)

        // Typing: drawn once it settles, not on every letter.
        maps.value = listOf(map("journal", "Journ"), map("garden"))
        advanceTimeBy(1_000)
        maps.value = listOf(map("journal", "Journal"), map("garden"))
        advanceTimeBy(1_999)
        assertEquals(emptyList<Long>(), drawn)
        advanceTimeBy(2)
        assertEquals(listOf(8_000L), drawn)

        // Made private: its widget shows its lock right away, with no name or picture for a moment longer.
        maps.value = listOf(map("journal", "Journal", private = true), map("garden"))
        runCurrent()
        assertEquals(listOf(8_000L, 8_001L), drawn)

        // Even while an edit waits to be drawn.
        maps.value = listOf(map("journal", "Journal", private = true), map("garden", "Garden"))
        advanceTimeBy(500)
        maps.value = listOf(map("journal", "Journal", private = true), map("garden", "Garden", private = true))
        runCurrent()
        assertEquals(listOf(8_000L, 8_001L, 8_501L), drawn)

        // Not private anymore, it shows its name once the maps settle, as any change does.
        maps.value = listOf(map("journal", "Journal"), map("garden", "Garden", private = true))
        runCurrent()
        assertEquals(3, drawn.size)
        advanceTimeBy(2_001)
        assertEquals(listOf(8_000L, 8_001L, 8_501L, 10_501L), drawn)
        drawing.cancel()
    }

    @Test
    fun `a drawing that fails doesn't stop the widgets following the maps`() = runTest {
        val maps = MutableStateFlow<List<MapSummary>?>(listOf(map("journal"), map("garden")))
        var drawings = 0

        val drawing = launch {
            widgetRedraws(maps, 2.seconds).collect {
                updateMapWidgets {
                    drawings++

                    // Glance's work, or its store, refuses the first one.
                    if (drawings == 1) {
                        throw IllegalStateException("WorkManager is not initialized properly")
                    }
                }
            }
        }

        runCurrent()

        maps.value = listOf(map("journal", private = true), map("garden"))
        runCurrent()
        assertEquals(1, drawings)
        assertTrue(drawing.isActive)

        // The next change draws them again, and the one after.
        maps.value = listOf(map("journal", private = true), map("garden", private = true))
        runCurrent()
        assertEquals(2, drawings)
        maps.value = listOf(map("journal", private = true), map("garden", "Garden", private = true))
        advanceTimeBy(2_001)
        assertEquals(3, drawings)
        drawing.cancel()
    }

    @Test
    fun `a drawing that fails as a map is made private is tried again until it takes`() = runTest {
        val maps = MutableStateFlow<List<MapSummary>?>(listOf(map("journal"), map("garden")))
        val drawn = mutableListOf<Long>()
        var failures = 1
        val redrawing = WidgetRedrawing({ drawn += currentTime; failures-- <= 0 })
        val following = launch { redrawing.follow(widgetRedraws(maps, 2.seconds)) }
        runCurrent()

        // Drawn at once, which fails: the widget would keep the map's name and picture. Again 5 seconds later, which takes.
        maps.value = listOf(map("journal", private = true), map("garden"))
        runCurrent()
        assertEquals(listOf(0L), drawn)
        advanceTimeBy(5_001)
        assertEquals(listOf(0L, 5_000L), drawn)

        // And no more.
        advanceTimeBy(10 * 60_000)
        assertEquals(2, drawn.size)
        following.cancel()
    }

    @Test
    fun `the next change stops the retries, and after the last, the widgets are drawn as Orbit comes back`() = runTest {
        val maps = MutableStateFlow<List<MapSummary>?>(listOf(map("journal"), map("garden")))
        val drawn = mutableListOf<Long>()
        var drawing = false
        val redrawing = WidgetRedrawing({ drawn += currentTime; drawing })
        val following = launch { redrawing.follow(widgetRedraws(maps, 2.seconds)) }
        runCurrent()

        maps.value = listOf(map("journal", private = true), map("garden"))
        runCurrent()
        advanceTimeBy(6_001)
        assertEquals(listOf(0L, 5_000L), drawn)

        // Another map made private draws them itself, and its retries take the place of the first's: none at 35 seconds.
        maps.value = listOf(map("journal", private = true), map("garden", private = true))
        runCurrent()
        advanceTimeBy(10 * 60_000)
        assertEquals(listOf(0L, 5_000L, 6_001L, 11_001L, 41_001L, 161_001L), drawn)

        // Every one failed: as Orbit comes back on screen, they're drawn again, and only while they're behind.
        drawing = true
        redrawing.cameBack()
        assertEquals(7, drawn.size)
        redrawing.cameBack()
        assertEquals(7, drawn.size)
        following.cancel()
    }
}
