package com.hyuchia.orbit.page

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.data.animationExtension
import com.hyuchia.orbit.data.writeAtomically
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** An animation's first frame is kept at most this many pixels on its longer side, as pictures from the phone are. */
private const val FIRST_FRAME_SIZE = 2048

/** The cache's folder for [FirstFrames]: the map's page and web pages share what it keeps. */
internal const val FIRST_FRAMES_DIRECTORY = "first-frames"

/**
 * A picture the map's page gets in place of a map's file
 * ([FirstFrames.standIn]): its name, whose extension says its type, how long
 * it is, and its bytes, from the cache, or from memory when the cache
 * couldn't keep them.
 */
internal class StandIn(val name: String, val length: Long, private val reading: () -> InputStream?) {
    /** Its bytes, or null when its file went, or can't be read, since it was made. */
    fun open(): InputStream? = reading()

    companion object {
        /** One kept in the cache, as `file`. */
        fun kept(file: File): StandIn = StandIn(file.name, file.length()) { openMedia(file) }

        /** One the cache couldn't keep, a full disk's, say: its `bytes` go to the page as they are, under `name`. */
        fun held(name: String, bytes: ByteArray): StandIn = StandIn(name, bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    }
}

/**
 * The first frames of a map's animated pictures, GIFs and WebPs that move,
 * for the map's page: a planet that moved would keep the page drawing for as
 * long as it's on screen, and the battery with it, whatever Android's
 * animations say. Its details and full screen play it. Each is made once,
 * as a PNG in `directory`, by `frame`, for the file as it is: one that
 * changes gets a new one. Pictures that stand still are no business of it,
 * but for HEIC and HEIF ones, as iPhones and some Samsungs take them:
 * Chromium doesn't draw those, so they go as a JPEG `drawn` makes, the same
 * way, to the map's page, and inside web pages and prints. Pictures from the
 * phone are made JPEGs as they come in; these come through the folder maps
 * are kept in, or a zip.
 */
internal class FirstFrames(
    private val directory: File,
    private val drawn: (File) -> ByteArray? = ::pictureJpeg,
    private val frame: (File) -> ByteArray? = ::firstFramePng,
) {
    /** Which files, as they are, are animations: so a still GIF or WebP is read through only once. */
    private val animated = ConcurrentHashMap<String, Boolean>()

    /**
     * The first frame of `file`, as a PNG, or a HEIC or HEIF picture as a
     * JPEG, kept in `directory`; null when it's neither, or can't be read.
     * Without `animations`, only a HEIC or HEIF picture. Throws
     * [IOException] when it can't be kept there, as in a full cache.
     */
    fun of(file: File, animations: Boolean = true): File? = made(file, animations, found = { it }) { kept, bytes ->
        directory.mkdirs()
        writeAtomically(kept, bytes)
        kept
    }

    /**
     * What the map's page gets in place of `file`: the picture [of] keeps, or,
     * when the cache can't keep it, full or unwritable, its bytes from
     * memory, made again each time it's asked for, so the page never goes
     * without. Null when there's none, as for [of]. Without `animations`, an
     * animation goes as it is, and only a HEIC or HEIF picture is made again,
     * as the audience's screen, which plays animations, gets them.
     */
    fun standIn(file: File, animations: Boolean = true): StandIn? = made(file, animations, found = StandIn::kept) { kept, bytes ->
        try {
            directory.mkdirs()
            writeAtomically(kept, bytes)
            StandIn.kept(kept)
        } catch (unwritable: IOException) {
            Logs.warn(TAG, "The cache couldn't keep a picture made for a page", unwritable)
            StandIn.held(kept.name, bytes)
        }
    }

    /**
     * The picture made of `file` for a page, as [of] says, an animation's
     * first frame only with `animations`: `found` when it's in the cache
     * already, else made and given to `keep` with the file it goes in there.
     */
    private fun <T> made(file: File, animations: Boolean, found: (File) -> T, keep: (File, ByteArray) -> T): T? {
        val extension = file.extension.lowercase()
        val heif = extension == "heic" || extension == "heif"

        if (!heif && (!animations || (extension != "gif" && extension != "webp"))) {
            return null
        }

        // Where the file is can't be told, as when it went meanwhile: it goes as it is, or is missing.
        val version = versionOf(file) ?: return null
        val made = File(directory, "$version.${if (heif) "jpg" else "png"}")

        if (made.isFile) {
            return found(made)
        }

        val moves = heif || animated.getOrPut(version) { runCatching { file.inputStream().buffered().use(::animationExtension) != null }.getOrDefault(false) }

        if (!moves) {
            return null
        }

        val bytes = (if (heif) drawn(file) else frame(file)) ?: return null
        return keep(made, bytes)
    }

    /** What tells this version of `file` from any other: where it is, how long it is, and when it last changed. Null when where it is can't be told. */
    private fun versionOf(file: File): String? {
        val path = try {
            file.canonicalPath
        } catch (unknown: IOException) {
            return null
        }

        val digest = MessageDigest.getInstance("SHA-256").digest("$path\n${file.length()}\n${file.lastModified()}".toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "OrbitFirstFrames"
    }
}

/**
 * An animation's first frame, as Android's own decoder reads it (the way up
 * its file says, and its transparency), as a PNG of at most
 * [FIRST_FRAME_SIZE] pixels on its longer side. Null when it can't be read.
 */
internal fun firstFramePng(file: File): ByteArray? = runCatching {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)

        if (longest > FIRST_FRAME_SIZE) {
            val scale = FIRST_FRAME_SIZE.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }

        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
}.getOrNull()

/**
 * A picture as a JPEG of at most [FIRST_FRAME_SIZE] pixels on its longer
 * side, the way up its file says, as pictures from the phone are kept. Null
 * when Android can't read it.
 */
internal fun pictureJpeg(file: File): ByteArray? = runCatching {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)

        if (longest > FIRST_FRAME_SIZE) {
            val scale = FIRST_FRAME_SIZE.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }

        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
        bitmap.recycle()
        output.toByteArray()
    }
}.getOrNull()
