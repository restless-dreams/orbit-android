package com.hyuchia.orbit.data

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/** Whose way a live photo keeps its motion inside its picture's file. */
internal enum class MotionKind {
    /** Google's Motion Photo format: XMP says how long the video at the end is. */
    Google,

    /** Older Pixels' MicroVideo: XMP says how far from the end the video starts. */
    MicroVideo,

    /** Samsung's trailer, with the video in its `MotionPhoto_Data`. */
    Samsung,

    /** Huawei's and Honor's: the video, then their own data, then a `LIVE_` trailer saying where it started. */
    Huawei,

    /** A HEIF or AVIF picture's `mpvd` box, Google's and Samsung's way for those. */
    Heif,

    /** An MP4 right after the picture, without any of the above to say so. */
    Appended,
}

/**
 * The motion a live photo keeps inside its picture's file: where its video
 * is, how long it is, and where in it the still was taken, in microseconds,
 * when the file says.
 */
internal data class EmbeddedMotion(val start: Long, val length: Long, val stillMicros: Long?, val kind: MotionKind)

/**
 * What a picture says about the live photo it may be half of: an iPhone's id
 * for the pair, when it was taken, as the phone's clock said, and who made
 * the camera, `Apple` for an iPhone's.
 */
internal data class StillFacts(val identifier: String?, val taken: LocalDateTime?, val maker: String? = null)

/**
 * An iPhone's live photo video, a QuickTime movie: the id it shares with its
 * still, whether it says it's a live photo's, when it was made, as the
 * phone's clock said, and where in it the still was taken, in seconds.
 */
internal data class LiveVideo(val identifier: String?, val live: Boolean, val created: LocalDateTime?, val stillSeconds: Double?)

/**
 * Reads live photos as phones keep them, by what's in the files rather than
 * their names:
 *
 * - **Android phones keep the motion inside the picture's file,** after the
 *   picture: Google's Motion Photo format (XMP's `Camera:MotionPhoto` and
 *   its `Container:Directory` of items, with the video last), older Pixels'
 *   MicroVideo, Samsung's trailer (`MotionPhoto_Data`, closed by `SEFT`),
 *   Huawei's and Honor's (`LIVE_` in the last bytes, giving where the video
 *   started), and in HEIF and AVIF pictures an `mpvd` box at the end. An MP4
 *   right after a JPEG's pictures counts too.
 * - **iPhones keep two files:** the still (HEIC or JPEG) with an id in
 *   Apple's maker note, and a QuickTime movie with the same id in its
 *   `com.apple.quicktime.content.identifier`. [LivePairs] matches them.
 *
 * Nothing here changes a file; it only says where things are.
 */
internal object LivePhotos {
    /** The motion inside a picture's file, or null when it has none, or none that can be read through. */
    fun motionIn(file: File): EmbeddedMotion? = try {
        RandomAccessFile(file, "r").use { motionIn(FileBytes(it)) }
    } catch (unreadable: IOException) {
        null
    }

