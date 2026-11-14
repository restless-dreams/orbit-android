package com.hyuchia.orbit.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import com.hyuchia.orbit.voice.Listening
import com.hyuchia.orbit.voice.TestRecognizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A talk on the map's screen ([Talk]), through a stand-in speech service
 * ([TestRecognizer]): when it ends, and what it lets through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TalkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The ideas the talk heard, as the map would be given them. */
    private val ideas = mutableListOf<String>()

    /** The speech services the talks were given, in order. */
    private val recognizers = mutableListOf<TestRecognizer>()

    /** How each talk finished, as the map's screen hears it. */
    private val finished = mutableListOf<String?>()

    private val talk: Talk = run {
        lateinit var made: Talk
        val listening = Listening(context, onIdeas = { ideas += it }, onFinished = { made.finished(it) }) { listener -> TestRecognizer(listener).also(recognizers::add) }

        Talk(listening).also { created ->
            made = created
            created.onFinished = { finished += it }

            created.begin = {
                created.start(keep = false)
                true
            }
        }
    }

    @Test
    fun `the map locking ends a hands-free talk at once, and nothing said after it comes out`() = runTest {
        val talkable = MutableStateFlow(true)
        backgroundScope.launch { talk.endWhenNot(talkable) }
        runCurrent()

        assertTrue(talk.hold())
        talk.keep()
        val recognizer = recognizers.single()
        recognizer.says("Before the lock")

        // Locked from its menu while the talk listens on, hands-free: the talk ends there and then.
        talkable.value = false
        runCurrent()

        assertFalse(talk.active)
        assertTrue(recognizer.destroyed)
        assertEquals(listOf<String?>(null), finished)

        // A speech service that goes on talking is heard by no one.
        recognizer.says("After the lock")
        assertEquals(listOf("Before the lock"), ideas)
    }

    @Test
    fun `the recorder opening ends a talk before anything is recorded, its idea being said left out`() = runTest {
        val talkable = MutableStateFlow(true)
        backgroundScope.launch { talk.endWhenNot(talkable) }
        runCurrent()

        assertTrue(talk.hold())
        talk.release()
        val recognizer = recognizers.single()
        assertTrue(talk.stopping)

        // The recorder opens while the talk waits for its last idea: the microphone is let go at once.
        talkable.value = false
        runCurrent()

        assertFalse(talk.active)
        assertTrue(recognizer.destroyed)
        recognizer.says("Being said as the recorder opened")
        assertTrue(ideas.isEmpty())
    }

    @Test
    fun `a talk can't begin while the last one is still finishing its last idea`() {
        assertTrue(talk.hold())
        talk.release()
        assertTrue(talk.active)

        // Held again while the speech service still has the microphone: nothing begins.
        assertFalse(talk.hold())
        assertEquals(1, recognizers.size)

        recognizers.single().says("Last idea")
        assertFalse(talk.active)
        assertTrue(talk.hold())
        assertEquals(2, recognizers.size)
    }

    @Test
    fun `the map's screen going ends its talk, and the map still offers to take the talk back`() = runTest {
        val events = mutableListOf<MapEvent>()
        var undone = 0

        // The map's side of the talk: its ideas joined one change at a time, and its message once it's over.
        val spoken = SpokenIdeas(
            scope = backgroundScope,
            join = { _, said -> said.size },
            lastChange = { "# Before" },
            undo = { undone += 1 },
            say = events::add,
            locked = { false },
        )

        talk.onFinished = { problem ->
            finished += problem
            spoken.over()
        }

        assertTrue(talk.hold())
        spoken.start()
        recognizers.single().says("An idea")
        spoken.add("root", ideas.toList())
        runCurrent()

        // The screen goes with the talk still listening, as when the map closes or the screen is made again.
        talk.close()
        runCurrent()

        assertFalse(talk.active)
        assertTrue(recognizers.single().destroyed)
        assertEquals(listOf<String?>(null), finished)
        val message = events.filterIsInstance<MapEvent.Snackbar>().single()
        assertEquals(ShownText.Plural(R.plurals.map_added_ideas, 1), message.message)
        message.onUndo?.invoke()
        assertEquals(1, undone)
    }

    @Test
    fun `the map's screen going with no talk lets the microphone go and says nothing`() {
        talk.close()

        assertFalse(talk.active)
        assertEquals(emptyList<String?>(), finished)
    }
}
