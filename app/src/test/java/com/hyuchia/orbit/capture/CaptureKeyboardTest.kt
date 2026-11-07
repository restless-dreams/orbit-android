package com.hyuchia.orbit.capture

import android.app.KeyguardManager
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Quick capture asks keyboards not to learn what's typed for a private map, as the map itself does. */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), so the sheet and its field are on screen.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class CaptureKeyboardTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()

    /** Maps called `titles`, the first one private, and its id. */
    private fun maps(vararg titles: String): String = runBlocking {
        app.container.maps.load()
        val ids = titles.map { app.container.maps.create(it) }
        app.container.maps.setPrivate(ids.first(), true)
        ids.first()
    }

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Whether keyboards may learn from the field with the keyboard's focus, in the sheet. */
    private fun keyboardsLearn(): Boolean {
        compose.waitForIdle()
        val focused = ShadowDialog.getLatestDialog().window!!.decorView.findFocus()!!
        val asked = EditorInfo()
        compose.runOnUiThread { focused.onCreateInputConnection(asked) }
        return asked.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
    }

    @Test
    fun `ideas for a private map ask keyboards not to learn from them, and for another map they may`() {
        // No screen lock: private maps are offered as any other.
        val journal = maps("Secret journal", "Groceries")

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, journal)).use {
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            compose.onNodeWithText("Your idea").performClick()
            assertFalse(keyboardsLearn())

            // The ideas go to another map instead: the same field, with what's typed in it, now learned from.
            compose.onNodeWithText("Your idea").performTextInput("Call Ana")
            compose.onNodeWithText("Map").performClick()
            compose.onNodeWithText("Groceries").performClick()
            compose.onNodeWithText("Call Ana").performClick()
            assertTrue(keyboardsLearn())
        }
    }

    @Test
    fun `ideas for a private map that's locked, waiting for it to be unlocked, ask the same`() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        app.container.privateMaps.refreshScreenLock()
        val journal = maps("Secret journal", "Groceries")

        ActivityScenario.launch<CaptureActivity>(captureIntent(app, journal)).use {
            compose.waitUntil(timeoutMillis = 20_000) { shows("Your idea") }
            // Locked, it isn't named, nor is another map chosen in its place.
            assertFalse(shows("Secret journal"))
            assertFalse(shows("Groceries"))
            compose.onNodeWithText("Your idea").performClick()
            assertFalse(keyboardsLearn())
        }
    }
}