    fun motionIn(bytes: Bytes): EmbeddedMotion? = try {
        val start = bytes.read(0, 16)

        when {
            isJpeg(start) -> jpegMotion(bytes)
            IsoBoxes.looksLikeOne(start) -> heifMotion(bytes)
            else -> null
        }
    } catch (unreadable: IOException) {
        null
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /** What a still says about its live photo, or null when it isn't a picture Orbit reads that way. */
    fun stillFacts(file: File): StillFacts? = try {
        RandomAccessFile(file, "r").use { stillFacts(FileBytes(it)) }
    } catch (unreadable: IOException) {
        null
    }

    fun stillFacts(bytes: Bytes): StillFacts? = try {
        val start = bytes.read(0, 16)

        val exif = when {
            isJpeg(start) -> jpegParts(bytes)?.exif
            IsoBoxes.looksLikeOne(start) -> heifExif(bytes)
            else -> return null
        }

        exif?.let(Exif::read) ?: StillFacts(null, null)
    } catch (unreadable: IOException) {
        null
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /** What a movie says about the live photo it may be half of, or null when it isn't a movie Orbit reads. */
    fun liveVideo(file: File): LiveVideo? = try {
        RandomAccessFile(file, "r").use { liveVideo(FileBytes(it)) }
    } catch (unreadable: IOException) {
        null
    }

    fun liveVideo(bytes: Bytes): LiveVideo? = try {
        if (!IsoBoxes.looksLikeOne(bytes.read(0, 16))) {
            null
        } else {
            QuickTimeFacts.read(bytes)
        }
    } catch (unreadable: IOException) {
        null
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /** Copies the motion out of the picture's file, as the MP4 it is. */
    fun copyMotion(file: File, motion: EmbeddedMotion, output: OutputStream) {
        RandomAccessFile(file, "r").use { input ->
            val bytes = FileBytes(input)
            var position = motion.start
            val end = motion.start + motion.length

            while (position < end) {
                val chunk = bytes.read(position, minOf(COPY_CHUNK.toLong(), end - position).toInt())

                if (chunk.isEmpty()) {
                    throw IOException("The picture ended before its motion did")
                }

                output.write(chunk)
                position += chunk.size
            }
        }
    }

    private fun isJpeg(start: ByteArray): Boolean = start.size >= 3 && start.byteAt(0) == 0xFF && start.byteAt(1) == 0xD8 && start.byteAt(2) == 0xFF

    // JPEG

    /** A JPEG's XMP and EXIF, and where its pictures end: the first one's, and any that follow it, as a gain map does. */
    private class JpegParts(val xmp: String?, val exif: ByteArray?, val end: Long)

    private fun jpegParts(bytes: Bytes): JpegParts? {
        var xmp: String? = null
        var exif: ByteArray? = null

        var end = pictureEnd(bytes, 0) { marker, payload ->
            when {
                marker != 0xE1 -> Unit
                xmp == null && payload.startsWith(XMP_HEADER) -> xmp = String(payload, XMP_HEADER.size, payload.size - XMP_HEADER.size, Charsets.UTF_8)
                exif == null && payload.startsWith(EXIF_HEADER) -> exif = payload.copyOfRange(EXIF_HEADER.size, payload.size)
            }
        } ?: return null

        // Pictures right after the first, as a gain map or a depth map follows it.
        while (end + 3 <= bytes.size && isJpeg(bytes.read(end, 3))) {
            end = pictureEnd(bytes, end) { _, _ -> } ?: break
        }

        return JpegParts(xmp, exif, end)
    }

    /**
     * Where the JPEG starting at `from` ends, after its end-of-image marker,
     * handing each segment before its picture data to `segment`; null when
     * it can't be read through.
     */
    private fun pictureEnd(bytes: Bytes, from: Long, segment: (Int, ByteArray) -> Unit): Long? {
        var position = from + 2

        while (position + 2 <= bytes.size) {
            val head = bytes.read(position, 2)

            if (head.byteAt(0) != 0xFF) {
                return null
            }

            val marker = head.byteAt(1)

            when {
                // Fill bytes before a marker.
                marker == 0xFF -> position += 1
                marker == 0xD9 -> return position + 2
                marker in 0xD0..0xD7 || marker == 0x01 -> position += 2
                else -> {
                    val length = bytes.read(position + 2, 2).takeIf { it.size == 2 }?.unsigned(0, 2) ?: return null

                    if (length < 2 || position + 2 + length > bytes.size) {
                        return null
                    }

                    if (marker in 0xE0..0xEF && length <= MOST_SEGMENT) {
                        segment(marker, bytes.read(position + 4, (length - 2).toInt()))
                    }

                    position += 2 + length

                    if (marker == 0xDA) {
                        position = afterScan(bytes, position) ?: return null
                    }
                }
            }
        }

        return null
    }

    /** Where the coded picture after a start-of-scan ends: at the next marker that isn't a stuffed byte or a restart. */
    private fun afterScan(bytes: Bytes, from: Long): Long? {
        var position = from

        while (position < bytes.size) {
            val chunk = bytes.read(position, SCAN_CHUNK)

            for (index in 0 until chunk.size - 1) {
                if (chunk.byteAt(index) == 0xFF) {
                    val next = chunk.byteAt(index + 1)

                    if (next != 0x00 && next !in 0xD0..0xD7 && next != 0xFF) {
                        return position + index
                    }
                }
            }

            // The last byte may be the start of a marker: it's read again with the next chunk.
            position += maxOf(1, chunk.size - 1)

            if (chunk.size < SCAN_CHUNK) {
                break
            }
        }

        return null
    }

    private fun jpegMotion(bytes: Bytes): EmbeddedMotion? {
        val parts = jpegParts(bytes) ?: return null
        val xmp = parts.xmp?.let(Xmp::read)

        // Google's Motion Photo: the item with the MotionPhoto semantic is the file's last bytes.
        if (xmp != null && xmp.motionPhoto != null && xmp.motionPhoto > 0 && xmp.videoLength != null) {
            video(bytes, bytes.size - xmp.videoLength, parts.end, MotionKind.Google, xmp.presentationMicros)?.let { return it }
        }

        // Older Pixels': the video starts this many bytes from the end.
        if (xmp != null && xmp.microVideo != null && xmp.microVideo > 0 && xmp.microVideoOffset != null) {
            video(bytes, bytes.size - xmp.microVideoOffset, parts.end, MotionKind.MicroVideo, xmp.microVideoMicros)?.let { return it }
        }

        val tail = bytes.read(maxOf(parts.end, bytes.size - TRAILER_BYTES), TRAILER_BYTES)

        // Huawei's and Honor's: the last bytes say how far from the end the video starts, less 40.
        HUAWEI_TRAILER.findAll(String(tail, Charsets.ISO_8859_1)).lastOrNull()?.let { match ->
            val distance = match.groupValues[1].toLongOrNull()

            // Some files count one byte fewer.
            for (off in listOf(0L, 1L, -1L)) {
                distance?.let { video(bytes, bytes.size - it - HUAWEI_SLACK + off, parts.end, MotionKind.Huawei, null) }?.let { return it }
            }
        }

        // Samsung's: the video follows the name of its block in their trailer.
        if (String(tail, Charsets.ISO_8859_1).endsWith("SEFT")) {
            samsungVideo(bytes, parts.end)?.let { return it }
        }

        // An MP4 right after the pictures, past any padding.
        return appendedVideo(bytes, parts.end)?.let { video(bytes, it, parts.end, MotionKind.Appended, xmp?.presentationMicros) }
    }

    /** The video starting at `start`, if an MP4 with a movie in it is there, after the pictures that end at `after`. */
    private fun video(bytes: Bytes, start: Long, after: Long, kind: MotionKind, stillMicros: Long?): EmbeddedMotion? {
        if (start < after || start >= bytes.size) {
            return null
        }

        val length = movieLength(SliceBytes(bytes, start, bytes.size - start)) ?: return null
        return EmbeddedMotion(start, length, stillMicros?.takeIf { it >= 0 }, kind)
    }

    /** How long the MP4 at the start of `bytes` is, its boxes followed to the last whole one, if it starts with `ftyp` and holds a movie. */
    private fun movieLength(bytes: Bytes): Long? {
        val (boxes, end) = IsoBoxes.top(bytes)

        if (boxes.firstOrNull()?.type != "ftyp" || boxes.none { it.type == "moov" } || boxes.none { it.type == "mdat" }) {
            return null
        }

        return end
    }

    /** Where an MP4 starts after `from`, past zeros and anything else a phone puts between, within [SEARCH_BYTES]. */
    private fun appendedVideo(bytes: Bytes, from: Long): Long? {
        var position = from
        val limit = minOf(bytes.size, from + SEARCH_BYTES)

        while (position < limit) {
            val chunk = bytes.read(position, SCAN_CHUNK + 8)
            val found = indexOf(chunk, FTYP)

            if (found >= 4) {
                return position + found - 4
            }

            if (chunk.size < SCAN_CHUNK + 8) {
                return null
            }

            position += SCAN_CHUNK
        }

        return null
    }

    /** Samsung's video: right after the name of its `MotionPhoto_Data` block, in the trailer at the end of the file. */
    private fun samsungVideo(bytes: Bytes, after: Long): EmbeddedMotion? {
        val from = maxOf(after, bytes.size - SAMSUNG_SEARCH)
        var position = from

        while (position < bytes.size) {
            val chunk = bytes.read(position, SCAN_CHUNK + SAMSUNG_BLOCK.size)
            val found = indexOf(chunk, SAMSUNG_BLOCK)

            if (found >= 0) {
                return video(bytes, position + found + SAMSUNG_BLOCK.size, after, MotionKind.Samsung, null)
            }

            if (chunk.size < SCAN_CHUNK + SAMSUNG_BLOCK.size) {
                return null
            }

            position += SCAN_CHUNK
        }

        return null
    }

    // HEIF and AVIF

    private fun heifMotion(bytes: Bytes): EmbeddedMotion? {
        val (top, _) = IsoBoxes.top(bytes)
        val brands = top.firstOrNull { it.type == "ftyp" }?.let { IsoBoxes.brands(bytes, it) }.orEmpty()

        if (brands.none { it in IsoBoxes.PICTURE_BRANDS }) {
            return null
        }

        val xmp = heifXmp(bytes)?.let(Xmp::read)
        val still = xmp?.presentationMicros?.takeIf { it >= 0 }
        val box = top.lastOrNull { it.type == "mpvd" }

        if (box != null) {
            movieLength(SliceBytes(bytes, box.body, box.end - box.body))?.let { return EmbeddedMotion(box.body, it, still, MotionKind.Heif) }
        }

        // Older Samsungs keep their trailer in an `sefd` box, its MotionPhoto_Data the video itself, or "mpv2" and where the video is.
        val trailer = top.lastOrNull { it.type == "sefd" } ?: return null

        return samsungBlock(SliceBytes(bytes, trailer.body, trailer.end - trailer.body))?.let { (at, data) ->
            val pointer = bytes.read(trailer.body + at, minOf(data, 12L).toInt())

            if (data == 12L && String(pointer, 0, 4, Charsets.ISO_8859_1) == "mpv2") {
                val start = pointer.unsigned(4, 4)
                movieLength(SliceBytes(bytes, start, minOf(pointer.unsigned(8, 4), bytes.size - start)))?.let { EmbeddedMotion(start, it, still, MotionKind.Samsung) }
            } else {
                movieLength(SliceBytes(bytes, trailer.body + at, data))?.let { EmbeddedMotion(trailer.body + at, it, still, MotionKind.Samsung) }
            }
        }
    }

    /**
     * Where the `MotionPhoto_Data` block's data is in Samsung's trailer
     * (`bytes`, which end with it), and how long it is: from its directory,
     * `SEFH` and its entries, with the directory's length and `SEFT` last.
     * Every number is little-endian; each entry says how far back from the
     * directory its block starts, and a block starts with its type and its
     * name's length, then the name.
     */
    private fun samsungBlock(bytes: Bytes): Pair<Long, Long>? {
        val footer = bytes.read(bytes.size - 8, 8)

        if (footer.size < 8 || String(footer, 4, 4, Charsets.ISO_8859_1) != "SEFT") {
            return null
        }

        val length = littleEndian(footer, 0, 4)
        val directory = bytes.size - 8 - length

        if (length < 12 || directory < 0) {
            return null
        }

        val head = bytes.read(directory, length.toInt().coerceAtMost(MOST_SEGMENT))

        if (String(head, 0, 4, Charsets.ISO_8859_1) != "SEFH") {
            return null
        }

        for (index in 0 until littleEndian(head, 8, 4).toInt()) {
            val entry = 12 + index * 12

            if (entry + 12 > head.size) {
                break
            }

            val block = directory - littleEndian(head, entry + 4, 4)
            val size = littleEndian(head, entry + 8, 4)
            val blockHead = bytes.read(block, 8)

            if (block < 0 || blockHead.size < 8) {
                continue
            }

            val nameLength = littleEndian(blockHead, 4, 4)
            val name = String(bytes.read(block + 8, nameLength.toInt().coerceIn(0, 64)), Charsets.ISO_8859_1)

            if (name == "MotionPhoto_Data") {
                return (block + 8 + nameLength) to (size - 8 - nameLength)
            }
        }

        return null
    }

    private fun littleEndian(bytes: ByteArray, at: Int, size: Int): Long = (0 until size).fold(0L) { value, index -> value or (bytes.byteAt(at + index).toLong() shl (8 * index)) }

    private fun heifParts(bytes: Bytes): List<HeifItem>? {
        val meta = IsoBoxes.top(bytes).first.firstOrNull { it.type == "meta" } ?: return null
        val parts = IsoBoxes.children(bytes, meta, skip = 4) ?: return null
        return IsoBoxes.items(bytes, parts)
    }

    private fun heifXmp(bytes: Bytes): String? {
        val item = heifParts(bytes)?.firstOrNull { it.type == "mime" && it.contentType.lowercase().startsWith("application/rdf+xml") } ?: return null
        return IsoBoxes.itemBytes(bytes, item, MOST_SEGMENT)?.toString(Charsets.UTF_8)
    }

    /** A HEIF picture's EXIF from its TIFF header on: the item starts with how far into it that is. */
    private fun heifExif(bytes: Bytes): ByteArray? {
        val item = heifParts(bytes)?.firstOrNull { it.type == "Exif" } ?: return null
        val content = IsoBoxes.itemBytes(bytes, item, MOST_SEGMENT) ?: return null

        if (content.size < 4) {
            return null
        }

        val skip = 4 + content.unsigned(0, 4).toInt()
        val exif = content.takeIf { skip in 4..it.size }?.copyOfRange(skip, content.size) ?: return null
        // Some writers keep JPEG's own header in front of the TIFF one.
        return if (exif.startsWith(EXIF_HEADER)) exif.copyOfRange(EXIF_HEADER.size, exif.size) else exif
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        for (index in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[index + it] == needle[it] }) {
                return index
            }
        }

        return -1
    }

    private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
    private val EXIF_HEADER = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    private val FTYP = "ftyp".toByteArray(Charsets.ISO_8859_1)
    private val SAMSUNG_BLOCK = "MotionPhoto_Data".toByteArray(Charsets.ISO_8859_1)

    /** Huawei's trailer: its last field, `LIVE_` and how far the video started from the end, less [HUAWEI_SLACK]. */
    private val HUAWEI_TRAILER = Regex("""LIVE_(\d{1,12})""")

    private const val HUAWEI_SLACK = 40L

    /** How much of a file's end a trailer is looked for in. */
    private const val TRAILER_BYTES = 256

    /** How far past the pictures an appended video is looked for. */
    private const val SEARCH_BYTES = 4L * 1024 * 1024

    /** How far from the end Samsung's video block is looked for. */
    private const val SAMSUNG_SEARCH = 256L * 1024 * 1024

    /** The biggest segment or item read for what it says. */
    private const val MOST_SEGMENT = 1 shl 20

    private const val SCAN_CHUNK = 64 * 1024
    private const val COPY_CHUNK = 256 * 1024
}

/** What XMP says about a motion photo, as Google's formats write it, by property name in its namespaces. */
internal class Xmp(
    val motionPhoto: Int?,
    val presentationMicros: Long?,
    val videoLength: Long?,
    val microVideo: Int?,
    val microVideoOffset: Long?,
    val microVideoMicros: Long?,
) {
    companion object {
        private const val CAMERA = "http://ns.google.com/photos/1.0/camera/"
        private const val CONTAINER = "http://ns.google.com/photos/1.0/container/"
        private const val ITEM = "http://ns.google.com/photos/1.0/container/item/"

        /** The XMP in `text`, or null when it can't be read. Properties are read as attributes or elements, whatever their prefix. */
        fun read(text: String): Xmp? {
            // A packet never needs a document type: one with entities is refused rather than read. Android's parser
            // refuses the feature that would say so, so it's looked for here.
            if (text.contains("<!DOCTYPE", ignoreCase = true)) {
                return null
            }

            val document = try {
                val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                factory.newDocumentBuilder().parse(InputSource(StringReader(text.trim('\u0000', ' ', '\n', '\r', '\t'))))
            } catch (unreadable: Exception) {
                return null
            }

            val elements = document.getElementsByTagNameNS("*", "*")
            val all = (0 until elements.length).map { elements.item(it) as Element }

            fun value(namespace: String, name: String): String? = all.firstNotNullOfOrNull { element ->
                element.getAttributeNS(namespace, name).takeIf { it.isNotBlank() }
                    ?: element.takeIf { it.namespaceURI == namespace && it.localName == name }?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }

            // The directory's items, each with its semantic and length, as attributes or elements.
            val items = all.filter { it.namespaceURI == CONTAINER && it.localName == "Item" }

            val motionLength = items.firstNotNullOfOrNull { item ->
                fun field(name: String) = item.getAttributeNS(ITEM, name).takeIf { it.isNotBlank() }
                    ?: (0 until item.childNodes.length).map { item.childNodes.item(it) }.filterIsInstance<Element>()
                        .firstOrNull { it.namespaceURI == ITEM && it.localName == name }?.textContent?.trim()

                field("Length")?.toLongOrNull()?.takeIf { field("Semantic") == "MotionPhoto" && it > 0 }
            }

            return Xmp(
                motionPhoto = value(CAMERA, "MotionPhoto")?.toIntOrNull(),
                presentationMicros = value(CAMERA, "MotionPhotoPresentationTimestampUs")?.toLongOrNull(),
                videoLength = motionLength,
                microVideo = value(CAMERA, "MicroVideo")?.toIntOrNull(),
                microVideoOffset = value(CAMERA, "MicroVideoOffset")?.toLongOrNull()?.takeIf { it > 0 },
                microVideoMicros = value(CAMERA, "MicroVideoPresentationTimestampUs")?.toLongOrNull(),
            )
        }
    }
}

/** What a picture's EXIF says about its live photo: Apple's id for the pair, in its maker note, and when it was taken. */
internal object Exif {
    /** From EXIF's TIFF header on. */
    fun read(tiff: ByteArray): StillFacts? {
        if (tiff.size < 8) {
            return null
        }

        val little = when (String(tiff, 0, 2, Charsets.ISO_8859_1)) {
            "II" -> true
            "MM" -> false
            else -> return null
        }

        val reader = TiffReader(tiff, little)
        val first = reader.entries(reader.number(4, 4)) ?: return null
        val exifIfd = first[EXIF_IFD]?.let { reader.number(it.valueAt, 4) }
        val exif = exifIfd?.let(reader::entries).orEmpty()
        // When it was taken, or else digitized, or else last written, as a copy made on the phone keeps only that.
        val taken = listOfNotNull(exif[DATE_TAKEN], exif[DATE_DIGITIZED], first[DATE_WRITTEN]).firstNotNullOfOrNull { reader.text(it)?.let(::exifTime) }
        val identifier = exif[MAKER_NOTE]?.let { appleIdentifier(reader.bytes(it)) }
        val maker = first[MAKER]?.let(reader::text)?.trim()?.takeIf { it.isNotEmpty() }
        return StillFacts(identifier, taken, maker)
    }

