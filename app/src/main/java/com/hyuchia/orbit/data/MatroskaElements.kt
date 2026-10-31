package com.hyuchia.orbit.data

import java.io.IOException
import java.util.zip.CRC32

/**
 * WebM videos and sounds, and Matroska's, element by element. What a file
 * says beyond what it shows and plays becomes a Void element of the same
 * size with nothing in it, so every frame stays where it is: its tags,
 * chapters and attachments (a cover among them), its title, when it was
 * made, the names its files had, its tracks' names, and its index's entries
 * for what went. The apps that made and wrote it, which a file must name,
 * are named with nothing. A Void element among the segment's parts, its
 * details and tracks is emptied, and a part with a checksum gets its
 * checksum again. A track's
 * codec setup goes as its codec's own header would: Vorbis's and Theora's
 * comments emptied ([OggPages]), FLAC's blocks padding ([FlacBlocks]).
 * Segments and clusters of no size, as MediaRecorder writes them while it
 * records, are read to where they end; tags after the last element stay
 * out ([SoundTags]). A file cut short in its last cluster, as a recording
 * that stopped or a copy that didn't finish, goes as far as its last whole
 * block: the block the cut falls in stays out, as no player can play it,
 * and the segment and the cluster, and the index's entries for what was
 * cut away, say where it ends now.
 */
internal object MatroskaElements {
    /** Whether a file's first bytes start with EBML's header, as WebM and Matroska do. */
    fun looksLikeOne(start: ByteArray): Boolean = start.size >= 4 && start.unsigned(0, 4) == EBML

    /**
     * What to write over the file, and where it ends, or null when it can't
     * be read through: an element longer than what it's in, or cut short
     * anywhere but in its last cluster, one of no size that can't have
     * none, or anything but EBML, segments, zeros and tags in it.
     */
    fun plan(bytes: Bytes): Pair<List<Patch>, Long>? {
        val reading = ReadAhead(bytes)
        val end = SoundTags.trailingStart(reading, reading.size)
        val patches = Patches()
        var position = 0L
        var segments = 0

        while (position < end) {
            if (reading.read(position, 1).single() == 0.toByte()) {
                position = SoundTags.zerosUntil(reading, position, end)
                continue
            }

            // A segment that says it goes on past the end of the file was cut short in it: it's read as far as the file goes.
            val element = headerAt(reading, position)?.takeIf { it.end == null || it.end <= end || it.id == SEGMENT } ?: return null

            if (element.id == SEGMENT) {
                val ending = segment(reading, element, end, patches) ?: return null
                segments += 1

                // Nothing is read after the block or cluster the file was cut short in: the file ends there.
                if (ending.cutShort) {
                    return patches to ending.at
                }

                position = ending.at
                continue
            }

            position = when (element.id) {
                EBML -> element.end
                VOID -> element.end?.also { patches += emptied(element) }
                else -> null
            } ?: return null
        }

        return (patches to end).takeIf { segments > 0 }
    }

    /**
     * An element: its ID, where it starts, where its size and what's in it
     * start, and where it ends, or null for one of no size, which runs until
     * something that can't be part of it begins.
     */
    private class Element(val id: Long, val start: Long, val sizeAt: Long, val body: Long, val end: Long?)

    /**
     * Where a segment or a cluster ends as it's read, at `at`: when the file
     * was `cutShort` in it, where the block or cluster the cut falls in
     * starts, after which nothing is read.
     */
    private class Ending(val at: Long, val cutShort: Boolean = false)

    /** The element at `at`, ending by `limit`, or null when no whole one starts there. */
    private fun elementAt(bytes: Bytes, at: Long, limit: Long): Element? = headerAt(bytes, at)?.takeIf { it.end == null || it.end <= limit }

    /** The element whose ID and size start at `at`, wherever it says it ends, or null when no element starts there whole as far as them. */
    private fun headerAt(bytes: Bytes, at: Long): Element? {
        val head = bytes.read(at, 12)
        val idLength = lengthOf(head, 0)?.takeIf { it <= 4 } ?: return null
        val sizeLength = lengthOf(head, idLength) ?: return null

        if (head.size < idLength + sizeLength) {
            return null
        }

        val said = head.unsigned(idLength, sizeLength) and ((1L shl (7 * sizeLength)) - 1)
        val body = at + idLength + sizeLength
        val end = if (said == (1L shl (7 * sizeLength)) - 1) null else body + said

        return Element(head.unsigned(0, idLength), at, at + idLength, body, end)
    }

