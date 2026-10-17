package com.hyuchia.orbit.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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

/** The layout the files are in, as the app that laid them out wrote it in `maps/.layout.json`. */
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
) {
    private val record = File(maps, ".layout.json")

    /**
     * Brings the files to this version's layout. Returns false when a newer
     * version laid them out in a way this one can't safely change: the maps
     * are then only read.
     */
    fun prepare(): Boolean {
        val current = read()

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

    /** The layout the files say they're in; null before layouts were written down. */
    fun read(): LayoutRecord? =
        record.takeIf { it.isFile }?.let { runCatching { layoutJson.decodeFromString(LayoutRecord.serializer(), it.readText()) }.getOrNull() }

    /** Earlier versions kept each map as a lone `.md` file: each gets its folder. */
    fun moveFlatFilesIntoFolders() {
        maps.listFiles { file -> file.isFile && file.name.endsWith(".md") }.orEmpty().forEach { file ->
            val taken = maps.list().orEmpty().toSet()
            val folder = File(maps, MapText.folderName(file.name.removeSuffix(".md"), taken))
            folder.mkdirs()
            val modified = file.lastModified()
            val target = File(folder, MAP_FILE)

            if (file.renameTo(target)) {
                target.setLastModified(modified)
            }
        }
    }

    /**
     * Each map's history, from its folder (`maps/<map>/history/`) to its own
     * (`history/<map>/`). A version already there stays; one only in the map's
     * folder moves. Safe to run again after a crash midway.
     */
    fun moveHistoryOutOfMaps() {
        maps.listFiles { file -> file.isDirectory }.orEmpty().forEach { folder ->
            val old = File(folder, HISTORY_DIRECTORY)

            if (!old.isDirectory) {
                return@forEach
            }

            val target = File(history, folder.name)

            if (!target.exists()) {
                history.mkdirs()

                if (old.renameTo(target)) {
                    return@forEach
                }
            }

            target.mkdirs()

            old.listFiles().orEmpty().forEach { version ->
                val moved = File(target, version.name)

                if (moved.exists()) {
                    version.delete()
                } else if (!version.renameTo(moved)) {
                    version.copyTo(moved)
                    version.delete()
                }
            }

            old.delete()
        }
    }

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
