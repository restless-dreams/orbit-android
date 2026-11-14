package com.hyuchia.orbit.capture

import android.app.KeyguardManager
import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hyuchia.orbit.OrbitApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Quick capture opened again while its sheet is there, from the tile or a
 * widget's +: Android brings back the sheet there is, with what was typed
 * in it, rather than clearing its task for an empty one.
 */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), so the sheet and its field are on screen.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class CaptureAgainTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** The sheet opened again over itself, as Android hands it the tile's or a widget's intent. */
    private fun ActivityScenario<CaptureActivity>.openedAgain(intent: Intent) {
        onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, intent) }
        compose.waitForIdle()
    }

    @Test
    fun `the tile and a widget's + bring back the sheet that's there, never clearing it for an empty one`() {
        val flags = captureIntent(app).flags

        assertEquals(0, flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun `opened again, the sheet keeps its draft, and a widget's + chooses its map, shown with the draft`() {
        val (lisbon, groceries) = runBlocking {
            app.container.maps.load()
            app.container.maps.create("Lisbon") to app.container.maps.create("Groceries")
        }

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, lisbon)).use { sheet ->
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            compose.onNodeWithText("Your idea").performTextInput(DRAFT)
            compose.waitUntil(timeoutMillis = 5_000) { shows(DRAFT) }

            // The tile, tapped again: nothing changes.
            sheet.openedAgain(captureIntent(app))
            assertTrue(shows(DRAFT))
            assertTrue(shows("Lisbon"))

            // Another map's widget: its map is chosen, named above the draft, which stays.
            sheet.openedAgain(captureIntent(app, groceries))
            compose.waitUntil(timeoutMillis = 5_000) { shows("Groceries") }
            assertFalse(shows("Lisbon"))
            assertTrue(shows(DRAFT))

            // Lisbon chosen in the sheet, then the same widget again: it asks for its map again, and gets it.
            compose.onNodeWithText("Map").performClick()
            compose.onNodeWithText("Lisbon").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !shows("Groceries") }
            sheet.openedAgain(captureIntent(app, groceries))
            compose.waitUntil(timeoutMillis = 5_000) { shows("Groceries") }
            assertTrue(shows(DRAFT))
        }
    }

    @Test
    fun `a draft for a private map stays as private maps lock, under a lock of its own, and shows again once they're unlocked`() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        app.container.privateMaps.refreshScreenLock()

        val (journal, groceries) = runBlocking {
            app.container.maps.load()
            val journal = app.container.maps.create("Secret journal")
            app.container.maps.setPrivate(journal, true)
            journal to app.container.maps.create("Groceries")
        }

        compose.runOnUiThread { app.container.privateMaps.lock.unlock() }

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, journal)).use { sheet ->
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            compose.onNodeWithText("Your idea").performTextInput(DRAFT)
            compose.waitUntil(timeoutMillis = 5_000) { shows(DRAFT) }

            // Private maps lock, as after a minute away: the draft stays, but nothing of it shows.
            compose.runOnUiThread { app.container.privateMaps.lock.lock() }
            compose.waitUntil(timeoutMillis = 5_000) { shows(KEPT) }
            assertFalse(shows(DRAFT))
            assertFalse(shows("Secret journal"))

            // Opened again for another map: still under its lock, and nowhere to add it.
            sheet.openedAgain(captureIntent(app, groceries))
            compose.waitUntil(timeoutMillis = 5_000) { shows("Groceries") }
            assertFalse(shows(DRAFT))
            compose.onNodeWithText("Add").assertIsNotEnabled()

            // Unlocked: it's back, whole.
            compose.runOnUiThread { app.container.privateMaps.lock.unlock() }
            compose.waitUntil(timeoutMillis = 5_000) { shows(DRAFT) }
            assertFalse(shows(KEPT))
        }
    }

    @Test
    fun `a draft typed for a private map while it's locked is under its lock once the sheet comes back`() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        app.container.privateMaps.refreshScreenLock()

        val journal = runBlocking {
            app.container.maps.load()
            app.container.maps.create("Secret journal").also { app.container.maps.setPrivate(it, true) }
        }

        compose.runOnUiThread { app.container.privateMaps.lock.lock() }

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, journal)).use { sheet ->
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            compose.onNodeWithText("Your idea").performTextInput(DRAFT)
            compose.waitUntil(timeoutMillis = 5_000) { shows(DRAFT) }

            // Away, as Home takes the sheet off the screen, and back: whoever has the phone now sees nothing of it.
            sheet.moveToState(Lifecycle.State.CREATED)
            sheet.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(timeoutMillis = 5_000) { shows(KEPT) }
            assertFalse(shows(DRAFT))
        }
    }

    @Test
    fun `back after private maps locked while it was away, no frame of the sheet shows the draft or its map`() {
        val journal = privateJournal()
        compose.runOnUiThread { app.container.privateMaps.lock.unlock() }

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, journal)).use { sheet ->
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            compose.onNodeWithText("Your idea").performTextInput(DRAFT)
            compose.waitUntil(timeoutMillis = 5_000) { shows(DRAFT) }

            // Away (Home), and private maps lock meanwhile.
            sheet.moveToState(Lifecycle.State.CREATED)
            compose.runOnUiThread { app.container.privateMaps.lock.lock() }

            // Back, one frame at a time: from the first, the draft is under its lock.
            compose.mainClock.autoAdvance = false
            sheet.moveToState(Lifecycle.State.RESUMED)

            repeat(3) {
                compose.mainClock.advanceTimeByFrame()
                assertFalse(shows(DRAFT))
                assertFalse(shows("Secret journal"))
            }

            compose.mainClock.autoAdvance = true
            compose.waitUntil(timeoutMillis = 5_000) { shows(KEPT) }
        }
    }

    @Test
    fun `turning the phone while typing for a private map that's locked leaves what's typed in sight`() {
        val journal = privateJournal()
        compose.runOnUiThread { app.container.privateMaps.lock.lock() }

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, journal)).use { sheet ->
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            compose.onNodeWithText("Your idea").performTextInput(DRAFT)
            compose.waitUntil(timeoutMillis = 5_000) { shows(DRAFT) }

            sheet.recreate()
            compose.waitUntil(timeoutMillis = 20_000) { shows(DRAFT) }
            assertFalse(shows(KEPT))
        }
    }

    /** A private map, "Secret journal", on a phone with a screen lock, and its id. */
    private fun privateJournal(): String {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        app.container.privateMaps.refreshScreenLock()

        return runBlocking {
            app.container.maps.load()
            app.container.maps.create("Secret journal").also { app.container.maps.setPrivate(it, true) }
        }
    }

    private companion object {
        const val DRAFT = "A draft typed before tapping the tile again"

        /** What the sheet says in place of a private map's draft while private maps are locked. */
        const val KEPT = "Your ideas for it are kept here"
    }
}
