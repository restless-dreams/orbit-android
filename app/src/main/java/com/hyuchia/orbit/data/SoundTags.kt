package com.hyuchia.orbit.data

import java.io.IOException

/**
 * The tags apps add around a sound, whatever its kind: ID3v2 before it, in
 * which MP3s and AAC carry their title, artist, cover and chapters, and
 * ID3v1, Lyrics3 and APE after it, as taggers add them to MP3s and AAC, and
 * some to FLAC, Ogg and WAV files too. Each kind's own reader finds what its
 * own format says besides ([MpegFrames], [FlacBlocks], [OggPages],
 * [WaveChunks]): these tags are no format's own.
 */
internal object SoundTags {
    /**
     * How long the ID3v2 tag at `at` is, its header and any footer
     * included, or null when none starts there. One that runs past the end
     * of the file isn't whole, and can't be read through.
     */
    fun id3Length(bytes: Bytes, at: Long): Long? {
        val length = id3Said(bytes, at) ?: return null

        if (at + length > bytes.size) {
            throw IOException("An ID3 tag that isn't whole")
        }

        return length
    }

    /** How long the ID3v2 tag at `at` says it is, whole or not, or null when none starts there. */
    fun id3Said(bytes: Bytes, at: Long): Long? = id3Said(bytes.read(at, ID3_HEADER))

    /** How long the ID3v2 tag whose header is `header` says it is, its footer included, or null when no tag starts so. */
    private fun id3Said(header: ByteArray): Long? {
        // As players tell one: its version and revision are never 0xFF, and its size is written seven bits a byte.
        if (header.size < ID3_HEADER || !header.startsWith("ID3") || header.byteAt(3) == 0xFF || header.byteAt(4) == 0xFF || (6..9).any { header.byteAt(it) >= 0x80 }) {
            return null
        }

        val footer = if (header.byteAt(5) and ID3_FOOTER_PRESENT != 0) ID3_HEADER else 0
        return (ID3_HEADER + sevenBitsAt(header, 6) + footer).toLong()
    }

    /**
     * The ID3v2 tag of `length` bytes at `at` as one that says nothing: its
     * header, with its flags cleared and a size that takes in the rest of
     * it, footer and all, and nothing after the header but padding. What
     * follows it stays where it is.
     */
    fun emptiedId3(at: Long, length: Long): List<Patch> {
        val size = length - ID3_HEADER

        if (size > ID3_LARGEST) {
            throw IOException("An ID3 tag too long to say it's empty")
        }

        val flagsAndSize = byteArrayOf(0) + (21 downTo 0 step 7).map { (size shr it and 0x7F).toByte() }
        return listOf(Patch(at + 5, 5, flagsAndSize), Patch(at + ID3_HEADER, size))
    }

    /** Where the ID3v2 tags at `at`, one after another, end, with the zeros after them: `at` when there are none. */
    fun afterId3Tags(bytes: Bytes, at: Long): Long {
        var position = at

        while (true) {
            position = zerosUntil(bytes, position + (id3Length(bytes, position) ?: return position), bytes.size)
        }
    }

    /**
     * Where the tags at the end of a file, all before `end`, start: ID3v1
     * and the TAG+ before it, Lyrics3, APE and an ID3v2 with a footer, in
     * whatever order taggers left them. `end` when there are none; one
     * there that isn't whole can't be read through.
     */
    fun trailingStart(bytes: Bytes, end: Long): Long {
        var position = end

        while (true) {
            position = startOfTagEndingAt(bytes, position) ?: return position
        }
    }

