package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** This device's maps, moving them to the trash with `rename`. */
    private fun repository(rename: (File, File) -> Boolean = { file, to -> file.renameTo(to) }) =
        MapRepository(File(root, "maps"), File(root, "trash"), starters, words, clock = { now }, rename = rename)

    /** Orbit stopping in the middle of a sync: past each step's own catch, as nothing runs after it. */
    private class Died : Error()

    private val maps = repository()

    /** What the sync does on its own after a call, to wait for. */
    private val work = SupervisorJob()

    private val day = 24 * 60 * 60 * 1000L
    private val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default + work), File(root, "folder-notes.json"), words, TestEditor(words)) { folder }

    private suspend fun start() {
        maps.load()
        settings.update { it.copy(syncFolder = "memory") }
    }

    /** `maps` as Orbit starts with them, and a sync of them. */
    private suspend fun started(maps: MapRepository): FolderSync {
        maps.load()
        settings.update { it.copy(syncFolder = "memory") }
        return FolderSync(maps, settings, CoroutineScope(Dispatchers.Default + work), File(root, "folder-notes.json"), words, TestEditor(words)) { folder }
    }

    /** "Gone" in the folder, with "Stays": then its file goes, and a sync long enough after is to agree. */
    private suspend fun goneFrom(maps: MapRepository, sync: FolderSync): String {
        val gone = maps.add("# Gone\n- Idea\n")
        maps.add("# Stays\n- Idea\n")
        sync.syncNow()
        folder.delete("Gone.md")
        sync.syncNow()
        val record = maps.summary(gone)!!.metadata.sync!!
        maps.setSyncRecord(gone, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        return gone
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

    @Test
    fun `reading the notes that show leaves a hidden private map's note, and its time in the trash`() = runTest {
        start()
        val diary = maps.add("# Diary\n- Idea\n")
        maps.setPrivate(diary, true)
        val plan = maps.add("# Plan\n- Idea\n")
        maps.add("# Stays\n- Idea\n")
        sync.syncNow()
        folder.delete("Diary.md")
        folder.delete("Plan.md")
        sync.syncNow()

        for (id in listOf(diary, plan)) {
            val record = maps.summary(id)!!.metadata.sync!!
            maps.setSyncRecord(id, record.copy(missingSince = record.missingSince!! - MISSING_CONFIRMATION))
        }

        sync.syncNow()
        assertEquals(setOf("Diary", "Plan"), sync.state.value.notes.removed.toSet())

        // While private maps hide, the note shows the plan alone: reading it leaves the diary's.
        sync.dismissNotes(FolderNotes(removed = listOf("Plan")), hidden = { it.isPrivate })
        work.children.forEach { it.join() }
        assertEquals(FolderNotes(removed = listOf("Diary"), private = listOf("Diary")), sync.state.value.notes)
        // So does the file that keeps them, across restarts.
        val kept = File(root, "folder-notes.json").readText()
        assertTrue("Diary" in kept && "Plan" !in kept)

        // The plan has the trash's thirty days from now, and goes after them; the diary waits for its note, however long.
        now += (TRASH_DAYS + 1) * day
        repository().load()
        now += day
        assertEquals(listOf("Diary"), repository().also { it.load() }.trash.value.map { it.title })

        // Its note read too, once private maps show, the diary has its days as well.
        sync.dismissNotes(sync.state.value.notes)
        work.children.forEach { it.join() }
        assertTrue(sync.state.value.notes.isEmpty)
        assertFalse(File(root, "folder-notes.json").exists())
        now += (TRASH_DAYS + 1) * day
        repository().load()
        now += day
        assertTrue(repository().also { it.load() }.trash.value.isEmpty())
    }

    @Test
    fun `notes read leave the others, and which of those are about private maps`() {
        val notes = FolderNotes(keptBoth = listOf("Trip (changed elsewhere)"), removed = listOf("Plan", "Diary"), tooBig = listOf("film.mp4, in “Diary”"), private = listOf("Diary", "film.mp4, in “Diary”"))
        val left = notes - FolderNotes(removed = listOf("Plan", "Diary"), keptBoth = listOf("Trip (changed elsewhere)"))
        assertEquals(FolderNotes(tooBig = listOf("film.mp4, in “Diary”"), private = listOf("film.mp4, in “Diary”")), left)
        assertTrue((notes - notes).isEmpty)
    }

    @Test
    fun `a map that can't follow its file into the trash stays, still following it, and isn't written back to the folder`() = runTest {
        var stuck = true
        val maps = repository { file, to -> !stuck && file.renameTo(to) }
        val sync = started(maps)
        val gone = goneFrom(maps, sync)

        sync.syncNow()

        assertEquals("Gone.md", maps.summary(gone)!!.metadata.sync!!.file)
        assertTrue(maps.trash.value.isEmpty())
        assertEquals(emptyList<String>(), sync.state.value.notes.removed)
        assertNotNull(sync.state.value.problem)
        sync.syncNow()
        assertFalse("Gone.md" in folder.texts)

        // Once it can, it goes.
        stuck = false
        sync.syncNow()
        assertFalse(maps.exists(gone))
        assertEquals(listOf("Gone"), maps.trash.value.map { it.title })
        assertEquals(listOf("Gone"), sync.state.value.notes.removed)
        assertEquals(setOf("Stays.md"), folder.texts.keys)
    }

    @Test
    fun `Orbit stopping as a map follows its file into the trash leaves it following the file, not written back to the folder`() = runTest {
        val first = repository { _, _ -> throw Died() }
        val firstSync = started(first)
        val gone = goneFrom(first, firstSync)
        firstSync.syncNow()

        val maps = repository()
        val sync = started(maps)
        assertEquals("Gone.md", maps.summary(gone)!!.metadata.sync!!.file)
        sync.syncNow()

        assertFalse(maps.exists(gone))
        assertEquals(listOf("Gone"), maps.trash.value.map { it.title })
        assertEquals(setOf("Stays.md"), folder.texts.keys)
    }

    @Test
    fun `a map the folder sent to the trash, Orbit stopping right after, comes back from it with a file of its own`() = runTest {
        val first = repository { file, to ->
            file.renameTo(to)
            throw Died()
        }

        val firstSync = started(first)
        goneFrom(first, firstSync)
        firstSync.syncNow()

        val maps = repository()
        val sync = started(maps)
        sync.syncNow()
        assertEquals(setOf("Stays.md"), folder.texts.keys)
        assertEquals(emptyList<SyncRecord>(), maps.folderDeletions())

        val back = maps.restore(maps.trash.value.single())!!
        sync.syncNow()
        assertEquals(setOf("Gone.md", "Stays.md"), folder.texts.keys)
        assertEquals("Gone.md", maps.summary(back)!!.metadata.sync!!.file)
        assertNull(maps.summary(back)!!.metadata.sync!!.missingSince)
    }

    @Test
    fun `a map follows its file into the trash while the folder's deletions can't be read, and leaves them as they were`() = runTest {
        start()
        val gone = goneFrom(maps, sync)
        val deletions = File(root, "folder-deletions.json")
        deletions.writeText("[{")

        assertNotNull(maps.delete(gone, byFolder = true))

        assertFalse(maps.exists(gone))
        assertEquals("[{", deletions.readText())
    }
}
