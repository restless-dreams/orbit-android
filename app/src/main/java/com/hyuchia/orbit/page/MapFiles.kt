package com.hyuchia.orbit.page

import android.net.Uri
import android.webkit.WebResourceResponse
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.isMediaName
import com.hyuchia.orbit.data.mapMediaFile
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
 * Where a page's job finds the media folder of the map it's drawing a
 * picture of: `/jobs/<key>/`, by a key the job alone is given, for as long
 * as it lasts ([jobBaseUrl]).
 */
internal const val JOBS_PATH = "/jobs/"

/** The address a job's map's folder has for it, by its `key`: see [JOBS_PATH]. */
internal fun jobBaseUrl(key: String): String = "https://$PAGE_HOST$JOBS_PATH$key/"

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

/** A picture or video in a job's map's media folder, as the job asks for it: the key the job was given, and the file's name. */
internal data class JobMedia(val key: String, val name: String)

/** The picture or video a job asks for at `path`, or null for anything else: `/jobs/<key>/media/<name>` (see [JOBS_PATH]). */
internal fun jobMedia(path: String): JobMedia? {
    if (!path.startsWith(JOBS_PATH)) {
        return null
    }

    val parts = path.removePrefix(JOBS_PATH).split('/')

    if (parts.size != 3 || parts[1] != MEDIA_DIRECTORY) {
        return null
    }

    val (key, _, name) = parts

    if (key.isEmpty() || !isMediaName(name)) {
        return null
    }

    return JobMedia(key, name)
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

/**
 * Where the map a job draws keeps its pictures, which the page may load
 * while the job runs: a picture of a map ([MapCommand.Snapshot]), or the
 * map ideas caught on the go are added to, drawn out of sight
 * ([MapCommand.AddIdeas]). Null for a job that draws no map.
 */
internal fun jobBaseUrl(job: MapCommand): String? = when (job) {
    is MapCommand.Snapshot -> job.baseUrl
    is MapCommand.AddIdeas -> job.baseUrl
    else -> null
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
 * what's in the media folder of a map it may read, as `readable` gives it
 * for the address it asks for: the map it shows, or, at an address of its
 * job's own, the one a job draws a picture of. So a map can't point a page
 * at another map's text, nor at another map's pictures, to have them end up
 * in an export: not through a name in between that leads elsewhere either,
 * as a link a folder was given outside Orbit may.
 *
 * A video asks for the part it plays next, and seeks by asking for another:
 * the answer says which part it is, and holds the whole file, as WebView
 * itself skips to the part a request asks for, and reads no further than
 * its end, in any stream an app answers with.
 *
 * With `stills`, an animated picture goes as its first frame, a PNG: the
 * map's planets stand still, as its thumbnails do; without `animations`, as
 * on the audience's screen, which plays them, it goes as it is. A HEIC or
 * HEIF picture goes as a JPEG, which the page draws.
 */
internal class MapFiles(directory: File, private val stills: FirstFrames? = null, private val animations: Boolean = true) {
    private val root: File by lazy { directory.canonicalFile }

    /**
     * The answer to a request for `url`, with the Range header `range` if it
     * has one: the file, part of it, or not found. WebView asks on a thread
     * of its own, where an exception would stop the app: a file that can't
     * be found or read, whatever the reason, is missing instead.
     */
    fun answer(url: Uri, range: String?, readable: (path: String) -> MapMedia?): WebResourceResponse = try {
        found(url, range, readable)
    } catch (unreadable: IOException) {
        notFound()
    }

    private fun found(url: Uri, range: String?, readable: (String) -> MapMedia?): WebResourceResponse {
        val found = file(url, readable) ?: return notFound()
        // An animation as its first frame, a HEIC picture as a JPEG; any other file as it is.
        val sent = stills?.standIn(found, animations) ?: StandIn.kept(found)
        val length = sent.length
        val type = mediaType(sent.name)
        val part = byteRange(range, length)
        val input = if (part is ByteRange.Unsatisfiable) ByteArrayInputStream(ByteArray(0)) else sent.open() ?: return notFound()

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
    private fun file(url: Uri, readable: (String) -> MapMedia?): File? {
        if (url.scheme != "https" || url.host != PAGE_HOST) {
            return null
        }

        val media = url.path?.let(readable) ?: return null

        // Nothing outside that map's own media folder, whatever a name in between leads to: not another map's, nor its own
        // text. The same file every way out of the phone reads, too.
        return mapMediaFile(File(root, media.mapId), media.name)
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", MAP_FILE_HEADERS, ByteArrayInputStream(ByteArray(0)))
}
