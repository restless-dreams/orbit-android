package com.hyuchia.orbit.page

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapFilesTest {
    @Test
    fun `pages get the pictures and videos in a map's media folder`() {
        assertEquals(MapMedia("lisbon", "alfama.jpg"), mapMedia("/maps/lisbon/media/alfama.jpg"))
        assertEquals(MapMedia("trip-2", "clip.mp4"), mapMedia("/maps/trip-2/media/clip.mp4"))
        assertEquals(MapMedia("weekend in lisbon", "poster.webp"), mapMedia("/maps/weekend in lisbon/media/poster.webp"))
    }

    @Test
    fun `nothing else of a map's, nor anything outside its media folder`() {
        listOf(
            // A map's text, its details and its history.
            "/maps/lisbon/map.md",
            "/maps/lisbon/meta.json",
            "/maps/lisbon/media",
            "/maps/lisbon/media/",
            "/maps/.layout.json",
            "/maps/lisbon/history/1760000000000.md",
            // Steps out, and folders deeper in.
            "/maps/lisbon/media/../map.md",
            "/maps/../files/settings/media/a.jpg",
            "/maps/./media/a.jpg",
            "/maps//media/a.jpg",
            "/maps/lisbon/media/old/a.jpg",
            // What a picture or video can't be: a page, a script, a name that hides.
            "/maps/lisbon/media/page.html",
            "/maps/lisbon/media/script.js",
            "/maps/lisbon/media/.hidden.jpg",
            "/maps/lisbon/media/notes.md",
            "/assets/map/index.html",
            "maps/lisbon/media/alfama.jpg",
        ).forEach { assertNull(it, mapMedia(it)) }
    }

    @Test
    fun `a picture that went or can't be read is missing, rather than stopping the app`() {
        val folder = Files.createTempDirectory("orbit-media").toFile()

        try {
            val picture = File(folder, "alfama.jpg")
            picture.writeBytes(byteArrayOf(1, 2, 3))
            openMedia(picture)!!.use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
            picture.setReadable(false)
            assertNull(openMedia(picture))
            assertNull(openMedia(File(folder, "gone.jpg")))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a base address names its map`() {
        assertEquals("lisbon", mapIdOf(mapBaseUrl("lisbon")))
        assertEquals("lisbon", mapIdOf("https://$PAGE_HOST/maps/lisbon/media/alfama.jpg"))
        assertNull(mapIdOf(null))
        assertNull(mapIdOf("https://$PAGE_HOST/maps/"))
        assertNull(mapIdOf("https://$PAGE_HOST/maps/../x/"))
        assertNull(mapIdOf("https://example.com/maps/lisbon/"))
        assertNull(mapIdOf("http://$PAGE_HOST/maps/lisbon/"))
    }
}
