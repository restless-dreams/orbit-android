package com.hyuchia.orbit.data

import com.hyuchia.orbit.media.PictureInFile
import com.hyuchia.orbit.media.PicturePieces
import com.hyuchia.orbit.media.SharedPictures
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64

/**
 * A map's own file as it leaves the phone for someone else: in a zip shared
 * or sent to another device, in a web page or a drawing, or to the browsers
 * a presentation is shown in. See [OutgoingMedia].
 */
internal sealed interface OutgoingFile {
    /** How many bytes it comes to. */
    val size: Long

    fun writeTo(output: OutputStream)

    /** The file itself, as the person sharing chose to share one Orbit couldn't clean ([Uncleaned]). */
    class AsIs(val file: File) : OutgoingFile {
        override val size: Long get() = file.length()

        override fun writeTo(output: OutputStream) {
            file.inputStream().use { it.copyTo(output) }
        }
    }

    /** A picture without what its file says beyond the picture, made in memory: an SVG drawing, whose text is read as a whole. */
    class Cleaned(val bytes: ByteArray) : OutgoingFile {
        override val size: Long get() = bytes.size.toLong()

        override fun writeTo(output: OutputStream) {
            output.write(bytes)
        }
    }

    /**
     * A JPEG, PNG, WebP or GIF picture without what its file says beyond the
     * picture: the parts of its file it keeps, with the few bytes written in
     * place of what goes ([SharedPictures.plan]), read as they're written, so
     * a big picture is never whole in memory.
     */
    class Pieced(val file: File, val pieces: PicturePieces) : OutgoingFile {
        override val size: Long get() = pieces.size

        override fun writeTo(output: OutputStream) {
            RandomAccessFile(file, "r").use { input -> pieces.writeTo(output, input) }
        }
    }

    /**
     * A video, a sound, or a HEIF, AVIF or BMP picture, with what it says
     * beyond what it shows or plays written over where it was, so every
     * frame and offset stays as it is, and what's after the last of them (a
     * phone's own trailer, a sound's tags) left out at `end`.
     */
    class Patched(val file: File, val patches: List<Patch>, val end: Long) : OutgoingFile {
        override val size: Long get() = end

        override fun writeTo(output: OutputStream) {
            RandomAccessFile(file, "r").use { input -> writePatched(FileBytes(input), patches, end, output) }
        }
    }
}

/** Bytes to write over a file's own from `at`: `with`, or `length` zeros when it's null. */
internal class Patch(val at: Long, val length: Long, val with: ByteArray? = null)

/**
 * The patches a plan gathers, as many as a file of any kind needs and no
 * more: one with more parts to write over than [MOST], as tens of
 * thousands of empty tags in a row, was made to wear the reader out, and
 * can't be read through. Patches of no length write nothing, and aren't
 * kept.
 */
internal class Patches : ArrayList<Patch>() {
    override fun add(element: Patch): Boolean {
        if (element.length <= 0) {
            return false
        }

        if (size >= MOST) {
            throw IOException("More to write over than a file of any kind has")
        }

        return super.add(element)
    }

    override fun addAll(elements: Collection<Patch>): Boolean = elements.map(::add).any { it }

    private companion object {
        const val MOST = 100_000
    }
}

