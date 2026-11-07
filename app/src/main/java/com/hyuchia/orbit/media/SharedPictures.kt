package com.hyuchia.orbit.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.data.readAtMost
import com.hyuchia.orbit.ui.openMapExport
import com.hyuchia.orbit.ui.openMapFile
import com.hyuchia.orbit.ui.shareMapExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.math.max

/** A picture drawn again to be shared is at most this many pixels on its longer side. */
private const val REDRAWN_SIZE = 4096

/** How sharing a file from a map went. */
enum class Shared {
    /** It went, without what its file says beyond what it shows or plays. */
    Clean,

    /** It didn't go: Orbit couldn't take that out, and its file would say it ([shareAsItIs] sends it if the person chooses). */
    NotClean,

    /** It didn't go: it couldn't be read or written. */
    Failed,
}

/**
 * Shares a picture from the folder of the map `mapId` as a copy without its
 * metadata (see [SharedPictures]): a GIF without its comments and other
 * apps' blocks, an SVG with only what draws it, a BMP with nothing but its
 * picture ([OutgoingMedia]), and a kind Orbit can't take it out of, such as
 * HEIF, drawn again. A picture that can't be read or drawn isn't shared
 * ([Shared.NotClean]), since its file would say what it holds. Nothing
 * opens for a private map that locked meanwhile ([shareMapExport]).
 */
suspend fun sharePicture(context: Context, mapId: String, file: File, label: String): Shared = try {
    val copy = withContext(Dispatchers.IO) { sharedCopy(file) }

    if (copy == null) {
        Shared.NotClean
    } else {
        shareMapExport(context, mapId, copy.name, mediaType(copy.name), label) { output ->
            // A picture can be 250 MB: it's begun only where there's room for all of it.
            this(copy.size)
            copy.write(output)
        }

        Shared.Clean
    }
} catch (failed: IOException) {
    Logs.warn("OrbitMedia", "A picture couldn't be shared", failed)
    Shared.Failed
}

/**
 * Shares a video or a sound from the folder of the map `mapId` as a copy
 * without what its file says beyond what it shows or plays, such as where
 * and when it was recorded and on what, or its tags ([OutgoingMedia]), as a
 * map's zip carries it. One Orbit can't read through isn't shared
 * ([Shared.NotClean]), since its file would say what it holds. Nothing
 * opens for a private map that locked while the copy was made
 * ([shareMapExport]).
 */
suspend fun shareVideoOrSound(context: Context, mapId: String, file: File, type: String, label: String): Shared = try {
    val outgoing = withContext(Dispatchers.IO) { OutgoingMedia.of(file) }

    if (outgoing == null) {
        Shared.NotClean
    } else {
        shareMapExport(context, mapId, file.name, type, label) { output -> outgoing.writeTo(output) }
        Shared.Clean
    }
} catch (failed: IOException) {
    Logs.warn("OrbitMedia", "A video or sound couldn't be shared", failed)
    Shared.Failed
}

/**
 * Opens a video from the folder of the map `mapId` in another app, as the
 * phone's video app, with a copy without what its file says beyond what it
 * shows and plays ([OutgoingMedia]), as sharing it sends one. One Orbit
 * can't read through isn't opened ([Shared.NotClean]): the person chooses
 * whether it goes as it is ([openVideoAsItIs]). Nothing opens for a private
 * map that locked while the copy was made, nor without room for the copy
 * ([Shared.Failed], [openMapExport]).
 */
suspend fun openVideoInAnotherApp(context: Context, mapId: String, file: File): Shared = try {
    val outgoing = withContext(Dispatchers.IO) { OutgoingMedia.of(file) }

    if (outgoing == null) {
        Shared.NotClean
    } else {
        openMapExport(context, mapId, file.name, videoType(file)) { output ->
            // Not begun on a storage without room for all of it, and some to spare.
            invoke(outgoing.size)
            outgoing.writeTo(output)
        }

        Shared.Clean
    }
} catch (failed: IOException) {
    Logs.warn("OrbitMedia", "A video couldn't be opened in another app", failed)
    Shared.Failed
}

