package com.hyuchia.orbit.data

import java.io.IOException

/**
 * FLAC sounds: after `fLaC`, their metadata blocks, then their frames. The
 * blocks that say what the sound is stay (its stream's details, and where
 * to seek), and those that say more, its tags, pictures, cue sheet and
 * other apps' blocks (where the flac tool keeps a WAV's own chunks), become
 * padding of the same length with nothing in it, so the frames stay where
 * they are. An ID3v2 tag some apps put before `fLaC` says nothing any more,
 * and tags after the frames stay out ([SoundTags]). The frames come one
 * right after another: a file with anything between them, which players
 * skip, can't be read through. A file cut short, as a
 * recording that stopped or a copy that didn't finish, goes as far as its
 * last whole frame: the frame or the tag the cut falls in stays out, as no
 * player can play or read it. FLAC in Ogg keeps its blocks in packets, and
 * goes the same way ([OggPages]).
 */
internal object FlacBlocks {
    /**
     * What to write over the file, and where it ends, or null when it can't
     * be read through: a block cut short, frames that don't follow the
     * blocks, or no whole frame, as their checksums say.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val patches = Patches()
        var position = 0L

        while (true) {
            val tag = SoundTags.id3Length(bytes, position) ?: break
            patches += SoundTags.emptiedId3(position, tag)
            position = SoundTags.zerosUntil(bytes, position + tag, bytes.size)
        }

        if (!bytes.read(position, 4).startsWith("fLaC")) {
            return null
        }

        position += 4
        val blocks = position
        var largestFrame = 0L
        var last = false

        while (!last) {
            val header = bytes.read(position, 4)

            if (header.size < 4) {
                return null
            }

            val length = header.unsigned(1, 3)
            val first = header.byteAt(0)
            last = first and LAST != 0

            // The stream's details come first, and only first.
            if (position + 4 + length > bytes.size || (first and 0x7F == STREAMINFO) != (position == blocks)) {
                return null
            }

            if (first and 0x7F == STREAMINFO) {
                largestFrame = bytes.read(position + 4 + 7, 3).unsigned(0, 3)
            }

            goingAs(first)?.let { now ->
                if (now != first) {
                    patches += Patch(position, 1, byteArrayOf(now.toByte()))
                }

                patches += Patch(position + 4, length)
            }

            position += 4 + length
        }

        val end = SoundTags.trailingStart(bytes, bytes.size)

        if (!startsFrame(bytes.read(position, 2), 0)) {
            return null
        }

        val framesEnd = if (endsWithWholeFrame(bytes, position, end, largestFrame)) end else cutShortFramesEnd(bytes, position, end, largestFrame) ?: return null

        // Frames alone, one after another: anything between them, which players skip, could say anything.
        return (patches to framesEnd).takeIf { onlyFrames(bytes, position, framesEnd) }
    }

    /**
     * Whether what's from `frames` to `end` is FLAC's frames alone, each
     * right after the one before: each starts with a header its checksum
     * agrees with, and ends where the checksum of all of it comes to zero,
     * right before the next one's header or `end`. Bytes between frames,
     * which a player skips as it looks for the next one, break that, so a
     * file with any can't be read through; but zeros, which say nothing and
     * leave the checksum as it was. Read once, in one pass a chunk at a time,
     * the checksum going on from frame to frame (it's zero where each
     * starts); a frame goes on no further than the largest FLAC makes.
     */
    private fun onlyFrames(bytes: Bytes, frames: Long, end: Long): Boolean {
        if (!frameAt(bytes, frames)) {
            return false
        }

        var start = frames
        var crc = 0
        var position = frames

        while (position < end) {
            val chunk = bytes.read(position, minOf(FRAMES_CHUNK.toLong(), end - position).toInt())

            if (chunk.isEmpty()) {
                return false
            }

            for (index in chunk.indices) {
                crc = ((crc shl 8) xor CRC16_TABLE[(crc shr 8) xor chunk.byteAt(index)]) and 0xFFFF
                val next = position + index + 1

                if (crc == 0 && next - start >= SMALLEST_FRAME && (next == end || frameAt(chunk, index + 1, bytes, next))) {
                    start = next
                } else if (next - start > LARGEST_FRAME_SOUGHT) {
                    return false
                }
            }

            position += chunk.size
        }

        return start == end
    }

