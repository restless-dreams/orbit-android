package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.FileNotFoundException

/** What's pasted when the app whose clip it is fails to say what it holds. */
@RunWith(AndroidJUnit4::class)
class MapClipboardTest {
    /** Another app's provider, which breaks, or refuses, when asked for its file's type, as `failure` says. */
    class BrokenProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

        override fun getType(uri: Uri): String = throw failure

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = throw FileNotFoundException(uri.toString())

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            var failure: RuntimeException = IllegalStateException("The provider broke")
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val picture = Uri.parse("content://$AUTHORITY/picture")

    @Before
    fun copied() {
        Robolectric.setupContentProvider(BrokenProvider::class.java, AUTHORITY)
        // The clip says it's a JPEG, as the app that copied it says.
        val clip = ClipData(ClipDescription("picture", arrayOf("image/jpeg")), ClipData.Item(picture))
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    @Test
    fun `a clip whose app breaks as it's asked its type pastes as the clip says, rather than closing Orbit`() {
        BrokenProvider.failure = IllegalStateException("The provider broke")
        val clipboard = MapClipboard(context)

        assertEquals(Pasteable.Picture, clipboard.pasteable())
        assertEquals(Clip.Picture(picture), clipboard.read())
    }

    @Test
    fun `a clip whose app refuses to say its type pastes as the clip says too`() {
        BrokenProvider.failure = SecurityException("Not yours")

        assertEquals(Clip.Picture(picture), MapClipboard(context).read())
    }

    private companion object {
        const val AUTHORITY = "com.example.broken"
    }
}
