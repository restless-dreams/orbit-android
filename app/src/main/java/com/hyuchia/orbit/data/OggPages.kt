package com.hyuchia.orbit.data

/**
 * Ogg sounds, page by page: Vorbis, Opus, FLAC and Speex, and Theora's
 * video should an Ogg sound carry one, in one stream or in several, side
 * by side or one after another. Each stream's comment header, where its
 * tags are, a cover among them, is emptied where it is: it keeps its kind's
 * own start, then says it has no tags and no vendor, and holds nothing
 * after that but zeros, which Opus calls padding and Vorbis's readers never
 * reach. FLAC's blocks go as in a FLAC file ([FlacBlocks]), but for its
 * comments, which FLAC in Ogg must have: they say none, and their vendor,
 * all zeros, takes in the rest, as libFLAC wants nothing after them. The pages
 * it's on get their checksums again; every other page stays as it is, and
 * tags after the last page stay out ([SoundTags]). A file cut short, as a
 * recording that stopped or a copy that didn't finish, goes as far as its
 * last whole page: the page or the tag the cut falls in stays out, as no
 * player can play or read it.
 */
internal object OggPages {
    /**
     * What to write over the file, and where it ends, or null when it can't
     * be read through: a stream of a kind Orbit doesn't know, header packets
     * that aren't what their kind's should be or don't come whole, or
     * anything but pages and tags in it.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val reading = ReadAhead(bytes)
        val end = SoundTags.trailingStart(reading, reading.size)
        val streams = HashMap<Long, Stream>()
        val patches = Patches()
        var position = 0L
        // Where the last page read starts, and how many packets of sound have ended on the pages read.
        var lastPage = -1L
        var sound = 0

        // The plan of a file cut short at `position`, in a page or a tag: as far as its last whole page, once every stream's headers are past and some sound is whole, and the last page is as its checksum says.
        fun cutShort(): Pair<List<Patch>, Long>? = (patches to position).takeIf { sound > 0 && streams.values.none { it.inHeaders } && checksOut(reading, lastPage, position) }

        while (position < end) {
            val header = reading.read(position, PAGE_HEADER)

            // Not a page: tags the file ends in after the pages, cut short, or else what can't be told.
            if (!header.startsWith(PAGE_START.take(header.size))) {
                return if (SoundTags.cutShortFrom(reading, position)) cutShort() else null
            }

            // Fewer bytes than a page's header, which start as one does: the file ends in it.
            if (header.size < PAGE_HEADER) {
                return cutShort()
            }

            val lacing = reading.read(position + PAGE_HEADER, header.byteAt(26))
            val pageEnd = position + PAGE_HEADER + lacing.size + lacing.sumOf { it.toInt() and 0xFF }

            // A page the file ends in, in its lacing or before its last packet ends.
            if (lacing.size < header.byteAt(26) || pageEnd > end) {
                return cutShort()
            }

            // A stream's first page starts it again, as the next of a chain of streams that reuses its number does.
            val serial = header.littleEndian(14, 4)
            val stream = if (header.byteAt(5) and FIRST_PAGE != 0) Stream().also { streams[serial] = it } else streams[serial] ?: return null

            if (streams.size > MOST_STREAMS) {
                return null
            }

            if (stream.inHeaders) {
                val page = reading.read(position, (pageEnd - position).toInt())
                val going = stream.read(page, lacing, continued = header.byteAt(5) and CONTINUED != 0) ?: return null
                patches += differences(page, going, position)
            } else {
                // A segment shorter than 255 bytes ends a packet.
                sound += lacing.count { (it.toInt() and 0xFF) < 255 }
            }

            lastPage = position
            position = pageEnd
        }

        return (patches to end).takeIf { streams.isNotEmpty() && streams.values.none { it.inHeaders } }
    }

    /** Whether the page from `at` to `end` is as its checksum says: none was read when `at` is less than nothing. */
    private fun checksOut(bytes: Bytes, at: Long, end: Long): Boolean {
        if (at < 0) {
            return false
        }

        val page = bytes.read(at, (end - at).toInt())
        val said = page.littleEndian(CHECKSUM, 4)
        0.toByteArray().copyInto(page, CHECKSUM)
        return checksum(page).toLong() and 0xFFFFFFFFL == said
    }

