package com.hyuchia.orbit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class UnknownFieldsTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val notes = FolderNotes.serializer().descriptor

    @Test
    fun `a newer version's fields come back, and this version's are as it wrote them`() {
        val kept = keepUnknownFields(json("""{"removed":["Trip"]}"""), json("""{"removed":["Plan"],"conflicts":["Trip"]}"""), notes)
        assertEquals(json("""{"conflicts":["Trip"],"removed":["Trip"]}"""), kept)
    }

    @Test
    fun `a field this version took away stays away`() {
        val kept = keepUnknownFields(json("""{}"""), json("""{"keptBoth":["Plan"],"conflicts":["Trip"]}"""), notes)
        assertEquals(json("""{"conflicts":["Trip"]}"""), kept)
    }

    @Test
    fun `fields inside fields come back, and inside each of a map's values`() {
        val descriptor = MapMetadata.serializer().descriptor
        val written = json("""{"sync":{"folder":"f","file":"a.md","hash":"b"},"rehearsal":{"a":3}}""")
        val original = json("""{"sync":{"folder":"f","file":"a.md","hash":"a","etag":"x"},"rehearsal":{"a":2}}""")
        assertEquals(json("""{"sync":{"etag":"x","folder":"f","file":"a.md","hash":"b"},"rehearsal":{"a":3}}"""), keepUnknownFields(written, original, descriptor))
    }
}
