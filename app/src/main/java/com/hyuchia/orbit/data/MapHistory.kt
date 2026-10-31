package com.hyuchia.orbit.data

import com.hyuchia.orbit.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException

/** Earlier versions of a map: in `history/<map>/` (and in a map's own folder before layout 2, see [StorageLayout]). */
const val HISTORY_DIRECTORY = "history"

/** A version kept as the file came in from elsewhere, before Orbit wrote it: `<time>.original.md`. */
internal const val ORIGINAL_SUFFIX = ".original.md"

/**
 * Why each version in a map's history was kept, by its file's name, beside
 * the versions (see [VersionReason]). Every release lists only the versions'
 * own files (`VERSION_NAME`) and thins out only those, so older ones never
 * see it, and leave it be.
 */
internal const val REASONS_FILE = "reasons.json"

/** A version's file name in a history: its time, and whether it's an original. */
internal val VERSION_NAME = Regex("""(\d+)(\.original)?\.md""")

/**
 * Each map's history: its earlier versions in `history/<map>/`, kept as it
 * changes and thinned out as they age, with why each was kept, and the
 * files maps came in as, kept as they came.
 */
internal class MapHistory(store: MapStore) : MapPart(store) {
    /**
     * The map's earlier versions, the newest first, with why each was kept
     * where that's known. Listed from the files' names, without reading them,
     * so a long history lists at once: what each holds is read when it's
     * shown ([readVersion]), and one that can't be read says so then.
     */
    suspend fun versions(id: String): List<MapVersion> = withContext(Dispatchers.IO) {
        val reasons = reasons(id)

        versionFiles(id).sortedByDescending { it.first }.map { (time, file) ->
            MapVersion(time, original = file.name.endsWith(ORIGINAL_SUFFIX), reason = reasons[file.name])
        }
    }

    /** The map's text as it was at `time`, if that version is still kept and can be read. */
    suspend fun readVersion(id: String, time: Long): String? = withContext(Dispatchers.IO) {
        val file = versionFiles(id).firstOrNull { it.first == time }?.second ?: return@withContext null

        try {
            file.readTextSafely()
        } catch (unreadable: IOException) {
            null
        }
    }

