package com.hyuchia.orbit

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What's shared to a map that's open waits for its screen, however soon it
 * came: shared before the screen began to take shares, as Android brings
 * Orbit back with a share, it was lost.
 */
class PendingSharesTest {
    @Test
    fun `a share made before any screen takes shares is taken later, by its map, once`() {
        val shares = PendingShares()
        shares.offer(SharedFiles("lisbon", emptyList(), text = "Fado houses"))
        shares.offer(SharedFiles("porto", emptyList(), text = "Livraria Lello"))

        assertEquals(emptyList<SharedFiles>(), shares.take("madrid"))
        assertEquals(listOf("Fado houses"), shares.take("lisbon").map { it.text })
        assertEquals(emptyList<SharedFiles>(), shares.take("lisbon"))
        assertEquals(listOf("porto"), shares.waiting.value.map { it.mapId })
    }

    @Test
    fun `every offer is told, even one like a share taken a moment before`() {
        val shares = PendingShares()
        val share = SharedFiles("lisbon", emptyList(), text = "Fado houses")

        shares.offer(share)
        val first = shares.offers.value
        shares.take("lisbon")
        shares.offer(share)

        // The list is as it was when the first was offered, but the screen hears this one all the same, and takes it.
        assertEquals(listOf(share), shares.waiting.value)
        assertEquals(first + 1, shares.offers.value)
        assertEquals(listOf(share), shares.take("lisbon"))
    }

    @Test
    fun `shares go with their map as it closes, and stay while it's open`() {
        val shares = PendingShares()
        shares.offer(SharedFiles("lisbon", emptyList(), text = "Fado houses"))
        shares.offer(SharedFiles("porto", emptyList(), text = "Livraria Lello"))

        shares.keepFor(setOf("porto"))

        assertEquals(listOf("porto"), shares.waiting.value.map { it.mapId })
    }
}
