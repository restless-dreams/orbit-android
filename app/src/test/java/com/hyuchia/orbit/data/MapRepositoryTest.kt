package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MapRepositoryTest {
    private val files: File = Files.createTempDirectory("orbit-maps").toFile()
    private val maps = File(files, "maps")
    private val history = File(files, "history")
    private var now = 1_000_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private val starters = object : StarterMaps {
        override fun samples() = listOf("# Sample\n- One\n")

        override fun welcome() = "# Welcome\n- Hello\n"
    }

    private val words = FileWords(Locale.US)

    private fun repository() = MapRepository(maps, File(files, "trash"), starters, words, clock = { now })

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    private suspend fun loaded(): MapRepository = repository().also { it.load() }

    @Test
    fun `a first launch puts the starter maps in, in this layout`() = runTest {
        val repository = loaded()
        assertEquals(setOf("Sample", "Welcome"), repository.maps.value!!.map { it.title }.toSet())
        assertTrue(File(maps, ".layout.json").readText().contains("\"version\":$LAYOUT_VERSION"))
        assertTrue(repository.writable.value)
    }

    @Test
    fun `a write keeps the map as it was when the visit begins, and then every ten minutes`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        assertEquals(WriteResult.Written, repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always))
        now += minute
        repository.write(id, "# Plan\n- A\n- B\n")
        assertEquals(listOf(0), repository.versions(id).map { it.ideas })
        now += 11 * minute
        repository.write(id, "# Plan\n- A\n- B\n- C\n")
        assertEquals(listOf(2, 0), repository.versions(id).map { it.ideas })
    }

    @Test
    fun `a save made before the map changed elsewhere isn't written, and is kept in the history`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        // Another device's version arrives…
        repository.write(id, "# Plan\n- From the laptop\n")
        // …while this screen still saves on top of what it had.
        assertEquals(WriteResult.Changed, repository.write(id, "# Plan\n- From the phone\n", base = "# Plan\n"))
        assertEquals("# Plan\n- From the laptop\n", repository.read(id))
        assertTrue(repository.versions(id).any { repository.readVersion(id, it.time) == "# Plan\n- From the phone\n" })
    }

    @Test
    fun `text that would empty a map never replaces it`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n- A\n")
        assertEquals(WriteResult.Refused, repository.write(id, "#  <!-- orbit id: empty -->\n"))
        assertEquals(WriteResult.Refused, repository.write(id, ""))
        assertEquals("# Plan\n- A\n", repository.read(id))
    }

    @Test
    fun `a save for a map that's gone is said to be missing`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.delete(id)
        assertEquals(WriteResult.Missing, repository.write(id, "# Plan\n- A\n"))
    }

    @Test
    fun `a deleted map waits in the trash with its history, and comes back whole`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always)
        val trashed = repository.delete(id)!!
        assertFalse(repository.exists(id))
        assertEquals(listOf("Plan"), repository.trash.value.map { it.title })

        // Still there after a restart.
        val restarted = loaded()
        val again = restarted.trash.value.single()
        assertEquals(id, restarted.restore(again))
        assertEquals("# Plan\n- A\n", restarted.read(id))
        assertEquals(1, restarted.versions(id).size)
        assertTrue(restarted.trash.value.isEmpty())
        assertNotNull(trashed)
    }

    @Test
    fun `a map restored after another took its name comes back beside it`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val trashed = repository.delete(id)!!
        // A new map can't take the name of one in the trash…
        val newer = repository.add("# Plan\n- Newer\n")
        assertNotEquals(id, newer)
        // …and the one from the trash keeps its own.
        assertEquals(id, repository.restore(trashed))
        assertEquals(2, repository.maps.value!!.count { it.title == "Plan" })
    }

    @Test
    fun `the trash lets maps go after thirty days`() = runTest {
        val repository = loaded()
        repository.delete(repository.add("# Old\n"))
        now += 29 * day
        assertEquals(1, loaded().trash.value.size)
        now += 2 * day
        assertTrue(loaded().trash.value.isEmpty())
    }

    @Test
    fun `a file from elsewhere is kept as it came in, for good`() = runTest {
        val repository = loaded()
        val original = "---\ntags: [travel]\n---\n# Trip\n<!-- a draft -->\n"
        val id = repository.add(original, original = original)
        val kept = repository.versions(id).single()
        assertTrue(kept.original)
        assertEquals(original, repository.readVersion(id, kept.time))

        // Hundreds of later versions never thin it out.
        repeat(250) { step ->
            now += 11 * minute
            repository.write(id, "# Trip\n- Step $step\n")
        }

        assertTrue(repository.versions(id).any { it.original && repository.readVersion(id, it.time) == original })
    }

    @Test
    fun `details this version doesn't know are kept when it writes them`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val meta = File(File(maps, id), "meta.json")
        // A newer version wrote a field this one doesn't know, and one it reads differently.
        meta.writeText("""{"created":5,"uuid":"abc","pinned":false,"futureField":{"a":[1,2]},"rehearsal":"in a newer shape"}""")
        repository.setPinned(id, true)
        val written = meta.readText()
        assertTrue(written.contains("\"futureField\":{\"a\":[1,2]}"))
        assertTrue(written.contains("\"rehearsal\":\"in a newer shape\""))
        assertTrue(written.contains("\"pinned\":true"))
        assertTrue(written.contains("\"uuid\":\"abc\""))
    }

    @Test
    fun `details that can't be read at all are moved aside, not written over`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val folder = File(maps, id)
        File(folder, "meta.json").writeText("{ not json")
        repository.setPinned(id, true)
        assertEquals(1, folder.list()!!.count { it.startsWith("meta.json.unreadable-") })
        assertEquals("{ not json", folder.listFiles()!!.single { it.name.startsWith("meta.json.unreadable-") }.readText())
    }

    @Test
    fun `older files are brought to this layout, after a copy of them is kept`() = runTest {
        maps.mkdirs()
        // Before layouts: one map as a lone file, another in its folder with its history inside.
        File(maps, "Lone.md").writeText("# Lone\n")
        File(maps, "plan/history").mkdirs()
        File(maps, "plan/map.md").writeText("# Plan\n- A\n")
        File(maps, "plan/history/123.md").writeText("# Plan\n")
        val repository = loaded()
        assertEquals(setOf("Lone", "Plan"), repository.maps.value!!.map { it.title }.toSet())
        assertFalse(File(maps, "plan/history").exists())
        assertEquals("# Plan\n", File(history, "plan/123.md").readText())
        assertEquals(listOf(123L), repository.versions("plan").map { it.time })
        val backup = File(files, "backups").listFiles()!!.single()
        assertEquals("# Plan\n- A\n", File(backup, "maps/plan/map.md").readText())
    }

    @Test
    fun `history an older version wrote back into a map's folder joins the rest`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        // After going back to an older version for a while, which keeps history in the map's folder.
        File(maps, "$id/history").mkdirs()
        File(maps, "$id/history/456.md").writeText("# Plan, older\n")
        loaded()
        assertEquals("# Plan, older\n", File(history, "$id/456.md").readText())
    }

    @Test
    fun `files a newer version laid out in a way this one can't change are only read`() = runTest {
        loaded()
        File(maps, ".layout.json").writeText("""{"version":${LAYOUT_VERSION + 1},"compatible":${LAYOUT_VERSION + 1}}""")
        val repository = loaded()
        assertFalse(repository.writable.value)
        val id = repository.maps.value!!.first().id
        val before = repository.read(id)
        assertEquals(WriteResult.Refused, repository.write(id, "# Changed\n"))
        assertNull(repository.delete(id))
        assertEquals(before, repository.read(id))
    }

    @Test
    fun `a newer layout this one may change is used as it is`() = runTest {
        loaded()
        File(maps, ".layout.json").writeText("""{"version":${LAYOUT_VERSION + 1},"compatible":1}""")
        val repository = loaded()
        assertTrue(repository.writable.value)
        assertTrue(File(maps, ".layout.json").readText().contains("\"version\":${LAYOUT_VERSION + 1}"))
    }

    @Test
    fun `a duplicate gets an identity of its own`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val uuid = repository.identity(id)
        val copy = repository.duplicate(id)
        assertEquals("Plan (copy)", repository.summary(copy)!!.title)
        assertNotEquals(uuid, repository.identity(copy))
    }

    @Test
    fun `the titles a copy gets and the reasons a zip is refused are in the app's language`() = runTest {
        val repository = MapRepository(maps, File(files, "trash"), starters, FileWords(Locale.forLanguageTag("es-MX")), clock = { now }).also { it.load() }
        val copy = repository.duplicate(repository.add("# Plan\n"))
        assertEquals("Plan (copia)", repository.summary(copy)!!.title)
        val slip = zip("map.md" to "# Trip\n".toByteArray(), "../map.md" to ByteArray(1))
        assertEquals("Ese archivo zip tiene un archivo que apunta fuera de él.", refused { repository.readBundle(ByteArrayInputStream(slip)) })
    }

    @Test
    fun `half-made maps a crash left behind are cleared, and never listed`() = runTest {
        loaded()
        File(maps, ".new-123").mkdirs()
        File(maps, ".new-123/map.md").writeText("# Half\n")
        val repository = loaded()
        assertFalse(File(maps, ".new-123").exists())
        assertTrue(repository.maps.value!!.none { it.title == "Half" })
    }

    @Test
    fun `a map whose file can't be read shows as such`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val file = File(maps, "$id/map.md")
        file.setReadable(false)

        try {
            val reloaded = loaded()
            assertTrue(reloaded.summary(id)!!.unreadable)
            // Nothing writes over it.
            assertEquals(WriteResult.Refused, reloaded.write(id, "# Plan\n- A\n"))
        } finally {
            file.setReadable(true)
        }

        assertEquals("# Plan\n", repository.read(id))
    }

    @Test
    fun `every map goes into one zip, and comes back from it on another device`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n")
        repository.addMediaFile(id, "picture-1.jpg") { it.write(byteArrayOf(1, 2, 3)); true }
        repository.write(id, "# Trip\n- Beach ![](media/picture-1.jpg)\n- Hike\n", checkpoint = Checkpoint.Always)
        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

        // Another device, with nothing but its starter maps.
        val other = File(files, "other")
        val elsewhere = MapRepository(File(other, "maps"), File(other, "trash"), starters, words, clock = { now })
        elsewhere.load()
        // Its own starter maps are the same text: they're here already.
        assertEquals(ImportedMaps(added = 1, alreadyHere = 2), elsewhere.importAll(ByteArrayInputStream(zip)))
        val trip = elsewhere.maps.value!!.single { it.title == "Trip" }.id
        assertEquals(repository.read(id), elsewhere.read(trip))
        assertTrue(elsewhere.picture(trip, "picture-1.jpg")!!.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(1, elsewhere.versions(trip).size)

        // The same zip again: those maps are here already.
        val again = elsewhere.importAll(ByteArrayInputStream(zip))
        assertEquals(0, again.added)
        assertEquals(3, again.alreadyHere)
    }

    @Test
    fun `a zip of a map gives its own map file before any other`() = runTest {
        val zip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("notes.md"))
                zip.write("# Notes\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("map.md"))
                zip.write("# The map\n".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        assertEquals("# The map\n", loaded().readBundle(ByteArrayInputStream(zip))!!.markdown)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private suspend fun refused(read: suspend () -> Unit): String? = try {
        read()
        null
    } catch (refused: RefusedZipException) {
        refused.message
    }

    @Test
    fun `a look at a map from a zip counts its pictures without reading them`() = runTest {
        val bundle = zip(
            "map.md" to "# Trip\n".toByteArray(),
            "media/one.jpg" to ByteArray(1_000),
            "media/two.jpg" to ByteArray(2_000),
        )
        val look = loaded().readBundle(ByteArrayInputStream(bundle), withPictures = false)!!
        assertEquals(2, look.pictureCount)
        assertTrue(look.pictures.isEmpty())
        val whole = loaded().readBundle(ByteArrayInputStream(bundle))!!
        assertEquals(2, whole.pictureCount)
        assertEquals(2_000, whole.pictures["two.jpg"]!!.size)
    }

    @Test
    fun `a map from elsewhere brings only pictures and videos into its media`() = runTest {
        val bundle = zip(
            "map.md" to "# Trip\n![Beach](media/beach.jpg)\n".toByteArray(),
            "media/beach.jpg" to ByteArray(10),
            "media/clip.mp4" to ByteArray(10),
            "media/diagram.SVG" to ByteArray(10),
            "media/page.html" to "<script>orbitNative.postMessage('x')</script>".toByteArray(),
            "media/script.js" to ByteArray(10),
            "media/no-extension" to ByteArray(10),
        )
        val whole = loaded().readBundle(ByteArrayInputStream(bundle))!!
        assertEquals(setOf("beach.jpg", "clip.mp4", "diagram.SVG"), whole.pictures.keys)
        assertEquals(3, whole.pictureCount)
    }

    @Test
    fun `media names are pictures and videos with names that stay in their folder`() {
        for (name in listOf("picture-1a2b.jpg", "photo.JPEG", "scan.png", "loop.gif", "shot.webp", "diagram.svg", "clip.mp4", "talk.webm")) {
            assertTrue(name, isMediaName(name))
        }

        for (name in listOf("page.html", "page.htm", "script.js", "notes.md", "data.json", "noextension", ".hidden.png", "../evil.png", "folder/evil.png", "evil.png ")) {
            assertFalse(name, isMediaName(name))
        }
    }

    @Test
    fun `a zip that unpacks to more than a map holds is refused`() = runTest {
        val repository = loaded()
        // Zeros pack small: two of these weigh little, and unpack to more than a map may.
        val big = ByteArray(50 * 1024 * 1024)
        val bomb = zip("map.md" to "# Trip\n".toByteArray(), "media/one.jpg" to big, "media/two.jpg" to big)
        assertTrue(bomb.size < 1024 * 1024)
        assertEquals("That zip unpacks to more than maps hold.", refused { repository.readBundle(ByteArrayInputStream(bomb)) })
        // Only counting pictures counts what they unpack to all the same.
        assertEquals("That zip unpacks to more than maps hold.", refused { repository.readBundle(ByteArrayInputStream(bomb), withPictures = false) })

        val crowded = zip(*Array(4_001) { "media/picture-$it.jpg" to ByteArray(1) })
        assertEquals("That zip holds too many files to be maps.", refused { repository.readBundle(ByteArrayInputStream(crowded)) })

        val longText = zip("map.md" to ByteArray(9 * 1024 * 1024) { 'a'.code.toByte() })
        assertEquals("A file in that zip is too big to be part of a map.", refused { repository.readBundle(ByteArrayInputStream(longText)) })
    }

    @Test
    fun `a zip with files that point outside it is refused`() = runTest {
        val repository = loaded()

        for (name in listOf("../map.md", "media/../../evil.jpg", "/data/map.md", "..\\map.md", "C:/map.md")) {
            val slip = zip("map.md" to "# Trip\n".toByteArray(), name to ByteArray(1))
            assertEquals(name, "That zip has a file that points outside it.", refused { repository.readBundle(ByteArrayInputStream(slip)) })
            assertEquals(name, "That zip has a file that points outside it.", refused { repository.importAll(ByteArrayInputStream(slip)) })
        }

        assertEquals(listOf("Sample", "Welcome"), repository.maps.value!!.map { it.title }.sorted())
    }

    @Test
    fun `a zip of every map is told apart from a zip of one`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n")
        val one = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()
        val every = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()
        assertFalse(repository.holdsManyMaps(ByteArrayInputStream(one)))
        assertTrue(repository.holdsManyMaps(ByteArrayInputStream(every)))
        // A zip of every map with only one in it still carries its details and history.
        val alone = zip("trip/map.md" to "# Trip\n".toByteArray(), "trip/meta.json" to "{}".toByteArray())
        assertTrue(repository.holdsManyMaps(ByteArrayInputStream(alone)))
        // A folder someone zipped with their map in it is one map.
        assertFalse(repository.holdsManyMaps(ByteArrayInputStream(zip("Trip/map.md" to "# Trip\n".toByteArray()))))
    }

    @Test
    fun `maps from a zip of every map are unpacked to files, which go once they're in`() = runTest {
        val repository = loaded()
        val archive = zip(
            "trip/map.md" to "# Trip\n- Beach ![](media/beach.jpg)\n".toByteArray(),
            "trip/media/beach.jpg" to ByteArray(3_000_000) { it.toByte() },
            "trip/history/1000.md" to "# Trip\n".toByteArray(),
            "trip/notes.txt" to "not part of a map".toByteArray(),
            "plan/map.md" to "# Plan\n".toByteArray(),
        )
        assertEquals(ImportedMaps(added = 2, alreadyHere = 0), repository.importAll(ByteArrayInputStream(archive)))
        val trip = repository.maps.value!!.single { it.title == "Trip" }.id
        assertTrue(repository.picture(trip, "beach.jpg")!!.readBytes().contentEquals(ByteArray(3_000_000) { it.toByte() }))
        assertEquals(1, repository.versions(trip).size)
        assertFalse(File(maps, "$trip/notes.txt").exists())
        assertEquals(emptyList<String>(), File(files, "unpacking").list().orEmpty().toList())
    }

    @Test
    fun `zips an import left unpacked are cleared`() = runTest {
        val leftover = File(files, "unpacking/left-behind/0/media")
        leftover.mkdirs()
        File(leftover, "picture.jpg").writeBytes(ByteArray(10))
        File(files, "unpacking/left-behind").setLastModified(now - 2 * 60 * minute)
        loaded()
        assertFalse(File(files, "unpacking/left-behind").exists())
    }

    @Test
    fun `a new picture is kept even before the text pointing at it is saved`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.addMediaFile(id, "picture-new.jpg") { it.write(byteArrayOf(1)); true }
        repository.removeUnusedPictures(id)
        assertNotNull(repository.picture(id, "picture-new.jpg"))
        now += 11 * minute
        File(maps, "$id/media/picture-new.jpg").setLastModified(now - 11 * minute)
        repository.removeUnusedPictures(id)
        assertNull(repository.picture(id, "picture-new.jpg"))
    }

    @Test
    fun `a map's zip carries the pictures and videos its text points at, and none its earlier versions kept`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n  [![](media/video-1.jpg)](media/video-1.mp4)\n")

        for ((name, size) in listOf("picture-1.jpg" to 10, "video-1.jpg" to 20, "video-1.mp4" to 3_000, "picture-old.jpg" to 40)) {
            repository.addMediaFile(id, name) { it.write(ByteArray(size)); true }
        }

        assertEquals(3_030L, repository.mediaSize(id))
        val zip = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()
        val bundle = repository.readBundle(ByteArrayInputStream(zip))!!
        assertEquals(setOf("picture-1.jpg", "video-1.jpg", "video-1.mp4"), bundle.pictures.keys)
        assertEquals(3_000, bundle.pictures["video-1.mp4"]!!.size)
    }

    @Test
    fun `a picture or video that went before it was copied leaves nothing behind`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n")
        repository.addMediaFile(id, "video-gone.mp4") { it.write(ByteArray(5)); false }
        assertNull(repository.picture(id, "video-gone.mp4"))
        assertEquals(emptyList<String>(), File(maps, "$id/media").list().orEmpty().toList())
    }

    @Test
    fun `a picture or video from elsewhere past its room is refused, and leaves nothing behind`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Tram [![](media/video-tram.jpg)](media/video-tram.mp4)\n")

        try {
            repository.addMediaFile(id, "video-tram.mp4", room = 100) { it.write(ByteArray(101)); true }
            fail("The video should have been refused")
        } catch (refused: MediaRefusedException) {
            assertNull(repository.picture(id, "video-tram.mp4"))
        }

        assertEquals(emptyList<String>(), File(maps, "$id/media").list().orEmpty().toList())
        assertEquals(100L, repository.addMediaFile(id, "video-tram.mp4", room = 100) { it.write(ByteArray(100)); true })
        assertEquals(100L, repository.mediaSize(id))
        assertEquals(0L, repository.mediaSize(id, "# Trip\n"))
    }

    @Test
    fun `a change from outside the screen only lands on the text it was made from`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        assertEquals(WriteResult.Changed, repository.change(id, "# Plan\n- Caught\n", base = "# Plan\n- Something else\n"))
        assertEquals(WriteResult.Written, repository.change(id, "# Plan\n- Caught\n", base = "# Plan\n"))
        assertEquals("# Plan\n- Caught\n", repository.read(id))
    }
}
