package com.hyuchia.orbit.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.ResourceWords
import com.hyuchia.orbit.data.ImportedMaps
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.privacy.conceals
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Import maps says, which Settings can say long after the import
 * began: as private maps are when it's said. Once they've locked and hide,
 * the maps here already, and the files that came into them, count none of
 * the private ones, however they were as the import began; and once
 * they're unlocked again, they count them all.
 */
@RunWith(AndroidJUnit4::class)
class ImportOutcomeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Two maps added, and two here already, one of them private, with files that came into both. */
    private val imported = ImportedMaps(added = 2, alreadyHere = 2, privateAlreadyHere = 1, filledIn = 3, privateFilledIn = 2)

    private val everything =
        "Added 2 maps. 2 were here already. 3 files that maps here were missing came in: pictures, videos, sounds or versions."
    private val nothingPrivate =
        "Added 2 maps. 1 was here already. One file a map here was missing came in: a picture, video, sound or version."

    @Test
    fun `an import that began while private maps showed counts none of them once they hide`() = runTest {
        val lock = PrivateLock(clock = { 0L })
        val backups = MapBackups(this)
        lock.unlock()

        // The import ends while private maps are unlocked, and what it came to waits for Settings...
        backups.start(MapBackups.Work.Importing) { importOutcome(imported, ResourceWords(context), context.resources) }
        advanceUntilIdle()
        val outcome = checkNotNull(backups.outcome.value)
        assertFalse(outcome.problem)
        assertEquals(everything, outcome.said(lock.view.value.conceals(hiding = true)))

        // ...which says it once they've locked, and hide.
        lock.lock()
        assertEquals(nothingPrivate, outcome.said(lock.view.value.conceals(hiding = true)))
        // Locked, but shown as locked, they still count.
        assertEquals(everything, outcome.said(lock.view.value.conceals(hiding = false)))
    }

    @Test
    fun `an import that began while they hid counts them all once they're unlocked`() = runTest {
        val lock = PrivateLock(clock = { 0L })
        val outcome = importOutcome(imported, ResourceWords(context), context.resources)
        assertEquals(nothingPrivate, outcome.said(lock.view.value.conceals(hiding = true)))

        lock.unlock()
        assertEquals(everything, outcome.said(lock.view.value.conceals(hiding = true)))
    }

    @Test
    fun `a zip with no maps is a problem, worded the same either way`() {
        val outcome = importOutcome(ImportedMaps(added = 0, alreadyHere = 0), ResourceWords(context), context.resources)
        assertTrue(outcome.problem)
        assertEquals("There are no maps in that file.", outcome.said(concealing = true))
        assertEquals("There are no maps in that file.", outcome.said(concealing = false))
    }
}
