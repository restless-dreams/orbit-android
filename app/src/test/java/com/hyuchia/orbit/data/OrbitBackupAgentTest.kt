package com.hyuchia.orbit.data

import org.junit.After
import org.junit.Assert.assertEquals
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
    private fun backup(quota: Long, history: Boolean = false): Set<String> =
        backupFiles(files, quota, history).filter { it.isFile }.map { it.relativeTo(files).invariantSeparatorsPath }.toSet()

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
    fun `pictures that don't all fit go smallest first, and the text goes whatever the quota`() {
        assertEquals(texts + setOf("maps/walk/media/video-hill.jpg", "maps/trip/media/video-tram.jpg"), backup(quota = 2L * megabyte))
        assertEquals(texts, backup(quota = 0))
    }

    @Test
    fun `each folder comes before what's in it, so a new phone has it to restore into`() {
        val chosen = backupFiles(files, quota = Long.MAX_VALUE, history = true).map { it.relativeTo(files).invariantSeparatorsPath }
        val folders = listOf("datastore", "history", "history/trip", "maps", "maps/trip", "maps/trip/media", "maps/walk", "maps/walk/media")
        assertEquals(folders, chosen.take(folders.size).sorted())

        for ((index, path) in chosen.withIndex()) {
            val parent = path.substringBeforeLast('/', "")

            if (parent.isNotEmpty()) {
                assertTrue("$parent comes before $path", chosen.indexOf(parent) in 0 until index)
            }
        }

        assertEquals(listOf("datastore", "maps", "maps/trip", "maps/trip/media", "maps/walk", "maps/walk/media"), backupFiles(files, 0, history = false).filter { it.isDirectory }.map { it.relativeTo(files).invariantSeparatorsPath })
    }

    @Test
    fun `moving to a new phone takes it all, the history and what only it points at too`() {
        assertEquals(
            texts + setOf(
                "maps/walk/media/video-hill.jpg",
                "maps/trip/media/video-tram.jpg",
                "maps/trip/media/picture-beach.jpg",
                "maps/walk/media/video-hill.mp4",
                "maps/trip/media/video-tram.mp4",
                "history/trip/1000.md",
                "maps/trip/media/picture-old.jpg",
            ),
            backup(quota = Long.MAX_VALUE, history = true),
        )
    }
}
