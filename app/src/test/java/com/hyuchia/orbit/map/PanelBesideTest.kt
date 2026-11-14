package com.hyuchia.orbit.map

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The search's field beside the map, with the keyboard up for it: on a phone
 * on its side the keyboard takes most of the height, and the room it leaves
 * under the top bar is less than the panel's title takes alone, so the
 * field lay whole under the keyboard. The panel rises over the top bar for
 * it, as far as it needs, and settles back as the keyboard goes.
 */
@RunWith(AndroidJUnit4::class)
class PanelBesideTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Whether the search is the panel beside the map, rather than the outline. */
    private var searching by mutableStateOf(true)

    /** Whether a panel is beside the map, rather than the source editor or nothing. */
    private var beside by mutableStateOf(true)

    /** Whether the panel has risen over the top bar, as the map's screen hears it: it draws the map's row over the top bar then. */
    private var risen by mutableStateOf(false)

    /** The map's screen in short: a top bar `topBar` dp tall under the status bar, and the search's panel beside the map under it. */
    private fun searchBeside(statusBar: Int, topBar: Int) {
        compose.setContent {
            OrbitTheme(dark = false) {
                Box(Modifier.fillMaxSize()) {
                    Text("The top bar", Modifier.fillMaxWidth().padding(top = statusBar.dp).height(topBar.dp))

                    Row(Modifier.fillMaxSize().padding(top = (statusBar + topBar).dp).zIndex(if (risen) 1f else 0f)) {
                        Box(Modifier.weight(1f))

                        if (beside) {
                            PanelBeside(topBar = topBar.dp, onRise = { risen = it }) {
                                if (searching) {
                                    PanelFrame(title = "Search the map", sheet = false, onDismiss = {}) {
                                        var query by remember { mutableStateOf("") }
                                        TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).keptAboveKeyboard(LocalKeptField.current))
                                    }
                                } else {
                                    PanelFrame(title = "Outline", sheet = false, onDismiss = {}) { Text("Trip to Japan") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Android tells the window it has a status bar `statusBar` dp tall, and a keyboard `keyboard` dp tall (none for 0). */
    private fun insets(statusBar: Int, keyboard: Int) {
        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val density = content.resources.displayMetrics.density

            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusBar * density).toInt(), 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboard * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0)
                .build()

            ViewCompat.dispatchApplyWindowInsets(content, insets)
        }

        compose.waitForIdle()
    }

    private val density get() = compose.activity.resources.displayMetrics.density
    private val field: Rect get() = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
    private val title: Rect get() = compose.onNode(hasText("Search the map")).fetchSemanticsNode().boundsInRoot

    /** Whether the field lies whole between the status bar and the keyboard. */
    private fun fieldAboveTheKeyboard(statusBar: Int, keyboard: Int) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val keyboardTop = window.bottom - keyboard * density
        assertTrue("field at $field, the room is from ${statusBar * density} to $keyboardTop", field.top >= statusBar * density && field.bottom <= keyboardTop)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `on a phone on its side the panel rises over the top bar for its field, and settles back as the keyboard goes`() {
        searchBeside(statusBar = 24, topBar = 68)
        insets(statusBar = 24, keyboard = 0)
        // Under the top bar, before anything's typed.
        val settled = field
        assertTrue("title at $title", title.top >= 92 * density)

        compose.onNode(hasSetTextAction()).performClick()
        // Gboard on a Pixel on its side: 261 dp of 411, which leaves 58 dp under the top bar.
        insets(statusBar = 24, keyboard = 261)
        fieldAboveTheKeyboard(statusBar = 24, keyboard = 261)
        // Its title shows too, under the status bar: the panel rose no further than the field needs.
        assertTrue("title at $title", title.top >= 24 * density)

        insets(statusBar = 24, keyboard = 0)
        assertEquals(settled, field)
    }

    @Test
    @Config(qualifiers = "es-w891dp-h411dp-land")
    fun `in Spanish too`() {
        searchBeside(statusBar = 24, topBar = 68)
        insets(statusBar = 24, keyboard = 0)
        compose.onNode(hasSetTextAction()).performClick()
        insets(statusBar = 24, keyboard = 261)
        fieldAboveTheKeyboard(statusBar = 24, keyboard = 261)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `on a smaller phone on its side it rises under the status bar, as far as the field needs`() {
        searchBeside(statusBar = 24, topBar = 64)
        insets(statusBar = 24, keyboard = 0)
        compose.onNode(hasSetTextAction()).performClick()
        insets(statusBar = 24, keyboard = 220)
        fieldAboveTheKeyboard(statusBar = 24, keyboard = 220)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `on a tablet the keyboard leaves it room, and it stays where it is`() {
        searchBeside(statusBar = 24, topBar = 64)
        insets(statusBar = 24, keyboard = 0)
        val settled = field
        compose.onNode(hasSetTextAction()).performClick()
        insets(statusBar = 24, keyboard = 330)
        fieldAboveTheKeyboard(statusBar = 24, keyboard = 330)
        assertEquals(settled, field)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `a keyboard for another field leaves it where it is`() {
        searchBeside(statusBar = 24, topBar = 68)
        insets(statusBar = 24, keyboard = 0)
        val settled = field

        // A planet on the map is being typed in: the field beside it doesn't have the focus.
        insets(statusBar = 24, keyboard = 261)
        assertEquals(settled, field)
        assertFalse(field.bottom <= compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 261 * density)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `a panel that takes the search's place while its field is in use stays under the top bar`() {
        searchBeside(statusBar = 24, topBar = 68)
        insets(statusBar = 24, keyboard = 0)
        compose.onNode(hasSetTextAction()).performClick()

        // The outline opens from the rail while the search's field has the focus, and a planet on the map is typed in.
        searching = false
        compose.waitForIdle()
        insets(statusBar = 24, keyboard = 261)
        val outline = compose.onNode(hasText("Outline")).fetchSemanticsNode().boundsInRoot
        assertTrue("title at $outline", outline.top >= 92 * density)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `a panel that goes while risen leaves the map's row under the top bar`() {
        searchBeside(statusBar = 24, topBar = 68)
        insets(statusBar = 24, keyboard = 0)
        compose.onNode(hasSetTextAction()).performClick()
        insets(statusBar = 24, keyboard = 261)
        assertTrue(risen)

        // The source editor takes the panel's place beside the map, the keyboard still up.
        beside = false
        compose.waitForIdle()
        assertFalse(risen)
    }
}
