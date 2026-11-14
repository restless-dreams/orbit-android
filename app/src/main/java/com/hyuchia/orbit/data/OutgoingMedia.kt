package com.hyuchia.orbit.data

import com.hyuchia.orbit.media.PictureInFile
import com.hyuchia.orbit.media.PicturePieces
import com.hyuchia.orbit.media.SharedPictures
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A map's own file as it leaves the phone for someone else: in a zip shared
 * or sent to another device, in a web page or a drawing, or to the browsers
 * a presentation is shown in. See [OutgoingMedia]. One read from its file
 * is the file it was planned for, as its stamp says ([FileStamp]): it's
 * read only once it's open and its descriptor says it's that file still,
 * and it's said once more after its last byte, so what's written of a file
 * that changed meanwhile is thrown away ([FileChanged]).
 */
internal sealed interface OutgoingFile {
    /** How many bytes it comes to. */
    val size: Long

    /** The file as it was planned, or null for what isn't read from it as it's written. */
    val stamp: FileStamp?

    /** Opens it to be written: [FileChanged] when its file isn't the one it was planned for, before anything is written. */
    fun open(): Source

    /** Writes it whole, as [open] and [Source.writeTo] do. */
    fun writeTo(output: OutputStream) = open().use { it.writeTo(output) }

    /** An outgoing file open to be written. */
    interface Source : Closeable {
        /** Writes it, then says [FileChanged] when its file changed as it was read: what was written is to be thrown away. */
        fun writeTo(output: OutputStream)
    }

    /**
     * The file itself, as the person sharing chose to share one Orbit
     * couldn't clean, as it was then ([AsTheyAreFiles]): as many bytes as it
     * had, and no more, whatever is written into it meanwhile.
     */
    class AsIs(val file: File, override val stamp: FileStamp) : OutgoingFile {
        override val size: Long get() = stamp.size

        override fun open(): Source = CheckedSource(file, stamp) { input, output ->
            val buffer = ByteArray(COPY_CHUNK)
            var left = stamp.size

            while (left > 0) {
                val count = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())

                if (count < 0) {
                    throw FileChanged(file.name)
                }

                output.write(buffer, 0, count)
                left -= count
            }
        }
    }

    /** A picture without what its file says beyond the picture, made in memory: an SVG drawing, whose text is read as a whole. */
    class Cleaned(val bytes: ByteArray) : OutgoingFile {
        override val size: Long get() = bytes.size.toLong()

        override val stamp: FileStamp? get() = null

        override fun open(): Source = object : Source {
            override fun writeTo(output: OutputStream) {
                output.write(bytes)
            }

            override fun close() = Unit
        }
    }

    /**
     * A JPEG, PNG, WebP or GIF picture without what its file says beyond the
     * picture: the parts of its file it keeps, with the few bytes written in
     * place of what goes ([SharedPictures.plan]), read as they're written, so
     * a big picture is never whole in memory.
     */
    class Pieced(val file: File, val pieces: PicturePieces, override val stamp: FileStamp) : OutgoingFile {
        override val size: Long get() = pieces.size

        override fun open(): Source = CheckedSource(file, stamp) { input, output -> pieces.writeTo(output, input) }
    }

    /**
     * A video, a sound, or a HEIF, AVIF or BMP picture, with what it says
     * beyond what it shows or plays written over where it was, so every
     * frame and offset stays as it is, and what's after the last of them (a
     * phone's own trailer, a sound's tags) left out at `end`.
     */
    class Patched(val file: File, val patches: List<Patch>, val end: Long, override val stamp: FileStamp) : OutgoingFile {
        override val size: Long get() = end

        override fun open(): Source = CheckedSource(file, stamp) { input, output -> writePatched(FileBytes(input), patches, end, output) }
    }
}

/**
 * `file` open to be written by `write`, once its descriptor says it's the
 * file `stamp` was taken of: [FileChanged] when it isn't, or went, and again
 * when it isn't after the last byte.
 */
private class CheckedSource(private val file: File, private val stamp: FileStamp, private val write: (RandomAccessFile, OutputStream) -> Unit) : OutgoingFile.Source {
    private val input = try {
        RandomAccessFile(file, "r")
    } catch (gone: FileNotFoundException) {
        throw FileChanged(file.name)
    }

    init {
        if (!stamp.isOpenAt(input.fd)) {
            input.close()
            throw FileChanged(file.name)
        }
    }