    /**
     * Whether the file was cut short in the element at `at`, as one of `id`
     * when it's given: it says it goes on past `limit`, or the file ends in
     * its ID or its size, as far as which they start as they should.
     */
    private fun cutShortAt(bytes: Bytes, at: Long, limit: Long, id: Long? = null): Boolean {
        val element = headerAt(bytes, at)

        if (element != null) {
            return element.end != null && element.end > limit && (id == null || element.id == id)
        }

        val head = bytes.read(at, 12)
        val idLength = lengthOf(head, 0)?.takeIf { it <= 4 } ?: return false

        // A size that starts with a zero byte isn't cut short, but no size at all.
        if (head.size > idLength && lengthOf(head, idLength) == null) {
            return false
        }

        return at + head.size == bytes.size && (id == null || (0 until minOf(idLength, head.size)).all { head.byteAt(it).toLong() == id shr (8 * (idLength - 1 - it)) and 0xFF })
    }

    /** How many bytes the number at `at` takes, as its first byte's leading zeros say: from one to eight, or null. */
    private fun lengthOf(bytes: ByteArray, at: Int): Int? {
        if (at >= bytes.size || bytes[at] == 0.toByte()) {
            return null
        }

        return Integer.numberOfLeadingZeros(bytes.byteAt(at)) - 23
    }

    /** The ID of the element at `at`, or null when none starts there. */
    private fun idAt(bytes: Bytes, at: Long): Long? {
        val head = bytes.read(at, 4)
        val length = lengthOf(head, 0)?.takeIf { it <= 4 && it <= head.size } ?: return null
        return head.unsigned(0, length)
    }

    /** The elements in `parent`, which has a size, and which they have to fill. */
    private fun partsOf(bytes: Bytes, parent: Element): List<Element> {
        val end = parent.end ?: throw IOException("A part of no size that has to have one")
        val parts = mutableListOf<Element>()
        var position = parent.body

        while (position < end) {
            val part = elementAt(bytes, position, end)?.takeIf { it.end != null } ?: throw IOException("A part that isn't whole")

            if (parts.size == MOST_PARTS) {
                throw IOException("More parts than any element has")
            }

            parts += part
            position = part.end!!
        }

        return parts
    }

    /**
     * Where a segment ends, its patches added to `patches`, or null when it
     * can't be read through. One of no size ends where the next segment or
     * file begins, or at `limit`. One the file was cut short in, whose size
     * says it goes on past `limit` or whose last cluster does, ends before
     * the block or cluster the cut falls in, and says so: its size, and its
     * index, whose entries for what was cut away become Void too. Null when
     * no cluster of it is left.
     */
    private fun segment(bytes: Bytes, segment: Element, limit: Long, patches: MutableList<Patch>): Ending? {
        val end = minOf(segment.end ?: limit, limit)
        val gone = mutableSetOf<Long>()
        val indexes = mutableListOf<Element>()
        var position = segment.body
        var clusters = 0
        var cutShort = false

        while (position < end) {
            if (segment.end == null && idAt(bytes, position).let { it == EBML || it == SEGMENT }) {
                break
            }

            // A cluster that says it goes on past where the file ends was cut short in it, as one the file ends in the ID or size of was.
            val part = elementAt(bytes, position, end)?.takeIf { it.end != null || it.id == CLUSTER } ?: headerAt(bytes, position)?.takeIf { it.id == CLUSTER && cutShortAt(bytes, position, end) }

            if (part == null) {
                if (!cutShortAt(bytes, position, end, CLUSTER)) {
                    return null
                }

                cutShort = true
                break
            }

            when (part.id) {
                CLUSTER, CUES -> Unit
                SEEK_HEAD -> indexes += part
                INFO -> patches += info(bytes, part)
                TRACKS -> patches += tracks(bytes, part)
                VOID -> patches += emptied(part)
                // A checksum of the whole segment couldn't be right once a part of it changes.
                CRC32 -> patches += voided(part)
                // Its tags, chapters and attachments, and any part Orbit doesn't know, say what it can't tell.
                else -> {
                    patches += voided(part)
                    gone += part.id
                }
            }

            if (part.end != null && part.end <= end) {
                if (part.id == CLUSTER) {
                    clusters += 1
                }

                position = part.end
                continue
            }

            val cluster = clusterEnd(bytes, part, end, patches) ?: return null

            // One cut short before any of its blocks was whole is left out with them.
            if (cluster.at > part.start) {
                clusters += 1
            }

            position = cluster.at

            if (cluster.cutShort) {
                cutShort = true
                break
            }
        }

        val cutAway = cutShort || (segment.end != null && segment.end > position)

        if (cutAway && clusters == 0) {
            return null
        }

        for (index in indexes) {
            patches += seekHead(bytes, index, gone, kept = if (cutAway) position - segment.body else Long.MAX_VALUE)
        }

        if (segment.end != null && segment.end > position) {
            patches += sizeSaid(segment, position - segment.body)
        }

        return Ending(position, cutShort)
    }

