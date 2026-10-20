package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    @Test
    fun everyLetterStaysOneLetter() {
        for (text in listOf("İstanbul", "İŞ PLANI", "Straße", "STRAẞE", "ΟΔΟΣ", "Kadıköy", "Ǆungla", "𐐀𐐁 Deseret", "🙂 Lisbon 🚋", "éclair")) {
            assertEquals(text, text.length, foldCase(text).length)
        }
    }

    @Test
    fun lettersFindTheirOtherCase() {
        assertEquals("istanbul", foldCase("İstanbul"))
        assertEquals("iş plani", foldCase("İŞ PLANI"))
        assertEquals("οδοσ", foldCase("ΟΔΟΣ"))
        assertEquals(foldCase("οδος"), foldCase("ΟΔΟΣ"))
        assertEquals("straße", foldCase("STRAẞE"))
        assertEquals(foldCase("kadiköy"), foldCase("Kadıköy"))
        assertEquals("𐐨𐐩 deseret", foldCase("𐐀𐐁 Deseret"))
        assertEquals("🙂 lisbon 🚋", foldCase("🙂 Lisbon 🚋"))
    }

    @Test
    fun aSearchIsItsWordsFolded() {
        assertEquals(listOf("istanbul", "kadiköy"), searchWords("  İstanbul\tKADIKÖY \n"))
        assertTrue(searchWords(" \n ").isEmpty())
    }
}
