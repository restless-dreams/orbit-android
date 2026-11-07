package com.hyuchia.orbit.map

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File

/**
 * A voice memo's take as it's listened to before it's kept ([TakePlayer]),
 * on Robolectric's player and audio manager: the phone's sound, headphones,
 * and a save that didn't go through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TakePlayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = shadowOf(context.getSystemService(AudioManager::class.java))
    private val take = File(context.cacheDir, "recordings/take-1.mp4")

    @Before
    fun recorded() {
        take.parentFile!!.mkdirs()
        take.writeBytes(ByteArray(4_096))
        // Robolectric's player plays what it's told about: a take three seconds long.
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(take.path), ShadowMediaPlayer.MediaInfo(3_000, 0))
    }

    @After
    fun cleanUp() {
        take.parentFile!!.deleteRecursively()
    }

    private val TestScope.player: TakePlayer get() = TakePlayer(context, backgroundScope).also { it.load(take) }

    /** The phone tells whoever has its sound that another app takes it, as `change` says. */
    private fun soundTaken(change: Int) {
        audio.lastAudioFocusRequest.listener.onAudioFocusChange(change)
    }

    @Test
    fun `playing takes the phone's sound, and pausing gives it back`() = runTest {
        val player = player
        player.toggle()
        assertTrue(player.playing)
        val asked = audio.lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, asked.focusGain)

        player.toggle()
        assertFalse(player.playing)
        assertSame(asked, audio.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `a call, or any other app taking the sound, pauses the take`() = runTest {
        val player = player
        player.toggle()
        soundTaken(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(player.playing)

        player.toggle()
        assertTrue(player.playing)
        soundTaken(AudioManager.AUDIOFOCUS_LOSS)
        assertFalse(player.playing)

        // Directions spoken over it: words are lost under them, so the take pauses rather than playing on quieter.
        player.toggle()
        soundTaken(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertFalse(player.playing)
    }

    @Test
    fun `headphones coming out pause the take, so it doesn't go on out loud`() = runTest {
        val player = player
        player.toggle()
        assertTrue(player.playing)

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.playing)
    }

    @Test
    fun `while a call has the phone's sound, the take waits`() = runTest {
        val player = player
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        player.toggle()
        assertFalse(player.playing)
    }

    @Test
    fun `a take that couldn't be saved can still be played, and saved again`() = runTest {
        val player = player
        val full = ShownText.Resource(R.string.audio_map_room_reached)

        assertEquals(full, player.keep(take) { full })
        assertTrue(player.loaded)
        player.toggle()
        assertTrue(player.playing)

        // Saved this time: the player lets the take go, which the map's folder has now.
        assertNull(player.keep(take) { null })
        assertFalse(player.loaded)
    }

    @Test
    fun `the player lets go of the take while it's saved`() = runTest {
        val player = player
        player.toggle()
        var loadedWhileSaving: Boolean? = null

        player.keep(take) {
            loadedWhileSaving = player.loaded
            null
        }

        assertNotNull(loadedWhileSaving)
        assertFalse(loadedWhileSaving!!)
        assertFalse(player.playing)
    }
}