    /**
     * Keeps `text` in the map's history without making it the map: a version
     * no write took (one made before the map changed elsewhere), so it's
     * never lost.
     */
    suspend fun keepAside(id: String, text: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (canWrite.value && File(folder(id), MAP_FILE).isFile) {
                keepAsideLocked(id, text)
            }
        }
    }

    /**
     * Keeps the map as it is now in its history, as an earlier version (the
     * one kept at `restoring`) is about to replace it. Storage that's full or
     * gone leaves it out, which the log says: the screen's undo still has it.
     */
    suspend fun keepCurrentVersion(id: String, restoring: Long? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                File(folder(id), MAP_FILE).takeIf { it.isFile && canWrite.value }?.let { keepVersionText(id, it.readTextSafely(), reason = VersionReason.Restoring(restoring)) }
            } catch (failed: IOException) {
                Logs.warn("MapRepository", "Couldn't keep the map before a version replaced it", failed)
            }
        }
    }

    /** Keeps `text` in the map's history, set aside; storage that's full or gone leaves it out, which the log says. */
    fun keepAsideLocked(id: String, text: String) {
        try {
            keepVersionText(id, text, reason = VersionReason.Aside)
        } catch (failed: IOException) {
            Logs.warn("MapRepository", "Couldn't keep a text set aside", failed)
        }
    }

    /** The map's kept versions, with when each was the map: files with a version's name, and nothing else that happens to have one. */
    fun versionFiles(id: String): List<Pair<Long, File>> =
        File(historyDirectory, id).listFiles().orEmpty().mapNotNull { file ->
            VERSION_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { file.isFile }?.let { it to file }
        }

    /** When each kept version was the map, originals left out: they're never thinned, and don't count as checkpoints. */
    fun versionTimes(id: String): List<Long> =
        versionFiles(id).filterNot { it.second.name.endsWith(ORIGINAL_SUFFIX) }.map { it.first }

    /**
     * Keeps `text` in the map's history for `reason`, unless it's `unless`
     * (the text about to replace it) or the same as the latest version; then
     * thins the history out as it ages (see [versionsToKeep]).
     */
    fun keepVersionText(id: String, text: String, unless: String? = null, reason: VersionReason) {
        val history = File(historyDirectory, id)
        val times = versionTimes(id)
        val latest = times.maxOrNull()?.let { File(history, "$it.md") }

        if (text == unless || (latest != null && latest.isFile && latest.readTextSafely() == text)) {
            return
        }

        // Named by the clock, never after a version a wrong clock dated ahead of it, which would date every version after it ahead too.
        var now = clock()

        while (now in times) {
            now += 1
        }

        writeAtomically(File(history, "$now.md"), text)
        recordReason(history, "$now.md", reason)

        // A clock that's wrong would age the versions wrongly: they wait for it to be right before any is thinned out.
        if (clockTrusted()) {
            val bigChanges = reasons(id).filterValues(::isBigChange).keys.mapNotNull { VERSION_NAME.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }.toSet()
            val kept = versionsToKeep(versionTimes(id), clock(), preferred = bigChanges)
            versionTimes(id).filterNot { it in kept }.forEach { File(history, "$it.md").delete() }
        }
    }

    /** Keeps a file as it came in, for good: Orbit may write it its own way later. `name` is its file's, when it had one. */
    fun keepOriginal(id: String, text: String, name: String?) {
        val history = File(historyDirectory, id)
        val file = File(history, "${clock()}$ORIGINAL_SUFFIX")
        writeAtomically(file, text)
        recordReason(history, file.name, VersionReason.CameIn(name))
    }

    /** The entries of the history's reasons file, by version file; none when it isn't there or can't be read. */
    fun reasonEntries(history: File): JsonObject {
        val file = File(history, REASONS_FILE)

        if (!file.isFile) {
            return JsonObject(emptyMap())
        }

        val whole = runCatching { mapsJson.parseToJsonElement(file.readTextSafely()) as? JsonObject }.getOrNull()
        return whole?.get("versions") as? JsonObject ?: JsonObject(emptyMap())
    }

    /**
     * Records why the version in the file `name` was kept. Entries for
     * versions that are gone (thinned out, here or by an older release) go
     * with them, judged by the files' times, so a newer Orbit's own versions
     * keep theirs; anything else in the file stays as it was.
     */
    fun recordReason(history: File, name: String, reason: VersionReason) {
        val file = File(history, REASONS_FILE)
        val whole = runCatching { mapsJson.parseToJsonElement(file.readTextSafely()) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val times = history.list().orEmpty().map { it.substringBefore('.') }.toSet()
        val entries = reasonEntries(history).filterKeys { it.substringBefore('.') in times } + (name to reason.toJson())
        writeAtomically(file, JsonObject(whole + ("versions" to JsonObject(entries))).toString())
    }

    /** Whether a version was kept before a big change, which thinning keeps over the others of its stretch: the map as it was before it may be wanted back. */
    private fun isBigChange(reason: VersionReason): Boolean =
        reason is VersionReason.Removing || reason is VersionReason.Restoring || reason == VersionReason.Text ||
            reason is VersionReason.FromDevice || reason is VersionReason.FromFile || reason == VersionReason.FromFolder

    /** Why each of the map's kept versions was kept, by its file's name; none for versions from before reasons were kept. */
    private fun reasons(id: String): Map<String, VersionReason> =
        reasonEntries(File(historyDirectory, id)).mapNotNull { (name, entry) -> (entry as? JsonObject)?.let(VersionReason::read)?.let { name to it } }.toMap()
}