    /**
     * The id in Apple's maker note (`ContentIdentifier`, tag 0x11): the note
     * starts `Apple iOS`, then its version and byte order, then its entries,
     * whose places count from the note's own start.
     */
    private fun appleIdentifier(note: ByteArray?): String? {
        if (note == null || note.size < APPLE_ENTRIES || String(note, 0, 9, Charsets.ISO_8859_1) != "Apple iOS") {
            return null
        }

        val little = String(note, 12, 2, Charsets.ISO_8859_1) == "II"
        val reader = TiffReader(note, little)
        val entries = reader.entries(APPLE_ENTRIES.toLong()) ?: return null
        return entries[APPLE_IDENTIFIER]?.let(reader::text)?.trim()?.takeIf { IDENTIFIER.matches(it) }
    }

    private fun exifTime(text: String): LocalDateTime? = try {
        LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
    } catch (unreadable: DateTimeParseException) {
        null
    }

    private const val EXIF_IFD = 0x8769
    private const val MAKER = 0x010F
    private const val DATE_TAKEN = 0x9003
    private const val DATE_DIGITIZED = 0x9004
    private const val DATE_WRITTEN = 0x0132
    private const val MAKER_NOTE = 0x927C
    private const val APPLE_IDENTIFIER = 0x0011

    /** Where an Apple maker note's entries begin: after `Apple iOS`, a zero, its version and its byte order. */
    private const val APPLE_ENTRIES = 14

