package com.hyuchia.orbit.voice

import android.content.Context
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * How [Listening] goes through a speech service, with a stand-in one
 * ([TestRecognizer]) and the main thread's clock in the test's hands.
 */
@RunWith(AndroidJUnit4::class)
class SpeechServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The ideas that came out, one list for each pause. */
    private val ideas = mutableListOf<List<String>>()

    /** How each talk finished: null when nothing went wrong. */
    private val finished = mutableListOf<String?>()

    /** The speech services the talks were given, in order. */
    private val recognizers = mutableListOf<TestRecognizer>()

    private val listening = Listening(context, onIdeas = { ideas += it }, onFinished = { finished += it }) { listener -> TestRecognizer(listener).also(recognizers::add) }

    /** The main thread's clock moves on `milliseconds`, running what was waiting for then. */
    private fun later(milliseconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }

    @Test
    fun `each pause's sentences come out as ideas, and it listens again until it stops`() {
        listening.start()
        val recognizer = recognizers.single()
        assertEquals(1, recognizer.listened)

        recognizer.says("Coffee with the team. Ship it")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(listOf("Coffee with the team", "Ship it")), ideas)
        assertEquals(2, recognizer.listened)

        listening.stop()
        assertTrue(recognizer.stopped)
        recognizer.says("And the logo")
        assertEquals(listOf("And the logo"), ideas.last())
        assertEquals(listOf<String?>(null), finished)
        assertFalse(listening.heard.value.listening)
    }

    @Test
    fun `a talk that timed out takes no late words and lets the speech service go`() {
        listening.start()
        val recognizer = recognizers.single()
        listening.stop()

        // The speech service says nothing for as long as a talk waits for its last idea: the talk is over.
        later(2_999)
        assertTrue(finished.isEmpty())
        later(1)
        assertEquals(listOf<String?>(null), finished)
        assertTrue(recognizer.destroyed)

        // Its words come after all: they go nowhere.
        recognizer.says("Too late")
        recognizer.fails(SpeechRecognizer.ERROR_NETWORK)
        later(1_000)
        assertTrue(ideas.isEmpty())
        assertEquals(listOf<String?>(null), finished)
        assertNull(listening.heard.value.problem)
    }

    @Test
    fun `a talk that's over lets its speech service go, and the next talk has its own`() {
        listening.start()
        val first = recognizers.single()
        listening.stop()
        first.says("Last idea")
        assertTrue(first.destroyed)

        listening.start()
        val second = recognizers.last()
        assertEquals(2, recognizers.size)
        assertFalse(second.destroyed)

        // The first talk's speech service, saying more, gives the second talk nothing.
        first.says("From the talk before")
        second.says("This talk's idea")
        assertEquals(listOf(listOf("Last idea"), listOf("This talk's idea")), ideas)
    }

    @Test
    fun `a talk closed at once gives nothing more, the idea being said left out`() {
        listening.start()
        val recognizer = recognizers.single()
        recognizer.says("Said in time")

        listening.close()
        assertTrue(recognizer.destroyed)
        recognizer.says("Being said as it closed")
        later(1_000)

        assertEquals(listOf(listOf("Said in time")), ideas)
        assertEquals(1, recognizer.listened)
        assertFalse(listening.heard.value.listening)
    }

    @Test
    fun `a speech service that gives up says why, and lets the microphone go`() {
        listening.start()
        val recognizer = recognizers.single()
        recognizer.fails(SpeechRecognizer.ERROR_AUDIO)

        assertEquals(listOf<String?>(context.getString(R.string.voice_microphone_busy)), finished)
        assertTrue(recognizer.destroyed)
    }
}