/**
 * Opens a video from the folder of the map `mapId` in another app exactly
 * as it is, with all its file says: only once the person chose to, knowing
 * what goes with it, after Orbit couldn't take that out
 * ([Shared.NotClean]), and while the map doesn't show locked.
 */
fun openVideoAsItIs(context: Context, mapId: String, file: File) {
    openMapFile(context, mapId, file, videoType(file))
}

/** The media type a video opens in another app as: its own, as its name says, or any video's. */
private fun videoType(file: File): String = mediaType(file.name).takeIf { it.startsWith("video/") } ?: "video/*"

/**
 * Shares a file from the folder of the map `mapId` exactly as it is, with
 * all its file says: only once the person chose to, knowing what goes with
 * it, after Orbit couldn't take that out ([Shared.NotClean]), and while the
 * map doesn't show locked ([shareMapExport]).
 */
suspend fun shareAsItIs(context: Context, mapId: String, file: File, type: String, label: String) {
    try {
        shareMapExport(context, mapId, file.name, type, label) { output -> file.inputStream().use { it.copyTo(output) } }
    } catch (failed: IOException) {
        Logs.warn("OrbitMedia", "A file couldn't be shared as it is", failed)
    }
}

/** What sharing a picture sends: its name, about how big it is, and what writes it, without what its file says beyond the picture. */
private class SharedCopy(val name: String, val size: Long, val write: (OutputStream) -> Unit)

/**
 * What sharing `file` sends: the picture as it leaves the phone in a zip,
 * written from its file a piece at a time, however big it is
 * ([OutgoingMedia]); one that can't be read through drawn again, but a
 * drawing, which can't be drawn again either, and isn't shared.
 */
private fun sharedCopy(file: File): SharedCopy? {
    OutgoingMedia.of(file)?.let { outgoing -> return SharedCopy(file.name, outgoing.size) { output -> outgoing.writeTo(output) } }
    val start = file.inputStream().use { it.readAtMost(KIND_BYTES) }

    if (SharedPictures.kindOf(start) == SharedPictures.Kind.Svg) {
        return null
    }

    return redrawn(file)?.let { (name, bytes) -> SharedCopy(name, bytes.size.toLong()) { output -> output.write(bytes) } }
}

/** How much of a picture's file tells its kind. */
private const val KIND_BYTES = 4096

/** A picture drawn again, as JPEG, or PNG when it's see-through: nothing of its file comes along. Null when it can't be drawn. */
private fun redrawn(file: File): Pair<String, ByteArray>? {
    val bitmap = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)

            if (longest > REDRAWN_SIZE) {
                val scale = REDRAWN_SIZE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }

            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (undrawable: IOException) {
        return null
    }

    val transparent = bitmap.hasAlpha()

    val bytes = ByteArrayOutputStream().use { output ->
        bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, output)
        output.toByteArray()
    }

    bitmap.recycle()
    return "${file.nameWithoutExtension}.${if (transparent) "png" else "jpg"}" to bytes
}

/**
 * Pictures as they're shared from a map: without what their files say
 * beyond the picture, such as where and when it was taken and on what. A
 * picture added in Orbit has none of that, since Orbit draws it again to
 * keep it; one that came with a map from elsewhere (an import, the folder
 * maps are kept in) may. The map's own file stays as it is.
 */
internal object SharedPictures {
    /** What a picture's file is, from its first bytes. */
    enum class Kind {
        Jpeg,
        Png,
        WebP,

        /** A GIF, which keeps its frames and looping, without comments or other apps' blocks. */
        Gif,

        /** An SVG drawing, which keeps only what draws it. */
        Svg,