    private val IDENTIFIER = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")
}

/** A TIFF directory's entry: its tag, type, count, and where its value is (in the entry, or where it points). */
private class TiffEntry(val type: Int, val count: Long, val valueAt: Long)

/** Reads TIFF directories, as EXIF and maker notes are, in their byte order, with places counted from `data`'s start. */
private class TiffReader(private val data: ByteArray, private val little: Boolean) {
    fun number(at: Long, size: Int): Long {
        if (at < 0 || at + size > data.size) {
            return -1
        }

        var value = 0L

        for (index in 0 until size) {
            val byte = data.byteAt((at + if (little) size - 1 - index else index).toInt()).toLong()
            value = (value shl 8) or byte
        }

        return value
    }

    /** The entries of the directory at `at`, by tag, or null when it isn't one. */
    fun entries(at: Long): Map<Int, TiffEntry>? {
        val count = number(at, 2)

        if (count !in 1..MOST_ENTRIES || at + 2 + count * 12 > data.size) {
            return null
        }

        return (0 until count).associate { index ->
            val entry = at + 2 + index * 12
            val type = number(entry + 2, 2).toInt()
            val items = number(entry + 4, 4)
            val size = TYPE_SIZES.getOrElse(type) { 1 } * items
            val valueAt = if (size <= 4) entry + 8 else number(entry + 8, 4)
            number(entry, 2).toInt() to TiffEntry(type, items, valueAt)
        }
    }

