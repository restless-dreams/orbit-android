package com.hyuchia.orbit.ui

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File

/** A file a way out of a map writes, and the share sheet it opens, or doesn't, as the map is right before it would. */
@RunWith(AndroidJUnit4::class)
class ShareExportTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun exports(): List<File> = File(activity.cacheDir, "exports").walkTopDown().filter { it.isFile }.toList()

    @Test
    fun aFileMadeForAMapThatLockedMeanwhileOpensNothingAndGoes() = runBlocking<Unit> {
        var asked = false

        // The map locked while its file was written.
        val goes = {
            asked = true
            false
        }

        val opened = shareExport(activity, "Journal.zip", "application/zip", "Journal", goes) { output ->
            // Asked only once the file is whole, right before the sheet would open.
            assertFalse(asked)
            output.write(ByteArray(64))
        }

        assertFalse(opened)
        assertTrue(asked)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(emptyList<File>(), exports())
    }

    @Test
    fun aFileThatStillGoesOpensTheShareSheet() = runBlocking<Unit> {
        assertTrue(shareExport(activity, "Trip.zip", "application/zip", "Trip", goes = { true }) { it.write(ByteArray(64)) })
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CHOOSER, started.intent.action)
        assertEquals(listOf("Trip.zip"), exports().map { it.name })
    }
}
