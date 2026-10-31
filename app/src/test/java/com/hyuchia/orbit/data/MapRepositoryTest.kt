package com.hyuchia.orbit.data

import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.study.StudyCard
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class MapRepositoryTest {
    private val files: File = Files.createTempDirectory("orbit-maps").toFile()
    private val maps = File(files, "maps")
    private val history = File(files, "history")
    private var now = 1_000_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private val starters = object : StarterMaps {
        override fun samples() = listOf("# Sample\n- One\n")

        override fun welcome() = StarterMap("# Welcome\n- Hello\n")
    }

    private val words = FileWords(Locale.US)

    private fun repository() = MapRepository(maps, File(files, "trash"), starters, words, clock = { now })

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    private suspend fun loaded(): MapRepository = repository().also { it.load() }

    @Test
    fun `a map whose original can't be kept, on a full disk say, is added all the same`() = runTest {
        val repository = loaded()
        // The history can't be written: where its folder goes, there's a file.
        history.deleteRecursively()
        history.writeText("")
        val id = repository.add("# Garden\n- Seeds\n", original = "<opml version=\"2.0\"/>")
        assertEquals(listOf("Garden"), repository.maps.value!!.filter { it.id == id }.map { it.title })
        assertEquals(listOf("Garden"), loaded().maps.value!!.filter { it.id == id }.map { it.title })
    }

    @Test
    fun `a map from another app comes with its pictures, and with nothing that isn't one`() = runTest {
        val repository = loaded()
        val id = repository.add(
            "# Garden\n![](media/plan.png)\n",
            original = "<opml version=\"2.0\"><body><outline text=\"Garden\"><outline text=\"Seeds\"/></outline></body></opml>",
            pictures = mapOf("plan.png" to ByteArray(10) { 7 }, "script.js" to ByteArray(3), "../outside.png" to ByteArray(3)),
        )

        assertEquals(10L, File(maps, "$id/media/plan.png").length())
        assertFalse(File(maps, "$id/media/script.js").exists())
        assertFalse(File(maps, "outside.png").exists())
        // The OPML it came from is kept in its history as it came in, counting its own ideas.
        val original = repository.versions(id).single()
        assertTrue(original.original)
        assertEquals(1, OtherApps.ideasIn(repository.readVersion(id, original.time)!!))
    }

    @Test
    fun `ideas pasted into another map bring the pictures and videos they point to`() = runTest {
        val repository = loaded()
        val from = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n")
        val to = repository.add("# Plans\n")
        File(maps, "$from/media").mkdirs()
        File(maps, "$from/media/picture-1.jpg").writeBytes(ByteArray(1_000) { 1 })
        File(maps, "$from/media/video-2.mp4").writeBytes(ByteArray(2_000) { 2 })
        File(maps, "$from/media/unused.jpg").writeBytes(ByteArray(10))

        val text = "# Beach <!-- orbit video: media/video-2.mp4 -->\n![](media/picture-1.jpg)\n![](media/missing.jpg)\n"

        assertEquals(text, repository.copyMedia(from, to, text))
        assertEquals(1_000L, File(maps, "$to/media/picture-1.jpg").length())
        assertEquals(2_000L, File(maps, "$to/media/video-2.mp4").length())
        assertFalse(File(maps, "$to/media/unused.jpg").exists())
        assertFalse(File(maps, "$to/media/missing.jpg").exists())
    }

    @Test
    fun `a pasted idea shows its own picture, beside one of the map's by the same name`() = runTest {
        val repository = loaded()
        val from = repository.add("# Trip\n")
        // Its own photo.jpg, from a zip, and an IMG_1234.jpg still on its way from the folder maps are kept in.
        val to = repository.add("# Plans\n- ![](media/photo.jpg)\n- ![](media/IMG_1234.jpg)\n")
        File(maps, "$from/media").mkdirs()
        File(maps, "$to/media").mkdirs()
        // Another picture of the same name and size; one the pasted ideas point at too; one pasted before; a live photo's motion.
        File(maps, "$from/media/photo.jpg").writeBytes(ByteArray(1_000) { 1 })
        File(maps, "$to/media/photo.jpg").writeBytes(ByteArray(1_000) { 2 })
        File(maps, "$from/media/photo-2.jpg").writeBytes(ByteArray(10) { 3 })
        File(maps, "$from/media/picture-1.jpg").writeBytes(ByteArray(500) { 4 })
        File(maps, "$to/media/picture-1.jpg").writeBytes(ByteArray(500) { 4 })
        File(maps, "$from/media/IMG_1234.jpg").writeBytes(ByteArray(20) { 5 })
        File(maps, "$from/media/lake-motion.mp4").writeBytes(ByteArray(30) { 6 })
        File(maps, "$to/media/lake-motion.mp4").writeBytes(ByteArray(40) { 7 })

        val pasted = repository.copyMedia(
            from,
            to,
            "- ![](media/photo.jpg) ![](media/photo-2.jpg) ![](./media/picture-1.jpg)\n- ![](media/IMG_1234.jpg) [See](https://example.com/media/photo.jpg)\n" +
                "- <!-- orbit image: { src: media/photo.jpg, motion: media/lake-motion.mp4 } -->\n",
        )

        assertEquals(
            "- ![](media/photo-3.jpg) ![](media/photo-2.jpg) ![](./media/picture-1.jpg)\n- ![](media/IMG_1234-2.jpg) [See](https://example.com/media/photo.jpg)\n" +
                "- <!-- orbit image: { src: media/photo-3.jpg, motion: media/lake-2-motion.mp4 } -->\n",
            pasted,
        )

        // The map's own as they were, and the pasted ones beside them, each holding what its idea shows.
        mapOf("photo.jpg" to 2, "photo-3.jpg" to 1, "photo-2.jpg" to 3, "picture-1.jpg" to 4, "IMG_1234-2.jpg" to 5, "lake-motion.mp4" to 7, "lake-2-motion.mp4" to 6).forEach { (name, byte) ->
            assertEquals(name, byte, File(maps, "$to/media/$name").readBytes().toSet().single().toInt())
        }

        // The map's own still on its way keeps its name for when it comes.
        assertFalse(File(maps, "$to/media/IMG_1234.jpg").exists())
    }

    @Test
    fun `pasting reaches nothing outside the maps`() = runTest {
        val repository = loaded()
        val from = repository.add("# Trip\n")
        val to = repository.add("# Plans\n")
        File(maps, "$from/media").mkdirs()
        File(maps, "$to/media").mkdirs()
        File(maps, "$from/media/picture-1.jpg").writeBytes(ByteArray(1_000))
        File(maps, "$to/media/picture-1.jpg").writeBytes(ByteArray(1_000))

        assertEquals("![](media/picture-1.jpg)", repository.copyMedia("../$from", to, "![](media/picture-1.jpg)"))
        assertEquals("![](media/../map.md) ![](media/.hidden)", repository.copyMedia(from, to, "![](media/../map.md) ![](media/.hidden)"))
        assertEquals(listOf("picture-1.jpg"), File(maps, "$to/media").list()!!.toList())
    }

    @Test
    fun `a new map can start with tags`() = runTest {
        val repository = loaded()
        val id = repository.create("Garden", tags = listOf("home"))

        assertEquals("---\ntags: [home]\n---\n\n# Garden\n", repository.read(id))
    }

    @Test
    fun `a folder that can't be copied whole stays where it was`() {
        val from = File(files, "history-from").apply { mkdirs() }
        File(from, "1000.md").writeText("one")
        File(from, "2000.md").writeText("two")
        val to = File(files, "history-to").apply { mkdirs() }

        // A folder it can't write into: the copy fails, and nothing of the history is deleted.
        to.setWritable(false)

        try {
            assertFalse(move(from, to))
            assertEquals(setOf("1000.md", "2000.md"), from.list()!!.toSet())
        } finally {
            to.setWritable(true)
        }
    }

    @Test
    fun `a folder moves whole, keeping the files the other side has already`() {
        val from = File(files, "history-from").apply { mkdirs() }
        File(from, "1000.md").writeText("one")
        File(from, "2000.md").writeText("two")
        val to = File(files, "history-to").apply { mkdirs() }
        File(to, "1000.md").writeText("kept")

        assertTrue(move(from, to))
        assertFalse(from.exists())
        assertEquals("kept", File(to, "1000.md").readText())
        assertEquals("two", File(to, "2000.md").readText())
        assertTrue(move(File(files, "history-to"), File(files, "history-elsewhere")))
        assertTrue(File(files, "history-elsewhere/2000.md").isFile)
    }

    @Test
    fun `a private map's cover is kept, and taken away when it's emptied`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Monday\n")
        repository.setPrivate(id, true)
        repository.setCover(id, LockedCover(icon = "📓", name = "Journal"))
        assertEquals(LockedCover(icon = "📓", name = "Journal"), repository.summary(id)!!.metadata.cover)

        repository.setCover(id, LockedCover(icon = " ", name = ""))
        assertEquals(null, repository.summary(id)!!.metadata.cover)
    }

    @Test
    fun `a private map's copy keeps its cover, saying it's a copy, and an open map's copy gets none`() = runTest {
        val repository = loaded()
        val journal = repository.add("# Journal\n- Monday\n")
        repository.setPrivate(journal, true)
        repository.setCover(journal, LockedCover(icon = "📓", name = "Journal"))
        assertEquals(LockedCover(icon = "📓", name = "Journal (copy)"), repository.summary(repository.duplicate(journal, "# Journal (copy)\n- Monday\n"))!!.metadata.cover)

        // A cover with only an emoji stays as it is.
        repository.setCover(journal, LockedCover(icon = "📓"))
        assertEquals(LockedCover(icon = "📓"), repository.summary(repository.duplicate(journal, "# Journal (copy)\n- Monday\n"))!!.metadata.cover)

        // Made open again, a map keeps its cover for when it's private again, but its copies don't take it.
        repository.setPrivate(journal, false)
        assertEquals(LockedCover(icon = "📓"), repository.summary(journal)!!.metadata.cover)
        assertEquals(null, repository.summary(repository.duplicate(journal, "# Journal (copy)\n- Monday\n"))!!.metadata.cover)
    }

    @Test
    fun `a deleted private map shows its cover in the trash`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Monday\n")
        repository.setPrivate(id, true)
        repository.setCover(id, LockedCover(icon = "📓", name = "Journal"))
        repository.delete(id)

        val trashed = repository.trash.value.single { it.title == "Journal" }
        assertTrue(trashed.isPrivate)
        assertEquals(LockedCover(icon = "📓", name = "Journal"), trashed.cover)
    }

    @Test
    fun `a first launch puts the starter maps in, in this layout`() = runTest {
        val repository = loaded()
        assertEquals(setOf("Sample", "Welcome"), repository.maps.value!!.map { it.title }.toSet())
        assertTrue(File(maps, ".layout.json").readText().contains("\"version\":$LAYOUT_VERSION"))
        assertTrue(repository.writable.value)
    }

    @Test
    fun `the welcome map comes in pinned, with its pictures, videos and sounds and its presentation path`() = runTest {
        val welcome = StarterMap(
            "# Welcome\n- A picture\n  ![](media/planet.webp)\n",
            media = mapOf("planet.webp" to ByteArray(12) { 3 }, "sound-tune.mp4" to ByteArray(5), "../outside.webp" to ByteArray(3)),
            metadata = MapMetadata(path = listOf("welcome", "a-picture")),
        )

        val onlyWelcome = object : StarterMaps {
            override fun samples() = emptyList<String>()

            override fun welcome() = welcome
        }

        val repository = MapRepository(maps, File(files, "trash"), onlyWelcome, words, clock = { now }).also { it.load() }
        val map = repository.maps.value!!.single()

        assertEquals("Welcome", map.title)
        assertTrue(map.metadata.pinned)
        assertEquals(now, map.metadata.created)
        assertEquals(listOf("welcome", "a-picture"), map.metadata.path)
        assertEquals(12L, File(maps, "${map.id}/media/planet.webp").length())
        assertEquals(5L, File(maps, "${map.id}/media/sound-tune.mp4").length())
        assertFalse(File(maps, "outside.webp").exists())
    }

    @Test
    fun `a map added again from the welcome map's assets keeps its presentation path`() = runTest {
        val repository = loaded()
        val id = repository.add("# Welcome\n- One\n", pinned = true, pictures = mapOf("planet.webp" to ByteArray(4)), path = listOf("welcome", "one"))
        val map = repository.summary(id)!!

        assertEquals(listOf("welcome", "one"), map.metadata.path)
        assertTrue(map.metadata.pinned)
        assertEquals(4L, File(maps, "$id/media/planet.webp").length())
    }

    @Test
    fun `a write keeps the map as it was when the visit begins, and then every ten minutes`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        assertEquals(WriteResult.Written, repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always))
        now += minute
        repository.write(id, "# Plan\n- A\n- B\n")
        assertEquals(listOf(0), repository.ideasInVersions(id))
        now += 11 * minute
        repository.write(id, "# Plan\n- A\n- B\n- C\n")
        assertEquals(listOf(2, 0), repository.ideasInVersions(id))
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
    fun `a version that can't be read is left out of the history, rather than stopping it`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always)
        val kept = repository.versions(id).single().time
        // Thinned out as the history was listed: the name is still there, but no file to read.
        val gone = kept + minute
        File(history, "$id/$gone.md").mkdirs()

        assertEquals(listOf(kept), repository.versions(id).map { it.time })
        assertNull(repository.readVersion(id, gone))
        assertEquals("# Plan\n", repository.readVersion(id, kept))
    }

    @Test
    fun `a map that can't be added says why, in the app's language`() = runTest {
        assertEquals("Orbit couldn’t add the map. Is this device’s storage full?", loaded().whyNotAdded())
        File(maps, ".layout.json").writeText("""{"version":${LAYOUT_VERSION + 1},"compatible":${LAYOUT_VERSION + 1}}""")
        val readOnly = loaded()

        try {
            readOnly.add("# Plan\n")
            fail("A map was added to maps a newer version keeps")
        } catch (refused: IOException) {
            assertEquals(readOnly.whyNotAdded(), refused.message)
        }

        assertEquals("A newer version of Orbit keeps these maps: update Orbit to change them.", readOnly.whyNotAdded())
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
    fun `a YAML map without ideas never replaces a map with some, from the map's screen or from the folder`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n- A\n")

        for (empty in listOf("nodes: []\n", "title: Plan\nnodes:\n", "nodes:\n  - text: \"\"\n", "title: Plan\n")) {
            assertEquals(WriteResult.Refused, repository.write(id, empty))
            assertFalse(repository.replaceFromFolder(id, empty, SyncRecord("folder", "Plan.md", digest(empty)), digest("# Plan\n- A\n")))
        }

        assertEquals("# Plan\n- A\n", repository.read(id))
        // One with ideas is a change like any other.
        assertTrue(repository.replaceFromFolder(id, "nodes:\n  - text: Plan\n", SyncRecord("folder", "Plan.md", digest("nodes:\n  - text: Plan\n")), digest("# Plan\n- A\n")))
        assertEquals(WriteResult.Written, repository.write(id, "nodes:\n  - text: Plan\n    children: [A]\n"))
    }

    @Test
    fun `a save for a map that's gone is said to be missing`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.delete(id)
        assertEquals(WriteResult.Missing, repository.write(id, "# Plan\n- A\n"))
    }

    @Test
    fun `a write the storage can't take leaves the map as it was, and says so rather than stopping`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n- A\n")
        val folder = File(maps, id)
        // A folder nothing can be written into fails each write as a full disk does.
        folder.setWritable(false)

        try {
            assertEquals(WriteResult.NotWritten, repository.write(id, "# Plan\n- A\n- B\n"))
            // A change from outside the map's screen, as Home's rename and tags make it.
            assertEquals(WriteResult.NotWritten, repository.change(id, "# Plans\n- A\n", base = "# Plan\n- A\n", reason = VersionReason.Renaming))
            assertEquals("# Plan\n- A\n", repository.read(id))
            assertEquals(listOf("map.md", "meta.json"), folder.list()!!.sorted())
        } finally {
            folder.setWritable(true)
        }

        // Once there's room, the same text is written whole.
        assertEquals(WriteResult.Written, repository.write(id, "# Plan\n- A\n- B\n"))
        assertEquals("# Plan\n- A\n- B\n", repository.read(id))
    }

    @Test
    fun `a version the storage can't take doesn't keep the change from being written, nor does a text set aside`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n- A\n")
        history.mkdirs()
        history.setWritable(false)

        try {
            assertEquals(WriteResult.Written, repository.write(id, "# Plan\n- B\n", checkpoint = Checkpoint.Always))
            repository.keepAside(id, "# Plan\n- C\n")
            repository.keepCurrentVersion(id)
            assertEquals("# Plan\n- B\n", repository.read(id))
        } finally {
            history.setWritable(true)
        }
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
        assertEquals(listOf<VersionReason?>(VersionReason.Editing), restarted.versions(id).map { it.reason })
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
    fun `the trash lets maps go after thirty days, once seen past them a day apart`() = runTest {
        val repository = loaded()
        repository.delete(repository.add("# Old\n"))
        now += 29 * day
        assertEquals(1, loaded().trash.value.size)
        now += 2 * day
        // Past its days, seen so once: a clock wrong for a while never takes it.
        assertEquals(1, loaded().trash.value.size)
        now += day
        assertTrue(loaded().trash.value.isEmpty())
    }

    @Test
    fun `a map deleted with the clock wrong keeps its thirty days in the trash`() = runTest {
        val repository = loaded()
        val installed = now
        // The clock behind, as after a restart without the network's time.
        now = installed - 3_000 * day
        repository.delete(repository.add("# Shopping\n"))
        now = installed + day
        val trashed = MapRepository(maps, File(files, "trash"), starters, words, clock = { now }, installed = installed).also { it.load() }.trash.value.single()
        assertEquals(now, trashed.deleted)

        // The clock ahead when it was deleted: its days start again from now too.
        now = installed + 400 * day
        repository.delete(repository.add("# Week plan\n"))
        now = installed + 2 * day
        val dates = MapRepository(maps, File(files, "trash"), starters, words, clock = { now }, installed = installed).also { it.load() }.trash.value.map { it.deleted }
        assertTrue(dates.all { it <= now })
        assertEquals(2, dates.size)
    }

    @Test
    fun `nothing is emptied from the trash or thinned from the history while the clock can't be trusted`() = runTest {
        val repository = loaded()
        repository.delete(repository.add("# Old\n"))
        now += 40 * day
        suspend fun untrusted() = MapRepository(maps, File(files, "trash"), starters, words, clock = { now }, clockTrusted = { false }).also { it.load() }
        assertEquals(1, untrusted().trash.value.size)
        now += 2 * day
        assertEquals(1, untrusted().trash.value.size)

        val wrong = untrusted()
        val id = wrong.add("# Plan\n")

        repeat(250) { step ->
            now += minute
            wrong.write(id, "# Plan\n- $step\n", checkpoint = Checkpoint.Always)
        }

        assertEquals(250, wrong.versions(id).size)
    }

    @Test
    fun `after the clock ran ahead once, new versions are named by the clock and kept every ten minutes`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val rightNow = now
        now = rightNow + 365 * day
        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always)
        now = rightNow

        repeat(4) { step ->
            now += minute
            repository.write(id, "# Plan\n- A\n- $step\n")
        }

        // The version a year ahead, and one of the four: the clock is right again, and a checkpoint was due once.
        val times = repository.versions(id).map { it.time }
        assertEquals(2, times.size)
        assertEquals(1, times.count { it <= now })
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
    fun `each version says why it was kept, and the ones from before reasons were kept say nothing`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        // A version an earlier release kept: just its file.
        val earlier = now - day
        File(history, "$id/$earlier.md").apply { parentFile!!.mkdirs() }.writeText("# Plan\n- From an earlier release\n")

        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always, reason = VersionReason.Opened)
        now += minute
        repository.write(id, "# Plan\n- B\n", checkpoint = Checkpoint.Always, reason = VersionReason.Removing("A", 2))
        now += minute
        repository.keepCurrentVersion(id, restoring = earlier)
        now += minute
        repository.write(id, "# Plan\n- C\n")
        repository.change(id, "# Plan\n- C\n- Caught\n", base = "# Plan\n- C\n")
        now += minute
        repository.change(id, MapTags.write("# Plan\n- C\n- Caught\n", listOf("work")), base = "# Plan\n- C\n- Caught\n", reason = VersionReason.Tags)
        now += minute
        val laptop = repository.add("# Plan\n- From the laptop\n")
        val bundle = ByteArrayOutputStream().also { repository.writeZip(laptop, it) }.toByteArray().let { repository.readBundle(ByteArrayInputStream(it)) }!!
        repository.receive(bundle, replacing = id, reason = VersionReason.FromDevice("Laptop"))
        now += minute
        repository.keepAside(id, "# Plan\n- Typed before the laptop's came\n")

        assertEquals(
            listOf(
                VersionReason.Aside,
                VersionReason.FromDevice("Laptop"),
                VersionReason.Tags,
                VersionReason.Caught,
                VersionReason.Restoring(earlier),
                VersionReason.Removing("A", 2),
                VersionReason.Opened,
                null,
            ),
            repository.versions(id).map { it.reason },
        )

        // A file as it came in says where from.
        val trip = repository.add("# Trip\n", original = "# Trip\n<!-- a draft -->\n", originalName = "Trip.md")
        assertEquals(VersionReason.CameIn("Trip.md"), repository.versions(trip).single().reason)
    }

    @Test
    fun `older releases list the versions alone, and what they thinned out loses its reason here`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always, reason = VersionReason.Opened)
        now += minute
        repository.write(id, "# Plan\n- B\n", checkpoint = Checkpoint.Always, reason = VersionReason.Text)
        val (newer, older) = repository.versions(id).map { it.time }

        // Every release up to 0.3.0 lists a history's files by this name, and thins out only `<time>.md` files.
        val releases = Regex("""(\d+)(\.original)?\.md""")
        val listed = File(history, id).list().orEmpty().filter { releases.matches(it) }.toSet()
        assertEquals(setOf("$newer.md", "$older.md"), listed)
        assertTrue(File(history, "$id/reasons.json").isFile)

        // An older release thins one out; this one keeps the next version's reason, and lets the gone one's go.
        File(history, "$id/$older.md").delete()
        now += minute
        repository.write(id, "# Plan\n- C\n", checkpoint = Checkpoint.Always, reason = VersionReason.Elsewhere)
        assertEquals(listOf<VersionReason?>(VersionReason.Elsewhere, VersionReason.Text), repository.versions(id).map { it.reason })
        assertFalse(File(history, "$id/reasons.json").readText().contains("$older.md"))
    }

    @Test
    fun `reasons this version doesn't know read as none, and stay with whatever else a newer version wrote`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always, reason = VersionReason.Opened)
        val kept = repository.versions(id).single().time
        // A newer Orbit's version, kept for a reason this one doesn't know, and fields it doesn't know.
        val newer = kept + minute
        File(history, "$id/$newer.md").writeText("# Plan\n- A\n- Newer\n")
        File(history, "$id/reasons.json").writeText(
            """{"format":2,"versions":{"$kept.md":{"why":"opened","mood":"calm"},"$newer.md":{"why":"dreamt","dream":"flying"}}}""",
        )

        assertEquals(listOf(null, VersionReason.Opened), repository.versions(id).map { it.reason })

        now = newer + minute
        repository.write(id, "# Plan\n- B\n", checkpoint = Checkpoint.Always, reason = VersionReason.Text)
        val reasons = File(history, "$id/reasons.json").readText()
        assertEquals(listOf(VersionReason.Text, null, VersionReason.Opened), repository.versions(id).map { it.reason })

        for (part in listOf(""""format":2""", """"mood":"calm"""", """"why":"dreamt"""", """"dream":"flying"""")) {
            assertTrue("$part stays in $reasons", part in reasons)
        }
    }

    @Test
    fun `a reasons file that can't be read leaves every version without one, and is written afresh`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        repository.write(id, "# Plan\n- A\n", checkpoint = Checkpoint.Always, reason = VersionReason.Opened)
        File(history, "$id/reasons.json").writeText("{ not json")

        assertEquals(listOf<VersionReason?>(null), repository.versions(id).map { it.reason })

        now += minute
        repository.write(id, "# Plan\n- B\n", checkpoint = Checkpoint.Always, reason = VersionReason.Text)
        assertEquals(listOf(VersionReason.Text, null), repository.versions(id).map { it.reason })
    }

    @Test
    fun `every map's zip carries why its versions were kept`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach\n")
        repository.write(id, "# Trip\n- Hike\n", checkpoint = Checkpoint.Always, reason = VersionReason.Removing("Beach", 2))
        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

        val other = File(files, "other")
        val elsewhere = MapRepository(File(other, "maps"), File(other, "trash"), starters, words, clock = { now }).also { it.load() }
        elsewhere.importAll(ByteArrayInputStream(zip))
        val trip = elsewhere.maps.value!!.single { it.title == "Trip" }.id

        assertEquals(listOf<VersionReason?>(VersionReason.Removing("Beach", 2)), elsewhere.versions(trip).map { it.reason })
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
    fun `studying keeps its progress in the map's details, never its text or its history`() = runTest {
        val repository = loaded()
        val id = repository.add("# Verbs\n## Ser\n- soy\n")
        val file = File(File(maps, id), "map.md")
        val text = file.readBytes()
        val modified = file.lastModified()
        val versions = repository.versions(id)
        now += day
        repository.saveStudy(id, mapOf("ser" to StudyCard(box = 1, due = 20_001, first = 20_000)))
        assertTrue(text.contentEquals(file.readBytes()))
        assertEquals(modified, file.lastModified())
        assertEquals(versions, repository.versions(id))
        assertEquals(StudyCard(box = 1, due = 20_001, first = 20_000), repository.summary(id)?.metadata?.study?.get("ser"))
        repository.saveStudy(id, emptyMap())
        assertNull(repository.summary(id)?.metadata?.study)
    }

    @Test
    fun `an idea's new id keeps its place on the path, its rehearsal and its study`() = runTest {
        val repository = loaded()
        val id = repository.add("# Verbs\n## Ser\n- soy\n")
        repository.savePath(id, listOf("verbs", "ser"))
        repository.saveRehearsal(id, mapOf("ser" to 12))
        repository.saveStudy(id, mapOf("ser" to StudyCard(box = 3, due = 20_007)))
        repository.renameIdea(id, "ser", "ser-to-be")
        val metadata = repository.summary(id)?.metadata
        assertEquals(listOf("verbs", "ser-to-be"), metadata?.path)
        assertEquals(mapOf("ser-to-be" to 12), metadata?.rehearsal)
        assertEquals(mapOf("ser-to-be" to StudyCard(box = 3, due = 20_007)), metadata?.study)
    }

    @Test
    fun `an idea's new id keeps how its card goes on every device as this device's answer, over an older idea's of that name`() = runTest {
        val repository = loaded()
        val id = repository.add("# Verbs\n## Ser\n- soy\n")
        val known = StudyCard(box = 2, due = 20_003, first = 19_990, at = 2_000)
        val tablet = mapOf("ser" to known, "estar" to StudyCard(box = 1, due = 20_001, at = 1_500))
        repository.saveStudy(id, mapOf("ser" to StudyCard(box = 1, due = 20_001, at = 1_000), "ser-to-be" to StudyCard(box = 5, due = 20_080, at = 500)))
        repository.saveStudyElsewhere(id, mapOf("tablet" to tablet))
        repository.renameIdea(id, "ser", "ser-to-be")
        val metadata = repository.summary(id)?.metadata
        assertEquals(mapOf("ser-to-be" to known), metadata?.study)
        // The other devices' answers stay as their records in the folder say, until they say otherwise.
        assertEquals(mapOf("tablet" to tablet), metadata?.studyElsewhere)
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
    fun `details that can't be read at all read as a private map's, until its owner makes it not private`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        File(maps, "$id/meta.json").writeText("{ not json")

        // Home, its search, widgets and shortcuts go by the list of maps: there, it's locked like any private map.
        val reloaded = loaded()
        assertTrue(reloaded.summary(id)!!.metadata.isPrivate)
        // Its details start afresh as it changes, still private.
        reloaded.markOpened(id)
        assertTrue(reloaded.summary(id)!!.metadata.isPrivate)
        assertTrue(loaded().summary(id)!!.metadata.isPrivate)
        reloaded.setPrivate(id, false)
        assertFalse(loaded().summary(id)!!.metadata.isPrivate)
    }

    @Test
    fun `a private field this version can't read keeps the map private, as it was written, until it's made not private`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        val meta = File(maps, "$id/meta.json")
        meta.writeText("""{"created":5,"private":{"since":7}}""")

        val reloaded = loaded()
        assertTrue(reloaded.summary(id)!!.metadata.isPrivate)
        reloaded.setPinned(id, true)
        assertTrue(meta.readText().contains("\"private\":{\"since\":7}"))
        assertTrue(loaded().summary(id)!!.metadata.isPrivate)

        reloaded.setPrivate(id, false)
        assertFalse(meta.readText().contains("private"))
        assertFalse(loaded().summary(id)!!.metadata.isPrivate)
    }

    @Test
    fun `details that can't be read for now are never written over, and read as a private map's meanwhile`() = runTest {
        val repository = loaded()
        val id = repository.add("# Verbs\n## Ser\n")
        repository.setPinned(id, true)
        repository.saveStudy(id, mapOf("ser" to StudyCard(box = 2, due = 20_003)))
        val meta = File(maps, "$id/meta.json")
        val details = meta.readText()
        meta.setReadable(false)

        try {
            val reloaded = loaded()
            assertTrue(reloaded.summary(id)!!.metadata.isPrivate)

            // What's in them isn't known: each change is refused rather than written over them, and the map stays.
            val fromFolder: suspend () -> Unit = { reloaded.replaceFromFolder(id, "# Verbs\n## Estar\n", SyncRecord("f", "Verbs.md", "a"), digest("# Verbs\n## Ser\n")) }

            for (change in listOf<suspend () -> Unit>({ reloaded.markOpened(id) }, { reloaded.setPrivate(id, false) }, { reloaded.identity(id) }, fromFolder)) {
                try {
                    change()
                    fail("Details that couldn't be read were written over")
                } catch (unreadable: IOException) {
                    // Refused, and said so to whoever changed them.
                }
            }

            assertNull(reloaded.delete(id))
            assertTrue(reloaded.exists(id))
        } finally {
            meta.setReadable(true)
        }

        assertEquals(details, meta.readText())
        assertEquals("# Verbs\n## Ser\n", repository.read(id))
        assertEquals(StudyCard(box = 2, due = 20_003), loaded().summary(id)!!.metadata.study!!["ser"])
    }

    @Test
    fun `a map in the trash whose details can't be read now waits there, rather than have them written over`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        val trashed = repository.delete(id)!!
        val meta = File(trashed.folder, "meta.json")
        val details = meta.readText()
        meta.setReadable(false)

        try {
            assertNull(repository.restore(trashed))
            assertTrue(trashed.folder.isDirectory)
        } finally {
            meta.setReadable(true)
        }

        assertEquals(details, meta.readText())
        assertEquals(id, repository.restore(trashed))
        assertTrue(repository.summary(id)!!.metadata.isPrivate)
    }

    @Test
    fun `a copy of a map whose details can't be read is private`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        File(maps, "$id/meta.json").writeText("{ not json")
        val reloaded = loaded()
        val copy = reloaded.duplicate(id, "# Journal (copy)\n- Today\n")
        assertTrue(reloaded.summary(copy)!!.metadata.isPrivate)
    }

    @Test
    fun `a map in the trash whose details can't be read is listed as a private one`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        val trashed = repository.delete(id)!!
        File(trashed.folder, "meta.json").writeText("{ not json")
        assertTrue(loaded().trash.value.single().isPrivate)
        File(trashed.folder, "meta.json").writeText("""{"created":5,"private":"yes"}""")
        assertTrue(loaded().trash.value.single().isPrivate)
    }

    @Test
    fun `a map from Export all whose details can't be read comes in private`() = runTest {
        val repository = loaded()

        val zip = zip(
            "journal/map.md" to "# Journal\n- Today\n".toByteArray(),
            "journal/meta.json" to "{ not json".toByteArray(),
            "letters/map.md" to "# Letters\n- Dear\n".toByteArray(),
            "letters/meta.json" to """{"created":5,"private":"yes"}""".toByteArray(),
            "trip/map.md" to "# Trip\n- Beach\n".toByteArray(),
            "trip/meta.json" to "{}".toByteArray(),
        )

        assertEquals(3, repository.importAll(ByteArrayInputStream(zip)).added)
        val private = repository.maps.value!!.associate { it.title to it.metadata.isPrivate }
        assertEquals(true, private["Journal"])
        assertEquals(true, private["Letters"])
        assertEquals(false, private["Trip"])
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
    fun `history a map back from the trash left in its folder joins the rest at the next launch`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        // Its history couldn't be moved as the map came back, and stayed where the trash keeps it.
        File(maps, "$id/.history").mkdirs()
        File(maps, "$id/.history/456.md").writeText("# Plan, older\n")
        val reloaded = loaded()
        assertEquals("# Plan, older\n", File(history, "$id/456.md").readText())
        assertFalse(File(maps, "$id/.history").exists())
        assertEquals(1, reloaded.versions(id).size)
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
    fun `a record of the layout that's there but can't be read leaves the maps only read, and as they are`() = runTest {
        loaded()

        for (record in listOf("""{"version":"three"}""", "not a record", """{"layout":{"version":3}}""")) {
            File(maps, ".layout.json").writeText(record)
            val repository = loaded()
            assertFalse(record, repository.writable.value)
            assertEquals(record, File(maps, ".layout.json").readText())
            assertEquals(WriteResult.Refused, repository.write(repository.maps.value!!.first().id, "# Changed\n"))
        }
    }

    @Test
    fun `a newer layout this one may change is used as it is`() = runTest {
        loaded()
        File(maps, ".layout.json").writeText("""{"version":${LAYOUT_VERSION + 1},"compatible":1}""")
        val repository = loaded()
        assertTrue(repository.writable.value)
        assertTrue(File(maps, ".layout.json").readText().contains("\"version\":${LAYOUT_VERSION + 1}"))
    }

    /** A map's folder as an earlier version left it: its text and its details as written. */
    private fun mapFolder(id: String, text: String, details: String) {
        File(maps, id).mkdirs()
        File(maps, "$id/map.md").writeText(text)
        File(maps, "$id/meta.json").writeText(details)
    }

    private val twin = "5d6e7f80-1a2b-4c3d-9e8f-7a6b5c4d3e2f"

    @Test
    fun `of two maps sharing an identity, the one made later gets its own, and nothing else of it changes`() = runTest {
        loaded()
        mapFolder("lisbon", "# Lisbon\n", """{"created":100,"uuid":"$twin"}""")
        mapFolder(
            "lisbon-2",
            "# Lisbon\n- Tram\n",
            """{"created":200,"uuid":"$twin","pinned":true,"study":{"tram":{"box":2,"due":20003}},"sync":{"folder":"f","file":"Lisbon.md","hash":"a"},"later":"kept"}""",
        )
        val repository = loaded()

        assertEquals(twin, repository.summary("lisbon")!!.metadata.uuid)
        val own = repository.summary("lisbon-2")!!.metadata
        assertNotEquals(twin, own.uuid)
        assertNotNull(own.uuid)
        // Sending, updates and offers find the one other devices know.
        assertEquals("lisbon", repository.findByIdentity(twin))
        assertEquals("lisbon-2", repository.findByIdentity(own.uuid!!))
        // Its text, its pin, its study, its record of the folder's file and what a newer version wrote stay as they were.
        assertEquals("# Lisbon\n- Tram\n", repository.read("lisbon-2"))
        assertTrue(own.pinned)
        assertEquals(2, own.study!!.getValue("tram").box)
        assertEquals("Lisbon.md", own.sync!!.file)
        val written = File(maps, "lisbon-2/meta.json").readText()
        assertTrue(written, written.contains("\"later\":\"kept\""))
        // The earlier one isn't written at all.
        assertEquals("""{"created":100,"uuid":"$twin"}""", File(maps, "lisbon/meta.json").readText())
        // Told apart once, they stay apart.
        assertEquals(own.uuid, loaded().summary("lisbon-2")!!.metadata.uuid)
    }

    @Test
    fun `with the same or an unknown time of making, the folders' names say which came first`() = runTest {
        loaded()
        mapFolder("b-plan", "# Plan\n", """{"created":100,"uuid":"$twin"}""")
        mapFolder("a-plan", "# Plan\n", """{"created":100,"uuid":"$twin"}""")
        mapFolder("c-plan", "# Plan\n", """{"uuid":"$twin"}""")
        val repository = loaded()
        assertEquals(twin, repository.summary("a-plan")!!.metadata.uuid)
        val others = listOf("b-plan", "c-plan").map { repository.summary(it)!!.metadata.uuid }
        assertTrue(others.none { it == twin || it == null })
        assertEquals(2, others.toSet().size)
    }

    @Test
    fun `maps that share an identity in files only read are left as they are`() = runTest {
        loaded()
        mapFolder("lisbon", "# Lisbon\n", """{"created":100,"uuid":"$twin"}""")
        mapFolder("lisbon-2", "# Lisbon\n- Tram\n", """{"created":200,"uuid":"$twin"}""")
        File(maps, ".layout.json").writeText("""{"version":${LAYOUT_VERSION + 1},"compatible":${LAYOUT_VERSION + 1}}""")
        val repository = loaded()
        assertFalse(repository.writable.value)
        assertEquals("""{"created":200,"uuid":"$twin"}""", File(maps, "lisbon-2/meta.json").readText())
    }

    @Test
    fun `a map Export all gives an identity is found by it at once, and never comes in again as a map with the same one`() = runTest {
        // A map from before identities, as Orbit 0.3.0 and earlier left them.
        File(maps, "lisbon").mkdirs()
        File(maps, "lisbon/map.md").writeText("# Lisbon\n- Food\n")
        File(maps, "lisbon/meta.json").writeText("{\"created\":5}")
        val repository = loaded()
        repository.exportAll(ByteArrayOutputStream())
        val uuid = MetadataFile.decode(File(maps, "lisbon/meta.json").readText()).uuid!!
        assertEquals("lisbon", repository.findByIdentity(uuid))

        // The same map, changed on another device, arriving: it's the one here.
        val bundle = repository.readBundle(ByteArrayInputStream(zip("map.md" to "# Lisbon\n- Food\n- Tram\n".toByteArray(), "orbit.json" to "{\"uuid\":\"$uuid\"}".toByteArray())))!!
        assertEquals("lisbon", repository.findByIdentity(bundle.uuid!!))
        // Kept both, the copy has an identity of its own.
        val copy = repository.receive(bundle)
        assertNotEquals(uuid, repository.identity(copy))
        assertEquals(listOf("lisbon"), repository.maps.value!!.filter { it.metadata.uuid == uuid }.map { it.id })
    }

    @Test
    fun `an identity written to disk since the maps were listed is found all the same`() = runTest {
        val repository = loaded()
        File(maps, "plan").mkdirs()
        File(maps, "plan/map.md").writeText("# Plan\n")
        File(maps, "plan/meta.json").writeText("{\"created\":5,\"uuid\":\"5d6e7f80-1a2b-4c3d-9e8f-7a6b5c4d3e2f\"}")
        assertEquals("plan", repository.findByIdentity("5d6e7f80-1a2b-4c3d-9e8f-7a6b5c4d3e2f"))
        assertNull(repository.findByIdentity("00000000-0000-0000-0000-000000000000"))
    }

    @Test
    fun `a duplicate gets an identity of its own`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        val uuid = repository.identity(id)
        val copy = repository.duplicate(id, "# Plan (copy)\n")
        assertEquals("Plan (copy)", repository.summary(copy)!!.title)
        assertNotEquals(uuid, repository.identity(copy))
    }

    @Test
    fun `a private map stays private after a restart, in its copies and through the trash`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        assertTrue(repository.summary(id)!!.metadata.isPrivate)
        assertTrue(File(maps, "$id/meta.json").readText().contains("\"private\":true"))
        // The map's own file says nothing about it: a sync app or another device sees a map like any other.
        assertEquals("# Journal\n- Today\n", repository.read(id))

        val copy = repository.duplicate(id, "# Journal (copy)\n- Today\n")
        assertTrue(repository.summary(copy)!!.metadata.isPrivate)

        repository.delete(id)
        val trashed = loaded().trash.value.single()
        assertTrue(trashed.isPrivate)
        val restarted = loaded()
        assertEquals(id, restarted.restore(restarted.trash.value.single()))
        assertTrue(restarted.summary(id)!!.metadata.isPrivate)

        restarted.setPrivate(id, false)
        assertFalse(restarted.summary(id)!!.metadata.isPrivate)
        assertFalse(File(maps, "$id/meta.json").readText().contains("private"))
    }

    @Test
    fun `emptying the trash can keep the maps it doesn't show, for their own time to go`() = runTest {
        val repository = loaded()
        val journal = repository.add("# Journal\n- Today\n")
        val groceries = repository.add("# Groceries\n- Bread\n")
        repository.setPrivate(journal, true)
        repository.delete(journal)
        repository.delete(groceries)
        assertEquals(2, repository.trash.value.size)

        // While private maps hide, the trash empties what it shows.
        repository.emptyTrash(keeping = { it.isPrivate })
        assertEquals(listOf("Journal"), repository.trash.value.map { it.title })
        assertEquals(listOf("Journal"), loaded().trash.value.map { it.title })

        repository.emptyTrash()
        assertEquals(emptyList<TrashedMap>(), repository.trash.value)
        assertEquals(emptyList<TrashedMap>(), loaded().trash.value)
    }

    @Test
    fun `a map whose file can't be read goes to the trash listed by its id, and comes back from it`() = runTest {
        val repository = loaded()
        val id = repository.add("# Plan\n")
        File(maps, "$id/map.md").setReadable(false)

        try {
            val trashed = repository.delete(id)
            assertNotNull(trashed)
            assertEquals(id, trashed!!.title)
            assertTrue(trashed.unreadable)
            assertEquals(listOf(id), repository.trash.value.map { it.id })
            assertEquals(listOf(id), loaded().trash.value.map { it.id })
            repository.restore(trashed)
        } finally {
            File(maps, "$id/map.md").setReadable(true)
        }

        assertEquals("# Plan\n", repository.read(id))
        assertEquals(emptyList<TrashedMap>(), repository.trash.value)
    }

    @Test
    fun `importing every map counts the private ones already here, so they can go unmentioned while hidden`() = runTest {
        val repository = loaded()
        val journal = repository.add("# Journal\n- Today\n")
        repository.add("# Groceries\n- Bread\n")
        repository.setPrivate(journal, true)
        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

        val again = repository.importAll(ByteArrayInputStream(zip))
        assertEquals(0, again.added)
        assertEquals(1, again.privateAlreadyHere)
        assertEquals(again.alreadyHere - 1, com.hyuchia.orbit.privacy.mentionedAlreadyHere(again.alreadyHere, again.privateAlreadyHere, concealing = true))
        assertEquals(again.alreadyHere, com.hyuchia.orbit.privacy.mentionedAlreadyHere(again.alreadyHere, again.privateAlreadyHere, concealing = false))
    }

    @Test
    fun `Export all brings a private map back still private, to a phone a backup left it out of`() = runTest {
        val repository = loaded()
        val journal = repository.add("# Journal\n- Today\n")
        repository.add("# Groceries\n- Bread\n")
        repository.setPrivate(journal, true)
        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

        // A restored phone with the other map, and not the private one.
        val elsewhere = File(files, "elsewhere")
        val restored = MapRepository(File(elsewhere, "maps"), File(elsewhere, "trash"), starters, words, clock = { now }).also { it.load() }
        restored.importAll(ByteArrayInputStream(zip.copyOf()))
        val back = restored.maps.value!!.single { it.title == "Journal" }
        assertTrue(back.metadata.isPrivate)
    }

    @Test
    fun `a private map that comes back from another device, both kept, keeps its copy private`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n- Today\n")
        repository.setPrivate(id, true)
        val bundle = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray().let { repository.readBundle(ByteArrayInputStream(it)) }!!

        val copy = repository.receive(bundle)
        assertNotEquals(id, copy)
        assertTrue(repository.summary(copy)!!.metadata.isPrivate)

        // Updating the map itself keeps it private, and a map new here isn't.
        assertEquals(id, repository.receive(bundle, replacing = id))
        assertTrue(repository.summary(id)!!.metadata.isPrivate)
        val other = repository.add("# Trip\n")
        val otherBundle = ByteArrayOutputStream().also { repository.writeZip(other, it) }.toByteArray().let { repository.readBundle(ByteArrayInputStream(it)) }!!
        repository.delete(other)
        assertFalse(repository.summary(repository.receive(otherBundle))!!.metadata.isPrivate)
    }

    @Test
    fun `the reasons a zip is refused are in the app's language`() = runTest {
        val repository = MapRepository(maps, File(files, "trash"), starters, FileWords(Locale.forLanguageTag("es-MX")), clock = { now }).also { it.load() }
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

    /** Every file in a zip, by name. */
    private fun unzipped(zip: ByteArray): List<Pair<String, ByteArray>> = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
        generateSequence { input.nextEntry }.filterNot { it.isDirectory }.map { it.name to input.readBytes() }.toList()
    }

    /** A JPEG with an EXIF block that says where it was taken, after JFIF's segment, as cameras write it. */
    private fun cameraJpeg(): ByteArray {
        val image = BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB)
        val plain = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        val afterJfif = 4 + (((plain[4].toInt() and 0xFF) shl 8) or (plain[5].toInt() and 0xFF))
        val description = "Lisbon, by the river\u0000".toByteArray(Charsets.ISO_8859_1)
        val tiff = ByteBuffer.allocate(8 + 2 + 12 + 4 + description.size).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put(byteArrayOf(0x49, 0x49, 0x2A, 0x00)).putInt(8).putShort(1)
        tiff.putShort(0x010E).putShort(2).putInt(description.size).putInt(26)
        tiff.putInt(0).put(description)
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.array()
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()) + exif
        return plain.copyOfRange(0, afterJfif) + segment + plain.copyOfRange(afterJfif, plain.size)
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
    fun `a map's zip says what made it, and reads with fields this version doesn't know or can't read`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n")
        val written = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()

        val info = ZipInputStream(ByteArrayInputStream(written)).use { input ->
            generateSequence { input.nextEntry }.first { it.name == BUNDLE_INFO }
            Json.parseToJsonElement(input.readBytes().decodeToString()).jsonObject
        }

        assertEquals("1", info.getValue("bundle").jsonPrimitive.content)
        assertEquals("1", info.getValue("compatible").jsonPrimitive.content)
        assertEquals(BuildConfig.VERSION_NAME, info.getValue("app").jsonPrimitive.content)

        // A newer Orbit's fields, and one it gave another shape: the map keeps its identity, and only that field is lost.
        val newer = zip(
            "map.md" to "# Trip\n".toByteArray(),
            "orbit.json" to """{"uuid":"5d2c9a7e-0f3b-4b8e-9c1d-2a6f8e4b7c10","focus":{"id":"food"},"view":"all","bundle":3,"compatible":1,"app":"0.9.0","sizes":[1,2]}""".toByteArray(),
        )
        val bundle = repository.readBundle(ByteArrayInputStream(newer))!!
        assertEquals("5d2c9a7e-0f3b-4b8e-9c1d-2a6f8e4b7c10", bundle.uuid)
        assertNull(bundle.focus)
        assertEquals("all", bundle.view)
    }

    @Test
    fun `a zip a newer Orbit says this one would misread is refused, saying to update`() = runTest {
        val repository = loaded()
        val update = "That zip comes from a newer version of Orbit. Update Orbit to open it."
        val newer = zip("map.md" to "# Trip\n".toByteArray(), "orbit.json" to """{"bundle":2,"compatible":2,"app":"1.0.0"}""".toByteArray())
        assertEquals(update, refused { repository.readBundle(ByteArrayInputStream(newer)) })

        // Export all's too, before any of its maps comes in.
        val everything = zip(
            "orbit-export.json" to """{"export":2,"compatible":2}""".toByteArray(),
            "trip/map.md" to "# Trip to Lisbon\n".toByteArray(),
            "trip/meta.json" to "{}".toByteArray(),
        )
        assertEquals(update, refused { repository.importAll(ByteArrayInputStream(everything)) })
        assertTrue(repository.maps.value!!.none { it.title == "Trip to Lisbon" })
    }

    @Test
    fun `files of kinds this version doesn't know stay out of a zip, counted`() = runTest {
        val repository = loaded()
        val one = zip("map.md" to "# Trip\n".toByteArray(), "media/picture-1.jpg" to ByteArray(10), "media/scan-1.xyz" to ByteArray(10))
        val bundle = repository.readBundle(ByteArrayInputStream(one))!!
        assertEquals(1, bundle.leftOut)
        assertEquals(setOf("picture-1.jpg"), bundle.pictures.keys)

        val everything = zip(
            "orbit-export.json" to "{}".toByteArray(),
            "trip/map.md" to "# Trip to Lisbon\n".toByteArray(),
            "trip/meta.json" to "{}".toByteArray(),
            "trip/media/scan-1.xyz" to ByteArray(10),
            "trip/media/scan-2.xyz" to ByteArray(10),
        )
        assertEquals(2, repository.importAll(ByteArrayInputStream(everything)).leftOut)
    }

    @Test
    fun `Export all's zip says what made it, first`() = runTest {
        val repository = loaded()
        val written = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

        ZipInputStream(ByteArrayInputStream(written)).use { input ->
            assertEquals(EXPORT_MANIFEST, input.nextEntry!!.name)
            val manifest = Json.parseToJsonElement(input.readBytes().decodeToString()).jsonObject
            assertEquals(listOf("export", "compatible", "app", "layout", "format"), manifest.keys.toList())
            assertEquals(BuildConfig.VERSION_NAME, manifest.getValue("app").jsonPrimitive.content)
        }

        assertTrue(repository.holdsManyMaps(ByteArrayInputStream(written)))
    }

    @Test
    fun `a look at a map from a zip counts its pictures without reading them`() = runTest {
        val bundle = zip(
            "map.md" to "# Trip\n".toByteArray(),
            "media/one.jpg" to ByteArray(1_000),
            "media/two.jpg" to ByteArray(2_000),
        )
        val look = loaded().readBundle(ByteArrayInputStream(bundle), withPictures = false)!!
        assertEquals(2, look.media.pictures)
        assertTrue(look.pictures.isEmpty())
        val whole = loaded().readBundle(ByteArrayInputStream(bundle))!!
        assertEquals(2, whole.media.pictures)
        assertEquals(2_000L, whole.pictures["two.jpg"]!!.length())
    }

    @Test
    fun `a map from a zip says what comes with it, its sounds by what its text plays as sounds`() = runTest {
        val bundle = zip(
            "map.md" to "# Trip\n## Fado <!-- orbit audio: media/memo-mf2k1x.mp4 -->\n## Tram <!-- orbit video: media/video-mf2k1x.mp4 -->\n![Beach](media/beach.jpg)\n".toByteArray(),
            "media/memo-mf2k1x.mp4" to ByteArray(10),
            "media/video-mf2k1x.mp4" to ByteArray(10),
            "media/video-mf2k1x.jpg" to ByteArray(10),
            "media/beach.jpg" to ByteArray(10),
        )

        for (look in listOf(loaded().readBundle(ByteArrayInputStream(bundle), withPictures = false)!!, loaded().readBundle(ByteArrayInputStream(bundle))!!)) {
            // A video's poster is part of it: a picture, a video and a sound.
            assertEquals(MediaSummary(pictures = 1, videos = 1, audio = 1, bytes = look.media.bytes), look.media)
        }
    }

    @Test
    fun `what a map carries counts its voice memos as sounds, though they're mp4s, and a page takes those that fit`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n## Fado <!-- orbit audio: media/memo-mf2k1x.mp4 -->\n## Tram <!-- orbit video: media/video-mf2k1x.mp4 -->\n")
        File(maps, "$id/media").mkdirs()
        File(maps, "$id/media/memo-mf2k1x.mp4").writeBytes(TestMedia.movie(100))
        File(maps, "$id/media/video-mf2k1x.mp4").writeBytes(TestMedia.movie(100))

        assertEquals(MediaSummary(videos = 1, audio = 1, bytes = 200), repository.mediaSummary(id))
        assertEquals(1, repository.soundsInPage(id))
    }

    @Test
    fun `a map's pictures from a zip wait in files, not memory, and move into the map it becomes`() = runTest {
        val repository = loaded()
        val bundle = repository.readBundle(ByteArrayInputStream(zip("map.md" to "# Trip\n![Beach](media/beach.jpg)\n".toByteArray(), "media/beach.jpg" to ByteArray(5_000))))!!
        val waiting = bundle.pictures.getValue("beach.jpg")

        assertTrue(waiting.isFile)
        assertEquals(5_000L, waiting.length())
        val id = repository.receive(bundle)
        assertFalse(waiting.exists())
        assertEquals(5_000L, File(maps, "$id/media/beach.jpg").length())
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
        assertEquals(3, whole.media.count)
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
    fun `an id no map could have is no map, rather than a crash`() = runTest {
        val repository = loaded()

        for (id in listOf("", ".", "..", "a/b", ".hidden")) {
            assertFalse(id, repository.exists(id))
        }
    }

    @Test
    fun `a file unpacked from a zip always lands inside the folder it's unpacked to`() = runTest {
        val folder = File(files, "unpacked")

        assertEquals(File(folder, "media/photo.jpg").canonicalPath, fileInside(folder, "media/photo.jpg", words).path)

        for (name in listOf("../map.md", "media/../../evil.jpg", "history/../../../map.md")) {
            assertEquals(name, "That zip has a file that points outside it.", refused { fileInside(folder, name, words) })
        }
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
    fun `every map comes back from Export all, whatever letters and numbers its title has`() = runTest {
        val repository = loaded()
        val titles = listOf("CO₂ cycle", "Reforma 80 m²", "½ recipes", "① First things", "٣ أفكار", "१० योजनाएँ", "１２ ideas", "Ünïcödé")
        titles.forEach { repository.add("# $it\n- One\n") }
        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()
        val folders = ZipInputStream(ByteArrayInputStream(zip)).use { input -> generateSequence { input.nextEntry?.name }.map { it.substringBefore('/') }.toSet() }
        // The zip names each map's folder as it's named here, from its title.
        assertTrue(folders.containsAll(listOf("co₂-cycle", "reforma-80-m²", "½-recipes", "①-first-things", "٣-أفكار", "１２-ideas")))

        val other = File(files, "other")
        val elsewhere = MapRepository(File(other, "maps"), File(other, "trash"), starters, words, clock = { now }).also { it.load() }
        assertTrue(elsewhere.holdsManyMaps(ByteArrayInputStream(zip)))
        val imported = elsewhere.importAll(ByteArrayInputStream(zip))

        assertEquals(titles.size, imported.added)
        assertEquals(0, imported.skipped)
        assertTrue(elsewhere.maps.value!!.map { it.title }.containsAll(titles))
    }

    @Test
    fun `an update from another device keeps this device's own copies of its pictures, and brings the ones it hasn't first`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n\n## Beach\n![](media/picture-1.jpg)\n")
        val original = cameraJpeg()
        File(maps, "$id/media").mkdirs()
        File(maps, "$id/media/picture-1.jpg").writeBytes(original)
        // Sent from this map, as another device would send it: the copy leaves out where the picture was taken.
        val zipped = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()
        val bundle = repository.readBundle(ByteArrayInputStream(zip(*unzipped(zipped).map { (name, bytes) -> name to bytes }.toTypedArray(), "media/picture-2.jpg" to cameraJpeg()))) ?: error("no bundle")

        repository.receive(MapBundle(bundle.markdown + "![](media/picture-2.jpg)\n", bundle.title, bundle.pictures, bundle.uuid, null, null, null, bundle.media), replacing = id)

        assertTrue(File(maps, "$id/media/picture-1.jpg").readBytes().contentEquals(original))
        assertTrue(File(maps, "$id/media/picture-2.jpg").isFile)
        assertTrue(repository.read(id).contains("picture-2.jpg"))
    }

    @Test
    fun `a map already here takes the pictures and versions it's missing from a zip of every map, and changes nothing it has`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n\n## Beach\n![](media/beach.jpg)\n## Talk <!-- orbit video: media/talk.mp4 -->\n")
        File(maps, "$id/media").mkdirs()
        File(maps, "$id/media/beach.jpg").writeBytes(ByteArray(10) { 1 })
        val archive = zip(
            "trip/map.md" to repository.read(id).toByteArray(),
            "trip/media/beach.jpg" to ByteArray(10) { 9 },
            "trip/media/talk.mp4" to ByteArray(20) { 2 },
            "trip/history/1000.md" to "# Trip\n".toByteArray(),
            "trip/history/reasons.json" to """{"versions":{"1000.md":{"why":"text"}}}""".toByteArray(),
        )

        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, filledIn = 2), repository.importAll(ByteArrayInputStream(archive)))
        // The map's own picture stays as it was; the video and the version it was missing came in.
        assertTrue(File(maps, "$id/media/beach.jpg").readBytes().contentEquals(ByteArray(10) { 1 }))
        assertEquals(20L, File(maps, "$id/media/talk.mp4").length())
        assertEquals(listOf(VersionReason.Text), repository.versions(id).map { it.reason })
        // A second time, nothing's missing.
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1), repository.importAll(ByteArrayInputStream(archive)))
    }

    @Test
    fun `files brought into a private map are counted apart, so they can go unmentioned while private maps hide`() = runTest {
        val repository = loaded()
        val id = repository.add("# Journal\n\n## Talk <!-- orbit video: media/talk.mp4 -->\n")
        repository.setPrivate(id, true)
        val archive = zip("journal/map.md" to repository.read(id).toByteArray(), "journal/media/talk.mp4" to ByteArray(20) { 2 })

        val imported = repository.importAll(ByteArrayInputStream(archive))
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, privateAlreadyHere = 1, filledIn = 1, privateFilledIn = 1), imported)
        assertEquals(0, com.hyuchia.orbit.privacy.mentionedFilledIn(imported.filledIn, imported.privateFilledIn, concealing = true))
        assertEquals(1, com.hyuchia.orbit.privacy.mentionedFilledIn(imported.filledIn, imported.privateFilledIn, concealing = false))
    }

    @Test
    fun `a zip's folders that hold no map are counted as left out, and hidden ones aren't maps`() = runTest {
        val repository = loaded()
        val archive = zip(
            "trip/map.md" to "# Trip\n".toByteArray(),
            "photos/media/beach.jpg" to ByteArray(10),
            "plan/meta.json" to "{}".toByteArray(),
            ".orbit/map.md" to "# Not a map\n".toByteArray(),
        )

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0, skipped = 2), repository.importAll(ByteArrayInputStream(archive)))
        assertFalse(repository.maps.value!!.any { it.title == "Not a map" })
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
    fun `a picture only an earlier version points at stays, and one nothing points at goes`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-now.jpg)\n")

        for (name in listOf("picture-now.jpg", "picture-before.jpg", "picture-never.jpg")) {
            repository.addMediaFile(id, name) { it.write(byteArrayOf(1)); true }
            File(maps, "$id/media/$name").setLastModified(now - 11 * minute)
        }

        File(history, "$id/${now - 60 * minute}.md").apply { parentFile!!.mkdirs() }.writeText("# Trip\n- Beach ![](media/picture-before.jpg)\n")
        File(history, "$id/${now - 30 * minute}.md").writeText("# Trip\n")
        repository.removeUnusedPictures(id)
        assertNotNull(repository.picture(id, "picture-now.jpg"))
        assertNotNull(repository.picture(id, "picture-before.jpg"))
        assertNull(repository.picture(id, "picture-never.jpg"))
    }

    @Test
    fun `a picture a map shows stays, wherever Orbit reads it from, and so does one its text names in a web address`() = runTest {
        val repository = loaded()
        val markdown = """
            # Trip <!-- orbit audio: media/memo-trip.mp4 -->
            ## Beach
            ![The beach](media/picture-beach.jpg)
            ## Trail
            [![Its poster](media/picture-poster.jpg)](media/video-trail.mp4)
            ## Lake
            <!-- orbit
            image:
              src: "media/picture-lake.jpg"
              motion: { src: './media/picture-lake-motion.mp4', still: 1.5 }
            -->
            Notes with ![a picture](media/picture-note.jpg), and [a search](https://example.com/?q=media/picture-search.jpg).
        """.trimIndent()
        val yaml = "nodes:\n  - text: Trip\n    image: media/picture-yaml.jpg\n    children:\n      - text: Talk\n        audio:\n          src: media/memo-yaml.mp4\n"
        val shown = listOf("memo-trip.mp4", "picture-beach.jpg", "picture-poster.jpg", "video-trail.mp4", "picture-lake.jpg", "picture-lake-motion.mp4", "picture-note.jpg")

        for ((text, names) in listOf(markdown to shown + "picture-search.jpg", yaml to listOf("picture-yaml.jpg", "memo-yaml.mp4"))) {
            val id = repository.add(text)

            for (name in names + "picture-never.jpg") {
                repository.addMediaFile(id, name) { it.write(byteArrayOf(1)); true }
                File(maps, "$id/media/$name").setLastModified(now - 11 * minute)
            }

            repository.removeUnusedPictures(id)
            assertEquals(names.toSet(), File(maps, "$id/media").list().orEmpty().toSet())
        }
    }

    @Test
    fun `a map's zip carries the files it shows, and none its text only names in a web address`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n- [Search](https://example.com/?q=media/picture-2.jpg)\n")
        repository.addMediaFile(id, "picture-1.jpg") { it.write(ByteArray(10) { 1 }); true }
        repository.addMediaFile(id, "picture-2.jpg") { it.write(ByteArray(10) { 2 }); true }

        val zipped = ByteArrayOutputStream().also { repository.writeZip(id, it, setOf("picture-1.jpg", "picture-2.jpg")) }.toByteArray()
        assertEquals(setOf("picture-1.jpg"), repository.readBundle(ByteArrayInputStream(zipped))!!.pictures.keys)
        assertEquals(1, repository.mediaSummary(id).pictures)
    }

    @Test
    fun `a file the map points at with its name percent-encoded stays, and one Orbit didn't name is never cleared`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture%2D1.jpg)\n- Café ![](media/caf%C3%A9.jpg)\n")
        val media = File(maps, "$id/media").apply { mkdirs() }

        for (name in listOf("picture-1.jpg", "café.jpg", "beach day.jpg", "picture-never.jpg")) {
            File(media, name).apply { writeBytes(byteArrayOf(1)) }.setLastModified(now - 11 * minute)
        }

        repository.removeUnusedPictures(id)
        assertEquals(setOf("picture-1.jpg", "café.jpg", "beach day.jpg"), media.list().orEmpty().toSet())
    }

    @Test
    fun `a percent-encoded reference keeps the file it names`() {
        assertTrue(pointsAtMedia("![](media/caf%C3%A9.jpg)", "café.jpg"))
        assertTrue(pointsAtMedia("![](media/picture%2D1.jpg)", "picture-1.jpg"))
        assertFalse(pointsAtMedia("![](media/picture%2D1.jpg)", "picture-2.jpg"))
    }

    @Test
    fun `a version that can't be read keeps every picture`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n")
        repository.addMediaFile(id, "picture-before.jpg") { it.write(byteArrayOf(1)); true }
        File(maps, "$id/media/picture-before.jpg").setLastModified(now - 11 * minute)
        val version = File(history, "$id/${now - 60 * minute}.md").apply { parentFile!!.mkdirs() }
        version.writeText("# Trip\n- Beach ![](media/picture-before.jpg)\n")
        version.setReadable(false)

        try {
            repository.removeUnusedPictures(id)
        } finally {
            version.setReadable(true)
        }

        assertNotNull(repository.picture(id, "picture-before.jpg"))
    }

    @Test
    fun `percent escapes read as the letters they stand for, and broken ones as they're written`() {
        assertEquals("media/beach day.jpg", percentDecoded("media/beach%20day.jpg"))
        assertEquals("media/café.jpg", percentDecoded("media/caf%C3%A9.jpg"))
        assertEquals("50%zz, and %C3 alone", percentDecoded("50%zz, and %C3 alone"))
    }

    @Test
    fun `a map's zip carries the pictures and videos its text points at, and none its earlier versions kept`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n  [![](media/video-1.jpg)](media/video-1.mp4)\n")

        val files = mapOf("picture-1.jpg" to TestMedia.picture(), "video-1.jpg" to TestMedia.picture(32, 18), "video-1.mp4" to TestMedia.movie(3_000), "picture-old.jpg" to TestMedia.picture(8, 8))

        for ((name, bytes) in files) {
            repository.addMediaFile(id, name) { it.write(bytes); true }
        }

        assertEquals(files.filterKeys { it != "picture-old.jpg" }.values.sumOf { it.size }.toLong(), repository.mediaSize(id))
        val zip = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()
        val bundle = repository.readBundle(ByteArrayInputStream(zip))!!
        assertEquals(setOf("picture-1.jpg", "video-1.jpg", "video-1.mp4"), bundle.pictures.keys)
        assertEquals(3_000L, bundle.pictures["video-1.mp4"]!!.length())
    }

    /** A camera's photo: a JPEG whose EXIF says where it was taken. */
    private fun locatedPhoto(place: String): ByteArray {
        val image = java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val plain = ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "jpg", it) }.toByteArray()
        val afterJfif = 4 + (((plain[4].toInt() and 0xFF) shl 8) or (plain[5].toInt() and 0xFF))
        val exif = "Exif\u0000\u0000II*\u0000".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(8, 0, 0, 0, 0, 0, 0, 0, 0, 0) + place.toByteArray(Charsets.ISO_8859_1)
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()) + exif
        return plain.copyOfRange(0, afterJfif) + segment + plain.copyOfRange(afterJfif, plain.size)
    }

    @Test
    fun `a map's zip carries its photos without where they were taken, and Export all keeps them exactly`() = runTest {
        val place = "GPS 38.7139 N 9.1394 W"
        val photo = locatedPhoto(place)
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n")
        repository.addMediaFile(id, "picture-1.jpg") { it.write(photo); true }

        val zip = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()
        val shared = repository.readBundle(ByteArrayInputStream(zip))!!.pictures.getValue("picture-1.jpg").readBytes()
        assertFalse(String(shared, Charsets.ISO_8859_1).contains(place))
        assertEquals(16, javax.imageio.ImageIO.read(shared.inputStream()).width)

        val every = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

        val backedUp = java.util.zip.ZipInputStream(ByteArrayInputStream(every)).use { zipped ->
            generateSequence { zipped.nextEntry }.first { it.name.endsWith("media/picture-1.jpg") }.let { zipped.readBytes() }
        }

        assertTrue(backedUp.contentEquals(photo))
        // The map's own file is as it was.
        assertTrue(repository.picture(id, "picture-1.jpg")!!.readBytes().contentEquals(photo))
    }

    @Test
    fun `a map's files that can't go clean are known, and go in its zip only as the person chose`() = runTest {
        val photo = locatedPhoto("GPS 38.7139 N 9.1394 W")
        val cut = photo.copyOfRange(0, photo.size / 3)
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n- Pier ![](media/picture-2.jpg)\n")
        repository.addMediaFile(id, "picture-1.jpg") { it.write(photo); true }
        repository.addMediaFile(id, "picture-2.jpg") { it.write(cut); true }

        assertEquals(Uncleaned(pictures = setOf("picture-2.jpg")), repository.uncleaned(id))
        assertEquals(2, repository.mediaSummary(id).pictures)
        assertEquals(1, repository.mediaSummary(id, leavingOut = setOf("picture-2.jpg")).pictures)

        suspend fun zipped(asTheyAre: Set<String>) =
            ByteArrayOutputStream().also { repository.writeZip(id, it, asTheyAre) }.toByteArray().let { repository.readBundle(ByteArrayInputStream(it))!!.pictures }

        // Left out unless chosen; then exactly as it is, while the one that goes clean still does.
        assertEquals(setOf("picture-1.jpg"), zipped(emptySet()).keys)
        val chosen = zipped(setOf("picture-2.jpg"))
        assertTrue(chosen.getValue("picture-2.jpg").readBytes().contentEquals(cut))
        assertFalse(String(chosen.getValue("picture-1.jpg").readBytes(), Charsets.ISO_8859_1).contains("GPS 38.7139"))
    }

    @Test
    fun `a map has pictures for a zip only when its text points at ones in its folder`() = runTest {
        val repository = loaded()
        val id = repository.add("# Trip\n- Beach ![](media/picture-1.jpg)\n")
        assertTrue(repository.mediaSummary(id).isEmpty)
        repository.addMediaFile(id, "picture-1.jpg") { it.write(ByteArray(10)); true }
        assertEquals(1, repository.mediaSummary(id).pictures)
        assertTrue(repository.mediaSummary(repository.add("# Plan\n- ![](https://example.com/picture.jpg)\n")).isEmpty)
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

    /** How many ideas each kept version holds, the newest first, as its text reads. */
    private suspend fun MapRepository.ideasInVersions(id: String): List<Int> = versions(id).map { MapText.ideas(readVersion(id, it.time)!!).size - 1 }
}