    fun bytes(entry: TiffEntry): ByteArray? {
        val size = TYPE_SIZES.getOrElse(entry.type) { 1 } * entry.count

        if (entry.valueAt < 0 || entry.valueAt + size > data.size) {
            return null
        }

        return data.copyOfRange(entry.valueAt.toInt(), (entry.valueAt + size).toInt())
    }

    fun text(entry: TiffEntry): String? = bytes(entry)?.let { String(it, Charsets.ISO_8859_1).trimEnd('\u0000') }

    private companion object {
        const val MOST_ENTRIES = 1000L

        /** The size of one value of each TIFF type, by its number. */
        val TYPE_SIZES = listOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 4)
    }
}

/** What a QuickTime movie's metadata says about the live photo it may be half of: see [LiveVideo]. */
private object QuickTimeFacts {
    fun read(bytes: Bytes): LiveVideo? {
        val (top, _) = IsoBoxes.top(bytes)
        val movie = top.firstOrNull { it.type == "moov" } ?: return null
        val parts = IsoBoxes.children(bytes, movie) ?: return null
        // An iPhone keeps its metadata in the movie's `meta`; other tools write it inside `udta`.
        val inData = parts.firstOrNull { it.type == "udta" }?.let { IsoBoxes.children(bytes, it) }.orEmpty()
        val values = (parts + inData).filter { it.type == "meta" }.map { keyedValues(bytes, it) }.fold(emptyMap<String, String>()) { all, more -> more + all }
        val identifier = values[IDENTIFIER_KEY]?.trim()?.takeIf { it.isNotEmpty() }
        val live = values.keys.any { it.startsWith(LIVE_PHOTO_KEYS) } || parts.any { it.type == "trak" && holds(bytes, it, STILL_TIME_KEY) }
        val created = values[CREATED_KEY]?.let(::isoTime)
        return LiveVideo(identifier, live, created, stillSeconds(bytes, parts))
    }

