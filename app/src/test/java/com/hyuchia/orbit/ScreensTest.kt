package com.hyuchia.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.home.HomeScreen
import com.hyuchia.orbit.settings.SettingsScreen
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A phone (Pixel 8's width and height in dp), each way up, at mdpi so its pictures stay small. */
private const val PHONE = "w411dp-h891dp-port-mdpi"
private const val PHONE_ON_ITS_SIDE = "w891dp-h411dp-land-mdpi"

/** A tablet (Pixel Tablet's), each way up. */
private const val TABLET = "w800dp-h1280dp-port-mdpi"
private const val TABLET_ON_ITS_SIDE = "w1280dp-h800dp-land-mdpi"

/** The phone in dark, and in Spanish and Brazilian Portuguese, whose words run longer. */
private const val PHONE_DARK = "w411dp-h891dp-port-night-mdpi"
private const val PHONE_SPANISH = "es-w411dp-h891dp-port-mdpi"
private const val PHONE_PORTUGUESE = "pt-rBR-w411dp-h891dp-port-mdpi"

/**
 * Every pixel has to match its picture, to within a little color: anti-aliased
 * edges can come out a few shades lighter or darker on another machine. A pixel
 * may be up to 0.1 away (black to white is 1.73), which a changed letter or a
 * flipped switch goes far beyond.
 */
private val Matching = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.1f),
        resultValidator = ThresholdValidator(0f),
    ),
)

/**
 * Home and Settings drawn on a phone and a tablet, each both ways up, and on
 * the phone in dark, in Spanish and in Brazilian Portuguese ("Phones and
 * tablets, both ways up" in AGENTS.md), and compared with their pictures in
 * src/test/screenshots/. The maps' own pictures, which WebViews draw, show
 * their placeholder here.
 * Both screens reach the app's container, so these start OrbitApplication
 * rather than robolectric.properties' plain Application.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = OrbitApplication::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test @Config(qualifiers = PHONE)
    fun homeOnAPhone() = home("phone")

    @Test @Config(qualifiers = PHONE_ON_ITS_SIDE)
    fun homeOnAPhoneOnItsSide() = home("phone-on-its-side")

    @Test @Config(qualifiers = TABLET)
    fun homeOnATablet() = home("tablet")

    @Test @Config(qualifiers = TABLET_ON_ITS_SIDE)
    fun homeOnATabletOnItsSide() = home("tablet-on-its-side")

    @Test @Config(qualifiers = PHONE_DARK)
    fun homeInDark() = home("phone-dark")

    @Test @Config(qualifiers = PHONE_SPANISH)
    fun homeInSpanish() = home("phone-spanish")

    @Test @Config(qualifiers = PHONE_PORTUGUESE)
    fun homeInPortuguese() = home("phone-portuguese")

    @Test @Config(qualifiers = PHONE)
    fun settingsOnAPhone() = settings("phone")

    @Test @Config(qualifiers = PHONE_ON_ITS_SIDE)
    fun settingsOnAPhoneOnItsSide() = settings("phone-on-its-side")

    @Test @Config(qualifiers = TABLET)
    fun settingsOnATablet() = settings("tablet")

    @Test @Config(qualifiers = TABLET_ON_ITS_SIDE)
    fun settingsOnATabletOnItsSide() = settings("tablet-on-its-side")

    @Test @Config(qualifiers = PHONE_DARK)
    fun settingsInDark() = settings("phone-dark")

    @Test @Config(qualifiers = PHONE_SPANISH)
    fun settingsInSpanish() = settings("phone-spanish")

    @Test @Config(qualifiers = PHONE_PORTUGUESE)
    fun settingsInPortuguese() = settings("phone-portuguese")

    private fun home(shape: String) {
        // As Orbit starts: the maps are read, and the welcome map written the first time.
        runBlocking { ApplicationProvider.getApplicationContext<OrbitApplication>().container.maps.load() }
        draw { HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {}) }
        // Home reads them off the main thread: the welcome map's card comes once it has.
        compose.waitUntil(timeoutMillis = 20_000) { compose.onAllNodesWithText("Orbit", substring = true).fetchSemanticsNodes().size > 1 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/home-$shape.png", roborazziOptions = Matching)
    }

    private fun settings(shape: String) {
        draw { SettingsScreen(onAbout = {}, onTrash = {}, onBack = {}) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings-$shape.png", roborazziOptions = Matching)
    }

    /** Draws `screen` alone, in Orbit's own colors rather than the wallpaper's, dark where the device is. */
    private fun draw(screen: @Composable () -> Unit) {
        // The device's qualifiers say "notnight" in light: only "night" itself is dark.
        val dark = "night" in RuntimeEnvironment.getQualifiers().split("-")

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false)) {
                OrbitTheme(dark = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { screen() }
                }
            }
        }
    }
}
