package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.privacy.mentionedFilledIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Every map in one zip, and back: each keeps its identity, privacy, cover, files and versions, and a zip that would leave any out isn't one. */
class ExportAllTest {
    private val root: File = Files.createTempDirectory("orbit-export-all").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    @After
    fun cleanUp() {
        root.walkTopDown().forEach { it.setReadable(true) }
        root.deleteRecursively()
    }

    /** A phone of its own, its maps read. */
    private suspend fun phone(name: String): MapRepository = MapRepository(File(root, "$name/maps"), File(root, "$name/trash"), starters, words).also { it.load() }

    private suspend fun export(repository: MapRepository): ByteArray = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun names(zip: ByteArray): List<String> = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
        generateSequence { input.nextEntry }.map { it.name }.toList()
    }

    /** A map whose ideas hold `names`, its own files. */
    private fun withFiles(title: String, vararg names: String): String = buildString {
        append("# ").append(title).append('\n')
        names.forEachIndexed { index, name -> append("- Idea ").append(index + 1).append(" <!-- orbit files: media/").append(name).append(" -->\n") }
    }

    /** A file of `size` bytes that takes no room: what the map's media come to is all that's looked at. */
    private fun sized(file: File, size: Long): File {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(size) }
        return file
    }

    @Test
    fun `Export all keeps identical-text maps with different identities separate`() = runTest {
        val source = phone("source")
        val markdown = withFiles("Shared", "file-secret.txt")
        source.add(markdown)
        val target = phone("target")
        target.importAll(ByteArrayInputStream(export(source)))
        val publicId = target.maps.value!!.single().id
        val secret = "PRIVATE MEDIA".toByteArray()
        source.add(markdown, original = "# Private earlier\n- Secret\n", pictures = mapOf("file-secret.txt" to secret), isPrivate = true)

        val imported = target.importAll(ByteArrayInputStream(export(source)))

        assertEquals(ImportedMaps(added = 1, alreadyHere = 1), imported)
        val summaries = target.maps.value!!
        assertEquals(2, summaries.size)
        val privateCopy = summaries.single { it.metadata.isPrivate }
        assertNotEquals(publicId, privateCopy.id)
        assertEquals(source.maps.value!!.single { it.metadata.isPrivate }.metadata.uuid, privateCopy.metadata.uuid)
        assertFalse(target.summary(publicId)!!.metadata.isPrivate)
        assertArrayEquals(secret, target.picture(privateCopy.id, "file-secret.txt", fileNamesIn(markdown))!!.readBytes())
        assertEquals(1, target.versions(privateCopy.id).size)
        // The map anyone sees got none of it.
        assertNull(target.picture(publicId, "file-secret.txt", fileNamesIn(markdown)))
        assertEquals(0, target.versions(publicId).size)
    }

    @Test
    fun `a private map never goes into a map here with the same text that's open to anyone`() = runTest {
        val source = phone("source")
        val markdown = withFiles("Shared", "file-secret.txt")
        val secret = "PRIVATE MEDIA".toByteArray()
        source.add(markdown, original = "# Private earlier\n", pictures = mapOf("file-secret.txt" to secret), isPrivate = true)
        // A map of the target's own, with the same text, that it never had from the source.
        val target = phone("target")
        val publicId = target.add(markdown)

        val imported = target.importAll(ByteArrayInputStream(export(source)))

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), imported)
        val privateCopy = target.maps.value!!.single { it.metadata.isPrivate }
        assertArrayEquals(secret, target.picture(privateCopy.id, "file-secret.txt", fileNamesIn(markdown))!!.readBytes())
        assertNull(target.picture(publicId, "file-secret.txt", fileNamesIn(markdown)))
        assertEquals(0, target.versions(publicId).size)
    }

    @Test
    fun `details that can't be read keep a map from going into one with the same text that's open to anyone`() = runTest {
        val target = phone("target")
        val publicId = target.add("# Journal\n- Today\n")

        val imported = target.importAll(ByteArrayInputStream(zip("journal/map.md" to "# Journal\n- Today\n".toByteArray(), "journal/meta.json" to "{ not json".toByteArray())))

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), imported)
        assertTrue(target.maps.value!!.single { it.id != publicId }.metadata.isPrivate)
    }

    @Test
    fun `two maps with the same text in one zip are two maps, identities or not`() = runTest {
        val target = phone("target")

        val imported = target.importAll(
            ByteArrayInputStream(
                zip(
                    "plan/map.md" to "# Plan\n- Week\n".toByteArray(),
                    "plan/history/1000.md" to "# Plan\n".toByteArray(),
                    "plan-2/map.md" to "# Plan\n- Week\n".toByteArray(),
                    "plan-2/history/2000.md" to "# Plan, another\n".toByteArray(),
                ),
            ),
        )

        assertEquals(ImportedMaps(added = 2, alreadyHere = 0), imported)
        assertEquals(listOf(1, 1), target.maps.value!!.map { target.versions(it.id).size })
    }

    @Test
    fun `two phones' maps with the same text and identities of their own are one, as their welcome maps are`() = runTest {
        val source = phone("source")
        source.add("# Welcome\n- Hello\n")
        val target = phone("target")
        val welcome = target.add("# Welcome\n- Hello\n")

        assertEquals(ImportedMaps(added = 0, alreadyHere = 1), target.importAll(ByteArrayInputStream(export(source))))
        assertEquals(listOf(welcome), target.maps.value!!.map { it.id })
    }

    @Test
    fun `Export all changed copy inherits the local private map's privacy and cover`() = runTest {
        val source = phone("source")
        val sourceId = source.add("# Journal\n- Before\n")
        val target = phone("target")
        target.importAll(ByteArrayInputStream(export(source)))
        val localId = target.maps.value!!.single().id
        val cover = LockedCover(icon = "🔒", name = "Mine")
        target.setPrivate(localId, true)
        target.setCover(localId, cover)
        source.write(sourceId, "# Journal\n- After\n", checkpoint = Checkpoint.Always)

        target.importAll(ByteArrayInputStream(export(source)))

        val changedCopy = target.maps.value!!.single { it.id != localId }
        assertTrue(changedCopy.metadata.isPrivate)
        assertEquals(cover, changedCopy.metadata.cover)
        assertNotEquals(source.summary(sourceId)!!.metadata.uuid, changedCopy.metadata.uuid)
        assertTrue(target.summary(localId)!!.metadata.isPrivate)
        assertEquals(cover, target.summary(localId)!!.metadata.cover)
    }

    @Test
    fun `a changed copy of a private map keeps its own cover, and comes in once however often the zip does`() = runTest {
        val source = phone("source")
        val sourceId = source.add("# Journal\n- Before\n")
        val target = phone("target")
        target.importAll(ByteArrayInputStream(export(source)))
        val localId = target.maps.value!!.single().id
        target.setPrivate(localId, true)
        target.setCover(localId, LockedCover(icon = "🔒", name = "Here"))
        source.write(sourceId, "# Journal\n- After\n", checkpoint = Checkpoint.Always)
        source.setPrivate(sourceId, true)
        source.setCover(sourceId, LockedCover(icon = "📓", name = "There"))
        val archive = export(source)

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), target.importAll(ByteArrayInputStream(archive)))
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, privateAlreadyHere = 1), target.importAll(ByteArrayInputStream(archive)))
        val changedCopy = target.maps.value!!.single { it.id != localId }
        assertEquals(LockedCover(icon = "📓", name = "There"), changedCopy.metadata.cover)
    }

    @Test
    fun `a changed copy of a map that's private here never goes into a map with its text that's open to anyone`() = runTest {
        val source = phone("source")
        val sourceId = source.add("# Journal\n- Before\n")
        val target = phone("target")
        target.importAll(ByteArrayInputStream(export(source)))
        val localId = target.maps.value!!.single().id
        target.setPrivate(localId, true)
        source.write(sourceId, "# Journal\n- After\n", checkpoint = Checkpoint.Always)
        // A map anyone sees, with the text the changed one has now.
        val open = target.add("# Journal\n- After\n")

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), target.importAll(ByteArrayInputStream(export(source))))
        assertEquals(0, target.versions(open).size)
        assertTrue(target.maps.value!!.single { it.id != localId && it.id != open }.metadata.isPrivate)
    }

    @Test
    fun `a private map's versions, and files only they point at, never go into the same map open here`() = runTest {
        val source = phone("source")
        val markdown = withFiles("Diary", "file-notes.txt")
        val diary = source.add(markdown, original = "# Diary, as it came\n- Secret\n", pictures = mapOf("file-notes.txt" to "notes".toByteArray()), isPrivate = true)
        // A picture only an earlier version points at.
        File(root, "source/maps/$diary/media/picture-1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        // The same map, sent here before: open, as a map from another device always comes.
        val target = phone("target")
        val open = target.receive(MapBundle(markdown, "Diary", emptyMap(), source.summary(diary)!!.metadata.uuid, null, null))

        val imported = target.importAll(ByteArrayInputStream(export(source)))

        // Not another map: the same phone, the map made open again on purpose, would get it back twice.
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, filledIn = 1, stayedPrivate = 2), imported)
        assertEquals(listOf(open), target.maps.value!!.map { it.id })
        assertFalse(target.summary(open)!!.metadata.isPrivate)
        // What its text points at, which it shows anyway, comes in; its versions, and the picture only they point at, don't.
        assertEquals("notes", target.picture(open, "file-notes.txt", fileNamesIn(markdown))!!.readText())
        assertFalse(File(root, "target/maps/$open/media/picture-1.jpg").exists())
        assertEquals(0, target.versions(open).size)
        assertEquals(
            listOf("One version or file stayed in the zip: it's from a map that's private there and not here."),
            Importer.zipNotes(0, 0, 0, words, stayedPrivate = 1),
        )
    }

    @Test
    fun `a map new from the zip takes its files while they fit in what a map carries`() = runTest {
        val target = MapRepository(File(root, "target/maps"), File(root, "target/trash"), starters, words, mapMediaLimit = 1_000).also { it.load() }
        val markdown = withFiles("Trip", "file-a.bin", "file-b.bin", "file-c.bin")

        val imported = target.importAll(
            ByteArrayInputStream(
                zip(
                    "trip/map.md" to markdown.toByteArray(),
                    "trip/media/file-a.bin" to ByteArray(600),
                    "trip/media/file-b.bin" to ByteArray(500),
                    "trip/media/file-c.bin" to ByteArray(300),
                ),
            ),
        )

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0, pastRoom = 1), imported)
        val trip = target.maps.value!!.single().id
        assertEquals(listOf("file-a.bin", "file-c.bin"), listOf("file-a.bin", "file-b.bin", "file-c.bin").filter { target.picture(trip, it, fileNamesIn(markdown)) != null })
        assertEquals(900L, target.mediaSize(trip))
    }

    @Test
    fun `Export all fails rather than leave out a picture that's there but can't be read`() = runTest {
        val repository = phone("phone")
        val markdown = withFiles("Trip", "file-notes.txt")
        val id = repository.add(markdown, pictures = mapOf("file-notes.txt" to "notes".toByteArray()))
        val file = repository.picture(id, "file-notes.txt", fileNamesIn(markdown))!!
        file.setReadable(false)
        assumeFalse("Files can't be made unreadable here", file.canRead())

        assertUnreadable(id) { repository.exportAll(ByteArrayOutputStream()) }
        // And it's found before anything is written, so a file chosen for the zip is never emptied for it.
        assertUnreadable(id) { repository.checkExportAll() }
    }

    @Test
    fun `Export all stopped partway leaves a zip no import takes for a whole one`() = runTest {
        val repository = phone("phone")
        val markdown = withFiles("Trip", "file-notes.txt")
        val id = repository.add(markdown, pictures = mapOf("file-notes.txt" to "notes".toByteArray()))
        val file = repository.picture(id, "file-notes.txt", fileNamesIn(markdown))!!
        file.setReadable(false)
        assumeFalse("Files can't be made unreadable here", file.canRead())
        val written = ByteArrayOutputStream()

        // The map's text and details are in before its file stops it.
        assertUnreadable(id) { repository.exportAll(written) }

        val other = phone("other")

        try {
            other.importAll(ByteArrayInputStream(written.toByteArray()))
            fail("Import maps took a zip Export all didn't finish, maps=${other.maps.value}")
        } catch (refused: IOException) {
            assertEquals(emptyList<MapSummary>(), other.maps.value)
        }
    }

    @Test
    fun `looking at every map's files before Export all writes nothing, not even an identity`() = runTest {
        File(root, "phone/maps/lisbon").mkdirs()
        File(root, "phone/maps/lisbon/map.md").writeText("# Lisbon\n- Food\n")
        File(root, "phone/maps/lisbon/meta.json").writeText("{\"created\":5}")
        val repository = phone("phone")

        repository.checkExportAll()

        assertEquals("{\"created\":5}", File(root, "phone/maps/lisbon/meta.json").readText())
    }

    @Test
    fun `Export all fails rather than leave out a map's history or files it can't list`() = runTest {
        val repository = phone("phone")
        val markdown = withFiles("Trip", "file-notes.txt")
        val id = repository.add(markdown, original = "# Trip, as it came\n", pictures = mapOf("file-notes.txt" to "notes".toByteArray()))
        val history = File(root, "phone/history/$id")
        assertTrue(history.isDirectory)
        history.setReadable(false)
        assumeFalse("Folders can't be made unreadable here", history.canRead())

        assertUnreadable(id) { repository.exportAll(ByteArrayOutputStream()) }
        assertUnreadable(id) { repository.checkExportAll() }

        history.setReadable(true)
        val media = File(root, "phone/maps/$id/media")
        media.setReadable(false)
        assertUnreadable(id) { repository.exportAll(ByteArrayOutputStream()) }
        assertUnreadable(id) { repository.checkExportAll() }

        media.setReadable(true)
        assertTrue(names(export(repository)).containsAll(listOf("$id/map.md", "$id/media/file-notes.txt")))
    }

    /** Says `export` stopped at a file of map `id` it couldn't read, rather than leave it out. */
    private suspend fun assertUnreadable(id: String, export: suspend () -> Unit) {
        try {
            export()
            fail("Export all went on without a file it couldn't read")
        } catch (unreadable: UnreadableMapFileException) {
            assertEquals(id, unreadable.map)
        }
    }

    @Test
    fun `a map's files come in from a zip while they fit in what a map carries, a smaller one after one too big included`() {
        val media = File(root, "media")
        val big = sized(File(media, "video-1.mp4"), 600_000_000)
        val bigger = sized(File(media, "video-2.mp4"), 500_000_000)
        val small = sized(File(media, "picture-1.jpg"), 300_000_000)

        assertEquals(setOf(bigger), pastMapRoom(listOf(big, bigger, small), carried = 0))
        assertEquals(setOf(big, bigger, small), pastMapRoom(listOf(big, bigger, small), carried = MAP_MEDIA_LIMIT))
        assertEquals(emptySet<File>(), pastMapRoom(listOf(small), carried = MAP_MEDIA_LIMIT - 300_000_000))
    }

    @Test
    fun `files a map here is missing come in from Export all only while they fit in what a map carries`() = runTest {
        val target = phone("target")
        val markdown = withFiles("Trip", "file-here.bin", "file-big.bin", "file-small.bin")
        val id = target.add(markdown)
        sized(File(root, "target/maps/$id/media/file-here.bin"), MAP_MEDIA_LIMIT - 100)

        val imported = target.importAll(
            ByteArrayInputStream(
                zip(
                    "trip/map.md" to markdown.toByteArray(),
                    "trip/media/file-big.bin" to ByteArray(1_000),
                    "trip/media/file-small.bin" to ByteArray(50),
                ),
            ),
        )

        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, filledIn = 1, pastRoom = 1), imported)
        assertNull(target.picture(id, "file-big.bin", fileNamesIn(markdown)))
        assertEquals(50L, target.picture(id, "file-small.bin", fileNamesIn(markdown))!!.length())
        assertTrue(target.mediaSize(id) <= MAP_MEDIA_LIMIT)
    }

    @Test
    fun `files left out of a private map here are counted as its, so they go unmentioned while it hides`() = runTest {
        val target = phone("target")
        val markdown = withFiles("Journal", "file-here.bin", "file-big.bin")
        val id = target.add(markdown, isPrivate = true)
        sized(File(root, "target/maps/$id/media/file-here.bin"), MAP_MEDIA_LIMIT - 100)

        val imported = target.importAll(ByteArrayInputStream(zip("journal/map.md" to markdown.toByteArray(), "journal/media/file-big.bin" to ByteArray(1_000))))

        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, privateAlreadyHere = 1, pastRoom = 1, privatePastRoom = 1), imported)
        val shown = mentionedFilledIn(imported.pastRoom, imported.privatePastRoom, concealing = false)
        val hidden = mentionedFilledIn(imported.pastRoom, imported.privatePastRoom, concealing = true)
        assertEquals(listOf("One file would have taken its map past the 1 GB a map can carry, so it stayed in the zip."), Importer.zipNotes(0, 0, 0, words, pastRoom = shown))
        assertEquals(emptyList<String>(), Importer.zipNotes(0, 0, 0, words, pastRoom = hidden))
    }

    /**
     * Where Export all writes, doing `meanwhile` once, as the zip's entry
     * `entry` starts: the map it belongs to has been read and its files
     * opened, and the rest of it is still to be written.
     */
    private class WritingThen(private val entry: String, private val meanwhile: () -> Unit) : OutputStream() {
        private val written = ByteArrayOutputStream()

        var happened = false
            private set

        override fun write(byte: Int) {
            written.write(byte)
            look()
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            written.write(bytes, offset, length)
            look()
        }

        private fun look() {
            if (!happened && written.toString(Charsets.ISO_8859_1.name()).contains(entry)) {
                happened = true
                meanwhile()
            }
        }

        fun bytes(): ByteArray = written.toByteArray()
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
        generateSequence { input.nextEntry }.associate { it.name to input.readBytes() }
    }

    /** How many files the tests' process has open now. */
    private fun openFiles(): Int {
        val open = File("/proc/self/fd").list()
        assumeTrue("Open files can't be counted here", open != null)
        return open!!.size
    }

    @Test
    fun `a map deleted while Export all writes it goes in whole, as it was, and comes back so`() = runTest {
        val repository = phone("phone")
        val markdown = withFiles("Trip", "file-notes.txt")
        val id = repository.add(markdown, original = "# Trip, as it came\n", pictures = mapOf("file-notes.txt" to "notes".toByteArray()))
        assertEquals(WriteResult.Written, repository.write(id, "$markdown- Later\n", checkpoint = Checkpoint.Always))
        val versions = repository.versions(id).size
        assertEquals(2, versions)
        val written = WritingThen("$id/map.md") { runBlocking { assertNotNull(repository.delete(id)) } }

        val exported = repository.exportAll(written)

        assertTrue(written.happened)
        assertFalse(repository.exists(id))
        assertEquals(1, exported)
        val entries = entries(written.bytes())
        assertEquals("notes", entries.getValue("$id/media/file-notes.txt").decodeToString())
        assertEquals(versions, entries.keys.count { it.startsWith("$id/$HISTORY_DIRECTORY/") && it.endsWith(".md") })
        assertTrue(entries.containsKey("$id/$HISTORY_DIRECTORY/$REASONS_FILE"))

        // It's a backup of the map as it was: it comes back whole.
        val other = phone("other")
        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), other.importAll(ByteArrayInputStream(written.bytes())))
        val back = other.maps.value!!.single()
        assertEquals("$markdown- Later\n", other.read(back.id))
        assertEquals("notes", other.picture(back.id, "file-notes.txt", fileNamesIn(markdown))!!.readText())
        assertEquals(versions, other.versions(back.id).size)
    }

    @Test
    fun `a version thinned out and a picture removed while Export all writes the map still go, as they were`() = runTest {
        val repository = phone("phone")
        val markdown = withFiles("Trip", "file-notes.txt", "file-plan.txt")
        val id = repository.add(markdown, pictures = mapOf("file-notes.txt" to "notes".toByteArray(), "file-plan.txt" to "plan".toByteArray()))
        assertEquals(WriteResult.Written, repository.write(id, "$markdown- Later\n", checkpoint = Checkpoint.Always))
        val version = File(root, "phone/history/$id").listFiles { file -> VERSION_NAME.matches(file.name) }!!.single()
        val versionText = version.readText()
        val picture = repository.picture(id, "file-plan.txt", fileNamesIn(markdown))!!

        val written = WritingThen("$id/map.md") {
            assertTrue(version.delete())
            assertTrue(picture.delete())
        }

        assertEquals(1, repository.exportAll(written))

        assertTrue(written.happened)
        val entries = entries(written.bytes())
        assertEquals(versionText, entries.getValue("$id/$HISTORY_DIRECTORY/${version.name}").decodeToString())
        assertEquals("plan", entries.getValue("$id/media/file-plan.txt").decodeToString())
        assertEquals("notes", entries.getValue("$id/media/file-notes.txt").decodeToString())
    }

    @Test
    fun `a map with more files than Export all holds open goes whole, and one of the rest gone by its turn stops the zip`() = runTest {
        val repository = phone("phone")
        val id = repository.add("# Album\n- Pictures\n")
        val media = File(repository.folder(id), MEDIA_DIRECTORY).apply { mkdirs() }
        val count = PINNED_FILES + 20
        repeat(count) { File(media, "file-$it.bin").writeBytes(byteArrayOf(it.toByte())) }
        export(repository)
        val before = openFiles()

        val whole = entries(export(repository))

        assertEquals(count, whole.keys.count { it.startsWith("$id/media/") })
        assertTrue(whole.getValue("$id/media/file-7.bin").contentEquals(byteArrayOf(7)))
        assertTrue("Files left open: ${openFiles() - before}", openFiles() <= before + 2)

        // Those it opened with the map's text go as they were; one it opens later that's gone by then would be missing.
        val written = WritingThen("$id/map.md") { media.listFiles()!!.forEach { it.delete() } }
        assertUnreadable(id) { repository.exportAll(written) }

        assertTrue(written.happened)
        assertTrue("Files left open: ${openFiles() - before}", openFiles() <= before + 2)
        val other = phone("other")

        try {
            other.importAll(ByteArrayInputStream(written.bytes()))
            fail("Import maps took a zip Export all didn't finish, maps=${other.maps.value}")
        } catch (refused: IOException) {
            assertEquals(emptyList<MapSummary>(), other.maps.value)
        }
    }

    @Test
    fun `a file Export all can't open leaves none of the map's others open`() = runTest {
        val repository = phone("phone")
        val id = repository.add("# Album\n- Pictures\n")
        val media = File(repository.folder(id), MEDIA_DIRECTORY).apply { mkdirs() }
        repeat(40) { File(media, "file-$it.bin").writeBytes(byteArrayOf(it.toByte())) }
        export(repository)
        val before = openFiles()
        val unreadable = File(media, "file-20.bin")
        unreadable.setReadable(false)
        assumeFalse("Files can't be made unreadable here", unreadable.canRead())

        assertUnreadable(id) { repository.exportAll(ByteArrayOutputStream()) }
        assertUnreadable(id) { repository.checkExportAll() }

        assertTrue("Files left open: ${openFiles() - before}", openFiles() <= before + 2)
    }
}
