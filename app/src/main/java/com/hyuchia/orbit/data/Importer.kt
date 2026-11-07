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

    /** A zip of maps, as Export all writes them, or a workbook's sheets: `added` came in, `alreadyHere` were here. */
    data class ImportedMany(
        val added: Int,
        val alreadyHere: Int,
        val notes: List<String> = emptyList(),
        val privateAlreadyHere: Int = 0,
        val filledIn: Int = 0,
        val privateFilledIn: Int = 0,
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
 * Turns what other apps hand Orbit into maps: markdown and YAML files, zips
 * of a map and its pictures, maps from other apps (OPML, FreeMind and
 * Freeplane, XMind), and text shared from anywhere. Each map keeps the text
 * it came from in its history, as it came in. What went wrong is said in
 * `words`.
 */
class Importer(private val maps: MapRepository, private val pages: PagePool, private val words: Words) {

    suspend fun importFile(uri: Uri, resolver: ContentResolver): ImportResult {
        // Orbit reads what it's handed with its own access, so a path on the phone or one of
        // Orbit's own shared files would give another app a way to read Orbit's private files.
        if (!importable(uri.scheme, uri.authority, "${BuildConfig.APPLICATION_ID}.files")) {
            return ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        }

        return try {
            // Even asking a file's name can be refused, when the app that gave it can't give it: then the address names it.
            val (name, type) = withContext(Dispatchers.IO) { displayName(uri, resolver).orEmpty() to fromProvider { resolver.getType(uri) }.orEmpty() }
            val zipNamed = name.endsWith(".zip", ignoreCase = true) || type in MapFileTypes.ZIP
            val kind = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { OtherApps.kindOf(name, type, zipNamed, it) } }
                ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

            if (kind is FileKind.FromOtherApp) {
                val limit = if (kind.format == OtherFormat.XMind) OtherApps.WORKBOOK_LIMIT else MAP_TEXT_LIMIT
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readAtMost((limit + 1).toInt()) } }
                    ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

                if (bytes.size > limit) {
                    return ImportResult.Failed(words.string(R.string.data_file_too_big_for_map))
                }

                return importFromOtherApp(bytes, name.ifBlank { null }, kind.format)
            }

            if (kind == FileKind.Zip) {
                // The maps here must be known first, to find the one the zip is.
                maps.maps.filterNotNull().first()
                val many = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.holdsManyMaps(it) } }
                    ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

                if (many) {
                    val imported = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.importAll(it) } }
                        ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))
                    // Files brought into maps already here are said once it's known whether private maps hide.
                    return ImportResult.ImportedMany(
                        imported.added,
                        imported.alreadyHere,
                        notes = zipNotes(filledIn = 0, skipped = imported.skipped, leftOut = imported.leftOut, words),
                        privateAlreadyHere = imported.privateAlreadyHere,
                        filledIn = imported.filledIn,
                        privateFilledIn = imported.privateFilledIn,
                    )
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

                return ImportResult.Imported(maps.receive(bundle), bundle.title, notes = listOfNotNull(leftOutNote(bundle.leftOut)))
            }

            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readAtMost((MAP_TEXT_LIMIT + 1).toInt())
                    if (bytes.size > MAP_TEXT_LIMIT) null else decodeText(bytes)
                }
            } ?: return ImportResult.Failed(words.string(R.string.data_file_too_big_for_map))

            val yaml = name.endsWith(".yaml", ignoreCase = true) || name.endsWith(".yml", ignoreCase = true) || "yaml" in type
            importText(text, name.substringBeforeLast('.').ifBlank { null }, yaml, plain = isPlainText(name, type), fileName = name.ifBlank { null })
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

    /** How long a zip is, as its file says, when its app doesn't say in its listing; 0 when it can't tell, as for a stream. */
    private fun zipLength(uri: Uri, resolver: ContentResolver): Long = try {
        fromProvider { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }?.takeIf { it > 0 } ?: 0L
    } catch (unknown: IOException) {
        0L
    }

    /** That files of kinds this version doesn't know stayed out of a zip, when some did: a newer Orbit's, say. */
    private fun leftOutNote(leftOut: Int): String? = words.plural(R.plurals.data_zip_files_left_out, leftOut, leftOut).takeIf { leftOut > 0 }

    /** Text shared from another app, or dropped on the list, with the title it came with (an article's, say). */
    suspend fun importShared(text: String, subject: String?): ImportResult = importText(text, title = subject, plain = true)

    /**
     * A new map for files shared to Orbit while no map is open: named after
     * the first, and holding them all on its idea, each copied in as it is.
     * One that can't join, too big or unreadable, is left out and said; with
     * none that could, there's no map.
     */
    suspend fun newMapWithFiles(uris: List<Uri>, resolver: ContentResolver): ImportResult {
        val first = uris.firstOrNull() ?: return ImportResult.Failed(words.string(R.string.data_nothing_to_make_a_map_from))
        val title = ideaName(withContext(Dispatchers.IO) { displayName(first, resolver) }) ?: words.string(R.string.files_title)
        val made = importText(title, plain = true) as? ImportResult.Imported ?: return ImportResult.Failed(words.string(R.string.files_unreadable))
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
            return ImportResult.Failed(notes.firstOrNull() ?: words.string(R.string.files_unreadable))
        }

        val markdown = maps.read(made.id)
        maps.write(made.id, "${markdown.trimEnd()}\n${filesAttribute(files)}\n", base = markdown)
        return made.copy(notes = notes)
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

        val id = try {
            maps.add(markdown, original = text, originalName = fileName)
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
    suspend fun importFromOtherApp(bytes: ByteArray, name: String?, format: OtherFormat): ImportResult {
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

        val ids = try {
            file.maps.map { map ->
                val pictures = map.media.mapValues { (_, data) -> Base64.getDecoder().decode(data) }
                maps.add(map.markdown, original = original, pictures = pictures, originalName = name)
            }
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

    companion object {
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
         * files its maps here were missing, the `skipped` folders without a map,
         * and the `leftOut` files of kinds this version doesn't know.
         */
        fun zipNotes(filledIn: Int, skipped: Int, leftOut: Int, words: Words): List<String> = listOfNotNull(
            words.plural(R.plurals.data_zip_files_filled_in, filledIn, filledIn).takeIf { filledIn > 0 },
            words.plural(R.plurals.data_zip_folders_without_maps, skipped, skipped).takeIf { skipped > 0 },
            words.plural(R.plurals.data_zip_files_left_out, leftOut, leftOut).takeIf { leftOut > 0 },
        )
    }
}
