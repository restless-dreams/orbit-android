package com.hyuchia.orbit.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * A file's text. Throws [StillArriving] for one that looks like it's
     * still being written, and [TooLargeForAMap] for one that holds more than
     * a map may, whatever size it was listed at.
     */
    fun read(name: String): String

    /** A file as it is now: its time and size, which say whether it changed since it was listed; null when it's gone. */
    fun describe(name: String): FolderFile? = files().firstOrNull { it.name == name }

    /**
     * Writes a file's text, making the file when `create` (the folder may
     * name it differently if the name's taken), and returns it as it is now.
     */
    fun write(name: String, text: String, create: Boolean): FolderFile

    fun delete(name: String)

    /** The pictures and videos in the folder's `media/`, by name: when each was last written (0 when the folder can't tell), and its size (-1 when it can't). */
    fun media(): Map<String, FolderFile>

    /** Copies a picture or video from the folder's `media/` into `into`; false when it isn't there. */
    fun readMedia(name: String, into: OutputStream): Boolean

    /**
     * Puts a picture or video into the folder's `media/`, over one by that
     * name if it has one, a piece at a time, and returns it as it is now:
     * under the name the folder's app gave it, which may be another (as when
     * a file by that name arrived since `media/` was listed). Throws
     * [NoRoomForMedia] when the folder is on this device and hasn't room for
     * a new one, before any of it is written.
     */
    fun writeMedia(name: String, from: File): FolderFile

    /** Deletes a picture or video from the folder's `media/`. */
    fun deleteMedia(name: String): Unit = throw IOException("This folder deletes no pictures or videos")

    /** The records devices keep of themselves in the folder's `.orbit/devices/` (see [FolderDevice]); none when it has none. */
    fun deviceRecords(): List<FolderFile> = emptyList()

    fun readDeviceRecord(name: String): String = throw FileNotFoundException(name)

    /** Writes this device's record, making `.orbit/devices/` when the folder doesn't have it yet. */
    fun writeDeviceRecord(name: String, text: String): Unit = throw IOException("This folder keeps no records")

    /** The records of how study goes that devices keep in `.orbit/study/` (see [StudyRecord]), by device; none when it has none. */
    fun studyRecords(): Map<String, List<FolderFile>> = emptyMap()

    fun readStudyRecord(device: String, name: String): String = throw FileNotFoundException(name)

    /** Writes one of this device's records, making its folder in `.orbit/study/` when the folder doesn't have it yet. */
    fun writeStudyRecord(device: String, name: String, text: String): Unit = throw IOException("This folder keeps no records")

    /** Deletes one of this device's records; another device's never is. */
    fun deleteStudyRecord(device: String, name: String): Unit = throw IOException("This folder keeps no records")
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
 * How long a map whose record another install of Orbit left (one restored
 * from a backup) waits for its file to come back to the folder before it
 * follows it out: a sync app on a new phone may take that long to fill it.
 */
internal const val FOREIGN_CONFIRMATION = 24 * 60 * 60 * 1000L

/** The most maps one sync sends to the trash as their files leave the folder, before it asks first (see [FolderSyncState.held]). */
internal const val MOST_REMOVALS = 3

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
 * while it's used. A map deleted here (`deleted`) takes its file with it,
 * unless the file changed since; one deleted while the maps were kept in
 * another folder waits until they're kept there again. Files that couldn't
 * be read, and maps whose text couldn't be, are left for a later sync. A
 * file that holds the text this device was writing to it, and hasn't seen
 * there whole yet ([SyncRecord.writing]), is this device's own: as it was
 * when they last matched, not a change from elsewhere.
 */
