package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.NumberFormat
import kotlin.math.ceil

/**
 * The most one of a map's files may be: a video or a sound from the phone,
 * an animated GIF or WebP kept as it is, a live photo's picture with its
 * motion, or one that comes from the folder maps are kept in. A file in a
 * map's zip may be up to [ZIP_ENTRY_LIMIT], so one this size always goes in
 * its map's zip, and comes back from it.
 */
const val MEDIA_FILE_LIMIT = 250_000_000L

/**
 * The most a map's pictures, videos and sounds may come to together. A zip
 * of one map unpacks to at most [BUNDLE_SIZE], its text included, and nearby
 * devices take maps of up to `OFFER_LIMIT`, so a map within this limit can
 * always be shared as a file, sent to a device and brought back: each way
 * streams it through files a piece at a time, never whole in memory.
 */
const val MAP_MEDIA_LIMIT = 1_000_000_000L

/**
 * What Orbit 0.4.0, and every release before it, keeps: a video or a sound
 * of up to 50 MB, and 90 MB of a map's pictures, videos and sounds together.
 * In the folder maps are kept in, those versions keep what's past that only
 * in the folder, and a device's record there says what it keeps
 * ([FolderDevice.mediaFileLimit]): one from before says nothing.
 */
const val MEDIA_FILE_LIMIT_UP_TO_0_4_0 = 50_000_000L
const val MAP_MEDIA_LIMIT_UP_TO_0_4_0 = 90_000_000L

/**
 * The most a web page of a map carries of its pictures, as their files are,
 * each as many times as the map shows it: more than a map could hold in all
 * before maps took a gigabyte, so every map whose page carried all its
 * pictures still does, and a page stays one a browser opens. Its sounds and
 * motions have budgets of their own ([PAGE_SOUNDS_LIMIT], [PAGE_MOTIONS_LIMIT]).
 */
internal const val PAGE_PICTURES_LIMIT = 100L * 1024 * 1024

/**
 * The pictures of the map in `mapFolder` that go inside its web page, by
 * name: those `markdown` shows, in its order, while they come to at most
 * `limit` bytes, each as many times as it's shown. One past what's left
 * stays out, and a smaller one after it may still go: the page shows its
 * idea without it, and Export says how many stay on this device.
 */
internal fun picturesFitting(markdown: String, mapFolder: File, limit: Long = PAGE_PICTURES_LIMIT): Set<String> {
    val media = File(mapFolder, MEDIA_DIRECTORY)
    // What the map shows, not the files its ideas hold, which a page lists without carrying.
    val shown = MediaNames.places(withoutFiles(markdown)).map { it.groupValues[2] }.filter { !isVideoName(it) && !isSoundName(it) }.groupingBy { it }.eachCount()
    var room = limit
    val fitting = LinkedHashSet<String>()

    for ((name, times) in shown) {
        val file = File(media, name).takeIf { it.isFile && isMediaName(name) } ?: continue
        val takes = file.length() * times

        if (takes <= room) {
            room -= takes
            fitting += name
        }
    }

    return fitting
}

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
    size > MEDIA_FILE_LIMIT -> words.string(R.string.data_video_too_big, readableSize(size, words), readableSize(MEDIA_FILE_LIMIT, words))
    mapMedia + size > MAP_MEDIA_LIMIT -> words.string(R.string.data_video_past_map_room, readableSize(mapMedia, words), readableSize(MAP_MEDIA_LIMIT, words))
    else -> null
}

/** What a sketch drawn in Orbit is named: `sketch-` and the moment, in the map's `media/` (see sketch/SketchSvg.kt). */
internal const val SKETCH_PREFIX = "sketch-"

/** Why a picture of `size` bytes can't join a map whose pictures and videos come to `mapMedia`, in `words`, or null when it can. */
fun pictureRefusal(size: Long, mapMedia: Long, words: Words): String? =
    if (mapMedia + size > MAP_MEDIA_LIMIT) {
        words.string(R.string.data_picture_past_map_room, readableSize(mapMedia, words))
    } else {
        null
    }

/**
 * The most a picture or video from elsewhere, such as the folder maps are
 * kept in, may be to join a map whose pictures and videos come to `mapMedia`:
 * no more than one from the phone may be.
 */
fun mediaRoom(mapMedia: Long): Long = minOf(MEDIA_FILE_LIMIT, MAP_MEDIA_LIMIT - mapMedia).coerceAtLeast(0)

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

/**
 * A size as people read it: "64.5 MB", "50 MB", and from 1,000 MB on in
 * gigabytes, "1 GB", "1.2 GB", each rounded up to its tenth, in the phone's
 * own way of writing numbers and the units as `words` write them. It never
 * breaks between the number and its unit, wherever a line ends.
 */
fun readableSize(bytes: Long, words: Words): String {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    val tenths = ceil(bytes / 100_000.0)

    val size = if (tenths < GIGABYTE_TENTHS) {
        words.string(R.string.data_megabytes, format.format(tenths / 10))
    } else {
        words.string(R.string.data_gigabytes, format.format(ceil(bytes / 100_000_000.0) / 10))
    }

    return size.replace(' ', '\u00A0')
}

/** A thousand megabytes, in tenths of a megabyte: sizes from there on read in gigabytes. */
private const val GIGABYTE_TENTHS = 10_000.0

/**
 * Copies a video into `output`, telling `onProgress` how far along it is,
 * from 0 to 1, when its `size` is known (0 or less when it isn't). A video
 * that turns out bigger than [MEDIA_FILE_LIMIT], or than what's left of the
 * room of a map whose pictures, videos and sounds come to `mapMedia`
 * ([MAP_MEDIA_LIMIT]), whatever size it said it was, is refused as soon as
 * it does, saying why in `words`. `keepGoing` throws to stop the copy, as a
 * cancelled coroutine does.
 */
internal fun copyVideo(input: InputStream, output: OutputStream, size: Long, mapMedia: Long, words: Words, onProgress: (Float) -> Unit, keepGoing: () -> Unit = {}) {
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

        if (copied > MEDIA_FILE_LIMIT) {
            throw MediaRefusedException(words.string(R.string.data_video_past_limit, readableSize(MEDIA_FILE_LIMIT, words)))
        }

        if (mapMedia + copied > MAP_MEDIA_LIMIT) {
            throw MediaRefusedException(words.string(R.string.data_video_past_map_room, readableSize(mapMedia, words), readableSize(MAP_MEDIA_LIMIT, words)))
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
