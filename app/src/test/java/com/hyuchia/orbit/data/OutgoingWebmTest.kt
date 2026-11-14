package com.hyuchia.orbit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32

/**
 * WebM and Matroska as they leave the phone ([OutgoingMedia]). The files
 * in `outgoing/`: `tagged.webm`, half a second of VP8 and Opus from ffmpeg
 * with a title, tags, chapters, track names and when it was made;
 * `streamed.weba`, Opus that ffmpeg wrote to a pipe, its segment of no
 * size; `attached.weba`, Matroska with a cover attached; and
 * `recorded.webm` and `recorded.weba`, recorded by Chromium's
 * MediaRecorder, as phones' browsers record, its segment and clusters of
 * no size.
 */
class OutgoingWebmTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun fixture(name: String): File = File("src/test/resources/outgoing/$name")

    private fun file(name: String, bytes: ByteArray): File = temporary.newFile(name).apply { writeBytes(bytes) }

    private fun cleaned(file: File): ByteArray = ByteArrayOutputStream().also { (OutgoingMedia.of(file) ?: throw AssertionError("${file.name} doesn't go")).writeTo(it) }.toByteArray()

    private fun ByteArray.says(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    private fun text(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)

    /** An element as the test reads it: its ID, where it starts, where what's in it starts, where it ends, and its parts when it's one that holds them. */
    private class Element(val id: Long, val start: Int, val body: Int, val end: Int, val parts: List<Element>)

    /** The elements in `bytes` from `from` to `until`, one inside another as their IDs say; one of no size ends where its parent's next part begins. */
    private fun elements(bytes: ByteArray, from: Int = 0, until: Int = bytes.size, ends: Set<Long> = emptySet()): List<Element> {
        val found = mutableListOf<Element>()
        var position = from

        while (position < until) {
            val idLength = Integer.numberOfLeadingZeros(bytes[position].toInt() and 0xFF) - 23
            val id = (0 until idLength).fold(0L) { value, index -> (value shl 8) or (bytes[position + index].toLong() and 0xFF) }

            if (id in ends) {
                break
            }

            val sizeLength = Integer.numberOfLeadingZeros(bytes[position + idLength].toInt() and 0xFF) - 23
            val said = (0 until sizeLength).fold(0L) { value, index -> (value shl 8) or (bytes[position + idLength + index].toLong() and 0xFF) } and ((1L shl (7 * sizeLength)) - 1)
            val body = position + idLength + sizeLength
            val unknown = said == (1L shl (7 * sizeLength)) - 1
            val known = if (unknown) until else body + said.toInt()
            assertTrue("an element that runs past what it's in, at $position", known <= until)
            val parts = if (id in MASTERS) elements(bytes, body, known, if (!unknown) emptySet() else if (id == CLUSTER) LEVEL_ONE else TOP_LEVEL) else emptyList()
            val end = if (unknown) parts.lastOrNull()?.end ?: body else known
            found += Element(id, position, body, end, parts)
            position = end
        }

        return found
    }

    private fun List<Element>.one(id: Long): Element = single { it.id == id }

    private fun Element.text(bytes: ByteArray): String = String(bytes, body, end - body, Charsets.ISO_8859_1)

    /** An element of `id` holding `content`, its size in as few bytes as it takes. */
    private fun element(id: Long, vararg content: ByteArray): ByteArray {
        val body = content.fold(ByteArray(0)) { all, part -> all + part }
        val idBytes = (0 until 4).map { (id shr (8 * (3 - it))).toByte() }.dropWhile { it == 0.toByte() }.toByteArray()
        val sizeLength = (1..8).first { body.size < (1L shl (7 * it)) - 1 }
        val size = ByteArray(sizeLength) { (body.size.toLong() shr (8 * (sizeLength - 1 - it))).toByte() }.also { it[0] = (it[0].toInt() or (0x80 shr (sizeLength - 1))).toByte() }
        return idBytes + size + body
    }

    /** A CRC-32 element for what follows it in its parent, as Matroska reckons it, little-endian. */
    private fun checksum(of: ByteArray): ByteArray = CRC32().apply { update(of) }.value.let { value -> element(CRC_32, ByteArray(4) { (value shr (8 * it)).toByte() }) }

    private fun assertChecksumsRight(bytes: ByteArray, elements: List<Element>) {
        for (element in elements) {
            val crc = element.parts.firstOrNull { it.id == CRC_32 }

            if (crc != null) {
                val value = CRC32().apply { update(bytes, crc.end, element.end - crc.end) }.value
                assertEquals("the checksum of the element at ${element.start}", value, (0 until 4).fold(0L) { all, index -> all or ((bytes[crc.body + index].toLong() and 0xFF) shl (8 * index)) })
            }

            assertChecksumsRight(bytes, element.parts)
        }
    }

    /** What the fixtures' tags say: a title, an artist, a comment, when and where it was recorded, chapters, track names, and the apps that made it. */
    private val tags = listOf("Test title", "Test artist", "Test comment", "Test chapter", "Test camera track", "Test microphone track", "2026-10-21", "+38.7139-009.1394/", "Lavf", "Lavc", "Chrome")

    @Test
    fun `a WebM goes without its tags, chapters, title and track names, every frame where it was`() {
        val original = fixture("tagged.webm").readBytes()
        val bytes = cleaned(fixture("tagged.webm"))

        tags.forEach { assertFalse(it, bytes.says(it)) }
        assertEquals(original.size, bytes.size)

        val before = elements(original).one(SEGMENT).parts
        val segment = elements(bytes).one(SEGMENT)
        // The chapters and tags are Void, and the index's entries for them too; the clusters and cues are as they were.
        assertEquals(before.map { if (it.id == CHAPTERS || it.id == TAGS) VOID else it.id }, segment.parts.map { it.id })
        assertEquals(before.map { it.start }, segment.parts.map { it.start })

        for (kept in segment.parts.filter { it.id == CLUSTER || it.id == CUES }) {
            assertArrayEquals(original.copyOfRange(kept.start, kept.end), bytes.copyOfRange(kept.start, kept.end))
        }

        val seeking = segment.parts.one(SEEK_HEAD).parts
        assertEquals(listOf(INFO, TRACKS, CUES), seeking.filter { it.id == SEEK }.map { seek -> seek.parts.one(SEEK_ID).let { id -> (id.body until id.end).fold(0L) { value, at -> (value shl 8) or (bytes[at].toLong() and 0xFF) } } })
        assertEquals(2, seeking.count { it.id == VOID })

        // The details keep how to time it and how long it is; the apps that made it are named with nothing.
        val info = segment.parts.one(INFO).parts
        assertEquals(listOf(TIMESTAMP_SCALE, VOID, MUXING_APP, VOID, WRITING_APP, VOID, VOID, DURATION), info.map { it.id })
        assertEquals("", info.one(MUXING_APP).text(bytes))
        assertEquals("", info.one(WRITING_APP).text(bytes))

        for (entry in segment.parts.one(TRACKS).parts) {
            assertFalse(entry.parts.any { it.id == NAME })
        }

        // Every Void says nothing.
        assertTrue(segment.parts.filter { it.id == VOID }.all { void -> bytes.copyOfRange(void.body, void.end).all { it == 0.toByte() } })
    }

    @Test
    fun `a recording of no size, as MediaRecorder writes it, goes without the app that made it`() {
        for (name in listOf("recorded.webm", "recorded.weba")) {
            val original = fixture(name).readBytes()
            val bytes = cleaned(fixture(name))

            assertFalse(bytes.says("Chrome"))
            assertEquals(original.size, bytes.size)

            val segment = elements(bytes).one(SEGMENT)
            val clusters = segment.parts.filter { it.id == CLUSTER }
            assertTrue(name, clusters.size > 1)
            assertEquals("", segment.parts.one(INFO).parts.one(MUXING_APP).text(bytes))
            // From the first cluster on, every byte is as it was.
            assertArrayEquals(original.copyOfRange(clusters.first().start, original.size), bytes.copyOfRange(clusters.first().start, bytes.size))
        }
    }

    @Test
    fun `a stream of no size from ffmpeg, and Matroska's attachments, go without what they say`() {
        for (name in listOf("streamed.weba", "attached.weba")) {
            val original = fixture(name).readBytes()
            val bytes = cleaned(fixture(name))

            tags.forEach { assertFalse("$name says $it", bytes.says(it)) }
            assertFalse(bytes.says("\u0089PNG"))
            assertFalse(bytes.says("cover.png"))
            assertEquals(original.size, bytes.size)
            assertTrue(elements(bytes).one(SEGMENT).parts.none { it.id == TAGS || it.id == ATTACHMENTS })
            // ffmpeg's Matroska has a checksum in each of the segment's parts: those that changed have theirs again.
            assertChecksumsRight(bytes, elements(bytes))
        }
    }

    /** A Matroska file with `tracks` and a cluster, its details with a title and when it was made, and checksums in its details and tracks. */
    private fun withChecksums(entry: ByteArray): ByteArray {
        val details = element(TIMESTAMP_SCALE, byteArrayOf(0x0F, 0x42, 0x40)) + element(TITLE, text("Test title")) +
            element(DATE_UTC, ByteArray(8) { 7 }) + element(MUXING_APP, text("x")) + element(WRITING_APP, text("Test writer"))
        val tracks = checksum(entry) + entry
        val cluster = element(CLUSTER, element(0xE7, byteArrayOf(0)), element(0xA3, byteArrayOf(0x81.toByte(), 0, 0, 0x80.toByte()) + ByteArray(20) { 3 }))
        val segment = element(INFO, checksum(details), details) + element(TRACKS, tracks) + cluster
        return element(EBML, element(0x4282, text("matroska"))) + element(SEGMENT, segment)
    }

    @Test
    fun `a part with a checksum gets it again, and a Vorbis track's setup goes without its comments`() {
        // Vorbis's three headers laced as Matroska keeps them: the comment header with a tag in it, as mkvmerge copies one from Ogg.
        val identification = text("\u0001vorbis") + ByteArray(23) { 1 }
        val comments = text("\u0003vorbis") + byteArrayOf(10, 0, 0, 0) + text("Test maker") + byteArrayOf(1, 0, 0, 0, 16, 0, 0, 0) + text("title=Test title") + byteArrayOf(1)
        val setup = text("\u0005vorbis") + ByteArray(40) { 5 }
        val laced = byteArrayOf(2, identification.size.toByte(), comments.size.toByte()) + identification + comments + setup
        val track = element(0xD7, byteArrayOf(1)) + element(CODEC_ID, text("A_VORBIS")) + element(NAME, text("Test microphone track")) + element(CODEC_PRIVATE, laced)
        val entry = element(TRACK_ENTRY, checksum(track), track)
        val original = withChecksums(entry)

        val bytes = cleaned(file("checked.mka", original))

        listOf("Test title", "Test maker", "Test writer", "Test microphone track").forEach { assertFalse(it, bytes.says(it)) }
        assertEquals(original.size, bytes.size)
        val segment = elements(bytes).one(SEGMENT)
        assertChecksumsRight(bytes, listOf(segment))
        // A one-letter app's name says nothing too, its size taking in its letter.
        assertEquals("", segment.parts.one(INFO).parts.one(MUXING_APP).text(bytes))

        val setupGoing = segment.parts.one(TRACKS).parts.one(TRACK_ENTRY).parts.one(CODEC_PRIVATE)
        val at = setupGoing.body + 3 + identification.size
        assertArrayEquals(text("\u0003vorbis") + ByteArray(8) + byteArrayOf(1) + ByteArray(comments.size - 16), bytes.copyOfRange(at, at + comments.size))
        assertArrayEquals(identification, bytes.copyOfRange(at - identification.size, at))
        assertArrayEquals(setup, bytes.copyOfRange(at + comments.size, at + comments.size + setup.size))
    }

    @Test
    fun `a WebM that can't be read through doesn't go`() {
        val webm = fixture("tagged.webm").readBytes()
        val recorded = fixture("recorded.webm").readBytes()
        val segment = elements(webm).one(SEGMENT)
        val tracks = segment.parts.one(TRACKS)
        val firstBlock = segment.parts.one(CLUSTER).parts.first { it.id == SIMPLE_BLOCK }
        val secondCluster = elements(recorded).one(SEGMENT).parts.filter { it.id == CLUSTER }[1]

        val broken = mapOf(
            // Cut short in its cues after its cluster, and in its tags: what it ends in isn't sound. And in its first block: no block is whole.
            "cues-cut.webm" to webm.copyOfRange(0, segment.parts.one(CUES).start + 8),
            "tags-cut.webm" to webm.copyOfRange(0, segment.parts.one(TAGS).start + 50),
            "first-block-cut.webm" to webm.copyOfRange(0, firstBlock.start + 5),
            "after.webm" to webm + text("Test leftover"),
            // Tracks that say they're longer than the segment they're in.
            "long.webm" to webm.copyOf().also { it[tracks.start + 4] = 0x1F; it[tracks.start + 5] = 0xFF.toByte() },
            "only-a-header.webm" to webm.copyOfRange(0, segment.start),
            // A recording with a block that doesn't read, in a cluster with more after it.
            "damaged.webm" to recorded.copyOf().also { it[secondCluster.parts[1].start] = 0 },
        )

        for ((name, bytes) in broken) {
            assertNull(name, OutgoingMedia.of(file(name, bytes)))
        }
    }

    /** The ID a Seek entry of the index points at. */
    private fun Element.target(bytes: ByteArray): Long = parts.one(SEEK_ID).let { id -> (id.body until id.end).fold(0L) { value, at -> (value shl 8) or (bytes[at].toLong() and 0xFF) } }

    @Test
    fun `a WebM cut short goes clean, without the block it ends in, its segment and cluster saying where they end now`() {
        val original = fixture("tagged.webm").readBytes()
        val cluster = elements(original).one(SEGMENT).parts.one(CLUSTER)
        val last = cluster.parts.last { it.id == SIMPLE_BLOCK || it.id == BLOCK_GROUP }

        // Cut in its last block, in that block's ID, and right after the block before it: the cues after its cluster are cut away.
        for (cut in listOf(last.start + 10, last.start + 1, last.start)) {
            val bytes = cleaned(file("cut-$cut.webm", original.copyOfRange(0, cut)))

            tags.forEach { assertFalse("cut at $cut says $it", bytes.says(it)) }
            assertEquals(last.start, bytes.size)

            // Every element is whole in what it's in, as elements() reads them, and the segment and its cluster end with the file.
            val segment = elements(bytes).one(SEGMENT)
            assertEquals(bytes.size, segment.end)
            assertEquals(bytes.size, segment.parts.one(CLUSTER).end)
            assertArrayEquals(original.copyOfRange(cluster.body, last.start), bytes.copyOfRange(cluster.body, bytes.size))
            // The index points at the details and the tracks, and no longer at the cues.
            assertEquals(listOf(INFO, TRACKS), segment.parts.one(SEEK_HEAD).parts.filter { it.id == SEEK }.map { it.target(bytes) })
        }
    }

    @Test
    fun `a recording of no size cut short goes clean, without the block or cluster it ends in`() {
        for (name in listOf("recorded.webm", "recorded.weba")) {
            val original = fixture(name).readBytes()
            val whole = cleaned(fixture(name))
            val last = elements(original).one(SEGMENT).parts.last { it.id == CLUSTER }
            val block = last.parts.last { it.id == SIMPLE_BLOCK }

            // Cut in its last block; and in its last cluster's time and size, before any block of it, when the cluster before is the last one.
            val cuts = mapOf(block.start + 3 to block.start, last.body + 2 to last.start, last.start + 6 to last.start)

            for ((cut, end) in cuts) {
                // Nothing in it says how long it is: the bytes before the cut go as the whole recording's do.
                assertArrayEquals("$name cut at $cut", whole.copyOfRange(0, end), cleaned(file("cut-$cut-$name", original.copyOfRange(0, cut))))
            }
        }
    }

    @Test
    fun `a stream of no size and Matroska with checksums, cut short, go clean, their cluster saying where it ends now`() {
        for (name in listOf("streamed.weba", "attached.weba")) {
            val original = fixture(name).readBytes()
            val last = elements(original).one(SEGMENT).parts.one(CLUSTER).parts.last()
            val bytes = cleaned(file("cut-$name", original.copyOfRange(0, last.start + 5)))

            tags.forEach { assertFalse("$name says $it", bytes.says(it)) }
            assertFalse(bytes.says("\u0089PNG"))
            assertEquals(last.start, bytes.size)
            assertEquals(bytes.size, elements(bytes).one(SEGMENT).parts.one(CLUSTER).end)
            // A cluster's checksum couldn't be right without its end: it says nothing now, as a segment's own does.
            assertChecksumsRight(bytes, elements(bytes))
        }
    }

    /** A cluster at `time`, with a block of `frames` for each of them, of track 1. */
    private fun cluster(time: Int, vararg frames: String): ByteArray =
        element(CLUSTER, element(0xE7, byteArrayOf(time.toByte())), *frames.map { element(SIMPLE_BLOCK, byteArrayOf(0x81.toByte(), 0, 0, 0x80.toByte()) + text(it)) }.toTypedArray())

    /** A cue at `time` for track 1, at the cluster `at` from the segment's start, in two bytes so the cues' size doesn't change with it, and at its block `relative` from what's in the cluster. */
    private fun cuePoint(time: Int, at: Int, relative: Int? = null): ByteArray {
        val positions = element(0xF7, byteArrayOf(1)) + element(0xF1, byteArrayOf((at shr 8).toByte(), at.toByte())) + (relative?.let { element(0xF0, byteArrayOf(it.toByte())) } ?: ByteArray(0))
        return element(CUE_POINT, element(0xB3, byteArrayOf((time shr 8).toByte(), time.toByte())), element(CUE_TRACK_POSITIONS, positions))
    }

    /**
     * A WebM with its index and cues before its clusters, as ffmpeg writes
     * them when asked to, the cues `pointing` at the clusters, from where
     * each starts in the segment: two clusters, the second of two blocks.
     */
    private fun cuesFirst(pointing: (List<Int>) -> List<ByteArray>): ByteArray {
        val clusters = listOf(cluster(0, "Test first frame"), cluster(100, "Test second frame", "Test third frame"))

        fun segment(starts: List<Int>): ByteArray {
            val cues = element(CUES, *pointing(starts).toTypedArray())
            val index = element(SEEK_HEAD, element(SEEK, element(SEEK_ID, text("\u001CS»k")), element(SEEK_POSITION, byteArrayOf(0, 0))))
            return index + cues + clusters.fold(ByteArray(0)) { all, cluster -> all + cluster }
        }

        // Where each cluster starts in the segment, past the index and the cues, whose sizes don't change with it.
        val before = segment(listOf(0, 0)).size - clusters.sumOf { it.size }
        val starts = listOf(before, before + clusters[0].size)
        return element(EBML, element(0x4282, text("webm"))) + element(SEGMENT, segment(starts))
    }

    @Test
    fun `a WebM cut short with its cues before its clusters goes without the cues for what was cut away`() {
        // As the review's probe made one: cues for the second cluster, cut short in it before any of its blocks was whole, so it's cut away.
        val alone = cuesFirst { starts -> listOf(cuePoint(100, starts[1])) }
        val secondAt = elements(alone).one(SEGMENT).parts.filter { it.id == CLUSTER }[1].start
        val cut = alone.copyOfRange(0, secondAt + 6)
        val bytes = cleaned(file("cues-first.webm", cut))

        assertEquals(secondAt, bytes.size)
        val segment = elements(bytes).one(SEGMENT)
        assertEquals(bytes.size, segment.end)
        // The cues had nothing left to say: they're Void, where they were, and so is the index's entry for them.
        val before = elements(alone).one(SEGMENT).parts
        assertEquals(before.dropLast(1).map { if (it.id == CUES) VOID else it.id }, segment.parts.map { it.id })
        assertEquals(before.dropLast(1).map { it.start }, segment.parts.map { it.start })
        assertTrue(bytes.copyOfRange(segment.parts.one(VOID).body, segment.parts.one(VOID).end).all { it == 0.toByte() })
        assertTrue(segment.parts.one(SEEK_HEAD).parts.none { it.id == SEEK })
        assertArrayEquals(alone.copyOfRange(segment.parts.one(CLUSTER).start, bytes.size), bytes.copyOfRange(segment.parts.one(CLUSTER).start, bytes.size))
    }

    @Test
    fun `a WebM's cues for a block cut away go, and those for what's left stay as they were`() {
        // A cue for each cluster, and one for the second cluster's second block, cut short in that block.
        val webm = cuesFirst { starts -> listOf(cuePoint(0, starts[0]), cuePoint(100, starts[1]), cuePoint(101, starts[1], relative = 3 + 23)) }
        val second = elements(webm).one(SEGMENT).parts.filter { it.id == CLUSTER }[1]
        val third = second.parts.last()
        assertArrayEquals(text("Test third frame"), webm.copyOfRange(third.body + 4, third.end))
        assertEquals(second.body + 3 + 23, third.start)
        val bytes = cleaned(file("cues-relative.webm", webm.copyOfRange(0, third.start + 5)))

        assertEquals(third.start, bytes.size)
        val segment = elements(bytes).one(SEGMENT)
        val points = elements(bytes, segment.parts.one(CUES).body, segment.parts.one(CUES).end)
        val pointsBefore = elements(webm).one(SEGMENT).parts.one(CUES).let { elements(webm, it.body, it.end) }
        // The cue for the block cut away is Void; the others are as they were, every one where it was.
        assertEquals(listOf(CUE_POINT, CUE_POINT, VOID), points.map { it.id })
        assertEquals(pointsBefore.map { it.start }, points.map { it.start })

        for (point in points.take(2)) {
            assertArrayEquals(webm.copyOfRange(point.start, point.end), bytes.copyOfRange(point.start, point.end))
        }

        assertTrue(bytes.copyOfRange(points[2].body, points[2].end).all { it == 0.toByte() })
        // The index still points at the cues, which are there.
        assertEquals(listOf(CUES), segment.parts.one(SEEK_HEAD).parts.filter { it.id == SEEK }.map { it.target(bytes) })
    }

    private companion object {
        const val EBML = 0x1A45DFA3L
        const val SEGMENT = 0x18538067L
        const val SEEK_HEAD = 0x114D9B74L
        const val SEEK = 0x4DBBL
        const val SEEK_ID = 0x53ABL
        const val SEEK_POSITION = 0x53ACL
        const val CUE_POINT = 0xBBL
        const val CUE_TRACK_POSITIONS = 0xB7L
        const val INFO = 0x1549A966L
        const val TIMESTAMP_SCALE = 0x2AD7B1L
        const val DURATION = 0x4489L
        const val TITLE = 0x7BA9L
        const val DATE_UTC = 0x4461L
        const val MUXING_APP = 0x4D80L
        const val WRITING_APP = 0x5741L
        const val TRACKS = 0x1654AE6BL
        const val TRACK_ENTRY = 0xAEL
        const val NAME = 0x536EL
        const val CODEC_ID = 0x86L
        const val CODEC_PRIVATE = 0x63A2L
        const val CHAPTERS = 0x1043A770L
        const val CLUSTER = 0x1F43B675L
        const val SIMPLE_BLOCK = 0xA3L
        const val BLOCK_GROUP = 0xA0L
        const val CUES = 0x1C53BB6BL
        const val ATTACHMENTS = 0x1941A469L
        const val TAGS = 0x1254C367L
        const val VOID = 0xECL
        const val CRC_32 = 0xBFL

        /** The elements that hold others, as far as the test reads them. */
        val MASTERS = setOf(EBML, SEGMENT, SEEK_HEAD, SEEK, INFO, TRACKS, TRACK_ENTRY, CHAPTERS, CUES, ATTACHMENTS, TAGS, CLUSTER)

        /** What ends a segment of no size: the next segment, or the next file's header. */
        val TOP_LEVEL = setOf(EBML, SEGMENT)

        /** What ends a cluster of no size: the segment's next part. */
        val LEVEL_ONE = setOf(SEEK_HEAD, INFO, TRACKS, CHAPTERS, CLUSTER, CUES, ATTACHMENTS, TAGS) + TOP_LEVEL
    }
}
