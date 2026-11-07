package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

/**
 * This device's own write to the folder that it hasn't seen there whole yet,
 * as when reading it back fails: the next sync takes the file for what this
 * device wrote, not for a change from elsewhere to keep twice, nor for a map
 * of its own.
 */
class FolderSyncWritingTest {
    private val root: File = Files.createTempDirectory("orbit-folder-writing").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val folder = TestFolder()
    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))
    private val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "folder-notes.json"), words, TestEditor(words)) { folder }

    private suspend fun start() {
        maps.load()
        settings.update { it.copy(syncFolder = "memory") }
    }

    /** A sync whose writes reach the folder, but whose reading them back fails, as a folder's app can. */
    private suspend fun syncReadingBackNothing() {
        folder.afterWrite = { name -> folder.failReads[name] = IOException("Couldn't read $name") }

        try {
            sync.syncNow()
        } finally {
            folder.afterWrite = null
            folder.failReads.clear()
        }
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a map changed again after its write wasn't read back writes its file, rather than keeping both`() = runTest {
        start()
        val trip = maps.add("# Trip\n- Lisbon\n")
        sync.syncNow()

        maps.write(trip, "# Trip\n- Lisbon\n- Porto\n")
        syncReadingBackNothing()
        assertEquals("# Trip\n- Lisbon\n- Porto\n", folder.texts["Trip.md"])
        assertNotNull(maps.summary(trip)!!.metadata.sync!!.writing)

        // Changed again, and not by adding to what was written: the file is still this device's write, and takes the new text.
        maps.write(trip, "# Trip\n- Porto\n")
        sync.syncNow()
        assertEquals(mapOf("Trip.md" to "# Trip\n- Porto\n"), folder.texts)
        assertEquals(listOf("Trip"), maps.maps.value!!.map { it.title })
        assertEquals(emptyList<String>(), sync.state.value.notes.keptBoth)
    }

    @Test
    fun `a file a write just made, not read back, is its map's rather than a map of its own`() = runTest {
        start()
        val trip = maps.add("# Trip\n- Lisbon\n")
        syncReadingBackNothing()
        assertEquals(mapOf("Trip.md" to "# Trip\n- Lisbon\n"), folder.texts)

        maps.write(trip, "# Trip\n- Porto\n")
        sync.syncNow()
        assertEquals(mapOf("Trip.md" to "# Trip\n- Porto\n"), folder.texts)
        assertEquals(listOf("Trip"), maps.maps.value!!.map { it.title })

        // And from then on, it's that map's file like any other.
        sync.syncNow()
        assertEquals("Trip.md", maps.summary(trip)!!.metadata.sync!!.file)
        assertNull(maps.summary(trip)!!.metadata.sync!!.writing)
    }
}
