package com.hyuchia.orbit.page

import com.hyuchia.orbit.FileWords
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The app and its pages read each other's JSON. web/protocol-examples.ts
 * has one of each message between them, written as the sender writes it and
 * checked against the pages' own types; the pages' build writes them into
 * `page-protocol.json`, which these tests read.
 */
class ProtocolTest {
    private val examples: JsonObject = protocolJson.parseToJsonElement(
        requireNotNull(ProtocolTest::class.java.classLoader?.getResource("page-protocol.json")) { "No page-protocol.json on the test classpath: build web/" }.readText(),
    ).jsonObject

    /** The examples of `kind` (`commands` or `messages`) for `page` (`map` or `audience`). */
    private fun examples(page: String, kind: String): List<JsonObject> = examples.getValue(page).jsonObject.getValue(kind).jsonArray.map { it.jsonObject }

    private val JsonObject.type: String get() = getValue("type").jsonPrimitive.content

    /** The kinds of messages a sealed type has, by the names they're written with. */
    private fun kinds(serializer: KSerializer<*>): Set<String> = serializer.descriptor.getElementDescriptor(1).elementNames.toSet()

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

    @Test
    fun `the app writes each command as the map's page reads it, field for field`() {
        for (example in examples("map", "commands").filter { it.type != LANGUAGE }) {
            val command = protocolJson.decodeFromJsonElement(MapCommand.serializer(), example)

            assertEquals(example.type, canonical(example), canonical(protocolJson.encodeToJsonElement(MapCommand.serializer(), command)))
        }
    }

    @Test
    fun `the app writes each command as the audience's page reads it, field for field`() {
        for (example in examples("audience", "commands").filter { it.type != LANGUAGE }) {
            val command = protocolJson.decodeFromJsonElement(AudienceCommand.serializer(), example)

            assertEquals(example.type, canonical(example), canonical(protocolJson.encodeToJsonElement(AudienceCommand.serializer(), command)))
        }
    }

    @Test
    fun `the app gives the pages every word they say`() {
        val written = protocolJson.encodeToJsonElement(LanguageCommand.serializer(), LanguageCommand(FileWords(Locale.US).pageWords())).jsonObject

        for (example in (examples("map", "commands") + examples("audience", "commands")).filter { it.type == LANGUAGE }) {
            val missing = example.getValue("words").jsonObject.keys - written.getValue("words").jsonObject.keys

            assertEquals(LANGUAGE, written.type)
            assertTrue("Words the pages have and the app doesn't give them: $missing", missing.isEmpty())
        }
    }

    @Test
    fun `the pages have an example of every command the app sends`() {
        assertEquals(emptySet<String>(), kinds(MapCommand.serializer()) - examples("map", "commands").map { it.type }.toSet())
        assertEquals(emptySet<String>(), kinds(AudienceCommand.serializer()) - examples("audience", "commands").map { it.type }.toSet())
    }

    @Test
    fun `the app reads each message the pages send, with all it says`() {
        for (example in examples("map", "messages") + examples("audience", "messages")) {
            val message = protocolJson.decodeFromJsonElement(MapMessage.serializer(), example)
            val read = canonical(protocolJson.encodeToJsonElement(MapMessage.serializer(), message))

            assertTrue("The app doesn't read all of ${example.type}: it read $read from $example", says(read, canonical(example)))
        }
    }

    @Test
    fun `the app reads every kind of message the pages send, and the pages send every kind it reads`() {
        val sent = (examples("map", "messages") + examples("audience", "messages")).map { it.type }.toSet()

        assertEquals(sent, kinds(MapMessage.serializer()) - MADE_BY_THE_APP)
    }

    @Test
    fun `a message the app can't read is reported by its kind alone, never what it says`() {
        assertEquals("saved", messageKind(protocolJson.parseToJsonElement("""{"type":"saved","markdown":"# Plans"}""").jsonObject))
        assertEquals("unknown", messageKind(protocolJson.parseToJsonElement("""{"type":"# Plans for the surprise party"}""").jsonObject))
        assertEquals("unknown", messageKind(protocolJson.parseToJsonElement("""{"markdown":"# Plans"}""").jsonObject))
        assertEquals("unknown", messageKind(null))
    }

    private companion object {
        /** The command that tells a page the app's language and words, which both pages have. */
        const val LANGUAGE = "language"

        /** Messages the app gives itself about its page, never from the page: its process stopped. */
        val MADE_BY_THE_APP = setOf("stopped")

        /** `element` with every number written one way: 56 and 56.0 are the same number to a page. */
        fun canonical(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, value) -> canonical(value) })
            is JsonArray -> JsonArray(element.map(::canonical))
            is JsonPrimitive -> if (element.isString) element else element.doubleOrNull?.let(::JsonPrimitive) ?: element
        }

        /** Whether `whole` says everything `part` does, the same: each of its fields at any depth, and its lists item for item. */
        fun says(whole: JsonElement, part: JsonElement): Boolean = when (part) {
            is JsonObject -> whole is JsonObject && part.all { (key, value) -> whole[key]?.let { says(it, value) } == true }
            is JsonArray -> whole is JsonArray && whole.size == part.size && part.indices.all { says(whole[it], part[it]) }
            is JsonPrimitive -> whole == part
        }
    }
}
