package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.settings.shown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

/**
 * A deleted private map never comes back as a map like any other: not when
 * its details are damaged, nor when one of the folder's deletions is, nor
 * when Orbit stops between moving it to the trash and keeping its deletion,
 * nor when its file is renamed in the folder.
 */
class FolderSyncDeletionsTest {
    private val root: File = Files.createTempDirectory("orbit-folder-deletions").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val folder = TestFolder()
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))
    private val deletions = File(root, "folder-deletions.json")

    /** Orbit stopping, as at a moment a test picks. */
    private class Stopped : RuntimeException()

    /** Orbit stopping in the middle of a sync: past each step's own catch, as nothing runs after it. */
    private class Died : Error()

    /** What the maps' clock throws at its [stopAt]th reading from now, as failing or stopping at that very moment of a step would. */
    private var stop: Throwable? = null

    private var stopAt = 0

    private fun failAt(reading: Int, failure: Throwable) {
        stop = failure
        stopAt = reading
    }

    private fun clock(): Long {
        stop?.let { failure ->
            stopAt -= 1

            if (stopAt == 0) {
                stop = null
                throw failure
            }
        }

        return System.currentTimeMillis()
    }

    /** The folders the maps can be kept in, by address: the test's, and others chosen later. */
    private val folders = mutableMapOf<String, TestFolder>("memory" to folder)

    /** This device's maps as Orbit starts with them, moving maps to the trash with `rename`, and kept in the folder the settings say. */
    private suspend fun repository(rename: (File, File) -> Boolean = { file, to -> file.renameTo(to) }) =
        MapRepository(File(root, "maps"), File(root, "trash"), starters, words, clock = ::clock, rename = rename, syncFolder = { settings.settings.first().syncFolder }).also { it.load() }

    private fun syncOf(maps: MapRepository) =
        FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "folder-notes.json"), words, TestEditor(words)) { address -> folders.getOrPut(address) { TestFolder() } }

    private suspend fun start(): Pair<MapRepository, FolderSync> {
        val repository = repository()
        settings.update { it.copy(syncFolder = "memory") }
        return repository to syncOf(repository)
    }

    private fun MapRepository.titled(title: String) = maps.value!!.filter { it.title == title }

    /** The folder's deletions as if their files had gone `by` earlier. */
    private fun goneEarlier(by: Long) {
        deletions.writeText(deletions.readText().replace(Regex("\"missingSince\":(\\d+)")) { "\"missingSince\":${it.groupValues[1].toLong() - by}" })
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** A private map, "Diary", kept in the folder: its id and its file's name there. */
    private suspend fun privateDiary(maps: MapRepository, sync: FolderSync): Pair<String, String> {
        val diary = maps.add("# Diary\n- Today\n")
        maps.setPrivate(diary, true)
        sync.syncNow()
        return diary to maps.summary(diary)!!.metadata.sync!!.file
    }

    private suspend fun assertDamagedDiaryStaysPrivate(damage: (File) -> Unit) {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        damage(File(root, "maps/$diary/meta.json"))

        assertNotNull(maps.delete(diary))
        // Changed on another device before this one's sync deleted it: it can't be told from a new file, and comes back private.
        folder.change(file, "# Diary\n- Today\n- Returned\n")
        sync.syncNow()

        val diaries = maps.titled("Diary")
        assertEquals(1, diaries.size)
        assertTrue(diaries.single().metadata.isPrivate)
        // Its deletion waits a while, as one whose file is gone does, then goes.
        assertNotNull(maps.folderDeletions().single().missingSince)
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a synced private map deleted with details that aren't JSON at all comes back private, not as a map like any other`() = runTest {
        assertDamagedDiaryStaysPrivate { it.writeText("{") }
    }

    @Test
    fun `a synced private map deleted with a sync record this version can't read comes back private, not as a map like any other`() = runTest {
        assertDamagedDiaryStaysPrivate { metadata ->
            val fields = Json.parseToJsonElement(metadata.readText()).jsonObject
            metadata.writeText(JsonObject(fields + ("sync" to JsonPrimitive("newer"))).toString())
        }
    }

    @Test
    fun `a synced map deleted with damaged details still deletes its file from the folder, as it was`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        val trip = maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        File(root, "maps/$diary/meta.json").writeText("{")

        maps.delete(diary)
        sync.syncNow()

        assertFalse(file in folder.texts)
        assertTrue(maps.titled("Diary").isEmpty())
        assertTrue(maps.exists(trip))
        // A private map's deletion stays a while once its file is gone, as any whose file is gone does.
        assertNotNull(maps.folderDeletions().single().missingSince)
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a private map with damaged details whose file changed in the folder keeps its text, and the file comes in private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        File(root, "maps/$diary/meta.json").writeText("{")
        // Read again, as at Orbit's next start.
        maps.load()
        folder.change(file, "# Diary\n- Today\n- On the laptop\n")

        sync.syncNow()

        assertEquals("# Diary\n- Today\n", maps.read(diary))
        val diaries = maps.titled("Diary")
        assertEquals(2, diaries.size)
        assertTrue(diaries.all { it.metadata.isPrivate })
        // It has a file of its own again, its details whole: the next sync is as any other.
        assertFalse(maps.summary(diary)!!.metadata.folderFileUnknown)
        folder.change("Plans.md", "# Plans\n- Soon\n")
        sync.syncNow()
        assertFalse(maps.titled("Plans").single().metadata.isPrivate)
    }

    @Test
    fun `a deletion brought back from the trash with damaged details leaves its file in the folder`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        File(root, "maps/$diary/meta.json").writeText("{")

        val trashed = maps.delete(diary)!!
        val back = maps.restore(trashed)!!
        sync.syncNow()

        assertEquals("# Diary\n- Today\n", folder.texts[file])
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        assertTrue(maps.summary(back)!!.metadata.isPrivate)
    }

    @Test
    fun `one deletion that can't be read whole doesn't hold the sync up, and the file that may be its map comes in private`() = runTest {
        val (maps, sync) = start()
        val (diary, diaryFile) = privateDiary(maps, sync)
        val trip = maps.add("# Trip\n- Beach\n")
        val plans = maps.add("# Plans\n- Soon\n")
        sync.syncNow()
        val tripFile = maps.summary(trip)!!.metadata.sync!!.file
        maps.delete(diary)
        maps.delete(trip)
        folder.change(diaryFile, "# Diary\n- Today\n- Returned\n")
        folder.change(tripFile, "# Trip\n- Beach\n- Returned\n")
        // Reshaped, as damage leaves it: its file can't be told.
        deletions.writeText(deletions.readText().replace("\"file\":\"$diaryFile\"", "\"file\":{\"name\":\"$diaryFile\"}"))

        sync.syncNow()

        // The sync goes on: the diary's file, which may be the deleted private map's, comes back private, saying why, and the
        // trip's as it was.
        assertNull(sync.state.value.problem)
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertFalse(maps.titled("Trip").single().metadata.isPrivate)
        assertEquals(listOf("Diary"), sync.state.value.notes.cameInPrivate)
        // So do deletions, and what of the record couldn't be read stays as it was written.
        assertNotNull(maps.delete(plans))
        assertTrue(deletions.readText().contains("\"file\":{\"name\":\"$diaryFile\"}"))
    }

    @Test
    fun `a damaged deletion whose missingSince can't be read is still forgotten once its file has been gone a while`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.delete(diary)
        folder.delete(file)
        // Damaged where a sync writes when its file went: what was there can't be read, and is no reason to keep it for good.
        deletions.writeText(deletions.readText().replace("\"private\":true", "\"private\":true,\"missingSince\":\"x\""))

        sync.syncNow()
        assertNotNull(maps.folderDeletions().single().missingSince)
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()

        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a deleted map's text is found in the trash by its file and its text, not by a file name another deleted map once had`() = runTest {
        val (maps, _) = start()
        val earlier = SyncRecord("memory", "Diary.md", digest("# Diary\n- 2025\n"), 1, 1)
        val later = SyncRecord("memory", "Diary.md", digest("# Diary\n- 2026\n"), 1, 1)

        // Two maps deleted at different times, whose files had the same name.
        for ((name, text, record) in listOf(Triple("a-1", "# Diary\n- 2025\n", earlier), Triple("b-2", "# Diary\n- 2026\n", later))) {
            File(root, "trash/$name").mkdirs()
            File(root, "trash/$name/map.md").writeText(text)
            File(root, "trash/$name/meta.json").writeText(MetadataFile.encode(MapMetadata(isPrivate = true, sync = record)))
        }

        assertEquals("# Diary\n- 2026\n", maps.deletedText(later.copy(private = true)))
        assertEquals("# Diary\n- 2025\n", maps.deletedText(earlier.copy(private = true)))
    }

    @Test
    fun `a map with damaged details deleted while the maps are kept in no folder leaves no deletion for a folder chosen later`() = runTest {
        val (maps, sync) = start()
        val (diary, _) = privateDiary(maps, sync)
        settings.update { it.copy(syncFolder = null) }
        File(root, "maps/$diary/meta.json").writeText("{")

        assertNotNull(maps.delete(diary))
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())

        // Weeks later, another folder, with notes, one of them the diary's very text.
        val notes = TestFolder()
        folders["notes"] = notes
        notes.change("Diary.md", "# Diary\n- Today\n")
        notes.change("Plans.md", "# Plans\n- Soon\n")
        notes.change("Trip.md", "# Trip\n- Beach\n")
        settings.update { it.copy(syncFolder = "notes") }
        sync.syncNow()

        assertEquals(setOf("Diary.md", "Plans.md", "Trip.md"), notes.texts.keys)
        assertEquals(3, maps.maps.value!!.size)
        assertTrue(maps.maps.value!!.none { it.metadata.isPrivate })
    }

    @Test
    fun `a deletion whose file couldn't be told stays with the folder it was made in`() = runTest {
        val (maps, sync) = start()
        val (diary, _) = privateDiary(maps, sync)
        File(root, "maps/$diary/meta.json").writeText("{")
        maps.delete(diary)
        assertEquals("memory", maps.folderDeletions().single().folder)

        // The maps go to another folder before a sync took the deletion: its files are that folder's, as they are.
        val notes = TestFolder()
        folders["notes"] = notes
        notes.change("Diary.md", "# Diary\n- Today\n")
        notes.change("Plans.md", "# Plans\n- Soon\n")
        settings.update { it.copy(syncFolder = "notes") }
        sync.syncNow()

        assertEquals(setOf("Diary.md", "Plans.md"), notes.texts.keys)
        assertTrue(maps.maps.value!!.none { it.metadata.isPrivate })
    }

    @Test
    fun `a map brought back from the trash after a sync found its file gone gets a file of its own, rather than follow it out again`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.delete(diary)
        // Gone from the folder before this device's sync deleted it.
        folder.delete(file)
        sync.syncNow()
        assertNotNull(maps.folderDeletions().single().missingSince)

        val back = maps.restore(maps.trash.value.single())!!
        sync.syncNow()

        val record = maps.summary(back)!!.metadata.sync!!
        assertNull(record.missingSince)
        assertTrue(record.file in folder.texts)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        assertTrue(maps.summary(back)!!.metadata.isPrivate)
    }

    @Test
    fun `a private map's file deleted elsewhere makes only a file that may be it come in private, and the notes say why`() = runTest {
        val (maps, sync) = start()
        val (_, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        folder.delete(file)
        folder.change("Groceries.md", "# Groceries\n- Bread\n")
        folder.change("Journal.md", "# Diary\n- Today\n- Tonight\n")

        sync.syncNow()

        assertFalse(maps.titled("Groceries").single().metadata.isPrivate)
        assertEquals(2, maps.titled("Diary").size)
        assertTrue(maps.titled("Diary").all { it.metadata.isPrivate })
        assertEquals(listOf("Diary"), sync.state.value.notes.cameInPrivate)
        assertTrue("Diary" in sync.state.value.notes.private)
    }

    @Test
    fun `deletions keep what a newer version added to them as they're written again`() = runTest {
        val (maps, sync) = start()
        val (diary, _) = privateDiary(maps, sync)
        val trip = maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(diary)
        // Written by a newer version: a field this one doesn't know, and a private it can't read.
        val newer = Json.parseToJsonElement(deletions.readText()).jsonArray.single().jsonObject
        val written = JsonObject(newer + ("origin" to JsonPrimitive("tablet")) + ("private" to JsonObject(mapOf("since" to JsonPrimitive(7)))))
        deletions.writeText("[$written]")

        maps.delete(trip)

        val records = Json.parseToJsonElement(deletions.readText()).jsonArray.map { it.jsonObject }
        assertEquals(2, records.size)
        assertEquals(written, records.first())
        assertTrue(maps.folderDeletions().first().private == true)
    }

    @Test
    fun `Orbit stopping right after moving a map to the trash leaves its deletion kept`() = runTest {
        val (first, firstSync) = start()
        val (diary, file) = privateDiary(first, firstSync)

        val stopping = repository { from, to ->
            from.renameTo(to)
            throw Stopped()
        }

        try {
            stopping.delete(diary)
            fail("Orbit was to stop")
        } catch (stopped: Stopped) {
            // As Orbit would, there and then.
        }

        val maps = repository()
        val sync = syncOf(maps)
        assertTrue(maps.titled("Diary").isEmpty())
        assertEquals(1, maps.trash.value.size)
        // Changed elsewhere before this device's sync deleted it: it comes back private, never as a map like any other.
        folder.change(file, "# Diary\n- Today\n- Returned\n")
        sync.syncNow()
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
    }

    @Test
    fun `Orbit stopping before moving a map to the trash leaves the map and its file as they were`() = runTest {
        val (first, firstSync) = start()
        val (diary, file) = privateDiary(first, firstSync)
        val stopping = repository { _, _ -> throw Stopped() }

        try {
            stopping.delete(diary)
            fail("Orbit was to stop")
        } catch (stopped: Stopped) {
            // As Orbit would, there and then.
        }

        val maps = repository()
        val sync = syncOf(maps)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        sync.syncNow()
        assertEquals("# Diary\n- Today\n", folder.texts[file])
        assertEquals(file, maps.summary(diary)!!.metadata.sync!!.file)
        assertTrue(maps.summary(diary)!!.metadata.isPrivate)
    }

    @Test
    fun `a map that can't move to the trash keeps its file, with no deletion left behind`() = runTest {
        val (first, firstSync) = start()
        val (diary, file) = privateDiary(first, firstSync)
        val stuck = repository { _, _ -> false }

        assertNull(stuck.delete(diary))

        assertEquals(emptyList<SyncRecord>(), stuck.folderDeletions())
        syncOf(stuck).syncNow()
        assertEquals("# Diary\n- Today\n", folder.texts[file])
    }

    @Test
    fun `a deleted private map's file renamed in the folder comes back private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.delete(diary)
        // Renamed on the laptop before this device's sync deleted it.
        val text = folder.texts.getValue(file)
        folder.delete(file)
        folder.change("Journal.md", text)

        sync.syncNow()

        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
    }

    @Test
    fun `a deleted private map's file renamed in two steps, gone in one sync and back in a later one, comes back private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        val trip = maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(diary)
        val text = folder.texts.getValue(file)
        folder.delete(file)
        sync.syncNow()
        // Its deletion waits for a later sync to agree it's gone.
        assertNotNull(maps.folderDeletions().single().missingSince)

        folder.change("Journal.md", text.replace("Today", "Tonight"))
        sync.syncNow()
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)

        // Once a later sync agrees it's gone, it's forgotten, and files new to the maps come in as they are.
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        folder.change("Plans.md", "# Plans\n- Soon\n")
        sync.syncNow()
        assertFalse(maps.titled("Plans").single().metadata.isPrivate)
        assertTrue(maps.exists(trip))
    }

    @Test
    fun `a file that can never be read keeps a deleted private map's deletion only for a day`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        folder.change("Broken.md", "# Broken\n")
        folder.failReads["Broken.md"] = IllegalStateException("The folder's app can't give it")
        maps.delete(diary)
        folder.delete(file)
        sync.syncNow()

        // It may be the diary's file, renamed and changed: the deletion stays while it can't be read.
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(1, maps.folderDeletions().size)

        // A day on, it doesn't hold the deletion, nor the files new to the maps private, any longer.
        goneEarlier(DELETION_PATIENCE)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        folder.change("Plans.md", "# Plans\n- Soon\n")
        sync.syncNow()
        assertFalse(maps.titled("Plans").single().metadata.isPrivate)
    }

    @Test
    fun `the sync a private map follows its file out in still tells new files by that map's text`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        folder.delete(file)
        sync.syncNow()
        val record = maps.summary(diary)!!.metadata.sync!!
        maps.setSyncRecord(diary, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        folder.change("Plans.md", "# Plans\n- Move to Lisbon\n")

        // The diary goes to the trash in this very sync, before the new file comes in.
        sync.syncNow()

        assertTrue(maps.titled("Diary").isEmpty())
        assertFalse(maps.titled("Plans").single().metadata.isPrivate)
        assertEquals(emptyList<String>(), sync.state.value.notes.cameInPrivate)
    }

    @Test
    fun `a private map's file renamed and changed in the folder comes in private`() = runTest {
        val (maps, sync) = start()
        val (_, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val text = folder.texts.getValue(file)
        folder.delete(file)
        folder.change("Journal.md", text.replace("Today", "Tonight"))

        sync.syncNow()

        val diaries = maps.titled("Diary")
        assertEquals(2, diaries.size)
        assertTrue(diaries.all { it.metadata.isPrivate })
    }

    @Test
    fun `a deleted private map's changed file that fails to come in keeps its deletion, and comes in private at the next sync`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.delete(diary)
        // Changed on the laptop before this device's sync deleted it.
        folder.change(file, "# Diary\n- Today\n- On the laptop\n")
        // The map it comes back as can't be made this time: as when the phone's storage is full.
        failAt(1, IOException("No room for the map"))

        sync.syncNow()

        assertTrue(maps.titled("Diary").isEmpty())
        assertEquals(file, maps.folderDeletions().single().file)
        sync.syncNow()
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `Orbit stopping as a deleted private map's changed file comes in leaves its deletion, and the file comes in private after`() = runTest {
        val (first, firstSync) = start()
        val (diary, file) = privateDiary(first, firstSync)
        first.delete(diary)
        folder.change(file, "# Diary\n- Today\n- On the laptop\n")
        // Stopped while the map it comes back as is being made.
        failAt(1, Died())
        firstSync.syncNow()

        val maps = repository()
        val sync = syncOf(maps)
        assertTrue(maps.titled("Diary").isEmpty())
        assertEquals(file, maps.folderDeletions().single().file)
        sync.syncNow()
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `Orbit stopping once a deleted private map's changed file is a map, before its deletion goes, leaves one private map`() = runTest {
        val (first, firstSync) = start()
        val (diary, file) = privateDiary(first, firstSync)
        first.delete(diary)
        folder.change(file, "# Diary\n- Today\n- On the laptop\n")
        // Stopped as the new map's original is kept: the map is there, private, and its deletion still is.
        failAt(2, Died())
        firstSync.syncNow()

        val maps = repository()
        val sync = syncOf(maps)
        sync.syncNow()

        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        assertEquals(setOf(file), folder.texts.keys)
    }

    /** A deleted private diary whose file was renamed on the laptop, and grew past what a map holds (`tooLarge` makes it so), before this device's sync deleted it. */
    private suspend fun assertRenamedTooLargeComesInPrivate(tooLarge: (String) -> Unit, readable: (String) -> Unit) {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.delete(diary)
        folder.delete(file)
        folder.change("Renamed.md", "# Diary\n- Today\n- Changed\n")
        tooLarge("Renamed.md")
        sync.syncNow()

        // It may be the diary's file: the deletion stays, past the time a deletion whose file is gone waits. That's no problem
        // the sync reports: the notes name the file already.
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(1, maps.folderDeletions().size)
        assertNull(sync.state.value.problem)

        // Small enough to be a map again, it's the diary's, and comes in private.
        readable("Renamed.md")
        sync.syncNow()
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(listOf("Diary"), sync.state.value.notes.cameInPrivate)
    }

    @Test
    fun `a deleted private map's file renamed and listed too big to read keeps its deletion, and comes in private once it can be read`() = runTest {
        assertRenamedTooLargeComesInPrivate({ folder.listedSizes[it] = MAP_TEXT_LIMIT + 1 }, { folder.listedSizes -= it })
    }

    @Test
    fun `a deleted private map's file renamed and found too big as it's read keeps its deletion, and comes in private once it can be read`() = runTest {
        assertRenamedTooLargeComesInPrivate({ folder.failReads[it] = TooLargeForAMap(it) }, { folder.failReads -= it })
    }

    @Test
    fun `a file too big to read keeps a deleted private map's deletion only for a day, and one that's a map's own doesn't`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        val trip = maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val tripFile = maps.summary(trip)!!.metadata.sync!!.file
        folder.listedSizes[tripFile] = MAP_TEXT_LIMIT + 1
        maps.delete(diary)
        folder.delete(file)
        sync.syncNow()

        // The trip's own file, too big now, isn't the diary's.
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())

        // One that's no map's holds a private map's deletion whose file is gone a day, then lets it go.
        val journal = maps.add("# Journal\n- Soon\n")
        maps.setPrivate(journal, true)
        sync.syncNow()
        val journalFile = maps.summary(journal)!!.metadata.sync!!.file
        maps.delete(journal)
        folder.delete(journalFile)
        folder.change("Large.md", "# Large\n")
        folder.listedSizes["Large.md"] = MAP_TEXT_LIMIT + 1
        sync.syncNow()
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(1, maps.folderDeletions().size)
        goneEarlier(DELETION_PATIENCE)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a file too big to read doesn't hold the deletion of a map that wasn't private, so the map brought back elsewhere stays`() = runTest {
        val (maps, sync) = start()
        val plans = maps.add("# Plans\n- Soon\n")
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val file = maps.summary(plans)!!.metadata.sync!!.file
        val text = folder.texts.getValue(file)
        folder.change("Large.md", "# Large\n")
        folder.listedSizes["Large.md"] = MAP_TEXT_LIMIT + 1
        // Deleted on the tablet, and here before this device's sync deleted its file.
        folder.delete(file)
        maps.delete(plans)
        sync.syncNow()
        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())

        // Brought back from the trash on the tablet, as it was: it stays, and comes in here too.
        folder.change(file, text)
        sync.syncNow()
        assertEquals(text, folder.texts[file])
        assertFalse(maps.titled("Plans").single().metadata.isPrivate)
    }

    @Test
    fun `a file too big to be a map named for a deleted private map's file, or a copy of it, isn't named while private maps are locked`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(diary)
        // Grown past what a map holds on the laptop, before this device's sync deleted it, and a sync app's copy of it.
        val copy = file.replace(".md", ".sync-conflict-20261113-101010-ABCDEFG.md")
        folder.change(file, "# Diary\n- Today\n- Changed\n")
        folder.change(copy, "# Diary\n- Today\n- Changed\n")
        folder.listedSizes[file] = MAP_TEXT_LIMIT + 1
        folder.listedSizes[copy] = MAP_TEXT_LIMIT + 1
        folder.change("Big.md", "# Big\n")
        folder.listedSizes["Big.md"] = MAP_TEXT_LIMIT + 1

        sync.syncNow()

        val notes = sync.state.value.notes
        assertEquals(setOf(file, copy, "Big.md"), notes.large.toSet())
        assertEquals(listOf("Big.md"), notes.shown(true, { false }, { false }, { it in notes.private }).large)
    }

    @Test
    fun `a file too big to be a map that may be a private map's, renamed, isn't named while private maps are locked`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.delete(diary)
        folder.delete(file)
        folder.change("Dear diary.md", "# Diary\n- Today\n- Changed\n")
        folder.listedSizes["Dear diary.md"] = MAP_TEXT_LIMIT + 1

        sync.syncNow()

        val notes = sync.state.value.notes
        assertEquals(listOf("Dear diary.md"), notes.large)
        assertEquals(emptyList<String>(), notes.shown(true, { false }, { false }, { it in notes.private }).large)
    }

    /** The diary, private, follows its file out of the folder: deleted on the laptop, and two syncs long enough apart agree. */
    private suspend fun followedOut(maps: MapRepository, sync: FolderSync, diary: String, file: String) {
        folder.delete(file)
        sync.syncNow()
        val record = maps.summary(diary)!!.metadata.sync!!
        maps.setSyncRecord(diary, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        sync.syncNow()
    }

    @Test
    fun `a private map that followed its file out of the folder makes a renamed, changed copy of it arriving after come in private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        followedOut(maps, sync, diary, file)
        assertTrue(maps.titled("Diary").isEmpty())
        assertEquals(file, maps.folderDeletions().single().file)

        folder.change("Dear diary.md", "# Diary\n- Today\n- Tonight\n")
        sync.syncNow()

        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(listOf("Diary"), sync.state.value.notes.cameInPrivate)
    }

    @Test
    fun `a private map that followed its file out of the folder, brought back by its name and text elsewhere, stays in the folder and comes in private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val text = folder.texts.getValue(file)
        followedOut(maps, sync, diary, file)

        // Brought back from the trash on the laptop, as it was.
        folder.change(file, text)
        sync.syncNow()

        assertEquals(text, folder.texts[file])
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        sync.syncNow()
        assertEquals(text, folder.texts[file])
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a private map that followed its file out of the folder leaves other new files as they are, and its deletion goes after a while`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        followedOut(maps, sync, diary, file)

        folder.change("Groceries.md", "# Groceries\n- Bread\n")
        sync.syncNow()
        assertFalse(maps.titled("Groceries").single().metadata.isPrivate)

        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        folder.change("Dear diary.md", "# Diary\n- Today\n- Tonight\n")
        sync.syncNow()
        assertFalse(maps.titled("Diary").single().metadata.isPrivate)
    }

    /** The diary follows its file out of the folder with Orbit stopping as it moves to the trash (`stopping`), then starting again. */
    private suspend fun assertStoppedRemovalLeavesCopiesPrivate(stopping: (File, File) -> Boolean) {
        val first = repository(stopping)
        settings.update { it.copy(syncFolder = "memory") }
        val firstSync = syncOf(first)
        val (diary, file) = privateDiary(first, firstSync)
        first.add("# Trip\n- Beach\n")
        firstSync.syncNow()
        followedOut(first, firstSync, diary, file)

        val maps = repository()
        val sync = syncOf(maps)
        sync.syncNow()
        assertTrue(maps.titled("Diary").isEmpty())
        folder.change("Dear diary.md", "# Diary\n- Today\n- Tonight\n")
        sync.syncNow()
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
    }

    @Test
    fun `Orbit stopping before a private map that follows its file out moves to the trash still makes a copy of it come in private`() = runTest {
        assertStoppedRemovalLeavesCopiesPrivate { _, _ -> throw Died() }
    }

    @Test
    fun `Orbit stopping right after a private map that follows its file out moves to the trash still makes a copy of it come in private`() = runTest {
        assertStoppedRemovalLeavesCopiesPrivate { from, to ->
            from.renameTo(to)
            throw Died()
        }
    }

    @Test
    fun `a deleted private map's file this device deleted, written back changed by another device, comes in private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(diary)
        sync.syncNow()
        assertFalse(file in folder.texts)
        assertNotNull(maps.folderDeletions().single().missingSince)

        // The laptop had edited it while its file was gone, and writes it again.
        folder.change(file, "# Diary\n- Today\n- On the laptop\n")
        sync.syncNow()

        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a deleted private map's deletion goes once a sync long enough after deleting its file agrees it's gone`() = runTest {
        val (maps, sync) = start()
        val (diary, _) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(diary)
        sync.syncNow()
        assertEquals(1, maps.folderDeletions().size)

        goneEarlier(MISSING_CONFIRMATION)
        sync.syncNow()
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a deleted private map's file this device deleted, brought back elsewhere as it was, stays in the folder and comes in private`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        val text = folder.texts.getValue(file)
        maps.delete(diary)
        sync.syncNow()
        assertFalse(file in folder.texts)

        // The laptop followed the file into its trash, and brings the diary back as it was.
        folder.change(file, text)
        sync.syncNow()
        sync.syncNow()

        assertEquals(text, folder.texts[file])
        assertTrue(maps.titled("Diary").single().metadata.isPrivate)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
    }

    @Test
    fun `a private map that followed its file out of the folder, brought back here from the trash, leaves new files as they are`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        followedOut(maps, sync, diary, file)

        val back = maps.restore(maps.trash.value.single())!!
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())
        folder.change("Groceries.md", "# Groceries\n- Bread\n")
        sync.syncNow()

        assertFalse(maps.titled("Groceries").single().metadata.isPrivate)
        assertTrue(maps.summary(back)!!.metadata.isPrivate)
        assertTrue(maps.summary(back)!!.metadata.sync!!.file in folder.texts)
    }

    @Test
    fun `a private map's deletion with no text to tell it by is told by the private map in the trash that had its file, not another`() = runTest {
        val (maps, _) = start()
        val record = SyncRecord("memory", "Diary.md", digest("# Diary\n- 2026\n"), 1, 1)

        // The private diary, and later a map that wasn't private by the same file's name, both in the trash, the latter newer.
        for ((name, text, private) in listOf(Triple("diary-1", "# Diary\n- 2026\n", true), Triple("notes-2", "# Notes\n- Bread\n", false))) {
            File(root, "trash/$name").mkdirs()
            File(root, "trash/$name/map.md").writeText(text)
            File(root, "trash/$name/meta.json").writeText(MetadataFile.encode(MapMetadata(isPrivate = private, sync = record)))
        }

        assertEquals("# Diary\n- 2026\n", maps.deletedText(record.gone(since = 1)))
    }

    @Test
    fun `a deletion whose text can't be read, its file and time still there, makes any file new to the maps come in private while it waits`() = runTest {
        val (maps, sync) = start()
        val (diary, file) = privateDiary(maps, sync)
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(diary)
        folder.delete(file)
        // Damaged where its text was: what it says of its file and time can still be read.
        deletions.writeText(deletions.readText().replace(Regex("\"hash\":\"[^\"]*\""), "\"hash\":{\"digest\":7}"))
        assertNull(maps.deletedText(maps.folderDeletions().single()))

        folder.change("Groceries.md", "# Groceries\n- Bread\n")
        sync.syncNow()

        assertTrue(maps.titled("Groceries").single().metadata.isPrivate)
    }

    @Test
    fun `bringing back from the trash a map that wasn't private, which had a private map's file name before it, leaves that map's deletion`() = runTest {
        val (maps, sync) = start()
        val earlier = maps.add("# Diary\n- Groceries\n")
        maps.add("# Trip\n- Beach\n")
        sync.syncNow()
        maps.delete(earlier)
        sync.syncNow()
        // A private diary takes the file's name since, and follows its file out of the folder.
        val (diary, file) = privateDiary(maps, sync)
        assertEquals("Diary.md", file)
        followedOut(maps, sync, diary, file)

        maps.restore(maps.trash.value.single { !it.isPrivate })!!
        assertEquals(1, maps.folderDeletions().size)
        folder.change("Dear diary.md", "# Diary\n- Today\n- Tonight\n")
        sync.syncNow()

        // The private diary's copy, renamed and changed, comes in private.
        assertTrue(maps.maps.value!!.single { it.metadata.sync?.file == "Dear diary.md" }.metadata.isPrivate)
    }
}