    /**
     * Where a cluster of no size ends: where something begins that can't be
     * one of its parts. Its Void elements are emptied. One the file was cut
     * short in, whose size says it goes on past `limit`, or whose last part
     * does, ends before the part the cut falls in, or where it starts when
     * none of its blocks is whole, and says so: its size, where it has one,
     * and its checksum, which can't be right without its end, Void.
     */
    private fun clusterEnd(bytes: Bytes, cluster: Element, limit: Long, patches: MutableList<Patch>): Ending? {
        val checksums = mutableListOf<Element>()
        var position = cluster.body
        var blocks = 0
        var cutShort = false

        while (position < limit && idAt(bytes, position) in CLUSTER_PARTS) {
            val part = elementAt(bytes, position, limit)

            // The part the file ends in, cut short: no player can play it.
            if (part == null) {
                if (!cutShortAt(bytes, position, limit)) {
                    return null
                }

                cutShort = true
                break
            }

            when (part.id) {
                VOID -> patches += emptied(part)
                CRC32 -> checksums += part
                SIMPLE_BLOCK, BLOCK_GROUP -> blocks += 1
            }

            position = part.end ?: return null
        }

        if (!cutShort && (cluster.end == null || cluster.end <= limit)) {
            return Ending(position)
        }

        // One with a size is read through to where the file ends, as far as its last part.
        if (!cutShort && position < limit) {
            return null
        }

        if (blocks == 0) {
            return Ending(cluster.start, cutShort = true)
        }

        patches += checksums.flatMap(::voided)

        if (cluster.end != null) {
            patches += sizeSaid(cluster, position - cluster.body)
        }

        return Ending(position, cutShort = true)
    }

    /** An element's size said again as `size`, in as many bytes as it took. */
    private fun sizeSaid(element: Element, size: Long): Patch {
        val length = (element.body - element.sizeAt).toInt()
        return Patch(element.sizeAt, length.toLong(), number(size, length))
    }

    /** The segment's details, without its title, when it was made, the apps that made it and its files' names. */
    private fun info(bytes: Bytes, info: Element): List<Patch> = withParts(bytes, info) { part ->
        when (part.id) {
            in INFO_SAID -> voided(part)
            in INFO_NAMED -> namedWithNothing(part)
            VOID -> emptied(part)
            else -> emptyList()
        }
    }

    /** The tracks, without their names, each one's codec setup going as its codec's header would. */
    private fun tracks(bytes: Bytes, tracks: Element): List<Patch> = withParts(bytes, tracks) { entry ->
        when (entry.id) {
            TRACK_ENTRY -> {
                // A codec's ID is a few letters: more than that names no codec Orbit reads the setup of.
                val codec = partsOf(bytes, entry).firstOrNull { it.id == CODEC_ID }?.let { String(bytes.read(it.body, minOf(it.end!! - it.body, CODEC_ID_LONGEST).toInt()), Charsets.ISO_8859_1).trimEnd('\u0000') }

                withParts(bytes, entry) { part ->
                    when (part.id) {
                        NAME -> voided(part)
                        VOID -> emptied(part)
                        CODEC_PRIVATE -> codecSetup(bytes, codec, part)
                        else -> emptyList()
                    }
                }
            }
            VOID -> emptied(entry)
            else -> emptyList()
        }
    }