    /**
     * Whether what's from `at` to the end of the file is the tags it ends
     * with, cut short where it ends, as a copy that didn't finish leaves
     * them: those [trailingStart] finds whole (ID3v1 and the TAG+ before
     * it, Lyrics3, APE and an ID3v2), each read from where it starts, as
     * what ends them was cut away, the last unfinished. An APE tag without
     * a header doesn't say what it is where it starts, and isn't told.
     */
    fun cutShortFrom(bytes: Bytes, at: Long): Boolean {
        var position = at

        while (position < bytes.size) {
            val start = bytes.read(position, APE_FOOTER)
            val left = bytes.size - position

            val length = when {
                // A header the file ends in says nothing more of its tag: it was cut short in it.
                start.startsWith("ID3") -> if (left < ID3_HEADER) return true else id3Said(start) ?: return false
                start.startsWith("APETAGEX") -> if (left < APE_FOOTER) return true else apeSaid(start) ?: return false
                start.startsWith(LYRICS_BEGIN) -> lyricsLength(bytes, position) ?: return left <= LYRICS_LONGEST
                start.startsWith("TAG+") -> ID3V1_EXTENDED.toLong()
                start.startsWith("TAG") -> ID3V1.toLong()
                // Fewer bytes than a tag's signature, as far as the file goes.
                else -> return left < LYRICS_BEGIN.length && SIGNATURES.any { it.startsWith(String(start, Charsets.ISO_8859_1)) }
            }

            position += length

            if (position > bytes.size) {
                return true
            }
        }

        return false
    }

    /** How long the APE tag whose header `header` is says it is, its header included, or null when that's no header. */
    private fun apeSaid(header: ByteArray): Long? {
        val size = header.littleEndian(12, 4)
        return (APE_FOOTER + size).takeIf { header.littleEndian(20, 4) and APE_IS_HEADER != 0L && size >= APE_FOOTER }
    }

    /**
     * How long the Lyrics3 tag at `at` is, read from its start: as far as
     * the `LYRICSEND` or `LYRICS200` that ends it, at most as long as one
     * can be. Null when none does before the file ends.
     */
    private fun lyricsLength(bytes: Bytes, at: Long): Long? {
        val tag = String(bytes.read(at, minOf(bytes.size - at, LYRICS_LONGEST.toLong()).toInt()), Charsets.ISO_8859_1)
        val end = listOf(tag.indexOf("LYRICSEND"), tag.indexOf("LYRICS200")).filter { it >= 0 }.minOrNull() ?: return null
        return end + LYRICS_END.toLong()
    }

    /** Where the zeros from `at` stop, at `end` at the latest: zeros say nothing. */
    fun zerosUntil(bytes: Bytes, at: Long, end: Long): Long {
        var position = at

        while (position < end) {
            val chunk = bytes.read(position, minOf(ZEROS_CHUNK.toLong(), end - position).toInt())
            val written = chunk.indexOfFirst { it != 0.toByte() }

            if (written >= 0) {
                return position + written
            }

            if (chunk.isEmpty()) {
                return end
            }

            position += chunk.size
        }

        return position
    }

    /** Where the tag that ends at `end` starts, or null when none does. */
    private fun startOfTagEndingAt(bytes: Bytes, end: Long): Long? {
        fun says(at: Long, text: String): Boolean = at >= 0 && bytes.read(at, text.length).startsWith(text)

        return when {
            // APE's footer says more than ID3v1's three letters do, so it's told first: its items may hold "TAG".
            says(end - APE_FOOTER, "APETAGEX") -> apeStart(bytes, end)
            says(end - ID3V1, "TAG") -> if (says(end - ID3V1 - ID3V1_EXTENDED, "TAG+")) end - ID3V1 - ID3V1_EXTENDED else end - ID3V1
            says(end - LYRICS_END, "LYRICS200") -> lyricsStart(bytes, end)
            says(end - LYRICS_END, "LYRICSEND") -> oldLyricsStart(bytes, end)
            says(end - ID3_HEADER, "3DI") -> footedId3Start(bytes, end)
            else -> null
        }
    }

