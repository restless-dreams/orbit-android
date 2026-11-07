package com.hyuchia.orbit.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Home's order sorts the pinned maps as well as the others, so it can be
 * changed wherever they are. Home reaches the app's container, so this starts
 * OrbitApplication, as ScreensTest does; on a screen tall enough for every
 * heading, as Home's grid makes only what shows.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h2400dp-port-mdpi")
class HomeSortTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<OrbitApplication>().container

    private fun showHome() {
        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false)) {
                OrbitTheme(dark = false) {
                    HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {})
                }
            }
        }

        // Home reads the maps off the main thread: the pinned ones' heading comes once it has.
        compose.waitUntil(timeoutMillis = 20_000) { compose.onAllNodesWithText("Pinned").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun sortButtons(): Int = compose.onAllNodesWithContentDescription("Sort: Last edited").fetchSemanticsNodes().size

    @Test
    fun `the order can be changed when every map shown is pinned`() {
        runBlocking {
            container.maps.load()
            container.maps.maps.value.orEmpty().forEach { container.maps.setPinned(it.id, true) }
        }

        showHome()

        assertEquals(1, sortButtons())
    }

    @Test
    fun `with maps that aren't pinned, the order is beside them alone`() {
        runBlocking {
            container.maps.load()
            container.maps.add("# Trip\n")
        }

        showHome()

        assertEquals(1, sortButtons())
    }
}
