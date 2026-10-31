package com.hyuchia.orbit.data

import android.app.backup.BackupAgent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * OrbitBackupAgent's part of a restore (`restoreFile`): where each file is
 * written, what takes its place, and its time. Android's own part, the bytes
 * into the file it's given, stands in here as it behaves: it writes them,
 * then gives the file its time as it came, in seconds. (Run on Robolectric,
 * Android's part writes nothing: it first checks the app's backup rules
 * against system state Robolectric doesn't set up.)
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

    @Test
    fun aMapsFileComesBackWholeAtTheTimeItLastChanged() {
        val map = File(filesDir, "maps/trip/map.md").apply { parentFile!!.mkdirs() }
        val text = "# Trip\n- Lisbon\n- Porto\n".toByteArray()
        val written = mutableListOf<String>()

        restoreFile(map, BackupAgent.TYPE_FILE, 1_760_000_000, filesDir) { target ->
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
    fun aFileTheRulesNameOnItsOwnIsWrittenWhereItIs() {
        // Beside it, it wouldn't be restored at all: the rules name the file, not its folder.
        val deletions = File(filesDir, "folder-deletions.json")
        val json = """{"deleted":["Old plan.md"]}""".toByteArray()
        val written = mutableListOf<String>()

        restoreFile(deletions, BackupAgent.TYPE_FILE, 1_760_000_500, filesDir) { target ->
            written += target.name
            android(json, 1_760_000_500)(target)
        }

        assertEquals(listOf("folder-deletions.json"), written)
        assertArrayEquals(json, deletions.readBytes())
        assertEquals(1_760_000_500_000, deletions.lastModified())
    }

    @Test
    fun aRestoredFileTakesThePlaceOfWhatWasThere() {
        val map = File(filesDir, "maps/plan/map.md").apply { parentFile!!.mkdirs() }
        map.writeText("# Plan\n- an older version\n")
        val text = "# Plan\n- the backed-up version\n".toByteArray()
        restoreFile(map, BackupAgent.TYPE_FILE, 1_760_001_000, filesDir, android(text, 1_760_001_000))

        assertArrayEquals(text, map.readBytes())
        assertTrue(map.parentFile!!.list()!!.none { it.endsWith(".partial") })
    }

    @Test
    fun aRestoreStoppedPartwayNeverTakesThePlaceOfWhatsThere() {
        val map = File(filesDir, "maps/plan/map.md").apply { parentFile!!.mkdirs() }
        map.writeText("# Plan\n- what was here\n")

        val stopped = runCatching {
            restoreFile(map, BackupAgent.TYPE_FILE, 1_760_001_000, filesDir) { target ->
                target.writeText("# Pl")
                throw IOException("The backup's stream ended")
            }
        }

        assertTrue(stopped.exceptionOrNull() is IOException)
        assertEquals("# Plan\n- what was here\n", map.readText())
    }

    @Test
    fun aTimeAlreadyInMillisecondsIsLeftAsItIs() {
        val picture = File(filesDir, "maps/trip/media/tram.jpg").apply { parentFile!!.mkdirs() }
        restoreFile(picture, BackupAgent.TYPE_FILE, 1_760_000_000_123, filesDir, android(ByteArray(64) { it.toByte() }, 1_760_000_000_123))

        assertEquals(1_760_000_000_123, picture.lastModified())
        assertFalse(File(picture.parentFile, ".tram.jpg.partial").exists())
    }

    @Test
    fun aFolderIsMadeWhereItIsWithItsTimeFixed() {
        val media = File(filesDir, "maps/trip/media")
        val made = mutableListOf<File>()

        restoreFile(media, BackupAgent.TYPE_DIRECTORY, 1_760_000_000, filesDir) { target ->
            made += target
            target.mkdirs()
            target.setLastModified(1_760_000_000)
        }

        assertEquals(listOf(media), made)
        assertEquals(1_760_000_000_000, media.lastModified())
    }
}
