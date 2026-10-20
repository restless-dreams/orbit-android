package com.hyuchia.orbit.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.ui.exportFile
import com.hyuchia.orbit.ui.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import kotlin.math.max

/** A picture drawn again to be shared is at most this many pixels on its longer side. */
private const val REDRAWN_SIZE = 4096

/**
 * Shares a picture from a map's folder as a copy without its metadata (see
 * [SharedPictures]): a GIF, BMP or SVG as it is, since they carry none, and
 * a kind Orbit can't take it out of, such as HEIF, drawn again. A picture
 * that can't be read or drawn isn't shared, since its file would say what
 * it holds.
 */
suspend fun sharePicture(context: Context, file: File, label: String) {
    try {
        val copy = withContext(Dispatchers.IO) { sharedCopy(file) }

        if (copy == null) {
            Logs.warn("OrbitMedia", "A picture couldn't be shared without its metadata")
            return
        }

        val (name, bytes) = copy
        shareFile(context, exportFile(context, name) { it.write(bytes) }, mediaType(name), label)
    } catch (failed: IOException) {
        Logs.warn("OrbitMedia", "A picture couldn't be shared", failed)
    }
}

/** What sharing a picture sends: its name and bytes, without what its file says beyond the picture. */
private fun sharedCopy(file: File): Pair<String, ByteArray>? {
    val bytes = file.readBytes()

    return when (SharedPictures.kindOf(bytes)) {
        SharedPictures.Kind.Plain -> file.name to bytes
        SharedPictures.Kind.Jpeg, SharedPictures.Kind.Png, SharedPictures.Kind.WebP -> SharedPictures.withoutMetadata(bytes)?.let { file.name to it } ?: redrawn(file)
        SharedPictures.Kind.Other -> redrawn(file)
    }
}

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

        /** GIF, BMP or SVG, which carry no camera's details, and go as they are. */
        Plain,

        /** Anything else, such as HEIF and AVIF: drawn again to be shared. */
        Other,
    }

    fun kindOf(bytes: ByteArray): Kind = when {
        bytes.startsWith(JPEG_START) -> Kind.Jpeg
        bytes.startsWith(PNG_SIGNATURE) -> Kind.Png
        bytes.startsWith(RIFF) && bytes.startsWith(WEBP, at = 8) -> Kind.WebP
        bytes.startsWith(GIF) || bytes.startsWith(BMP) || looksLikeSvg(bytes) -> Kind.Plain
        else -> Kind.Other
    }

    /**
     * A JPEG, PNG or WebP picture's bytes with nothing but the picture: its
     * pixels, its color profile and which way up it is. Everything else goes,
     * EXIF and XMP (where a camera writes where the photo was taken), IPTC,
     * comments, thumbnails and pictures stored after it. Null for any other
     * kind, or a file that isn't whole.
     */
    fun withoutMetadata(bytes: ByteArray): ByteArray? = when (kindOf(bytes)) {
        Kind.Jpeg -> jpeg(bytes)
        Kind.Png -> png(bytes)
        Kind.WebP -> webP(bytes)
        Kind.Plain, Kind.Other -> null
    }

    private fun jpeg(bytes: ByteArray): ByteArray? {
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
    private fun assembleJpeg(bytes: ByteArray, kept: List<IntRange>, orientation: Int): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        output.write(JPEG_START)
        val jfifFirst = kept.firstOrNull()?.let { bytes.byteAt(it.first + 1) == APP0 } == true

        kept.forEachIndexed { index, range ->
            if (index == (if (jfifFirst) 1 else 0) && orientation != UPRIGHT) {
                val tiff = orientationTiff(orientation)
                output.write(byteArrayOf(0xFF.toByte(), APP1.toByte()))
                output.writeShort(2 + EXIF_HEADER.size + tiff.size, bigEndian = true)
                output.write(EXIF_HEADER)
                output.write(tiff)
            }

            output.write(bytes, range.first, range.last - range.first + 1)
        }

        return output.toByteArray()
    }

    /** Where a scan's coded picture ends: at the next marker, past stuffed bytes and restarts. */
    private fun scanEnd(bytes: ByteArray, from: Int): Int? {
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
    private fun keepsJpegSegment(marker: Int, bytes: ByteArray, data: Int): Boolean = when (marker) {
        APP0 -> bytes.startsWith(JFIF_HEADER, at = data)
        APP2 -> bytes.startsWith(ICC_HEADER, at = data)
        APP14 -> bytes.startsWith(ADOBE_HEADER, at = data)
        in APP0..APP15, JPEG_COMMENT -> false
        else -> true
    }

    private fun png(bytes: ByteArray): ByteArray? {
        val kept = mutableListOf<IntRange>()
        var orientation = UPRIGHT
        var position = PNG_SIGNATURE.size

        while (position + 8 <= bytes.size) {
            val length = bytes.intAt(position, bigEndian = true)
            val type = String(bytes, position + 4, 4, Charsets.ISO_8859_1)
            val end = position + 12 + length

            if (length < 0 || end > bytes.size) {
                return null
            }

            if (type == "eXIf") {
                orientation = orientationOf(bytes, position + 8, position + 8 + length) ?: orientation
            }

            if (type !in PNG_METADATA) {
                kept += position until end
            }

            if (type == "IEND") {
                return assemblePng(bytes, kept, orientation)
            }

            position = end
        }

        return null
    }

    /** The PNG again from the chunks it keeps, with which way up it is when that isn't upright, before its picture data as the format asks. */
    private fun assemblePng(bytes: ByteArray, kept: List<IntRange>, orientation: Int): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        output.write(PNG_SIGNATURE)
        var placed = orientation == UPRIGHT

        for (range in kept) {
            val type = String(bytes, range.first + 4, 4, Charsets.ISO_8859_1)

            if (!placed && (type == "IDAT" || type == "IEND")) {
                val tiff = orientationTiff(orientation)
                val chunk = "eXIf".toByteArray(Charsets.ISO_8859_1) + tiff
                output.writeInt(tiff.size, bigEndian = true)
                output.write(chunk)
                output.writeInt(CRC32().apply { update(chunk) }.value.toInt(), bigEndian = true)
                placed = true
            }

            output.write(bytes, range.first, range.last - range.first + 1)
        }

        return output.toByteArray()
    }

    private fun webP(bytes: ByteArray): ByteArray? {
        val kept = mutableListOf<IntRange>()
        var orientation = UPRIGHT
        var features: Int? = null
        var position = 12

        while (position + 8 <= bytes.size) {
            val type = String(bytes, position, 4, Charsets.ISO_8859_1)
            val size = bytes.intAt(position + 4, bigEndian = false)
            val end = position + 8 + size + size % 2

            if (size < 0 || position + 8 + size > bytes.size) {
                return null
            }

            when (type) {
                "EXIF" -> orientationOf(bytes, position + 8 + if (bytes.startsWith(EXIF_HEADER, at = position + 8)) EXIF_HEADER.size else 0, position + 8 + size)?.let { orientation = it }
                "XMP " -> Unit
                else -> kept += position until minOf(end, bytes.size)
            }

            if (type == "VP8X") {
                features = position + 8
            }

            position = end
        }

        val output = ByteArrayOutputStream(bytes.size)
        output.write(RIFF)
        output.writeInt(0, bigEndian = false)
        output.write(WEBP)

        for (range in kept) {
            val start = output.size()
            output.write(bytes, range.first, range.last - range.first + 1)

            // A chunk of an odd size is followed by a byte of padding, even at the end of a file that left it out.
            if ((range.last - range.first + 1) % 2 == 1) {
                output.write(0)
            }

            // The extended format's header says which metadata the file has: none now, but which way up it is.
            if (features != null && range.first + 8 == features) {
                val flags = bytes.byteAt(features) and (WEBP_HAS_EXIF or WEBP_HAS_XMP).inv()
                val withOrientation = if (orientation != UPRIGHT) flags or WEBP_HAS_EXIF else flags
                output.overwrite(start + 8, withOrientation)
            }
        }

        if (orientation != UPRIGHT && features != null) {
            val tiff = orientationTiff(orientation)
            output.write("EXIF".toByteArray(Charsets.ISO_8859_1))
            output.writeInt(tiff.size, bigEndian = false)
            output.write(tiff)
        }

        val result = output.toByteArray()
        val size = result.size - 8
        result[4] = size.toByte()
        result[5] = (size shr 8).toByte()
        result[6] = (size shr 16).toByte()
        result[7] = (size shr 24).toByte()
        return result
    }

    /** The orientation an EXIF block (TIFF, from `start` to `end`) gives its picture, 1 to 8, or null when it gives none. */
    private fun orientationOf(bytes: ByteArray, start: Int, end: Int): Int? {
        if (end - start < 8) {
            return null
        }

        val bigEndian = when {
            bytes.startsWith(byteArrayOf(0x4D, 0x4D), at = start) -> true
            bytes.startsWith(byteArrayOf(0x49, 0x49), at = start) -> false
            else -> return null
        }

        val directory = start + bytes.intAt(start + 4, bigEndian)

        if (directory < start || directory + 2 > end) {
            return null
        }

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

    private fun ByteArray.shortAt(index: Int, bigEndian: Boolean): Int =
        if (bigEndian) (byteAt(index) shl 8) or byteAt(index + 1) else byteAt(index) or (byteAt(index + 1) shl 8)

    private fun ByteArray.intAt(index: Int, bigEndian: Boolean): Int = if (bigEndian) {
        (shortAt(index, bigEndian = true) shl 16) or shortAt(index + 2, bigEndian = true)
    } else {
        shortAt(index, bigEndian = false) or (shortAt(index + 2, bigEndian = false) shl 16)
    }

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

    /** Changes the byte written at `index`. */
    private fun ByteArrayOutputStream.overwrite(index: Int, value: Int) {
        val bytes = toByteArray()
        bytes[index] = value.toByte()
        reset()
        write(bytes)
    }

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

    /** A PNG's chunks that say something besides the picture: EXIF, text, and when it was made. */
    private val PNG_METADATA = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")
}
