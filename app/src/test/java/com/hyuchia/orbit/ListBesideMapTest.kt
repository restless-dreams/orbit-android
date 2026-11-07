package com.hyuchia.orbit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.width
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.ui.MinimumMapWidth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A tablet (Pixel Tablet's size), each way up. */
private const val TABLET = "w800dp-h1280dp-port-xhdpi"
private const val TABLET_ON_ITS_SIDE = "w1280dp-h800dp-land-xhdpi"

/** A small tablet upright, as a foldable's inner screen can be: on its side it has room for both. */
private const val SMALL_TABLET = "w600dp-h960dp-port-xhdpi"

/** A phone, each way up. */
private const val PHONE = "w411dp-h914dp-port-xxhdpi"
private const val PHONE_ON_ITS_SIDE = "w914dp-h411dp-land-xxhdpi"

/**
 * Home's list and a map in one window, laid out as the app lays them out
 * ([listAndMapDirective] and [rememberListDetailScenes]): side by side on a
 * tablet whichever way up it is ("Phones and tablets, both ways up" in
 * AGENTS.md), and the map alone on a phone, on a small tablet upright, and
 * while the map wants the room.
 */
@RunWith(AndroidJUnit4::class)
class ListBesideMapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val backStack = mutableStateListOf<NavKey>(HomeKey, MapKey("welcome"))

    /** Whether the map wants the window to itself, as a panel beside it, its text or a presentation has it. */
    private var wantsRoom by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    private fun showScreens() {
        compose.setContent {
            val window = LocalWindowInfo.current.containerDpSize
            val directive = listAndMapDirective(currentWindowAdaptiveInfo(), window, wantsRoom)

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategies = listOf(rememberListDetailScenes(homeOnTop = backStack.lastOrNull() == HomeKey, roomForTwo = roomForTwoPanes(window), listFits = listBesideMap(listAndMapDirective(currentWindowAdaptiveInfo(), window, mapWantsRoom = false)), directive = directive)),
                entryProvider = entryProvider {
                    entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text("Pick a map") })) { Text("Your maps", Modifier.fillMaxSize()) }
                    entry<MapKey>(metadata = ListDetailSceneStrategy.detailPane()) { Text("The welcome map", Modifier.fillMaxSize()) }
                },
            )
        }

        compose.waitForIdle()
    }

    /** The list is beside the map, both whole, and the map keeps a phone's width. */
    private fun assertSideBySide() {
        val list = compose.onNodeWithText("Your maps").assertIsDisplayed().getBoundsInRoot()
        val map = compose.onNodeWithText("The welcome map").assertIsDisplayed().getBoundsInRoot()

        assertTrue("The list ($list) is beside the map ($map), before it", list.right <= map.left)
        assertTrue("The map keeps a phone's width beside the list: ${map.width}", map.width >= MinimumMapWidth)
    }

    /** The map has the window to itself: the list has stepped aside. */
    private fun assertMapAlone() {
        compose.onNodeWithText("The welcome map").assertIsDisplayed()
        compose.onNodeWithText("Your maps").assertDoesNotExist()
    }

    @Test @Config(qualifiers = TABLET)
    fun `a tablet upright keeps the list beside the map`() {
        showScreens()

        assertSideBySide()
    }

    @Test @Config(qualifiers = TABLET_ON_ITS_SIDE)
    fun `a tablet on its side keeps the list beside the map`() {
        showScreens()

        assertSideBySide()
    }

    @Test @Config(qualifiers = TABLET)
    fun `a map that wants the room has a tablet upright to itself, and gives the list its place back after`() {
        wantsRoom = true
        showScreens()

        assertMapAlone()

        wantsRoom = false
        compose.waitForIdle()

        assertSideBySide()
    }

    @Test @Config(qualifiers = SMALL_TABLET)
    fun `a small tablet upright has no room for the list beside a phone's width of map, and shows the map alone`() {
        showScreens()

        assertMapAlone()
    }

    @Test @Config(qualifiers = PHONE)
    fun `a phone shows the map alone`() {
        showScreens()

        assertMapAlone()
    }

    @Test @Config(qualifiers = PHONE_ON_ITS_SIDE)
    fun `a phone on its side, too short for both, shows the map alone`() {
        showScreens()

        assertMapAlone()
    }
}
