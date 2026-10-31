package com.hyuchia.orbit.data

import java.io.IOException
import java.io.RandomAccessFile

/** Where bytes come from, a few at a time: a file, bytes in memory, or a part of either. */
internal interface Bytes {
    val size: Long

    /** `count` bytes from `at`, or fewer at the end. */
    fun read(at: Long, count: Int): ByteArray
}

internal class FileBytes(private val file: RandomAccessFile) : Bytes {
    override val size: Long = file.length()

    override fun read(at: Long, count: Int): ByteArray {
        val wanted = minOf(count.toLong(), (size - at).coerceAtLeast(0)).toInt()
        val bytes = ByteArray(wanted)
        file.seek(at)
        file.readFully(bytes)
        return bytes
    }
}

internal class ArrayBytes(private val array: ByteArray) : Bytes {
    override val size: Long = array.size.toLong()

    override fun read(at: Long, count: Int): ByteArray {
        val from = at.coerceIn(0, size).toInt()
        return array.copyOfRange(from, minOf(array.size, from + count))
    }
}

/** `size` bytes of `source` from `from`, as if they were all there is: an MP4 inside a picture's file, say. */
internal class SliceBytes(private val source: Bytes, private val from: Long, override val size: Long) : Bytes {
    override fun read(at: Long, count: Int): ByteArray = source.read(from + at, minOf(count.toLong(), (size - at).coerceAtLeast(0)).toInt())
}

/**
 * `source` read a chunk at a time, for reading a file through a few bytes
 * at a time, as a sound's frames are: a read the chunk it keeps has comes
 * from it, rather than from the file each time.
 */
internal class ReadAhead(private val source: Bytes, private val chunk: Int = READ_AHEAD) : Bytes {
    override val size: Long get() = source.size

    /** The chunk it keeps. */
    private var kept = ByteArray(0)

    /** Where that chunk starts in `source`. */
    private var start = 0L

    override fun read(at: Long, count: Int): ByteArray {
        if (count > chunk) {
            return source.read(at, count)
        }

        if (at < start || at + count > start + kept.size) {
            start = at
            kept = source.read(at, chunk)
        }

        val from = (at - start).toInt()
        return kept.copyOfRange(from, minOf(kept.size, from + count))
    }

    private companion object {
        const val READ_AHEAD = 64 * 1024
    }
}

/** A box of ISO's base media file format (MP4, QuickTime, HEIF and AVIF): its type, and where it starts and ends. */
internal class IsoBox(val type: String, val start: Long, val header: Int, val end: Long) {
    /** Where what's inside begins. */
    val body: Long get() = start + header

    /** How long the size and type are at its start, without a `uuid`'s own type after them. */
    val sizeHeader: Int get() = if (type == "uuid") header - 16 else header
}

/**
 * An item of a HEIF or AVIF picture: its id and type (`Exif`, `mime` with
 * its content type, `hvc1`), and where its bytes are, or null when that
 * can't be told.
 */
internal class HeifItem(val id: Long, val type: String, val contentType: String, val ranges: List<Pair<Long, Long>>?)

/**
 * Reads the boxes of ISO's base media file format, only as far as a file
 * needs to be read: see [OutgoingMedia] and [LivePhotos].
 */
internal object IsoBoxes {
    /** The boxes a file of this format starts with. */
    val FIRST_BOXES = setOf("ftyp", "moov", "mdat", "wide", "free", "skip")

