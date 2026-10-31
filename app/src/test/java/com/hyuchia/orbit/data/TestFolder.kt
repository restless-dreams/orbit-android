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

    /** The pictures, videos and sounds in `media/`, by name. */
    val mediaFiles = mutableMapOf<String, ByteArray>()

    /** Sizes the media listing says, for files still arriving: what's there of them is shorter; -1 for one the folder can't tell the size of. */
    val mediaListedSizes = mutableMapOf<String, Long>()

    /** When each of the pictures, videos and sounds was last written, as the media listing says. */
    val mediaTimes = mutableMapOf<String, Long>()

    /** The pictures, videos and sounds read from `media/`, by name, in the order they were. */
    val mediaReads = mutableListOf<String>()

    override fun files(): List<FolderFile> {
        failListing?.let { throw it }
        val listing = texts.keys.map { describeNow(it) }
        afterListing?.invoke()
        return listing
    }

    override fun read(name: String): String {
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

        if (cutNextWrite) {
            cutNextWrite = false
            change(name, text.take(text.length / 2))
            throw IOException("Cut short")
        }

        change(name, text)
        val written = describeNow(name)
        afterWrite?.invoke(name)
        return written
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

    override fun media() = mediaFiles.mapValues { (name, bytes) -> FolderFile(name, mediaTimes[name] ?: 0, mediaListedSizes[name] ?: bytes.size.toLong()) }

    override fun readMedia(name: String, into: OutputStream): Boolean {
        val bytes = mediaFiles[name] ?: return false
        mediaReads += name
        into.write(bytes)
        val listed = mediaListedSizes[name] ?: bytes.size.toLong()

        // As DocumentFolder reads it: one that isn't the size it was listed at is still arriving.
        if (listed > 0 && listed != bytes.size.toLong()) {
            throw IOException("$name is still arriving")
        }

        return true
    }

    override fun writeMedia(name: String, from: File): FolderFile {
        val bytes = from.readBytes()
        mediaFiles[name] = bytes
        mediaListedSizes -= name
        time += 1_000
        mediaTimes[name] = time
        return FolderFile(name, time, bytes.size.toLong())
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
