package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * The most an entry of a zip may hold: anything larger isn't a map's text,
 * picture, video or sound, which may be up to [MEDIA_FILE_LIMIT], a cleaned
 * picture a little more.
 */
internal const val ZIP_ENTRY_LIMIT = 256L * 1024 * 1024

/** The most an entry of a zip may hold in Orbit 0.4.0, and every release before it: they refuse a zip with a bigger one. */
internal const val ZIP_ENTRY_LIMIT_UP_TO_0_4_0 = 64L * 1024 * 1024

/** The most a map's text, or one of its versions, may be in a zip: what it may be anywhere. */
internal const val ZIP_TEXT_LIMIT = MAP_TEXT_LIMIT

/** A zip Orbit won't take in: too big once unpacked, too many files, or files that point outside it. Its message says which, for people. */
open class RefusedZipException(message: String) : IOException(message)

/** A zip there's no room on the device for, found as it was unpacked: what it unpacked to so far is gone. */
class NoRoomForZipException(message: String) : RefusedZipException(message)

/**
 * What a zip may still unpack to: so many files, so many bytes. A zip can
 * hold far more than it weighs, so what comes out of it is counted as it's
 * read, and reading stops once it's past what maps hold. Its refusals say
 * why in `words`.
 */
internal class ZipBudget(private var entries: Int, private var bytes: Long, private val words: Words) {
    /** The next file, named `name`. */
    fun entry(name: String) {
        entries -= 1

        if (entries < 0) {
            throw RefusedZipException(words.string(R.string.data_zip_too_many_files))
        }

        checkEntryName(name, words)
    }

    fun take(count: Int) {
        bytes -= count

        if (bytes < 0) {
            throw RefusedZipException(words.string(R.string.data_zip_too_big))
        }
    }

    /** Refuses a file of the zip too big to be part of a map. */
    fun refuseFile(): Nothing = throw RefusedZipException(words.string(R.string.data_zip_file_too_big))
}

/** Refuses a file in a zip named to land outside it, as `../` or a path from the root would: no map has one. */
internal fun checkEntryName(name: String, words: Words) {
    val parts = name.split('/', '\\')

    if (name.startsWith("/") || name.startsWith("\\") || name.contains('\u0000') || parts.any { it == ".." } || DRIVE.containsMatchIn(name)) {
        throw RefusedZipException(words.string(R.string.data_zip_points_outside))
    }
}

private val DRIVE = Regex("^[A-Za-z]:")

/**
 * The file `name` names inside `directory`, refusing a name that resolves outside it. The names
 * are checked as they're read already; this is the check Google Play looks for where a zip is
 * unpacked, made on the path the file is written to.
 */
internal fun fileInside(directory: File, name: String, words: Words): File {
    val root = directory.canonicalFile
    val file = File(root, name).canonicalFile

    if (!file.path.startsWith(root.path + File.separator)) {
        throw RefusedZipException(words.string(R.string.data_zip_points_outside))
    }

    return file
}

/** An entry of a zip, refusing one bigger than `limit`, and counting it in `budget`. */
internal fun ZipInputStream.readLimited(budget: ZipBudget, limit: Long = ZIP_ENTRY_LIMIT): ByteArray {
    val output = ByteArrayOutputStream()
    copyLimited(output, budget, limit)
    return output.toByteArray()
}

/**
 * Copies an entry of a zip into `output`, refusing one bigger than `limit`,
 * and counting it in `budget`. `checkRoom` looks at the room left where it's
 * written, before the first byte and after each megabyte, and throws once
 * there's too little, so a big file never fills the storage.
 */
internal fun ZipInputStream.copyLimited(output: OutputStream?, budget: ZipBudget, limit: Long = ZIP_ENTRY_LIMIT, checkRoom: () -> Unit = {}) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
    var total = 0L
    var sinceLook = 0L
    checkRoom()

    while (true) {
        val read = read(buffer)

        if (read < 0) {
            break
        }

        total += read

        if (total > limit) {
            budget.refuseFile()
        }

        budget.take(read)
        output?.write(buffer, 0, read)
        sinceLook += read

        if (sinceLook >= ROOM_LOOK_BYTES) {
            checkRoom()
            sinceLook = 0
        }
    }
}

/** How much of a zip's file is written between looks at the room left where it goes. */
private const val ROOM_LOOK_BYTES = 1024L * 1024

/** Reads past an entry of a zip, counting it in `budget`: skipping one unpacks it all the same. */
internal fun ZipInputStream.skipEntry(budget: ZipBudget) = copyLimited(null, budget, Long.MAX_VALUE)
