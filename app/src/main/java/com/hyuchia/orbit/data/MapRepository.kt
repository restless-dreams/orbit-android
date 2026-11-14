package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.study.StudyCard
import com.hyuchia.orbit.study.StudyChange
import com.hyuchia.orbit.study.notCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.FileAlreadyExistsException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The map's text in its folder. */
const val MAP_FILE = "map.md"

/** A picture added this recently is kept even if nothing points at it yet: the text pointing at it may be on its way. */
private const val PICTURE_GRACE = 10 * 60 * 1000L

/** Unpacked zips older than this were left by an import that never finished. */
private const val UNPACKED_LEFTOVER = 60 * 60 * 1000L

/**
 * How long a zip unpacked for a question that was never answered stays, at
 * most, when no screen let it go (see [WaitingArrivals]): a screen Android
 * brings back after stopping Orbit asks it again until then.
 */
private const val WAITING_LEFTOVER = 7 * 24 * 60 * 60 * 1000L

/** A picture or video that went from where it was being copied from. */
private class NothingToCopy : IOException()

/** A map, as the home screen shows it. */
data class MapSummary(
    val id: String,
    val outline: MapOutline,
    val modified: Long,
    val metadata: MapMetadata,

    /** The map's words, to search in. */
    val searchText: String,

    /** Changes whenever the map's text does: what the folder maps are kept in is compared by. */
    val contentKey: String,

    /** Its file couldn't be read: the map shows as such, and nothing writes over it. */
    val unreadable: Boolean = false,

    /**
     * The pictures, videos and sounds its text points at that its folder
     * doesn't have, as when they're still on their way from the folder maps
     * are kept in, or a zip: its picture is drawn again as they come
     * ([pictureKey]).
     */
    val missingMedia: Set<String> = emptySet(),
) {
    val title: String get() = outline.title

    /** Changes whenever the map's picture would: with its text, and as media it was missing comes (see [missingMedia]). */
    val pictureKey: String by lazy { if (missingMedia.isEmpty()) contentKey else "$contentKey-${digest(missingMedia.sorted().joinToString("/"))}" }
}

/** How a write went. */
sealed interface WriteResult {
    /** The text is the map's now. */
    data object Written : WriteResult

    /** The map changed since the text the write was based on: nothing was written, and the text was kept in the map's history. */
    data object Changed : WriteResult

    /** The text would have emptied a map with ideas on it, or the maps can only be read here: nothing was written. */
    data object Refused : WriteResult

    /** The map isn't here anymore. */
    data object Missing : WriteResult

    /** The text couldn't be written, as when the device's storage is full: the map's file is as it was. */
    data object NotWritten : WriteResult
}

/** Ideas of map `map` that new text gave new ids, `renames` from the old to the new (see [MapRepository.change]). */
data class IdeasRenamed(val map: String, val renames: Map<String, String>)

/** When a write keeps the map as it was in its history first. */
enum class Checkpoint {
    /** Once the latest version is [CHECKPOINT_INTERVAL] old: one version for every stretch of work. */
    Interval,

    /** Always: before a big change, or the first change of a visit. */
    Always,
}

/**
 * A map a first launch puts in: its text, the pictures, videos and sounds it
 * shows, by their names in its `media/`, and the details its `meta.json`
 * starts with, such as a presentation path.
 */
class StarterMap(
    val markdown: String,
    val media: Map<String, ByteArray> = emptyMap(),
    val metadata: MapMetadata = MapMetadata(),
)

/** The maps a first launch puts in: the samples, oldest first (debug builds have one), then the welcome map. */
interface StarterMaps {
    fun samples(): List<String>

    fun welcome(): StarterMap?
}

/**
 * The starter maps in the app's assets, in the language the app is in when
 * it has its own: `welcome.md`, with its details in `welcome.json` and its
 * media in `welcome/`, the same in every language; and `samples/`, which
 * only debug builds have (a big map to try things on).
 */
class AssetStarterMaps(private val context: Context) : StarterMaps {
    override fun samples(): List<String> {
        val folder = context.assetPath("samples")
        return context.assets.list(folder).orEmpty().sorted().map { name -> context.assets.open("$folder/$name").bufferedReader().use { it.readText() } }
    }

    override fun welcome(): StarterMap? = runCatching {
        StarterMap(
            markdown = context.openAsset("welcome.md").bufferedReader().use { it.readText() },
            media = context.assets.list(WELCOME_MEDIA).orEmpty().associateWith { name -> context.assets.open("$WELCOME_MEDIA/$name").use { it.readBytes() } },
            // Details it can't read leave it as any new map is: it presents in reading order.
            metadata = runCatching { MetadataFile.decode(context.openAsset("welcome.json").bufferedReader().use { it.readText() }) }.getOrDefault(MapMetadata()),
        )
    }.getOrNull()

    private companion object {
        /** The welcome map's pictures, video and sound, drawn by art/welcome.ts. */
        const val WELCOME_MEDIA = "welcome"
    }
}

/**
 * The user's maps: one folder per map, holding the map as an orbit-md file
 * (`map.md`), the pictures added to it (`media/`) and what the app remembers
 * about it (`meta.json`). Maps stay plain files, so they can be shared,
 * backed up and opened anywhere Orbit runs.
 *
 * Nothing here loses a map: every file is written whole or not at all (see
 * [writeAtomically]); a map's earlier versions are kept as it changes; text
 * that would empty a map never replaces it; deleted maps wait in the trash
 * for [TRASH_DAYS]; and files a newer version laid out are only read.
 * What it writes for people to read (an untitled map's title, a copy's,
 * why a zip or a video is refused) is in `words`.
 */
