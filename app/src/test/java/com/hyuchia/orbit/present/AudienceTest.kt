package com.hyuchia.orbit.present

import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.protocolJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AudienceTest {
    private fun encode(command: AudienceCommand): JsonObject =
        protocolJson.parseToJsonElement(protocolJson.encodeToString(AudienceCommand.serializer(), command)).jsonObject

    @Test
    fun commandsReadTheWayTheAudiencePageExpectsThem() {
        val show = encode(AudienceCommand.Show("# Talk", AppTheme(dark = true, tokens = mapOf("sun" to "#123456")), baseUrl = null, focus = "talk"))
        assertEquals("show", show.getValue("type").jsonPrimitive.content)
        assertEquals("# Talk", show.getValue("markdown").jsonPrimitive.content)
        assertEquals("talk", show.getValue("focus").jsonPrimitive.content)
        assertEquals("true", show.getValue("captions").jsonPrimitive.content)
        assertEquals("true", show.getValue("theme").jsonObject.getValue("dark").jsonPrimitive.content)

        assertEquals(
            mapOf("type" to "step", "id" to "intro", "index" to "2", "total" to "9"),
            encode(AudienceCommand.Step("intro", 2, 9)).mapValues { it.value.jsonPrimitive.content },
        )
        assertEquals(mapOf("type" to "captions", "on" to "false"), encode(AudienceCommand.Captions(false)).mapValues { it.value.jsonPrimitive.content })
        assertEquals(mapOf("type" to "end"), encode(AudienceCommand.End).mapValues { it.value.jsonPrimitive.content })
    }

    @Test
    fun mediaCommandsReadTheWayTheAudiencePageExpectsThem() {
        assertEquals(
            """{"type":"media","id":"tram","kind":"video","playing":true,"at":42.5}""",
            protocolJson.encodeToString(AudienceCommand.serializer(), AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 42.5)),
        )
        assertEquals(
            """{"type":"media","id":"alfama","kind":"image","playing":false,"at":0.0}""",
            protocolJson.encodeToString(AudienceCommand.serializer(), AudienceCommand.Media("alfama", MediaKind.Picture)),
        )
        assertEquals(mapOf("type" to "playback", "playing" to "false"), encode(AudienceCommand.Playback(false)).mapValues { it.value.jsonPrimitive.content })
        assertEquals(mapOf("type" to "seek", "at" to "0.0"), encode(AudienceCommand.Seek(0.0)).mapValues { it.value.jsonPrimitive.content })
        assertEquals(mapOf("type" to "closeMedia"), encode(AudienceCommand.CloseMedia).mapValues { it.value.jsonPrimitive.content })

        // And back, as the phone reads what it sent, the same.
        listOf(AudienceCommand.Media("tram", MediaKind.Video, true, 3.0), AudienceCommand.Playback(true), AudienceCommand.Seek(61.25), AudienceCommand.CloseMedia).forEach { command ->
            assertEquals(command, protocolJson.decodeFromString(AudienceCommand.serializer(), protocolJson.encodeToString(AudienceCommand.serializer(), command)))
        }
    }

    @Test
    fun screensSayHowTheirVideoIsGoingTheWayThePageWritesIt() {
        // As web/audience.ts reports it, before the length is known and after.
        val first = protocolJson.decodeFromString(MapMessage.serializer(), """{"type":"mediaState","id":"tram","playing":false,"time":0,"duration":null,"ended":false,"muted":false}""")
        assertEquals(MapMessage.MediaState("tram", playing = false, time = 0.0), first)
        val later = protocolJson.decodeFromString(MapMessage.serializer(), """{"type":"mediaState","id":"tram","playing":true,"time":12.75,"duration":190.2,"ended":false,"muted":true}""")
        assertEquals(MapMessage.MediaState("tram", playing = true, time = 12.75, duration = 190.2, muted = true), later)
    }
}
