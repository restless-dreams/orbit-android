package com.hyuchia.orbit.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.MediaNames
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.data.PAGE_MOTIONS_LIMIT
import com.hyuchia.orbit.data.PAGE_SOUNDS_LIMIT
import com.hyuchia.orbit.data.isSoundName
import com.hyuchia.orbit.data.isVideoName
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.data.motionsFitting
import com.hyuchia.orbit.data.soundType
import com.hyuchia.orbit.data.soundsFitting
import com.hyuchia.orbit.nearby.inlineScripts
import com.hyuchia.orbit.nearby.scriptHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlin.math.roundToInt

/**
 * A map as one HTML file that opens in any browser, offline, and flies like
 * the app: Orbit, the map, its pictures and its sounds inside
 * (web/web-page.ts). Readers explore it or present it, and printed it's the
 * map laid out for paper.
 */
object WebPage {
    /** What the page reads from its #orbit-map script: web/web-page.ts's PageData. */
    @Serializable
    private data class PageData(
        val markdown: String,
        val theme: AppTheme,
        val path: List<String>,
        val download: String? = null,
        val words: PageWords? = null,
        val print: PrintRequest? = null,

        /** The sounds inside the page, by the address the map gives them (see [soundsInside]). */
        val sounds: Map<String, String> = emptyMap(),

        /** The live photos' motions inside the page, by the address the map gives them (see [motionsInside]). */
        val motions: Map<String, String> = emptyMap(),
    )

    /**
     * The page for the map, in the language of `context`: the app's, for the
     * page's own words, and Orbit's that follow its `lang`. `download` links
     * to the map itself, for a page a device serves on the local network.
     * With `print`, the page is only the map laid out for paper, for
     * [MapPrinter]. `asTheyAre` names the map's files that can't go clean
     * which the person sharing chose to share as they are (`Uncleaned`).
     */
    suspend fun build(
        context: Context,
        title: String,
        markdown: String,
        theme: AppTheme,
        mapFolder: File,
        path: List<String> = emptyList(),
        download: String? = null,
        print: PrintRequest? = null,
        asTheyAre: Set<String> = emptySet(),
    ): String =
        withContext(Dispatchers.IO) {
            val template = context.assets.open("map/web-page.html").bufferedReader().use { it.readText() }
            val script = context.assets.open("map/web-page.js").bufferedReader().use { it.readText() }
            // A page for paper plays nothing: its sounds and motions stay out.
            val sounds = if (print == null) soundsInside(markdown, mapFolder, asTheyAre = asTheyAre) else emptyMap()
            val motions = if (print == null) motionsInside(markdown, mapFolder, asTheyAre = asTheyAre) else emptyMap()
            val pictures = inlinePictures(markdown, mapFolder, asTheyAre = asTheyAre)
            page(template, script, title, pictures, theme, path, download, pageWords(context), print, sounds, motions, generator(OrbitBuild.bundled(context)))
        }

