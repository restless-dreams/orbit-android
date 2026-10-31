package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSummaryTest {
    @Test
    fun `a map's files are counted by kind, with each video's poster as part of it`() {
        val media = MediaSummary.of(
            mapOf(
                "picture-1.jpg" to 1_000L,
                "picture-2.png" to 2_000L,
                "video-1.mp4" to 30_000L,
                "video-1.jpg" to 500L,
                "video-2.webm" to 20_000L,
            ),
        )

        assertEquals(MediaSummary(pictures = 2, videos = 2, audio = 0, bytes = 53_500L), media)
        assertEquals(4, media.count)
    }

    @Test
    fun `files the map's text plays as audio count as audio, whatever their extension`() {
        val media = MediaSummary.of(mapOf("memo-1.mp4" to 8_000L, "picture-1.jpg" to 1_000L), audio = setOf("memo-1.mp4"))
        assertEquals(MediaSummary(pictures = 1, videos = 0, audio = 1, bytes = 9_000L), media)
    }

    @Test
    fun `a live photo's motion counts with its picture, and a motion without one is a video`() {
        val media = MediaSummary.of(
            mapOf(
                "picture-1.jpg" to 1_000L,
                "picture-1-motion.mp4" to 5_000L,
                "picture-2.heic" to 2_000L,
                "picture-2-motion.mov" to 6_000L,
                "video-1.mp4" to 30_000L,
            ),
        )

        // Two live photos: two pictures, their motions part of them, and every byte counted.
        assertEquals(MediaSummary(pictures = 2, videos = 1, audio = 0, bytes = 44_000L, motions = 2), media)
        assertEquals(3, media.count)

        // A motion whose picture isn't there is a video of its own.
        assertEquals(MediaSummary(videos = 1, bytes = 5_000L), MediaSummary.of(mapOf("picture-1-motion.mp4" to 5_000L)))
    }

    @Test
    fun `a map with nothing but text carries nothing`() {
        assertTrue(MediaSummary.of(emptyMap()).isEmpty)
        // Files of no kind a map keeps aren't counted.
        assertTrue(MediaSummary.of(mapOf("notes.txt" to 10L)).isEmpty)
    }
}
