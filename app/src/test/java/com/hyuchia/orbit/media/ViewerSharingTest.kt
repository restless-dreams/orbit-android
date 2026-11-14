package com.hyuchia.orbit.media

import com.hyuchia.orbit.data.FileStamp
import com.hyuchia.orbit.page.Media
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
import java.io.File

/** Sharing from the full-screen viewer: what it asks, about which file, and what it says when nothing could go. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerSharingTest {
    private val ferry = GalleryItem("ferry", MediaKind.Picture, Media("media/ferry.heic"), "The ferry")
    private val memo = GalleryItem("kickoff", MediaKind.Sound, Media("media/memo-1.mp4"), "Kickoff")

    /** The ferry's picture as Orbit found it couldn't clean it, and the one put in its place since. */
    private val asked = FileStamp("/maps/trip/media/ferry.heic", 1_000, 1, 1, "1:1")

    private val another = FileStamp("/maps/trip/media/ferry.heic", 1_000, 2, 2, "1:2")

    @Test
    fun `a file Orbit can't clean is asked about as the one it is, whatever page is on screen once the answer comes`() = runTest {
        val cleaning = CompletableDeferred<Shared>()
        val sharedAsItIs = mutableListOf<Triple<GalleryItem, File, FileStamp>>()

        val sharing = ViewerSharing(
            this,
            share = { _, _ -> cleaning.await() },
            shareAsItIs = { item, file, stamp ->
                sharedAsItIs += Triple(item, file, stamp)
                Shared.Clean
            },
        )

        // The ferry's picture is shared, and the person swipes on to the memo while its copy is made.
        sharing.start(ferry, File("media/ferry.heic"))
        runCurrent()
        assertTrue(sharing.busy)
        cleaning.complete(Shared.NotClean(asked))
        advanceUntilIdle()

        // Asked about the ferry's picture, by its own kind and name, never the memo's, as it was then.
        assertEquals(UncleanFile(ferry, File("media/ferry.heic"), asked), sharing.asking)
        assertFalse(sharing.busy)

        sharing.answer(asItIs = true)
        advanceUntilIdle()
        assertNull(sharing.asking)
        assertEquals(listOf(Triple(ferry, File("media/ferry.heic"), asked)), sharedAsItIs)
    }

    @Test
    fun `a copy that couldn't be made says so, clean or as it is, and cancelled nothing goes`() = runTest {
        var result: Shared = Shared.Failed

        val sharing = ViewerSharing(
            this,
            share = { _, _ -> result },
            shareAsItIs = { _, _, _ -> Shared.Failed },
        )

        sharing.start(memo, File("media/memo-1.mp4"))
        advanceUntilIdle()
        assertEquals(MediaKind.Sound, sharing.failed)
        assertNull(sharing.asking)
        sharing.dismissFailure()
        assertNull(sharing.failed)

        // Not clean: cancelled, nothing goes and nothing's said.
        result = Shared.NotClean(asked)
        sharing.start(memo, File("media/memo-1.mp4"))
        advanceUntilIdle()
        sharing.answer(asItIs = false)
        advanceUntilIdle()
        assertNull(sharing.failed)

        // Chosen to go as it is, but no copy could be made of it: said so too.
        sharing.start(memo, File("media/memo-1.mp4"))
        advanceUntilIdle()
        sharing.answer(asItIs = true)
        advanceUntilIdle()
        assertEquals(MediaKind.Sound, sharing.failed)
    }

    @Test
    fun `sharing a picture again while its copy as it is is made makes no second copy`() = runTest {
        val copying = CompletableDeferred<Shared>()
        var shares = 0
        var copies = 0

        val sharing = ViewerSharing(
            this,
            share = { _, _ ->
                shares += 1
                Shared.NotClean(asked)
            },
            shareAsItIs = { _, _, _ ->
                copies += 1
                copying.await()
            },
        )

        sharing.start(ferry, File("media/ferry.heic"))
        advanceUntilIdle()
        sharing.answer(asItIs = true)
        runCurrent()

        // Its copy as it is takes a while, and the person taps Share again, twice.
        assertTrue(sharing.busy)
        sharing.start(ferry, File("media/ferry.heic"))
        sharing.start(ferry, File("media/ferry.heic"))
        advanceUntilIdle()
        assertEquals(1, shares)
        assertEquals(1, copies)
        assertNull(sharing.asking)

        // Once it's made, Share works again.
        copying.complete(Shared.Clean)
        advanceUntilIdle()
        assertFalse(sharing.busy)
        sharing.start(ferry, File("media/ferry.heic"))
        advanceUntilIdle()
        assertEquals(2, shares)
    }

    @Test
    fun `a file replaced after the person answered is asked about again, as it is now, or goes clean`() = runTest {
        var now: Shared = Shared.NotClean(asked)
        val sharedAsItIs = mutableListOf<FileStamp>()

        val sharing = ViewerSharing(
            this,
            share = { _, _ -> now },
            shareAsItIs = { _, _, stamp ->
                sharedAsItIs += stamp
                // What goes is the very file asked about: another put in its place doesn't.
                if (now == Shared.NotClean(stamp)) Shared.Clean else Shared.Changed
            },
        )

        sharing.start(ferry, File("media/ferry.heic"))
        advanceUntilIdle()

        // Another picture Orbit can't clean takes its place before the person answers: asked about again, saying so.
        now = Shared.NotClean(another)
        sharing.answer(asItIs = true)
        advanceUntilIdle()
        assertEquals(UncleanFile(ferry, File("media/ferry.heic"), another, changed = true), sharing.asking)
        assertNull(sharing.failed)

        // Answered for it, that one goes.
        sharing.answer(asItIs = true)
        advanceUntilIdle()
        assertEquals(listOf(asked, another), sharedAsItIs)
        assertNull(sharing.asking)
        assertNull(sharing.failed)
    }
}
