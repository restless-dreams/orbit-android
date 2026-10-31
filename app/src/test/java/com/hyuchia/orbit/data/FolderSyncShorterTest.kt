package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** Files shorter than their maps: deleting a map's last ideas elsewhere, a file still arriving, and this device's own write cut short. */
class FolderSyncShorterTest {
    private val root: File = Files.createTempDirectory("orbit-folder-shorter").toFile()
    private val words = FileWords(Locale.US)
    private var clock = 0L

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** A device keeping its maps in `folder`, its own maps and sync on the test's clock. */
    private inner class Device(name: String, folder: SyncFolder) {
        val maps = MapRepository(File(root, "$name/maps"), File(root, "$name/trash"), starters, words)
        val settings = SettingsRepository(settingsStoreIn(File(root, "$name/settings.preferences_pb")))
        val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "$name/folder-notes.json"), words, TestEditor(words), elapsed = { clock }) { folder }

        suspend fun start(): Device {
            maps.load()
            settings.update { it.copy(syncFolder = "memory") }
            return this
        }

        suspend fun text(id: String) = maps.read(id)

        fun only() = maps.maps.value!!.single().id
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `deleting a map's last ideas on another device reaches this one, once the file has settled`() = runTest {
        val folder = TestFolder()
        val phone = Device("phone", folder).start()
        val tablet = Device("tablet", folder).start()
        val trip = phone.maps.add("# Trip\n- Lisbon\n- Porto\n")
        phone.sync.syncNow()
        tablet.sync.syncNow()
        val there = tablet.only()

        tablet.maps.write(there, "# Trip\n- Lisbon\n")
        tablet.sync.syncNow()
        val file = folder.texts.keys.single()
        assertEquals("# Trip\n- Lisbon\n", folder.texts[file])

        // The phone doesn't write its longer text back, and waits for the file to stay as it is.
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n", folder.texts[file])
        assertEquals("# Trip\n- Lisbon\n- Porto\n", phone.text(trip))

        clock += SETTLING.inWholeMilliseconds
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n", phone.text(trip))

        // The tablet keeps what it did, and nothing comes back on either side.
        tablet.sync.syncNow()
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n", tablet.text(there))
        assertEquals("# Trip\n- Lisbon\n", folder.texts[file])
        assertEquals(1, phone.maps.maps.value!!.size)
    }

    @Test
    fun `a file still arriving isn't written over, and its map takes the whole file once it's there`() = runTest {
        val folder = TestFolder()
        val phone = Device("phone", folder).start()
        val trip = phone.maps.add("# Trip\n- Lisbon\n- Porto\n")
        phone.sync.syncNow()
        val file = folder.texts.keys.single()

        // Another device's longer text, of which only the start has arrived.
        folder.change(file, "# Trip\n- Lis")
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lis", folder.texts[file])
        assertEquals("# Trip\n- Lisbon\n- Porto\n", phone.text(trip))

        clock += SETTLING.inWholeMilliseconds / 2
        folder.change(file, "# Trip\n- Lisbon\n- Porto\n- Faro\n")
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Porto\n- Faro\n", phone.text(trip))
    }

    @Test
    fun `this device's own write cut short is written again, with no copy`() = runTest {
        val folder = TestFolder()
        val phone = Device("phone", folder).start()
        val trip = phone.maps.add("# Trip\n- Lisbon\n")
        phone.sync.syncNow()
        val file = folder.texts.keys.single()

        phone.maps.write(trip, "# Trip\n- Lisbon\n- Porto\n- Faro\n")
        folder.cutNextWrite = true
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon", folder.texts[file])

        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Porto\n- Faro\n", folder.texts[file])
        assertEquals(listOf(trip), phone.maps.maps.value!!.map { it.id })
        assertNull(phone.maps.summary(trip)!!.metadata.sync!!.writing)
    }

    @Test
    fun `this device's own write cut short, and the map edited again before the next sync, is written again with no copy`() = runTest {
        val folder = TestFolder()
        val phone = Device("phone", folder).start()
        val trip = phone.maps.add("# Trip\n- Lisbon\n")
        phone.sync.syncNow()
        val file = folder.texts.keys.single()

        phone.maps.write(trip, "# Trip\n- Lisbon\n- Porto\n- Faro\n")
        folder.cutNextWrite = true
        phone.sync.syncNow()
        phone.maps.write(trip, "# Trip\n- Lisbon\n- Porto\n- Faro\n- Sintra\n")

        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Porto\n- Faro\n- Sintra\n", folder.texts[file])
        assertEquals(listOf(trip), phone.maps.maps.value!!.map { it.id })
    }

    @Test
    fun `a file emptied in the folder still gets its map's ideas back`() = runTest {
        val folder = TestFolder()
        val phone = Device("phone", folder).start()
        val trip = phone.maps.add("# Trip\n- Lisbon\n")
        phone.sync.syncNow()
        val file = folder.texts.keys.single()

        folder.change(file, "")
        phone.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n", folder.texts[file])
        assertEquals("# Trip\n- Lisbon\n", phone.text(trip))
    }

    @Test
    fun `a file made a YAML map without ideas elsewhere gets its map's ideas back, from whichever device syncs first`() = runTest {
        val folder = TestFolder()
        val phone = Device("phone", folder).start()
        val tablet = Device("tablet", folder).start()
        val trip = phone.maps.add("# Trip\n- Lisbon\n")
        phone.sync.syncNow()
        tablet.sync.syncNow()
        val there = tablet.only()
        val file = folder.texts.keys.single()

        for ((index, empty) in listOf("nodes: []\n", "title: Trip\nnodes:\n", "nodes:\n  - text: \"\"\n").withIndex()) {
            folder.change(file, empty)
            clock += SETTLING.inWholeMilliseconds
            val first = if (index % 2 == 0) phone else tablet
            first.sync.syncNow()
            assertEquals("# Trip\n- Lisbon\n", folder.texts[file])
            assertEquals(listOf("Trip"), first.sync.state.value.notes.restored)
        }

        phone.sync.syncNow()
        tablet.sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n", phone.text(trip))
        assertEquals("# Trip\n- Lisbon\n", tablet.text(there))
        assertEquals("# Trip\n- Lisbon\n", folder.texts[file])
    }
}
