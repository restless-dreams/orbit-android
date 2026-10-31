package com.hyuchia.orbit.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.keepRestoredPrivateMapsNote
import com.hyuchia.orbit.data.privateMapsLeftOutOfRestore
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

private const val TITLE = "Your 2 private maps weren't in the backup"

/**
 * After a restore from a backup that left private maps out, Home says how
 * many, and how they come back, once: "Got it" puts it away for good.
 */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), so the card and its button are on screen to tap.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class PrivateRestoreNoteTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()

    /** As a restore leaves it: the count its backup carried, beside the maps, then kept for Home. */
    private fun restored(count: Int) {
        runBlocking { app.container.maps.load() }
        File(app.filesDir, "maps/.private-maps-left-out").apply { parentFile!!.mkdirs() }.writeText(count.toString())
        keepRestoredPrivateMapsNote(app.filesDir)
    }

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Home says once how many private maps the backup left out, and that Export all brings them back`() {
        restored(2)
        var open by mutableStateOf(true)

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false, storageNoteDismissed = true)) {
                OrbitTheme(dark = false) {
                    if (open) {
                        HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {})
                    }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) { shows(TITLE) }
        compose.onNodeWithText("Import maps in Settings brings them back, still private", substring = true).assertExists()
        compose.onNodeWithText("Got it").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !shows(TITLE) }
        compose.waitUntil(timeoutMillis = 5_000) { privateMapsLeftOutOfRestore(app.filesDir) == 0 }

        // Home again, as after Orbit starts again: it isn't said twice.
        open = false
        compose.waitForIdle()
        open = true
        compose.waitUntil(timeoutMillis = 20_000) { shows("Welcome to Orbit") }
        assertEquals(false, shows(TITLE))
    }

    @Test
    fun `with a folder, it says they come back from it as ordinary maps, to make private again`() {
        restored(2)

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false, syncFolder = "content://folder")) {
                OrbitTheme(dark = false) {
                    HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {})
                }
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) { shows(TITLE) }
        compose.onNodeWithText("they come back from it as ordinary maps: make them private again", substring = true).assertExists()
    }
}
