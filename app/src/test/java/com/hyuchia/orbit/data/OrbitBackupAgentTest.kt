package com.hyuchia.orbit.data

import android.app.backup.BackupAgent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OrbitBackupAgentTest {
    private val files: File = Files.createTempDirectory("orbit-backup").toFile()
    private val megabyte = 1024 * 1024

    private fun file(path: String, size: Int = 10, text: String? = null) {
        val file = File(files, path)
        file.parentFile!!.mkdirs()

        if (text != null) {
            file.writeText(text)
        } else {
            file.writeBytes(ByteArray(size))
        }
    }

    /** The files a backup takes, without the folders they're in. */
    private fun backup(quota: Long, newPhone: Boolean = false): Set<String> =
        backupFiles(files, quota, newPhone).filter { it.isFile }.map { it.relativeTo(files).invariantSeparatorsPath }.toSet()

    private fun path(file: File): String = file.relativeTo(files).invariantSeparatorsPath

    /** What a backup of `chosen` hands Android to write, in order, as [backUp] writes it; `writing` sees each as it goes. */
    private fun written(chosen: List<File>, privateMaps: Boolean, writing: (File) -> Unit = {}): List<String> {
        val written = mutableListOf<String>()

        backUp(files, chosen, privateMaps) { file ->
            written += path(file)
            writing(file)
        }

        return written
    }

    private val texts = setOf(
        "datastore/settings.preferences_pb",
        "maps/.layout.json",
        "maps/trip/map.md",
        "maps/trip/meta.json",
        "maps/walk/map.md",
    )

    @Before
    fun maps() {
        file("datastore/settings.preferences_pb")
        file("maps/.layout.json", text = "{}")
        file("maps/trip/map.md", text = "# Trip\n- Beach ![](media/picture-beach.jpg)\n- Tram [![](media/video-tram.jpg)](media/video-tram.mp4)\n")
        file("maps/trip/meta.json", text = "{}")
        file("maps/trip/media/picture-beach.jpg", 3 * megabyte)
        file("maps/trip/media/video-tram.jpg", 200_000)
        file("maps/trip/media/video-tram.mp4", 40 * megabyte)
        // Only an earlier version points at it.
        file("maps/trip/media/picture-old.jpg", 100_000)
        // A copy that never finished.
        file("maps/trip/media/.video-cut.mp4.partial", 1_000)
        // A write of the layout's record that never finished, beside the maps.
        file("maps/..layout.json.partial", text = "{")
        file("maps/walk/map.md", text = "# Walk\n- Hill [![](media/video-hill.jpg)](media/video-hill.mp4)\n")
        file("maps/walk/media/video-hill.jpg", 100_000)
        file("maps/walk/media/video-hill.mp4", 5 * megabyte)
        file("history/trip/1000.md", text = "# Trip\n- Beach ![](media/picture-old.jpg)\n")
        file("trash/plan-2000/map.md", text = "# Plan\n")
        file("folder-deletions.json", text = "[]")
    }

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    @Test
    fun `a backup to the cloud takes the maps and settings, and the pictures and videos that fit in its quota`() {
        assertEquals(
            texts + setOf(
                "maps/walk/media/video-hill.jpg",
                "maps/trip/media/video-tram.jpg",
                "maps/trip/media/picture-beach.jpg",
                "maps/walk/media/video-hill.mp4",
            ),
            backup(quota = 25L * megabyte),
        )
    }

    @Test
    fun `pictures that don't all fit go smallest first`() {
        assertEquals(texts + setOf("maps/walk/media/video-hill.jpg", "maps/trip/media/video-tram.jpg"), backup(quota = 2L * megabyte))
    }

    @Test
    fun `past the quota, the maps' text goes the most recently changed first, each map whole, and the rest is said to be left out`() {
        File(files, "maps/trip/map.md").setLastModified(2_000_000_000_000)
        File(files, "maps/walk/map.md").setLastModified(1_000_000_000_000)
        file("maps/walk/notes.txt", 400_000)
        // Room for the settings, the layout's record and the trip, not the walk with its notes as well, nor the trip's pictures.
        val quota = 1024L * 1024 + 100_000
        val chosen = backupFiles(files, quota, newPhone = false)
        assertEquals(setOf("datastore/settings.preferences_pb", "maps/.layout.json", "maps/trip/map.md", "maps/trip/meta.json"), chosen.filter { it.isFile }.map { it.relativeTo(files).invariantSeparatorsPath }.toSet())
        assertEquals(1, mapsLeftOut(files, chosen))
        assertEquals(0, mapsLeftOut(files, backupFiles(files, 25L * megabyte, newPhone = false)))
        // Nothing fits in nothing, and nothing goes.
        assertEquals(emptyList<File>(), backupFiles(files, 0, newPhone = false))
    }

    @Test
    fun `a widget's choice of map stays on this phone, where its widget is`() {
        file("datastore/appWidget-12.preferences_pb")
        assertTrue(backup(quota = 25L * megabyte).none { "appWidget" in it })
        assertTrue(backup(quota = Long.MAX_VALUE, newPhone = true).none { "appWidget" in it })
    }

    @Test
    fun `each folder comes before what's in it, so a new phone has it to restore into`() {
        val chosen = backupFiles(files, quota = Long.MAX_VALUE, newPhone = true).map { it.relativeTo(files).invariantSeparatorsPath }
        val folders = listOf("datastore", "history", "history/trip", "maps", "maps/trip", "maps/trip/media", "maps/walk", "maps/walk/media", "trash", "trash/plan-2000")
        assertEquals(folders, chosen.filter { File(files, it).isDirectory }.sorted())

        for ((index, path) in chosen.withIndex()) {
            val parent = path.substringBeforeLast('/', "")

            if (parent.isNotEmpty()) {
                assertTrue("$parent comes before $path", chosen.indexOf(parent) in 0 until index)
            }

            // Right before the first of its files: a map's folder, which names it, goes as its own files do.
            if (File(files, path).isDirectory) {
                assertTrue("$path comes right before what's in it", chosen[index + 1].startsWith("$path/"))
            }
        }

        // Only the folders of what goes: a picture that doesn't fit brings no folder of its own.
        assertEquals(listOf("datastore", "maps", "maps/trip", "maps/walk"), backupFiles(files, 1024L * 1024 + 50_000, newPhone = false).filter { it.isDirectory }.map { it.relativeTo(files).invariantSeparatorsPath }.sorted())
    }

    @Test
    fun `moving to a new phone takes it all, the history and what only it points at, the trash, and the folder's deletions still to make`() {
        assertEquals(
            texts + setOf(
                "maps/walk/media/video-hill.jpg",
                "maps/trip/media/video-tram.jpg",
                "maps/trip/media/picture-beach.jpg",
                "maps/walk/media/video-hill.mp4",
                "maps/trip/media/video-tram.mp4",
                "history/trip/1000.md",
                "maps/trip/media/picture-old.jpg",
                "trash/plan-2000/map.md",
                "folder-deletions.json",
            ),
            backup(quota = Long.MAX_VALUE, newPhone = true),
        )
    }

    @Test
    fun `sounds go after the pictures and before the videos, a voice memo among them`() {
        file("maps/walk/map.md", text = "# Walk <!-- orbit audio: media/memo-walk.mp4 -->\n- Hill [![](media/video-hill.jpg)](media/video-hill.mp4)\n- Wind <!-- orbit audio: media/sound-wind.mp3 -->\n")
        file("maps/walk/media/memo-walk.mp4", 2 * megabyte)
        file("maps/walk/media/sound-wind.mp3", megabyte)
        val pictures = setOf("maps/walk/media/video-hill.jpg", "maps/trip/media/video-tram.jpg", "maps/trip/media/picture-beach.jpg")
        val sounds = setOf("maps/walk/media/sound-wind.mp3", "maps/walk/media/memo-walk.mp4")
        // Room for the pictures and the sounds, and not for the hill's video after them.
        assertEquals(texts + pictures + sounds, backup(quota = 8L * megabyte))
        // Room for the pictures and the smaller sound only.
        assertEquals(texts + pictures + "maps/walk/media/sound-wind.mp3", backup(quota = 6L * megabyte))
    }

    @Test
    fun `private maps go only into a backup that's end-to-end encrypted, or straight to a new phone`() {
        assertFalse(takesPrivateMaps(0))
        assertTrue(takesPrivateMaps(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED))
        assertTrue(takesPrivateMaps(BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER))
        assertTrue(takesPrivateMaps(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED or BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER))
    }

    @Test
    fun `a backup that isn't encrypted takes every map but the private ones, and an encrypted one takes them all`() {
        file("maps/diary/map.md", text = "# Diary\n- Today ![](media/picture-today.jpg)\n")
        file("maps/diary/meta.json", text = "{\"private\":true}")
        file("maps/diary/media/picture-today.jpg", 100_000)
        file("history/diary/1000.md", text = "# Diary\n")
        val diary = setOf("maps/diary/map.md", "maps/diary/meta.json", "maps/diary/media/picture-today.jpg")

        // Without end-to-end encryption, the diary stays out, its folders too; the ordinary maps and the settings go as ever.
        val plain = backupFiles(files, Long.MAX_VALUE, newPhone = true, privateMaps = false).map { it.relativeTo(files).invariantSeparatorsPath }
        assertTrue(plain.none { it.startsWith("maps/diary") || it.startsWith("history/diary") })
        assertTrue(plain.containsAll(texts + "history/trip/1000.md" + "maps/trip/media/video-tram.mp4"))

        // Encrypted, or to a new phone, it goes with the rest, its history included.
        val encrypted = backupFiles(files, Long.MAX_VALUE, newPhone = true, privateMaps = true).map { it.relativeTo(files).invariantSeparatorsPath }
        assertTrue(encrypted.containsAll(diary + "history/diary/1000.md" + "maps/diary" + "maps/diary/media"))
        assertTrue(encrypted.containsAll(plain))
    }

    @Test
    fun `a private map in the trash stays out of a backup that can't take private maps, and isn't counted as one without room`() {
        file("trash/diary-3000/map.md", text = "# Diary\n")
        file("trash/diary-3000/meta.json", text = "{\"private\":true}")
        file("maps/diary/map.md", text = "# Diary\n")
        file("maps/diary/meta.json", text = "{\"private\":true}")
        val plain = backupFiles(files, Long.MAX_VALUE, newPhone = true, privateMaps = false)
        val paths = plain.map { it.relativeTo(files).invariantSeparatorsPath }
        assertTrue(paths.none { it.startsWith("trash/diary") || it.startsWith("maps/diary") })
        assertTrue("trash/plan-2000/map.md" in paths)
        assertEquals(0, mapsLeftOut(files, plain, privateMaps = false))
    }

    @Test
    fun `a backup without private maps carries how many it left out, a count and nothing else`() {
        file("maps/diary/map.md", text = "# Diary\n- Today\n")
        file("maps/diary/meta.json", text = "{\"private\":true,\"cover\":{\"name\":\"Notes\"}}")
        file("maps/letters/map.md", text = "# Letters\n")
        file("maps/letters/meta.json", text = "{\"private\":true}")

        var count: String? = null
        val taken = written(backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = false), privateMaps = false) { file -> if (file.name == ".private-maps-left-out") count = file.readText() }
        // Two maps, said as a number: no folder, title or cover. It goes last, once every map's privacy is known.
        assertEquals("2", count)
        assertEquals("maps/.private-maps-left-out", taken.last())
        assertTrue(taken.none { it.startsWith("maps/diary") || it.startsWith("maps/letters") })
        // The backup deletes it once it's made: the phone it came from never says it.
        assertFalse(File(files, "maps/.private-maps-left-out").exists())
        keepRestoredPrivateMapsNote(files)
        assertEquals(0, privateMapsLeftOutOfRestore(files))
    }

    @Test
    fun `a backup that takes private maps, or has none to leave out, carries no count`() {
        file("maps/diary/map.md", text = "# Diary\n")
        file("maps/diary/meta.json", text = "{\"private\":true}")
        // A count an earlier backup left by a crash goes too, and no backup takes it.
        file("maps/.private-maps-left-out", text = "1")

        assertTrue(written(backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = true), privateMaps = true).none { it.endsWith(".private-maps-left-out") })
        assertFalse(File(files, "maps/.private-maps-left-out").exists())
        File(files, "maps/diary/meta.json").writeText("{}")
        assertTrue(written(backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = false), privateMaps = false).none { it.endsWith(".private-maps-left-out") })
    }

    @Test
    fun `a map still being made, or one a crash left half made, never goes into a backup`() {
        // A private map being copied: its text and pictures are there, its details saying it's private not yet.
        file("maps/.new-1234/map.md", text = "# Diary\n- Today ![](media/picture-today.jpg)\n")
        file("maps/.new-1234/media/picture-today.jpg", 100_000)

        for (privateMaps in listOf(true, false)) {
            val chosen = backupFiles(files, Long.MAX_VALUE, newPhone = true, privateMaps = privateMaps)
            assertTrue(chosen.map(::path).none { it.startsWith("maps/.new-") })
            assertTrue(written(chosen, privateMaps).none { it.startsWith("maps/.new-") })
        }

        // Nor is it a map without room, nor a private one left out.
        assertEquals(0, mapsLeftOut(files, backupFiles(files, 1024L * 1024 + 100_000, newPhone = false), privateMaps = false))
        assertTrue(written(backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = false), privateMaps = false).none { it.endsWith(".private-maps-left-out") })
    }

    @Test
    fun `a map made private once the backup chose its files stays out of it, its folder too, and is counted`() {
        file("maps/diary/map.md", text = "# Diary\n- Today ![](media/picture-today.jpg)\n")
        file("maps/diary/meta.json", text = "{}")
        file("maps/diary/media/picture-today.jpg", 100_000)
        val chosen = backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = false)
        assertTrue("maps/diary/map.md" in chosen.map(::path))

        // Made private while the backup is written, before Android reads the diary's files.
        File(files, "maps/diary/meta.json").writeText("{\"private\":true}")
        var count: String? = null
        val written = written(chosen, privateMaps = false) { file -> if (file.name == ".private-maps-left-out") count = file.readText() }
        assertTrue(written.none { it.startsWith("maps/diary") })
        assertTrue(written.containsAll(texts))
        assertEquals("1", count)
        assertFalse(File(files, "maps/.private-maps-left-out").exists())
    }

    @Test
    fun `a map made private partway through a backup keeps the rest of its files out`() {
        file("maps/diary/map.md", text = "# Diary\n- Today ![](media/picture-today.jpg)\n")
        file("maps/diary/meta.json", text = "{}")
        file("maps/diary/media/picture-today.jpg", 100_000)
        val chosen = backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = false)

        val written = written(chosen, privateMaps = false) { file ->
            if (path(file) == "maps/diary/map.md") {
                File(files, "maps/diary/meta.json").writeText("{\"private\":true}")
            }
        }

        // Its text went while it wasn't private; its picture, written after every map's text, doesn't.
        assertTrue("maps/diary/map.md" in written)
        assertFalse("maps/diary/media/picture-today.jpg" in written)
        assertTrue("maps/trip/media/picture-beach.jpg" in written)
    }

    @Test
    fun `a map whose details can't be read stays out of a backup that can't take private maps`() {
        file("maps/diary/map.md", text = "# Diary\n")
        file("maps/diary/meta.json", text = "{ not json")
        file("maps/letters/map.md", text = "# Letters\n")
        file("maps/letters/meta.json", text = "{\"private\":\"yes\"}")
        file("maps/notes/map.md", text = "# Notes\n")
        file("maps/notes/meta.json", text = "{\"private\":true}")
        File(files, "maps/notes/meta.json").setReadable(false)

        try {
            val chosen = backupFiles(files, Long.MAX_VALUE, newPhone = false, privateMaps = false)
            val kept = listOf("maps/diary", "maps/letters", "maps/notes")
            assertTrue(chosen.map(::path).none { path -> kept.any { path.startsWith(it) } })
            var count: String? = null
            assertTrue(written(chosen, privateMaps = false) { file -> if (file.name == ".private-maps-left-out") count = file.readText() }.none { path -> kept.any { path.startsWith(it) } })
            assertEquals("3", count)
            assertEquals(0, mapsLeftOut(files, chosen, privateMaps = false))
        } finally {
            File(files, "maps/notes/meta.json").setReadable(true)
        }
    }

    @Test
    fun `a restore keeps the count where Home reads it, until it has said it`() {
        // As Android restores it, beside the maps, where the maps aren't read from.
        file("maps/.private-maps-left-out", text = "3")

        keepRestoredPrivateMapsNote(files)
        assertFalse(File(files, "maps/.private-maps-left-out").exists())
        assertEquals(3, privateMapsLeftOutOfRestore(files))
        // Still there, should Orbit close before Home says it.
        assertEquals(3, privateMapsLeftOutOfRestore(files))
        forgetPrivateMapsLeftOutOfRestore(files)
        assertEquals(0, privateMapsLeftOutOfRestore(files))
    }
}
