package com.hyuchia.orbit.media

import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryTest {
    private fun node(id: String, parent: String? = null, image: String? = null, video: String? = null, icon: String? = null) =
        MapNode(id = id, text = id.replaceFirstChar { it.uppercase() }, parentId = parent, icon = icon, image = image?.let { Media(it) }, video = video?.let { Media(it) })

    // As the page reports a map: the top, then each idea before the ideas under it.
    private val trip = listOf(
        node("trip", image = "media/cover.jpg"),
        node("alfama", "trip", image = "media/alfama.jpg", video = "media/video-alfama.mp4"),
        node("tram", "alfama", video = "https://youtu.be/aqz-KE-bpKQ"),
        node("belem", "trip"),
        node("nata", "belem", image = "https://example.com/nata.jpg", icon = "🥧"),
    )

    @Test
    fun `the gallery reads the map in its outline's order, each picture before its idea's video`() {
        val gallery = galleryOf(trip, "Untitled")
        assertEquals(
            listOf("trip/Picture", "alfama/Picture", "alfama/Video", "tram/Video", "nata/Picture"),
            gallery.map { it.key },
        )
        assertEquals("🥧 Nata", gallery.last().label)
    }

    @Test
    fun `an idea without text is named as the app names untitled ideas`() {
        val nameless = node("nameless", image = "media/nameless.jpg").copy(text = " ")
        assertEquals("Sin título", galleryOf(listOf(nameless), "Sin título").single().label)
    }

    @Test
    fun `ideas without a picture or video, or with an empty address, have no page`() {
        val gallery = galleryOf(listOf(node("empty", image = " "), node("belem")), "Untitled")
        assertEquals(emptyList<GalleryItem>(), gallery)
    }

    @Test
    fun `the viewer opens at the picture or video asked for`() {
        val gallery = galleryOf(trip, "Untitled")
        assertEquals(1, gallery.indexOf("alfama", MediaKind.Picture))
        assertEquals(2, gallery.indexOf("alfama", MediaKind.Video))
        // An idea with only a video opens at it, whichever was asked for.
        assertEquals(3, gallery.indexOf("tram", MediaKind.Picture))
        // An idea that isn't there opens the gallery at its start.
        assertEquals(0, gallery.indexOf("gone", MediaKind.Picture))
    }

    @Test
    fun `a page describes itself by its picture's description, else its caption, else by its idea in the app's words`() {
        val described = GalleryItem("belem", MediaKind.Picture, Media("media/belem.jpg", alt = "The tower at sunset", caption = "Belém"), "Belém")
        assertEquals("The tower at sunset", described.givenDescription())
        assertEquals("Belém", described.copy(media = Media("media/belem.jpg", caption = "Belém")).givenDescription())
        assertNull(described.copy(media = Media("media/belem.jpg")).givenDescription())
        // A video's description isn't shown, so only its caption describes it.
        assertNull(described.copy(kind = MediaKind.Video, media = Media("media/belem.mp4", alt = "The tower at sunset")).givenDescription())
    }
}