/**
 * What a map's pictures, videos and sounds say beyond what they show or
 * play, taken out as they leave the phone for other people, as a picture
 * shared on its own is (see [SharedPictures]): where and when they were
 * taken or recorded and on what, and what their tags say.
 *
 * - **JPEG, PNG and WebP** go as [SharedPictures.withoutMetadata] makes them:
 *   their pixels, color profile and which way up they are. So do **GIFs**,
 *   without comments or other apps' blocks, and **SVG drawings**, with only
 *   what draws them.
 * - **HEIF and AVIF pictures** keep their picture, and an animation's
 *   frames: their EXIF and XMP items are written over with zeros where they
 *   are, an image sequence's movie is cleaned as a video's is (below), and
 *   the boxes beside the picture, the video a motion photo keeps in it, a
 *   phone's own trailer and other apps' among them, become free space, and
 *   are left out when they come after it, as they do.
 * - **BMP pictures** keep their headers, colors, pixels and color profile;
 *   the bytes the format leaves to apps, and anything between or after
 *   those, say nothing ([Bitmaps]).
 * - **MP4 and QuickTime videos** keep every frame where it is: the boxes
 *   cameras write their place, date and make into (`udta`, `meta` and
 *   `meco`, XMP's `uuid`, and any `uuid` in the movie, as Canon's), in the
 *   movie, its tracks, their media and its fragments, become free space,
 *   and what free space held goes too;
 *   the dates in the movie's, tracks' and media's headers become zero, the
 *   names handlers and sample descriptions give the camera say nothing, and
 *   a phone's own trailer after the last box is left out. The audio Orbit
 *   records is MP4 too, and goes the same way.
 * - **WebM videos and sounds** keep every frame where it is: their tags,
 *   chapters, attachments, title, date and the names of their files and
 *   tracks become Void, and the apps that made them are named with nothing
 *   ([MatroskaElements]).
 * - **MP3s and AAC** keep every frame where it is: an ID3v2 tag before them
 *   (their title, artist, cover and chapters, and when and where they were
 *   recorded) says nothing any more, and the tags after them, ID3v1, APE
 *   and Lyrics3, are left out ([MpegFrames], [SoundTags]).
 * - **FLAC sounds** keep their frames where they are: the blocks with their
 *   tags, pictures, cue sheet and other apps' details become padding
 *   ([FlacBlocks]).
 * - **Ogg sounds** (Vorbis, Opus, FLAC and Speex) keep every page but those
 *   of their comment headers, which say no tags and no vendor any more,
 *   their cover with them, and have their checksums written again
 *   ([OggPages]).
 * - **WAV sounds** keep the chunks that make the sound, its format, samples,
 *   cue points, loops and instrument, and every other chunk (a Broadcast
 *   Wave's originator and date, LIST's tags, iXML, XMP, ID3) becomes JUNK
 *   with nothing in it ([WaveChunks]).
 *
 * What an encoder writes into a sound's or a video's stream itself, as its
 * name in an MP3's first frame, stays with the frames that need it, and a
 * video keeps all of its tracks, a camera's GPS track among them, as the
 * privacy policy says. A sound cut short at its end, as a recording that
 * stopped or a copy that didn't finish, goes as far as its last whole
 * frame, page, block or sample: what the cut falls in, which no player can
 * play, stays out, and what says how long the file is says so. A file
 * Orbit can't read through otherwise doesn't go, since what it holds can't
 * be told ([Uncleaned]), unless the person sharing chose to share it as it
 * is: one damaged anywhere but at its end, a picture or an MP4 or
 * QuickTime movie that isn't whole (a movie can't be played without all of
 * it), or one of no kind Orbit knows. The map's own files never change;
 * Export all, a backup, and the folder maps are kept in take them exactly
 * as they are.
 */
internal object OutgoingMedia {
    /**
     * How `file` goes, as it is when it's one of `asTheyAre`, the files the
     * person sharing chose to share as they are ([Uncleaned]); null when it
     * doesn't go.
     */
    fun of(file: File, asTheyAre: Set<String>): OutgoingFile? = of(file) ?: OutgoingFile.AsIs(file).takeIf { file.name in asTheyAre }

    /**
     * How `file` goes, or null when it doesn't: it can't be read, or read
     * through. Working that out reads a sound frame by frame, so how a file
     * goes is kept for as long as it's the same file, by its path, size and
     * time ([plans]): the export panel and the send sheet, opened again and
     * again, read none of them twice. An SVG drawing, cleaned in memory, is
     * worked out each time.
     */
    fun of(file: File): OutgoingFile? {
        val key = PlanKey(file.path, file.length(), file.lastModified())
        synchronized(plans) { plans[key] }?.let { return it }
        val outgoing = plan(file)

        if (outgoing != null && outgoing !is OutgoingFile.Cleaned) {
            synchronized(plans) { plans[key] = outgoing }
        }

        return outgoing
    }

