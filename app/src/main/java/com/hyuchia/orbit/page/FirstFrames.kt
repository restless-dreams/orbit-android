package com.hyuchia.orbit.page

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.hyuchia.orbit.data.animationExtension
import com.hyuchia.orbit.data.writeAtomically
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** An animation's first frame is kept at most this many pixels on its longer side, as pictures from the phone are. */
private const val FIRST_FRAME_SIZE = 2048

/**
 * The first frames of a map's animated pictures, GIFs and WebPs that move,
 * for the map's page: a planet that moved would keep the page drawing for as
 * long as it's on screen, and the battery with it, whatever Android's
 * animations say. Its details and full screen play it. Each is made once,
 * as a PNG in `directory`, by `frame`, for the file as it is: one that
 * changes gets a new one. Pictures that stand still are no business of it,
 * but for HEIC and HEIF ones, as iPhones and some Samsungs take them: the
 * page's Chromium doesn't draw those, so they go as a JPEG `drawn` makes,
 * the same way. Pictures from the phone are made JPEGs as they come in;
 * these come through the folder maps are kept in, or a zip.
 */
internal class FirstFrames(
    private val directory: File,
    private val drawn: (File) -> ByteArray? = ::pictureJpeg,
    private val frame: (File) -> ByteArray? = ::firstFramePng,
) {
    /** Which files, as they are, are animations: so a still GIF or WebP is read through only once. */
    private val animations = ConcurrentHashMap<String, Boolean>()

    /**
     * The first frame of `file`, as a PNG, or a HEIC or HEIF picture as a
     * JPEG; null when it's neither, or can't be read.
     */
    fun of(file: File): File? {
        val extension = file.extension.lowercase()

        if (extension == "heic" || extension == "heif") {
            return rendition(file)
        }

        if (extension != "gif" && extension != "webp") {
            return null
        }

        val version = versionOf(file)
        val still = File(directory, "$version.png")

        if (still.isFile) {
            return still
        }

        val moves = animations.getOrPut(version) { runCatching { file.inputStream().buffered().use(::animationExtension) != null }.getOrDefault(false) }

        if (!moves) {
            return null
        }

        val png = frame(file) ?: return null
        directory.mkdirs()
        writeAtomically(still, png)
        return still
    }

    private fun rendition(file: File): File? {
        val drawable = File(directory, "${versionOf(file)}.jpg")

        if (drawable.isFile) {
            return drawable
        }

        val jpeg = drawn(file) ?: return null
        directory.mkdirs()
        writeAtomically(drawable, jpeg)
        return drawable
    }

    /** What tells this version of `file` from any other: where it is, how long it is, and when it last changed. */
    private fun versionOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${file.canonicalPath}\n${file.length()}\n${file.lastModified()}".toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
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
