package com.hyuchia.orbit.media

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.nio.file.Files

/** A voice memo needs the phone's sound: during a call, or while another app keeps it, nothing is recorded, and what was is kept. */
@RunWith(AndroidJUnit4::class)
class VoiceRecorderFocusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sound = shadowOf(context.getSystemService(AudioManager::class.java))
    private val folder: File = Files.createTempDirectory("orbit-takes").toFile()

    /** The recorder's clock never ticks here: what's tested is how a take starts and goes on. */
    private val recorder = VoiceRecorder(context, File(folder, "take-1.mp4"), limit = { Long.MAX_VALUE }, scope = TestScope())

    @After
    fun cleanUp() {
        recorder.release()
        folder.deleteRecursively()
    }

    @Test
    fun `a take doesn't start during a call, and says why`() {
        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertFalse(recorder.record())
        assertEquals(RecorderPhase.Ready, recorder.phase)
        assertEquals(R.string.audio_sound_is_taken, recorder.problem)

        // Once the call is over, it does.
        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertTrue(recorder.record())
        assertEquals(RecorderPhase.Recording, recorder.phase)
        assertNull(recorder.problem)
    }

    @Test
    fun `a paused take doesn't go on during a call, and stays paused`() {
        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertTrue(recorder.record())
        recorder.pause()

        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(recorder.record())
        assertEquals(RecorderPhase.Paused, recorder.phase)
        assertEquals(R.string.audio_sound_is_taken, recorder.problem)
    }

    @Test
    fun `recording again during a call keeps the take that's done`() {
        File(folder, "take-1.mp4").writeBytes(ByteArray(4_096))
        recorder.restore(elapsed = 5_000, levels = listOf(0.4f, 0.8f), full = false)
        assertEquals(RecorderPhase.Done, recorder.phase)

        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(recorder.record())

        assertEquals(RecorderPhase.Done, recorder.phase)
        assertEquals(4_096L, recorder.take?.length())
        assertEquals(5_000L, recorder.elapsed)
    }
}