    /** The brands of HEIF and AVIF pictures, whose `meta` box holds the picture itself. */
    val PICTURE_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "mif2", "msf1", "avif", "avis", "avio")

    /** The most boxes a file, or one box, holds as far as these are read. */
    private const val MOST_BOXES = 100_000

    /** The `uuid` box type XMP is stored under. */
    val XMP_UUID = byteArrayOf(
        0xBE.toByte(), 0x7A, 0xCF.toByte(), 0xCB.toByte(), 0x97.toByte(), 0xA9.toByte(), 0x42, 0xE8.toByte(),
        0x9C.toByte(), 0x71, 0x99.toByte(), 0x94.toByte(), 0x91.toByte(), 0xE3.toByte(), 0xAF.toByte(), 0xAC.toByte(),
    )

    /** Whether the first bytes of a file start with a box of this format: its `ftyp`, or an old QuickTime file's first atom. */
    fun looksLikeOne(start: ByteArray): Boolean = start.size >= 8 && String(start, 4, 4, Charsets.ISO_8859_1) in FIRST_BOXES

    /**
     * The boxes that follow each other from the start of `bytes`, and where
     * the last whole one ends: anything after it isn't a box.
     */
    fun top(bytes: Bytes): Pair<List<IsoBox>, Long> {
        val boxes = mutableListOf<IsoBox>()
        var position = 0L

        // Past more boxes than any file has, the rest isn't read as boxes: it was put there to wear the reader out.
        while (position < bytes.size && boxes.size < MOST_BOXES) {
            val box = boxAt(bytes, position, bytes.size) ?: break
            boxes += box
            position = box.end
        }

        return boxes to position
    }

    /** The boxes inside `parent`, from `skip` bytes into it (a full box's version and flags), or null when they don't fill it. */
    fun children(bytes: Bytes, parent: IsoBox, skip: Int = 0): List<IsoBox>? {
        val boxes = mutableListOf<IsoBox>()
        var position = parent.body + skip

        while (position < parent.end) {
            val box = boxAt(bytes, position, parent.end) ?: return null

            if (boxes.size == MOST_BOXES) {
                return null
            }

            boxes += box
            position = box.end
        }

        return boxes
    }

    /** The box at `at`, if a whole one starts there before `limit`. */
    fun boxAt(bytes: Bytes, at: Long, limit: Long): IsoBox? {
        if (limit - at < 8) {
            return null
        }

        val head = bytes.read(at, 8)

        if (head.size < 8) {
            return null
        }

        val small = head.unsigned(0, 4)
        val typeBytes = head.copyOfRange(4, 8)

        // A type is four letters, digits, spaces or ©, as QuickTime writes its own.
        if (!typeBytes.all { (it.toInt() and 0xFF) in 0x20..0x7E || (it.toInt() and 0xFF) == 0xA9 }) {
            return null
        }

        val type = String(typeBytes, Charsets.ISO_8859_1)
        var header = 8

        val size = when (small) {
            0L -> limit - at
            1L -> {
                if (limit - at < 16) {
                    return null
                }

                header = 16
                bytes.read(at + 8, 8).unsigned(0, 8)
            }
            else -> small
        }

        if (type == "uuid") {
            header += 16
        }

        if (size < header || size > limit - at) {
            return null
        }

        return IsoBox(type, at, header, at + size)
    }

    /** The brands a file's `ftyp` names: its major brand and the compatible ones. */
    fun brands(bytes: Bytes, type: IsoBox): Set<String> {
        val content = bytes.read(type.body, minOf(type.end - type.body, 256L).toInt())
        return (0..content.size - 4 step 4).filter { it != 4 }.map { String(content, it, 4, Charsets.ISO_8859_1) }.toSet()
    }

    fun isXmp(bytes: Bytes, box: IsoBox): Boolean = bytes.read(box.start + box.sizeHeader, 16).contentEquals(XMP_UUID)

    /** A HEIF or AVIF picture's items, from its `meta` box's parts, or null when they can't be read. */
    fun items(bytes: Bytes, parts: List<IsoBox>): List<HeifItem>? {
        val info = parts.firstOrNull { it.type == "iinf" } ?: return emptyList()
        val locations = parts.firstOrNull { it.type == "iloc" } ?: return emptyList()
        val types = itemTypes(bytes, info) ?: return null
        val data = parts.firstOrNull { it.type == "idat" }

        return (itemLocations(bytes, locations) ?: return null).mapNotNull { (id, method, ranges) ->
            val (type, contentType) = types[id] ?: return@mapNotNull null
            HeifItem(id, type, contentType, places(bytes, method, ranges, data))
        }
    }

    /** An item's bytes, at most `most` of them, or null when they're more or can't be told. */
    fun itemBytes(bytes: Bytes, item: HeifItem, most: Int): ByteArray? {
        val ranges = item.ranges ?: return null

        if (ranges.sumOf { it.second } > most) {
            return null
        }

        return ranges.fold(ByteArray(0)) { all, (at, length) -> all + bytes.read(at, length.toInt()) }
    }

    /** Where an item's bytes are in the file, given how its place is told (`method`), or null when they can't be told. */
    private fun places(bytes: Bytes, method: Int, ranges: List<Pair<Long, Long>>, data: IsoBox?): List<Pair<Long, Long>>? = ranges.map { (offset, given) ->
        val (at, limit) = when (method) {
            0 -> offset to bytes.size
            1 -> (data ?: return null).let { it.body + offset to it.end }
            else -> return null
        }

        // A length of zero runs to the end of where the item is.
        val length = if (given == 0L) limit - at else given

        if (at < 0 || length < 0 || at + length > limit) {
            return null
        }

        at to length
    }

    /** Each item's type, and a `mime` item's content type, by id. */
    private fun itemTypes(bytes: Bytes, info: IsoBox): Map<Long, Pair<String, String>>? {
        val head = bytes.read(info.body, 8)

        if (head.isEmpty()) {
            return null
        }

        val version = head.byteAt(0)
        val first = if (version == 0) 6 else 8
        val types = mutableMapOf<Long, Pair<String, String>>()

        for (entry in children(bytes, info, skip = first) ?: return null) {
            if (entry.type != "infe") {
                continue
            }

            val content = bytes.read(entry.body, minOf(entry.end - entry.body, 512L).toInt())

            if (content.size < 12) {
                continue
            }

            val entryVersion = content.byteAt(0)

            if (entryVersion < 2) {
                continue
            }

            val idSize = if (entryVersion == 2) 2 else 4
            val id = content.unsigned(4, idSize)
            val typeAt = 4 + idSize + 2
            val type = String(content, typeAt, 4, Charsets.ISO_8859_1)
            val name = content.textAt(typeAt + 4)
            val contentType = if (type == "mime") content.textAt(typeAt + 4 + name.length + 1) else ""
            types[id] = type to contentType
        }

        return types
    }

    /** Each item's id, how its place is given, and the places it's in. */
    private fun itemLocations(bytes: Bytes, locations: IsoBox): List<Triple<Long, Int, List<Pair<Long, Long>>>>? {
        val content = bytes.read(locations.body, minOf(locations.end - locations.body, 1L shl 20).toInt())

        if (content.size < 6) {
            return null
        }

        val version = content.byteAt(0)
        val offsetSize = content.byteAt(4) shr 4
        val lengthSize = content.byteAt(4) and 0x0F
        val baseSize = content.byteAt(5) shr 4
        val indexSize = if (version == 1 || version == 2) content.byteAt(5) and 0x0F else 0
        var position = 6

        fun next(size: Int): Long {
            if (size !in listOf(0, 2, 4, 8) || position + size > content.size) {
                throw IOException("Item locations that can't be read")
            }

            return content.unsigned(position, size).also { position += size }
        }

        return try {
            val count = next(if (version < 2) 2 else 4)
            val items = mutableListOf<Triple<Long, Int, List<Pair<Long, Long>>>>()

            repeat(count.toInt()) {
                val id = next(if (version < 2) 2 else 4)
                val method = if (version == 1 || version == 2) (next(2) and 0x0F).toInt() else 0
                next(2)
                val base = next(baseSize)
                val extents = next(2)

                val ranges = (0 until extents).map {
                    if (indexSize > 0) {
                        next(indexSize)
                    }

                    val offset = next(offsetSize)
                    val length = next(lengthSize)
                    base + offset to length
                }

                items += Triple(id, method, ranges)
            }

            items
        } catch (unreadable: IOException) {
            null
        }
    }
}

