package com.hyuchia.orbit.nearby

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.UUID

/** There's no room where a shared map's zip is made for all of it: none of it is begun. */
internal class NoRoomException : IOException("No room for the map's zip")

/**
 * The zip of a map shown to computers at `code`, for the browsers that
 * download it: made once for each version of the map, one at a time, into a
 * file of its own in `folder`, so a room of browsers asking at once waits
 * for one zip rather than each making its own, every picture and video
 * inside. A download already going keeps the file it opened, as the zip of
 * a newer version replaces it; the version before stays besides, for one
 * about to start, and older ones go, and all of them as the share ends.
 */
internal class SharedZip(private val folder: File, private val code: String) {
    private val making = Mutex()

    /** The zip last made, and the version of the map it was made from. */
    private var latest: Pair<String, File>? = null

    /** The zip made before it. */
    private var before: File? = null

    private var ended = false

    /**
     * The zip of the map's version `version`, made by `write` into a new
     * file when there's none: null once the share ended. What `write`
     * throws, it throws, its file gone.
     */
    suspend fun of(version: String, write: suspend (File) -> Unit): File? = making.withLock {
        synchronized(this) {
            if (ended) {
                return@withLock null
            }

            latest?.takeIf { (madeFrom, zip) -> madeFrom == version && zip.isFile }?.let { return@withLock it.second }
        }

        folder.mkdirs()
        val file = File(folder, "$code-${UUID.randomUUID()}.zip")
        var made = false

        try {
            write(file)
            made = true
        } finally {
            if (!made) {
                file.delete()
            }
        }

        synchronized(this) {
            // Made as the share ended: no one is sent it.
            if (ended) {
                file.delete()
                return@withLock null
            }

            before?.delete()
            before = latest?.second
            latest = version to file
        }

        file
    }

    /** The share ended: its zips go. A download going has its file open, and finishes. */
    fun end() {
        synchronized(this) {
            ended = true
            listOfNotNull(latest?.second, before).forEach { it.delete() }
            latest = null
            before = null
        }
    }
}
