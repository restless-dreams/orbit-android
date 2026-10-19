package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiSearchTest {
    private fun asset(name: String): String = File("src/main/assets/emoji/$name.txt").readText()

    private val english = EmojiIndex.read(asset("emoji"), asset("en"), english = null, drawable = { true })
    private val spanish = EmojiIndex.read(asset("emoji"), asset("es"), english = asset("en"), drawable = { true })

    private fun EmojiIndex.first(query: String): String? = search(query).firstOrNull()?.emoji

    private fun EmojiIndex.found(query: String): List<String> = search(query).map { it.emoji }

    @Test
    fun findsEmojiByTheirNames() {
        assertEquals("🚀", english.first("rocket"))
        assertEquals("🔥", english.first("fire"))
        assertEquals("💡", english.first("light bulb"))
        assertEquals("👍", english.first("thumbs up"))
        assertEquals("❤️", english.first("red heart"))
    }

    @Test
    fun theEmojiPeopleUseMostComeFirst() {
        // "heart with arrow" comes before "red heart" in Unicode's order, but red hearts are sent far more.
        assertEquals("❤️", english.first("heart"))
        assertEquals("😂", english.first("joy"))
        assertEquals("❤️", spanish.first("corazon"))
    }

    @Test
    fun aNameFoundFromItsStartComesBeforeKeywords() {
        assertEquals(listOf("🪨", "🚀"), english.found("rock").take(2))
        assertEquals("🚀", english.first("rocke"))
        assertEquals("👍", english.first("thumbsup"))
        assertEquals("👍", english.first("THUMBS"))
    }

    @Test
    fun findsEmojiByTheirKeywords() {
        assertTrue("🚀" in english.found("launch"))
        assertTrue("🔥" in english.found("flame"))
        assertTrue("💡" in english.found("idea"))
    }

    @Test
    fun everyWordTypedHasToMatch() {
        assertEquals(listOf("❤️"), english.found("red heart").take(1))
        assertTrue(english.found("heart rocket").isEmpty())
        assertTrue(english.found("qwertyuiop").isEmpty())
    }

    @Test
    fun spanishIgnoresAccentsAndCase() {
        assertEquals("🚀", spanish.first("cohete"))
        assertEquals("❤️", spanish.first("corazon rojo"))
        assertEquals("❤️", spanish.first("Corazón rojo"))
        assertEquals("🇲🇽", spanish.first("mexico"))
        assertTrue("💡" in spanish.found("foco"))
    }

    @Test
    fun englishFindsWhatSpanishDoesntAfterIt() {
        assertEquals("🚀", spanish.first("rocket"))
        // The Spanish name comes first: "sol" is the sun, before any English word starting with "sol".
        assertEquals("☀️", spanish.first("sol"))
        assertEquals("cohete", spanish.search("rocket").first().name)
    }

    @Test
    fun symbolsFindTheirEmoji() {
        assertTrue("#️⃣" in english.found("#"))
        assertTrue("❓" in english.found("?"))
    }

    @Test
    fun aPastedEmojiFindsItselfWithItsSkinTone() {
        val match = english.search("👍🏽").first()

        assertEquals("👍", match.emoji)
        assertEquals("👍🏽", match.pasted)
        assertEquals("🚀", english.first("🚀"))
        assertNull(english.search("🚀").first().pasted)
    }

    @Test
    fun skinTonesSayTheirTones() {
        val thumbs = english.search("thumbs up").first()
        val handshake = english.search("handshake").first()
        val holdingHands = english.search("people holding hands").first()

        assertEquals(listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), thumbs.variants)
        assertEquals("thumbs up: medium skin tone", thumbs.nameOf("👍🏽"))
        assertEquals("handshake: light skin tone, dark skin tone", handshake.nameOf("🫱🏻‍🫲🏿"))
        assertEquals("people holding hands: light skin tone", holdingHands.nameOf("🧑🏻‍🤝‍🧑🏻"))
        assertEquals("pulgar hacia arriba: tono de piel medio", spanish.search("pulgar").first { it.emoji == "👍" }.nameOf("👍🏽"))
    }

    @Test
    fun emojiThePhoneCantDrawAreLeftOut() {
        val withoutRockets = EmojiIndex.read(asset("emoji"), asset("en"), english = null, drawable = { it != "🚀" && it != "👍🏽" })

        assertTrue("🚀" !in withoutRockets.found("rocket"))
        assertEquals(listOf("👍🏻", "👍🏼", "👍🏾", "👍🏿"), withoutRockets.search("thumbs up").first().variants)
    }

    @Test
    fun nothingTypedFindsNothing() {
        assertTrue(english.search("").isEmpty())
        assertTrue(english.search("   ").isEmpty())
    }

    @Test
    fun everyEmojiHasANameInEveryLanguage() {
        val emoji = asset("emoji").lines().filter { it.isNotBlank() && !it.startsWith("# ") }

        assertTrue(emoji.size > 1800)
        assertEquals(emoji.size, english.size)
        assertEquals(emoji.size, EmojiIndex.read(asset("emoji"), asset("es"), english = null, drawable = { true }).size)

        for (language in listOf("en", "es")) {
            for (line in asset(language).lines().filter { it.isNotBlank() && !it.startsWith("# ") }) {
                assertEquals(line, 3, line.split('\t').size)
            }
        }
    }
}