    /** An APE tag's start, from its footer: the size it gives counts its items and the footer, and not a header before them. */
    private fun apeStart(bytes: Bytes, end: Long): Long {
        val footer = bytes.read(end - APE_FOOTER, APE_FOOTER)
        val size = footer.littleEndian(12, 4)
        val headed = footer.littleEndian(20, 4) and APE_HEADER_PRESENT != 0L
        val start = end - size - if (headed) APE_FOOTER else 0

        if (size < APE_FOOTER || start < 0 || (headed && !bytes.read(start, 8).startsWith("APETAGEX"))) {
            throw IOException("An APE tag that isn't whole")
        }

        return start
    }

    /** A Lyrics3 tag's start: version 2 gives its size, in six digits before its end, from `LYRICSBEGIN` on. */
    private fun lyricsStart(bytes: Bytes, end: Long): Long {
        val size = String(bytes.read(end - LYRICS_END - 6, 6), Charsets.ISO_8859_1).toLongOrNull()
        val start = size?.let { end - LYRICS_END - 6 - it }

        if (start == null || start < 0 || !bytes.read(start, 11).startsWith("LYRICSBEGIN")) {
            throw IOException("A Lyrics3 tag that isn't whole")
        }

        return start
    }

    /** The first Lyrics3's start, which gives no size: its `LYRICSBEGIN`, at most its 5100 bytes of lyrics before its end. */
    private fun oldLyricsStart(bytes: Bytes, end: Long): Long {
        val reach = minOf(end, OLD_LYRICS_LONGEST.toLong())
        val begin = String(bytes.read(end - reach, reach.toInt()), Charsets.ISO_8859_1).lastIndexOf("LYRICSBEGIN")

        if (begin < 0) {
            throw IOException("A Lyrics3 tag that isn't whole")
        }

        return end - reach + begin
    }

    /** An ID3v2 tag's start from its footer, which says its size as its header does. */
    private fun footedId3Start(bytes: Bytes, end: Long): Long {
        val footer = bytes.read(end - ID3_HEADER, ID3_HEADER)
        val start = end - 2 * ID3_HEADER - sevenBitsAt(footer, 6)

        if ((6..9).any { footer.byteAt(it) >= 0x80 } || start < 0 || id3Length(bytes, start) != end - start) {
            throw IOException("An ID3 tag that isn't whole")
        }

        return start
    }

    /** A number written seven bits a byte in four bytes from `at`, as ID3v2 writes its sizes. */
    private fun sevenBitsAt(bytes: ByteArray, at: Int): Int = (0 until 4).fold(0) { value, index -> (value shl 7) or (bytes.byteAt(at + index) and 0x7F) }

    private const val ID3_HEADER = 10
    private const val ID3_FOOTER_PRESENT = 0x10

    /** The most an ID3v2 tag's size can say, in its 28 bits. */
    private const val ID3_LARGEST = (1L shl 28) - 1

    private const val ID3V1 = 128

    /** The extended ID3v1 tag's part before the tag itself, `TAG+` and its longer fields. */
    private const val ID3V1_EXTENDED = 227

    private const val APE_FOOTER = 32
    private const val APE_HEADER_PRESENT = 1L shl 31

    /** What an APE tag's header says of itself, where its footer doesn't. */
    private const val APE_IS_HEADER = 1L shl 29

    /** `LYRICS200` and `LYRICSEND`, which end a Lyrics3 tag. */
    private const val LYRICS_END = 9

    private const val LYRICS_BEGIN = "LYRICSBEGIN"

    /** The first Lyrics3 at its longest: `LYRICSBEGIN`, 5100 bytes of lyrics and `LYRICSEND`. */
    private const val OLD_LYRICS_LONGEST = 11 + 5100 + 9

    /** Lyrics3's second version at its longest: as much as its six digits can say, then them and `LYRICS200`. */
    private const val LYRICS_LONGEST = 999_999 + 6 + 9

    /** How the tags after a sound start, as far as they say what they are there. */
    private val SIGNATURES = listOf("ID3", "APETAGEX", LYRICS_BEGIN, "TAG")

    private const val ZEROS_CHUNK = 4096
}
