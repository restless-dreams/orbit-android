package com.hyuchia.orbit.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A movie's uncompressed sound, as 16-bit samples, little-endian, its channels one after the other. */
internal class Pcm(val samples: ByteArray, val rate: Int, val channels: Int)

/**
 * What Android's own reader doesn't do with an iPhone's QuickTime movie:
 * open one that starts without an `ftyp`, as older iPhones write theirs,
 * and read its uncompressed sound, which QuickTime describes its own ways
 * (see [MotionVideos]).
 */
internal object QuickTimeMovies {
    /**
     * Copies a movie that starts without an `ftyp` to `output` with one in
     * front, the places its chunks start moved on by as much. False, and
     * nothing written, for a movie that has one, or isn't laid out so.
     */
    fun withFileType(input: File, output: File): Boolean {
        val tables = try {
            RandomAccessFile(input, "r").use { file ->
                val bytes = FileBytes(file)
                val boxes = IsoBoxes.top(bytes).first

                if (boxes.firstOrNull()?.type == "ftyp" || boxes.none { it.type == "mdat" }) {
                    return false
                }

                val movie = boxes.firstOrNull { it.type == "moov" } ?: return false
                chunkTables(bytes, movie) ?: return false
            }
        } catch (unreadable: IOException) {
            return false
        }

        val moved = tables.map { table -> table to table.entries.map { it + QUICKTIME_FILE_TYPE.size } }

        if (moved.any { (table, entries) -> !table.wide && entries.any { it > MOST_NARROW_OFFSET } }) {
            return false
        }

        output.outputStream().buffered().use { sink ->
            sink.write(QUICKTIME_FILE_TYPE)
            input.inputStream().use { it.copyTo(sink) }
        }

        RandomAccessFile(output, "rw").use { file ->
            for ((table, entries) in moved) {
                val buffer = ByteBuffer.allocate(entries.size * if (table.wide) 8 else 4)
                entries.forEach { if (table.wide) buffer.putLong(it) else buffer.putInt(it.toInt()) }
                file.seek(table.start + QUICKTIME_FILE_TYPE.size)
                file.write(buffer.array())
            }
        }

        return true
    }

    /**
     * A movie's uncompressed sound (`lpcm`, `sowt` or `twos`, described in
     * any of QuickTime's three versions) as 16-bit samples, or null for a
     * movie whose sound is compressed, as AAC, or that has none.
     */
    fun sound(file: File): Pcm? = try {
        RandomAccessFile(file, "r").use { sound(FileBytes(it)) }
    } catch (unreadable: IOException) {
        null
    }

    fun sound(bytes: Bytes): Pcm? {
        val movie = IsoBoxes.top(bytes).first.firstOrNull { it.type == "moov" } ?: return null
        val tracks = IsoBoxes.children(bytes, movie)?.filter { it.type == "trak" } ?: return null

        val table = tracks.firstNotNullOfOrNull { track ->
            val media = child(bytes, track, "mdia") ?: return@firstNotNullOfOrNull null
            // The handler's type follows its version, flags and QuickTime's component type.
            val handler = child(bytes, media, "hdlr")?.let { bytes.read(it.body + 8, 4).toString(Charsets.ISO_8859_1) }
            child(bytes, media, "minf")?.let { child(bytes, it, "stbl") }?.takeIf { handler == "soun" }
        } ?: return null

        val format = sampleFormat(bytes, table) ?: return null
        val offsets = chunkTable(bytes, table)?.entries ?: return null
        val runs = chunkRuns(bytes, table) ?: return null
        // The track's samples, each as many frames as the format says.
        val count = bytes.read((child(bytes, table, "stsz") ?: return null).body + 8, 4).takeIf { it.size == 4 }?.unsigned(0, 4) ?: return null
        val frameBytes = format.channels * format.bits / 8
        var frames = count * format.framesPerSample
        val sound = ByteArrayOutputStream()

        for ((index, offset) in offsets.withIndex()) {
            // Each run of chunks holds as many samples, from its first chunk (counted from 1) to the next run's.
            val inChunk = minOf(runs.last { it.first <= index + 1 }.second * format.framesPerSample, frames)

            if (inChunk <= 0) {
                break
            }

            if (sound.size() + inChunk * format.channels * 2 > MOST_SOUND) {
                return null
            }

            val length = (inChunk * frameBytes).toInt()
            val chunk = bytes.read(offset, length)
            sound.write(sixteenBits(chunk, format))
            frames -= chunk.size / frameBytes

            if (chunk.size < length) {
                break
            }
        }

        return Pcm(sound.toByteArray(), format.rate, format.channels).takeIf { it.samples.isNotEmpty() }
    }

    /** How a track's samples are written: their rate and channels, their bits, whether they're floats or big-endian, and frames to a sample. */
    private class SampleFormat(val rate: Int, val channels: Int, val bits: Int, val float: Boolean, val bigEndian: Boolean, val framesPerSample: Long)

