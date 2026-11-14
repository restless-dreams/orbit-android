package com.hyuchia.orbit.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.FolderApp
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.UnreadableMapFileException
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.privacy.conceals
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Export all writing into a document another app keeps, as the save
 * screen gives it: a new one, or an earlier backup the person chose to
 * write over. Each way it can fail leaves no half a backup, and never
 * deletes one it didn't start writing.
 */
@RunWith(AndroidJUnit4::class)
class ExportIntoTest {
    @get:Rule
    val disk = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val earlier = "an earlier backup".toByteArray()

    @Before
    fun start() {
        Robolectric.setupContentProvider(FolderApp::class.java, FolderApp.AUTHORITY)
        FolderApp.reset(disk.newFolder("app"))
    }

    private fun file(name: String) = File(FolderApp.folder, name)

    /** A zip of `count` maps, as Export all writes it. */
    private fun maps(count: Int): suspend (OutputStream) -> Int = { output ->
        output.write("zip".toByteArray())
        count
    }

    @Test
    fun `the zip goes into the document, over what it held`() = runTest {
        file("Orbit maps.zip").writeBytes(earlier)

        assertEquals(Exported.Maps(3), exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip"), export = maps(3)))
        assertArrayEquals("zip".toByteArray(), file("Orbit maps.zip").readBytes())
    }

    @Test
    fun `an earlier backup that can't be opened for the new one is left as it was`() = runTest {
        file("Orbit maps.zip").writeBytes(earlier)
        FolderApp.beforeOpening = { _, _ -> throw FileNotFoundException("The drive is offline") }
        var exported = false

        val result = exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip")) {
            exported = true
            3
        }

        assertEquals(Exported.NotWritten, result)
        assertFalse(exported)
        assertArrayEquals(earlier, file("Orbit maps.zip").readBytes())
    }

    @Test
    fun `a new document that can't be opened goes, empty as the save screen made it`() = runTest {
        file("Orbit maps.zip").createNewFile()
        FolderApp.beforeOpening = { _, _ -> throw IllegalStateException("The app that keeps it stopped") }

        assertEquals(Exported.NotWritten, exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip"), export = maps(3)))
        assertFalse(file("Orbit maps.zip").exists())
    }

    @Test
    fun `a zip that stops partway goes, earlier backup or not`() = runTest {
        file("Orbit maps.zip").writeBytes(earlier)

        val result = exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip")) { output ->
            output.write("half a zip".toByteArray())
            throw IOException("No room left on the drive")
        }

        assertEquals(Exported.NotWritten, result)
        assertFalse(file("Orbit maps.zip").exists())
    }

    @Test
    fun `a zip the app that keeps it can't finish goes`() = runTest {
        val target = file("Orbit maps.zip")
        target.writeBytes(earlier)
        val uri = FolderApp.document("Orbit maps.zip")

        // The app takes every byte, and says as the document closes that it couldn't keep them.
        shadowOf(context.contentResolver).registerOutputStreamSupplier(uri) {
            object : FilterOutputStream(FileOutputStream(target)) {
                override fun close() {
                    super.close()
                    throw IOException("The upload failed")
                }
            }
        }

        assertEquals(Exported.NotWritten, exportInto(context.contentResolver, uri, export = maps(3)))
        assertFalse(target.exists())
    }

    @Test
    fun `a zip that would leave out a map's file it couldn't read goes, and says which map`() = runTest {
        file("Orbit maps.zip").createNewFile()

        val result = exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip")) { output ->
            output.write("half a zip".toByteArray())
            throw UnreadableMapFileException("trip")
        }

        assertEquals(Exported.Unreadable("trip"), result)
        assertFalse(file("Orbit maps.zip").exists())
    }

    @Test
    fun `an earlier backup is left as it was when a map's file can't be read, found before it's opened`() = runTest {
        file("Orbit maps.zip").writeBytes(earlier)
        var exported = false

        val result = exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip"), check = { throw UnreadableMapFileException("trip") }) {
            exported = true
            3
        }

        assertEquals(Exported.Unreadable("trip"), result)
        assertFalse(exported)
        assertArrayEquals(earlier, file("Orbit maps.zip").readBytes())
    }

    @Test
    fun `half a zip the app that keeps it won't delete is left empty`() = runTest {
        file("Orbit maps.zip").writeBytes(earlier)
        FolderApp.refusesDeleting = true

        val result = exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip")) { output ->
            output.write("half a zip".toByteArray())
            throw IOException("No room left on the drive")
        }

        assertEquals(Exported.NotWritten, result)
        assertEquals(0L, file("Orbit maps.zip").length())
    }

    @Test
    fun `what's said of a map whose file can't be read names it, as private maps show while they're locked`() {
        val resources = context.resources

        fun map(title: String, private: Boolean = false, cover: LockedCover? = null) =
            MapSummary("trip", MapOutline(title, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private, cover = cover), "", "")

        assertEquals("Orbit couldn't read one of the files of “Trip”, so it didn't write a zip that would be missing it.", unreadableExport(map("Trip"), concealing = true, resources))
        assertEquals("Orbit couldn't read one of the files of “📓 Mine”, so it didn't write a zip that would be missing it.", unreadableExport(map("Diary", private = true, cover = LockedCover("📓", "Mine")), concealing = false, resources))
        assertEquals("Orbit couldn't read one of the files of “Private map”, so it didn't write a zip that would be missing it.", unreadableExport(map("Diary", private = true), concealing = false, resources))
        // While private maps hide, nothing tells of one.
        assertEquals("Orbit couldn't read one of your maps' files, so it didn't write a zip that would be missing it.", unreadableExport(map("Diary", private = true), concealing = true, resources))
        assertEquals("Orbit couldn't read one of your maps' files, so it didn't write a zip that would be missing it.", unreadableExport(null, concealing = false, resources))
    }

    @Test
    fun `a private map whose file couldn't be read is named as private maps are when Settings says so`() = runTest {
        val diary = MapSummary("diary", MapOutline("Diary", branches = 0, ideas = 0), 0, MapMetadata(isPrivate = true, cover = LockedCover("📓", "Mine")), "", "")
        val lock = PrivateLock(clock = { 0L })
        val backups = MapBackups(this)
        lock.unlock()

        // Export all ends while private maps are unlocked, and what it came to waits for Settings...
        backups.start(MapBackups.Work.Exporting) { exportOutcome(Exported.Unreadable("diary"), { id -> diary.takeIf { it.id == id } }, context.resources) }
        advanceUntilIdle()
        val outcome = checkNotNull(backups.outcome.value)
        assertEquals(
            "Orbit couldn't read one of the files of “📓 Mine”, so it didn't write a zip that would be missing it.",
            outcome.said(lock.view.value.conceals(hiding = true)),
        )

        // ...which says it once they've locked, and hide: then it tells nothing of one.
        lock.lock()
        assertEquals("Orbit couldn't read one of your maps' files, so it didn't write a zip that would be missing it.", outcome.said(lock.view.value.conceals(hiding = true)))
    }

    @Test
    fun `a document Orbit isn't allowed to write is left as it was`() = runTest {
        file("Orbit maps.zip").writeBytes(earlier)
        FolderApp.beforeOpening = { _, _ -> throw SecurityException("Not yours") }

        assertEquals(Exported.NotAllowed, exportInto(context.contentResolver, FolderApp.document("Orbit maps.zip"), export = maps(3)))
        assertArrayEquals(earlier, file("Orbit maps.zip").readBytes())
    }
}
