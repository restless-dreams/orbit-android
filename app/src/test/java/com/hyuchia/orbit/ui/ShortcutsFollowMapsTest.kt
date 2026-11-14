package com.hyuchia.orbit.ui

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers

/** Orbit keeps the launcher's shortcuts in line with its maps, whatever changed them: here, the folder maps are kept in, and the trash. */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class)
class ShortcutsFollowMapsTest {
    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()
    private val manager: ShortcutManager get() = app.getSystemService(ShortcutManager::class.java)

    /** Robolectric keeps a pinned shortcut that was turned off apart from the others, and doesn't mark it. */
    private fun turnedOff(): Map<String, ShortcutInfo> = ReflectionHelpers.getStaticField(ShadowShortcutManager::class.java, "disabledPinnedShortcuts")

    /** Waits for `condition`, which Orbit brings about in the background as the maps change. */
    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 30_000

        while (!condition()) {
            assertTrue("Timed out", System.currentTimeMillis() < until)
            Thread.sleep(20)
        }
    }

    @Test
    fun `a map the folder deleted turns its pinned shortcut off, and restored from the trash, back on`() = runBlocking {
        val maps = app.container.maps
        maps.load()
        val id = maps.create("Trip to Lisbon")
        offerShortcut(app, id, "Trip to Lisbon")
        assertTrue(manager.requestPinShortcut(manager.dynamicShortcuts.single(), null))

        // Its file was deleted in the folder maps are kept in: the folder's sync moves the map to the trash.
        val trashed = maps.delete(id, byFolder = true)
        assertNotNull(trashed)
        waitFor { turnedOff().keys == setOf("map:$id") }
        assertTrue(manager.dynamicShortcuts.isEmpty())

        // Restored from the trash, before anyone opens it.
        assertEquals(id, maps.restore(trashed!!))
        waitFor { turnedOff().isEmpty() }
        assertEquals("Trip to Lisbon", manager.pinnedShortcuts.single().shortLabel)
    }
}
