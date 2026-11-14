package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The map's text among a zip's markdown files: picked as zips of one map always were, and never holding them all.
 * Each is at most a map's text, 8 MiB, and a zip may hold over a hundred of them: read whole, they'd want gigabytes.
 */
class BundleTextTest {
    private val files: File = Files.createTempDirectory("orbit-bundle-text").toFile()

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    /** What each markdown file offered did: read, or let go by unread. */
    private val done = mutableListOf<String>()

    /** Offers each file in turn, as the zip has them, its text its name's, and returns the text picked. */
    private fun picked(vararg names: String): String? {
        val text = BundleText()

        for (name in names) {
            text.offer(name, read = { done += "read $name"; "text of $name" }, skip = { done += "skip $name" })
            assertTrue("Held ${text.held} after $name", text.held <= 1)
        }

        return text.markdown
    }

    @Test
    fun `the map's own map md comes first, then the first in a folder, then the only one`() {
        assertEquals("text of map.md", picked("notes.md", "trip/map.md", "map.md", "other/map.md"))
        assertEquals("text of trip/map.md", picked("notes.md", "trip/map.md", "other/map.md", "more.md"))
        assertEquals("text of notes.md", picked("notes.md"))
        assertNull(picked("notes.md", "more.md"))
        assertNull(picked())
    }

    @Test
    fun `a name the zip has twice is read as its last`() {
        assertEquals("text of notes.md", picked("notes.md", "notes.md"))
        assertEquals(listOf("read notes.md", "read notes.md"), done)
    }

    @Test
    fun `a file that can no longer be picked is never read`() {
        assertEquals("text of map.md", picked("map.md", "a.md", "b/map.md", "c.md"))
        assertEquals(listOf("read map.md", "skip a.md", "skip b/map.md", "skip c.md"), done)
        done.clear()

        assertNull(picked("a.md", "b.md", "c.md", "d.md"))
        assertEquals(listOf("read a.md", "skip b.md", "skip c.md", "skip d.md"), done)
    }

    @Test
    fun `a zip of many big markdown files opens its map without holding them all`() = runTest {
        val repository = MapRepository(File(files, "maps"), File(files, "trash"), NO_STARTERS, FileWords(Locale.US)).also { it.load() }
        // Eighty notes as big as a map's text may be, around the map's own: whole, they'd want over a gigabyte of the
        // test's 512 MB, as they would of a phone's.
        val page = "- ${"a".repeat(1022)}\n".toByteArray()

        val zip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for (note in 1..80) {
                    zip.putNextEntry(ZipEntry("notes/$note.md"))
                    repeat((MAP_TEXT_LIMIT / page.size).toInt()) { zip.write(page) }
                    zip.closeEntry()
                }

                zip.putNextEntry(ZipEntry("map.md"))
                zip.write("# Lisbon\n- Tram 28\n".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val bundle = repository.readBundle(ByteArrayInputStream(zip))

        assertEquals("# Lisbon\n- Tram 28\n", bundle?.markdown)
    }

    private companion object {
        val NO_STARTERS = object : StarterMaps {
            override fun samples() = emptyList<String>()

            override fun welcome(): StarterMap? = null
        }
    }
}
