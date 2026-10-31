package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.Locale

/** A folder kept in memory: what a sync app or a computer changes there is [change]. */
private class MemoryFolder : SyncFolder {
    override val name = "Maps"
    val texts = mutableMapOf<String, String>()
    private val times = mutableMapOf<String, Long>()
    private var time = 1L

    override fun files() = texts.map { (name, text) -> FolderFile(name, times.getValue(name), text.length.toLong()) }

    override fun read(name: String) = texts[name] ?: throw IOException("No $name")

    override fun write(name: String, text: String, create: Boolean): FolderFile {
        texts[name] = text
        times[name] = ++time
        return FolderFile(name, time, text.length.toLong())
    }

    override fun delete(name: String) {
        texts -= name
        times -= name
    }

    override fun media() = emptyMap<String, FolderFile>()

    override fun readMedia(name: String, into: OutputStream) = false

    override fun writeMedia(name: String, from: File) = FolderFile(name, 1, from.length())

    fun change(name: String, text: String) {
        texts[name] = text
        time += 1_000
        times[name] = time
    }
}

class FolderSyncPrivateTest {
    private val files: File = Files.createTempDirectory("orbit-folder-private").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    private fun notes(): FolderNotes = Json.decodeFromString(FolderNotes.serializer(), File(files, "folder-notes.json").readText())

    @Test
    fun `the folder's version of a private map is kept private, and the notes know which maps were private`() = runTest {
        val maps = MapRepository(File(files, "maps"), File(files, "trash"), starters, words).also { it.load() }
        val settings = SettingsRepository(settingsStoreIn(File(files, "settings.preferences_pb")))
        settings.update { it.copy(syncFolder = "memory") }
        val folder = MemoryFolder()
        val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(files, "folder-notes.json"), words, TestEditor(words)) { folder }
        val journal = maps.add("# Journal\n- Today\n")
        val groceries = maps.add("# Groceries\n- Bread\n")
        val health = maps.add("# Health\n- Sleep\n")
        maps.setPrivate(journal, true)
        maps.setCover(journal, LockedCover(icon = "📓", name = "Journal"))
        maps.setPrivate(health, true)
        sync.syncNow()
        val journalFile = folder.texts.entries.single { it.value.startsWith("# Journal") }.key
        val groceriesFile = folder.texts.entries.single { it.value.startsWith("# Groceries") }.key
        val healthFile = folder.texts.entries.single { it.value.startsWith("# Health") }.key

        // Changed on both sides: both are kept, the folder's as a copy.
        maps.write(journal, "# Journal\n- Today\n- Here\n")
        folder.change(journalFile, "# Journal\n- Today\n- There\n")
        maps.write(groceries, "# Groceries\n- Bread\n- Milk\n")
        folder.change(groceriesFile, "# Groceries\n- Bread\n- Eggs\n")
        sync.syncNow()

        val journalCopy = maps.maps.value!!.single { it.title == "Journal (changed elsewhere)" }
        val groceriesCopy = maps.maps.value!!.single { it.title == "Groceries (changed elsewhere)" }
        assertTrue(journalCopy.metadata.isPrivate)
        // Its cover says it's the folder's version too, so the two tell apart while they're locked.
        assertEquals(LockedCover(icon = "📓", name = "Journal (changed elsewhere)"), journalCopy.metadata.cover)
        assertFalse(groceriesCopy.metadata.isPrivate)
        assertEquals(setOf("Journal (changed elsewhere)", "Groceries (changed elsewhere)"), notes().keptBoth.toSet())
        assertEquals(listOf("Journal (changed elsewhere)"), notes().private)

        // Emptied in the folder: the map keeps its ideas, and the note says it's private.
        folder.change(journalFile, "")
        sync.syncNow()
        assertEquals(listOf("Journal"), notes().restored)
        assertTrue("Journal" in notes().private)

        // Its file deleted from the folder, and gone still at a sync long enough after: the map goes to the trash, privately.
        folder.delete(healthFile)
        sync.syncNow()
        val record = maps.summary(health)!!.metadata.sync!!
        maps.setSyncRecord(health, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        sync.syncNow()
        assertEquals(listOf("Health"), notes().removed)
        assertTrue("Health" in notes().private)
        assertTrue(maps.trash.value.single { it.title == "Health" }.isPrivate)
    }

    @Test
    fun `notes from before private maps read as having none`() {
        val read = Json { ignoreUnknownKeys = true }.decodeFromString(FolderNotes.serializer(), """{"keptBoth":["Trip (changed elsewhere)"]}""")
        assertEquals(listOf("Trip (changed elsewhere)"), read.keptBoth)
        assertEquals(emptyList<String>(), read.private)
        assertEquals(listOf("a", "b"), (FolderNotes(private = listOf("a")) + FolderNotes(private = listOf("b", "a"))).private)
    }
}