    /**
     * The sound's format, from its sample description: QuickTime's first two
     * versions give the channels, bits and rate in 16 bits each (the second
     * adds what a packet holds); the third gives them in fields of their own,
     * after the first version's, with flags for floats and byte order.
     */
    private fun sampleFormat(bytes: Bytes, table: IsoBox): SampleFormat? {
        val descriptions = child(bytes, table, "stsd") ?: return null
        // The first description follows the box's version, flags and count.
        val entry = IsoBoxes.boxAt(bytes, descriptions.body + 8, descriptions.end) ?: return null
        val fields = bytes.read(entry.body, minOf(entry.end - entry.body, DESCRIPTION_BYTES.toLong()).toInt())

        if (fields.size < FIRST_VERSION_BYTES || entry.type !in UNCOMPRESSED) {
            return null
        }

        val format = when (fields.unsigned(8, 2).toInt()) {
            2 -> {
                if (fields.size < DESCRIPTION_BYTES) {
                    return null
                }

                val flags = fields.unsigned(52, 4).toInt()
                val float = entry.type == "lpcm" && flags and FLOAT_FLAG != 0
                val bigEndian = if (entry.type == "lpcm") flags and BIG_ENDIAN_FLAG != 0 else entry.type == "twos"
                val rate = Double.fromBits(fields.unsigned(32, 8))
                SampleFormat(rate.toInt(), fields.unsigned(40, 4).toInt(), fields.unsigned(48, 4).toInt(), float, bigEndian, fields.unsigned(60, 4).coerceAtLeast(1))
            }
            else -> SampleFormat((fields.unsigned(24, 4) shr 16).toInt(), fields.unsigned(16, 2).toInt(), fields.unsigned(18, 2).toInt(), false, entry.type == "twos", 1)
        }

        val known = if (format.float) format.bits == 32 || format.bits == 64 else format.bits in setOf(8, 16, 24, 32)
        return format.takeIf { known && it.channels in 1..MOST_CHANNELS && it.rate > 0 }
    }

    /** Samples as 16-bit ones, little-endian: integers keep their highest 16 bits, floats are scaled to them. */
    private fun sixteenBits(chunk: ByteArray, format: SampleFormat): ByteArray {
        val size = format.bits / 8
        val count = chunk.size / size
        val input = ByteBuffer.wrap(chunk).order(if (format.bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val output = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)

        repeat(count) { index ->
            val at = index * size

            val sample = when {
                format.float && size == 4 -> (input.getFloat(at).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                format.float -> (input.getDouble(at).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
                size == 1 -> chunk[at].toInt() shl 8
                size == 2 -> input.getShort(at).toInt()
                // The highest two bytes: the last two of little-endian ones, the first two of big-endian ones.
                format.bigEndian -> input.getShort(at).toInt()
                else -> input.getShort(at + size - 2).toInt()
            }

            output.putShort(sample.toShort())
        }

        return output.array()
    }

    /** Where a track's chunks start, from its `stco` or, past 4 GB, its `co64`. */
    private class ChunkTable(val start: Long, val wide: Boolean, val entries: List<Long>)

    private fun chunkTables(bytes: Bytes, movie: IsoBox): List<ChunkTable>? {
        val tracks = IsoBoxes.children(bytes, movie)?.filter { it.type == "trak" } ?: return null

        return tracks.map { track ->
            val table = child(bytes, track, "mdia")?.let { child(bytes, it, "minf") }?.let { child(bytes, it, "stbl") } ?: return null
            chunkTable(bytes, table) ?: return null
        }
    }

    private fun chunkTable(bytes: Bytes, table: IsoBox): ChunkTable? {
        val offsets = IsoBoxes.children(bytes, table)?.firstOrNull { it.type == "stco" || it.type == "co64" } ?: return null
        val wide = offsets.type == "co64"
        val size = if (wide) 8 else 4
        val count = bytes.read(offsets.body + 4, 4).takeIf { it.size == 4 }?.unsigned(0, 4) ?: return null

        if (offsets.body + 8 + count * size > offsets.end) {
            return null
        }

        val entries = bytes.read(offsets.body + 8, (count * size).toInt())
        return ChunkTable(offsets.body + 8, wide, (0 until count.toInt()).map { entries.unsigned(it * size, size) })
    }

    /** A track's runs of chunks that hold as many samples each: from which chunk, counted from 1, and how many samples. */
    private fun chunkRuns(bytes: Bytes, table: IsoBox): List<Pair<Long, Long>>? {
        val box = child(bytes, table, "stsc") ?: return null
        val count = bytes.read(box.body + 4, 4).takeIf { it.size == 4 }?.unsigned(0, 4) ?: return null

        if (count == 0L || box.body + 8 + count * 12 > box.end) {
            return null
        }

        val entries = bytes.read(box.body + 8, (count * 12).toInt())
        return (0 until count.toInt()).map { entries.unsigned(it * 12, 4) to entries.unsigned(it * 12 + 4, 4) }.takeIf { it.first().first == 1L }
    }

    private fun child(bytes: Bytes, parent: IsoBox, type: String): IsoBox? = IsoBoxes.children(bytes, parent)?.firstOrNull { it.type == type }

    /** The `ftyp` a QuickTime movie starts with: QuickTime's brand, version 0, and QuickTime as what it's compatible with. */
    private val QUICKTIME_FILE_TYPE = byteArrayOf(0, 0, 0, 20) + "ftypqt  ".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 0) + "qt  ".toByteArray(Charsets.ISO_8859_1)

    private const val MOST_NARROW_OFFSET = 0xFFFFFFFFL

    /** The kinds of uncompressed sound QuickTime describes. */
    private val UNCOMPRESSED = setOf("lpcm", "sowt", "twos")

    /** A sound description's fields: QuickTime's first version's, and the third's. */
    private const val FIRST_VERSION_BYTES = 28

    private const val DESCRIPTION_BYTES = 64

    private const val FLOAT_FLAG = 1
    private const val BIG_ENDIAN_FLAG = 2
    private const val MOST_CHANNELS = 8

    /** The most sound read, as 16-bit samples: a live photo's few seconds are far less. */
    private const val MOST_SOUND = 32L * 1024 * 1024
}
