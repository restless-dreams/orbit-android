package com.hyuchia.orbit.data

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

/** What a file adds to a backup besides its bytes: the archive's record of it, and padding, at most. */
private const val BACKUP_FILE_OVERHEAD = 2048L

/** What a backup leaves of its quota, for files that grow while it's made. */
private const val BACKUP_MARGIN = 1024L * 1024

/** A file's time from this on is in milliseconds already: as seconds, it would be thousands of years away. */
private const val MILLISECONDS_FROM = 100_000_000_000L

/** Where the last backup to the cloud wrote how many maps it had no room for, in the app's files, for Settings to say. */
private const val LEFT_OUT_NOTE = "backup-left-out.txt"

/**
 * Beside the maps, in a backup that leaves private maps out: how many it left
 * out, a count only, so the phone it's restored to can say so.
 */
private const val PRIVATE_LEFT_OUT_NOTE = ".private-maps-left-out"

/** Where a restore keeps that count, in the app's files, until Home has said it; no backup takes it. */
private const val RESTORED_PRIVATE_NOTE = "private-maps-left-out-of-restore.txt"

/** The maps' folder, in the app's files. */
private const val MAPS_DIRECTORY = "maps"

/** The trash's folder, in the app's files. */
private const val TRASH_DIRECTORY = "trash"

/** The folder's deletions still to make (`MapRepository`'s tombstones), in the app's files. */
private const val FOLDER_DELETIONS = "folder-deletions.json"

/**
 * What Android's backup takes of Orbit. Private maps go only into one that's
 * end-to-end encrypted or goes to a new phone ([takesPrivateMaps]), each map
 * private or not as it is when its files go ([backUp]); every other map, and
 * the settings, go into any. A backup stops altogether past its
 * quota, 25 MB on Google's cloud, and one video from the phone can be more
 * than that. So the settings and the maps' text and details go first, the
 * most recently changed map first, then the pictures, sounds and videos the
 * maps point at, smallest first, as long as they fit; how many maps' text
 * didn't fit, if any did, is noted for Settings to say. Moving to a new
 * phone, from Android 12 on, takes the maps' history, the trash and what the
 * folder maps are kept in still has to delete too, as its backup rules
 * allow. Restoring is Android's own, each file back where it was, at the
 * time it was last changed, but written beside its place first and only
 * then given its name, so a restore cut short never leaves half a file. A
 * backup that leaves private maps out carries how many, and a restore from
 * it keeps that count for Home to say ([privateMapsLeftOutOfRestore]).
 */
class OrbitBackupAgent : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
        // Orbit backs up whole files, in onFullBackup.
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        // Nothing is backed up any other way.
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        val toNewPhone = data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
        val newPhone = toNewPhone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val privateMaps = takesPrivateMaps(data.transportFlags)
        val chosen = backupFiles(filesDir, data.quota, newPhone, privateMaps)

        if (!toNewPhone) {
            noteLeftOut(filesDir, mapsLeftOut(filesDir, chosen, privateMaps))
        }

        backUp(filesDir, chosen, privateMaps) { file -> fullBackupFile(file, data) }
    }

    override fun onRestoreFinished() {
        keepRestoredPrivateMapsNote(filesDir)
    }

    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File, type: Int, mode: Long, mtime: Long) {
        restoreFile(destination, type, mtime, filesDir) { target -> super.onRestoreFile(data, size, target, type, mode, mtime) }
    }
}

/**
 * Restores one file of `type` to `destination`, through `restore`, Android's
 * own restore into the file it's given. Android restores only what the backup
 * rules name: a file they name on its own (the folder's deletions, directly
 * in `filesDir`) is written where it is, since one beside it wouldn't be
 * restored; one in a folder they name is written beside its place first, as
 * Orbit writes every file, and only then given its name, so a restore stopped
 * partway through never takes the place of what's there. Then its time.
 */
internal fun restoreFile(destination: File, type: Int, mtime: Long, filesDir: File, restore: (File) -> Unit) {
    if (type != BackupAgent.TYPE_FILE || destination.parentFile?.canonicalPath == filesDir.canonicalPath) {
        restore(destination)
        fixTime(destination, type, mtime)
        return
    }

    val partial = File(destination.parentFile, ".${destination.name}.partial")
    restore(partial)

    if (!partial.renameTo(destination)) {
        partial.delete()
        return
    }

    fixTime(destination, type, mtime)
}

