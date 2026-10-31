package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.hyuchia.orbit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max

/** Pictures are kept at most this many pixels on their longer side. */
private const val PICTURE_SIZE = 2048

/** Where pictures and movies coming in are copied to be read whole, beside the maps: emptied as the maps load. */
internal const val INCOMING_DIRECTORY = "incoming"

/** The most a picture or movie coming in is copied to be read: far more than any a phone takes. */
private const val INCOMING_LIMIT = 256L * 1024 * 1024

/** The most a movie is read to tell whether it's a live photo's: an iPhone's is a few megabytes. */
private const val LIVE_MOVIE_MOST = 64L * 1024 * 1024

/** A video's poster is at most this many pixels on its longer side: enough for a planet, and for the viewer until the video plays. */
private const val POSTER_SIZE = 1280

/** A video's poster comes from a tenth of the way in, and never later than this. */
private const val POSTER_AT_MOST_MILLISECONDS = 2_000L

/** Room on the device that copying a video in always leaves free. */
private const val VIDEO_SPARE_ROOM = 64L * 1024 * 1024

/** A video copied into a map's folder, and its poster beside it when the phone could read a frame, as the map points at them. */
data class AddedVideo(val src: String, val thumbnail: String?)

/**
 * A picture copied into a map's folder, as the map points at it, and the
 * motion a live photo brought with it (see [LivePhotos]). `motionLeftOut`
 * says a live photo's motion would have taken it past what a picture and
 * its motion may come to, so the picture came alone.
 */
data class AddedPicture(val src: String, val motion: AddedMotion? = null, val motionLeftOut: Boolean = false, val fromIphone: Boolean = false)

/** A live photo's motion, as the map points at it (`media/…-motion.mp4`), and where in it the still was taken, in seconds, when its file said. */
data class AddedMotion(val src: String, val still: Double? = null)

/** A live photo's motion to keep beside its still: `length` bytes that `copy` writes, and where in them the still was taken, in seconds. */
private class MotionToKeep(val length: Long, val still: Double?, val extension: String, val copy: (OutputStream) -> Unit)

