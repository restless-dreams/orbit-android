package com.hyuchia.orbit.data

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
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.PagePool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Files another app hands Orbit through its content provider, as a file
 * manager's or a chat app's: read by their names and types, refused when the
 * app refuses them, and never from Orbit's own provider or a path.
 */
@RunWith(AndroidJUnit4::class)
class ImportProviderTest {
    /** Another app's files: each with its name, type and bytes, or a refusal. */
    class OtherAppsFiles : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val file = FILES[uri.lastPathSegment] ?: return null
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(file.name, file.bytes.size.toLong())) }
        }

        override fun getType(uri: Uri): String? = FILES[uri.lastPathSegment]?.type

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = FILES[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())

            if (file.refused) {
                throw SecurityException("Not shared with Orbit")
            }

            val copy = File.createTempFile("shared", ".bin").apply {
                deleteOnExit()
                writeBytes(file.bytes)
            }

            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    class SharedFile(val name: String, val type: String, val bytes: ByteArray, val refused: Boolean = false)

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val english = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome() = StarterMap("# Welcome\n")
    }

    private lateinit var maps: MapRepository
    private lateinit var importer: Importer

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/files/$key")

    @Before
    fun start() = runBlocking {
        Robolectric.setupContentProvider(OtherAppsFiles::class.java, AUTHORITY)
        FILES.clear()
        maps = MapRepository(folder.newFolder("maps"), folder.newFolder("trash"), starters, english)
        maps.load()
        importer = Importer(maps, PagePool(context, folder.root), english)
    }

    @Test
    fun aMarkdownFileBecomesAMapKeptAsItWasWritten() = runBlocking {
        val text = "# Trip to Lisbon\n- Belém\n  - The tower\n- Alfama\n"
        FILES["trip"] = SharedFile("Trip to Lisbon.md", "text/markdown", text.toByteArray())

        val imported = importer.importFile(address("trip"), context.contentResolver) as ImportResult.Imported
        assertEquals("Trip to Lisbon", imported.title)
        assertEquals(text, File(maps.folder(imported.id), "map.md").readText())
    }

    @Test
    fun aZipOfAMapComesInWithItsPictures() = runBlocking {
        // The zip another phone or Export and share made of a map with a picture.
        val elsewhere = MapRepository(folder.newFolder("other-maps"), folder.newFolder("other-trash"), starters, english)
        elsewhere.load()
        val picture = TestMedia.picture()
        val id = elsewhere.add("# Weekend\n- The tram\n\n  ![Tram](media/tram.jpg)\n", pictures = mapOf("tram.jpg" to picture))
        val zip = java.io.ByteArrayOutputStream().also { elsewhere.writeZip(id, it) }.toByteArray()
        FILES["weekend"] = SharedFile("Weekend.zip", "application/zip", zip)

        val imported = importer.importFile(address("weekend"), context.contentResolver) as ImportResult.Imported
        assertEquals("Weekend", imported.title)
        // The picture as it left the other phone: without what its file says beyond the picture.
        assertTrue(File(maps.folder(imported.id), "media/tram.jpg").readBytes().contentEquals(OutgoingMedia.picture(picture)))
    }

    @Test
    fun aFileTheOtherAppWontGiveIsSaidToBeSo() = runBlocking {
        FILES["secret"] = SharedFile("Secret.md", "text/markdown", "# Secret\n".toByteArray(), refused = true)

        assertEquals(
            ImportResult.Failed(english.string(R.string.common_not_allowed_to_read_that_file)),
            importer.importFile(address("secret"), context.contentResolver),
        )
        assertTrue(maps.maps.value.orEmpty().none { it.title == "Secret" })
    }

    @Test
    fun aFileThatIsntThereCantBeRead() = runBlocking {
        assertEquals(ImportResult.Failed(english.string(R.string.data_file_unreadable)), importer.importFile(address("gone"), context.contentResolver))
    }

    @Test
    fun aFileTooBigToBeAMapStaysOut() = runBlocking {
        FILES["huge"] = SharedFile("Huge.md", "text/markdown", ByteArray((MAP_TEXT_LIMIT + 1).toInt()) { 'a'.code.toByte() })

        assertEquals(ImportResult.Failed(english.string(R.string.data_file_too_big_for_map)), importer.importFile(address("huge"), context.contentResolver))
    }

    @Test
    fun orbitsOwnFilesAndPathsAreNeverReadForAnotherApp() = runBlocking {
        val refused = ImportResult.Failed(english.string(R.string.common_not_allowed_to_read_that_file))
        val ownFiles = Uri.parse("content://${BuildConfig.APPLICATION_ID}.files/maps/trip/map.md")
        val path = Uri.fromFile(File(context.filesDir, "maps/trip/map.md"))

        assertEquals(refused, importer.importFile(ownFiles, context.contentResolver))
        assertEquals(refused, importer.importFile(path, context.contentResolver))
    }

    private companion object {
        const val AUTHORITY = "com.example.files"

        /** The files the other app shares, by the last part of their address. */
        val FILES = mutableMapOf<String, SharedFile>()
    }
}
