package com.hyuchia.orbit.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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

/**
 * A failure of the output something was being written to, such as a file
 * on a storage that's full, told apart from a failure of what was being
 * read into it, such as a map's file that went: a [watched] output throws it
 * in place of its own.
 */
internal class OutputFailed(cause: IOException) : IOException(cause.message, cause)

/** This output, throwing [OutputFailed] where it would fail, so its failures are told apart from those of what writes to it. */
internal fun OutputStream.watched(): OutputStream = WatchedOutput(this)

private class WatchedOutput(private val output: OutputStream) : OutputStream() {
    override fun write(byte: Int) = watch { output.write(byte) }

    override fun write(bytes: ByteArray, offset: Int, length: Int) = watch { output.write(bytes, offset, length) }

    override fun flush() = watch { output.flush() }

    override fun close() = watch { output.close() }

    private inline fun watch(action: () -> Unit) {
        try {
            action()
        } catch (failed: OutputFailed) {
            throw failed
        } catch (failed: IOException) {
            throw OutputFailed(failed)
        }
    }
}

/**
 * Room a big file leaves free on its storage, besides itself: nothing fills
 * it to the brim, the maps and their saves need some too.
 */
internal const val ROOM_LEFT_FREE = 32L * 1024 * 1024

/**
 * Throws [OutputFailed] when the storage `folder` is on hasn't room for
 * `size` more bytes, and [ROOM_LEFT_FREE] besides: a big file isn't started
 * where it can't be finished. A storage that can't say how much room it has
 * is left to say so as it's written.
 */
internal fun requireRoom(folder: File, size: Long) {
    val free = folder.usableSpace

    if (free > 0 && free < size + ROOM_LEFT_FREE) {
        throw OutputFailed(IOException("No room for $size bytes: $free free"))
    }
}
