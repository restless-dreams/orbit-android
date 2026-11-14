package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The files ideas hold, as the app reads them from a map's text and carries
 * them: by the names its `files` entries give, of any kind, and only those,
 * in zips, backups and the map's room; never in a web page.
 */
class IdeaFilesTest {
    private val files: File = Files.createTempDirectory("orbit-files").toFile()
    private val maps = File(files, "maps")
    private var now = 1_000_000_000_000L
    private val minute = 60_000L
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome() = StarterMap("# Welcome\n")
    }

    private fun repository() = MapRepository(maps, File(files, "trash"), starters, words, clock = { now }).also { runBlocking { it.load() } }

    /** A map with Orbit's own way of writing an idea's files, a PDF and a spreadsheet, under its idea "Tickets". */
    private val trip = """
        # Trip
        ## Tickets
        <!-- orbit
        files:
          - src: media/file-boarding-pass.pdf
            name: "Boarding pass: final.pdf"
            type: "application/pdf"
            size: 4
          - src: media/file-budget.xlsx
            name: "Budget.xlsx"
            size: 3
        -->
        ![The poster](media/picture-1.jpg)
    """.trimIndent() + "\n"

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    @Test
    fun anIdeasFilesAreTheAddressesItsFilesEntriesGiveInEveryFormOrbitReads() {
        assertEquals(listOf("file-boarding-pass.pdf", "file-budget.xlsx"), fileNamesIn(trip))
        // Inline, a flow list, one alone, an object, and among a YAML map's keys.
        assertEquals(listOf("a.pdf", "b.docx"), fileNamesIn("# Trip\n## Plans <!-- orbit due: 2026-11-20, files: [media/a.pdf, \"media/b.docx\"], owner: Ana -->\n"))
        assertEquals(listOf("c.pdf"), fileNamesIn("# Trip\n- Visa <!-- orbit files: media/c.pdf, status: done -->\n"))
        assertEquals(listOf("d.zip"), fileNamesIn("# Trip\n- Maps <!-- orbit files: { src: media/d.zip, name: All the maps } -->\n"))
        assertEquals(listOf("e.pdf", "f.csv"), fileNamesIn("nodes:\n  - text: Trip\n    files:\n      - src: media/e.pdf\n        name: Plan\n      - media/f.csv\n"))
        // A list written right under its key, as YAML allows.
        assertEquals(listOf("g.pdf"), fileNamesIn("# Trip\n## Visa\n<!-- orbit\nfiles:\n- src: media/g.pdf\nstatus: done\n-->\n"))
    }

    @Test
    fun aFileIsKnownByTheNameItsEntryGivesItAsYamlReadsIt() {
        assertEquals(mapOf("file-boarding-pass.pdf" to "Boarding pass: final.pdf", "file-budget.xlsx" to "Budget.xlsx"), fileLabelsIn(trip))
        // A flow list of objects, quoted either way, plain, and a comment after it.
        val inline = "# Trip\n## Plans <!-- orbit files: [{src: media/a.pdf, name: 'Ana''s plan.pdf'}, {name: Plan B, src: media/b.pdf}, {src: media/c.pdf}], status: done -->\n"
        assertEquals(mapOf("a.pdf" to "Ana's plan.pdf", "b.pdf" to "Plan B"), fileLabelsIn(inline))
        assertEquals(mapOf("d.zip" to "All the maps"), fileLabelsIn("# Trip\n- Maps <!-- orbit files: { src: media/d.zip, name: All the maps } -->\n"))
        assertEquals(mapOf("e.pdf" to "Plan, the last one", "g.pdf" to "Café\n\"menu\""), fileLabelsIn("nodes:\n  - text: Trip\n    files:\n      - src: media/e.pdf\n        name: Plan, the last one # for May\n      - media/f.csv\n      - name: \"Caf\\u00e9\\n\\\"menu\\\"\"\n        src: media/g.pdf\n"))
        // What Orbit writes, read back as it was given.
        val written = filesAttribute(listOf(AddedFile("media/file-a.pdf", "Plan --> final: \"v2\".pdf", "application/pdf", 482133)))
        assertEquals(mapOf("file-a.pdf" to "Plan --> final: \"v2\".pdf"), fileLabelsIn("# Trip\n$written\n"))
        assertEquals(emptyMap<String, String>(), fileLabelsIn("# Trip\n- Visa <!-- orbit files: media/c.pdf -->\n"))
    }

    @Test
    fun aFilesOtherFieldsAWebAddressAndWordsAreNoFileOfTheMaps() {
        val map = "# Trip\n## Tickets\n<!-- orbit\nfiles:\n  - src: https://example.com/media/plan.pdf\n    name: \"see media/notes.pdf\"\n-->\nThe note says files: media/elsewhere.pdf too.\n"
        assertEquals(emptyList<String>(), fileNamesIn(map))
        assertEquals(emptyList<String>(), fileNamesIn("# Trip\n## Tickets <!-- orbit image: media/picture-1.jpg -->\n"))
    }

    @Test
    fun whatAMapShowsIsItsTextWithoutItsIdeasFiles() {
        val shown = withoutFiles(trip)
        assertEquals(trip.length, shown.length)
        assertEquals(listOf("picture-1.jpg"), MediaNames.references(shown))
        val noted = trip.replace("files:", "notes:")
        assertEquals(noted, withoutFiles(noted))
        // A picture held as a file is never one a page shows.
        assertEquals(emptyList<String>(), MediaNames.references(withoutFiles("# Trip\n- Scan <!-- orbit files: [media/file-scan.png] -->\n")))
    }

    @Test
    fun aMapCarriesItsPicturesVideosAndSoundsByTheirKindsAndItsIdeasFilesOfAnyKind() {
        val map = trip + "Read [the guide](media/guide.html) too.\n"
        assertEquals(listOf("picture-1.jpg", "file-boarding-pass.pdf", "file-budget.xlsx"), carriedNames(map))
        assertTrue(isCarriedName("file-budget.xlsx", fileNamesIn(map)))
        assertFalse(isCarriedName("guide.html", fileNamesIn(map)))
        assertFalse(isCarriedName("../map.md", listOf("../map.md")))
    }

    @Test
    fun orbitWritesAnIdeasFilesAsItsOwnFormatHasThemQuotedSoNothingEndsTheComment() {
        val written = filesAttribute(listOf(AddedFile("media/file-a.pdf", "Plan --> final: \"v2\".pdf", "application/pdf", 482133), AddedFile("media/file-b", "Notes", null, 12)))
        assertEquals(
            "<!-- orbit\nfiles:\n  - src: media/file-a.pdf\n    name: \"Plan --\\u003e final: \\\"v2\\\".pdf\"\n    type: \"application/pdf\"\n    size: 482133\n" +
                "  - src: media/file-b\n    name: \"Notes\"\n    size: 12\n-->",
            written,
        )
        assertEquals(listOf("file-a.pdf", "file-b"), fileNamesIn("# Trip\n$written\n"))
    }

    @Test
    fun anIdeasFileIsKeptUnderFileAndTheNameItCameWithMadeSafe() {
        assertEquals("file-boarding-pass-final.pdf", ideaFileName("Boarding pass (final).PDF", "application/pdf"))
        assertEquals("file-resume-2026.docx", ideaFileName("Résumé 2026.docx", null))
        assertEquals("file-file.pdf", ideaFileName("研究.pdf", null))
        assertEquals("file-notes", ideaFileName("notes", null))
        assertEquals("file-a.tar", ideaFileName(".a.tar", null))
        assertTrue(MediaNames.NAME.matches(ideaFileName("../../etc/passwd", null)))
        assertTrue(isIdeaFileName("file-boarding-pass.pdf"))
        assertFalse(isIdeaFileName("file-"))
        assertFalse(isIdeaFileName("picture-1.jpg"))
    }

    /** A map with `text` and files of `bytes` in its media folder. */
    private fun mapWith(repository: MapRepository, text: String, vararg media: Pair<String, ByteArray>): String = runBlocking {
        val id = repository.add(text)
        val folder = File(repository.folder(id), MEDIA_DIRECTORY).apply { mkdirs() }
        media.forEach { (name, bytes) -> File(folder, name).writeBytes(bytes) }
        id
    }

    private val pdf = "%PDF".toByteArray()

    @Test
    fun ideasFilesCountInTheMapsRoomAndWhatGoesWithItAsFilesOrbitCantClean() = runBlocking {
        val repository = repository()
        val id = mapWith(repository, trip, "file-boarding-pass.pdf" to pdf, "file-budget.xlsx" to "PK!".toByteArray(), "guide.html" to "<script>".toByteArray())

        assertEquals(listOf(3L, 4L), repository.mediaFileSizes(id).sorted())
        assertEquals(7L, repository.mediaSize(id))
        assertEquals(listOf("file-boarding-pass.pdf", "file-budget.xlsx"), repository.uncleaned(id).files.sorted())
        assertEquals(2, repository.mediaSummary(id).files)
        assertEquals(0, repository.mediaSummary(id).pictures)
        assertEquals(setOf("file-boarding-pass.pdf", "file-budget.xlsx"), repository.uncleaned(id).names)
        assertEquals("Boarding pass: final.pdf", repository.uncleaned(id).label("file-boarding-pass.pdf"))
        assertEquals("Boarding pass: final.pdf", repository.uncleaned(id).only(setOf("file-boarding-pass.pdf")).label("file-boarding-pass.pdf"))
        assertTrue(repository.uncleaned(id).inPage.isEmpty)
        assertTrue(repository.uncleaned(id).inRoom.isEmpty)
    }

    @Test
    fun aMapsZipNeverFollowsALinkOutOfItsFolderAndStopsForAFileReplacedAfterThePersonAnswered() = runBlocking {
        val repository = repository()
        val id = mapWith(repository, trip, "file-boarding-pass.pdf" to pdf, "file-budget.xlsx" to "PK!".toByteArray())
        val media = File(repository.folder(id), MEDIA_DIRECTORY)

        fun entries(zip: ByteArray): List<String> = ZipInputStream(ByteArrayInputStream(zip)).use { input -> generateSequence { input.nextEntry?.name }.toList() }

        // The budget a link to a file outside the map, as the review's probe made one: it isn't the map's, and goes nowhere.
        val outside = File(files, "outside.xlsx").apply { writeText("another map's budget") }
        File(media, "file-budget.xlsx").delete()
        Files.createSymbolicLink(File(media, "file-budget.xlsx").toPath(), outside.toPath())
        val uncleaned = repository.uncleaned(id)
        assertEquals(setOf("file-boarding-pass.pdf"), uncleaned.names)

        // The person chose to share the boarding pass as it is.
        val answers = AsTheyAreFiles(asked = uncleaned.stamps.values.toSet(), chosen = uncleaned.stamps.values.toSet())
        val zip = ByteArrayOutputStream().also { repository.writeZip(id, it, answers) }.toByteArray()
        assertEquals(listOf("map.md", "media/file-boarding-pass.pdf", "orbit.json"), entries(zip))

        // Another boarding pass in its place, the same size, before it goes: the zip stops, for the person to be asked about it.
        writeAtomically(File(media, "file-boarding-pass.pdf"), "%PDX".toByteArray())
        val stopped = runCatching { repository.writeZip(id, ByteArrayOutputStream(), answers) }.exceptionOrNull()
        assertTrue("$stopped", stopped is FileChanged)
        // A way out that can't ask leaves it here.
        val lasting = ByteArrayOutputStream().also { repository.writeZip(id, it, answers.lasting()) }.toByteArray()
        assertEquals(listOf("map.md", "orbit.json"), entries(lasting))
        // Asked about as it is now, it's another file.
        assertFalse(repository.uncleaned(id).stamps.values.any { it in answers.chosen })
    }

    @Test
    fun aMapsZipCarriesItsIdeasFilesOnlyWhenChosenToGoAsTheyAreAndTakesInOnlyThoseItsTextNames() = runBlocking {
        val repository = repository()
        val id = mapWith(repository, trip, "file-boarding-pass.pdf" to pdf, "file-budget.xlsx" to "PK!".toByteArray())

        fun entries(zip: ByteArray): List<String> = ZipInputStream(ByteArrayInputStream(zip)).use { input -> generateSequence { input.nextEntry?.name }.toList() }

        val clean = ByteArrayOutputStream().also { repository.writeZip(id, it) }.toByteArray()
        assertEquals(listOf("map.md", "orbit.json"), entries(clean))

        val chosen = ByteArrayOutputStream().also { repository.writeZip(id, it, asTheyAre = chosenAsTheyAreIn(repository.folder(id), "file-boarding-pass.pdf", "file-budget.xlsx")) }.toByteArray()
        assertEquals(listOf("map.md", "media/file-boarding-pass.pdf", "media/file-budget.xlsx", "orbit.json"), entries(chosen))

        // A zip from elsewhere, its files before its text: those its ideas hold come in, a page it doesn't name stays out.
        val elsewhere = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for ((name, bytes) in listOf("media/file-boarding-pass.pdf" to pdf, "media/guide.html" to "<script>".toByteArray(), "map.md" to trip.toByteArray())) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        val bundle = repository.readBundle(ByteArrayInputStream(elsewhere))!!
        assertEquals(setOf("file-boarding-pass.pdf"), bundle.pictures.keys)
        assertEquals(1, bundle.leftOut)
        assertEquals(1, bundle.media.files)
        repository.discard(bundle)
    }

    @Test
    fun orbitsOwnUnusedFilesGoOnceNothingPointsAtThemAndAnyOtherStays() = runBlocking {
        val repository = repository()
        val id = mapWith(repository, trip, "file-boarding-pass.pdf" to pdf, "file-old.pdf" to pdf, "notes.pdf" to pdf)
        val media = File(repository.folder(id), MEDIA_DIRECTORY)
        media.listFiles()!!.forEach { it.setLastModified(now - 60 * minute) }

        repository.removeUnusedPictures(id)

        assertEquals(listOf("file-boarding-pass.pdf", "notes.pdf"), media.list()!!.sorted())
    }

    @Test
    fun aWebPageNeverCarriesAPictureHeldAsAFile() = runBlocking {
        val repository = repository()
        val id = mapWith(repository, "# Trip\n- Scan <!-- orbit files: [media/file-scan.png] -->\n- Poster ![](media/picture-1.png)\n", "file-scan.png" to ByteArray(8), "picture-1.png" to ByteArray(8))
        assertEquals(setOf("picture-1.png"), picturesFitting(repository.read(id), repository.folder(id)))
        assertNull(MediaNames.references(withoutFiles(repository.read(id))).firstOrNull { it == "file-scan.png" })
    }

    @Test
    fun backupsTakeIdeasFilesAfterSoundsAndBeforeVideosAndAnEarlierVersionsOnANewPhone() {
        val root = File(files, "device").apply { mkdirs() }
        val map = File(root, "maps/trip").apply { mkdirs() }
        File(map, MAP_FILE).writeText(trip)
        File(map, "meta.json").writeText("{}")
        val media = File(map, MEDIA_DIRECTORY).apply { mkdirs() }
        File(media, "file-boarding-pass.pdf").writeBytes(pdf)
        File(media, "file-older.pdf").writeBytes(pdf)
        File(media, "guide.html").writeBytes(pdf)

        fun taken(newPhone: Boolean) = backupFiles(root, Long.MAX_VALUE, newPhone).filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()

        assertTrue("maps/trip/media/file-boarding-pass.pdf" in taken(newPhone = false))
        assertFalse("maps/trip/media/file-older.pdf" in taken(newPhone = false))
        assertTrue("maps/trip/media/file-older.pdf" in taken(newPhone = true))
        assertFalse("maps/trip/media/guide.html" in taken(newPhone = true))
    }
}
