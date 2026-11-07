package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How big videos would be made smaller, from what their headers say. */
class SmallerVideosTest {
    /** A minute of a phone's own video: `width` by `height` at `megabits` a second, with its sound. */
    private fun minute(width: Int, height: Int, megabits: Double, frames: Float = 30f, sound: Boolean = true): VideoFacts {
        val bitrate = (megabits * 1_000_000).toLong()
        return VideoFacts(60_000, width, height, size = bitrate * 60 / 8, frameRate = frames, bitrate = bitrate, sound = sound)
    }

    @Test
    fun `a phone's 1080p keeps its size, at a bitrate under half its own, and comes to about half as much`() {
        val facts = minute(1920, 1080, 17.0)
        val plan = SmallerVideos.plan(facts)!!

        assertNull(plan.shortSide)
        assertEquals(1920, plan.width)
        assertEquals(1080, plan.height)
        assertEquals(8_000_000, plan.videoBitrate)
        assertTrue(plan.sound)
        // A minute of 8 Mbps and its sound: about 62.7 MB, of 127.5.
        assertEquals(62_668_800L, plan.estimate)
        assertTrue(plan.estimate < facts.size / 2)
    }

    @Test
    fun `a 4K video becomes 1080p on its shorter side, whichever way up it was recorded`() {
        val wide = SmallerVideos.plan(minute(3840, 2160, 45.0))!!
        assertEquals(SmallerVideos.SHORT_SIDE, wide.shortSide)
        assertEquals(1920 to 1080, wide.width to wide.height)

        // Upright, as phones keep some: encoded on its side all the same, which way up it plays kept apart.
        val upright = SmallerVideos.plan(minute(2160, 3840, 45.0))!!
        assertEquals(1920 to 1080, upright.width to upright.height)
        assertEquals(wide.videoBitrate, upright.videoBitrate)
    }

    @Test
    fun `a smaller picture takes fewer bits, and more frames more, within their bounds`() {
        val hd = SmallerVideos.plan(minute(1280, 720, 10.0))!!
        assertNull(hd.shortSide)
        assertTrue(hd.videoBitrate in 3_000_000..4_000_000)

        // Sixty frames a second take twice the bits, up to the most a video made smaller has.
        assertEquals(12_000_000, SmallerVideos.plan(minute(1920, 1080, 30.0, frames = 60f))!!.videoBitrate)
        assertEquals(12_000_000, SmallerVideos.plan(minute(1920, 1080, 60.0, frames = 120f))!!.videoBitrate)
    }

    @Test
    fun `a video without sound is planned without it`() {
        val plan = SmallerVideos.plan(minute(1920, 1080, 17.0, sound = false))!!
        assertEquals(false, plan.sound)
        assertEquals(61_200_000L, plan.estimate)
    }

    @Test
    fun `a video at a low bitrate already isn't offered, since it wouldn't come out much smaller`() {
        // 1080p at 4 Mbps: made smaller it would be over 70% of what it is.
        assertNull(SmallerVideos.plan(minute(1920, 1080, 4.0)))
        // Its file's own bitrate, worked out from its size when its headers don't say.
        assertNull(SmallerVideos.plan(minute(1920, 1080, 4.0).copy(bitrate = null)))
        assertTrue(SmallerVideos.plan(minute(1920, 1080, 17.0).copy(bitrate = null)) != null)
    }

    @Test
    fun `a video whose headers don't say how long or big it is isn't planned`() {
        assertNull(SmallerVideos.plan(minute(1920, 1080, 17.0).copy(durationMilliseconds = 0)))
        assertNull(SmallerVideos.plan(minute(1920, 1080, 17.0).copy(width = 0)))
        assertNull(SmallerVideos.plan(minute(1920, 1080, 17.0).copy(size = 0)))
    }

    @Test
    fun `videos are offered smaller from about half a minute of a phone's 1080p`() {
        assertEquals(50_000_000L, SmallerVideos.OFFER_FROM)
        assertEquals(1080, SmallerVideos.SHORT_SIDE)
    }
}
