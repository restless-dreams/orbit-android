package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.net.Uri
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.CheckedMap
import com.hyuchia.orbit.page.ImportProblem
import com.hyuchia.orbit.page.ImportWarning
import com.hyuchia.orbit.page.ImportedFile
import com.hyuchia.orbit.page.ImportedFileMap
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PagePool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

sealed interface ImportResult {
    /** A map that came in; `notes` say what another app's map had that Orbit keeps another way, or couldn't keep. */
    data class Imported(val id: String, val title: String, val notes: List<String> = emptyList()) : ImportResult

    /**
     * A zip of maps, as Export all writes them, or a workbook's sheets: `added` came in, `alreadyHere` were here.
     * What's said of the private maps here waits until it's known whether they hide ([ImportedMaps]). `stopped`
     * says why a zip of maps stopped partway, when it did ([ImportStoppedException]): the rest is what came before.
     */
    data class ImportedMany(
        val added: Int,
        val alreadyHere: Int,
        val notes: List<String> = emptyList(),
        val privateAlreadyHere: Int = 0,
        val filledIn: Int = 0,
        val privateFilledIn: Int = 0,
        val pastRoom: Int = 0,
        val privatePastRoom: Int = 0,
        val stopped: String? = null,
    ) : ImportResult

    /** A map that's already here, `existing`, in another version: it can update it, or be kept as well. */
    data class AlreadyHere(val bundle: MapBundle, val existing: String, val name: String? = null) : ImportResult

    data class Failed(val reason: String) : ImportResult

    /**
     * Several files brought in at once, some of them other versions of maps
     * that are here: each of `otherVersions` is asked about in turn, as one
     * file would be, then `summary` says what the rest brought, when there's
     * anything to say.
     */
    data class SeveralFiles(val otherVersions: List<AlreadyHere>, val summary: ImportResult?) : ImportResult
}

/**
 * The most a map's text may be, in a file opened or let go on the list of
 * maps, and in a zip ([ZIP_TEXT_LIMIT]): a map is text, so anything larger
 * isn't one.
 */
internal const val MAP_TEXT_LIMIT = 8L * 1024 * 1024

/** A heading or a list item: text with a shape Orbit can draw. */
private val STRUCTURE = Regex("""^\s{0,3}#{1,6}\s|^\s*(?:[-*+]|\d+[.)])\s""")

/** The longest a map's title gets from shared text; a longer line is kept whole as the map's note. */
private const val TITLE_LENGTH = 80

/** A markdown heading: text with one is a map of its own. */
private val HEADING = Regex("""^\s{0,3}#{1,6}\s""")

/** How many spaces a tab counts for in an outline's indents (web/paste.ts reads them the same). */
private const val TAB_WIDTH = 4

/** How many times files shared into a new map are put on its idea, the map read again each time, while it changes as they're put there. */
private const val ATTACHING_TRIES = 3

/** What each of Orbit's warnings about another app's map says, by its code. */
private val WARNING_TEXTS = mapOf(
    "floating-ideas-moved" to R.plurals.data_import_floating_ideas_moved,
    "summaries-moved" to R.plurals.data_import_summaries_moved,
    "callouts-moved" to R.plurals.data_import_callouts_moved,
    "boundaries-in-notes" to R.plurals.data_import_boundaries_in_notes,
    "links-in-notes" to R.plurals.data_import_links_in_notes,
    "missing-link-targets" to R.plurals.data_import_missing_link_targets,
    "icons-not-kept" to R.plurals.data_import_icons_not_kept,
    "pictures-not-kept" to R.plurals.data_import_pictures_not_kept,
    "attachments-not-kept" to R.plurals.data_import_attachments_not_kept,
    "local-links-not-kept" to R.plurals.data_import_local_links_not_kept,
    "encrypted-branches-not-read" to R.plurals.data_import_encrypted_branches_not_read,
    "formatting-not-kept" to R.plurals.data_import_formatting_not_kept,
)

/** Whether a file at an address with this scheme and authority comes from another app: a content address that isn't Orbit's own. */
internal fun importable(scheme: String?, authority: String?, ownAuthority: String): Boolean =
    scheme == ContentResolver.SCHEME_CONTENT && authority != null && authority != ownAuthority

