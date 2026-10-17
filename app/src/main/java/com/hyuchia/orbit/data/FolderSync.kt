package com.hyuchia.orbit.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A folder the maps are kept in, as markdown files: one per map, with the
 * pictures and videos they point at in its `media/` folder. [DocumentFolder]
 * is one the user chose on the device.
 */
interface SyncFolder {
    /** The folder's own name, to show. */
    val name: String

    /** The markdown files in the folder. Throws when it can't be read. */
    fun files(): List<FolderFile>

    fun read(name: String): String

    /**
     * Writes a file's text, making the file when `create` (the folder may
     * name it differently if the name's taken), and returns it as it is now.
     */
    fun write(name: String, text: String, create: Boolean): FolderFile

    fun delete(name: String)

    /** The pictures and videos in the folder's `media/`: their names, and their sizes (-1 when the folder can't tell). */
    fun media(): Map<String, Long>

    /** Copies a picture or video from the folder's `media/` into `into`; false when it isn't there. */
    fun readMedia(name: String, into: OutputStream): Boolean

    /** Puts a picture or video into the folder's `media/`, over one by that name if it has one, a piece at a time. */
    fun writeMedia(name: String, from: File)
}

/** A markdown file in the folder: its name, when it was last written (0 when the folder can't tell), and its size. */
data class FolderFile(val name: String, val modified: Long, val size: Long)

/**
 * A map here, as a sync compares it: its text's [digest], and its file in
 * the folder when it has one. A map whose text can't be read (`readable`
 * false) is left alone, and so is its file.
 */
internal data class LocalMap(val id: String, val hash: String, val record: SyncRecord?, val readable: Boolean = true)

/**
 * A file in the folder, with its text when it had to be read: new to Orbit,
 * or changed since it was last compared. An `unreadable` one is there but
 * couldn't be read this time: nothing happens to it, or to its map, until
 * a later sync reads it.
 */
internal data class RemoteFile(val file: FolderFile, val text: String?, val unreadable: Boolean = false) {
    val name: String get() = file.name
    val hash: String? = text?.let(::digest)
}

/** What a sync does, as [planSync] plans it. */
internal sealed interface SyncStep {
    /** The map changed here, or isn't in the folder yet: its file takes its text (a new file when `file` is null). */
    data class Write(val id: String, val file: String?) : SyncStep

    /** Its file changed in the folder: the map takes the file's text, while it's still the text compared (`expected`). */
    data class Take(val id: String, val file: RemoteFile, val expected: String) : SyncStep

    /** The map and the file say the same: from now on the map follows the file. */
    data class Link(val id: String, val file: RemoteFile) : SyncStep

    /** Changed here and in the folder: the map keeps its own text, and the file's becomes a map of its own. */
    data class KeepBoth(val id: String, val file: RemoteFile) : SyncStep

    /**
     * Its file has been gone from the folder for two syncs at least
     * [MISSING_CONFIRMATION] apart, and the map hadn't changed since: it
     * follows the file into the trash.
     */
    data class Remove(val id: String) : SyncStep

    /** Its file is gone from the folder, for the first time: a later sync has to agree before the map follows it. */
    data class Missing(val id: String, val record: SyncRecord) : SyncStep

    /** Its file is back in the folder after a sync missed it. */
    data class Found(val id: String, val record: SyncRecord) : SyncStep

    /** A file new in the folder: it becomes a map. */
    data class Add(val file: RemoteFile) : SyncStep

    /** A map deleted here, its file in the folder as the map left it: the file goes too. */
    data class DeleteFile(val record: SyncRecord) : SyncStep

    /** A map deleted here whose file is gone already, or changed in the folder since: then it comes back as a new map. */
    data class Forget(val record: SyncRecord) : SyncStep
}

/**
 * How long after a sync first misses a map's file a later sync must still
 * miss it before the map follows it into the trash: a folder still being
 * downloaded, or listed in part, deletes nothing.
 */
internal const val MISSING_CONFIRMATION = 10 * 60 * 1000L

/**
 * Compares the maps here with the files in the folder at `folder` and plans
 * what brings them in step. A map's record says how both sides were when
 * they last matched, so the side that changed since wins, and when both did,
 * both are kept. A file renamed in the folder keeps its map. A map follows
 * its file out of the folder only once two syncs at least
 * [MISSING_CONFIRMATION] apart found it gone, and then into the trash. When
 * none of the maps' files is there anymore, the folder was more likely
 * emptied or swapped than every file deleted, so the maps are written to it
 * again; so is a map open on a screen (`onScreen`), which mustn't vanish
 * while it's used. Files that couldn't be read, and maps whose text couldn't
 * be, are left for a later sync.
 */