    /**
     * The movie's own metadata, by key: a `meta` box with `mdta` keys, its
     * `keys` box naming them in order, and its `ilst` items, each numbered by
     * its key, holding its value in a `data` atom.
     */
    private fun keyedValues(bytes: Bytes, meta: IsoBox): Map<String, String> {
        // QuickTime's `meta` is a plain box; ISO's is a full box, four bytes longer.
        val skip = if (bytes.read(meta.body + 4, 4).toString(Charsets.ISO_8859_1) == "hdlr") 0 else 4
        val parts = IsoBoxes.children(bytes, meta, skip) ?: return emptyMap()
        val keysBox = parts.firstOrNull { it.type == "keys" } ?: return emptyMap()
        val items = parts.firstOrNull { it.type == "ilst" } ?: return emptyMap()
        val keys = bytes.read(keysBox.body, minOf(keysBox.end - keysBox.body, MOST_KEYS).toInt())

        if (keys.size < 8) {
            return emptyMap()
        }

        val names = mutableListOf<String>()
        var position = 8

        repeat(keys.unsigned(4, 4).toInt().coerceAtMost(256)) {
            if (position + 8 > keys.size) {
                return@repeat
            }

            val size = keys.unsigned(position, 4)

            // Counted in Long: a size near 2³¹ would otherwise wrap around past the check.
            if (size < 8 || position + size > keys.size) {
                return@repeat
            }

            names += String(keys, position + 8, size.toInt() - 8, Charsets.UTF_8)
            position += size.toInt()
        }

        val values = mutableMapOf<String, String>()
        var at = items.body

        // Each item's type is its key's number, from 1, which isn't a box's type of letters: they're read by hand.
        while (at + 8 <= items.end) {
            val head = bytes.read(at, 8)
            val size = head.unsigned(0, 4)

            if (size < 8 || at + size > items.end) {
                break
            }

            val name = names.getOrNull(head.unsigned(4, 4).toInt() - 1)
            val data = IsoBoxes.children(bytes, IsoBox("item", at, 8, at + size))?.firstOrNull { it.type == "data" }

            if (name != null && data != null) {
                val content = bytes.read(data.body, minOf(data.end - data.body, MOST_VALUE).toInt())

                // A `data` atom's type indicator is 1 for UTF-8 text, then a locale, then the value.
                if (content.size >= 8 && content.unsigned(0, 4) == 1L) {
                    values[name] = String(content, 8, content.size - 8, Charsets.UTF_8)
                }
            }

            at += size
        }

        return values
    }

