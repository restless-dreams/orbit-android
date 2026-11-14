package com.hyuchia.orbit.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.ResourceWords
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.ImportedMaps
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.OtherFormat
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.page.PagePool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Settings' Import maps: maps from other apps (OPML, FreeMind, XMind) come
 * in as Open a file brings them, anything else as a zip of every map; one
 * that stopped partway says what stopped it, exactly what came, and that
 * the same file brings the rest; and Export all names a map as it is when
 * that's said.
 */
@RunWith(AndroidJUnit4::class)
class ImportIntoTest {
    /** Files the file picker hands Settings, by name. */
    class PickedFiles : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val bytes = FILES[uri.lastPathSegment] ?: return null
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(uri.lastPathSegment!!, bytes.size.toLong())) }
        }

        // As a file manager types a file it doesn't know.
        override fun getType(uri: Uri): String = "application/octet-stream"

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val bytes = FILES[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())

            val copy = File.createTempFile("picked", ".bin").apply {
                deleteOnExit()
                writeBytes(bytes)
            }

            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = ResourceWords(context)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private lateinit var importer: Importer

    private fun address(name: String): Uri = Uri.parse("content://$AUTHORITY/files/$name")

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    @Before
    fun start() = runBlocking {
        Robolectric.setupContentProvider(PickedFiles::class.java, AUTHORITY)
        FILES.clear()
        FILES["Trip.opml"] = """<opml version="2.0"><body><outline text="Trip"><outline text="Flights"/></outline></body></opml>""".toByteArray()
        FILES["Plan.mm"] = """<map version="1.0.1"><node TEXT="Plan"><node TEXT="Specs"/></node></map>""".toByteArray()
        FILES["Garden.xmind"] = zip(
            "content.json" to """[{"id":"one","rootTopic":{"id":"root","title":"Garden"}},{"id":"two","rootTopic":{"id":"root-2","title":"Shopping"}}]""".toByteArray(),
        )
        FILES["All maps.zip"] = zip("trip/map.md" to "# Trip\n".toByteArray(), "plan/map.md" to "# Plan\n".toByteArray())
        FILES["Trip.md"] = "# Trip\n- Flights\n".toByteArray()
        val maps = MapRepository(folder.newFolder("maps"), folder.newFolder("trash"), starters, FileWords(Locale.US))
        maps.load()
        importer = Importer(maps, PagePool(context, folder.root), words)
    }

    @Test
    fun `OPML, FreeMind and XMind files are other apps' maps, and a zip of maps or a markdown file isn't`() = runBlocking {
        val resolver = context.contentResolver

        assertEquals("Trip.opml" to OtherFormat.Opml, importer.otherAppFile(address("Trip.opml"), resolver))
        assertEquals("Plan.mm" to OtherFormat.FreeMind, importer.otherAppFile(address("Plan.mm"), resolver))
        assertEquals("Garden.xmind" to OtherFormat.XMind, importer.otherAppFile(address("Garden.xmind"), resolver))
        assertNull(importer.otherAppFile(address("All maps.zip"), resolver))
        assertNull(importer.otherAppFile(address("Trip.md"), resolver))
    }

    @Test
    fun `Import maps makes maps of other apps' files, as Open a file does, and reads anything else as a zip of maps`() = runBlocking {
        val resolver = context.contentResolver

        for (name in listOf("Trip.opml", "Plan.mm", "Garden.xmind")) {
            var zipRead = false
            val converted = mutableListOf<OtherFormat>()

            // The conversion itself runs on a page, as Open a file's does: here it's only told what to convert.
            val outcome = importInto(
                resolver,
                address(name),
                words,
                context.resources,
                fromOtherApp = {
                    importer.otherAppFile(address(name), resolver)?.let { (_, format) ->
                        converted += format
                        ImportResult.ImportedMany(added = 2, alreadyHere = 0, notes = listOf("Note."))
                    }
                },
                importAll = {
                    zipRead = true
                    ImportedMaps(added = 0, alreadyHere = 0)
                },
            )

            assertEquals(1, converted.size)
            assertFalse(zipRead)
            assertFalse(outcome.problem)
            assertEquals("Added 2 maps. Note.", outcome.said(concealing = false))
        }

        var read = 0

        val outcome = importInto(
            resolver,
            address("All maps.zip"),
            words,
            context.resources,
            fromOtherApp = { importer.otherAppFile(address("All maps.zip"), resolver)?.let { ImportResult.Failed("Converted a zip") } },
            importAll = { input ->
                read = input.readBytes().size
                ImportedMaps(added = 2, alreadyHere = 0)
            },
        )

        assertEquals(FILES.getValue("All maps.zip").size, read)
        assertEquals("Added 2 maps.", outcome.said(concealing = false))
    }

    @Test
    fun `an import that stopped partway says why, exactly what came, and that the same file brings the rest`() {
        val stopped = importOutcome(
            ImportedMaps(added = 1, alreadyHere = 1, privateAlreadyHere = 1, filledIn = 2, privateFilledIn = 2),
            words,
            context.resources,
            stopped = IOException("No space left on device"),
        )

        assertTrue(stopped.problem)
        assertEquals(
            "Orbit had to stop partway through that file: No space left on device. Added 1 map. 1 was here already. " +
                "2 files that maps here were missing came in: pictures, videos, sounds or versions. " +
                "Importing it again brings in the rest, and leaves what came in as it is.",
            stopped.said(concealing = false),
        )
        // While private maps hide, nothing of theirs is counted.
        assertEquals(
            "Orbit had to stop partway through that file: No space left on device. Added 1 map. Importing it again brings in the rest, and leaves what came in as it is.",
            stopped.said(concealing = true),
        )

        // A reason that names a file's path isn't one for people; one stopped after files alone says nothing of the file's maps.
        val onlyFiles = importOutcome(
            ImportedMaps(added = 0, alreadyHere = 1, filledIn = 1),
            words,
            context.resources,
            stopped = FileNotFoundException("/data/user/0/history/second/.1000.md.partial (Permission denied)"),
        )

        assertEquals(
            "Orbit had to stop partway through that file. One file a map here was missing came in: a picture, video, sound or version. " +
                "Importing it again brings in the rest, and leaves what came in as it is.",
            onlyFiles.said(concealing = false),
        )
    }

    @Test
    fun `Export all names a map that couldn't be read as it is when that's said, never by a title it had before it was private`() {
        var current = MapSummary("diary", MapOutline("Secret diary", branches = 0, ideas = 0), 0, MapMetadata(), "", "")
        val outcome = exportOutcome(Exported.Unreadable("diary"), { id -> current.takeIf { it.id == id } }, context.resources)
        assertTrue(outcome.said(concealing = true).contains("Secret diary"))

        current = current.copy(metadata = MapMetadata(isPrivate = true, cover = LockedCover("📓", "Mine")))

        assertFalse(outcome.said(concealing = true).contains("Secret diary"))
        assertEquals("Orbit couldn't read one of your maps' files, so it didn't write a zip that would be missing it.", outcome.said(concealing = true))
        assertTrue(outcome.said(concealing = false).contains("Mine"))
        assertFalse(outcome.said(concealing = false).contains("Secret diary"))
    }

    private companion object {
        const val AUTHORITY = "com.example.picker.files"
        val FILES = mutableMapOf<String, ByteArray>()
    }
}
