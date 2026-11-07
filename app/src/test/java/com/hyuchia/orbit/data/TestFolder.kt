package com.hyuchia.orbit.data

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * A folder kept in memory, shared by the devices of a test as a sync app
 * shares one: [change] is another app writing a file, [arriving] one still
 * writing it, and [cutNextWrite] makes Orbit's next write stop partway, as a
 * write cut short does. Files are read as [DocumentFolder] reads them, and
 * the other hooks change the folder at the moments a race would.
 */
internal class TestFolder : SyncFolder {
    override val name = "Maps"
    val texts = mutableMapOf<String, String>()
    private val times = mutableMapOf<String, Long>()
    private var time = 1L

    /** Files that are only bytes so far, as one still being written is: they read as [DocumentFolder] reads them. */
    private val bytes = mutableMapOf<String, ByteArray>()

    /** Sizes the listing says, for files bigger than a test should hold. */
    val listedSizes = mutableMapOf<String, Long>()

    var cutNextWrite = false

    /** Runs after the folder is listed, as another app writing then would. */
    var afterListing: (() -> Unit)? = null

    /** Runs after each of Orbit's writes, with the file's name, as another app writing right then would. */
    var afterWrite: ((String) -> Unit)? = null

    /** What listing the folder throws, as a provider failing would. */
    var failListing: Throwable? = null

    /** What writing a file of these names throws, as a provider failing would. */
    val failWrites = mutableMapOf<String, Throwable>()

    /** What reading a file of these names throws, as a provider failing would. */
    val failReads = mutableMapOf<String, Throwable>()

    /** The pictures, videos and sounds in `media/`, by name. */
    val mediaFiles = mutableMapOf<String, ByteArray>()

    /** The names of the pictures, videos and sounds as `media/` was last listed: one written by another name is made anew. */
    private var mediaListed = emptySet<String>()

    /** The name the folder's app gives a picture, video or sound it makes, by the name asked for: as an app that renames them would. */
    var mediaNamed: ((String) -> String)? = null

    /** Sizes the media listing says, for files still arriving: what's there of them is shorter; -1 for one the folder can't tell the size of. */
    val mediaListedSizes = mutableMapOf<String, Long>()

    /** When each of the pictures, videos and sounds was last written, as the media listing says. */
    val mediaTimes = mutableMapOf<String, Long>()

    /** The pictures, videos and sounds read from `media/`, by name, in the order they were. */
    val mediaReads = mutableListOf<String>()

    /** The room the storage the folder is on has free: a new picture, video or sound past it, and some to spare, is refused as [DocumentFolder] refuses it. */
    var room = Long.MAX_VALUE

    override fun files(): List<FolderFile> {
        failListing?.let { throw it }
        val listing = texts.keys.map { describeNow(it) }
        afterListing?.invoke()
        return listing
    }

    override fun read(name: String): String {
        failReads[name]?.let { throw it }
        val partial = bytes[name]

        if (partial != null) {
            if (endsMidCharacter(partial)) {
                throw StillArriving(name, decodeText(partial))
            }

            return decodeText(partial)
        }

        return texts[name] ?: throw IOException("No $name")
    }

    override fun describe(name: String): FolderFile? = if (name in texts) describeNow(name) else null

    override fun write(name: String, text: String, create: Boolean): FolderFile {
        failWrites[name]?.let { throw it }
        // A file made by a name that's taken gets another, as a folder's app numbers it.
        val made = if (create) free(name, texts.keys) else name

        if (cutNextWrite) {
            cutNextWrite = false
            change(made, text.take(text.length / 2))
            throw IOException("Cut short")
        }

        change(made, text)
        val written = describeNow(made)
        afterWrite?.invoke(made)
        return written
    }

    /** `name`, or numbered as a folder's app numbers a copy (`Trip (1).md`) when `taken` has it. */
    private fun free(name: String, taken: Set<String>): String {
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(0) { it + 1 }.map { if (it == 0) name else "$stem ($it)$extension" }.first { it !in taken }
    }

    override fun delete(name: String) {
        texts -= name
        times -= name
        bytes -= name
    }

    /** The devices' records in `.orbit/devices/`, by name. */
    val devices = mutableMapOf<String, String>()

    override fun deviceRecords() = devices.map { (name, text) -> FolderFile(name, 1, text.length.toLong()) }

    override fun readDeviceRecord(name: String) = devices[name] ?: throw FileNotFoundException(name)

    override fun writeDeviceRecord(name: String, text: String) {
        devices[name] = text
    }

    override fun media(): Map<String, FolderFile> {
        mediaListed = mediaFiles.keys.toSet()
        return mediaFiles.mapValues { (name, bytes) -> FolderFile(name, mediaTimes[name] ?: 0, mediaListedSizes[name] ?: bytes.size.toLong()) }
    }

    override fun readMedia(name: String, into: OutputStream): Boolean {
        val bytes = mediaFiles[name] ?: return false
        mediaReads += name
        into.write(bytes)
        val listed = mediaListedSizes[name] ?: bytes.size.toLong()

        // As DocumentFolder reads it: one that isn't the size it was listed at (an empty one too) is still arriving.
        if (listed >= 0 && listed != bytes.size.toLong()) {
            throw IOException("$name is still arriving")
        }

        return true
    }

    /**
     * As DocumentFolder writes one: over the one by its name when `media/`
     * was listed with it, or else made anew, which a file by that name since
     * makes the folder's app number (`photo (1).jpg`), as may [mediaNamed].
     */
    override fun writeMedia(name: String, from: File): FolderFile {
        if (name !in mediaListed && from.length() + ROOM_LEFT_FREE > room) {
            throw NoRoomForMedia(name)
        }

        val bytes = from.readBytes()
        val made = if (name in mediaListed) name else free(mediaNamed?.invoke(name) ?: name, mediaFiles.keys)
        mediaListed = mediaListed + made
        mediaFiles[made] = bytes
        mediaListedSizes -= made
        time += 1_000
        mediaTimes[made] = time
        return FolderFile(made, time, bytes.size.toLong())
    }

    override fun deleteMedia(name: String) {
        mediaFiles -= name
        mediaListedSizes -= name
        mediaTimes -= name
    }

    fun change(name: String, text: String) {
        texts[name] = text
        bytes -= name
        time += 1_000
        times[name] = time
    }

    /** Another app writing `name`, of which only `written` has arrived so far. */
    fun arriving(name: String, written: ByteArray) {
        change(name, decodeText(written))
        bytes[name] = written
    }

    private fun describeNow(name: String) = FolderFile(name, times.getValue(name), listedSizes[name] ?: (bytes[name]?.size ?: texts.getValue(name).length).toLong())
}
