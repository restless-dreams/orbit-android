package com.hyuchia.orbit.data

/**
 * WAV sounds, chunk by chunk: RIFF's, and RF64's and BW64's, which carry
 * sounds past 4 GB. The chunks that make the sound stay as they are: its
 * format and samples, and the cue points, loops, instrument and tempo some
 * apps play it with, though a sampler's own details go from its loops.
 * Every other chunk, among them a Broadcast Wave's originator and when it
 * was recorded, LIST's tags and labels, iXML, XMP, an ID3 tag and a peak
 * envelope with when it was made, becomes a JUNK chunk of the same size
 * with nothing in it, so the samples stay where they are. Tags after the
 * chunks stay out ([SoundTags]). A file cut short, as a recording that
 * stopped or a copy that didn't finish, goes as far as its last whole
 * sample, or its last whole chunk after its samples, and its sizes say
 * where it ends now, so players don't look past it.
 */
internal object WaveChunks {
    /** Whether a file's first twelve bytes are a WAV's. */
    fun looksLikeOne(start: ByteArray): Boolean = (start.startsWith("RIFF") || start.startsWith("RF64") || start.startsWith("BW64")) && start.startsWith("WAVE", at = 8)

    /**
     * What to write over the file, and where it ends, or null when it can't
     * be read through: a chunk whose size can't be told, a chunk cut short
     * before the samples, or no format or whole sample.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val large = !bytes.read(0, 4).startsWith("RIFF")
        val end = SoundTags.trailingStart(bytes, bytes.size)
        val patches = Patches()
        var position = FIRST_CHUNK
        var largeData: Long? = null
        // Where RF64's ds64 chunk says its sizes, and how many bytes a sample takes on every channel together, as the format says.
        var largeSizes = 0L
        var blockAlign = 0L
        var format = false
        var samples = false
        var padded = false

        // Where RIFF says the file ends, in its header or RF64's ds64 chunk: a chunk cut short starts before it, as what's after it isn't RIFF's.
        fun riffEnd(): Long = 8 + if (large) bytes.read(largeSizes, 8).littleEndian(0, 8) else bytes.read(RIFF_SIZE, 4).littleEndian(0, 4)

        // The plan of a file that ends at `at`: RIFF's size, or RF64's, says so when it said more, as a file cut short's does.
        fun endingAt(at: Long): Pair<List<Patch>, Long> {
            if (riffEnd() > at) {
                patches += if (large) Patch(largeSizes, 8, littleEndianBytes(at - 8, 8)) else Patch(RIFF_SIZE, 4, littleEndianBytes(at - 8, 4))
            }

            return patches to at
        }

        while (position + CHUNK_HEADER <= end) {
            var header = bytes.read(position, CHUNK_HEADER)

            // Some writers leave out the byte of padding after a chunk of an odd size: the next chunk starts a byte sooner.
            if (!isChunkHeader(header) && padded && isChunkHeader(bytes.read(position - 1, CHUNK_HEADER))) {
                position -= 1
                header = bytes.read(position, CHUNK_HEADER)
            }

            if (!isChunkHeader(header)) {
                // Zeros after the chunks say nothing, and tags the file ends in after its samples, cut short, stay out; anything else can't be told.
                if (SoundTags.zerosUntil(bytes, position, end) == end) {
                    break
                }

                return if (format && samples && SoundTags.cutShortFrom(bytes, position)) endingAt(position) else null
            }

            val type = String(header, 0, 4, Charsets.ISO_8859_1)
            val said = header.littleEndian(4, 4)

            // RF64 says the sizes past 32 bits in its ds64 chunk, which comes first: only the samples' is read from there.
            val size = when {
                large && position == FIRST_CHUNK && type != "ds64" -> return null
                large && said == UNSAID && type == "data" -> largeData ?: return null
                large && said == UNSAID -> return null
                else -> said
            }

            val body = position + CHUNK_HEADER

            // Counted so that a size near 2⁶³ can't wrap around past the check.
            if (size < 0) {
                return null
            }

            // The chunk the file ends in, cut short: its samples go as far as their last whole sample, and a chunk after them stays out.
            if (size > end - body) {
                val whole = if (blockAlign > 0) (end - body) / blockAlign * blockAlign else 0

                return when {
                    type == "data" && whole > 0 -> {
                        // RF64's readers take the samples' size from its ds64 chunk, and others from the chunk's own.
                        if (large) {
                            patches += Patch(largeSizes + 8, 8, littleEndianBytes(whole, 8))
                        }

                        if (!large || said != UNSAID) {
                            patches += Patch(position + 4, 4, littleEndianBytes(whole, 4))
                        }

                        endingAt(body + whole)
                    }
                    format && samples && position < riffEnd() -> endingAt(position)
                    else -> null
                }
            }

            when (type) {
                "ds64" -> {
                    largeData = bytes.read(body + 8, 8).takeIf { large && size >= 16 && it.size == 8 }?.littleEndian(0, 8)?.takeIf { it >= 0 } ?: return null
                    largeSizes = body
                }
                "fmt " -> {
                    format = true
                    blockAlign = if (size >= 14) bytes.read(body + 12, 2).littleEndian(0, 2) else 0
                }
                "data" -> samples = true
                in PLAYING -> Unit
                "smpl" -> patches += samplerDetails(bytes, body, size)
                else -> patches += listOf(Patch(position, 4, JUNK), Patch(body, size))
            }

            // A chunk of an odd size is followed by a byte of padding.
            padded = size % 2 == 1L
            position = body + size + size % 2
        }

        // A last chunk's padding byte may be missing; anything else left over has to say nothing, or start a chunk or tags the file ends in after its samples.
        if (position < end && SoundTags.zerosUntil(bytes, position, end) < end) {
            val left = bytes.read(position, CHUNK_HEADER)
            val cutShort = left.size < CHUNK_HEADER && position < riffEnd() && (0 until minOf(4, left.size)).all { left.byteAt(it) in 0x20..0x7E }
            return if (format && samples && (cutShort || SoundTags.cutShortFrom(bytes, position))) endingAt(position) else null
        }

        return if (format && samples) endingAt(end) else null
    }

    /** Whether `header` starts a chunk: four letters, digits, spaces or signs, and a size. */
    private fun isChunkHeader(header: ByteArray): Boolean = header.size == CHUNK_HEADER && (0 until 4).all { header.byteAt(it) in 0x20..0x7E }

