package com.hyuchia.orbit.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.hyuchia.orbit.data.isVideoName
import com.hyuchia.orbit.data.mediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/** Where the map's pictures live in its folder; the map refers to them as `media/<name>`. */
private const val MEDIA = "media"

/**
 * A map as one HTML file that opens in any browser, offline, and flies like
 * the app: Orbit, the map and its pictures inside (web/web-page.ts). Readers
 * explore it or present it.
 */
object WebPage {
    /** What the page reads from its #orbit-map script: web/web-page.ts's PageData. */
    @Serializable
    private data class PageData(val markdown: String, val theme: AppTheme, val path: List<String>, val download: String? = null, val words: PageWords? = null)

    /**
     * The page for the map, in the language of `context`: the app's, for the
     * page's own words, and Orbit's that follow its `lang`. `download` links
     * to the map itself, for a page a device serves on the local network.
     */
    suspend fun build(
        context: Context,
        title: String,
        markdown: String,
        theme: AppTheme,
        mapFolder: File,
        path: List<String> = emptyList(),
        download: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val template = context.assets.open("map/web-page.html").bufferedReader().use { it.readText() }
            val script = context.assets.open("map/web-page.js").bufferedReader().use { it.readText() }
            page(template, script, title, inlinePictures(markdown, mapFolder), theme, path, download, pageWords(context))
        }

    /** The page from its template and script, for a map whose pictures are inside it, in the language of `words`. */
    internal fun page(
        template: String,
        script: String,
        title: String,
        markdown: String,
        theme: AppTheme,
        path: List<String>,
        download: String?,
        words: PageWords,
    ): String {
        val data = protocolJson.encodeToString(PageData.serializer(), PageData(markdown, theme, path, download, words))
        return fill(
            template,
            mapOf(
                htmlIn(words.language),
                "<!-- title -->" to escapeHtml(title),
                "<!-- present key -->" to escapeHtml(words.presentKey),
                "<!-- present -->" to escapeHtml(words.present),
                "<!-- presentation -->" to escapeHtml(words.presentation),
                "<!-- previous idea -->" to escapeHtml(words.previousIdea),
                "<!-- next idea -->" to escapeHtml(words.nextIdea),
                "<!-- stop presenting key -->" to escapeHtml(words.stopPresentingKey),
                "<!-- download for orbit -->" to escapeHtml(words.downloadForOrbit),
                "<!-- drawn with orbit -->" to escapeHtml(words.drawnWithOrbit),
                "<!-- data -->" to jsonScript("orbit-map", data),
                "<!-- script -->" to "<script type=\"module\">${scriptSafe(script)}</script>",
            ),
        )
    }
}

/** How the pages' templates in web/ begin: in English, until [htmlIn] puts them in the app's language. */
private const val ENGLISH_PAGE = "<html lang=\"en\">"

/** For [fill]: the template's `<html>` in `language`, which screen readers and Orbit's own words follow. */
internal fun htmlIn(language: String): Pair<String, String> = ENGLISH_PAGE to "<html lang=\"${escapeHtml(language)}\">"

/**
 * JSON in a script element, for a page to read by its id. JSON has no <
 * outside its strings, so all of them can be escaped: nothing in it can end
 * or upset the script element.
 */
internal fun jsonScript(id: String, json: String): String = "<script type=\"application/json\" id=\"$id\">${json.replace("<", "\\u003c")}</script>"

/**
 * The map with its own pictures inside it, as data: URLs, for a page or a
 * screen that can't reach the map's folder. `maxSide` shrinks pictures larger
 * than it, in pixels, for screens that don't need them full size. Videos stay
 * out, as tens of megabytes of text would be too much to carry: their posters,
 * which are pictures, come along and show on the map.
 */
internal fun inlinePictures(markdown: String, mapFolder: File, maxSide: Int? = null): String {
    val pictures = File(mapFolder, MEDIA).listFiles().orEmpty().filter { it.isFile && !isVideoName(it.name) }

    return pictures.fold(markdown) { text, picture ->
        val reference = "$MEDIA/${picture.name}"

        if (!text.contains(reference)) {
            text
        } else {
            val (type, bytes) = if (maxSide == null) pictureType(picture.name) to picture.readBytes() else shrunk(picture, maxSide)
            val url = "data:$type;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            text.replace("./$reference", url).replace(reference, url)
        }
    }
}

/** The picture at most `maxSide` pixels on its longer side, as JPEG, or PNG when it's see-through. */
private fun shrunk(picture: File, maxSide: Int): Pair<String, ByteArray> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(picture.path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)

    if (longest <= maxSide) {
        return pictureType(picture.name) to picture.readBytes()
    }

    var sample = 1

    while (longest / (sample * 2) >= maxSide) {
        sample *= 2
    }

    val decoded = BitmapFactory.decodeFile(picture.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return pictureType(picture.name) to picture.readBytes()
    val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)

    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
    } else {
        decoded
    }

    val output = ByteArrayOutputStream()
    val seeThrough = bitmap.hasAlpha()
    bitmap.compress(if (seeThrough) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 82, output)
    return (if (seeThrough) "image/png" else "image/jpeg") to output.toByteArray()
}

/** The media type of a map's picture or video, by its name. */
internal fun pictureType(name: String): String = mediaType(name)

/** The template with each placeholder replaced once, so text inside a replacement is never mistaken for another one. */
internal fun fill(template: String, values: Map<String, String>): String {
    val builder = StringBuilder()
    var position = 0

    values.keys.map { placeholder -> template.indexOf(placeholder) to placeholder }
        .filter { (index, _) -> index >= 0 }
        .sortedBy { (index, _) -> index }
        .forEach { (index, placeholder) ->
            builder.append(template, position, index).append(values.getValue(placeholder))
            position = index + placeholder.length
        }

    return builder.append(template, position, template.length).toString()
}

/**
 * The script as it can sit inside a script element: the sequences that would
 * end it or change how HTML reads it are written with \x3c, which means the
 * same < in the strings and patterns where they appear.
 */
internal fun scriptSafe(script: String): String =
    Regex("<(!--|/?script)", RegexOption.IGNORE_CASE).replace(script) { match -> "\\x3c" + match.groupValues[1] }

internal fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
