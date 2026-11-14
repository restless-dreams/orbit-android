package com.hyuchia.orbit.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException

/**
 * The layout this version keeps its files in. Each change to the layout is a
 * step in [LAYOUT_STEPS], and older files are moved to this layout before
 * anything else reads them, a copy of every map's text kept first.
 */
internal const val LAYOUT_VERSION = 2

/**
 * The oldest layout an app must know to change files in this one. Apps that
 * only know an older layout still read the maps, but leave them unchanged.
 * Version 2 only moved history out of the maps' folders, which an app on
 * version 1 handles without harm, so they may change it.
 */
internal const val LAYOUT_COMPATIBLE = 1

/**
 * The layout the files are in, as the app that laid them out wrote it in
 * `maps/.layout.json`. Its fields stay whole numbers, and fields are only
 * ever added: a record an app can't read leaves its maps only read.
 */
@Serializable
internal data class LayoutRecord(val version: Int, val compatible: Int = 1)

/** A change to the layout: what it takes the files to, and how. */
internal class LayoutStep(val to: Int, val apply: (StorageLayout) -> Unit)

internal val LAYOUT_STEPS = listOf(
    // Version 1: a folder for each map, its text in map.md, instead of one .md file per map.
    LayoutStep(1) { layout -> layout.moveFlatFilesIntoFolders() },
    // Version 2: history beside the maps (history/<map>/), so Android's backup can leave it out.
    LayoutStep(2) { layout -> layout.moveHistoryOutOfMaps() },
)

/** How long a copy made before a layout change is kept. */
private const val BACKUP_DAYS = 30

private val layoutJson = Json { ignoreUnknownKeys = true }

/**
 * The app's files: `maps/` (one folder per map), `history/` (each map's
 * earlier versions) and `backups/` (copies made before the layout changed).
 */