    override fun writeTo(output: OutputStream) {
        write(input, output)

        if (!stamp.isOpenAt(input.fd)) {
            throw FileChanged(file.name)
        }
    }

    override fun close() = input.close()
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
 *   are, their items' names, their handler's name, and the properties that
 *   describe them or say when they were made, changed or taken say
 *   nothing, an image sequence's movie is cleaned as a video's is (below),
 *   and the boxes beside the picture, the video a motion photo keeps in it,
 *   a phone's own trailer and other apps' among them, become free space,
 *   and are left out when they come after it, as they do.
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
 *   with nothing in it, and so does the byte of padding after a chunk of
 *   an odd size ([WaveChunks]).
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
     * How `file` goes as it is now, with what the person sharing answered
     * about the files Orbit can't clean (`asTheyAre`): clean when Orbit can
     * clean it; as it is when it's the very file they chose to share so, as
     * it was when they were asked ([AsTheyAreFiles]); null when it doesn't
     * go, as one they left out. One no one was asked about as it is now, as
     * one that changed since, stops a way out that can ask again
     * ([FileChanged]), and stays on this device otherwise.
     */
    fun of(file: File, asTheyAre: AsTheyAreFiles): OutgoingFile? {
        val judged = judge(file) ?: return null
        judged.clean?.let { return it }

        return when {
            judged.stamp in asTheyAre.chosen -> OutgoingFile.AsIs(file, judged.stamp)
            judged.stamp in asTheyAre.asked || !asTheyAre.stops -> null
            else -> throw FileChanged(file.name)
        }
    }

    /** How `file` goes clean, or null when it doesn't: Orbit can't clean it, or it can't be read ([judge]). */
    fun of(file: File): OutgoingFile? = judge(file)?.clean

    /** The stamp of `file` as it is now when Orbit can't clean it, for the person sharing to be asked about it; null when it can, or can't be read ([judge]). */
    fun uncleanable(file: File): FileStamp? = judge(file)?.takeIf { it.clean == null }?.stamp

    /** A file as it is now ([FileStamp]), and how it goes clean, or null when Orbit can't clean it ([judge]). */
    class Judged(val stamp: FileStamp, val clean: OutgoingFile?)

    /**
     * How `file` goes as it is now, or null when it can't be read, or kept
     * changing as it was. It's read through its descriptor, while that says
     * it's the file its stamp was taken of, so how it goes is said of that
     * very file. Working that out reads a sound frame by frame, so it's kept
     * for as long as it's the same file ([FileStamp]): the export panel and
     * the send sheet, opened again and again, read none of them twice. A
     * file written again under its name is another, even at the same size
     * and time, as a sync app may leave it, so it's worked out again: what
     * was kept for the one before could keep what this one says. An SVG
     * drawing, cleaned in memory, is worked out each time, and so is a file
     * Orbit can't clean.
     */
    fun judge(file: File): Judged? {
        repeat(JUDGING_TRIES) {
            val stamp = FileStamp.of(file) ?: return null
            synchronized(plans) { plans[stamp] }?.let { return Judged(stamp, it) }

            val input = try {
                RandomAccessFile(file, "r")
            } catch (gone: FileNotFoundException) {
                return null
            }

            // Read from the file its stamp says, and still that one once it's read: else it's looked at again.
            val planned = input.use { if (stamp.isOpenAt(input.fd)) Planned(plan(file, stamp, input)).takeIf { stamp.isOpenAt(input.fd) } else null } ?: return@repeat
            val outgoing = planned.outgoing

            if (outgoing != null && outgoing !is OutgoingFile.Cleaned) {
                synchronized(plans) { plans[stamp] = outgoing }
            }

            return Judged(stamp, outgoing)
        }

        return null
    }

    /** How a file goes, or null when it can't go clean, worked out from it. */
    private class Planned(val outgoing: OutgoingFile?)

