package com.hyuchia.orbit.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * A live photo's motion ([LiveMotion]) as Orbit leaves the screen, with a
 * stand-in player that says when its texture is laid out, as ExoPlayer
 * does, and a lifecycle the test moves.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class LiveMotionTest {
    @get:Rule
    val compose = createComposeRule()

    /** Orbit's place on screen: shown, until the test takes it off. */
    private val screen = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = registry
    }

    /**
     * A player that plays nothing, but says what a player says: its texture's
     * size, whether it's playing, and with `frames`, its first frame drawn as
     * it starts.
     */
    private class TestPlayer(private val frames: Boolean = false) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .build()

        override fun getState(): State = state

        private fun change(update: State.Builder.() -> Unit): ListenableFuture<*> {
            state = state.buildUpon().apply(update).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) = change {
            setPlaylist(mediaItems.mapIndexed { index, item -> MediaItemData.Builder(index).setMediaItem(item).build() })
        }

        override fun handlePrepare() = change { setPlaybackState(Player.STATE_READY) }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean) = change {
            setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            setNewlyRenderedFirstFrame(playWhenReady && frames)
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int) = change {}

        override fun handleSetVolume(volume: Float) = change { setVolume(volume) }

        override fun handleSetVolume(volume: Float, volumeOperationType: Int) = change { setVolume(volume) }

        override fun handleSetAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) = change {}

        // The texture is laid out: a motion can start.
        override fun handleSetVideoOutput(videoOutput: Any) = change { setSurfaceSize(Size(320, 240)) }

        override fun handleClearVideoOutput(videoOutput: Any?) = change { setSurfaceSize(Size.ZERO) }

        override fun handleRelease() = change {}
    }

    @Test
    fun `a live photo's motion stops, sound and all, as Orbit leaves the screen, and its still is back`() {
        val player = TestPlayer()
        var play by mutableStateOf<LivePlay?>(null)
        var ended = 0
        screen.registry.currentState = Lifecycle.State.RESUMED

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                LiveMotion(
                    File("media/picture-ferry-motion.mp4"),
                    play,
                    onShowing = {},
                    onEnded = {
                        ended += 1
                        play = null
                    },
                    newPlayer = { player },
                )
            }
        }

        // Held, the picture plays its motion with its sound.
        play = LivePlay(sound = true)
        compose.waitForIdle()
        assertTrue(player.playWhenReady)
        assertEquals(1f, player.volume)

        // Home, or the screen off: Orbit leaves the screen.
        screen.registry.currentState = Lifecycle.State.CREATED
        compose.waitForIdle()

        assertFalse(player.playWhenReady)
        assertEquals(1, ended)
    }

    @Test
    fun `held as Orbit leaves the screen, the motion ends and its still is back, though the picture is still held`() {
        val player = TestPlayer(frames = true)
        var play by mutableStateOf<LivePlay?>(null)
        var showing = false
        var ended = 0
        screen.registry.currentState = Lifecycle.State.RESUMED

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                // Held, the picture keeps asking for its motion through its end, until the finger lifts.
                LiveMotion(File("media/picture-ferry-motion.mp4"), play, onShowing = { showing = it }, onEnded = { ended += 1 }, newPlayer = { player })
            }
        }

        play = LivePlay(sound = true, held = true)
        compose.waitForIdle()
        assertTrue(showing)

        // The screen turns off with the finger still on the picture.
        screen.registry.currentState = Lifecycle.State.CREATED
        compose.waitForIdle()

        assertFalse(player.playWhenReady)
        assertFalse(showing)
        assertEquals(1, ended)
    }

    @Test
    fun `headphones coming out end a motion playing with its sound, and its still is back`() {
        val player = TestPlayer(frames = true)
        var play by mutableStateOf<LivePlay?>(null)
        var showing = false
        var ended = 0
        screen.registry.currentState = Lifecycle.State.RESUMED

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                LiveMotion(File("media/picture-ferry-motion.mp4"), play, onShowing = { showing = it }, onEnded = { ended += 1 }, newPlayer = { player })
            }
        }

        // Held, with its sound in the headphones.
        play = LivePlay(sound = true, held = true)
        compose.waitForIdle()
        assertTrue(showing)

        unplugged()

        assertFalse(player.playWhenReady)
        assertFalse(showing)
        assertEquals(1, ended)
    }

    @Test
    fun `headphones coming out leave a motion playing without sound as it is`() {
        val player = TestPlayer()
        var ended = 0
        screen.registry.currentState = Lifecycle.State.RESUMED

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                LiveMotion(File("media/picture-ferry-motion.mp4"), LivePlay(sound = false), onShowing = {}, onEnded = { ended += 1 }, newPlayer = { player })
            }
        }

        compose.waitForIdle()
        assertTrue(player.playWhenReady)

        unplugged()

        assertTrue(player.playWhenReady)
        assertEquals(0, ended)
    }

    /** Headphones come out, as Android tells apps playing sound. */
    private fun unplugged() {
        ApplicationProvider.getApplicationContext<Context>().sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    @Test
    fun `leaving the screen with no motion playing leaves the picture as it is`() {
        var ended = 0
        screen.registry.currentState = Lifecycle.State.RESUMED

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                LiveMotion(File("media/picture-ferry-motion.mp4"), null, onShowing = {}, onEnded = { ended += 1 }, newPlayer = { error("Nothing plays") })
            }
        }

        screen.registry.currentState = Lifecycle.State.CREATED
        compose.waitForIdle()
        assertEquals(0, ended)
    }
}
