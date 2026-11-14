package com.hyuchia.orbit.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The map's messages, one at a time, and taken away as a private map locks. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapMessagesTest {
    @Test
    fun `as a private map locks, its message goes without its Undo, and so do those waiting`() = runTest {
        val host = SnackbarHostState()
        val messages = MapMessages(host, backgroundScope)
        val heard = mutableListOf<SnackbarResult>()

        messages.show("Deleted Monday", "Undo") { heard += it }
        runCurrent()
        assertEquals("Deleted Monday", host.currentSnackbarData?.visuals?.message)

        // Another comes as the map locks, before it shows: neither is left to show once the map is unlocked.
        messages.show("Kept in the history", "Version history") { heard += it }
        messages.clear()
        runCurrent()

        assertNull(host.currentSnackbarData)
        assertEquals(emptyList<SnackbarResult>(), heard)
    }

    @Test
    fun `each message takes the place of the one showing, and says how it went`() = runTest {
        val host = SnackbarHostState()
        val messages = MapMessages(host, backgroundScope)
        val heard = mutableListOf<Pair<String, SnackbarResult>>()

        messages.show("Copied Monday") { heard += "copied" to it }
        messages.show("Deleted Tuesday", "Undo") { heard += "deleted" to it }
        runCurrent()
        assertEquals("Deleted Tuesday", host.currentSnackbarData?.visuals?.message)

        host.currentSnackbarData?.performAction()
        runCurrent()

        assertEquals(listOf("copied" to SnackbarResult.Dismissed, "deleted" to SnackbarResult.ActionPerformed), heard)
        assertNull(host.currentSnackbarData)
    }
}
