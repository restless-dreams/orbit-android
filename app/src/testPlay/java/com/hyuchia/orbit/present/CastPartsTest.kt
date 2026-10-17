package com.hyuchia.orbit.present

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPartsTest {
    @Test
    fun bigCommandsGoInPiecesThatFitInACastMessage() {
        val text = "x".repeat(CAST_PART_LENGTH * 2 + 345)
        val parts = castParts(text)
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.length <= CAST_PART_LENGTH })
        assertEquals(text, parts.joinToString(""))
    }

    @Test
    fun piecesNeverSplitAnEmojiInHalf() {
        // The emoji's two halves straddle the first cut.
        val text = "a".repeat(CAST_PART_LENGTH - 1) + "🪐" + "b".repeat(20)
        val parts = castParts(text)
        assertEquals(CAST_PART_LENGTH - 1, parts[0].length)
        assertTrue(parts[1].startsWith("🪐"))
        assertEquals(text, parts.joinToString(""))
    }
}
