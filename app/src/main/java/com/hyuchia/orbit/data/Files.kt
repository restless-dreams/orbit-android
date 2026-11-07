package com.hyuchia.orbit.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Writes a file so a crash, a full disk or a dead battery never leaves half
 * of it: `write` fills a file beside it, which reaches the disk and only then
 * takes its name, in one step. If `write` throws or that step fails, the file
 * is as it was, and the write throws: nothing is ever written over the file
 * in place. A big file, such as a video, streams through without being held
 * in memory.
 */
internal fun writeAtomically(file: File, write: (OutputStream) -> Unit) {
    val directory = file.absoluteFile.parentFile ?: throw IOException("No folder for $file")
    directory.mkdirs()
    // A name of its own for each write, so two writers of one file never fill the same one.
    val partial = File(directory, ".${file.name}.${UUID.randomUUID().toString().take(8)}.partial")

    try {
        FileOutputStream(partial).use { output ->
            write(output)
            output.flush()
            output.fd.sync()
        }

        if (!partial.renameTo(file)) {
            throw IOException("Couldn't put ${file.name} in place")
        }

        // The rename reaches the disk too, so a crash right after can't bring the old file back.
        syncDirectory(directory)
    } finally {
        partial.delete()
    }
}

/** Puts a folder's entries on the disk, as a rename in it left them; where the system doesn't allow it, it's left to the system. */
private fun syncDirectory(directory: File) {
    runCatching { FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }
}

/**
 * The partial file [writeAtomically] would fill for `file`, for what's made
 * by its path rather than written through a stream, as a video made smaller
 * is: [putInPlace] then gives it `file`'s name. The caller deletes it when
 * it isn't put in place; one left behind goes as a crash's partial files go.
 */
internal fun partialFile(file: File): File {
    val directory = file.absoluteFile.parentFile ?: throw IOException("No folder for $file")
    directory.mkdirs()
    return File(directory, ".${file.name}.${UUID.randomUUID().toString().take(8)}.partial")
}

/** Puts `partial`, made whole, in `file`'s place as [writeAtomically] does: on the disk first, then renamed in one step, the rename synced too. */
internal fun putInPlace(partial: File, file: File) {
    FileOutputStream(partial, true).use { it.fd.sync() }

    if (!partial.renameTo(file)) {
        throw IOException("Couldn't put ${file.name} in place")
    }

    syncDirectory(file.absoluteFile.parentFile ?: return)
}

/** Whether `file` is a write's partial file, as [writeAtomically] names them: one left means a write stopped partway. */
internal fun isPartial(file: File): Boolean = file.name.startsWith(".") && file.name.endsWith(".partial")

internal fun writeAtomically(file: File, bytes: ByteArray) = writeAtomically(file) { it.write(bytes) }

internal fun writeAtomically(file: File, text: String) = writeAtomically(file, text.toByteArray(Charsets.UTF_8))

/**
 * Text from a file someone else wrote: UTF-8 when it is, else UTF-16 when it
 * says so, else Windows-1252, which reads any bytes as the characters a
 * Western text editor would show. A UTF-8 file with a few bytes that aren't,
 * such as one stray byte, keeps its UTF-8 characters, and only those bytes
 * read as Windows-1252: a file that's mostly characters of more than one
 * byte is UTF-8. Nothing is replaced with "�", so writing the text back
 * loses no characters (it's written as UTF-8).
 */
fun decodeText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return decodeText(bytes.copyOfRange(3, bytes.size))
    }

    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }

    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }

    strictly(bytes, Charsets.UTF_8)?.let { return it }
    var characters = 0
    var strays = 0
    var index = 0

    while (index < bytes.size) {
        val length = utf8Length(bytes, index)

        when {
            length == 0 -> strays += 1
            length > 1 -> characters += 1
        }

        index += maxOf(length, 1)
    }

    return if (characters > 0 && characters >= strays) mixedText(bytes) else westernText(bytes)
}

/**
 * Whether `bytes` are UTF-8 that stops partway through a character: what's
 * there of a file still being written, as a sync app writes one in place,
 * or one cut short.
 */
fun endsMidCharacter(bytes: ByteArray): Boolean {
    var index = 0

    while (index < bytes.size) {
        val length = utf8Length(bytes, index)

        if (length == 0) {
            return bytes.size - index < expectedLength(bytes[index].toInt() and 0xFF) && continuesUntilEnd(bytes, index)
        }

        index += length
    }

    return false
}

