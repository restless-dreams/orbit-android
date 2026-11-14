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
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.PagePool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.util.Locale

/**
 * Files shared to Orbit while no map is open make a map holding them: it's
 * there only when they're on its idea, or it says why they aren't, never a
 * map that came in without them as though they were.
 */
@RunWith(AndroidJUnit4::class)
class ImporterAttachTest {
    /** A file shared to Orbit; `copying` happens to the new map as it's copied in. */
    class SharedFiles : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>("note.pdf", 4L)) }

        override fun getType(uri: Uri): String = "application/pdf"

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            maps.maps.value?.singleOrNull()?.let { made ->
                copying?.invoke(made.id)
                copying = null
            }

            val copy = File.createTempFile("shared-note", ".pdf").apply {
                deleteOnExit()
                writeBytes(byteArrayOf(1, 2, 3, 4))
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
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples(): List<String> = emptyList()

        override fun welcome(): StarterMap? = null
    }

    private lateinit var importer: Importer
    private val shared = Uri.parse("content://$AUTHORITY/note.pdf")

    @Before
    fun start() = runBlocking {
        maps = MapRepository(folder.newFolder("maps"), folder.newFolder("trash"), starters, words)
        maps.load()
        copying = null
        Robolectric.setupContentProvider(SharedFiles::class.java, AUTHORITY)
        importer = Importer(maps, PagePool(context, folder.root), words)
    }

    @After
    fun cleanUp() {
        folder.root.walkTopDown().forEach {
            it.setReadable(true)
            it.setWritable(true)
        }
    }

    @Test
    fun `a map whose files can't be put on its idea says so, rather than come in as though they were`() = runBlocking {
        var mapFolder: File? = null

        copying = { id ->
            mapFolder = maps.folder(id).also { File(it, MEDIA_DIRECTORY).mkdirs() }
            mapFolder!!.setWritable(false)
        }

        val result = importer.newMapWithFiles(listOf(shared), context.contentResolver)
        assumeFalse("Folders can't be made read-only here", mapFolder!!.canWrite())

        // The map can't go to the trash while its folder can't be written either: it's here, and says why its file isn't on it.
        val imported = result as ImportResult.Imported
        assertEquals(listOf(maps.whyNotWritten()), imported.notes)
        assertFalse(maps.read(imported.id).contains("media/"))
        assertEquals(listOf(imported.id), maps.maps.value!!.map { it.id })
    }

    @Test
    fun `a map whose text can't be read once its files copied goes to the trash, and the import says it failed`() = runBlocking {
        var text: File? = null

        copying = { id ->
            text = File(maps.folder(id), MAP_FILE)
            text!!.setReadable(false)
        }

        val result = importer.newMapWithFiles(listOf(shared), context.contentResolver)
        assumeFalse("Files can't be made unreadable here", text!!.canRead())

        assertEquals(ImportResult.Failed(maps.whyNotChanged()), result)
        assertEquals(emptyList<MapSummary>(), maps.maps.value)
        assertEquals(1, maps.trash.value.size)
    }

    @Test
    fun `an edit made while the files copy is kept, and the files are on the map`() = runBlocking {
        copying = { id ->
            runBlocking {
                val markdown = maps.read(id)
                assertEquals(WriteResult.Written, maps.write(id, "$markdown- Edited\n", base = markdown))
            }
        }

        val imported = importer.newMapWithFiles(listOf(shared), context.contentResolver) as ImportResult.Imported

        val markdown = maps.read(imported.id)
        assertTrue(markdown.contains("- Edited"))
        assertTrue(markdown.contains("media/"))
        assertEquals(emptyList<String>(), imported.notes)
    }

    private companion object {
        const val AUTHORITY = "com.example.shared.files"
        lateinit var maps: MapRepository
        var copying: ((String) -> Unit)? = null
    }
}
