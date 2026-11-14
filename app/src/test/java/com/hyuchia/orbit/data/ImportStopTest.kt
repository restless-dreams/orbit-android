package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A zip of every map that stops partway says exactly what came in: each
 * map whole and listed, the list always what's on disk, and each file that
 * came into a map here counted; importing it again brings the rest. A
 * locked private map is found by its identity alone, so a zip of its text
 * tells nothing of it. Several maps from one file come in all together or
 * not at all.
 */
class ImportStopTest {
    private val root: File = Files.createTempDirectory("orbit-import-stop").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    @After
    fun cleanUp() {
        root.walkTopDown().forEach {
            it.setReadable(true)
            it.setWritable(true)
        }

        root.deleteRecursively()
    }

    /** A phone of its own, its maps read. */
    private suspend fun phone(name: String): MapRepository = MapRepository(File(root, "$name/maps"), File(root, "$name/trash"), starters, words).also { it.load() }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** The maps phone `name` has on disk, by their folders. */
    private fun onDisk(name: String): Set<String> =
        File(root, "$name/maps").listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }.orEmpty().mapTo(HashSet()) { it.name }

    /** Hidden folders left in phone `name`'s maps, as one being made leaves until it's whole. */
    private fun hidden(name: String): List<String> = File(root, "$name/maps").list().orEmpty().filter { it.startsWith(".new-") }

    /** `folder`, which can't be written now; skips the test where that can't be (as for root). */
    private fun readOnly(folder: File): File {
        folder.mkdirs()
        folder.setWritable(false)
        assumeFalse("Folders can't be made read-only here", folder.canWrite())
        return folder
    }

    private suspend fun stopped(import: suspend () -> ImportedMaps): ImportStoppedException {
        try {
            val imported = import()
            fail("The import went on to the end: $imported")
            throw AssertionError()
        } catch (stopped: ImportStoppedException) {
            return stopped
        }
    }

    @Test
    fun `a zip that stops partway says exactly which maps came in, each listed, and brings the rest when imported again`() = runTest {
        val repository = phone("phone")
        val archive = zip(
            "first/map.md" to "# First\n".toByteArray(),
            "second/map.md" to "# Second\n".toByteArray(),
            "second/history/1000.md" to "# Second, earlier\n".toByteArray(),
        )
        val history = readOnly(File(root, "phone/history"))

        // The second map's versions can't come: it's here, listed, and so is the first.
        val stopped = stopped { repository.importAll(ByteArrayInputStream(archive)) }

        assertEquals(ImportedMaps(added = 2, alreadyHere = 0), stopped.soFar)
        assertEquals(setOf("First", "Second"), repository.maps.value!!.mapTo(HashSet()) { it.title })
        assertEquals(onDisk("phone"), repository.maps.value!!.mapTo(HashSet()) { it.id })
        assertTrue(stopped.cause is IOException)

        // Importing it again brings the version, and no map twice.
        history.setWritable(true)
        assertEquals(ImportedMaps(added = 0, alreadyHere = 2, filledIn = 1), repository.importAll(ByteArrayInputStream(archive)))
        assertEquals(2, repository.maps.value!!.size)
        val second = repository.maps.value!!.single { it.title == "Second" }
        assertEquals(listOf(1000L), repository.versions(second.id).map { it.time })
    }

    @Test
    fun `files that came into a map here before a new map couldn't be made are counted, and the new map is nowhere`() = runTest {
        val repository = phone("phone")
        val markdown = "# Trip\n- Day one\n"
        val id = repository.add(markdown)
        val uuid = repository.summary(id)!!.metadata.uuid!!
        val archive = zip(
            "trip/map.md" to markdown.toByteArray(),
            "trip/meta.json" to """{"created":5,"uuid":"$uuid"}""".toByteArray(),
            "trip/history/1000.md" to "# Trip\n".toByteArray(),
            "plan/map.md" to "# Plan\n".toByteArray(),
        )
        val maps = readOnly(File(root, "phone/maps"))

        val stopped = stopped { repository.importAll(ByteArrayInputStream(archive)) }

        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, filledIn = 1), stopped.soFar)
        assertEquals(listOf(1000L), repository.versions(id).map { it.time })
        assertEquals(listOf(id), repository.maps.value!!.map { it.id })
        assertEquals(setOf(id), onDisk("phone"))
        assertEquals(emptyList<String>(), hidden("phone"))

        maps.setWritable(true)
        assertEquals(ImportedMaps(added = 1, alreadyHere = 1), repository.importAll(ByteArrayInputStream(archive)))
        assertEquals(setOf("Trip", "Plan"), repository.maps.value!!.mapTo(HashSet()) { it.title })
    }

    @Test
    fun `files that came into a map here are counted one by one when the rest of them can't come`() = runTest {
        val repository = phone("phone")
        val markdown = "# Trip\n- A <!-- orbit files: media/file-a.bin -->\n- B <!-- orbit files: media/file-b.bin -->\n"
        val id = repository.add(markdown)
        val uuid = repository.summary(id)!!.metadata.uuid!!
        val archive = zip(
            "trip/map.md" to markdown.toByteArray(),
            "trip/meta.json" to """{"created":5,"uuid":"$uuid"}""".toByteArray(),
            "trip/media/file-a.bin" to byteArrayOf(1),
            "trip/media/file-b.bin" to byteArrayOf(2),
            "trip/history/1000.md" to "# Trip\n".toByteArray(),
        )
        // Its pictures come; its version can't.
        readOnly(File(root, "phone/history/$id"))

        val stopped = stopped { repository.importAll(ByteArrayInputStream(archive)) }

        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, filledIn = 2), stopped.soFar)
        assertEquals(1, File(root, "phone/maps/$id/media/file-a.bin").length())
        assertEquals(1, File(root, "phone/maps/$id/media/file-b.bin").length())
        assertEquals(emptySet<String>(), repository.summary(id)!!.missingMedia)
    }

    @Test
    fun `a zip that stops before anything came in fails as it always has`() = runTest {
        val repository = phone("phone")
        readOnly(File(root, "phone/maps"))

        try {
            repository.importAll(ByteArrayInputStream(zip("plan/map.md" to "# Plan\n".toByteArray())))
            fail("A map came in where none could be made")
        } catch (failed: IOException) {
            assertFalse(failed is ImportStoppedException)
        }

        assertEquals(emptyList<MapSummary>(), repository.maps.value)
        assertEquals(emptySet<String>(), onDisk("phone"))
    }

    @Test
    fun `a zip Export all wrote before 0_4_0, without its manifest or identities, comes in whole, and once`() = runTest {
        val repository = phone("phone")
        val markdown = "# Lisbon\n- Food\n  ![Sardines](media/picture-1.jpg)\n"
        val archive = zip(
            "lisbon/map.md" to markdown.toByteArray(),
            "lisbon/meta.json" to """{"created":5,"pinned":true}""".toByteArray(),
            "lisbon/media/picture-1.jpg" to byteArrayOf(1, 2, 3),
            "lisbon/history/1000.md" to "# Lisbon\n".toByteArray(),
        )

        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), repository.importAll(ByteArrayInputStream(archive)))

        val lisbon = repository.maps.value!!.single()
        assertEquals(markdown, repository.read(lisbon.id))
        assertTrue(lisbon.metadata.pinned)
        assertEquals(3, File(repository.folder(lisbon.id), "$MEDIA_DIRECTORY/picture-1.jpg").length())
        assertEquals(listOf(1000L), repository.versions(lisbon.id).map { it.time })
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1), repository.importAll(ByteArrayInputStream(archive)))
    }

    @Test
    fun `a locked private map is found by its identity alone, so a zip of its text tells nothing of it`() = runTest {
        val text = "# Diary\n- Secret\n"
        val guess = zip("guess/map.md" to text.toByteArray())
        val locked: (MapSummary) -> Boolean = { it.metadata.isPrivate }
        val withIt = phone("with")
        val diary = withIt.add(text, isPrivate = true)
        val withoutIt = phone("without")

        val there = withIt.importAll(ByteArrayInputStream(guess), locked)
        val notThere = withoutIt.importAll(ByteArrayInputStream(guess), locked)

        // The same either way: the zip's text comes in as a map of its own, as it was in the zip, and the private map is as it was.
        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), there)
        assertEquals(notThere, there)
        val copy = withIt.maps.value!!.single { it.id != diary }
        assertFalse(copy.metadata.isPrivate)
        assertEquals(text, withIt.read(copy.id))
        assertTrue(withIt.summary(diary)!!.metadata.isPrivate)

        // By its identity, as in its owner's own backup, it's found, locked or not.
        val backup = ByteArrayOutputStream().also { withIt.exportAll(it) }.toByteArray()
        assertEquals(ImportedMaps(added = 0, alreadyHere = 2, privateAlreadyHere = 1), withIt.importAll(ByteArrayInputStream(backup), locked))

        // Unlocked, its owner's import finds it by its text, as ever.
        val owner = phone("owner")
        owner.add(text, isPrivate = true)
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, privateAlreadyHere = 1), owner.importAll(ByteArrayInputStream(guess)))
    }

    @Test
    fun `a changed copy of a private map comes in once, however often its owner's backup comes in while it's locked`() = runTest {
        val repository = phone("phone")
        val diary = repository.add("# Diary\n- Monday\n", isPrivate = true)
        val backup = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()
        assertEquals(WriteResult.Written, repository.write(diary, "# Diary\n- Monday\n- Tuesday\n"))
        val locked: (MapSummary) -> Boolean = { it.metadata.isPrivate }

        // The backup's Diary changed since: it comes in beside the one here, private too, and only once, as importing the
        // file again after an import that stopped partway says to.
        assertEquals(ImportedMaps(added = 1, alreadyHere = 0), repository.importAll(ByteArrayInputStream(backup), locked))
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, privateAlreadyHere = 1), repository.importAll(ByteArrayInputStream(backup), locked))
        assertEquals(ImportedMaps(added = 0, alreadyHere = 1, privateAlreadyHere = 1), repository.importAll(ByteArrayInputStream(backup)))

        val maps = repository.maps.value!!
        assertEquals(2, maps.size)
        assertTrue(maps.all { it.metadata.isPrivate })
        assertEquals(2, maps.mapNotNull { it.metadata.uuid }.toSet().size)
    }

    @Test
    fun `several maps from one file come in together or not at all`() = runTest {
        val repository = phone("phone")
        // A file name longer than any the device takes: the second sheet can't be made.
        val name = "file-${"a".repeat(300)}.bin"
        val sheets = listOf(
            "# Garden\n- Roses\n" to emptyMap(),
            "# Shopping\n- List <!-- orbit files: media/$name -->\n" to mapOf(name to byteArrayOf(1, 2, 3)),
        )

        try {
            repository.addAll(sheets)
            fail("A sheet that couldn't be made came in")
        } catch (failed: IOException) {
            assertEquals(emptyList<MapSummary>(), repository.maps.value)
            assertEquals(emptySet<String>(), onDisk("phone"))
            assertEquals(emptyList<String>(), hidden("phone"))
            assertEquals(emptyList<TrashedMap>(), repository.trash.value)
        }

        val ids = repository.addAll(listOf("# Garden\n- Roses\n" to emptyMap(), "# Shopping\n- Milk\n" to emptyMap()))

        assertEquals(listOf("Garden", "Shopping"), ids.map { repository.summary(it)!!.title })
        assertEquals(ids.toSet(), onDisk("phone"))
        assertEquals(2, repository.maps.value!!.mapNotNull { it.metadata.uuid }.toSet().size)
    }
}
