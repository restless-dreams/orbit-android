package com.hyuchia.orbit.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataFileTest {
    @Test
    fun `details read and write back as they were`() {
        val metadata = MapMetadata(created = 5, pinned = true, uuid = "abc", path = listOf("a", "b"), rehearsal = mapOf("a" to 12))
        assertEquals(metadata, MetadataFile.decode(MetadataFile.encode(metadata)))
    }

    @Test
    fun `fields a newer version added are kept`() {
        val read = MetadataFile.decode("""{"created":5,"colorScheme":"sunset","layout":{"rings":3}}""")
        assertEquals(5, read.created)
        assertEquals(setOf("colorScheme", "layout"), read.extras.keys)
        val written = MetadataFile.encode(read.copy(pinned = true))
        assertTrue(written.contains("\"colorScheme\":\"sunset\""))
        assertTrue(written.contains("\"layout\":{\"rings\":3}"))
    }

    @Test
    fun `a field a newer version changed the shape of is kept as it was, and the rest still read`() {
        val read = MetadataFile.decode("""{"uuid":"abc","path":{"steps":["a"]},"pinned":true}""")
        assertEquals("abc", read.uuid)
        assertEquals(true, read.pinned)
        assertEquals(null, read.path)
        assertTrue(MetadataFile.encode(read).contains("\"path\":{\"steps\":[\"a\"]}"))
    }

    @Test
    fun `a field this version sets wins over the one it kept`() {
        val read = MetadataFile.decode("""{"path":{"steps":["a"]}}""")
        val written = MetadataFile.encode(read.copy(path = listOf("b")))
        assertEquals(listOf("b"), MetadataFile.decode(written).path)
        assertEquals(JsonPrimitive("x"), MetadataFile.decode("""{"later":"x"}""").extras["later"])
    }

    @Test(expected = SerializationException::class)
    fun `text that isn't a JSON object can't be read`() {
        MetadataFile.decode("[1, 2]")
    }
}