internal fun planSync(
    folder: String,
    maps: List<LocalMap>,
    files: List<RemoteFile>,
    deleted: List<SyncRecord>,
    onScreen: Set<String> = emptySet(),
    now: Long = System.currentTimeMillis(),
    install: String? = null,
): List<SyncStep> {
    val steps = mutableListOf<SyncStep>()
    val byName = files.associateBy { it.name }
    val claimed = files.filter { it.unreadable }.mapTo(mutableSetOf()) { it.name }

    for (record in deleted) {
        // A map deleted while the maps were kept in another folder waits for that one: its file there still goes.
        if (record.folder != folder) {
            continue
        }

        val file = byName[record.file]

        if (file != null && file.unreadable) {
            continue
        }

        if (file != null && record.unchangedElsewhere(file.hash ?: record.hash)) {
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
        // Another install's record, matched here, is this install's from now on; so is one from before records said whose they were.
        val adopted = install != null && record.install != install

        val step = when {
            map.hash == remote -> if (file.text != null || adopted) SyncStep.Link(map.id, file) else null
            record.unchangedElsewhere(remote) -> SyncStep.Write(map.id, file.name)
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
        val renamed = unclaimed.firstOrNull { file -> file.hash?.let(record::unchangedElsewhere) == true }
        val since = record.missingSince
        // A record another install of Orbit left, as a phone restored from a backup has, waits longer: its folder may still be filling.
        val foreign = install != null && record.install != null && record.install != install
        val confirmed = since != null && now - since >= if (foreign) FOREIGN_CONFIRMATION else MISSING_CONFIRMATION

        when {
            renamed != null -> {
                unclaimed -= renamed
                steps += SyncStep.Link(map.id, renamed)

                if (map.hash != renamed.hash) {
                    steps += SyncStep.Write(map.id, renamed.name)
                }
            }
            map.hash != record.hash -> steps += SyncStep.Write(map.id, null)
            // None of the maps' files there: emptied or swapped, more likely than all deleted, or still filling, as on a
            // restored phone. Once a later sync agrees, the maps are written again; until then nothing is written over.
            swapped && confirmed -> steps += SyncStep.Write(map.id, null)
            swapped && since == null -> steps += SyncStep.Missing(map.id, record)
            swapped -> Unit
            map.id in onScreen -> steps += SyncStep.Write(map.id, null)
            since == null -> steps += SyncStep.Missing(map.id, record)
            confirmed -> steps += SyncStep.Remove(map.id)
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

/**
 * Whether a file whose text is `hash` is as this device last left it, so
 * nothing changed it elsewhere since: the text both sides had when they last
 * matched, or the text this device was writing to it and hasn't seen there
 * whole yet ([SyncRecord.writing]), as a write whose reading back failed
 * leaves it.
 */
private fun SyncRecord.unchangedElsewhere(hash: String): Boolean = hash == this.hash || hash == writing

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

    /** How many syncs in a row had a problem: one that lasts shows on Home too. */
    val failures: Int = 0,

    /** What syncs did that the user should hear about, until they've read it. */
    val notes: FolderNotes = FolderNotes(),

    /** This device, as its record in the folder says, once it has synced there. */
    val device: FolderDevice? = null,

    /** The other devices that keep their records in the folder and synced recently (see [FolderRoster]). */
    val devices: List<FolderDevice> = emptyList(),

    /** The extensions of the files in the folder's media, read only when two devices carry different kinds. */
    val mediaTypes: Set<String> = emptySet(),

    /** The extensions of the ideas' files among them, as Orbit names them (`file-…`): every device that keeps ideas' files opens them. */
    val fileTypes: Set<String> = emptySet(),

    /**
     * The maps in the folder that another device there keeps only part of,
     * by its id, as their titles: an older Orbit keeps what's past its room
     * only in the folder ([FolderRoster.partlyKept]).
     */
    val partly: Map<String, List<String>> = emptyMap(),

    /**
     * Maps whose files left the folder all in one sync, more than
     * [MOST_REMOVALS]: they wait for a yes on Home before they follow
     * their files into the trash ([FolderSync.removeHeld]), or are written
     * back ([FolderSync.keepHeld]).
     */
    val held: List<String> = emptyList(),
) {
    /** What this device should hear about the others. */
    val notices: List<FolderNotice> get() = device?.let { FolderRoster.notices(it, devices, mediaTypes, fileTypes) }.orEmpty()
}

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

    /** Markdown files too big to be maps ([MAP_TEXT_LIMIT]), so they're only in the folder: their names. */
    val large: List<String> = emptyList(),

    /**
     * Maps written to the folder for a newer format than another device
     * there reads ([FolderRoster.leftBehind]), so that device can only show
     * them: their titles.
     */
    val newer: List<String> = emptyList(),

    /**
     * The notes above that are about private maps (their titles, and the
     * pictures and videos too big for one), which Settings doesn't name
     * while private maps are locked.
     */
    val private: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = keptBoth.isEmpty() && removed.isEmpty() && restored.isEmpty() && tooBig.isEmpty() && large.isEmpty() && newer.isEmpty()

    operator fun plus(other: FolderNotes) = FolderNotes(
        keptBoth = (keptBoth + other.keptBoth).distinct(),
        removed = (removed + other.removed).distinct(),
        restored = (restored + other.restored).distinct(),
        tooBig = (tooBig + other.tooBig).distinct(),
        large = (large + other.large).distinct(),
        newer = (newer + other.newer).distinct(),
        private = (private + other.private).distinct(),
    )

    /** These notes without the entries `read` has, as one shown and read leaves them: which of the rest are about private maps too. */
    operator fun minus(read: FolderNotes): FolderNotes {
        val left = FolderNotes(
            keptBoth = keptBoth - read.keptBoth.toSet(),
            removed = removed - read.removed.toSet(),
            restored = restored - read.restored.toSet(),
            tooBig = tooBig - read.tooBig.toSet(),
            large = large - read.large.toSet(),
            newer = newer - read.newer.toSet(),
        )

        val entries = (left.keptBoth + left.removed + left.restored + left.tooBig + left.large + left.newer).toSet()
        return left.copy(private = private.filter { it in entries })
    }
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

    /**
     * Names the folder's version of a map changed on both sides as Orbit's
     * own editor names a map, markdown or YAML alike (the app's is the
     * spare page's, [MapTextEditor.onPages]).
     */
    private val editor: MapTextEditor,

    /** This device as it describes itself to the others in the folder (see [FolderDevice]), or null to keep no record. */
    private val device: suspend () -> FolderDevice? = { null },

    /** A clock that only goes forward, in milliseconds, for how long a file has stayed as it is. */
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000 },

    private val open: (String) -> SyncFolder,
) {
    private val status = MutableStateFlow(FolderSyncState())

    /**
     * Files that hold only the start of their maps' texts, by folder and
     * name, with what they held and when a sync first found it: one is taken
     * once it has stayed as it is for [SETTLING].
     */
    private val settling = mutableMapOf<Pair<String, String>, Pair<String, Long>>()

    /** The sync asked for once a file waiting in [settling] may have settled. */
    private var settlingCheck: Job? = null

    /** The maps held back from the trash that the person said yes to, by id (see [removeHeld]). */
    private val approved: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The maps held back from the trash that the person kept, by id: the next sync forgets their files, to write them again (see [keepHeld]). */
    private val kept: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The maps found with every picture, video and sound they point at, by folder and map, with the text ([MapSummary.contentKey]) they had then. */
    private val complete = mutableMapOf<Pair<String, String>, String>()

    /**
     * Pictures and videos too big for their maps, by folder and map, then
     * name, which stay only in the folder: each with the folder's file and
     * the map's room as they were then, so it isn't tried again until either
     * changes.
     */
    private val refused = mutableMapOf<Pair<String, String>, MutableMap<String, Pair<FolderFile, Long>>>()

    /**
     * The pictures, videos and sounds in the folder that couldn't be brought
     * to their maps, by folder, map and name (none when the map's couldn't be
     * looked at as a whole), with when that first happened, by [elapsed]: one
     * that goes on failing for [MEDIA_PATIENCE] is a problem the sync reports.
     */
    private val unfetched = mutableMapOf<Triple<String, String, String>, Long>()

    /**
     * The pictures, videos and sounds here known to be the very file the
     * folder's `media/` holds by their name, by folder and file here: with
     * both as they were then, so that while neither changes, writing their
     * maps needn't read the folder's again to tell (see [Run.givePictures]).
     */
    private val matching = mutableMapOf<Pair<String, String>, Pair<FolderFile, FolderFile>>()

    /** The devices' records as they were last read, by folder and file, so ones that haven't changed aren't read again. */
    private val records = mutableMapOf<String, MutableMap<String, Pair<FolderFile, FolderDevice?>>>()

    /** The study records as they were last read, by folder, then device and file, with their text: as for [records]. */
    private val studies = mutableMapOf<String, MutableMap<Pair<String, String>, Pair<FolderFile, String>>>()

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

    /**
     * The user has read these notes (`read`), as Home or Settings showed
     * them: they go, and the others stay, as those about private maps do
     * while private maps hide. The maps the folder sent to the trash were
     * kept there until their note was read: from now on, they have the
     * trash's usual days, but those `hidden` (a private map's, while private
     * maps hide), which wait until their note is read too.
     */
    fun dismissNotes(read: FolderNotes, hidden: (TrashedMap) -> Boolean = { false }) {
        val left = status.updateAndGet { it.copy(notes = it.notes - read) }.notes

        scope.launch(Dispatchers.IO) {
            if (left.isEmpty) {
                notesFile.delete()
            } else {
                runCatching { writeAtomically(notesFile, notesText(left)) }
            }

            maps.seeFolderRemovals(keeping = hidden)
        }
    }

    /** Yes, these maps held back ([FolderSyncState.held]) follow their files into the trash. */
    fun removeHeld(ids: Collection<String>) {
        approved += ids
        status.update { it.copy(held = it.held - ids.toSet()) }
        request()
    }

    /** No, these maps held back ([FolderSyncState.held]) stay, and are written back to the folder. */
    fun keepHeld(ids: Collection<String>) {
        kept += ids
        status.update { it.copy(held = it.held - ids.toSet()) }
        request()
    }

    /** Brings the maps and the folder in step now, and waits for it: for tests, whose state follows the folder as [start] has it do. */
    internal suspend fun syncNow() {
        val folder = settings.settings.first().syncFolder
        status.update { if (it.folder == folder) it else it.copy(folder = folder) }
        sync()
    }

    /** A folder's name and how many markdown files it has that could be maps, before the maps are kept there. */
    suspend fun inspect(address: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        val folder = open(address)
        // One too big to be a map stays only in the folder: it isn't one to take in.
        folder.name to folder.files().count { it.size <= MAP_TEXT_LIMIT }
    }

    private fun readNotes(): FolderNotes =
        runCatching { notesJson.decodeFromString(FolderNotes.serializer(), notesFile.readText()) }.getOrDefault(FolderNotes())

    /** `notes` as their file keeps them, with the kinds of note a newer version keeps there, which this one doesn't know, left in. */
    private fun notesText(notes: FolderNotes): String {
        val written = notesJson.encodeToJsonElement(FolderNotes.serializer(), notes)
        val before = runCatching { notesJson.parseToJsonElement(notesFile.readText()) }.getOrNull()
        return (before?.let { keepUnknownFields(written, it, FolderNotes.serializer().descriptor) } ?: written).toString()
    }

    private suspend fun sync() {
        val address = settings.settings.first().syncFolder
        maps.maps.filterNotNull().first()

        if (!maps.writable.value) {
            return
        }

        // With no folder, the maps keep what the other devices there studied: leaving one loses nothing.
        if (address == null) {
            maps.keepStudyHere()
            return
        }

        status.update { if (it.folder == address) it.copy(syncing = true) else it }
        var run: Run? = null

        // A sync that stops partway keeps the notes of what it did before then, as maps it trashed or kept twice.
        val outcome = try {
            withContext(Dispatchers.IO) { Run(address, open(address)).also { run = it }.sync() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            Outcome(problem = words.string(R.string.data_folder_unreachable), notes = run?.notes() ?: FolderNotes())
        } catch (failure: Throwable) {
            // Whatever went wrong, out of memory included, ends this sync only: the next one tries again.
            Logs.warn("OrbitFolder", "A sync with the folder stopped", failure)
            Outcome(problem = words.string(R.string.data_folder_unreadable), notes = run?.notes() ?: FolderNotes())
        }

        // A file waiting to settle is looked at again once it may have, rather than at the next sync, minutes away.
        if (outcome.settling && settlingCheck?.isActive != true) {
            settlingCheck = scope.launch {
                delay(SETTLING)
                request()
            }
        }

        val notes = status.value.notes + outcome.notes

        if (!outcome.notes.isEmpty) {
            withContext(Dispatchers.IO) {
                runCatching { writeAtomically(notesFile, notesText(notes)) }
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
                    failures = if (outcome.problem == null) 0 else current.failures + 1,
                    notes = notes,
                    device = outcome.roster?.device ?: current.device,
                    devices = outcome.roster?.devices ?: current.devices,
                    mediaTypes = outcome.roster?.mediaTypes ?: current.mediaTypes,
                    fileTypes = outcome.roster?.fileTypes ?: current.fileTypes,
                    partly = outcome.roster?.partly ?: current.partly,
                    // Not those the person answered about while this sync ran.
                    held = (if (outcome.problem == null || outcome.held.isNotEmpty()) outcome.held else current.held).filterNot { it in approved || it in kept },
                )
            }
        }
    }

    private class Outcome(
        val name: String? = null,
        val problem: String? = null,
        val notes: FolderNotes = FolderNotes(),
        val roster: Roster? = null,

        /** Whether a file is waiting to settle (see [settling]). */
        val settling: Boolean = false,

        /** The maps held back from the trash (see [FolderSyncState.held]). */
        val held: List<String> = emptyList(),
    )

    /** This device and the others, as their records in the folder say. */
    private class Roster(val device: FolderDevice, val devices: List<FolderDevice>, val mediaTypes: Set<String>, val partly: Map<String, List<String>> = emptyMap(), val fileTypes: Set<String> = emptySet())

    /** One sync with the folder at `address`. */
    private inner class Run(private val address: String, private val folder: SyncFolder) {
        /** The names of the folder's files, for new ones to be unlike. */
        private val names = mutableSetOf<String>()

        private val pictures: MutableMap<String, FolderFile> by lazy { folder.media().toMutableMap() }
        private val keptBoth = mutableListOf<String>()
        private val removed = mutableListOf<String>()
        private val restored = mutableListOf<String>()
        private val tooBig = mutableListOf<String>()
        private val large = mutableListOf<String>()
        private val newer = mutableListOf<String>()

        /** The folder's markdown files as listed when the sync began, or as this sync wrote them, by name. */
        private val listed = mutableMapOf<String, FolderFile>()

        /**
         * The text of each of the folder's markdown files as this sync
         * compared it, as a [digest], by name: as read, as its record says
         * for one just as it was when it last matched, or as this sync wrote
         * it. A file is written over or deleted only while it still holds it
         * (see [stillAsCompared]).
         */
        private val compared = mutableMapOf<String, String>()

        /** The notes about private maps, among the four above. */
        private val privateNotes = mutableListOf<String>()

        /** The files this sync found waiting to settle (see [settling]), or changed since it compared them. */
        private val waiting = mutableSetOf<String>()

        /** The pictures, videos and sounds this sync couldn't bring to their maps, as [unfetched] keeps them. */
        private val failing = mutableSetOf<Triple<String, String, String>>()

        /** The pictures, videos and sounds this sync didn't copy to or from the folder for lack of room on this device, by name. */
        private val roomless = mutableSetOf<String>()

        /** The maps deleted here whose files were in the folder, as the sync began. */
        private var deletions: List<SyncRecord> = emptyList()

        /** This install of Orbit, which the records it writes carry (see [SyncRecord.install]). */
        private var install: String? = null

        /** The maps this sync holds back from the trash (see [FolderSyncState.held]). */
        private var held: List<String> = emptyList()

        /** The other devices in the folder, as their records say: read only once a map written for a later format needs them. */
        private val others: List<FolderDevice> by lazy {
            val me = install ?: return@lazy emptyList()

            try {
                FolderDeviceRecords(folder, records.getOrPut(address) { mutableMapOf() }).others(me, System.currentTimeMillis())
            } catch (refused: SecurityException) {
                throw refused
            } catch (failure: Exception) {
                Logs.warn("OrbitFolder", "The devices' records in the folder couldn't be read", failure)
                emptyList()
            }
        }

        private var failed = 0

        suspend fun sync(): Outcome {
            val name = folder.name

            // The maps deleted here, which a sync can't do without: taken for none, a deleted private map's file would come back
            // as a map like any other. Until they can be read, nothing happens, and the sync says so.
            val deleted = try {
                maps.folderDeletions()
            } catch (unreadable: IOException) {
                Logs.warn("OrbitFolder", "The maps deleted here couldn't be read", unreadable)
                return Outcome(name, words.string(R.string.data_folder_deletions_unreadable))
            }

            deletions = deleted
            val listing = folder.files()
            names += listing.map { it.name }
            listing.associateByTo(listed) { it.name }

            // With no record, a map the person kept as its file left is new to the folder: this sync writes it there again,
            // unless its file came back meanwhile.
            for (id in kept.toList()) {
                val record = maps.summary(id)?.metadata?.sync

                if (record != null && record.folder == address && listing.none { it.name == record.file }) {
                    maps.setSyncRecord(id, null)
                }

                kept -= id
            }

            val summaries = maps.maps.value.orEmpty()
            // Each map's file as the sync begins, to follow it to the one it has after.
            val before = summaries.associate { it.id to it.metadata.sync }
            val mine = device()
            install = mine?.id
            // A file just as it was when it last matched needn't be read again.
            val known = (summaries.mapNotNull { it.metadata.sync } + deleted).filter { it.folder == address }.associateBy { it.file }
            var unreadable = 0

            // Too big to be a map: it stays only in the folder, and its map, if it has one, as it is.
            fun tooLarge(file: FolderFile, record: SyncRecord?): RemoteFile {
                note(large, file.name, record?.let { known -> summaries.any { it.metadata.sync == known && it.metadata.isPrivate } } == true)
                return RemoteFile(file, null, unreadable = true)
            }

            val files = listing.map { file ->
                val record = known[file.name]
                val unchanged = record != null && file.modified != 0L && file.modified == record.modified && file.size == record.size

                if (unchanged) {
                    RemoteFile(file, null)
                } else if (file.size > MAP_TEXT_LIMIT) {
                    tooLarge(file, record)
                } else {
                    // One file that can't be read waits for the next sync; the others go on.
                    try {
                        RemoteFile(file, folder.read(file.name))
                    } catch (arriving: StillArriving) {
                        // Still being written, it waits, unless it has stayed as it is a while: then that's how it is.
                        if (settled(file.name, arriving.text)) RemoteFile(file, arriving.text) else RemoteFile(file, null, unreadable = true)
                    } catch (large: TooLargeForAMap) {
                        // Listed smaller, or at a size the folder can't tell: only what a map holds was read of it.
                        tooLarge(file, record)
                    } catch (failure: IOException) {
                        unreadable += 1
                        RemoteFile(file, null, unreadable = true)
                    } catch (refused: SecurityException) {
                        throw refused
                    } catch (failure: RuntimeException) {
                        // A folder's app answers with whatever it throws, as IllegalStateException.
                        unreadable += 1
                        RemoteFile(file, null, unreadable = true)
                    }
                }
            }

            // What each file was compared as: its text as read, or as its record says for one just as it was then.
            for (file in files.filterNot { it.unreadable }) {
                (file.hash ?: known[file.name]?.hash)?.let { compared[file.name] = it }
            }

            val local = summaries.map { LocalMap(it.id, it.contentKey, it.metadata.sync, readable = !it.unreadable) }

            val steps = planSync(address, local, files, deleted, maps.onScreen(), System.currentTimeMillis(), install)
            val removals = steps.filterIsInstance<SyncStep.Remove>().map { it.id }
            // Many maps following their files out at once is likelier files moved, or a sync app gone wrong, than deletions: they wait for a yes.
            held = if (removals.size > MOST_REMOVALS) removals.filter { it !in approved } else emptyList()
            approved.retainAll(removals.toSet())

            for (step in steps.filterNot { it is SyncStep.Remove && it.id in held }) {
                // A step that fails, whatever the folder's app throws, is tried again by the next sync; the others go on.
                try {
                    perform(step)
                } catch (full: NoRoomForMedia) {
                    // Its map waits with it, and the sync says why.
                    roomless += full.name
                } catch (failure: IOException) {
                    failed += 1
                } catch (refused: SecurityException) {
                    throw refused
                } catch (failure: RuntimeException) {
                    Logs.warn("OrbitFolder", "A step of a sync with the folder failed", failure)
                    failed += 1
                }
            }

            // Media that reached the folder after its map's text, which the steps above found missing.
            fetchMissingMedia()
            val now = elapsed()

            // Media that failed before and not this time arrived, or isn't wanted anymore. What has gone on failing a while is
            // a problem, as a file the folder's app can never give whole is.
            failing.forEach { unfetched.putIfAbsent(it, now) }
            unfetched.keys.removeAll { it.first == address && it !in failing }
            val stuck = unfetched.filter { (key, since) -> key.first == address && now - since >= MEDIA_PATIENCE.inWholeMilliseconds }.keys.map { it.second }.toSet()

            val problems = listOfNotNull(
                failed.takeIf { it > 0 }?.let { count -> words.plural(R.plurals.data_folder_maps_not_in_step, count, count) },
                unreadable.takeIf { it > 0 }?.let { count -> words.plural(R.plurals.data_folder_files_unreadable, count, count) },
                stuck.size.takeIf { it > 0 }?.let { count -> words.plural(R.plurals.data_folder_media_not_arriving, count, count) },
                roomless.size.takeIf { it > 0 }?.let { count -> words.plural(R.plurals.data_folder_no_room, count, count) },
            )
            // Each problem is a sentence of its own, and so is what Orbit does about them.
            val problem = problems.takeIf { it.isNotEmpty() }?.joinToString(" ", postfix = " " + words.string(R.string.data_folder_tries_again))

            if (mine != null) {
                study(mine.id, before)
            }

            // Files that left the folder, or stopped looking cut short, no longer wait.
            settling.keys.removeAll { (folder, file) -> folder == address && file !in waiting }
            return Outcome(name, problem, notes(), roster(mine), settling = waiting.isNotEmpty(), held = held)
        }

        /** What this sync did that the user should hear about, so far. */
        fun notes() = FolderNotes(keptBoth.toList(), removed.toList(), restored.toList(), tooBig.toList(), large.toList(), newer.toList(), privateNotes.toList())

        /**
         * Brings how study goes in step with the folder: the other devices'
         * answers to each map kept there join its details, and this device's
         * go to its own records, which follow the maps' files as they're
         * renamed and go once their maps do. Records that can't be read or
         * written change nothing about the sync: study just goes on as it
         * was, and a later sync tries again.
         */
        private suspend fun study(me: String, before: Map<String, SyncRecord?>) {
            try {
                val listing = folder.studyRecords().filterKeys { DEVICE_ID.matches(it) }
                val known = studies.getOrPut(address) { mutableMapOf() }
                known.keys.retainAll { (device, file) -> listing[device].orEmpty().any { it.name == file } }
                // The devices with a record this sync couldn't read, or another device's it couldn't make out: until a later
                // sync can, what each studied stays as it was here, and this device's own records aren't written. A record a
                // newer Orbit says this one would misread is another matter: it's left out, as if it weren't there.
                val unread = mutableSetOf<String>()

                val texts = listing.mapValues { (device, files) ->
                    files.mapNotNull { file ->
                        val text = readStudy(device, file, known)

                        if (text == null) {
                            unread += device
                        }

                        text?.let { file.name to it }
                    }.toMap()
                }

                val records = texts.mapValues { (device, kept) ->
                    kept.values.mapNotNull { text ->
                        when (val read = FolderStudy.read(text)) {
                            is StudyRead.Record -> read.record
                            StudyRead.Incompatible -> null
                            StudyRead.Garbled -> {
                                if (device != me) {
                                    unread += device
                                }

                                null
                            }
                        }
                    }
                }

                val own = texts[me].orEmpty()
                val ownRecords = own.mapNotNull { (file, text) -> FolderStudy.decode(text)?.let { file to it } }.toMap()
                // This device's own records a newer Orbit wrote and says this one would misread, as after going back to an older
                // version: left as they are, never written over with what this one knows, nor deleted, so the newer one finds them.
                val newer = own.filterValues { FolderStudy.read(it) == StudyRead.Incompatible }.keys
                val written = mutableSetOf<String>()

                for (map in maps.maps.value.orEmpty()) {
                    val record = map.metadata.sync?.takeIf { it.folder == address } ?: continue
                    val previous = before[map.id]
                    var answers = map.metadata.study.orEmpty()

                    // Kept in another folder before: what the devices there studied stays this map's, joining this device's
                    // answers as they are now, an answer just given among them.
                    if ((previous == null || previous.folder != address) && !map.metadata.studyElsewhere.isNullOrEmpty()) {
                        answers = maps.takeStudyElsewhere(map.id) ?: map.metadata.studyProgress
                    }

                    val renamedFrom = previous?.takeIf { it.folder == address && it.file != record.file }?.file
                    val file = FolderStudy.recordName(record.file)
                    val earlier = renamedFrom?.let(FolderStudy::recordName)
                    val remembered = listOfNotNull(earlier, file).flatMap { ownRecords[it]?.was.orEmpty() }
                    val found = FolderStudy.elsewhere(record.file, me, records, names, listOfNotNull(renamedFrom) + remembered)
                    val elsewhere = found.filterKeys { it !in unread } + map.metadata.studyElsewhere.orEmpty().filterKeys { it in unread }

                    if (elsewhere != map.metadata.studyElsewhere.orEmpty()) {
                        maps.saveStudyElsewhere(map.id, elsewhere)
                    }

                    if (answers.isNotEmpty() && me !in unread && file !in newer) {
                        val kept = (own[file] ?: earlier?.takeIf { it !in newer }?.let(own::get))?.let(FolderStudy::fields)
                        val text = FolderStudy.encode(FolderStudy.record(record.file, answers, renamedFrom, remembered), kept)

                        if (text != own[file]) {
                            folder.writeStudyRecord(me, file, text)
                            known -= me to file
                        }

                        written += file
                    }
                }

                // This device's records of maps no longer in the folder, or of files they had before, go: not a newer Orbit's.
                for (file in if (me in unread) emptySet() else own.keys - written - newer) {
                    folder.deleteStudyRecord(me, file)
                    known -= me to file
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logs.warn("OrbitFolder", "How study goes couldn't be read from the folder or written to it", failure)
            }
        }

        /** A study record's text as it was last read, or read again when its file changed; null when it can't be read now. */
        private fun readStudy(device: String, file: FolderFile, known: MutableMap<Pair<String, String>, Pair<FolderFile, String>>): String? {
            val before = known[device to file.name]

            if (before != null && before.first == file && file.modified != 0L) {
                return before.second
            }

            // A record that can't be read now is read again by the next sync.
            val text = try {
                folder.readStudyRecord(device, file.name)
            } catch (unreadable: IOException) {
                return null
            } catch (refused: SecurityException) {
                return null
            }

            known[device to file.name] = file to text
            return text
        }

        /**
         * Writes this device's record in the folder when it needs it, and
         * reads the others'. A folder that keeps no records, or a record that
         * can't be written, changes nothing about the sync: the devices just
         * don't show.
         */
        private fun roster(device: FolderDevice?): Roster? {
            val now = System.currentTimeMillis()
            // Ideas' files are of any kind: the record lists those the folder holds, so a version from before them says it can't open them.
            val fileTypes = pictures.keys.filter(::isIdeaFileName).mapNotNull { name -> name.substringAfterLast('.', "").lowercase().ifEmpty { null } }.toSet()
            val mine = device?.let { it.copy(synced = now, mediaTypes = (it.mediaTypes + fileTypes).distinct().sorted()) } ?: return null

            return try {
                val others = FolderDeviceRecords(folder, records.getOrPut(address) { mutableMapOf() }).update(mine, now)
                // Which kinds of file are there matters only when two devices carry different ones: then the folder's media is listed.
                val differ = others.any { it.mediaTypes.toSet() != mine.mediaTypes.toSet() }
                val types = if (differ) pictures.keys.mapNotNull { name -> name.substringAfterLast('.', "").lowercase().ifEmpty { null } }.toSet() else emptySet()
                // What each map in the folder comes to matters only when a device there keeps less of it than this one.
                val partly = if (FolderRoster.keepLess(others)) FolderRoster.partlyKept(others, folderMaps()) else emptyMap()
                Roster(mine, others, types, partly, fileTypes)
            } catch (failure: Exception) {
                Logs.warn("OrbitFolder", "The devices' records in the folder couldn't be read or written", failure)
                null
            }
        }

        /** The maps kept in this folder, by title, with the sizes of the pictures, videos and sounds each points at. */
        private fun folderMaps(): List<Pair<String, List<Long>>> =
            maps.maps.value.orEmpty().filter { it.metadata.sync?.folder == address && !it.unreadable }.map { it.title to maps.mediaFileSizes(it.id) }

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
                    val markdown = maps.read(step.id)
                    // The same text, but a picture here may not be the one the folder holds by its name: as when the map is written,
                    // the folder gets the ones it lacks, and one it holds another file by goes there under a name of its own, which
                    // the map's file then points at. Only then do they match, so a sync that fails before links them again.
                    val given = givePictures(step.id, markdown) ?: return
                    maps.setSyncRecord(step.id, record(step.file.file, step.file.hash ?: digest(markdown)))

                    if (given != markdown) {
                        write(step.id, step.file.name)
                    }
                }
                is SyncStep.KeepBoth -> {
                    val text = step.file.text ?: folder.read(step.file.name)

                    if (keepsMap(step.id, step.file.name, text)) {
                        return
                    }

                    val title = words.string(R.string.data_changed_elsewhere_title, MapText.outline(text, words).title)
                    // Renamed as Orbit renames a map, markdown or YAML. One Orbit can only show (a newer Orbit's, or one it can't read all of) is kept as it came,
                    // and only its file's name says it's a copy.
                    val renamed = editor.edit(text, title, null).takeIf { it.refused == null }?.markdown
                    // The folder's version of a private map is private too, from the moment it's there.
                    val private = isPrivate(step.id)
                    // Its cover says it's the folder's version, as its title does.
                    val cover = maps.summary(step.id)?.metadata?.cover?.takeIf { private }?.let { maps.coverOfCopy(it, R.string.data_changed_elsewhere_title) }
                    val copy = maps.add(renamed ?: text, original = text, originalName = step.file.name, isPrivate = private, cover = cover)

                    // The folder's text is kept now: whatever fails next, a later sync writes this map's text rather than keeping both again.
                    note(keptBoth, title, private)
                    maps.setSyncRecord(step.id, record(step.file.file, digest(text)))
                    takePictures(copy, text)
                    write(copy, null, named = title)
                    write(step.id, step.file.name)
                }
                is SyncStep.Remove -> {
                    val title = maps.summary(step.id)?.title
                    val private = isPrivate(step.id)
                    // No record left, so deleting the map doesn't go on to delete a file. It waits in the trash, at least until its note is read.
                    maps.setSyncRecord(step.id, null)
                    maps.delete(step.id, byFolder = true)
                    title?.let { note(removed, it, private) }
                }
                is SyncStep.Missing -> maps.setSyncRecord(step.id, step.record.copy(missingSince = System.currentTimeMillis()))
                is SyncStep.Found -> maps.setSyncRecord(step.id, step.record.copy(missingSince = null))
                is SyncStep.Add -> {
                    val text = step.file.text ?: folder.read(step.file.name)
                    val id = maps.add(
                        text,
                        sync = record(step.file.file, digest(text)),
                        original = text,
                        originalName = step.file.name,
                        isPrivate = privateFile(step.file.name),
                        cover = conflictCover(step.file.name),
                    )
                    takePictures(id, text)
                }
                is SyncStep.DeleteFile -> {
                    // Deleted only while it holds the very text compared: changed since, by another device say, it stays, and so
                    // does its deletion, for the next sync to compare it again (it then comes back as a map).
                    if (!stillAsCompared(step.record.file, always = true)) {
                        waiting += step.record.file
                        return
                    }

                    folder.delete(step.record.file)
                    names -= step.record.file
                    maps.forgetFolderDeletion(step.record)
                }
                is SyncStep.Forget -> maps.forgetFolderDeletion(step.record)
            }
        }

        /**
         * Whether the map keeps its text over `text` from its file, for good
         * or for now. A file emptied while the map has ideas gets them back.
         * A file that holds only the start of the map's text is this device's
         * own write cut short when the map's record says a write of it was
         * under way (the map may have changed again since), and is written
         * again. Otherwise it's someone else's shorter text, as deleting a
         * map's last ideas leaves it, or a file still arriving: it waits until
         * it has stayed as it is for [SETTLING], and is then taken as any
         * change is.
         */
        private suspend fun keepsMap(id: String, file: String, text: String): Boolean {
            val local = maps.read(id)

            if (!MapText.hasContent(text) && MapText.hasContent(local)) {
                write(id, file)
                maps.summary(id)?.title?.let { note(restored, it, isPrivate(id)) }
                return true
            }

            if (text.length >= local.length || !local.startsWith(text)) {
                return false
            }

            if (maps.summary(id)?.metadata?.sync?.writing != null) {
                write(id, file)
                return true
            }

            return !settled(file, text)
        }

        /** Whether the folder's `name` has held `text` since a sync at least [SETTLING] ago; until then, it's [waiting]. */
        private fun settled(name: String, text: String): Boolean {
            val key = address to name
            val hash = digest(text)
            val now = elapsed()
            val seen = settling[key]?.takeIf { (held, _) -> held == hash }

            if (seen == null) {
                settling[key] = hash to now
            } else if (now - seen.second >= SETTLING.inWholeMilliseconds) {
                settling -= key
                return true
            }

            waiting += name
            return false
        }

        /**
         * Writes the map as it is now to its file, or to a new one named for
         * its title (or `named`), and reads it back: the map's record only
         * says they match once the folder holds the text.
         */
        private suspend fun write(id: String, file: String?, named: String? = null) {
            if (!maps.exists(id)) {
                return
            }

            val markdown = givePictures(id, maps.read(id)) ?: return
            val name = file ?: MapText.fileName(named ?: MapText.outline(markdown, words).title, names, words)

            // A file that changed since this sync compared it isn't written over: the next sync compares it again.
            if (file != null && !stillAsCompared(file)) {
                waiting += file
                return
            }

            // Until the file is seen to hold the text, its record says it's being written (see [SyncRecord.writing]).
            maps.summary(id)?.metadata?.sync?.takeIf { it.folder == address && it.file == name }?.let { record ->
                maps.setSyncRecord(id, record.copy(writing = digest(markdown)))
            }

            var written = folder.write(name, markdown, create = file == null)
            names += written.name
            listed[written.name] = written

            // A file this write made is the map's from the moment it's there, empty as it was made: until it's seen to hold the
            // text whole, its record says it's being written, so a later sync takes it for this device's own write, should
            // reading it back fail, rather than for a map of its own.
            if (file == null) {
                maps.setSyncRecord(id, SyncRecord(address, written.name, digest(""), writing = digest(markdown), install = install))
            }

            val back = readBack(written.name)

            if (back != markdown) {
                // Only this write cut short is written again: anything else there is someone's, for the next sync to compare,
                // and isn't this device's write, should it be shorter than the map.
                if (!markdown.startsWith(back)) {
                    maps.summary(id)?.metadata?.sync?.let { maps.setSyncRecord(id, it.copy(writing = null)) }
                    throw IOException("${written.name} changed as it was written")
                }

                written = folder.write(written.name, markdown, create = false)
                listed[written.name] = written
                val again = readBack(written.name)

                if (again != markdown) {
                    // A file this write made that holds only the start of the map isn't left for a later sync to take as a map of its own.
                    if (file == null && markdown.startsWith(again)) {
                        runCatching { folder.delete(written.name) }
                    }

                    throw IOException("${written.name} didn't keep what was written")
                }
            }

            compared[written.name] = digest(markdown)
            maps.setSyncRecord(id, record(written, digest(markdown)))

            // Every Orbit reads the first format: a map written for a later one, a device here that reads only earlier ones can
            // only show, and the notes say so.
            val format = MapText.formatVersion(markdown)

            if (format > 1 && FolderRoster.leftBehind(others, format).isNotEmpty()) {
                maps.summary(id)?.title?.let { note(newer, it, isPrivate(id)) }
            }
        }

        /** What the folder holds at `name` right after a write: as read, even if it looks still arriving. */
        private fun readBack(name: String): String = try {
            folder.read(name)
        } catch (arriving: StillArriving) {
            arriving.text
        }

        /**
         * Whether the folder's `name` still holds the text this sync compared
         * it as ([compared]), checked right before it's written over or
         * deleted: its time and size as listed, or as this sync wrote it, and
         * where the folder can't tell a file's time, or `always` (as before a
         * deletion), the text itself. One gone, or that can't be read whole
         * now, has changed. Android's storage access can't write or delete a
         * file only while it's unchanged, so a change that lands between this
         * check and the write or deletion right after it is the one still
         * lost (see `docs/architecture.md`).
         */
        private fun stillAsCompared(name: String, always: Boolean = false): Boolean {
            val before = listed[name] ?: return false
            val expected = compared[name] ?: return false
            val now = folder.describe(name) ?: return false

            if (now.modified != before.modified || now.size != before.size) {
                return false
            }

            if (now.modified != 0L && !always) {
                return true
            }

            val text = try {
                folder.read(name)
            } catch (changed: IOException) {
                return false
            }

            return digest(text) == expected
        }

        /**
         * The pictures and videos the map points at that the folder doesn't
         * have yet, or has only the start of, as a copy cut short leaves it.
         * The folder keeps one `media/` for every map, so one there by the
         * same name that holds another file (as when two maps from zips each
         * have their "photo.jpg", of one size or not) is another map's: this
         * map's gets a name of its own, in its media and in its text, and goes
         * under it. A name and a size don't tell two pictures apart, so what
         * they hold does, read once and remembered while neither file changes
         * ([matching]). Returns the map's text as it's to be written, or null
         * when it changed meanwhile, for the next sync to write.
         */
        private suspend fun givePictures(id: String, markdown: String): String? {
            var text = markdown

            // Its ideas' files too, of any kind, by the names its text gives them.
            for (name in carriedNames(markdown)) {
                val file = maps.picture(id, name, fileNamesIn(text)) ?: continue
                val there = pictures[name]

                if (there == null) {
                    text = give(id, name, file, text) ?: return null
                    continue
                }

                if (matches(file, there)) {
                    continue
                }

                when (copyThere(name, file)) {
                    FolderCopy.Same -> remember(file, there)
                    // Gone from the folder since it was listed, or a copy there cut short: this map's goes there whole.
                    null, FolderCopy.CutShort -> text = give(id, name, file, text) ?: return null
                    // A copy here that holds only the start of the folder's isn't another file: the folder's stays whole.
                    FolderCopy.Whole -> Unit
                    FolderCopy.Other -> {
                        val renamed = freeName(id, name)
                        text = maps.renameMedia(id, name, renamed, expected = text) ?: return null
                        val copy = maps.picture(id, renamed, fileNamesIn(text))

                        if (copy != null) {
                            text = give(id, renamed, copy, text) ?: return null
                        }
                    }
                }
            }

            return text
        }

        /**
         * Puts the map's `name` (`file` here) into the folder's media, and
         * returns the map's `text` as it's to be written. The folder's app may
         * name it otherwise: as it names a copy when a file by that name
         * arrived since `media/` was listed, or a name whose extension doesn't
         * say its type the way it likes. Then the map points at it by that
         * name, when a map's file can have it and the map has none by it; one
         * it can't follow goes from the folder rather than stay there as no
         * map's, and the next sync finds what the folder holds by the name.
         * Null when the map changed meanwhile, for the next sync to write.
         */
        private suspend fun give(id: String, name: String, file: File, text: String): String? {
            val written = folder.writeMedia(name, file)
            pictures[written.name] = written

            if (written.name == name) {
                remember(file, written)
                return text
            }

            // An idea's file follows a name of any kind; a picture, video or sound keeps its kind.
            val files = fileNamesIn(text)
            val follows = isMediaName(written.name) || (name in files && MediaNames.NAME.matches(written.name))

            if (!follows || maps.picture(id, written.name, files + written.name) != null) {
                discard(written.name)
                throw IOException("The folder named $name ${written.name}")
            }

            val followed = maps.renameMedia(id, name, written.name, expected = text)

            if (followed == null) {
                discard(written.name)
                return null
            }

            maps.picture(id, written.name, fileNamesIn(followed))?.let { remember(it, written) }
            return followed
        }

        /** Deletes a picture or video this sync put in the folder's media that no map points at. */
        private fun discard(name: String) {
            folder.deleteMedia(name)
            pictures -= name
        }

        /**
         * Whether `file` here is known to be the very file the folder holds
         * by its name, listed as `there`, neither having changed since. A
         * folder that can't tell when its file was written can't tell it was
         * written over either: there, the files are compared each time.
         */
        private fun matches(file: File, there: FolderFile): Boolean = there.modified != 0L && matching[address to file.path] == described(file) to there

        /** Remembers that `file` here is the very file the folder holds by its name, listed as `there`. */
        private fun remember(file: File, there: FolderFile) {
            matching[address to file.path] = described(file) to there
        }

        /** A file here as a listing describes one, to tell when it changes. */
        private fun described(file: File) = FolderFile(file.name, file.lastModified(), file.length())

        /** `name`, numbered (`photo-2.jpg`, `photo-3.jpg`…) to one neither the folder's media nor the map's has. */
        private fun freeName(id: String, name: String): String {
            val stem = name.substringBeforeLast('.')
            val extension = name.substringAfterLast('.', "")

            return generateSequence(2) { it + 1 }
                .map { number -> if (extension.isEmpty()) "$stem-$number" else "$stem-$number.$extension" }
                .first { it !in pictures && maps.picture(id, it) == null }
        }

        /** What the folder's `name` is to `file` here, by what each holds, read once; null when the folder doesn't have it anymore. */
        private fun copyThere(name: String, file: File): FolderCopy? = file.inputStream().buffered().use { here ->
            try {
                if (!folder.readMedia(name, SameBytes(here))) {
                    return null
                }
            } catch (different: Different) {
                return FolderCopy.Other
            } catch (longer: Longer) {
                return FolderCopy.Whole
            }

            if (here.read() == -1) FolderCopy.Same else FolderCopy.CutShort
        }

        /**
         * The pictures and videos a text from the folder points at that the
         * map doesn't have yet. One that would be more than a map keeps, as
         * one from the phone would ([mediaRoom]), stays only in the folder,
         * where it's left as it is, and the notes say so: the map then
         * still fits in its zip, and comes back from it. It isn't tried again
         * until the folder's file changes or the map has more room ([refused]).
         * One that can't be read now is tried again by the next sync, and
         * one that goes on failing for [MEDIA_PATIENCE] is a problem the sync
         * reports ([unfetched]). One this device hasn't room for waits for room,
         * and the sync says so.
         */
        private suspend fun takePictures(id: String, markdown: String) {
            var carried = maps.mediaSize(id, markdown)
            val files = fileNamesIn(markdown)

            for (name in carriedNames(markdown)) {
                val there = pictures[name] ?: continue

                if (maps.picture(id, name, files) != null) {
                    continue
                }

                val room = mediaRoom(carried)
                val refusal = refused[address to id]?.get(name)

                // Too big before, and the folder's file and the map's room are as they were: it stays only in the folder.
                if (refusal != null && refusal.first == there && room <= refusal.second) {
                    continue
                }

                try {
                    if (there.size > room) {
                        throw MediaRefusedException(words.string(R.string.data_more_than_a_map_keeps))
                    }

                    val added = maps.addMediaFile(id, name, room, size = there.size, files = files) { output -> folder.readMedia(name, output) }
                    carried += added
                    refused[address to id]?.remove(name)
                    // Taken from the folder, it's the very file the folder holds: writing the map needn't read it again.
                    maps.picture(id, name)?.takeIf { added > 0 }?.let { remember(it, there) }
                } catch (tooLarge: MediaRefusedException) {
                    refused.getOrPut(address to id) { mutableMapOf() }[name] = there to room
                    note(tooBig, words.string(R.string.data_folder_too_big_file, name, MapText.outline(markdown, words).title), isPrivate(id))
                } catch (full: NoRoomForMedia) {
                    roomless += name
                } catch (unreadable: IOException) {
                    // One still arriving, or that can't be read now, doesn't keep its map from the text: a later sync fetches it.
                    Logs.warn("OrbitFolder", "A picture or video in the folder couldn't be read", unreadable)
                    failing += Triple(address, id, name)
                }
            }
        }

        /**
         * The pictures, videos and sounds that maps kept in the folder point
         * at but don't have, as one that reaches the folder after its map's
         * text leaves it: fetched now. A map is looked at again only once its
         * text changes, or while one it points at is still missing (one too
         * big for it included, which is tried again once the folder's file
         * or the map's room changes), and the folder's media is listed only
         * then. A map whose media can't be listed or brought here now is
         * tried again by the next sync, the others going on.
         */
        private suspend fun fetchMissingMedia() {
            for (summary in maps.maps.value.orEmpty()) {
                val key = address to summary.id

                if (summary.unreadable || summary.metadata.sync?.folder != address || complete[key] == summary.contentKey) {
                    continue
                }

                try {
                    val markdown = maps.read(summary.id)
                    val wanted = carriedNames(markdown)
                    val files = fileNamesIn(markdown)

                    if (wanted.any { maps.picture(summary.id, it, files) == null }) {
                        takePictures(summary.id, markdown)
                    }

                    if (wanted.all { maps.picture(summary.id, it, files) != null }) {
                        complete[key] = summary.contentKey
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (denied: SecurityException) {
                    throw denied
                } catch (failure: Exception) {
                    Logs.warn("OrbitFolder", "Pictures and videos missing here couldn't be fetched from the folder", failure)
                    failing += Triple(address, summary.id, "")
                }
            }
        }

        /** Adds `entry` to `notes`, and to the private ones when it's about a private map. */
        private fun note(notes: MutableList<String>, entry: String, private: Boolean) {
            notes += entry

            if (private) {
                privateNotes += entry
            }
        }

        private fun isPrivate(id: String): Boolean = maps.summary(id)?.metadata?.isPrivate == true

        /**
         * Whether a file new to the maps is a private map's, so its map is
         * private too: a sync app's conflict copy of a private map's file (see
         * [conflictOriginal]), or a private map's file back in the folder after
         * the map was deleted here, or a conflict copy of that.
         */
        private fun privateFile(name: String): Boolean {
            val original = conflictOriginal(name)
            val names = listOfNotNull(name, original)

            return deletions.any { it.folder == address && it.file in names && it.private == true } ||
                (original != null && maps.maps.value.orEmpty().any { map -> map.metadata.isPrivate && map.metadata.sync?.let { it.folder == address && it.file == original } == true })
        }

        /** The cover of a sync app's conflict copy of a private map's file: the map's own, saying it's the folder's version. */
        private fun conflictCover(name: String): LockedCover? {
            val original = conflictOriginal(name) ?: return null
            val map = maps.maps.value.orEmpty().firstOrNull { map -> map.metadata.isPrivate && map.metadata.sync?.let { it.folder == address && it.file == original } == true }
            return map?.metadata?.cover?.let { maps.coverOfCopy(it, R.string.data_changed_elsewhere_title) }
        }

        private fun record(file: FolderFile, hash: String) = SyncRecord(address, file.name, hash, file.modified, file.size, install = install)
    }
}

/** What the folder's file by a picture's name is to the picture here, by what each holds. */
private enum class FolderCopy {
    /** The very same file. */
    Same,

    /** Only its start, as a copy cut short leaves it. */
    CutShort,

    /** The whole of it, which the one here holds only the start of. */
    Whole,

    /** Another file. */
    Other,
}

/**
 * Takes bytes only while they're the ones `original` holds, in order:
 * throws [Different] at the first that isn't, and [Longer] at one past
 * its end.
 */
private class SameBytes(private val original: InputStream) : OutputStream() {
    private var buffer = ByteArray(0)

    override fun write(byte: Int) {
        val mine = original.read()

        if (mine == -1) {
            throw Longer()
        }

        if (mine != (byte and 0xFF)) {
            throw Different()
        }
    }

    // A piece at a time, as they're copied, rather than a byte at a time: a video can be long.
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (buffer.size < length) {
            buffer = ByteArray(length)
        }

        var read = 0

        while (read < length) {
            val count = original.read(buffer, read, length - read)

            if (count < 0) {
                break
            }

            read += count
        }

        for (index in 0 until read) {
            if (buffer[index] != bytes[offset + index]) {
                throw Different()
            }
        }

        if (read < length) {
            throw Longer()
        }
    }
}

private class Different : IOException()

/** Thrown when what's compared goes on past the file it's compared with. */
private class Longer : IOException()

/** How long the maps stay still before a sync, so typing doesn't write the folder on every letter. */
private val SETTLE = 4.seconds

/** How often the folder is checked for changes while Orbit is open. */
private val INTERVAL = 2.minutes

/**
 * The name of the file a sync app's conflict copy is of: Syncthing's
 * `Trip.sync-conflict-20261022-101010-ABCDEFG.md`, and Dropbox's, Nextcloud's
 * or ownCloud's `Trip (conflicted copy 2026-10-22).md` or `Trip (Diana's
 * conflicted copy).md` are copies of `Trip.md`. Null for any other name.
 */
internal fun conflictOriginal(name: String): String? {
    val extension = name.substringAfterLast('.', "")
    val stem = name.substringBeforeLast('.')
    val original = (SYNCTHING_CONFLICT.matchEntire(stem) ?: CONFLICTED_COPY.matchEntire(stem))?.groupValues?.get(1) ?: return null
    return if (extension.isEmpty()) original else "$original.$extension"
}

private val SYNCTHING_CONFLICT = Regex("""(.+)\.sync-conflict-\d{8}-\d{6}(?:-[A-Z0-9]{7})?""")

private val CONFLICTED_COPY = Regex("""(.+?) \([^()]*\bconflict(?:ed|o)?\b[^()]*\)""", RegexOption.IGNORE_CASE)

/**
 * How long a file that holds only the start of its map's text has to stay as
 * it is before its map takes it: a file still arriving changes before then.
 */
internal val SETTLING = 30.seconds

/**
 * How long a picture, video or sound in the folder can go on failing to
 * reach its map, as one still arriving does for a while, before a sync says
 * so: then it's more likely one the folder's app can't give whole.
 */
internal val MEDIA_PATIENCE = 10.minutes

/** A device's id, as its folders and records in `.orbit/` are named: Orbit's own random one. */
private val DEVICE_ID = Regex("[A-Za-z0-9-]{8,64}")
