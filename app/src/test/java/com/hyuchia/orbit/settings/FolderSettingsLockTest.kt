package com.hyuchia.orbit.settings

import android.app.KeyguardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings showing the folder the maps are kept in while private maps
 * lock, from its own Lock them now and as the screen goes off: it stays,
 * and still shows the folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class FolderSettingsLockTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** A private map, unlocked, and the maps kept in a folder, with Settings open on them. */
    private fun settingsWithAFolder() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        app.container.privateMaps.refreshScreenLock()

        runBlocking {
            app.container.maps.load()
            app.container.maps.setPrivate(app.container.maps.create("Secret journal"), true)
            app.container.settings.update { it.copy(syncFolder = "content://com.android.externalstorage.documents/tree/primary%3AOrbit") }
        }

        compose.runOnUiThread { app.container.privateMaps.lock.unlock() }

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false)) {
                OrbitTheme(dark = false) { SettingsScreen(onAbout = {}, onTrash = {}, onBack = {}) }
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) { shows("Stop keeping maps there") && shows("Lock them now") }
    }

    @Test
    fun `locking private maps from Settings with a folder set leaves Settings there`() {
        settingsWithAFolder()

        compose.onNodeWithText("Lock them now").performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue(app.container.privateMaps.view.value.locked)
        assertTrue(shows("Stop keeping maps there"))
    }

    @Test
    fun `the screen going off with Settings on a folder leaves Settings there`() {
        settingsWithAFolder()

        compose.runOnUiThread { app.container.privateMaps.lock.screenOff() }
        compose.waitForIdle()

        assertTrue(app.container.privateMaps.view.value.locked)
        assertTrue(shows("Stop keeping maps there"))
    }
}
