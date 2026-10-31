package com.hyuchia.orbit.capture

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.PrivateView
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureChoicesTest {
    private fun map(id: String, opened: Long, pinned: Boolean = false, private: Boolean = false) =
        MapSummary(id, MapOutline(id, branches = 0, ideas = 0), 0, MapMetadata(opened = opened, pinned = pinned, isPrivate = private), "", "")

    private val groceries = map("groceries", opened = 1)
    private val trip = map("trip", opened = 2, pinned = true)
    private val journal = map("journal", opened = 3, private = true)

    @Test
    fun `locked private maps aren't offered, and Unlock in the list brings them in when there are some`() {
        val locked = captureChoices(listOf(groceries, trip, journal), PrivateView(locked = true), hiding = false)
        assertEquals(listOf("trip", "groceries"), locked.maps.map { it.id })
        assertEquals(true, locked.unlockInList)
        assertEquals(false, locked.showButton)

        // No private map: nothing to unlock.
        assertEquals(false, captureChoices(listOf(groceries, trip), PrivateView(locked = true), hiding = false).unlockInList)
    }

    @Test
    fun `while they hide, nothing says there are private maps, and the open lock is there whether there are or not`() {
        val hidden = captureChoices(listOf(groceries, trip, journal), PrivateView(locked = true), hiding = true)
        assertEquals(listOf("trip", "groceries"), hidden.maps.map { it.id })
        assertEquals(false, hidden.unlockInList)
        assertEquals(true, hidden.showButton)

        assertEquals(CaptureChoices(listOf(trip, groceries), unlockInList = false, showButton = true), captureChoices(listOf(groceries, trip), PrivateView(locked = true), hiding = true))
        // Every map private: none to offer, and the open lock still there.
        assertEquals(CaptureChoices(emptyList(), unlockInList = false, showButton = true), captureChoices(listOf(journal), PrivateView(locked = true), hiding = true))
    }

    @Test
    fun `unlocked, every map is offered and nothing more`() {
        for (hiding in listOf(false, true)) {
            val open = captureChoices(listOf(groceries, trip, journal), PrivateView(locked = false), hiding)
            assertEquals(listOf("trip", "journal", "groceries"), open.maps.map { it.id })
            assertEquals(false, open.unlockInList)
            assertEquals(false, open.showButton)
        }
    }

    @Test
    fun `the list names private maps only while they're unlocked, which keeps quick capture out of Recents`() {
        assertEquals(false, captureChoices(listOf(groceries, journal), PrivateView(locked = true), hiding = false).namesPrivateMaps)
        assertEquals(false, captureChoices(listOf(groceries, journal), PrivateView(locked = true), hiding = true).namesPrivateMaps)
        assertEquals(true, captureChoices(listOf(groceries, journal), PrivateView(locked = false), hiding = false).namesPrivateMaps)
        assertEquals(false, captureChoices(listOf(groceries, trip), PrivateView(locked = false), hiding = false).namesPrivateMaps)
    }
}
