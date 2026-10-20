package com.hyuchia.orbit.page

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapPicturesTest {
    private val cache: File = Files.createTempDirectory("orbit-cache").toFile()

    @After
    fun cleanUp() {
        cache.deleteRecursively()
    }

    @Test
    fun `a new thumbnail takes the place of the map's older ones`() {
        val thumbnails = File(cache, "thumbnails")
        thumbnails.mkdirs()
        File(thumbnails, "trip~old~1.webp").writeBytes(ByteArray(3))
        File(thumbnails, "walk~same~1.webp").writeBytes(ByteArray(3))
        val file = File(thumbnails, "trip~new~1.webp")

        assertTrue(keepThumbnail(thumbnails, "trip", file, byteArrayOf(1, 2)))
        assertArrayEquals(byteArrayOf(1, 2), file.readBytes())
        assertFalse(File(thumbnails, "trip~old~1.webp").exists())
        assertTrue(File(thumbnails, "walk~same~1.webp").exists())
    }

    @Test
    fun `a thumbnail that can't be kept is none, rather than a crash`() {
        // Where the thumbnails go, something else is in the way, as a full cache would be.
        val thumbnails = File(cache, "thumbnails")
        thumbnails.writeText("not a folder")

        assertFalse(keepThumbnail(thumbnails, "trip", File(thumbnails, "trip~new~1.webp"), byteArrayOf(1, 2)))
    }
}
