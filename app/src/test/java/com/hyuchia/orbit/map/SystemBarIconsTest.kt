package com.hyuchia.orbit.map

import android.app.Activity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.ThemeMode
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.ThemedSystemBars
import com.hyuchia.orbit.theme.appIsDark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The status and navigation bars' icons over a map whose style is always
 * light, while the app's theme turns dark under it: the map keeps its own,
 * and leaves the app's as the app is then, not as it was when the map opened.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarIconsTest {
    @get:Rule
    val compose = createComposeRule()

    private var theme by mutableStateOf(ThemeMode.Light)
    private var mapShown by mutableStateOf(true)
    private lateinit var bars: WindowInsetsControllerCompat

    /** The map's bars as MapScreen sets them: in its layout of its own, where the map takes the whole window. */
    @Composable
    private fun MapScreenBars(dark: Boolean) {
        val windowWidth = LocalWindowInfo.current.containerSize.width

        BoxWithConstraints {
            if (constraints.maxWidth >= windowWidth - 1) {
                SystemBarIcons(dark)
            }
        }
    }

    private fun showLightMap() {
        compose.setContent {
            val view = LocalView.current
            bars = WindowCompat.getInsetsController((view.context as Activity).window, view)

            // As MainActivity and MapScreen have them: the app's bars, then the map's, in a layout of its own.
            CompositionLocalProvider(LocalAppSettings provides AppSettings(themeMode = theme)) {
                ThemedSystemBars(appIsDark())

                if (mapShown) {
                    MapScreenBars(dark = false)
                }
            }
        }

        compose.waitForIdle()
    }

    @Test
    fun `a light map keeps dark icons as the app turns dark, and leaves the app's light ones`() {
        showLightMap()
        assertTrue("A light map has dark icons", bars.isAppearanceLightStatusBars)

        compose.runOnIdle { theme = ThemeMode.Dark }
        compose.waitForIdle()

        assertTrue("The map keeps its own icons", bars.isAppearanceLightStatusBars)
        assertTrue(bars.isAppearanceLightNavigationBars)

        compose.runOnIdle { mapShown = false }
        compose.waitForIdle()

        assertFalse("The app, dark now, gets light icons back", bars.isAppearanceLightStatusBars)
        assertFalse(bars.isAppearanceLightNavigationBars)
    }

    @Test
    fun `a dark map opened over a light one keeps its icons as the light one goes, and leaves the app's`() {
        var lightMap by mutableStateOf(true)
        var darkMap by mutableStateOf(false)

        compose.setContent {
            val view = LocalView.current
            bars = WindowCompat.getInsetsController((view.context as Activity).window, view)

            CompositionLocalProvider(LocalAppSettings provides AppSettings(themeMode = ThemeMode.Light)) {
                ThemedSystemBars(appIsDark())

                if (lightMap) {
                    MapScreenBars(dark = false)
                }

                if (darkMap) {
                    MapScreenBars(dark = true)
                }
            }
        }

        compose.waitForIdle()
        assertTrue("A light map has dark icons", bars.isAppearanceLightStatusBars)

        // A widget opens the dark map over the light one, which goes once the dark one is shown.
        compose.runOnIdle { darkMap = true }
        compose.waitForIdle()
        assertFalse("The dark map has light icons", bars.isAppearanceLightStatusBars)

        compose.runOnIdle { lightMap = false }
        compose.waitForIdle()
        assertFalse("The map under it going leaves the dark map's", bars.isAppearanceLightStatusBars)
        assertFalse(bars.isAppearanceLightNavigationBars)

        compose.runOnIdle { darkMap = false }
        compose.waitForIdle()
        assertTrue("The app, light, gets dark icons back", bars.isAppearanceLightStatusBars)
    }
}
