package com.hyuchia.orbit.home

import android.app.KeyguardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** How long a test waits for work Orbit does off the main thread, such as deleting a map: CI runs beside other builds. */
private const val SLOW_WORK = 30_000L

/**
 * What Home says of a private map deleted as private maps lock: worded as
 * they are once it's in the trash, not as they were when it was deleted.
 */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), so the maps' cards are on screen.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class HomePrivateMapsTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()
    private val privateMaps get() = app.container.privateMaps

    /** A map called `title`, with the welcome map Orbit starts with. */
    private fun map(title: String): String = runBlocking {
        app.container.maps.load()
        app.container.maps.create(title)
    }

    /** A private map called `title`, with the welcome map Orbit starts with. */
    private fun privateMap(title: String, cover: LockedCover? = null): String = map(title).also { id ->
        runBlocking {
            app.container.maps.setPrivate(id, true)
            cover?.let { app.container.maps.setCover(id, it) }
        }
    }

    /** The phone has a screen lock, and private maps are unlocked. */
    private fun unlocked() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        privateMaps.refreshScreenLock()
        privateMaps.lock.unlock()
    }

    private fun home(hiding: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false, storageNoteDismissed = true, hideLockedPrivateMaps = hiding)) {
                OrbitTheme(dark = false) {
                    HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {})
                }
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) { shows("Welcome to Orbit") }
    }

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Does what `label` says in the menu of the card of the map called `title`, as screen readers do it, then `then`. */
    private fun fromMenu(title: String, label: String, then: () -> Unit = {}) {
        val card = compose.onNodeWithText(title, substring = true).fetchSemanticsNode()
        val action = card.config[SemanticsActions.CustomActions].first { it.label == label }

        compose.runOnUiThread {
            action.action()
            then()
        }
    }

    /** Deletes the map called `title` from its card's menu, and the screen turns off right after, before it's in the trash. */
    private fun deleteAsTheyLock(title: String) {
        fromMenu(title, "Delete") { privateMaps.lock.screenOff() }
    }

    /**
     * The map `id`, deleted from its own screen, comes to Home to be offered back, and the screen turns off right after:
     * Home words the message once they've locked, but before it hears they did, which comes next. The same happens to a
     * deletion from Home whose work off the main thread is done, waiting for the main thread, as the screen turns off.
     */
    private fun offeredBackAsTheyLock(id: String) {
        val deleted = runBlocking { app.container.maps.delete(id) }!!

        compose.runOnUiThread {
            app.container.deletedMaps.trySend(deleted)
            privateMaps.lock.screenOff()
        }
    }

    @Test
    fun `a private map deleted as they lock isn't named in the message that offers it back`() {
        unlocked()
        privateMap("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        deleteAsTheyLock("Secret journal")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { app.container.maps.trash.value.any { it.title == "Secret journal" } }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted a private map") }
        assertFalse(shows("Secret journal"))

        // Its Undo still brings it back.
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { app.container.maps.maps.value.orEmpty().any { it.outline.title == "Secret journal" } }
    }

    @Test
    fun `worded once they've locked, the message stays as Home hears they did`() {
        unlocked()
        val id = privateMap("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        offeredBackAsTheyLock(id)
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted a private map") }
        assertFalse(shows("Secret journal"))

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { app.container.maps.maps.value.orEmpty().any { it.outline.title == "Secret journal" } }
    }

    @Test
    fun `a map that isn't private keeps its message as they lock`() {
        unlocked()
        val id = map("Groceries")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Groceries") }

        offeredBackAsTheyLock(id)
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted “Groceries”") }
    }

    @Test
    fun `with a cover it's named by its cover, and while they hide it isn't mentioned at all`() {
        unlocked()
        privateMap("Secret journal", cover = LockedCover(icon = "📓", name = "Journal"))
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        deleteAsTheyLock("Secret journal")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { app.container.maps.trash.value.any { it.title == "Secret journal" } }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted “📓 Journal”") }
        assertFalse(shows("Secret journal"))
    }

    @Test
    fun `while locked private maps hide, a deletion as they lock says nothing`() {
        unlocked()
        privateMap("Secret journal")
        home(hiding = true)
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        deleteAsTheyLock("Secret journal")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { app.container.maps.trash.value.any { it.title == "Secret journal" } }
        compose.waitForIdle()
        assertFalse(shows("Deleted"))
        assertFalse(shows("Secret journal"))
    }

    @Test
    fun `unlocked, a deleted private map is named as ever`() {
        unlocked()
        privateMap("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        fromMenu("Secret journal", "Delete")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted “Secret journal”") }
    }

    @Test
    fun `named while they're unlocked, the message goes as they lock`() {
        unlocked()
        privateMap("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        fromMenu("Secret journal", "Delete")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted “Secret journal”") }
        compose.runOnUiThread { privateMaps.lock.screenOff() }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { !shows("Secret journal") }
    }

    @Test
    fun `any other message that names one goes as they lock too`() {
        unlocked()
        map("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }

        fromMenu("Secret journal", "Make private")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("“Secret journal” is private now") }
        compose.runOnUiThread { privateMaps.lock.screenOff() }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { !shows("Secret journal") }
    }
}
