package com.hyuchia.orbit.data

import java.io.InputStream
import java.io.SequenceInputStream

/** The bytes a WebP's header takes, up to the flags of its extended format. */
private const val WEBP_HEADER = 21

/** The flag a WebP's extended format sets when it holds an animation. */
private const val WEBP_ANIMATION = 0x02

/** A GIF's blocks, after its header: an extension, and a frame. */
private const val GIF_EXTENSION = 0x21
private const val GIF_FRAME = 0x2C

/** The flag a GIF's screen or frame sets when a color table follows, whose size is in the flags' lowest three bits. */
private const val GIF_COLOR_TABLE = 0x80

/**
 * The extension an animation is kept with, read from its bytes: `gif` for a
 * GIF of more than one frame, `webp` for a WebP whose header says it's
 * animated. Null for anything else, a GIF or WebP that stands still
 * included, and for bytes that end before they can tell. A GIF is read up
 * to its second frame, and never past it.
 */
fun animationExtension(input: InputStream): String? {
    val head = ByteArray(WEBP_HEADER)
    val read = input.readUpTo(head)

    fun text(at: Int, length: Int) = if (read >= at + length) String(head, at, length, Charsets.ISO_8859_1) else ""

    return when {
        text(0, 4) == "RIFF" && text(8, 4) == "WEBP" -> "webp".takeIf { text(12, 4) == "VP8X" && read > 20 && head[20].toInt() and WEBP_ANIMATION != 0 }
        text(0, 6) == "GIF87a" || text(0, 6) == "GIF89a" -> "gif".takeIf { SequenceInputStream(head.inputStream(0, read), input).hasSecondFrame() }
        else -> null
    }
}

/**
 * Whether a GIF, from its first byte, has a second frame. Each block is
 * skipped by the sizes it gives, so no frame is ever decoded.
 */
private fun InputStream.hasSecondFrame(): Boolean {
    // The header and the screen's size come first, then its flags, and its background and shape.
    if (skipFully(10) < 10) {
        return false
    }

    val screen = read()

    if (screen < 0 || skipFully(2) < 2 || !skipColorTable(screen)) {
        return false
    }

    var frames = 0

    while (true) {
        when (read()) {
            GIF_EXTENSION -> {
                if (read() < 0 || !skipSubBlocks()) {
                    return false
                }
            }
            GIF_FRAME -> {
                frames++

                if (frames > 1) {
                    return true
                }

                // Where the frame goes and how big it is, then its flags, its own colors, and its picture's code size.
                if (skipFully(8) < 8) {
                    return false
                }

                val flags = read()

                if (flags < 0 || !skipColorTable(flags) || read() < 0 || !skipSubBlocks()) {
                    return false
                }
            }
            // Its end, the end of the bytes, or something that isn't a GIF's block.
            else -> return false
        }
    }
}

/** Skips the color table `flags` says follows, if any. False when the bytes end first. */
private fun InputStream.skipColorTable(flags: Int): Boolean {
    if (flags and GIF_COLOR_TABLE == 0) {
        return true
    }

    val size = 3L * (1 shl ((flags and 0x07) + 1))
    return skipFully(size) == size
}

/** Skips a GIF's data, in blocks that each say how long they are, up to the empty one that ends them. False when the bytes end first. */
private fun InputStream.skipSubBlocks(): Boolean {
    while (true) {
        val size = read()

        when {
            size < 0 -> return false
            size == 0 -> return true
            skipFully(size.toLong()) < size -> return false
        }
    }
}

/** Skips `count` bytes, or as many as there are. Returns how many it skipped. */
private fun InputStream.skipFully(count: Long): Long {
    var skipped = 0L

    while (skipped < count) {
        val step = skip(count - skipped)

        if (step > 0) {
            skipped += step
        } else if (read() >= 0) {
            // A stream that skips nothing may still have bytes to read: one between two streams, say.
            skipped++
        } else {
            break
        }
    }

    return skipped
}