internal class StorageLayout(
    val maps: File,
    val history: File,
    private val backups: File,
    private val clock: () -> Long,

    /** Moves a file to where it's going, in one step, and says whether it did: [File.renameTo], unless a test's storage fails it. */
    private val move: (File, File) -> Boolean = { file, to -> file.renameTo(to) },
) {
    private val record = File(maps, ".layout.json")

    /**
     * Brings the files to this version's layout. Returns false when a newer
     * version laid them out in a way this one can't safely change, or left a
     * record of its layout this one can't read: the maps are then only read.
     * Throws [java.io.IOException] when a step couldn't be done whole: the
     * record then stays as it was, so the next launch runs the steps again.
     */
    fun prepare(): Boolean {
        val current = read()

        // A record that's there but can't be read, reshaped by a newer version or damaged, doesn't mean the files are from before layouts.
        if (current == null && record.exists()) {
            return false
        }

        if (current != null && current.version > LAYOUT_VERSION && LAYOUT_VERSION < current.compatible) {
            return false
        }

        removeOldBackups()

        if (current != null && current.version >= LAYOUT_VERSION) {
            // History an older version kept in a map's folder since, after going back to it for a while.
            moveHistoryOutOfMaps()
            return true
        }

        val from = current?.version ?: 0

        if (maps.isDirectory) {
            backUp(from)
        }

        maps.mkdirs()
        LAYOUT_STEPS.filter { it.to > from }.sortedBy { it.to }.forEach { it.apply(this) }
        writeAtomically(record, layoutJson.encodeToString(LayoutRecord.serializer(), LayoutRecord(LAYOUT_VERSION, LAYOUT_COMPATIBLE)))
        return true
    }

    /** The layout the files say they're in; null before layouts were written down, or when their record can't be read. */
    fun read(): LayoutRecord? =
        record.takeIf { it.isFile }?.let { runCatching { layoutJson.decodeFromString(LayoutRecord.serializer(), it.readText()) }.getOrNull() }

    /**
     * Earlier versions kept each map as a lone `.md` file: each gets its
     * folder. One that can't be moved stays where it is, without an empty
     * folder in its name's place, and the step throws once the others have
     * moved: the layout isn't recorded as done, so the next launch moves it.
     */
    fun moveFlatFilesIntoFolders() {
        var stayed = 0

        maps.listFiles { file -> file.isFile && file.name.endsWith(".md") }.orEmpty().forEach { file ->
            val taken = maps.list().orEmpty().toSet()
            val folder = File(maps, MapText.folderName(file.name.removeSuffix(".md"), taken))
            folder.mkdirs()
            val modified = file.lastModified()
            val target = File(folder, MAP_FILE)

            if (move(file, target)) {
                target.setLastModified(modified)
            } else {
                folder.delete()
                stayed += 1
            }
        }

        if (stayed > 0) {
            throw IOException("$stayed maps couldn't be moved into folders of their own")
        }
    }

    /**
     * Each map's history, from its folder (`maps/<map>/history/`) to its own
     * (`history/<map>/`). A version already there stays; one only in the map's
     * folder moves, and so does one there by a name the history has for
     * another text, beside it. Safe to run again after a crash midway. So
     * does the history a map brought back from the trash left in its folder
     * (`.history/`) when it couldn't be moved then.
     */
    fun moveHistoryOutOfMaps() {
        maps.listFiles { file -> file.isDirectory }.orEmpty().forEach { folder ->
            moveHistory(File(folder, HISTORY_DIRECTORY), folder.name)
            moveHistory(File(folder, TRASHED_HISTORY), folder.name)
        }
    }

    /**
     * Moves the versions in `old` into the history of map `id`, and joins
     * the reasons kept for them to the history's own ([moveReasons]). Each
     * file is copied whole or not at all, and leaves `old` only once it's
     * whole in the history, so a move cut short at any point, here or by an
     * earlier release, leaves every version whole somewhere ([moveHistoryFile]).
     */
    private fun moveHistory(old: File, id: String) {
        if (!old.isDirectory) {
            return
        }

        val target = File(history, id)

        if (!target.exists()) {
            history.mkdirs()

            if (old.renameTo(target)) {
                return
            }
        }

        target.mkdirs()
        val files = old.listFiles().orEmpty()
        val renamed = mutableMapOf<String, String>()

        for (file in files.filter { it.name != REASONS_FILE }) {
            moveHistoryFile(file, target)?.takeIf { it != file.name }?.let { renamed[file.name] = it }
        }

        files.firstOrNull { it.name == REASONS_FILE }?.let { moveReasons(it, File(target, REASONS_FILE), renamed) }
        old.delete()
    }

    /**
     * Moves one of an old history's files into `target`, whole, and deletes
     * it from where it was only once it's there. Where the history has a
     * file by its name already, the same one is only deleted; a version
     * that's another text, or whatever a copy cut short by an earlier
     * release left in its place, keeps its place, and this one goes in
     * beside it under a time of its own, so neither is lost. Returns its
     * name in `target`, or null when it stays where it was: a file of
     * another kind by a name the history has.
     */
    private fun moveHistoryFile(file: File, target: File): String? {
        val moved = File(target, file.name)

        if (!moved.exists()) {
            if (file.renameTo(moved)) {
                return moved.name
            }

            if (!file.isFile) {
                return null
            }

            copyWhole(file, moved)
            file.delete()
            return moved.name
        }

        if (file.isFile && sameBytes(file, moved)) {
            file.delete()
            return moved.name
        }

        return moveUnderTimeOfItsOwn(file, target)
    }

    /** Moves a version into `target` under a time a millisecond on from its own that no version there has; null, leaving it, for a file that isn't a version. */
    private fun moveUnderTimeOfItsOwn(file: File, target: File): String? {
        val time = VERSION_NAME.matchEntire(file.name)?.groupValues?.get(1)?.takeIf { file.isFile } ?: return null
        val kind = file.name.removePrefix(time)
        val free = generateSequence((time.toLongOrNull() ?: return null) + 1) { it + 1 }.map { File(target, "$it$kind") }.first { !it.exists() }
        copyWhole(file, free)
        file.delete()
        return free.name
    }

    /**
     * Joins an old history's reasons file (`old`) into the history's own
     * (`into`): its entries go in, under the names their versions moved in
     * under ([moveHistoryFile]), but for those the history has already, and
     * whatever else either holds stays. One that can't be read now throws,
     * staying where it is for the next start; one that isn't a reasons file
     * at all reads as none, as the history reads it ([MapHistory.reasonEntries]).
     */
    private fun moveReasons(old: File, into: File, renamed: Map<String, String>) {
        val oldFields = reasonsFields(old)
        val intoFields = reasonsFields(into)
        val oldEntries = (oldFields["versions"] as? JsonObject).orEmpty().mapKeys { (name, _) -> renamed[name] ?: name }
        val intoEntries = (intoFields["versions"] as? JsonObject).orEmpty()
        writeAtomically(into, JsonObject(oldFields + intoFields + ("versions" to JsonObject(oldEntries + intoEntries))).toString())
        old.delete()
    }

    /** A reasons file's fields: none when it isn't there or isn't JSON's object. Throws [java.io.IOException] when it's there but can't be read now. */
    private fun reasonsFields(file: File): JsonObject {
        if (!file.isFile) {
            return JsonObject(emptyMap())
        }

        val text = file.readTextSafely()
        return runCatching { mapsJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    /** Copies `from` to `to` whole or not at all, a little at a time. */
    private fun copyWhole(from: File, to: File) = writeAtomically(to) { output -> from.inputStream().use { it.copyTo(output) } }

    /**
     * A copy of every map's text, details and history before the layout
     * changes, in `backups/layout-<from>-<time>/`. Pictures stay where they
     * are: layout changes don't touch them, and copying them could fill the
     * phone.
     */
    private fun backUp(from: Int) {
        val target = File(backups, "layout-$from-${clock()}")
        maps.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".md") || it.name.endsWith(".json")) && MEDIA_DIRECTORY !in it.relativeTo(maps).path.split(File.separatorChar) }
            .forEach { file -> file.copyTo(File(target, "maps/${file.relativeTo(maps).path}"), overwrite = true) }
        history.takeIf { it.isDirectory }?.copyRecursively(File(target, "history"), overwrite = true)
    }

    private fun removeOldBackups() {
        val limit = clock() - BACKUP_DAYS * 24 * 60 * 60 * 1000L
        backups.listFiles().orEmpty()
            .filter { it.name.substringAfterLast('-').toLongOrNull()?.let { time -> time < limit } == true }
            .forEach { it.deleteRecursively() }
    }
}
