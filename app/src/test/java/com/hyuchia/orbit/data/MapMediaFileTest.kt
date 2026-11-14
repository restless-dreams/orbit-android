package com.hyuchia.orbit.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The one way everything that leaves the phone finds a map's file: in its own media folder, never through a link. */
class MapMediaFileTest {
    private val root: File = Files.createTempDirectory("orbit-files").toFile()

    /** The app's own storage, as on many phones: its folder reached through a link (`/data/user/0` is `/data/data`). */
    private val data = File(root, "data").apply { mkdirs() }

    private val user = File(root, "user0").also { Files.createSymbolicLink(it.toPath(), data.toPath()) }
    private val maps = File(user, "maps")
    private val trip = File(maps, "trip")
    private val media = File(trip, MEDIA_DIRECTORY).apply { mkdirs() }

    /** A file outside the map, as another map's, or anything a link could lead to. */
    private val outside = File(root, "outside/another-map.jpg").apply {
        parentFile!!.mkdirs()
        writeText("another map's picture")
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a map's own file is found, as its storage names it, though the app's storage is reached through a link`() {
        File(media, "picture-1.jpg").writeText("the picture")

        val found = mapMediaFile(trip, "picture-1.jpg")
        assertEquals(File(data, "maps/trip/media/picture-1.jpg").canonicalFile, found)
        assertEquals("the picture", found!!.readText())
    }

    @Test
    fun `a link never leads a way out of the phone elsewhere, whether it's the file, the media folder or the map's folder`() {
        // The file itself a link, as the review's probes made one: to another map's picture, or to the map's own text.
        Files.createSymbolicLink(File(media, "beach.jpg").toPath(), outside.toPath())
        File(trip, "map.md").writeText("# Trip")
        Files.createSymbolicLink(File(media, "text.jpg").toPath(), File(trip, "map.md").toPath())
        assertNull(mapMediaFile(trip, "beach.jpg"))
        assertNull(mapMediaFile(trip, "text.jpg"))

        // The media folder a link.
        val linkedMedia = File(maps, "linked-media").apply { mkdirs() }
        Files.createSymbolicLink(File(linkedMedia, MEDIA_DIRECTORY).toPath(), outside.parentFile!!.toPath())
        assertNull(mapMediaFile(linkedMedia, "another-map.jpg"))

        // The map's folder a link.
        Files.createSymbolicLink(File(maps, "linked-map").toPath(), File(root, "outside").toPath())
        File(root, "outside/media").mkdirs()
        File(root, "outside/media/picture.jpg").writeText("elsewhere")
        assertNull(mapMediaFile(File(maps, "linked-map"), "picture.jpg"))
    }

    @Test
    fun `only a file with a name Orbit gives one is found`() {
        File(media, "folder.jpg").mkdirs()
        File(media, ".hidden.jpg").writeText("hidden")
        File(trip, "map.md").writeText("# Trip")

        assertNull(mapMediaFile(trip, "folder.jpg"))
        assertNull(mapMediaFile(trip, ".hidden.jpg"))
        assertNull(mapMediaFile(trip, "../map.md"))
        assertNull(mapMediaFile(trip, "sub/picture.jpg"))
        assertNull(mapMediaFile(trip, "missing.jpg"))
    }
}
