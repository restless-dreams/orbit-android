package com.hyuchia.orbit.map

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.MAP_MEDIA_LIMIT
import com.hyuchia.orbit.data.MEDIA_FILE_LIMIT
import com.hyuchia.orbit.data.SmallerVideo
import com.hyuchia.orbit.data.VideoFacts
import com.hyuchia.orbit.media.VideoMaker
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** How big videos from the phone come into a map: offered smaller, kept, or as they are where the phone can't. */
@RunWith(AndroidJUnit4::class)
class BigVideosTest {
    /** A phone's videos, a minute of 1080p each, at a bitrate that makes them `size` bytes; it makes them smaller when it `can`. */
    private class Phone(private val can: Boolean = true) : VideoMaker {
        val asked = mutableListOf<Uri>()

        override suspend fun facts(uri: Uri, size: Long): VideoFacts {
            asked += uri
            return VideoFacts(60_000, 1920, 1080, size, frameRate = 30f, bitrate = size * 8 / 60, sound = true)
        }

        override fun canMake(width: Int, height: Int): Boolean = can

        override suspend fun make(uri: Uri, plan: SmallerVideo, output: File, onProgress: (Float) -> Unit) = Unit
    }

    private val offers = SmallerOffers()
    private val video = Uri.parse("content://gallery/files/waves")
    private val other = Uri.parse("content://gallery/files/concert")

    /** Answers the next offer with `answer`, giving the offer it was. */
    private suspend fun answering(answer: SmallerAnswer): SmallerOffer {
        val offer = withTimeout(5_000) { offers.offer.filterNotNull().first() }
        offers.answer(answer)
        return offer
    }

    @Test
    fun `a video up to about 50 MB comes as it is, without a word, nor its headers read`() = runBlocking {
        val phone = Phone()
        assertEquals(VideoWay.AsItIs, BigVideos({ phone }, offers).way(video, 50_000_000, mapMedia = 0))
        assertTrue(phone.asked.isEmpty())
        assertNull(offers.offer.value)
    }

    @Test
    fun `a big video is offered smaller, with its size and about what it would be, and comes as the person says`() = runBlocking {
        val videos = BigVideos({ Phone() }, offers)

        for ((answer, expected) in listOf(SmallerAnswer.AsTheyAre to VideoWay.AsItIs, SmallerAnswer.Neither to VideoWay.Neither)) {
            val way = async(start = CoroutineStart.UNDISPATCHED) { videos.way(video, 127_500_000, mapMedia = 0) }
            val offer = answering(answer)
            assertEquals(SmallerOffer(videos = 1, size = 127_500_000, estimate = 62_668_800, keepable = true), offer)
            assertEquals(expected, way.await())
            assertNull(offers.offer.value)
        }

        val way = async(start = CoroutineStart.UNDISPATCHED) { videos.way(video, 127_500_000, mapMedia = 0) }
        answering(SmallerAnswer.Smaller)
        val smaller = way.await() as VideoWay.Smaller
        assertEquals(127_500_000L, smaller.size)
        assertEquals(62_668_800L, smaller.plan.estimate)
    }

    @Test
    fun `a video too big for a map as it is is offered only smaller`() = runBlocking {
        val videos = BigVideos({ Phone() }, offers)

        // Past what a file may be.
        val past = async(start = CoroutineStart.UNDISPATCHED) { videos.way(video, MEDIA_FILE_LIMIT + 1, mapMedia = 0) }
        assertEquals(false, answering(SmallerAnswer.Smaller).keepable)
        assertTrue(past.await() is VideoWay.Smaller)

        // Past what's left of the map's room, but not once it's smaller.
        val full = async(start = CoroutineStart.UNDISPATCHED) { videos.way(video, 127_500_000, mapMedia = MAP_MEDIA_LIMIT - 100_000_000) }
        assertEquals(false, answering(SmallerAnswer.Neither).keepable)
        assertEquals(VideoWay.Neither, full.await())
    }

    @Test
    fun `a video that wouldn't fit even smaller isn't offered, and its copy says why`() = runBlocking {
        assertEquals(VideoWay.AsItIs, BigVideos({ Phone() }, offers).way(video, 127_500_000, mapMedia = MAP_MEDIA_LIMIT - 10_000_000))
        assertNull(offers.offer.value)
    }

    @Test
    fun `a big video this phone can't make smaller comes as it is, and says so, without asking`() = runBlocking {
        assertEquals(VideoWay.AsItIsHere, BigVideos({ Phone(can = false) }, offers).way(video, 127_500_000, mapMedia = 0))
        assertNull(offers.offer.value)
    }

    @Test
    fun `whoever adds a video hears when the person is about to be asked, and only then`() = runBlocking {
        var asked = 0
        val videos = BigVideos({ Phone() }, offers)
        assertEquals(VideoWay.AsItIs, videos.way(video, 50_000_000, mapMedia = 0) { asked++ })
        assertEquals(VideoWay.AsItIsHere, BigVideos({ Phone(can = false) }, offers).way(video, 127_500_000, mapMedia = 0) { asked++ })
        assertEquals(0, asked)

        val way = async(start = CoroutineStart.UNDISPATCHED) { videos.way(video, 127_500_000, mapMedia = 0) { asked++ } }
        answering(SmallerAnswer.Smaller)
        assertEquals(1, asked)
        assertTrue(way.await() is VideoWay.Smaller)
    }

    @Test
    fun `big videos let go together are offered smaller once, all together`() = runBlocking {
        val videos = BigVideos({ Phone() }, offers)
        val big = listOfNotNull(videos.big(video, 127_500_000, mapMedia = 0), videos.big(other, 255_000_000, mapMedia = 0), videos.big(Uri.parse("content://gallery/files/small"), 1_000_000, mapMedia = 0))
        assertEquals(2, big.size)

        val ways = async(start = CoroutineStart.UNDISPATCHED) { videos.ways(big, mapMedia = 0) }
        val offer = answering(SmallerAnswer.Smaller)
        assertEquals(2, offer.videos)
        assertEquals(382_500_000L, offer.size)
        // The second is past what a file may be: kept as they are isn't offered for them.
        assertEquals(false, offer.keepable)
        assertEquals(setOf(video, other), ways.await().filterValues { it is VideoWay.Smaller }.keys)
    }

    @Test
    fun `an offer asked while another waits answers that one with neither`() = runBlocking {
        val videos = BigVideos({ Phone() }, offers)
        val first = async(start = CoroutineStart.UNDISPATCHED) { videos.way(video, 127_500_000, mapMedia = 0) }
        withTimeout(5_000) { offers.offer.filterNotNull().first() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { videos.way(other, 127_500_000, mapMedia = 0) }

        assertEquals(VideoWay.Neither, first.await())
        answering(SmallerAnswer.AsTheyAre)
        assertEquals(VideoWay.AsItIs, second.await())
    }
}