internal fun planSync(
    folder: String,
    maps: List<LocalMap>,
    files: List<RemoteFile>,
    deleted: List<SyncRecord>,
    onScreen: Set<String> = emptySet(),
    now: Long = System.currentTimeMillis(),
): List<SyncStep> {
    val steps = mutableListOf<SyncStep>()
    val byName = files.associateBy { it.name }
    val claimed = files.filter { it.unreadable }.mapTo(mutableSetOf()) { it.name }

    for (record in deleted) {
        val file = byName[record.file]?.takeIf { record.folder == folder }

        if (file != null && file.unreadable) {
            continue
        }

        if (file != null && (file.hash ?: record.hash) == record.hash) {
            claimed += file.name
            steps += SyncStep.DeleteFile(record)
        } else {
            steps += SyncStep.Forget(record)
        }
    }

    val linked = maps.mapNotNull { map -> map.record?.takeIf { it.folder == folder }?.let { map to it } }
    // A map whose text can't be read keeps its file, untouched, and does nothing else.
    linked.filter { (map, _) -> !map.readable }.forEach { (_, record) -> claimed += record.file }
    val readableLinked = linked.filter { (map, _) -> map.readable }
    val missing = readableLinked.filter { (_, record) -> record.file !in byName }
    val linkedIds = linked.mapTo(HashSet()) { (map, _) -> map.id }
    val unlinked = maps.filterTo(mutableListOf()) { it.id !in linkedIds && it.readable }

    for ((map, record) in readableLinked) {
        val file = byName[record.file] ?: continue

        if (file.unreadable) {
            continue
        }

        if (file.name in claimed) {
            // Another map's file already: this one gets a file of its own.
            unlinked += map
            continue
        }

        claimed += file.name
        val remote = file.hash ?: record.hash

        val step = when {
            map.hash == remote -> if (file.text != null) SyncStep.Link(map.id, file) else null
            remote == record.hash -> SyncStep.Write(map.id, file.name)
            map.hash == record.hash -> SyncStep.Take(map.id, file, map.hash)
            else -> SyncStep.KeepBoth(map.id, file)
        }

        when {
            step != null -> steps += step
            // Back after a sync missed it: nothing else to do, so its record only forgets that.
            record.missingSince != null -> steps += SyncStep.Found(map.id, record)
        }
    }

    val unclaimed = files.filterTo(mutableListOf()) { it.name !in claimed }
    val swapped = files.isEmpty() || (readableLinked.size > 1 && missing.size == readableLinked.size)

    for ((map, record) in missing) {
        val renamed = unclaimed.firstOrNull { it.hash == record.hash }
        val since = record.missingSince

        when {
            renamed != null -> {
                unclaimed -= renamed
                steps += SyncStep.Link(map.id, renamed)

                if (map.hash != record.hash) {
                    steps += SyncStep.Write(map.id, renamed.name)
                }
            }
            map.hash != record.hash || swapped || map.id in onScreen -> steps += SyncStep.Write(map.id, null)
            since == null -> steps += SyncStep.Missing(map.id, record)
            now - since >= MISSING_CONFIRMATION -> steps += SyncStep.Remove(map.id)
            // Missed once already: a later sync decides.
            else -> Unit
        }
    }

    for (map in unlinked) {
        // The same text in a file already, as when a second device starts keeping its maps in the folder.
        val same = unclaimed.firstOrNull { it.hash == map.hash }

        if (same != null) {
            unclaimed -= same
            steps += SyncStep.Link(map.id, same)
        } else {
            steps += SyncStep.Write(map.id, null)
        }
    }

    unclaimed.forEach { steps += SyncStep.Add(it) }
    return steps
}

/** How keeping maps in a folder is going, for Settings. */
data class FolderSyncState(
    /** The folder's address, or null when the maps aren't kept in a folder. */
    val folder: String? = null,

    /** The folder's name, once Orbit has reached it. */
    val name: String? = null,

    val syncing: Boolean = false,

    /** When the maps and the folder were last brought in step. */
    val synced: Long? = null,

    /** What went wrong the last time, if anything. */
    val problem: String? = null,

    /** What syncs did that the user should hear about, until they've read it. */
    val notes: FolderNotes = FolderNotes(),
)

