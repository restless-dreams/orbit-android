package com.hyuchia.orbit.data

/**
 * BMP pictures. Their headers say only what the picture is, its size,
 * colors, color masks and color profile: no field says where, when or on
 * what it was made. An app may keep its own in the four bytes the format
 * leaves to apps, though, between the colors and the pixels, or after the
 * pixels, and as a BMP goes those say nothing: the four bytes and what's
 * between are zeros, and anything after the pixels and the color profile
 * is left out. A linked profile's file name goes too. A BMP that holds a
 * JPEG or PNG, which have metadata of their own, or whose pixels can't be
 * told, can't be read through.
 */
internal object Bitmaps {
    /**
     * What to write over the picture, and where it ends, or null when it
     * can't be read through.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val head = bytes.read(0, FILE_HEADER + LARGEST_HEADER)

        if (head.size < FILE_HEADER + 12 || !head.startsWith("BM")) {
            return null
        }

        val headerSize = head.littleEndian(FILE_HEADER, 4).toInt()
        val core = headerSize == CORE_HEADER

        if (headerSize !in HEADERS || head.size < FILE_HEADER + headerSize) {
            return null
        }

        // A header from OS/2 says its width and height in two bytes; the others in four, the height upside down when it's negative.
        val width = if (core) head.littleEndian(18, 2) else head.littleEndian(18, 4).toInt().toLong()
        val height = if (core) head.littleEndian(20, 2) else kotlin.math.abs(head.littleEndian(22, 4).toInt().toLong())
        val bits = head.littleEndian(if (core) 24 else 28, 2).toInt()
        val compression = if (core) 0 else head.littleEndian(30, 4).toInt()

        if (width <= 0 || height == 0L || bits !in BITS || compression !in COMPRESSIONS) {
            return null
        }

        val masks = when {
            headerSize != INFO_HEADER -> 0
            compression == BITFIELDS -> 12
            compression == ALPHA_BITFIELDS -> 16
            else -> 0
        }

        val colorsSaid = if (core) 0 else head.littleEndian(46, 4)
        val colors = if (colorsSaid > 0) colorsSaid else if (bits <= 8) 1L shl bits else 0L
        val pixels = head.littleEndian(10, 4)
        // Some writers keep fewer colors than their header says: the pixels start where they say.
        val palette = minOf(FILE_HEADER + headerSize + masks + colors * if (core) 3 else 4, pixels)

        // Reckoned so that a width or height near 2³¹ can't wrap around: it throws, and the picture can't be read through.
        val pixelsEnd = Math.addExact(
            pixels,
            when (compression) {
                RLE8, RLE4 -> head.littleEndian(34, 4).takeIf { it > 0 } ?: return null
                else -> Math.multiplyExact((bits * width + 31) / 32 * 4, height)
            },
        )

        if (FILE_HEADER + headerSize + masks > pixels || pixelsEnd <= pixels || pixelsEnd > bytes.size) {
            return null
        }

        val patches = Patches().apply { add(Patch(6, 4)) }
        var end = pixelsEnd
        val profile = profileOf(head, headerSize)

        if (profile != null) {
            val (start, size, embedded) = profile

            if (start < palette || start + size > bytes.size || (start < pixelsEnd && start + size > pixels)) {
                return null
            }

            if (embedded) {
                // Kept as a JPEG's or PNG's is: it's how the colors look. The gap it leaves is zeros.
                end = maxOf(end, start + size)
                patches += gaps(palette, pixels, pixelsEnd, end, start, start + size)
            } else {
                patches += Patch(start, size)
                patches += gaps(palette, pixels, pixelsEnd, end, null, null)
            }
        } else {
            patches += gaps(palette, pixels, pixelsEnd, end, null, null)
        }

        // The file's size, in its header, as it is now.
        if (head.littleEndian(2, 4) > end) {
            patches += Patch(2, 4, ByteArray(4) { (end shr (8 * it)).toByte() })
        }

        return patches to end
    }

    /** Where a version 5 header's color profile is, how long it is, and whether it's embedded rather than a file's name; null without one. */
    private fun profileOf(head: ByteArray, headerSize: Int): Triple<Long, Long, Boolean>? {
        if (headerSize < V5_HEADER) {
            return null
        }

        val type = head.littleEndian(FILE_HEADER + 56, 4)

        if (type != EMBEDDED && type != LINKED) {
            return null
        }

        return Triple(FILE_HEADER + head.littleEndian(FILE_HEADER + 112, 4), head.littleEndian(FILE_HEADER + 116, 4), type == EMBEDDED)
    }

    /** The patches that zero what's between the colors and the pixels, and between the pixels and `end`, but for what's from `keptFrom` to `keptUntil`. */
    private fun gaps(palette: Long, pixels: Long, pixelsEnd: Long, end: Long, keptFrom: Long?, keptUntil: Long?): List<Patch> {
        val gaps = listOf(palette to pixels, pixelsEnd to end).flatMap { (from, until) ->
            if (keptFrom == null || keptUntil == null || keptUntil <= from || keptFrom >= until) {
                listOf(from to until)
            } else {
                listOf(from to keptFrom, keptUntil to until)
            }
        }

        return gaps.filter { (from, until) -> until > from }.map { (from, until) -> Patch(from, until - from) }
    }

    private const val FILE_HEADER = 14
    private const val CORE_HEADER = 12
    private const val INFO_HEADER = 40
    private const val V5_HEADER = 124
    private const val LARGEST_HEADER = V5_HEADER

    /** The headers Orbit reads: OS/2's first, then Windows's, from 3 to 5. */
    private val HEADERS = setOf(CORE_HEADER, INFO_HEADER, 52, 56, 108, V5_HEADER)

    private val BITS = setOf(1, 2, 4, 8, 16, 24, 32)

    private const val RLE8 = 1
    private const val RLE4 = 2
    private const val BITFIELDS = 3
    private const val ALPHA_BITFIELDS = 6

    /** Pixels as they are, run-length encoded or with color masks; a JPEG's or PNG's (4 and 5) can't be told here. */
    private val COMPRESSIONS = setOf(0, RLE8, RLE4, BITFIELDS, ALPHA_BITFIELDS)

    /** A version 5 header's color space types: a profile embedded in the file ('MBED'), or one linked to by its file's name ('LINK'). */
    private const val EMBEDDED = 0x4D424544L

    private const val LINKED = 0x4C494E4BL
}
