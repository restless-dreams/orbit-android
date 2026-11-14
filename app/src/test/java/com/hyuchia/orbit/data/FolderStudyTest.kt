package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.study.StudyCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.Locale

/**
 * A folder in memory, as a sync app keeps it in step between devices: its
 * markdown files, and `.orbit/` with the devices' records and how each one's
 * study goes. What a sync app or another app does there is [rename].
 */
private class SharedFolder : SyncFolder {
    val texts = mutableMapOf<String, String>()
    val devices = mutableMapOf<String, String>()

    /** The study records, by device, then name: their text, and when they were written. */
    val study = mutableMapOf<String, MutableMap<String, Pair<String, Long>>>()

    private val times = mutableMapOf<String, Long>()
    private var time = 1L

    override val name = "Maps"

    override fun files() = texts.map { (name, text) -> FolderFile(name, times.getValue(name), text.length.toLong()) }

    override fun read(name: String) = texts[name] ?: throw IOException("No $name")

    override fun write(name: String, text: String, create: Boolean): FolderFile {
        texts[name] = text
        times[name] = ++time
        return FolderFile(name, time, text.length.toLong())
    }

    override fun delete(name: String) {
        texts -= name
        times -= name
    }

    override fun media() = emptyMap<String, FolderFile>()

    override fun readMedia(name: String, into: OutputStream) = false

    override fun writeMedia(name: String, from: File) = FolderFile(name, 1, from.length())

    override fun deviceRecords() = devices.map { (name, text) -> FolderFile(name, 1, text.length.toLong()) }

    override fun readDeviceRecord(name: String) = devices[name] ?: throw FileNotFoundException(name)

    override fun writeDeviceRecord(name: String, text: String) {
        devices[name] = text
    }

    override fun studyRecords() = study.mapValues { (_, records) -> records.map { (name, record) -> FolderFile(name, record.second, record.first.length.toLong()) } }

    /** Study records that can't be read just now, by device and name, as a provider failing would leave them. */
    val unreadable = mutableSetOf<Pair<String, String>>()

    override fun readStudyRecord(device: String, name: String): String {
        if (device to name in unreadable) {
            throw IOException("Couldn't read $name")
        }

        return study[device]?.get(name)?.first ?: throw FileNotFoundException(name)
    }

    /** Makes the next study record written stop halfway, its start left there, throwing this: a failure, or Orbit stopping there. */
    var cutNextStudyWrite: (() -> Throwable)? = null

    override fun writeStudyRecord(device: String, name: String, text: String) {
        val cut = cutNextStudyWrite

        if (cut != null) {
            cutNextStudyWrite = null
            study.getOrPut(device) { mutableMapOf() }[name] = text.take(text.length / 2) to ++time
            throw cut()
        }

        study.getOrPut(device) { mutableMapOf() }[name] = text to ++time
    }

    override fun deleteStudyRecord(device: String, name: String) {
        study[device]?.remove(name)
    }

    /** A file renamed in the folder, by hand or on another device: the same text under another name. */
    fun rename(from: String, to: String) {
        texts[to] = texts.remove(from)!!
        times -= from
        time += 1_000
        times[to] = time
    }

    /** What a listing of the folder's top shows: each file, `.orbit/` once it's there, and whether each is a folder. */
    fun top(): List<Pair<String, Boolean>> = texts.keys.map { it to false } + listOfNotNull((ORBIT_DIRECTORY to true).takeIf { devices.isNotEmpty() || study.isNotEmpty() })
}

class FolderStudyTest {
    private val files: File = Files.createTempDirectory("orbit-folder-study").toFile()
    private val words = FileWords(Locale.US)
    private val folder = SharedFolder()
    private val today = 20_000L
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L
    private val phoneId = "phone-2a6c0e4e"
    private val tabletId = "tablet-9d35f1f6"

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** A device with its own maps and settings, keeping its maps in the shared folder. */
    private inner class Device(val id: String, name: String) {
        val maps = MapRepository(File(files, "$id/maps"), File(files, "$id/trash"), starters, words)
        val settings = SettingsRepository(settingsStoreIn(File(files, "$id/settings.preferences_pb")))
        private val record = FolderDevice(id, name, FolderDevice.PHONE, "0.4.0", 5, 1, listOf("jpg", "mp4", "png"), 0)
        val sync = syncing()

        /** A sync of this device's maps, as Orbit started again has it: nothing in memory but what it keeps in its files. */
        fun syncing() = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(files, "$id/folder-notes.json"), words, TestEditor(words), device = { record }) { folder }