    /**
     * A track's codec setup as it goes: for Vorbis and Theora, their three
     * headers laced as Xiph laces them, the comment header emptied; for
     * FLAC, its metadata blocks after `fLaC`, those that say more than the
     * stream padding. Other codecs' setups say nothing but how to decode.
     */
    private fun codecSetup(bytes: Bytes, codec: String?, setup: Element): List<Patch> {
        val end = setup.end!!

        return when (codec) {
            "A_VORBIS", "V_THEORA" -> {
                val head = bytes.read(setup.body, minOf(end - setup.body, LACING_READ).toInt())

                if (head.isEmpty() || head.byteAt(0) != 2) {
                    throw IOException("A codec setup that isn't three headers")
                }

                // Each of the first two headers' length, as bytes of 255 and the one less that ends it.
                var at = 1
                val lengths = LongArray(2)

                for (index in 0 until 2) {
                    do {
                        val byte = head.getOrNull(at)?.toInt()?.and(0xFF) ?: throw IOException("A codec setup's lengths that aren't whole")
                        lengths[index] += byte
                        at += 1
                    } while (byte == 255)
                }

                val comments = setup.body + at + lengths[0]

                if (comments + lengths[1] > end) {
                    throw IOException("A codec setup's headers that aren't whole")
                }

                val start = OggPages.emptiedCommentsStart(bytes.read(comments, 8))?.takeIf { it.size <= lengths[1] } ?: throw IOException("A comment header that isn't one")
                listOf(Patch(comments, start.size.toLong(), start), Patch(comments + start.size, lengths[1] - start.size))
            }
            "A_FLAC" -> {
                val patches = Patches()
                var position = setup.body + 4

                if (!bytes.read(setup.body, 4).startsWith("fLaC")) {
                    throw IOException("A FLAC setup without fLaC")
                }

                while (position < end) {
                    val header = bytes.read(position, 4).takeIf { it.size == 4 } ?: throw IOException("A FLAC block that isn't whole")
                    val length = header.unsigned(1, 3)

                    if (position + 4 + length > end) {
                        throw IOException("A FLAC block that isn't whole")
                    }

                    FlacBlocks.goingAs(header.byteAt(0))?.let { now ->
                        patches += Patch(position, 1, byteArrayOf(now.toByte()))
                        patches += Patch(position + 4, length)
                    }

                    position += 4 + length
                }

                patches
            }
            else -> emptyList()
        }
    }

    /**
     * The index's entries for parts that are Void now, or that a file cut
     * short doesn't have, as they were at `kept` or past it from the
     * segment's start, made Void too: what's left points where it did.
     */
    private fun seekHead(bytes: Bytes, index: Element, gone: Set<Long>, kept: Long): List<Patch> = withParts(bytes, index) { entry ->
        when (entry.id) {
            SEEK -> {
                val parts = partsOf(bytes, entry)
                val (target, at) = listOf(SEEK_ID, SEEK_POSITION).map { id -> parts.firstOrNull { it.id == id }?.let { bytes.read(it.body, (it.end!! - it.body).toInt().coerceAtMost(8)) } }
                if (target == null || target.unsigned(0, target.size) in gone || (at != null && at.unsigned(0, at.size) >= kept)) voided(entry) else emptyList()
            }
            VOID -> emptied(entry)
            else -> emptyList()
        }
    }

