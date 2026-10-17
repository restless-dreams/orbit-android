package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.NumberFormat
import kotlin.math.ceil

/**
 * The most a video from the phone may be. A file in a map's zip may be up to
 * [ZIP_ENTRY_LIMIT], and nearby devices take maps of up to 64 MiB: a video
 * this size leaves room for the map's text and pictures, so a map with one
 * still goes straight to another device, and its zip always comes back in.
 */
const val VIDEO_SIZE_LIMIT = 50_000_000L

/**
 * The most a map's pictures and videos may come to together. A zip of one
 * map unpacks to at most [BUNDLE_SIZE], its text included, so a map within
 * this limit can always be shared as a file and brought back.
 */
const val MAP_MEDIA_LIMIT = 90_000_000L

/** Why a picture or video can't join a map, in words for people. */
class MediaRefusedException(message: String) : IOException(message)

/**
 * The extension a video from the phone is kept with: by its media type, or
 * by its name when the type says nothing. Null for a video Orbit can't keep
 * in a map's folder.
 */
fun videoExtension(mimeType: String?, displayName: String?): String? {
    val type = mimeType?.substringBefore(';')?.trim()?.lowercase()

    val byType = when (type) {
        "video/mp4" -> "mp4"
        "video/x-m4v" -> "m4v"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> null
    }

    if (byType != null) {
        return byType
    }

    val unsaid = type == null || type == "application/octet-stream" || type == "video/*"
    val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return extension.takeIf { unsaid && it.isNotEmpty() && isVideoName("video.$it") }
}

/** Why a video of `size` bytes can't join a map whose pictures and videos come to `mapMedia`, in `words`, or null when it can. */
fun videoRefusal(size: Long, mapMedia: Long, words: Words): String? = when {
    size > VIDEO_SIZE_LIMIT -> words.string(R.string.data_video_too_big, megabytes(size, words), megabytes(VIDEO_SIZE_LIMIT, words))
    mapMedia + size > MAP_MEDIA_LIMIT -> words.string(R.string.data_video_past_map_room, megabytes(mapMedia, words), megabytes(MAP_MEDIA_LIMIT, words))
    else -> null
}

/** Why a picture of `size` bytes can't join a map whose pictures and videos come to `mapMedia`, in `words`, or null when it can. */
fun pictureRefusal(size: Long, mapMedia: Long, words: Words): String? =
    if (mapMedia + size > MAP_MEDIA_LIMIT) {
        words.string(R.string.data_picture_past_map_room, megabytes(mapMedia, words))
    } else {
        null
    }

/**
 * The most a picture or video from elsewhere, such as the folder maps are
 * kept in, may be to join a map whose pictures and videos come to `mapMedia`:
 * no more than one from the phone may be.
 */
fun mediaRoom(mapMedia: Long): Long = minOf(VIDEO_SIZE_LIMIT, MAP_MEDIA_LIMIT - mapMedia).coerceAtLeast(0)

/** Passes bytes on to `output` until they come to more than `limit`, and then refuses them with a [MediaRefusedException] saying so in `words`. */
internal class CappedOutput(private val output: OutputStream, private val limit: Long, private val words: Words) : OutputStream() {
    private var written = 0L

    override fun write(byte: Int) {
        take(1)
        output.write(byte)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        take(length)
        output.write(bytes, offset, length)
    }

    private fun take(count: Int) {
        written += count

        if (written > limit) {
            throw MediaRefusedException(words.string(R.string.data_more_than_a_map_keeps))
        }
    }
}

/** A size as people read it: "64.5 MB", "50 MB", in the phone's own way of writing numbers and the unit as `words` write it. */
fun megabytes(bytes: Long, words: Words): String {
    val tenths = ceil(bytes / 100_000.0)
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    return words.string(R.string.data_megabytes, format.format(tenths / 10))
}

/**
 * Copies a video into `output`, telling `onProgress` how far along it is,
 * from 0 to 1, when its `size` is known (0 or less when it isn't). A video
 * that turns out bigger than [VIDEO_SIZE_LIMIT], whatever size it said it
 * was, is refused, saying why in `words`. `keepGoing` throws to stop the
 * copy, as a cancelled coroutine does.
 */
internal fun copyVideo(input: InputStream, output: OutputStream, size: Long, words: Words, onProgress: (Float) -> Unit, keepGoing: () -> Unit = {}) {
    val buffer = ByteArray(COPY_BUFFER)
    var copied = 0L
    var reported = -1

    while (true) {
        keepGoing()
        val read = input.read(buffer)

        if (read < 0) {
            break
        }

        copied += read

        if (copied > VIDEO_SIZE_LIMIT) {
            throw MediaRefusedException(words.string(R.string.data_video_past_limit, megabytes(VIDEO_SIZE_LIMIT, words)))
        }

        output.write(buffer, 0, read)

        if (size > 0) {
            // Each whole percent once, rather than every few kilobytes.
            val percent = (copied * 100 / size).toInt().coerceAtMost(100)

            if (percent != reported) {
                reported = percent
                onProgress(percent / 100f)
            }
        }
    }
}

private const val COPY_BUFFER = 256 * 1024
