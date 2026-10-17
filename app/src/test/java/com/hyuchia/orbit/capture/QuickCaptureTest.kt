package com.hyuchia.orbit.capture

import com.hyuchia.orbit.page.CapturedIdea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureTest {
    @Test
    fun eachLineIsAnIdea() {
        assertEquals(listOf("Call the venue", "Book the band"), ideasFromText("Call the venue\nBook the band"))
    }

    @Test
    fun blankLinesAreNone() {
        assertEquals(listOf("Flyers", "Tickets"), ideasFromText("\n  Flyers\n\n   \nTickets\n"))
    }

    @Test
    fun aCopiedListLosesItsMarkers() {
        assertEquals(
            listOf("Milk", "Eggs", "Bread", "Butter", "Jam", "Honey", "Tea"),
            ideasFromText("- Milk\n* Eggs\n+ Bread\n• Butter\n1. Jam\n2) Honey\n- [ ] Tea"),
        )
    }

    @Test
    fun aCheckedItemLosesItsCheckboxToo() {
        assertEquals(listOf("Pay the deposit", "Sign the contract"), ideasFromText("- [x] Pay the deposit\n- [X] Sign the contract"))
    }

    @Test
    fun markersInsideAnIdeaStay() {
        assertEquals(listOf("Pros - cons of moving", "Plan B: 2. attempt"), ideasFromText("Pros - cons of moving\nPlan B: 2. attempt"))
    }

    @Test
    fun spacesInsideAnIdeaCollapse() {
        assertEquals(listOf("A logo for the shop"), ideasFromText("A  logo for\tthe shop"))
    }

    @Test
    fun aLongIdeaIsNamedByItsStartAndKeptWholeAsItsNote() {
        val line = "word ".repeat(100).trim()
        assertEquals(listOf(line), ideasFromText(line))
        val idea = capturedIdeas(ideasFromText(line)).single()
        assertTrue(idea.text.length <= 200)
        assertTrue(idea.text.endsWith("…"))
        assertEquals(line, idea.note)
        assertEquals(listOf(CapturedIdea("Short")), capturedIdeas(listOf("Short")))
    }

    @Test
    fun windowsLineEndingsSplitToo() {
        assertEquals(listOf("One", "Two"), ideasFromText("One\r\nTwo\r\n"))
    }
}
