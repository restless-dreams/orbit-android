package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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

    /** A device keeping its maps in the test's folder. */
    private inner class Device(name: String) {
        val maps = MapRepository(File(root, "$name/maps"), File(root, "$name/trash"), starters, words)
        val settings = SettingsRepository(settingsStoreIn(File(root, "$name/settings.preferences_pb")))
        val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(root, "$name/folder-notes.json"), words, TestEditor(words)) { folder }

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
}