/**
 * Thrown when a file looks like it's still arriving: what could be read of it
 * so far may be only its start. `text` is what was read, for when it stays
 * as it is: then it's how the file is.
 */
class StillArriving(name: String, val text: String) : IOException("$name is still arriving")

/**
 * Thrown when a file holds more than a map's text may ([MAP_TEXT_LIMIT]),
 * whatever size it was listed at: no more than that of it was read.
 */
class TooLargeForAMap(name: String) : IOException("$name is more than a map holds")

/**
 * Thrown when a picture, video or sound isn't copied to or from the folder
 * maps are kept in, for lack of room on this device for it and some to spare
 * ([ROOM_LEFT_FREE]): nothing of it was written, and it waits for room.
 */
class NoRoomForMedia(val name: String) : IOException("No room for $name")

/** How many bytes the UTF-8 character at `index` takes, or 0 when the bytes there aren't one, whole. */
private fun utf8Length(bytes: ByteArray, index: Int): Int {
    val lead = bytes[index].toInt() and 0xFF

    if (lead < 0x80) {
        return 1
    }

    val length = expectedLength(lead)

    if (length == 0 || index + length > bytes.size) {
        return 0
    }

    val second = bytes[index + 1].toInt() and 0xFF

    // Each lead byte allows its own range for the next, so no character is written longer than it needs, nor as a surrogate.
    val low = when (lead) {
        0xE0 -> 0xA0
        0xF0 -> 0x90
        else -> 0x80
    }

    val high = when (lead) {
        0xED -> 0x9F
        0xF4 -> 0x8F
        else -> 0xBF
    }

    if (second !in low..high || (2 until length).any { (bytes[index + it].toInt() and 0xFF) !in 0x80..0xBF }) {
        return 0
    }

    return length
}

/** How many bytes a UTF-8 character starting with `lead` takes, or 0 when no character starts with it. */
private fun expectedLength(lead: Int): Int = when (lead) {
    in 0xC2..0xDF -> 2
    in 0xE0..0xEF -> 3
    in 0xF0..0xF4 -> 4
    else -> 0
}

/** Whether every byte after the lead byte at `index` continues a character, up to the end. */
private fun continuesUntilEnd(bytes: ByteArray, index: Int): Boolean = (index + 1 until bytes.size).all { (bytes[it].toInt() and 0xFF) in 0x80..0xBF }

/** UTF-8, with each byte that isn't part of a whole character read as Windows-1252. */
private fun mixedText(bytes: ByteArray): String = buildString(bytes.size) {
    var index = 0

    while (index < bytes.size) {
        val length = utf8Length(bytes, index)

        if (length == 0) {
            append(WESTERN[bytes[index].toInt() and 0xFF])
            index += 1
        } else {
            append(String(bytes, index, length, Charsets.UTF_8))
            index += length
        }
    }
}

/**
 * Windows-1252, the five bytes it leaves undefined read as Latin-1 does
 * (control characters), so every byte becomes a character of its own.
 */
private val WESTERN: CharArray by lazy {
    val table = String(ByteArray(256) { it.toByte() }, Charset.forName("windows-1252")).toCharArray()

    for (index in table.indices) {
        if (table[index] == '�') {
            table[index] = index.toChar()
        }
    }

    table
}

private fun westernText(bytes: ByteArray): String {
    val characters = CharArray(bytes.size) { WESTERN[bytes[it].toInt() and 0xFF] }
    return String(characters)
}

private fun strictly(bytes: ByteArray, charset: Charset): String? = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (unreadable: CharacterCodingException) {
    null
}

/** Whether `one` and the file `other` hold the same bytes: told by their sizes when those differ, else read a piece at a time, as a video can be long. */
internal fun sameBytes(one: File, other: File): Boolean {
    if (!other.isFile || one.length() != other.length()) {
        return false
    }

    return one.inputStream().use { first ->
        other.inputStream().use { second ->
            val mine = ByteArray(DEFAULT_BUFFER_SIZE)
            val theirs = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = mine.size

            while (read == mine.size) {
                read = first.readUpTo(mine)

                // Past what was read, both still hold the same piece as before: the whole of each compares.
                if (second.readUpTo(theirs) != read || !mine.contentEquals(theirs)) {
                    return false
                }
            }

            true
        }
    }
}

/** A text file's text, decoded as [decodeText] does. */
internal fun File.readTextSafely(): String = decodeText(readBytes())