    /** A file as it was when how it goes was worked out: one written again, under its name, is another. */
    private data class PlanKey(val path: String, val size: Long, val modified: Long)

    /** How the files last asked about go, the latest few hundred, by what they were then. */
    private val plans = object : LinkedHashMap<PlanKey, OutgoingFile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlanKey, OutgoingFile>?): Boolean = size > PLANS_KEPT
    }

    /** How `file` goes, worked out from what it holds: see [of]. */
    private fun plan(file: File): OutgoingFile? {
        val start = try {
            file.inputStream().use { input -> input.readAtMost(START_BYTES) }
        } catch (unreadable: IOException) {
            return null
        }

        return when (val kind = kindOf(start)) {
            null -> null
            Kind.Picture -> picture(file, start)
            else -> RandomAccessFile(file, "r").use { input -> plan(kind, FileBytes(input)) }?.let { (patches, end) -> OutgoingFile.Patched(file, patches, end) }
        }
    }

    /**
     * How a picture's file goes, whose first bytes are `start`: a JPEG, PNG,
     * WebP or GIF as the parts of its file it keeps, read from the file as
     * they're written, and an SVG drawing cleaned in memory; null when it
     * can't be read through.
     */
    private fun picture(file: File, start: ByteArray): OutgoingFile? {
        if (SharedPictures.kindOf(start) == SharedPictures.Kind.Svg) {
            // Read whole to be cleaned: one bigger than any drawing a map has goes only as it is, when the person chooses.
            if (file.length() > SVG_CLEANED_MOST) {
                return null
            }

            return SharedPictures.withoutMetadata(file.readBytes())?.let { OutgoingFile.Cleaned(it) }
        }

        return try {
            RandomAccessFile(file, "r").use { input -> SharedPictures.plan(PictureInFile(input)) }?.let { OutgoingFile.Pieced(file, it) }
        } catch (unreadable: IOException) {
            // It went, or changed, as it was read.
            null
        }
    }

    /** A picture's bytes as they go inside a page or a drawing, or null when they can't. */
    fun picture(bytes: ByteArray): ByteArray? = when (val kind = kindOf(bytes)) {
        null -> null
        Kind.Picture -> SharedPictures.withoutMetadata(bytes)
        else -> plan(kind, ArrayBytes(bytes))?.let { (patches, end) ->
            ByteArrayOutputStream(end.toInt()).also { output -> writePatched(ArrayBytes(bytes), patches, end, output) }.toByteArray()
        }
    }

    /**
     * An SVG drawing of a map, whose pictures are inside it as data
     * addresses, with each picture as [picture] makes it: one that can't be
     * read through is left out, its address emptied, unless the person
     * sharing chose to share such pictures as they are (`asTheyAre`).
     */
    fun drawing(svg: String, asTheyAre: Boolean = false): String = DATA_PICTURE.replace(svg) { match ->
        val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[2]) }.getOrNull()
        val cleaned = bytes?.let(::picture)

        when {
            cleaned != null -> "data:${match.groupValues[1]};base64,${Base64.getEncoder().encodeToString(cleaned)}"
            asTheyAre && bytes != null -> match.value
            else -> "data:,"
        }
    }

    private enum class Kind { Picture, Boxes, Sound, Matroska, Bitmap }

    /** What kind of file starts with `start`, or null for none Orbit knows: what it holds can't be told. */
    private fun kindOf(start: ByteArray): Kind? = when {
        SharedPictures.kindOf(start).let { it != SharedPictures.Kind.Plain && it != SharedPictures.Kind.Other } -> Kind.Picture
        IsoBoxes.looksLikeOne(start) -> Kind.Boxes
        MatroskaElements.looksLikeOne(start) -> Kind.Matroska
        soundKind(start) in TAGGED_SOUNDS || WaveChunks.looksLikeOne(start) -> Kind.Sound
        SharedPictures.kindOf(start) == SharedPictures.Kind.Plain -> Kind.Bitmap
        else -> null
    }

    /** What to write over a file of `kind`, and where it ends, or null when it can't be read through. */
    private fun plan(kind: Kind, bytes: Bytes): Pair<List<Patch>, Long>? = try {
        val plan = when (kind) {
            Kind.Boxes -> MetadataPatches.plan(bytes)
            Kind.Sound -> soundPlan(bytes)
            Kind.Matroska -> MatroskaElements.plan(bytes)
            Kind.Bitmap -> Bitmaps.plan(bytes)
            Kind.Picture -> null
        }

        // A plan that ends outside the file, or writes before its start, was misled by what the file says: it can't be read through.
        plan?.takeIf { (patches, end) -> end in 0..bytes.size && patches.all { it.at >= 0 && it.length >= 0 } }
    } catch (unreadable: IOException) {
        null
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /**
     * A sound's patches, by what follows the ID3v2 tags at its start, if
     * any: FLAC, Ogg, WAV, or else MPEG audio's or AAC's frames. Ogg and WAV
     * start their files, so one with a tag before it can't be read as either.
     */
    private fun soundPlan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val sound = SoundTags.afterId3Tags(bytes, 0)
        val head = bytes.read(sound, 12)

        return when {
            head.startsWith("fLaC") -> FlacBlocks.plan(bytes)
            sound > 0 && (head.startsWith("OggS") || WaveChunks.looksLikeOne(head)) -> null
            head.startsWith("OggS") -> OggPages.plan(bytes)
            WaveChunks.looksLikeOne(head) -> WaveChunks.plan(bytes)
            else -> MpegFrames.plan(bytes)
        }
    }

    /** The sounds Orbit reads through, by what their first bytes say ([soundKind]): an ID3v2 tag says an MP3, AAC or FLAC follows. WAVs past 4 GB start as RF64 does. */
    private val TAGGED_SOUNDS = setOf("mp3", "aac", "flac", "ogg", "opus", "id3")

    /** Enough of a file to tell its kind: a JPEG's or PNG's signature, an MP4's first box, or where an SVG's element starts. */
    private const val START_BYTES = 512

    /** How many files' plans [of] keeps: a map's, and a few more. */
    private const val PLANS_KEPT = 512

    /** The most an SVG drawing may be to be cleaned, which reads it whole: drawings are kilobytes, a big one a few megabytes. */
    private const val SVG_CLEANED_MOST = 32L * 1024 * 1024

    /** A picture inside a drawing: its media type, and its bytes in base64. */
    private val DATA_PICTURE = Regex("""data:(image/[A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=]+)""")
}

