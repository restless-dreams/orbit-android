package com.hyuchia.orbit.data

import com.hyuchia.orbit.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

/** Deleted maps stay in the trash this long. */
const val TRASH_DAYS = 30

/** A day, for the trash's days. */
private const val DAY_MILLISECONDS = 24 * 60 * 60 * 1000L

/** In a map in the trash, when it was first seen past its days: it goes for good a day after, if it still is. */
private const val TRASH_SEEN_EXPIRED = ".expired"

/** In a map in the trash, its history, so it comes back with its versions. */
internal const val TRASHED_HISTORY = ".history"

/** [MapMetadata.removedByFolder]'s field in `meta.json`. */
private const val REMOVED_BY_FOLDER = "removedByFolder"

/** A deleted map in the trash, until it's restored or [TRASH_DAYS] go by. */
data class TrashedMap(
    /** The id it had, and gets back unless another map took it since. */
    val id: String,

    val title: String,

    /** When it was deleted. */
    val deleted: Long,

    val ideas: Int,

    /** It was private: while private maps are locked, the trash doesn't say its name. */
    val isPrivate: Boolean = false,

    /** What it shows instead of its name while it's locked, if its owner chose a cover (see [LockedCover]). */
    val cover: LockedCover? = null,

    internal val folder: File,

    /** Orbit can't read its file: it's listed by its id, to restore or delete like any other. */
    val unreadable: Boolean = false,
)

/**
 * The trash: deleted maps wait in it with their history for [TRASH_DAYS],
 * to be restored or deleted for good, and one the folder maps are kept in
 * sent there waits until the note saying so is read.
 */
