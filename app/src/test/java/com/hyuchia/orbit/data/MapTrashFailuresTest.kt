package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Deleting a map, bringing it back and deleting it for good, when a step of
 * it fails or Orbit stops between two: each finishes, or leaves the map
 * where it was, as it was, and says so, never claiming it's done with some
 * of it left behind.
 */
class MapTrashFailuresTest {
    private val files: File = Files.createTempDirectory("orbit-trash-failures").toFile()
    private val maps = File(files, "maps")
    private val trash = File(files, "trash")
    private val history = File(files, "history")
    private var now = 1_000_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** Orbit stopping where a test says: past every catch, as nothing runs after it. */
    private class Died : Error()

    /** How a map's folder moves between the maps and the trash: a test fails it, or stops Orbit there. */
    private var moving: (File, File) -> Boolean = { from, to -> from.renameTo(to) }

    private fun repository() = MapRepository(maps, trash, starters, words, clock = { now }, rename = { from, to -> moving(from, to) })

    private suspend fun loaded(): MapRepository = repository().also { it.load() }

    @After
    fun cleanUp() {
        files.walkTopDown().forEach {
            it.setReadable(true)
            it.setWritable(true)
        }

        files.deleteRecursively()
    }

    /** The details in `folder`, as they're written. */
    private fun details(folder: File): MapMetadata = MetadataFile.decode(File(folder, METADATA_FILE).readText())

    /** Makes `file` one Orbit can't write in, for the test's injected failure, unless nothing stops whoever runs the tests. */
    private fun unwritable(file: File) {
        file.setWritable(false)
        assumeFalse(file.canWrite())
    }

    /** A private map kept in the folder as `Trip.md`, and its id. */
    private suspend fun privateTrip(repository: MapRepository): String {
        val text = "# Trip\n- Beach\n"
        val id = repository.add(text)
        repository.setPrivate(id, true)
        repository.setSyncRecord(id, SyncRecord("memory", "Trip.md", digest(text), 1, 1))
        return id
    }

    /** A map with two earlier versions in its history, and its id. */
    private suspend fun withHistory(repository: MapRepository, title: String): String {
        val id = repository.add("# $title\n")
        repository.write(id, "# $title\n- One\n", checkpoint = Checkpoint.Always)
        now += minute
        repository.write(id, "# $title\n- Two\n", checkpoint = Checkpoint.Always)
        assertEquals(2, repository.versions(id).size)
        return id
    }

    /** Waits a day past the trash's days, seen there at a start, then a day later at another, which takes what's past them. */
    private suspend fun pastItsDays(): MapRepository {
        now += (TRASH_DAYS + 1) * day
        loaded()
        now += day
        return loaded()
    }

    /** Puts the history of the map in the trash's `folder` back outside it, as an earlier version left it when it couldn't take it along. */
    private fun historyLeftBehind(folder: File, id: String) {
        assertTrue(File(folder, TRASHED_HISTORY).renameTo(File(history, id)))
    }

    // Marked as the folder's: findings [0]

    @Test
    fun `a map the folder sent away that can't be marked so stays where it was, and once it can, waits in the trash for its note`() = runTest {
        val repository = loaded()
        val id = privateTrip(repository)
        val folder = repository.folder(id)
        unwritable(folder)

        try {
            assertNull(repository.delete(id, byFolder = true))
        } finally {
            folder.setWritable(true)
        }

        // Where it was, as it was: among the maps, not marked, with no deletion for its file.
        assertTrue(repository.exists(id))
        assertTrue(repository.trash.value.isEmpty())
        assertFalse(details(folder).removedByFolder)
        assertEquals(emptyList<SyncRecord>(), repository.folderDeletions())

        val trashed = repository.delete(id, byFolder = true)!!
        assertTrue(details(trashed.folder).removedByFolder)
        // Past its days, seen so a day apart, it still waits for its note.
        assertEquals(listOf(id), pastItsDays().trash.value.map { it.id })

        // Once the note's read, its days start again, and it goes once they're past.
        loaded().seeFolderRemovals()
        assertFalse(details(loaded().trash.value.single().folder).removedByFolder)
        assertTrue(pastItsDays().trash.value.isEmpty())
    }