    /** Whether a track's boxes name `key` anywhere: a timed metadata track lists its keys in its sample entry. */
    private fun holds(bytes: Bytes, track: IsoBox, key: String): Boolean {
        if (track.end - track.start > MOST_TRACK_HEADER) {
            return false
        }

        return bytes.read(track.start, (track.end - track.start).toInt()).toString(Charsets.ISO_8859_1).contains(key)
    }

    /**
     * Where the still was taken, in seconds: the track holding
     * `still-image-time` has its one sample there, after an empty edit as
     * long as the time before it.
     */
    private fun stillSeconds(bytes: Bytes, parts: List<IsoBox>): Double? {
        val header = parts.firstOrNull { it.type == "mvhd" } ?: return null
        val head = bytes.read(header.body, 32)

        if (head.size < 32) {
            return null
        }

        val scale = if (head.byteAt(0) == 1) head.unsigned(20, 4) else head.unsigned(12, 4)
        val track = parts.firstOrNull { it.type == "trak" && holds(bytes, it, STILL_TIME_KEY) } ?: return null
        val edits = IsoBoxes.children(bytes, track)?.firstOrNull { it.type == "edts" }?.let { IsoBoxes.children(bytes, it) }?.firstOrNull { it.type == "elst" } ?: return null
        val list = bytes.read(edits.body, minOf(edits.end - edits.body, 4096L).toInt())

        if (list.size < 8 || scale <= 0) {
            return null
        }

        val wide = list.byteAt(0) == 1
        val size = if (wide) 20 else 12
        var before = 0L

        for (index in 0 until list.unsigned(4, 4).toInt()) {
            val at = 8 + index * size

            if (at + size > list.size) {
                break
            }

            val duration = list.unsigned(at, if (wide) 8 else 4)
            val start = list.unsigned(at + if (wide) 8 else 4, if (wide) 8 else 4)
            val empty = start == if (wide) -1L else 0xFFFFFFFFL

            if (!empty) {
                break
            }

            before += duration
        }

        return before.toDouble() / scale
    }

