package com.hyuchia.orbit.home

import android.app.KeyguardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
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
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** How long a test waits for work Orbit does off the main thread, such as deleting a map: CI runs beside other builds. */
private const val SLOW_WORK = 30_000L

/** How long a test waits for a message that mustn't come: one that does comes within moments. */
private const val SAID_AT_ONCE = 3_000L

/**
 * What Home says when a map can't go to the trash, or its Undo can't bring
 * it back: that it's where it was, named as private maps are as it shows.
 */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), so the maps' cards are on screen.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class HomeDeletionFailuresTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()
    private val privateMaps get() = app.container.privateMaps

    /** Files the test made unreadable, readable again once it's done. */
    private val unreadable = mutableListOf<File>()

    @After
    fun readable() {
        unreadable.forEach { it.setReadable(true) }
    }

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

    private fun home() {
        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false, storageNoteDismissed = true)) {
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

    /** Makes the details in `folder` ones Orbit can't read now, so the map can't move to the trash, nor come back from it. */
    private fun detailsUnreadable(folder: File) {
        val details = File(folder, "meta.json")
        details.setReadable(false)
        unreadable += details
        assumeFalse(details.canRead())
    }

    @Test
    fun `a map that can't go to the trash says so, and stays on Home`() {
        val id = map("Groceries")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Groceries") }
        detailsUnreadable(app.container.maps.folder(id))

        fromMenu("Groceries", "Delete")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Orbit couldn’t delete “Groceries”. It’s still in your maps.") }
        assertTrue(app.container.maps.exists(id))
        assertTrue(app.container.maps.trash.value.isEmpty())
        assertFalse(shows("Deleted “Groceries”"))
    }

    @Test
    fun `an Undo that can't bring a map back says it's still in the trash`() {
        val id = map("Groceries")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Groceries") }

        fromMenu("Groceries", "Delete")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted “Groceries”") }
        detailsUnreadable(app.container.maps.trash.value.single { it.id == id }.folder)
        compose.onNodeWithText("Undo").performClick()

        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Orbit couldn’t restore “Groceries”. It’s still in the trash.") }
        assertFalse(app.container.maps.exists(id))
        assertTrue(app.container.maps.trash.value.any { it.id == id })
    }

    @Test
    fun `an Undo for a map that came back another way meanwhile says nothing of the trash`() {
        val id = map("Groceries")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Groceries") }

        fromMenu("Groceries", "Delete")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Deleted “Groceries”") }
        // Restored beside the list, as a tablet's map pane offers it, before Undo is tapped.
        runBlocking { app.container.maps.restore(app.container.maps.trash.value.single { it.id == id }) }
        compose.onNodeWithText("Undo").performClick()

        assertThrows(ComposeTimeoutException::class.java) {
            compose.waitUntil(timeoutMillis = SAID_AT_ONCE) { shows("Orbit couldn’t restore") }
        }

        assertTrue(app.container.maps.exists(id))
    }

    @Test
    fun `a private map that can't go to the trash is named while they're unlocked, and the message goes as they lock`() {
        unlocked()
        val id = privateMap("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }
        detailsUnreadable(app.container.maps.folder(id))

        fromMenu("Secret journal", "Delete")
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Orbit couldn’t delete “Secret journal”. It’s still in your maps.") }
        compose.runOnUiThread { privateMaps.lock.screenOff() }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { !shows("Orbit couldn’t delete") }
        assertTrue(app.container.maps.exists(id))
    }

    @Test
    fun `a private map that can't go to the trash as they lock is named by its cover`() {
        unlocked()
        val id = privateMap("Secret journal", cover = LockedCover(icon = "📓", name = "Journal"))
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }
        detailsUnreadable(app.container.maps.folder(id))

        // The screen turns off right after Delete, before Home hears the map couldn't go.
        fromMenu("Secret journal", "Delete") { privateMaps.lock.screenOff() }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Orbit couldn’t delete “📓 Journal”. It’s still in your maps.") }
        assertFalse(shows("Secret journal"))
        assertTrue(app.container.maps.exists(id))
    }

    @Test
    fun `a private map without a cover that can't go to the trash as they lock isn't named`() {
        unlocked()
        val id = privateMap("Secret journal")
        home()
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Secret journal") }
        detailsUnreadable(app.container.maps.folder(id))

        fromMenu("Secret journal", "Delete") { privateMaps.lock.screenOff() }
        compose.waitUntil(timeoutMillis = SLOW_WORK) { shows("Orbit couldn’t delete a private map. It’s still in your maps.") }
        assertFalse(shows("Secret journal"))
        assertTrue(app.container.maps.exists(id))
    }
}
