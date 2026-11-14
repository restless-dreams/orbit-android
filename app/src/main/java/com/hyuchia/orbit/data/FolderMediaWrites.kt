package com.hyuchia.orbit.data

import com.hyuchia.orbit.Logs
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException

/**
 * The pictures, videos, sounds and files this device began putting into a
 * folder's `media/` and hasn't seen go there whole yet, kept in `file` so
 * they outlast Orbit stopping midway, as [SyncRecord.writing] does for a
 * map's text: a copy in the folder that holds only the start of the map's
 * is this device's own write cut short only while it's among them, and is
 * then written again. Any other copy there that's shorter is another file,
 * which a map's own never goes over ([FolderSync]). A write that stopped
 * with Orbit still running vouches only for the very file it left there
 * ([cut]), and for none once nothing's left of it. They stay on this
 * device: lost, a copy cut short only counts as another file, and the map's
 * goes under a name of its own.
 */
internal class FolderMediaWrites(private val file: File) {
    /**
     * A write begun: `name` in the media of the folder at `folder`, from a
     * file of `size` bytes here; and once it stopped partway with Orbit
     * still running, the file it left there, as a listing describes it
     * (`left`). Earlier versions read past `left`.
     */
    @Serializable
    data class Write(val folder: String, val name: String, val size: Long, val left: Left? = null)

    /** What a write that stopped partway left in the folder: its time (0 where the folder can't tell) and its size. */
    @Serializable
    data class Left(val modified: Long, val size: Long)

    private var writes: List<Write>? = null

    /**
     * Notes that this device is about to write `name`, `size` bytes, into
     * the media of the folder at `folder`, and returns how it was noted
     * before, for [undo].
     */
    @Synchronized
    fun begin(folder: String, name: String, size: Long): Write? {
        val before = all().firstOrNull { it.folder == folder && it.name == name }
        change(all().filterNot { it.folder == folder && it.name == name } + Write(folder, name, size))
        return before
    }

    /** A write [begin] noted didn't start, as for want of room: `name` is noted as it was `before` it. */
    @Synchronized
    fun undo(folder: String, name: String, before: Write?) {
        change(all().filterNot { it.folder == folder && it.name == name } + listOfNotNull(before))
    }

    /** A write of `name` stopped partway, leaving `there` in the folder at `folder`: only that very file is its write cut short. */
    @Synchronized
    fun cut(folder: String, name: String, there: FolderFile) {
        change(all().map { if (it.folder == folder && it.name == name) it.copy(left = Left(there.modified, there.size)) else it })
    }

    /** `name` is whole in the media of the folder at `folder`, or this device isn't writing it anymore. */
    @Synchronized
    fun end(folder: String, name: String) {
        val left = all().filterNot { it.folder == folder && it.name == name }

        if (left.size != all().size) {
            change(left)
        }
    }

    /**
     * Whether this device began writing `name`, from a file of `size` bytes,
     * into the folder at `folder` and hasn't seen it there whole, and the
     * folder's file by that name, listed as `there`, is the one that write
     * left: as it left it when it stopped with Orbit running, or any when
     * Orbit stopped with it.
     */
    @Synchronized
    fun writing(folder: String, name: String, size: Long, there: FolderFile): Boolean = all().any { write ->
        write.folder == folder && write.name == name && write.size == size && write.left.let { it == null || it == Left(there.modified, there.size) }
    }

    private fun all(): List<Write> = writes ?: read().also { writes = it }

    /** The writes as the file keeps them; none when it can't be read, which only makes a copy cut short count as another file. */
    private fun read(): List<Write> {
        if (!file.isFile) {
            return emptyList()
        }

        return runCatching { mapsJson.decodeFromString(ListSerializer(Write.serializer()), file.readText()) }.getOrDefault(emptyList())
    }

    private fun change(changed: List<Write>) {
        writes = changed

        try {
            if (changed.isEmpty()) {
                file.delete()
            } else {
                writeAtomically(file, mapsJson.encodeToString(ListSerializer(Write.serializer()), changed))
            }
        } catch (failed: IOException) {
            // Not kept, a write cut short only counts as another file later: the map's goes under a name of its own.
            Logs.warn("OrbitFolder", "The pictures and videos being written to the folder couldn't be noted", failed)
        }
    }
}
