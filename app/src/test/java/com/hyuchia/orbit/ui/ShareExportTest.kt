package com.hyuchia.orbit.ui

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.OutputFailed
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
import java.io.IOException

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

    @Test
    fun anExportIsMadeIntoAFileOfItsOwnAsItsWrittenAndNothingStaysOfOneThatCantBe() = runBlocking<Unit> {
        // Written as it's made, a piece at a time, as a web page or a zip of over 100 MB is.
        val made = makeExport(activity, "Trip.zip") { output ->
            repeat(64) { output.write(ByteArray(1024) { it.toByte() }) }
            true
        }!!

        assertEquals("Trip.zip", made.name)
        assertEquals(65_536L, made.length())

        // One that couldn't be made, or failed partway, leaves nothing behind.
        val unmade = makeExport(activity, "Trip.html") { output ->
            output.write(ByteArray(10))
            false
        }

        assertNull(unmade)

        val failed = runCatching {
            makeExport(activity, "Trip.svg") { output ->
                output.write(ByteArray(10))
                throw IOException("A picture went")
            }
        }

        assertEquals("A picture went", failed.exceptionOrNull()?.message)
        assertFalse(failed.exceptionOrNull() is OutputFailed)

        // The cache failing to take it, as a full storage does, is told apart from the map's files failing.
        val full = runCatching {
            makeExport(activity, "Trip.png") { output ->
                // A write too big to wait in a buffer, to a file that can't take it.
                output.close()
                output.write(ByteArray(64 * 1024))
                true
            }
        }

        assertTrue(full.exceptionOrNull().toString(), full.exceptionOrNull() is OutputFailed)
        assertEquals(listOf(made), exports())

        // Shared once it's made (as aFileThatStillGoesOpensTheShareSheet's is), unless the map locked meanwhile: then it goes.
        assertFalse(shareMade(activity, made, "application/zip", "Trip", goes = { false }))
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(emptyList<File>(), exports())
    }
}