/** What syncs did on their own that the user should hear about: kept until dismissed, across restarts. */
@Serializable
data class FolderNotes(
    /** Maps changed both here and in the folder: both kept, these are the folder's. */
    val keptBoth: List<String> = emptyList(),

    /** Maps moved to the trash as their files were deleted from the folder. */
    val removed: List<String> = emptyList(),

    /** Maps whose files were emptied in the folder: the maps kept their ideas, and the files got them back. */
    val restored: List<String> = emptyList(),

    /** Pictures and videos too big to join their maps, so they're only in the folder: "name, in “title”". */
    val tooBig: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = keptBoth.isEmpty() && removed.isEmpty() && restored.isEmpty() && tooBig.isEmpty()

    operator fun plus(other: FolderNotes) = FolderNotes(
        keptBoth = (keptBoth + other.keptBoth).distinct(),
        removed = (removed + other.removed).distinct(),
        restored = (restored + other.restored).distinct(),
        tooBig = (tooBig + other.tooBig).distinct(),
    )
}

private val notesJson = Json { ignoreUnknownKeys = true }

/**
 * Keeps the maps in a folder the user chose, as markdown files, both ways:
 * a map changed here is written to its file, and a file changed in the
 * folder (by a sync app, a computer, another device) changes its map, which
 * keeps what it was in its history. New files become maps, each kept as it
 * came in; deleting a map deletes its file, and a file gone from the folder
 * (for two syncs) sends its map to the trash. A map changed on both sides is
 * kept twice. Nothing written to the folder is trusted until it's read back.
 * It runs as Orbit comes to the foreground and leaves it, every couple of
 * minutes while it's open, and once edits settle. What it reports, and
 * the titles it gives maps, are in `words`.
 */
