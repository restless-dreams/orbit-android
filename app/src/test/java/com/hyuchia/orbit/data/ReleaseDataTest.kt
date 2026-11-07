package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.study.NEW_CARDS_A_DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Each release's files as that release's own code left them (see
 * `src/test/resources/releases/README.md`), opened by this version: every
 * map, picture, video, version, trashed map and setting is still there, and
 * opening them writes over nothing.
 */
@RunWith(Parameterized::class)
class ReleaseDataTest(private val release: String) {
    companion object {
        private val releases: File = File(requireNotNull(ReleaseDataTest::class.java.classLoader?.getResource("releases")) { "No release data on the test classpath" }.toURI())

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun releases(): List<String> = releases.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.sorted()

        private const val DAY = 24 * 60 * 60 * 1000L

        /** Releases whose page didn't keep an idea's color in the map yet when it moved: no `paletteColor` in their files. */
        private val BEFORE_PALETTE_COLORS = setOf("before-0.1.0", "0.1.0", "0.1.1", "0.2.0")

        // The ids of the maps scripts/release-data/ adds from 0.4.0 on, in their meta.json.
        private const val STUDY_NOTES = "0b9a4c1e-37f2-4b1c-8f0e-6b2d9e7c5a22"
        private const val EVERY_KIND_OF_FILE = "7d1e5f3a-9c2b-4a8e-b6d0-2f4c8a1e9b44"
    }

    /**
     * Whether the release is `version` or a later one, for what its steps
     * (the README's) leave from then on: from 0.4.0, why versions were kept,
     * a private map, study progress, sounds and a live photo, the folder's
     * records and 0.4.0's settings.
     */
    private fun since(version: String): Boolean = release != "before-0.1.0" && compareVersions(release, version) >= 0

    private val files: File = Files.createTempDirectory("orbit-release").toFile()
    private val maps = File(files, "maps")
    private val words = FileWords(Locale.US)

    /** Nothing new on a launch that isn't the first. */
    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** A day after the release's map went to the trash, so nothing there has waited long enough to go. */
    private val now: Long by lazy {
        File(files, "trash").list().orEmpty().mapNotNull { it.substringAfterLast('-').toLongOrNull() }.maxOrNull()?.plus(DAY) ?: System.currentTimeMillis()
    }

    @Before
    fun copyRelease() {
        File(releases, "$release/files").copyRecursively(files)
    }

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    private suspend fun loaded(): MapRepository = MapRepository(maps, File(files, "trash"), starters, words, clock = { now }).also { it.load() }

