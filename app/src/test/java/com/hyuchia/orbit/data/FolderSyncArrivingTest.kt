package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** Files still arriving, too big to be maps, or changing while a sync writes; and a sync that fails as badly as it can. */
class FolderSyncArrivingTest {
    private val root: File = Files.createTempDirectory("orbit-folder-arriving").toFile()
    private val words = FileWords(Locale.US)
    private var clock = 0L

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val folder = TestFolder()
    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))
    private val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "folder-notes.json"), words, TestEditor(words), elapsed = { clock }) { folder }

    private suspend fun start() {
        maps.load()
        settings.update { it.copy(syncFolder = "memory") }
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a file that stops partway through a character isn't taken until it's whole`() = runTest {
        start()
        val trip = maps.add("# Weekend in Lisbon\n- Pastéis de nata\n")
        sync.syncNow()
        val file = folder.texts.keys.single()
        val longer = "# Weekend in Lisbon\n- Pastéis de nata\n\n## 🧳 To pack\n- Shoes\n".toByteArray()

        // Cut inside 🧳: decoded whole, it would read as other characters.
        folder.arriving(file, longer.copyOf(longer.indexOf(0xF0.toByte()) + 2))
        sync.syncNow()
        assertEquals("# Weekend in Lisbon\n- Pastéis de nata\n", maps.read(trip))
        assertNull(sync.state.value.problem)

        folder.change(file, longer.decodeToString())
        sync.syncNow()
        assertEquals(longer.decodeToString(), maps.read(trip))
    }

    @Test
    fun `a file that stays cut partway through a character is taken as it is, once it has settled`() = runTest {
        start()
        val bytes = "# Café\n- Grano".toByteArray() + byteArrayOf(0xE9.toByte())
        folder.arriving("Café.md", bytes)
        sync.syncNow()
        assertEquals(emptyList<String>(), maps.maps.value!!.map { it.title })

        clock += SETTLING.inWholeMilliseconds
        sync.syncNow()
        assertEquals(listOf("Café"), maps.maps.value!!.map { it.title })
    }

    @Test
    fun `a file too big to be a map stays only in the folder, and the notes say so`() = runTest {
        folder.change("Big notes.md", "# Big notes\n")
        folder.listedSizes["Big notes.md"] = MAP_TEXT_LIMIT + 1
        // Choosing the folder, there's nothing in it to ask about taking in.
        assertEquals(0, sync.inspect("memory").second)

        start()
        sync.syncNow()
        assertEquals(emptyList<String>(), maps.maps.value!!.map { it.title })
        assertEquals(listOf("Big notes.md"), sync.state.value.notes.large)
        assertNull(sync.state.value.problem)
    }

    @Test
    fun `a file that changed after the folder was listed isn't written over`() = runTest {
        start()
        val trip = maps.add("# Trip\n- Lisbon\n")
        sync.syncNow()
        val file = folder.texts.keys.single()
        maps.write(trip, "# Trip\n- Lisbon\n- Here\n")
        folder.afterListing = { folder.change(file, "# Trip\n- Lisbon\n- There\n") }
        sync.syncNow()
        folder.afterListing = null
        assertEquals("# Trip\n- Lisbon\n- There\n", folder.texts[file])

        // The next sync sees both changes, and keeps both.
        sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Here\n", maps.read(trip))
        assertTrue(maps.maps.value!!.any { it.title == "Trip (changed elsewhere)" })
    }

    @Test
    fun `a file someone else wrote right after this device isn't written over again`() = runTest {
        start()
        val trip = maps.add("# Trip\n- Lisbon\n")
        sync.syncNow()
        val file = folder.texts.keys.single()
        maps.write(trip, "# Trip\n- Lisbon\n- Here\n")
        folder.afterWrite = { name -> folder.afterWrite = null; folder.change(name, "# Trip\n- Elsewhere\n") }
        sync.syncNow()
        assertEquals("# Trip\n- Elsewhere\n", folder.texts[file])
        assertNotNull(sync.state.value.problem)

        sync.syncNow()
        assertTrue(maps.maps.value!!.any { it.title == "Trip (changed elsewhere)" })
        assertEquals("# Trip\n- Lisbon\n- Here\n", folder.texts[file])
    }

    @Test
    fun `a sync that fails as badly as it can ends alone, and the next one goes on`() = runTest {
        start()
        val trip = maps.add("# Trip\n- Lisbon\n")
        folder.failListing = OutOfMemoryError("Failed to allocate a 314572812 byte allocation")
        sync.syncNow()
        assertNotNull(sync.state.value.problem)

        folder.failListing = null
        sync.syncNow()
        assertNull(sync.state.value.problem)
        assertEquals("# Trip\n- Lisbon\n", folder.texts.values.single())
        assertNotNull(maps.summary(trip)!!.metadata.sync)
    }
}