/** A sound copied into a map's folder, as the map points at it, and a caption from its file's name when that says something. */
data class AddedSound(val src: String, val caption: String?)

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
     * it is, up to [VIDEO_SIZE_LIMIT]: drawn again, it would keep only its
     * first frame, as a bigger one does. A live photo that keeps its motion
     * in its file, as Android phones do ([LivePhotos]), comes as two: the
     * picture, and its motion beside it as the MP4 it is. Returns where the
     * map points at them: `media/…`.
     */
    suspend fun addPicture(id: String, uri: Uri, resolver: ContentResolver): AddedPicture = withContext(Dispatchers.IO) {
        checkWritable()

        val animation = resolver.openInputStream(uri)?.buffered()?.use(::animationExtension)

        if (animation != null) {
            addAnimation(id, uri, resolver, animation)?.let { return@withContext AddedPicture(it) }
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
    suspend fun addLivePhoto(id: String, still: Uri, movie: Uri, resolver: ContentResolver): AddedPicture = withContext(Dispatchers.IO) {
        checkWritable()

        val picture = incomingCopy(resolver, still, "picture")
        val video = incomingCopy(resolver, movie, "movie")
        val made = File(video.parentFile, "${video.name}.mp4")

        try {
            val facts = LivePhotos.liveVideo(video)
            val kept = if (MotionVideos.toMp4(video, made)) made else video
            val extension = if (kept == made) "mp4" else "mov"
            val motion = MotionToKeep(kept.length(), facts?.stillSeconds, extension) { output -> kept.inputStream().use { it.copyTo(output) } }
            addStill(id, ImageDecoder.createSource(picture), motion)
        } finally {
            picture.delete()
            video.delete()
            made.delete()
        }
    }

    /**
     * Gives the map's picture `picture` (`media/…`) its live photo's motion:
     * an iPhone's movie, kept beside it as [addLivePhoto] keeps one, within
     * what a picture and its motion may come to together.
     */
    suspend fun addMotion(id: String, picture: String, movie: Uri, resolver: ContentResolver): AddedMotion = withContext(Dispatchers.IO) {
        checkWritable()

        val media = mediaFolder(id)
        val still = fileInside(media, picture.removePrefix("$MEDIA_DIRECTORY/"), words)
        val video = incomingCopy(resolver, movie, "movie")
        val made = File(video.parentFile, "${video.name}.mp4")

        try {
            val facts = LivePhotos.liveVideo(video)
            val kept = if (MotionVideos.toMp4(video, made)) made else video
            val stillSize = if (still.isFile) still.length() else 0L
            videoRefusal(stillSize + kept.length(), mediaSize(id), words)?.let { throw MediaRefusedException(it) }
            // A motion added again doesn't go over the one before it, which earlier versions of the map may point at.
            val file = File(media, motionName(media, still.nameWithoutExtension, if (kept == made) "mp4" else "mov"))
            writeAtomically(file) { output -> kept.inputStream().use { it.copyTo(output) } }
            AddedMotion("$MEDIA_DIRECTORY/${file.name}", facts?.stillSeconds)
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
     * with a [MediaRefusedException] past `to`'s room.
     */
    suspend fun copyMedia(from: String, to: String, markdown: String): String = withContext(Dispatchers.IO) {
        if (from == to || !exists(from) || !exists(to)) {
            return@withContext markdown
        }

        checkWritable()

        val source = File(folder(from), MEDIA_DIRECTORY)
        val target = File(folder(to), MEDIA_DIRECTORY)
        val names = MediaNames.references(markdown)
        // What `to`'s text points at: a file still on its way there, from the folder maps are kept in, keeps its name too.
        val pointedAt = MediaNames.references(runCatching { File(folder(to), MAP_FILE).readTextSafely() }.getOrDefault("")).toSet()
        // The names a file pasted under a name of its own can't take: `to`'s, and the ones the pasted text points at.
        val taken = (target.list().orEmpty().toSet() + pointedAt + names).toMutableSet()
        val renamed = mutableMapOf<String, String>()
        val copies = mutableListOf<Pair<File, String>>()

        for (name in names) {
            val file = try {
                fileInside(source, name, words)
            } catch (outside: RefusedZipException) {
                null
            }

            if (file == null || !file.isFile) {
                continue
            }

            val there = File(target, name)

            when {
                !there.exists() && name !in pointedAt -> copies += file to name
                sameBytes(file, there) -> Unit
                else -> {
                    val free = freeName(name, taken)
                    taken += free
                    renamed[name] = free
                    copies += file to free
                }
            }
        }

        pictureRefusal(copies.sumOf { (file, _) -> file.length() }, mediaSize(to), words)?.let { throw MediaRefusedException(it) }

        for ((file, name) in copies) {
            writeAtomically(fileInside(target, name, words)) { output -> file.inputStream().use { it.copyTo(output) } }
        }

        MediaNames.replaced(markdown) { place -> renamed[place.groupValues[2]]?.let { "${place.groupValues[1]}$MEDIA_DIRECTORY/$it" } }
    }

    /**
     * Copies a video from the phone into the map's folder as it is, under a
     * name of Orbit's own with the extension its type has, and puts a frame
     * from it beside it as a JPEG, for the map to show as its poster. A video
     * past [VIDEO_SIZE_LIMIT], or one that would take the map's pictures and
     * videos past [MAP_MEDIA_LIMIT], is refused with a [MediaRefusedException]
     * saying why. `onProgress` hears how far the copy is, from 0 to 1.
     */
    suspend fun addVideo(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedVideo = withContext(Dispatchers.IO) {
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

        if (size >= 0) {
            videoRefusal(size, mediaSize(id), words)?.let { throw MediaRefusedException(it) }
        }

        val media = mediaFolder(id)
        media.mkdirs()

        if (room(media) < maxOf(size, 0L) + VIDEO_SPARE_ROOM) {
            throw MediaRefusedException(words.string(R.string.data_no_room_for_video))
        }

        val stem = uniqueStem(media, "video-${clock().toString(36)}")
        val video = File(media, "$stem.$extension")
        val context = currentCoroutineContext()

        writeAtomically(video) { output ->
            val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the video")
            input.use { copyVideo(it, output, size, words, onProgress) { context.ensureActive() } }
        }

        if (size < 0) {
            // Its size wasn't known before the copy: the map's room is checked now.
            videoRefusal(video.length(), mediaSize(id), words)?.let { problem ->
                video.delete()
                throw MediaRefusedException(problem)
            }
        }

        val poster = runCatching { writePoster(video, File(media, "$stem.jpg")) }.getOrNull()
        AddedVideo("$MEDIA_DIRECTORY/${video.name}", poster?.let { "$MEDIA_DIRECTORY/${it.name}" })
    }

    /**
     * Copies a sound from the phone into the map's folder as it is, under a
     * name of Orbit's own with the extension its type has (an `.m4a` as the
     * `.mp4` it is). One past [VIDEO_SIZE_LIMIT], or one that would take the
     * map's pictures, videos and sounds past [MAP_MEDIA_LIMIT], is refused
     * with a [MediaRefusedException] saying why. `onProgress` hears how far
     * the copy is, from 0 to 1.
     */
    suspend fun addSound(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedSound = withContext(Dispatchers.IO) {
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

            if (size >= 0) {
                soundRefusal(size, mediaSize(id), words)?.let { throw MediaRefusedException(it) }
            }

            val media = mediaFolder(id)
            media.mkdirs()

            if (room(media) < maxOf(size, 0L) + VIDEO_SPARE_ROOM) {
                throw MediaRefusedException(words.string(R.string.data_no_room_for_sound))
            }

            val sound = File(media, "${uniqueStem(media, "$SOUND_PREFIX${clock().toString(36)}")}.$extension")
            val context = currentCoroutineContext()
            writeAtomically(sound) { output -> copySound(input, output, size, words, onProgress) { context.ensureActive() } }

            if (size < 0) {
                // Its size wasn't known before the copy: the map's room is checked now.
                soundRefusal(sound.length(), mediaSize(id), words)?.let { problem ->
                    sound.delete()
                    throw MediaRefusedException(problem)
                }
            }

            sound
        }

        AddedSound("$MEDIA_DIRECTORY/${sound.name}", soundCaption(displayName))
    }

    /**
     * Keeps a voice memo recorded in Orbit, `recording`, in the map's folder
     * as `memo-` and the moment, an `.mp4` every release carries. Refused,
     * and left where it is, with a [MediaRefusedException] past the map's
     * room. Returns where the map points at it.
     */
    suspend fun addRecording(id: String, recording: File): String = withContext(Dispatchers.IO) {
        checkWritable()
        soundRefusal(recording.length(), mediaSize(id), words)?.let { throw MediaRefusedException(it) }
        val media = mediaFolder(id)
        media.mkdirs()
        val memo = File(media, "${uniqueStem(media, "$MEMO_PREFIX${clock().toString(36)}")}.mp4")
        writeAtomically(memo) { output -> recording.inputStream().use { it.copyTo(output) } }
        recording.delete()
        "$MEDIA_DIRECTORY/${memo.name}"
    }

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
        // Two stills in one millisecond, as a drop of several, each get a name of their own.
        val stem = uniqueStem(media, "picture-${clock().toString(36)}")
        val name = "$stem.${if (transparent) "png" else "jpg"}"

        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, output)
            output.toByteArray()
        }

        bitmap.recycle()
        val mapMedia = mediaSize(id)
        val withMotion = motion?.takeIf { videoRefusal(bytes.size + it.length, mapMedia, words) == null }
        pictureRefusal(bytes.size.toLong(), mapMedia, words)?.let { throw MediaRefusedException(it) }

        val kept = withMotion?.let { live ->
            val file = File(media, motionName(media, stem, live.extension))

            try {
                writeAtomically(file) { output -> live.copy(output) }
                AddedMotion("$MEDIA_DIRECTORY/${file.name}", live.still)
            } catch (unreadable: IOException) {
                // The picture still comes, as one would without its motion.
                file.delete()
                null
            }
        }

        writeAtomically(File(media, name), bytes)
        return AddedPicture("$MEDIA_DIRECTORY/$name", kept, motionLeftOut = motion != null && kept == null)
    }

    /**
     * Copies an animation from the phone into the map's folder as it is, with
     * `extension`, under a picture's name. Null for one past
     * [VIDEO_SIZE_LIMIT], to be kept still; one past the map's room is
     * refused with a [MediaRefusedException], as any picture is.
     */
    private fun addAnimation(id: String, uri: Uri, resolver: ContentResolver, extension: String): String? {
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

        if (size > VIDEO_SIZE_LIMIT) {
            return null
        }

        pictureRefusal(maxOf(size, 0L), mediaSize(id), words)?.let { throw MediaRefusedException(it) }
        val media = mediaFolder(id)
        media.mkdirs()
        val animation = File(media, "${uniqueStem(media, "picture-${clock().toString(36)}")}.$extension")

        try {
            writeAtomically(animation) { output ->
                val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the picture")
                input.use { it.copyTo(CappedOutput(output, VIDEO_SIZE_LIMIT, words)) }
            }
        } catch (tooBig: MediaRefusedException) {
            // Its size wasn't known, and it came to more than an animation may be: nothing of it was kept.
            return null
        }

        if (size < 0) {
            // Its size wasn't known before the copy: the map's room is checked now.
            pictureRefusal(animation.length(), mediaSize(id), words)?.let { problem ->
                animation.delete()
                throw MediaRefusedException(problem)
            }
        }

        return "$MEDIA_DIRECTORY/${animation.name}"
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
            writeAtomically(to, bytes)
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
     * (without its extension), as a file of `extension`: `still-motion.mp4`,
     * or `still-2-motion.mp4` and so on when no file in `folder` starts so,
     * so it still ends as motions' names do ([MediaNames.motionName]).
     */
    private fun motionName(folder: File, still: String, extension: String): String {
        val taken = folder.list().orEmpty().map { it.substringBeforeLast('.') }.toSet()
        return generateSequence(1) { it + 1 }.map { MediaNames.motionName(if (it == 1) still else "$still-$it", extension) }.first { it.substringBeforeLast('.') !in taken }
    }

    /** `stem`, or `stem-2`, `stem-3` and so on: a name no file in `folder` starts with. */
    private fun uniqueStem(folder: File, stem: String): String {
        val taken = folder.list().orEmpty().map { it.substringBeforeLast('.') }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) stem else "$stem-$it" }.first { it !in taken }
    }

    /**
     * `name`, numbered to one that isn't `taken`: `photo-2.jpg`, `photo-3.jpg`
     * and so on, as the folder maps are kept in gives one, and a live photo's
     * motion `photo-2-motion.mp4`, so it's still known for one.
     */
    private fun freeName(name: String, taken: Set<String>): String {
        val extension = name.substringAfterLast('.', "")
        val still = MediaNames.stillOf(name)

        return generateSequence(2) { it + 1 }
            .map { number ->
                when {
                    still != null -> MediaNames.motionName("$still-$number", extension)
                    extension.isEmpty() -> "$name-$number"
                    else -> "${name.substringBeforeLast('.')}-$number.$extension"
                }
            }
            .first { it !in taken }
    }
}

/** Whether `one` and the file `other` hold the same bytes: told by their sizes when those differ, else read a piece at a time, as a video can be long. */
private fun sameBytes(one: File, other: File): Boolean {
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