class MapRepository(
    private val directory: File,
    private val trashDirectory: File,
    private val starterMaps: StarterMaps,
    private val words: Words,
    private val historyDirectory: File = File(directory.parentFile, HISTORY_DIRECTORY),

    /** Files of deleted maps, still to be deleted from the folder maps are kept in. */
    private val tombstones: File = File(directory.parentFile, "folder-deletions.json"),

    backups: File = File(directory.parentFile, "backups"),
    private val clock: () -> Long = System::currentTimeMillis,

    /** How many bytes can still be written where a file is, for a video from the phone: the system knows best, cache it can clear included. */
    private val room: (File) -> Long = { Long.MAX_VALUE },

    /** When Orbit was installed here: a map can't have gone to the trash before, so one dated earlier was deleted with the clock wrong. */
    private val installed: Long = 0,

    /**
     * Whether the clock can be trusted to age the history and the trash by:
     * not while the network's time, where the phone knows it, says otherwise.
     * Nothing is thinned out or emptied for good while it can't.
     */
    private val clockTrusted: () -> Boolean = { true },

    /** Moves a map's folder between the maps and the trash in one step, as [File.renameTo] does, unless a test stops Orbit there. */
    rename: (File, File) -> Boolean = { file, to -> file.renameTo(to) },

    /** The folder the maps are kept in now (the settings' `syncFolder`), or null: a deletion is kept for it ([MapTrash.delete]). */
    syncFolder: suspend () -> String? = { null },

    /** The most a map's pictures, videos, sounds and files come to as a zip of every map brings them in ([MAP_MEDIA_LIMIT]); less only in tests. */
    mapMediaLimit: Long = MAP_MEDIA_LIMIT,

    /** Hears each name held for a file coming into a map's media folder, once it's held ([IncomingMedia.Names]): a test holds a copy there, to bring another in beside it. */
    naming: (String) -> Unit = {},
) {
    /** Zips of maps, unpacked while they're taken in. */
    private val unpacking = File(directory.parentFile, "unpacking")

    /** What this repository's parts share (see [MapStore]). */
    private val store = MapStore(directory, trashDirectory, historyDirectory, tombstones, words, clock, installed, clockTrusted, rename, syncFolder, naming)

    private val trashing = MapTrash(store)
    private val histories = MapHistory(store)
    private val intake = MapMediaIntake(store, room)
    private val bundles = MapBundles(store, histories, unpacking, mapMediaLimit)

    /** Files of maps here already in other versions, kept while they wait for an answer (see [WaitingArrivals]). */
    val waitingArrivals = WaitingArrivals(unpacking, directory)

    private val mutex: Mutex get() = store.mutex
    private val summaries: MutableStateFlow<List<MapSummary>?> get() = store.summaries
    private val trashed: MutableStateFlow<List<TrashedMap>> get() = store.trashed
    private val canWrite: MutableStateFlow<Boolean> get() = store.canWrite
    private val replacedMaps: MutableSharedFlow<String> get() = store.replacedMaps

    private val layout = StorageLayout(directory, historyDirectory, backups, clock)

    /** How many screens show each map, by id. */
    private val screens = ConcurrentHashMap<String, Int>()

    /**
     * Maps replaced by a newer version from elsewhere (another device, the
     * folder, a quick capture, or a screen that closed on the map as it
     * opened again: [lastWritten]), by id, so a screen showing one can catch
     * up.
     */
    val replaced: SharedFlow<String> = replacedMaps.asSharedFlow()

    private val renamedIdeas = MutableSharedFlow<IdeasRenamed>(extraBufferCapacity = 16)

    /** Ideas a change from outside their map's screen gave new ids (see [change]), so a screen showing the map follows them. */
    val ideasRenamed: SharedFlow<IdeasRenamed> = renamedIdeas.asSharedFlow()

    /** Every map, or null until they are read. */
    val maps: StateFlow<List<MapSummary>?> = summaries.asStateFlow()

    /** The maps in the trash, the latest deleted first. */
    val trash: StateFlow<List<TrashedMap>> = trashed.asStateFlow()

    /**
     * False when a newer version of Orbit laid the files out in a way this
     * one can't safely change: maps are then only read, until Orbit is updated.
     */
    val writable: StateFlow<Boolean> = canWrite.asStateFlow()

    /**
     * Reads the maps, after bringing the files to this version's layout. On
     * the first launch, puts the welcome map in. Maps that waited in the
     * trash too long are deleted for good.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val firstLaunch = !directory.exists()

            // Files that can't be brought to this layout (a full phone, say) are still read, and left as they are.
            canWrite.value = try {
                layout.prepare()
            } catch (failed: IOException) {
                false
            }

            if (firstLaunch && canWrite.value) {
                installStarterMaps()
            }

            if (canWrite.value) {
                clearLeftovers()
                trashing.emptyExpiredTrash()
            }

            val read = directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }
                .orEmpty()
                .map(::summarize)
            summaries.value = if (canWrite.value) withoutTwins(read) else read
            trashed.value = trashing.listTrash()

            // A deletion Orbit stopped in the middle of left its record with the map still here.
            if (canWrite.value) {
                store.forgetUnfinishedDeletions(summaries.value.orEmpty())
            }
        }
    }

    /**
     * `read` with each map that shares its identity with an earlier one
     * given an identity of its own, as two maps could be left sharing one
     * before 0.4.0. The one made first keeps it, the one other devices know
     * it by: by when each was made, or by their folders' names when that's
     * the same or unknown. Only the later one's details are written, keeping
     * what a newer version added, and never its text.
     */
    private fun withoutTwins(read: List<MapSummary>): List<MapSummary> {
        val later = read.filter { it.metadata.uuid != null }.groupBy { it.metadata.uuid }.values.filter { it.size > 1 }.flatMap { twins ->
            val known = twins.all { it.metadata.created > 0 }
            val first = if (known) twins.minWith(compareBy({ it.metadata.created }, { it.id })) else twins.minBy { it.id }
            twins - first
        }

        if (later.isEmpty()) {
            return read
        }

        val separated = later.associate { twin ->
            val folder = folder(twin.id)
            runCatching { writeMetadata(folder, readMetadataToChange(folder).copy(uuid = UUID.randomUUID().toString())) }
                .onFailure { Logs.warn("MapRepository", "Couldn't give a map an identity of its own", it) }
            twin.id to summarize(folder)
        }

        return read.map { separated[it.id] ?: it }
    }

    fun folder(id: String): File = store.folder(id)

    /** Whether `id` is a map here (see [MapStore.exists]). */
    fun exists(id: String): Boolean = store.exists(id)

    /** The map's text. Throws when it's gone or can't be read. */
    suspend fun read(id: String): String = withContext(Dispatchers.IO) {
        File(folder(id), MAP_FILE).readTextSafely()
    }

    fun summary(id: String): MapSummary? = store.summary(id)

    /**
     * A screen started or stopped showing the map. Once none shows it, what
     * came into its folder that its text never pointed at takes none of its
     * room ([IncomingMedia.forget]).
     */
    fun showing(id: String, shown: Boolean) {
        val left = screens.compute(id) { _, count -> ((count ?: 0) + if (shown) 1 else -1).takeIf { it > 0 } }

        if (left == null) {
            store.incoming.forget(id)
        }
    }

    /** Maps a screen shows now, which nothing outside the screen should delete. */
    fun onScreen(): Set<String> = screens.keys.toSet()

    /**
     * A screen that closed on the map is done writing its last text, which
     * it does after it's gone: a screen that opened the map again meanwhile
     * may have read it before, and catches up with it (see [replaced]), as
     * with a change from elsewhere. One that read it after finds it the same.
     * `keptAside` when the last text found the map changed meanwhile (that
     * screen saved first, say) and went into its history: the screen showing
     * the map says so ([keptAside]), as the one that closed can't.
     */
    fun lastWritten(id: String, keptAside: Boolean = false) {
        if (screens.containsKey(id)) {
            if (keptAside) {
                keptAsideMaps.tryEmit(id)
            }

            replacedMaps.tryEmit(id)
        }
    }

    private val keptAsideMaps = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Maps a screen that closed on them couldn't write its last edit to, which went into their history instead (see [lastWritten]), by id. */
    val keptAside: SharedFlow<String> = keptAsideMaps.asSharedFlow()

    /**
     * Saves a map's text. With `base`, the text the change was made to, it's
     * only written while the map is still that text: a map that changed
     * since (another device's version, the folder's, ideas caught on the go)
     * isn't written over, and the text goes into its history instead
     * ([WriteResult.Changed]). Text without any content never replaces a map
     * that has some ([WriteResult.Refused]). The map as it was goes into its
     * history first, as `checkpoint` says: one kept every so often is kept
     * while editing, and one kept always is kept for `reason`.
     */
    suspend fun write(
        id: String,
        markdown: String,
        base: String? = null,
        checkpoint: Checkpoint = Checkpoint.Interval,
        reason: VersionReason = VersionReason.Editing,
    ): WriteResult =
        withContext(Dispatchers.IO) {
            mutex.withLock { writeLocked(id, markdown, base, checkpoint, reason) }
        }

    private fun writeLocked(
        id: String,
        markdown: String,
        base: String?,
        checkpoint: Checkpoint,
        reason: VersionReason = VersionReason.Editing,
        keepRefused: Boolean = true,
    ): WriteResult {
        val folder = folder(id)
        val file = File(folder, MAP_FILE)

        if (!file.isFile) {
            return WriteResult.Missing
        }

        if (!canWrite.value) {
            return WriteResult.Refused
        }

        // A file that can't be read is never written over: what's in it can't be kept.
        val current = try {
            file.readTextSafely()
        } catch (unreadable: IOException) {
            return WriteResult.Refused
        }

        if (current == markdown) {
            return WriteResult.Written
        }

        if (base != null && current != base) {
            if (keepRefused) {
                histories.keepAsideLocked(id, markdown)
            }

            return WriteResult.Changed
        }

        if (!MapText.hasContent(markdown) && MapText.hasContent(current)) {
            return WriteResult.Refused
        }

        try {
            if (checkpoint == Checkpoint.Always) {
                histories.keepVersionText(id, current, unless = markdown, reason = reason)
            } else if (checkpointDue(histories.versionTimes(id), clock())) {
                histories.keepVersionText(id, current, unless = markdown, reason = VersionReason.Editing)
            }
        } catch (failed: IOException) {
            // The map as it was is in its history up to the last version kept: the change itself matters more.
            Logs.warn("MapRepository", "Couldn't keep the map's version before a change", failed)
        }

        try {
            writeAtomically(file, markdown)
        } catch (failed: IOException) {
            // Storage that's full or gone: the file is as it was, and whoever wrote can say so and try again.
            Logs.warn("MapRepository", "Couldn't write the map", failed)
            return WriteResult.NotWritten
        }

        // What came in for the map that its text points at now is the map's.
        store.incoming.settle(id) { carriedNames(markdown).toSet() }
        replace(summarize(folder))
        return WriteResult.Written
    }

    /** The map's earlier versions, the newest first (see [MapHistory.versions]). */
    suspend fun versions(id: String): List<MapVersion> = histories.versions(id)

    /** The map's text as it was at `time`, if that version is still kept and can be read. */
    suspend fun readVersion(id: String, time: Long): String? = histories.readVersion(id, time)

    /** Keeps `text` in the map's history without making it the map (see [MapHistory.keepAside]). */
    suspend fun keepAside(id: String, text: String) = histories.keepAside(id, text)

    /** Now, by the clock the trash dates deletions by (see [keepAsideInTrash]). */
    fun now(): Long = clock()

    /**
     * Keeps `text` in the history map `id` took to the trash, the map
     * deleted last under that id, at [now]'s `deletedSince` or after: a save
     * that found the map gone, as it went there while it was open, so
     * restoring it brings the text back with its versions. An older map in
     * the trash that had the same id is another map, and never takes it.
     * False when it isn't in the trash, or the text couldn't be kept, which
     * the log says. Nor does one whose text went as it was deleted for good,
     * which only waits for the rest of it to go ([MapTrash.deleteForever]).
     */
    suspend fun keepAsideInTrash(id: String, text: String, deletedSince: Long): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val item = trashed.value.filter { it.id == id && it.deleted >= deletedSince }.maxByOrNull { it.deleted }

            if (item == null || !File(item.folder, MAP_FILE).isFile || !canWrite.value) {
                return@withLock false
            }

            try {
                histories.keepAsideIn(File(item.folder, TRASHED_HISTORY), text)
                true
            } catch (failed: IOException) {
                Logs.warn("MapRepository", "Couldn't keep a text in a map's history in the trash", failed)
                false
            }
        }
    }

    /** Keeps the map as it is now in its history, as an earlier version is about to replace it (see [MapHistory.keepCurrentVersion]). */
    suspend fun keepCurrentVersion(id: String, restoring: Long? = null) = histories.keepCurrentVersion(id, restoring)

    /**
     * Changes a map's text from outside its screen, as quick capture does,
     * while it's still `base` (the caller tries again otherwise): a screen
     * showing the map catches up at once, and undo brings it back. Ideas the
     * new text gives new ids (`renamed`, from the old to the new, as a new
     * name gives the center one) keep what the map's details keep about them,
     * as the text is written, and a screen showing the map follows them.
     */
    suspend fun change(id: String, markdown: String, base: String, reason: VersionReason = VersionReason.Caught, renamed: Map<String, String> = emptyMap()): WriteResult {
        val result = withContext(Dispatchers.IO) {
            mutex.withLock {
                writeLocked(id, markdown, base, Checkpoint.Always, reason, keepRefused = false).also { result ->
                    if (result == WriteResult.Written && renamed.isNotEmpty()) {
                        renameIdeasLocked(id, renamed)
                    }
                }
            }
        }

        if (result == WriteResult.Written) {
            if (renamed.isNotEmpty()) {
                renamedIdeas.tryEmit(IdeasRenamed(id, renamed))
            }

            replacedMaps.tryEmit(id)
        }

        return result
    }

    /** The map's details with ideas that got new ids following them (see [MapMetadata.withIdeasRenamed]); details that can't be read for now stay as they are. */
    private fun renameIdeasLocked(id: String, renamed: Map<String, String>) {
        val folder = folder(id)

        try {
            writeMetadata(folder, readMetadataToChange(folder).withIdeasRenamed(renamed))
            replace(summarize(folder))
        } catch (failed: IOException) {
            Logs.warn("MapRepository", "Couldn't keep the map's details as its ideas got new ids", failed)
        }
    }

    /**
     * Why a map couldn't be added, in `words`: a newer version of Orbit
     * keeps the maps, or the files couldn't be written (a full phone, say).
     */
    fun whyNotAdded(): String = words.string(if (canWrite.value) R.string.data_could_not_add_map else R.string.data_newer_version_keeps_maps)

    /** Why a map couldn't be changed from outside its screen, in `words`: a newer version of Orbit keeps the maps, or its file couldn't be read. */
    fun whyNotChanged(): String = words.string(if (canWrite.value) R.string.data_could_not_change_map else R.string.data_newer_version_keeps_maps)

    /** Why a change to a map couldn't be written, in `words`: the device's storage is full, most likely. */
    fun whyNotWritten(): String = words.string(R.string.data_could_not_save_change)

    /** Creates a map with just its root, and returns its id. */
    suspend fun create(title: String, style: String? = null, tags: List<String> = emptyList()): String =
        add(MapTags.write(MapText.newMap(title, words, style), tags))

    /**
     * Creates a map from orbit-md text: a template, an import, something
     * shared, a file new in the folder maps are kept in. `original` is
     * someone's own file the map came from (`originalName`, when it had one):
     * it's kept as it came in, for good, in the map's history, whatever Orbit
     * writes later. `pictures`
     * go in its media folder, by name; a name that isn't a picture's, a
     * video's or a sound's is left out. `path` is its presentation path. One
     * `isPrivate` is private from the moment it's there, with its `cover`.
     */
    suspend fun add(
        markdown: String,
        pinned: Boolean = false,
        sync: SyncRecord? = null,
        original: String? = null,
        pictures: Map<String, ByteArray> = emptyMap(),
        originalName: String? = null,
        path: List<String>? = null,
        isPrivate: Boolean = false,
        cover: LockedCover? = null,
    ): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                checkWritable()
                val id = newFolder(MapText.outline(markdown, words).title)

                val folder = settle(id) { staging ->
                    // A private map is private from the moment it's there, from its first file on: Home never shows it unlocked, even for a moment.
                    writeMetadata(
                        staging,
                        MapMetadata(created = clock(), pinned = pinned, uuid = UUID.randomUUID().toString(), path = path, sync = sync, isPrivate = isPrivate, cover = cover?.takeIf { isPrivate }),
                    )
                    writeNewMap(staging, markdown, pictures)
                }

                // The map is there now: an original that can't be kept, on a full disk say, doesn't undo it.
                original?.let {
                    try {
                        histories.keepOriginal(id, it, originalName)
                    } catch (failure: IOException) {
                        Logs.warn("MapRepository", "Couldn't keep a new map's original in its history", failure)
                    }
                }

                insert(summarize(folder))
                id
            }
        }

    /** A new map's text, and those of `pictures` it points at, in its `folder` ([add], [addAll]). */
    private fun writeNewMap(folder: File, markdown: String, pictures: Map<String, ByteArray>) {
        writeAtomically(File(folder, MAP_FILE), markdown)
        val files = fileNamesIn(markdown)

        pictures.filterKeys { isCarriedName(it, files) }.forEach { (name, bytes) ->
            writeAtomically(fileInside(File(folder, MEDIA_DIRECTORY), name, words), bytes)
        }
    }

    /**
     * Creates several maps from one file, as a workbook's sheets make, all or
     * none: each made as [add] makes one, from its text and the pictures,
     * videos and sounds it came with, by name. They show up together once
     * every one is whole ([MapStore.settleAll]); whatever stops it leaves none
     * of them. Returns their ids, in order.
     */
    suspend fun addAll(maps: List<Pair<String, Map<String, ByteArray>>>): List<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                checkWritable()
                val taken = store.takenIds()
                val ids = maps.map { (markdown, _) -> MapText.folderName(MapText.outline(markdown, words).title, taken).also { taken += it } }

                val folders = store.settleAll(ids) { index, staging ->
                    val (markdown, pictures) = maps[index]
                    writeMetadata(staging, MapMetadata(created = clock(), uuid = UUID.randomUUID().toString()))
                    writeNewMap(staging, markdown, pictures)
                }

                folders.forEach { insert(summarize(it)) }
                ids
            }
        }

    /**
     * A copy of a map, pictures included, with `markdown` as its text (the
     * map's, named as a copy: see [MapEdits.duplicate]) and an identity of
     * its own. A private map's copy is private too, and its cover says
     * "(copy)" as well, so the two tell apart while they're locked.
     */
    suspend fun duplicate(id: String, markdown: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkWritable()
            val source = folder(id)
            val copyId = newFolder(MapText.outline(markdown, words).title)
            val original = readMetadata(source)
            val cover = original.cover?.takeIf { original.isPrivate }?.let { coverOfCopy(it, R.string.data_copy_title) }

            val copy = settle(copyId) { staging ->
                // Private, when it is, from its first file on.
                writeMetadata(staging, MapMetadata(created = clock(), uuid = UUID.randomUUID().toString(), isPrivate = original.isPrivate, cover = cover))
                File(source, MEDIA_DIRECTORY).takeIf { it.isDirectory }?.copyRecursively(File(staging, MEDIA_DIRECTORY))
                writeAtomically(File(staging, MAP_FILE), markdown)
            }

            insert(summarize(copy))
            copyId
        }
    }

    /** Moves a map to the trash (see [MapTrash.delete]). */
    suspend fun delete(id: String, byFolder: Boolean = false): TrashedMap? = trashing.delete(id, byFolder)

    /** The text a deleted map had, as the trash keeps it, for its deletion `record` (see [MapTrash.deletedText]). */
    suspend fun deletedText(record: SyncRecord): String? = trashing.deletedText(record)

    /** Brings a map back from the trash (see [MapTrash.restore]); the id it's back under, or null when it's gone. */
    suspend fun restore(item: TrashedMap): String? = trashing.restore(item)

    /** Deletes a map in the trash for good, and says whether it's gone: files that don't all delete keep it there (see [MapTrash.deleteForever]). */
    suspend fun deleteForever(item: TrashedMap): Boolean = trashing.deleteForever(item)

    /** Deletes every map in the trash for good but those `keeping` keeps, and says how many stayed, their files not all deleting (see [MapTrash.emptyTrash]). */
    suspend fun emptyTrash(keeping: (TrashedMap) -> Boolean = { false }): Int = trashing.emptyTrash(keeping)

    /** The note saying which maps the folder sent to the trash has been read, but for those `keeping` keeps (see [MapTrash.seeFolderRemovals]). */
    suspend fun seeFolderRemovals(keeping: (TrashedMap) -> Boolean = { false }) = trashing.seeFolderRemovals(keeping)

    // The folder maps are kept in (see FolderSync)

    /** Files of deleted maps, still to delete from the folder. */
    suspend fun folderDeletions(): List<SyncRecord> = withContext(Dispatchers.IO) { mutex.withLock { readTombstones() } }

    /** A deleted map's file is gone from the folder, or stays there on purpose. */
    suspend fun forgetFolderDeletion(record: SyncRecord) = withContext(Dispatchers.IO) {
        mutex.withLock { writeTombstones(readTombstones() - record) }
    }

    /** A sync first found a deleted map's file gone from the folder, `since` then: a later one agreeing forgets its deletion (see [planSync]). */
    suspend fun setFolderDeletionMissing(record: SyncRecord, since: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readTombstones()

            if (record in records) {
                writeTombstones(records.map { if (it == record) it.copy(missingSince = since) else it })
            }
        }
    }

    /** A deleted private map's file the sync just deleted from the folder: its deletion stays a while, with no text to tell it by (see [SyncRecord.gone]). */
    suspend fun setFolderDeletionGone(record: SyncRecord, gone: SyncRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readTombstones()

            if (record in records) {
                writeTombstones(records.map { if (it == record) gone else it })
            }
        }
    }

    suspend fun setSyncRecord(id: String, record: SyncRecord?) = updateMetadata(id) { it.copy(sync = record) }

    /**
     * The map's text from its file in the folder, changed there: it replaces
     * the map's, which goes into its history, and a screen showing the map
     * catches up. Only while the map is still the text the folder was
     * compared with (`expected`, a [digest]): one edited since is left as it
     * is, to compare again; and never text without content over a map with
     * some. Returns whether the map took the text.
     */
    suspend fun replaceFromFolder(id: String, markdown: String, record: SyncRecord, expected: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)
            val current = File(folder, MAP_FILE).takeIf { it.isFile }?.readTextSafely()

            if (!canWrite.value || current == null || digest(current) != expected || (!MapText.hasContent(markdown) && MapText.hasContent(current))) {
                return@withLock false
            }

            // Details that can't be read now leave the map as it is: the next sync takes the folder's text.
            val metadata = readMetadataToChange(folder)
            histories.keepVersionText(id, current, unless = markdown, reason = VersionReason.FromFolder)
            writeAtomically(File(folder, MAP_FILE), markdown)
            writeMetadata(folder, metadata.copy(sync = record))
            replace(summarize(folder))
            replacedMaps.tryEmit(id)
            true
        }
    }

    /**
     * Gives the map's picture, video, sound or file `name` the name
     * `renamed`, in its media and in its text, for a name the folder maps are
     * kept in holds another file under (see [FolderSync]): a screen showing
     * the map catches up. Returns the map's new text, or null when the map
     * isn't here or its text isn't `expected` anymore, or when its media has
     * another file by `renamed` already, which the map's text never comes to
     * point at in this one's place. The file stays under its old name too,
     * for earlier versions, until nothing points at it.
     */
    suspend fun renameMedia(id: String, name: String, renamed: String, expected: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)
            val media = File(folder, MEDIA_DIRECTORY)
            val source = File(media, name)
            val current = File(folder, MAP_FILE).takeIf { it.isFile }?.readTextSafely()

            val files = current?.let(::fileNamesIn).orEmpty()

            // An idea's file keeps being one under its new name, of any kind; a picture, video or sound keeps its kind.
            if (!canWrite.value || current != expected || !isCarriedName(name, files) || !(isMediaName(renamed) || (name in files && MediaNames.NAME.matches(renamed))) || !source.isFile) {
                return@withLock null
            }

            val target = File(media, renamed)

            // The new name is held while the copy is made, so a file coming in meanwhile, attached or pasted, gets one of its
            // own, and the copy never goes over one that took it.
            val copied = store.incoming.names(id).use { holding ->
                when {
                    holding.hold(media, renamed) -> try {
                        writeNew(target) { output -> source.inputStream().use { it.copyTo(output) } }
                        true
                    } catch (taken: FileAlreadyExistsException) {
                        false
                    }
                    // Another of its files, or one on its way in, or one kept for its earlier versions: an earlier rename's copy
                    // holds the same bytes.
                    else -> sameBytes(source, target)
                }
            }

            if (!copied) {
                return@withLock null
            }

            val updated = MediaNames.renamed(current, name, renamed)
            writeAtomically(File(folder, MAP_FILE), updated)
            replace(summarize(folder))
            replacedMaps.tryEmit(id)
            updated
        }
    }

    /** A picture, video, sound or idea's file in the map's media folder, if it's there: one of a kind it keeps, or a file its text names (`files`). */
    fun picture(id: String, name: String, files: Collection<String> = emptySet()): File? =
        File(File(folder(id), MEDIA_DIRECTORY), name).takeIf { isCarriedName(name, files) && it.isFile }

    /**
     * Whether the map's media folder has anything by `name`, of whatever
     * kind: one its text points at now, or one kept for its earlier
     * versions, or one on its way in that holds the name ([IncomingMedia.Names]).
     */
    fun hasMediaNamed(id: String, name: String): Boolean = File(File(folder(id), MEDIA_DIRECTORY), name).exists() || store.incoming.holds(id, name)

    /**
     * Puts a picture or video the map points at into its media folder, unless
     * it's there already. `copy` writes it out, and says false when there's
     * nothing to copy after all; it's written whole or not at all. One that
     * comes to more than `room` bytes is refused with a
     * [MediaRefusedException], leaving nothing behind. One of `size` bytes
     * (when it's known) that this device hasn't room for, and some to spare,
     * isn't started: [NoRoomForMedia]. Once it's there, the map's picture on
     * Home is drawn again with it ([MapStore.mediaArrived]). Returns its size
     * in the map, 0 when it isn't there.
     */
    suspend fun addMediaFile(id: String, name: String, room: Long = Long.MAX_VALUE, size: Long = -1, files: Collection<String> = emptySet(), copy: (OutputStream) -> Boolean): Long = withContext(Dispatchers.IO) {
        // An idea's file, of any kind, comes in while the map's text names it (`files`, else what its text says now).
        val named = if (isMediaName(name) || name in files) files else runCatching { fileNamesIn(File(folder(id), MAP_FILE).readTextSafely()) }.getOrDefault(emptyList())

        if (!isCarriedName(name, named) || !canWrite.value) {
            return@withContext 0L
        }

        val media = File(folder(id), MEDIA_DIRECTORY)
        val file = File(media, name)

        // A map moved to the trash meanwhile isn't brought back as a media folder alone.
        if (!File(folder(id), MAP_FILE).isFile) {
            return@withContext 0L
        }

        // Its name is held while it's copied, so a file attached or pasted meanwhile gets one of its own; one already there, or on
        // its way in under that name, stays, and the next sync looks again.
        store.incoming.names(id).use { holding ->
            if (holding.hold(media, name)) {
                // A big file isn't started where it can't be finished: the maps and their saves need room too.
                if (size > 0 && this@MapRepository.room(folder(id)) < size + ROOM_LEFT_FREE) {
                    throw NoRoomForMedia(name)
                }

                try {
                    writeNew(file) { output ->
                        if (!copy(CappedOutput(output, room, words))) {
                            throw NothingToCopy()
                        }
                    }

                    // Here now, it's in the map's picture on Home, drawn again with it.
                    mutex.withLock { store.mediaArrived(id, setOf(name)) }
                } catch (missing: NothingToCopy) {
                    // The file went from where it was coming from: the next sync tries again.
                } catch (taken: FileAlreadyExistsException) {
                    // Put there meanwhile by something other than Orbit's own copies: it stays.
                }
            }
        }

        if (file.isFile) file.length() else 0L
    }

    private fun mediaFiles(folder: File, markdown: String): List<File> = store.mediaFiles(folder, markdown)

    /** The map's own files that go with it to other people: those it names only in code stay here (see [sharedNames]). */
    private fun sharedFiles(folder: File, markdown: String): List<File> = store.sharedFiles(folder, markdown)

    /** How many bytes the pictures, videos and sounds the map's text, or `markdown`, points at come to (see [MapStore.mediaSize]). */
    fun mediaSize(id: String, markdown: String? = null): Long = store.mediaSize(id, markdown)

    /** What the map's pictures, videos and sounds come to now, read off the main thread. */
    suspend fun mediaSizeNow(id: String): Long = withContext(Dispatchers.IO) { store.mediaSize(id) }

    /** The sizes of the pictures, videos and sounds the map's text points at that are here: reads its text, so off the main thread. */
    fun mediaFileSizes(id: String): List<Long> {
        val folder = folder(id)
        return mediaFiles(folder, runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")).map { it.length() }
    }

    /**
     * Whether the map's zip is past what Orbit 0.4.0, and every release
     * before it, takes in: one that unpacks to more than 96 MiB, or holds a
     * file of more than 64 MiB, which they refuse. Sharing one says to update
     * Orbit wherever it's opened.
     */
    suspend fun pastOlderZips(id: String): Boolean = withContext(Dispatchers.IO) {
        val sizes = mediaFileSizes(id)
        File(folder(id), MAP_FILE).length() + sizes.sum() > BUNDLE_SIZE_UP_TO_0_4_0 || sizes.any { it > ZIP_ENTRY_LIMIT_UP_TO_0_4_0 }
    }

    suspend fun setPinned(id: String, pinned: Boolean) = updateMetadata(id) { it.copy(pinned = pinned) }

    /** Makes a map private, or not: see [MapMetadata.isPrivate]. */
    suspend fun setPrivate(id: String, private: Boolean) = updateMetadata(id) { it.copy(isPrivate = private) }

    /** What the map shows instead of its name while it's locked; null, or an empty cover, takes it away. */
    suspend fun setCover(id: String, cover: LockedCover?) = updateMetadata(id) { it.copy(cover = cover?.takeUnless(LockedCover::isEmpty)) }

    /**
     * The cover of a private map's copy that stays private (a duplicate, or
     * the folder's version kept beside it): its name as the copy's title says
     * it, with `suffix`, a string such as "%1$s (copy)", so the two tell
     * apart while they're locked. A cover with only an emoji stays as it is.
     */
    internal fun coverOfCopy(cover: LockedCover, suffix: Int): LockedCover =
        cover.name?.takeIf { it.isNotBlank() }?.let { name -> cover.copy(name = words.string(suffix, name)) } ?: cover

    suspend fun markOpened(id: String) = updateMetadata(id) { it.copy(opened = clock()) }

    /** Remembers where the reader was, to open the map there next time. */
    suspend fun savePlace(id: String, focus: String?, view: String?) = updateMetadata(id) { it.copy(focus = focus, view = view) }

    /** The ideas to present, in order; an empty path presents them all in reading order. */
    suspend fun savePath(id: String, path: List<String>) = updateMetadata(id) { it.copy(path = path.ifEmpty { null }) }

    /** Seconds the last rehearsal spent on each idea. */
    suspend fun saveRehearsal(id: String, seconds: Map<String, Int>) = updateMetadata(id) { it.copy(rehearsal = seconds) }

    /**
     * This device's answers to the map's cards in study mode, by idea id, all
     * of them: in its details, never its text. What changes them as they're
     * studied goes through [changeStudy], which never writes back a copy.
     */
    suspend fun saveStudy(id: String, cards: Map<String, StudyCard>) = updateMetadata(id) { it.copy(study = cards.ifEmpty { null }) }

    /**
     * Makes a change to this device's answers to the map's cards (an answer,
     * one taken back, the progress of ideas gone from the map), against them
     * as its details have them now, under the lock every change to the maps
     * is made under: whatever changed them since the change was made stays.
     * One that can't be written, as on a full storage or while a newer Orbit
     * keeps the maps, throws an [IOException], to be tried again
     * ([com.hyuchia.orbit.study.StudyWriting]); a map that's gone takes none.
     */
    suspend fun changeStudy(id: String, change: StudyChange) = updateMetadata(id, strict = true) { metadata ->
        val study = change.applyTo(metadata.study.orEmpty(), metadata.studyElsewhere.orEmpty())
        if (study == metadata.study.orEmpty()) metadata else metadata.copy(study = study.ifEmpty { null })
    }

    /** The other devices' answers to the map's cards, by device, as the folder the maps are kept in says (see [FolderStudy]). */
    suspend fun saveStudyElsewhere(id: String, elsewhere: Map<String, Map<String, StudyCard>>) =
        updateMetadata(id) { it.copy(studyElsewhere = elsewhere.ifEmpty { null }) }

    /**
     * The maps stop being kept in a folder: each keeps how its cards are
     * going, the other devices' answers among this one's, so leaving the
     * folder loses nothing studied.
     */
    suspend fun keepStudyHere() {
        for (map in summaries.value.orEmpty().filter { it.metadata.studyElsewhere != null }) {
            updateMetadata(map.id) { it.copy(study = it.studyProgress.ifEmpty { null }, studyElsewhere = null) }
        }
    }

    /**
     * The other devices' answers to the map's cards join this device's own,
     * against its details as they are now, as the map comes to another folder:
     * what the devices in the one it was kept in studied stays the map's.
     * Gives this device's answers then; null when the map's details can't be
     * changed (it's gone, or a newer Orbit keeps the maps).
     */
    suspend fun takeStudyElsewhere(id: String): Map<String, StudyCard>? {
        var taken: Map<String, StudyCard>? = null

        updateMetadata(id) { metadata ->
            val all = metadata.studyProgress
            taken = all
            metadata.copy(study = all.ifEmpty { null })
        }

        return taken
    }

    /**
     * Which of the ideas the map's cards have answers for, here or on another
     * device, aren't cards now, given its `cards` as its page read them, whole:
     * Home's count of the cards due leaves them out ([MapMetadata.studyNotCards]).
     */
    suspend fun setStudyCards(id: String, cards: Set<String>) = updateMetadata(id) { metadata ->
        val aside = notCards(metadata.studyProgress, cards).sorted().ifEmpty { null }
        if (aside == metadata.studyNotCards) metadata else metadata.copy(studyNotCards = aside)
    }

    /**
     * An idea's new id, after new text gave it one: the path, the rehearsal
     * and the study keep the idea. Its card goes on as its latest answer on
     * any device left it, kept as this device's own, over what an idea that
     * had the new id before left: the other devices see only the new text,
     * and find the card under the new id in this device's record in the
     * folder. Their own answers stay as their records say.
     */
    suspend fun renameIdea(id: String, from: String, to: String) = updateMetadata(id) { it.withIdeasRenamed(mapOf(from to to)) }

    /** Copies a picture into the map's folder (see [MapMediaIntake.addPicture]). */
    suspend fun addPicture(id: String, uri: Uri, resolver: ContentResolver): AddedPicture = intake.addPicture(id, uri, resolver)

    /** Copies an iPhone's live photo into the map's folder: its still and its movie (see [MapMediaIntake.addLivePhoto]). */
    suspend fun addLivePhoto(id: String, still: Uri, movie: Uri, resolver: ContentResolver): AddedPicture = intake.addLivePhoto(id, still, movie, resolver)

    /** Gives the map's picture `picture` its live photo's motion (see [MapMediaIntake.addMotion]). */
    suspend fun addMotion(id: String, picture: String, movie: Uri, resolver: ContentResolver): AddedMotion = intake.addMotion(id, picture, movie, resolver)

    /** What an incoming picture or movie says about the live photo it may be half of (see [MapMediaIntake.liveHalf]). */
    internal suspend fun liveHalf(uri: Uri, resolver: ContentResolver, movie: Boolean): Pair<StillFacts?, LiveVideo?> = intake.liveHalf(uri, resolver, movie)

    /** Copies the pictures, videos and sounds `markdown` points to from map `from` into map `to`, and gives back the text to paste there, and what was copied (see [MapMediaIntake.copyMedia]). */
    suspend fun copyMedia(from: String, to: String, markdown: String): CopiedMedia = intake.copyMedia(from, to, markdown)

    /** Copies a video from the phone into the map's folder, with a frame of it as its poster (see [MapMediaIntake.addVideo]). */
    suspend fun addVideo(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedVideo = intake.addVideo(id, uri, resolver, onProgress)

    /** A video from the phone made smaller into the map's folder by `make`, about `estimate` bytes (see [MapMediaIntake.addMadeVideo]). */
    suspend fun addMadeVideo(id: String, estimate: Long, make: suspend (File) -> Unit): AddedVideo = intake.addMadeVideo(id, estimate, make)

    /** How big the file at `uri` is, as its app says, or as its file is when it doesn't; -1 when neither can tell. */
    suspend fun sizeOf(uri: Uri, resolver: ContentResolver): Long = withContext(Dispatchers.IO) {
        val said = fromProvider { nameAndSize(uri, resolver).second } ?: -1L

        if (said >= 0) {
            return@withContext said
        }

        try {
            fromProvider { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } } ?: -1L
        } catch (unknown: IOException) {
            -1L
        }
    }

    /** Copies a sound from the phone into the map's folder (see [MapMediaIntake.addSound]). */
    suspend fun addSound(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedSound = intake.addSound(id, uri, resolver, onProgress)

    /** Copies a file from the phone into the map's folder for one of its ideas (see [MapMediaIntake.addFile]). */
    suspend fun addFile(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedFile = intake.addFile(id, uri, resolver, onProgress)

    /** Keeps a voice memo recorded in Orbit in the map's folder (see [MapMediaIntake.addRecording]). */
    suspend fun addRecording(id: String, recording: File): String = intake.addRecording(id, recording)

    /** Keeps a sketch drawn in Orbit in the map's folder (see [MapMediaIntake.addSketch]). */
    suspend fun addSketch(id: String, svg: ByteArray): String = intake.addSketch(id, svg)

    /** Takes back what was just copied into the map's folder and never joined it (see [MapMediaIntake.takeBack]). */
    suspend fun takeBack(id: String, sources: List<String>) = intake.takeBack(id, sources)

    /**
     * What was copied into the map's folder (`media/…`) won't join it now,
     * though it may yet show up in its text, as from a change the page made
     * late: it takes none of the map's room as coming in, and its files stay,
     * for the tidying up to delete once nothing points at them
     * ([IncomingMedia.release]).
     */
    fun letGo(id: String, sources: List<String>) {
        store.incoming.release(id, sources.filter { it.startsWith("$MEDIA_DIRECTORY/") }.map { it.removePrefix("$MEDIA_DIRECTORY/") }.toSet())
    }

    /**
     * Deletes pictures in the map's folder that neither the map nor an
     * earlier version of it points at, unless they were added in the last
     * few minutes: the text that points at a new one may be on its way.
     * Only files with names Orbit gives go: one put there some other way
     * stays, whatever points at it. The map's own text is looked at first,
     * and most often points at every file there is; only then are the
     * versions read, one at a time, so a big map's history is never all in
     * memory. A version that can't be read keeps every file, and so does a
     * history that can't be listed, or one still in the map's folder: its
     * versions may point at any of them.
     */
    suspend fun removeUnusedPictures(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canWrite.value) {
                return@withLock
            }

            val folder = folder(id)
            val markdown = File(folder, MAP_FILE).takeIf { it.isFile }?.readTextSafely() ?: return@withLock
            val recent = clock() - PICTURE_GRACE

            // Ideas' files Orbit named go too (`file-…`), of any kind: one named some other way stays, as other files do.
            val unused = File(folder, MEDIA_DIRECTORY).listFiles().orEmpty()
                .filter { file -> file.isFile && (isMediaName(file.name) || isIdeaFileName(file.name)) && file.lastModified() < recent && !pointsAtMedia(markdown, file.name) }
                .toMutableList()

            val versions = histories.versionFilesOrNull(id) ?: return@withLock

            // A history waiting in the map's folder to join the rest at the next start (one that couldn't move as the map came
            // back from the trash, or went there) keeps every file too: its versions aren't read here.
            if (File(folder, TRASHED_HISTORY).exists()) {
                return@withLock
            }

            for ((_, version) in versions) {
                if (unused.isEmpty()) {
                    return@withLock
                }

                val text = try {
                    version.readTextSafely()
                } catch (unreadable: IOException) {
                    return@withLock
                }

                unused.removeAll { pointsAtMedia(text, it.name) }
            }

            unused.forEach { it.delete() }
        }
    }

    /** What goes with the map when it's shared, sent or shown, but those `leavingOut`: see [MediaSummary]. */
    suspend fun mediaSummary(id: String, leavingOut: Set<String> = emptySet()): MediaSummary = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val text = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        val files = sharedFiles(folder, text).filter { it.name !in leavingOut }
        MediaSummary.of(files.associate { it.name to it.length() }, audio = soundNamesIn(text).toSet(), ideaFiles = fileNamesIn(text).toSet())
    }

    /** The map's own files that can't go to other people clean, which every way out leaves here unless the person chooses otherwise: see [Uncleaned]. */
    suspend fun uncleaned(id: String): Uncleaned = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val text = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        val files = sharedFiles(folder, text)
        // Each as it is now: the person's answer is about that very file.
        val stamps = files.mapNotNull { file -> OutgoingMedia.uncleanable(file)?.let { file.name to it } }.toMap()
        Uncleaned.of(stamps.keys, all = files.map { it.name }, sounds = soundNamesIn(text).toSet(), files = fileNamesIn(text).toSet(), labels = fileLabelsIn(text), stamps = stamps)
    }

    /**
     * How many of the map's pictures stay out of a web page of it, past what
     * a page carries of them ([picturesFitting]): counted as the rest of what
     * goes is ([MediaSummary]), a video's poster with its video.
     */
    suspend fun picturesOutOfPage(id: String): Int = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val text = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        val fitting = picturesFitting(text, folder)
        val sizes = sharedFiles(folder, text).associate { it.name to it.length() }
        val sounds = soundNamesIn(text).toSet()
        val files = fileNamesIn(text).toSet()
        val inPage = sizes.filterKeys { name -> isVideoName(name) || isSoundName(name) || name in sounds || name in fitting }
        MediaSummary.of(sizes, audio = sounds, ideaFiles = files).pictures - MediaSummary.of(inPage, audio = sounds, ideaFiles = files).pictures
    }

    /**
     * The map's sounds that go inside a web page of it, by name: those that
     * fit its room, the rest stay on this device ([soundsFitting]), with
     * those of `asTheyAre` that can't go clean.
     */
    suspend fun soundsInPage(id: String, asTheyAre: AsTheyAreFiles = AsTheyAreFiles.NONE): Set<String> = withContext(Dispatchers.IO) {
        val folder = folder(id)
        soundsFitting(runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault(""), folder, asTheyAre = asTheyAre.lasting()).mapTo(LinkedHashSet()) { it.file.name }
    }

    /**
     * The map's live photos' motions that go inside a web page of it, by
     * name: those that fit its room, the rest stay on this device
     * ([motionsFitting]), with those of `asTheyAre` that can't go clean.
     */
    suspend fun motionsInPage(id: String, asTheyAre: AsTheyAreFiles = AsTheyAreFiles.NONE): Set<String> = withContext(Dispatchers.IO) {
        val folder = folder(id)
        motionsFitting(runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault(""), folder, asTheyAre = asTheyAre.lasting()).mapTo(LinkedHashSet()) { it.name }
    }

    /** Writes the map as a bundle for another device, in a zip [readBundle] reads back (see [MapBundles.writeZip]). */
    suspend fun writeZip(id: String, output: OutputStream, asTheyAre: AsTheyAreFiles = AsTheyAreFiles.NONE, room: (Long) -> Unit = {}) = bundles.writeZip(id, output, asTheyAre, room)

    /** The map's lasting identity, given one now if it's older than identities (see [MapBundles.identity]). */
    suspend fun identity(id: String): String = bundles.identity(id)

    /** The map with this identity, if it's here. Reads the disk when the list of maps doesn't know it: call it off the main thread (see [MapBundles.findByIdentity]). */
    fun findByIdentity(uuid: String): String? = bundles.findByIdentity(uuid)

    /** A bundle read from a zip, or null when there's no map in it, unpacked while `room` says there's room (see [MapBundles.readBundle]). */
    suspend fun readBundle(input: InputStream, withPictures: Boolean = true, room: (() -> Long)? = null): MapBundle? =
        if (room != null) bundles.readBundle(input, withPictures, room) else bundles.readBundle(input, withPictures)

    /** Lets go of a bundle that isn't taken in: the files it was unpacked to go now (see [MapBundles.discard]). */
    fun discard(bundle: MapBundle) = bundles.discard(bundle)

    /** Why a map's zip of `size` bytes can't be unpacked here, or null when there's room for it (see [MapBundles.noRoomForBundle]). */
    fun noRoomForBundle(size: Long): String? = bundles.noRoomForBundle(size)

    /** Whether a zip holds maps a folder each, as [exportAll] writes them, rather than one map's bundle (see [MapBundles.holdsManyMaps]). */
    suspend fun holdsManyMaps(input: InputStream): Boolean = bundles.holdsManyMaps(input)

    /**
     * Takes in a map from another device, as a map of its own or as `replacing`'s new version, an answered arrival's
     * once only, over the map's text it was answered for (`base`), or as a map of identity `copy`, or of its own, which
     * `adding` hears first (see [MapBundles.receive]).
     */
    suspend fun receive(
        bundle: MapBundle,
        replacing: String? = null,
        reason: VersionReason = VersionReason.FromFile(null),
        base: String? = null,
        copy: String? = null,
        adding: (String) -> Unit = {},
    ): String = bundles.receive(bundle, replacing, reason, base, copy, adding)

    // Every map at once: a way out of the app without Google's backups

    /** Writes every map into one zip, and says how many it holds (see [MapBundles.exportAll]). */
    suspend fun exportAll(output: OutputStream): Int = bundles.exportAll(output)

    /** Throws an [UnreadableMapFileException] for a map with a file [exportAll] couldn't read, before anything is written (see [MapBundles.checkExportAll]). */
    suspend fun checkExportAll() = bundles.checkExportAll()

    /** Adds the maps in a zip [exportAll] wrote, leaving alone the ones here already, a `locked` private map found by its identity alone (see [MapBundles.importAll]). */
    suspend fun importAll(input: InputStream, locked: (MapSummary) -> Boolean = { false }): ImportedMaps = bundles.importAll(input, locked)

    // Folders

    /** Folders a crash left half made, and zips it left unpacked. */
    private fun clearLeftovers() {
        directory.listFiles { file -> file.isDirectory && file.name.startsWith(".new-") }.orEmpty().forEach { it.deleteRecursively() }
        val stale = clock() - UNPACKED_LEFTOVER
        // One waiting for an answer is kept for the screen that asks it again, for a while, and one answered until it's in (see
        // WaitingArrivals).
        val forgotten = clock() - WAITING_LEFTOVER
        unpacking.listFiles { file -> !waitingArrivals.isTakingIn(file.name) && file.lastModified() < if (File(file, WAITING_ARRIVAL).isFile) forgotten else stale }
            .orEmpty()
            .filterNot { waitingArrivals.isAnswered(it.name) }
            .forEach { it.deleteRecursively() }
        // Copies of pictures and movies read as they came in: one left means Orbit stopped while reading it.
        File(directory.parentFile, INCOMING_DIRECTORY).listFiles { file -> file.lastModified() < stale }.orEmpty().forEach { it.deleteRecursively() }

        // Writes that stopped partway leave their partial files: each write names its own, so they don't go by themselves.
        listOf(directory, historyDirectory).forEach { root ->
            root.walkTopDown().filter { it.isFile && isPartial(it) && it.lastModified() < stale }.forEach { it.delete() }
        }
    }

    // Details, through the store (see [MapStore])

    private fun checkWritable() = store.checkWritable()

    private fun settle(id: String, fill: (File) -> Unit): File = store.settle(id, fill)

    private suspend fun updateMetadata(id: String, strict: Boolean = false, change: (MapMetadata) -> MapMetadata) = store.updateMetadata(id, strict, change)

    private fun summarize(folder: File): MapSummary = store.summarize(folder)

    private fun readMetadata(folder: File): MapMetadata = store.readMetadata(folder)

    private fun readMetadataToChange(folder: File): MapMetadata = store.readMetadataToChange(folder)

    private fun writeMetadata(folder: File, metadata: MapMetadata) = store.writeMetadata(folder, metadata)

    private fun insert(summary: MapSummary) = store.insert(summary)

    private fun replace(summary: MapSummary) = store.replace(summary)

    private fun newFolder(title: String): String = store.newFolder(title)

    private fun readTombstones(): List<SyncRecord> = store.readTombstones()

    private fun writeTombstones(records: List<SyncRecord>) = store.writeTombstones(records)

    /** The welcome map, pinned, then the samples, oldest first so they list in order. */
    private fun installStarterMaps() {
        val samples = starterMaps.samples()
        val now = clock()

        samples.forEachIndexed { index, markdown ->
            install(StarterMap(markdown), now - (samples.size - index + 1) * 60_000L, pinned = false)
        }

        starterMaps.welcome()?.let { install(it, now, pinned = true) }
    }

    private fun install(map: StarterMap, time: Long, pinned: Boolean) {
        val id = newFolder(MapText.outline(map.markdown, words).title)

        val folder = settle(id) { staging ->
            writeMetadata(staging, map.metadata.copy(created = time, pinned = pinned))
            writeAtomically(File(staging, MAP_FILE), map.markdown)

            map.media.filterKeys(::isMediaName).forEach { (name, bytes) ->
                writeAtomically(fileInside(File(staging, MEDIA_DIRECTORY), name, words), bytes)
            }
        }

        File(folder, MAP_FILE).setLastModified(time)
    }
}

