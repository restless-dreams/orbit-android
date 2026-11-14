package com.hyuchia.orbit.data

import com.hyuchia.orbit.Logs
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
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.util.UUID

/** What the app remembers about a map, in its folder (see [MapMetadata]). */
internal const val METADATA_FILE = "meta.json"

/** A map's details that a version couldn't read at all, kept aside as it wrote new ones ([MapStore.writeMetadata]): `meta.json.unreadable-<time>`. */
internal val UNREADABLE_METADATA = Regex("""${Regex.escape(METADATA_FILE)}\.unreadable-\d+""")

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

    /** Moves a map's folder between the maps and the trash in one step, as [File.renameTo] does, and says whether it did. */
    val rename: (File, File) -> Boolean = { file, to -> file.renameTo(to) },

    /** The folder the maps are kept in now, as the settings say, or null when they aren't kept in one. */
    val syncFolder: suspend () -> String? = { null },

    /** Hears each name held for a file coming into a map's media folder ([IncomingMedia.Names]). */
    holding: (String) -> Unit = {},
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

    /** The pictures, videos, sounds and files the map's text points at, that its folder has: what its zip carries ([carriedNames]). */
    fun mediaFiles(folder: File, markdown: String): List<File> {
        val media = File(folder, MEDIA_DIRECTORY)
        return carriedNames(markdown).map { File(media, it) }.filter { it.isFile }
    }

    /**
     * The pictures, videos, sounds and files of the map's that go with it to
     * other people, that its folder has ([sharedNames]): each in its media
     * folder itself, never one a link there leads to ([mapMediaFile]).
     */
    fun sharedFiles(folder: File, markdown: String): List<File> = sharedNames(markdown).mapNotNull { mapMediaFile(folder, it) }

    /** What's on its way into the maps' folders, which their texts don't point at yet (see [IncomingMedia]). */
    val incoming = IncomingMedia(holding)

    /**
     * How many bytes the pictures, videos and sounds the map has that its
     * text, or `markdown`, points at come to, with what's on its way into its
     * folder ([incoming]) but `besides`, leaving out the file named `except`
     * in its media folder: one that's counted on its own, as a picture is
     * with the motion it's given.
     */
    fun mediaSize(id: String, markdown: String? = null, except: String? = null, besides: IncomingMedia.Arrival? = null): Long {
        val folder = folder(id)
        val text = markdown ?: runCatching { File(folder, MAP_FILE).readTextSafely() }.getOrDefault("")
        val pointedAt = carriedNames(text).toSet()
        val media = File(folder, MEDIA_DIRECTORY)
        val own = pointedAt.filter { it != except }.map { File(media, it) }.filter { it.isFile }.sumOf { it.length() }
        // What the map's own text points at is the map's: what came in for it counts no more as coming in.
        return own + incoming.taken(id, pointedAt + listOfNotNull(except), besides, settling = markdown == null)
    }

    /**
     * Lets a copy start into map `id`, taking as much of its room as `take`
     * says, given what the map comes to with all else on its way in; `take`
     * refuses it with a [MediaRefusedException] instead. It's counted and
     * taken at the same moment, so two copies at once never both take the
     * last of the room. The file named `except` in its media folder is left
     * out of what the map comes to, as [mediaSize] leaves it. Close the
     * arrival when the copy didn't come in, and say what did once it's in
     * ([arrived]).
     */
    fun arriving(id: String, except: String? = null, take: (mapMedia: Long) -> Long): IncomingMedia.Arrival = synchronized(incoming) {
        val mapMedia = mediaSize(id, except = except)
        incoming.add(id, take(mapMedia), mapMedia)
    }

    /**
     * The copy `arrival` let in is in, as `files`: they count against the
     * map's room by their own sizes until its text points at them. When
     * `refusal` says why they don't fit after all, given what the map comes
     * to with all else on its way in, as for a file that came out bigger than
     * it said, they go, and they're refused with a [MediaRefusedException].
     */
    fun arrived(arrival: IncomingMedia.Arrival, files: List<File>, refusal: ((mapMedia: Long) -> String?)? = null) {
        synchronized(incoming) {
            refusal?.invoke(mediaSize(arrival.id, besides = arrival))?.let { problem ->
                files.forEach(File::delete)
                arrival.close()
                throw MediaRefusedException(problem)
            }

            arrival.arrived(files)
        }
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

    /**
     * Makes several maps' folders whole before any of them shows up, all or
     * none: each filled in a hidden folder, as [settle] fills one (`fill`
     * hears its place in `ids`), and only once every one is are they given
     * their names, `ids` in order. Whatever stops it leaves none of them, and
     * none of their hidden folders: one named already, never listed yet, goes
     * back.
     */
    fun settleAll(ids: List<String>, fill: (index: Int, staging: File) -> Unit): List<File> {
        val stagings = ids.map { File(directory, ".new-${UUID.randomUUID()}") }
        val named = mutableListOf<File>()

        try {
            stagings.forEachIndexed { index, staging ->
                staging.mkdirs()
                fill(index, staging)
            }

            ids.forEachIndexed { index, id ->
                val folder = folder(id)

                if (!stagings[index].renameTo(folder)) {
                    throw IOException("Couldn't add the map $id")
                }

                named += folder
            }

            return named
        } catch (failed: Throwable) {
            named.forEachIndexed { index, folder ->
                if (!folder.renameTo(stagings[index])) {
                    folder.deleteRecursively()
                }
            }

            throw failed
        } finally {
            stagings.forEach { it.deleteRecursively() }
        }
    }

    /**
     * Changes the map's details as they are now, under the lock; a change
     * that gives them back as they were writes nothing. A map that's gone
     * takes no change, and neither do the maps while a newer Orbit keeps
     * them: quietly, or, `strict`, with the [IOException] [checkWritable]
     * gives, for a change that has to be written or tried again.
     */
    suspend fun updateMetadata(id: String, strict: Boolean = false, change: (MapMetadata) -> MapMetadata) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)

            if (!File(folder, MAP_FILE).isFile) {
                return@withLock
            }

            if (!canWrite.value) {
                if (strict) {
                    checkWritable()
                }

                return@withLock
            }

            val current = readMetadataToChange(folder)
            val changed = change(current)

            if (changed !== current) {
                writeMetadata(folder, changed)
                replace(summarize(folder))
            }
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
            missingMedia = missingMedia(folder, markdown),
        )
    }

    /** The pictures, videos, sounds and files `markdown` points at that the map in `folder` doesn't have ([MapSummary.missingMedia]); its media is listed once. */
    private fun missingMedia(folder: File, markdown: String): Set<String> {
        val wanted = carriedNames(markdown)

        if (wanted.isEmpty()) {
            return emptySet()
        }

        val here = File(folder, MEDIA_DIRECTORY).list().orEmpty().toHashSet()
        return wanted.filterNotTo(HashSet()) { it in here }
    }

    /**
     * Media among `names` came into map `id`'s folder, from the folder maps
     * are kept in or a zip: those its text points at are missing no more
     * ([MapSummary.missingMedia]), and its picture on Home is drawn again
     * with them. Expects the lock held.
     */
    fun mediaArrived(id: String, names: Set<String>) {
        val summary = summary(id) ?: return
        val media = File(folder(id), MEDIA_DIRECTORY)
        val arrived = summary.missingMedia.filterTo(HashSet()) { it in names && File(media, it).isFile }

        if (arrived.isNotEmpty()) {
            replace(summary.copy(missingMedia = summary.missingMedia - arrived))
        }
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
    fun newFolder(title: String): String = MapText.folderName(title, takenIds())

    /** The ids a new map can't have: those of the maps here, in the trash, and with a history left behind. */
    fun takenIds(): MutableSet<String> =
        (directory.list().orEmpty().asList() + trashDirectory.list().orEmpty().map { it.substringBeforeLast('-') } + historyDirectory.list().orEmpty()).toHashSet()

    /**
     * The files of deleted maps still to delete from the folder, a record
     * each. Each is read on its own: one whose `private` this version can't
     * read was a private map's, so its file, back in the folder, comes back
     * private ([SyncRecord.private]). One that can't be read whole, as
     * damage leaves one (its `file` reshaped, say, or not a record at all),
     * neither stops the rest nor is dropped: what of it can be read is kept,
     * as a private map's deletion, since it may have been one, and as an
     * untold one without its `file` ([SyncRecord.untold]). Throws
     * [IOException] when they're there but can't be read, now or at all
     * (damaged, not a list of records): none is taken for no deletions, so
     * no deleted map's file comes back as a map like any other, and nothing
     * writes over them.
     */
    fun readTombstones(): List<SyncRecord> = readTombstoneFields().map { it.record }

    /** One of the folder's deletions as read: what this version reads of it, as it's written, and its fields this version couldn't read. */
    private class Tombstone(val record: SyncRecord, val written: JsonElement, val unread: Set<String> = emptySet())

    /** Each of the folder's deletions as [readTombstones] reads it, with its fields as they're written: what a newer version added to it included. */
    private fun readTombstoneFields(): List<Tombstone> {
        if (!tombstones.isFile) {
            return emptyList()
        }

        val text = tombstones.readText()

        val records = try {
            mapsJson.parseToJsonElement(text).jsonArray
        } catch (damaged: IllegalArgumentException) {
            throw IOException("The folder's deletions can't be read", damaged)
        }

        return records.map { record ->
            val fields = record as? JsonObject
            val read = fields?.let { runCatching { mapsJson.decodeFromJsonElement(SyncRecord.serializer(), it) }.getOrNull() ?: privateTombstone(it) }
            read?.let { Tombstone(it, record) } ?: salvagedTombstone(record)
        }
    }

    /** A deleted map's record whose `private` this version can't read, as a private map's; null when the rest can't be read either. */
    private fun privateTombstone(fields: JsonObject): SyncRecord? =
        runCatching { mapsJson.decodeFromJsonElement(SyncRecord.serializer(), JsonObject(fields - "private")) }.getOrNull()?.copy(private = true)

    /**
     * A deleted map's record that can't be read whole: each of its fields
     * this version can read, a private map's, and those it can't kept as
     * they're written, for when the record is written again.
     */
    private fun salvagedTombstone(written: JsonElement): Tombstone {
        val fields = written as? JsonObject ?: JsonObject(emptyMap())

        fun text(name: String): String? = (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun number(name: String): Long? = (fields[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

        val record = SyncRecord(
            folder = text("folder").orEmpty(),
            file = text("file").orEmpty(),
            hash = text("hash").orEmpty(),
            modified = number("modified") ?: 0,
            size = number("size") ?: -1,
            missingSince = number("missingSince"),
            writing = text("writing"),
            private = true,
            install = text("install"),
        )

        val read = mapsJson.encodeToJsonElement(SyncRecord.serializer(), record).jsonObject
        val known = SyncRecord.serializer().descriptor.elementNames.toSet()
        return Tombstone(record, written, fields.keys.filterTo(HashSet()) { it in known && fields[it] != read[it] })
    }

    /**
     * Writes the folder's deletions. A record that was there already is
     * written as it was, with what a newer version added to it, or a
     * `private` this one can't read; one changed here (its file found
     * missing, say) keeps what a newer version added, and the fields this
     * version couldn't read as they were; only new ones are written as this
     * version writes them. Throws [IOException] when they can't be written,
     * or, with none left, deleted.
     */
    fun writeTombstones(records: List<SyncRecord>) {
        if (records.isEmpty()) {
            if (!tombstones.delete() && tombstones.exists()) {
                throw IOException("The folder's deletions couldn't be deleted")
            }

            return
        }

        val kept = runCatching { readTombstoneFields() }.getOrDefault(emptyList())

        val fields = records.distinct().map { record ->
            val written = mapsJson.encodeToJsonElement(SyncRecord.serializer(), record)
            val same = kept.firstOrNull { it.record == record }
            // One that lost its text as its file went (see [SyncRecord.textless]) is the same deletion, by its file.
            val changed = kept.firstOrNull { it.record.folder == record.folder && it.record.file == record.file && (it.record.hash == record.hash || record.textless) }
            same?.written ?: changed?.let { before -> changedTombstone(written, before) } ?: written
        }

        writeAtomically(tombstones, JsonArray(fields).toString())
    }

    /**
     * `written`, a deletion changed here, with what `before` held that this
     * version doesn't know or couldn't read, as it was: a field it couldn't
     * read only while the change left it as it read it, so one it changed
     * (`missingSince`, as its file was found missing) is written as it is now.
     */
    private fun changedTombstone(written: JsonElement, before: Tombstone): JsonElement {
        val fields = before.written as? JsonObject ?: return written
        val kept = keepUnknownFields(written, fields, SyncRecord.serializer().descriptor) as JsonObject
        val read = mapsJson.encodeToJsonElement(SyncRecord.serializer(), before.record).jsonObject
        return JsonObject(kept + fields.filterKeys { it in before.unread && kept[it] == read[it] })
    }

    /**
     * Forgets the deletions a deletion that didn't finish left: it records
     * the map's file first, then moves the map to the trash ([MapTrash.delete]),
     * so one Orbit stopped in between finds the map still among `maps`, with
     * the same file in the folder, or whose file can't be told and its very
     * text ([SyncRecord.untold]). And so does a map brought back from the
     * trash just before Orbit stopped, whose deletion was still to go. Their
     * files stay in the folder. Deletions that can't be read stay as they are.
     */
    fun forgetUnfinishedDeletions(maps: List<MapSummary>) {
        val records = try {
            readTombstones()
        } catch (unreadable: IOException) {
            return
        }

        val files = maps.mapNotNullTo(HashSet()) { map -> map.metadata.sync?.let { it.folder to it.file } }
        val untold = maps.filter { it.metadata.folderFileUnknown }.mapTo(mutableListOf()) { it.contentKey }

        val left = records.filterNot { record ->
            if (record.untold) untold.remove(record.hash) else (record.folder to record.file) in files
        }

        if (left.size != records.size) {
            runCatching { writeTombstones(left) }.onFailure { Logs.warn("MapRepository", "Couldn't forget a deletion that didn't finish", it) }
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
    protected val rename: (File, File) -> Boolean get() = store.rename
    protected val syncFolder: suspend () -> String? get() = store.syncFolder
    protected val mutex: Mutex get() = store.mutex
    protected val summaries: MutableStateFlow<List<MapSummary>?> get() = store.summaries
    protected val trashed: MutableStateFlow<List<TrashedMap>> get() = store.trashed
    protected val canWrite: MutableStateFlow<Boolean> get() = store.canWrite
    protected val replacedMaps: MutableSharedFlow<String> get() = store.replacedMaps

    protected fun folder(id: String): File = store.folder(id)

    protected fun exists(id: String): Boolean = store.exists(id)

    protected fun summary(id: String): MapSummary? = store.summary(id)

    protected fun mediaFiles(folder: File, markdown: String): List<File> = store.mediaFiles(folder, markdown)

    protected fun sharedFiles(folder: File, markdown: String): List<File> = store.sharedFiles(folder, markdown)

    protected fun mediaSize(id: String, markdown: String? = null, except: String? = null, besides: IncomingMedia.Arrival? = null): Long = store.mediaSize(id, markdown, except, besides)

    protected fun arriving(id: String, except: String? = null, take: (mapMedia: Long) -> Long): IncomingMedia.Arrival = store.arriving(id, except, take)

    protected fun arrived(arrival: IncomingMedia.Arrival, files: List<File>, refusal: ((mapMedia: Long) -> String?)? = null) = store.arrived(arrival, files, refusal)

    protected fun checkWritable() = store.checkWritable()

    protected fun settle(id: String, fill: (File) -> Unit): File = store.settle(id, fill)

    protected suspend fun updateMetadata(id: String, strict: Boolean = false, change: (MapMetadata) -> MapMetadata) = store.updateMetadata(id, strict, change)

    protected fun summarize(folder: File): MapSummary = store.summarize(folder)

    protected fun readMetadata(folder: File): MapMetadata = store.readMetadata(folder)

    protected fun readMetadataToChange(folder: File): MapMetadata = store.readMetadataToChange(folder)

    protected fun writeMetadata(folder: File, metadata: MapMetadata) = store.writeMetadata(folder, metadata)

    protected fun insert(summary: MapSummary) = store.insert(summary)

    protected fun replace(summary: MapSummary) = store.replace(summary)

    protected fun mediaArrived(id: String, names: Set<String>) = store.mediaArrived(id, names)

    protected fun newFolder(title: String): String = store.newFolder(title)

    protected fun takenIds(): MutableSet<String> = store.takenIds()

    protected fun readTombstones(): List<SyncRecord> = store.readTombstones()

    protected fun writeTombstones(records: List<SyncRecord>) = store.writeTombstones(records)
}
