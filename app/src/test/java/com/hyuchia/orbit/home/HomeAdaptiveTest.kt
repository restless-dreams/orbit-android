package com.hyuchia.orbit.home

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A phone's window upright, and on its side. */
private val Upright = DpSize(411.dp, 891.dp)
private val OnItsSide = DpSize(891.dp, 411.dp)

/**
 * Home as a phone turns, and in a window beside another app's: its search
 * stays in view after turning, and the slim bar of a phone on its side keeps
 * its title and its search whole, beside each other where there's room, in
 * each language and in larger words. Home reaches the app's container, so
 * this starts OrbitApplication, and draws its words as Android does, as
 * ScreensTest does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class HomeAdaptiveTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<OrbitApplication>().container
    private val context get() = ApplicationProvider.getApplicationContext<OrbitApplication>()

    /** Home, among enough maps to scroll, its words at `fontScale` times their size, in `window` while it's given one. */
    private fun showHome(fontScale: Float = 1f, window: () -> DpSize? = { null }) {
        runBlocking {
            container.maps.load()
            repeat(8) { container.maps.add("# Trip $it\n") }
        }

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false), LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OrbitTheme(dark = false) {
                    Sized(window()) {
                        HomeScreen(onOpen = {}, onPresent = {}, onStudy = {}, onNewMap = {}, onImported = {}, onSettings = {}, onTrash = {}, onSend = {})
                    }
                }
            }
        }

        // Home reads the maps off the main thread: the bar counts them once it has, in the language it speaks.
        compose.waitUntil(timeoutMillis = 30_000) { shows(count()) }
        compose.waitForIdle()
    }

    /** What the bar says of the maps there are: "10 maps". */
    private fun count(): String {
        val maps = container.maps.maps.value.orEmpty().size
        return context.resources.getQuantityString(R.plurals.home_map_count, maps, maps)
    }

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Composable
    private fun Sized(size: DpSize?, content: @Composable () -> Unit) {
        if (size == null) {
            content()
        } else {
            Box(Modifier.requiredSize(size)) { content() }
        }
    }

    private val search get() = compose.onNode(hasContentDescription(context.getString(R.string.common_search_your_maps)) and hasSetTextAction())

    private fun boundsOf(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    @Test
    fun `a phone turned on its side and back shows Home's search again, as it was`() {
        var window by mutableStateOf(Upright)
        showHome { window }
        val count = count()

        search.assertIsDisplayed()

        // On its side, the search is in the slim bar.
        window = OnItsSide
        compose.waitForIdle()
        search.assertIsDisplayed()

        // Upright again, it's back above the maps, below the bar's title and count, rather than just above the first card.
        window = Upright
        compose.waitForIdle()
        search.assertIsDisplayed()
        val shown = search.fetchSemanticsNode().boundsInRoot
        assertTrue("The search at $shown is under the bar, above the count at ${boundsOf(count)}", shown.top >= boundsOf(count).bottom)
    }

    /** The slim bar's title, and the search, whole in the window and apart, in the bar or above the maps. */
    private fun titleAndSearchWhole(searchInBar: Boolean?) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onAllNodesWithText(context.getString(R.string.app_name)).fetchSemanticsNodes().first().boundsInRoot
        search.assertIsDisplayed()
        val field = search.fetchSemanticsNode().boundsInRoot

        assertTrue("The title is squeezed to ${title.width} px", title.width >= 24f)

        for ((name, bounds) in listOf("title" to title, "search" to field)) {
            assertTrue("The $name at $bounds is outside the window, $window", bounds.left >= window.left && bounds.right <= window.right)
        }

        assertTrue("The title at $title and the search at $field overlap", !title.overlaps(field))

        // In the bar, the search is level with the title; above the maps, it's under it.
        when (searchInBar) {
            true -> assertTrue("The search at $field isn't in the bar beside the title at $title", field.top < title.bottom)
            false -> assertTrue("The search at $field isn't above the maps, under the title at $title", field.top >= title.bottom)
            null -> Unit
        }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-mdpi")
    fun `on a phone on its side, the bar holds the title and the search`() {
        showHome()
        titleAndSearchWhole(searchInBar = true)
    }

    @Test
    @Config(qualifiers = "w445dp-h411dp-land-mdpi")
    fun `on its side beside another app's, the bar keeps its title and its search`() {
        showHome()
        titleAndSearchWhole(searchInBar = null)
    }

    @Test
    @Config(qualifiers = "es-w445dp-h411dp-land-mdpi")
    fun `beside another app's in Spanish, the bar keeps its title and its search`() {
        showHome()
        titleAndSearchWhole(searchInBar = null)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w445dp-h411dp-land-mdpi")
    fun `beside another app's in Portuguese, the bar keeps its title and its search`() {
        showHome()
        titleAndSearchWhole(searchInBar = null)
    }

    @Test
    @Config(qualifiers = "w445dp-h411dp-land-mdpi")
    fun `beside another app's in larger words, the search goes above the maps`() {
        showHome(fontScale = 1.3f)
        titleAndSearchWhole(searchInBar = false)
    }

    @Test
    fun `the bar's search is as wide as the window leaves it, and stays out of one too narrow for its words`() {
        // "Search your maps" is about 136 dp wide at the font's own size, and about 177 dp at 1.3 times it.
        assertEquals(360.dp, barSearchWidth(891.dp, 1f, 136.dp))
        assertEquals(213.dp, barSearchWidth(445.dp, 1f, 136.dp))
        assertNull(barSearchWidth(400.dp, 1f, 136.dp))
        // Longer words, as another language's, need more.
        assertNull(barSearchWidth(445.dp, 1f, 160.dp))
        // In larger words, the title takes more, and the search's words need more too.
        assertNull(barSearchWidth(445.dp, 1.3f, 177.dp))
        assertEquals(360.dp, barSearchWidth(891.dp, 1.3f, 177.dp))
    }

    @Test
    fun `the keyboard coming up doesn't compose again what reads Home's sides`() {
        // Home reads its sides while composing its body: read with the keyboard's, it composed all of it again with
        // each frame of the keyboard coming up as its search was tapped.
        var view: View? = null
        var compositions = 0

        compose.setContent {
            view = LocalView.current
            homeSides().calculateLeftPadding(LayoutDirection.Ltr)
            SideEffect { compositions += 1 }
        }

        fun keyboard(height: Int) {
            compose.runOnUiThread {
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                    .setVisible(WindowInsetsCompat.Type.ime(), height > 0)
                    .build()

                ViewCompat.dispatchApplyWindowInsets(view!!, insets)
            }

            compose.waitForIdle()
        }

        keyboard(0)
        val before = compositions

        for (height in listOf(120, 360, 720, 0)) {
            keyboard(height)
        }

        assertEquals(before, compositions)
    }
}
