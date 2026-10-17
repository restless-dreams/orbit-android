package com.hyuchia.orbit.page

import android.net.Uri
import android.webkit.WebResourceResponse
import com.hyuchia.orbit.data.isMediaName
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.nearby.ByteRange
import com.hyuchia.orbit.nearby.byteRange
import java.io.ByteArrayInputStream
import java.io.File

/** Where pages find the maps' folders, as [mapBaseUrl] says. */
internal const val MAPS_PATH = "/maps/"

/**
 * Parts of the pictures and videos in the maps' folders, for pages: a video
 * asks for the part it plays next, and seeks by asking for another, and
 * WebViewAssetLoader answers as if it sent the whole file, through which a
 * video can't seek. Requests that ask for no part go to the loader as before.
 *
 * The answer says which part it is, and holds the whole file: WebView itself
 * skips to the part a request asks for, and reads no further than its end,
 * in any stream an app answers with.
 */
internal class MapFileParts(directory: File) {
    private val root: File by lazy { directory.canonicalFile }

    /** The answer to a request for `url` with the Range header `range`, or null for the loader to answer. */
    fun answer(url: Uri, range: String): WebResourceResponse? {
        if (url.scheme != "https" || url.host != PAGE_HOST) {
            return null
        }

        val path = url.path?.takeIf { it.startsWith(MAPS_PATH) } ?: return null
        val file = File(root, path.removePrefix(MAPS_PATH)).canonicalFile

        // Nothing outside the maps' folders, and only what a map's media folder keeps.
        if (!file.path.startsWith(root.path + File.separator) || !isMediaName(file.name) || !file.isFile) {
            return null
        }

        val length = file.length()
        val type = mediaType(file.name)

        return when (val part = byteRange(range, length)) {
            ByteRange.Whole -> null
            is ByteRange.Part -> WebResourceResponse(
                type,
                null,
                206,
                "Partial Content",
                mapOf("Content-Range" to part.contentRange, "Accept-Ranges" to "bytes"),
                file.inputStream(),
            )
            is ByteRange.Unsatisfiable -> WebResourceResponse(
                type,
                null,
                416,
                "Range Not Satisfiable",
                mapOf("Content-Range" to part.contentRange, "Accept-Ranges" to "bytes"),
                ByteArrayInputStream(ByteArray(0)),
            )
        }
    }
}