    /** What made a page, for its `generator`: "Orbit for Android 0.4.0 (Orbit 0.3.0, 00c0099)", so a page found anywhere says which builds made it. */
    internal fun generator(orbit: OrbitBuild?, app: String = BuildConfig.VERSION_NAME): String {
        val library = orbit?.let { build -> listOf(build.version, build.commit).filter { it.isNotEmpty() }.joinToString(", ") }
        return "Orbit for Android $app" + (library?.let { " (Orbit $it)" } ?: "")
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
        print: PrintRequest? = null,
        sounds: Map<String, String> = emptyMap(),
        motions: Map<String, String> = emptyMap(),
        generator: String = "Orbit for Android",
    ): String {
        val data = protocolJson.encodeToString(PageData.serializer(), PageData(markdown, theme, path, download, words, print, sounds, motions))
        val page = fill(
            template,
            mapOf(
                htmlIn(words.language),
                GENERATOR to "<meta name=\"generator\" content=\"${escapeHtml(generator)}\">",
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
        return withPolicy(page)
    }
}

/** What the web page's template in web/ says made it, until [WebPage.page] says which builds. */
private const val GENERATOR = "<meta name=\"generator\" content=\"Orbit for Android\">"

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
 * which are pictures, come along and show on the map. A page goes to other
 * people, so its pictures go without what their files say beyond the
 * picture ([OutgoingMedia]); one Orbit can't read through stays out, unless
 * it's one of `asTheyAre`, which the person sharing chose to share as they
 * are (`Uncleaned`). The map's text is written once, with every picture it
 * points to, and each picture is read once, however many places name it:
 * one picture after another, a big map would be copied whole each time.
 */
internal fun inlinePictures(markdown: String, mapFolder: File, maxSide: Int? = null, asTheyAre: Set<String> = emptySet()): String {
    val pictures = File(mapFolder, MEDIA_DIRECTORY).listFiles().orEmpty().filter { it.isFile && !isVideoName(it.name) && !isSoundName(it.name) }.associateBy { it.name }
    // Each picture's address once it's read: null for one that stays out.
    val urls = HashMap<String, String?>()

    return pointAt(markdown, pictures.keys) { name ->
        if (name !in urls) {
            urls[name] = pictureUrl(pictures.getValue(name), maxSide, asItIs = name in asTheyAre)
        }

        urls[name]
    }
}

/**
 * A picture as a data: URL, without what its file says beyond the picture,
 * or as it is when `asItIs`, as the person sharing chose; null when Orbit
 * can't read it through and it isn't.
 */
private fun pictureUrl(picture: File, maxSide: Int?, asItIs: Boolean): String? {
    val (type, original) = if (maxSide == null) mediaType(picture.name) to picture.readBytes() else shrunk(picture, maxSide)
    val bytes = OutgoingMedia.picture(original) ?: original.takeIf { asItIs } ?: return null
    return "data:$type;base64,${Base64.getEncoder().encodeToString(bytes)}"
}

/**
 * `text` with each place it names one of the map's files by a name in
 * `names` (as `media/…`, or as `./media/…`, where the map points at one:
 * see [MediaNames.references]) naming what `url` gives for that name
 * instead, all in one pass; a place `url` gives null for stays as it is, and
 * so do a web address and a longer name that only say the same. Inside a
 * list or mapping written on one line in an idea's attributes
 * (`{ src: media/…, caption: … }`, as Orbit writes a picture with a caption
 * or a live photo), the url goes in quotes: a data: URL's comma would end
 * the value there, and the picture would be lost.
 */
internal fun pointAt(text: String, names: Collection<String>, url: (String) -> String?): String {
    if (names.isEmpty()) {
        return text
    }

    val named = names.toSet()

    return MediaNames.replaced(text) { place ->
        val address = place.groupValues[2].takeIf { it in named }?.let(url)

        when {
            address == null -> null
            inFlowCollection(text, place.range.first) -> "\"$address\""
            else -> address
        }
    }
}

/**
 * Whether `at` is inside a flow list or mapping (`[…]`, `{…}`) of the
 * attributes comment it's in, outside quotes: plain values there end at a
 * comma. Text outside an attributes comment never is.
 */
private fun inFlowCollection(text: String, at: Int): Boolean {
    val open = text.lastIndexOf("<!--", at)

    if (open < 0 || text.lastIndexOf("-->", at) > open) {
        return false
    }

    var depth = 0
    var quote: Char? = null
    // A quote opens a value only where one starts, after a colon, a comma or an opening bracket: "It's" is plain text.
    var previous = ' '

    for (index in open + 4 until at) {
        val character = text[index]

        when {
            quote != null -> if (character == quote) quote = null
            (character == '"' || character == '\'') && previous in VALUE_STARTS -> quote = character
            character == '{' || character == '[' -> depth += 1
            character == '}' || character == ']' -> depth -= 1
        }

        if (!character.isWhitespace()) {
            previous = character
        }
    }

    return depth > 0 && quote == null
}

/** What comes before a value in an attributes comment: its key's colon, a comma, an opening bracket, or nothing yet. */
private val VALUE_STARTS = setOf(' ', ':', ',', '[', '{', '-')

/**
 * The map's sounds as data: URLs a browser plays (a voice memo's `.mp4` as
 * audio), by their place in the map's folder (`media/…`: the page reads
 * `./media/…` as the same), for the page to put in place of its sounds'
 * addresses (web/web-page.ts): those that fit ([soundsFitting]), each in the
 * page once. A page goes to other people, so each goes as [OutgoingMedia]
 * makes it: a memo, or any sound or video in MP4, without when and where it
 * was made, or on what, any other sound without its tags, or as it is when
 * it's one of `asTheyAre`. The rest stay out, and a page away from the
 * phone can't play them. The page swaps only sounds' addresses, so a video
 * stays a video.
 */
internal fun soundsInside(markdown: String, mapFolder: File, limit: Long = PAGE_SOUNDS_LIMIT, asTheyAre: Set<String> = emptySet()): Map<String, String> =
    soundsFitting(markdown, mapFolder, limit, asTheyAre).mapNotNull { sound ->
        val bytes = try {
            ByteArrayOutputStream(sound.outgoing.size.toInt()).also(sound.outgoing::writeTo).toByteArray()
        } catch (changed: IOException) {
            // Its file changed as the page was made: it can't be told what it holds now, so it stays out.
            return@mapNotNull null
        }

        "$MEDIA_DIRECTORY/${sound.file.name}" to "data:${soundType(sound.file.name)};base64,${Base64.getEncoder().encodeToString(bytes)}"
    }.toMap()

/**
 * The map's live photos' motions as data: URLs (`media/…-motion.mp4`, as
 * Orbit names them), for the page to put in place of their addresses
 * (web/web-page.ts): a motion is a few seconds, so those that fit come
 * inside ([motionsFitting]), each without what its file says beyond the
 * motion, or as it is when it's one of `asTheyAre`. The rest stay out, and
 * their pictures show still.
 */
internal fun motionsInside(markdown: String, mapFolder: File, limit: Long = PAGE_MOTIONS_LIMIT, asTheyAre: Set<String> = emptySet()): Map<String, String> =
    motionsFitting(markdown, mapFolder, limit, asTheyAre).mapNotNull { motion ->
        val bytes = try {
            ByteArrayOutputStream(motion.outgoing.size.toInt()).also(motion.outgoing::writeTo).toByteArray()
        } catch (changed: IOException) {
            // Its file changed as the page was made, as a sound's can: it stays out, and its picture shows still.
            return@mapNotNull null
        }

        "$MEDIA_DIRECTORY/${motion.name}" to "data:${mediaType(motion.name)};base64,${Base64.getEncoder().encodeToString(bytes)}"
    }.toMap()

/** The picture at most `maxSide` pixels on its longer side, as JPEG, or PNG when it's see-through. */
private fun shrunk(picture: File, maxSide: Int): Pair<String, ByteArray> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(picture.path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)

    if (longest <= maxSide) {
        return mediaType(picture.name) to picture.readBytes()
    }

    var sample = 1

    while (longest / (sample * 2) >= maxSide) {
        sample *= 2
    }

    val decoded = BitmapFactory.decodeFile(picture.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return mediaType(picture.name) to picture.readBytes()
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

/**
 * The policy a shared page keeps wherever it's opened, a file on a phone, a
 * website or a browser on the Wi-Fi: its own script runs, by its hash, and
 * nothing else does; what a map holds shows, from inside the page or over
 * https, but never runs, so an address that slips past Orbit's guard still
 * can't. Nothing is fetched: pictures, sounds and motions are inside.
 */
internal fun sharedPagePolicy(html: String): String {
    val scripts = inlineScripts(html).joinToString("") { " '${scriptHash(it)}'" }
    return listOf(
        "default-src 'none'",
        "script-src$scripts",
        "style-src 'unsafe-inline'",
        "img-src data: https:",
        "media-src data: https:",
        "frame-src https:",
        "font-src data:",
        "base-uri 'none'",
        "form-action 'none'",
        "upgrade-insecure-requests",
    ).joinToString("; ")
}

/** `html` with its policy ([sharedPagePolicy]) first thing in its head, before any script it covers. */
internal fun withPolicy(html: String): String {
    val charset = "<meta charset=\"utf-8\">"
    check(html.contains(charset)) { "A page's template starts its head with $charset" }
    return html.replaceFirst(charset, "$charset\n<meta http-equiv=\"Content-Security-Policy\" content=\"${sharedPagePolicy(html)}\">")
}

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