class FolderSync(
    private val maps: MapRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,

    /** Where [FolderNotes] are kept. */
    private val notesFile: File,

    private val words: Words,
    private val open: (String) -> SyncFolder,
) {
    private val status = MutableStateFlow(FolderSyncState())
    val state: StateFlow<FolderSyncState> = status.asStateFlow()

    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** Starts keeping the maps in the folder, when there is one, following the app's `lifecycle` in and out of the foreground. */
    @OptIn(FlowPreview::class)
    fun start(lifecycle: Lifecycle) {
        scope.launch {
            val notes = withContext(Dispatchers.IO) { readNotes() }
            status.update { it.copy(notes = notes) }

            for (request in requests) {
                sync()
            }
        }

        scope.launch {
            settings.settings.map { it.syncFolder }.distinctUntilChanged().collect { folder ->
                status.update { FolderSyncState(folder = folder, notes = it.notes) }
                request()
            }
        }

        scope.launch {
            var ids: Set<String>? = null

            maps.maps.filterNotNull().debounce(SETTLE).collect { summaries ->
                val folder = status.value.folder
                val now = summaries.mapTo(HashSet()) { it.id }
                // A map edited or added since it matched its file, or one deleted.
                val changed = summaries.any { map -> map.metadata.sync.let { it == null || it.folder != folder || it.hash != map.contentKey } }

                if (folder != null && (changed || now != ids)) {
                    request()
                }

                ids = now
            }
        }

        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    request()
                    delay(INTERVAL)
                }
            }
        }

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    request()
                }
            },
        )
    }

    /** Brings the maps and the folder in step soon, if the maps are kept in one. */
    fun request() {
        requests.trySend(Unit)
    }

    /** The user has read the notes: they go. */
    fun dismissNotes() {
        status.update { it.copy(notes = FolderNotes()) }
        scope.launch(Dispatchers.IO) { notesFile.delete() }
    }

    /** A folder's name and how many markdown files it has, before the maps are kept there. */
    suspend fun inspect(address: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        val folder = open(address)
        folder.name to folder.files().size
    }

    private fun readNotes(): FolderNotes =
        runCatching { notesJson.decodeFromString(FolderNotes.serializer(), notesFile.readText()) }.getOrDefault(FolderNotes())

    private suspend fun sync() {
        val address = settings.settings.first().syncFolder ?: return
        maps.maps.filterNotNull().first()

        if (!maps.writable.value) {
            return
        }

        status.update { if (it.folder == address) it.copy(syncing = true) else it }

        val outcome = try {
            withContext(Dispatchers.IO) { Run(address, open(address)).sync() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            Outcome(problem = words.string(R.string.data_folder_unreachable))
        } catch (failure: Exception) {
            Outcome(problem = words.string(R.string.data_folder_unreadable))
        }

        val notes = status.value.notes + outcome.notes

        if (!outcome.notes.isEmpty) {
            withContext(Dispatchers.IO) {
                runCatching { writeAtomically(notesFile, notesJson.encodeToString(FolderNotes.serializer(), notes)) }
            }
        }

        status.update { current ->
            if (current.folder != address) {
                current
            } else {
                current.copy(
                    name = outcome.name ?: current.name,
                    syncing = false,
                    synced = if (outcome.problem == null) System.currentTimeMillis() else current.synced,
                    problem = outcome.problem,
                    notes = notes,
                )
            }
        }
    }

    private class Outcome(
        val name: String? = null,
        val problem: String? = null,
        val notes: FolderNotes = FolderNotes(),
    )

    /** One sync with the folder at `address`. */
    private inner class Run(private val address: String, private val folder: SyncFolder) {
        /** The names of the folder's files, for new ones to be unlike. */
        private val names = mutableSetOf<String>()

        private val pictures: MutableMap<String, Long> by lazy { folder.media().toMutableMap() }
        private val keptBoth = mutableListOf<String>()
        private val removed = mutableListOf<String>()
        private val restored = mutableListOf<String>()
        private val tooBig = mutableListOf<String>()
        private var failed = 0

        suspend fun sync(): Outcome {
            val name = folder.name
            val listing = folder.files()
            names += listing.map { it.name }
            val summaries = maps.maps.value.orEmpty()
            val deleted = maps.folderDeletions()
            // A file just as it was when it last matched needn't be read again.
            val known = (summaries.mapNotNull { it.metadata.sync } + deleted).filter { it.folder == address }.associateBy { it.file }
            var unreadable = 0

            val files = listing.map { file ->
                val record = known[file.name]
                val unchanged = record != null && file.modified != 0L && file.modified == record.modified && file.size == record.size

                if (unchanged) {
                    RemoteFile(file, null)
                } else {
                    // One file that can't be read waits for the next sync; the others go on.
                    try {
                        RemoteFile(file, folder.read(file.name))
                    } catch (failure: IOException) {
                        unreadable += 1
                        RemoteFile(file, null, unreadable = true)
                    } catch (failure: IllegalArgumentException) {
                        unreadable += 1
                        RemoteFile(file, null, unreadable = true)
                    }
                }
            }

            val local = summaries.map { LocalMap(it.id, it.contentKey, it.metadata.sync, readable = !it.unreadable) }

            for (step in planSync(address, local, files, deleted, maps.onScreen(), System.currentTimeMillis())) {
                try {
                    perform(step)
                } catch (failure: IOException) {
                    failed += 1
                } catch (failure: IllegalArgumentException) {
                    failed += 1
                }
            }

            val problems = listOfNotNull(
                failed.takeIf { it > 0 }?.let { count -> words.plural(R.plurals.data_folder_maps_not_in_step, count, count) },
                unreadable.takeIf { it > 0 }?.let { count -> words.plural(R.plurals.data_folder_files_unreadable, count, count) },
            )
            // Each problem is a sentence of its own, and so is what Orbit does about them.
            val problem = problems.takeIf { it.isNotEmpty() }?.joinToString(" ", postfix = " " + words.string(R.string.data_folder_tries_again))
            return Outcome(name, problem, FolderNotes(keptBoth, removed, restored, tooBig))
        }

        private suspend fun perform(step: SyncStep) {
            when (step) {
                is SyncStep.Write -> write(step.id, step.file)
                is SyncStep.Take -> {
                    val text = step.file.text ?: folder.read(step.file.name)

                    if (!keepsMap(step.id, step.file.name, text)) {
                        takePictures(step.id, text)
                        maps.replaceFromFolder(step.id, text, record(step.file.file, digest(text)), step.expected)
                    }
                }
                is SyncStep.Link -> {
                    val hash = step.file.hash ?: digest(maps.read(step.id))
                    maps.setSyncRecord(step.id, record(step.file.file, hash))
                }
                is SyncStep.KeepBoth -> {
                    val text = step.file.text ?: folder.read(step.file.name)

                    if (keepsMap(step.id, step.file.name, text)) {
                        return
                    }

                    val title = words.string(R.string.data_changed_elsewhere_title, MapText.outline(text, words).title)
                    val copy = maps.add(MapText.retitle(text, title, words), original = text)
                    takePictures(copy, text)
                    write(copy, null)
                    write(step.id, step.file.name)
                    keptBoth += title
                }
                is SyncStep.Remove -> {
                    val title = maps.summary(step.id)?.title
                    // No record left, so deleting the map doesn't go on to delete a file. It waits in the trash.
                    maps.setSyncRecord(step.id, null)
                    maps.delete(step.id)
                    title?.let { removed += it }
                }
                is SyncStep.Missing -> maps.setSyncRecord(step.id, step.record.copy(missingSince = System.currentTimeMillis()))
                is SyncStep.Found -> maps.setSyncRecord(step.id, step.record.copy(missingSince = null))
                is SyncStep.Add -> {
                    val text = step.file.text ?: folder.read(step.file.name)
                    val id = maps.add(text, sync = record(step.file.file, digest(text)), original = text)
                    takePictures(id, text)
                }
                is SyncStep.DeleteFile -> {
                    folder.delete(step.record.file)
                    names -= step.record.file
                    maps.forgetFolderDeletion(step.record)
                }
                is SyncStep.Forget -> maps.forgetFolderDeletion(step.record)
            }
        }

        /**
         * Whether the map keeps its text over `text` from its file, which is
         * then written again: when the file holds only the start of the map
         * (a write cut short), or was emptied while the map has ideas.
         */
        private suspend fun keepsMap(id: String, file: String, text: String): Boolean {
            val local = maps.read(id)
            val cut = text.length < local.length && local.startsWith(text)
            val emptied = !MapText.hasContent(text) && MapText.hasContent(local)

            if (!cut && !emptied) {
                return false
            }

            write(id, file)

            if (emptied) {
                maps.summary(id)?.title?.let { restored += it }
            }

            return true
        }

        /**
         * Writes the map as it is now to its file, or to a new one named for
         * its title, and reads it back: the map's record only says they match
         * once the folder holds the text.
         */
        private suspend fun write(id: String, file: String?) {
            if (!maps.exists(id)) {
                return
            }

            val markdown = maps.read(id)
            givePictures(id, markdown)
            val name = file ?: MapText.fileName(MapText.outline(markdown, words).title, names, words)
            var written = folder.write(name, markdown, create = file == null)
            names += written.name

            if (folder.read(written.name) != markdown) {
                written = folder.write(written.name, markdown, create = false)

                if (folder.read(written.name) != markdown) {
                    throw IOException("${written.name} didn't keep what was written")
                }
            }

            maps.setSyncRecord(id, record(written, digest(markdown)))
        }

        /**
         * The pictures and videos the map points at that the folder doesn't
         * have yet, or has only the start of, as a copy cut short leaves it.
         * One the folder has otherwise, changed there, say, stays as it is.
         */
        private fun givePictures(id: String, markdown: String) {
            for (name in picturesIn(markdown)) {
                val file = maps.picture(id, name) ?: continue
                val there = pictures[name]

                if (there == null || (there in 0 until file.length() && cutShort(name, file))) {
                    folder.writeMedia(name, file)
                    pictures[name] = file.length()
                }
            }
        }

        /** Whether the folder's `name` holds only the start of `file`. */
        private fun cutShort(name: String, file: File): Boolean = file.inputStream().buffered().use { local ->
            val start = StartOf(local)

            try {
                folder.readMedia(name, start)
            } catch (different: Different) {
                false
            }
        }

        /**
         * The pictures and videos a text from the folder points at that the
         * map doesn't have yet. One that would be more than a map keeps, as
         * one from the phone would ([mediaRoom]), stays only in the folder,
         * where it's left as it is, and the notes say so: the map then
         * still fits in its zip, and comes back from it.
         */
        private suspend fun takePictures(id: String, markdown: String) {
            var carried = maps.mediaSize(id, markdown)

            for (name in picturesIn(markdown)) {
                val size = pictures[name] ?: continue

                if (maps.picture(id, name) != null) {
                    continue
                }

                val room = mediaRoom(carried)

                try {
                    if (size > room) {
                        throw MediaRefusedException(words.string(R.string.data_more_than_a_map_keeps))
                    }

                    carried += maps.addMediaFile(id, name, room) { output -> folder.readMedia(name, output) }
                } catch (refused: MediaRefusedException) {
                    tooBig += words.string(R.string.data_folder_too_big_file, name, MapText.outline(markdown, words).title)
                }
            }
        }

        private fun record(file: FolderFile, hash: String) = SyncRecord(address, file.name, hash, file.modified, file.size)
    }
}

/** Takes bytes only while they're the ones `original` starts with, and throws [Different] at the first that isn't. */
private class StartOf(private val original: java.io.InputStream) : OutputStream() {
    override fun write(byte: Int) {
        if (original.read() != (byte and 0xFF)) {
            throw Different()
        }
    }
}

private class Different : IOException()

/** How long the maps stay still before a sync, so typing doesn't write the folder on every letter. */
private val SETTLE = 4.seconds

/** How often the folder is checked for changes while Orbit is open. */
private val INTERVAL = 2.minutes
