package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** Maps following their files out of the folder: many at once wait for a yes, and the trash keeps them until the note is read. */
class FolderSyncRemovalsTest {
    private val root: File = Files.createTempDirectory("orbit-folder-removals").toFile()
    private val words = FileWords(Locale.US)
    private var now = 1_800_000_000_000L

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val folder = TestFolder()
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))

    private fun repository() = MapRepository(File(root, "maps"), File(root, "trash"), starters, words, clock = { now })

    private val maps = repository()
    private val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "folder-notes.json"), words, TestEditor(words)) { folder }

    private suspend fun start() {
        maps.load()
        settings.update { it.copy(syncFolder = "memory") }
    }

    /** Five maps in the folder, and one more that stays: then the five files go, and a sync long enough after agrees. */
    private suspend fun fiveGone(): List<String> {
        val gone = (1..5).map { maps.add("# Gone $it\n- Idea\n") }
        maps.add("# Stays\n- Idea\n")
        sync.syncNow()
        (1..5).forEach { folder.delete("Gone $it.md") }
        sync.syncNow()

        for (id in gone) {
            val record = maps.summary(id)!!.metadata.sync!!
            maps.setSyncRecord(id, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        }

        sync.syncNow()
        return gone
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `many maps whose files left at once wait for a yes before they go to the trash`() = runTest {
        start()
        val gone = fiveGone()
        assertEquals(gone.toSet(), sync.state.value.held.toSet())
        assertEquals(6, maps.maps.value!!.size)

        sync.removeHeld(sync.state.value.held)
        sync.syncNow()
        assertEquals(listOf("Stays"), maps.maps.value!!.map { it.title })
        assertEquals((1..5).map { "Gone $it" }.toSet(), sync.state.value.notes.removed.toSet())
        assertEquals(emptyList<String>(), sync.state.value.held)
    }

    @Test
    fun `maps kept when their files left are written back to the folder`() = runTest {
        start()
        fiveGone()
        sync.keepHeld(sync.state.value.held)
        assertEquals(emptyList<String>(), sync.state.value.held)

        sync.syncNow()
        assertEquals(6, maps.maps.value!!.size)
        assertEquals((1..5).map { "Gone $it.md" }.toSet() + "Stays.md", folder.texts.keys)
        assertEquals(emptyList<String>(), sync.state.value.held)
        assertTrue(maps.trash.value.isEmpty())
    }

    @Test
    fun `a few maps whose files left go to the trash on their own, and the trash keeps them until the note is read`() = runTest {
        start()
        val gone = (1..MOST_REMOVALS).map { maps.add("# Gone $it\n- Idea\n") }
        maps.add("# Stays\n- Idea\n")
        sync.syncNow()
        (1..MOST_REMOVALS).forEach { folder.delete("Gone $it.md") }
        sync.syncNow()

        for (id in gone) {
            val record = maps.summary(id)!!.metadata.sync!!
            maps.setSyncRecord(id, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        }

        sync.syncNow()
        assertEquals(emptyList<String>(), sync.state.value.held)
        assertEquals(listOf("Stays"), maps.maps.value!!.map { it.title })
        assertEquals(MOST_REMOVALS, sync.state.value.notes.removed.size)

        // Restored, a map is the trash's no more, and goes as any map does when deleted again.
        val trashed = maps.trash.value.first { it.title == "Gone 1" }
        val back = maps.restore(trashed)!!
        maps.delete(back)
        now += (TRASH_DAYS + 1) * 24 * 60 * 60 * 1000L
        // Seen past its thirty days at one start, and gone at one a day later.
        repository().load()
        now += 24 * 60 * 60 * 1000L
        val later = repository().also { it.load() }
        assertEquals((2..MOST_REMOVALS).map { "Gone $it" }.toSet(), later.trash.value.map { it.title }.toSet())
    }

    @Test
    fun `a map the folder sent to the trash stays past thirty days until its note is read`() = runTest {
        start()
        val diary = maps.add("# Diary\n- Idea\n")
        maps.add("# Stays\n- Idea\n")
        sync.syncNow()
        folder.delete("Diary.md")
        sync.syncNow()
        val record = maps.summary(diary)!!.metadata.sync!!
        maps.setSyncRecord(diary, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        sync.syncNow()
        assertEquals(listOf("Diary"), maps.trash.value.map { it.title })

        now += (TRASH_DAYS + 1) * 24 * 60 * 60 * 1000L
        repository().load()
        now += 24 * 60 * 60 * 1000L
        val later = repository().also { it.load() }
        assertEquals(listOf("Diary"), later.trash.value.map { it.title })

        // Read now: thirty days from now, it goes as anything in the trash does, seen past them at two starts a day apart.
        later.seeFolderRemovals()
        assertEquals(listOf("Diary"), repository().also { it.load() }.trash.value.map { it.title })
        now += (TRASH_DAYS + 1) * 24 * 60 * 60 * 1000L
        assertEquals(listOf("Diary"), repository().also { it.load() }.trash.value.map { it.title })
        now += 24 * 60 * 60 * 1000L
        assertTrue(repository().also { it.load() }.trash.value.isEmpty())
    }
}
