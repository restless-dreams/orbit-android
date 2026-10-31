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
import java.nio.file.Files
import java.util.Locale

/** A folder's app that throws what it likes: a sync goes on past it, keeps its notes, and says when a problem lasts. */
class FolderSyncProviderTest {
    private val root: File = Files.createTempDirectory("orbit-folder-provider").toFile()
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

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a step the folder's app fails with an IllegalStateException doesn't stop the others`() = runTest {
        start()
        maps.add("# Alpha\n- One\n")
        maps.add("# Beta\n- Two\n")
        folder.failWrites["Alpha.md"] = IllegalStateException("Failed to open document")
        sync.syncNow()
        assertEquals(setOf("Beta.md"), folder.texts.keys)
        assertNotNull(sync.state.value.problem)

        folder.failWrites.clear()
        sync.syncNow()
        assertEquals(setOf("Alpha.md", "Beta.md"), folder.texts.keys)
        assertNull(sync.state.value.problem)
    }

    @Test
    fun `a sync that stops partway keeps the notes of what it did before then`() = runTest {
        start()
        val trip = maps.add("# Trip\n- Lisbon\n")
        sync.syncNow()
        maps.write(trip, "# Trip\n- Here\n")
        folder.change("Trip.md", "# Trip\n- There\n")
        // Keeping both writes the folder's copy as a new file first, then this map's text: that write stops the sync.
        folder.failWrites["Trip.md"] = OutOfMemoryError("Failed to allocate")
        sync.syncNow()
        assertNotNull(sync.state.value.problem)
        assertEquals(listOf("Trip (changed elsewhere)"), sync.state.value.notes.keptBoth)

        // The next sync writes this map's text, rather than keeping both a second time.
        folder.failWrites.clear()
        sync.syncNow()
        assertEquals(1, maps.maps.value!!.count { it.title == "Trip (changed elsewhere)" })
        assertEquals("# Trip\n- Here\n", folder.texts["Trip.md"])
    }

    @Test
    fun `a problem that lasts more than one sync is counted, and a sync that goes well clears it`() = runTest {
        start()
        maps.add("# Trip\n- Lisbon\n")
        folder.failListing = IllegalStateException("Failed to query")
        sync.syncNow()
        assertEquals(1, sync.state.value.failures)
        sync.syncNow()
        assertEquals(2, sync.state.value.failures)

        folder.failListing = null
        sync.syncNow()
        assertEquals(0, sync.state.value.failures)
        assertNull(sync.state.value.problem)
    }
}