/**
 * Copies of a map's files as they leave the phone ([OutgoingMedia]), kept in
 * `folder` for as long as they're served, so a video a browser plays in
 * parts is cleaned once rather than for each part. A copy is made again
 * when its file changes, and copies a day old go.
 */
internal class OutgoingCopies(private val folder: File, private val now: () -> Long = System::currentTimeMillis) {
    init {
        folder.listFiles().orEmpty().filter { now() - it.lastModified() > KEPT }.forEach { it.delete() }
    }

    /**
     * The file to serve for `file`: itself when it goes as it is, as one of
     * `asTheyAre` does when it can't go clean, a clean copy otherwise, or
     * null when it doesn't go.
     */
    fun of(file: File, asTheyAre: Set<String> = emptySet()): File? {
        val outgoing = OutgoingMedia.of(file, asTheyAre) ?: return null

        if (outgoing is OutgoingFile.AsIs) {
            return file
        }

        val key = MessageDigest.getInstance("SHA-1").digest("${file.canonicalPath}\n${file.length()}\n${file.lastModified()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val copy = File(folder, "$key.${file.extension.lowercase()}")

        if (!copy.isFile) {
            folder.mkdirs()
            writeAtomically(copy) { outgoing.writeTo(it) }
        }

        return copy
    }

    private companion object {
        const val KEPT = 24 * 60 * 60 * 1000L
    }
}

/** Writes `source` up to `end`, with `patches` over its own bytes. */
private fun writePatched(source: Bytes, patches: List<Patch>, end: Long, output: OutputStream) {
    val sorted = patches.filter { it.at < end }.sortedBy { it.at }
    var position = 0L

    fun copy(until: Long) {
        while (position < until) {
            val chunk = source.read(position, minOf(COPY_CHUNK.toLong(), until - position).toInt())

            if (chunk.isEmpty()) {
                throw IOException("The file ended before it was copied")
            }

            output.write(chunk)
            position += chunk.size
        }
    }

    for (patch in sorted) {
        copy(patch.at)
        val over = minOf(patch.length, end - patch.at) - (position - patch.at)

        if (over <= 0) {
            continue
        }

        val with = patch.with

        if (with != null) {
            output.write(with, (position - patch.at).toInt(), over.toInt())
        } else {
            val zeros = ByteArray(minOf(COPY_CHUNK.toLong(), over).toInt())
            var left = over

            while (left > 0) {
                val count = minOf(left, zeros.size.toLong()).toInt()
                output.write(zeros, 0, count)
                left -= count
            }
        }

        position += over
    }

    copy(end)
}

private const val COPY_CHUNK = 256 * 1024

/**
 * What a video or a HEIF or AVIF picture says beyond what it shows, found in
 * its boxes and planned as patches over them: see [OutgoingMedia].
 */
private object MetadataPatches {
    /**
     * What to write over the file, and where it ends, or null when it can't
     * be read through or holds nothing to show.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? = try {
        // Boxes follow each other to the end; anything after the last whole one isn't one, and stays out.
        val (top, end) = IsoBoxes.top(bytes)
        val brands = top.firstOrNull { it.type == "ftyp" }?.let { IsoBoxes.brands(bytes, it) }.orEmpty()

        if (brands.any { it in IsoBoxes.PICTURE_BRANDS }) {
            pictureMetadata(bytes, top)
        } else {
            // A movie whose media data isn't whole, as one cut short in it, can't be played: its index points at frames that aren't there.
            movieMetadata(bytes, top)?.takeIf { top.any { it.type == "mdat" } }?.let { it to end }
        }
    } catch (unreadable: IOException) {
        null
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /**
     * A movie's patches: its metadata boxes freed and its dates cleared,
     * wherever ISO and QuickTime let a movie keep them: in the movie, each
     * track, its media and the media's information, and the fragments a
     * movie may come in. Free space is emptied too, as an editor may have
     * left a box's old contents in it, and so is when a live stream's
     * fragments were made. Null without a movie in it, or when one of those
     * can't be read.
     */
    private fun movieMetadata(bytes: Bytes, top: List<IsoBox>): List<Patch>? {
        val movie = top.firstOrNull { it.type == "moov" } ?: return null
        val patches = Patches()

        for (box in top) {
            when (box.type) {
                "moov" -> Unit
                "moof" -> patches += metadataIn(bytes, box, "traf") ?: return null
                "prft" -> patches += freed(box)
                else -> patches += metadataOf(bytes, box)
            }
        }

        patches += inMovie(bytes, movie) ?: return null
        return patches
    }