    /**
     * How a Vorbis or Theora comment header starts as it goes, by how it
     * starts now (`start`, its first eight bytes at least): its own start,
     * then no vendor and no tags, and for Vorbis the bit that ends them; the
     * rest of it is zeros. Null for anything else. Matroska keeps these
     * headers in a track's codec setup ([MatroskaElements]).
     */
    fun emptiedCommentsStart(start: ByteArray): ByteArray? = when {
        start.startsWith(Codec.Vorbis.comments) -> Codec.Vorbis.comments.toByteArray(Charsets.ISO_8859_1) + ByteArray(8) + 1
        start.startsWith(Codec.Theora.comments) -> Codec.Theora.comments.toByteArray(Charsets.ISO_8859_1) + ByteArray(8)
        else -> null
    }

    /**
     * The patches that make `page`, at `at`, `going`: each run of bytes that
     * changed, as zeros when that's all it is now, so a cover's worth of
     * them takes no room.
     */
    private fun differences(page: ByteArray, going: ByteArray, at: Long): List<Patch> {
        val patches = Patches()
        var index = 0

        while (index < page.size) {
            if (page[index] == going[index]) {
                index += 1
                continue
            }

            val start = index
            val zeros = going[index] == 0.toByte()

            while (index < page.size && page[index] != going[index] && (going[index] == 0.toByte()) == zeros) {
                index += 1
            }

            patches += if (zeros) Patch(at + start, (index - start).toLong()) else Patch(at + start, (index - start).toLong(), going.copyOfRange(start, index))
        }

        return patches
    }

    /**
     * The kinds of stream Orbit reads, by how their first packet starts:
     * how their comment header starts, which it keeps, and how many header
     * packets they have. FLAC says its last header packet in the packet,
     * and Speex's comments have no start of their own.
     */
    private enum class Codec(val identification: String, val comments: String, val headers: Int) {
        Vorbis("\u0001vorbis", "\u0003vorbis", 3),
        Opus("OpusHead", "OpusTags", 2),
        Theora("\u0080theora", "\u0081theora", 3),
        Speex("Speex   ", "", 2),
        Flac("\u007FFLAC", "", 0),
    }

    /** A stream while its header packets come: which packet is being read, how far into it, and how it starts. */
    private class Stream {
        /** Whether its header packets are still coming: once they're past, its pages go as they are. */
        var inHeaders = true
            private set

        private var codec: Codec? = null
        private var packet = 0
        private var offset = 0

        /** The packet's first bytes, as far as they've come: they say what it is. */
        private var start = ByteArray(0)

        /** For a FLAC block: whether it stays as it is, as its first byte says. */
        private var keepsBlock = true

        /**
         * `page` with this stream's header packets on it as they go, and its
         * checksum again when that changed it, or null when they aren't what
         * they should be: `lacing` says where its packets end, and whether a
         * page `continued` the last one has to agree with where that ended.
         */
        fun read(page: ByteArray, lacing: ByteArray, continued: Boolean): ByteArray? {
            if (continued != (offset > 0)) {
                return null
            }

            val going = page.copyOf()
            var at = PAGE_HEADER + lacing.size

            for (segment in lacing) {
                val length = segment.toInt() and 0xFF

                if (inHeaders) {
                    for (index in at until at + length) {
                        val original = page.byteAt(index)

                        if (start.size < START_KEPT) {
                            start += original.toByte()
                        }

                        going[index] = (goingAs(offset, original) ?: return null).toByte()
                        offset += 1
                    }

                    // A packet goes on into the next segment, and the next page, while its segments are whole.
                    if (length < 255 && !ended()) {
                        return null
                    }
                }

                at += length
            }

            if (!going.contentEquals(page)) {
                0.toByteArray().copyInto(going, CHECKSUM)
                checksum(going).toByteArray().copyInto(going, CHECKSUM)
            }

            return going
        }

