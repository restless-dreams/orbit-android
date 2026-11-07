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
}