        suspend fun start(keepsMapsInFolder: Boolean = true): Device {
            maps.load()

            if (keepsMapsInFolder) {
                settings.update { it.copy(syncFolder = "memory") }
            }

            return this
        }

        fun map(title: String): MapSummary = maps.maps.value!!.single { it.title == title }

        fun records(): Map<String, StudyRecord> = folder.study[id].orEmpty().mapValues { (_, record) -> FolderStudy.decode(record.first)!! }
    }

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    private fun card(box: Int, due: Long, at: Long, first: Long = today - 10) = StudyCard(box = box, due = due, first = first, at = at)

    @Test
    fun `a device's answers reach the folder, the other device takes them, and of both devices' answers the latest decides`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n- Estar\n  - estoy\n")
        val studied = mapOf("ser" to card(box = 2, due = today + 3, at = now - day), "estar" to card(box = 0, due = today, at = now - day))
        phone.maps.saveStudy(verbs, studied)
        phone.sync.syncNow()

        // The phone's answers are in its own record, beside the map's file, and say nothing but the ideas' ids and how each goes.
        val file = phone.map("Spanish verbs").metadata.sync!!.file
        assertEquals(setOf("$file.json"), phone.records().keys)
        assertEquals(StudyRecord(map = file, cards = studied), phone.records().getValue("$file.json"))
        val written = Json.parseToJsonElement(folder.study.getValue(phoneId).getValue("$file.json").first).jsonObject
        assertEquals(setOf("format", "compatible", "map", "was", "cards"), written.keys)

        // The tablet takes the map from the folder, and the phone's answers with it.
        tablet.sync.syncNow()
        val onTablet = tablet.map("Spanish verbs")
        assertEquals(mapOf(phoneId to studied), onTablet.metadata.studyElsewhere)
        assertNull(onTablet.metadata.study)
        assertEquals(studied, onTablet.metadata.studyProgress)

        // Studied on the tablet afterwards: its answer to Estar is the later one, on both devices.
        val estar = card(box = 1, due = today + 1, at = now)
        tablet.maps.saveStudy(onTablet.id, mapOf("estar" to estar))
        tablet.sync.syncNow()
        phone.sync.syncNow()
        assertEquals(mapOf(tabletId to mapOf("estar" to estar)), phone.map("Spanish verbs").metadata.studyElsewhere)
        assertEquals(mapOf("ser" to studied.getValue("ser"), "estar" to estar), phone.map("Spanish verbs").metadata.studyProgress)
        // Each device wrote only its own records.
        assertEquals(setOf(phoneId, tabletId), folder.study.keys)
        assertEquals(mapOf("estar" to estar), tablet.records().getValue("$file.json").cards)
        assertEquals(studied, phone.records().getValue("$file.json").cards)
        // Releases before study records see the same maps as ever: `.orbit/` is no map to them.
        assertEquals(listOf(file), folder.top().filter { (name, directory) -> !directory && name.endsWith(".md", ignoreCase = true) && !name.startsWith(".") }.map { it.first })
    }

    @Test
    fun `a record that can't be read keeps its device's answers here, and is read again by the next sync`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        val studied = mapOf("ser" to card(box = 2, due = today + 3, at = now - day))
        phone.maps.saveStudy(verbs, studied)
        phone.sync.syncNow()
        tablet.sync.syncNow()
        assertEquals(mapOf(phoneId to studied), tablet.map("Spanish verbs").metadata.studyElsewhere)

        // The phone studies again, and the tablet can't read its record just then: what the phone studied stays on the tablet.
        val again = mapOf("ser" to card(box = 3, due = today + 7, at = now))
        phone.maps.saveStudy(verbs, again)
        phone.sync.syncNow()
        folder.unreadable += phoneId to "${phone.map("Spanish verbs").metadata.sync!!.file}.json"
        tablet.sync.syncNow()
        assertEquals(mapOf(phoneId to studied), tablet.map("Spanish verbs").metadata.studyElsewhere)

        // The record, unchanged since, is read again rather than taken as gone.
        folder.unreadable.clear()
        tablet.sync.syncNow()
        assertEquals(mapOf(phoneId to again), tablet.map("Spanish verbs").metadata.studyElsewhere)
    }

    @Test
    fun `a map renamed in the folder keeps its answers on each device, even before the other device sees the new name`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        tablet.sync.syncNow()
        val onTablet = tablet.map("Spanish verbs").id
        tablet.maps.saveStudy(onTablet, mapOf("ser" to card(box = 2, due = today + 3, at = now)))
        tablet.sync.syncNow()
        val before = phone.map("Spanish verbs").metadata.sync!!.file

        folder.rename(before, "Verbs.md")
        phone.sync.syncNow()

        // The phone follows the file: its record moves to the new name, remembering the old one.
        assertEquals("Verbs.md", phone.map("Spanish verbs").metadata.sync!!.file)
        assertEquals(StudyRecord(map = "Verbs.md", was = listOf(before), cards = mapOf("ser" to card(box = 1, due = today + 1, at = now - day))), phone.records().getValue("Verbs.md.json"))
        assertEquals(setOf("Verbs.md.json"), phone.records().keys)
        // The tablet hasn't synced since, so its record still has the old name: the phone takes it all the same.
        assertEquals(setOf("$before.json"), tablet.records().keys)
        assertEquals(card(box = 2, due = today + 3, at = now), phone.map("Spanish verbs").metadata.studyProgress["ser"])

        tablet.sync.syncNow()
        assertEquals(setOf("Verbs.md.json"), tablet.records().keys)
        assertEquals(listOf(before), tablet.records().getValue("Verbs.md.json").was)
        assertEquals(mapOf(phoneId to mapOf("ser" to card(box = 1, due = today + 1, at = now - day))), tablet.map("Spanish verbs").metadata.studyElsewhere)
    }

    @Test
    fun `an idea renamed on one device goes on as its latest answer on any device left it, on every device`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        tablet.sync.syncNow()
        // Known on the tablet this morning: its answer is the latest.
        val known = card(box = 2, due = today + 3, at = now)
        tablet.maps.saveStudy(tablet.map("Spanish verbs").id, mapOf("ser" to known))
        tablet.sync.syncNow()
        phone.sync.syncNow()

        // On the phone, Ser's new text gives it a new id, and its screen moves what the map keeps about it there.
        phone.maps.write(verbs, "# Spanish verbs\n- Ser to be\n  - soy\n", base = phone.maps.read(verbs))
        phone.maps.renameIdea(verbs, "ser", "ser-to-be")
        phone.sync.syncNow()
        tablet.sync.syncNow()

        // The tablet only hears of the new text: the phone's record brings the card's progress under the new id.
        assertEquals("# Spanish verbs\n- Ser to be\n  - soy\n", tablet.maps.read(tablet.map("Spanish verbs").id))
        assertEquals(known, phone.map("Spanish verbs").metadata.studyProgress["ser-to-be"])
        assertEquals(known, tablet.map("Spanish verbs").metadata.studyProgress["ser-to-be"])
    }

    @Test
    fun `choosing a folder brings the answers along, and leaving it keeps every device's on this one`() = runTest {
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val phone = Device(phoneId, "Pixel 8").start(keepsMapsInFolder = false)
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n- Estar\n  - estoy\n")
        val studied = mapOf("ser" to card(box = 2, due = today + 3, at = now - day))
        phone.maps.saveStudy(verbs, studied)
        phone.sync.syncNow()
        assertTrue(folder.study.isEmpty())

        // The phone chooses the folder: its answers go there with its maps.
        phone.settings.update { it.copy(syncFolder = "memory") }
        phone.sync.syncNow()
        val file = phone.map("Spanish verbs").metadata.sync!!.file
        assertEquals(studied, phone.records().getValue("$file.json").cards)

        tablet.sync.syncNow()
        val estar = card(box = 1, due = today + 1, at = now)
        tablet.maps.saveStudy(tablet.map("Spanish verbs").id, mapOf("estar" to estar))
        tablet.sync.syncNow()
        phone.sync.syncNow()
        assertEquals(mapOf(tabletId to mapOf("estar" to estar)), phone.map("Spanish verbs").metadata.studyElsewhere)

        // Leaving the folder, the phone keeps the tablet's answers among its own, and nothing in the folder changes.
        val folderBefore = folder.study.mapValues { (_, records) -> records.toMap() }
        phone.settings.update { it.copy(syncFolder = null) }
        phone.sync.syncNow()
        assertEquals(studied + ("estar" to estar), phone.map("Spanish verbs").metadata.study)
        assertNull(phone.map("Spanish verbs").metadata.studyElsewhere)
        assertEquals(folderBefore, folder.study.mapValues { (_, records) -> records.toMap() })
    }

    @Test
    fun `a map deleted takes this device's record with it, and leaves the other device's alone`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        tablet.sync.syncNow()
        tablet.maps.saveStudy(tablet.map("Spanish verbs").id, mapOf("ser" to card(box = 2, due = today + 3, at = now)))
        tablet.sync.syncNow()

        phone.maps.delete(verbs)
        phone.sync.syncNow()
        assertEquals(emptyMap<String, StudyRecord>(), phone.records())
        assertEquals(1, tablet.records().size)
    }

    @Test
    fun `a record from a newer Orbit is read for what this one knows, and this device's own keeps what a newer Orbit added`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n- Estar\n  - estoy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        val file = phone.map("Spanish verbs").metadata.sync!!.file

        folder.writeStudyRecord(
            tabletId,
            "$file.json",
            """{"format":2,"map":"$file","was":[],"cards":{"estar":{"box":1,"due":${today + 1},"first":$today,"at":$now,"ease":2.5}},"streak":4}""",
        )
        val own = folder.study.getValue(phoneId).getValue("$file.json").first
        folder.writeStudyRecord(phoneId, "$file.json", own.dropLast(1) + ""","goal":{"cards":10}}""")
        // Not a record at all: it's left out.
        folder.writeStudyRecord("laptop-0b7e4c43", "$file.json", "not json")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 2, due = today + 3, at = now)))
        phone.sync.syncNow()

        val estar = phone.map("Spanish verbs").metadata.studyElsewhere!!.getValue(tabletId).getValue("estar")
        assertEquals(card(box = 1, due = today + 1, at = now, first = today), estar.copy(extras = emptyMap()))
        assertEquals(setOf(tabletId), phone.map("Spanish verbs").metadata.studyElsewhere!!.keys)
        val rewritten = Json.parseToJsonElement(folder.study.getValue(phoneId).getValue("$file.json").first).jsonObject
        assertEquals("""{"cards":10}""", rewritten.getValue("goal").toString())
        assertEquals("1", rewritten.getValue("format").jsonPrimitive.content)
        assertEquals(card(box = 2, due = today + 3, at = now), phone.records().getValue("$file.json").cards["ser"])
        // The tablet's record is never written by another device.
        assertTrue(folder.study.getValue(tabletId).getValue("$file.json").first.contains("\"streak\":4"))
    }

    @Test
    fun `another device's record a newer Orbit marks as one this would misread is gone from here, and one it can't make out keeps its answers`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        val studied = mapOf("ser" to card(box = 2, due = today + 3, at = now - day))
        phone.maps.saveStudy(verbs, studied)
        phone.sync.syncNow()
        tablet.sync.syncNow()
        assertEquals(mapOf(phoneId to studied), tablet.map("Spanish verbs").metadata.studyElsewhere)
        val file = "${phone.map("Spanish verbs").metadata.sync!!.file}.json"

        // Cut short as it's written: the tablet can't make it out, and keeps what the phone studied until it can.
        folder.writeStudyRecord(phoneId, file, """{"format":1,"compatible":1,"map":""")
        tablet.sync.syncNow()
        assertEquals(mapOf(phoneId to studied), tablet.map("Spanish verbs").metadata.studyElsewhere)

        // Written by a newer Orbit that says this one would misread it: it's as if the phone kept no record of the map.
        folder.writeStudyRecord(phoneId, file, """{"format":3,"compatible":2,"map":"${file.removeSuffix(".json")}","cards":{}}""")
        tablet.sync.syncNow()
        assertEquals(emptyMap<String, Map<String, StudyCard>>(), tablet.map("Spanish verbs").metadata.studyElsewhere.orEmpty())
    }

    @Test
    fun `a record a newer Orbit says this one would misread is left out, and this one's records say what they are`() {
        // A newer format this one reads right is read; one marked as one it would misread is left out, and told from a file that isn't a record.
        assertNotNull(FolderStudy.decode("""{"format":3,"compatible":1,"map":"Verbs.md","cards":{}}"""))
        assertNull(FolderStudy.decode("""{"format":3,"compatible":2,"map":"Verbs.md","cards":{}}"""))
        assertEquals(StudyRead.Incompatible, FolderStudy.read("""{"format":3,"compatible":2,"map":"Verbs.md","cards":{}}"""))
        assertEquals(StudyRead.Garbled, FolderStudy.read("""{"format":3,"map":"""))
        assertEquals(StudyRead.Garbled, FolderStudy.read("Not a record"))

        val written = Json.parseToJsonElement(FolderStudy.encode(FolderStudy.record("Verbs.md", emptyMap(), null, emptyList()))).jsonObject
        assertEquals("1", written.getValue("format").jsonPrimitive.content)
        assertEquals("1", written.getValue("compatible").jsonPrimitive.content)
    }

    @Test
    fun `the other devices' answers come from each one's record of the map, or of a file it had before, while no other map has that name`() {
        val ser = StudyCard(box = 1, due = today + 1, at = 1)
        val estar = StudyCard(box = 2, due = today + 3, at = 2)
        val records = mapOf(
            "phone-0001" to listOf(StudyRecord(map = "Verbs.md", was = listOf("Spanish.md"), cards = mapOf("ser" to ser))),
            "tablet-0002" to listOf(StudyRecord(map = "Spanish.md", cards = mapOf("estar" to estar)), StudyRecord(map = "Other.md", cards = mapOf("x" to ser))),
            "laptop-0003" to listOf(StudyRecord(map = "Verbs.md", cards = emptyMap())),
        )
        assertEquals(mapOf("tablet-0002" to mapOf("estar" to estar)), FolderStudy.elsewhere("Verbs.md", "phone-0001", records, present = setOf("Verbs.md")))
        assertEquals(
            mapOf("phone-0001" to mapOf("ser" to ser), "tablet-0002" to mapOf("estar" to estar)),
            FolderStudy.elsewhere("Verbs.md", "laptop-0003", records, present = setOf("Verbs.md")),
        )
        // A new map has the old name now: the old records are its own.
        assertEquals(emptyMap<String, Map<String, StudyCard>>(), FolderStudy.elsewhere("Verbs.md", "phone-0001", records, present = setOf("Verbs.md", "Spanish.md")))
        // This device knows the map's file had another name a moment ago.
        assertEquals(mapOf("tablet-0002" to mapOf("x" to ser)), FolderStudy.elsewhere("New.md", "phone-0001", records, present = setOf("New.md"), known = listOf("Other.md")))

        assertEquals(listOf("B.md", "A.md"), FolderStudy.record("C.md", emptyMap(), renamedFrom = "B.md", remembered = listOf("A.md", "B.md", "C.md")).was)
        assertEquals(FolderStudy.NAMES_KEPT, FolderStudy.record("Z.md", emptyMap(), null, (1..20).map { "$it.md" }).was.size)
        assertNull(FolderStudy.decode("{}"))
    }

    @Test
    fun `this device's own record that a newer Orbit says this one would misread is never written over or deleted`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        val file = "${phone.map("Spanish verbs").metadata.sync!!.file}.json"

        // A newer Orbit on this phone wrote it so this one would misread it, before the phone went back to this version.
        val newer = """{"format":2,"compatible":2,"map":"${file.removeSuffix(".json")}","cards":{"ser":{"stage":"known","at":$now}}}"""
        folder.writeStudyRecord(phoneId, file, newer)

        // Studied again here, the record stays as the newer Orbit left it.
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 2, due = today + 3, at = now)))
        phone.sync.syncNow()
        assertEquals(newer, folder.study.getValue(phoneId).getValue(file).first)

        // With nothing studied here, it isn't deleted either.
        phone.maps.saveStudy(verbs, emptyMap())
        phone.sync.syncNow()
        assertEquals(newer, folder.study.getValue(phoneId).getValue(file).first)
    }

    @Test
    fun `a map renamed in the folder leaves this device's record by the old name as a newer Orbit wrote it`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        val before = phone.map("Spanish verbs").metadata.sync!!.file
        val newer = """{"format":2,"compatible":2,"map":"$before","cards":{"ser":{"stage":"known","at":$now}},"streak":3}"""
        folder.writeStudyRecord(phoneId, "$before.json", newer)

        folder.rename(before, "Verbs.md")
        phone.sync.syncNow()

        // The record by the old name stays as it was, and the map's answers go to one of this version's own by the new name,
        // which takes nothing from a record this version would misread.
        assertEquals(newer, folder.study.getValue(phoneId).getValue("$before.json").first)
        val written = Json.parseToJsonElement(folder.study.getValue(phoneId).getValue("Verbs.md.json").first).jsonObject
        assertEquals("1", written.getValue("compatible").jsonPrimitive.content)
        assertNull(written["streak"])
    }

    /** Orbit stopping in the middle of a sync: past each step's own catch, as nothing runs after it. */
    private class Died : Error()

    @Test
    fun `this device's own record that holds JSON this version can't make out is never written over or deleted, until it can`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        phone.sync.syncNow()
        val file = "${phone.map("Spanish verbs").metadata.sync!!.file}.json"

        // JSON, but no record this version can make out: someone's, as a newer Orbit's is, kept as it is.
        val garbled = """{"format":1,"map":"${file.removeSuffix(".json")}","cards":"not this version's"}"""
        folder.writeStudyRecord(phoneId, file, garbled)
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 2, due = today + 3, at = now)))
        phone.sync.syncNow()
        assertEquals(garbled, folder.study.getValue(phoneId).getValue(file).first)

        phone.maps.saveStudy(verbs, emptyMap())
        phone.sync.syncNow()
        assertEquals(garbled, folder.study.getValue(phoneId).getValue(file).first)

        // Once it reads as a record again, it's this device's to write.
        folder.writeStudyRecord(phoneId, file, """{"format":1,"compatible":1,"map":"${file.removeSuffix(".json")}","cards":{}}""")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 3, due = today + 7, at = now + day)))
        phone.sync.syncNow()
        assertEquals(3, phone.records().getValue(file).cards.getValue("ser").box)
    }

    @Test
    fun `this device's own record an earlier version's write left only the start of is written again, and later answers reach the other device`() = runTest {
        val phone = Device(phoneId, "Pixel 8").start()
        val tablet = Device(tabletId, "Pixel Tablet").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        val nouns = phone.maps.add("# Nouns\n- Casa\n  - house\n")
        val ser = card(box = 1, due = today + 1, at = now - day)
        phone.maps.saveStudy(verbs, mapOf("ser" to ser))
        phone.sync.syncNow()
        val file = "${phone.map("Spanish verbs").metadata.sync!!.file}.json"

        // Cut short by Orbit 0.5.0 stopping as it wrote it: the start of a record, with nothing noted of it.
        val whole = folder.study.getValue(phoneId).getValue(file).first
        folder.writeStudyRecord(phoneId, file, whole.take(whole.length / 2))
        val casa = card(box = 2, due = today + 3, at = now)
        phone.maps.saveStudy(nouns, mapOf("casa" to casa))
        phone.sync.syncNow()
        tablet.sync.syncNow()

        assertEquals(mapOf("ser" to ser), phone.records().getValue(file).cards)
        assertEquals(mapOf(phoneId to mapOf("casa" to casa)), tablet.map("Nouns").metadata.studyElsewhere)
        assertEquals(mapOf(phoneId to mapOf("ser" to ser)), tablet.map("Spanish verbs").metadata.studyElsewhere)
    }

    /** This device's own record, its write cut short by `cut`: Orbit started again writes it whole. */
    private suspend fun assertCutShortWrittenAgain(cut: () -> Throwable) {
        val phone = Device(phoneId, "Pixel 8").start()
        val verbs = phone.maps.add("# Spanish verbs\n- Ser\n  - soy\n")
        phone.maps.saveStudy(verbs, mapOf("ser" to card(box = 1, due = today + 1, at = now - day)))
        folder.cutNextStudyWrite = cut
        phone.sync.syncNow()
        val file = "${phone.map("Spanish verbs").metadata.sync!!.file}.json"
        assertNull(FolderStudy.decode(folder.study.getValue(phoneId).getValue(file).first))

        // Started again, with nothing of that sync in memory: the record is this device's own write, cut short.
        phone.syncing().syncNow()
        assertEquals(1, phone.records().getValue(file).cards.getValue("ser").box)
    }

    @Test
    fun `this device's own record a failure left only the start of is written again whole`() = runTest {
        assertCutShortWrittenAgain { IOException("Cut short") }
    }

    @Test
    fun `this device's own record Orbit stopping left only the start of is written again whole`() = runTest {
        assertCutShortWrittenAgain { Died() }
    }
}
