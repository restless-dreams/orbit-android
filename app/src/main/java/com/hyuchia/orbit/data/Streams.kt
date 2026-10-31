package com.hyuchia.orbit.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads until `count` bytes are in `buffer` or the input ends. Returns how many were read. */
internal fun InputStream.readUpTo(buffer: ByteArray, count: Int = buffer.size): Int {
    var read = 0

    while (read < count) {
        val got = read(buffer, read, count - read)

        if (got < 0) {
            break
        }

        read += got
    }

    return read
}

/** Up to `limit` bytes of the stream: Android has `readNBytes` only from API 33. */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)

    while (bytes.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - bytes.size()))

        if (read < 0) {
            break
        }

        bytes.write(chunk, 0, read)
    }

    return bytes.toByteArray()
}
