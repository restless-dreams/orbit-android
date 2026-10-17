package com.hyuchia.orbit.nearby

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** The longest number a Range header may give: more digits than any file's length has. */
private const val RANGE_DIGITS = 18

/**
 * What a request's Range header asks of a file, as a server that serves one
 * span of bytes at a time answers it: videos ask for the part they play
 * next, and seek by asking for another.
 */
sealed interface ByteRange {
    /** The whole file: no range was asked for, or one this server doesn't serve in parts. */
    data object Whole : ByteRange

    /** Bytes `first` to `last` of a file `length` long, both included. */
    data class Part(val first: Long, val last: Long, val length: Long) : ByteRange {
        val count: Long get() = last - first + 1

        /** The answer's Content-Range header. */
        val contentRange: String get() = "bytes $first-$last/$length"
    }

    /** A range that starts past the file's end: answered 416, with the file's length. */
    data class Unsatisfiable(val length: Long) : ByteRange {
        val contentRange: String get() = "bytes */$length"
    }
}

/**
 * The bytes `header` (a request's Range) asks of a file `length` bytes
 * long. One range, `first-`, `first-last` or a suffix `-count`, is served;
 * several at once, other units and malformed ones get the whole file, as
 * the standard lets a server choose. An If-Range header asks for the whole
 * file unless the file still matches what it names, and the server names
 * nothing, so it gets the whole file too.
 */
fun byteRange(header: String?, length: Long, ifRange: String? = null): ByteRange {
    val spec = header?.trim()?.takeIf { it.isNotEmpty() } ?: return ByteRange.Whole

    if (ifRange != null) {
        return ByteRange.Whole
    }

    val equals = spec.indexOf('=')

    if (equals < 0 || !spec.substring(0, equals).trim().equals("bytes", ignoreCase = true)) {
        return ByteRange.Whole
    }

    val ranges = spec.substring(equals + 1).split(',').map { it.trim() }

    if (ranges.size != 1) {
        return ByteRange.Whole
    }

    val range = ranges.single()
    val dash = range.indexOf('-')

    if (dash < 0) {
        return ByteRange.Whole
    }

    val firstText = range.substring(0, dash).trim()
    val lastText = range.substring(dash + 1).trim()

    if (firstText.isEmpty()) {
        // The last `count` bytes.
        val count = number(lastText) ?: return ByteRange.Whole

        if (count == 0L || length == 0L) {
            return ByteRange.Unsatisfiable(length)
        }

        return ByteRange.Part(maxOf(0L, length - count), length - 1, length)
    }

    val first = number(firstText) ?: return ByteRange.Whole
    val last = if (lastText.isEmpty()) null else number(lastText) ?: return ByteRange.Whole

    if (last != null && last < first) {
        return ByteRange.Whole
    }

    if (first >= length) {
        return ByteRange.Unsatisfiable(length)
    }

    return ByteRange.Part(first, minOf(last ?: (length - 1), length - 1), length)
}

private fun number(text: String): Long? = text.takeIf { it.isNotEmpty() && it.length <= RANGE_DIGITS && it.all { character -> character in '0'..'9' } }?.toLong()

/** `count` bytes of `file` from byte `first`, read as they're needed, never all at once. */
fun openRange(file: File, first: Long, count: Long): InputStream {
    val input = FileInputStream(file)

    try {
        input.channel.position(first)
    } catch (failure: Exception) {
        input.close()
        throw failure
    }

    return Bounded(input, count)
}

/** An input that ends after `left` bytes, whatever follows them. */
private class Bounded(private val input: InputStream, private var left: Long) : InputStream() {
    override fun read(): Int {
        if (left <= 0) {
            return -1
        }

        val byte = input.read()

        if (byte >= 0) {
            left -= 1
        }

        return byte
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (left <= 0) {
            return -1
        }

        val count = input.read(buffer, offset, minOf(length.toLong(), left).toInt())

        if (count > 0) {
            left -= count
        }

        return count
    }

    override fun available(): Int = minOf(input.available().toLong(), left).toInt()

    override fun close() = input.close()
}
