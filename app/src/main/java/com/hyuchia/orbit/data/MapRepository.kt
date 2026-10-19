package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

/** The map's text in its folder. */
const val MAP_FILE = "map.md"

/** Pictures added to a map live next to it, and the map points at them as `media/…`. */
const val MEDIA_DIRECTORY = "media"

private const val METADATA_FILE = "meta.json"

/** In a map's bundle, what it carries besides its text and pictures: its identity and where the reader was. */
const val BUNDLE_INFO = "orbit.json"

/** Earlier versions of a map: in `history/<map>/` (and in a map's own folder before layout 2, see [StorageLayout]). */
const val HISTORY_DIRECTORY = "history"

/** A version kept as the file came in from elsewhere, before Orbit wrote it: `<time>.original.md`. */
private const val ORIGINAL_SUFFIX = ".original.md"

/** In a map in the trash, its history, so it comes back with its versions. */
private const val TRASHED_HISTORY = ".history"

/** A picture's name in a bundle: nothing that could step outside the map's media folder. */
private val PICTURE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

/** The types of pictures a map's media folder keeps, by extension. */
private val PICTURE_TYPES = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "heic" to "image/heic",
    "heif" to "image/heif",
    "bmp" to "image/bmp",
    "svg" to "image/svg+xml",
)

/** The types of videos a map's media folder keeps, by extension. */
private val VIDEO_TYPES = mapOf(
    "mp4" to "video/mp4",
    "m4v" to "video/mp4",
    "webm" to "video/webm",
    "mov" to "video/quicktime",
)

/**
 * The pictures and videos a map's media folder keeps. A map from someone
 * else can't bring a page or a script along: the map's page shares an origin
 * with its media, so a file it could open as a page would be on the app's side.
 */
private val MEDIA_EXTENSIONS = PICTURE_TYPES.keys + VIDEO_TYPES.keys

/** Whether a file may be in a map's media folder: a safe name, and a picture's or video's type. */
internal fun isMediaName(name: String): Boolean =
    PICTURE_NAME.matches(name) && name.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS

/** Whether a name in a map's media folder is a video's. */
fun isVideoName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_TYPES

/** The media type of a file in a map's media folder, by its name. */
fun mediaType(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return PICTURE_TYPES[extension] ?: VIDEO_TYPES[extension] ?: "application/octet-stream"
}

/** A version's file name in a history: its time, and whether it's an original. */
private val VERSION_NAME = Regex("""(\d+)(\.original)?\.md""")

/** Pictures are kept at most this many pixels on their longer side. */
private const val PICTURE_SIZE = 2048

/** How many times a tag change reads the map again when it changed under it. */
private const val TAG_TRIES = 3

/** A video's poster is at most this many pixels on its longer side: enough for a planet, and for the viewer until the video plays. */
private const val POSTER_SIZE = 1280

/** A video's poster comes from a tenth of the way in, and never later than this. */
private const val POSTER_AT_MOST_MILLISECONDS = 2_000L

/** Room on the device that copying a video in always leaves free. */
private const val VIDEO_SPARE_ROOM = 64L * 1024 * 1024

/** Deleted maps stay in the trash this long. */
const val TRASH_DAYS = 30

/** A picture added this recently is kept even if nothing points at it yet: the text pointing at it may be on its way. */
private const val PICTURE_GRACE = 10 * 60 * 1000L

/** The most an entry of a zip may hold: anything larger isn't a map's text, picture or video. */
internal const val ZIP_ENTRY_LIMIT = 64L * 1024 * 1024

/** The most a map's text, or one of its versions, may be in a zip. */
internal const val ZIP_TEXT_LIMIT = 8L * 1024 * 1024

/** The most files a zip of one map may hold: its text, its details and its pictures. */
private const val BUNDLE_ENTRIES = 4_000

/** The most a zip of one map may unpack to, its files together: it's read into memory. */
internal const val BUNDLE_SIZE = 96L * 1024 * 1024

/** The most files a zip of every map may hold. */
private const val ARCHIVE_ENTRIES = 200_000

/**
 * The most a zip of every map may unpack to: more than a phone's maps come
 * to, with their videos. What it writes is bounded by the room on the
 * device too, checked as it unpacks.
 */
private const val ARCHIVE_SIZE = 64L * 1024 * 1024 * 1024

/** Room on the device that unpacking a zip of maps always leaves free. */
private const val SPARE_ROOM = 256L * 1024 * 1024

/** Unpacked zips older than this were left by an import that never finished. */
private const val UNPACKED_LEFTOVER = 60 * 60 * 1000L

/** A folder name in a zip of maps: one map each. */
private val ZIP_FOLDER = Regex("[A-Za-z0-9\\p{L}][\\w\\p{L}.-]*")

/**
 * A map on its way to or from another device: its text and title, its
 * pictures, and what it carries with it: its identity and where the reader was.
 */
class MapBundle(
    val markdown: String,

    /** The map's title, as its outline reads it. */
    val title: String,

    /**
     * Its pictures and videos, by name: files they were unpacked to as the zip was read, never
     * memory, since a map's can come to nearly a hundred megabytes. [MapRepository.receive] moves
     * them into the map, and files a bundle that's never taken in leaves are cleared later.
     */
    val pictures: Map<String, File>,

    val uuid: String?,
    val focus: String?,
    val view: String?,
    val path: List<String>? = null,

    /** How many pictures come with it: [pictures] is empty when they were only counted. */
    val pictureCount: Int = pictures.size,
)

/** A video copied into a map's folder, and its poster beside it when the phone could read a frame, as the map points at them. */
data class AddedVideo(val src: String, val thumbnail: String?)

/** A picture or video that went from where it was being copied from. */
private class NothingToCopy : IOException()

@Serializable
private data class BundleInfo(val uuid: String? = null, val focus: String? = null, val view: String? = null, val path: List<String>? = null)

/** A map, as the home screen shows it. */
data class MapSummary(
    val id: String,
    val outline: MapOutline,
    val modified: Long,
    val metadata: MapMetadata,

    /** The map's words, to search in. */
    val searchText: String,

    /** Changes whenever the map's text does, for thumbnails. */
    val contentKey: String,

    /** Its file couldn't be read: the map shows as such, and nothing writes over it. */
    val unreadable: Boolean = false,
) {
    val title: String get() = outline.title
}

/** A deleted map in the trash, until it's restored or [TRASH_DAYS] go by. */
data class TrashedMap(
    /** The id it had, and gets back unless another map took it since. */
    val id: String,

    val title: String,

    /** When it was deleted. */
    val deleted: Long,

    val ideas: Int,
    internal val folder: File,
)

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
}

/** When a write keeps the map as it was in its history first. */
enum class Checkpoint {
    /** Once the latest version is [CHECKPOINT_INTERVAL] old: one version for every stretch of work. */
    Interval,

