package com.hyuchia.orbit.media

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * A picture's bytes as Orbit reads through it to share it ([SharedPictures]):
 * in memory, or in its file, read a window at a time, so that a picture as
 * big as a map's file may be is never whole in memory. Reading past its end
 * throws an [IndexOutOfBoundsException], which says it isn't whole, as a
 * byte array's would.
 */
internal abstract class PictureBytes {
    abstract val size: Int

    /** The byte at `index`, from 0 to 255. */
    abstract fun byteAt(index: Int): Int

    fun shortAt(index: Int, bigEndian: Boolean): Int =
        if (bigEndian) (byteAt(index) shl 8) or byteAt(index + 1) else byteAt(index) or (byteAt(index + 1) shl 8)

    fun intAt(index: Int, bigEndian: Boolean): Int = if (bigEndian) {
        (shortAt(index, bigEndian = true) shl 16) or shortAt(index + 2, bigEndian = true)
    } else {
        shortAt(index, bigEndian = false) or (shortAt(index + 2, bigEndian = false) shl 16)
    }

    fun startsWith(prefix: ByteArray, at: Int = 0): Boolean =
        at >= 0 && at + prefix.size <= size && prefix.indices.all { byteAt(at + it) == prefix[it].toInt() and 0xFF }

    /** `count` bytes from `at`, as text in Latin-1: a chunk's type, an application's name. */
    fun text(at: Int, count: Int): String = String(bytes(at, count), Charsets.ISO_8859_1)

    /** `count` bytes from `at`. */
    open fun bytes(at: Int, count: Int): ByteArray = ByteArray(count) { byteAt(at + it).toByte() }
}

/** A picture's bytes, all in memory. */
internal class PictureInMemory(private val bytes: ByteArray) : PictureBytes() {
    override val size: Int get() = bytes.size

    override fun byteAt(index: Int): Int = bytes[index].toInt() and 0xFF

    override fun bytes(at: Int, count: Int): ByteArray {
        if (at < 0 || count < 0 || at + count > bytes.size) {
            throw IndexOutOfBoundsException("$count bytes at $at of ${bytes.size}")
        }

        return bytes.copyOfRange(at, at + count)
    }
}

/**
 * A picture's bytes in its file, read a window at a time: the readers mostly
 * go forward, so a window serves many reads. A file too big to count its
 * bytes in an [Int], past 2 GB, is never read through.
 */
internal class PictureInFile(private val file: RandomAccessFile) : PictureBytes() {
    override val size: Int = file.length().let { length -> if (length > Int.MAX_VALUE) throw IndexOutOfBoundsException("A picture of $length bytes") else length.toInt() }

    private val window = ByteArray(WINDOW)
    private var start = 0
    private var filled = 0

    override fun byteAt(index: Int): Int {
        if (index < start || index >= start + filled) {
            load(index)
        }

        return window[index - start].toInt() and 0xFF
    }

    private fun load(index: Int) {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("Byte $index of $size")
        }

        file.seek(index.toLong())
        filled = minOf(WINDOW, size - index)
        // A file that shrank since it was measured fails here: it can't be told what it holds now.
        file.readFully(window, 0, filled)
        start = index
    }

    private companion object {
        const val WINDOW = 256 * 1024
    }
}

/**
 * A picture as it goes to other people ([SharedPictures.plan]): the parts of
 * its file it keeps, in order, with the few bytes written in place of what
 * goes, as the EXIF that says only which way up it is. Written from its
 * file a piece at a time, it's never whole in memory.
 */
internal class PicturePieces {
    private sealed interface Piece

    /** The bytes of the picture's file from `from`, `count` of them. */
    private class Kept(val from: Int, val count: Int) : Piece

    private class Written(val bytes: ByteArray) : Piece

    private val pieces = ArrayList<Piece>()

    /** How many bytes the picture comes to. */
    var size = 0L
        private set

    /** Keeps the bytes of the picture's file in `range`. */
    fun keep(range: IntRange) {
        if (!range.isEmpty()) {
            pieces += Kept(range.first, range.last - range.first + 1)
            size += range.last - range.first + 1
        }
    }

    fun write(bytes: ByteArray) {
        pieces += Written(bytes)
        size += bytes.size
    }

    /** Writes the pieces of `other` after these, kept from the same file. */
    fun write(other: PicturePieces) {
        pieces += other.pieces
        size += other.size
    }

    /** Writes the picture to `output`, its kept parts read from `file` a piece at a time. */
    fun writeTo(output: OutputStream, file: RandomAccessFile) {
        val buffer = ByteArray(COPY_PIECE)

        for (piece in pieces) {
            when (piece) {
                is Written -> output.write(piece.bytes)
                is Kept -> {
                    file.seek(piece.from.toLong())
                    var left = piece.count

                    while (left > 0) {
                        val read = file.read(buffer, 0, minOf(left, buffer.size))

                        if (read < 0) {
                            throw EOFException("The picture ended before it was copied")
                        }

                        output.write(buffer, 0, read)
                        left -= read
                    }
                }
            }
        }
    }

    /** The picture in memory, its kept parts from `bytes`: for a picture already there, as one inside a drawing. */
    fun bytes(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(size.toInt())

        for (piece in pieces) {
            when (piece) {
                is Written -> output.write(piece.bytes)
                is Kept -> output.write(bytes, piece.from, piece.count)
            }
        }

        return output.toByteArray()
    }

    private companion object {
        const val COPY_PIECE = 256 * 1024
    }
}
