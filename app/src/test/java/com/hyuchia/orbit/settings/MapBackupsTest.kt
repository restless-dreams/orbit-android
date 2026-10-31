package com.hyuchia.orbit.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBackupsTest {
    /** The app's scope as the container makes it: a failure is logged, not the end of other work. */
    private fun TestScope.appScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, _ -> })

    @Test
    fun `one backup runs at a time, and a second tap leaves it be`() = runTest {
        val scope = appScope()
        val backups = MapBackups(scope)
        val writing = CompletableDeferred<Unit>()
        val exported = MapBackups.Outcome("Exported 5 maps.", problem = false)

        assertTrue(backups.start(MapBackups.Work.Exporting) { writing.await().let { exported } })
        assertFalse(backups.start(MapBackups.Work.Importing) { MapBackups.Outcome("Added 2 maps.", problem = false) })
        advanceUntilIdle()
        assertEquals(MapBackups.Work.Exporting, backups.work.value)

        writing.complete(Unit)
        advanceUntilIdle()
        assertNull(backups.work.value)
        assertSame(exported, backups.outcome.value)
        scope.cancel()
    }

    @Test
    fun `what a backup came to waits until it's said, and a newer one isn't lost`() = runTest {
        val scope = appScope()
        val backups = MapBackups(scope)
        val first = MapBackups.Outcome("Exported 5 maps.", problem = false)
        val second = MapBackups.Outcome("Exported 5 maps.", problem = false)

        backups.start(MapBackups.Work.Exporting) { first }
        advanceUntilIdle()
        backups.start(MapBackups.Work.Exporting) { second }
        advanceUntilIdle()

        // The same words twice are two outcomes: saying the first doesn't take the second.
        backups.said(first)
        assertSame(second, backups.outcome.value)
        backups.said(second)
        assertNull(backups.outcome.value)
        scope.cancel()
    }

    @Test
    fun `a backup that fails outright frees the way for the next`() = runTest {
        val scope = appScope()
        val backups = MapBackups(scope)

        backups.start(MapBackups.Work.Importing) { throw IllegalStateException("the folder's app crashed") }
        advanceUntilIdle()
        assertNull(backups.work.value)
        assertTrue(backups.start(MapBackups.Work.Importing) { MapBackups.Outcome("Added 2 maps.", problem = false) })
        scope.cancel()
    }
}
