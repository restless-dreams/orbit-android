package com.hyuchia.orbit.data

import android.app.backup.BackupAgent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * OrbitBackupAgent's part of a restore (`restoreFile`): where each file is
 * written, what takes its place, and its time. Android's own part, the bytes
 * into the file it's given, stands in here as it behaves: it writes them,
 * then gives the file its time as it came, in seconds. (Run on Robolectric,
 * Android's part writes nothing: it first checks the app's backup rules
 * against system state Robolectric doesn't set up.) The backup's stream, which
 * Orbit reads the folder's deletions from itself, stands in as bytes, with the
 * next file's after them.
 */
class BackupRestoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val filesDir: File by lazy { folder.newFolder("files") }

    /** Android's restore of `bytes` into the file it's given, with the time it was backed up with. */
    private fun android(bytes: ByteArray, mtime: Long): (File) -> Unit = { target ->
        target.writeBytes(bytes)
        target.setLastModified(mtime)
    }

    /** The backup's stream, for a file Android restores itself: Orbit never reads it then. */
    private val androidReads: () -> InputStream = { throw AssertionError("Android reads this file's bytes, not Orbit") }

    /** What Android restores in its place, should it be asked to: the folder's deletions never are. */
    private val notAndroid: (File) -> Unit = { throw AssertionError("Android restores this file only in its place, so Orbit restores it itself") }

    /** The next file's bytes in the backup's stream, which restoring one file must leave there. */
    private val nextFile = "the next file".toByteArray()

    @Test
    fun aMapsFileComesBackWholeAtTheTimeItLastChanged() {
        val map = File(filesDir, "maps/trip/map.md").apply { parentFile!!.mkdirs() }
        val text = "# Trip\n- Lisbon\n- Porto\n".toByteArray()
        val written = mutableListOf<String>()

        restoreFile(map, BackupAgent.TYPE_FILE, text.size.toLong(), 1_760_000_000, filesDir, androidReads) { target ->
            written += target.name
            android(text, 1_760_000_000)(target)
        }

        // Written beside its place first, then given its name: nothing's left beside it.
        assertEquals(listOf(".map.md.partial"), written)
        assertArrayEquals(text, map.readBytes())
        assertEquals(listOf("map.md"), map.parentFile!!.list()!!.toList())
        // Android gives the time in seconds: the map reads as changed then, not in 1970.
        assertEquals(1_760_000_000_000, map.lastModified())
    }

    @Test
    fun theFolderDeletionsComeBackWholeWrittenBesideTheirPlaceFirst() {
        // Android would restore them only in their place, the rules naming the file on its own: Orbit reads their bytes itself.
        val deletions = File(filesDir, "folder-deletions.json")
        deletions.writeText("[]")
        val json = """[{"file":"Old plan.md","hash":"0f1e2d3c4b5a6978"}]""".toByteArray()
        val stream = ByteArrayInputStream(json + nextFile)

        // As the bytes come, what's there stays as it was: they're written beside it.
        val watched = object : InputStream() {
            override fun read(): Int = stream.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertEquals("[]", deletions.readText())
                return stream.read(buffer, offset, length)
            }
        }

        restoreFile(deletions, BackupAgent.TYPE_FILE, json.size.toLong(), 1_760_000_500, filesDir, { watched }, notAndroid)

        assertArrayEquals(json, deletions.readBytes())
        assertEquals(listOf("folder-deletions.json"), filesDir.list()!!.toList())
        assertEquals(1_760_000_500_000, deletions.lastModified())
        // Their bytes and none past them: the next file starts where it should.
        assertArrayEquals(nextFile, stream.readBytes())
    }

    @Test
    fun aBackupEndingPartwayThroughTheFolderDeletionsLeavesThemAsTheyWere() {
        val deletions = File(filesDir, "folder-deletions.json")
        deletions.writeText("""[{"file":"Kept.md"}]""")
        val json = """[{"file":"Old plan.md","hash":"0f1e2d3c4b5a6978"}]""".toByteArray()

        restoreFile(deletions, BackupAgent.TYPE_FILE, json.size.toLong(), 1_760_000_500, filesDir, { ByteArrayInputStream(json.copyOf(10)) }, notAndroid)

        assertEquals("""[{"file":"Kept.md"}]""", deletions.readText())
        assertEquals(listOf("folder-deletions.json"), filesDir.list()!!.toList())
    }

    @Test
    fun theFolderDeletionsThatCantBeWrittenAreReadAllTheSame() {
        val deletions = File(filesDir, "folder-deletions.json")
        deletions.writeText("[]")
        val json = """[{"file":"Old plan.md","hash":"0f1e2d3c4b5a6978"}]""".toByteArray()
        val stream = ByteArrayInputStream(json + nextFile)
        // Nothing can be written beside them, as on a full phone.
        filesDir.setWritable(false)

        try {
            restoreFile(deletions, BackupAgent.TYPE_FILE, json.size.toLong(), 1_760_000_500, filesDir, { stream }, notAndroid)
        } finally {
            filesDir.setWritable(true)
        }

        assertEquals("[]", deletions.readText())
        assertArrayEquals(nextFile, stream.readBytes())
    }

    @Test
    fun aRestoredFileTakesThePlaceOfWhatWasThere() {
        val map = File(filesDir, "maps/plan/map.md").apply { parentFile!!.mkdirs() }
        map.writeText("# Plan\n- an older version\n")
        val text = "# Plan\n- the backed-up version\n".toByteArray()
        restoreFile(map, BackupAgent.TYPE_FILE, text.size.toLong(), 1_760_001_000, filesDir, androidReads, android(text, 1_760_001_000))

        assertArrayEquals(text, map.readBytes())
        assertTrue(map.parentFile!!.list()!!.none { it.endsWith(".partial") })
    }

    @Test
    fun aRestoreStoppedPartwayNeverTakesThePlaceOfWhatsThere() {
        val map = File(filesDir, "maps/plan/map.md").apply { parentFile!!.mkdirs() }
        map.writeText("# Plan\n- what was here\n")

        val stopped = runCatching {
            restoreFile(map, BackupAgent.TYPE_FILE, 40, 1_760_001_000, filesDir, androidReads) { target ->
                target.writeText("# Pl")
                throw IOException("The backup's stream ended")
            }
        }

        assertTrue(stopped.exceptionOrNull() is IOException)
        assertEquals("# Plan\n- what was here\n", map.readText())
    }

    @Test
    fun aBackupEndingPartwayThroughAFileNeverTakesThePlaceOfWhatsThere() {
        // Android stops at the end of a backup cut short without saying so: the file it wrote is shorter than the backup said.
        val map = File(filesDir, "maps/plan/map.md").apply { parentFile!!.mkdirs() }
        map.writeText("# Plan\n- what was here\n")
        val text = "# Plan\n- the backed-up version\n".toByteArray()
        restoreFile(map, BackupAgent.TYPE_FILE, text.size.toLong(), 1_760_001_000, filesDir, androidReads, android(text.copyOf(9), 1_760_001_000))

        assertEquals("# Plan\n- what was here\n", map.readText())
        assertEquals(listOf("map.md"), map.parentFile!!.list()!!.toList())
    }

    @Test
    fun aTimeAlreadyInMillisecondsIsLeftAsItIs() {
        val picture = File(filesDir, "maps/trip/media/tram.jpg").apply { parentFile!!.mkdirs() }
        restoreFile(picture, BackupAgent.TYPE_FILE, 64, 1_760_000_000_123, filesDir, androidReads, android(ByteArray(64) { it.toByte() }, 1_760_000_000_123))

        assertEquals(1_760_000_000_123, picture.lastModified())
        assertFalse(File(picture.parentFile, ".tram.jpg.partial").exists())
    }

    @Test
    fun aFolderIsMadeWhereItIsWithItsTimeFixed() {
        val media = File(filesDir, "maps/trip/media")
        val made = mutableListOf<File>()

        restoreFile(media, BackupAgent.TYPE_DIRECTORY, 0, 1_760_000_000, filesDir, androidReads) { target ->
            made += target
            target.mkdirs()
            target.setLastModified(1_760_000_000)
        }

        assertEquals(listOf(media), made)
        assertEquals(1_760_000_000_000, media.lastModified())
    }
}
