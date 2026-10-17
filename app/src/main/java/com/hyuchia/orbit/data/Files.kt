package com.hyuchia.orbit.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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
    val partial = File(directory, ".${file.name}.partial")

    try {
        FileOutputStream(partial).use { output ->
            write(output)
            output.flush()
            output.fd.sync()
        }

        if (!partial.renameTo(file)) {
            throw IOException("Couldn't put ${file.name} in place")
        }
    } finally {
        partial.delete()
    }
}

internal fun writeAtomically(file: File, bytes: ByteArray) = writeAtomically(file) { it.write(bytes) }

internal fun writeAtomically(file: File, text: String) = writeAtomically(file, text.toByteArray(Charsets.UTF_8))

/**
 * Text from a file someone else wrote: UTF-8 when it is, else UTF-16 when it
 * says so, else Windows-1252, which reads any bytes as the characters a
 * Western text editor would show. Nothing is replaced with "�", so writing
 * the text back loses no characters (it's written as UTF-8).
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

    return strictly(bytes, Charsets.UTF_8) ?: westernText(bytes)
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

/** A text file's text, decoded as [decodeText] does. */
internal fun File.readTextSafely(): String = decodeText(readBytes())
