package com.hyuchia.orbit.media

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.page.Media
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The map's pictures and sounds full screen, with a keyboard: a button Tab
 * is on takes Space and Enter, as it takes a tap; with the viewer itself
 * focused, they play and pause a sound, as its own keys.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-mdpi")
class MediaViewerKeysTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0
    private var toggles = 0

    private val sounds = object : SoundControls {
        override fun play(item: GalleryItem, from: Long?) = Unit

        override fun toggle(item: GalleryItem) {
            toggles += 1
        }

        override fun pause() = Unit

        override fun seekTo(position: Long) = Unit

        override fun skip(by: Long) = Unit

        override fun toggleMute() = Unit
    }

    private fun open(item: GalleryItem) {
        compose.setContent {
            MediaViewer(
                mapId = "trip",
                items = listOf(item),
                start = 0,
                source = { null },
                onPlay = {},
                onClosed = { closed += 1 },
                sound = null,
                sounds = sounds,
            )
        }

        compose.waitForIdle()
    }

    private fun sound() = open(GalleryItem("memo", MediaKind.Sound, Media("media/memo.mp3"), "Memo"))

    /** A key on the keyboard, to whatever has the focus in the viewer's window: something has to. */
    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** Tab, until Close has the focus. */
    private fun tabToClose() {
        repeat(16) {
            val focused = compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Focused) == true }

            if (focused) {
                return
            }

            press(Key.Tab)
        }

        compose.onNodeWithContentDescription("Close").assertIsFocused()
    }

    @Test
    fun `on a sound's page, Enter on the focused Close closes the viewer, and the sound stays as it was`() {
        sound()
        tabToClose()

        press(Key.Enter)
        compose.waitUntil(timeoutMillis = 5_000) { closed == 1 }
        assertEquals(0, toggles)
    }

    @Test
    fun `on a sound's page, Space on the focused Close closes the viewer, and the sound stays as it was`() {
        sound()
        tabToClose()

        press(Key.Spacebar)
        compose.waitUntil(timeoutMillis = 5_000) { closed == 1 }
        assertEquals(0, toggles)
    }

    @Test
    fun `on a sound's page, with the viewer itself focused, Space and Enter play and pause it`() {
        sound()

        press(Key.Spacebar)
        assertEquals(1, toggles)

        press(Key.Enter)
        assertEquals(2, toggles)
        assertEquals(0, closed)
    }

    @Test
    fun `on a picture's page, Enter on the focused Close closes the viewer`() {
        open(GalleryItem("tower", MediaKind.Picture, Media("media/tower.jpg"), "Tower"))
        tabToClose()

        press(Key.Enter)
        compose.waitUntil(timeoutMillis = 5_000) { closed == 1 }
    }
}