        /** A BMP, whose headers say only what the picture is ([OutgoingMedia] empties what apps may keep around it). */
        Plain,

        /** Anything else, such as HEIF and AVIF: drawn again to be shared. */
        Other,
    }

    fun kindOf(bytes: ByteArray): Kind = when {
        bytes.startsWith(JPEG_START) -> Kind.Jpeg
        bytes.startsWith(PNG_SIGNATURE) -> Kind.Png
        bytes.startsWith(RIFF) && bytes.startsWith(WEBP, at = 8) -> Kind.WebP
        bytes.startsWith(GIF) -> Kind.Gif
        looksLikeSvg(bytes) -> Kind.Svg
        bytes.startsWith(BMP) -> Kind.Plain
        else -> Kind.Other
    }

    /**
     * A JPEG, PNG or WebP picture's bytes with nothing but the picture: its
     * pixels, its color profile and which way up it is, and an animated
     * PNG's or WebP's frames. Everything else goes, EXIF and XMP (where a
     * camera writes where the photo was taken), IPTC, comments, thumbnails,
     * pictures stored after it, and the chunks and segments apps keep their
     * own in, which a PNG or WebP keeps only when they draw it. A GIF keeps
     * its frames and looping, and an SVG what draws it ([gifWithoutMetadata],
     * [svgWithoutMetadata]). Null for any other kind, or a file that isn't
     * whole.
     */
    fun withoutMetadata(bytes: ByteArray): ByteArray? = try {
        when (kindOf(bytes)) {
            Kind.Jpeg, Kind.Png, Kind.WebP, Kind.Gif -> plan(PictureInMemory(bytes))?.bytes(bytes)
            Kind.Svg -> svgWithoutMetadata(bytes)
            Kind.Plain, Kind.Other -> null
        }
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    /**
     * How a JPEG, PNG, WebP or GIF picture goes, as [withoutMetadata] makes
     * it: the parts of `bytes` it keeps, with what's written in place of
     * what goes. So a picture in its file ([PictureInFile]) goes a piece at a
     * time, however big it is. Null for any other kind, or a file that isn't
     * whole; reading its file can fail too, as one that changes meanwhile.
     */
    fun plan(bytes: PictureBytes): PicturePieces? = try {
        when (kindOf(bytes.bytes(0, minOf(bytes.size, KIND_BYTES)))) {
            Kind.Jpeg -> jpeg(bytes)
            Kind.Png -> png(bytes)
            Kind.WebP -> webP(bytes)
            Kind.Gif -> gifWithoutMetadata(bytes)
            Kind.Svg, Kind.Plain, Kind.Other -> null
        }
    } catch (malformed: RuntimeException) {
        // A file made to trip the reader is one it can't read through, as a broken one is.
        null
    }

    private fun jpeg(bytes: PictureBytes): PicturePieces? {
        val kept = mutableListOf<IntRange>()
        var orientation = UPRIGHT
        var position = 2

        while (position + 1 < bytes.size) {
            if (bytes.byteAt(position) != 0xFF) {
                return null
            }

            val marker = bytes.byteAt(position + 1)

            if (marker == 0xFF) {
                // A fill byte before a marker.
                position += 1
                continue
            }

            if (marker == JPEG_END) {
                kept += position until position + 2
                return assembleJpeg(bytes, kept, orientation)
            }

            if (marker in JPEG_RESTARTS || marker == JPEG_TEMPORARY) {
                kept += position until position + 2
                position += 2
                continue
            }

            if (position + 3 >= bytes.size) {
                return null
            }

            val end = position + 2 + bytes.shortAt(position + 2, bigEndian = true)

            if (end <= position + 3 || end > bytes.size) {
                return null
            }

            if (marker == JPEG_SCAN) {
                val next = scanEnd(bytes, end) ?: return null
                kept += position until next
                position = next
                continue
            }

            val data = position + 4

            if (marker == APP1 && bytes.startsWith(EXIF_HEADER, at = data)) {
                orientation = orientationOf(bytes, data + EXIF_HEADER.size, end) ?: orientation
            }

            if (keepsJpegSegment(marker, bytes, data)) {
                kept += position until end
            }

            position = end
        }

        return null
    }

    /** The JPEG again from the parts it keeps, with which way up it is when that isn't upright: after JFIF's segment, where EXIF's goes. */
    private fun assembleJpeg(bytes: PictureBytes, kept: List<IntRange>, orientation: Int): PicturePieces {
        val output = PicturePieces()
        output.write(JPEG_START)
        val jfifFirst = kept.firstOrNull()?.let { bytes.byteAt(it.first + 1) == APP0 } == true

        kept.forEachIndexed { index, range ->
            if (index == (if (jfifFirst) 1 else 0) && orientation != UPRIGHT) {
                val tiff = orientationTiff(orientation)
                val segment = ByteArrayOutputStream()
                segment.write(byteArrayOf(0xFF.toByte(), APP1.toByte()))
                segment.writeShort(2 + EXIF_HEADER.size + tiff.size, bigEndian = true)
                segment.write(EXIF_HEADER)
                segment.write(tiff)
                output.write(segment.toByteArray())
            }

            output.keep(range)
        }

        return output
    }

    /** Where a scan's coded picture ends: at the next marker, past stuffed bytes and restarts. */
    private fun scanEnd(bytes: PictureBytes, from: Int): Int? {
        var position = from

        while (position + 1 < bytes.size) {
            if (bytes.byteAt(position) == 0xFF) {
                val next = bytes.byteAt(position + 1)

                if (next != 0x00 && next !in JPEG_RESTARTS) {
                    return position
                }
            }

            position += 1
        }

        return null
    }

    /**
     * Whether a JPEG keeps a segment: everything that makes the picture, and
     * of the application segments only JFIF's, the color profile and Adobe's
     * color transform. Comments, and the other application segments (EXIF,
     * XMP, IPTC, thumbnails, more pictures), go.
     */
    private fun keepsJpegSegment(marker: Int, bytes: PictureBytes, data: Int): Boolean = when (marker) {
        APP0 -> bytes.startsWith(JFIF_HEADER, at = data)
        APP2 -> bytes.startsWith(ICC_HEADER, at = data)
        APP14 -> bytes.startsWith(ADOBE_HEADER, at = data)
        in APP0..APP15, JPEG_COMMENT -> false
        else -> true
    }

    private fun png(bytes: PictureBytes): PicturePieces? {
        val kept = mutableListOf<IntRange>()
        var orientation = UPRIGHT
        var position = PNG_SIGNATURE.size

        while (position + 8 <= bytes.size) {
            val length = bytes.intAt(position, bigEndian = true)
            val type = bytes.text(position + 4, 4)

            // Counted in Long: a length near 2³¹ would otherwise wrap around past the check.
            if (length < 0 || position + 12L + length > bytes.size) {
                return null
            }

            val end = position + 12 + length

            if (type == "eXIf") {
                orientation = orientationOf(bytes, position + 8, position + 8 + length) ?: orientation
            }

            if (type in PNG_PICTURE) {
                kept += position until end
            } else if (!type.all { it in 'A'..'Z' || it in 'a'..'z' } || type[0] in 'A'..'Z') {
                // A chunk the picture can't be read without (its first letter says so), of a kind Orbit doesn't know, as Apple's CgBI, or no chunk at all: what it holds can't be told.
                return null
            }

            if (type == "IEND") {
                return assemblePng(bytes, kept, orientation)
            }

            position = end
        }

        return null
    }

    /** The PNG again from the chunks it keeps, with which way up it is when that isn't upright, before its picture data as the format asks. */
    private fun assemblePng(bytes: PictureBytes, kept: List<IntRange>, orientation: Int): PicturePieces {
        val output = PicturePieces()
        output.write(PNG_SIGNATURE)
        var placed = orientation == UPRIGHT

        for (range in kept) {
            val type = bytes.text(range.first + 4, 4)

            if (!placed && (type == "IDAT" || type == "IEND")) {
                val tiff = orientationTiff(orientation)
                val chunk = "eXIf".toByteArray(Charsets.ISO_8859_1) + tiff
                val written = ByteArrayOutputStream()
                written.writeInt(tiff.size, bigEndian = true)
                written.write(chunk)
                written.writeInt(CRC32().apply { update(chunk) }.value.toInt(), bigEndian = true)
                output.write(written.toByteArray())
                placed = true
            }

            output.keep(range)
        }

        return output
    }

    /** A chunk of a WebP's RIFF: its type, where it starts, and how long what it holds is. */
    private class WebPChunk(val type: String, val start: Int, val size: Int) {
        val data: Int get() = start + 8
    }

    private fun webP(bytes: PictureBytes): PicturePieces? {
        // The RIFF says where the file ends: what follows isn't the picture's, and stays out. One that ends past the file, but for the padding its last chunk may leave out, isn't whole.
        val riffEnd = 8L + (bytes.intAt(4, bigEndian = false).toLong() and 0xFFFFFFFFL)

        if (riffEnd > bytes.size + 1L) {
            return null
        }

        val chunks = webPChunks(bytes, 12, minOf(riffEnd, bytes.size.toLong()).toInt()) ?: return null
        // Which way up it is, as the last EXIF that says so does: all that stays of its EXIF.
        val orientation = chunks.filter { it.type == "EXIF" }
            .mapNotNull { exif -> orientationOf(bytes, exif.data + if (bytes.startsWith(EXIF_HEADER, at = exif.data)) EXIF_HEADER.size else 0, exif.data + exif.size) }
            .lastOrNull() ?: UPRIGHT
        val extended = chunks.any { it.type == "VP8X" }
        val body = PicturePieces()

        for (chunk in chunks) {
            when (chunk.type) {
                "ANMF" -> {
                    if (!body.writeWebPFrame(bytes, chunk)) {
                        return null
                    }
                }
                // The extended format's header says which metadata the file has: none now, but which way up it is.
                "VP8X" -> {
                    val flags = bytes.byteAt(chunk.data) and (WEBP_HAS_EXIF or WEBP_HAS_XMP).inv()
                    body.keep(chunk.start until chunk.data)
                    body.write(byteArrayOf((if (orientation != UPRIGHT) flags or WEBP_HAS_EXIF else flags).toByte()))
                    body.keep(chunk.data + 1 until chunk.data + chunk.size)
                    body.pad(chunk.size)
                }
                in WEBP_PICTURE -> body.keepWebPChunk(chunk)
            }
        }

        if (orientation != UPRIGHT && extended) {
            val tiff = orientationTiff(orientation)
            val chunk = ByteArrayOutputStream()
            chunk.write("EXIF".toByteArray(Charsets.ISO_8859_1))
            chunk.writeInt(tiff.size, bigEndian = false)
            chunk.write(tiff)
            body.write(chunk.toByteArray())
        }

        // Its RIFF header says how long the rest is, WEBP and its chunks.
        val header = ByteArrayOutputStream()
        header.write(RIFF)
        header.writeInt((4 + body.size).toInt(), bigEndian = false)
        header.write(WEBP)
        val output = PicturePieces()
        output.write(header.toByteArray())
        output.write(body)
        return output
    }

    /**
     * The chunks from `from` to `until`, or null when one runs past it. The
     * last may leave out the byte of padding that follows a chunk of an odd
     * size; fewer bytes than a chunk's header after it aren't one, and stay
     * out.
     */
    private fun webPChunks(bytes: PictureBytes, from: Int, until: Int): List<WebPChunk>? {
        val chunks = mutableListOf<WebPChunk>()
        var position = from

        while (position + 8 <= until) {
            val size = bytes.intAt(position + 4, bigEndian = false)

            // Counted in Long: a size near 2³¹ would otherwise wrap around past the check.
            if (size < 0 || position + 8L + size > until) {
                return null
            }

            chunks += WebPChunk(bytes.text(position, 4), position, size)
            position += 8 + size + size % 2
        }

        return chunks
    }

    /** Keeps a chunk as it is, its header and what it holds, and a byte of padding after an odd size, written as zero. */
    private fun PicturePieces.keepWebPChunk(chunk: WebPChunk) {
        keep(chunk.start until chunk.data + chunk.size)
        pad(chunk.size)
    }

    /** The byte of padding a chunk of an odd `size` is followed by. */
    private fun PicturePieces.pad(size: Int) {
        if (size % 2 == 1) {
            write(byteArrayOf(0))
        }
    }

    /**
     * Writes an animation's frame with only what draws it: where it goes
     * and for how long, its transparency and its image data. Another app's
     * chunks inside it go too. False, writing nothing, when it can't be read
     * through.
     */
    private fun PicturePieces.writeWebPFrame(bytes: PictureBytes, frame: WebPChunk): Boolean {
        if (frame.size < WEBP_FRAME_HEADER) {
            return false
        }

        val parts = webPChunks(bytes, frame.data + WEBP_FRAME_HEADER, frame.data + frame.size)?.filter { it.type in WEBP_FRAME } ?: return false
        val content = PicturePieces()
        content.keep(frame.data until frame.data + WEBP_FRAME_HEADER)
        parts.forEach { content.keepWebPChunk(it) }
        val header = ByteArrayOutputStream()
        header.write("ANMF".toByteArray(Charsets.ISO_8859_1))
        header.writeInt(content.size.toInt(), bigEndian = false)
        write(header.toByteArray())
        write(content)
        // A frame's content is always even: its own part is 16 bytes, and each chunk in it is padded.
        return true
    }

    /** The orientation an EXIF block (TIFF, from `start` to `end`) gives its picture, 1 to 8, or null when it gives none. */
    private fun orientationOf(bytes: PictureBytes, start: Int, end: Int): Int? {
        if (end - start < 8) {
            return null
        }

        val bigEndian = when {
            bytes.startsWith(byteArrayOf(0x4D, 0x4D), at = start) -> true
            bytes.startsWith(byteArrayOf(0x49, 0x49), at = start) -> false
            else -> return null
        }

        val offset = bytes.intAt(start + 4, bigEndian)

        // Counted in Long: an offset near 2³¹ would otherwise wrap around past the check.
        if (offset < 0 || start.toLong() + offset + 2 > end) {
            return null
        }

        val directory = start + offset
        val entries = bytes.shortAt(directory, bigEndian)

        for (index in 0 until entries) {
            val entry = directory + 2 + index * 12

            if (entry + 12 > end) {
                return null
            }

            if (bytes.shortAt(entry, bigEndian) == ORIENTATION_TAG && bytes.shortAt(entry + 2, bigEndian) == TIFF_SHORT) {
                return bytes.shortAt(entry + 8, bigEndian).takeIf { it in 1..8 }
            }
        }

        return null
    }

    /** An EXIF block (TIFF, big-endian) that says only which way up its picture is. */
    private fun orientationTiff(orientation: Int): ByteArray {
        val output = ByteArrayOutputStream(26)
        output.write(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A))
        output.writeInt(8, bigEndian = true)
        output.writeShort(1, bigEndian = true)
        output.writeShort(ORIENTATION_TAG, bigEndian = true)
        output.writeShort(TIFF_SHORT, bigEndian = true)
        output.writeInt(1, bigEndian = true)
        output.writeShort(orientation, bigEndian = true)
        output.writeShort(0, bigEndian = true)
        output.writeInt(0, bigEndian = true)
        return output.toByteArray()
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val start = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return start.startsWith("<svg") || (start.startsWith("<?xml") && "<svg" in start)
    }

