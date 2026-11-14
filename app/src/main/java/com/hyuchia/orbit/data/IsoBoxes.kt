package com.hyuchia.orbit.data

import java.io.ByteArrayOutputStream
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
 * Where an item of a HEIF or AVIF picture is named in its entry: from `at`
 * to the end of the entry, at `end`, its name, the zero that ends it, and
 * what follows that, `after` (a `mime` item's content type and encoding,
 * a `uri ` item's type), as far as it's read.
 */
internal class ItemName(val at: Long, val end: Long, val after: ByteArray)

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

    /**
     * Whether every frame a movie's tracks keep is in the file before `end`:
     * each chunk of them starts before it, as its track's `stco` says, or
     * past 4 GB its `co64`. A track without a sample table says nothing
     * about where its frames are; one whose table can't be read can't be
     * told to have them.
     */
    fun chunksBefore(bytes: Bytes, movie: IsoBox, end: Long): Boolean {
        for (track in children(bytes, movie) ?: return false) {
            if (track.type != "trak") {
                continue
            }

            val table = child(bytes, track, "mdia")?.let { child(bytes, it, "minf") }?.let { child(bytes, it, "stbl") } ?: continue
            val offsets = (children(bytes, table) ?: return false).firstOrNull { it.type == "stco" || it.type == "co64" } ?: continue
            val size = if (offsets.type == "co64") 8 else 4
            val count = bytes.read(offsets.body + 4, 4).takeIf { it.size == 4 }?.unsigned(0, 4) ?: return false
            var at = offsets.body + 8
            val until = at + count * size

            if (until > offsets.end) {
                return false
            }

            while (at < until) {
                val entries = bytes.read(at, minOf(until - at, CHUNKS_READ).toInt())

                // An offset past 2⁶³ reads as less than nothing: it's past the end all the same.
                if (entries.size < size || (0 until entries.size / size).any { entries.unsigned(it * size, size).let { offset -> offset < 0 || offset >= end } }) {
                    return false
                }

                at += entries.size / size * size
            }
        }

        return true
    }

    /**
     * Whether the samples a movie's fragment (`moof`) holds start in the
     * file before `end`, wherever its media data is, before or after it:
     * each run of them (`trun`) at its offset from its track fragment's
     * base, which its header (`tfhd`) gives, or else the fragment's start.
     * A track fragment after the first with no base of its own starts after
     * the one before, so this finds where its runs start at the earliest.
     */
    fun fragmentBefore(bytes: Bytes, fragment: IsoBox, end: Long): Boolean {
        for (track in children(bytes, fragment) ?: return false) {
            if (track.type != "traf") {
                continue
            }

            val parts = children(bytes, track) ?: return false
            // Its version and flags, its track, and the base when its first flag says it's there.
            val header = parts.firstOrNull { it.type == "tfhd" }?.let { bytes.read(it.body, minOf(it.end - it.body, 16L).toInt()) }?.takeIf { it.size >= 8 } ?: return false
            val base = if ((header.byteAt(3) and 1) == 0) fragment.start else header.takeIf { it.size == 16 }?.unsigned(8, 8) ?: return false

            for (run in parts) {
                if (run.type != "trun") {
                    continue
                }

                // Its version and flags, how many samples, and their data's offset when its first flag says it's there, a signed 32 bits.
                val head = bytes.read(run.body, minOf(run.end - run.body, 12L).toInt())

                if (head.size < 8 || head.unsigned(4, 4) == 0L) {
                    continue
                }

                val offset = if ((head.byteAt(3) and 1) == 0) 0L else head.takeIf { it.size == 12 }?.unsigned(8, 4)?.toInt()?.toLong() ?: return false

                if (base < 0 || base + offset < 0 || base + offset >= end) {
                    return false
                }
            }
        }

        return true
    }

    /** The first box of `type` in `parent`, or null when it has none, or they can't be read. */
    private fun child(bytes: Bytes, parent: IsoBox, type: String): IsoBox? = children(bytes, parent)?.firstOrNull { it.type == type }

    /** How much of a chunk table is read at a time: a whole number of entries of either size. */
    private const val CHUNKS_READ = 64L * 1024

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

        // Each piece once, into one buffer: however many there are, nothing is copied again.
        val all = ByteArrayOutputStream(ranges.sumOf { it.second }.toInt())

        for ((at, length) in ranges) {
            all.write(bytes.read(at, length.toInt()))
        }

        return all.toByteArray()
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

    /**
     * Where a HEIF or AVIF picture's items are named, from its `meta` box's
     * parts, or null when its item information can't be read. A name can
     * say anything, as where the picture was taken; readers go by an item's
     * type alone.
     */
    fun itemNames(bytes: Bytes, parts: List<IsoBox>): List<ItemName>? {
        val info = parts.firstOrNull { it.type == "iinf" } ?: return emptyList()
        return itemEntries(bytes, info)?.mapNotNull { it.name }
    }

    /** Each item's type, and a `mime` item's content type, by id: those that have a type, as entries of version 2 and later do. */
    private fun itemTypes(bytes: Bytes, info: IsoBox): Map<Long, Pair<String, String>>? =
        itemEntries(bytes, info)?.filter { it.type != null }?.associate { it.id to (it.type!! to it.contentType) }

    /**
     * An item's entry in a picture's item information: its id, its type
     * (null in versions 0 and 1, which have none), a `mime` item's content
     * type, and where it's named, when it is.
     */
    private class ItemEntry(val id: Long, val type: String?, val contentType: String, val name: ItemName?)

    /** The entries of a picture's item information, or null when they can't be read. */
    private fun itemEntries(bytes: Bytes, info: IsoBox): List<ItemEntry>? {
        val head = bytes.read(info.body, 8)

        if (head.isEmpty()) {
            return null
        }

        val version = head.byteAt(0)
        val first = if (version == 0) 6 else 8
        val entries = mutableListOf<ItemEntry>()

        for (entry in children(bytes, info, skip = first) ?: return null) {
            if (entry.type != "infe") {
                continue
            }

            // Whole, however long the item's name is, so a content type after it is read where it is.
            val content = bytes.read(entry.body, minOf(entry.end - entry.body, ITEM_INFO_MOST).toInt())

            if (content.size < 4) {
                continue
            }

            // Its version and flags, its id and protection, and from version 2 on its type: then its name.
            val entryVersion = content.byteAt(0)
            val idSize = if (entryVersion < 3) 2 else 4
            val typeAt = 4 + idSize + 2
            val nameAt = if (entryVersion < 2) typeAt else typeAt + 4

            if (content.size < nameAt) {
                continue
            }

            val type = if (entryVersion < 2) null else String(content, typeAt, 4, Charsets.ISO_8859_1)
            // Counted in bytes, as a name of letters of more than a byte is written, to the zero that ends it.
            val nameEnd = (nameAt until content.size).firstOrNull { content[it] == 0.toByte() } ?: content.size
            val contentType = if (type == "mime") content.textAt(nameEnd + 1) else ""
            val after = if (nameEnd < content.size) content.copyOfRange(nameEnd + 1, content.size) else ByteArray(0)
            val name = ItemName(entry.body + nameAt, entry.end, after).takeIf { nameEnd > nameAt }
            entries += ItemEntry(content.unsigned(4, idSize), type, contentType, name)
        }

        return entries
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
            // Places can take no bytes to say (an offset and a length of no size): as many as a picture has, and no more.
            var places = 0L

            if (count > ITEM_PLACES_MOST) {
                return null
            }

            repeat(count.toInt()) {
                val id = next(if (version < 2) 2 else 4)
                val method = if (version == 1 || version == 2) (next(2) and 0x0F).toInt() else 0
                next(2)
                val base = next(baseSize)
                val extents = next(2)
                places += extents

                if (places > ITEM_PLACES_MOST) {
                    throw IOException("More item places than a picture has")
                }

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

/** The most an item's information, its name and content type, is read of: kilobytes are plenty. */
private const val ITEM_INFO_MOST = 64L * 1024

/**
 * The most places a HEIF or AVIF picture's items may be in, together: a
 * phone's picture of 48 tiles has about 50. More can't be read through,
 * since each would be kept in memory while what goes is worked out.
 */
private const val ITEM_PLACES_MOST = 1L shl 16
