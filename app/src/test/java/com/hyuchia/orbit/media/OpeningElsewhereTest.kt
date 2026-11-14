package com.hyuchia.orbit.media

import com.hyuchia.orbit.data.FileStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player's "Open in another app" for a map's video: what it asks, and when. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningElsewhereTest {
    /** The video as Orbit found it couldn't clean it, and the one put in its place since. */
    private val asked = FileStamp("/maps/trip/media/video-1.mp4", 1_000, 1, 1, "1:1")

    private val another = FileStamp("/maps/trip/media/video-1.mp4", 1_000, 2, 2, "1:2")

    @Test
    fun `a clean copy opens without a question, and one Orbit can't clean waits for the person's answer`() = runTest {
        var result: Shared = Shared.Clean
        var opened = 0
        val asItIs = mutableListOf<FileStamp>()

        val opening = OpeningElsewhere(
            this,
            open = {
                opened += 1
                result
            },
            openAsItIs = { stamp ->
                asItIs += stamp
                Shared.Clean
            },
        )

        opening.start()
        advanceUntilIdle()
        assertEquals(1, opened)
        assertNull(opening.asking)
        assertFalse(opening.busy)

        // Cancelled, nothing goes.
        result = Shared.NotClean(asked)
        opening.start()
        advanceUntilIdle()
        assertEquals(AskedVideo(asked), opening.asking)
        opening.answer(asItIs = false)
        assertNull(opening.asking)
        assertEquals(emptyList<FileStamp>(), asItIs)

        // Chosen, the very file asked about goes as it is.
        opening.start()
        advanceUntilIdle()
        opening.answer(asItIs = true)
        advanceUntilIdle()
        assertEquals(listOf(asked), asItIs)

        // No copy could be made: it says so, and nothing goes as it is.
        result = Shared.Failed
        opening.start()
        advanceUntilIdle()
        assertTrue(opening.failed)
        assertNull(opening.asking)
        opening.dismissFailure()
        assertFalse(opening.failed)
        assertEquals(listOf(asked), asItIs)
    }

    @Test
    fun `a video replaced after the person answered is asked about again, as it is now, and only once`() = runTest {
        var now = asked
        val asItIs = mutableListOf<FileStamp>()

        val opening = OpeningElsewhere(
            this,
            open = { Shared.NotClean(now) },
            openAsItIs = { stamp ->
                asItIs += stamp
                // The copy is of the very file asked about: another put in its place doesn't go.
                if (stamp == now) Shared.Clean else Shared.Changed
            },
        )

        opening.start()
        advanceUntilIdle()

        // Another video takes its place, as a synced folder brings a newer one, before the person answers.
        now = another
        opening.answer(asItIs = true)
        advanceUntilIdle()
        assertEquals(AskedVideo(another, changed = true), opening.asking)
        assertFalse(opening.busy)

        // And changing again once asked again, it says it couldn't.
        now = asked
        opening.answer(asItIs = true)
        advanceUntilIdle()
        assertNull(opening.asking)
        assertTrue(opening.failed)
        assertEquals(listOf(asked, another), asItIs)
    }

    @Test
    fun `a tap while the copy is made starts nothing more, clean or as it is`() = runTest {
        val copied = CompletableDeferred<Shared>()
        val copiedAsItIs = CompletableDeferred<Shared>()
        var opened = 0
        var asItIs = 0

        val opening = OpeningElsewhere(
            this,
            open = {
                opened += 1
                copied.await()
            },
            openAsItIs = {
                asItIs += 1
                copiedAsItIs.await()
            },
        )

        opening.start()
        runCurrent()
        assertTrue(opening.busy)
        opening.start()
        runCurrent()
        assertEquals(1, opened)

        copied.complete(Shared.NotClean(asked))
        advanceUntilIdle()
        assertFalse(opening.busy)

        // Its copy as it is takes a while too: another tap meanwhile makes no second one.
        opening.answer(asItIs = true)
        runCurrent()
        assertTrue(opening.busy)
        opening.start()
        runCurrent()
        assertEquals(1, opened)
        assertEquals(1, asItIs)
        copiedAsItIs.complete(Shared.Clean)
        advanceUntilIdle()
        assertFalse(opening.busy)
    }
}
