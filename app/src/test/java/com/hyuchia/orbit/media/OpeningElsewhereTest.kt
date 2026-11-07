package com.hyuchia.orbit.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player's "Open in another app" for a map's video: what it asks, and when. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningElsewhereTest {
    @Test
    fun `a clean copy opens without a question, and one Orbit can't clean waits for the person's answer`() = runTest {
        var result = Shared.Clean
        var opened = 0
        var asItIs = 0

        val opening = OpeningElsewhere(
            this,
            open = {
                opened += 1
                result
            },
            openAsItIs = { asItIs += 1 },
        )

        opening.start()
        advanceUntilIdle()
        assertEquals(1, opened)
        assertFalse(opening.asking)
        assertFalse(opening.busy)

        // Cancelled, nothing goes.
        result = Shared.NotClean
        opening.start()
        advanceUntilIdle()
        assertTrue(opening.asking)
        opening.answer(asItIs = false)
        assertFalse(opening.asking)
        assertEquals(0, asItIs)

        // Chosen, the file goes as it is.
        opening.start()
        advanceUntilIdle()
        opening.answer(asItIs = true)
        assertEquals(1, asItIs)

        // No copy could be made: it says so, and nothing goes as it is.
        result = Shared.Failed
        opening.start()
        advanceUntilIdle()
        assertTrue(opening.failed)
        assertFalse(opening.asking)
        opening.dismissFailure()
        assertFalse(opening.failed)
        assertEquals(1, asItIs)
    }

    @Test
    fun `a tap while the copy is made starts nothing more`() = runTest {
        val copied = CompletableDeferred<Shared>()
        var opened = 0

        val opening = OpeningElsewhere(
            this,
            open = {
                opened += 1
                copied.await()
            },
            openAsItIs = {},
        )

        opening.start()
        runCurrent()
        assertTrue(opening.busy)
        opening.start()
        runCurrent()
        assertEquals(1, opened)

        copied.complete(Shared.Clean)
        advanceUntilIdle()
        assertFalse(opening.busy)
    }
}
