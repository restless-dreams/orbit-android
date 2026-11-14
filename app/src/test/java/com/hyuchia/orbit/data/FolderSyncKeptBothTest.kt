package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.PageException
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

/**
 * The folder's version of a map changed on both sides, kept beside it: named as Orbit's editor names a map, or kept
 * as it came when Orbit can only show it, or when the page can't rename it.
 */
class FolderSyncKeptBothTest {
    private val root: File = Files.createTempDirectory("orbit-folder-kept-both").toFile()
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
    fun `a YAML map's version from the folder is renamed by Orbit's editor, every idea kept`() = runTest {
        start()
        val notes = maps.add("title: Study notes\nnodes:\n  - text: Photosynthesis\n    children:\n      - text: Light reactions\n")
        sync.syncNow()
        val file = folder.texts.keys.single()
        maps.write(notes, "title: Study notes\nnodes:\n  - text: Photosynthesis\n    children:\n      - text: Light reactions\n      - text: Calvin cycle\n")
        folder.change(file, "title: Study notes\nnodes:\n  - text: Photosynthesis\n    children:\n      - text: Light reactions\n      - text: Chlorophyll\n")
        sync.syncNow()

        val title = sync.state.value.notes.keptBoth.single()
        val copy = maps.maps.value!!.single { it.id != notes }
        assertEquals("title: Study notes\nnodes:\n  - text: $title\n    children:\n      - text: Light reactions\n      - text: Chlorophyll\n", maps.read(copy.id))
        assertEquals("$title.md", copy.metadata.sync!!.file)
    }

    @Test
    fun `a newer Orbit's version from the folder is kept as it came, and only its file's name says it's a copy`() = runTest {
        start()
        val plan = maps.add("# Plan\n- Here\n")
        sync.syncNow()
        maps.write(plan, "# Plan\n- Here\n- And here\n")
        val newer = "---\nschemaVersion: 2\n---\n# Plan\n- There\n"
        folder.change("Plan.md", newer)
        sync.syncNow()

        val copy = maps.maps.value!!.single { it.id != plan }
        assertEquals(newer, maps.read(copy.id))
        assertEquals("Plan (changed elsewhere).md", copy.metadata.sync!!.file)
        assertEquals(newer, folder.texts["Plan (changed elsewhere).md"])
        assertEquals("# Plan\n- Here\n- And here\n", folder.texts["Plan.md"])
    }

    @Test
    fun `a version from the folder the page can't rename is kept as it came, and the sync goes on`() = runTest {
        // A WebView too old to draw maps: the page fails every job at once, as it does there.
        val failing = MapTextEditor { _, _, _ -> throw PageException("This device's WebView is too old to draw maps.") }
        val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "folder-notes.json"), words, failing) { folder }
        start()
        val plan = maps.add("# Plan\n- Here\n")
        sync.syncNow()
        maps.write(plan, "# Plan\n- Here\n- And here\n")
        folder.change("Plan.md", "# Plan\n- There\n")
        sync.syncNow()

        assertNull(sync.state.value.problem)
        val copy = maps.maps.value!!.single { it.id != plan }
        assertEquals("# Plan\n- There\n", maps.read(copy.id))
        assertEquals("Plan (changed elsewhere).md", copy.metadata.sync!!.file)
        assertEquals("# Plan\n- Here\n- And here\n", folder.texts["Plan.md"])
    }
}