internal class MapTrash(store: MapStore) : MapPart(store) {
    /**
     * Moves a map to the trash, its history with it; [restore] brings it
     * back for [TRASH_DAYS]. A map kept in a folder leaves its file there to
     * delete; restored, it's written there again. One the folder's sync sent
     * there as its file left (`byFolder`) stays past those days until the
     * note saying so is read ([seeFolderRemovals]).
     */
    suspend fun delete(id: String, byFolder: Boolean = false): TrashedMap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)

            if (!folder.isDirectory || !canWrite.value) {
                return@withLock null
            }

            // The folder's deletions, this map's file among them when it has one. Details or deletions that can't be read now
            // would leave its file there unrecorded, to come back as a map like any other: the map stays instead.
            val deletions = try {
                val metadata = readMetadataToChange(folder)
                // A private map's file, back in the folder, comes back private (see [SyncRecord.private]).
                val record = metadata.sync?.let { if (metadata.isPrivate) it.copy(private = true) else it }
                record?.let { readTombstones() + it }
            } catch (unreadable: IOException) {
                return@withLock null
            }

            trashDirectory.mkdirs()
            val trashedFolder = File(trashDirectory, "$id-${clock()}")

            if (!folder.renameTo(trashedFolder)) {
                return@withLock null
            }

            // Its deletion is kept at once: should that fail, the map goes back where it was, for the same reason.
            try {
                deletions?.let(::writeTombstones)
            } catch (failed: IOException) {
                trashedFolder.renameTo(folder)
                return@withLock null
            }

            if (byFolder) {
                runCatching { writeMetadata(trashedFolder, readMetadataToChange(trashedFolder).copy(removedByFolder = true)) }
            }

            val history = File(historyDirectory, id)

            if (history.isDirectory && !move(history, File(trashedFolder, TRASHED_HISTORY))) {
                Logs.warn("MapRepository", "Couldn't take a map's history to the trash: it stays where it was")
            }

            summaries.update { maps -> maps?.filterNot { it.id == id } }
            val item = trashedMap(trashedFolder)
            trashed.update { items -> listOfNotNull(item) + items }
            item
        }
    }

    /**
     * Brings a map back from the trash, under its own id unless another map
     * took it since. Returns the id it's back under, or null when it's gone.
     */
    suspend fun restore(item: TrashedMap): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!item.folder.isDirectory || !canWrite.value) {
                return@withLock null
            }

            // Details, or the folder's deletions, that can't be read now aren't written over: it waits in the trash until they can be.
            val (metadata, tombstones) = try {
                val metadata = readMetadataToChange(item.folder)
                metadata to if (metadata.sync != null) readTombstones() else emptyList()
            } catch (unreadable: IOException) {
                return@withLock null
            }

            val id = if (File(directory, item.id).exists()) newFolder(item.title) else item.id
            val folder = folder(id)
            // What the trash noted about it stays there.
            File(item.folder, TRASH_SEEN_EXPIRED).delete()

            if (!item.folder.renameTo(folder)) {
                return@withLock null
            }

            val trashedHistory = File(folder, TRASHED_HISTORY)

            if (trashedHistory.isDirectory && !move(trashedHistory, File(historyDirectory, id))) {
                Logs.warn("MapRepository", "Couldn't bring a map's history back from the trash: it stays in the map's folder")
            }

            // Its file stays in the folder; if it's gone from there already, it's written again as a new one.
            val record = metadata.sync
            // Back from the trash, it no longer waits there for a note to be read.
            var restored = metadata.copy(removedByFolder = false)

            if (record != null) {
                val tombstone = tombstones.firstOrNull { it.copy(private = null) == record.copy(private = null) }

                if (tombstone != null) {
                    writeTombstones(tombstones - tombstone)
                } else {
                    restored = restored.copy(sync = null)
                }
            }

            if (restored != metadata) {
                writeMetadata(folder, restored)
            }

            trashed.update { items -> items.filterNot { it.folder == item.folder } }
            insert(summarize(folder))
            id
        }
    }

    /**
     * Deletes a map in the trash for good. Returns whether it's gone: one
     * whose files don't all delete stays in the trash, listed as it is now,
     * to delete again ([deleteTrashedFolder]). Nothing goes while a newer
     * version keeps the maps.
     */
    suspend fun deleteForever(item: TrashedMap): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canWrite.value) {
                return@withLock false
            }

            val gone = deleteTrashedFolder(item.folder)
            val left = if (gone) null else trashedMap(item.folder)
            trashed.update { items -> items.mapNotNull { if (it.folder == item.folder) left else it } }
            gone
        }
    }

    /**
     * Deletes every map in the trash for good, but those `keeping` keeps: the ones the trash doesn't
     * show while private maps hide, which wait for their own time to go. Returns how many maps stayed,
     * their files not all deleting: the trash lists them still, to delete again ([deleteTrashedFolder]).
     */
    suspend fun emptyTrash(keeping: (TrashedMap) -> Boolean = { false }): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canWrite.value) {
                return@withLock 0
            }

            val kept = trashed.value.filter(keeping)
            val keptFolders = kept.mapTo(HashSet()) { it.folder.name }
            // A trash that can't be listed now can't be emptied either: every map it shows stays.
            val entries = trashDirectory.listFiles() ?: return@withLock trashed.value.count { it.folder.name !in keptFolders }
            val stayed = mutableListOf<TrashedMap>()

            for (entry in entries.filter { it.name !in keptFolders }) {
                val gone = if (entry.isDirectory) deleteTrashedFolder(entry) else entry.delete() || !entry.exists()

                if (!gone) {
                    trashedMap(entry)?.let { stayed += it }
                }
            }

            trashed.value = (kept + stayed).sortedByDescending { it.deleted }
            stayed.size
        }
    }

    /**
     * Deletes the folder of a map in the trash, its text and then its
     * details last: should any of its other files not delete (storage that
     * fails, say), its text and details stay, and the map with them, listed
     * in the trash as it was, private if it was, to delete again rather than
     * said to be gone. Every file that will go goes all the same. Returns
     * whether the map is gone.
     */
    private fun deleteTrashedFolder(folder: File): Boolean {
        // A folder that can't be listed now can't be deleted either: it stays as it is.
        val files = folder.listFiles() ?: return !folder.exists()
        val text = File(folder, MAP_FILE)
        val details = File(folder, METADATA_FILE)
        val others = files.filter { it != text && it != details }.fold(true) { whole, file -> file.deleteRecursively() && whole }

        if (!others || !(text.delete() || !text.exists())) {
            return false
        }

        // Its text gone, the map is: details or a folder that stay all the same hold no map, and the trash lists none.
        details.delete()
        folder.delete()
        return true
    }

    /**
     * The note saying which maps the folder sent to the trash has been read:
     * from now on, each has the trash's usual [TRASH_DAYS], counted from now,
     * but those `keeping` keeps, whose note wasn't shown (a private map's,
     * while private maps hide): they wait until it's read too.
     */
    suspend fun seeFolderRemovals(keeping: (TrashedMap) -> Boolean = { false }) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = clock()

            for (item in listTrash().filter { removedByFolder(it.folder) && !keeping(it) }) {
                runCatching { writeMetadata(item.folder, readMetadataToChange(item.folder).copy(removedByFolder = false)) }
                restamp(item, now)
            }

            trashed.value = listTrash()
        }
    }

    fun listTrash(): List<TrashedMap> =
        trashDirectory.listFiles { file -> file.isDirectory }.orEmpty().mapNotNull(::trashedMap).sortedByDescending { it.deleted }

    fun trashedMap(folder: File): TrashedMap? {
        val deleted = folder.name.substringAfterLast('-').toLongOrNull() ?: folder.lastModified()
        val id = folder.name.substringBeforeLast('-').takeIf { folder.name.contains('-') && it.isNotEmpty() } ?: folder.name
        val file = File(folder, MAP_FILE).takeIf { it.isFile } ?: return null
        // A map whose file can't be read is listed all the same, so it's never emptied away unseen.
        val outline = runCatching { file.readTextSafely() }.getOrNull()?.let { MapText.outline(it, words) }
        // Read as it is, never moved aside: listing the trash changes nothing in it. Details that can't be read are a private map's.
        val metadata = MetadataFile.readOrPrivate(File(folder, METADATA_FILE), MapMetadata())
        return TrashedMap(
            id,
            outline?.title ?: id,
            deleted,
            outline?.ideas ?: 0,
            isPrivate = metadata.isPrivate,
            cover = metadata.cover?.takeIf { metadata.isPrivate },
            folder = folder,
            unreadable = outline == null,
        )
    }

    /**
     * Deletes for good the maps in the trash past [TRASH_DAYS], once they've
     * been seen past them a day apart, and only while the clock can be
     * trusted: a clock that's wrong for a while never takes a map early. A
     * map dated in the future, or before Orbit was installed, went to the
     * trash with the clock wrong: its days start again now.
     */
    fun emptyExpiredTrash() {
        val now = clock()

        for (item in listTrash()) {
            val seen = File(item.folder, TRASH_SEEN_EXPIRED)

            when {
                item.deleted > now + DAY_MILLISECONDS || item.deleted < installed -> restamp(item, now)
                // A map the folder sent here waits for its note to be read, however long that takes.
                removedByFolder(item.folder) -> Unit
                now - item.deleted < TRASH_DAYS * DAY_MILLISECONDS -> seen.delete()
                !clockTrusted() -> Unit
                !seen.isFile -> runCatching { writeAtomically(seen, now.toString()) }
                (seen.readText().trim().toLongOrNull() ?: now) <= now - DAY_MILLISECONDS -> if (!deleteTrashedFolder(item.folder)) {
                    Logs.warn("MapRepository", "A map past its days in the trash couldn't be deleted: it stays, to go at the next start")
                }
            }
        }
    }

    /**
     * Dates a map in the trash `now`: its days in the trash start again, as
     * for one deleted with a wrong clock, or one the folder sent there once
     * its note has been read ([seeFolderRemovals]).
     */
    private fun restamp(item: TrashedMap, now: Long) {
        val folder = item.folder
        val renamed = File(folder.parentFile, "${folder.name.substringBeforeLast('-')}-$now")

        if (!renamed.exists()) {
            folder.renameTo(renamed)
        }
    }

    /**
     * Whether the folder sent the map in `folder` to the trash, to wait there
     * until the note saying so is read ([MapMetadata.removedByFolder]). Only
     * details that say it didn't, or a map that has none, let it go when its
     * days are up: details that can't be read, now or at all, or whose
     * `removedByFolder` this version can't read, may have said it did, and
     * the map waits rather than go for good unseen.
     */
    private fun removedByFolder(folder: File): Boolean {
        val file = File(folder, METADATA_FILE)

        if (!file.exists()) {
            return false
        }

        return try {
            val metadata = MetadataFile.decode(file.readText())
            metadata.removedByFolder || REMOVED_BY_FOLDER in metadata.extras
        } catch (unreadable: IOException) {
            true
        } catch (damaged: SerializationException) {
            true
        }
    }
}