/**
 * Android gives a restored file or folder its time in seconds where it
 * takes milliseconds, and every map would read as last changed in 1970.
 */
private fun fixTime(destination: File, type: Int, mtime: Long) {
    if ((type == BackupAgent.TYPE_FILE || type == BackupAgent.TYPE_DIRECTORY) && mtime in 1 until MILLISECONDS_FROM && destination.lastModified() == mtime) {
        destination.setLastModified(mtime * 1000)
    }
}

/**
 * Whether a backup with `transportFlags` takes private maps: only one that's
 * end-to-end encrypted, as Google's cloud backup is on a phone with a screen
 * lock, or one that goes straight to a new phone. Private maps go nowhere
 * their phone's owner alone can't open: not into a cloud backup from a
 * phone without a screen lock, nor into `adb backup` on Android 10 and 11.
 * Every other map goes into any backup.
 */
internal fun takesPrivateMaps(transportFlags: Int): Boolean =
    transportFlags and BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0 || transportFlags and BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER != 0

/**
 * The files a backup of at most `quota` bytes takes from `files`, the app's
 * own: the settings, and then each map's text and details, the most recently
 * changed map first, whole or not at all, while they fit. Then the pictures,
 * the sounds and then the videos those maps point at, each smallest first,
 * while they fit. To a `newPhone`, the maps' earlier versions follow, then
 * the pictures and videos only those point at, then the trash and the
 * folder's deletions still to make. Without `privateMaps`, private maps stay
 * out altogether: their text, details, pictures, videos, sounds, history
 * and place in the trash; so does one whose details can't be read, which
 * may be private. A map still being made, in a hidden folder until it's
 * whole, never goes, nor does one a crash left half made: its details may
 * not say yet that it's private. Widgets' choices stay out: they're kept by
 * this phone's widget ids, which another phone gives other widgets. Each
 * folder the files are in comes right before the first of them: on a new
 * phone, a file is only restored into a folder the backup rules name once
 * that folder is there.
 */
internal fun backupFiles(files: File, quota: Long, newPhone: Boolean, privateMaps: Boolean = true): List<File> {
    val leftOut = if (privateMaps) emptySet() else privateMapsIn(File(files, MAPS_DIRECTORY))
    val chosen = BackupChoice(files, quota - BACKUP_MARGIN)
    chosen.takeWhole(filesIn(File(files, "datastore")).filterNot(::isWidgetState))
    val pictures = mutableListOf<File>()
    val sounds = mutableListOf<File>()
    val videos = mutableListOf<File>()
    val earlier = mutableListOf<File>()
    val entries = File(files, MAPS_DIRECTORY).listFiles().orEmpty()
    // What's beside the maps, such as the layout's record; the count of private maps left out goes last ([backUp]).
    chosen.takeWhole(entries.filter { it.isFile && !unfinished(it) && it.name != PRIVATE_LEFT_OUT_NOTE }.sortedBy { it.name })

    for (entry in entries.filter { isMap(it) && it.name !in leftOut }.sortedWith(compareByDescending<File> { File(it, MAP_FILE).lastModified() }.thenBy { it.name })) {
        val media = File(entry, MEDIA_DIRECTORY)

        if (!chosen.takeWhole(filesIn(entry).filter { it.parentFile != media })) {
            continue
        }

        val text = File(entry, MAP_FILE).takeIf { it.isFile }?.let { runCatching { it.readTextSafely() }.getOrNull() }.orEmpty()
        val pointedAt = MediaNames.references(text).toSet()

        for (file in media.listFiles().orEmpty()) {
            when {
                !file.isFile || !isMediaName(file.name) -> Unit
                file.name !in pointedAt -> earlier += file
                isSoundFile(file.name) -> sounds += file
                isVideoName(file.name) -> videos += file
                else -> pictures += file
            }
        }
    }

    (bySize(pictures) + bySize(sounds) + bySize(videos)).forEach(chosen::take)

    if (newPhone) {
        val history = File(files, HISTORY_DIRECTORY)
        val trash = File(files, TRASH_DIRECTORY)
        val trashLeftOut = if (privateMaps) emptySet() else privateMapsIn(trash)
        filesIn(history).filter { it.relativeTo(history).invariantSeparatorsPath.substringBefore('/') !in leftOut }.forEach(chosen::take)
        bySize(earlier).forEach(chosen::take)
        filesIn(trash).filter { it.relativeTo(trash).invariantSeparatorsPath.substringBefore('/') !in trashLeftOut }.forEach(chosen::take)
        File(files, FOLDER_DELETIONS).takeIf { it.isFile }?.let(chosen::take)
    }

    return chosen.files()
}

