package com.hyuchia.orbit.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ListeningTest {
    @Test
    fun eachSentenceSaidIsAnIdea() {
        assertEquals(
            listOf("Coffee with the team", "A logo for the shop?", "Ship it!"),
            ideasFrom("Coffee with the team. A logo for the shop? Ship it!", Locale.ENGLISH),
        )
    }

    @Test
    fun withoutPunctuationAPauseIsOneIdea() {
        assertEquals(listOf("Ask the bakery about weekend hours"), ideasFrom("ask the bakery about weekend hours", Locale.ENGLISH))
    }

    @Test
    fun ideasStartWithACapitalAndLoseTheirFullStop() {
        assertEquals(listOf("Évora trip", "Buy tickets"), ideasFrom("évora trip.  buy tickets...", Locale.forLanguageTag("pt")))
    }

    @Test
    fun sentencesEndTheWaySomeLanguagesEndThem() {
        assertEquals(listOf("買い物に行く", "明日は？"), ideasFrom("買い物に行く。明日は？", Locale.JAPANESE))
    }

    @Test
    fun silenceIsNoIdea() {
        assertEquals(emptyList<String>(), ideasFrom("  ", Locale.ENGLISH))
        assertEquals(emptyList<String>(), ideasFrom(" . ", Locale.ENGLISH))
    }
}
