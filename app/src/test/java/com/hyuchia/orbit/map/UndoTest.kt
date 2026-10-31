package com.hyuchia.orbit.map

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoTest {
    @Test
    fun undoGoesBackAHundredSteps() {
        val versions = ArrayDeque((1..101).map { "# Plan $it" })
        trimUndo(versions)
        assertEquals(100, versions.size)
        assertEquals("# Plan 2", versions.first())
    }

    @Test
    fun aBigMapGoesBackFewerSteps() {
        val versions = ArrayDeque((1..5).map { "$it".repeat(400) })
        trimUndo(versions, characters = 1_000)
        assertEquals(listOf("4".repeat(400), "5".repeat(400)), versions.toList())
    }

    @Test
    fun aMapBiggerThanAllUndoKeepsStillGoesBackOneStep() {
        val versions = ArrayDeque(listOf("# Plan", "#".repeat(2_000)))
        trimUndo(versions, characters = 1_000)
        assertEquals(listOf("#".repeat(2_000)), versions.toList())
    }
}
