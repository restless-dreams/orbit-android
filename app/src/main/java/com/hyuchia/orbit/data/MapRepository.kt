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
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The map's text in its folder. */
const val MAP_FILE = "map.md"

/** A picture added this recently is kept even if nothing points at it yet: the text pointing at it may be on its way. */
private const val PICTURE_GRACE = 10 * 60 * 1000L

/** Unpacked zips older than this were left by an import that never finished. */
private const val UNPACKED_LEFTOVER = 60 * 60 * 1000L

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
) {
    /** Zips of maps, unpacked while they're taken in. */
    private val unpacking = File(directory.parentFile, "unpacking")

    /** What this repository's parts share (see [MapStore]). */
    private val store = MapStore(directory, trashDirectory, historyDirectory, tombstones, words, clock, installed, clockTrusted)

    private val trashing = MapTrash(store)
    private val histories = MapHistory(store)
    private val intake = MapMediaIntake(store, room)
    private val bundles = MapBundles(store, histories, unpacking)

    private val mutex: Mutex get() = store.mutex
    private val summaries: MutableStateFlow<List<MapSummary>?> get() = store.summaries
    private val trashed: MutableStateFlow<List<TrashedMap>> get() = store.trashed
    private val canWrite: MutableStateFlow<Boolean> get() = store.canWrite
    private val replacedMaps: MutableSharedFlow<String> get() = store.replacedMaps

    private val layout = StorageLayout(directory, historyDirectory, backups, clock)

    /** How many screens show each map, by id. */
    private val screens = ConcurrentHashMap<String, Int>()

    /** Maps replaced by a newer version from elsewhere (another device, the folder, a quick capture), by id, so a screen showing one can catch up. */
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

    /** A screen started or stopped showing the map. */
    fun showing(id: String, shown: Boolean) {
        screens.compute(id) { _, count -> ((count ?: 0) + if (shown) 1 else -1).takeIf { it > 0 } }
    }

    /** Maps a screen shows now, which nothing outside the screen should delete. */
    fun onScreen(): Set<String> = screens.keys.toSet()

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

        replace(summarize(folder))
        return WriteResult.Written
    }

    /** The map's earlier versions, the newest first (see [MapHistory.versions]). */
    suspend fun versions(id: String): List<MapVersion> = histories.versions(id)

    /** The map's text as it was at `time`, if that version is still kept and can be read. */
    suspend fun readVersion(id: String, time: Long): String? = histories.readVersion(id, time)

    /** Keeps `text` in the map's history without making it the map (see [MapHistory.keepAside]). */
    suspend fun keepAside(id: String, text: String) = histories.keepAside(id, text)

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
                    writeAtomically(File(staging, MAP_FILE), markdown)

                    val files = fileNamesIn(markdown)

                    pictures.filterKeys { isCarriedName(it, files) }.forEach { (name, bytes) ->
                        writeAtomically(fileInside(File(staging, MEDIA_DIRECTORY), name, words), bytes)
                    }
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
     * isn't here or its text isn't `expected` anymore. The file stays under
     * its old name too, for earlier versions, until nothing points at it.
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

            if (!target.exists()) {
                writeAtomically(target) { output -> source.inputStream().use { it.copyTo(output) } }
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

        val file = File(File(folder(id), MEDIA_DIRECTORY), name)

        // A map moved to the trash meanwhile isn't brought back as a media folder alone.
        if (!File(folder(id), MAP_FILE).isFile) {
            return@withContext 0L
        }

        if (!file.exists()) {
            // A big file isn't started where it can't be finished: the maps and their saves need room too.
            if (size > 0 && this@MapRepository.room(folder(id)) < size + ROOM_LEFT_FREE) {
                throw NoRoomForMedia(name)
            }

            try {
                writeAtomically(file) { output ->
                    if (!copy(CappedOutput(output, room, words))) {
                        throw NothingToCopy()
                    }
                }

                // Here now, it's in the map's picture on Home, drawn again with it.
                mutex.withLock { store.mediaArrived(id, setOf(name)) }
            } catch (missing: NothingToCopy) {
                // The file went from where it was coming from: the next sync tries again.
            }
        }

        if (file.isFile) file.length() else 0L
    }

    private fun mediaFiles(folder: File, markdown: String): List<File> = store.mediaFiles(folder, markdown)

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
     */
    suspend fun changeStudy(id: String, change: StudyChange) = updateMetadata(id) { metadata ->
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

    /** Copies the pictures, videos and sounds `markdown` points to from map `from` into map `to`, and gives back the text to paste there (see [MapMediaIntake.copyMedia]). */
    suspend fun copyMedia(from: String, to: String, markdown: String): String = intake.copyMedia(from, to, markdown)

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

    /**
     * Deletes pictures in the map's folder that neither the map nor an
     * earlier version of it points at, unless they were added in the last
     * few minutes: the text that points at a new one may be on its way.
     * Only files with names Orbit gives go: one put there some other way
     * stays, whatever points at it. The map's own text is looked at first,
     * and most often points at every file there is; only then are the
     * versions read, one at a time, so a big map's history is never all in
     * memory. A version that can't be read keeps every file, and so does a
     * history that can't be listed: its versions may point at any of them.
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
        val files = mediaFiles(folder, text).filter { it.name !in leavingOut }
        MediaSummary.of(files.associate { it.name to it.length() }, audio = soundNamesIn(text).toSet(), ideaFiles = fileNamesIn(text).toSet())
    }

    /** The map's own files that can't go to other people clean, which every way out leaves here unless the person chooses otherwise: see [Uncleaned]. */
    suspend fun uncleaned(id: String): Uncleaned = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val text = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        val files = mediaFiles(folder, text)
        Uncleaned.of(files.filter { OutgoingMedia.of(it) == null }.map { it.name }, all = files.map { it.name }, sounds = soundNamesIn(text).toSet(), files = fileNamesIn(text).toSet(), labels = fileLabelsIn(text))
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
        val sizes = mediaFiles(folder, text).associate { it.name to it.length() }
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
    suspend fun soundsInPage(id: String, asTheyAre: Set<String> = emptySet()): Set<String> = withContext(Dispatchers.IO) {
        val folder = folder(id)
        soundsFitting(runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault(""), folder, asTheyAre = asTheyAre).mapTo(LinkedHashSet()) { it.file.name }
    }

    /**
     * The map's live photos' motions that go inside a web page of it, by
     * name: those that fit its room, the rest stay on this device
     * ([motionsFitting]), with those of `asTheyAre` that can't go clean.
     */
    suspend fun motionsInPage(id: String, asTheyAre: Set<String> = emptySet()): Set<String> = withContext(Dispatchers.IO) {
        val folder = folder(id)
        motionsFitting(runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault(""), folder, asTheyAre = asTheyAre).mapTo(LinkedHashSet()) { it.name }
    }

    /** Writes the map as a bundle for another device, in a zip [readBundle] reads back (see [MapBundles.writeZip]). */
    suspend fun writeZip(id: String, output: OutputStream, asTheyAre: Set<String> = emptySet(), room: (Long) -> Unit = {}) = bundles.writeZip(id, output, asTheyAre, room)

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

    /** Takes in a map from another device, as a map of its own or as `replacing`'s new version (see [MapBundles.receive]). */
    suspend fun receive(bundle: MapBundle, replacing: String? = null, reason: VersionReason = VersionReason.FromFile(null)): String = bundles.receive(bundle, replacing, reason)

    // Every map at once: a way out of the app without Google's backups

    /** Writes every map into one zip, and says how many it holds (see [MapBundles.exportAll]). */
    suspend fun exportAll(output: OutputStream): Int = bundles.exportAll(output)

    /** Adds the maps in a zip [exportAll] wrote, leaving alone the ones here already (see [MapBundles.importAll]). */
    suspend fun importAll(input: InputStream): ImportedMaps = bundles.importAll(input)

    // Folders

    /** Folders a crash left half made, and zips it left unpacked. */
    private fun clearLeftovers() {
        directory.listFiles { file -> file.isDirectory && file.name.startsWith(".new-") }.orEmpty().forEach { it.deleteRecursively() }
        val stale = clock() - UNPACKED_LEFTOVER
        unpacking.listFiles { file -> file.lastModified() < stale }.orEmpty().forEach { it.deleteRecursively() }
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

    private suspend fun updateMetadata(id: String, change: (MapMetadata) -> MapMetadata) = store.updateMetadata(id, change)

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
 * Moves a folder, or copies it over when it can't simply be renamed. Files
 * the other side has already stay as they are there. A copy that fails
 * leaves the folder where it was, whole: nothing of it is deleted unless
 * every file got across. Says whether it moved.
 */
internal fun move(from: File, to: File): Boolean {
    to.parentFile?.mkdirs()

    if (!to.exists() && from.renameTo(to)) {
        return true
    }

    var whole = true

    // Some failures reach the callback, and some (a file that can't be opened) are thrown: neither deletes anything.
    try {
        from.copyRecursively(to, overwrite = false) { _, failure ->
            if (failure !is FileAlreadyExistsException) {
                whole = false
            }

            OnErrorAction.SKIP
        }
    } catch (failed: IOException) {
        whole = false
    }

    if (whole) {
        from.deleteRecursively()
    }

    return whole
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
