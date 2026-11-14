package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.hyuchia.orbit.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.util.UUID
import kotlin.math.max

/** The copies let start by the copy into a map running on this thread ([MapMediaIntake.intake]), which follow it from thread to thread. */
private val admitted = ThreadLocal<MutableList<IncomingMedia.Arrival>?>()

/** Pictures are kept at most this many pixels on their longer side. */
private const val PICTURE_SIZE = 2048

/** Where pictures and movies coming in are copied to be read whole, beside the maps: emptied as the maps load. */
internal const val INCOMING_DIRECTORY = "incoming"

/**
 * The most a picture or movie coming in is copied to be read: far more than
 * any a phone takes, and more than a file of a map's may be
 * ([MEDIA_FILE_LIMIT]), so a live photo that fits one is always read whole.
 */
internal const val INCOMING_LIMIT = 256L * 1024 * 1024

/**
 * The most a movie is read to tell whether it's a live photo's: an iPhone's
 * is a few megabytes. It stays as it is while maps take bigger files: a
 * longer video is a video, and isn't copied to be read.
 */
private const val LIVE_MOVIE_MOST = 64L * 1024 * 1024

/** A video's poster is at most this many pixels on its longer side: enough for a planet, and for the viewer until the video plays. */
private const val POSTER_SIZE = 1280

/** A video's poster comes from a tenth of the way in, and never later than this. */
private const val POSTER_AT_MOST_MILLISECONDS = 2_000L

/** Room on the device that copying a video in always leaves free. */
private const val VIDEO_SPARE_ROOM = 64L * 1024 * 1024

/**
 * A video copied into a map's folder, and its poster beside it when the
 * phone could read a frame, as the map points at them; `bytes` is how big
 * one made smaller came out.
 */
data class AddedVideo(val src: String, val thumbnail: String?, val bytes: Long = 0)

/**
 * A picture copied into a map's folder, as the map points at it, and the
 * motion a live photo brought with it (see [LivePhotos]). `motionLeftOut`
 * says a live photo's motion would have taken it past what a picture and
 * its motion may come to, so the picture came alone.
 */
data class AddedPicture(val src: String, val motion: AddedMotion? = null, val motionLeftOut: Boolean = false, val fromIphone: Boolean = false)

/**
 * A file copied into a map's folder for one of its ideas, as its `files`
 * entry gives it: where the map points at it (`media/file-…`), its name as
 * it came, its type and its size.
 */
data class AddedFile(val src: String, val name: String, val type: String?, val size: Long)

/** A live photo's motion, as the map points at it (`media/…-motion.mp4`), and where in it the still was taken, in seconds, when its file said. */
data class AddedMotion(val src: String, val still: Double? = null)

/** A live photo's motion to keep beside its still: `length` bytes that `copy` writes, and where in them the still was taken, in seconds. */
private class MotionToKeep(val length: Long, val still: Double?, val extension: String, val copy: (OutputStream) -> Unit)

/** A sound copied into a map's folder, as the map points at it, and a caption from its file's name when that says something. */
data class AddedSound(val src: String, val caption: String?)

/**
 * Ideas copied in one map, ready to paste into another ([MapMediaIntake.copyMedia]):
 * their `text`, which points at each of their pictures, videos and sounds
 * where the other map keeps it, and the files that were copied into it for
 * them (`media/…`), each new to it: what goes again if the paste doesn't
 * happen after all. A file it had already, the very same, isn't among them.
 */
data class CopiedMedia(val text: String, val copied: List<String> = emptyList())

/**
 * Pictures, live photos, videos and sounds coming into a map's folder: each
 * copied in under a name of Orbit's own, within what a map may hold, and
 * refused with a [MediaRefusedException] saying why past it.
 */