    /**
     * What a movie's own box says beyond what it plays: its header's dates,
     * its tracks' metadata, its own metadata and free space, and a camera's
     * `uuid` box. Null when one of them can't be read.
     */
    private fun inMovie(bytes: Bytes, movie: IsoBox): List<Patch>? {
        val patches = Patches()

        for (box in IsoBoxes.children(bytes, movie) ?: return null) {
            when (box.type) {
                "mvhd" -> patches += datesCleared(bytes, box)
                "trak" -> patches += trackMetadata(bytes, box) ?: return null
                // Cameras keep their own in a `uuid` box in the movie, as Canon's does its EXIF and where it was filmed;
                // a track's, as a 360° video's projection, says how it shows, and stays.
                "uuid" -> patches += freed(box)
                else -> patches += metadataOf(bytes, box)
            }
        }

        return patches
    }

    /**
     * A track's patches: its own and its media's dates cleared, the
     * metadata it, its media and the media's information keep freed, and
     * the names its handlers and sample descriptions give the app or camera
     * that made it said with nothing.
     */
    private fun trackMetadata(bytes: Bytes, track: IsoBox): List<Patch>? {
        val patches = Patches()

        for (part in IsoBoxes.children(bytes, track) ?: return null) {
            when (part.type) {
                "tkhd" -> patches += datesCleared(bytes, part)
                "mdia" -> {
                    val media = IsoBoxes.children(bytes, part) ?: return null
                    val handler = media.firstOrNull { it.type == "hdlr" }?.let { String(bytes.read(it.body + 8, 4), Charsets.ISO_8859_1) }

                    for (box in media) {
                        when (box.type) {
                            "mdhd" -> patches += datesCleared(bytes, box)
                            "hdlr" -> patches += handlerUnnamed(box)
                            "minf" -> patches += mediaInformation(bytes, box, handler) ?: return null
                            else -> patches += metadataOf(bytes, box)
                        }
                    }
                }
                else -> patches += metadataOf(bytes, part)
            }
        }

        return patches
    }