    private fun ByteArray.byteAt(index: Int): Int = this[index].toInt() and 0xFF

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean =
        at >= 0 && at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }

    private fun ByteArrayOutputStream.writeShort(value: Int, bigEndian: Boolean) {
        if (bigEndian) {
            write(value shr 8 and 0xFF)
            write(value and 0xFF)
        } else {
            write(value and 0xFF)
            write(value shr 8 and 0xFF)
        }
    }

    private fun ByteArrayOutputStream.writeInt(value: Int, bigEndian: Boolean) {
        if (bigEndian) {
            writeShort(value ushr 16, bigEndian = true)
            writeShort(value and 0xFFFF, bigEndian = true)
        } else {
            writeShort(value and 0xFFFF, bigEndian = false)
            writeShort(value ushr 16, bigEndian = false)
        }
    }

    /** How many of a picture's first bytes tell its kind ([kindOf]): as far as an SVG's element, after its XML declaration. */
    private const val KIND_BYTES = 512

    private const val UPRIGHT = 1
    private const val ORIENTATION_TAG = 0x0112
    private const val TIFF_SHORT = 3

    private const val APP0 = 0xE0
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val APP14 = 0xEE
    private const val APP15 = 0xEF
    private const val JPEG_COMMENT = 0xFE
    private const val JPEG_SCAN = 0xDA
    private const val JPEG_END = 0xD9
    private const val JPEG_TEMPORARY = 0x01
    private val JPEG_RESTARTS = 0xD0..0xD7

    private const val WEBP_HAS_EXIF = 0x08
    private const val WEBP_HAS_XMP = 0x04

    private val JPEG_START = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val RIFF = "RIFF".toByteArray(Charsets.ISO_8859_1)
    private val WEBP = "WEBP".toByteArray(Charsets.ISO_8859_1)
    private val GIF = "GIF8".toByteArray(Charsets.ISO_8859_1)
    private val BMP = "BM".toByteArray(Charsets.ISO_8859_1)
    private val EXIF_HEADER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
    private val JFIF_HEADER = byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00)
    private val ICC_HEADER = "ICC_PROFILE".toByteArray(Charsets.ISO_8859_1) + 0.toByte()
    private val ADOBE_HEADER = "Adobe".toByteArray(Charsets.ISO_8859_1)

    /**
     * A PNG's chunks that draw the picture: its header, palette, data and
     * end; its transparency and background; how its colors look (cHRM,
     * gAMA, iCCP, sBIT, sRGB, cICP, and HDR's mDCV and cLLI); how big it
     * prints; and an animation's control and frames (acTL, fcTL, fdAT). Every
     * other chunk goes: EXIF, text and when it was made, and the private
     * chunks apps keep their own in, such as content credentials (caBX).
     */
    private val PNG_PICTURE = setOf(
        "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "bKGD", "cHRM", "gAMA", "iCCP", "sBIT", "sRGB", "cICP", "mDCV", "cLLI", "pHYs", "acTL", "fcTL", "fdAT",
    )

    /**
     * A WebP's chunks that draw the picture: its features (VP8X), color
     * profile, animation (ANIM, and each frame's ANMF) and image data, lossy
     * or lossless, with its transparency. EXIF, XMP and every other app's
     * chunk, as content credentials (C2PA), go.
     */
    private val WEBP_PICTURE = setOf("VP8X", "ICCP", "ANIM", "ALPH", "VP8 ", "VP8L")

    /** What an animation's frame (ANMF) holds after where it goes and for how long: its transparency and image data. */
    private val WEBP_FRAME = setOf("ALPH", "VP8 ", "VP8L")

    /** How long a WebP frame's own part is, before its image data: where it goes, its size, how long it shows and how. */
    private const val WEBP_FRAME_HEADER = 16
}