    /**
     * The patches `patchesOf` gives a master element's parts, and its
     * checksum again when it has one and they change what it covers: all
     * that comes after it in the element, as it goes.
     */
    private fun withParts(bytes: Bytes, master: Element, patchesOf: (Element) -> List<Patch>): List<Patch> {
        val parts = partsOf(bytes, master)
        val patches = parts.flatMap(patchesOf)
        val checksum = parts.firstOrNull { it.id == CRC32 } ?: return patches

        if (patches.isEmpty()) {
            return patches
        }

        if (checksum.end!! - checksum.body != 4L) {
            throw IOException("A checksum that isn't four bytes")
        }

        val crc = CRC32()
        var position = checksum.end

        while (position < master.end!!) {
            val chunk = bytes.read(position, minOf(CHECKSUM_CHUNK, master.end - position).toInt())

            for (patch in patches) {
                val from = maxOf(patch.at, position)
                val until = minOf(patch.at + patch.length, position + chunk.size)

                for (at in from until until) {
                    chunk[(at - position).toInt()] = patch.with?.get((at - patch.at).toInt()) ?: 0
                }
            }

            crc.update(chunk)
            position += chunk.size
        }

        val value = crc.value
        return patches + Patch(checksum.body, 4, ByteArray(4) { (value shr (8 * it)).toByte() })
    }

    /** An element made a Void element of the same size, with nothing in it. */
    private fun voided(element: Element): List<Patch> {
        val total = element.end!! - element.start
        val sizeLength = (1..8).first { total - 1 - it < (1L shl (7 * it)) - 1 }
        val header = byteArrayOf(VOID.toByte()) + number(total - 1 - sizeLength, sizeLength)
        return listOf(Patch(element.start, header.size.toLong(), header), Patch(element.start + header.size, total - header.size))
    }

    /**
     * A text element a file must have, saying nothing: its ID, then a size
     * of zero as long as its own size was, then a Void element over the
     * text, or a size one byte longer over a text of one letter.
     */
    private fun namedWithNothing(element: Element): List<Patch> {
        val sizeLength = (element.body - element.sizeAt).toInt()
        val text = element.end!! - element.body

        return when {
            text == 0L -> emptyList()
            text == 1L && sizeLength < 8 -> listOf(Patch(element.sizeAt, sizeLength + 1L, number(0, sizeLength + 1)))
            text == 1L -> voided(element)
            else -> listOf(Patch(element.sizeAt, sizeLength.toLong(), number(0, sizeLength))) + voided(Element(VOID, element.body, element.body, element.body, element.end))
        }
    }

    /** A Void element's content as nothing: what an app left in it may say anything. */
    private fun emptied(void: Element): List<Patch> = listOfNotNull(void.end?.let { Patch(void.body, it - void.body) })

    /** `value` as an element's size, in `length` bytes. */
    private fun number(value: Long, length: Int): ByteArray = ByteArray(length) { (value shr (8 * (length - 1 - it))).toByte() }.also {
        it[0] = (it[0].toInt() or (0x80 shr (length - 1))).toByte()
    }

    private const val EBML = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val SEEK_HEAD = 0x114D9B74L
    private const val SEEK = 0x4DBBL
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POSITION = 0x53ACL
    private const val INFO = 0x1549A966L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val NAME = 0x536EL
    private const val CODEC_ID = 0x86L
    private const val CODEC_PRIVATE = 0x63A2L
    private const val CLUSTER = 0x1F43B675L
    private const val SIMPLE_BLOCK = 0xA3L
    private const val BLOCK_GROUP = 0xA0L
    private const val CUES = 0x1C53BB6BL
    private const val VOID = 0xECL
    private const val CRC32 = 0xBFL

    /** The segment's details that say what can't be told from it: its title, when it was made, and its files' names, and the files before and after it. */
    private val INFO_SAID = setOf(0x7BA9L, 0x4461L, 0x7384L, 0x3C83ABL, 0x3E83BBL)

    /** The apps that made it and wrote it, which a file has to name. */
    private val INFO_NAMED = setOf(0x4D80L, 0x5741L)

    /** What a cluster holds: its time, its blocks, and where it and the one before are. */
    private val CLUSTER_PARTS = setOf(0xE7L, 0x5854L, 0xA7L, 0xABL, 0xA3L, 0xA0L, 0xAFL, VOID, CRC32)

    /** The longest codec ID read: Matroska's are a few letters, such as `A_VORBIS`. */
    private const val CODEC_ID_LONGEST = 32L

    /** The most parts one element may hold: more were put there to wear the reader out. */
    private const val MOST_PARTS = 100_000

    /** As much of a codec setup as its headers' lengths take, at most. */
    private const val LACING_READ = 64L * 1024

    private const val CHECKSUM_CHUNK = 64L * 1024
}
