package com.hyuchia.orbit

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.map.BesideListBackHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Back on a tablet, with Home's list beside a map, as the app's screens are
 * laid out ([listAndMapDirective], [rememberListDetailScenes]) and the map's screen takes Back
 * ([BesideListBackHandler] first, then the map's own, as `MapScreen` and
 * `MapBackHandlers` add them): inside the map, Back flies up it, also after a
 * panel beside the map came and went; at its top, Back closes the map and
 * the list stays; and only Back from the list alone leaves Orbit, as on a
 * phone.
 */
@RunWith(AndroidJUnit4::class)
// A tablet on its side, 2560 by 1600 pixels, with room for the list beside the map.
@Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
class ListDetailBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val backStack = mutableStateListOf<NavKey>(HomeKey, MapKey("welcome"))

    /** Whether the map is in an idea, so its own Back flies up a level. */
    private var inAnIdea by mutableStateOf(false)

    /** Whether the map wants the window to itself, as a panel beside it has it. */
    private var wantsRoom by mutableStateOf(false)

    private var flewUp = 0

    /** How many times the map's page, a view as its WebView is, left the window: moved to another scene, as it was, it draws blank. */
    private var pageDetached = 0

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    private fun showScreens() {
        compose.setContent {
            val window = LocalWindowInfo.current.containerDpSize
            // As the app does, a map that wants room has the window to itself.
            val directive = listAndMapDirective(currentWindowAdaptiveInfo(), window, wantsRoom)

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategies = listOf(rememberListDetailScenes(homeOnTop = backStack.lastOrNull() == HomeKey, roomForTwo = roomForTwoPanes(window), listFits = listBesideMap(listAndMapDirective(currentWindowAdaptiveInfo(), window, mapWantsRoom = false)), directive = directive)),
                entryProvider = entryProvider {
                    entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text("Pick a map") })) { Text("Home") }

                    entry<MapKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                        BesideListBackHandler(listBesideMap(directive), remember { Animatable(1f) }, onClose = { backStack.remove(key) })
                        BackHandler(enabled = inAnIdea) { flewUp += 1 }
                        Text("The welcome map")
                        AndroidView(
                            factory = { context ->
                                View(context).apply {
                                    addOnAttachStateChangeListener(
                                        object : View.OnAttachStateChangeListener {
                                            override fun onViewAttachedToWindow(view: View) = Unit

                                            override fun onViewDetachedFromWindow(view: View) {
                                                pageDetached += 1
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }

        compose.waitForIdle()
    }

    /** Android's Back, as the system's button or gesture gives it to the app. */
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back at the top of the map beside the list closes the map, and Orbit stays with the list`() {
        showScreens()
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("The welcome map").assertExists()

        back()

        assertEquals(listOf<NavKey>(HomeKey), backStack.toList())
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Pick a map").assertExists()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun `upright too, Back at the top of the map beside the list closes the map, and Orbit stays with the list`() {
        showScreens()
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("The welcome map").assertExists()

        back()

        assertEquals(listOf<NavKey>(HomeKey), backStack.toList())
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Pick a map").assertExists()
    }

    @Test
    fun `Back inside the map flies up it, and the map stays`() {
        inAnIdea = true
        showScreens()

        back()

        assertEquals(1, flewUp)
        assertEquals(listOf<NavKey>(HomeKey, MapKey("welcome")), backStack.toList())
    }

    @Test
    fun `the map takes the whole window and gives the list its room back without its page leaving the window`() {
        showScreens()

        // A sketch, or a panel beside the map, has the list step aside, and the map's page stays where it is.
        wantsRoom = true
        compose.waitForIdle()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("The welcome map").assertExists()
        wantsRoom = false
        compose.waitForIdle()

        compose.onNodeWithText("Home").assertExists()
        assertEquals(0, pageDetached)
    }

    @Test
    fun `while the map has the window to itself, Back goes to what's over it, and the map stays`() {
        inAnIdea = true
        showScreens()
        wantsRoom = true
        compose.waitForIdle()

        back()

        assertEquals(1, flewUp)
        assertEquals(listOf<NavKey>(HomeKey, MapKey("welcome")), backStack.toList())
    }

    @Test
    fun `after a panel beside the map closes, Back still flies up the map`() {
        inAnIdea = true
        showScreens()

        // A panel opens beside the map, and the list steps aside; Back closes it (the map's own), and the list comes back.
        wantsRoom = true
        compose.waitForIdle()
        wantsRoom = false
        compose.waitForIdle()

        back()

        assertEquals(1, flewUp)
        assertEquals(listOf<NavKey>(HomeKey, MapKey("welcome")), backStack.toList())

        // At the top of the map, the next Back closes it.
        inAnIdea = false
        compose.waitForIdle()
        back()

        assertEquals(listOf<NavKey>(HomeKey), backStack.toList())
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun `Back from the list alone leaves Orbit`() {
        backStack.removeAt(backStack.lastIndex)
        showScreens()
        compose.onNodeWithText("Pick a map").assertExists()

        back()

        assertTrue(compose.activity.isFinishing)
    }
}
