package com.hyuchia.orbit.page

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {
    @Test
    fun `messages from the page are read with the map they're about, and words a newer page adds`() {
        val message = protocolJson.decodeFromString(MapMessage.serializer(), """{"type":"details","id":"tokyo","map":"trip","later":true}""")

        assertEquals(MapMessage.Details("tokyo"), message)
    }

    @Test
    fun `a job's answer is read whatever map the page has`() {
        val message = protocolJson.decodeFromString(MapMessage.serializer(), """{"type":"result","job":7,"value":{"count":2},"map":"other"}""")

        assertEquals(7, (message as MapMessage.Result).job)
    }
}