    /** Every file's contents, by its path in the app's files. */
    private fun snapshot(): Map<String, String> = files.walkTopDown()
        .filter { it.isFile }
        .associate { file -> file.relativeTo(files).invariantSeparatorsPath to MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) } }

    private fun MapRepository.lisbon(): MapSummary = maps.value.orEmpty().single { it.title == "Lisbon in May" }

    @Test
    fun `every map, picture, video, version and trashed map the release left is there, and opening them writes nothing`() = runTest {
        val before = snapshot()
        val repository = loaded()
        assertTrue("$release: maps can be changed", repository.writable.value)

        val folders = maps.listFiles { file -> file.isDirectory && File(file, MAP_FILE).isFile }.orEmpty().map { it.name }.toSet()
        assertEquals(folders, repository.maps.value.orEmpty().map { it.id }.toSet())

        for (map in repository.maps.value.orEmpty()) {
            assertFalse("$release: ${map.id} reads", map.unreadable)
            assertEquals(File(maps, "${map.id}/$MAP_FILE").readText(), repository.read(map.id))
        }

        val lisbon = repository.lisbon()
        // An idea added on the map by the release's own page, and what the maps came in with.
        assertTrue(repository.read(lisbon.id).contains("- Night markets"))
        assertTrue(repository.read(lisbon.id).contains("type: goal, target: 300, current: 120, unit: EUR"))

        if (release !in BEFORE_PALETTE_COLORS) {
            // The color the release's own page kept for an idea moved earlier in its orbit.
            assertTrue(repository.read(lisbon.id).contains("## 🚋 Tram 28 <!-- orbit paletteColor: 4 -->"))
        }

        assertTrue(lisbon.metadata.pinned)
        assertEquals("6f1c2b6e-1a7d-4d55-9a53-2f7e5d1c0a11", lisbon.metadata.uuid)
        assertEquals(listOf("food", "museums", "savings"), lisbon.metadata.path)
        assertEquals(mapOf("food" to 42, "museums" to 30, "savings" to 12), lisbon.metadata.rehearsal)
        assertTrue("$release: a later version's field is kept", "fromALaterVersion" in lisbon.metadata.extras)
        assertNotNull(repository.picture(lisbon.id, "picture-1.jpg"))
        assertTrue(File(maps, "${lisbon.id}/media/video-2.mp4").length() > 0)

        val versions = repository.versions(lisbon.id)
        assertEquals("$release: the versions it came with, and the one before the edit", 3, versions.size)
        assertEquals(1, versions.count { it.original })

        if (since("0.4.1")) {
            // The date the release's own editor gave an idea, in step 10, as Orbit writes one.
            assertTrue("$release: a date set on the map", Regex("""- Night markets <!-- orbit due: "\d{4}-\d{2}-\d{2} 18:30" -->""").containsMatchIn(repository.read(lisbon.id)))
        }

        if (since("0.4.0")) {
            // The version kept as the edit of step 3.3 was saved, the visit's first change.
            assertEquals("$release: the version before the edit says why it was kept", VersionReason.Opened, versions.first().reason)
        } else {
            assertTrue("$release: versions kept before reasons were say none", versions.all { it.reason == null })
        }

        for (version in versions) {
            assertNotNull(repository.readVersion(lisbon.id, version.time))
        }

        if (since("0.4.0")) {
            assertNewKindsOfData(repository)
            // Deleted once the folder had it, it left a tombstone there too.
            assertEquals(setOf("Old ideas", "🎤 Why mind maps should move"), repository.trash.value.map { it.title }.toSet())
        } else {
            assertEquals(listOf("Old ideas"), repository.trash.value.map { it.title })
        }

        if (since("0.4.1")) {
            // Step 11: Food holds the boarding pass, a file of a kind no earlier release carries, which the map carries and counts.
            val text = repository.read(lisbon.id)
            assertEquals("$release: Food's file", listOf("file-boarding-pass.pdf"), fileNamesIn(text))
            assertTrue("$release: the file itself", File(maps, "${lisbon.id}/media/file-boarding-pass.pdf").length() > 0)
            assertEquals(setOf("file-boarding-pass.pdf"), repository.uncleaned(lisbon.id).files)
            assertEquals(1, repository.mediaSummary(lisbon.id).files)
        }

        assertEquals("$release: opening the maps wrote nothing", before, snapshot())
    }

    /** What the steps leave from 0.4.0 on: a private map, study progress, sounds and a live photo, and the folder's records. */
    private fun assertNewKindsOfData(repository: MapRepository) {
        val summaries = repository.maps.value.orEmpty()
        val lisbon = summaries.single { it.title == "🏖️ Weekend in Lisbon" }
        assertTrue("$release: a private map stays private", lisbon.metadata.isPrivate)
        assertEquals("$release: a private map keeps its cover", LockedCover(icon = "✈️", name = "Trips"), lisbon.metadata.cover)
        // By their ids (scripts/release-data/*/meta.json), whatever a YAML map's title reads as.
        val card = present(summaries.single { it.metadata.uuid == STUDY_NOTES }.metadata.study?.get("calvin-cycle"))
        // Again, then Knew it: known once, it comes back on a day after the one it was first studied.
        assertEquals("$release: a card's study progress is kept: $card", 1, card.box)
        assertTrue("$release: the days a card was first studied and is due are kept: $card", card.first > 0 && card.due > card.first)
        assertTrue("$release: when a card was answered is kept: $card", card.at > 0)

        val kinds = summaries.single { it.metadata.uuid == EVERY_KIND_OF_FILE }
        val media = File(maps, "${kinds.id}/media").list().orEmpty().toSet()
        assertTrue("$release: a voice memo, a sound and a live photo's picture and motion: $media", media.containsAll(setOf("memo-release.mp4", "sound-release.mp3", "picture-live.jpg", "picture-live-motion.mp4")))

        if (since("0.4.1")) {
            // From the release after 0.4.0, a sketch drawn on a planet: it comes back as a sketch to draw on.
            val sketch = File(maps, "${kinds.id}/media/sketch-release.svg")
            assertNotNull("$release: a sketch is kept, and reopens: $media", sketch.takeIf { it.isFile }?.let { SketchSvg.read(it.readBytes()) })
        }

        // Every map the folder kept knows its file there, and this device's id there is kept, with when the install
        // that made it was installed. (A deleted map's file is gone from the folder by the next sync, which takes its
        // tombstone, in folder-deletions.json, with it.)
        assertTrue("$release: maps kept in the folder know their files", summaries.all { it.metadata.sync != null })
        val device = File(files, "folder-device").readText().trim()
        val installed = File(files, "folder-device-installed").readText().trim()
        assertTrue("$release: this device's id for the folder is kept", device.isNotBlank())
        assertEquals("$release: on the install that made it, it stays this device's id", device, keptDeviceId(device, keptInstall = installed, installed = installed))
    }

    @Test
    fun `a release's maps go as zips the release itself opens, saying nothing of updating`() = runTest {
        val repository = loaded()

        for (map in repository.maps.value.orEmpty().filterNot { it.unreadable }) {
            assertFalse("$release: ${map.title} is past what older versions open", repository.pastOlderZips(map.id))
            val zip = ByteArrayOutputStream().also { repository.writeZip(map.id, it) }.toByteArray()
            val entries = ZipInputStream(zip.inputStream()).use { input -> generateSequence { input.nextEntry }.map { it.name to input.readBytes().size.toLong() }.toList() }

            // As Orbit 0.4.0 and every release before it read a map's zip: within 96 MiB, no file over 64 MiB, of the zips' first version.
            assertTrue("$release: ${map.title}", entries.sumOf { it.second } <= BUNDLE_SIZE_UP_TO_0_4_0)
            assertTrue("$release: ${map.title}", entries.all { it.second <= ZIP_ENTRY_LIMIT_UP_TO_0_4_0 })
            val info = ZipInputStream(zip.inputStream()).use { input -> generateSequence { input.nextEntry }.first { it.name == BUNDLE_INFO }.let { input.readBytes().decodeToString() } }
            assertTrue("$release: ${map.title}: $info", info.contains("\"compatible\":1"))
        }
    }

    @Test
    fun `a release's map takes changes, keeping its pictures, versions and details`() = runTest {
        val repository = loaded()
        val lisbon = repository.lisbon()
        val text = repository.read(lisbon.id)
        val picture = File(maps, "${lisbon.id}/media/picture-1.jpg").readBytes()
        val versions = repository.versions(lisbon.id).size
        val reasons = repository.versions(lisbon.id).map { it.reason }

        assertEquals(WriteResult.Written, repository.write(lisbon.id, text.replace("- Gulbenkian\n", "- Gulbenkian\n- Coach museum\n"), base = text, checkpoint = Checkpoint.Always, reason = VersionReason.Opened))
        repository.setPinned(lisbon.id, false)

        val again = loaded().lisbon()
        assertTrue(repository.read(lisbon.id).contains("- Coach museum"))
        val kept = repository.versions(lisbon.id)
        assertEquals(versions + 1, kept.size)
        assertEquals("$release: the version kept before the change says why", VersionReason.Opened, kept.first().reason)
        assertEquals("$release: the release's own versions keep what they said, or saying nothing", reasons, kept.drop(1).map { it.reason })
        assertTrue(picture.contentEquals(File(maps, "${lisbon.id}/media/picture-1.jpg").readBytes()))
        assertFalse(again.metadata.pinned)
        assertEquals(listOf("food", "museums", "savings"), again.metadata.path)
        assertEquals(mapOf("food" to 42, "museums" to 30, "savings" to 12), again.metadata.rehearsal)
        assertTrue("$release: a later version's field survives this version's save", "fromALaterVersion" in again.metadata.extras)

        if (since("0.4.0")) {
            // Deleted while the folder kept it, the map took its file there with it: back from the trash, it's a map
            // of its own, with no file in the folder to follow.
            val fromFolder = repository.trash.value.single { it.title == "🎤 Why mind maps should move" }
            val restored = present(repository.restore(fromFolder))
            assertNull("$release: a map back from the trash isn't tied to the file it left", repository.maps.value.orEmpty().single { it.id == restored }.metadata.sync)
        }
    }

    @Test
    fun `a map the release put in the trash comes back whole`() = runTest {
        val repository = loaded()
        val trashed = repository.trash.value.single { it.title == "Old ideas" }
        val text = File(trashed.folder, MAP_FILE).readText()

        val id = present(repository.restore(trashed))
        assertEquals(text, repository.read(id))
        assertTrue(repository.trash.value.none { it.title == "Old ideas" })
    }

    @Test
    fun `the settings the release chose come back`() = runTest {
        val file = File(files, "datastore/settings.preferences_pb")
        val settings = SettingsRepository(settingsStoreIn(file))
        val read = settings.settings.first()

        assertEquals(ThemeMode.Dark, read.themeMode)
        assertEquals("paper", read.newMapStyle)
        assertFalse(read.snapPlanets)
        assertEquals(8, read.presentationSeconds)
        assertFalse(read.receiveFromNearby)
        // Ones the release left alone keep their defaults, settings it didn't have yet included.
        assertTrue(read.haptics)
        assertEquals(SortOrder.Edited, read.sortOrder)
        assertTrue(read.checkForUpdates)

        if (since("0.4.0")) {
            assertEquals(PrivateLockDelay.FiveMinutes, read.lockPrivateMapsAfter)
            assertTrue(read.hideLockedPrivateMaps)
            assertEquals(5, read.newCardsADay)
            assertTrue(read.storageNoteDismissed)
            // The folder chosen in step 9: the one each map kept there names.
            val folders = loaded().maps.value.orEmpty().mapNotNull { it.metadata.sync?.folder }.toSet()
            assertEquals("$release: the folder maps are kept in", setOf(present(read.syncFolder)), folders)
        } else {
            // Settings it didn't have yet read as their defaults.
            assertEquals(PrivateLockDelay.OneMinute, read.lockPrivateMapsAfter)
            assertFalse(read.hideLockedPrivateMaps)
            assertEquals(NEW_CARDS_A_DAY, read.newCardsADay)
            assertFalse(read.storageNoteDismissed)
        }

        // Turned off in step 10 from the release after 0.4.0; on before, which had none.
        assertEquals("$release: reminders", !since("0.4.1"), read.reminders)

        settings.update { it }
        assertEquals(read, settings.settings.first())
    }

    @Test
    fun `files a newer layout this version can't change are only read`() = runTest {
        File(maps, ".layout.json").writeText("""{"version":${LAYOUT_VERSION + 1},"compatible":${LAYOUT_VERSION + 1}}""")
        val before = snapshot()
        val repository = loaded()
        val lisbon = repository.lisbon()

        assertFalse(repository.writable.value)
        assertTrue(repository.read(lisbon.id).contains("- Night markets"))
        assertEquals(WriteResult.Refused, repository.write(lisbon.id, "# Something else\n- New\n"))
        assertNull(repository.delete(lisbon.id))
        assertEquals("$release: nothing was written, moved or deleted", before, snapshot())
    }

    private fun <T> present(value: T?): T = value ?: throw AssertionError("$release: expected a value")
}

/** Two versions' order, part by part: "0.10.0" comes after "0.9.1". */
internal fun compareVersions(first: String, second: String): Int {
    val left = first.split('.').map { it.toIntOrNull() ?: 0 }
    val right = second.split('.').map { it.toIntOrNull() ?: 0 }

    return (0 until maxOf(left.size, right.size)).asSequence()
        .map { (left.getOrNull(it) ?: 0).compareTo(right.getOrNull(it) ?: 0) }
        .firstOrNull { it != 0 } ?: 0
}