internal fun ByteArray.byteAt(index: Int): Int = this[index].toInt() and 0xFF

/** A big-endian number of `size` bytes from `at`. */
internal fun ByteArray.unsigned(at: Int, size: Int): Long {
    var value = 0L

    for (index in at until at + size) {
        value = (value shl 8) or byteAt(index).toLong()
    }

    return value
}

/** A little-endian number of `size` bytes from `at`, as RIFF, Ogg and APE write theirs. */
internal fun ByteArray.littleEndian(at: Int, size: Int): Long {
    var value = 0L

    for (index in at + size - 1 downTo at) {
        value = (value shl 8) or byteAt(index).toLong()
    }

    return value
}

/** Whether these bytes hold `text` from `at`, a letter a byte, as a file's signatures and four-letter names are. */
internal fun ByteArray.startsWith(text: String, at: Int = 0): Boolean =
    at >= 0 && at + text.length <= size && text.indices.all { this[at + it] == text[it].code.toByte() }

/** The text from `at` to the next zero byte, or the end. */
internal fun ByteArray.textAt(at: Int): String {
    if (at >= size) {
        return ""
    }

    val end = (at until size).firstOrNull { this[it] == 0.toByte() } ?: size
    return String(this, at, end - at, Charsets.UTF_8)
}
