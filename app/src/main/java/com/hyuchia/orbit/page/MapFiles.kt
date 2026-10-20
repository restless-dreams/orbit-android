package com.hyuchia.orbit.page

import android.net.Uri
import android.webkit.WebResourceResponse
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.isMediaName
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.nearby.ByteRange
import com.hyuchia.orbit.nearby.byteRange
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Where pages find the maps' folders, as [mapBaseUrl] says. */
internal const val MAPS_PATH = "/maps/"

/**
 * What every file of a map's a page gets says: it never runs as a page, a
 * picture with a script inside included, and its type is never guessed
 * from what's in it.
 */
internal val MAP_FILE_HEADERS = mapOf(
    "Content-Security-Policy" to "sandbox",
    "X-Content-Type-Options" to "nosniff",
)

/** A picture or video in a map's media folder, as a page asks for it: the map's id and the file's name. */
internal data class MapMedia(val mapId: String, val name: String)

/**
 * The picture or video a page asks for at `path` (decoded, from the page's
 * own host), or null for anything else: `/maps/<map>/media/<name>`, with a
 * name a picture or video in a map can have. A map's text, its details and
 * its history are never among them.
 */
internal fun mapMedia(path: String): MapMedia? {
    if (!path.startsWith(MAPS_PATH)) {
        return null
    }

    val parts = path.removePrefix(MAPS_PATH).split('/')

    if (parts.size != 3 || parts[1] != MEDIA_DIRECTORY) {
        return null
    }

    val (mapId, _, name) = parts

    if (mapId.isEmpty() || mapId.startsWith(".") || !isMediaName(name)) {
        return null
    }

    return MapMedia(mapId, name)
}

/**
 * A picture or video to send a page, or null when it went, or can't be
 * read, since it was found: the page then shows it's missing, where an
 * exception would stop the app (WebView asks on a thread of its own).
 */
internal fun openMedia(file: File): InputStream? = try {
    file.inputStream()
} catch (unreadable: IOException) {
    null
}

/** The map whose folder `baseUrl` is (see [mapBaseUrl]), or null when it's no map's. */
internal fun mapIdOf(baseUrl: String?): String? {
    val prefix = "https://$PAGE_HOST$MAPS_PATH"

    if (baseUrl == null || !baseUrl.startsWith(prefix)) {
        return null
    }

    return baseUrl.removePrefix(prefix).substringBefore('/').takeIf { it.isNotEmpty() && !it.startsWith(".") }
}

/**
 * The pictures and videos in the maps' folders, for pages. A page gets only
 * what's in the media folder of a map `mayRead` names: the map it shows, or
 * one it draws a picture of. So a map can't point a page at another map's
 * text, nor at another map's pictures, to have them end up in an export.
 *
 * A video asks for the part it plays next, and seeks by asking for another:
 * the answer says which part it is, and holds the whole file, as WebView
 * itself skips to the part a request asks for, and reads no further than
 * its end, in any stream an app answers with.
 */
internal class MapFiles(directory: File) {
    private val root: File by lazy { directory.canonicalFile }

    /** The answer to a request for `url`, with the Range header `range` if it has one: the file, part of it, or not found. */
    fun answer(url: Uri, range: String?, mayRead: (String) -> Boolean): WebResourceResponse {
        val file = file(url, mayRead) ?: return notFound()
        val length = file.length()
        val type = mediaType(file.name)
        val part = byteRange(range, length)
        val input = if (part is ByteRange.Unsatisfiable) ByteArrayInputStream(ByteArray(0)) else openMedia(file) ?: return notFound()

        return when (part) {
            ByteRange.Whole -> WebResourceResponse(type, null, 200, "OK", MAP_FILE_HEADERS + ("Accept-Ranges" to "bytes"), input)
            is ByteRange.Part -> WebResourceResponse(
                type,
                null,
                206,
                "Partial Content",
                MAP_FILE_HEADERS + mapOf("Content-Range" to part.contentRange, "Accept-Ranges" to "bytes"),
                input,
            )
            is ByteRange.Unsatisfiable -> WebResourceResponse(
                type,
                null,
                416,
                "Range Not Satisfiable",
                MAP_FILE_HEADERS + mapOf("Content-Range" to part.contentRange, "Accept-Ranges" to "bytes"),
                input,
            )
        }
    }

    /** The file `url` names, when it's a picture or video of a map the page may read. */
    private fun file(url: Uri, mayRead: (String) -> Boolean): File? {
        if (url.scheme != "https" || url.host != PAGE_HOST) {
            return null
        }

        val media = url.path?.let(::mapMedia)?.takeIf { mayRead(it.mapId) } ?: return null
        val file = File(File(File(root, media.mapId), MEDIA_DIRECTORY), media.name).canonicalFile

        // Nothing outside the maps' folders, whatever a name in between leads to.
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            return null
        }

        return file
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", MAP_FILE_HEADERS, ByteArrayInputStream(ByteArray(0)))
}
