package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.io.IOException
import java.util.UUID

/** What the app remembers about a map, in its folder (see [MapMetadata]). */
internal const val METADATA_FILE = "meta.json"

/** How the maps' own JSON files are read and written: what a newer version added is skipped, and defaults aren't written. */
internal val mapsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * What the parts of [MapRepository] share: where the maps, the trash and
 * the maps' histories are kept, the one lock every change to them is made
 * under, the maps and the trash as they're known, and the helpers that read
 * and write a map's folder. Those that write expect the lock held.
 */
internal class MapStore(
    val directory: File,
    val trashDirectory: File,
    val historyDirectory: File,

    /** Files of deleted maps, still to be deleted from the folder maps are kept in. */
    val tombstones: File,

    val words: Words,
    val clock: () -> Long,

    /** When Orbit was installed here: a map can't have gone to the trash before, so one dated earlier was deleted with the clock wrong. */
    val installed: Long,

    /**
     * Whether the clock can be trusted to age the history and the trash by:
     * not while the network's time, where the phone knows it, says otherwise.
     * Nothing is thinned out or emptied for good while it can't.
     */
    val clockTrusted: () -> Boolean,
) {
    val mutex = Mutex()
    val summaries = MutableStateFlow<List<MapSummary>?>(null)
    val trashed = MutableStateFlow<List<TrashedMap>>(emptyList())
    val canWrite = MutableStateFlow(true)
    val replacedMaps = MutableSharedFlow<String>(extraBufferCapacity = 16)

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

    /** The map in the list of maps, once they're read. */
    fun summary(id: String): MapSummary? = summaries.value?.firstOrNull { it.id == id }

    /** The pictures, videos and sounds the map's text points at, that its folder has: what its zip carries. */
    fun mediaFiles(folder: File, markdown: String): List<File> {
        val media = File(folder, MEDIA_DIRECTORY)
        return MediaNames.references(markdown).filter(::isMediaName).map { File(media, it) }.filter { it.isFile }
    }

    /** How many bytes the pictures, videos and sounds the map has that its text, or `markdown`, points at come to. */
    fun mediaSize(id: String, markdown: String? = null): Long {
        val folder = folder(id)
        val text = markdown ?: runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        return mediaFiles(folder, text).sumOf { it.length() }
    }

    fun checkWritable() {
        if (!canWrite.value) {
            throw IOException(words.string(R.string.data_newer_version_keeps_maps))
        }
    }

    /**
     * Makes a map's folder whole before it shows up: filled in a hidden
     * folder, then given its name in one step, so a crash midway leaves no
     * half a map behind (and nothing with another map's identity). Each fill
     * writes the map's details first, so a private map is private from its
     * first file on; backups never take the hidden folder at all.
     */
    fun settle(id: String, fill: (File) -> Unit): File {
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

    suspend fun updateMetadata(id: String, change: (MapMetadata) -> MapMetadata) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)

            if (!File(folder, MAP_FILE).isFile || !canWrite.value) {
                return@withLock
            }

            writeMetadata(folder, change(readMetadataToChange(folder)))
            replace(summarize(folder))
        }
    }

    fun summarize(folder: File): MapSummary {
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
     * What the app remembers about the map, for what only reads it. Details
     * that can't be read, damaged or out of reach for now, read as a private
     * map's ([MetadataFile.unreadable]): the map may be private, so nothing
     * shows it unlocked.
     */
    fun readMetadata(folder: File): MapMetadata = MetadataFile.readOrPrivate(File(folder, METADATA_FILE), freshMetadata(folder))

    /**
     * The map's details, to change and write back. Throws [IOException] when
     * its `meta.json` is there but can't be read now: what it holds isn't
     * known, so a change never writes fresh details over it. Damaged ones
     * read as a private map's, and start afresh as they're written over
     * ([writeMetadata]).
     */
    fun readMetadataToChange(folder: File): MapMetadata = MetadataFile.read(File(folder, METADATA_FILE), freshMetadata(folder))

    /** The details of a map that has none yet. */
    private fun freshMetadata(folder: File): MapMetadata = MapMetadata(created = File(folder, MAP_FILE).lastModified())

    /**
     * Writes the map's details. A `meta.json` this version can't read at all
     * is kept aside first (`meta.json.unreadable-<time>`), never lost: a
     * copy, so a write that fails leaves it where it was, still read as a
     * private map's. One that can't be read now isn't written over at all
     * ([IOException]).
     */
    fun writeMetadata(folder: File, metadata: MapMetadata) {
        val file = File(folder, METADATA_FILE)

        if (file.isFile) {
            try {
                MetadataFile.decode(file.readText())
            } catch (damaged: SerializationException) {
                file.copyTo(File(folder, "$METADATA_FILE.unreadable-${clock()}"), overwrite = true)
            }
        }

        writeAtomically(file, MetadataFile.encode(metadata))
    }

    fun insert(summary: MapSummary) {
        summaries.update { maps -> listOf(summary) + maps.orEmpty().filterNot { it.id == summary.id } }
    }

    fun replace(summary: MapSummary) {
        summaries.update { maps ->
            if (maps == null || maps.none { it.id == summary.id }) {
                listOf(summary) + maps.orEmpty()
            } else {
                maps.map { if (it.id == summary.id) summary else it }
            }
        }
    }

    /** A new map's id, unlike any map's here, in the trash, or with a history left behind. */
    fun newFolder(title: String): String {
        val taken = directory.list().orEmpty().toSet() +
            trashDirectory.list().orEmpty().map { it.substringBeforeLast('-') } +
            historyDirectory.list().orEmpty()
        return MapText.folderName(title, taken)
    }

    /**
     * The files of deleted maps still to delete from the folder, a record
     * each. Each is read on its own, so one this version can't read doesn't
     * lose the rest; one whose `private` it can't read was a private map's, so
     * its file, back in the folder, comes back private ([SyncRecord.private]).
     * Throws [IOException] when they're there but can't be read now: none is
     * taken for no deletions, so no deleted map's file comes back as a map
     * like any other, and nothing writes over them.
     */
    fun readTombstones(): List<SyncRecord> {
        if (!tombstones.isFile) {
            return emptyList()
        }

        val text = tombstones.readText()
        val records = runCatching { mapsJson.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()

        return records.mapNotNull { record -> runCatching { mapsJson.decodeFromJsonElement(SyncRecord.serializer(), record) }.getOrNull() ?: privateTombstone(record) }
    }

    /** A deleted map's record whose `private` this version can't read, as a private map's; null when the rest can't be read either. */
    private fun privateTombstone(record: JsonElement): SyncRecord? {
        val fields = record as? JsonObject ?: return null
        return runCatching { mapsJson.decodeFromJsonElement(SyncRecord.serializer(), JsonObject(fields - "private")) }.getOrNull()?.copy(private = true)
    }

    fun writeTombstones(records: List<SyncRecord>) {
        if (records.isEmpty()) {
            tombstones.delete()
        } else {
            writeAtomically(tombstones, mapsJson.encodeToString(ListSerializer(SyncRecord.serializer()), records.distinct()))
        }
    }
}

/**
 * A part of [MapRepository], such as the trash: it reaches the [MapStore]
 * it shares with the others by the names [MapRepository] has always used,
 * so its code reads as it did there.
 */
internal abstract class MapPart(protected val store: MapStore) {
    protected val directory: File get() = store.directory
    protected val trashDirectory: File get() = store.trashDirectory
    protected val historyDirectory: File get() = store.historyDirectory
    protected val words: Words get() = store.words
    protected val clock: () -> Long get() = store.clock
    protected val installed: Long get() = store.installed
    protected val clockTrusted: () -> Boolean get() = store.clockTrusted
    protected val mutex: Mutex get() = store.mutex
    protected val summaries: MutableStateFlow<List<MapSummary>?> get() = store.summaries
    protected val trashed: MutableStateFlow<List<TrashedMap>> get() = store.trashed
    protected val canWrite: MutableStateFlow<Boolean> get() = store.canWrite
    protected val replacedMaps: MutableSharedFlow<String> get() = store.replacedMaps

    protected fun folder(id: String): File = store.folder(id)

    protected fun exists(id: String): Boolean = store.exists(id)

    protected fun summary(id: String): MapSummary? = store.summary(id)

    protected fun mediaFiles(folder: File, markdown: String): List<File> = store.mediaFiles(folder, markdown)

    protected fun mediaSize(id: String, markdown: String? = null): Long = store.mediaSize(id, markdown)

    protected fun checkWritable() = store.checkWritable()

    protected fun settle(id: String, fill: (File) -> Unit): File = store.settle(id, fill)

    protected suspend fun updateMetadata(id: String, change: (MapMetadata) -> MapMetadata) = store.updateMetadata(id, change)

    protected fun summarize(folder: File): MapSummary = store.summarize(folder)

    protected fun readMetadata(folder: File): MapMetadata = store.readMetadata(folder)

    protected fun readMetadataToChange(folder: File): MapMetadata = store.readMetadataToChange(folder)

    protected fun writeMetadata(folder: File, metadata: MapMetadata) = store.writeMetadata(folder, metadata)

    protected fun insert(summary: MapSummary) = store.insert(summary)

    protected fun replace(summary: MapSummary) = store.replace(summary)

    protected fun newFolder(title: String): String = store.newFolder(title)

    protected fun readTombstones(): List<SyncRecord> = store.readTombstones()

    protected fun writeTombstones(records: List<SyncRecord>) = store.writeTombstones(records)
}