    /** `value` as RIFF writes its numbers, in `width` bytes, little-endian. */
    private fun littleEndianBytes(value: Long, width: Int): ByteArray = ByteArray(width) { (value shr (8 * it)).toByte() }

    /**
     * A sampler chunk's patches: its loops stay, and what says which
     * sampler made it, its SMPTE time and its own data after the loops go.
     */
    private fun samplerDetails(bytes: Bytes, body: Long, size: Long): List<Patch> {
        if (size < SAMPLER_LOOPS) {
            return listOf(Patch(body, size))
        }

        val counts = bytes.read(body + 28, 8)
        val ownData = body + SAMPLER_LOOPS + counts.littleEndian(0, 4) * SAMPLER_LOOP

        return listOfNotNull(
            Patch(body, 8),
            Patch(body + 20, 8),
            Patch(ownData, minOf(counts.littleEndian(4, 4), body + size - ownData)).takeIf { ownData < body + size },
        )
    }

    /** Where RIFF says how long the rest of the file is, after its `RIFF`. */
    private const val RIFF_SIZE = 4L

    private const val FIRST_CHUNK = 12L
    private const val CHUNK_HEADER = 8

    /** A size RF64 leaves to its ds64 chunk. */
    private const val UNSAID = 0xFFFFFFFFL

    /** The chunks that say how the sound plays, besides its format and samples: a compressed one's length, cue points and their playlist, an instrument, and a loop's tempo. */
    private val PLAYING = setOf("fact", "cue ", "plst", "inst", "acid")

    /** Where a sampler chunk's loops start, after its details, and how long each loop is. */
    private const val SAMPLER_LOOPS = 36L

    private const val SAMPLER_LOOP = 24L

    private val JUNK = "JUNK".toByteArray(Charsets.ISO_8859_1)
}
