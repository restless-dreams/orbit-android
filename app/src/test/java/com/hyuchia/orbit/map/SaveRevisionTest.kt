package com.hyuchia.orbit.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveRevisionTest {
    @Test
    fun `a save made on the text the screen has is written, and one made before a change from elsewhere is kept aside`() {
        assertFalse(isStale(revision = 3, generation = 3))
        assertTrue(isStale(revision = 2, generation = 3))
        // A page from before revisions says none: its saves go as ever.
        assertFalse(isStale(revision = null, generation = 3))
    }
}
