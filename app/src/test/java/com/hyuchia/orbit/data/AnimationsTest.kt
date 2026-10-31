package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream

class AnimationsTest {
    private fun file(name: String) = File("src/test/resources/animations/$name")

    private fun extensionOf(name: String) = file(name).inputStream().buffered().use(::animationExtension)

    private fun extensionOf(bytes: ByteArray) = animationExtension(ByteArrayInputStream(bytes))

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** A GIF of `frames` frames of a pixel each, with a loop and a frame's timing as animators write them. */
    private fun gif(frames: Int, version: String = "GIF89a", end: Boolean = true): ByteArray {
        // A pixel's screen, with a table of two colors.
        val start = version.toByteArray(Charsets.ISO_8859_1) + bytes(1, 0, 1, 0, 0x80, 0, 0) + bytes(0, 0, 0, 255, 255, 255)
        val loop = bytes(0x21, 0xFF, 11) + "NETSCAPE2.0".toByteArray(Charsets.ISO_8859_1) + bytes(3, 1, 0, 0, 0)
        val timing = bytes(0x21, 0xF9, 4, 0, 10, 0, 0, 0)
        val frame = bytes(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4C, 0x01, 0)
        val body = (1..frames).fold(ByteArray(0)) { all, _ -> all + timing + frame }
        return start + loop + body + if (end) bytes(0x3B) else ByteArray(0)
    }

    @Test
    fun `a GIF of more than one frame and a WebP that says it moves are animations`() {
        assertEquals("gif", extensionOf("orbit.gif"))
        assertEquals("webp", extensionOf("orbit.webp"))
        assertEquals("gif", extensionOf(gif(frames = 2)))
        assertEquals("gif", extensionOf(gif(frames = 30, version = "GIF87a")))
    }

    @Test
    fun `a GIF or WebP that stands still isn't one, nor is anything else`() {
        assertNull(extensionOf("still.gif"))
        assertNull(extensionOf("still.webp"))
        assertNull(extensionOf("still-alpha.webp"))
        assertNull(extensionOf(gif(frames = 1)))
        assertNull(extensionOf(bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 13, 10, 26, 10)))
        assertNull(extensionOf(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertNull(extensionOf(ByteArray(0)))
    }

    @Test
    fun `bytes that end before a second frame can't tell, and aren't one`() {
        val two = gif(frames = 2, end = false)
        assertNull(extensionOf(two.copyOf(two.size - 16)))
        assertNull(extensionOf(gif(frames = 1, end = false)))
        assertNull(extensionOf("GIF89a".toByteArray(Charsets.ISO_8859_1)))
        assertNull(extensionOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8X".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun `a GIF is read only up to its second frame, however long it is`() {
        val read = CountingInput(file("orbit.gif").inputStream())
        assertEquals("gif", animationExtension(read))
        assertTrue(read.count < file("orbit.gif").length() / 4)
    }

    /** Counts the bytes read or skipped through it. */
    private class CountingInput(input: InputStream) : FilterInputStream(input) {
        var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }
}
