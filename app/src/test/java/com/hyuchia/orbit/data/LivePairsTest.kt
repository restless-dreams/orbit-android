package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class LivePairsTest {
    private val noon = LocalDateTime.of(2022, 11, 19, 11, 14, 36)
    private val id = "C0633EC8-48F1-43C9-84DC-23C480CA1FA4"

    private fun still(name: String?, identifier: String? = null, taken: LocalDateTime? = null) = LiveHalf(name ?: "still", name, still = StillFacts(identifier, taken))

    private fun movie(name: String?, identifier: String? = null, created: LocalDateTime? = null, live: Boolean = true) =
        LiveHalf(name ?: "movie", name, video = LiveVideo(identifier, live, created, null))

    @Test
    fun `the id joins a still and its movie first, whatever their names`() {
        val matched = LivePairs.match(listOf(still("photo.heic", id), still("IMG_0911.JPG")), listOf(movie("IMG_0911.MOV", id)))
        assertEquals(listOf("photo.heic" to "IMG_0911.MOV"), matched.pairs)
        assertNull(matched.offer)
    }

    @Test
    fun `without ids, the same name joins them, in any case`() {
        val matched = LivePairs.match(listOf(still("IMG_0911.jpg"), still("IMG_0912.JPG")), listOf(movie("img_0912.mov"), movie("IMG_0911.MOV")))
        assertEquals(setOf("IMG_0911.jpg" to "IMG_0911.MOV", "IMG_0912.JPG" to "img_0912.mov"), matched.pairs.toSet())
    }

    @Test
    fun `without ids or names, the nearest capture times within a few seconds join them`() {
        val matched = LivePairs.match(
            listOf(still("a.jpg", taken = noon.plusSeconds(1)), still("b.jpg", taken = noon.plusSeconds(40))),
            listOf(movie("x.mov", created = noon), movie("y.mov", created = noon.plusSeconds(39))),
        )
        assertEquals(setOf("a.jpg" to "x.mov", "b.jpg" to "y.mov"), matched.pairs.toSet())
        // Too far apart: not the same moment.
        assertEquals(emptyList<Pair<String, String>>(), LivePairs.match(listOf(still("a.jpg", taken = noon.plusSeconds(10))), listOf(movie("x.mov", created = noon))).pairs)
    }

    @Test
    fun `a still and a movie whose ids differ are never joined, by their names or their times, nor offered to be`() {
        val other = "9F0B3C11-2D7A-4E55-8B2E-6A1C0D9E7F21"
        // The same name and the same moment, but each carries its own live photo's id: halves of two of them.
        val apart = LivePairs.match(listOf(still("IMG_0911.HEIC", id, taken = noon)), listOf(movie("IMG_0911.MOV", other, created = noon)))
        assertEquals(emptyList<Pair<String, String>>(), apart.pairs)
        assertNull(apart.offer)

        // The movie goes on to the still its time says, which lost its id on the way, and the other still stays alone.
        val crossed = LivePairs.match(
            listOf(still("IMG_0911.HEIC", id, taken = noon.plusSeconds(30)), still("IMG_0912.JPG", taken = noon)),
            listOf(movie("IMG_0911.MOV", other, created = noon)),
        )
        assertEquals(listOf("IMG_0912.JPG" to "IMG_0911.MOV"), crossed.pairs)

        // A movie without an id says nothing against a still's, and the name joins them.
        assertEquals(listOf("IMG_0911.HEIC" to "IMG_0911.MOV"), LivePairs.match(listOf(still("IMG_0911.HEIC", id)), listOf(movie("IMG_0911.MOV"))).pairs)
    }

    @Test
    fun `one picture and one live photo's movie that nothing ties together are offered, and a plain video never is`() {
        val left = LivePairs.match(listOf(still("photo.jpg")), listOf(movie("clip.mov")))
        assertEquals(emptyList<Pair<String, String>>(), left.pairs)
        assertEquals("photo.jpg" to "clip.mov", left.offer)
        val plain = LivePairs.match(listOf(still("photo.jpg")), listOf(movie("clip.mp4", live = false)))
        assertNull(plain.offer)
        // Two pictures and one movie: nothing to guess from.
        assertNull(LivePairs.match(listOf(still("a.jpg"), still("b.jpg")), listOf(movie("clip.mov"))).offer)
    }
}
