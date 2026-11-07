package com.hyuchia.orbit.media

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

/**
 * A voice memo's take through a screen made again, as Android makes it for
 * a new language or font size ([rememberVoiceRecorder]), on Robolectric's
 * recorder, which writes no file: the test writes what the microphone heard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VoiceTakeTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folder = File(context.cacheDir, "recordings")

    /** The recorder's clock, never moved on: the take's time comes from the phone's, and its waveform from the test. */
    private val clock = CoroutineScope(StandardTestDispatcher())

    private val restoration = StateRestorationTester(compose)

    /** The recorder the screen has, made again with it. */
    private var recorder: VoiceRecorder? = null

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun showRecorder() {
        restoration.setContent {
            recorder = rememberVoiceRecorder(folder, { Long.MAX_VALUE }, clock)
        }
    }

    /** Records `milliseconds` of a take, its file filled and its waveform drawn as the microphone would. */
    private fun record(milliseconds: Long): VoiceRecorder {
        val recording = compose.runOnIdle { recorder!!.also { assertTrue(it.record()) } }
        recording.file.writeBytes(ByteArray(8_192))
        compose.runOnIdle { recording.levels.addAll(listOf(0.2f, 0.9f, 0.4f)) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
        return recording
    }

    @Test
    fun `a paused take outlasts the screen made again, ready to listen to and save`() {
        showRecorder()
        val before = record(2_000)
        // Leaving Orbit for the phone's settings pauses it.
        compose.runOnIdle { before.pause() }
        assertEquals(RecorderPhase.Paused, before.phase)
        val elapsed = before.elapsed
        assertTrue(elapsed >= 2_000)

        // A new language: Android makes the screen again.
        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            val after = recorder!!
            assertNotSame(before, after)
            assertEquals(RecorderPhase.Done, after.phase)
            assertEquals(before.file, after.take)
            assertEquals(elapsed, after.elapsed)
            assertEquals(listOf(0.2f, 0.9f, 0.4f), after.levels.toList())
        }
    }

    @Test
    fun `a finished take outlasts the screen made again, and records again from scratch`() {
        showRecorder()
        val before = record(5_000)
        compose.runOnIdle { before.finish() }
        assertEquals(RecorderPhase.Done, before.phase)
        val elapsed = before.elapsed

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            val after = recorder!!
            assertEquals(RecorderPhase.Done, after.phase)
            assertEquals(before.file, after.take)
            assertEquals(elapsed, after.elapsed)

            // Recording again throws that take away, as before.
            after.discard()
            assertFalse(before.file.exists())
        }
    }

    @Test
    fun `a take too short to hear doesn't come back, and its file goes`() {
        showRecorder()
        val before = record(300)
        compose.runOnIdle { before.pause() }

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            assertEquals(RecorderPhase.Ready, recorder!!.phase)
            assertNull(recorder!!.take)
            assertFalse(before.file.exists())
        }
    }
}