    private fun isoTime(text: String): LocalDateTime? = try {
        OffsetDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XX")).toLocalDateTime()
    } catch (unreadable: DateTimeParseException) {
        runCatching { LocalDateTime.parse(text.trim().take(19)) }.getOrNull()
    }

    private const val IDENTIFIER_KEY = "com.apple.quicktime.content.identifier"
    private const val CREATED_KEY = "com.apple.quicktime.creationdate"
    private const val LIVE_PHOTO_KEYS = "com.apple.quicktime.live-photo"
    private const val STILL_TIME_KEY = "com.apple.quicktime.still-image-time"
    private const val MOST_KEYS = 64L * 1024
    private const val MOST_VALUE = 4096L
    private const val MOST_TRACK_HEADER = 256L * 1024
}

/** The most a web page carries of live photos' motions, as of sounds. */
internal const val PAGE_MOTIONS_LIMIT = 25L * 1024 * 1024

/** A live photo's motion that goes inside a web page: the name the map gives it in its media folder, and the file as it leaves the phone. */
internal class PageMotion(val name: String, val outgoing: OutgoingFile)

/**
 * The map's live photos' motions that go inside its web page, as they leave
 * the phone ([OutgoingMedia]): the motions the map points at, in its order,
 * while they fit in `limit`. One too big for what's left stays out, and a
 * smaller one after it may still go; so does one Orbit can't read through,
 * unless it's one of `asTheyAre`, which the person sharing chose to share
 * as they are. The rest stay on this device, and their pictures show still.
 */
internal fun motionsFitting(markdown: String, mapFolder: File, limit: Long = PAGE_MOTIONS_LIMIT, asTheyAre: Set<String> = emptySet()): List<PageMotion> {
    var room = limit
    val media = File(mapFolder, MEDIA_DIRECTORY)

    return MediaNames.references(markdown).filter(MediaNames::isMotion).mapNotNull { name ->
        val file = File(media, name).takeIf { it.isFile && it.length() <= room } ?: return@mapNotNull null
        val outgoing = OutgoingMedia.of(file, asTheyAre)?.takeIf { it.size <= room } ?: return@mapNotNull null
        room -= outgoing.size
        PageMotion(name, outgoing)
    }
}