    /** The patches of a track's media information, whose media's `handler` says what kind it is: a video's or a sound's. */
    private fun mediaInformation(bytes: Bytes, information: IsoBox, handler: String?): List<Patch>? {
        val patches = Patches()

        for (box in IsoBoxes.children(bytes, information) ?: return null) {
            when (box.type) {
                "hdlr" -> patches += handlerUnnamed(box)
                "stbl" -> patches += descriptionsUnnamed(bytes, box, handler)
                else -> patches += metadataOf(bytes, box)
            }
        }

        return patches
    }

    /**
     * A handler with nothing after its type: what QuickTime calls its maker,
     * and its name, where cameras may name themselves ("GoPro AVC"). Players
     * go by its type alone.
     */
    private fun handlerUnnamed(handler: IsoBox): List<Patch> = if (handler.end > handler.body + 12) listOf(Patch(handler.body + 12, handler.end - handler.body - 12)) else emptyList()

    /**
     * A video's sample descriptions without their compressor's name, where
     * encoders and cameras name themselves, and a video's or sound's
     * without QuickTime's vendor; decoders read neither. Descriptions that
     * can't be read stay as they are: they say how to decode, not where or
     * when.
     */
    private fun descriptionsUnnamed(bytes: Bytes, table: IsoBox, handler: String?): List<Patch> {
        val descriptions = IsoBoxes.children(bytes, table)?.firstOrNull { it.type == "stsd" } ?: return emptyList()

        return IsoBoxes.children(bytes, descriptions, skip = 8).orEmpty().flatMap { entry ->
            when {
                handler in VISUAL_HANDLERS && entry.end - entry.body >= VISUAL_DESCRIPTION -> listOf(Patch(entry.body + 12, 4), Patch(entry.body + 42, 32))
                handler == "soun" && entry.end - entry.body >= SOUND_DESCRIPTION -> listOf(Patch(entry.body + 12, 4))
                else -> emptyList()
            }
        }
    }

    /** The handlers of tracks of pictures: a video's, an image sequence's (an animated AVIF's), and their transparency's. */
    private val VISUAL_HANDLERS = setOf("vide", "pict", "auxv")

    /** How long a video's and a sound's sample descriptions are at the least, after their box's header. */
    private const val VISUAL_DESCRIPTION = 78L

    private const val SOUND_DESCRIPTION = 20L

