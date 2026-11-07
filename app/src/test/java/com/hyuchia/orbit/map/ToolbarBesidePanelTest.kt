package com.hyuchia.orbit.map

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.HomeKey
import com.hyuchia.orbit.MapKey
import com.hyuchia.orbit.listAndMapDirective
import com.hyuchia.orbit.listBesideMap
import com.hyuchia.orbit.rememberListDetailScenes
import com.hyuchia.orbit.roomForTwoPanes
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The map's toolbar without a +, as a map that can only be looked at has it,
 * while a panel opens beside the map on a tablet: the map's pane takes the
 * list's room, moving in a lookahead pass, as the app lays the panes out.
 * Material's own toolbar without a + reads its alignment lines as it measures
 * there, and throws, closing Orbit.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class ToolbarBesidePanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val backStack = mutableStateListOf<NavKey>(HomeKey, MapKey("moving-house"))

    /** Whether the map wants the window to itself, as a panel opening beside it has it. */
    private var wantsRoom by mutableStateOf(false)

    private val buttons = @Composable {
        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Outline") }
        IconButton(onClick = {}) { Icon(Icons.Outlined.Description, contentDescription = "What Kitchen holds") }
    }

    /** The list and a map with `toolbar` at its foot, then a panel opening beside the map: the panes move. */
    private fun panelOpensBeside(toolbar: @Composable () -> Unit) {
        compose.setContent {
            val window = LocalWindowInfo.current.containerDpSize
            val directive = listAndMapDirective(currentWindowAdaptiveInfo(), window, wantsRoom)

            OrbitTheme(dark = false) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(rememberListDetailScenes(homeOnTop = false, roomForTwo = roomForTwoPanes(window), listFits = listBesideMap(listAndMapDirective(currentWindowAdaptiveInfo(), window, mapWantsRoom = false)), directive = directive)),
                    entryProvider = entryProvider {
                        entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text("Pick a map") })) { Text("Your maps", Modifier.fillMaxSize()) }

                        entry<MapKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { toolbar() }
                        }
                    },
                )
            }
        }

        compose.waitForIdle()
        wantsRoom = true
        compose.waitForIdle()
        wantsRoom = false
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `the toolbar without a + measures as a panel opens beside the map, on a tablet on its side`() {
        panelOpensBeside { ToolbarWithoutAdd(vertical = false, buttons) }

        compose.onNodeWithContentDescription("What Kitchen holds").assertExists()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun `the toolbar without a + measures as a panel opens beside the map, on a tablet upright`() {
        panelOpensBeside { ToolbarWithoutAdd(vertical = false, buttons) }

        compose.onNodeWithContentDescription("What Kitchen holds").assertExists()
    }
}
