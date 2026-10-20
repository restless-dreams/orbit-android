package com.hyuchia.orbit.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
}