/**
 * Moves a folder, or copies it over when it can't simply be renamed, each
 * file whole or not at all ([writeAtomically]). Files the other side has
 * already stay as they are there when they hold what this side's do, or
 * more; one that holds only their first bytes, a copy a crash cut short,
 * is finished from this side's. A copy that fails, or a file there that
 * holds something else, leaves the folder where it was, whole: nothing of
 * it is deleted unless every file is there. Says whether it moved.
 */
internal fun move(from: File, to: File): Boolean {
    to.parentFile?.mkdirs()

    if (!to.exists() && from.renameTo(to)) {
        return true
    }

    var whole = true

    // A file that can't be read or written, or a folder that can't be listed, is thrown or reaches the callback: neither deletes anything.
    try {
        from.walkTopDown().onFail { _, _ -> whole = false }.forEach { file ->
            val there = File(to, file.relativeTo(from).path)

            val copy = when {
                file.isDirectory -> null
                there.exists() -> copyThere(file, there)
                else -> CopyThere.Missing
            }

            when (copy) {
                null -> if (!there.isDirectory && !there.mkdirs()) {
                    whole = false
                }
                CopyThere.Missing, CopyThere.CutShort -> writeAtomically(there) { output -> file.inputStream().use { it.copyTo(output) } }
                CopyThere.Other -> whole = false
                CopyThere.Same, CopyThere.More -> Unit
            }
        }
    } catch (failed: IOException) {
        whole = false
    }

    if (whole) {
        from.deleteRecursively()
    }

    return whole
}