        /** The byte at `at` in the packet being read, as it goes: null when the packet isn't what it should be. */
        private fun goingAs(at: Int, original: Int): Int? {
            val codec = codec ?: return original

            return when {
                codec == Codec.Flac -> when {
                    at == 0 -> FlacBlocks.goingAs(original, keepsComments = true).also { keepsBlock = it == null } ?: original
                    at < 4 || keepsBlock -> original
                    // The comments' vendor, little-endian: as long as the block is but for the vendor's length and the comments' count.
                    at < 8 && isComments() -> ((start.unsigned(1, 3) - 8) shr (8 * (at - 4)) and 0xFF).toInt()
                    else -> 0
                }
                packet != 1 -> original
                at < codec.comments.length -> original.takeIf { it == codec.comments[at].code }
                // Vorbis's comments end with a bit set, right after they say there are none.
                codec == Codec.Vorbis && at == codec.comments.length + 8 -> 1
                else -> 0
            }
        }

        /** Whether the packet being read is FLAC's comments. */
        private fun isComments(): Boolean = start.byteAt(0) and 0x7F == FlacBlocks.VORBIS_COMMENT

        /** What a packet that ended says of the stream, as it ends: false when it isn't what it should be. */
        private fun ended(): Boolean {
            val length = offset
            val wasFirst = packet == 0

            if (wasFirst) {
                codec = Codec.entries.firstOrNull { start.startsWith(it.identification) } ?: return false
            }

            val codec = codec ?: return false

            val fits = when {
                // Speex's own extra headers say nothing Orbit knows of.
                codec == Codec.Speex && wasFirst -> start.size >= SPEEX_HEADER && start.littleEndian(SPEEX_EXTRA_HEADERS, 4) == 0L
                // The first packet holds the stream's details, a block that may be the last.
                codec == Codec.Flac && wasFirst -> start.size > FLAC_FIRST_BLOCK && start.startsWith("fLaC", at = FLAC_FIRST_BLOCK - 4)
                // Every other one is one block, as long as it says, and comments are at least their vendor's length and count.
                codec == Codec.Flac -> length >= 4 && start.unsigned(1, 3) + 4 == length.toLong() && (!isComments() || length >= 12)
                packet == 1 -> length >= codec.comments.length + if (codec == Codec.Vorbis) 9 else 8
                packet == 2 -> start.startsWith(if (codec == Codec.Vorbis) "\u0005vorbis" else "\u0082theora")
                else -> true
            }

            val lastBlock = codec == Codec.Flac && start.byteAt(if (wasFirst) FLAC_FIRST_BLOCK else 0) and 0x80 != 0

            if (lastBlock || (codec != Codec.Flac && packet + 1 == codec.headers)) {
                inHeaders = false
            }

            packet += 1
            offset = 0
            start = ByteArray(0)
            keepsBlock = true
            return fits
        }
    }

    /** Ogg's checksum of a page with its own field zeroed: CRC-32 with the polynomial 0x04C11DB7, from zero, unreflected. */
    private fun checksum(page: ByteArray): Int {
        var crc = 0

        for (byte in page) {
            crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF]
        }

        return crc
    }

    private val CRC_TABLE = IntArray(256) { index ->
        var crc = index shl 24

        repeat(8) {
            crc = if (crc and (1 shl 31) != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1
        }

        crc
    }

    /** A number as Ogg writes it, little-endian. */
    private fun Int.toByteArray(): ByteArray = byteArrayOf(toByte(), (this shr 8).toByte(), (this shr 16).toByte(), (this shr 24).toByte())

    /** The most streams a file may start, a long chain of them included: more were put there to wear the reader out. */
    private const val MOST_STREAMS = 10_000

    private const val PAGE_HEADER = 27

    /** How every page starts: its capture pattern, and the only version Ogg has. */
    private const val PAGE_START = "OggS\u0000"

    private const val CHECKSUM = 22
    private const val CONTINUED = 0x01
    private const val FIRST_PAGE = 0x02

    /** As much of a packet's start as tells what it is: a Speex header's whole. */
    private const val START_KEPT = 80

    private const val SPEEX_HEADER = 80
    private const val SPEEX_EXTRA_HEADERS = 68

    /** Where the stream's details start in FLAC's first packet, after its mapping's own header and `fLaC`. */
    private const val FLAC_FIRST_BLOCK = 13
}