    @Test
    fun `a map the folder sent away is marked before it moves, so its folder failing after the move doesn't let it go unseen`() = runTest {
        val repository = loaded()
        val id = repository.add("# Synced\n- Gone from the folder\n")
        // The trash takes the map, and then nothing more can be written in it.
        moving = { from, to -> from.renameTo(to).also { moved -> if (moved && to.parentFile == trash) to.setWritable(false) } }
        val trashed = repository.delete(id, byFolder = true)!!
        trashed.folder.setWritable(true)
        moving = { from, to -> from.renameTo(to) }

        assertTrue(details(trashed.folder).removedByFolder)
        assertEquals(listOf(id), pastItsDays().trash.value.map { it.id })
    }

    @Test
    fun `Orbit stopping after a map the folder sent away is marked, before it moves, leaves it among the maps to go at the next sync`() = runTest {
        val repository = loaded()
        val id = privateTrip(repository)
        moving = { from, to -> if (to.parentFile == trash) throw Died() else from.renameTo(to) }

        try {
            repository.delete(id, byFolder = true)
            fail("Orbit went on")
        } catch (died: Died) {
            // Orbit stopped, and starts again.
        }

        moving = { from, to -> from.renameTo(to) }
        val restarted = loaded()
        assertTrue(restarted.exists(id))
        // Its deletion is forgotten, as the map is still here, still following its file.
        assertEquals(emptyList<SyncRecord>(), restarted.folderDeletions())

        // The sync's next try takes it to the trash, marked.
        val trashed = restarted.delete(id, byFolder = true)!!
        assertTrue(details(trashed.folder).removedByFolder)
        assertEquals(listOf(id), pastItsDays().trash.value.map { it.id })
    }

    @Test
    fun `a mark Orbit stopping left on a map doesn't keep it in the trash once it's deleted here`() = runTest {
        val repository = loaded()
        val id = repository.add("# Synced\n- Back in the folder\n")
        moving = { from, to -> if (to.parentFile == trash) throw Died() else from.renameTo(to) }

        try {
            repository.delete(id, byFolder = true)
            fail("Orbit went on")
        } catch (died: Died) {
            // Orbit stopped, and starts again; its file came back, so the folder doesn't send it away after all.
        }

        moving = { from, to -> from.renameTo(to) }
        val restarted = loaded()
        assertTrue(details(restarted.folder(id)).removedByFolder)

        val trashed = restarted.delete(id)!!
        assertFalse(details(trashed.folder).removedByFolder)
        assertTrue(pastItsDays().trash.value.isEmpty())
    }

    @Test
    fun `Orbit stopping right after a map the folder sent away moves leaves it marked in the trash`() = runTest {
        val repository = loaded()
        val id = repository.add("# Synced\n- Gone from the folder\n")
        moving = { from, to -> from.renameTo(to).also { if (to.parentFile == trash) throw Died() } }

        try {
            repository.delete(id, byFolder = true)
            fail("Orbit went on")
        } catch (died: Died) {
            // Orbit stopped, and starts again.
        }

        moving = { from, to -> from.renameTo(to) }
        val restarted = loaded()
        assertTrue(details(restarted.trash.value.single().folder).removedByFolder)
        assertEquals(listOf(id), pastItsDays().trash.value.map { it.id })
    }

    @Test
    fun `a map that can't move to the trash is left as it was, its history, mark and deletion too`() = runTest {
        val repository = loaded()
        val id = privateTrip(repository)
        repository.write(id, "# Trip\n- Beach\n- Mountains\n", checkpoint = Checkpoint.Always)
        moving = { from, to -> if (to.parentFile == trash) false else from.renameTo(to) }

        assertNull(repository.delete(id, byFolder = true))
        assertNull(repository.delete(id))

        val folder = repository.folder(id)
        assertFalse(details(folder).removedByFolder)
        assertFalse(File(folder, TRASHED_HISTORY).exists())
        assertEquals(1, repository.versions(id).size)
        assertEquals(emptyList<SyncRecord>(), repository.folderDeletions())
        assertTrue(repository.trash.value.isEmpty())
        assertEquals(listOf(id), repository.maps.value!!.map { it.id })
    }

