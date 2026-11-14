package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Locale

/** Pictures in the folder's one `media/`: ones that arrive after their maps, ones still arriving, and two maps' pictures by one name, of one size or not. */
class FolderSyncMediaTest {
    private val root: File = Files.createTempDirectory("orbit-folder-media").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val folder = TestFolder()

    /** The clock the devices' syncs tell how long something has gone on by. */
    private var clock = 0L

    /** A device keeping its maps in the test's folder, with the `room` its storage has. */
    private inner class Device(name: String, room: (File) -> Long = { Long.MAX_VALUE }) {
        val maps = MapRepository(File(root, "$name/maps"), File(root, "$name/trash"), starters, words, room = room)
        val settings = SettingsRepository(settingsStoreIn(File(root, "$name/settings.preferences_pb")))
        val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "$name/folder-notes.json"), words, TestEditor(words), elapsed = { clock }) { folder }

        suspend fun start(): Device {
            maps.load()
            settings.update { it.copy(syncFolder = "memory") }
            return this
        }

        fun map(title: String) = maps.maps.value!!.single { it.title == title }
    }

    // Not real pictures: the folder and the maps carry them as bytes.
    private val photoA = ByteArray(300) { 1 }
    private val photoB = ByteArray(200) { 2 }

    /** Another picture as long as [photoA]. */
    private val photoC = ByteArray(300) { 3 }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a picture that reaches the folder after its map's text is fetched by a later sync`() = runTest {
        val phone = Device("phone").start()
        folder.change("Late media.md", "# Late media\n![](media/late-photo.jpg)\n")
        phone.sync.syncNow()
        val late = phone.map("Late media").id
        assertNull(phone.maps.picture(late, "late-photo.jpg"))

        folder.mediaFiles["late-photo.jpg"] = photoA
        phone.sync.syncNow()
        assertArrayEquals(photoA, phone.maps.picture(late, "late-photo.jpg")!!.readBytes())
    }

    @Test
    fun `a map's picture on Home is drawn again once a picture that reached the folder late is here`() = runTest {
        val phone = Device("phone").start()
        folder.change("Late media.md", "# Late media\n![](media/late-photo.jpg)\n")
        phone.sync.syncNow()
        val before = phone.map("Late media")
        assertEquals(setOf("late-photo.jpg"), before.missingMedia)

        folder.mediaFiles["late-photo.jpg"] = photoA
        phone.sync.syncNow()
        val after = phone.map("Late media")
        assertEquals(emptySet<String>(), after.missingMedia)
        // Drawn again with the photo, while what the folder is compared by stays, as the text did.
        assertNotEquals(before.pictureKey, after.pictureKey)
        assertEquals(before.contentKey, after.contentKey)
    }

    @Test
    fun `a picture still arriving in the folder isn't taken until it's whole`() = runTest {
        val phone = Device("phone").start()
        folder.mediaFiles["photo.jpg"] = photoA.copyOf(100)
        folder.mediaListedSizes["photo.jpg"] = photoA.size.toLong()
        folder.change("Trip.md", "# Trip\n![](media/photo.jpg)\n")
        phone.sync.syncNow()
        val trip = phone.map("Trip").id
        assertNull(phone.maps.picture(trip, "photo.jpg"))
        assertNull(phone.sync.state.value.problem)

        folder.mediaFiles["photo.jpg"] = photoA
        folder.mediaListedSizes.clear()
        phone.sync.syncNow()
        assertArrayEquals(photoA, phone.maps.picture(trip, "photo.jpg")!!.readBytes())
    }

    @Test
    fun `two maps' pictures by one name each keep their own in the folder, and another device gets the right one`() = runTest {
        val phone = Device("phone").start()
        val twinA = phone.maps.add("# Twin A\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()
        val twinB = phone.maps.add("# Twin B\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoB))
        phone.sync.syncNow()

        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertArrayEquals(photoB, folder.mediaFiles["photo-2.jpg"])
        assertEquals("# Twin B\n![](media/photo-2.jpg)\n", phone.maps.read(twinB))
        assertEquals("# Twin B\n![](media/photo-2.jpg)\n", folder.texts["Twin B.md"])
        assertEquals("# Twin A\n![](media/photo.jpg)\n", phone.maps.read(twinA))

        val tablet = Device("tablet").start()
        tablet.sync.syncNow()
        assertArrayEquals(photoB, tablet.maps.picture(tablet.map("Twin B").id, "photo-2.jpg")!!.readBytes())
        assertArrayEquals(photoA, tablet.maps.picture(tablet.map("Twin A").id, "photo.jpg")!!.readBytes())
    }

    @Test
    fun `two maps' pictures by one name and of one size each keep their own in the folder`() = runTest {
        val phone = Device("phone").start()
        val twinA = phone.maps.add("# Twin A\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()
        val twinC = phone.maps.add("# Twin C\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoC))
        phone.sync.syncNow()

        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertArrayEquals(photoC, folder.mediaFiles["photo-2.jpg"])
        assertEquals("# Twin C\n![](media/photo-2.jpg)\n", phone.maps.read(twinC))
        assertEquals("# Twin C\n![](media/photo-2.jpg)\n", folder.texts["Twin C.md"])
        assertEquals("# Twin A\n![](media/photo.jpg)\n", phone.maps.read(twinA))

        val tablet = Device("tablet").start()
        tablet.sync.syncNow()
        assertArrayEquals(photoC, tablet.maps.picture(tablet.map("Twin C").id, "photo-2.jpg")!!.readBytes())
        assertArrayEquals(photoA, tablet.maps.picture(tablet.map("Twin A").id, "photo.jpg")!!.readBytes())
    }

    @Test
    fun `a picture the folder can't tell the size of is compared, not taken for this map's own`() = runTest {
        val phone = Device("phone").start()
        phone.maps.add("# Twin A\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()
        folder.mediaListedSizes["photo.jpg"] = -1
        val twinB = phone.maps.add("# Twin B\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoB))
        val same = phone.maps.add("# Same\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()

        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertArrayEquals(photoB, folder.mediaFiles["photo-2.jpg"])
        assertEquals("# Twin B\n![](media/photo-2.jpg)\n", phone.maps.read(twinB))
        // The very same picture is the folder's already: it keeps its name, and nothing is copied twice.
        assertEquals("# Same\n![](media/photo.jpg)\n", phone.maps.read(same))
        assertEquals(setOf("photo.jpg", "photo-2.jpg"), folder.mediaFiles.keys)
    }

    /** A map's text whose ideas each have one of `names` as their file, as Orbit writes an idea's files. */
    private fun withFiles(title: String, vararg names: String): String = buildString {
        append("# ").append(title).append('\n')

        names.forEachIndexed { index, name ->
            append("- Idea ").append(index + 1).append(" <!-- orbit files: media/").append(name).append(" -->\n")
        }
    }

    @Test
    fun `another map's file in the folder that holds only the start of this map's stays, and this map's gets a name of its own`() = runTest {
        val phone = Device("phone").start()
        val earlier = "abc".toByteArray()
        val longer = "abcdef".toByteArray()
        phone.maps.add(withFiles("Earlier", "file-notes.txt"), pictures = mapOf("file-notes.txt" to earlier))
        phone.sync.syncNow()
        val later = phone.maps.add(withFiles("Later", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))

        phone.sync.syncNow()

        // Not taken for a copy cut short: no device was writing it, so it's another map's, and it stays as it is.
        assertArrayEquals(earlier, folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(longer, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("Later", "file-notes-2.txt"), phone.maps.read(later))
        assertEquals(withFiles("Later", "file-notes-2.txt"), folder.texts["Later.md"])

        val tablet = Device("tablet").start()
        tablet.sync.syncNow()
        assertArrayEquals(earlier, tablet.maps.picture(tablet.map("Earlier").id, "file-notes.txt", listOf("file-notes.txt"))!!.readBytes())
        assertArrayEquals(longer, tablet.maps.picture(tablet.map("Later").id, "file-notes-2.txt", listOf("file-notes-2.txt"))!!.readBytes())
    }

    @Test
    fun `a picture this device's write left only the start of in the folder is written again whole, under its name, after Orbit stops`() = runTest {
        val phone = Device("phone").start()
        val trip = phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        folder.cutNextMediaWrite = true
        phone.sync.syncNow()
        assertArrayEquals(photoA.copyOf(150), folder.mediaFiles["photo.jpg"])

        // Orbit started again, with nothing of that sync in memory: what it was writing is still known.
        val again = FolderSync(phone.maps, phone.settings, CoroutineScope(Dispatchers.Default), File(root, "phone/folder-notes.json"), words, TestEditor(words), elapsed = { clock }) { folder }
        again.syncNow()

        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertEquals(setOf("photo.jpg"), folder.mediaFiles.keys)
        assertEquals("# Trip\n![](media/photo.jpg)\n", phone.maps.read(trip))
        assertEquals("# Trip\n![](media/photo.jpg)\n", folder.texts["Trip.md"])
        assertNull(again.state.value.problem)
        // Seen whole, it's no longer being written.
        assertFalse(File(root, "phone/folder-media-writes.json").exists())
    }

    /** Orbit stopping in the middle of a sync: past each step's own catch, as nothing runs after it. */
    private class Died : Error()

    /** Orbit started again on `device`, with nothing of its syncs in memory but what it keeps in its files. */
    private fun restarted(device: Device, name: String) =
        FolderSync(device.maps, device.settings, CoroutineScope(Dispatchers.Default), File(root, "$name/folder-notes.json"), words, TestEditor(words), elapsed = { clock }) { folder }

    @Test
    fun `a picture this device's write left only the start of as Orbit stopped is written again whole, under its name`() = runTest {
        val phone = Device("phone").start()
        phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        folder.cutNextMediaWrite = true
        folder.cutMediaWriteStops = { Died() }
        phone.sync.syncNow()
        assertArrayEquals(photoA.copyOf(150), folder.mediaFiles["photo.jpg"])

        restarted(phone, "phone").syncNow()

        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertEquals(setOf("photo.jpg"), folder.mediaFiles.keys)
        assertEquals("# Trip\n![](media/photo.jpg)\n", folder.texts["Trip.md"])
    }

    @Test
    fun `a write the folder's app cleared away once it failed vouches for nothing, and another device's file by its name stays`() = runTest {
        val phone = Device("phone").start()
        val earlier = "abc".toByteArray()
        val longer = "abcdef".toByteArray()
        val first = phone.maps.add(withFiles("First", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))
        folder.cutNextMediaWrite = true
        folder.cutMediaWriteCleared = true
        phone.sync.syncNow()
        assertNull(folder.mediaFiles["file-notes.txt"])
        phone.maps.delete(first)

        // Another device's idea's file by that name, the start of this device's, and its map.
        folder.mediaFiles["file-notes.txt"] = earlier
        folder.change("Other.md", withFiles("Other", "file-notes.txt"))
        phone.sync.syncNow()
        val later = phone.maps.add(withFiles("Later", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))
        phone.sync.syncNow()

        assertArrayEquals(earlier, folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(longer, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("Later", "file-notes-2.txt"), phone.maps.read(later))
        assertEquals(withFiles("Other", "file-notes.txt"), folder.texts["Other.md"])
    }

    @Test
    fun `a write cut short that another app then wrote over, shorter, vouches for nothing, and that file stays`() = runTest {
        val phone = Device("phone").start()
        val longer = "abcdef".toByteArray()
        val trip = phone.maps.add(withFiles("Trip", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))
        folder.cutNextMediaWrite = true
        phone.sync.syncNow()
        assertArrayEquals("abc".toByteArray(), folder.mediaFiles["file-notes.txt"])

        // Written over by another app before this device's next sync: another file, which only starts as this one does.
        folder.mediaFiles["file-notes.txt"] = "ab".toByteArray()
        folder.mediaTimes["file-notes.txt"] = 99_000
        phone.sync.syncNow()

        assertArrayEquals("ab".toByteArray(), folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(longer, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("Trip", "file-notes-2.txt"), phone.maps.read(trip))
    }

    /**
     * A write the folder's app made beside a file by its name that arrived
     * meanwhile, which fails, the folder's app taking what it left away
     * (`cleared`) or not: it vouches for nothing, and that file stays.
     */
    private suspend fun assertWriteMadeBesideVouchesForNothing(cleared: Boolean) {
        val phone = Device("phone").start()
        val earlier = "abc".toByteArray()
        val longer = "abcdef".toByteArray()
        val first = phone.maps.add(withFiles("First", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))

        // Another device's idea's file by that name, the start of this device's, reaches the folder right after it lists its media,
        // with its map: this device's write is made beside it, and fails, the folder's app taking it away.
        folder.afterMediaListing = {
            folder.mediaFiles["file-notes.txt"] = earlier
            folder.change("Other.md", withFiles("Other", "file-notes.txt"))
        }

        folder.cutNextMediaWrite = true
        folder.cutMediaWriteCleared = cleared
        phone.sync.syncNow()
        assertArrayEquals(earlier, folder.mediaFiles["file-notes.txt"])

        phone.sync.syncNow()

        assertArrayEquals(earlier, folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(longer, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("First", "file-notes-2.txt"), phone.maps.read(first))
        assertEquals(withFiles("Other", "file-notes.txt"), folder.texts["Other.md"])
    }

    @Test
    fun `a write made beside a file by its name that arrived meanwhile, then taken away, vouches for nothing, and that file stays`() = runTest {
        assertWriteMadeBesideVouchesForNothing(cleared = true)
    }

    @Test
    fun `a write made beside a file by its name that arrived meanwhile, its start left there, vouches for nothing, and that file stays`() = runTest {
        assertWriteMadeBesideVouchesForNothing(cleared = false)
    }

    @Test
    fun `a write that fails without saying what it left vouches for nothing, and a file by its name stays`() = runTest {
        val phone = Device("phone").start()
        val longer = "abcdef".toByteArray()
        val trip = phone.maps.add(withFiles("Trip", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))
        folder.cutNextMediaWrite = true
        folder.cutMediaWriteStops = { IOException("The folder's app went away") }
        phone.sync.syncNow()
        assertArrayEquals("abc".toByteArray(), folder.mediaFiles["file-notes.txt"])

        phone.sync.syncNow()

        assertArrayEquals("abc".toByteArray(), folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(longer, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("Trip", "file-notes-2.txt"), phone.maps.read(trip))
    }

    @Test
    fun `a write of a copy cut short that the folder refuses keeps vouching only for that copy, not for a file written over it since`() = runTest {
        val phone = Device("phone").start()
        val longer = "abcdef".toByteArray()
        val trip = phone.maps.add(withFiles("Trip", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))
        folder.cutNextMediaWrite = true
        phone.sync.syncNow()
        // Written again, the folder out of reach for a moment.
        folder.failNextMediaWrite = SecurityException("No access to the folder")
        phone.sync.syncNow()
        assertArrayEquals("abc".toByteArray(), folder.mediaFiles["file-notes.txt"])

        // Written over by another app before this device's next sync: another file, which only starts as this one does.
        folder.mediaFiles["file-notes.txt"] = "ab".toByteArray()
        folder.mediaTimes["file-notes.txt"] = 99_000
        phone.sync.syncNow()

        assertArrayEquals("ab".toByteArray(), folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(longer, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("Trip", "file-notes-2.txt"), phone.maps.read(trip))
    }

    @Test
    fun `a picture this device's write left only the start of waits for room to be written over, and then is, under its name`() = runTest {
        val phone = Device("phone").start()
        phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        folder.cutNextMediaWrite = true
        phone.sync.syncNow()
        assertArrayEquals(photoA.copyOf(150), folder.mediaFiles["photo.jpg"])

        // Room for the half still to come, but not for some to spare beside it.
        folder.room = ROOM_LEFT_FREE + 149
        phone.sync.syncNow()
        assertArrayEquals(photoA.copyOf(150), folder.mediaFiles["photo.jpg"])
        assertNull(folder.texts["Trip.md"])
        assertEquals("A picture, video or sound needs more room than this device has free. Orbit tries again in a moment.", phone.sync.state.value.problem)

        folder.room = ROOM_LEFT_FREE + 150
        phone.sync.syncNow()
        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertEquals(setOf("photo.jpg"), folder.mediaFiles.keys)
        assertEquals("# Trip\n![](media/photo.jpg)\n", folder.texts["Trip.md"])
        assertNull(phone.sync.state.value.problem)
    }

    @Test
    fun `a map's file that holds only the start of the folder's by its name gets a name of its own where the folder tells sizes`() = runTest {
        val phone = Device("phone").start()
        val longer = "abcdef".toByteArray()
        val earlier = "abc".toByteArray()
        phone.maps.add(withFiles("Later", "file-notes.txt"), pictures = mapOf("file-notes.txt" to longer))
        phone.sync.syncNow()
        val first = phone.maps.add(withFiles("Earlier", "file-notes.txt"), pictures = mapOf("file-notes.txt" to earlier))

        phone.sync.syncNow()

        // The folder's isn't this map's: its file goes under a name of its own, rather than point at the longer one.
        assertArrayEquals(longer, folder.mediaFiles["file-notes.txt"])
        assertArrayEquals(earlier, folder.mediaFiles["file-notes-2.txt"])
        assertEquals(withFiles("Earlier", "file-notes-2.txt"), phone.maps.read(first))
    }

    @Test
    fun `a map's picture that holds only the start of the folder's, where the folder can't tell sizes, may be one taken as it arrived, and points at the folder's`() = runTest {
        val phone = Device("phone").start()
        phone.maps.add("# Whole\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()
        folder.mediaListedSizes["photo.jpg"] = -1
        val cut = phone.maps.add("# Cut\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA.copyOf(100)))

        phone.sync.syncNow()

        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertEquals(setOf("photo.jpg"), folder.mediaFiles.keys)
        assertEquals("# Cut\n![](media/photo.jpg)\n", phone.maps.read(cut))
    }

    @Test
    fun `a name of its own for a map's picture skips the map's other files by the names it would take`() = runTest {
        val phone = Device("phone").start()
        val remote = "remote".toByteArray()
        val report = "report".toByteArray()
        val second = "second".toByteArray()
        phone.maps.add(withFiles("Remote", "file-report.pdf"), pictures = mapOf("file-report.pdf" to remote))
        phone.sync.syncNow()
        val local = phone.maps.add(withFiles("Local", "file-report.pdf", "file-report-2.pdf"), pictures = mapOf("file-report.pdf" to report, "file-report-2.pdf" to second))

        phone.sync.syncNow()

        // Not -2, the map's own second report: each idea keeps its own file.
        assertArrayEquals(remote, folder.mediaFiles["file-report.pdf"])
        assertEquals(withFiles("Local", "file-report-3.pdf", "file-report-2.pdf"), phone.maps.read(local))
        val files = fileNamesIn(phone.maps.read(local))
        assertArrayEquals(report, phone.maps.picture(local, "file-report-3.pdf", files)!!.readBytes())
        assertArrayEquals(second, phone.maps.picture(local, "file-report-2.pdf", files)!!.readBytes())
        assertArrayEquals(report, folder.mediaFiles["file-report-3.pdf"])
        assertArrayEquals(second, folder.mediaFiles["file-report-2.pdf"])
    }

    @Test
    fun `a name of its own for a map's file skips one the map keeps only for its earlier versions`() = runTest {
        val phone = Device("phone").start()
        val remote = "remote".toByteArray()
        val earlier = "earlier".toByteArray()
        val report = "report".toByteArray()
        phone.maps.add(withFiles("Remote", "file-report.pdf"), pictures = mapOf("file-report.pdf" to remote))
        phone.sync.syncNow()
        // Its idea had another report before, which stays in its media for the version that points at it.
        val local = phone.maps.add(withFiles("Local", "file-report-2.pdf"), pictures = mapOf("file-report-2.pdf" to earlier))
        phone.maps.write(local, withFiles("Local", "file-report.pdf"))

        phone.maps.addMediaFile(local, "file-report.pdf", files = listOf("file-report.pdf")) { output ->
            output.write(report)
            true
        }

        phone.sync.syncNow()

        assertEquals(withFiles("Local", "file-report-3.pdf"), phone.maps.read(local))
        assertArrayEquals(report, folder.mediaFiles["file-report-3.pdf"])
        assertArrayEquals(earlier, File(root, "phone/maps/$local/media/file-report-2.pdf").readBytes())
    }

    @Test
    fun `a picture found to be the folder's isn't read from the folder again while neither changes`() = runTest {
        val phone = Device("phone").start()
        val trip = phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()
        val tablet = Device("tablet").start()
        tablet.sync.syncNow()
        val there = tablet.map("Trip").id

        // The tablet took the picture from the folder, and the phone put it there: neither reads it again to write the map.
        folder.mediaReads.clear()
        phone.maps.write(trip, "# Trip\n![](media/photo.jpg)\n- Beach\n")
        phone.sync.syncNow()
        tablet.sync.syncNow()
        tablet.maps.write(there, "# Trip\n![](media/photo.jpg)\n- Beach\n- Sunset\n")
        tablet.sync.syncNow()
        phone.sync.syncNow()
        assertEquals("# Trip\n![](media/photo.jpg)\n- Beach\n- Sunset\n", phone.maps.read(trip))
        assertEquals(emptyList<String>(), folder.mediaReads)

        // Another app puts another picture of the same size under its name: the next write finds it isn't the map's.
        folder.mediaFiles["photo.jpg"] = photoC
        folder.mediaTimes["photo.jpg"] = 99
        phone.maps.write(trip, "# Trip\n![](media/photo.jpg)\n- Beach\n- Sunset\n- Dinner\n")
        phone.sync.syncNow()
        assertEquals(listOf("photo.jpg"), folder.mediaReads)
        assertEquals("# Trip\n![](media/photo-2.jpg)\n- Beach\n- Sunset\n- Dinner\n", folder.texts["Trip.md"])
        assertArrayEquals(photoA, folder.mediaFiles["photo-2.jpg"])
    }

    @Test
    fun `a picture for a map moved to the trash doesn't bring its folder back`() = runTest {
        val phone = Device("phone").start()
        val gone = phone.maps.add("# Gone\n")
        phone.maps.delete(gone)
        assertEquals(0L, phone.maps.addMediaFile(gone, "photo.jpg") { output -> output.write(photoA); true })
        assertFalse(File(root, "phone/maps/$gone").exists())
        assertNotNull(phone.maps.trash.value.singleOrNull { it.title == "Gone" })
        assertTrue(phone.maps.maps.value!!.none { it.id == gone })
    }

    @Test
    fun `a picture the folder's app names otherwise keeps its map pointing at it`() = runTest {
        // An app that keeps the names of what it holds in lower case, say.
        folder.mediaNamed = { it.lowercase() }
        val phone = Device("phone").start()
        val trip = phone.maps.add("# Trip\n![](media/Photo.jpg)\n", pictures = mapOf("Photo.jpg" to photoA))
        phone.sync.syncNow()

        assertEquals(setOf("photo.jpg"), folder.mediaFiles.keys)
        assertEquals("# Trip\n![](media/photo.jpg)\n", phone.maps.read(trip))
        assertEquals("# Trip\n![](media/photo.jpg)\n", folder.texts["Trip.md"])

        // Another device finds the picture the map points at.
        val tablet = Device("tablet").start()
        tablet.sync.syncNow()
        assertArrayEquals(photoA, tablet.maps.picture(tablet.map("Trip").id, "photo.jpg")!!.readBytes())
    }

    @Test
    fun `a picture the folder's app numbers, as one by its name arrived meanwhile, goes there under a name of its own`() = runTest {
        val phone = Device("phone").start()
        val trip = phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))

        // Another device's picture by the same name reaches the folder after this one listed it, as this one's goes there.
        folder.mediaNamed = { name ->
            folder.mediaNamed = null
            folder.mediaFiles["photo.jpg"] = photoC
            name
        }

        phone.sync.syncNow()
        // The copy the app numbered ("photo (1).jpg", which no map can point at) goes, and the other picture stays as it is.
        assertEquals(setOf("photo.jpg"), folder.mediaFiles.keys)
        assertArrayEquals(photoC, folder.mediaFiles["photo.jpg"])

        // The next sync finds the other picture by the name, and this map's goes there under a name of its own.
        phone.sync.syncNow()
        assertArrayEquals(photoA, folder.mediaFiles["photo-2.jpg"])
        assertEquals("# Trip\n![](media/photo-2.jpg)\n", folder.texts["Trip.md"])
        assertEquals("# Trip\n![](media/photo-2.jpg)\n", phone.maps.read(trip))
    }

    @Test
    fun `a map with a file's very text, but another picture by its name, gives its own a name of its own`() = runTest {
        val phone = Device("phone").start()
        phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()

        // The tablet has the same text, with another picture by the name: as two maps from one template have.
        val tablet = Device("tablet").start()
        val there = tablet.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoC))
        tablet.sync.syncNow()

        // Its picture went to the folder under a name of its own, and its map and the file point at it.
        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertArrayEquals(photoC, folder.mediaFiles["photo-2.jpg"])
        assertEquals("# Trip\n![](media/photo-2.jpg)\n", tablet.maps.read(there))
        assertEquals(mapOf("Trip.md" to "# Trip\n![](media/photo-2.jpg)\n"), folder.texts)

        // The phone takes the file's text and the picture it points at; the one it had stays for its earlier version.
        phone.sync.syncNow()
        val trip = phone.map("Trip").id
        assertEquals("# Trip\n![](media/photo-2.jpg)\n", phone.maps.read(trip))
        assertArrayEquals(photoC, phone.maps.picture(trip, "photo-2.jpg")!!.readBytes())
        assertArrayEquals(photoA, phone.maps.picture(trip, "photo.jpg")!!.readBytes())
    }

    @Test
    fun `a picture too big for its map is tried again once the folder's file changes`() = runTest {
        val phone = Device("phone").start()
        // Listed at more than a map keeps.
        folder.mediaFiles["big.jpg"] = photoA
        folder.mediaListedSizes["big.jpg"] = MAP_MEDIA_LIMIT + 1
        folder.change("Trip.md", "# Trip\n![](media/big.jpg)\n")
        phone.sync.syncNow()
        val trip = phone.map("Trip").id
        assertNull(phone.maps.picture(trip, "big.jpg"))
        assertEquals(listOf("big.jpg, in “Trip”"), phone.sync.state.value.notes.tooBig)

        // As it was, it isn't tried again.
        phone.sync.syncNow()
        assertNull(phone.maps.picture(trip, "big.jpg"))
        assertEquals(emptyList<String>(), folder.mediaReads)

        // Put there again, smaller: the next sync takes it.
        folder.mediaListedSizes.clear()
        folder.mediaTimes["big.jpg"] = 99
        phone.sync.syncNow()
        assertArrayEquals(photoA, phone.maps.picture(trip, "big.jpg")!!.readBytes())
    }

    @Test
    fun `a picture too big for its map's room is tried again once the map has room`() = runTest {
        val phone = Device("phone").start()
        // The map's own pictures take nearly all the room a map has: its huge one is here already, and its last one isn't yet.
        folder.mediaFiles["huge.jpg"] = ByteArray(1)
        folder.change("Trip.md", "# Trip\n![](media/huge.jpg)\n![](media/last.jpg)\n")
        phone.sync.syncNow()
        val trip = phone.map("Trip").id
        RandomAccessFile(phone.maps.picture(trip, "huge.jpg"), "rw").use { it.setLength(MAP_MEDIA_LIMIT - 100) }

        folder.mediaFiles["last.jpg"] = photoA
        phone.sync.syncNow()
        assertNull(phone.maps.picture(trip, "last.jpg"))
        assertEquals(listOf("last.jpg, in “Trip”"), phone.sync.state.value.notes.tooBig)

        // The huge one taken out of the map: now there's room for the last one.
        phone.maps.write(trip, "# Trip\n![](media/last.jpg)\n")
        phone.sync.syncNow()
        assertArrayEquals(photoA, phone.maps.picture(trip, "last.jpg")!!.readBytes())
    }

    @Test
    fun `a picture from the folder this device hasn't room for waits for room, and the sync says why`() = runTest {
        var room = ROOM_LEFT_FREE + photoA.size - 1
        val phone = Device("phone", room = { room }).start()
        folder.mediaFiles["photo.jpg"] = photoA
        folder.change("Trip.md", "# Trip\n![](media/photo.jpg)\n")
        phone.sync.syncNow()

        // Its map came without it, and nothing of it was written.
        val trip = phone.map("Trip").id
        assertNull(phone.maps.picture(trip, "photo.jpg"))
        assertEquals("A picture, video or sound needs more room than this device has free. Orbit tries again in a moment.", phone.sync.state.value.problem)

        room = Long.MAX_VALUE
        phone.sync.syncNow()
        assertArrayEquals(photoA, phone.maps.picture(trip, "photo.jpg")!!.readBytes())
        assertNull(phone.sync.state.value.problem)
    }

    @Test
    fun `a map's picture the folder hasn't room for waits with its map, and the sync says why`() = runTest {
        val phone = Device("phone").start()
        folder.room = ROOM_LEFT_FREE + photoA.size - 1
        phone.maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to photoA))
        phone.sync.syncNow()

        assertNull(folder.mediaFiles["photo.jpg"])
        assertNull(folder.texts["Trip.md"])
        assertEquals("A picture, video or sound needs more room than this device has free. Orbit tries again in a moment.", phone.sync.state.value.problem)

        folder.room = Long.MAX_VALUE
        phone.sync.syncNow()
        assertArrayEquals(photoA, folder.mediaFiles["photo.jpg"])
        assertEquals("# Trip\n![](media/photo.jpg)\n", folder.texts["Trip.md"])
        assertNull(phone.sync.state.value.problem)
    }

    @Test
    fun `a picture that goes on failing to arrive is a problem the sync reports, once it has for a while`() = runTest {
        val phone = Device("phone").start()
        // Never whole: the folder's app lists it at a size it never reaches.
        folder.mediaFiles["photo.jpg"] = photoA.copyOf(100)
        folder.mediaListedSizes["photo.jpg"] = photoA.size.toLong()
        folder.change("Trip.md", "# Trip\n![](media/photo.jpg)\n")
        phone.sync.syncNow()
        assertNull(phone.sync.state.value.problem)

        clock += MEDIA_PATIENCE.inWholeMilliseconds
        phone.sync.syncNow()
        assertEquals("One map is still missing pictures, videos or sounds from the folder. Orbit tries again in a moment.", phone.sync.state.value.problem)
        assertEquals(1, phone.sync.state.value.failures)

        // Once it arrives whole, it's in step again.
        folder.mediaFiles["photo.jpg"] = photoA
        folder.mediaListedSizes.clear()
        phone.sync.syncNow()
        assertArrayEquals(photoA, phone.maps.picture(phone.map("Trip").id, "photo.jpg")!!.readBytes())
        assertNull(phone.sync.state.value.problem)
    }
}
