package com.hyuchia.orbit.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FilesTest {
    private val folder: File = Files.createTempDirectory("orbit-files").toFile()

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a file is written whole, with nothing left beside it`() {
        val file = File(folder, "map.md")
        writeAtomically(file, "# One\n")
        writeAtomically(file, "# Two\n")
        assertEquals("# Two\n", file.readText())
        assertEquals(listOf("map.md"), folder.list()!!.toList())
    }

    @Test
    fun `a write that can't take the file's place leaves it as it was, and says so`() {
        val file = File(folder, "map.md")
        file.mkdirs()
        File(file, "inside").writeText("kept")

        try {
            writeAtomically(file, "# New\n")
            fail("The write should have failed")
        } catch (expected: IOException) {
            // Nothing written over the file in place, and no partial file left.
        }

        assertEquals("kept", File(file, "inside").readText())
        assertFalse(File(folder, ".map.md.partial").exists())
    }

    @Test
    fun `text in UTF-8, with or without a byte order mark, reads as it was written`() {
        assertEquals("Café ☕ 東京", decodeText("Café ☕ 東京".toByteArray()))
        assertEquals("Café", decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Café".toByteArray()))
        assertEquals("Café", decodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Café".toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun `text from an older editor keeps every character`() {
        // "Café – résumé" in Windows-1252, and a byte it leaves undefined.
        val bytes = byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte(), 0x20, 0x96.toByte(), 0x20, 0x72, 0xE9.toByte(), 0x73, 0x75, 0x6D, 0xE9.toByte(), 0x81.toByte())
        val text = decodeText(bytes)
        assertEquals("Café – résumé\u0081", text)
        assertTrue('�' !in text)
    }
}