/**
 * The maps an import made before Orbit stopped it midway (`made`, in the
 * order it made them), which it doesn't make again when it runs again, and
 * what hears each map it makes, as soon as it's there (`making`), to keep it
 * with the import ([com.hyuchia.orbit.ImportRequests]); a workbook's sheets
 * come in together, so it hears of them together.
 */
class ImportProgress(
    val made: List<String> = emptyList(),
    val making: (List<String>) -> Unit = {},

    /** Hears each map made before that goes to the trash after all, as a new map for files that couldn't be put on it does. */
    val unmaking: (String) -> Unit = {},
) {
    /** The `index`th map made before, while it's still there (`exists`). */
    internal fun made(index: Int, exists: (String) -> Boolean): String? = made.getOrNull(index)?.takeIf(exists)

    companion object {
        val NONE = ImportProgress()
    }
}

/**
 * Turns what other apps hand Orbit into maps: markdown and YAML files, zips
 * of a map and its pictures, maps from other apps (OPML, FreeMind and
 * Freeplane, XMind), and text shared from anywhere. Each map keeps the text
 * it came from in its history, as it came in. What went wrong is said in
 * `words`. A private map that's `locked` now is found by its identity alone
 * as a zip of every map comes in ([MapRepository.importAll]).
 */
class Importer(
    private val maps: MapRepository,
    private val pages: PagePool,
    private val words: Words,
    private val locked: (MapSummary) -> Boolean = { false },
) {

    suspend fun importFile(uri: Uri, resolver: ContentResolver, progress: ImportProgress = ImportProgress.NONE): ImportResult {
        // Orbit reads what it's handed with its own access, so a path on the phone or one of
        // Orbit's own shared files would give another app a way to read Orbit's private files.
        if (!importable(uri.scheme, uri.authority, "${BuildConfig.APPLICATION_ID}.files")) {
            return ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        }

        return try {
            // Even asking a file's name can be refused, when the app that gave it can't give it: then the address names it.
            val (name, type) = withContext(Dispatchers.IO) { displayName(uri, resolver).orEmpty() to fromProvider { resolver.getType(uri) }.orEmpty() }
            val kind = withContext(Dispatchers.IO) { kindOf(uri, resolver, name, type) } ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

            if (kind is FileKind.FromOtherApp) {
                return importFromOtherApp(uri, resolver, name, kind.format, progress)
            }

            if (kind == FileKind.Zip) {
                // The maps here must be known first, to find the one the zip is.
                maps.maps.filterNotNull().first()
                val many = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.holdsManyMaps(it) } }
                    ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

                if (many) {
                    val imported = try {
                        withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.importAll(it, locked) } }
                            ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))
                    } catch (stopped: ImportStoppedException) {
                        // What came in before it stopped is said as exactly as a whole import is, and why it stopped first.
                        return importedMany(stopped.soFar, stoppedNote(stopReason(stopped.cause, words.language), words))
                    }

                    return importedMany(imported)
                }

                // Made before Orbit stopped: it's there whole, as a map's zip comes in in one step.
                progress.made(0, maps::exists)?.let { id ->
                    return ImportResult.Imported(id, maps.summary(id)?.title ?: words.string(R.string.data_map_without_a_title))
                }

                // A map's zip unpacks to about its size: one there's no room for here is refused before any of it is read.
                val size = withContext(Dispatchers.IO) { fromProvider { nameAndSize(uri, resolver).second }?.takeIf { it > 0 } ?: zipLength(uri, resolver) }
                maps.noRoomForBundle(size)?.let { return ImportResult.Failed(it) }

                val bundle = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.readBundle(it) } }
                    ?: return ImportResult.Failed(words.string(R.string.data_no_map_in_zip))
                val existing = bundle.uuid?.let { uuid -> withContext(Dispatchers.IO) { maps.findByIdentity(uuid) } }

                if (existing != null) {
                    return ImportResult.AlreadyHere(bundle, existing, name.ifBlank { null })
                }

                val id = maps.receive(bundle).also { progress.making(listOf(it)) }
                return ImportResult.Imported(id, bundle.title, notes = listOfNotNull(leftOutNote(bundle.leftOut)))
            }

            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readAtMost((MAP_TEXT_LIMIT + 1).toInt())
                    if (bytes.size > MAP_TEXT_LIMIT) null else decodeText(bytes)
                }
            } ?: return ImportResult.Failed(words.string(R.string.data_file_too_big_for_map))

            val yaml = name.endsWith(".yaml", ignoreCase = true) || name.endsWith(".yml", ignoreCase = true) || "yaml" in type
            importText(text, name.substringBeforeLast('.').ifBlank { null }, yaml, plain = isPlainText(name, type), fileName = name.ifBlank { null }, progress = progress)
        } catch (unreadable: SecurityException) {
            ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        } catch (refused: RefusedZipException) {
            ImportResult.Failed(refused.message.orEmpty())
        } catch (unreadable: IOException) {
            ImportResult.Failed(words.string(R.string.data_file_unreadable))
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (unreadable: RuntimeException) {
            // Whatever else the app that gave it throws back (see fromProvider), or a zip whose names aren't text.
            Logs.warn(TAG, "Couldn't read a file to import", unreadable)
            ImportResult.Failed(words.string(R.string.data_file_unreadable))
        }
    }

    /**
     * What a zip of every map brought, `imported`, to say once it's known
     * whether private maps hide, which counts the files it brought into maps
     * already here; `stopped`, when it stopped partway.
     */
    private fun importedMany(imported: ImportedMaps, stopped: String? = null) = ImportResult.ImportedMany(
        imported.added,
        imported.alreadyHere,
        notes = zipNotes(filledIn = 0, skipped = imported.skipped, leftOut = imported.leftOut, words, stayedPrivate = imported.stayedPrivate),
        privateAlreadyHere = imported.privateAlreadyHere,
        filledIn = imported.filledIn,
        privateFilledIn = imported.privateFilledIn,
        pastRoom = imported.pastRoom,
        privatePastRoom = imported.privatePastRoom,
        stopped = stopped,
    )

    /** What the file at `uri`, named `name` and typed `type` by its app, holds, by those and its first bytes; null when it can't be opened. */
    private fun kindOf(uri: Uri, resolver: ContentResolver, name: String, type: String): FileKind? {
        val zipNamed = name.endsWith(".zip", ignoreCase = true) || type in MapFileTypes.ZIP
        return resolver.openInputStream(uri)?.use { OtherApps.kindOf(name, type, zipNamed, it) }
    }

    /**
     * Another app's map at `uri` (OPML, FreeMind and Freeplane, XMind), by
     * its name and format, as [importFile] tells it from other files; null
     * when it's any other file, or can't be opened. For Settings' Import
     * maps, which takes these as Open a file does, and other files as a zip
     * of maps.
     */
    internal suspend fun otherAppFile(uri: Uri, resolver: ContentResolver): Pair<String, OtherFormat>? = withContext(Dispatchers.IO) {
        val name = displayName(uri, resolver).orEmpty()
        val type = fromProvider { resolver.getType(uri) }.orEmpty()
        (kindOf(uri, resolver, name, type) as? FileKind.FromOtherApp)?.let { name to it.format }
    }

    /**
     * The maps in another app's file at `uri` ([otherAppFile]), made as
     * [importFile] makes them; null when it isn't one. Settings' Import maps
     * takes those first, and other files as a zip of maps.
     */
    suspend fun importFromOtherAppFile(uri: Uri, resolver: ContentResolver): ImportResult? {
        if (!importable(uri.scheme, uri.authority, "${BuildConfig.APPLICATION_ID}.files")) {
            return ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        }

        return try {
            val (name, format) = otherAppFile(uri, resolver) ?: return null
            importFromOtherApp(uri, resolver, name, format)
        } catch (unreadable: SecurityException) {
            ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        } catch (unreadable: IOException) {
            ImportResult.Failed(words.string(R.string.data_file_unreadable))
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (unreadable: RuntimeException) {
            Logs.warn(TAG, "Couldn't read a file to import", unreadable)
            ImportResult.Failed(words.string(R.string.data_file_unreadable))
        }
    }

    /** The maps in another app's file at `uri`, named `name`, in `format`: read whole, while it's no bigger than such a file may be. */
    private suspend fun importFromOtherApp(uri: Uri, resolver: ContentResolver, name: String, format: OtherFormat, progress: ImportProgress = ImportProgress.NONE): ImportResult {
        val limit = if (format == OtherFormat.XMind) OtherApps.WORKBOOK_LIMIT else MAP_TEXT_LIMIT
        val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readAtMost((limit + 1).toInt()) } }
            ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

        if (bytes.size > limit) {
            return ImportResult.Failed(words.string(R.string.data_file_too_big_for_map))
        }

        return importFromOtherApp(bytes, name.ifBlank { null }, format, progress)
    }

    /** How long a zip is, as its file says, when its app doesn't say in its listing; 0 when it can't tell, as for a stream. */
    private fun zipLength(uri: Uri, resolver: ContentResolver): Long = try {
        fromProvider { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }?.takeIf { it > 0 } ?: 0L
    } catch (unknown: IOException) {
        0L
    }

    /** That files of kinds this version doesn't know stayed out of a zip, when some did: a newer Orbit's, say. */
    private fun leftOutNote(leftOut: Int): String? = words.plural(R.plurals.data_zip_files_left_out, leftOut, leftOut).takeIf { leftOut > 0 }

    /** Text shared from another app, or dropped on the list, with the title it came with (an article's, say). */
    suspend fun importShared(text: String, subject: String?, progress: ImportProgress = ImportProgress.NONE): ImportResult =
        importText(text, title = subject, plain = true, progress = progress)

    /**
     * A new map for files shared to Orbit while no map is open: named after
     * the first, and holding them all on its idea, each copied in as it is.
     * One that can't join, too big or unreadable, is left out and said; with
     * none that could, there's no map, and nor is there when they can't be
     * put on its idea: it goes to the trash with them, and that's said.
     */
    suspend fun newMapWithFiles(uris: List<Uri>, resolver: ContentResolver, progress: ImportProgress = ImportProgress.NONE): ImportResult {
        val first = uris.firstOrNull() ?: return ImportResult.Failed(words.string(R.string.data_nothing_to_make_a_map_from))
        val title = ideaName(withContext(Dispatchers.IO) { displayName(first, resolver) }) ?: words.string(R.string.files_title)
        // Made before Orbit stopped, while its files were copied: they're copied into it again, unless they're in it already.
        val made = importText(title, plain = true, progress = progress) as? ImportResult.Imported ?: return ImportResult.Failed(words.string(R.string.files_unreadable))

        if (progress.made.isNotEmpty() && FILES_ATTRIBUTE in maps.read(made.id)) {
            return made
        }

        val files = mutableListOf<AddedFile>()
        val notes = mutableListOf<String>()

        for (uri in uris) {
            try {
                files += maps.addFile(made.id, uri, resolver) {}
            } catch (refused: MediaRefusedException) {
                notes += refused.message.orEmpty()
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (failed: Exception) {
                Logs.warn("Importer", "Couldn't copy a shared file", failed)
                notes += words.string(R.string.files_unreadable)
            }
        }

        if (files.isEmpty()) {
            maps.delete(made.id)
            progress.unmaking(made.id)
            return ImportResult.Failed(notes.firstOrNull() ?: words.string(R.string.files_unreadable))
        }

        val attached = attach(made.id, files)

        if (attached != WriteResult.Written) {
            val why = if (attached == WriteResult.NotWritten) maps.whyNotWritten() else maps.whyNotChanged()

            // A map without what it was made for isn't one to keep: it goes to the trash, the files with it. One deleted
            // meanwhile is gone already; one that won't go is there, and says why its files aren't on it. Run again, the
            // import makes another, never the one in the trash.
            return if (attached == WriteResult.Missing || maps.delete(made.id) != null) {
                progress.unmaking(made.id)
                ImportResult.Failed(why)
            } else {
                made.copy(notes = notes + why)
            }
        }

        return made.copy(notes = notes)
    }

    /**
     * Puts `files`, copied into new map `id`, on its idea: on its text as it
     * is now, read again when it changed meanwhile (opened and edited while
     * they copied, say), as a change from outside its screen, which a screen
     * showing it catches up with ([MapRepository.change]).
     */
    private suspend fun attach(id: String, files: List<AddedFile>): WriteResult {
        repeat(ATTACHING_TRIES) {
            val markdown = try {
                maps.read(id)
            } catch (unreadable: IOException) {
                return if (maps.exists(id)) WriteResult.Refused else WriteResult.Missing
            }

            val result = maps.change(id, "${markdown.trimEnd()}\n${filesAttribute(files)}\n", base = markdown, reason = VersionReason.Editing)

            if (result != WriteResult.Changed) {
                return result
            }
        }

        return WriteResult.Changed
    }

    /**
     * A map from text: orbit-md is kept as written, YAML becomes orbit-md,
     * and plain lines become a list under the first one (or under `title`).
     * Text that came as text (`plain`), not in a markdown file, keeps the
     * branches of lines indented under one another ([newMap]). YAML Orbit
     * can't read all of isn't imported: the map would lose what wasn't read.
     */
    suspend fun importText(
        text: String,
        fallbackTitle: String? = null,
        yaml: Boolean = false,
        title: String? = null,
        plain: Boolean = false,
        fileName: String? = null,
        progress: ImportProgress = ImportProgress.NONE,
    ): ImportResult {
        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            return ImportResult.Failed(words.string(R.string.data_nothing_to_make_a_map_from))
        }

        val markdown = if (isYamlMap(text, yaml)) {
            val checked = try {
                check(trimmed)
            } catch (unreadable: PageException) {
                return ImportResult.Failed(words.string(R.string.data_could_not_read_that, unreadable.message.orEmpty()))
            }

            checked.diagnostics.firstOrNull { it.isError }?.let { error ->
                val line = error.line
                return ImportResult.Failed(
                    if (line != null) {
                        words.string(R.string.data_yaml_unreadable_at_line, error.message, line)
                    } else {
                        words.string(R.string.data_yaml_unreadable, error.message)
                    },
                )
            }

            checked.markdown
        } else {
            newMap(text, title, plain, ::outline)
        }

        // Made before Orbit stopped: it's there whole, as a map's made in one step.
        progress.made(0, maps::exists)?.let { id ->
            return ImportResult.Imported(id, maps.summary(id)?.title ?: fallbackTitle ?: words.string(R.string.data_map_without_a_title))
        }

        val id = try {
            maps.add(markdown, original = text, originalName = fileName).also { progress.making(listOf(it)) }
        } catch (failed: IOException) {
            return ImportResult.Failed(failed.message ?: words.string(R.string.common_could_not_keep_that_map))
        }

        return ImportResult.Imported(id, maps.summary(id)?.title ?: fallbackTitle ?: words.string(R.string.data_map_without_a_title))
    }

    private suspend fun check(text: String): CheckedMap = pages.work { page ->
        page.request(CheckedMap.serializer()) { job -> MapCommand.Check(job, text) }
    }

    /**
     * A new map from text indented as an outline, nested as pasting nests it,
     * made on a page (see [MapCommand.MapFromText]). Null when no page can
     * make it, as where the WebView is too old to draw maps: the map is then
     * made the way a list without indents is.
     */
    private suspend fun outline(text: String, subject: String?): String? =
        try {
            pages.work { page -> page.request(String.serializer()) { job -> MapCommand.MapFromText(job, text, subject) } }.takeIf { it.isNotBlank() }
        } catch (failed: PageException) {
            null
        }

    /**
     * Maps from another app's file, read by Orbit on a page: one, or one for
     * each sheet of an XMind workbook, with the pictures inside it. An OPML
     * or `.mm` file is kept in the map's history as it came in, as text files
     * are; a workbook isn't text, so it isn't.
     */
    suspend fun importFromOtherApp(bytes: ByteArray, name: String?, format: OtherFormat, progress: ImportProgress = ImportProgress.NONE): ImportResult {
        val file = try {
            pages.work { page ->
                page.request(ImportedFile.serializer()) { job -> MapCommand.ImportFile(job, Base64.getEncoder().encodeToString(bytes), name) }
            }
        } catch (unreadable: PageException) {
            return ImportResult.Failed(words.string(R.string.data_could_not_read_that, unreadable.message.orEmpty()))
        }

        file.error?.let { return ImportResult.Failed(problemText(it, words)) }

        if (file.maps.isEmpty()) {
            return ImportResult.Failed(words.string(R.string.data_nothing_to_make_a_map_from))
        }

        val original = if (format == OtherFormat.XMind || file.maps.size > 1) null else decodeText(bytes)

        // A workbook's sheets come in together or not at all, so a file that stops partway never leaves some of its maps.
        val ids = try {
            addMaps(file.maps, original, name, progress)
        } catch (failed: IOException) {
            return ImportResult.Failed(failed.message ?: words.string(R.string.common_could_not_keep_that_map))
        } catch (damaged: IllegalArgumentException) {
            return ImportResult.Failed(words.string(R.string.data_file_unreadable))
        }

        val notes = importNotes(file.maps.flatMap { it.warnings }, words)

        return if (ids.size == 1) {
            ImportResult.Imported(ids.single(), maps.summary(ids.single())?.title ?: words.string(R.string.data_map_without_a_title), notes)
        } else {
            ImportResult.ImportedMany(added = ids.size, alreadyHere = 0, notes = notes)
        }
    }

    /**
     * Adds the maps another app's file holds (a workbook's sheets), all in
     * one step ([MapRepository.addAll]), telling `progress` of them as soon
     * as they're there. Made before Orbit stopped it midway, they came in
     * together, and the ones still here aren't added again. Gives their ids,
     * in order.
     */
    internal suspend fun addMaps(sheets: List<ImportedFileMap>, original: String?, name: String?, progress: ImportProgress): List<String> {
        if (progress.made.isNotEmpty()) {
            return progress.made.filter(maps::exists)
        }

        val made = sheets.map { map -> map.markdown to map.media.mapValues { (_, data) -> Base64.getDecoder().decode(data) } }

        val ids = if (made.size == 1) {
            listOf(maps.add(made.single().first, original = original, pictures = made.single().second, originalName = name))
        } else {
            maps.addAll(made)
        }

        progress.making(ids)
        return ids
    }

    companion object {
        /** How a map's text starts the files of an idea ([filesAttribute]): one a new map for shared files has once they're in. */
        private const val FILES_ATTRIBUTE = "<!-- orbit\nfiles:"

        /**
         * What didn't come over from another app's map as it was, in `words`:
         * a line for each kind, its places counted across the file's maps. A
         * kind the app has no words for says Orbit's own message.
         */
        fun importNotes(warnings: List<ImportWarning>, words: Words): List<String> =
            warnings.groupBy { it.code }.map { (code, found) ->
                val count = found.sumOf { it.count }
                WARNING_TEXTS[code]?.let { words.plural(it, count, count) } ?: found.first().message
            }

        /** Why a file from another app makes no map, in `words`. */
        fun problemText(problem: ImportProblem, words: Words): String = when (problem.code) {
            "unknown-format" -> words.string(R.string.data_import_unknown_format)
            "unreadable" -> words.string(R.string.data_import_unreadable)
            "empty" -> words.string(R.string.data_nothing_to_make_a_map_from)
            "password-protected" -> words.string(R.string.data_import_password_protected)
            "too-large" -> words.string(R.string.data_import_too_large)
            else -> words.string(R.string.data_could_not_read_that, problem.message)
        }

        /**
         * Whether text that came in is a YAML map, to be made orbit-md: its
         * file says so (`yamlFile`, by its name or type), or it reads as one
         * the way Orbit tells YAML from markdown ([MapText.isYaml]). Markdown
         * with a note or a fence that has a `nodes:` line stays markdown, and
         * is kept as written.
         */
        fun isYamlMap(text: String, yamlFile: Boolean = false): Boolean = yamlFile || MapText.isYaml(text)

        /**
         * The markdown of a new map from text that isn't YAML. A markdown file
         * (not `plain`) is a map as written, whatever's in it: Orbit reads one
         * of paragraphs alone, a journal's, as the note of a map without ideas
         * yet. Text that came as text (`plain`: shared, dropped on the list,
         * or in a `.txt` file) and is indented as an outline keeps its
         * branches, as pasting it would, in the map `outline` makes of it on a
         * page (see [MapCommand.MapFromText]). Other text is made by
         * [markdownFor]; so is an outline no page could make.
         */
        suspend fun newMap(text: String, title: String?, plain: Boolean, outline: suspend (String, String?) -> String?): String = when {
            !plain -> if (text.endsWith("\n")) text else "$text\n"
            isOutline(text) -> outline(text, title) ?: markdownFor(text, title)
            else -> markdownFor(text, title)
        }

        /**
         * Whether a file holds plain text, which makes a map as shared text
         * does ([newMap]): a `.txt` file, or one typed as plain text. One
         * whose name or type says it's markdown (or YAML) is a map as written,
         * even when an app calls it plain text.
         */
        fun isPlainText(name: String, type: String): Boolean {
            val extension = name.substringAfterLast('.', "").lowercase()
            val plain = type.substringBefore(';').trim().lowercase()

            if (extension in MapFileTypes.TEXT_EXTENSIONS || plain in MapFileTypes.MARKDOWN) {
                return false
            }

            return extension == "txt" || plain == MapFileTypes.PLAIN_TEXT
        }

        /**
         * Whether text is an outline, its lines indented under one another,
         * which a new map from plain text keeps the branches of ([newMap]).
         * Markdown with headings or frontmatter is a map of its own, kept as
         * written, and a list without indents makes the map it always has
         * ([markdownFor]); so does text whose lines are all indented alike.
         */
        fun isOutline(text: String): Boolean {
            val lines = text.lines().filter { it.isNotBlank() }

            if (lines.isEmpty() || lines.first().trim() == "---" || lines.any { HEADING.containsMatchIn(it) }) {
                return false
            }

            val widths = lines.map(::indentWidth)
            return widths.any { it > widths.min() }
        }

        /** How far a line is indented, a tab counting as four spaces, as pasting reads it. */
        private fun indentWidth(line: String): Int = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') TAB_WIDTH else 1 }

        /**
         * Markdown text as Orbit reads it: orbit-md is kept as written, a
         * single line becomes a map's title, and plain lines become a list
         * under the first one ("Milk\nEggs\nBread", say, shared from a notes
         * app), or under `title` when the text came with one. Nothing is cut:
         * a line too long for a title names the map by its start, and the
         * whole line is the map's note.
         */
        fun markdownFor(text: String, title: String? = null): String {
            val trimmed = text.trim()

            if (trimmed.lines().any { STRUCTURE.containsMatchIn(it) }) {
                return if (text.endsWith("\n")) text else "$text\n"
            }

            val lines = trimmed.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() }
            val named = title?.replace(Regex("""\s+"""), " ")?.trim()?.takeIf { it.isNotEmpty() && it != lines.first() }

            return buildString {
                if (named != null) {
                    append("# ").append(MapText.shorten(named, TITLE_LENGTH)).append("\n\n")

                    if (lines.size == 1) {
                        append(lines.single()).append('\n')
                    } else {
                        lines.forEach { append("- ").append(it).append('\n') }
                    }

                    return@buildString
                }

                val first = lines.first()
                append("# ").append(MapText.shorten(first, TITLE_LENGTH)).append('\n')

                if (first.length > TITLE_LENGTH) {
                    append('\n').append(first).append('\n')
                }

                if (lines.size > 1) {
                    append('\n')
                    lines.drop(1).forEach { append("- ").append(it).append('\n') }
                }
            }
        }

        private const val TAG = "OrbitImport"

        /**
         * What an import of every map says besides what came in: the `filledIn`
         * files its maps here were missing, the `pastRoom` files that stayed in
         * the zip, past what a map carries, the `stayedPrivate` versions and
         * files of maps private in the zip that stayed there, their map being
         * open here, the `skipped` folders without a map, and the `leftOut`
         * files of kinds this version doesn't know.
         */
        fun zipNotes(filledIn: Int, skipped: Int, leftOut: Int, words: Words, pastRoom: Int = 0, stayedPrivate: Int = 0): List<String> = listOfNotNull(
            words.plural(R.plurals.data_zip_files_filled_in, filledIn, filledIn).takeIf { filledIn > 0 },
            pastRoomNote(pastRoom, words),
            words.plural(R.plurals.data_zip_files_stayed_private, stayedPrivate, stayedPrivate).takeIf { stayedPrivate > 0 },
            words.plural(R.plurals.data_zip_folders_without_maps, skipped, skipped).takeIf { skipped > 0 },
            words.plural(R.plurals.data_zip_files_left_out, leftOut, leftOut).takeIf { leftOut > 0 },
        )

        /** What's said first of an import of a zip of maps that stopped partway, with `reason` when there's one to say ([stopReason]). */
        fun stoppedNote(reason: String?, words: Words): String =
            reason?.let { words.string(R.string.common_import_stopped, it) } ?: words.string(R.string.common_import_stopped_without_reason)

        /**
         * Why an import stopped, `cause`, as Android or Java says it, when
         * that's words for people in `language` (a BCP 47 tag): they say it
         * in English, and never a file's path.
         */
        fun stopReason(cause: Throwable?, language: String): String? =
            cause?.message?.takeIf { language.substringBefore('-') == "en" && it.isNotBlank() && '/' !in it }?.trimEnd('.')

        /** What's said of `pastRoom` files of a zip of every map that stayed in it, past what a map carries ([MAP_MEDIA_LIMIT]); null for none. */
        fun pastRoomNote(pastRoom: Int, words: Words): String? =
            words.plural(R.plurals.data_zip_files_past_map_room, pastRoom, pastRoom, readableSize(MAP_MEDIA_LIMIT, words)).takeIf { pastRoom > 0 }
    }
}
