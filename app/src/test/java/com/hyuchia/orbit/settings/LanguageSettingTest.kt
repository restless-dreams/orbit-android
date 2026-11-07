package com.hyuchia.orbit.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSettingTest {
    @Test
    fun `the languages show in the order of their names in themselves, whatever order Android gives them in`() {
        // Android's locale config gives them in an order of its own: Portuguese came before Spanish once it arrived.
        assertEquals(listOf("en", "es", "pt"), inNameOrder(listOf("en", "pt", "es")))
        assertEquals(listOf("en", "es", "pt"), inNameOrder(listOf("pt", "es", "en", "pt")))
    }

    @Test
    fun `each language is named in itself`() {
        assertEquals(listOf("English", "Español", "Português"), listOf("en", "es", "pt").map(::languageName))
    }
}