    // Its history goes with it: findings [1]

    @Test
    fun `a map whose history can't go to the trash with it stays where it was, its history whole`() = runTest {
        val repository = loaded()
        val id = withHistory(repository, "Journal")
        val versions = File(history, id)
        val texts = versions.listFiles { file -> file.name.endsWith(".md") }!!.associate { it.name to it.readText() }
        // One version can't be read now, and the history can't simply be renamed: it can't be copied whole.
        versions.listFiles { file -> file.name.endsWith(".md") }!!.first().setReadable(false)
        unwritable(versions)

        try {
            assertNull(repository.delete(id))
        } finally {
            versions.walkTopDown().forEach { it.setReadable(true) }
            versions.setWritable(true)
        }

        assertTrue(repository.exists(id))
        assertTrue(repository.trash.value.isEmpty())
        assertEquals(texts, versions.listFiles { file -> file.name.endsWith(".md") }!!.associate { it.name to it.readText() })

        // What it copied into the map's folder joins the rest at the next start, and the map then goes, history and all.
        val restarted = loaded()
        assertFalse(File(restarted.folder(id), TRASHED_HISTORY).exists())
        assertEquals(2, restarted.versions(id).size)
        val trashed = restarted.delete(id)!!
        assertEquals(texts.keys, File(trashed.folder, TRASHED_HISTORY).list()!!.filter { it.endsWith(".md") }.toSet())
        assertFalse(versions.exists())
    }

    @Test
    fun `Orbit stopping between a map's history moving and the map leaves both where they were at the next start`() = runTest {
        val repository = loaded()
        val id = withHistory(repository, "Journal")
        moving = { from, to -> if (to.parentFile == trash) throw Died() else from.renameTo(to) }

        try {
            repository.delete(id)
            fail("Orbit went on")
        } catch (died: Died) {
            // Orbit stopped, its history in the map's folder, and starts again.
        }

        assertTrue(File(repository.folder(id), TRASHED_HISTORY).isDirectory)
        moving = { from, to -> from.renameTo(to) }
        val restarted = loaded()
        assertTrue(restarted.exists(id))
        assertFalse(File(restarted.folder(id), TRASHED_HISTORY).exists())
        assertEquals(listOf("# Journal\n- One\n", "# Journal\n"), restarted.versions(id).map { restarted.readVersion(id, it.time) })
    }

    @Test
    fun `a history copied to the trash whose own copy wouldn't delete goes when the map is deleted for good`() = runTest {
        val repository = loaded()
        val id = withHistory(repository, "Journal")
        val versions = File(history, id)
        // It can't be renamed, nor its files deleted: it's copied, and stays.
        unwritable(versions)

        val trashed = try {
            repository.delete(id)!!
        } finally {
            versions.setWritable(true)
        }

        assumeTrue(versions.exists())
        assertEquals(2, File(trashed.folder, TRASHED_HISTORY).list()!!.count { it.endsWith(".md") })

        assertTrue(repository.deleteForever(trashed))
        assertFalse(trashed.folder.exists())
        assertFalse(versions.exists())
        assertTrue(loaded().trash.value.isEmpty())
    }

    @Test
    fun `a history an earlier version left outside the trash goes with its map, for good or emptied`() = runTest {
        val repository = loaded()
        val journal = withHistory(repository, "Journal")
        val diary = withHistory(repository, "Diary")
        val journalTrashed = repository.delete(journal)!!
        val diaryTrashed = repository.delete(diary)!!
        historyLeftBehind(journalTrashed.folder, journal)
        historyLeftBehind(diaryTrashed.folder, diary)

        assertTrue(repository.deleteForever(journalTrashed))
        assertFalse(File(history, journal).exists())
        assertTrue(File(history, diary).exists())

        assertEquals(0, repository.emptyTrash())
        assertFalse(File(history, diary).exists())
    }