internal class MapMediaIntake(
    store: MapStore,

    /** How many bytes can still be written where a file is, for a video from the phone: the system knows best, cache it can clear included. */
    private val room: (File) -> Long,
) : MapPart(store) {
    /**
     * Copies a picture into the map's folder, at most [PICTURE_SIZE] pixels
     * on its longer side and in a format every browser reads (JPEG, or PNG
     * with transparency). An animation, a GIF or WebP that moves, is kept as
     * it is, up to [MEDIA_FILE_LIMIT]: drawn again, it would keep only its
     * first frame, as a bigger one does. A live photo that keeps its motion
     * in its file, as Android phones do ([LivePhotos]), comes as two: the
     * picture, and its motion beside it as the MP4 it is. Returns where the
     * map points at them: `media/…`.
     */
    suspend fun addPicture(id: String, uri: Uri, resolver: ContentResolver): AddedPicture = intake {
        checkWritable()

        val animation = resolver.openInputStream(uri)?.buffered()?.use(::animationExtension)

        if (animation != null) {
            addAnimation(id, uri, resolver, animation)?.let { return@intake AddedPicture(it) }
        }

        // The picture's own file, copied first: a live photo's motion is read out of it.
        val copy = incomingCopy(resolver, uri, "picture")

        try {
            val embedded = LivePhotos.motionIn(copy)

            val motion = embedded?.let {
                MotionToKeep(it.length, it.stillMicros?.let { micros -> micros / 1_000_000.0 }, "mp4") { output -> LivePhotos.copyMotion(copy, it, output) }
            }

            // An iPhone keeps a live photo's motion in a movie of its own, which may come next ([addMotion]).
            val iphone = embedded == null && LivePhotos.stillFacts(copy)?.maker.equals("Apple", ignoreCase = true)
            addStill(id, ImageDecoder.createSource(copy), motion).copy(fromIphone = iphone)
        } finally {
            copy.delete()
        }
    }

    /**
     * Copies an iPhone's live photo into the map's folder: its still, as any
     * picture, and its movie beside it as an MP4, its video as it was and its
     * sound made AAC, so browsers play it (see [MotionVideos]). A movie that
     * can't be made one stays the QuickTime movie it is.
     */
    suspend fun addLivePhoto(id: String, still: Uri, movie: Uri, resolver: ContentResolver): AddedPicture = intake {
        checkWritable()

        val picture = incomingCopy(resolver, still, "picture")

        // The picture's copy goes whatever happens next, a movie that can't be copied included.
        try {
            val video = incomingCopy(resolver, movie, "movie")
            val made = File(video.parentFile, "${video.name}.mp4")

            try {
                val facts = LivePhotos.liveVideo(video)
                val kept = if (MotionVideos.toMp4(video, made)) made else video
                val extension = if (kept == made) "mp4" else "mov"
                val motion = MotionToKeep(kept.length(), facts?.stillSeconds, extension) { output -> kept.inputStream().use { it.copyTo(output) } }
                addStill(id, ImageDecoder.createSource(picture), motion)
            } finally {
                video.delete()
                made.delete()
            }
        } finally {
            picture.delete()
        }
    }

    /**
     * Gives the map's picture `picture` (`media/…`) its live photo's motion:
     * an iPhone's movie, kept beside it as [addLivePhoto] keeps one, within
     * what a picture and its motion may come to together.
     */
    suspend fun addMotion(id: String, picture: String, movie: Uri, resolver: ContentResolver): AddedMotion = intake {
        checkWritable()

        val media = mediaFolder(id)
        val still = fileInside(media, picture.removePrefix("$MEDIA_DIRECTORY/"), words)
        val video = incomingCopy(resolver, movie, "movie")
        val made = File(video.parentFile, "${video.name}.mp4")

        try {
            val facts = LivePhotos.liveVideo(video)
            val kept = if (MotionVideos.toMp4(video, made)) made else video
            val stillSize = if (still.isFile) still.length() else 0L

            // The picture counts once, with its motion: the map's own media, which have it already, are counted without it.
            val arrival = admit(id, except = still.name) { mapMedia ->
                videoRefusal(stillSize + kept.length(), mapMedia, words)?.let { throw MediaRefusedException(it) }
                kept.length()
            }

            arrival.use {
                // A motion added again doesn't go over the one before it, which earlier versions of the map may point at.
                val file = File(media, motionName(arrival, media, still.nameWithoutExtension, if (kept == made) "mp4" else "mov"))
                writeNew(file) { output -> kept.inputStream().use { it.copyTo(output) } }
                arrived(arrival, listOf(file))
                AddedMotion("$MEDIA_DIRECTORY/${file.name}", facts?.stillSeconds)
            }
        } finally {
            video.delete()
            made.delete()
        }
    }

    /** What an incoming picture or movie says about the live photo it may be half of, read from a copy of its file. */
    suspend fun liveHalf(uri: Uri, resolver: ContentResolver, movie: Boolean): Pair<StillFacts?, LiveVideo?> = withContext(Dispatchers.IO) {
        // A live photo's movie is a few seconds: a longer video isn't copied to be read.
        val size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L

        if (movie && size > LIVE_MOVIE_MOST) {
            return@withContext null to null
        }

        val copy = runCatching { incomingCopy(resolver, uri, if (movie) "movie" else "picture") }.getOrNull() ?: return@withContext null to null

        try {
            if (movie) null to LivePhotos.liveVideo(copy) else LivePhotos.stillFacts(copy) to null
        } finally {
            copy.delete()
        }
    }

    /**
     * Copies the pictures, videos and sounds `markdown` points to (`media/…`)
     * from map `from` into map `to`, for ideas copied in one map and pasted
     * into the other, and gives back the text to paste, which points at each
     * where `to` keeps it. A name `to` already uses, for a file in its folder
     * or one its text points at, stays `to`'s: the pasted file goes under it
     * only when it's the very file there, as one pasted before is, and
     * otherwise under a name of its own (`photo-2.jpg`). Two maps from zips
     * can each have their "photo.jpg": the pasted ideas show theirs, and
     * `to`'s keep theirs. As adding them one by one would be, it's refused
     * with a [MediaRefusedException] past `to`'s room, or without room for
     * them on the device; a copy that fails all the same, as when something
     * else fills the disk meanwhile, takes the ones before it away again.
     */
    suspend fun copyMedia(from: String, to: String, markdown: String): CopiedMedia = intake {
        if (from == to || !exists(from) || !exists(to)) {
            return@intake CopiedMedia(markdown)
        }

        checkWritable()

        val source = File(folder(from), MEDIA_DIRECTORY)
        val target = File(folder(to), MEDIA_DIRECTORY)
        val names = (MediaNames.references(markdown) + fileNamesIn(markdown)).distinct()
        // What `to`'s text points at: a file still on its way there, from the folder maps are kept in, keeps its name too.
        val toText = runCatching { File(folder(to), MAP_FILE).readTextSafely() }.getOrDefault("")
        val pointedAt = (MediaNames.references(toText) + fileNamesIn(toText)).toSet()
        // The names a file pasted under a name of its own can't take: `to`'s, and the ones the pasted text points at.
        val taken = (target.list().orEmpty().toSet() + pointedAt + names).toMutableSet()
        val renamed = mutableMapOf<String, String>()
        val copies = mutableListOf<Pair<File, String>>()

        // The names they go under are held until they're in, so a picture or a file coming into `to` meanwhile gets one of its own.
        store.incoming.names(to).use { holding ->
            for (name in names) {
                val file = try {
                    fileInside(source, name, words)
                } catch (outside: RefusedZipException) {
                    null
                }

                if (file == null || !file.isFile) {
                    continue
                }

                when {
                    name !in pointedAt && holding.hold(target, name) -> copies += file to name
                    sameBytes(file, File(target, name)) -> Unit
                    else -> {
                        val free = numbered(name).first { it !in taken && holding.hold(target, it) }
                        taken += free
                        renamed[name] = free
                        copies += file to free
                    }
                }
            }

            val size = copies.sumOf { (file, _) -> file.length() }

            val arrival = admit(to) { mapMedia ->
                pictureRefusal(size, mapMedia, words)?.let { throw MediaRefusedException(it) }
                size
            }

            arrival.use {
                // Room on the device for them all, with some to spare, before the first is copied: a paste never fills the disk.
                if (size > 0 && room(folder(to)) < size + VIDEO_SPARE_ROOM) {
                    throw MediaRefusedException(words.string(R.string.data_no_room_for_pasted_media))
                }

                val written = mutableListOf<File>()
                var whole = false

                try {
                    for ((file, name) in copies) {
                        val copy = fileInside(target, name, words)
                        writeNew(copy) { output -> file.inputStream().use { it.copyTo(output) } }
                        written += copy
                    }

                    whole = true
                } finally {
                    // One that couldn't be copied, as on a full disk, pastes nothing: the copies made before it go, each new to `to`.
                    if (!whole) {
                        written.forEach { it.delete() }
                    }
                }

                arrived(arrival, written)
            }
        }

        val text = MediaNames.replaced(markdown) { place -> renamed[place.groupValues[2]]?.let { "${place.groupValues[1]}$MEDIA_DIRECTORY/$it" } }
        // Here, every one was copied: one that couldn't be took the others away again, and pasted nothing.
        CopiedMedia(text, copies.map { (_, name) -> "$MEDIA_DIRECTORY/$name" })
    }

    /**
     * Copies a video from the phone into the map's folder as it is, under a
     * name of Orbit's own with the extension its type has, and puts a frame
     * from it beside it as a JPEG, for the map to show as its poster. A video
     * past [MEDIA_FILE_LIMIT], or one that would take the map's pictures and
     * videos past [MAP_MEDIA_LIMIT], is refused with a [MediaRefusedException]
     * saying why. `onProgress` hears how far the copy is, from 0 to 1.
     */
    suspend fun addVideo(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedVideo = intake {
        checkWritable()

        val (displayName, declaredSize) = nameAndSize(uri, resolver)

        val type = resolver.getType(uri)
        val extension = videoExtension(type, displayName) ?: throw MediaRefusedException(
            if (type != null) {
                words.string(R.string.data_video_kind_refused, type)
            } else {
                words.string(R.string.data_video_kind_unknown)
            },
        )
        val size = declaredSize.takeIf { it >= 0 } ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

        val arrival = admit(id) { mapMedia ->
            if (size >= 0) {
                videoRefusal(size, mapMedia, words)?.let { throw MediaRefusedException(it) }
            }

            maxOf(size, 0L)
        }

        arrival.use {
            val media = mediaFolder(id)
            media.mkdirs()

            if (room(media) < maxOf(size, 0L) + VIDEO_SPARE_ROOM) {
                throw MediaRefusedException(words.string(R.string.data_no_room_for_video))
            }

            val stem = arrival.stem(media, numberedStems("video-${clock().toString(36)}"))
            val video = File(media, "$stem.$extension")
            val context = currentCoroutineContext()

            // Within the map's room as it's copied, whatever size it said it was: nothing past it is kept.
            writeNew(video) { output ->
                val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the video")
                input.use { copyVideo(it, output, size, arrival.mapMedia, words, onProgress) { context.ensureActive() } }
            }

            val poster = runCatching { writePoster(video, File(media, "$stem.jpg")) }.getOrNull()
            // As it came in, beside what else came in meanwhile.
            arrived(arrival, listOfNotNull(video, poster)) { mapMedia -> videoRefusal(video.length(), mapMedia, words) }
            AddedVideo("$MEDIA_DIRECTORY/${video.name}", poster?.let { "$MEDIA_DIRECTORY/${it.name}" })
        }
    }

    /**
     * A video from the phone made smaller into the map's folder, as an MP4
     * under a name of Orbit's own, with a frame from it beside it as its
     * poster: `make` makes it into the file it's given, which takes its
     * place only once it's whole. One about `estimate` bytes that wouldn't
     * fit what a map keeps ([MEDIA_FILE_LIMIT], the map's room), or the room
     * on the device, is refused before it's made, with a
     * [MediaRefusedException], and so is one that comes out past them.
     * Nothing of one that isn't made whole, or is stopped, stays.
     */
    suspend fun addMadeVideo(id: String, estimate: Long, make: suspend (File) -> Unit): AddedVideo = intake {
        checkWritable()

        val arrival = admit(id) { mapMedia ->
            videoRefusal(estimate, mapMedia, words)?.let { throw MediaRefusedException(it) }
            estimate
        }

        arrival.use {
            val media = mediaFolder(id)
            media.mkdirs()

            if (room(media) < estimate + VIDEO_SPARE_ROOM) {
                throw MediaRefusedException(words.string(R.string.data_no_room_for_video))
            }

            val stem = arrival.stem(media, numberedStems("video-${clock().toString(36)}"))
            val video = File(media, "$stem.mp4")
            val made = partialFile(video)

            try {
                make(made)
                // As it came out: the estimate was only that.
                videoRefusal(made.length(), mediaSize(id, besides = arrival), words)?.let { throw MediaRefusedException(it) }
                putInPlace(made, video, replace = false)
            } finally {
                made.delete()
            }

            val poster = runCatching { writePoster(video, File(media, "$stem.jpg")) }.getOrNull()
            arrived(arrival, listOfNotNull(video, poster)) { mapMedia -> videoRefusal(video.length(), mapMedia, words) }
            AddedVideo("$MEDIA_DIRECTORY/${video.name}", poster?.let { "$MEDIA_DIRECTORY/${it.name}" }, video.length())
        }
    }

    /**
     * Copies a sound from the phone into the map's folder as it is, under a
     * name of Orbit's own with the extension its type has (an `.m4a` as the
     * `.mp4` it is). One past [MEDIA_FILE_LIMIT], or one that would take the
     * map's pictures, videos and sounds past [MAP_MEDIA_LIMIT], is refused
     * with a [MediaRefusedException] saying why. `onProgress` hears how far
     * the copy is, from 0 to 1.
     */
    suspend fun addSound(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedSound = intake {
        checkWritable()

        val (displayName, declaredSize) = nameAndSize(uri, resolver)

        val type = resolver.getType(uri)
        val input = resolver.openInputStream(uri)?.buffered() ?: throw IOException("Couldn't open the sound")

        val sound = input.use {
            val extension = soundExtension(type, displayName, input.peek(SOUND_HEAD)) ?: throw MediaRefusedException(
                if (type != null) {
                    words.string(R.string.data_sound_kind_refused, type)
                } else {
                    words.string(R.string.data_sound_kind_unknown)
                },
            )
            val size = declaredSize.takeIf { it >= 0 } ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

            val arrival = admit(id) { mapMedia ->
                if (size >= 0) {
                    soundRefusal(size, mapMedia, words)?.let { throw MediaRefusedException(it) }
                }

                maxOf(size, 0L)
            }

            arrival.use {
                val media = mediaFolder(id)
                media.mkdirs()

                if (room(media) < maxOf(size, 0L) + VIDEO_SPARE_ROOM) {
                    throw MediaRefusedException(words.string(R.string.data_no_room_for_sound))
                }

                val sound = File(media, "${arrival.stem(media, numberedStems("$SOUND_PREFIX${clock().toString(36)}"))}.$extension")
                val context = currentCoroutineContext()
                // Within the map's room as it's copied, whatever size it said it was: nothing past it is kept.
                writeNew(sound) { output -> copySound(input, output, size, arrival.mapMedia, words, onProgress) { context.ensureActive() } }
                arrived(arrival, listOf(sound)) { mapMedia -> soundRefusal(sound.length(), mapMedia, words) }
                sound
            }
        }

        AddedSound("$MEDIA_DIRECTORY/${sound.name}", soundCaption(displayName))
    }

    /**
     * Copies a file from the phone into the map's folder for one of its
     * ideas, as it is, of any kind: a PDF, a spreadsheet, a ticket. It's
     * named `file-` and the name it came with, made safe
     * ([ideaFileName]), and given a free one when that's taken. One past
     * [MEDIA_FILE_LIMIT], or one that would take the map's pictures, videos,
     * sounds and files past [MAP_MEDIA_LIMIT], is refused with a
     * [MediaRefusedException] saying why, before it's copied when its size
     * is known, and as soon as it's past either otherwise; nothing of it
     * stays. `onProgress` hears how far the copy is, from 0 to 1.
     */
    suspend fun addFile(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedFile = intake {
        checkWritable()

        val (displayName, declaredSize) = nameAndSize(uri, resolver)
        val type = resolver.getType(uri)?.takeIf { it.contains('/') && it != "application/octet-stream" }
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: words.string(R.string.files_kind_other)
        val input = resolver.openInputStream(uri)?.buffered() ?: throw IOException("Couldn't open the file")

        val file = input.use {
            val size = declaredSize.takeIf { it >= 0 } ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

            val arrival = admit(id) { mapMedia ->
                if (size >= 0) {
                    fileRefusal(size, mapMedia, words)?.let { throw MediaRefusedException(it) }
                }

                maxOf(size, 0L)
            }

            arrival.use {
                val media = mediaFolder(id)
                media.mkdirs()

                if (room(media) < maxOf(size, 0L) + VIDEO_SPARE_ROOM) {
                    throw MediaRefusedException(words.string(R.string.data_no_room_for_file))
                }

                val stored = ideaFileName(name, type)
                val file = File(media, "${arrival.stem(media, numberedStems(stored.substringBeforeLast('.')))}${stored.extensionPart()}")
                val context = currentCoroutineContext()
                // Within the map's room as it's copied, whatever size it said it was: nothing past it is kept.
                writeNew(file) { output -> copyFile(input, output, size, arrival.mapMedia, words, onProgress) { context.ensureActive() } }
                arrived(arrival, listOf(file)) { mapMedia -> fileRefusal(file.length(), mapMedia, words) }
                file
            }
        }

        AddedFile("$MEDIA_DIRECTORY/${file.name}", name, type, file.length())
    }

    /**
     * Keeps a voice memo recorded in Orbit, `recording`, in the map's folder
     * as `memo-` and the moment, an `.mp4` every release carries. Refused,
     * and left where it is, with a [MediaRefusedException] past the map's
     * room. Returns where the map points at it.
     */
    suspend fun addRecording(id: String, recording: File): String = intake {
        checkWritable()
        val size = recording.length()

        val arrival = admit(id) { mapMedia ->
            soundRefusal(size, mapMedia, words)?.let { throw MediaRefusedException(it) }
            size
        }

        arrival.use {
            val media = mediaFolder(id)
            media.mkdirs()
            val memo = File(media, "${arrival.stem(media, numberedStems("$MEMO_PREFIX${clock().toString(36)}"))}.mp4")
            writeNew(memo) { output -> recording.inputStream().use { it.copyTo(output) } }
            arrived(arrival, listOf(memo))
            recording.delete()
            "$MEDIA_DIRECTORY/${memo.name}"
        }
    }

    /**
     * Keeps a sketch drawn in Orbit, `svg` (Orbit's own drawing, see
     * sketch/SketchSvg.kt), in the map's folder as `sketch-` and the moment, an
     * `.svg`, which every release carries and shows. A new file each time, so
     * earlier versions keep the sketch they point at. Refused with a
     * [MediaRefusedException] past the map's room. Returns where the map
     * points at it.
     */
    suspend fun addSketch(id: String, svg: ByteArray): String = intake {
        checkWritable()

        val arrival = admit(id) { mapMedia ->
            pictureRefusal(svg.size.toLong(), mapMedia, words)?.let { throw MediaRefusedException(it) }
            svg.size.toLong()
        }

        arrival.use {
            val media = mediaFolder(id)
            media.mkdirs()
            val sketch = File(media, "${arrival.stem(media, numberedStems("$SKETCH_PREFIX${clock().toString(36)}"))}.svg")
            writeNew(sketch, svg)
            arrived(arrival, listOf(sketch))
            "$MEDIA_DIRECTORY/${sketch.name}"
        }
    }

    /**
     * Takes back pictures, videos, sounds and files just copied into the
     * map's folder (`media/…`) that never joined it, as a picture a newer one
     * took the place of while it was copied in: they go, and take none of
     * its room. One the map's text points at stays, as does anything outside
     * its media folder. Under the lock, as every deletion of a map's files
     * is: Export all, which lists a map's files and opens them under it,
     * never finds one of them gone.
     */
    suspend fun takeBack(id: String, sources: List<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)
            val pointedAt = carriedNames(runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")).toSet()
            val media = File(folder, MEDIA_DIRECTORY)

            for (source in sources) {
                val name = source.removePrefix("$MEDIA_DIRECTORY/").takeIf { source.startsWith("$MEDIA_DIRECTORY/") && it !in pointedAt } ?: continue
                val file = runCatching { fileInside(media, name, words) }.getOrNull()
                file?.delete()
            }
        }
    }

    /**
     * Runs a copy into a map on the IO threads. Stopped as its files came
     * in, past the moment it would have noticed, the files are in but whoever
     * asked for them never hears of them: they're let go of, so they take
     * none of the map's room from then on, and stay for the map's tidying
     * up, as a drop stopped partway leaves them ([IncomingMedia.Arrival.letGo]).
     */
    private suspend fun <T> intake(copy: suspend CoroutineScope.() -> T): T {
        val arrivals = Collections.synchronizedList(mutableListOf<IncomingMedia.Arrival>())

        try {
            return withContext(Dispatchers.IO + admitted.asContextElement(arrivals), copy)
        } catch (stopped: CancellationException) {
            arrivals.forEach { it.letGo() }
            throw stopped
        }
    }

    /** A copy let start into map `id`, as [arriving] lets it, which the copy running it ([intake]) lets go of if it's stopped. */
    private fun admit(id: String, except: String? = null, take: (mapMedia: Long) -> Long): IncomingMedia.Arrival =
        arriving(id, except, take).also { admitted.get()?.add(it) }

    /** A copy of what `uri` holds, in a folder of its own beside the maps, to read it as a whole: the caller deletes it. */
    private fun incomingCopy(resolver: ContentResolver, uri: Uri, kind: String): File {
        val folder = File(directory.parentFile, INCOMING_DIRECTORY)
        folder.mkdirs()
        val copy = File(folder, "$kind-${UUID.randomUUID()}")

        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the $kind")
            input.use { source -> copy.outputStream().use { CappedOutput(it, INCOMING_LIMIT, words).let { capped -> source.copyTo(capped) } } }
        } catch (failed: Exception) {
            copy.delete()
            throw failed
        }

        return copy
    }

    /**
     * Writes a picture, as [addPicture] says, and its live photo's `motion`
     * beside it under the same name: both within what a video may be and the
     * map's room, counted together. A motion past them is left out, and the
     * picture comes alone.
     */
    private fun addStill(id: String, source: ImageDecoder.Source, motion: MotionToKeep?): AddedPicture {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)

            if (longest > PICTURE_SIZE) {
                val scale = PICTURE_SIZE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }

            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        val transparent = bitmap.hasAlpha()
        val media = mediaFolder(id)

        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, output)
            output.toByteArray()
        }

        bitmap.recycle()
        var withMotion: MotionToKeep? = null

        val arrival = admit(id) { mapMedia ->
            withMotion = motion?.takeIf { videoRefusal(bytes.size + it.length, mapMedia, words) == null }
            pictureRefusal(bytes.size.toLong(), mapMedia, words)?.let { throw MediaRefusedException(it) }
            bytes.size + (withMotion?.length ?: 0L)
        }

        arrival.use {
            // Two stills in one millisecond, as a drop of several, each get a name of their own.
            val stem = arrival.stem(media, numberedStems("picture-${clock().toString(36)}"))
            val name = "$stem.${if (transparent) "png" else "jpg"}"

            val kept = withMotion?.let { live ->
                val file = File(media, motionName(arrival, media, stem, live.extension))

                try {
                    writeNew(file) { output -> live.copy(output) }
                    file to AddedMotion("$MEDIA_DIRECTORY/${file.name}", live.still)
                } catch (unreadable: IOException) {
                    // The picture still comes, as one would without its motion; nothing of the motion was put in place, and a
                    // file that took its name meanwhile stays.
                    null
                }
            }

            val picture = File(media, name)
            writeNew(picture, bytes)
            arrived(arrival, listOfNotNull(picture, kept?.first))
            return AddedPicture("$MEDIA_DIRECTORY/$name", kept?.second, motionLeftOut = motion != null && kept == null)
        }
    }

    /**
     * Copies an animation from the phone into the map's folder as it is, with
     * `extension`, under a picture's name. Null for one past
     * [MEDIA_FILE_LIMIT], to be kept still; one past the map's room is
     * refused with a [MediaRefusedException], as any picture is.
     */
    private fun addAnimation(id: String, uri: Uri, resolver: ContentResolver, extension: String): String? {
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

        if (size > MEDIA_FILE_LIMIT) {
            return null
        }

        val arrival = admit(id) { mapMedia ->
            pictureRefusal(maxOf(size, 0L), mapMedia, words)?.let { throw MediaRefusedException(it) }
            maxOf(size, 0L)
        }

        arrival.use {
            val media = mediaFolder(id)
            media.mkdirs()
            val animation = File(media, "${arrival.stem(media, numberedStems("picture-${clock().toString(36)}"))}.$extension")

            try {
                writeNew(animation) { output ->
                    val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the picture")
                    input.use { it.copyTo(CappedOutput(output, MEDIA_FILE_LIMIT, words)) }
                }
            } catch (tooBig: MediaRefusedException) {
                // Its size wasn't known, and it came to more than an animation may be: nothing of it was kept.
                return null
            }

            // A size that wasn't known before the copy is checked against the map's room now; nothing of one past it stays.
            arrived(arrival, listOf(animation)) { mapMedia -> pictureRefusal(animation.length(), mapMedia, words) }
            return "$MEDIA_DIRECTORY/${animation.name}"
        }
    }

    /** A frame from early in the video, at most [POSTER_SIZE] pixels on its longer side, written to `to`; null when the phone can't read the video. */
    private fun writePoster(video: File, to: File): File? {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(video.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // A moment in, where there's more to see than a first frame often shows, but never far into a short clip.
            val at = (duration / 10).coerceAtMost(POSTER_AT_MOST_MILLISECONDS) * 1_000
            val frame = retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, POSTER_SIZE, POSTER_SIZE) ?: return null

            val bytes = ByteArrayOutputStream().use { output ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, output)
                output.toByteArray()
            }

            frame.recycle()
            writeNew(to, bytes)
            return to
        } finally {
            retriever.release()
        }
    }

    /**
     * The map's media folder, for a picture, video or sound to join it.
     * Throws when the map isn't here, as one moved to the trash meanwhile:
     * its media alone would come back as a folder that isn't a map.
     */
    private fun mediaFolder(id: String): File {
        val folder = folder(id)

        if (!File(folder, MAP_FILE).isFile) {
            throw IOException("The map is gone")
        }

        return File(folder, MEDIA_DIRECTORY)
    }

    /**
     * The name of a live photo's motion beside the still named `still`
     * (without its extension), as a file of `extension`, held for its copy
     * (`names`): `still-motion.mp4`, or `still-2-motion.mp4` and so on when
     * a file in `folder` starts so, or another on its way there holds it, so
     * it still ends as motions' names do ([MediaNames.motionName]).
     */
    private fun motionName(names: IncomingMedia.Names, folder: File, still: String, extension: String): String {
        val stems = generateSequence(1) { it + 1 }.map { MediaNames.motionName(if (it == 1) still else "$still-$it", extension).substringBeforeLast('.') }
        return "${names.stem(folder, stems)}.$extension"
    }

    /** `stem`, then `stem-2`, `stem-3` and so on: the names a file's copy may be given, without its extension, the first free one held for it ([IncomingMedia.Names.stem]). */
    private fun numberedStems(stem: String): Sequence<String> = generateSequence(1) { it + 1 }.map { if (it == 1) stem else "$stem-$it" }

    /**
     * `name`, numbered: `photo-2.jpg`, `photo-3.jpg` and so on, as the
     * folder maps are kept in gives one, and a live photo's motion
     * `photo-2-motion.mp4`, so it's still known for one.
     */
    private fun numbered(name: String): Sequence<String> {
        val extension = name.substringAfterLast('.', "")
        val still = MediaNames.stillOf(name)

        return generateSequence(2) { it + 1 }.map { number ->
            when {
                still != null -> MediaNames.motionName("$still-$number", extension)
                extension.isEmpty() -> "$name-$number"
                else -> "${name.substringBeforeLast('.')}-$number.$extension"
            }
        }
    }
}