/**
 * Writes `chosen`, the files and folders [backupFiles] picked from `files`,
 * through `write`, Android's own. Without `privateMaps`, whether a map is
 * private is looked at again right before each of its own entries goes, its
 * folder first, which names it: one made private since they were picked, or
 * whose details can't be read now, stays out from then on. Last, beside the
 * maps, the backup carries how many private maps it left out
 * ([notePrivateMapsLeftOut]), gone from here once it's written.
 */
internal fun backUp(files: File, chosen: List<File>, privateMaps: Boolean, write: (File) -> Unit) {
    val written = mutableSetOf<File>()
    val keptOut = mutableSetOf<File>()

    for (file in chosen) {
        val map = mapFolderOf(file, files)

        if (!privateMaps && map != null && (map in keptOut || isPrivateMap(map))) {
            keptOut += map
            continue
        }

        write(file)
        written += file
    }

    // The maps whose text didn't go for being private: the ones left out as the files were picked, and since. A
    // backup with room for nothing carries nothing, the count neither; any other has room for it in its margin.
    val maps = File(files, MAPS_DIRECTORY)
    val leftOut = if (privateMaps || written.isEmpty()) 0 else maps.listFiles().orEmpty().count { isMap(it) && File(it, MAP_FILE) !in written && (it in keptOut || isPrivateMap(it)) }
    val note = notePrivateMapsLeftOut(files, leftOut) ?: return

    try {
        if (maps !in written) {
            write(maps)
        }

        write(note)
    } finally {
        note.delete()
    }
}

/** How many of the maps in `files` a backup of `chosen` had no room for, their text and all; private maps kept out on purpose (without `privateMaps`) aren't counted. */
internal fun mapsLeftOut(files: File, chosen: List<File>, privateMaps: Boolean = true): Int {
    val taken = chosen.toSet()
    val maps = File(files, MAPS_DIRECTORY)
    val kept = if (privateMaps) emptySet() else privateMapsIn(maps)
    return maps.listFiles().orEmpty().count { folder -> isMap(folder) && folder.name !in kept && File(folder, MAP_FILE).let { it.isFile && it !in taken } }
}

/** The maps in `folder` (the maps' or the trash's) whose details say they're private, or can't be read: those may be private, as the app reads them too. */
private fun privateMapsIn(folder: File): Set<String> =
    folder.listFiles().orEmpty().filter { it.isDirectory && isPrivateMap(it) }.map { it.name }.toSet()

/** Whether the map in `folder` is private, as its details say; details that can't be read may have said so, and it is. */
private fun isPrivateMap(folder: File): Boolean = MetadataFile.readOrPrivate(File(folder, METADATA_FILE), MapMetadata()).isPrivate

/** Whether `folder`, in the maps' folder, is a map's: a map still being made is in a hidden one until it's whole. */
private fun isMap(folder: File): Boolean = folder.isDirectory && !folder.name.startsWith(".")

/**
 * The folder of the map that `file`, among the app's `files`, is part of,
 * its history included: in the maps' folder, or the trash's. Null for
 * what's no map's, such as the settings, or the layout's record beside the
 * maps, which is hidden, as no map's folder is.
 */
private fun mapFolderOf(file: File, files: File): File? {
    val parts = file.relativeTo(files).invariantSeparatorsPath.split('/')

    if (parts.size < 2 || parts[1].startsWith(".")) {
        return null
    }

    return when (parts[0]) {
        MAPS_DIRECTORY, HISTORY_DIRECTORY -> File(File(files, MAPS_DIRECTORY), parts[1])
        TRASH_DIRECTORY -> File(File(files, TRASH_DIRECTORY), parts[1])
        else -> null
    }
}

/**
 * Writes, beside the maps in `files`, how many private maps a backup that
 * can't take them leaves out, `count`, for the backup to carry: a count only,
 * never which maps, nor their titles. Returns the note, which the backup
 * deletes once it's made; null when it leaves none out.
 */
