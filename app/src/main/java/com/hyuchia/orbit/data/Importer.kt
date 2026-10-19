package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.CheckedMap
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PagePool
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface ImportResult {
    data class Imported(val id: String, val title: String) : ImportResult

    /** A zip of maps, as Export all writes them: `added` came in, `alreadyHere` were here. */
    data class ImportedMany(val added: Int, val alreadyHere: Int) : ImportResult

    /** A map that's already here, `existing`, in another version: it can update it, or be kept as well. */
    data class AlreadyHere(val bundle: MapBundle, val existing: String) : ImportResult

    data class Failed(val reason: String) : ImportResult
}

/** Files are at most this big; a map is text, so anything larger isn't one. */
private const val MAXIMUM_SIZE = 8L * 1024 * 1024

/** The types zips come as, from different apps. */
private val ZIP_TYPES = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")

/** A heading or a list item: text with a shape Orbit can draw. */
private val STRUCTURE = Regex("""^\s{0,3}#{1,6}\s|^\s*(?:[-*+]|\d+[.)])\s""")

/** A YAML map: its nodes under a top-level `nodes:` key. */
private val YAML_NODES = Regex("""(?m)^nodes:\s*$""")

/** The longest a map's title gets from shared text; a longer line is kept whole as the map's note. */
private const val TITLE_LENGTH = 80

/** Whether a file at an address with this scheme and authority comes from another app: a content address that isn't Orbit's own. */
internal fun importable(scheme: String?, authority: String?, ownAuthority: String): Boolean =
    scheme == ContentResolver.SCHEME_CONTENT && authority != null && authority != ownAuthority

/**
 * Turns what other apps hand Orbit into maps: markdown and YAML files, zips
 * of a map and its pictures, and text shared from anywhere. Each map keeps
 * the text it came from in its history, as it came in. What went wrong is
 * said in `words`.
 */
class Importer(private val maps: MapRepository, private val pages: PagePool, private val words: Words) {

    suspend fun importFile(uri: Uri, resolver: ContentResolver): ImportResult {
        // Orbit reads what it's handed with its own access, so a path on the phone or one of
        // Orbit's own shared files would give another app a way to read Orbit's private files.
        if (!importable(uri.scheme, uri.authority, "${BuildConfig.APPLICATION_ID}.files")) {
            return ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        }

        return try {
            // Even asking a file's name can be refused, when the app that gave it can't give it.
            val name = withContext(Dispatchers.IO) { displayName(uri, resolver) }.orEmpty()
            val type = resolver.getType(uri).orEmpty()

            if (name.endsWith(".zip", ignoreCase = true) || type in ZIP_TYPES) {
                // The maps here must be known first, to find the one the zip is.
                maps.maps.filterNotNull().first()
                val many = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.holdsManyMaps(it) } }
                    ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))

                if (many) {
                    val imported = resolver.openInputStream(uri)?.use { maps.importAll(it) } ?: return ImportResult.Failed(words.string(R.string.data_file_unreadable))
                    return ImportResult.ImportedMany(imported.added, imported.alreadyHere)
                }

                val bundle = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { maps.readBundle(it) } }
                    ?: return ImportResult.Failed(words.string(R.string.data_no_map_in_zip))
                val existing = bundle.uuid?.let(maps::findByIdentity)

                if (existing != null) {
                    return ImportResult.AlreadyHere(bundle, existing)
                }

                return ImportResult.Imported(maps.receive(bundle), bundle.title)
            }

            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readAtMost((MAXIMUM_SIZE + 1).toInt())
                    if (bytes.size > MAXIMUM_SIZE) null else decodeText(bytes)
                }
            } ?: return ImportResult.Failed(words.string(R.string.data_file_too_big_for_map))

            val yaml = name.endsWith(".yaml", ignoreCase = true) || name.endsWith(".yml", ignoreCase = true) || "yaml" in type
            importText(text, name.substringBeforeLast('.').ifBlank { null }, yaml)
        } catch (unreadable: SecurityException) {
            ImportResult.Failed(words.string(R.string.common_not_allowed_to_read_that_file))
        } catch (refused: RefusedZipException) {
            ImportResult.Failed(refused.message.orEmpty())
        } catch (unreadable: IOException) {
            ImportResult.Failed(words.string(R.string.data_file_unreadable))
        }
    }

    /** Text shared from another app, with the title it came with (an article's, say). */
    suspend fun importShared(text: String, subject: String?): ImportResult = importText(text, title = subject)

    /**
     * A map from text: orbit-md is kept as written, YAML becomes orbit-md,
     * and plain lines become a list under the first one (or under `title`).
     * YAML Orbit can't read all of isn't imported: the map would lose what
     * wasn't read.
     */
    suspend fun importText(text: String, fallbackTitle: String? = null, yaml: Boolean = false, title: String? = null): ImportResult {
        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            return ImportResult.Failed(words.string(R.string.data_nothing_to_make_a_map_from))
        }

        val markdown = if (yaml || YAML_NODES.containsMatchIn(trimmed)) {
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
            markdownFor(text, title)
        }

        val id = try {
            maps.add(markdown, original = text)
        } catch (failed: IOException) {
            return ImportResult.Failed(failed.message ?: words.string(R.string.common_could_not_keep_that_map))
        }

        return ImportResult.Imported(id, maps.summary(id)?.title ?: fallbackTitle ?: words.string(R.string.data_map_without_a_title))
    }

    private suspend fun check(text: String): CheckedMap = pages.work { page ->
        page.request(CheckedMap.serializer()) { job -> MapCommand.Check(job, text) }
    }

    companion object {
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
    }

    private fun displayName(uri: Uri, resolver: ContentResolver): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment
}

/** Up to `limit` bytes of the stream: Android has `readNBytes` only from API 33. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)

    while (bytes.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - bytes.size()))

        if (read < 0) {
            break
        }

        bytes.write(chunk, 0, read)
    }

    return bytes.toByteArray()
}
