package com.hyuchia.orbit.study

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyKeysTest {
    private val card = Study(start = "photosynthesis", queue = listOf("light", "calvin"), total = 2)
    private val revealed = card.copy(revealed = true)
    private val summary = card.copy(queue = emptyList(), ending = StudyEnding.Finished)
    private val pressing = listOf(Key.Spacebar, Key.Enter, Key.NumPadEnter)

    private fun key(key: Key, study: Study, buttonFocused: Boolean = false, ctrl: Boolean = false, rightToLeft: Boolean = false) = studyKey(key, ctrl, study, rightToLeft, buttonFocused)

    @Test
    fun `space and enter reveal a card, say it was known, and close the summary`() {
        for (press in pressing) {
            assertEquals(StudyKey.Reveal, key(press, card))
            assertEquals(StudyKey.Knew, key(press, revealed))
            assertEquals(StudyKey.Stop, key(press, summary))
        }
    }

    @Test
    fun `with a button focused, space and enter press that button in every state`() {
        for (press in pressing) {
            for (study in listOf(card, revealed, summary)) {
                assertNull(key(press, study, buttonFocused = true))
            }
        }
    }

    @Test
    fun `escape, the answers and undo stay the session's while a button has focus`() {
        for (buttonFocused in listOf(false, true)) {
            assertEquals(StudyKey.Stop, key(Key.Escape, card, buttonFocused))
            assertEquals(StudyKey.Stop, key(Key.Escape, summary, buttonFocused))
            assertEquals(StudyKey.Again, key(Key.One, revealed, buttonFocused))
            assertEquals(StudyKey.Again, key(Key.DirectionLeft, revealed, buttonFocused))
            assertEquals(StudyKey.Knew, key(Key.Two, revealed, buttonFocused))
            assertEquals(StudyKey.Knew, key(Key.DirectionRight, revealed, buttonFocused))
            assertEquals(StudyKey.Undo, key(Key.Z, revealed, buttonFocused, ctrl = true))
        }
    }

    @Test
    fun `answers wait for the card to be revealed, and the arrows follow the reading direction`() {
        assertNull(key(Key.One, card))
        assertNull(key(Key.DirectionRight, card))
        assertEquals(StudyKey.Knew, key(Key.DirectionLeft, revealed, rightToLeft = true))
        assertEquals(StudyKey.Again, key(Key.DirectionRight, revealed, rightToLeft = true))
    }

    @Test
    fun `other keys are left alone`() {
        assertNull(key(Key.Tab, card))
        assertNull(key(Key.A, revealed))
        assertNull(key(Key.One, summary))
        assertNull(key(Key.C, revealed, ctrl = true))
    }
}
