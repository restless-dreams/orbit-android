package com.hyuchia.orbit.data

/**
 * MP3s, and MPEG audio's other layers, and AAC in ADTS as phones and
 * converters write it, read frame by frame from the first to the last.
 * Their frames hold the sound, and what a file says beyond it is in the
 * tags around them ([SoundTags]): an ID3v2 tag before the first frame, or
 * between two, is emptied where it is, and those after the last stay out.
 * Zeros between frames say nothing, and stay. The encoder's name some write
 * into the first frame, or into the frames' spare bytes, stays with them:
 * players need that frame as it is to start and end the sound where it
 * does. A file cut short, as a recording that stopped or a copy that didn't
 * finish, goes as far as its last whole frame: the frame or the tag the
 * cut falls in stays out, as no player can play or read it.
 */
internal object MpegFrames {
    /**
     * What to write over the file, and where it ends, or null when it can't
     * be read through: something other than frames, tags and zeros in it,
     * or no whole frame at all.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val reading = ReadAhead(bytes)
        val end = SoundTags.trailingStart(reading, reading.size)
        val patches = Patches()
        var position = 0L
        var frames = 0
        // Whether its frames are AAC's: a file is one or the other all through.
        var aac: Boolean? = null
        // How the last whole frame starts: one cut short in its header starts as it does.
        var lastHead = ByteArray(0)

        // The plan of a file cut short at `position`, in a frame or a tag: as far as its last whole frame, when it has one.
        fun cutShort(): Pair<List<Patch>, Long>? = (patches to position).takeIf { frames > 0 }

        while (position < end) {
            val tag = SoundTags.id3Said(reading, position)

            if (tag != null) {
                // One the file ends in, cut short, as an ID3v2 tag after the frames may be.
                if (position + tag > reading.size) {
                    return cutShort()
                }

                if (position + tag > end) {
                    return null
                }

                patches += SoundTags.emptiedId3(position, tag)
                position += tag
                continue
            }

            val head = reading.read(position, FRAME_HEADER)

            if (head.first() == 0.toByte()) {
                position = SoundTags.zerosUntil(reading, position, end)
                continue
            }

            // Not a frame: tags the file ends in after the frames, cut short, or fewer bytes than a header, which start as the last frame's did.
            val (length, isAac) = frameAt(head) ?: return cutShort().takeIf {
                SoundTags.cutShortFrom(reading, position) || (head.size < FRAME_HEADER && lastHead.startsWith(head.copyOf(minOf(head.size, 2))))
            }

            if (aac != null && isAac != aac) {
                return null
            }

            // A frame that goes on past the end of the file, or into the tags after it: the file was cut short in it.
            if (position + length > end) {
                return cutShort()
            }

            aac = isAac
            lastHead = head
            position += length
            frames += 1
        }

        return (patches to end).takeIf { frames > 0 }
    }

    private fun ByteArray.startsWith(start: ByteArray): Boolean = size >= start.size && start.indices.all { this[it] == start[it] }

    /** How long the frame that starts with `head` is, and whether it's AAC's, or null when no frame starts so. */
    private fun frameAt(head: ByteArray): Pair<Int, Boolean>? = when {
        head.size < 4 || head.byteAt(0) != 0xFF -> null
        // Both start with twelve bits set; ADTS has no layer, MPEG audio always one.
        head.byteAt(1) and 0xF6 == 0xF0 -> adtsLength(head)?.let { it to true }
        head.byteAt(1) and 0xE0 == 0xE0 -> mpegLength(head)?.let { it to false }
        else -> null
    }

    /** An ADTS frame's length, its header included, as the header says it: null for a header no frame has. */
    private fun adtsLength(head: ByteArray): Int? {
        if (head.size < 7 || (head.byteAt(2) shr 2 and 0x0F) > LAST_ADTS_RATE) {
            return null
        }

        val length = ((head.byteAt(3) and 0x03) shl 11) or (head.byteAt(4) shl 3) or (head.byteAt(5) shr 5)
        // A header with a checksum is two bytes longer.
        val header = if (head.byteAt(1) and 0x01 == 0) 9 else 7
        return length.takeIf { it >= header }
    }

    /**
     * An MPEG audio frame's length, as its header says it: null for a
     * header no frame has, and for a free bitrate's, whose frames don't say
     * how long they are.
     */
    private fun mpegLength(head: ByteArray): Int? {
        val version = head.byteAt(1) shr 3 and 0x03
        val layer = 4 - (head.byteAt(1) shr 1 and 0x03)
        val bitrateIndex = head.byteAt(2) shr 4
        val rateIndex = head.byteAt(2) shr 2 and 0x03
        val padding = head.byteAt(2) shr 1 and 0x01

        if (version == RESERVED_VERSION || layer == 4 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
            return null
        }

        val first = version == MPEG1
        val bitrate = 1000 * (if (first) MPEG1_BITRATES[layer - 1] else MPEG2_BITRATES[if (layer == 1) 0 else 1])[bitrateIndex - 1]
        // MPEG-2 halves MPEG-1's sampling rates, and MPEG-2.5 halves them again.
        val rate = MPEG1_RATES[rateIndex] shr if (first) 0 else if (version == MPEG2) 1 else 2

        return when {
            layer == 1 -> (12 * bitrate / rate + padding) * 4
            layer == 3 && !first -> 72 * bitrate / rate + padding
            else -> 144 * bitrate / rate + padding
        }
    }

    /** As much of a frame's start as tells how long it is: ADTS's header, without its checksum. */
    private const val FRAME_HEADER = 7

    /** The last sampling rate an ADTS header can name, 7350 Hz; those after it are reserved. */
    private const val LAST_ADTS_RATE = 12

    /** MPEG audio's versions, as their headers write them: 2.5, then one reserved, 2 and 1. */
    private const val RESERVED_VERSION = 1

    private const val MPEG2 = 2
    private const val MPEG1 = 3

    private val MPEG1_RATES = intArrayOf(44_100, 48_000, 32_000)

    /** Bitrates in kilobits a second, by bitrate index from 1: MPEG-1's for layers I, II and III. */
    private val MPEG1_BITRATES = listOf(
        intArrayOf(32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),
        intArrayOf(32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),
        intArrayOf(32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
    )

    /** MPEG-2's and 2.5's: for layer I, then for layers II and III. */
    private val MPEG2_BITRATES = listOf(
        intArrayOf(32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
        intArrayOf(8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    )
}
