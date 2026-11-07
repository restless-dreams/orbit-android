package com.hyuchia.orbit.map

import com.hyuchia.orbit.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A talk's ideas as the map takes them in ([SpokenIdeas]), with a page that
 * answers when the test says, and an undo stack of the test's own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpokenIdeasTest {
    /** The map's text, as each change leaves it. */
    private var map = "# Trip"

    /** What undo would bring back, newest last. */
    private val undoStack = ArrayDeque<String>()

    /** The changes asked of the page, by their ideas, each waiting for its answer: how many joined. */
    private val asked = mutableListOf<Pair<List<String>, CompletableDeferred<Int>>>()

    private val said = mutableListOf<MapEvent>()
    private var undone = 0
    private var locked = false

    private val TestScope.spoken: SpokenIdeas
        get() = SpokenIdeas(
            scope = backgroundScope,
            join = { _, ideas -> CompletableDeferred<Int>().also { asked += ideas to it }.await() },
            lastChange = { undoStack.lastOrNull() },
            undo = { undone += 1 },
            say = { said += it },
            locked = { locked },
        )

    /** The page makes the oldest change asked of it, saving the map as the map's screen does, then answers how many ideas joined. */
    private fun TestScope.answer(spoken: SpokenIdeas, count: Int = asked.first().first.size) {
        val (ideas, answer) = asked.removeAt(0)

        if (spoken.keepsUndo("add")) {
            undoStack.addLast(map)
        }

        map += ideas.joinToString("") { "\n- $it" }.takeIf { count > 0 }.orEmpty()
        answer.complete(count)
        runCurrent()
    }

    private val offered: List<MapEvent.Snackbar> get() = said.filterIsInstance<MapEvent.Snackbar>()

    @Test
    fun `a talk's changes are made one at a time, and go back with one undo`() = runTest {
        val spoken = spoken
        spoken.start()
        assertTrue(spoken.add("tokyo", listOf("Ramen")))
        assertTrue(spoken.add("tokyo", listOf("Sushi", "Tempura")))
        runCurrent()

        // The second waits for the first to be made: each save is known for the change it is.
        assertEquals(1, asked.size)
        answer(spoken)
        assertEquals(listOf("Sushi", "Tempura"), asked.single().first)
        answer(spoken)

        assertEquals(listOf("# Trip"), undoStack.toList())
        // An edit of the person's own, after: kept for undo on its own.
        assertTrue(spoken.keepsUndo("update"))
    }

    @Test
    fun `its Undo comes once the talk is over and its last ideas are saved`() = runTest {
        val spoken = spoken
        spoken.start()
        spoken.add("tokyo", listOf("Ramen"))
        runCurrent()
        spoken.over()
        assertTrue(offered.isEmpty())

        answer(spoken)
        assertEquals(ShownText.Plural(R.plurals.map_added_ideas, 1), offered.single().message)

        // The talk is still the last change: Undo takes it back.
        offered.single().onUndo!!.invoke()
        assertEquals(1, undone)
    }

    @Test
    fun `its Undo takes back the talk alone, never a change made after it`() = runTest {
        val spoken = spoken
        spoken.start()
        spoken.add("tokyo", listOf("Ramen"))
        runCurrent()
        answer(spoken)
        spoken.over()

        undoStack.addLast(map)
        offered.single().onUndo!!.invoke()
        assertEquals(0, undone)
    }

    @Test
    fun `nothing said after the talk is over is taken, however late it comes`() = runTest {
        val spoken = spoken
        assertFalse(spoken.add("tokyo", listOf("Before any talk")))
        spoken.start()
        spoken.over()
        assertFalse(spoken.add("tokyo", listOf("Too late")))
        runCurrent()
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a talk whose ideas didn't join, or that ended behind the lock, offers nothing to undo`() = runTest {
        val spoken = spoken
        spoken.start()
        spoken.add("tokyo", listOf("Ramen"))
        runCurrent()
        spoken.over()
        answer(spoken, count = 0)
        assertTrue(offered.isEmpty())

        spoken.start()
        spoken.add("tokyo", listOf("Sushi"))
        runCurrent()
        locked = true
        spoken.over()
        answer(spoken)
        assertTrue(offered.isEmpty())

        // Unlocked later, with the recorder opening, which ends talks: that talk is long over, and isn't offered then.
        locked = false
        spoken.over()
        assertTrue(offered.isEmpty())
    }
}
