package com.hyuchia.orbit.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/** What a drag from another app holds, as far as its types say: what it would make in Orbit. */
enum class DropKind { Ideas, Pictures, Videos, Sounds, Maps, Files }

/** One item of a drag let go on Orbit: text, or a file another app hands over, by its content address. */
sealed interface DroppedItem {
    /** Text, a web address on its own included. */
    data class Text(val text: String) : DroppedItem

    data class Picture(val address: String, val name: String?) : DroppedItem

    data class Video(val address: String, val name: String?) : DroppedItem

    /** An iPhone's live photo, let go as its two halves: its still and its movie, joined ([LivePairs]). */
    data class LivePhoto(val still: Picture, val movie: Video) : DroppedItem

    /** A sound, which becomes an idea's as one added from the phone does: the same copy, limits and kinds kept. */
    data class Sound(val address: String, val name: String?) : DroppedItem

    /** A map: markdown, YAML, a zip, or another app's (OPML, FreeMind and Freeplane, XMind). */
    data class MapFile(val address: String, val name: String?) : DroppedItem

    /** A file of plain text, which becomes ideas as text does. */
    data class TextFile(val address: String, val name: String?) : DroppedItem

    /** A file of any other kind, a PDF or a spreadsheet, which joins an idea's files. */
    data class File(val address: String, val name: String?) : DroppedItem
}

/** The types maps come as, other apps' own among them, and the XML OPML often says it is. */
private val MAP_TYPES = MapFileTypes.MAPS + MapFileTypes.XML

/** What one of a drag's types would make in Orbit: any other kind of file, such as a PDF, joins an idea's files. */
fun dropKindOf(mimeType: String): DropKind? {
    val type = mimeType.substringBefore(';').trim().lowercase()

    return when {
        type.startsWith("image/") -> DropKind.Pictures
        type.startsWith("video/") -> DropKind.Videos
        type.startsWith("audio/") -> DropKind.Sounds
        type in MAP_TYPES || type == MapFileTypes.ANY_FILE -> DropKind.Maps
        type.startsWith("text/") -> DropKind.Ideas
        type.isEmpty() -> null
        else -> DropKind.Files
    }
}

/** What a drag offers Orbit, from the types its description lists. */
fun dropKinds(description: ClipDescription?): Set<DropKind> {
    if (description == null) {
        return emptySet()
    }

    return (0 until description.mimeTypeCount).mapNotNull { dropKindOf(description.getMimeType(it)) }.toSet()
}

/**
 * A file another app hands over in a drag, by its content address, its type
 * and its name: what Orbit makes of it. A file without a type of its own is
 * a map only when its name says so; one of any other kind joins an idea's
 * files.
 */
fun droppedFile(address: String, type: String?, name: String?): DroppedItem? {
    val plain = type?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()

    return when {
        plain.startsWith("image/") -> DroppedItem.Picture(address, name)
        videoExtension(type, name) != null -> DroppedItem.Video(address, name)
        plain.startsWith("audio/") || ((plain.isEmpty() || plain == MapFileTypes.ANY_FILE) && soundExtension(null, name) != null) -> DroppedItem.Sound(address, name)
        extension in MapFileTypes.EXTENSIONS || plain in MAP_TYPES -> DroppedItem.MapFile(address, name)
        plain.startsWith("text/") || extension == "txt" -> DroppedItem.TextFile(address, name)
        else -> DroppedItem.File(address, name)
    }
}

/**
 * What a drag let go on Orbit holds, item by item. Files come only from
 * other apps' content addresses, never from a path on the phone or Orbit's
 * own shared files, which would let an app have Orbit read its private files.
 */
fun droppedItems(clip: ClipData, resolver: ContentResolver): List<DroppedItem> {
    val description = clip.description

    return (0 until clip.itemCount).mapNotNull { index ->
        val item = clip.getItemAt(index)
        val uri = item.uri

        when {
            uri != null && (uri.scheme == "https" || uri.scheme == "http") -> DroppedItem.Text(uri.toString())
            uri != null -> if (importable(uri.scheme, uri.authority, "${BuildConfig.APPLICATION_ID}.files")) droppedContent(uri, resolver, description) else null
            else -> droppedText(item)
        }
    }
}

private fun droppedContent(uri: Uri, resolver: ContentResolver, description: ClipDescription): DroppedItem? {
    // An app that can't say what its file is, or is no longer there to say, gives nothing to read either.
    val type = fromProvider { resolver.getType(uri) } ?: description.takeIf { it.mimeTypeCount == 1 }?.getMimeType(0)
    return droppedFile(uri.toString(), type, displayName(uri, resolver))
}

