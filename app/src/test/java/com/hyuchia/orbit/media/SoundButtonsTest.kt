package com.hyuchia.orbit.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.page.Media
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The full-screen sound's buttons ([SoundButtons]) at the widths a phone,
 * a narrow phone, a split screen and a tablet give them: each whole, and
 * none over another.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w900dp-h900dp-mdpi")
class SoundButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    private val item = GalleryItem("ferry", MediaKind.Sound, Media("media/memo-ferry.mp4"), "Ferry")

    private val controls = object : SoundControls {
        override fun play(item: GalleryItem, from: Long?) = Unit

        override fun toggle(item: GalleryItem) = Unit

        override fun pause() = Unit

        override fun seekTo(position: Long) = Unit

        override fun skip(by: Long) = Unit

        override fun toggleMute() = Unit
    }

    /** The buttons by what TalkBack calls them: ten seconds back, play, ten on, and the sound's switch. */
    private val names = listOf("Back 10 seconds", "Play", "Forward 10 seconds", "Turn the sound off")

    private fun bounds(name: String): DpRect = compose.onNodeWithContentDescription(name).getUnclippedBoundsInRoot()

    @Test
    fun `no button covers another, and each is whole, however narrow the window`() {
        var width by mutableStateOf(420.dp)

        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(width)) {
                    SoundButtons(item, SoundState(item), controls)
                }
            }
        }

        // A tablet's, Pixels', a 360 dp phone's (312 dp inside the page's margins), a smaller one's, and a split screen's.
        for (shown in listOf<Dp>(420.dp, 372.dp, 345.dp, 312.dp, 300.dp, 280.dp, 260.dp)) {
            width = shown
            compose.waitForIdle()
            val buttons = names.map(::bounds)

            for ((index, button) in buttons.withIndex()) {
                assertTrue("${names[index]} is cut off at $shown: $button", button.left >= 0.dp && button.right <= shown)

                for (later in buttons.drop(index + 1)) {
                    assertTrue("${names[index]} and another button overlap at $shown: $button, $later", button.right <= later.left || later.right <= button.left)
                }
            }

            // In order, as they read: back, play, on, the switch.
            assertTrue("The buttons are out of order at $shown", buttons.zipWithNext().all { (before, after) -> before.right <= after.left })
        }
    }

    @Test
    fun `where there's room, play stands in the middle, under the sound's name and time`() {
        var width by mutableStateOf(420.dp)

        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(width)) {
                    SoundButtons(item, SoundState(item), controls)
                }
            }
        }

        for (shown in listOf(420.dp, 372.dp, 312.dp)) {
            width = shown
            compose.waitForIdle()
            val play = bounds("Play")
            val middle = (play.left + play.right) / 2
            assertTrue("Play is off the middle at $shown: $play", (middle - shown / 2).value in -1f..1f)
        }
    }
}
