package com.hyuchia.orbit.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaveQueueTest {
    /** What was written and kept aside, in order. */
    private val done = mutableListOf<String>()

    /** Each write, and each text kept aside, waits here until the test lets it through, as a slow storage's does. */
    private val held = ArrayDeque<CompletableDeferred<Unit>>()

    private suspend fun slowly(what: String) {
        CompletableDeferred<Unit>().also { held += it }.await()
        done += what
    }

    private fun queue(scope: CoroutineScope) = SaveQueue(scope, write = { slowly("write ${it.text}") }, keepAside = { slowly("aside $it") })

    /** Lets every write and text kept aside through, each in turn. */
    private fun TestScope.letThrough() {
        runCurrent()

        while (held.isNotEmpty()) {
            held.removeFirst().complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun `a save made before a change from elsewhere, coming behind the screen's text, takes nothing's place`() = runTest {
        val queue = queue(backgroundScope)
        queue.save(Save("A", 1))
        runCurrent()

        // A is being written. B, the screen's newest text, waits; then C comes, made on the map before a change from elsewhere.
        queue.save(Save("B", 1))
        queue.setAside("C")
        letThrough()

        assertEquals(listOf("write A", "aside C", "write B"), done)
    }

    @Test
    fun `the screen's text, coming behind a save made before a change from elsewhere, takes nothing's place`() = runTest {
        val queue = queue(backgroundScope)
        queue.save(Save("A", 1))
        runCurrent()
        queue.setAside("C")
        queue.save(Save("B", 1))
        letThrough()

        assertEquals(listOf("write A", "aside C", "write B"), done)
    }

    @Test
    fun `of the screen's texts waiting only the newest is written, and every one set aside is kept`() = runTest {
        val queue = queue(backgroundScope)
        queue.save(Save("A", 1))
        runCurrent()
        queue.save(Save("B", 1))
        queue.setAside("C")
        queue.save(Save("D", 1))
        queue.setAside("E")
        letThrough()

        // D holds all B did: B has nothing more to write.
        assertEquals(listOf("write A", "aside C", "aside E", "write D"), done)
    }

    @Test
    fun `a save waiting as the screen catches up with a change from elsewhere goes into the history`() = runTest {
        val queue = queue(backgroundScope)
        queue.save(Save("A", 1))
        runCurrent()
        queue.save(Save("B", 1))
        queue.caughtUp()
        // The next edit, on the map as the change left it, doesn't take B's place.
        queue.save(Save("D", 2))
        letThrough()

        assertEquals(listOf("write A", "aside B", "write D"), done)
    }

    @Test
    fun `as the screen goes, a write begun ends, and what's set aside is left for it to keep`() = runTest {
        val screen = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = queue(screen)
        queue.save(Save("A", 1))
        runCurrent()
        queue.setAside("C")
        queue.save(Save("B", 1))
        queue.setAside("E")

        screen.cancel()
        letThrough()

        // B is the screen's text, which closing writes itself.
        assertEquals(listOf("write A"), done)
        assertTrue(queue.job.isCompleted)
        assertEquals(listOf("C", "E"), queue.takeAsides())
        assertTrue(queue.takeAsides().isEmpty())
    }
}