    /** Always: before a big change, or the first change of a visit. */
    Always,
}

/** Maps a zip of maps brought in, and the ones that were here already. */
data class ImportedMaps(val added: Int, val alreadyHere: Int)

/** The maps a first launch puts in: the samples, oldest first, then the welcome map. */
interface StarterMaps {
    fun samples(): List<String>

    fun welcome(): String?
}

/** The starter maps in the app's assets, in the language the app is in when it has its own: `samples/` and `welcome.md`. */
class AssetStarterMaps(private val context: Context) : StarterMaps {
    override fun samples(): List<String> {
        val folder = context.assetPath("samples")
        return context.assets.list(folder).orEmpty().sorted().map { name -> context.assets.open("$folder/$name").bufferedReader().use { it.readText() } }
    }

    override fun welcome(): String? = runCatching { context.openAsset("welcome.md").bufferedReader().use { it.readText() } }.getOrNull()
}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
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
) {
    private val mutex = Mutex()
    private val layout = StorageLayout(directory, historyDirectory, backups, clock)

    /** Zips of maps, unpacked while they're taken in. */
    private val unpacking = File(directory.parentFile, "unpacking")

    private val summaries = MutableStateFlow<List<MapSummary>?>(null)
    private val trashed = MutableStateFlow<List<TrashedMap>>(emptyList())
    private val canWrite = MutableStateFlow(true)

    /** How many screens show each map, by id. */
    private val screens = ConcurrentHashMap<String, Int>()

    private val replacedMaps = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Maps replaced by a newer version from elsewhere (another device, the folder, a quick capture), by id, so a screen showing one can catch up. */
    val replaced: SharedFlow<String> = replacedMaps.asSharedFlow()

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
     * the first launch, puts the welcome map and the samples in. Maps that
     * waited in the trash too long are deleted for good.
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
                emptyExpiredTrash()
            }

            summaries.value = directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }
                .orEmpty()
                .map(::summarize)
            trashed.value = listTrash()
        }
    }

    fun folder(id: String): File {
        val folder = File(directory, id)
        require(folder.parentFile == directory && id.isNotBlank() && !id.startsWith(".")) { "Not a map: $id" }
        return folder
    }

    /** Whether `id` is a map here. Shortcuts and widgets other apps can start may carry any text as an id: one no map could have is no map. */
    fun exists(id: String): Boolean = try {
        File(folder(id), MAP_FILE).isFile
    } catch (notAMap: IllegalArgumentException) {
        false
    }

    /** The map's text. Throws when it's gone or can't be read. */
    suspend fun read(id: String): String = withContext(Dispatchers.IO) {
        File(folder(id), MAP_FILE).readTextSafely()
    }

    fun summary(id: String): MapSummary? = summaries.value?.firstOrNull { it.id == id }

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
     * history first, as `checkpoint` says.
     */
    suspend fun write(id: String, markdown: String, base: String? = null, checkpoint: Checkpoint = Checkpoint.Interval): WriteResult =
        withContext(Dispatchers.IO) {
            mutex.withLock { writeLocked(id, markdown, base, checkpoint) }
        }

    private fun writeLocked(id: String, markdown: String, base: String?, checkpoint: Checkpoint, keepRefused: Boolean = true): WriteResult {
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
                keepVersionText(id, markdown)
            }

            return WriteResult.Changed
        }

        if (!MapText.hasContent(markdown) && MapText.hasContent(current)) {
            return WriteResult.Refused
        }

        if (checkpoint == Checkpoint.Always || checkpointDue(versionTimes(id), clock())) {
            keepVersionText(id, current, unless = markdown)
        }

        writeAtomically(file, markdown)
        replace(summarize(folder))
        return WriteResult.Written
    }

    /** The map's earlier versions, the newest first. */
    suspend fun versions(id: String): List<MapVersion> = withContext(Dispatchers.IO) {
        versionFiles(id).sortedByDescending { it.first }.mapNotNull { (time, file) ->
            val outline = runCatching { MapText.outline(file.readTextSafely(), words) }.getOrNull() ?: return@mapNotNull null
            MapVersion(time, outline.title, outline.ideas, original = file.name.endsWith(ORIGINAL_SUFFIX))
        }
    }

    /** The map's text as it was at `time`, if that version is kept. */
    suspend fun readVersion(id: String, time: Long): String? = withContext(Dispatchers.IO) {
        versionFiles(id).firstOrNull { it.first == time }?.second?.readTextSafely()
    }

    /**
     * Keeps `text` in the map's history without making it the map: a version
     * no write took (one made before the map changed elsewhere), so it's
     * never lost.
     */
    suspend fun keepAside(id: String, text: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (canWrite.value && File(folder(id), MAP_FILE).isFile) {
                keepVersionText(id, text)
            }
        }
    }

    /** Keeps the map as it is now in its history, as an earlier version is about to replace it. */
    suspend fun keepCurrentVersion(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(folder(id), MAP_FILE).takeIf { it.isFile && canWrite.value }?.let { keepVersionText(id, it.readTextSafely()) }
        }
    }

    /**
     * Changes a map's text from outside its screen, as quick capture does,
     * while it's still `base` (the caller tries again otherwise): a screen
     * showing the map catches up at once, and undo brings it back.
     */
    suspend fun change(id: String, markdown: String, base: String): WriteResult {
        val result = withContext(Dispatchers.IO) {
            mutex.withLock { writeLocked(id, markdown, base, Checkpoint.Always, keepRefused = false) }
        }

        if (result == WriteResult.Written) {
            replacedMaps.tryEmit(id)
        }

        return result
    }

    /**
     * Gives a map these tags, in its file's frontmatter (see [MapTags]). A
     * screen showing the map catches up, and undo there brings the old ones
     * back. Tries again while the map changes under it.
     */
    suspend fun setTags(id: String, tags: List<String>): WriteResult = editTags(id) { tags }

    /**
     * Renames a tag in every map that has it, whatever its capitals, as one
     * tag with any it's renamed to; `to` null takes it out of them. Says how
     * many maps changed.
     */
    suspend fun renameTag(from: String, to: String?): Int {
        val key = MapTags.key(from)
        val tagged = summaries.value.orEmpty().filter { map -> !map.unreadable && map.outline.tags.any { MapTags.key(it) == key } }
        var changed = 0

        for (map in tagged) {
            val result = editTags(map.id) { tags -> tags.flatMap { tag -> if (MapTags.key(tag) == key) listOfNotNull(to) else listOf(tag) } }

            if (result == WriteResult.Written) {
                changed += 1
            }
        }

        return changed
    }

    private suspend fun editTags(id: String, edit: (List<String>) -> List<String>): WriteResult {
        repeat(TAG_TRIES) {
            val current = try {
                read(id)
            } catch (gone: IOException) {
                return WriteResult.Missing
            }

            val result = change(id, MapTags.write(current, edit(MapTags.read(current))), base = current)

            if (result != WriteResult.Changed) {
                return result
            }
        }

        return WriteResult.Changed
    }

    /** Creates a map with just its root, and returns its id. */
    suspend fun create(title: String, style: String? = null, tags: List<String> = emptyList()): String =
        add(MapTags.write(MapText.newMap(title, words, style), tags))

    /**
     * Creates a map from orbit-md text: a template, an import, something
     * shared, a file new in the folder maps are kept in. `original` is
     * someone's own file the map came from: it's kept as it came in, for
     * good, in the map's history, whatever Orbit writes later.
     */
    suspend fun add(markdown: String, pinned: Boolean = false, sync: SyncRecord? = null, original: String? = null): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                checkWritable()
                val id = newFolder(MapText.outline(markdown, words).title)

                val folder = settle(id) { staging ->
                    writeAtomically(File(staging, MAP_FILE), markdown)
                    writeMetadata(staging, MapMetadata(created = clock(), pinned = pinned, uuid = UUID.randomUUID().toString(), sync = sync))
                }

                original?.let { keepOriginal(id, it) }
                insert(summarize(folder))
                id
            }
        }

    /** A copy of a map, pictures included, with "(copy)" after its title in `words`, and an identity of its own. */
    suspend fun duplicate(id: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkWritable()
            val source = folder(id)
            val markdown = File(source, MAP_FILE).readTextSafely()
            val title = words.string(R.string.data_copy_title, MapText.outline(markdown, words).title)
            val copyId = newFolder(title)

            val copy = settle(copyId) { staging ->
                File(source, MEDIA_DIRECTORY).takeIf { it.isDirectory }?.copyRecursively(File(staging, MEDIA_DIRECTORY))
                writeAtomically(File(staging, MAP_FILE), MapText.retitle(markdown, title, words))
                writeMetadata(staging, MapMetadata(created = clock(), uuid = UUID.randomUUID().toString()))
            }

            insert(summarize(copy))
            copyId
        }
    }

    suspend fun rename(id: String, title: String): WriteResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(folder(id), MAP_FILE)

            if (!file.isFile) {
                return@withLock WriteResult.Missing
            }

            val current = file.readTextSafely()
            writeLocked(id, MapText.retitle(current, title, words), base = current, checkpoint = Checkpoint.Interval)
        }
    }

    /**
     * Moves a map to the trash, its history with it; [restore] brings it
     * back for [TRASH_DAYS]. A map kept in a folder leaves its file there to
     * delete; restored, it's written there again.
     */
    suspend fun delete(id: String): TrashedMap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)

            if (!folder.isDirectory || !canWrite.value) {
                return@withLock null
            }

            trashDirectory.mkdirs()
            val trashedFolder = File(trashDirectory, "$id-${clock()}")
            val record = readMetadata(folder).sync

            if (!folder.renameTo(trashedFolder)) {
                return@withLock null
            }

            val history = File(historyDirectory, id)

            if (history.isDirectory && !move(history, File(trashedFolder, TRASHED_HISTORY))) {
                Logs.warn("MapRepository", "Couldn't take a map's history to the trash: it stays where it was")
            }

            record?.let { writeTombstones(readTombstones() + it) }
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

            val id = if (File(directory, item.id).exists()) newFolder(item.title) else item.id
            val folder = folder(id)

            if (!item.folder.renameTo(folder)) {
                return@withLock null
            }

            val trashedHistory = File(folder, TRASHED_HISTORY)

            if (trashedHistory.isDirectory && !move(trashedHistory, File(historyDirectory, id))) {
                Logs.warn("MapRepository", "Couldn't bring a map's history back from the trash: it stays in the map's folder")
            }

            // Its file stays in the folder; if it's gone from there already, it's written again as a new one.
            val metadata = readMetadata(folder)
            val record = metadata.sync

            if (record != null) {
                val tombstones = readTombstones()

                if (record in tombstones) {
                    writeTombstones(tombstones - record)
                } else {
                    writeMetadata(folder, metadata.copy(sync = null))
                }
            }

            trashed.update { items -> items.filterNot { it.folder == item.folder } }
            insert(summarize(folder))
            id
        }
    }

    /** Deletes a map in the trash for good. */
    suspend fun deleteForever(item: TrashedMap) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (canWrite.value) {
                item.folder.deleteRecursively()
                trashed.update { items -> items.filterNot { it.folder == item.folder } }
            }
        }
    }

    /** Deletes every map in the trash for good. */
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (canWrite.value) {
                trashDirectory.listFiles().orEmpty().forEach { it.deleteRecursively() }
                trashed.value = emptyList()
            }
        }
    }

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

            keepVersionText(id, current, unless = markdown)
            writeAtomically(File(folder, MAP_FILE), markdown)
            writeMetadata(folder, readMetadata(folder).copy(sync = record))
            replace(summarize(folder))
            replacedMaps.tryEmit(id)
            true
        }
    }

    /** A picture or video in the map's media folder, if it's there. */
    fun picture(id: String, name: String): File? =
        File(File(folder(id), MEDIA_DIRECTORY), name).takeIf { isMediaName(name) && it.isFile }

    /**
     * Puts a picture or video the map points at into its media folder, unless
     * it's there already. `copy` writes it out, and says false when there's
     * nothing to copy after all; it's written whole or not at all. One that
     * comes to more than `room` bytes is refused with a
     * [MediaRefusedException], leaving nothing behind. Returns its size in
     * the map, 0 when it isn't there.
     */
    suspend fun addMediaFile(id: String, name: String, room: Long = Long.MAX_VALUE, copy: (OutputStream) -> Boolean): Long = withContext(Dispatchers.IO) {
        if (!isMediaName(name) || !canWrite.value) {
            return@withContext 0L
        }

        val file = File(File(folder(id), MEDIA_DIRECTORY), name)

        if (!file.exists()) {
            try {
                writeAtomically(file) { output ->
                    if (!copy(CappedOutput(output, room, words))) {
                        throw NothingToCopy()
                    }
                }
            } catch (missing: NothingToCopy) {
                // The file went from where it was coming from: the next sync tries again.
            }
        }

        if (file.isFile) file.length() else 0L
    }

    /** The pictures and videos the map's text points at, that its folder has: what its zip carries. */
    private fun mediaFiles(folder: File, markdown: String): List<File> {
        val media = File(folder, MEDIA_DIRECTORY)
        return picturesIn(markdown).filter(::isMediaName).map { File(media, it) }.filter { it.isFile }
    }

    /** How many bytes the pictures and videos the map has that its text, or `markdown`, points at come to. */
    fun mediaSize(id: String, markdown: String? = null): Long {
        val folder = folder(id)
        val text = markdown ?: runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        return mediaFiles(folder, text).sumOf { it.length() }
    }

    private fun readTombstones(): List<SyncRecord> =
        runCatching { json.decodeFromString(ListSerializer(SyncRecord.serializer()), tombstones.readText()) }.getOrDefault(emptyList())

    private fun writeTombstones(records: List<SyncRecord>) {
        if (records.isEmpty()) {
            tombstones.delete()
        } else {
            writeAtomically(tombstones, json.encodeToString(ListSerializer(SyncRecord.serializer()), records.distinct()))
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) = updateMetadata(id) { it.copy(pinned = pinned) }

    suspend fun markOpened(id: String) = updateMetadata(id) { it.copy(opened = clock()) }

    /** Remembers where the reader was, to open the map there next time. */
    suspend fun savePlace(id: String, focus: String?, view: String?) = updateMetadata(id) { it.copy(focus = focus, view = view) }

    /** The ideas to present, in order; an empty path presents them all in reading order. */
    suspend fun savePath(id: String, path: List<String>) = updateMetadata(id) { it.copy(path = path.ifEmpty { null }) }

    /** Seconds the last rehearsal spent on each idea. */
    suspend fun saveRehearsal(id: String, seconds: Map<String, Int>) = updateMetadata(id) { it.copy(rehearsal = seconds) }

    /** An idea's new id, after new text gave it one: the path and the rehearsal keep the idea. */
    suspend fun renameIdea(id: String, from: String, to: String) = updateMetadata(id) { metadata ->
        metadata.copy(
            path = metadata.path?.map { if (it == from) to else it },
            rehearsal = metadata.rehearsal?.mapKeys { (idea, _) -> if (idea == from) to else idea },
        )
    }

    /**
     * Copies a picture into the map's folder, at most [PICTURE_SIZE] pixels
     * on its longer side and in a format every browser reads (JPEG, or PNG
     * with transparency). Returns where the map points at it: `media/…`.
     */
    suspend fun addPicture(id: String, uri: Uri, resolver: ContentResolver): String = withContext(Dispatchers.IO) {
        checkWritable()

        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)

            if (longest > PICTURE_SIZE) {
                val scale = PICTURE_SIZE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }

            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        val transparent = bitmap.hasAlpha()
        val name = "picture-${clock().toString(36)}.${if (transparent) "png" else "jpg"}"

        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, output)
            output.toByteArray()
        }

        bitmap.recycle()
        pictureRefusal(bytes.size.toLong(), mediaSize(id), words)?.let { throw MediaRefusedException(it) }
        writeAtomically(File(File(folder(id), MEDIA_DIRECTORY), name), bytes)
        "$MEDIA_DIRECTORY/$name"
    }

    /**
     * Copies the pictures and videos `markdown` points to (`media/…`) from map
     * `from` into map `to`, for ideas copied in one map and pasted into the
     * other. A name `to` has already is left as it is: Orbit names media by
     * the moment they were added, so it's the same file. As adding them one
     * by one would be, it's refused with a [MediaRefusedException] past
     * `to`'s room.
     */
    suspend fun copyMedia(from: String, to: String, markdown: String): Unit = withContext(Dispatchers.IO) {
        if (from == to || !exists(from) || !exists(to)) {
            return@withContext
        }

        checkWritable()

        val source = File(folder(from), MEDIA_DIRECTORY)
        val target = File(folder(to), MEDIA_DIRECTORY)

        val copies = mediaNamesIn(markdown).mapNotNull { name ->
            val file = try {
                fileInside(source, name, words)
            } catch (outside: RefusedZipException) {
                null
            }

            file?.takeIf { it.isFile && !File(target, name).exists() }
        }

        pictureRefusal(copies.sumOf { it.length() }, mediaSize(to), words)?.let { throw MediaRefusedException(it) }

        for (file in copies) {
            writeAtomically(fileInside(target, file.name, words)) { output -> file.inputStream().use { it.copyTo(output) } }
        }
    }

    /**
     * Copies a video from the phone into the map's folder as it is, under a
     * name of Orbit's own with the extension its type has, and puts a frame
     * from it beside it as a JPEG, for the map to show as its poster. A video
     * past [VIDEO_SIZE_LIMIT], or one that would take the map's pictures and
     * videos past [MAP_MEDIA_LIMIT], is refused with a [MediaRefusedException]
     * saying why. `onProgress` hears how far the copy is, from 0 to 1.
     */
    suspend fun addVideo(id: String, uri: Uri, resolver: ContentResolver, onProgress: (Float) -> Unit): AddedVideo = withContext(Dispatchers.IO) {
        checkWritable()

        val (displayName, declaredSize) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0) to (if (cursor.isNull(1)) -1L else cursor.getLong(1))
            } else {
                null
            }
        } ?: (null to -1L)

        val type = resolver.getType(uri)
        val extension = videoExtension(type, displayName) ?: throw MediaRefusedException(
            if (type != null) {
                words.string(R.string.data_video_kind_refused, type)
            } else {
                words.string(R.string.data_video_kind_unknown)
            },
        )
        val size = declaredSize.takeIf { it >= 0 } ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

        if (size >= 0) {
            videoRefusal(size, mediaSize(id), words)?.let { throw MediaRefusedException(it) }
        }

        val media = File(folder(id), MEDIA_DIRECTORY)
        media.mkdirs()

        if (room(media) < maxOf(size, 0L) + VIDEO_SPARE_ROOM) {
            throw MediaRefusedException(words.string(R.string.data_no_room_for_video))
        }

        val stem = uniqueStem(media, "video-${clock().toString(36)}")
        val video = File(media, "$stem.$extension")
        val context = currentCoroutineContext()

        writeAtomically(video) { output ->
            val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the video")
            input.use { copyVideo(it, output, size, words, onProgress) { context.ensureActive() } }
        }

        if (size < 0) {
            // Its size wasn't known before the copy: the map's room is checked now.
            videoRefusal(video.length(), mediaSize(id), words)?.let { problem ->
                video.delete()
                throw MediaRefusedException(problem)
            }
        }

        val poster = runCatching { writePoster(video, File(media, "$stem.jpg")) }.getOrNull()
        AddedVideo("$MEDIA_DIRECTORY/${video.name}", poster?.let { "$MEDIA_DIRECTORY/${it.name}" })
    }

    /** A frame from early in the video, at most [POSTER_SIZE] pixels on its longer side, written to `to`; null when the phone can't read the video. */
    private fun writePoster(video: File, to: File): File? {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(video.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // A moment in, where there's more to see than a first frame often shows, but never far into a short clip.
            val at = (duration / 10).coerceAtMost(POSTER_AT_MOST_MILLISECONDS) * 1_000
            val frame = retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, POSTER_SIZE, POSTER_SIZE) ?: return null

            val bytes = ByteArrayOutputStream().use { output ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, output)
                output.toByteArray()
            }

            frame.recycle()
            writeAtomically(to, bytes)
            return to
        } finally {
            retriever.release()
        }
    }

    /** `stem`, or `stem-2`, `stem-3` and so on: a name no file in `folder` starts with. */
    private fun uniqueStem(folder: File, stem: String): String {
        val taken = folder.list().orEmpty().map { it.substringBeforeLast('.') }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) stem else "$stem-$it" }.first { it !in taken }
    }

    /**
     * Deletes pictures in the map's folder that neither the map nor an
     * earlier version of it points at, unless they were added in the last
     * few minutes: the text that points at a new one may be on its way.
     */
    suspend fun removeUnusedPictures(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canWrite.value) {
                return@withLock
            }

            val folder = folder(id)
            val markdown = File(folder, MAP_FILE).takeIf { it.isFile }?.readTextSafely() ?: return@withLock
            val versions = versionFiles(id).map { it.second.readTextSafely() }
            val recent = clock() - PICTURE_GRACE
            File(folder, MEDIA_DIRECTORY).listFiles().orEmpty()
                .filter { file -> file.lastModified() < recent }
                .filter { file -> "$MEDIA_DIRECTORY/${file.name}".let { name -> name !in markdown && versions.none { name in it } } }
                .forEach { it.delete() }
        }
    }

    /** Whether the map's text points at pictures or videos in its folder. */
    fun hasPictures(id: String): Boolean {
        val folder = folder(id)
        return mediaFiles(folder, runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")).isNotEmpty()
    }

    /**
     * The map as a bundle for another device: its text, the pictures and
     * videos it points at, its identity and place, in a zip that [readBundle]
     * reads back. Files kept only for its earlier versions stay here.
     */
    suspend fun writeZip(id: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val metadata = readMetadata(folder)
        val info = BundleInfo(uuid = identity(id), focus = metadata.focus, view = metadata.view, path = metadata.path)
        val text = File(folder, MAP_FILE).readBytes()

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MAP_FILE))
            zip.write(text)
            zip.closeEntry()

            mediaFiles(folder, decodeText(text)).forEach { file ->
                zip.putNextEntry(ZipEntry("$MEDIA_DIRECTORY/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry(BUNDLE_INFO))
            zip.write(json.encodeToString(BundleInfo.serializer(), info).toByteArray())
            zip.closeEntry()
        }
    }

    /** The map's lasting identity, given one now if it's older than identities. */
    suspend fun identity(id: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)
            val metadata = readMetadata(folder)

            metadata.uuid ?: UUID.randomUUID().toString().also { uuid ->
                if (canWrite.value) {
                    writeMetadata(folder, metadata.copy(uuid = uuid))
                    replace(summarize(folder))
                }
            }
        }
    }

    /** The map with this identity, if it's here. */
    fun findByIdentity(uuid: String): String? = summaries.value?.firstOrNull { it.metadata.uuid == uuid }?.id

    /** A map here with exactly this text, if there is one. */
    private fun findByText(markdown: String): String? {
        val key = digest(markdown)
        return summaries.value?.firstOrNull { it.contentKey == key && runCatching { File(folder(it.id), MAP_FILE).readTextSafely() }.getOrNull() == markdown }?.id
    }

    /**
     * A bundle read from a zip, or null when there's no map in it: its
     * `map.md`, or else its only other markdown file. Without `withPictures`
     * the pictures are only counted, to show what's coming before it's taken
     * in. A zip that unpacks to more than a map holds, or whose files point
     * outside it, is refused with an [IOException].
     */
    suspend fun readBundle(input: InputStream, withPictures: Boolean = true): MapBundle? = withContext(Dispatchers.IO) {
        val pictures = mutableMapOf<String, File>()
        val pictureNames = mutableSetOf<String>()
        val texts = mutableMapOf<String, String>()
        var info = BundleInfo()
        val budget = ZipBudget(BUNDLE_ENTRIES, BUNDLE_SIZE, words)
        val unpacked = File(unpacking, UUID.randomUUID().toString())

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val name = entry.name.substringAfterLast('/')

                when {
                    entry.isDirectory || name.startsWith(".") -> zip.skipEntry(budget)
                    entry.name == BUNDLE_INFO -> {
                        val read = zip.readLimited(budget, ZIP_TEXT_LIMIT).decodeToString()
                        info = runCatching { json.decodeFromString(BundleInfo.serializer(), read) }.getOrDefault(info)
                    }
                    entry.name.endsWith(".md") -> texts[entry.name] = decodeText(zip.readLimited(budget, ZIP_TEXT_LIMIT))
                    entry.name.contains("$MEDIA_DIRECTORY/") && isMediaName(name) -> {
                        pictureNames += name

                        if (withPictures) {
                            val file = fileInside(unpacked, name, words)
                            unpacked.mkdirs()

                            FileOutputStream(file).use { output -> zip.copyLimited(output, budget) }
                            pictures[name] = file
                        } else {
                            zip.skipEntry(budget)
                        }
                    }
                    else -> zip.skipEntry(budget)
                }
            }
        }

        val markdown = texts[MAP_FILE] ?: texts.entries.firstOrNull { it.key.endsWith("/$MAP_FILE") }?.value ?: texts.values.singleOrNull()
        markdown?.let { MapBundle(it, MapText.outline(it, words).title, pictures, info.uuid, info.focus, info.view, info.path, pictureNames.size) }
    }

    /**
     * Whether a zip holds maps a folder each, as [exportAll] writes them
     * (several maps, or one with its details and history), rather than one
     * map's bundle: [importAll] takes those in.
     */
    suspend fun holdsManyMaps(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        val folders = mutableSetOf<String>()
        var detailed = false
        val budget = ZipBudget(ARCHIVE_ENTRIES, ARCHIVE_SIZE, words)

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val parts = entry.name.split('/')

                when {
                    entry.name == MAP_FILE -> return@withContext false
                    parts.size == 2 && ZIP_FOLDER.matches(parts[0]) && parts[1] == MAP_FILE -> folders += parts[0]
                    parts.size == 2 && ZIP_FOLDER.matches(parts[0]) && parts[1] == METADATA_FILE -> detailed = true
                }

                if (folders.size > 1) {
                    return@withContext true
                }

                zip.skipEntry(budget)
            }
        }

        folders.isNotEmpty() && detailed
    }

    /**
     * Takes in a map from another device. With `replacing`, it becomes that
     * map's new version, the old one kept in its history; otherwise it's
     * added, keeping its identity unless a map here already has it.
     */
    suspend fun receive(bundle: MapBundle, replacing: String? = null): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkWritable()
            val existing = replacing?.let(::folder)?.takeIf { File(it, MAP_FILE).isFile }

            if (existing != null) {
                keepVersionText(existing.name, File(existing, MAP_FILE).readTextSafely(), unless = bundle.markdown)
                writeAtomically(File(existing, MAP_FILE), bundle.markdown)
                writePictures(existing, bundle.pictures)
                val metadata = readMetadata(existing)
                writeMetadata(
                    existing,
                    metadata.copy(
                        focus = bundle.focus ?: metadata.focus,
                        view = bundle.view ?: metadata.view,
                        uuid = metadata.uuid ?: bundle.uuid,
                        path = bundle.path ?: metadata.path,
                    ),
                )
                replace(summarize(existing))
                replacedMaps.tryEmit(existing.name)
                return@withLock existing.name
            }

            val id = newFolder(bundle.title)
            val uuid = bundle.uuid?.takeIf { findByIdentity(it) == null } ?: UUID.randomUUID().toString()

            val folder = settle(id) { staging ->
                writeAtomically(File(staging, MAP_FILE), bundle.markdown)
                writePictures(staging, bundle.pictures)
                writeMetadata(staging, MapMetadata(created = clock(), focus = bundle.focus, view = bundle.view, uuid = uuid, path = bundle.path))
            }

            insert(summarize(folder))
            id
        }
    }

    /** Adds the map in a zip (a bundle from another device, or any zip with a map in it), or updates the one it is. */
    suspend fun addZip(input: InputStream): String? {
        val bundle = readBundle(input) ?: return null
        return receive(bundle)
    }

    // Every map at once: a way out of the app without Google's backups

    /**
     * Writes every map into one zip: a folder each, with its text, details,
     * pictures and history, which [importAll] reads back on any device.
     * Returns how many maps it holds.
     */
    suspend fun exportAll(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val folders = mutex.withLock {
            directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }.orEmpty().toList()
        }

        var exported = 0

        ZipOutputStream(output).use { zip ->
            for (folder in folders) {
                // Each map's text and details as they are now, under the lock, so a save meanwhile never leaves half of one.
                val map = mutex.withLock { exportedMap(folder) } ?: continue
                exported += 1

                for ((name, bytes) in map.texts) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }

                // Pictures, videos and versions are only ever replaced whole, never written into, so they're copied
                // outside the lock: a big video doesn't hold up saves and syncs. One deleted meanwhile is left out.
                for ((file, name) in map.files) {
                    val input = try {
                        file.inputStream()
                    } catch (gone: FileNotFoundException) {
                        continue
                    }

                    input.use {
                        zip.putNextEntry(ZipEntry(name))
                        it.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }
        }

        exported
    }

    /** A map as [exportAll] writes it: its text and details read now, and its other files to copy. Null once it's gone. */
    private class ExportedMap(val texts: List<Pair<String, ByteArray>>, val files: List<Pair<File, String>>)

    private fun exportedMap(folder: File): ExportedMap? {
        val text = File(folder, MAP_FILE)

        if (!text.isFile) {
            return null
        }

        // Each map leaves with an identity, so bringing it back finds it here instead of copying it.
        val metadata = readMetadata(folder)

        if (metadata.uuid == null && canWrite.value) {
            writeMetadata(folder, metadata.copy(uuid = UUID.randomUUID().toString()))
        }

        val read = setOf(MAP_FILE, METADATA_FILE)

        val texts = folder.listFiles { file -> file.name in read && file.isFile }.orEmpty()
            .map { "${folder.name}/${it.name}" to it.readBytes() }

        val files = folder.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && it.relativeTo(folder).path !in read }
            .map { it to "${folder.name}/${it.relativeTo(folder).invariantSeparatorsPath}" }
            .toList() + versionFiles(folder.name).map { (_, file) -> file to "${folder.name}/$HISTORY_DIRECTORY/${file.name}" }

        return ExportedMap(texts, files)
    }

    /**
     * Adds the maps in a zip [exportAll] wrote. A map that's here already
     * with the same text is left alone; one that changed since comes in as
     * a map of its own, so nothing here is written over. The zip is unpacked
     * to files first, however many pictures it holds; one that would unpack
     * to more than maps hold, or past the room left on the device, or whose
     * files point outside it, is refused with an [IOException] before any
     * map is added.
     */
    suspend fun importAll(input: InputStream): ImportedMaps = withContext(Dispatchers.IO) {
        // The maps here must be known first, to find the ones the zip has already.
        summaries.filterNotNull().first()
        val unpacked = File(unpacking, UUID.randomUUID().toString())

        try {
            val incoming = unpackMaps(input, unpacked)

            mutex.withLock {
                checkWritable()
                var added = 0
                var alreadyHere = 0

                for (map in incoming) {
                    val markdown = File(map, MAP_FILE).takeIf { it.isFile }?.readTextSafely() ?: continue
                    val metadata = File(map, METADATA_FILE).takeIf { it.isFile }?.let { file -> runCatching { MetadataFile.decode(file.readText()) }.getOrNull() }
                        ?: MapMetadata(created = clock())
                    val here = metadata.uuid?.let(::findByIdentity)

                    if ((here != null && File(folder(here), MAP_FILE).readTextSafely() == markdown) || findByText(markdown) != null) {
                        alreadyHere += 1
                        continue
                    }

                    val id = newFolder(MapText.outline(markdown, words).title)

                    val folder = settle(id) { staging ->
                        writeAtomically(File(staging, MAP_FILE), markdown)
                        moveFiles(File(map, MEDIA_DIRECTORY), File(staging, MEDIA_DIRECTORY))
                        // Its file in a folder belongs to the device it was kept on.
                        writeMetadata(staging, metadata.copy(uuid = metadata.uuid?.takeIf { here == null } ?: UUID.randomUUID().toString(), sync = null))
                    }

                    moveFiles(File(map, HISTORY_DIRECTORY), File(historyDirectory, id))
                    insert(summarize(folder))
                    added += 1
                }

                ImportedMaps(added, alreadyHere)
            }
        } finally {
            unpacked.deleteRecursively()
        }
    }

    /**
     * Unpacks a zip of maps into `into`: a numbered folder for each map, in
     * the order they come, with only what a map has (its text, details,
     * pictures and versions). Returns the folders.
     */
    private fun unpackMaps(input: InputStream, into: File): List<File> {
        val folders = linkedMapOf<String, File>()
        val budget = ZipBudget(ARCHIVE_ENTRIES, ARCHIVE_SIZE, words)
        into.mkdirs()

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val parts = entry.name.split('/')

                val wanted = !entry.isDirectory && parts.size in 2..3 && ZIP_FOLDER.matches(parts[0]) && when {
                    parts.size == 2 -> parts[1] == MAP_FILE || parts[1] == METADATA_FILE
                    parts[1] == MEDIA_DIRECTORY -> isMediaName(parts[2])
                    parts[1] == HISTORY_DIRECTORY -> VERSION_NAME.matches(parts[2])
                    else -> false
                }

                if (!wanted) {
                    zip.skipEntry(budget)
                    continue
                }

                if (into.usableSpace < SPARE_ROOM) {
                    throw RefusedZipException(words.string(R.string.data_no_room_for_zip_maps))
                }

                val folder = folders.getOrPut(parts[0]) { File(into, folders.size.toString()) }
                val file = fileInside(folder, parts.drop(1).joinToString("/"), words)
                file.parentFile?.mkdirs()

                FileOutputStream(file).use { output ->
                    zip.copyLimited(output, budget, if (parts[1] == MEDIA_DIRECTORY) ZIP_ENTRY_LIMIT else ZIP_TEXT_LIMIT)
                    output.flush()
                    output.fd.sync()
                }
            }
        }

        return folders.values.toList()
    }

    /** Moves the files in `from` into `to`, copying the ones that can't simply be renamed. */
    private fun moveFiles(from: File, to: File) {
        val files = from.listFiles { file -> file.isFile }.orEmpty()

        if (files.isEmpty()) {
            return
        }

        to.mkdirs()

        for (file in files) {
            val target = File(to, file.name)

            if (!file.renameTo(target)) {
                writeAtomically(target, file.readBytes())
            }
        }
    }

    /** Moves a bundle's pictures and videos into a map's folder, copying one only when it can't move. */
    private fun writePictures(folder: File, pictures: Map<String, File>) {
        val media = File(folder, MEDIA_DIRECTORY)
        media.mkdirs()

        pictures.forEach { (name, source) ->
            val target = fileInside(media, name, words)

            if (!source.renameTo(target)) {
                writeAtomically(target) { output -> source.inputStream().use { it.copyTo(output) } }
                source.delete()
            }

            source.parentFile?.delete()
        }
    }

    // History

    /** The map's kept versions, with when each was the map. */
    private fun versionFiles(id: String): List<Pair<Long, File>> =
        File(historyDirectory, id).listFiles().orEmpty().mapNotNull { file ->
            VERSION_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to file }
        }

    /** When each kept version was the map, originals left out: they're never thinned, and don't count as checkpoints. */
    private fun versionTimes(id: String): List<Long> =
        versionFiles(id).filterNot { it.second.name.endsWith(ORIGINAL_SUFFIX) }.map { it.first }

    /**
     * Keeps `text` in the map's history, unless it's `unless` (the text about
     * to replace it) or the same as the latest version; then thins the history
     * out as it ages (see [versionsToKeep]).
     */
    private fun keepVersionText(id: String, text: String, unless: String? = null) {
        val history = File(historyDirectory, id)
        val times = versionTimes(id)
        val latest = times.maxOrNull()?.let { File(history, "$it.md") }

        if (text == unless || (latest != null && latest.isFile && latest.readTextSafely() == text)) {
            return
        }

        val now = max(clock(), (times.maxOrNull() ?: 0) + 1)
        writeAtomically(File(history, "$now.md"), text)
        val kept = versionsToKeep(versionTimes(id), clock())
        versionTimes(id).filterNot { it in kept }.forEach { File(history, "$it.md").delete() }
    }

    /** Keeps a file as it came in, for good: Orbit may write it its own way later. */
    private fun keepOriginal(id: String, text: String) {
        writeAtomically(File(File(historyDirectory, id), "${clock()}$ORIGINAL_SUFFIX"), text)
    }

    // Folders

    /**
     * Makes a map's folder whole before it shows up: filled in a hidden
     * folder, then given its name in one step, so a crash midway leaves no
     * half a map behind (and nothing with another map's identity).
     */
    private fun settle(id: String, fill: (File) -> Unit): File {
        val staging = File(directory, ".new-${UUID.randomUUID()}")
        staging.mkdirs()

        try {
            fill(staging)
            val folder = folder(id)

            if (!staging.renameTo(folder)) {
                throw IOException("Couldn't add the map $id")
            }

            return folder
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Folders a crash left half made, and zips it left unpacked. */
    private fun clearLeftovers() {
        directory.listFiles { file -> file.isDirectory && file.name.startsWith(".new-") }.orEmpty().forEach { it.deleteRecursively() }
        val stale = clock() - UNPACKED_LEFTOVER
        unpacking.listFiles { file -> file.lastModified() < stale }.orEmpty().forEach { it.deleteRecursively() }
    }

    private fun checkWritable() {
        if (!canWrite.value) {
            throw IOException(words.string(R.string.data_newer_version_keeps_maps))
        }
    }

    // The trash

    private fun listTrash(): List<TrashedMap> =
        trashDirectory.listFiles { file -> file.isDirectory }.orEmpty().mapNotNull(::trashedMap).sortedByDescending { it.deleted }

    private fun trashedMap(folder: File): TrashedMap? {
        val deleted = folder.name.substringAfterLast('-').toLongOrNull() ?: folder.lastModified()
        val id = folder.name.substringBeforeLast('-').takeIf { folder.name.contains('-') && it.isNotEmpty() } ?: folder.name
        val markdown = runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrNull() ?: return null
        val outline = MapText.outline(markdown, words)
        return TrashedMap(id, outline.title, deleted, outline.ideas, folder)
    }

    private fun emptyExpiredTrash() {
        val limit = clock() - TRASH_DAYS * 24 * 60 * 60 * 1000L
        listTrash().filter { it.deleted < limit }.forEach { it.folder.deleteRecursively() }
    }

    // Details

    private suspend fun updateMetadata(id: String, change: (MapMetadata) -> MapMetadata) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)

            if (!File(folder, MAP_FILE).isFile || !canWrite.value) {
                return@withLock
            }

            writeMetadata(folder, change(readMetadata(folder)))
            replace(summarize(folder))
        }
    }

    private fun summarize(folder: File): MapSummary {
        val file = File(folder, MAP_FILE)
        val metadata = readMetadata(folder)

        val markdown = try {
            file.readTextSafely()
        } catch (unreadable: IOException) {
            return MapSummary(
                id = folder.name,
                outline = MapOutline(title = folder.name, branches = 0, ideas = 0),
                modified = file.lastModified(),
                metadata = metadata,
                searchText = "",
                contentKey = "unreadable-${file.lastModified()}",
                unreadable = true,
            )
        }

        return MapSummary(
            id = folder.name,
            outline = MapText.outline(markdown, words),
            modified = file.lastModified(),
            metadata = metadata,
            searchText = MapText.searchText(markdown),
            contentKey = digest(markdown),
        )
    }

    /**
     * What the app remembers about the map. A file this version can't read
     * at all is moved aside (`meta.json.unreadable-<time>`) rather than
     * written over: the map then starts its details afresh.
     */
    private fun readMetadata(folder: File): MapMetadata {
        val file = File(folder, METADATA_FILE)
        val fresh = MapMetadata(created = File(folder, MAP_FILE).lastModified())

        if (!file.isFile) {
            return fresh
        }

        return try {
            MetadataFile.decode(file.readText())
        } catch (unreadable: SerializationException) {
            if (canWrite.value) {
                // Kept aside, never written over: copied when it can't be moved, since the next write takes its place.
                val aside = File(folder, "$METADATA_FILE.unreadable-${clock()}")

                if (!file.renameTo(aside)) {
                    runCatching { file.copyTo(aside, overwrite = true) }.onFailure { Logs.warn("MapRepository", "Couldn't keep an unreadable ${file.name} aside", it) }
                }
            }

            fresh
        } catch (unreadable: IOException) {
            fresh
        }
    }

    private fun writeMetadata(folder: File, metadata: MapMetadata) {
        writeAtomically(File(folder, METADATA_FILE), MetadataFile.encode(metadata))
    }

    private fun insert(summary: MapSummary) {
        summaries.update { maps -> listOf(summary) + maps.orEmpty().filterNot { it.id == summary.id } }
    }

    private fun replace(summary: MapSummary) {
        summaries.update { maps ->
            if (maps == null || maps.none { it.id == summary.id }) {
                listOf(summary) + maps.orEmpty()
            } else {
                maps.map { if (it.id == summary.id) summary else it }
            }
        }
    }

    /** A new map's id, unlike any map's here, in the trash, or with a history left behind. */
    private fun newFolder(title: String): String {
        val taken = directory.list().orEmpty().toSet() +
            trashDirectory.list().orEmpty().map { it.substringBeforeLast('-') } +
            historyDirectory.list().orEmpty()
        return MapText.folderName(title, taken)
    }

    /** The welcome map, pinned, then the samples, oldest first so they list in order. */
    private fun installStarterMaps() {
        val samples = starterMaps.samples()
        val now = clock()

        samples.forEachIndexed { index, markdown ->
            install(markdown, now - (samples.size - index + 1) * 60_000L, pinned = false)
        }

        starterMaps.welcome()?.let { install(it, now, pinned = true) }
    }

    private fun install(markdown: String, time: Long, pinned: Boolean) {
        val id = newFolder(MapText.outline(markdown, words).title)

        val folder = settle(id) { staging ->
            writeAtomically(File(staging, MAP_FILE), markdown)
            writeMetadata(staging, MapMetadata(created = time, pinned = pinned))
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

/** A zip Orbit won't take in: too big once unpacked, too many files, or files that point outside it. Its message says which, for people. */
class RefusedZipException(message: String) : IOException(message)

/**
 * What a zip may still unpack to: so many files, so many bytes. A zip can
 * hold far more than it weighs, so what comes out of it is counted as it's
 * read, and reading stops once it's past what maps hold. Its refusals say
 * why in `words`.
 */
internal class ZipBudget(private var entries: Int, private var bytes: Long, private val words: Words) {
    /** The next file, named `name`. */
    fun entry(name: String) {
        entries -= 1

        if (entries < 0) {
            throw RefusedZipException(words.string(R.string.data_zip_too_many_files))
        }

        checkEntryName(name, words)
    }

    fun take(count: Int) {
        bytes -= count

        if (bytes < 0) {
            throw RefusedZipException(words.string(R.string.data_zip_too_big))
        }
    }

    /** Refuses a file of the zip too big to be part of a map. */
    fun refuseFile(): Nothing = throw RefusedZipException(words.string(R.string.data_zip_file_too_big))
}

/** Refuses a file in a zip named to land outside it, as `../` or a path from the root would: no map has one. */
internal fun checkEntryName(name: String, words: Words) {
    val parts = name.split('/', '\\')

    if (name.startsWith("/") || name.startsWith("\\") || name.contains('\u0000') || parts.any { it == ".." } || DRIVE.containsMatchIn(name)) {
        throw RefusedZipException(words.string(R.string.data_zip_points_outside))
    }
}

private val DRIVE = Regex("^[A-Za-z]:")

/**
 * The file `name` names inside `directory`, refusing a name that resolves outside it. The names
 * are checked as they're read already; this is the check Google Play looks for where a zip is
 * unpacked, made on the path the file is written to.
 */
internal fun fileInside(directory: File, name: String, words: Words): File {
    val root = directory.canonicalFile
    val file = File(root, name).canonicalFile

    if (!file.path.startsWith(root.path + File.separator)) {
        throw RefusedZipException(words.string(R.string.data_zip_points_outside))
    }

    return file
}

/** An entry of a zip, refusing one bigger than `limit`, and counting it in `budget`. */
private fun ZipInputStream.readLimited(budget: ZipBudget, limit: Long = ZIP_ENTRY_LIMIT): ByteArray {
    val output = ByteArrayOutputStream()
    copyLimited(output, budget, limit)
    return output.toByteArray()
}

/** Copies an entry of a zip into `output`, refusing one bigger than `limit`, and counting it in `budget`. */
private fun ZipInputStream.copyLimited(output: OutputStream?, budget: ZipBudget, limit: Long = ZIP_ENTRY_LIMIT) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L

    while (true) {
        val read = read(buffer)

        if (read < 0) {
            break
        }

        total += read

        if (total > limit) {
            budget.refuseFile()
        }

        budget.take(read)
        output?.write(buffer, 0, read)
    }
}

/** Reads past an entry of a zip, counting it in `budget`: skipping one unpacks it all the same. */
private fun ZipInputStream.skipEntry(budget: ZipBudget) = copyLimited(null, budget, Long.MAX_VALUE)

/** The pictures a map's text points at, by name in its media folder. */
fun picturesIn(markdown: String): Set<String> =
    PICTURE_REFERENCE.findAll(markdown).map { it.groupValues[1] }.filter { PICTURE_NAME.matches(it) }.toSet()

private val PICTURE_REFERENCE = Regex("$MEDIA_DIRECTORY/([A-Za-z0-9][A-Za-z0-9._-]*)")

internal fun digest(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

/** A picture or video a map's text points to: `media/` and a name of Orbit's own. */
private val MediaReference = Regex("""\bmedia/([A-Za-z0-9][A-Za-z0-9._-]*)""")

/** The names of the pictures and videos `markdown` points to in its map's `media/`, each once. */
internal fun mediaNamesIn(markdown: String): List<String> = MediaReference.findAll(markdown).map { it.groupValues[1] }.distinct().toList()
