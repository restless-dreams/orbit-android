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
     * delete, its deletion recorded before it moves, so no map is ever in
     * the trash without it; restored, it's written there again. One whose
     * details can't say where its file is leaves what they can't say
     * ([SyncRecord.untold]). One the folder's sync sent there as its file
     * left (`byFolder`) leaves no deletion, its file gone already, unless
     * it's private ([folderRemoval]), and keeps its record of the file,
     * which [restore] lets go of; it stays past those days until the note
     * saying so is read ([seeFolderRemovals]). Its history and that mark go
     * into its folder before it moves, so the one move takes them all: a
     * step that fails leaves the map where it was, as it was, and it returns
     * null, as it does when Orbit can't move it.
     */
    suspend fun delete(id: String, byFolder: Boolean = false): TrashedMap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)

            if (!folder.isDirectory || !canWrite.value) {
                return@withLock null
            }

            // The folder's deletions, this map's file among them when it has one. Details or deletions that can't be read now
            // would leave its file there unrecorded, to come back as a map like any other: the map stays instead.
            val (metadata, before, record) = try {
                val metadata = readMetadataToChange(folder)
                val record = if (byFolder) folderRemoval(metadata) else folderDeletion(folder, metadata)
                Triple(metadata, if (record != null) readTombstones() else emptyList(), record)
            } catch (unreadable: IOException) {
                return@withLock null
            }

            // Its deletion is kept first: should that fail, the map stays, for the same reason. Should Orbit stop before the map
            // has moved, the next start finds it still here, and forgets the deletion ([MapStore.forgetUnfinishedDeletions]).
            try {
                record?.let { writeTombstones(before + it) }
            } catch (failed: IOException) {
                return@withLock null
            }

            val history = File(historyDirectory, id)
            val carried = File(folder, TRASHED_HISTORY)
            var marked = false

            // What a step that fails leaves undone, as far as it can be: the map stays where it was, its file in the folder with
            // no deletion to take it. A history that can't go back now does at the next start, as one Orbit stopped with does.
            fun undo(historyCarried: Boolean) {
                if (marked) {
                    runCatching { writeMetadata(folder, metadata) }
                }

                if (historyCarried && !move(carried, history)) {
                    Logs.warn("MapRepository", "Couldn't put a map's history back: it goes back at the next start")
                }

                record?.let { runCatching { writeTombstones(before) } }
            }

            // Its history goes into its folder first, so the one move to the trash takes both, and the map never waits there, nor
            // goes for good, with its history left behind. Should Orbit stop before that move, the next start puts the history
            // back where it was ([StorageLayout.moveHistoryOutOfMaps]).
            val historyCarried = history.isDirectory

            // A move that fails leaves the history whole where it was ([move]); what it copied stays in the map's folder until the
            // next start puts it with the rest, where it's already.
            if (historyCarried && !move(history, carried)) {
                Logs.warn("MapRepository", "Couldn't take a map's history to the trash: the map stays where it was")
                undo(historyCarried = false)
                return@withLock null
            }

            // One the folder sent is marked so while it's still among the maps, and the same move takes the mark with it: in
            // the trash, it always waits for its note to be read. One deleted here that's still marked, as Orbit stopping right
            // after marking it may have left it, isn't anymore, if that can be written: it would only wait longer in the trash.
            if (metadata.removedByFolder != byFolder) {
                try {
                    writeMetadata(folder, metadata.copy(removedByFolder = byFolder))
                    marked = byFolder
                } catch (failed: IOException) {
                    if (byFolder) {
                        undo(historyCarried)
                        return@withLock null
                    }
                }
            }

            trashDirectory.mkdirs()
            val trashedFolder = File(trashDirectory, "$id-${clock()}")

            if (!rename(folder, trashedFolder)) {
                undo(historyCarried)
                return@withLock null
            }

            summaries.update { maps -> maps?.filterNot { it.id == id } }
            val item = trashedMap(trashedFolder)
            trashed.update { items -> listOfNotNull(item) + items }
            item
        }
    }

    /**
     * The record of a map's file in the folder its deletion leaves, for the
     * sync to delete the file: its record, saying it was private when it
     * was, so its file, back in the folder, comes back private (see
     * [SyncRecord.private]). Details that can't say whether it has a file,
     * nor which, leave its text as it is and whether it was private, for the
     * folder the maps are kept in now ([SyncRecord.untold]), so a sync of it
     * never takes its file for a map like any other; with the maps kept in
     * no folder, it has no file a sync could find, and leaves nothing. Null
     * for a map with no file in a folder.
     */
    private suspend fun folderDeletion(folder: File, metadata: MapMetadata): SyncRecord? {
        val record = metadata.sync

        if (record != null) {
            return if (metadata.isPrivate) record.copy(private = true) else record
        }

        if (!metadata.folderFileUnknown) {
            return null
        }

        val keptIn = syncFolder() ?: return null
        // A text that can't be read matches no file: a hash no text has.
        val text = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrNull()
        return SyncRecord.untoldDeletion(keptIn, text?.let(::digest).orEmpty(), metadata.isPrivate)
    }

    /**
     * The deletion a private map the folder's sync sent to the trash leaves,
     * its file gone from the folder already, so a file that may be the
     * map's comes in private: with no text to tell its file by (an empty
     * `hash`, and no time or size), so one by its name that comes back is
     * never deleted, but comes in private, as does one that may be it
     * renamed, by the text the trash keeps ([deletedText]); and gone since
     * now, so it goes as any deletion whose file is gone does. None for a
     * map that isn't private.
     */
    private fun folderRemoval(metadata: MapMetadata): SyncRecord? = metadata.sync?.takeIf { metadata.isPrivate }?.gone(since = clock())

    /**
     * The text a deleted map had, as the trash keeps it, for its deletion
     * `record`: the map in the trash whose details name the same file, with
     * the same text as it last matched it (two deleted maps can have had
     * one file's name), or, for one whose file couldn't be told
     * ([SyncRecord.untold]), the one with the same text; for a private
     * map's deletion with no text to tell it by ([SyncRecord.textless]), the
     * newest private map in the trash whose details name its file. Null when
     * the trash doesn't have it anymore, or the deletion says too little to
     * find it by.
     */
    suspend fun deletedText(record: SyncRecord): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (record.untold && record.hash.isEmpty()) {
                return@withLock null
            }

            // No text to tell it by: the newest private map in the trash whose file was the deletion's, as only a private map's
            // deletion has none. Not one that isn't private, which may have had the name too.
            if (record.textless) {
                val newestFirst = trashDirectory.listFiles { file -> file.isDirectory }.orEmpty().sortedByDescending { it.name.substringAfterLast('-').toLongOrNull() ?: 0 }

                for (trashed in newestFirst) {
                    val metadata = readMetadata(trashed)

                    if (metadata.isPrivate && metadata.sync?.let { it.folder == record.folder && it.file == record.file } == true) {
                        return@withLock runCatching { File(trashed, MAP_FILE).readTextSafely() }.getOrNull()
                    }
                }

                return@withLock null
            }

            for (trashed in trashDirectory.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }) {
                val text = runCatching { File(trashed, MAP_FILE).readTextSafely() }.getOrNull() ?: continue

                val its = if (record.untold) {
                    digest(text) == record.hash
                } else {
                    readMetadata(trashed).sync?.let { it.folder == record.folder && it.file == record.file && it.hash == record.hash } == true
                }

                if (its) {
                    return@withLock text
                }
            }

            null
        }
    }

    /**
     * Brings a map back from the trash, under its own id unless another map
     * took it since. Returns the id it's back under, or null when it's gone
     * or couldn't come back: then it's in the trash as it was, its deletion
     * and details too, never back here halfway.
     */
    suspend fun restore(item: TrashedMap): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            // One whose text went as it was deleted for good, its details staying, is no map to bring back: it only goes.
            if (!File(item.folder, MAP_FILE).isFile || !canWrite.value) {
                return@withLock null
            }

            // Details, or the folder's deletions, that can't be read now aren't written over: it waits in the trash until they can be.
            val (metadata, tombstones) = try {
                val metadata = readMetadataToChange(item.folder)
                metadata to if (metadata.sync != null || metadata.folderFileUnknown) readTombstones() else emptyList()
            } catch (unreadable: IOException) {
                return@withLock null
            }

            val id = if (File(directory, item.id).exists()) newFolder(item.title) else item.id
            val folder = folder(id)
            // What the trash noted about it stays there.
            File(item.folder, TRASH_SEEN_EXPIRED).delete()

            if (!rename(item.folder, folder)) {
                return@withLock null
            }

            // Its file stays in the folder; if it's gone from there already, it's written again as a new one.
            val record = metadata.sync
            // Back from the trash, it no longer waits there for a note to be read.
            var restored = metadata.copy(removedByFolder = false)
            var left = tombstones

            if (record != null) {
                // Its deletion goes, whether or not a sync found its file gone since; and with it, the record of a file gone.
                val tombstone = tombstones.firstOrNull { it.copy(private = null, missingSince = null) == record.copy(private = null, missingSince = null) }

                // So does one with no text to tell its file by, that a private map's whose file had gone left (see
                // [SyncRecord.textless]), when this is a private map: back here, a file new to the maps isn't this map. Not when
                // it isn't, which may only have had the same file's name before the private map did.
                val textless = tombstones.filter { metadata.isPrivate && it.textless && it.folder == record.folder && it.file == record.file }
                left = left - listOfNotNull(tombstone).toSet() - textless.toSet()

                // Gone from the folder already (no deletion left, or one a sync found its file missing for), it has no file there
                // to follow out again: it's written there as a new one.
                if (tombstone == null || tombstone.missingSince != null) {
                    restored = restored.copy(sync = null)
                }
            }

            // Its deletion, where its file couldn't be told, goes once it's back, by its text, which it still has.
            if (metadata.folderFileUnknown) {
                val text = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrNull()
                val untold = text?.let(::digest)?.let { hash -> tombstones.firstOrNull { it.untold && it.hash == hash } }

                if (untold != null) {
                    left = left - untold
                }
            }

            // Its deletion goes and its details say it's back, or it goes back to the trash as it was: here with its deletion
            // kept, the next sync would delete its file, and it would follow it out. Should Orbit stop before they're written,
            // the next start finds it among the maps, and forgets its deletion then ([MapStore.forgetUnfinishedDeletions]).
            try {
                if (left != tombstones) {
                    writeTombstones(left)
                }

                if (restored != metadata) {
                    writeMetadata(folder, restored)
                }
            } catch (failed: IOException) {
                if (left != tombstones) {
                    runCatching { writeTombstones(tombstones) }
                }

                if (rename(folder, item.folder)) {
                    Logs.warn("MapRepository", "Couldn't bring a map back from the trash: it's there as it was", failed)
                    return@withLock null
                }

                // Not even that: it's back, as the list says. Its deletion goes, if it can now, so no sync takes its file away
                // before the next start forgets it.
                Logs.warn("MapRepository", "Couldn't bring a map back from the trash whole, nor put it back there", failed)

                if (left != tombstones) {
                    runCatching { writeTombstones(left) }
                }
            }

            val trashedHistory = File(folder, TRASHED_HISTORY)

            if (trashedHistory.isDirectory && !move(trashedHistory, File(historyDirectory, id))) {
                Logs.warn("MapRepository", "Couldn't bring a map's history back from the trash: it stays in the map's folder")
            }

            trashed.update { items -> items.filterNot { it.folder == item.folder } }
            insert(summarize(folder))
            id
        }
    }

    /**
     * Deletes a map in the trash for good. Returns whether it's gone: one
     * whose files don't all delete stays in the trash, listed as it is now,
     * or as it was once its text is gone and its details aren't, to delete
     * again ([deleteTrashedFolder]). Nothing goes while a newer version
     * keeps the maps.
     */
    suspend fun deleteForever(item: TrashedMap): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canWrite.value) {
                return@withLock false
            }

            val gone = deleteTrashedFolder(item.folder)
            val left = if (gone) null else stillThere(item.folder)
            trashed.update { items -> items.mapNotNull { if (it.folder == item.folder) left else it } }
            gone
        }
    }

    /**
     * The map in the trash's `folder` that didn't all delete, as the trash
     * lists it now, or, its text gone and its details not, as it listed it
     * before, private if it was: it's still there, to delete again. Null
     * once nothing's left of it.
     */
    private fun stillThere(folder: File): TrashedMap? = trashedMap(folder) ?: trashed.value.firstOrNull { it.folder == folder }?.takeIf { folder.exists() }

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
                    stillThere(entry)?.let { stayed += it }
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
     * said to be gone. Every file that will go goes all the same. Its history
     * left outside the trash goes first ([deleteHistoryLeftBehind]). Returns
     * whether the map is gone: only once nothing's left of it, its details
     * and its folder, which is named after it, included.
     */
    private fun deleteTrashedFolder(folder: File): Boolean {
        // A folder that can't be listed now can't be deleted either: it stays as it is.
        val files = folder.listFiles() ?: return !folder.exists()
        val text = File(folder, MAP_FILE)
        val details = File(folder, METADATA_FILE)

        if (!deleteHistoryLeftBehind(folder)) {
            return false
        }

        val others = files.filter { it != text && it != details }.fold(true) { whole, file -> file.deleteRecursively() && whole }

        if (!others || !(text.delete() || !text.exists())) {
            return false
        }

        // Its text gone, the trash lists it no more after a restart, but its details may still say what it was, and its folder's
        // name what it was called: it's gone once they are. One whose details stay goes at the next start ([emptyExpiredTrash]).
        return (details.delete() || !details.exists()) && (folder.delete() || !folder.exists())
    }

    /**
     * Deletes the history the map in the trash's `folder` left outside it,
     * in `history/` under its id, as an earlier version left it when it
     * couldn't take it along, or a move that copied it but couldn't delete
     * it: unless a map here, or another in the trash, has that id, whose it
     * may be. Returns whether none of it is left.
     */
    private fun deleteHistoryLeftBehind(folder: File): Boolean {
        val id = trashedId(folder)

        if (id.isEmpty() || id.startsWith(".") || '/' in id || '\\' in id) {
            return true
        }

        val history = File(historyDirectory, id)

        if (!history.exists()) {
            return true
        }

        val others = trashDirectory.listFiles { file -> file != folder && trashedId(file) == id }

        if (File(directory, id).exists() || others == null || others.isNotEmpty()) {
            return true
        }

        history.deleteRecursively()
        return !history.exists()
    }

    /** The id of the map in the trash's `folder`, named `<id>-<time>`. */
    private fun trashedId(folder: File): String =
        folder.name.substringBeforeLast('-').takeIf { folder.name.contains('-') && it.isNotEmpty() } ?: folder.name

    /**
     * The note saying which maps the folder sent to the trash has been read:
     * from now on, each has the trash's usual [TRASH_DAYS], counted from now,
     * but those `keeping` keeps, whose note wasn't shown (a private map's,
     * while private maps hide): they wait until it's read too.
     */
    suspend fun seeFolderRemovals(keeping: (TrashedMap) -> Boolean = { false }) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = clock()

            // Its days start again first, and only then does it stop waiting: one that can't be dated now waits on, for the next
            // note read, rather than go within a day of the day it went to the trash.
            for (item in listTrash().filter { removedByFolder(it.folder) && !keeping(it) }) {
                val restamped = restamp(item, now) ?: continue
                runCatching { writeMetadata(restamped, readMetadataToChange(restamped).copy(removedByFolder = false)) }
            }

            trashed.value = listTrash()
        }
    }

    fun listTrash(): List<TrashedMap> =
        trashDirectory.listFiles { file -> file.isDirectory }.orEmpty().mapNotNull(::trashedMap).sortedByDescending { it.deleted }

    fun trashedMap(folder: File): TrashedMap? {
        val deleted = folder.name.substringAfterLast('-').toLongOrNull() ?: folder.lastModified()
        val id = trashedId(folder)
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
     * trash with the clock wrong: its days start again now. A map deleted
     * for good whose text went but whose details or folder stayed, as Orbit
     * stopping in between leaves it, goes first.
     */
    fun emptyExpiredTrash() {
        val now = clock()

        for (left in trashDirectory.listFiles { file -> file.isDirectory && leftByDeletion(file) }.orEmpty()) {
            if (!deleteTrashedFolder(left)) {
                Logs.warn("MapRepository", "What a map deleted for good left in the trash couldn't be deleted: it goes at the next start")
            }
        }

        for (item in listTrash()) {
            val seen = File(item.folder, TRASH_SEEN_EXPIRED)
            // When it was first seen past its days: a mark that isn't there, or can't be read, says nothing, and is made again.
            val seenAt by lazy { runCatching { seen.readText().trim().toLongOrNull() }.getOrNull() }

            when {
                item.deleted > now + DAY_MILLISECONDS || item.deleted < installed -> if (restamp(item, now) == null) {
                    Logs.warn("MapRepository", "A map in the trash dated with the clock wrong couldn't be dated again: it waits")
                }
                // A map the folder sent here waits for its note to be read, however long that takes.
                removedByFolder(item.folder) -> Unit
                now - item.deleted < TRASH_DAYS * DAY_MILLISECONDS -> seen.delete()
                !clockTrusted() -> Unit
                seenAt == null -> runCatching { writeAtomically(seen, now.toString()) }
                (seenAt ?: now) <= now - DAY_MILLISECONDS -> if (!deleteTrashedFolder(item.folder)) {
                    Logs.warn("MapRepository", "A map past its days in the trash couldn't be deleted: it stays, to go at the next start")
                }
            }
        }
    }

    /**
     * Whether `folder` in the trash is what deleting a map for good leaves
     * when it stops after the map's text: its details alone, or nothing.
     * Anything else stays, whatever it is.
     */
    private fun leftByDeletion(folder: File): Boolean = folder.list()?.all { it == METADATA_FILE } == true

    /**
     * Dates a map in the trash `now`: its days in the trash start again, as
     * for one deleted with a wrong clock, or one the folder sent there once
     * its note has been read ([seeFolderRemovals]). When it was seen past its
     * earlier days goes first, so the new ones are counted whole. Returns its
     * folder now, or null when it couldn't be dated again, and keeps its date.
     */
    private fun restamp(item: TrashedMap, now: Long): File? {
        val folder = item.folder
        val seen = File(folder, TRASH_SEEN_EXPIRED)
        val renamed = File(folder.parentFile, "${folder.name.substringBeforeLast('-')}-$now")

        if (!(seen.delete() || !seen.exists())) {
            return null
        }

        // Dated now already, as one the folder sent here this very moment.
        if (renamed == folder) {
            return folder
        }

        return renamed.takeIf { !it.exists() && folder.renameTo(it) }
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
