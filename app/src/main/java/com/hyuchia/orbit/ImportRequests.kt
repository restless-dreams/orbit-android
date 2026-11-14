package com.hyuchia.orbit

import android.net.Uri
import androidx.core.net.toUri
import com.hyuchia.orbit.data.writeAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Imports other apps asked of Orbit, each kept on disk, in Orbit's own
 * files, from before it starts until a screen takes what it brought, so a
 * screen Android brings back after stopping Orbit midway knows what it was
 * waiting for: it's imported again, or what it brought is shown, never both
 * ([Intake.resumeImports]). The files are read again with the permission
 * the screen Android brings back still holds; a new screen, whose task was
 * closed, holds none, and lets them go. Nothing here goes into backups.
 */
class ImportRequests(private val folder: File) {
    /** An import, by its id: what it was asked to read, and once it's done, the map it made. */
    @Serializable
    data class Request(
        /** What it reads: [FILE], [TEXT] or [FILES]. */
        val kind: String,

        val addresses: List<String> = emptyList(),
        val text: String? = null,
        val subject: String? = null,

        /** It's done: what it brought was said, or waits for a screen to say it. */
        val done: Boolean = false,

        /** The map it made, which opens. */
        val map: String? = null,

        /** The maps it made so far, in order, each as soon as it was there: run again, it makes only the rest. */
        val made: List<String> = emptyList(),
    ) {
        val uris: List<Uri> get() = addresses.map { it.toUri() }
    }

    /** Keeps `request`, import `id`, before it starts; false when it can't be kept, as on a full storage: it's imported all the same. */
    fun start(id: String, request: Request): Boolean = write(id, request)

    /** Import `id` made `maps`, which it keeps, so run again it doesn't make them again: a workbook's sheets, all at once. */
    @Synchronized
    fun made(id: String, maps: List<String>) {
        read(id)?.let { write(id, it.copy(made = it.made + maps)) }
    }

    /** A map import `id` made went to the trash after all: run again, it isn't counted as made. */
    @Synchronized
    fun unmade(id: String, map: String) {
        read(id)?.let { write(id, it.copy(made = it.made - map)) }
    }

    /** Import `id` is done, and made `map`, if it made one. */
    @Synchronized
    fun finish(id: String, map: String?) {
        read(id)?.let { write(id, it.copy(done = true, map = map)) }
    }

    /** Import `id` as it's kept, or null once it isn't. */
    fun read(id: String): Request? {
        val file = fileOf(id) ?: return null

        return try {
            JSON.decodeFromString(Request.serializer(), file.readText())
        } catch (unreadable: IOException) {
            null
        } catch (unreadable: SerializationException) {
            null
        } catch (unreadable: IllegalArgumentException) {
            null
        }
    }

    /** A screen took what import `id` brought: nothing will ask for it again. */
    fun forget(id: String) {
        fileOf(id)?.delete()
    }

    /** Lets go of every import but those `kept` lists: no screen will ask for them again. */
    fun forgetAllBut(kept: Set<String>) {
        folder.listFiles { file -> file.name.endsWith(SUFFIX) && file.name.removeSuffix(SUFFIX) !in kept }.orEmpty().forEach { it.delete() }
    }

    private fun write(id: String, request: Request): Boolean = try {
        folder.mkdirs()
        writeAtomically(File(folder, id + SUFFIX), JSON.encodeToString(Request.serializer(), request))
        true
    } catch (failed: IOException) {
        Logs.warn("ImportRequests", "Couldn't keep an import", failed)
        false
    }

    private fun fileOf(id: String): File? = File(folder, id + SUFFIX).takeIf { id.isNotEmpty() && '/' !in id && !id.startsWith(".") && it.isFile }

    companion object {
        /** A file opened with Orbit, or shared to it on its own. */
        const val FILE = "file"

        /** Text shared to Orbit, with its subject, for a new map. */
        const val TEXT = "text"

        /** Files shared to Orbit for a new map, while no map was open to take them. */
        const val FILES = "files"

        private const val SUFFIX = ".json"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
