package com.hyuchia.orbit.media

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaRecorder
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.nio.file.Files
import java.time.Duration

/**
 * A recorder that refuses to pause, or to go on, when the test says so, as a
 * phone's recorder can partway through a take: with an
 * IllegalStateException, or the plain RuntimeException Android's recorder
 * throws for any other error ("pause failed.", "resume failed.").
 */
@Implements(MediaRecorder::class)
class FailingMediaRecorder : ShadowMediaRecorder() {
    @Implementation
    protected fun pause() {
        failsToPause?.let { throw it() }
    }

    @Implementation
    protected fun resume() {
        failsToResume?.let { throw it() }
    }

    companion object {
        @JvmStatic
        var failsToPause: (() -> RuntimeException)? = null

        @JvmStatic
        var failsToResume: (() -> RuntimeException)? = null
    }
}

/**
 * A take recorded so far stays when the recorder fails to pause or to go
 * on: done, to listen to and save, with what happened said, and gone only
 * when it's thrown away or recorded over.
 */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [FailingMediaRecorder::class])
class VoiceRecorderFailureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sound = shadowOf(context.getSystemService(AudioManager::class.java))
    private val folder: File = Files.createTempDirectory("orbit-takes").toFile()

    /** The recorder's clock never ticks here: what's tested is what a failure leaves. */
    private val recorder = VoiceRecorder(context, File(folder, "take-1.mp4"), limit = { Long.MAX_VALUE }, scope = TestScope())

    @Before
    fun start() {
        FailingMediaRecorder.failsToPause = null
        FailingMediaRecorder.failsToResume = null
    }

    @After
    fun cleanUp() {
        recorder.release()
        folder.deleteRecursively()
    }

    /** A take started, with `bytes` of it recorded so far, over `seconds`. */
    private fun recording(bytes: Int, seconds: Long = 5) {
        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertTrue(recorder.record())
        recorder.file.writeBytes(ByteArray(bytes))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds))
    }

    @Test
    fun `a take that can't go on after a pause stays, done, and says the recording stopped`() {
        recording(8_192)
        recorder.pause()
        assertEquals(RecorderPhase.Paused, recorder.phase)

        FailingMediaRecorder.failsToResume = { IllegalStateException("The recorder couldn't go on") }
        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertFalse(recorder.record())

        assertEquals(RecorderPhase.Done, recorder.phase)
        assertEquals(8_192L, recorder.take?.length())
        assertEquals(R.string.audio_recording_failed, recorder.problem)
    }

    @Test
    fun `a take that can't pause stays, done, and says the recording stopped`() {
        recording(8_192)

        FailingMediaRecorder.failsToPause = { IllegalStateException("The recorder couldn't pause") }
        recorder.pause()

        assertEquals(RecorderPhase.Done, recorder.phase)
        assertEquals(8_192L, recorder.take?.length())
        assertEquals(R.string.audio_recording_failed, recorder.problem)
    }

    @Test
    fun `a take stays when the phone's recorder fails to pause or go on with any other error`() {
        // Android's recorder says "pause failed." for an error that isn't the wrong state: a plain RuntimeException.
        recording(8_192)
        FailingMediaRecorder.failsToPause = { RuntimeException("pause failed.") }
        recorder.pause()

        assertEquals(RecorderPhase.Done, recorder.phase)
        assertEquals(8_192L, recorder.take?.length())

        // And "resume failed." as it goes on.
        FailingMediaRecorder.failsToPause = null
        recorder.discard()
        recording(4_096)
        recorder.pause()
        FailingMediaRecorder.failsToResume = { RuntimeException("resume failed.") }
        sound.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertFalse(recorder.record())

        assertEquals(RecorderPhase.Done, recorder.phase)
        assertEquals(4_096L, recorder.take?.length())
        assertEquals(R.string.audio_recording_failed, recorder.problem)
    }

    @Test
    fun `a take kept after a failure goes only when it's thrown away or recorded over`() {
        recording(8_192)
        FailingMediaRecorder.failsToPause = { IllegalStateException("The recorder couldn't pause") }
        recorder.pause()
        assertTrue(recorder.file.isFile)

        // Recorded over: the new take starts in its place.
        FailingMediaRecorder.failsToPause = null
        recording(0)
        assertEquals(RecorderPhase.Recording, recorder.phase)
        assertEquals(0L, recorder.file.length())
        assertNull(recorder.problem)

        // Thrown away.
        recorder.file.writeBytes(ByteArray(4_096))
        FailingMediaRecorder.failsToPause = { IllegalStateException("The recorder couldn't pause") }
        recorder.pause()
        assertEquals(RecorderPhase.Done, recorder.phase)
        recorder.discard()
        assertEquals(RecorderPhase.Ready, recorder.phase)
        assertFalse(recorder.file.exists())
    }

    @Test
    fun `a failure before anything was recorded leaves nothing`() {
        recording(0)

        FailingMediaRecorder.failsToPause = { IllegalStateException("The recorder couldn't pause") }
        recorder.pause()

        assertEquals(RecorderPhase.Ready, recorder.phase)
        assertNull(recorder.take)
        assertFalse(recorder.file.exists())
        assertEquals(R.string.audio_recording_failed, recorder.problem)
    }

    @Test
    fun `a failure a moment in leaves nothing, though the file has its header`() {
        // A phone's recorder writes the file's header as it starts: a take 50 ms long isn't empty, but is nothing to hear.
        recording(1_024, seconds = 0)
        ShadowSystemClock.advanceBy(Duration.ofMillis(50))

        FailingMediaRecorder.failsToPause = { RuntimeException("pause failed.") }
        recorder.pause()

        assertEquals(RecorderPhase.Ready, recorder.phase)
        assertNull(recorder.take)
        assertFalse(recorder.file.exists())
        assertEquals(R.string.audio_recording_failed, recorder.problem)
    }
}
