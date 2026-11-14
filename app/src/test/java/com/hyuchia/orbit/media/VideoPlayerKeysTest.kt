package com.hyuchia.orbit.media

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hyuchia.orbit.OrbitApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The video player with a keyboard: a button Tab is on takes Space and
 * Enter, as it takes a tap, and with the player itself focused they play
 * and pause. Its controls, which go after a moment of playing, stay while
 * one of them has the keyboard's focus: gone, they'd take it with them.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-mdpi")
class VideoPlayerKeysTest {
    @get:Rule
    val compose = createComposeRule()

    private val player = TestPlayer()
    private var closed = 0

    private fun open() {
        compose.setContent {
            VideoPlayer(player, "Tram 28", missing = false, poster = null, onElsewhere = null, onClosed = { closed += 1 })
        }

        compose.waitForIdle()
    }

    private fun focused(name: String): Boolean = compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Focused) == true }

    /** Whether the player itself has the focus, rather than one of its controls. */
    private fun onTheVideo(): Boolean {
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        return focused.size == 1 && SemanticsActions.OnClick !in focused[0].config && SemanticsProperties.ContentDescription !in focused[0].config
    }

    /** A key on the keyboard, to whatever has the focus in the player's window: something has to. */
    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** Tab, until the button called `name` has the focus. */
    private fun tabTo(name: String) {
        repeat(12) {
            if (focused(name)) {
                return
            }

            press(Key.Tab)
        }

        compose.onNodeWithContentDescription(name).assertIsFocused()
    }

    @Test
    fun `Enter on the focused Close closes the player`() {
        open()
        tabTo("Close")

        press(Key.Enter)
        compose.waitUntil(timeoutMillis = 5_000) { closed == 1 }
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Space on the focused Close closes the player`() {
        open()
        tabTo("Close")

        press(Key.Spacebar)
        compose.waitUntil(timeoutMillis = 5_000) { closed == 1 }
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Enter and Space on the focused sound switch turn the sound off and on, and the video stays as it was`() {
        open()
        tabTo("Turn the sound off")

        press(Key.Enter)
        assertEquals(0f, player.volume)
        assertFalse(player.playWhenReady)
        compose.onNodeWithContentDescription("Turn the sound on").assertIsFocused()

        press(Key.Spacebar)
        assertEquals(1f, player.volume)
        assertFalse(player.playWhenReady)
        compose.onNodeWithContentDescription("Turn the sound off").assertIsFocused()
    }

    @Test
    fun `with the player itself focused, Space and Enter play and pause`() {
        open()

        press(Key.Spacebar)
        assertTrue(player.playWhenReady)

        press(Key.Enter)
        assertFalse(player.playWhenReady)
        assertEquals(0, closed)
    }

    @Test
    fun `the controls stay while one of them has the keyboard's focus, and go once it's back on the video`() {
        open()
        press(Key.Spacebar)
        assertTrue(player.isPlaying)
        tabTo("Turn the sound off")

        // Long after they'd have gone, the sound switch is there, with the focus: Enter still turns the sound off.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Turn the sound off").assertIsFocused()
        press(Key.Enter)
        assertEquals(0f, player.volume)
        assertTrue(player.isPlaying)

        // Tab goes on through the controls and back to the video, which plays on: after a moment, they go.
        repeat(12) {
            if (!onTheVideo()) {
                press(Key.Tab)
            }
        }

        assertTrue(onTheVideo())
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isEmpty())

        // Their keys are still the player's.
        press(Key.Spacebar)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `with the controls gone, Tab brings them back with the focus on the first, and Shift+Tab on the last`() {
        open()
        press(Key.Spacebar)
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isEmpty())

        // Tab: back they come, Close first, and they stay while it has the focus.
        press(Key.Tab)
        compose.onNodeWithContentDescription("Close").assertIsFocused()
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertIsFocused()
        assertTrue(player.isPlaying)

        // Back on the video, they go again; Shift+Tab brings them back on the last, the video's position.
        repeat(12) {
            if (!onTheVideo()) {
                press(Key.Tab)
            }
        }

        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isEmpty())
        compose.onNode(isFocused()).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Position").assertIsFocused()
        assertTrue(player.isPlaying)
    }

    @Test
    fun `the controls go after a moment of playing, with the keys on the video`() {
        open()
        press(Key.Spacebar)
        assertTrue(player.isPlaying)

        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isEmpty())
    }
}

/**
 * The video player's buttons in the narrowest window Android makes, 220 dp
 * wide: back, play and on whole, side by side, none smaller than a touch.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class, qualifiers = "w220dp-h640dp-mdpi")
class VideoPlayerNarrowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `back, play and on are whole at 220 dp`() {
        compose.setContent {
            VideoPlayer(TestPlayer(), "Tram 28", missing = false, poster = null, onElsewhere = null, onClosed = {})
        }

        compose.waitForIdle()
        val width = 220.dp
        val buttons = listOf("Back 10 seconds", "Play", "Forward 10 seconds").map { compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot() }

        for (button in buttons) {
            assertTrue("A button is cut off in $width: $button", button.left >= 0.dp && button.right <= width)
            assertTrue("A button is smaller than a touch: $button", button.width >= 48.dp && button.height >= 48.dp)
        }

        val (back, play, forward) = buttons
        assertTrue("The buttons overlap: $buttons", back.right <= play.left && play.right <= forward.left)
        assertTrue("Play is no bigger than the others: $buttons", play.width > back.width && play.width > forward.width)
    }
}

/** A player that plays and pauses as it's told, at once, with a minute's video it never decodes: it goes ten seconds back and on, as the app's does. */
@OptIn(UnstableApi::class)
internal class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var state = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
        .setPlaylist(listOf(MediaItemData.Builder("tram").setDurationUs(60_000_000).build()))
        .setPlaybackState(Player.STATE_READY)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    override fun getState(): State = state

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        state = state.buildUpon().setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        state = state.buildUpon().setVolume(volume).build()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        state = state.buildUpon().setContentPositionMs(positionMs).build()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> = Futures.immediateVoidFuture()
}
