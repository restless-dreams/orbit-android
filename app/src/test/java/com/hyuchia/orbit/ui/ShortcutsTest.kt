package com.hyuchia.orbit.ui

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers

/**
 * The launcher's shortcuts to maps follow the maps, whatever changed them: renamed, deleted, restored, made private.
 * Trip's shortcut is pinned to the home screen, and Garden's among the launcher icon's (Robolectric keeps a shortcut
 * one or the other, where Android lets it be both).
 */
@RunWith(AndroidJUnit4::class)
class ShortcutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: ShortcutManager get() = context.getSystemService(ShortcutManager::class.java)

    private fun map(id: String, title: String, private: Boolean = false, cover: LockedCover? = null) =
        MapSummary(id, MapOutline(title, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private, cover = cover), "", "")

    /** Brings the shortcuts in line with `maps`, as Orbit does whenever they change. */
    private fun follow(vararg maps: MapSummary) = matchShortcuts(context, shortcutLabels(context.resources, maps.toList()))

    /** Robolectric keeps a pinned shortcut that was turned off apart from the others, and doesn't mark it. */
    private fun turnedOff(): Map<String, ShortcutInfo> = ReflectionHelpers.getStaticField(ShadowShortcutManager::class.java, "disabledPinnedShortcuts")

    private fun pinned(): String = manager.pinnedShortcuts.single { it.id == "map:trip" }.shortLabel.toString()

    private fun launcherIcons(): String = manager.dynamicShortcuts.single { it.id == "map:garden" }.shortLabel.toString()

    @Before
    fun opened() {
        // Trip was opened, and its shortcut pinned to the home screen; Garden was opened, so it's among the launcher icon's.
        offerShortcut(context, "trip", "Trip")
        assertTrue(manager.requestPinShortcut(manager.dynamicShortcuts.single { it.id == "map:trip" }, null))
        offerShortcut(context, "garden", "Garden")
        assertEquals("Trip", pinned())
        assertEquals("Garden", launcherIcons())
    }

    @Test
    fun `a map opened again after it was deleted and restored turns its pinned shortcut back on`() {
        withdrawShortcut(context, "trip")
        assertEquals(setOf("map:trip"), turnedOff().keys)

        offerShortcut(context, "trip", "Trip again")

        assertTrue(turnedOff().isEmpty())
        assertEquals("Trip again", pinned())
    }

    @Test
    fun `a map renamed on Home renames its shortcuts`() {
        follow(map("trip", "Lisbon in May"), map("garden", "Garden by the river"), map("talk", "Talk"))

        assertEquals("Lisbon in May", pinned())
        assertEquals("Garden by the river", launcherIcons())
        // Only the maps opened are among the launcher icon's shortcuts.
        assertTrue(manager.dynamicShortcuts.none { it.id == "map:talk" })
    }

    @Test
    fun `a map gone from the folder maps are kept in leaves the shortcuts, and its pinned one says it was deleted`() {
        follow(map("talk", "Talk"))

        assertTrue(manager.dynamicShortcuts.none { it.id == "map:garden" })
        assertEquals(setOf("map:trip"), turnedOff().keys)
    }

    @Test
    fun `a map restored from the trash, before it's opened, turns its pinned shortcut back on, named as it is`() {
        follow(map("garden", "Garden"))
        assertEquals(setOf("map:trip"), turnedOff().keys)

        follow(map("trip", "Trip, restored"), map("garden", "Garden"))

        assertTrue(turnedOff().isEmpty())
        assertEquals("Trip, restored", pinned())
    }

    @Test
    fun `a private map's shortcuts say only its cover's name, however it became private, as the cover changes`() {
        // Made private some other way than its switch, which takes it out of the launcher icon's: its details unreadable, say.
        follow(map("trip", "Trip", private = true), map("garden", "Garden", private = true))
        assertEquals("Private map", pinned())
        assertEquals("Private map", launcherIcons())

        val cover = LockedCover(icon = "🧳", name = "Plans")
        follow(map("trip", "Trip", private = true, cover = cover), map("garden", "Garden", private = true))
        assertEquals("🧳 Plans", pinned())

        // Deleted and restored while private, it's back on, and still says nothing of the map.
        follow()
        follow(map("trip", "Trip", private = true, cover = cover))
        assertTrue(turnedOff().isEmpty())
        assertEquals("🧳 Plans", pinned())
    }

    @Test
    fun `a new title that starts as the old one did still renames the long label the launcher shows`() {
        follow(map("trip", "Trip to Lisbon in the spring"), map("garden", "Garden"))
        assertEquals("Trip to Lisbon in the sp", pinned())

        follow(map("trip", "Trip to Lisbon in the springtime"), map("garden", "Garden"))

        assertEquals("Trip to Lisbon in the springtime", manager.pinnedShortcuts.single { it.id == "map:trip" }.longLabel.toString())
    }

    @Test
    fun `what the launcher refused in the background goes again as Orbit comes back on screen`() = runTest {
        var refusing = true

        val matching = ShortcutMatching { labels ->
            matchShortcuts(context, labels) { context, renamed -> !refusing && ShortcutManagerCompat.updateShortcuts(context, renamed) }
        }

        val labels = MutableStateFlow(shortcutLabels(context.resources, listOf(map("trip", "Trip"), map("garden", "Garden"))))
        val following = launch { matching.follow(labels) }
        runCurrent()

        // Made private on another device, its sync landing while Orbit is in the background: Android refuses the update.
        labels.value = shortcutLabels(context.resources, listOf(map("trip", "Trip", private = true), map("garden", "Garden")))
        runCurrent()
        assertEquals("Trip", pinned())

        // Back on screen, it goes again, and is taken; once taken, coming back sends nothing.
        refusing = false
        matching.cameBack()
        assertEquals("Private map", pinned())
        refusing = true
        matching.cameBack()
        assertEquals("Private map", pinned())
        following.cancel()
    }

    @Test
    fun `an update that throws, over the few Android allows, isn't taken`() {
        val taken = matchShortcuts(context, shortcutLabels(context.resources, listOf(map("trip", "Lisbon"), map("garden", "Garden")))) { _, _ ->
            throw IllegalStateException("Calling package is rate-limited")
        }

        assertEquals(false, taken)
        assertEquals(true, follow(map("trip", "Lisbon"), map("garden", "Garden")))
    }
}
