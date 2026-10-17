package com.hyuchia.orbit.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderNameTest {
    @Test
    fun `names keep their letters and lose what hides or reorders text`() {
        assertEquals("Ana's Pixel", senderName("Ana's Pixel"))
        assertEquals("Ana's Pixel", senderName("  Ana's\n\n\tPixel  "))
        assertEquals("Pixel", senderName("Pi​xel\u0007"))
        // A right-to-left override would show "lexiP" as something else entirely.
        assertEquals("evilgpj.exe", senderName("evil‮gpj.exe"))
        assertEquals("Téléphone de Zoë 📱", senderName("Téléphone de Zoë 📱"))
    }

    @Test
    fun `names are short, and never cut a character in half`() {
        assertEquals("a".repeat(40), senderName("a".repeat(100)))
        val emoji = senderName("📱".repeat(50))!!
        assertEquals(40, emoji.codePointCount(0, emoji.length))
    }

    @Test
    fun `a name too long for an advertisement is cut to fit, never in the middle of a character`() {
        assertEquals("Ana's Pixel", serviceName("Ana's Pixel"))
        // 40 phones are 160 bytes: an advertisement's name holds 63.
        val phones = serviceName(senderName("📱".repeat(40))!!)
        assertEquals(15, phones.codePointCount(0, phones.length))
        assertEquals(60, phones.toByteArray().size)
        val mixed = serviceName("Téléphone de Zoë, celui de la cuisine, près de la fenêtre")
        assertTrue(mixed.toByteArray().size <= 63)
        assertTrue("Téléphone de Zoë, celui de la cuisine, près de la fenêtre".startsWith(mixed))
    }

    @Test
    fun `a name with nothing to show is no name`() {
        assertNull(senderName(null))
        assertNull(senderName(""))
        assertNull(senderName(" ​‮\n"))
    }
}