internal fun notePrivateMapsLeftOut(files: File, count: Int): File? {
    val note = File(File(files, MAPS_DIRECTORY), PRIVATE_LEFT_OUT_NOTE)

    if (count == 0) {
        note.delete()
        return null
    }

    return runCatching { writeAtomically(note, count.toString()) }.map { note }.getOrNull()
}

/**
 * After a restore, the count of private maps its backup left out, if it
 * carried one, moves out of the maps' folder to where Home reads it, and
 * where no later backup takes it.
 */
internal fun keepRestoredPrivateMapsNote(files: File) {
    val restored = File(File(files, MAPS_DIRECTORY), PRIVATE_LEFT_OUT_NOTE)

    if (restored.isFile && !restored.renameTo(File(files, RESTORED_PRIVATE_NOTE))) {
        restored.delete()
    }
}

/** How many private maps the backup this phone was restored from left out, until Home has said so; 0 otherwise. */
fun privateMapsLeftOutOfRestore(files: File): Int = runCatching { File(files, RESTORED_PRIVATE_NOTE).readText().trim().toInt() }.getOrDefault(0)

/** Home has said how many private maps the restore left out: it isn't said again. */
fun forgetPrivateMapsLeftOutOfRestore(files: File) {
    File(files, RESTORED_PRIVATE_NOTE).delete()
}

/** How many maps the last backup to the cloud had no room for, as it noted in `files`; 0 when it had room for all, or before one. */
fun mapsLeftOutOfBackup(files: File): Int = runCatching { File(files, LEFT_OUT_NOTE).readText().trim().toInt() }.getOrDefault(0)

private fun noteLeftOut(files: File, leftOut: Int) {
    val note = File(files, LEFT_OUT_NOTE)

    runCatching {
        if (leftOut > 0) {
            writeAtomically(note, leftOut.toString())
        } else {
            note.delete()
        }
    }
}

/**
 * The files a backup takes, and the folders they're in, within `room`
 * bytes: each file, or each group of files, goes only if it fits with the
 * folders it brings.
 */
private class BackupChoice(private val files: File, private var room: Long) {
    private val folders = sortedSetOf<String>()
    private val chosen = mutableListOf<File>()

    /** Takes `file` if it fits; returns whether it did. */
    fun take(file: File): Boolean = takeWhole(listOf(file))

    /** Takes all of `group` if it fits, or none of it; returns whether it did. */
    fun takeWhole(group: List<File>): Boolean {
        val brought = group.flatMap(::foldersOf).filter { it.path !in folders }.distinctBy { it.path }
        val size = group.sumOf(::stored) + brought.sumOf(::stored)

        if (size > room) {
            return false
        }

        room -= size
        brought.forEach { folders += it.path }
        chosen += group
        return true
    }

    /**
     * The files in the order they were taken, each folder right before the
     * first file in it, outermost first: a map's folder, which names it,
     * goes as its own files do.
     */
    fun files(): List<File> {
        val placed = mutableSetOf<String>()
        return chosen.flatMap { file -> foldersOf(file).reversed().filter { placed.add(it.path) } + file }
    }

    /** The folders `file` is in, below the app's files. */
    private fun foldersOf(file: File): List<File> = generateSequence(file.parentFile) { it.parentFile }.takeWhile { it != files }.toList()
}

/** Whether `file` is a widget's choice of map, which Glance keeps by the widget's id on this phone. */
private fun isWidgetState(file: File): Boolean = file.name.startsWith("appWidget-")

/** The files in `directory` and the folders in it, but not ones a write left unfinished, in the order of their paths. */
private fun filesIn(directory: File): List<File> =
    directory.walkTopDown().filter { it.isFile && !unfinished(it) }.sortedBy { it.path }.toList()

/** Whether a write left `file` unfinished ([writeAtomically] writes beside a file, then gives it its name). */
private fun unfinished(file: File): Boolean = file.name.endsWith(".partial")

private fun bySize(files: List<File>): List<File> = files.sortedWith(compareBy({ it.length() }, { it.path }))

private fun stored(file: File): Long = if (file.isFile) file.length() + BACKUP_FILE_OVERHEAD else BACKUP_FILE_OVERHEAD
