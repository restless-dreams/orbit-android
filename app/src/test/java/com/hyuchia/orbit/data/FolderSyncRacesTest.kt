package com.hyuchia.orbit.data

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.util.Locale

/**
 * Another device's edit that reaches the folder after a sync compared a file,
 * and before it deletes it or writes over it, is never lost: through
 * Android's storage access, as a folder's app answers it ([FolderApp]), the
 * edit landing at the very moment, every time.
 */
@RunWith(AndroidJUnit4::class)
class FolderSyncRacesTest {
    @get:Rule
    val disk = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private lateinit var maps: MapRepository
    private lateinit var sync: FolderSync

    @Before
    fun start() = runBlocking {
        Robolectric.setupContentProvider(FolderApp::class.java, FolderApp.AUTHORITY)
        FolderApp.reset(disk.newFolder("app"))
        maps = MapRepository(disk.newFolder("maps"), disk.newFolder("trash"), starters, words)
        val settings = SettingsRepository(settingsStoreIn(File(disk.root, "settings.preferences_pb")))

        sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(disk.root, "folder-notes.json"), words, TestEditor(words)) { address ->
            DocumentFolder(context.contentResolver, address.toUri(), words)
        }

        maps.load()
        settings.update { it.copy(syncFolder = FolderApp.tree.toString()) }
    }

    private fun file(name: String) = File(FolderApp.folder, name)

    private fun map(title: String) = maps.maps.value!!.single { it.title == title }

    /** Another device's edit, as a sync app writes it into the folder: the file's text, and a time of its own. */
    private fun edit(file: File, text: String) {
        val before = file.lastModified()
        file.writeText(text)
        file.setLastModified(before + 2_000)
    }

    /** Has another device's edit to `file` land as this device's sync opens `opened` to read it: after the sync read `file`, which comes first. */
    private fun editAsOpened(opened: String, file: File, text: String) {
        FolderApp.beforeOpening = { target, mode ->
            if (target.name == opened && mode == "r") {
                FolderApp.beforeOpening = null
                file.writeText(text)
            }
        }
    }

    @Test
    fun aFileChangedElsewhereAfterTheListingIsntDeletedWithItsMap() = runBlocking {
        val trip = maps.add("# Trip\n- Lisbon\n")
        sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n", file("Trip.md").readText())

        maps.delete(trip)

        // Right after this sync lists the folder, and before it deletes the map's file, another device's edit lands there.
        FolderApp.afterListing = { listed ->
            if (listed == FolderApp.folder) {
                FolderApp.afterListing = null
                edit(file("Trip.md"), "# Trip\n- Lisbon\n- Porto\n")
            }
        }

        sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Porto\n", file("Trip.md").readText())

        // The deletion waited for the next sync, which finds the file changed: it comes back as a map, with the edit.
        sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Porto\n", maps.read(map("Trip").id))
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun aFileChangedElsewhereToTextOfTheSameSizeIsntDeletedWhereTheFolderCantTellItsTime() = runBlocking {
        FolderApp.times = false
        val trip = maps.add("# Trip\n- Lisbon\n")
        maps.add("# Zoo\n- Lions\n")
        sync.syncNow()

        maps.delete(trip)
        // Another device's edit of the same size lands right after this sync read the file to compare it.
        editAsOpened("Zoo.md", file("Trip.md"), "# Trip\n- Madrid\n")
        sync.syncNow()
        assertEquals("# Trip\n- Madrid\n", file("Trip.md").readText())

        sync.syncNow()
        assertEquals("# Trip\n- Madrid\n", maps.read(map("Trip").id))
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun textOfTheSameSizeWrittenElsewhereAfterTheSyncReadItIsKeptWhereTheFolderCantTellItsTime() = runBlocking {
        FolderApp.times = false
        val trip = maps.add("# Trip\n- Lisbon\n")
        maps.add("# Zoo\n- Lions\n")
        sync.syncNow()

        maps.write(trip, "# Trip\n- Lisbon\n- Porto\n")
        // Another device's edit of the same size lands right after this sync read the file to compare it, before it writes the map's.
        editAsOpened("Zoo.md", file("Trip.md"), "# Trip\n- Madrid\n")
        sync.syncNow()
        assertEquals("# Trip\n- Madrid\n", file("Trip.md").readText())

        // Changed on both sides, then: both are kept, as any change made on both is.
        sync.syncNow()
        assertEquals("# Trip\n- Lisbon\n- Porto\n", file("Trip.md").readText())
        assertEquals("# Trip\n- Lisbon\n- Porto\n", maps.read(trip))
        assertEquals("# Trip (changed elsewhere)\n- Madrid\n", maps.read(map("Trip (changed elsewhere)").id))
        assertEquals(listOf("Trip (changed elsewhere)"), sync.state.value.notes.keptBoth)
    }
}