/** What's where [move] copies a file to: nothing yet, or a file holding the same bytes, only their first ones, them and more, or something else. */
private enum class CopyThere { Missing, Same, CutShort, More, Other }

/** What `there` holds of `file` (see [CopyThere]). */
private fun copyThere(file: File, there: File): CopyThere {
    val here = ByteArray(DEFAULT_BUFFER_SIZE)
    val copy = ByteArray(DEFAULT_BUFFER_SIZE)

    file.inputStream().use { mine ->
        there.inputStream().use { theirs ->
            while (true) {
                val read = mine.readFully(here)
                val copied = theirs.readFully(copy)

                if ((0 until minOf(read, copied)).any { here[it] != copy[it] }) {
                    return CopyThere.Other
                }

                when {
                    copied < read -> return CopyThere.CutShort
                    read < copied -> return CopyThere.More
                    read < here.size -> return CopyThere.Same
                }
            }
        }
    }
}

/** Reads into `buffer` until it's full or the stream ends; says how many bytes it read. */
private fun InputStream.readFully(buffer: ByteArray): Int {
    var total = 0

    while (total < buffer.size) {
        val read = read(buffer, total, buffer.size - total)

        if (read < 0) {
            break
        }

        total += read
    }

    return total
}

/**
 * Whether `text` points at the file `name` in its map's media folder, as
 * it's named or with its name's letters percent-encoded, as a link may write
 * them: a file anything points at is never taken for unused.
 */
internal fun pointsAtMedia(text: String, name: String): Boolean {
    val texts = if ('%' in text) listOf(text, percentDecoded(text)) else listOf(text)
    val names = if ('%' in name) listOf(name, percentDecoded(name)) else listOf(name)
    return texts.any { text -> names.any { name -> "$MEDIA_DIRECTORY/$name" in text } }
}

private val PERCENT_ESCAPES = Regex("(?:%[0-9A-Fa-f]{2})+")

/** `text` with each run of percent escapes as the letters it stands for; a run that isn't UTF-8 stays as it's written. */
internal fun percentDecoded(text: String): String = PERCENT_ESCAPES.replace(text) { run ->
    val bytes = run.value.chunked(3).map { it.substring(1).toInt(16).toByte() }.toByteArray()
    runCatching { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }.getOrDefault(run.value)
}

internal fun digest(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
