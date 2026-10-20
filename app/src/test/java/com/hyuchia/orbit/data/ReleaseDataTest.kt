package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
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
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

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
    }

    private val files: File = Files.createTempDirectory("orbit-release").toFile()
    private val maps = File(files, "maps")
    private val words = FileWords(Locale.US)

    /** Nothing new on a launch that isn't the first. */
    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): String? = null
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

        for (version in versions) {
            assertNotNull(repository.readVersion(lisbon.id, version.time))
        }

        assertEquals(listOf("Old ideas"), repository.trash.value.map { it.title })
        assertEquals("$release: opening the maps wrote nothing", before, snapshot())
    }

    @Test
    fun `a release's map takes changes, keeping its pictures, versions and details`() = runTest {
        val repository = loaded()
        val lisbon = repository.lisbon()
        val text = repository.read(lisbon.id)
        val picture = File(maps, "${lisbon.id}/media/picture-1.jpg").readBytes()
        val versions = repository.versions(lisbon.id).size

        assertEquals(WriteResult.Written, repository.write(lisbon.id, text.replace("- Gulbenkian\n", "- Gulbenkian\n- Coach museum\n"), base = text, checkpoint = Checkpoint.Always))
        repository.setPinned(lisbon.id, false)

        val again = loaded().lisbon()
        assertTrue(repository.read(lisbon.id).contains("- Coach museum"))
        assertEquals(versions + 1, repository.versions(lisbon.id).size)
        assertTrue(picture.contentEquals(File(maps, "${lisbon.id}/media/picture-1.jpg").readBytes()))
        assertFalse(again.metadata.pinned)
        assertEquals(listOf("food", "museums", "savings"), again.metadata.path)
        assertEquals(mapOf("food" to 42, "museums" to 30, "savings" to 12), again.metadata.rehearsal)
        assertTrue("$release: a later version's field survives this version's save", "fromALaterVersion" in again.metadata.extras)
    }

    @Test
    fun `a map the release put in the trash comes back whole`() = runTest {
        val repository = loaded()
        val trashed = repository.trash.value.single()
        val text = File(trashed.folder, MAP_FILE).readText()

        val id = present(repository.restore(trashed))
        assertEquals(text, repository.read(id))
        assertTrue(repository.trash.value.isEmpty())
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