    /** How the files last asked about go, the latest few hundred, by what they were then. */
    private val plans = object : LinkedHashMap<FileStamp, OutgoingFile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FileStamp, OutgoingFile>?): Boolean = size > PLANS_KEPT
    }

    /** How `file` goes, the one `stamp` says, worked out from what `input` holds of it: see [judge]. */
    private fun plan(file: File, stamp: FileStamp, input: RandomAccessFile): OutgoingFile? {
        val start = try {
            input.seek(0)
            ByteArray(minOf(START_BYTES.toLong(), input.length()).toInt()).also(input::readFully)
        } catch (unreadable: IOException) {
            return null
        }

        return when (val kind = kindOf(start)) {
            null -> null
            Kind.Picture -> picture(file, stamp, start, input)
            else -> plan(kind, FileBytes(input))?.let { (patches, end) -> OutgoingFile.Patched(file, patches, end, stamp) }
        }
    }

    /**
     * How a picture's file goes, whose first bytes are `start`: a JPEG, PNG,
     * WebP or GIF as the parts of its file it keeps, read from the file as
     * they're written, and an SVG drawing cleaned in memory; null when it
     * can't be read through.
     */
    private fun picture(file: File, stamp: FileStamp, start: ByteArray, input: RandomAccessFile): OutgoingFile? = try {
        if (SharedPictures.kindOf(start) == SharedPictures.Kind.Svg) {
            // Read whole to be cleaned: one bigger than any drawing a map has goes only as it is, when the person chooses.
            if (input.length() > SVG_CLEANED_MOST) {
                null
            } else {
                input.seek(0)
                SharedPictures.withoutMetadata(ByteArray(input.length().toInt()).also(input::readFully))?.let { OutgoingFile.Cleaned(it) }
            }
        } else {
            SharedPictures.plan(PictureInFile(input))?.let { OutgoingFile.Pieced(file, it, stamp) }
        }
    } catch (unreadable: IOException) {
        // It went, or changed, as it was read.
        null
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
     * read through is left out, its address emptied, unless it's all that
     * one of the files the person sharing chose to share as they are holds,
     * as it was when they chose (`asTheyAre`). The drawing doesn't name its
     * pictures, so each is known by what it holds: one chosen goes, and one
     * left out, or no one was asked about, as a picture from the web, stays
     * out, whatever else was chosen. A chosen file that changed since stops
     * a way out that can ask again ([FileChanged]).
     */
    fun drawing(svg: String, asTheyAre: AsTheyAreFiles = AsTheyAreFiles.NONE): String {
        val chosen = ChosenPictures(asTheyAre)

        return DATA_PICTURE.replace(svg) { match ->
            val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[2]) }.getOrNull()
            val cleaned = bytes?.let(::picture)

            when {
                cleaned != null -> "data:${match.groupValues[1]};base64,${Base64.getEncoder().encodeToString(cleaned)}"
                bytes != null && chosen.holds(bytes) -> match.value
                else -> "data:,"
            }
        }
    }

    /**
     * The files chosen to go as they are, each as it was when it was chosen,
     * told by what it holds: read only for a picture as big, and once, while
     * its descriptor says it's that file still.
     */
    private class ChosenPictures(private val asTheyAre: AsTheyAreFiles) {
        private val digests = HashMap<FileStamp, ByteArray?>()

        init {
            // Where the way out can ask, a picture chosen as it was is asked about again as it is now, as [of] goes by: only when Orbit
            // can't clean it now and no one was asked about it as it is now. One that went, or goes clean now, isn't; nor is any other
            // file, which a drawing doesn't carry.
            if (asTheyAre.stops) {
                asTheyAre.chosen.map { File(it.path) }.distinct().filter { mediaType(it.name).startsWith("image/") && it.isFile }.forEach { of(it, asTheyAre) }
            }
        }

        /** Whether `bytes` are all that one of the files holds. */
        fun holds(bytes: ByteArray): Boolean {
            val wanted by lazy { MessageDigest.getInstance("SHA-256").digest(bytes) }

            return asTheyAre.chosen.any { stamp ->
                stamp.size == bytes.size.toLong() && digests.getOrPut(stamp) { digestOf(stamp) }?.contentEquals(wanted) == true
            }
        }

        /** Where what's only digested is written: nowhere. */
        private object Nowhere : OutputStream() {
            override fun write(byte: Int) = Unit

            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }

        /** What the file `stamp` was holds, or null when it isn't that file any more: one chosen before it changed holds nothing. */
        private fun digestOf(stamp: FileStamp): ByteArray? = try {
            val digest = MessageDigest.getInstance("SHA-256")
            OutgoingFile.AsIs(File(stamp.path), stamp).writeTo(DigestOutputStream(Nowhere, digest))
            digest.digest()
        } catch (unreadable: IOException) {
            // FileChanged among them.
            null
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

    /** How many files' plans [judge] keeps: a map's, and a few more. */
    private const val PLANS_KEPT = 512

    /** How many times a file that changed as it was read is looked at again before it's taken for one that can't be read. */
    private const val JUDGING_TRIES = 3

    /** The most an SVG drawing may be to be cleaned, which reads it whole: drawings are kilobytes, a big one a few megabytes. */
    private const val SVG_CLEANED_MOST = 32L * 1024 * 1024

    /** A picture inside a drawing: its media type, and its bytes in base64. */
    private val DATA_PICTURE = Regex("""data:(image/[A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=]+)""")
}

/** The cache's folder for copies of a map's files served as they leave the phone ([OutgoingCopies]). */
internal const val OUTGOING_COPIES = "outgoing"

/**
 * Copies of a map's files as they leave the phone ([OutgoingMedia]), kept in
 * `folder` for as long as they're served, so a video a browser plays in
 * parts is cleaned once rather than for each part, and made once, however
 * many browsers ask for it at once: the others wait for it. A copy is made
 * again when its file changes, and copies a day old go. One the person
 * chose to share as it is is a copy too, made of the very file they chose,
 * as it was then, the first time it's asked for: what's served never
 * changes under a browser, and a file put in its place since is never
 * served for it. Each is in a folder of its own, for `madeAsItIs` to keep a
 * private map's with [com.hyuchia.orbit.privacy.PrivateCopies]; they go as
 * the way out they were made for ends ([forgetAsTheyAre]), one still being
 * made then included, and those left as Orbit stopped go as it starts
 * again ([asTheyAreIn]). Each copy is begun only where there's room for it
 * and some to spare (`room`, [requireRoom]), and copies are deleted by
 * `deleting`, off the thread that asks.
 */
internal class OutgoingCopies(
    private val folder: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val room: (folder: File, size: Long) -> Unit = ::requireRoom,
    private val madeAsItIs: (copy: File) -> Unit = {},
    private val deleting: (() -> Unit) -> Unit = { work -> thread(name = "OutgoingCopies") { work() } },
) {
    /** Where the copies of files chosen to go as they are wait, a folder for each time they were forgotten. */
    private val asTheyAre = asTheyAreIn(folder)

    /**
     * Which time the copies of files as they are were last forgotten, as
     * the folder they wait in is named: a copy begun before goes too. It
     * starts from the clock, so no folder left by another is this one's.
     */
    private var forgotten = System.nanoTime()

    /** Held while [forgotten] or [closed] is read or moved on. */
    private val forgetting = Any()

    /** Whether the way out these copies were made for stopped for good: no copy of a file as it is is made any more ([close]). */
    private var closed = false

    /** What each copy being made is made under, by its path: one at a time, and others asking for it wait. */
    private val making = ConcurrentHashMap<String, Any>()

    init {
        // Those left by a way out that ended, as Orbit stopped or another room was made: none of them this one's.
        val left = asTheyAre.listFiles().orEmpty().toList()

        deleting {
            folder.listFiles().orEmpty().filter { it.isFile && now() - it.lastModified() > KEPT }.forEach { it.delete() }
            left.forEach { it.deleteRecursively() }
        }
    }

    /**
     * The file to serve for `file`: a clean copy, or a copy of it as it was
     * chosen to go as it is ([AsTheyAreFiles]), or null when it doesn't go:
     * it can't go clean and wasn't chosen as it is now, or there's no room
     * for its copy, or it kept changing as its copy was made, or the copies
     * of files as they are were forgotten before or as it was made, or the
     * way out they were for stopped ([close]).
     */
    fun of(file: File, chosen: AsTheyAreFiles = AsTheyAreFiles.NONE): File? {
        repeat(COPYING_TRIES) {
            val outgoing = OutgoingMedia.of(file, chosen.lasting()) ?: return null
            val asItIs = outgoing is OutgoingFile.AsIs
            val since = synchronized(forgetting) { forgotten.takeUnless { asItIs && closed } } ?: return null
            // A copy for the file as it is now: one written again under its name gets a copy of its own, even at the same size and time.
            val stamp = outgoing.stamp ?: FileStamp.of(file)
            val key = MessageDigest.getInstance("SHA-1").digest("${file.canonicalPath}\n${stamp ?: System.nanoTime()}".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val copy = if (asItIs) File(File(File(asTheyAre, since.toString()), key), file.name) else File(folder, "$key.${file.extension.lowercase()}")

            synchronized(making.getOrPut(copy.path) { Any() }) {
                // Forgotten while it waited for another to make it, as the presentation ended: none is made again, only to go.
                if (asItIs && synchronized(forgetting) { forgotten != since }) {
                    return null
                }

                if (stamp == null || !copy.isFile) {
                    try {
                        val into = copy.parentFile ?: folder
                        into.mkdirs()
                        room(into, outgoing.size)
                        writeAtomically(copy) { outgoing.writeTo(it) }
                    } catch (changed: FileChanged) {
                        // It changed as it was copied: what was written of it is gone, and it's looked at again as it is now.
                        return@repeat
                    }

                    if (asItIs) {
                        madeAsItIs(copy)
                    }
                }
            }

            // Forgotten as it was made, as the presentation it was for ended: it goes too.
            synchronized(forgetting) {
                if (asItIs && forgotten != since) {
                    copy.parentFile?.let { made -> deleting { made.deleteRecursively() } }
                    return null
                }
            }

            return copy
        }

        return null
    }

    /** Lets the copies of files chosen to go as they are go, one still being made among them: the way out they were made for ended. */
    fun forgetAsTheyAre() {
        val current = synchronized(forgetting) { (++forgotten).toString() }
        deleting { asTheyAre.listFiles().orEmpty().filter { it.name != current }.forEach { it.deleteRecursively() } }
    }

    /** Lets them go as [forgetAsTheyAre] does, and makes none again: the way out they were made for stopped for good. */
    fun close() {
        synchronized(forgetting) { closed = true }
        forgetAsTheyAre()
    }

    companion object {
        private const val KEPT = 24 * 60 * 60 * 1000L

        /** The folder of copies of files chosen to go as they are, in the copies' own. */
        private const val AS_THEY_ARE = "as-they-are"

        /** How many times a file that changed as it was copied is copied again before it stays out. */
        private const val COPYING_TRIES = 2

        /** Where the copies of files chosen to go as they are wait, in `folder`, the copies' own: none outlives Orbit, which deletes them as it starts. */
        fun asTheyAreIn(folder: File): File = File(folder, AS_THEY_ARE)
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
            movieMetadata(bytes, top)?.takeIf { top.any { it.type == "mdat" } && framesBefore(bytes, top, end) }?.let { it to end }
        }
    } catch (unreadable: IOException) {
        null
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /**
     * Whether the frames a movie's index points at are all in what goes,
     * before `end`: its tracks' chunks, and its fragments' samples, start
     * before it. One cut short in media data after a whole box of it, which
     * `top` stops before, points past where it ends.
     */
    private fun framesBefore(bytes: Bytes, top: List<IsoBox>, end: Long): Boolean = top.all { box ->
        when (box.type) {
            "moov" -> IsoBoxes.chunksBefore(bytes, box, end)
            "moof" -> IsoBoxes.fragmentBefore(bytes, box, end)
            else -> true
        }
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
     * A HEIF or AVIF picture's patches, and where it ends. Its EXIF, XMP and
     * other metadata items ([METADATA_ITEMS]) are written over with zeros,
     * its items' names say nothing ([unnamed]), nor does its handler's name,
     * nor its properties that say who described it how, and when it was
     * made, changed and taken ([propertiesSaid]), and an image sequence's
     * movie, as an animated AVIF's, is cleaned as any movie is. Every other
     * box beside the picture becomes free space, and is left out when it
     * comes after the picture, as it does: the video a motion photo keeps
     * (`mpvd`), Samsung's own trailer (`sefd`), XMP, content credentials and
     * a camera's own (`uuid`), what free space held, and any box of a kind a
     * picture isn't made of, unless one of its items is in it. Null when its
     * items, its properties or its movie can't be read, or aren't all
     * there, as in a picture cut short in its media data, or in itself.
     */
    private fun pictureMetadata(bytes: Bytes, top: List<IsoBox>): Pair<List<Patch>, Long>? {
        val meta = top.firstOrNull { it.type == "meta" } ?: return null
        val parts = IsoBoxes.children(bytes, meta, skip = 4) ?: return null
        val items = IsoBoxes.items(bytes, parts) ?: return null
        val patches = Patches()

        for (name in IsoBoxes.itemNames(bytes, parts) ?: return null) {
            patches += unnamed(name)
        }

        // Its handler's name, which says what wrote it ("libavif"), as a track's does.
        parts.firstOrNull { it.type == "hdlr" }?.let { patches += handlerUnnamed(it) }
        patches += propertiesSaid(bytes, parts) ?: return null

        for (item in items) {
            if (item.type !in METADATA_ITEMS) {
                continue
            }

            for ((at, length) in item.ranges ?: return null) {
                patches += Patch(at, length)
            }
        }

        // A box any of its items is in stays, whatever its kind.
        val places = ItemPlaces(items.flatMap { it.ranges.orEmpty() })
        val kept = top.filter { box -> box.type in PICTURE_BOXES || places.anyIn(box.start, box.end) }
        val keeps = kept.toHashSet()

        for (box in top) {
            when {
                box !in keeps -> patches += freed(box)
                box.type == "moov" -> patches += inMovie(bytes, box) ?: return null
                box.type == "moof" -> patches += metadataIn(bytes, box, "traf") ?: return null
            }
        }

        // What follows the last box it keeps is free space now, and stays out.
        val end = kept.maxOfOrNull { it.end } ?: return null

        // An item, or a frame of its movie, past that, as in media data cut short that `top` stops before, isn't there to be shown;
        // nor is one whose places can't be told, as one that goes on past the end of the file, cut short in it.
        if (items.any { item -> item.ranges?.any { (at, length) -> at + length > end } ?: true } || !framesBefore(bytes, top, end)) {
            return null
        }

        return patches to end
    }

    /**
     * What a picture's item properties say beyond what it shows, written
     * over where they are, as long as they were: a description's language,
     * name, description and tags (`udes`) empty, and when it was made and
     * changed (`crtt`, `mdft`) and taken, by the TAI clock (`itai`), zero.
     * Null when its properties can't be read.
     */
    private fun propertiesSaid(bytes: Bytes, parts: List<IsoBox>): List<Patch>? {
        val properties = parts.firstOrNull { it.type == "iprp" }?.let { IsoBoxes.children(bytes, it) ?: return null }?.firstOrNull { it.type == "ipco" } ?: return emptyList()
        val patches = Patches()

        // Each after its version and flags: the description's four texts, each ended by a zero, say nothing as zeros.
        for (property in IsoBoxes.children(bytes, properties) ?: return null) {
            when (property.type) {
                "udes" -> patches += Patch(property.body + 4, property.end - property.body - 4)
                in DATED_PROPERTIES -> patches += Patch(property.body + 4, minOf(8L, property.end - property.body - 4))
            }
        }

        return patches
    }

    /** The properties of a picture that say when: it was made, changed, and taken by the TAI clock, each in 64 bits. */
    private val DATED_PROPERTIES = setOf("crtt", "mdft", "itai")

    /**
     * An item's entry without its name, as long as it was: its name empty,
     * what followed it moved up to where it started, and zeros after that.
     */
    private fun unnamed(name: ItemName): List<Patch> {
        val moved = byteArrayOf(0) + name.after
        val length = name.end - name.at
        return listOf(Patch(name.at, minOf(moved.size.toLong(), length), moved), Patch(name.at + moved.size, length - moved.size))
    }

    /**
     * Where a picture's items are, each as its start and length, sorted once,
     * so whether any is in a box is found at once, for each of tens of
     * thousands of boxes and places.
     */
    private class ItemPlaces(places: List<Pair<Long, Long>>) {
        private val starts = places.map { it.first }.sorted().toLongArray()

        /** For the places sorted by where they start, the furthest any of the first so many reaches. */
        private val reaches: LongArray

        init {
            val sorted = places.sortedBy { it.first }
            reaches = LongArray(sorted.size)
            var furthest = Long.MIN_VALUE

            sorted.forEachIndexed { index, (at, length) ->
                furthest = maxOf(furthest, at + length)
                reaches[index] = furthest
            }
        }

        /** Whether any place starts before `end` and reaches past `start`. */
        fun anyIn(start: Long, end: Long): Boolean {
            // How many places start before `end`: those are the ones that could be in the box.
            var low = 0
            var high = starts.size

            while (low < high) {
                val middle = (low + high) ushr 1

                if (starts[middle] < end) {
                    low = middle + 1
                } else {
                    high = middle
                }
            }

            return low > 0 && reaches[low - 1] > start
        }
    }

    /**
     * The kinds of a HEIF or AVIF picture's items that only say things
     * about it: EXIF, anything with a content type, as XMP is, whatever its
     * name and type say, and a URI's. None draws the picture.
     */
    private val METADATA_ITEMS = setOf("Exif", "mime", "uri ")

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
