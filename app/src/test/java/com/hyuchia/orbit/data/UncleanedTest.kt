package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UncleanedTest {
    private val all = listOf(
        "picture-1.jpg",
        "picture-2.jpg",
        "picture-2-motion.mp4",
        "picture-3.jpg",
        "picture-3-motion.mp4",
        "video-tram.mp4",
        "video-tram.jpg",
        "memo-intro.mp4",
        "sound-fado.m4a",
    )

    @Test
    fun `files that can't go clean are known by the kind people know them as`() {
        val files = Uncleaned.of(
            listOf("picture-1.jpg", "picture-2.jpg", "picture-2-motion.mp4", "picture-3-motion.mp4", "video-tram.mp4", "memo-intro.mp4", "sound-fado.m4a"),
            all,
            sounds = setOf("memo-intro.mp4"),
        )

        assertEquals(setOf("picture-1.jpg", "picture-2.jpg"), files.pictures)
        assertEquals(setOf("video-tram.mp4"), files.videos)
        // A voice memo's .mp4 is a sound, as the map's text plays it.
        assertEquals(setOf("memo-intro.mp4", "sound-fado.m4a"), files.sounds)
        assertEquals(setOf("picture-2-motion.mp4", "picture-3-motion.mp4"), files.motions)
        // A live photo whose picture and motion are both here is one; picture-3's motion is here alone.
        assertEquals(1, files.livePhotos)
        assertEquals(7, files.names.size)
    }

    @Test
    fun `each way out carries its own of them`() {
        val files = Uncleaned(pictures = setOf("picture-1.jpg"), videos = setOf("video-tram.mp4"), sounds = setOf("memo-intro.mp4"), motions = setOf("picture-3-motion.mp4"))

        // A page carries pictures, sounds and motions; paper, a drawing and a TV pictures; browsers all but the sounds.
        assertEquals(setOf("picture-1.jpg", "memo-intro.mp4", "picture-3-motion.mp4"), files.inPage.names)
        assertEquals(setOf("picture-1.jpg"), files.inPictures.names)
        assertEquals(setOf("picture-1.jpg", "video-tram.mp4", "picture-3-motion.mp4"), files.inRoom.names)
        assertEquals(setOf("video-tram.mp4"), files.only(setOf("video-tram.mp4", "elsewhere.jpg")).names)
        assertTrue(Uncleaned().isEmpty)
    }
}
