package com.hyuchia.orbit.data

import com.hyuchia.orbit.study.StudyCard
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MetadataFileTest {
    @Test
    fun `details read and write back as they were`() {
        val metadata = MapMetadata(created = 5, pinned = true, uuid = "abc", path = listOf("a", "b"), rehearsal = mapOf("a" to 12))
        assertEquals(metadata, MetadataFile.decode(MetadataFile.encode(metadata)))
    }

    @Test
    fun `study progress reads and writes back as it was`() {
        val metadata = MapMetadata(study = mapOf("ser" to StudyCard(box = 2, due = 20_003, first = 19_990), "estar" to StudyCard()))
        val written = MetadataFile.encode(metadata)
        assertEquals(metadata, MetadataFile.decode(written))
        assertTrue(written.contains("\"study\":{\"ser\":{\"box\":2,\"due\":20003,\"first\":19990},\"estar\":{}}"))
        assertEquals(null, MetadataFile.decode("{}").study)
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

    @Test
    fun `a private map's cover reads and writes back as it was, and only when it has one`() {
        val metadata = MapMetadata(created = 5, isPrivate = true, cover = LockedCover(icon = "📓", name = "Journal"))
        val written = MetadataFile.encode(metadata)
        assertTrue(written.contains("\"cover\":{\"icon\":\"📓\",\"name\":\"Journal\"}"))
        assertEquals(metadata, MetadataFile.decode(written))
        assertEquals(LockedCover(name = "Journal"), MetadataFile.decode("""{"cover":{"name":"Journal"}}""").cover)
        // Details written before covers read as having none.
        assertEquals(null, MetadataFile.decode("""{"created":5,"private":true}""").cover)
        assertFalse(MetadataFile.encode(MapMetadata(created = 5, isPrivate = true)).contains("cover"))
    }

    @Test
    fun `what a newer version adds to a cover is kept as this one changes it`() {
        val read = MetadataFile.decode("""{"private":true,"cover":{"icon":"📓","name":"Journal","color":"#FF9E96"}}""")
        assertEquals(LockedCover(icon = "📓", name = "Journal", extras = mapOf("color" to JsonPrimitive("#FF9E96"))), read.cover)

        val renamed = MetadataFile.encode(read.copy(cover = read.cover?.copy(name = "Diary")))
        assertEquals(LockedCover(icon = "📓", name = "Diary", extras = mapOf("color" to JsonPrimitive("#FF9E96"))), MetadataFile.decode(renamed).cover)
    }

    @Test
    fun `a cover of another shape is kept as it was, and the rest still read`() {
        val read = MetadataFile.decode("""{"private":true,"cover":"📓 Journal"}""")
        assertEquals(true, read.isPrivate)
        assertEquals(null, read.cover)
        assertTrue(MetadataFile.encode(read).contains("\"cover\":\"📓 Journal\""))
    }

    @Test
    fun `a private map says so as private, and only when it is`() {
        val written = MetadataFile.encode(MapMetadata(created = 5, isPrivate = true))
        assertTrue(written.contains("\"private\":true"))
        assertEquals(true, MetadataFile.decode(written).isPrivate)
        assertFalse(MetadataFile.encode(MapMetadata(created = 5)).contains("private"))
        // Details written before maps could be private read as not private.
        assertEquals(false, MetadataFile.decode("""{"created":5,"pinned":true}""").isPrivate)
    }

    @Test
    fun `a private field this version can't read is private, and goes back as it was until the map is made not private`() {
        for (written in listOf("\"yes\"", "{\"locked\":true}", "null", "1")) {
            val read = MetadataFile.decode("""{"created":5,"private":$written,"pinned":true}""")
            assertTrue("$written reads as private", read.isPrivate)
            assertEquals(true, read.pinned)
            // While it's still private, the newer form goes back as it came, and reads as private again.
            val kept = MetadataFile.encode(read.copy(opened = 7))
            assertTrue(kept.contains("\"private\":$written"))
            assertTrue(MetadataFile.decode(kept).isPrivate)
            // Made not private, it says nothing of the kind any more.
            val cleared = MetadataFile.encode(read.copy(isPrivate = false))
            assertFalse(cleared.contains("private"))
            assertFalse(MetadataFile.decode(cleared).isPrivate)
        }
    }

    @Test
    fun `details that can't be read at all read as a private map's, and none as an ordinary map's`() {
        val folder = Files.createTempDirectory("orbit-details").toFile()

        try {
            val file = File(folder, "meta.json")
            val fresh = MapMetadata(created = 5)
            assertEquals(fresh, MetadataFile.read(file, fresh))

            for (damaged in listOf("{ not json", "", "[1, 2]", "\"private\"")) {
                file.writeText(damaged)
                assertEquals(fresh.copy(isPrivate = true, unreadable = true), MetadataFile.read(file, fresh))
                // Nor do they say where its file is in the folder maps are kept in.
                assertTrue(MetadataFile.read(file, fresh).folderFileUnknown)
            }

            file.writeText("""{"created":3,"private":true}""")
            file.setReadable(false)

            // Details that can't be read for now aren't known: a change mustn't write over them, and readers take them as a private map's.
            assertThrows(IOException::class.java) { MetadataFile.read(file, fresh) }
            assertEquals(fresh.copy(isPrivate = true, unreadable = true), MetadataFile.readOrPrivate(file, fresh))
        } finally {
            File(folder, "meta.json").setReadable(true)
            folder.deleteRecursively()
        }
    }

    @Test
    fun `fields a newer version added inside the folder's record are kept as this one writes the record again`() {
        val read = MetadataFile.decode("""{"created":5,"sync":{"folder":"f","file":"Trip.md","hash":"a","etag":"x1","checked":{"at":7}}}""")
        assertEquals("Trip.md", read.sync?.file)
        val written = MetadataFile.decode(MetadataFile.encode(read.copy(sync = read.sync?.copy(hash = "b"), pinned = true)))
        assertEquals("b", written.sync?.hash)
        assertEquals(true, written.pinned)
        // Written again by this version, they're still there, inside the record.
        assertTrue(MetadataFile.encode(written).contains("\"etag\":\"x1\""))
        assertTrue(MetadataFile.encode(written).contains("\"checked\":{\"at\":7}"))
    }

    @Test
    fun `a record this version takes away stays away, what a newer version added inside it too`() {
        val read = MetadataFile.decode("""{"created":5,"sync":{"folder":"f","file":"Trip.md","hash":"a","etag":"x1"}}""")
        val written = MetadataFile.encode(read.copy(sync = null))
        assertFalse(written.contains("sync"))
        assertFalse(written.contains("etag"))
    }

    @Test
    fun `details with nothing a newer version added inside them keep nothing of the kind`() {
        val read = MetadataFile.decode("""{"created":5,"sync":{"folder":"f","file":"Trip.md","hash":"a"},"study":{"ser":{"box":2}}}""")
        assertEquals(emptyMap<String, Any>(), read.extrasWithin)
        assertEquals(read, MetadataFile.decode(MetadataFile.encode(read)))
    }

    @Test(expected = SerializationException::class)
    fun `text that isn't a JSON object can't be read`() {
        MetadataFile.decode("[1, 2]")
    }
}