    @Test
    fun `a history an earlier version left outside the trash goes with its map past its days`() = runTest {
        val repository = loaded()
        val id = withHistory(repository, "Journal")
        historyLeftBehind(repository.delete(id)!!.folder, id)

        assertTrue(pastItsDays().trash.value.isEmpty())
        assertFalse(File(history, id).exists())
    }

    @Test
    fun `a history outside the trash stays while another map in the trash, or here, has its id`() = runTest {
        val repository = loaded()
        val id = withHistory(repository, "Journal")
        val first = repository.delete(id)!!
        historyLeftBehind(first.folder, id)
        // Another of its id in the trash, as one deleted, brought back, and deleted again by hand.
        val second = File(trash, "$id-${now + 1}")
        first.folder.copyRecursively(second)

        val repository2 = loaded()
        assertTrue(repository2.deleteForever(repository2.trash.value.first { it.folder == first.folder }))
        assertTrue(File(history, id).exists())

        // And one here by that id.
        File(maps, id).mkdirs()
        File(maps, "$id/map.md").writeText("# Journal\n- Here\n")
        val repository3 = loaded()
        assertTrue(repository3.deleteForever(repository3.trash.value.single()))
        assertTrue(File(history, id).exists())
        assertEquals(2, repository3.versions(id).size)
    }

    @Test
    fun `a map whose history left outside the trash won't delete stays in the trash, to delete again`() = runTest {
        val repository = loaded()
        val id = withHistory(repository, "Journal")
        val trashed = repository.delete(id)!!
        historyLeftBehind(trashed.folder, id)
        val versions = File(history, id)
        unwritable(versions)

        try {
            assertFalse(repository.deleteForever(trashed))
            assertEquals(listOf("Journal"), repository.trash.value.map { it.title })
            assertTrue(File(trashed.folder, MAP_FILE).isFile)
        } finally {
            versions.setWritable(true)
        }

        assertTrue(repository.deleteForever(repository.trash.value.single()))
        assertFalse(versions.exists())
        assertFalse(trashed.folder.exists())
    }

    // Its details go too: findings [2]

    @Test
    fun `a map whose details won't delete after its text did isn't said to be gone, and stays in the trash, private, to delete again`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        val trashed = repository.delete(id)!!
        // Its text went, then its folder failed: its details stay.
        assertTrue(File(trashed.folder, MAP_FILE).delete())
        unwritable(trashed.folder)

        try {
            assertFalse(repository.deleteForever(trashed))
            assertTrue(File(trashed.folder, METADATA_FILE).isFile)
            assertEquals(listOf("Journal" to true), repository.trash.value.map { it.title to it.isPrivate })
            // There's no map to bring back.
            assertNull(repository.restore(repository.trash.value.single()))
            assertFalse(repository.exists(id))
        } finally {
            trashed.folder.setWritable(true)
        }

