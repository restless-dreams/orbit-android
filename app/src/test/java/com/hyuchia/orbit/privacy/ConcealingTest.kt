package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ConcealingTest {
    private fun map(id: String, private: Boolean) = MapSummary(id, MapOutline(id, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    @Test
    fun `private maps hide altogether only while Settings hides them and they're locked`() {
        assertEquals(true, PrivateView(locked = true).conceals(hiding = true))
        assertEquals(false, PrivateView(locked = true).conceals(hiding = false))
        assertEquals(false, PrivateView(locked = false).conceals(hiding = true))
        // Without a screen lock nothing locks, so nothing hides.
        assertEquals(false, PrivateView(locked = false, lockable = false).conceals(hiding = true))
    }

    @Test
    fun `maps already here are counted without the private ones while they hide`() {
        assertEquals(1, mentionedAlreadyHere(alreadyHere = 3, privateAlreadyHere = 2, concealing = true))
        assertEquals(3, mentionedAlreadyHere(alreadyHere = 3, privateAlreadyHere = 2, concealing = false))
        assertEquals(0, mentionedAlreadyHere(alreadyHere = 2, privateAlreadyHere = 2, concealing = true))
    }

    @Test
    fun `files brought into maps already here are counted without the private ones' while they hide`() {
        assertEquals(2, mentionedFilledIn(filledIn = 5, privateFilledIn = 3, concealing = true))
        assertEquals(5, mentionedFilledIn(filledIn = 5, privateFilledIn = 3, concealing = false))
    }

    @Test
    fun `taking every map out asks for the screen lock while a private map is locked, and always while they hide`() {
        val plain = map("plain", private = false)
        val secret = map("secret", private = true)

        assertEquals(true, PrivateView(locked = true).asksBeforeEveryMap(listOf(plain, secret), hiding = false))
        assertEquals(false, PrivateView(locked = true).asksBeforeEveryMap(listOf(plain), hiding = false))
        assertEquals(false, PrivateView(locked = false).asksBeforeEveryMap(listOf(plain, secret), hiding = false))
        // Hidden, it asks whether any private map is there or not, so asking gives nothing away.
        assertEquals(true, PrivateView(locked = true).asksBeforeEveryMap(listOf(plain), hiding = true))
        assertEquals(true, PrivateView(locked = true).asksBeforeEveryMap(emptyList(), hiding = true))
        // One made private since they locked is open until the next lock.
        assertEquals(false, PrivateView(locked = true, keptOpen = setOf("secret")).asksBeforeEveryMap(listOf(plain, secret), hiding = false))
        // Without a screen lock nothing locks, so there's nothing to ask.
        assertEquals(false, PrivateView(locked = false, lockable = false).asksBeforeEveryMap(listOf(secret), hiding = true))
    }
}
