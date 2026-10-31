package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** What a sync leaves behind when a write doesn't finish, and what it says about a map another device can only show. */
class FolderSyncLeftoversTest {
    private val root: File = Files.createTempDirectory("orbit-folder-leftovers").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val folder = TestFolder()
    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))
    private val phone = FolderDevice("phone-2a6c0e4e", "Pixel 8", FolderDevice.PHONE, "0.4.0", 5, 1, listOf("jpg", "mp4", "png"), 0)
    private val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "folder-notes.json"), words, TestEditor(words), device = { phone }) { folder }

    private suspend fun start() {
        maps.load()
        settings.update { it.copy(syncFolder = "memory") }
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a new file that keeps only the start of its map goes, rather than become a map of its own`() = runTest {
        start()
        val plan = maps.add("# Plan\n- One\n- Two\n")
        // The folder's app keeps only the start of each write.
        folder.afterWrite = { name -> folder.change(name, folder.texts.getValue(name).take(8)) }
        sync.syncNow()
        assertEquals(emptySet<String>(), folder.texts.keys)
        assertNotNull(sync.state.value.problem)

        folder.afterWrite = null
        sync.syncNow()
        assertEquals(listOf("# Plan\n- One\n- Two\n"), folder.texts.values.toList())
        assertEquals(listOf(plan), maps.maps.value!!.map { it.id })
    }

    @Test
    fun `a map written for a newer format than another device here reads is in the notes`() = runTest {
        start()
        val tablet = FolderDevice("tablet-9d35f1f6", "Pixel Tablet", FolderDevice.TABLET, "0.4.0", 5, 1, listOf("jpg", "mp4", "png"), System.currentTimeMillis())
        folder.devices["${tablet.id}.json"] = FolderDeviceRecords.encode(tablet)
        maps.add("# Plan\n- One\n")
        maps.add("---\nschemaVersion: 2\n---\n# Future\n- One\n")
        sync.syncNow()
        assertEquals(listOf("Future"), sync.state.value.notes.newer)
        assertEquals(setOf("Plan.md", "Future.md"), folder.texts.keys)
    }
}
