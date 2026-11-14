package com.hyuchia.orbit.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A map's tags from its card, as Home's sheet keeps them: each tap sends them all, so the chips must be what the map
 * will have. They follow the map's file as its changes land or come from elsewhere, and go back to the file's tags
 * only when this map's change wasn't kept, never when something else went wrong meanwhile.
 */
@RunWith(AndroidJUnit4::class)
class MapTagsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var map by mutableStateOf(trip(listOf("Work")))

    /** What each tap sent, in order. */
    private val sent = mutableListOf<List<String>>()

    /** The maps whose tags weren't kept, as Home tells the sheet. */
    private val notKept = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private fun trip(tags: List<String>) = MapSummary("trip", MapOutline("Trip", branches = 0, ideas = 0, tags = tags), 0, MapMetadata(), "", "")

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false)) {
                OrbitTheme(dark = false) {
                    MapTagsSheet(map, known = listOf("Work", "Home", "Lisbon"), onChange = { sent += it }, onDismiss = {}, notKept = notKept)
                }
            }
        }

        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithText(tag).performClick()
        compose.waitForIdle()
    }

    private fun tell(id: String) {
        compose.runOnIdle { notKept.tryEmit(id) }
        compose.waitForIdle()
    }

    @Test
    fun `another map's problem leaves the tags just picked, and only this map's change not kept shows its file's tags`() {
        show()
        tap("Lisbon")

        // A duplicate of another map fails while Lisbon is on its way to the file: the sheet keeps it.
        tell("plan")
        compose.onNodeWithText("Lisbon").assertIsSelected()
        tap("Home")

        assertEquals(listOf("Work", "Lisbon", "Home"), sent.last())

        // This map's change wasn't kept: the sheet shows what its file has.
        tell("trip")
        compose.onNodeWithText("Work").assertIsSelected()
        compose.onNodeWithText("Lisbon").assertIsNotSelected()
        compose.onNodeWithText("Home").assertIsNotSelected()
    }

    @Test
    fun `the chips follow the map's file as its own changes land, and as it changes elsewhere`() {
        show()
        tap("Lisbon")
        tap("Home")

        // The first change lands while the second is on its way: the second's tags stay.
        compose.runOnIdle { map = trip(listOf("Work", "Lisbon")) }
        compose.waitForIdle()
        compose.onNodeWithText("Home").assertIsSelected()

        compose.runOnIdle { map = trip(listOf("Work", "Lisbon", "Home")) }
        compose.waitForIdle()
        compose.onNodeWithText("Home").assertIsSelected()

        // Another device takes Work off and adds Garden: the sheet shows the map as it is now, and the next tap keeps it.
        compose.runOnIdle { map = trip(listOf("Lisbon", "Home", "Garden")) }
        compose.waitForIdle()
        compose.onNodeWithText("Work").assertIsNotSelected()
        compose.onNodeWithText("Garden").assertIsSelected()
        tap("Work")

        assertEquals(listOf("Lisbon", "Home", "Garden", "Work"), sent.last())
    }
}
