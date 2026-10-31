package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** A private map's other copies in the folder: a sync app's conflict copies, its file back after it was deleted, and the folder's version kept beside it. */
class FolderSyncPrivateCopiesTest {
    private val root: File = Files.createTempDirectory("orbit-folder-private-copies").toFile()
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

    private fun map(title: String) = maps.maps.value!!.single { it.title == title }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `sync apps' conflict copies are known by their names`() {
        assertEquals("Twin A.md", conflictOriginal("Twin A.sync-conflict-20261022-101010-ABCDEFG.md"))
        assertEquals("Twin A.md", conflictOriginal("Twin A.sync-conflict-20261022-101010.md"))
        assertEquals("Twin A.md", conflictOriginal("Twin A (conflicted copy).md"))
        assertEquals("Twin A.md", conflictOriginal("Twin A (Diana's conflicted copy 2026-10-22).md"))
        assertEquals("Twin A.md", conflictOriginal("Twin A (conflicted copy 2026-10-22 101010).md"))
        assertEquals("Viaje.md", conflictOriginal("Viaje (copia en conflicto de Diana).md"))
        assertNull(conflictOriginal("Twin A.md"))
        assertNull(conflictOriginal("Twin A (2).md"))
        assertNull(conflictOriginal("Notes (draft).md"))
    }

    @Test
    fun `a sync app's conflict copy of a private map's file comes in private`() = runTest {
        start()
        val twin = maps.add("# Twin A\n- Secret\n")
        maps.setPrivate(twin, true)
        maps.setCover(twin, LockedCover(icon = "🔑", name = "Keys"))
        val open = maps.add("# Open\n- Shared\n")
        sync.syncNow()

        folder.change("Twin A.sync-conflict-20261022-101010-ABCDEFG.md", "# Twin A\n- Secret, changed on the laptop\n")
        folder.change("Open (conflicted copy).md", "# Open\n- Shared, changed on the laptop\n")
        sync.syncNow()

        val copies = maps.maps.value!!.filter { it.id != twin && it.id != open }
        assertEquals(2, copies.size)
        assertTrue(copies.single { it.title == "Twin A" }.metadata.isPrivate)
        assertFalse(copies.single { it.title == "Open" }.metadata.isPrivate)
        // Its cover says it's the folder's version, so the two tell apart while they're locked.
        assertEquals(LockedCover(icon = "🔑", name = "Keys (changed elsewhere)"), copies.single { it.title == "Twin A" }.metadata.cover)
    }

    @Test
    fun `a private map's file back in the folder after the map was deleted here comes back private`() = runTest {
        start()
        val diary = maps.add("# Diary\n- Today\n")
        maps.setPrivate(diary, true)
        sync.syncNow()
        val file = folder.texts.keys.single()

        maps.delete(diary)
        // Another device changed the file before this one's sync deleted it: it stays, and comes back as a map.
        folder.change(file, "# Diary\n- Today\n- Tonight\n")
        sync.syncNow()

        assertTrue(map("Diary").metadata.isPrivate)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a deleted private map's file stays out while the folder's deletions can't be read, and comes back private once they can`() = runTest {
        start()
        val diary = maps.add("# Diary\n- Today\n")
        maps.setPrivate(diary, true)
        val trip = maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val file = maps.summary(diary)!!.metadata.sync!!.file

        maps.delete(diary)
        // Another device changed the file before this one's sync deleted it: it stays, to come back as a map.
        folder.change(file, "# Diary\n- Today\n- Tonight\n")
        val deletions = File(root, "folder-deletions.json")
        val recorded = deletions.readText()
        deletions.setReadable(false)

        try {
            // Not taken for no deletions: the diary doesn't come back as a map like any other.
            sync.syncNow()
            assertTrue(maps.maps.value!!.none { it.title == "Diary" })
            // Nor written over by the next deletion, which waits: its map stays.
            assertNull(maps.delete(trip))
            assertTrue(maps.exists(trip))
        } finally {
            deletions.setReadable(true)
        }

        assertEquals(recorded, deletions.readText())
        sync.syncNow()
        assertTrue(map("Diary").metadata.isPrivate)
    }

    @Test
    fun `a deleted map's record whose private this version can't read brings its file back private`() = runTest {
        start()
        val diary = maps.add("# Diary\n- Today\n")
        maps.setPrivate(diary, true)
        val trip = maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val diaryFile = maps.summary(diary)!!.metadata.sync!!.file
        val tripFile = maps.summary(trip)!!.metadata.sync!!.file

        maps.delete(diary)
        maps.delete(trip)
        folder.change(diaryFile, "# Diary\n- Today\n- Tonight\n")
        folder.change(tripFile, "# Trip\n- Beach\n- Dinner\n")
        // Written by a newer version, say: one record says private in a way this one can't read.
        val deletions = File(root, "folder-deletions.json")
        deletions.writeText(deletions.readText().replace("\"private\":true", "\"private\":{\"since\":7}"))
        sync.syncNow()

        assertTrue(map("Diary").metadata.isPrivate)
        // The other record still reads as it was.
        assertFalse(map("Trip").metadata.isPrivate)
    }

    @Test
    fun `a map added private is private from the moment it's there`() = runTest {
        start()
        val secret = maps.add("# Secret\n", isPrivate = true)
        val written = File(root, "maps/$secret/meta.json").readText()
        assertTrue(written.contains("\"private\":true"))
        assertTrue(maps.summary(secret)!!.metadata.isPrivate)
    }

    @Test
    fun `a private map deleted and brought back from the trash keeps its file`() = runTest {
        start()
        val diary = maps.add("# Diary\n- Today\n")
        maps.setPrivate(diary, true)
        sync.syncNow()
        val file = folder.texts.keys.single()

        val trashed = maps.delete(diary)!!
        val back = maps.restore(trashed)!!
        sync.syncNow()

        assertEquals(setOf(file), folder.texts.keys)
        assertEquals(file, maps.summary(back)!!.metadata.sync!!.file)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }
}
