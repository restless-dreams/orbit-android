package com.hyuchia.orbit.media

/** The application extensions a GIF keeps: its looping, as two apps write it, and its color profile. */
private val KEPT_APPLICATIONS = setOf("NETSCAPE2.0", "ANIMEXTS1.0", "ICCRGBG1012")

/**
 * A GIF with only what draws it: its colors, its frames, how each shows and
 * for how long, its looping and its color profile, as the parts of `bytes`
 * it keeps. Comments and other apps' blocks, such as XMP, where an editor
 * can write who made it and where, go, and so does anything after its end.
 * Null when it isn't whole.
 */
internal fun gifWithoutMetadata(bytes: PictureBytes): PicturePieces? {
    // Its signature and version, then its screen: size, colors and background.
    if (bytes.size < 13) {
        return null
    }

    var position = 13
    val screen = bytes.byteAt(10)

    if (screen and 0x80 != 0) {
        position += 3 * (1 shl ((screen and 0x07) + 1))
    }

    if (position > bytes.size) {
        return null
    }

    val output = PicturePieces()
    output.keep(0 until position)

    while (position < bytes.size) {
        when (bytes.byteAt(position)) {
            // The trailer: what follows it isn't the picture's, and stays out.
            0x3B -> {
                output.write(byteArrayOf(0x3B))
                return output
            }
            // A frame: where it goes, its own colors, and its data.
            0x2C -> {
                if (position + 11 > bytes.size) {
                    return null
                }

                val frame = bytes.byteAt(position + 9)
                var data = position + 10

                if (frame and 0x80 != 0) {
                    data += 3 * (1 shl ((frame and 0x07) + 1))
                }

                // Past the colors, the code size its data starts with.
                val end = subBlocksEnd(bytes, data + 1) ?: return null
                output.keep(position until end)
                position = end
            }
            0x21 -> {
                if (position + 2 > bytes.size) {
                    return null
                }

                val end = subBlocksEnd(bytes, position + 2) ?: return null

                if (keepsExtension(bytes, position)) {
                    output.keep(position until end)
                }

                position = end
            }
            else -> return null
        }
    }

    return null
}

/** Where the sub-blocks from `from` end, past the empty one that ends them; null when they run past the file. */
private fun subBlocksEnd(bytes: PictureBytes, from: Int): Int? {
    var position = from

    while (position < bytes.size) {
        val size = bytes.byteAt(position)

        if (size == 0) {
            return position + 1
        }

        position += 1 + size
    }

    return null
}

/**
 * Whether the extension at `at` draws the GIF: how a frame shows (its
 * graphic control), text drawn on it, and the applications' blocks that
 * loop it or give its colors' profile. Comments and anything else go.
 */
private fun keepsExtension(bytes: PictureBytes, at: Int): Boolean = when (bytes.byteAt(at + 1)) {
    0xF9, 0x01 -> true
    0xFF -> at + 14 <= bytes.size && bytes.byteAt(at + 2) == 11 && bytes.text(at + 3, 11) in KEPT_APPLICATIONS
    else -> false
}
