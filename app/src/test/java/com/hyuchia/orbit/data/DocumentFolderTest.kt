package com.hyuchia.orbit.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * The folder the maps are kept in, as Android's storage access reaches it
 * through a folder's app ([FolderApp]): what the app says of a file's size,
 * or doesn't, never lets a file still arriving pass for whole, nor more of a
 * file be read than a map may hold.
 */
@RunWith(AndroidJUnit4::class)
class DocumentFolderTest {
    @get:Rule
    val disk = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = FileWords(Locale.US)
    private lateinit var folder: DocumentFolder

    @Before
    fun start() {
        Robolectric.setupContentProvider(FolderApp::class.java, FolderApp.AUTHORITY)
        FolderApp.reset(disk.newFolder("app"))
        folder = DocumentFolder(context.contentResolver, FolderApp.tree, words)
    }

    private fun file(name: String) = File(FolderApp.folder, name)

    /** A markdown file of more than a map may hold, a line at a time. */
    private fun tooLarge(file: File) {
        val line = "- An idea\n".toByteArray()
        file.outputStream().buffered().use { output -> repeat((MAP_TEXT_LIMIT / line.size).toInt() + 1) { output.write(line) } }
    }

    @Test
    fun aFileListedEmptyThatHoldsTextByTheTimeItsReadIsStillArriving() {
        // A sync app makes the file, and goes on writing it after the folder is listed.
        file("Trip.md").writeText("")
        assertEquals(0L, folder.files().single().size)
        file("Trip.md").writeText("# Trip\n- Lisbon\n")

        assertThrows(StillArriving::class.java) { folder.read("Trip.md") }

        // Listed again whole, it reads.
        folder.files()
        assertEquals("# Trip\n- Lisbon\n", folder.read("Trip.md"))
    }

    @Test
    fun aPictureListedEmptyThatHoldsBytesByTheTimeItsReadIsStillArriving() {
        val photo = File(FolderApp.folder, "$MEDIA_DIRECTORY/photo.jpg").apply { parentFile!!.mkdirs() }
        photo.writeBytes(ByteArray(0))
        folder.files()
        assertEquals(0L, folder.media().getValue("photo.jpg").size)
        photo.writeBytes(ByteArray(300) { 1 })

        assertThrows(IOException::class.java) { folder.readMedia("photo.jpg", ByteArrayOutputStream()) }
    }

    @Test
    fun aFileTheFolderCantTellTheSizeOfIsReadNoFurtherThanAMapHolds() {
        FolderApp.sizeOf = { null }
        tooLarge(file("Big.md"))
        assertEquals(-1L, folder.files().single().size)

        assertThrows(TooLargeForAMap::class.java) { folder.read("Big.md") }
    }

    @Test
    fun aFileListedSmallerThanItIsNowIsReadNoFurtherThanAMapHolds() {
        // Listed before a sync app wrote the rest of it.
        FolderApp.sizeOf = { 100 }
        tooLarge(file("Big.md"))
        folder.files()

        assertThrows(TooLargeForAMap::class.java) { folder.read("Big.md") }
    }

    @Test
    fun aMarkdownFileTooLargeForAMapWhoseSizeTheFolderCantTellStaysOnlyInTheFolder() = runBlocking {
        FolderApp.sizeOf = { null }
        tooLarge(file("Big.md"))
        file("Trip.md").writeText("# Trip\n- Lisbon\n")
        val (maps, sync) = syncing()

        sync.syncNow()
        assertEquals(listOf("Trip"), maps.maps.value!!.map { it.title })
        assertEquals(listOf("Big.md"), sync.state.value.notes.large)
        assertNull(sync.state.value.problem)
    }

    @Test
    fun aPictureInTheFoldersMediaIsDeletedByName() {
        folder.files()
        val photo = disk.newFile("photo.jpg").apply { writeBytes(ByteArray(64) { 7 }) }
        folder.writeMedia("photo.jpg", photo)

        folder.deleteMedia("photo.jpg")
        assertEquals(emptyList<String>(), File(FolderApp.folder, MEDIA_DIRECTORY).list()!!.toList())
        assertEquals(emptyMap<String, FolderFile>(), folder.media())
    }

    @Test
    fun aPictureTheFoldersAppNumbersAsOneByItsNameArrivedGoesThereUnderANameOfItsOwn() = runBlocking {
        val media = File(FolderApp.folder, MEDIA_DIRECTORY).apply { mkdirs() }
        val (maps, sync) = syncing()
        val trip = maps.add("# Trip\n![](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to ByteArray(300) { 1 }))

        // Another device's picture by the same name reaches the folder right after this one lists its media.
        FolderApp.afterListing = { listed ->
            if (listed == media) {
                FolderApp.afterListing = null
                File(media, "photo.jpg").writeBytes(ByteArray(300) { 3 })
            }
        }

        sync.syncNow()
        // The copy the app numbered ("photo (1).jpg", which no map can point at) goes; the other picture stays as it is.
        assertEquals(listOf("photo.jpg"), media.list()!!.sorted())
        assertArrayEquals(ByteArray(300) { 3 }, File(media, "photo.jpg").readBytes())

        // The next sync finds the other picture by the name, and this map's goes there under a name of its own.
        sync.syncNow()
        assertEquals(listOf("photo-2.jpg", "photo.jpg"), media.list()!!.sorted())
        assertArrayEquals(ByteArray(300) { 1 }, File(media, "photo-2.jpg").readBytes())
        assertEquals("# Trip\n![](media/photo-2.jpg)\n", file("Trip.md").readText())
        assertEquals("# Trip\n![](media/photo-2.jpg)\n", maps.read(trip))
    }

    /** This device's maps, kept in the folder by a sync of their own. */
    private suspend fun syncing(): Pair<MapRepository, FolderSync> {
        val maps = MapRepository(disk.newFolder("maps"), disk.newFolder("trash"), NoStarters, words)
        val settings = SettingsRepository(settingsStoreIn(File(disk.root, "settings.preferences_pb")))
        val sync = FolderSync(maps, settings, CoroutineScope(Dispatchers.Default), File(disk.root, "folder-notes.json"), words, TestEditor(words)) { folder }
        maps.load()
        settings.update { it.copy(syncFolder = FolderApp.tree.toString()) }
        return maps to sync
    }

    private object NoStarters : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }
}