        assertTrue(repository.deleteForever(repository.trash.value.single()))
        assertFalse(trashed.folder.exists())
        assertTrue(repository.trash.value.isEmpty())
    }

    @Test
    fun `a save that finds its map gone keeps nothing in what deleting it for good left in the trash`() = runTest {
        val repository = loaded()
        val opened = repository.now()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        val trashed = repository.delete(id)!!
        assertTrue(File(trashed.folder, MAP_FILE).delete())
        unwritable(trashed.folder)

        try {
            assertFalse(repository.deleteForever(trashed))
        } finally {
            trashed.folder.setWritable(true)
        }

        // Still listed, its text gone: a save from its screen finds it there, and keeps nothing in it.
        assertEquals(listOf(id), repository.trash.value.map { it.id })
        assertFalse(repository.keepAsideInTrash(id, "# Journal\n- Today, typed\n", deletedSince = opened))
        assertFalse(File(trashed.folder, TRASHED_HISTORY).exists())

        // So the next start finishes deleting it.
        loaded()
        assertFalse(trashed.folder.exists())
    }

    @Test
    fun `what Orbit stopping after a map's text went for good left of it goes at the next start`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        val trashed = repository.delete(id)!!
        // Orbit stopped right after deleting its text, its details still there.
        assertTrue(File(trashed.folder, MAP_FILE).delete())

        val restarted = loaded()
        assertFalse(trashed.folder.exists())
        assertTrue(restarted.trash.value.isEmpty())
    }

    @Test
    fun `emptying the trash counts a map whose details won't delete after its text did`() = runTest {
        val repository = loaded()
        val trashed = repository.delete(repository.add("# Journal\n- Today\n"))!!
        repository.delete(repository.add("# Groceries\n- Bread\n"))
        assertTrue(File(trashed.folder, MAP_FILE).delete())
        unwritable(trashed.folder)

        try {
            assertEquals(1, repository.emptyTrash())
            assertEquals(listOf("Journal"), repository.trash.value.map { it.title })
        } finally {
            trashed.folder.setWritable(true)
        }

        assertEquals(0, repository.emptyTrash())
        assertTrue(repository.trash.value.isEmpty())
        assertFalse(trashed.folder.exists())
    }

    @Test
    fun `a folder in the trash with more than a map's details and no text isn't Orbit's to delete`() = runTest {
        loaded()
        val other = File(trash, "notes-${now - day}")
        File(other, "media").mkdirs()
        File(other, "media/picture-1.jpg").writeBytes(byteArrayOf(1))
        File(other, METADATA_FILE).writeText("{}")

        loaded()
        assertTrue(File(other, "media/picture-1.jpg").isFile)
        assertTrue(File(other, METADATA_FILE).isFile)
    }

    // Checkpoints: findings [3]

    @Test
    fun `a newest version that can't be read doesn't stop a checkpoint that's due`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.write(id, "# Plan\n- First\n", checkpoint = Checkpoint.Always)
        now += minute
        val newest = File(history, "$id/$now.md").apply { writeText("# Plan\n- Newest history\n") }
        newest.setReadable(false)
        assumeFalse(newest.canRead())

        try {
            now += 11 * minute
            assertEquals(WriteResult.Written, repository.write(id, "# Plan\n- Second\n"))
            now += minute
            assertEquals(WriteResult.Written, repository.write(id, "# Plan\n- Third\n", checkpoint = Checkpoint.Always))
        } finally {
            newest.setReadable(true)
        }

        // The map as it was before each, kept in its history all the same.
        val texts = repository.versions(id).map { repository.readVersion(id, it.time) }
        assertTrue("# Plan\n- First\n" in texts)
        assertTrue("# Plan\n- Second\n" in texts)
        assertEquals(4, texts.size)
    }

    // Dated again: findings [4]

    @Test
    fun `a map in the trash dated again loses when it was seen past its earlier days, and goes a day after its new ones`() = runTest {
        val repository = loaded()
        val deletedAt = now
        repository.delete(repository.add("# Clock\n- Wrong time\n"))
        now = deletedAt + (TRASH_DAYS + 1) * day
        val seen = File(loaded().trash.value.single().folder, ".expired")
        assertTrue(seen.isFile)

        // The clock goes back, and the map's days start again.
        now = deletedAt - 2 * day
        val restamped = loaded().trash.value.single()
        assertEquals(now, restamped.deleted)
        assertFalse(File(restamped.folder, ".expired").exists())

        now = restamped.deleted + (TRASH_DAYS + 1) * day
        assertEquals(1, loaded().trash.value.size)
        now += day
        assertTrue(loaded().trash.value.isEmpty())
    }

    @Test
    fun `a map in the trash whose mark won't go keeps its date, and goes nowhere, until it can be dated again`() = runTest {
        val repository = loaded()
        val deletedAt = now
        repository.delete(repository.add("# Clock\n- Wrong time\n"))
        now = deletedAt + (TRASH_DAYS + 1) * day
        val folder = loaded().trash.value.single().folder
        assertTrue(File(folder, ".expired").isFile)
        unwritable(folder)

        try {
            now = deletedAt - 2 * day
            assertEquals(listOf(deletedAt), loaded().trash.value.map { it.deleted })
            assertTrue(File(folder, ".expired").isFile)
        } finally {
            folder.setWritable(true)
        }

        val restamped = loaded().trash.value.single()
        assertEquals(now, restamped.deleted)
        assertFalse(File(restamped.folder, ".expired").exists())
    }

    @Test
    fun `a map the folder sent away that can't be dated again as its note is read keeps waiting`() = runTest {
        val repository = loaded()
        val trashed = repository.delete(repository.add("# Synced\n- Gone from the folder\n"), byFolder = true)!!
        now += 3 * day
        unwritable(trash)

        try {
            repository.seeFolderRemovals()
            assertTrue(details(trashed.folder).removedByFolder)
            assertEquals(listOf(trashed.deleted), repository.trash.value.map { it.deleted })
        } finally {
            trash.setWritable(true)
        }

        assertEquals(1, pastItsDays().trash.value.size)
    }

    // Back from the trash: findings [5]

    @Test
    fun `a map whose deletion can't be let go of as it comes back goes back to the trash as it was`() = runTest {
        val repository = loaded()
        val text = "# Trip\n- Beach\n"
        val id = repository.add(text)
        val record = SyncRecord("memory", "Trip.md", digest(text), 1, 1)
        repository.setSyncRecord(id, record)
        val trashed = repository.delete(id)!!
        assertEquals(listOf(record), repository.folderDeletions())
        // The folder's deletions are beside the maps: nothing new can be written there.
        unwritable(files)

        try {
            assertNull(repository.restore(trashed))
        } finally {
            files.setWritable(true)
        }

        assertTrue(File(trashed.folder, MAP_FILE).isFile)
        assertFalse(repository.exists(id))
        assertEquals(listOf(trashed.folder), repository.trash.value.map { it.folder })
        assertEquals(listOf(record), repository.folderDeletions())
        assertEquals(record, details(trashed.folder).sync)

        assertEquals(id, repository.restore(repository.trash.value.single()))
        assertEquals(emptyList<SyncRecord>(), repository.folderDeletions())
        assertEquals(text, repository.read(id))
    }

    // A history waiting in a map's folder isn't the map's own files

    /** Map `id` with a history waiting in its folder, as one that couldn't move as it came back from the trash leaves it, pointing at a picture. */
    private fun waitingHistory(repository: MapRepository, id: String) {
        val folder = repository.folder(id)
        File(folder, "media").mkdirs()

        File(folder, "media/picture-1.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(now - 10 * day)
        }

        File(folder, TRASHED_HISTORY).mkdirs()
        File(folder, "$TRASHED_HISTORY/${now - day}.md").writeText("# Journal\n- Beach ![](media/picture-1.jpg)\n")
    }

    @Test
    fun `a history waiting in a map's folder keeps the pictures its versions point at`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        waitingHistory(repository, id)

        repository.removeUnusedPictures(id)
        assertTrue(File(repository.folder(id), "media/picture-1.jpg").isFile)
    }

    @Test
    fun `a history waiting in a map's folder stays out of a zip of every map`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        waitingHistory(repository, id)

        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()
        val names = ZipInputStream(ByteArrayInputStream(zip)).use { input -> generateSequence { input.nextEntry }.map { it.name }.toList() }
        assertTrue("$id/map.md" in names)
        assertEquals(emptyList<String>(), names.filter { "/$TRASHED_HISTORY/" in it })
    }

    @Test
    fun `a history waiting in a map's folder goes into a backup only to a new phone, with the rest`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        waitingHistory(repository, id)
        val waiting = "maps/$id/$TRASHED_HISTORY/${now - day}.md"

        fun backup(newPhone: Boolean) = backupFiles(files, Long.MAX_VALUE, newPhone).filter { it.isFile }.map { it.relativeTo(files).invariantSeparatorsPath }

        assertTrue("maps/$id/map.md" in backup(newPhone = false))
        assertFalse(waiting in backup(newPhone = false))
        assertTrue(waiting in backup(newPhone = true))
    }
}
