package com.hyuchia.orbit.data

import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.reminders.Reminder
import com.hyuchia.orbit.reminders.ReminderAlarms
import com.hyuchia.orbit.reminders.ReminderNotices
import com.hyuchia.orbit.reminders.Reminders
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.study.NEW_CARDS_A_DAY
import com.hyuchia.orbit.ui.noteBoxOn
import com.hyuchia.orbit.ui.withNoteBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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

        /** Sardines with the sketch drawn on its planet in step 12, as Orbit points an idea at its picture. */
        private val SARDINES_SKETCH = Regex("""- Sardines <!-- orbit status: in progress -->\n {2}!\[A sardine]\(media/(sketch-[a-z0-9]+\.svg)\)""")

        /** The file Food holds from step 11, in the map's media folder. */
        private const val BOARDING_PASS = "file-boarding-pass.pdf"

        /** An install of a version after the release's, in the same folder: another device, as far as the folder's records go. */
        private const val LATER_INSTALL = "4b0e2c7a-later-install"
    }

    /**
     * Whether the release is `version` or a later one, for what its steps
     * (the README's) leave from then on: from 0.4.0, why versions were kept,
     * a private map, study progress, sounds and a live photo, the folder's
     * records and 0.4.0's settings; from 0.5.0, a date and its reminder, an
     * idea's file, a sketch, a note's table and checklist, and the records
     * this install keeps in the folder maps are kept in.
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
        // The two it came with, and the one kept before the first change of each visit that changed it: step 3's,
        // and from 0.5.0, step 10's and step 11's too.
        assertEquals("$release: the versions it came with, and the one before each visit's edits", if (since("0.5.0")) 5 else 3, versions.size)
        assertEquals(1, versions.count { it.original })

        if (since("0.5.0")) {
            // The date the release's own editor gave an idea, in step 10, as Orbit writes one, and as reminders read it.
            val text = repository.read(lisbon.id)
            assertTrue("$release: a date set on the map", Regex("""- Night markets <!-- orbit due: "\d{4}-\d{2}-\d{2} 18:30" -->""").containsMatchIn(text))
            val dated = MapText.dates(text).single { it.name == "Night markets" }
            assertEquals("$release: the 20th of the month after the release", 20, dated.date.day.dayOfMonth)
            assertEquals("$release: at 18:30", LocalTime.of(18, 30), dated.date.time)
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

        if (since("0.5.0")) {
            // Step 11: Food holds the boarding pass, a file of a kind no earlier release carries, which the map carries and counts.
            val text = repository.read(lisbon.id)
            assertEquals("$release: Food's file", listOf(BOARDING_PASS), fileNamesIn(text))
            assertEquals("$release: by the name it came with", mapOf(BOARDING_PASS to "boarding-pass.pdf"), fileLabelsIn(text))
            assertTrue("$release: the file itself", File(maps, "${lisbon.id}/media/$BOARDING_PASS").length() > 0)
            assertEquals(setOf(BOARDING_PASS), repository.uncleaned(lisbon.id).files)
            assertEquals(1, repository.mediaSummary(lisbon.id).files)

            // Step 12: the sketch drawn on Sardines' planet, its picture, comes back as the sketch it was: three strokes.
            val sketch = present(SARDINES_SKETCH.find(text)?.groupValues?.get(1))
            val strokes = SketchSvg.read(File(maps, "${lisbon.id}/media/$sketch").readBytes())?.strokes
            assertEquals("$release: a sketch drawn in the release reopens to draw on: $sketch", 3, strokes?.size)

            // Step 13: Museums' note, with a table and a checklist whose first box was checked in the details.
            val note = quotedNote(text, "## 🏛️ Museums")
            assertTrue("$release: the note's table: $note", note.contains("| Museum | Opens |\n| --- | ---: |\n| Azulejo | 10:00 |\n| Gulbenkian | 10:00 |"))
            val checked = note.lines().indexOfFirst { it.endsWith("Buy tickets online") }
            val unchecked = note.lines().indexOfFirst { it.endsWith("Check Monday closures") }
            assertEquals("$release: the box checked in the details: $note", true, noteBoxOn(note, checked))
            assertEquals("$release: the box left: $note", false, noteBoxOn(note, unchecked))
            assertEquals("$release: a box checks on its own line", note.replace("- [ ] Check Monday closures", "- [x] Check Monday closures"), withNoteBox(note, unchecked, checked = true))
        }

        assertEquals("$release: opening the maps wrote nothing", before, snapshot())
    }

    /**
     * What the steps leave from 0.4.0 on: a private map, study progress,
     * sounds and a live photo, and the folder's records; from 0.5.0, a sketch
     * among the kinds of file, and the records kept in the folder itself.
     */
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

        if (since("0.5.0")) {
            // From 0.5.0, a sketch drawn on a planet, which the zip brings: it comes back as a sketch to draw on.
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

        if (since("0.5.0")) {
            assertFolderRecords(summaries, device)
        }
    }

    /**
     * From 0.5.0, the records the release's install kept in the folder maps
     * are kept in (`folder/.orbit/` beside its `files/`), as a later version
     * reads them there: another device's, once this one is a later install.
     */
    private fun assertFolderRecords(summaries: List<MapSummary>, device: String) {
        val orbit = File(releases, "$release/folder/$ORBIT_DIRECTORY")
        val folder = TestFolder()
        File(orbit, DEVICES_DIRECTORY).listFiles().orEmpty().forEach { folder.devices[it.name] = it.readText() }
        assertEquals("$release: one device record, named by the install's id", setOf("$device.json"), folder.devices.keys)

        // The device record: what the release opens, and how big a map's files may be there, read as its fields say
        // (0.4.0's says nothing of sizes, and reads as 50 MB a file and 90 MB a map).
        val fields = Json.parseToJsonElement(folder.devices.getValue("$device.json")).jsonObject
        val record = FolderDeviceRecords(folder, mutableMapOf()).others(LATER_INSTALL, now).single()
        assertEquals(device, record.id)
        assertEquals(release, record.appVersion)
        assertEquals("$release: the most a file may be there", (fields["mediaFileLimit"] as? JsonPrimitive)?.longOrNull, record.keepsFile)
        assertEquals("$release: the most a map's files may come to there", (fields["mapMediaLimit"] as? JsonPrimitive)?.longOrNull, record.keepsMapMedia)
        assertEquals("$release: it keeps ideas' files of every kind", true, record.ideaFiles)
        assertTrue("$release: the kinds of file it carries, the boarding pass's and the sketches' among them: ${record.mediaTypes}", record.mediaTypes.containsAll(listOf("pdf", "svg")))
        // So a later install in the folder, this version's, doesn't take it for a device that can't open the boarding pass.
        val me = FolderDevice(
            id = LATER_INSTALL,
            name = "Tablet",
            kind = FolderDevice.TABLET,
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = record.appVersionCode + 1,
            mapFormat = MAP_FORMAT,
            mediaTypes = carriedMediaTypes().sorted(),
            synced = now,
            mediaFileLimit = MEDIA_FILE_LIMIT,
            mapMediaLimit = MAP_MEDIA_LIMIT,
            ideaFiles = true,
        )

        val unopened = FolderRoster.notices(me, listOf(record), present = setOf("pdf", "svg"), ideaFiles = setOf("pdf")).flatMap { notice ->
            when (notice) {
                is FolderNotice.Newer -> notice.unopened
                is FolderNotice.Older -> notice.unopened
            }
        }

        assertEquals("$release: files the release would leave out of the folder's maps", emptyList<String>(), unopened)

        // The study record, kept under the install's id and named after the map's file: Calvin cycle's answers,
        // which a later install studying the same map picks up.
        val study = summaries.single { it.metadata.uuid == STUDY_NOTES }
        val file = present(study.metadata.sync?.file)
        val studied = File(orbit, STUDY_DIRECTORY).listFiles().orEmpty()
        assertEquals("$release: study records only under the install's id", listOf(device), studied.map { it.name })
        val records = studied.single().listFiles().orEmpty()
        assertEquals(listOf(FolderStudy.recordName(file)), records.map { it.name })
        val kept = (FolderStudy.read(records.single().readText()) as? StudyRead.Record)?.record
        assertEquals("$release: the record names the map's file", file, kept?.map)
        assertEquals("$release: the answers this install kept, as its record says them", study.metadata.study, kept?.cards)
        assertEquals("$release: another install reads them", mapOf(device to kept?.cards), FolderStudy.elsewhere(file, LATER_INSTALL, mapOf(device to listOfNotNull(kept)), present = setOf(file)))
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

        if (since("0.5.0")) {
            // Lisbon's zip carries Sardines' sketch, and Food's boarding pass, as it is, once that's chosen.
            val lisbon = repository.lisbon()
            val zip = ByteArrayOutputStream().also { repository.writeZip(lisbon.id, it, asTheyAre = chosenAsTheyAreIn(repository.folder(lisbon.id), BOARDING_PASS)) }.toByteArray()
            val entries = ZipInputStream(zip.inputStream()).use { input -> generateSequence { input.nextEntry }.associate { it.name to input.readBytes() } }
            val sketch = present(SARDINES_SKETCH.find(repository.read(lisbon.id))?.groupValues?.get(1))
            assertTrue("$release: the boarding pass goes as it is: ${entries.keys}", entries["$MEDIA_DIRECTORY/$BOARDING_PASS"]?.contentEquals(File(maps, "${lisbon.id}/media/$BOARDING_PASS").readBytes()) == true)
            assertNotNull("$release: the sketch goes as a sketch: ${entries.keys}", entries["$MEDIA_DIRECTORY/$sketch"]?.let(SketchSvg::read))
        }
    }

    @Test
    fun `a release's maps, every one in a zip, come back whole on a phone without them`() = runTest {
        val repository = loaded()
        val zip = ByteArrayOutputStream().also { repository.exportAll(it) }.toByteArray()
        val other = MapRepository(File(files, "other/maps"), File(files, "other/trash"), starters, words, clock = { now }).also { it.load() }

        val imported = other.importAll(ByteArrayInputStream(zip))

        val listed = repository.maps.value.orEmpty()
        assertEquals(release, ImportedMaps(added = listed.size, alreadyHere = 0), imported)

        for (map in listed) {
            val back = other.maps.value.orEmpty().single { it.metadata.uuid == map.metadata.uuid }
            assertEquals("$release: ${map.title}", File(maps, "${map.id}/$MAP_FILE").readText(), other.read(back.id))
            assertEquals("$release: ${map.title}", File(maps, "${map.id}/$MEDIA_DIRECTORY").list().orEmpty().toSet(), other.folder(back.id).resolve(MEDIA_DIRECTORY).list().orEmpty().toSet())
            assertEquals("$release: ${map.title}", repository.versions(map.id), other.versions(back.id))
            assertEquals("$release: ${map.title}", map.metadata.isPrivate, back.metadata.isPrivate)
        }
    }

    @Test
    fun `a release's map takes changes, keeping its pictures, versions and details`() = runTest {
        val repository = loaded()
        val lisbon = repository.lisbon()
        val text = repository.read(lisbon.id)
        val picture = File(maps, "${lisbon.id}/media/picture-1.jpg").readBytes()
        // Its picture and video, and from 0.5.0, Sardines' sketch and Food's boarding pass.
        val media = File(maps, "${lisbon.id}/media").listFiles().orEmpty().associate { it.name to it.readBytes() }
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
        val after = File(maps, "${lisbon.id}/media").listFiles().orEmpty().associate { it.name to it.readBytes() }
        assertEquals("$release: every file of the map stays", media.keys, after.keys)
        assertTrue("$release: each as it was", media.all { (name, bytes) -> after[name]?.contentEquals(bytes) == true })
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

        // Turned off in step 10 from 0.5.0; on before, which had none. And Orbit asked about notifications as the
        // first date was set, which "Not now" answered.
        assertEquals("$release: reminders", !since("0.5.0"), read.reminders)
        assertEquals("$release: notifications asked about", since("0.5.0"), read.notificationsAsked)

        settings.update { it }
        assertEquals(read, settings.settings.first())
    }

    @Test
    fun `the reminders a release kept come at their moment once they're on`() = runTest {
        val file = File(files, "reminders.json")

        if (!since("0.5.0")) {
            assertFalse("$release: reminders came with dates, in 0.5.0", file.exists())
            return@runTest
        }

        val zone = ZoneId.of("America/Mexico_City")
        var clock = now
        var alarm: Long? = null
        val shown = mutableListOf<String>()

        val reminders = Reminders(
            file,
            alarms = object : ReminderAlarms {
                override fun set(at: Long) {
                    alarm = at
                }

                override fun cancel() {
                    alarm = null
                }
            },
            notices = object : ReminderNotices {
                override fun post(reminder: Reminder, at: Instant) {
                    shown += "${reminder.mapTitle}: ${reminder.idea}"
                }

                override fun keepPrivate(private: Set<String>, reminders: List<Reminder>) = Unit
            },
            clock = { clock },
            zone = { zone },
        )

        val repository = loaded()
        val maps = repository.maps.value.orEmpty()
        val moment = MapText.dates(repository.read(repository.lisbon().id)).single { it.name == "Night markets" }.date.remindAt(zone).toEpochMilli()

        // Off, as step 10 left them: nothing comes, and no alarm waits.
        reminders.follow(maps, { repository.read(it) }, on = false)
        assertNull("$release: no alarm while reminders are off", alarm)

        // Turned on again: Night markets' reminder, which the release saw before its moment, waits for 18:30 on its
        // day, and comes then.
        reminders.follow(maps, { repository.read(it) }, on = true)
        assertEquals("$release: the next alarm is Night markets'", moment, alarm)
        assertEquals(emptyList<String>(), shown)
        clock = moment
        reminders.ring(on = true)
        assertEquals("$release: the reminder the release set", listOf("Lisbon in May: Night markets"), shown)
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

    /**
     * The note of the idea whose line in `text` is `idea`, as Orbit writes a
     * note in a quote under it (after its picture, if it has one): its lines
     * without their `> `.
     */
    private fun quotedNote(text: String, idea: String): String = text.lines()
        .dropWhile { it != idea }
        .drop(1)
        .dropWhile { !it.startsWith(">") }
        .takeWhile { it.startsWith(">") }
        .joinToString("\n") { it.removePrefix(">").removePrefix(" ") }

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
