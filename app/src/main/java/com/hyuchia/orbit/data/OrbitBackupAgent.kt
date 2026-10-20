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

/**
 * What Android's backup takes of Orbit. A backup stops altogether past its
 * quota, 25 MB on Google's cloud, and one video from the phone can be more
 * than that. So the settings and the maps' text and details always go, then
 * the pictures and videos the maps point at, smallest first, as long as they
 * fit. Moving to a new phone, from Android 12 on, takes the maps' history
 * too, as its backup rules allow. Restoring is Android's own: each file goes
 * back where it was, at the time it was last changed.
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
        val history = toNewPhone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        for (file in backupFiles(filesDir, data.quota, history)) {
            fullBackupFile(file, data)
        }
    }

    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File, type: Int, mode: Long, mtime: Long) {
        super.onRestoreFile(data, size, destination, type, mode, mtime)

        // Android gives a restored file its time in seconds where it takes
        // milliseconds, and every map would read as last changed in 1970.
        if (type == TYPE_FILE && mtime in 1 until MILLISECONDS_FROM && destination.lastModified() == mtime) {
            destination.setLastModified(mtime * 1000)
        }
    }
}

/**
 * The files a backup of at most `quota` bytes takes from `files`, the app's
 * own: the settings and the maps' text and details, then the pictures and
 * then the videos the maps point at, each smallest first, while they fit.
 * With `history`, the maps' earlier versions follow, then the pictures and
 * videos only those point at. The folders they're in come first, each
 * before what's in it: on a new phone, a file is only restored into a
 * folder the backup rules name once that folder is there.
 */
internal fun backupFiles(files: File, quota: Long, history: Boolean): List<File> {
    val roots = listOfNotNull(File(files, "datastore"), File(files, "maps"), File(files, HISTORY_DIRECTORY).takeIf { history })
    val folders = roots.flatMap { root -> root.walkTopDown().filter { it.isDirectory }.toList() }.sortedBy { it.path }
    val chosen = filesIn(File(files, "datastore")).toMutableList()
    val pictures = mutableListOf<File>()
    val videos = mutableListOf<File>()
    val earlier = mutableListOf<File>()

    for (entry in File(files, "maps").listFiles().orEmpty().sortedBy { it.name }) {
        if (entry.isFile) {
            if (!unfinished(entry)) {
                chosen += entry
            }

            continue
        }

        val media = File(entry, MEDIA_DIRECTORY)
        chosen += filesIn(entry).filter { it.parentFile != media }
        val text = File(entry, MAP_FILE).takeIf { it.isFile }?.let { runCatching { it.readTextSafely() }.getOrNull() }.orEmpty()
        val pointedAt = picturesIn(text)

        for (file in media.listFiles().orEmpty()) {
            when {
                !file.isFile || !isMediaName(file.name) -> Unit
                file.name !in pointedAt -> earlier += file
                isVideoName(file.name) -> videos += file
                else -> pictures += file
            }
        }
    }

    val extras = bySize(pictures) + bySize(videos) +
        if (history) filesIn(File(files, HISTORY_DIRECTORY)) + bySize(earlier) else emptyList()
    var room = quota - BACKUP_MARGIN - folders.sumOf(::stored) - chosen.sumOf(::stored)

    for (file in extras) {
        val size = stored(file)

        if (size <= room) {
            chosen += file
            room -= size
        }
    }

    return folders + chosen
}

/** The files in `directory` and the folders in it, but not ones a write left unfinished, in the order of their paths. */
private fun filesIn(directory: File): List<File> =
    directory.walkTopDown().filter { it.isFile && !unfinished(it) }.sortedBy { it.path }.toList()

/** Whether a write left `file` unfinished ([writeAtomically] writes beside a file, then gives it its name). */
private fun unfinished(file: File): Boolean = file.name.endsWith(".partial")

private fun bySize(files: List<File>): List<File> = files.sortedWith(compareBy({ it.length() }, { it.path }))

private fun stored(file: File): Long = if (file.isFile) file.length() + BACKUP_FILE_OVERHEAD else BACKUP_FILE_OVERHEAD