private fun droppedText(item: ClipData.Item): DroppedItem? {
    val text = item.text?.toString() ?: item.htmlText?.let { html -> Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString() }
    return text?.takeIf { it.isNotBlank() }?.let(DroppedItem::Text)
}

/**
 * What asking about another app's files answers, or null when it fails: its
 * content provider, or Android lending a drag's files from it, both across
 * processes. A provider can throw anything back: a permission taken back
 * (`SecurityException`), an address it no longer knows
 * (`IllegalArgumentException`), a cursor that broke (`IllegalStateException`),
 * a database's error or one of its own. None of it is Orbit's to crash on:
 * the file it was about fails alone.
 */
internal inline fun <T> fromProvider(ask: () -> T): T? = try {
    ask()
} catch (stopped: CancellationException) {
    throw stopped
} catch (failed: RuntimeException) {
    null
}

/** The name an app gives its file, without the folders before it: the address's last part when the app can't say. */
internal fun displayName(uri: Uri, resolver: ContentResolver): String? = fromProvider {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} ?: uri.lastPathSegment?.substringAfterLast('/')

/** The name an app gives its file and the size it says the file has: -1 when it doesn't say, and no name either when it can't. */
internal fun nameAndSize(uri: Uri, resolver: ContentResolver): Pair<String?, Long> =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0) to (if (cursor.isNull(1)) -1L else cursor.getLong(1))
        } else {
            null
        }
    } ?: (null to -1L)

/** A file's name as an idea's: without its extension, and with nothing when there's nothing else. */
fun ideaName(fileName: String?): String? = fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }

/** A text file let go on Orbit, read: its whole text, or why it couldn't come in. */
sealed interface TextFileRead {
    data class Read(val text: String) : TextFileRead

    /** Longer than it may be: it's refused whole, never cut. */
    data object TooLong : TextFileRead

    /** The app that gave it didn't let Orbit read it, or no longer does. */
    data object NotAllowed : TextFileRead

    data object Unreadable : TextFileRead
}

/**
 * A text file's whole text, when it's at most `limit` bytes: decoded at once
 * as files are ([decodeText]), so no character is cut in half and its
 * encoding is told from all of it. A longer one is refused, never cut.
 * `open` opens it, through its app's content provider, whose failures are
 * the file's ([fromProvider]).
 */
internal fun readTextFile(limit: Int, open: () -> InputStream?): TextFileRead {
    val bytes = try {
        open()?.use { it.readAtMost(limit + 1) } ?: return TextFileRead.Unreadable
    } catch (refused: SecurityException) {
        return TextFileRead.NotAllowed
    } catch (failed: IOException) {
        return TextFileRead.Unreadable
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failed: RuntimeException) {
        return TextFileRead.Unreadable
    }

    return if (bytes.size > limit) TextFileRead.TooLong else TextFileRead.Read(decodeText(bytes))
}

/** What a drop's text comes to: all of it, or why it doesn't come in. */
sealed interface DroppedText {
    data class Text(val text: String) : DroppedText

    data class Refused(val reason: String) : DroppedText
}

/**
 * The text a drop holds, one item after another: its text, and each text
 * file read whole, at most `limit` bytes ([readTextFile]). A file that can't
 * come in refuses the drop, saying why in `words` (`tooLong` for one past
 * `limit`), by its name when the drop held more than it: what comes in is
 * never a part of what was let go. `open` opens a file by its address.
 */
internal fun droppedText(items: List<DroppedItem>, limit: Int, tooLong: String, words: Words, open: (String) -> InputStream?): DroppedText {
    val parts = mutableListOf<String>()

    for (item in items) {
        when (item) {
            is DroppedItem.Text -> parts += item.text
            is DroppedItem.TextFile -> when (val read = readTextFile(limit) { open(item.address) }) {
                is TextFileRead.Read -> parts += read.text
                else -> {
                    val reason = when (read) {
                        TextFileRead.TooLong -> tooLong
                        TextFileRead.NotAllowed -> words.string(R.string.common_not_allowed_to_read_that_file)
                        else -> words.string(R.string.data_file_unreadable)
                    }

                    val name = item.name?.takeIf { items.size > 1 }
                    return DroppedText.Refused(if (name != null) words.string(R.string.drop_file_problem, name, reason) else reason)
                }
            }
            else -> Unit
        }
    }

    return DroppedText.Text(parts.joinToString("\n"))
}