    /** Whether a frame starts at `at`: its sync code, and a header its checksum agrees with. */
    private fun frameAt(bytes: Bytes, at: Long): Boolean {
        val header = bytes.read(at, LONGEST_HEADER)
        return startsFrame(header, 0) && headerAgrees(header, 0)
    }

    /** Whether a frame starts at `at` in the file, which is `index` in `chunk`: read from the chunk when its header is all there. */
    private fun frameAt(chunk: ByteArray, index: Int, bytes: Bytes, at: Long): Boolean =
        if (index + LONGEST_HEADER <= chunk.size) startsFrame(chunk, index) && headerAgrees(chunk, index) else frameAt(bytes, at)

    /**
     * What a metadata block that starts with `first` becomes as it goes:
     * null when it stays as it is (its stream's details, where to seek), or
     * else the first byte it starts with now, padding's, or its own for a
     * VORBIS_COMMENT block that `keepsComments`, as FLAC in Ogg needs one
     * ([OggPages] empties it). Either way, nothing after its header says
     * anything. Throws for a type no block can have.
     */
    fun goingAs(first: Int, keepsComments: Boolean = false): Int? = when (first and 0x7F) {
        STREAMINFO, SEEKTABLE -> null
        INVALID -> throw IOException("A FLAC block of a type no block can have")
        VORBIS_COMMENT -> if (keepsComments) first else (first and LAST) or PADDING
        else -> (first and LAST) or PADDING
    }

    /** Whether a frame starts at `at`: fourteen bits of sync, then a zero. */
    private fun startsFrame(bytes: ByteArray, at: Int): Boolean = at + 2 <= bytes.size && bytes.byteAt(at) == 0xFF && bytes.byteAt(at + 1) and 0xFE == 0xF8

    /**
     * Whether the frames from `frames` end in a whole one at `end`: the last
     * frame starts with a header its checksum agrees with, at most
     * `largestFrame` (the stream's details say it, 0 when they don't)
     * before `end`, and the checksum `end` ends with is the whole frame's.
     * A real file's last frame is the first such header from the end, or
     * nearly: past a few that aren't it, the file was made to wear the
     * search out, and isn't read through.
     */
    private fun endsWithWholeFrame(bytes: Bytes, frames: Long, end: Long, largestFrame: Long): Boolean {
        val reach = minOf(end - frames, if (largestFrame > 0) minOf(largestFrame, LARGEST_FRAME_SOUGHT) else LARGEST_FRAME_SOUGHT).toInt()

        if (reach < SMALLEST_FRAME) {
            return false
        }

        val tail = bytes.read(end - reach, reach)
        val sum = tail.unsigned(reach - 2, 2).toInt()
        var headers = 0

        for (start in reach - SMALLEST_FRAME downTo 0) {
            if (!startsFrame(tail, start) || !headerAgrees(tail, start)) {
                continue
            }

            if (crc16(tail, start, reach - 2) == sum) {
                return true
            }

            headers += 1

            if (headers == HEADERS_SOUGHT) {
                return false
            }
        }

        return false
    }

    /**
     * Where the last whole frame of those from `frames` ends, in a file cut
     * short before `end`: right before the frame the cut falls in, or the
     * tags after the frames it does. That frame starts with a header its
     * checksum agrees with, or as much of one as the file has, after a
     * whole one: a header that agrees, and as far as where the checksum of
     * all of it, its own included, comes to zero. They're sought from the
     * end, as in [endsWithWholeFrame], at most two of the largest frames
     * back. Null when no frame is whole.
     */
    private fun cutShortFramesEnd(bytes: Bytes, frames: Long, end: Long, largestFrame: Long): Long? {
        val largest = if (largestFrame > 0) minOf(largestFrame, LARGEST_FRAME_SOUGHT).toInt() else LARGEST_FRAME_SOUGHT.toInt()
        val reach = minOf(end - frames, 2L * largest).toInt()
        val from = end - reach
        val tail = bytes.read(from, reach)
        var headers = 0

        // Whether what's from `at` is the start of the frame the cut falls in, shorter than the largest, or of the tags it does.
        fun cutShortAt(at: Int): Boolean = when {
            startsFrame(tail, at) -> reach - at < largest && (headerAgrees(tail, at) || reach - at < LONGEST_HEADER)
            reach - at == 1 -> tail.byteAt(at) == 0xFF
            else -> end == bytes.size && SoundTags.cutShortFrom(bytes, from + at)
        }

        for (start in reach - SMALLEST_FRAME downTo 0) {
            if (!startsFrame(tail, start) || !headerAgrees(tail, start)) {
                continue
            }

            var crc = 0

            for (index in start until minOf(reach, start + largest)) {
                crc = ((crc shl 8) xor CRC16_TABLE[(crc shr 8) xor tail.byteAt(index)]) and 0xFFFF

                if (crc == 0 && index + 1 - start >= SMALLEST_FRAME && index + 1 < reach && cutShortAt(index + 1)) {
                    return from + index + 1
                }
            }

            headers += 1

            if (headers == HEADERS_SOUGHT) {
                return null
            }
        }

        return null
    }