    /** The metadata in `parent`'s boxes freed, and in those of its `inner` boxes, as a fragment's tracks; null when they can't be read. */
    private fun metadataIn(bytes: Bytes, parent: IsoBox, inner: String? = null): List<Patch>? {
        val patches = Patches()

        for (box in IsoBoxes.children(bytes, parent) ?: return null) {
            patches += if (box.type == inner) metadataIn(bytes, box) ?: return null else metadataOf(bytes, box)
        }

        return patches
    }

    /** A box freed when it's metadata (`udta`, `meta`, `meco`, XMP's `uuid`) or free space already, which may hold what was there before. */
    private fun metadataOf(bytes: Bytes, box: IsoBox): List<Patch> = when {
        box.type in METADATA || box.type in FREE_SPACE -> freed(box)
        box.type == "uuid" && IsoBoxes.isXmp(bytes, box) -> freed(box)
        else -> emptyList()
    }

    /** The boxes ISO and QuickTime keep metadata in: user data, metadata, and further metadata's container. */
    private val METADATA = setOf("udta", "meta", "meco")

    private val FREE_SPACE = setOf("free", "skip")

    /**
     * A HEIF or AVIF picture's patches, and where it ends. Its EXIF and XMP
     * items are written over with zeros, and an image sequence's movie, as
     * an animated AVIF's, is cleaned as any movie is. Every other box beside
     * the picture becomes free space, and is left out when it comes after
     * the picture, as it does: the video a motion photo keeps (`mpvd`),
     * Samsung's own trailer (`sefd`), XMP, content credentials and a
     * camera's own (`uuid`), what free space held, and any box of a kind a
     * picture isn't made of, unless one of its items is in it. Null when its
     * items, or its movie, can't be read.
     */
    private fun pictureMetadata(bytes: Bytes, top: List<IsoBox>): Pair<List<Patch>, Long>? {
        val meta = top.firstOrNull { it.type == "meta" } ?: return null
        val parts = IsoBoxes.children(bytes, meta, skip = 4) ?: return null
        val items = IsoBoxes.items(bytes, parts) ?: return null
        val patches = Patches()

        for (item in items) {
            if (item.type != "Exif" && !(item.type == "mime" && item.contentType.lowercase().startsWith("application/rdf+xml"))) {
                continue
            }

            for ((at, length) in item.ranges ?: return null) {
                patches += Patch(at, length)
            }
        }

        // A box any of its items is in stays, whatever its kind.
        val places = items.flatMap { it.ranges.orEmpty() }
        val kept = top.filter { box -> box.type in PICTURE_BOXES || places.any { (at, length) -> at < box.end && at + length > box.start } }

        for (box in top) {
            when {
                box !in kept -> patches += freed(box)
                box.type == "moov" -> patches += inMovie(bytes, box) ?: return null
                box.type == "moof" -> patches += metadataIn(bytes, box, "traf") ?: return null
            }
        }

        // What follows the last box it keeps is free space now, and stays out.
        return patches to (kept.maxOfOrNull { it.end } ?: return null)
    }

    /**
     * The boxes a HEIF or AVIF picture is made of: its type, its items and
     * their data, and an image sequence's movie, its fragments and their
     * indexes.
     */
    private val PICTURE_BOXES = setOf("ftyp", "meta", "mdat", "moov", "moof", "mfra", "sidx", "ssix", "styp", "pdin")

    /** A box made free space: its type says so, and nothing it held is left. */
    private fun freed(box: IsoBox): List<Patch> = listOf(
        Patch(box.start + 4, 4, "free".toByteArray(Charsets.ISO_8859_1)),
        Patch(box.start + box.sizeHeader, box.end - box.start - box.sizeHeader),
    )

    /** A movie's, track's or media's header with when it was made and changed set to zero. */
    private fun datesCleared(bytes: Bytes, box: IsoBox): List<Patch> {
        val version = bytes.read(box.body, 1).firstOrNull()?.toInt() ?: return emptyList()
        val length = if (version == 1) 16L else 8L
        return if (box.body + 4 + length <= box.end) listOf(Patch(box.body + 4, length)) else emptyList()
    }
}
