package com.hyuchia.orbit.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalAskTest {
    @Test
    fun `a map arrives as new, as another version, or as another version of a locked private map`() {
        assertEquals(ArrivalAsk.New, arrivalAsk(alreadyHere = false, hidden = false, concealed = false))
        assertEquals(ArrivalAsk.Update, arrivalAsk(alreadyHere = true, hidden = false, concealed = false))
        assertEquals(ArrivalAsk.UpdateLocked, arrivalAsk(alreadyHere = true, hidden = true, concealed = false))
    }

    @Test
    fun `while private maps hide, another version of one is asked about as a new map, until they're unlocked`() {
        assertEquals(ArrivalAsk.NewUntilUnlocked, arrivalAsk(alreadyHere = true, hidden = true, concealed = true))
        // Unlocked, it's the usual question.
        assertEquals(ArrivalAsk.Update, arrivalAsk(alreadyHere = true, hidden = false, concealed = true))
        // A map that isn't private, or a new one, is asked about as ever.
        assertEquals(ArrivalAsk.New, arrivalAsk(alreadyHere = false, hidden = false, concealed = true))
    }
}