    /** Whether the frame header at `at` is one, its last byte the checksum of those before it. */
    private fun headerAgrees(bytes: ByteArray, at: Int): Boolean {
        if (at + 6 > bytes.size) {
            return false
        }

        val blockSize = bytes.byteAt(at + 2) shr 4
        val rate = bytes.byteAt(at + 2) and 0x0F
        val channels = bytes.byteAt(at + 3) shr 4
        val sampleSize = bytes.byteAt(at + 3) shr 1 and 0x07

        if (blockSize == 0 || rate == 15 || channels > 10 || sampleSize == 3 || bytes.byteAt(at + 3) and 0x01 != 0) {
            return false
        }

        // The frame's or first sample's number, written as UTF-8 writes a character: its first byte's leading ones say how many bytes.
        val ones = Integer.numberOfLeadingZeros(bytes.byteAt(at + 4).inv() and 0xFF) - 24

        val numberLength = when (ones) {
            0 -> 1
            in 2..7 -> ones
            else -> return false
        }

        val extra = when (blockSize) {
            6 -> 1
            7 -> 2
            else -> 0
        } + when (rate) {
            12 -> 1
            13, 14 -> 2
            else -> 0
        }

        val length = 4 + numberLength + extra

        if (at + length + 1 > bytes.size || (1 until numberLength).any { bytes.byteAt(at + 4 + it) and 0xC0 != 0x80 }) {
            return false
        }

        return crc8(bytes, at, at + length) == bytes.byteAt(at + length)
    }

    /** FLAC's checksum of a frame's header: CRC-8, x⁸ + x² + x + 1. */
    private fun crc8(bytes: ByteArray, from: Int, until: Int): Int {
        var crc = 0

        for (index in from until until) {
            crc = crc xor bytes.byteAt(index)

            repeat(8) {
                crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x07 else crc shl 1
            }

            crc = crc and 0xFF
        }

        return crc
    }

    /** FLAC's checksum of a whole frame: CRC-16, x¹⁶ + x¹⁵ + x² + 1. */
    private fun crc16(bytes: ByteArray, from: Int, until: Int): Int {
        var crc = 0

        for (index in from until until) {
            crc = ((crc shl 8) xor CRC16_TABLE[(crc shr 8) xor bytes.byteAt(index)]) and 0xFFFF
        }

        return crc
    }

    private val CRC16_TABLE = IntArray(256) { index ->
        var crc = index shl 8

        repeat(8) {
            crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x8005 else crc shl 1
        }

        crc and 0xFFFF
    }

    /** A block's first byte: its last bit says no block follows it, and the rest its type. */
    private const val LAST = 0x80

    private const val STREAMINFO = 0
    private const val PADDING = 1
    private const val SEEKTABLE = 3
    const val VORBIS_COMMENT = 4
    private const val INVALID = 127

    /** A frame at its smallest: its header, a subframe's first byte and the checksum. */
    private const val SMALLEST_FRAME = 9

    /** A frame's header at its longest: its sync code and codes, a sample's number in seven bytes, its block size and rate in four, and its checksum. */
    private const val LONGEST_HEADER = 16

    /** How far before the end the last frame is sought at most: the largest frames FLAC makes, eight channels of 32 bits verbatim, come to about 2 MB. */
    private const val LARGEST_FRAME_SOUGHT = 2L shl 20

    /** How much of the frames is read at a time as they're checked one by one. */
    private const val FRAMES_CHUNK = 256 * 1024

    /** How many headers that check out the search for the last frame tries before it gives up. */
    private const val HEADERS_SOUGHT = 8
}
