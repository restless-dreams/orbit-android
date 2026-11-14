package com.hyuchia.orbit.home

import android.app.KeyguardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapTags
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** How long a test waits for work Orbit does off the main thread: CI runs beside other builds. */
private const val SLOW_WORK = 20_000L

/**
 * A private map found by Home's search goes as private maps lock, at once,
 * and so does a tag only it has: not once Home's state, worked out off the
 * main thread, follows the lock.
 */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), so the maps' cards are on screen.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class HomeSearchLockTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Whether screen readers hear `text`: how many maps a search found is said as it's heard, rather than as it shows. */
    private fun says(text: String): Boolean = compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a private map found before they lock is gone the moment they do, while Home's state is still to follow`() {
        val privateMaps = app.container.privateMaps
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        privateMaps.refreshScreenLock()
        privateMaps.lock.unlock()

        // Its tag is on no other map: Home offers it only while the private map shows.
        runBlocking {
            app.container.maps.load()
            app.container.maps.add(MapTags.write("# Quokka journal\n", listOf("Marsupials")), isPrivate = true)
        }

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false, storageNoteDismissed = true)) {
                OrbitTheme(dark = false) {
                    HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {})
                }
            }
        }

        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Welcome to Orbit") }
        compose.onNodeWithText("Search your maps").performTextInput("Quokka")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Quokka journal") && says("1 map matches") }
        assertTrue(shows("Marsupials"))

        // Every thread Home's state is worked out on is busy: it can't follow the lock until they're free.
        val workers = Runtime.getRuntime().availableProcessors()
        val started = CountDownLatch(workers)
        val release = CountDownLatch(1)
        val busy = CoroutineScope(SupervisorJob())

        val blocking = List(workers) {
            busy.launch(Dispatchers.Default) {
                started.countDown()
                release.await()
            }
        }

        assertTrue(started.await(10, TimeUnit.SECONDS))

        try {
            compose.runOnUiThread { privateMaps.lock.lock() }
            compose.waitForIdle()
            // Gone, card, menu and count: nothing found says one was, and no tag chip says it's there.
            assertFalse(shows("Quokka journal"))
            assertFalse(says("1 map matches"))
            assertTrue(says("No maps match"))
            assertFalse(shows("Marsupials"))
        } finally {
            release.countDown()
            runBlocking { blocking.joinAll() }
        }

        // Once Home's state follows, it stays gone.
        compose.waitForIdle()
        assertFalse(shows("Quokka journal"))
        assertFalse(shows("Marsupials"))
    }
}
