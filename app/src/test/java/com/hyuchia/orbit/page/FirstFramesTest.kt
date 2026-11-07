package com.hyuchia.orbit.page

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FirstFramesTest {
    private val folder: File = Files.createTempDirectory("orbit-first-frames").toFile()
    private val media = File(folder, "media").apply { mkdirs() }
    private val made = mutableListOf<String>()

    /** Frames made up from the file's name, counting each one made. */
    private val frames = FirstFrames(File(folder, "frames")) { file ->
        made += file.name
        "frame of ${file.name}".toByteArray()
    }

    private fun picture(name: String, from: String = name) = File("src/test/resources/animations/$from").copyTo(File(media, name), overwrite = true)

    /** What the map's page gets of a picture made in place of a map's file. */
    private fun StandIn.bytes(): ByteArray = open()!!.use { it.readBytes() }

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    @Test
    fun `a HEIC or HEIF picture goes as a JPEG the page draws, made once, and one that can't be read goes as it is`() {
        val drawn = mutableListOf<String>()
        val renditions = FirstFrames(File(folder, "frames"), drawn = { file -> drawn += file.name; "jpeg of ${file.name}".toByteArray() }) { error("Not an animation") }
        val heic = File("src/test/resources/live/iphone-live.heic").copyTo(File(media, "picture-1.heic"))
        val heif = File("src/test/resources/live/iphone-live.heic").copyTo(File(media, "picture-2.HEIF"))

        assertArrayEquals("jpeg of picture-1.heic".toByteArray(), renditions.of(heic)!!.readBytes())
        assertEquals("jpg", renditions.of(heic)!!.extension)
        assertNotNull(renditions.of(heif))
        assertEquals(listOf("picture-1.heic", "picture-2.HEIF"), drawn)
        assertNull(FirstFrames(File(folder, "unread"), drawn = { null }) { null }.of(heic))
    }

    @Test
    fun `an animated GIF or WebP goes as its first frame, made once`() {
        val gif = picture("orbit.gif")
        val webp = picture("orbit.webp")

        assertArrayEquals("frame of orbit.gif".toByteArray(), frames.of(gif)!!.readBytes())
        assertArrayEquals("frame of orbit.webp".toByteArray(), frames.of(webp)!!.readBytes())
        assertEquals(frames.of(gif), frames.of(gif))
        // The map's page gets the same file the cache keeps.
        assertArrayEquals(frames.of(gif)!!.readBytes(), frames.standIn(gif)!!.bytes())
        assertEquals(listOf("orbit.gif", "orbit.webp"), made)
    }

    @Test
    fun `pictures that stand still go as they are`() {
        assertNull(frames.of(picture("still.gif")))
        assertNull(frames.of(picture("still.webp")))
        assertNull(frames.of(picture("still-alpha.webp")))
        assertNull(frames.of(File(media, "alfama.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) }))
        // A JPEG called a GIF is no animation either.
        assertNull(frames.of(File(media, "named.gif").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) }))
        assertEquals(emptyList<String>(), made)
    }

    @Test
    fun `an animation that changes gets a new first frame, and one that went has none`() {
        val gif = picture("pictured.gif", from = "orbit.gif")
        val first = frames.of(gif)
        assertNotNull(first)

        // Another animation under the same name: a different file, and a frame of its own.
        picture("pictured.gif", from = "orbit.webp").setLastModified(gif.lastModified() + 2_000)
        val second = frames.of(gif)
        assertNotNull(second)
        assertNotEquals(first, second)
        assertEquals(2, made.size)

        gif.delete()
        assertNull(frames.of(gif))
    }

    @Test
    fun `a first frame or a HEIC picture's JPEG the cache can't keep, full or unwritable, still goes to the map's page, made each time`() {
        // Where the cache's folder would be, there's a file: nothing can be written there, as on a full disk.
        val cache = File(folder, "full").apply { writeText("") }
        val drawn = mutableListOf<String>()
        val full = FirstFrames(cache, drawn = { file -> drawn += file.name; "jpeg of ${file.name}".toByteArray() }) { file -> made += file.name; "frame of ${file.name}".toByteArray() }
        val gif = picture("orbit.gif")
        val heic = File("src/test/resources/live/iphone-live.heic").copyTo(File(media, "picture-1.heic"))

        val frame = full.standIn(gif)!!
        assertArrayEquals("frame of orbit.gif".toByteArray(), frame.bytes())
        assertEquals("png", frame.name.substringAfterLast('.'))
        assertEquals("frame of orbit.gif".length.toLong(), frame.length)
        assertArrayEquals("jpeg of picture-1.heic".toByteArray(), full.standIn(heic)!!.bytes())
        // Nothing was kept, so each is made again when it's asked for again.
        assertArrayEquals("frame of orbit.gif".toByteArray(), full.standIn(gif)!!.bytes())
        assertEquals(listOf("orbit.gif", "orbit.gif"), made)
        assertEquals(listOf("picture-1.heic"), drawn)
        assertTrue(cache.isFile)

        // What keeps a file, as a web page's pictures do, hears that it couldn't, and sends the picture as it is.
        try {
            full.of(heic)
            fail("A picture that can't be kept has no file")
        } catch (unkept: IOException) {
            // As WebPage's pictures expect.
        }
    }

    @Test
    fun `an animation whose frame can't be read goes as it is`() {
        val unreadable = FirstFrames(File(folder, "frames")) { null }
        assertNull(unreadable.of(picture("orbit.gif")))
    }
}
