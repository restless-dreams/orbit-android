package com.hyuchia.orbit.nearby

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ByteRangeTest {
    private val length = 10_000L

    @Test
    fun oneRangeIsServedAsAsked() {
        assertEquals(ByteRange.Part(0, 99, length), byteRange("bytes=0-99", length))
        assertEquals(ByteRange.Part(0, 9_999, length), byteRange("bytes=0-", length))
        assertEquals(ByteRange.Part(9_990, 9_999, length), byteRange("bytes=9990-", length))
        // A range past the end ends with the file.
        assertEquals(ByteRange.Part(5_000, 9_999, length), byteRange("bytes=5000-20000", length))
        // The last bytes, however many the file has.
        assertEquals(ByteRange.Part(9_995, 9_999, length), byteRange("bytes=-5", length))
        assertEquals(ByteRange.Part(0, 9_999, length), byteRange("bytes=-50000", length))
        // As browsers write it, and with room around the parts.
        assertEquals(ByteRange.Part(100, 199, length), byteRange(" Bytes = 100 - 199 ", length))
        assertEquals("bytes 100-199/10000", ByteRange.Part(100, 199, length).contentRange)
        assertEquals(100L, ByteRange.Part(100, 199, length).count)
    }

    @Test
    fun aRangePastTheEndCantBeServed() {
        assertEquals(ByteRange.Unsatisfiable(length), byteRange("bytes=10000-", length))
        assertEquals(ByteRange.Unsatisfiable(length), byteRange("bytes=20000-30000", length))
        assertEquals(ByteRange.Unsatisfiable(length), byteRange("bytes=-0", length))
        assertEquals(ByteRange.Unsatisfiable(0), byteRange("bytes=0-", 0))
        assertEquals("bytes */10000", ByteRange.Unsatisfiable(length).contentRange)
    }

    @Test
    fun anythingElseGetsTheWholeFile() {
        listOf(
            null, "", "bytes=", "bytes=-", "bytes=abc-", "bytes=1-abc", "bytes=5-3", "items=0-99", "0-99", "bytes=0-99,200-299",
            "bytes=1234567890123456789-", "bytes=+1-2", "bytes=0x10-",
        ).forEach { header ->
            assertEquals("$header", ByteRange.Whole, byteRange(header, length))
        }

        // A browser that kept an older copy: the server names no versions, so it gets the whole file.
        assertEquals(ByteRange.Whole, byteRange("bytes=0-99", length, ifRange = "\"v1\""))
    }

    @Test
    fun aRangeIsReadFromTheFileAsItGoes() {
        val file = Files.createTempFile("orbit-range", ".mp4").toFile()

        try {
            file.writeBytes(ByteArray(1_000) { (it % 251).toByte() })
            val bytes = openRange(file, 990, 20).use { it.readBytes() }
            // Only what the file has, from where it was asked.
            assertEquals((990 until 1_000).map { (it % 251).toByte() }, bytes.toList())
            assertEquals((100 until 110).map { (it % 251).toByte() }, openRange(file, 100, 10).use { it.readBytes() }.toList())
        } finally {
            file.delete()
        }
    }
}
