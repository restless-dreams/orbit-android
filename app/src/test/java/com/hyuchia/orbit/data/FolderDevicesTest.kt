package com.hyuchia.orbit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream

class FolderDevicesTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun device(
        id: String,
        name: String = "Pixel 8",
        code: Int = 5,
        format: Int = 1,
        media: List<String> = listOf("jpg", "mp4", "png"),
        synced: Long = now,
    ) = FolderDevice(id, name, FolderDevice.PHONE, "0.$code.0", code, format, media, synced)

    /**
     * A folder in memory, laid out as a sync app leaves it: its markdown
     * files, `media/`, and the devices' records in `.orbit/devices/`.
     */
    private class MemoryFolder : SyncFolder {
        val maps = mutableMapOf("Trip.md" to "# Trip\n", "Plan.md" to "# Plan\n")
        val media = mutableMapOf("picture-1.jpg" to FolderFile("picture-1.jpg", 1, 10))
        val records = mutableMapOf<String, Pair<String, Long>>()
        val reads = mutableListOf<String>()
        val writes = mutableListOf<String>()
        private var changes = 1L

        override val name = "Maps"

        override fun files(): List<FolderFile> = maps.map { (name, text) -> FolderFile(name, 1, text.length.toLong()) }

        override fun read(name: String): String = maps.getValue(name)

        override fun write(name: String, text: String, create: Boolean): FolderFile {
            maps[name] = text
            return FolderFile(name, 1, text.length.toLong())
        }

        override fun delete(name: String) {
            maps -= name
        }

        override fun media(): Map<String, FolderFile> = media

        override fun readMedia(name: String, into: OutputStream): Boolean = false

        override fun writeMedia(name: String, from: File) = FolderFile(name, 1, from.length())

        override fun deviceRecords(): List<FolderFile> = records.map { (name, record) -> FolderFile(name, record.second, record.first.length.toLong()) }

        override fun readDeviceRecord(name: String): String {
            reads += name
            return records[name]?.first ?: throw FileNotFoundException(name)
        }

        override fun writeDeviceRecord(name: String, text: String) {
            writes += name
            records[name] = text to ++changes
        }

        /** What a listing of the folder's top shows: each file and folder, and whether it's a folder. */
        fun top(): List<Pair<String, Boolean>> =
            maps.keys.map { it to false } + listOf(MEDIA_DIRECTORY to true) + listOfNotNull((ORBIT_DIRECTORY to true).takeIf { records.isNotEmpty() })
    }

    /** What every release so far (0.1.0 to 0.3.0) takes for a map in the folder: `DocumentFolder.files()`'s own test. */
    private fun mapForOlderReleases(name: String, directory: Boolean) = !directory && name.endsWith(".md", ignoreCase = true) && !name.startsWith(".")

    @Test
    fun `a device keeps its record where no release looks for maps, and touches nothing else`() {
        val folder = MemoryFolder()
        val mine = device("2a6c0e4e-0b7e-4c43-9d35-1f6f2d8e7a10")

        assertEquals(emptyList<FolderDevice>(), FolderDeviceRecords(folder, mutableMapOf()).update(mine, now))

        assertEquals(listOf("${mine.id}.json"), folder.writes)
        assertEquals(mine, FolderDeviceRecords.decode(folder.records.getValue("${mine.id}.json").first))
        // Older releases see the same maps as before, and nothing of the records.
        assertEquals(setOf("Trip.md", "Plan.md"), folder.top().filter { (name, directory) -> mapForOlderReleases(name, directory) }.map { it.first }.toSet())
        assertEquals(mapOf("Trip.md" to "# Trip\n", "Plan.md" to "# Plan\n"), folder.maps)
        assertEquals(mapOf("picture-1.jpg" to FolderFile("picture-1.jpg", 1, 10)), folder.media)
    }

    @Test
    fun `a record is written again only when it says something new, or a day later`() {
        val folder = MemoryFolder()
        val known = mutableMapOf<String, Pair<FolderFile, FolderDevice?>>()
        val mine = device("2a6c0e4e-0b7e-4c43-9d35-1f6f2d8e7a10")
        FolderDeviceRecords(folder, known).update(mine, now)

        FolderDeviceRecords(folder, known).update(mine.copy(synced = now + 2 * 60_000), now + 2 * 60_000)
        assertEquals(1, folder.writes.size)

        FolderDeviceRecords(folder, known).update(mine.copy(name = "Tablet", synced = now + 4 * 60_000), now + 4 * 60_000)
        assertEquals(2, folder.writes.size)

        FolderDeviceRecords(folder, known).update(mine.copy(name = "Tablet", synced = now + day + 4 * 60_000), now + day + 4 * 60_000)
        assertEquals(3, folder.writes.size)
    }

    @Test
    fun `other devices' records are read once until they change, and never written`() {
        val folder = MemoryFolder()
        val other = device("7b1d9f30-5c2e-4a8b-b6f1-0e9d8c7b6a51", name = "Tablet", code = 4)
        folder.records["${other.id}.json"] = FolderDeviceRecords.encode(other) to 1L
        val known = mutableMapOf<String, Pair<FolderFile, FolderDevice?>>()
        val mine = device("2a6c0e4e-0b7e-4c43-9d35-1f6f2d8e7a10")

        assertEquals(listOf(other), FolderDeviceRecords(folder, known).update(mine, now))
        assertEquals(listOf(other), FolderDeviceRecords(folder, known).update(mine, now + 60_000))
        assertEquals(1, folder.reads.count { it == "${other.id}.json" })
        assertFalse("${other.id}.json" in folder.writes)

        folder.records["${other.id}.json"] = FolderDeviceRecords.encode(other.copy(appVersionCode = 6)) to 9L
        assertEquals(6, FolderDeviceRecords(folder, known).update(mine, now + 120_000).single().appVersionCode)
    }

    @Test
    fun `records that can't be read, aren't records, or haven't changed in months are left out`() {
        val folder = MemoryFolder()
        val old = device("4c2e8a1b-3d5f-4e6a-8b9c-0d1e2f3a4b5c", name = "Old phone", synced = now - 120 * day)
        folder.records["${old.id}.json"] = FolderDeviceRecords.encode(old) to 1L
        folder.records["5d6e7f80-1a2b-4c3d-9e8f-7a6b5c4d3e2f.json"] = "{ not a record" to 1L
        folder.records["notes.json"] = FolderDeviceRecords.encode(device("x")) to 1L

        assertEquals(emptyList<FolderDevice>(), FolderDeviceRecords(folder, mutableMapOf()).update(device("2a6c0e4e-0b7e-4c43-9d35-1f6f2d8e7a10"), now))
    }

    @Test
    fun `a newer Orbit's record still reads, and this device keeps what a newer Orbit wrote in its own`() {
        val newer = """{"id":"7b1d9f30-5c2e-4a8b-b6f1-0e9d8c7b6a51","name":"Studio laptop","kind":"headset","appVersion":"0.9.0","appVersionCode":12,""" +
            """"mapFormat":2,"mediaTypes":["jpg","mp3"],"synced":$now,"screen":"large"}"""
        val read = FolderDeviceRecords.decode(newer)!!
        assertEquals(12, read.appVersionCode)
        assertEquals(2, read.mapFormat)

        val folder = MemoryFolder()
        val mine = device("2a6c0e4e-0b7e-4c43-9d35-1f6f2d8e7a10")
        // This install, run by a newer Orbit before it went back to this one, wrote a field this version doesn't know.
        folder.records["${mine.id}.json"] = (FolderDeviceRecords.encode(mine.copy(appVersionCode = 9)).dropLast(2) + ",\n    \"screen\": \"large\"\n}") to 1L
        FolderDeviceRecords(folder, mutableMapOf()).update(mine, now)

        val written = Json.parseToJsonElement(folder.records.getValue("${mine.id}.json").first).jsonObject
        assertEquals("large", written.getValue("screen").jsonPrimitive.content)
        assertEquals("5", written.getValue("appVersionCode").jsonPrimitive.content)
    }

    @Test
    fun `a record a newer Orbit says this one would misread is left out`() {
        val record = """{"id":"7b1d9f30-5c2e-4a8b-b6f1-0e9d8c7b6a51","name":"Studio laptop","appVersion":"2.0.0","appVersionCode":30,""" +
            """"mapFormat":3,"mediaTypes":["jpg"],"synced":$now,"format":4"""
        assertEquals(4, FolderDeviceRecords.decode("$record}")!!.format)
        assertNull(FolderDeviceRecords.decode("$record,\"compatible\":2}"))
        assertEquals("1", Json.parseToJsonElement(FolderDeviceRecords.encode(device("2a6c0e4e-0b7e-4c43-9d35-1f6f2d8e7a10"))).jsonObject.getValue("compatible").jsonPrimitive.content)
    }

    @Test
    fun `a device hears about another's newer or older Orbit only as far as it matters`() {
        val me = device("me", code = 5, media = listOf("jpg", "mp4"))
        val newer = device("newer", name = "Tablet", code = 7, media = listOf("jpg", "mp3", "mp4"))
        val older = device("older", name = "Old phone", code = 4, media = listOf("jpg"))
        val same = device("same", name = "Laptop", code = 5, media = listOf("jpg", "mp4"))

        val notices = FolderRoster.notices(me, listOf(newer, older, same), present = setOf("jpg", "mp3", "mp4"))
        assertEquals(
            listOf(FolderNotice.Newer(newer, unopened = listOf("mp3"), newerFormat = false), FolderNotice.Older(older, unopened = listOf("mp4"), showsOnly = false)),
            notices,
        )
        assertTrue(notices.all { it.matters })

        // Files of kinds the folder doesn't hold don't matter, and neither does a version alone.
        val quiet = FolderRoster.notices(me, listOf(newer, older), present = setOf("jpg"))
        assertEquals(2, quiet.size)
        assertFalse(quiet.any { it.matters })

        // A newer map format matters even without files: maps it saves only show here.
        val formats = FolderRoster.notices(me, listOf(newer.copy(mapFormat = 2), older.copy(mapFormat = 0)), present = emptySet())
        assertEquals(listOf(true, true), formats.map { it.matters })
        assertTrue((formats[0] as FolderNotice.Newer).newerFormat)
        assertTrue((formats[1] as FolderNotice.Older).showsOnly)
    }

    @Test
    fun `a notice put away comes back only for another version`() {
        val other = device("other", code = 7, media = listOf("jpg", "mp3"))
        val first = FolderRoster.notices(device("me"), listOf(other), setOf("mp3")).single()
        val updated = FolderRoster.notices(device("me"), listOf(other.copy(appVersionCode = 8)), setOf("mp3")).single()
        assertFalse(first.key == updated.key)
        assertEquals(first.key, FolderRoster.notices(device("me"), listOf(other.copy(name = "Renamed")), setOf("mp3")).single().key)
    }

    @Test
    fun `before maps go out in a newer format, the devices that could only show them are known`() {
        val others = listOf(device("a", format = 1), device("b", format = 2), device("c", format = 3))
        assertEquals(listOf("a"), FolderRoster.leftBehind(others, format = 2).map { it.id })
        assertEquals(emptyList<String>(), FolderRoster.leftBehind(others, format = MAP_FORMAT).map { it.id })
    }

    @Test
    fun `the roster leaves this device out, and keeps devices in order by name`() {
        val records = listOf(device("me"), device("b", name = "tablet"), device("a", name = "Laptop"))
        assertEquals(listOf("a", "b"), FolderRoster.current(records, me = "me", now = now).map { it.id })
        assertNull(FolderRoster.current(listOf(device("gone", synced = now - 91 * day)), me = "me", now = now).firstOrNull())
    }
}
