package com.hyuchia.orbit.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SharingTest {
    private val directory: File = Files.createTempDirectory("orbit-exports").toFile()
    private val now = 1_000_000_000_000L
    private val hour = 60 * 60 * 1000L

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `an export shared a moment ago stays for the app it went to, and day-old ones go`() {
        val shared = exportDestination(directory, "Trip.zip", now)
        shared.writeText("shared")
        shared.parentFile!!.setLastModified(now)
        val old = File(directory, "old")
        old.mkdirs()
        File(old, "Plan.md").writeText("old")
        old.setLastModified(now - 25 * hour)
        // Written before each export had a folder of its own.
        val older = File(directory, "Walk.md")
        older.writeText("older")
        older.setLastModified(now - 48 * hour)

        val next = exportDestination(directory, "Trip.zip", now + hour)

        assertEquals("shared", shared.readText())
        assertEquals("Trip.zip", next.name)
        assertNotEquals(shared.parentFile, next.parentFile)
        assertFalse(old.exists())
        assertFalse(older.exists())
    }

    @Test
    fun `only exports and the media in a map's folder are handed out to other apps`() {
        val exports = File(directory, "cache/exports")
        val maps = File(directory, "files/maps")
        File(maps, "trip/media").mkdirs()
        File(maps, "trip/map.md").writeText("# Trip")

        assertTrue(sharesFile(File(exports, "1234/Trip.zip"), exports, maps))
        assertTrue(sharesFile(File(maps, "trip/media/video-1.mp4"), exports, maps))

        // A map's text, its details and its versions never are, nor what ".." or a link reaches from the media folder.
        assertFalse(sharesFile(File(maps, "trip/map.md"), exports, maps))
        assertFalse(sharesFile(File(maps, "trip/meta.json"), exports, maps))
        assertFalse(sharesFile(File(maps, "trip/media/../map.md"), exports, maps))
        assertFalse(sharesFile(File(maps, "trip/history/media/1.md"), exports, maps))
        val link = Files.createSymbolicLink(File(maps, "trip/media/video-2.mp4").toPath(), File(maps, "trip/map.md").toPath()).toFile()
        assertFalse(sharesFile(link, exports, maps))

        // Nor the folders themselves, or a map in the trash.
        assertFalse(sharesFile(exports, exports, maps))
        assertFalse(sharesFile(File(directory, "files/trash/trip/media/video-1.mp4"), exports, maps))
    }
}
