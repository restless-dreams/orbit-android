package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.data.LockedCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedCoversTest {
    @Test
    fun `a locked map is named by its cover, its emoji first, or as a private map`() {
        assertEquals("📓 Journal", coverTitle(LockedCover(icon = "📓", name = "Journal"), "Private map"))
        assertEquals("Journal", coverTitle(LockedCover(name = "Journal"), "Private map"))
        assertEquals("📓 Private map", coverTitle(LockedCover(icon = "📓"), "Private map"))
        assertEquals("Private map", coverTitle(LockedCover(icon = " ", name = ""), "Private map"))
        assertEquals("Private map", coverTitle(null, "Private map"))
    }

    @Test
    fun `a locked map's look comes from its id, the same every time`() {
        assertEquals(lockedLook("journal"), lockedLook("journal"))

        val looks = (1..200).map { lockedLook("map-$it") }
        assertTrue(looks.all { it.hue in LockedHues.indices && it.planets in 3..7 && it.outer in 1..4 && it.turn in 0f..1f })
        // Maps tell apart: every color and every count of planets comes up, and ids alike in their letters differ.
        assertEquals(LockedHues.indices.toSet(), looks.map { it.hue }.toSet())
        assertEquals((3..7).toSet(), looks.map { it.planets }.toSet())
        assertTrue(looks.zipWithNext().count { (one, next) -> one == next } < 5)
    }
}
