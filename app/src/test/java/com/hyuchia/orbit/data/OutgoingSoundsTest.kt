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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sounds as they leave the phone ([OutgoingMedia]). The files in
 * `outgoing/` are half a second of a tone, made with ffmpeg 9 (and the
 * flac tool) with every tag their format carries: a title, an artist, a
 * comment, a date and where they were recorded, and a cover and chapters
 * where the format has them. `frames.flac` is `tagged.flac`'s tone, tags
 * and cover from the flac tool in frames of 256 samples, so it can be cut
 * short after a few of them.
 */
class OutgoingSoundsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun fixture(name: String): File = File("src/test/resources/outgoing/$name")

    private fun file(name: String, bytes: ByteArray): File = temporary.newFile(name).apply { writeBytes(bytes) }

    private fun sent(outgoing: OutgoingFile): ByteArray = ByteArrayOutputStream().also { outgoing.writeTo(it) }.toByteArray()

    private fun cleaned(file: File): ByteArray = sent(OutgoingMedia.of(file) ?: throw AssertionError("${file.name} doesn't go"))

    private fun ByteArray.says(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    private fun text(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)

    private fun littleEndian(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    /** What the fixtures' tags say, the cover's PNG among it. */
    private val tags = listOf("Test title", "Test artist", "Test album", "Test comment", "Test chapter", "2026-10-21", "+38.7139-009.1394/", "\u0089PNG")

    private fun assertSaysNothing(name: String, bytes: ByteArray) {
        for (tag in tags) {
            assertFalse("$name still says $tag", bytes.says(tag))
        }
    }

    /** Where an ID3v2 tag at the start of `bytes` ends: its size is seven bits a byte. */
    private fun id3End(bytes: ByteArray): Int = 10 + (6..9).fold(0) { size, at -> (size shl 7) or (bytes[at].toInt() and 0x7F) }

    @Test
    fun `an MP3 goes without its tags, every frame where it was`() {
        val original = fixture("tagged.mp3").readBytes()
        val bytes = cleaned(fixture("tagged.mp3"))
        val frames = id3End(original)

        assertSaysNothing("tagged.mp3", bytes)
        // ID3v1's 128 bytes at the end stay out; the ID3v2 tag at the start stays as long, with nothing in it.
        assertEquals(original.size - 128, bytes.size)
        assertEquals(frames, id3End(bytes))
        assertArrayEquals(text("ID3") + byteArrayOf(4, 0, 0), bytes.copyOfRange(0, 6))
        assertTrue(bytes.copyOfRange(10, frames).all { it == 0.toByte() })
        assertArrayEquals(original.copyOfRange(frames, original.size - 128), bytes.copyOfRange(frames, bytes.size))
    }

    @Test
    fun `AAC in ADTS goes without its ID3 and APE tags`() {
        val original = fixture("tagged.aac").readBytes()
        val bytes = cleaned(fixture("tagged.aac"))
        val frames = id3End(original)
        val ape = String(original, Charsets.ISO_8859_1).indexOf("APETAGEX")

        assertSaysNothing("tagged.aac", bytes)
        assertEquals(ape, bytes.size)
        assertArrayEquals(original.copyOfRange(frames, ape), bytes.copyOfRange(frames, bytes.size))
    }

    /** tagged.mp3's frames alone. */
    private fun mp3Frames(): ByteArray = fixture("tagged.mp3").readBytes().let { it.copyOfRange(id3End(it), it.size - 128) }

    /** An APEv2 tag with a header, as foobar2000 and Mp3tag write one: its one item is a title. */
    private fun apeTag(): ByteArray {
        val item = littleEndian(10) + littleEndian(0) + text("Title\u0000") + text("Test title")
        val size = item.size + 32

        fun frame(flags: Int) = text("APETAGEX") + littleEndian(2000) + littleEndian(size) + littleEndian(1) + littleEndian(flags) + ByteArray(8)

        return frame(0xA0000000.toInt()) + item + frame(0x80000000.toInt())
    }

    /** A Lyrics3v2 tag: its fields, then its size in six digits and `LYRICS200`. */
    private fun lyricsTag(): ByteArray {
        val fields = text("LYRICSBEGIN") + text("IND00003110") + text("LYR00010Test title")
        return fields + text("%06d".format(fields.size)) + text("LYRICS200")
    }

    /** An ID3v1 tag, with the extended tag's TAG+ before it. */
    private fun id3v1Tags(): ByteArray = text("TAG+") + text("Test title").copyOf(223) + text("TAG") + text("Test artist").copyOf(125)

    @Test
    fun `tags after an MP3's frames stay out, whichever there are`() {
        val frames = mp3Frames()
        val bytes = cleaned(file("tags.mp3", frames + apeTag() + lyricsTag() + id3v1Tags()))

        assertArrayEquals(frames, bytes)
    }

    /** MPEG-2's bitrates for layer III in kilobits a second, by bitrate index from 1, as tagged.mp3's frames give theirs. */
    private val layerThreeKilobits = intArrayOf(8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    /**
     * Where each frame of an MP3 or an AAC starts, from `at` until `until`,
     * as its header says how long it is: ADTS says it, and tagged.mp3's
     * frames, MPEG-2's layer III at 22 050 Hz, are 72 bytes for each
     * kilobit a second, and their byte of padding.
     */
    private fun frameStarts(bytes: ByteArray, at: Int, until: Int): List<Int> {
        val starts = mutableListOf<Int>()
        var position = at

        while (position < until) {
            starts += position
            val head = (0 until 6).map { bytes[position + it].toInt() and 0xFF }

            position += if (head[1] and 0xF6 == 0xF0) {
                ((head[3] and 0x03) shl 11) or (head[4] shl 3) or (head[5] shr 5)
            } else {
                72_000 * layerThreeKilobits[(head[2] shr 4) - 1] / 22_050 + (head[2] shr 1 and 0x01)
            }
        }

        return starts
    }

    @Test
    fun `an MP3 or AAC cut short goes clean, without the frame or tag its file ends in`() {
        for (name in listOf("tagged.mp3", "tagged.aac")) {
            val original = fixture(name).readBytes()
            val whole = cleaned(fixture(name))
            // Where its frames end, and its tags after them start: the MP3's ID3v1, and AAC's APE tag.
            val tags = whole.size
            val last = frameStarts(original, id3End(original), tags).last()

            // Cut in its last frame, in that frame's header, right after it, and in the tag after it: each goes as far as its last whole frame.
            val cuts = mapOf(tags - 10 to last, last + 2 to last, last + 5 to last, tags to tags, tags + 2 to tags, tags + 20 to tags, tags + 100 to tags)

            for ((cut, end) in cuts) {
                val bytes = cleaned(file("cut-$cut-$name", original.copyOfRange(0, cut)))

                assertSaysNothing("$name cut at $cut", bytes)
                assertArrayEquals("$name cut at $cut", whole.copyOfRange(0, end), bytes)
            }
        }
    }

    /** An ID3v2.4 tag with a footer, as that version lets a tag come after the sound: its one frame is a title. */
    private fun footedId3Tag(): ByteArray {
        val frame = text("TIT2") + byteArrayOf(0, 0, 0, 11, 0, 0, 3) + text("Test title")
        val size = byteArrayOf(0, 0, 0, frame.size.toByte())
        return text("ID3") + byteArrayOf(4, 0, 0x10) + size + frame + text("3DI") + byteArrayOf(4, 0, 0x10) + size
    }

    @Test
    fun `tags after an MP3's frames that its file ends in, cut short, stay out`() {
        val frames = mp3Frames()
        val tagged = frames + apeTag() + lyricsTag() + id3v1Tags()
        val lyrics = frames.size + apeTag().size

        // Cut in the APE tag's header and in its items, in the Lyrics3 tag, in TAG+ and in ID3v1's TAG after it.
        for (cut in listOf(frames.size + 20, frames.size + 60, lyrics + 30, tagged.size - 300, tagged.size - 40)) {
            assertArrayEquals("cut at $cut", frames, cleaned(file("tags-$cut.mp3", tagged.copyOfRange(0, cut))))
        }

        // An ID3v2 tag with a footer after them, cut in its title.
        val footed = frames + footedId3Tag()
        assertArrayEquals(frames, cleaned(file("footed.mp3", footed.copyOfRange(0, footed.size - 15))))
    }

    @Test
    fun `an ID3 tag between two MP3s' frames is emptied where it is, and zeros between frames stay`() {
        val one = fixture("tagged.mp3").readBytes().let { it.copyOfRange(0, it.size - 128) }
        val two = one + ByteArray(300) + one
        val bytes = cleaned(file("two.mp3", two))

        assertSaysNothing("two.mp3", bytes)
        assertEquals(two.size, bytes.size)
        assertArrayEquals(mp3Frames(), bytes.copyOfRange(id3End(one), one.size))
        assertArrayEquals(mp3Frames(), bytes.copyOfRange(one.size + 300 + id3End(one), bytes.size))
    }

    /** A FLAC's metadata blocks from `at`, its `fLaC`: each one's start, first byte and length, and where the frames start. */
    private fun flacBlocks(bytes: ByteArray, at: Int = 0): Pair<List<Triple<Int, Int, Int>>, Int> {
        val blocks = mutableListOf<Triple<Int, Int, Int>>()
        var position = at + 4

        do {
            val first = bytes[position].toInt() and 0xFF
            val length = ((bytes[position + 1].toInt() and 0xFF) shl 16) or ((bytes[position + 2].toInt() and 0xFF) shl 8) or (bytes[position + 3].toInt() and 0xFF)
            blocks += Triple(position, first, length)
            position += 4 + length
        } while (first and 0x80 == 0)

        return blocks to position
    }

    @Test
    fun `a FLAC keeps its stream and seek table, and its other blocks become padding`() {
        val original = fixture("tagged.flac").readBytes()
        val bytes = cleaned(fixture("tagged.flac"))
        val (before, frames) = flacBlocks(original)
        val (after, framesAfter) = flacBlocks(bytes)

        assertSaysNothing("tagged.flac", bytes)
        // Its cue sheet's catalog number, and the WAV chunks the flac tool kept, its editor's leftovers among them.
        listOf("1234567890123", "Test recorder", "Test project", "Test marker", "Test leftover", "libFLAC").forEach { assertFalse(it, bytes.says(it)) }
        assertEquals(original.size, bytes.size)
        assertEquals(frames, framesAfter)
        assertEquals(before.map { it.first to it.third }, after.map { it.first to it.third })
        // Every block keeps whether it's the last; those that said more than the stream are padding with nothing in it.
        assertEquals(listOf(0, 3, 1, 1, 1) + List(before.size - 6) { 1 } + 0x81, after.map { it.second })

        for ((start, first, length) in after) {
            val content = bytes.copyOfRange(start + 4, start + 4 + length)

            if (first == 0 || first == 3) {
                assertArrayEquals(original.copyOfRange(start + 4, start + 4 + length), content)
            } else {
                assertTrue(content.all { it == 0.toByte() })
            }
        }

        assertArrayEquals(original.copyOfRange(frames, original.size), bytes.copyOfRange(frames, bytes.size))
    }

    @Test
    fun `an ID3 tag before a FLAC says nothing, and tags after it stay out`() {
        val tag = fixture("tagged.mp3").readBytes().let { it.copyOfRange(0, id3End(it)) }
        val flac = fixture("tagged.flac").readBytes()
        val bytes = cleaned(file("tagged.flac", tag + flac + apeTag() + id3v1Tags()))

        assertSaysNothing("tagged.flac", bytes)
        assertEquals(tag.size + flac.size, bytes.size)
        assertTrue(bytes.copyOfRange(10, tag.size).all { it == 0.toByte() })
        assertArrayEquals(cleaned(fixture("tagged.flac")), bytes.copyOfRange(tag.size, bytes.size))
    }

    @Test
    fun `a FLAC that can't be read through doesn't go`() {
        val flac = fixture("tagged.flac").readBytes()
        val (blocks, frames) = flacBlocks(flac)
        // A block of the one type no block can have.
        val invalid = flac.copyOf().also { it[blocks[2].first] = 127 }

        val broken = mapOf(
            "blocks-cut.flac" to flac.copyOfRange(0, blocks[3].first + 10),
            // Cut short in its one frame: no whole frame is left to play.
            "frame-cut.flac" to flac.copyOfRange(0, flac.size - 20),
            "after.flac" to flac + text("Test leftover"),
            "invalid.flac" to invalid,
            "no-frames.flac" to flac.copyOfRange(0, frames),
            "no-streaminfo.flac" to text("fLaC") + flac.copyOfRange(blocks[1].first, flac.size),
        )

        for ((name, bytes) in broken) {
            assertNull(name, OutgoingMedia.of(file(name, bytes)))
        }
    }

    /** Where a FLAC's frames start, from `at`: a frame header's sync code, and its checksum as its last byte, whatever its length. */
    private fun flacFrameStarts(bytes: ByteArray, at: Int): List<Int> = (at until bytes.size - 8).filter { start ->
        bytes[start] == 0xFF.toByte() && bytes[start + 1].toInt() and 0xFE == 0xF8 &&
            (5..8).any { length -> flacHeaderChecksum(bytes.copyOfRange(start, start + length)) == bytes[start + length].toInt() and 0xFF }
    }

    @Test
    fun `a FLAC cut short goes clean, without the frame or tag its file ends in`() {
        val original = fixture("frames.flac").readBytes()
        val whole = cleaned(fixture("frames.flac"))
        val starts = flacFrameStarts(original, flacBlocks(original).second)
        val last = starts.last()
        assertEquals(7, starts.size)

        // Cut in its last frame, in that frame's header, in the frame before it, and right after that one.
        val cuts = mapOf(original.size - 20 to last, last + 3 to last, last - 100 to starts[starts.size - 2], last to last)

        for ((cut, end) in cuts) {
            val bytes = cleaned(file("cut-$cut.flac", original.copyOfRange(0, cut)))

            assertSaysNothing("frames.flac cut at $cut", bytes)
            assertArrayEquals("frames.flac cut at $cut", whole.copyOfRange(0, end), bytes)
        }

        // Cut in tags after its frames, an APE tag's and ID3v1's: it goes whole, without them.
        val tagged = original + apeTag() + id3v1Tags()

        for (cut in listOf(original.size + 40, tagged.size - 30)) {
            assertArrayEquals("tagged cut at $cut", whole, cleaned(file("tags-$cut.flac", tagged.copyOfRange(0, cut))))
        }

        // Cut in its first frame, it has no whole frame to play.
        assertNull(OutgoingMedia.of(file("first-cut.flac", original.copyOfRange(0, starts.first() + 100))))
    }

    /** Ogg's checksum of a page: CRC-32 with the polynomial 0x04C11DB7, from zero, unreflected, its own field read as zeros. */
    private fun oggChecksum(page: ByteArray): Int {
        var crc = 0

        page.forEachIndexed { index, byte ->
            crc = crc xor ((if (index in 22 until 26) 0 else byte.toInt() and 0xFF) shl 24)

            repeat(8) {
                crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1
            }
        }

        return crc
    }

    /** An Ogg file's pages, each one's bytes. */
    private fun oggPages(bytes: ByteArray): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()
        var position = 0

        while (position < bytes.size) {
            val segments = bytes[position + 26].toInt() and 0xFF
            val body = (0 until segments).sumOf { bytes[position + 27 + it].toInt() and 0xFF }
            pages += bytes.copyOfRange(position, position + 27 + segments + body)
            position += 27 + segments + body
        }

        return pages
    }

    /** Each stream's packets, by its number, as its pages' segments make them. */
    private fun oggPackets(bytes: ByteArray): Map<Int, List<ByteArray>> {
        val packets = LinkedHashMap<Int, MutableList<ByteArray>>()
        val unfinished = HashMap<Int, ByteArrayOutputStream>()

        for (page in oggPages(bytes)) {
            val serial = ByteBuffer.wrap(page, 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val segments = page[26].toInt() and 0xFF
            var at = 27 + segments

            for (index in 0 until segments) {
                val length = page[27 + index].toInt() and 0xFF
                val packet = unfinished.getOrPut(serial) { ByteArrayOutputStream() }
                packet.write(page, at, length)
                at += length

                if (length < 255) {
                    packets.getOrPut(serial) { mutableListOf() } += packet.toByteArray()
                    unfinished.remove(serial)
                }
            }
        }

        return packets
    }

    /** One Ogg stream of `packets`, at most `segments` segments a page, each page's checksum as Ogg reckons it. */
    private fun oggStream(serial: Int, packets: List<ByteArray>, segments: Int): ByteArray {
        val laced = packets.flatMap { packet -> List(packet.size / 255) { 255 } + packet.size % 255 }
        val data = packets.fold(ByteArray(0)) { all, packet -> all + packet }
        val output = ByteArrayOutputStream()
        var used = 0
        var continued = false

        laced.chunked(segments).forEachIndexed { sequence, lacing ->
            val body = data.copyOfRange(used, used + lacing.sum())
            val flags = (if (continued) 1 else 0) or (if (sequence == 0) 2 else 0) or (if (used + body.size == data.size) 4 else 0)
            val header = text("OggS") + byteArrayOf(0, flags.toByte()) + ByteArray(8) + littleEndian(serial) + littleEndian(sequence) + ByteArray(4)
            val page = header + byteArrayOf(lacing.size.toByte()) + lacing.map { it.toByte() } + body
            littleEndian(oggChecksum(page)).copyInto(page, 22)
            output.write(page)
            used += body.size
            continued = lacing.last() == 255
        }

        return output.toByteArray()
    }

    @Test
    fun `Ogg sounds go without their comments, every other packet as it was`() {
        // Each comment header's own start, and how long what follows it says: no vendor, no tags, and Vorbis's last bit.
        val emptied = mapOf(
            "tagged.ogg" to text("\u0003vorbis") + ByteArray(8) + byteArrayOf(1),
            "tagged.opus" to text("OpusTags") + ByteArray(8),
            "tagged-speex.ogg" to ByteArray(8),
        )

        for (name in listOf("tagged.ogg", "tagged.opus", "tagged-speex.ogg", "tagged.oga")) {
            val original = fixture(name).readBytes()
            val bytes = cleaned(fixture(name))

            assertSaysNothing(name, bytes)
            listOf("Lavf", "Lavc", "libFLAC").forEach { assertFalse("$name says $it", bytes.says(it)) }
            assertEquals(name, original.size, bytes.size)
            assertEquals(oggPages(original).map { it.size }, oggPages(bytes).map { it.size })
            oggPages(bytes).forEach { page -> assertEquals(name, oggChecksum(page), ByteBuffer.wrap(page, 22, 4).order(ByteOrder.LITTLE_ENDIAN).int) }

            val before = oggPackets(original).values.single()
            val after = oggPackets(bytes).values.single()
            assertEquals(before.map { it.size }, after.map { it.size })

            before.zip(after).forEachIndexed { index, (packet, going) ->
                val expected = when {
                    // FLAC's blocks: its comments stay comments, with none in them, their vendor all zeros and the rest of the block.
                    name == "tagged.oga" && index == 1 -> packet.copyOfRange(0, 4) + littleEndian(packet.size - 12) + ByteArray(packet.size - 8)
                    // Its picture, and its padding, are padding with nothing in it.
                    name == "tagged.oga" && index in 2..3 -> byteArrayOf(((packet[0].toInt() and 0x80) or 1).toByte()) + packet.copyOfRange(1, 4) + ByteArray(packet.size - 4)
                    index == 1 -> emptied.getValue(name).let { it + ByteArray(packet.size - it.size) }
                    else -> packet
                }

                assertArrayEquals("$name's packet $index", expected, going)
            }
        }
    }

    @Test
    fun `a comment header over several pages has each page's checksum written again`() {
        val cover = text("METADATA_BLOCK_PICTURE=") + ByteArray(1500) { (it % 26 + 65).toByte() }
        val comments = text("OpusTags") + littleEndian(12) + text("Test encoder") + littleEndian(2) +
            littleEndian(16) + text("title=Test title") + littleEndian(cover.size) + cover
        val head = text("OpusHead") + byteArrayOf(1, 1, 0x38, 1) + littleEndian(48_000) + byteArrayOf(0, 0, 0)
        val sound = ByteArray(40) { (it * 7 + 1).toByte() }
        // Two segments a page: the comments over the first four pages, the sound alone on the fifth.
        val ogg = oggStream(1234, listOf(head, comments, sound), segments = 2)
        assertEquals(5, oggPages(ogg).size)

        val bytes = cleaned(file("long-comments.opus", ogg))

        assertFalse(bytes.says("Test title"))
        assertFalse(bytes.says("METADATA_BLOCK_PICTURE"))
        assertEquals(ogg.size, bytes.size)
        oggPages(bytes).forEach { page -> assertEquals(oggChecksum(page), ByteBuffer.wrap(page, 22, 4).order(ByteOrder.LITTLE_ENDIAN).int) }
        assertEquals(listOf(head, text("OpusTags") + ByteArray(comments.size - 8), sound).map { it.toList() }, oggPackets(bytes).getValue(1234).map { it.toList() })
        // The page with the sound alone is just as it was.
        assertArrayEquals(oggPages(ogg).last(), oggPages(bytes).last())
    }

    @Test
    fun `an Ogg that can't be read through doesn't go`() {
        val opus = fixture("tagged.opus").readBytes()
        val head = text("OpusHead") + byteArrayOf(1, 1, 0x38, 1) + littleEndian(48_000) + byteArrayOf(0, 0, 0)
        val tags = text("OpusTags") + littleEndian(0) + littleEndian(0)

        val broken = mapOf(
            // Cut short in its comments, and in its one page of sound: no whole page of sound is left to play.
            "tags-cut.opus" to opus.copyOfRange(0, 300),
            "cut.opus" to opus.copyOfRange(0, opus.size - 30),
            "after.opus" to opus + text("Test leftover"),
            // A stream of a kind Orbit doesn't know, as Ogg Skeleton's.
            "skeleton.ogg" to oggStream(1, listOf(text("fishead\u0000") + ByteArray(56), ByteArray(10)), segments = 4) + opus,
            // Comments that aren't Opus's, and a stream whose comments never come.
            "not-tags.opus" to oggStream(1, listOf(head, text("OpusTagz") + ByteArray(8), ByteArray(20)), segments = 4),
            "no-tags.opus" to oggStream(1, listOf(head), segments = 4),
            // A page of a stream that never started.
            "orphan.opus" to opus + oggStream(9, listOf(head, tags), segments = 4).let { it.copyOf().also { page -> page[5] = 0 } },
        )

        for ((name, bytes) in broken) {
            assertNull(name, OutgoingMedia.of(file(name, bytes)))
        }
    }

    /**
     * An Ogg fixture's stream laid out again, `segments` segments a page,
     * as encoders that write smaller pages do, with its last packet twice
     * so that even FLAC's one frame of sound has a page after it.
     */
    private fun repaged(name: String, segments: Int): ByteArray {
        val original = fixture(name).readBytes()
        val packets = oggPackets(original).values.single()
        return oggStream(ByteBuffer.wrap(original, 14, 4).order(ByteOrder.LITTLE_ENDIAN).int, packets + packets.last(), segments)
    }

    /** Where each of an Ogg file's pages starts, and where the last one ends. */
    private fun pageStarts(bytes: ByteArray): List<Int> = oggPages(bytes).runningFold(0) { at, page -> at + page.size }

    @Test
    fun `an Ogg cut short goes clean, without the page its file ends in`() {
        for (name in listOf("tagged.ogg", "tagged.opus", "tagged-speex.ogg", "tagged.oga")) {
            val ogg = repaged(name, segments = 2)
            val whole = cleaned(file("whole-$name", ogg))
            val starts = pageStarts(ogg)
            val last = starts[starts.size - 2]

            // Cut in its last page's sound, in its lacing, in its header, and right after the page before it.
            for (cut in listOf(ogg.size - 3, last + 28, last + 10, last)) {
                val bytes = cleaned(file("cut-$cut-$name", ogg.copyOfRange(0, cut)))

                assertSaysNothing("$name cut at $cut", bytes)
                assertArrayEquals("$name cut at $cut", whole.copyOfRange(0, last), bytes)
                oggPages(bytes).forEach { page -> assertEquals(name, oggChecksum(page), ByteBuffer.wrap(page, 22, 4).order(ByteOrder.LITTLE_ENDIAN).int) }
            }
        }

        // FLAC's one frame over two pages, cut in the second: the page before holds only the start of it, and no sound is whole.
        val oga = fixture("tagged.oga").readBytes().let { original -> oggStream(1, oggPackets(original).values.single(), segments = 1) }
        assertNull(OutgoingMedia.of(file("half-a-frame.oga", oga.copyOfRange(0, oga.size - 3))))

        // A last whole page whose checksum isn't its own was damaged, not cut short.
        val opus = repaged("tagged.opus", segments = 2)
        val starts = pageStarts(opus)
        val damaged = opus.copyOfRange(0, opus.size - 3).also { it[starts[starts.size - 3] + 22] = (it[starts[starts.size - 3] + 22] + 1).toByte() }
        assertNull(OutgoingMedia.of(file("damaged.opus", damaged)))
    }

    /** A WAV's chunks: each one's start, type and size, as its header says it. */
    private fun waveChunks(bytes: ByteArray): List<Triple<Int, String, Long>> {
        val chunks = mutableListOf<Triple<Int, String, Long>>()
        var position = 12

        while (position + 8 <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            chunks += Triple(position, String(bytes, position, 4, Charsets.ISO_8859_1), size)
            position += 8 + if (size == 0xFFFFFFFFL) bytes.size else (size + size % 2).toInt()
        }

        return chunks
    }

    @Test
    fun `a WAV keeps the chunks that make its sound, and every other one is JUNK with nothing in it`() {
        val original = fixture("tagged.wav").readBytes()
        val bytes = cleaned(fixture("tagged.wav"))
        val before = waveChunks(original)
        val after = waveChunks(bytes)

        assertSaysNothing("tagged.wav", bytes)
        // Broadcast Wave's originator and history, iXML's project, XMP's editor, a cue's label, an editor's leftovers, and when its peaks were reckoned.
        listOf("Test recorder", "Test reference", "Test history", "Test project", "Test editor", "Test marker", "Test leftover", "2026:10", "Lavf").forEach { assertFalse(it, bytes.says(it)) }
        assertEquals(original.size, bytes.size)
        assertEquals(before.map { it.first to it.third }, after.map { it.first to it.third })
        assertEquals(listOf("fmt ", "JUNK", "JUNK", "cue ", "smpl", "inst", "JUNK", "JUNK", "JUNK", "JUNK", "JUNK", "data", "JUNK"), after.map { it.second })

        for ((start, type, size) in after) {
            val content = bytes.copyOfRange(start + 8, start + 8 + size.toInt())
            val was = original.copyOfRange(start + 8, start + 8 + size.toInt())

            when (type) {
                "JUNK" -> assertTrue(content.all { it == 0.toByte() })
                // A sampler's maker and model, and its SMPTE time, go; its loop stays.
                "smpl" -> assertArrayEquals(ByteArray(8) + was.copyOfRange(8, 20) + ByteArray(8) + was.copyOfRange(28, was.size), content)
                else -> assertArrayEquals(type, was, content)
            }
        }
    }

    @Test
    fun `an RF64 WAV goes the same way, its samples' size from its ds64 chunk`() {
        val original = fixture("tagged-rf64.wav").readBytes()
        val bytes = cleaned(fixture("tagged-rf64.wav"))

        assertSaysNothing("tagged-rf64.wav", bytes)
        assertEquals(original.size, bytes.size)
        assertEquals(listOf("ds64", "fmt ", "JUNK", "data"), waveChunks(bytes).map { it.second })
        val samples = waveChunks(bytes).last().first + 8
        assertArrayEquals(original.copyOfRange(samples, original.size), bytes.copyOfRange(samples, bytes.size))
    }

    @Test
    fun `a WAV that can't be read through doesn't go`() {
        val wav = fixture("tagged.wav").readBytes()
        val chunks = waveChunks(wav)
        val samples = chunks.single { it.second == "data" }
        val rf64 = fixture("tagged-rf64.wav").readBytes()

        val broken = mapOf(
            // Cut short in its first sample, and before its samples: none is whole.
            "first-sample-cut.wav" to wav.copyOfRange(0, samples.first + 8 + 1),
            "after.wav" to wav + text("Test leftover"),
            "no-samples.wav" to wav.copyOfRange(0, samples.first),
            // A chunk whose name isn't one, and an RF64 whose sizes aren't said first.
            "unnamed.wav" to wav.copyOf().also { it[chunks[2].first] = 0x01 },
            "no-ds64.wav" to rf64.copyOfRange(0, 12) + rf64.copyOfRange(waveChunks(rf64)[1].first, rf64.size),
        )

        for ((name, bytes) in broken) {
            assertNull(name, OutgoingMedia.of(file(name, bytes)))
        }
    }

    /** The four bytes from `at`, as RIFF writes its sizes. */
    private fun ByteArray.sizeAt(at: Int): Long = ByteBuffer.wrap(this, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    @Test
    fun `a WAV cut short goes clean, as far as its last whole sample, and says where it ends now`() {
        val original = fixture("tagged.wav").readBytes()
        val whole = cleaned(fixture("tagged.wav"))
        val chunks = waveChunks(original)
        val samples = chunks.single { it.second == "data" }
        val after = chunks.last()
        assertEquals("levl", after.second)

        // Cut in its samples, partway through a sample of two bytes: it goes as far as the one before, and the chunk after them was cut away.
        // Cut in the chunk after its samples, in that chunk's header, and right after the samples: they go whole.
        val cuts = listOf(
            Triple(samples.first + 8 + 1001, samples.first + 8 + 1000, 1000L),
            Triple(after.first + 50, after.first, samples.third),
            Triple(after.first + 5, after.first, samples.third),
            Triple(after.first, after.first, samples.third),
        )

        for ((cut, end, size) in cuts) {
            val bytes = cleaned(file("cut-$cut.wav", original.copyOfRange(0, cut)))

            assertSaysNothing("tagged.wav cut at $cut", bytes)
            assertEquals(end, bytes.size)
            // RIFF's size and the samples' say where it ends now; everything else goes as the whole file does.
            assertEquals(end - 8L, bytes.sizeAt(4))
            assertEquals(size, bytes.sizeAt(samples.first + 4))
            val expected = whole.copyOfRange(0, end).also { littleEndian(end - 8).copyInto(it, 4); littleEndian(size.toInt()).copyInto(it, samples.first + 4) }
            assertArrayEquals("tagged.wav cut at $cut", expected, bytes)
        }

        // An RF64 WAV says both in its ds64 chunk, after its own header: the RIFF's size, then the samples'.
        val large = fixture("tagged-rf64.wav").readBytes()
        val data = waveChunks(large).last()
        val bytes = cleaned(file("cut-rf64.wav", large.copyOfRange(0, data.first + 8 + 501)))
        val sizes = ByteBuffer.wrap(bytes, 20, 16).order(ByteOrder.LITTLE_ENDIAN)

        assertSaysNothing("tagged-rf64.wav cut", bytes)
        assertEquals(data.first + 8 + 500, bytes.size)
        assertEquals(bytes.size - 8L, sizes.long)
        assertEquals(500L, sizes.long)
        val going = cleaned(fixture("tagged-rf64.wav"))
        assertArrayEquals(going.copyOfRange(0, 20), bytes.copyOfRange(0, 20))
        assertArrayEquals(going.copyOfRange(36, bytes.size), bytes.copyOfRange(36, bytes.size))
    }

    @Test(timeout = 20_000)
    fun `a file made to wear the readers out doesn't go, and doesn't keep them long`() {
        val mp3 = fixture("tagged.mp3").readBytes()
        // Two hundred thousand empty ID3 tags in a row, more than any file has, then the sound.
        val emptyTags = ByteArray(200_000 * 10).also { tags -> for (at in tags.indices step 10) { text("ID3").copyInto(tags, at); tags[at + 3] = 4 } }
        assertNull(OutgoingMedia.of(file("tags.mp3", emptyTags + mp3Frames())))

        // An RF64 WAV whose samples' size, in its ds64 chunk, is less than nothing.
        val rf64 = fixture("tagged-rf64.wav").readBytes().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(12 + 8 + 8, -8) }
        assertNull(OutgoingMedia.of(file("negative.wav", rf64)))

        // A FLAC whose frames are headers that check out, one after another, with no frame's checksum at the end, and whose stream details don't say how large frames get.
        val flac = fixture("tagged.flac").readBytes().also { it.fill(0, 15, 18) }
        val (_, frames) = flacBlocks(flac)
        val header = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x11, 0x08, 0x00)
        val headers = (1..60_000).fold(ByteArrayOutputStream()) { all, _ -> all.apply { write(header); write(flacHeaderChecksum(header)) } }.toByteArray()
        assertNull(OutgoingMedia.of(file("headers.flac", flac.copyOfRange(0, frames) + headers + byteArrayOf(1, 2))))
    }

    /** FLAC's CRC-8 of a frame header, x⁸ + x² + x + 1. */
    private fun flacHeaderChecksum(header: ByteArray): Int = header.fold(0) { crc, byte ->
        (0 until 8).fold(crc xor (byte.toInt() and 0xFF)) { value, _ -> if (value and 0x80 != 0) (value shl 1 xor 0x07) and 0xFF else (value shl 1) and 0xFF }
    }

    @Test
    fun `a WAV goes with zeros after its chunks, and with a chunk whose padding byte its writer left out`() {
        val wav = fixture("tagged.wav").readBytes()
        val zeros = cleaned(file("zeros.wav", wav + ByteArray(40)))
        assertArrayEquals(cleaned(fixture("tagged.wav")), zeros.copyOfRange(0, wav.size))

        // An odd-sized LIST chunk with no byte of padding after it, before the format.
        val list = text("LIST") + littleEndian(15) + text("INFOINAM") + littleEndian(3) + text("Tes")
        val bytes = cleaned(file("unpadded.wav", wav.copyOfRange(0, 12) + list + wav.copyOfRange(12, wav.size)))
        assertFalse(bytes.says("INFOINAM"))
        assertEquals("JUNK", String(bytes, 12, 4, Charsets.ISO_8859_1))
        // The chunks after it, the format first, go as they do without it.
        assertArrayEquals(cleaned(fixture("tagged.wav")).copyOfRange(12, wav.size), bytes.copyOfRange(12 + list.size, bytes.size))
    }

    @Test
    fun `an ID3 tag before an Ogg or a WAV makes it one that can't be read through`() {
        val tag = fixture("tagged.mp3").readBytes().let { it.copyOfRange(0, id3End(it)) }

        for (name in listOf("tagged.opus", "tagged.wav")) {
            assertNull(name, OutgoingMedia.of(file(name, tag + fixture(name).readBytes())))
        }
    }

    @Test
    fun `an MP3 or AAC that can't be read through doesn't go`() {
        val mp3 = fixture("tagged.mp3").readBytes()
        val frames = id3End(mp3)
        val aac = fixture("tagged.aac").readBytes()
        // An APE tag whose footer says it's longer than the whole file.
        val longTag = apeTag().also { littleEndian(1_000_000).copyInto(it, it.size - 32 + 12) }

        val broken = mapOf(
            // Cut short in its tag, and in its first frame, and in AAC's: no frame is whole.
            "tag-cut.mp3" to mp3.copyOfRange(0, frames / 2),
            "first-frame-cut.mp3" to mp3.copyOfRange(0, frames + 100),
            "first-frame-cut.aac" to aac.copyOfRange(0, id3End(aac) + 100),
            // Something that's neither a frame nor a tag, after the tag and between two frames.
            "after-tag.mp3" to mp3.copyOfRange(0, frames) + text("Test leftover") + mp3.copyOfRange(frames, mp3.size),
            "between.mp3" to mp3Frames() + text("Test leftover") + mp3Frames(),
            // A frame cut short with more frames after it, as a copy that skipped a part: it isn't the file's end that was cut.
            "frame-cut-between.mp3" to mp3Frames().let { it.copyOfRange(0, it.size - 50) + it },
            "frame-cut-between.aac" to aac.copyOfRange(0, String(aac, Charsets.ISO_8859_1).indexOf("APETAGEX") - 10) + aac.copyOfRange(id3End(aac), aac.size),
            "long-tag.mp3" to mp3Frames() + longTag,
            // A tag with no sound after it.
            "only-a-tag.mp3" to mp3.copyOfRange(0, frames),
        )

        for ((name, bytes) in broken) {
            assertNull(name, OutgoingMedia.of(file(name, bytes)))
        }
    }
}
