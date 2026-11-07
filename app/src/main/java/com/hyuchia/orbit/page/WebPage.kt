package com.hyuchia.orbit.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.MediaNames
import com.hyuchia.orbit.data.OutgoingFile
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.data.OutputFailed
import com.hyuchia.orbit.data.PageMotion
import com.hyuchia.orbit.data.PageSound
import com.hyuchia.orbit.data.isSoundName
import com.hyuchia.orbit.data.isVideoName
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.data.motionsFitting
import com.hyuchia.orbit.data.picturesFitting
import com.hyuchia.orbit.data.requireRoom
import com.hyuchia.orbit.data.soundType
import com.hyuchia.orbit.data.soundsFitting
import com.hyuchia.orbit.data.watched
import com.hyuchia.orbit.media.SketchPictures
import com.hyuchia.orbit.nearby.inlineScripts
import com.hyuchia.orbit.nearby.scriptHash
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.sketch.isSketchName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Base64
import kotlin.math.roundToInt

/**
 * A map as one HTML file that opens in any browser, offline, and flies like
 * the app: Orbit, the map, its pictures and its sounds inside
 * (web/web-page.ts). Readers explore it or present it, and printed it's the
 * map laid out for paper.
 *
 * A map's pictures, sounds and live photos' motions come to a gigabyte, so a
 * page carries up to 100 MiB of its pictures ([PAGE_PICTURES_LIMIT]), and
 * 25 MiB each of its sounds and motions, about 200 MB in Base64; it's
 * written a piece at a time wherever it goes:
 * a file to save or share, the page a computer opens from this device, or
 * the page laid out for printing. Neither the page nor any sound or motion
 * in it is ever whole in memory; only one picture at a time is, as it's
 * cleaned.
 */
object WebPage {
    /**
     * Writes the page for the map to `output`, in the language of `context`:
     * the app's, for the page's own words, and Orbit's that follow its
     * `lang`. `download` links to the map itself, for a page a device serves
     * on the local network. With `print`, the page is only the map laid out
     * for paper, for [MapPrinter]. `asTheyAre` names the map's files that
     * can't go clean which the person sharing chose to share as they are
     * (`Uncleaned`). `room` hears the most the page may come to before any
     * of it is written, and throws when its storage hasn't room for it
     * ([requireRoom]). Gives the scripts the page runs, for the content
     * policy a server sends with it.
     */
    suspend fun write(
        context: Context,
        output: OutputStream,
        title: String,
        markdown: String,
        theme: AppTheme,
        mapFolder: File,
        path: List<String> = emptyList(),
        download: String? = null,
        print: PrintRequest? = null,
        asTheyAre: Set<String> = emptySet(),
        room: (Long) -> Unit = {},
    ): List<String> =
        withContext(Dispatchers.IO) {
            val template = context.assets.open("map/web-page.html").bufferedReader().use { it.readText() }
            val script = context.assets.open("map/web-page.js").bufferedReader().use { it.readText() }
            // A page for paper plays nothing: its sounds and motions stay out.
            val plays = print == null
            val media = PageMedia(
                folder = mapFolder,
                asTheyAre = asTheyAre,
                pictures = picturesFitting(markdown, mapFolder),
                sounds = if (plays) soundsFitting(markdown, mapFolder, asTheyAre = asTheyAre) else emptyList(),
                motions = if (plays) motionsFitting(markdown, mapFolder, asTheyAre = asTheyAre) else emptyList(),
                stills = FirstFrames(File(context.cacheDir, FIRST_FRAMES_DIRECTORY)),
                spool = pageParts(context.cacheDir),
            )
            write(output, template, script, title, markdown, theme, path, download, pageWords(context), print, media, generator(OrbitBuild.bundled(context)), room)
        }

    /** What made a page, for its `generator`: "Orbit for Android 0.4.0 (Orbit 0.3.0, 00c0099)", so a page found anywhere says which builds made it. */
    internal fun generator(orbit: OrbitBuild?, app: String = BuildConfig.VERSION_NAME): String {
        val library = orbit?.let { build -> listOf(build.version, build.commit).filter { it.isNotEmpty() }.joinToString(", ") }
        return "Orbit for Android $app" + (library?.let { " (Orbit $it)" } ?: "")
    }

    /**
     * Writes the page from its template and script to `output`, for a map
     * whose pictures, sounds and motions are `media`'s (none without it), in
     * the language of `words`, and gives the scripts it runs. The page's own
     * HTML is made first, without the map, and the map's data goes where the
     * template says, a piece at a time: the map's text, with each of its
     * pictures in Base64 as its place comes, then its sounds and motions.
     * `room` hears the most it may come to first.
     */
    internal fun write(
        output: OutputStream,
        template: String,
        script: String,
        title: String,
        markdown: String,
        theme: AppTheme,
        path: List<String>,
        download: String?,
        words: PageWords,
        print: PrintRequest? = null,
        media: PageMedia? = null,
        generator: String = "Orbit for Android",
        room: (Long) -> Unit = {},
    ): List<String> {
        val page = withPolicy(
            fill(
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
                    "<!-- script -->" to "<script type=\"module\">${scriptSafe(script)}</script>",
                ),
            ),
        )
        // Every value above is escaped, so the data's place is the template's own, and the policy covers every script the page runs.
        val data = page.indexOf(DATA)
        check(data >= 0) { "A page's template has a place for its data" }
        room(mostOf(page, markdown, media))

        val writing = PageOutput(output)
        writing.html(page, 0, data)
        writing.html("<script type=\"application/json\" id=\"orbit-map\">")
        writing.json("{\"markdown\":\"")
        writing.markdown(markdown, media)
        writing.json("\",\"theme\":" + protocolJson.encodeToString(AppTheme.serializer(), theme))
        writing.json(",\"path\":" + protocolJson.encodeToString(ListSerializer(String.serializer()), path))
        writing.json(",\"download\":" + protocolJson.encodeToString(String.serializer().nullable, download))
        writing.json(",\"words\":" + protocolJson.encodeToString(PageWords.serializer(), words))
        writing.json(",\"print\":" + protocolJson.encodeToString(PrintRequest.serializer().nullable, print))
        writing.json(",\"sounds\":{")
        writing.inside(media?.sounds.orEmpty().map { it.file.name to it.outgoing }, media?.spool, ::soundType)
        writing.json("},\"motions\":{")
        writing.inside(media?.motions.orEmpty().map { it.name to it.outgoing }, media?.spool, ::mediaType)
        writing.json("}}")
        writing.html("</script>")
        writing.html(page, data + DATA.length, page.length)
        writing.flush()
        return inlineScripts(page)
    }
}

/**
 * What goes inside a page of a map besides its text: the pictures in
 * `folder`'s media that the map points at, those of `pictures` when it says
 * ([picturesFitting]), `sounds` and `motions`, those that fit
 * ([soundsFitting], [motionsFitting]), and of the map's files that can't go
 * clean, those of `asTheyAre`, as they are. `stills` makes the JPEGs of HEIC
 * and HEIF pictures, and each sound and motion is copied into `spool`, the
 * system's temporary folder without one, on its way into the page.
 */
internal class PageMedia(
    val folder: File,
    val asTheyAre: Set<String> = emptySet(),
    val pictures: Set<String>? = null,
    val sounds: List<PageSound> = emptyList(),
    val motions: List<PageMotion> = emptyList(),
    val stills: FirstFrames? = null,
    val spool: File? = null,
)

/**
 * The most a page may come to, for a storage to make room for: its own HTML
 * in UTF-8, the map's text escaped in JSON, and what goes inside in Base64,
 * each picture as many times as the map names it, as big as its file, and
 * the little a cleaned picture may gain (a JPEG's orientation).
 */
private fun mostOf(page: String, markdown: String, media: PageMedia?): Long {
    val pictures = media?.let { pictureFiles(it.folder, it.pictures) }.orEmpty()
    val named = if (pictures.isEmpty()) 0L else MediaNames.places(markdown).sumOf { place -> pictures[place.groupValues[2]]?.let(::pictureMost) ?: 0L }
    val playing = media?.let { it.sounds.sumOf { sound -> sound.outgoing.size } + it.motions.sumOf { motion -> motion.outgoing.size } } ?: 0L
    return 3L * page.length + 6L * markdown.length + (named + playing) * 4 / 3 + 4
}

/** The most a picture gains as it's cleaned, or drawn again as a JPEG ([pagePicture]). */
private const val PICTURE_GAIN = 64L * 1024

/** How many pixels across a sketch goes inside a page, as the PNG it draws ([sketchPicture]): sharp full screen and on paper. */
private const val SKETCH_SIDE = 1024

/** The most a sketch's PNG comes to: flat colors, which PNG keeps small. */
private const val SKETCH_MOST = 512L * 1024

/** The most a picture comes to inside a page: a sketch as its PNG, any other as its file, cleaned or drawn again. */
private fun pictureMost(picture: File): Long = if (isSketchFile(picture)) SKETCH_MOST else picture.length() + PICTURE_GAIN

/** Whether a file in a map's media folder is named as Orbit names a sketch (`sketch-….svg`). */
private fun isSketchFile(picture: File): Boolean = isSketchName("media/${picture.name}")

/**
 * One of Orbit's own sketches as the PNG it draws, [SKETCH_SIDE] pixels
 * across ([SketchPictures.png]), in a file of its own in `spool`: Orbit
 * takes no SVG inside a page as a data: URL, since an SVG can do more than
 * draw. Null for any other picture, an SVG another app drew included,
 * which goes as it is.
 */
private fun sketchPicture(picture: File, spool: File?): PagePicture? {
    val sketch = picture.takeIf(::isSketchFile)?.let { SketchSvg.read(it.readBytes()) } ?: return null
    val png = SketchPictures.png(sketch, SKETCH_SIDE)
    requireRoom(spool ?: File(System.getProperty("java.io.tmpdir").orEmpty()), png.size.toLong())
    val file = File.createTempFile("page-", ".part", spool)

    try {
        file.outputStream().watched().use { it.write(png) }
    } catch (full: OutputFailed) {
        file.delete()
        throw full
    }

    return PagePicture("image/png", file)
}

/** The cache's folder for what a page copies in on its way ([PageMedia.spool]). */
private const val PAGE_PARTS = "page-parts"

/** How long a part of a page stays when the page was never finished, as when Orbit stopped in the middle of it: a page takes minutes at most. */
private const val PAGE_PART_KEPT = 60 * 60 * 1000L

/**
 * The folder in `cache` a page copies what goes inside it into, one file at
 * a time ([PageMedia.spool]): parts left there by a page that was never
 * finished go as the next one starts, once they're an hour old.
 */
internal fun pageParts(cache: File, now: Long = System.currentTimeMillis()): File {
    val folder = File(cache, PAGE_PARTS)
    folder.mkdirs()
    folder.listFiles().orEmpty().filter { now - it.lastModified() > PAGE_PART_KEPT }.forEach { it.delete() }
    return folder
}

/** Where a page's template puts its data: the map, for its script to read (web/web-page.ts's PageData). */
private const val DATA = "<!-- data -->"

/** What the web page's template in web/ says made it, until [WebPage.write] says which builds. */
private const val GENERATOR = "<meta name=\"generator\" content=\"Orbit for Android\">"

/** How the pages' templates in web/ begin: in English, until [htmlIn] puts them in the app's language. */
private const val ENGLISH_PAGE = "<html lang=\"en\">"

/** How much of a page is kept before it's written out, in bytes or characters. */
private const val PIECE = 64 * 1024

/** For [fill]: the template's `<html>` in `language`, which screen readers and Orbit's own words follow. */
internal fun htmlIn(language: String): Pair<String, String> = ENGLISH_PAGE to "<html lang=\"${escapeHtml(language)}\">"

/**
 * JSON in a script element, for a page to read by its id. JSON has no <
 * outside its strings, so all of them can be escaped: nothing in it can end
 * or upset the script element.
 */
internal fun jsonScript(id: String, json: String): String = "<script type=\"application/json\" id=\"$id\">${json.replace("<", "\\u003c")}</script>"

/**
 * A page as it's written: its HTML, and its data's JSON, as text, and what
 * goes inside it in Base64 straight to the bytes, after the text before it.
 * A `<` in the data's JSON is always escaped, as [jsonScript] escapes it,
 * so nothing in the data can end or upset its script element.
 */
private class PageOutput(output: OutputStream) {
    private val bytes = BufferedOutputStream(output, PIECE)
    private val text = BufferedWriter(OutputStreamWriter(bytes, Charsets.UTF_8), PIECE)

    /** The page's own HTML, from `from` until `to`. */
    fun html(html: String, from: Int = 0, to: Int = html.length) {
        text.write(html, from, to - from)
    }

    /** JSON as it's written, but for its `<`s, which only its strings can hold. */
    fun json(json: String) {
        text.write(json.replace("<", "\\u003c"))
    }

    /** Text inside a JSON string, from `from` until `to`, escaped as JSON escapes it, and its `<`s too. */
    fun inString(value: String, from: Int = 0, to: Int = value.length) {
        var start = from

        for (index in from until to) {
            val escaped = escaped(value[index]) ?: continue
            text.write(value, start, index - start)
            text.write(escaped)
            start = index + 1
        }

        text.write(value, start, to - start)
    }

    /** What `write` writes, in Base64, which a JSON string holds as it is. */
    fun base64(write: (OutputStream) -> Unit) {
        // The text before it goes first.
        text.flush()
        Base64.getEncoder().wrap(KeptOpen(bytes)).use(write)
    }

    fun flush() {
        text.flush()
        bytes.flush()
    }

    /** A character as a JSON string holds it, or null when it holds it as it is. */
    private fun escaped(character: Char): String? = when {
        character == '"' -> "\\\""
        character == '\\' -> "\\\\"
        character == '<' -> "\\u003c"
        character == '\n' -> "\\n"
        character == '\r' -> "\\r"
        character == '\t' -> "\\t"
        character < ' ' -> "\\u00" + HEX[character.code shr 4] + HEX[character.code and 0xF]
        else -> null
    }

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}

/** An output that stays open when what writes to it closes, as Base64's encoder does once it's written its last bytes. */
private class KeptOpen(private val output: OutputStream) : OutputStream() {
    override fun write(byte: Int) = output.write(byte)

    override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)

    override fun flush() = output.flush()

    override fun close() = Unit
}

/**
 * Writes the map's text into the page's data, inside its JSON string, with
 * each place it names one of the map's pictures (as `media/…`, or as
 * `./media/…`, where the map points at one: see [MediaNames.references])
 * naming the picture itself instead, as a data: URL ([pagePicture]). A place
 * naming a picture that stays out stays as it is, and so do a web address
 * and a longer name that only say the same. Inside a list or mapping written
 * on one line in an idea's attributes (`{ src: media/…, caption: … }`, as
 * Orbit writes a picture with a caption or a live photo), the address goes
 * in quotes: a data: URL's comma would end the value there, and the picture
 * would be lost. Videos stay out, as tens of megabytes of text would be too
 * much to carry: their posters, which are pictures, come along and show on
 * the map. Each picture is copied in as its place comes, streamed from its
 * file through a file of its own, as a sound is ([inside]), so none is ever
 * whole in memory; one that stays out isn't read again.
 */
private fun PageOutput.markdown(markdown: String, media: PageMedia?) {
    val pictures = media?.let { pictureFiles(it.folder, it.pictures) }.orEmpty()
    var end = 0

    if (media != null && pictures.isNotEmpty()) {
        val left = HashSet<String>()

        for (place in MediaNames.places(markdown)) {
            val name = place.groupValues[2]
            val file = pictures[name]?.takeIf { name !in left } ?: continue
            val picture = pagePicture(file, asItIs = name in media.asTheyAre, media.stills, media.spool)

            if (picture == null) {
                left += name
                continue
            }

            try {
                val quoted = inFlowCollection(markdown, place.range.first)
                inString(markdown, end, place.range.first)
                inString(if (quoted) "\"data:${picture.type};base64," else "data:${picture.type};base64,")
                base64 { output -> picture.file.inputStream().use { it.copyTo(output, PIECE) } }

                if (quoted) {
                    inString("\"")
                }
            } finally {
                picture.file.delete()
            }

            end = place.range.last + 1
        }
    }

    inString(markdown, end, markdown.length)
}

/**
 * Writes `files`, by their names in the map's media folder, into the page's
 * data as the members of a JSON object: each by the address the map gives it
 * (`media/…`: the page reads `./media/…` as the same), as a data: URL of the
 * type `type` names, for the page to put in place of its address
 * (web/web-page.ts). Each is copied whole into `spool` first and goes from
 * there: one whose file changed as the page was made, as a sync app can
 * change it, can't be told what it holds now, so it stays out, rather than
 * leave half of itself in the page.
 */
private fun PageOutput.inside(files: List<Pair<String, OutgoingFile>>, spool: File?, type: (String) -> String) {
    var first = true

    for ((name, outgoing) in files) {
        val whole = spooled(outgoing, spool) ?: continue

        try {
            json(if (first) "\"" else ",\"")
            inString("$MEDIA_DIRECTORY/$name")
            json("\":\"")
            inString("data:${type(name)};base64,")
            base64 { output -> whole.inputStream().use { it.copyTo(output, PIECE) } }
            json("\"")
            first = false
        } finally {
            whole.delete()
        }
    }
}

/**
 * The file as it leaves the phone, copied whole to a file of its own in
 * `spool`, or null when it couldn't be read through, as when it changed as
 * it was copied. A spool that hasn't room for it, as a full storage hasn't,
 * fails the page before it's copied ([requireRoom]).
 */
private fun spooled(outgoing: OutgoingFile, spool: File?): File? {
    requireRoom(spool ?: File(System.getProperty("java.io.tmpdir").orEmpty()), outgoing.size)
    val file = File.createTempFile("page-", ".part", spool)

    return try {
        file.outputStream().watched().buffered(PIECE).use(outgoing::writeTo)
        file
    } catch (full: OutputFailed) {
        file.delete()
        throw full
    } catch (changed: IOException) {
        file.delete()
        null
    }
}

/** A picture as a page carries it: its type, and what goes of it, in a file of its own until it's in the page. */
private class PagePicture(val type: String, val file: File)

/**
 * A picture as it goes inside a page, without what its file says beyond the
 * picture ([OutgoingMedia]), or as it is when `asItIs`, as the person
 * sharing chose, copied into `spool` ([spooled]); null when Orbit can't read
 * it through and it isn't, or it changed as it was copied. A HEIC or HEIF
 * picture, which Chromium doesn't draw, nor browsers but Safari, goes as the
 * JPEG `stills` makes of it for the map's own page ([FirstFrames]), once
 * Android can read it.
 */
private fun pagePicture(picture: File, asItIs: Boolean, stills: FirstFrames?, spool: File?): PagePicture? {
    sketchPicture(picture, spool)?.let { return it }
    val outgoing = OutgoingMedia.of(picture, if (asItIs) setOf(picture.name) else emptySet()) ?: return null

    val drawable = stills?.takeIf { isHeif(picture.name) }?.let { frames ->
        try {
            frames.of(picture)?.let(OutgoingMedia::of)
        } catch (unkept: IOException) {
            // Its JPEG couldn't be kept: the picture goes as it is, which Safari draws.
            null
        }
    }

    return if (drawable != null) {
        spooled(drawable, spool)?.let { PagePicture("image/jpeg", it) }
    } else {
        spooled(outgoing, spool)?.let { PagePicture(mediaType(picture.name), it) }
    }
}

/** Whether a picture is HEIC or HEIF by its name, as iPhones and some Samsungs take them. */
private fun isHeif(name: String): Boolean = name.substringAfterLast('.', "").lowercase().let { it == "heic" || it == "heif" }

/**
 * The map with its own pictures inside it, as data: URLs, for a screen that
 * can't reach the map's folder, as a TV the map is cast to: pictures larger
 * than `maxSide` pixels are shrunk, as the screen doesn't need them full
 * size. Videos stay out, as tens of megabytes of text would be too much to
 * carry: their posters, which are pictures, come along and show on the map.
 * Pictures go without what their files say beyond the picture
 * ([OutgoingMedia]); one Orbit can't read through stays out, unless it's one
 * of `asTheyAre`, which the person sharing chose to share as they are
 * (`Uncleaned`). The map's text is written once, with every picture it
 * points to, and each picture is read once, however many places name it.
 * They come to at most `most` bytes as they go, in the map's order: past
 * that, as a gigabyte of a map's photos would take it, the rest stay out,
 * and the screen shows their ideas without them.
 */
internal fun inlinePictures(markdown: String, mapFolder: File, maxSide: Int, asTheyAre: Set<String> = emptySet(), most: Long = Long.MAX_VALUE): String {
    val pictures = pictureFiles(mapFolder)
    // Each picture's address once it's read: null for one that stays out.
    val urls = HashMap<String, String?>()
    var room = most

    return pointAt(markdown, pictures.keys) { name ->
        if (name !in urls) {
            // One whose file is past what's left, and would go as it is, isn't read at all: an animation can be 250 MB.
            val url = pictures.getValue(name).takeIf { it.length() <= room || needsShrinking(it, maxSide) }?.let { pictureUrl(it, maxSide, asItIs = name in asTheyAre) }
            urls[name] = url?.takeIf { it.length <= room }
            urls[name]?.let { room -= it.length }
        }

        urls[name]
    }
}

/** Whether a picture is drawn again to go, rather than as its file is: larger than `maxSide` pixels, or a HEIC or HEIF one. */
private fun needsShrinking(picture: File, maxSide: Int): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(picture.path, bounds)
    return maxOf(bounds.outWidth, bounds.outHeight) > maxSide || isHeif(picture.name)
}

/**
 * A picture at most `maxSide` pixels on its longer side as a data: URL,
 * without what its file says beyond the picture, or as it is when `asItIs`,
 * as the person sharing chose; null when Orbit can't read it through and it
 * isn't.
 */
private fun pictureUrl(picture: File, maxSide: Int, asItIs: Boolean): String? {
    // A sketch goes as the PNG it draws, as Orbit takes no SVG inside a page.
    picture.takeIf(::isSketchFile)?.let { SketchSvg.read(it.readBytes()) }?.let { sketch ->
        return "data:image/png;base64,${Base64.getEncoder().encodeToString(SketchPictures.png(sketch, minOf(maxSide, SKETCH_SIDE)))}"
    }

    val (type, original) = shrunk(picture, maxSide)
    val bytes = OutgoingMedia.picture(original) ?: original.takeIf { asItIs } ?: return null
    return "data:$type;base64,${Base64.getEncoder().encodeToString(bytes)}"
}

/**
 * `text` with each place it names one of the map's files by a name in
 * `names` (as `media/…`, or as `./media/…`, where the map points at one:
 * see [MediaNames.references]) naming what `url` gives for that name
 * instead, all in one pass; a place `url` gives null for stays as it is, and
 * so do a web address and a longer name that only say the same. Inside a
 * list or mapping written on one line in an idea's attributes, the url goes
 * in quotes, as a page's does ([markdown]).
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
 * The picture at most `maxSide` pixels on its longer side, as JPEG, or PNG
 * when it's see-through. A HEIC or HEIF one is drawn again whatever its
 * size, as Chromium, which a TV's receiver is, doesn't draw it.
 */
private fun shrunk(picture: File, maxSide: Int): Pair<String, ByteArray> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(picture.path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)

    if (longest <= maxSide && !isHeif(picture.name)) {
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

/** The pictures in the map's media folder, by name: its files but its videos and sounds, and only those of `only` when it says. */
private fun pictureFiles(mapFolder: File, only: Set<String>? = null): Map<String, File> =
    File(mapFolder, MEDIA_DIRECTORY).listFiles().orEmpty()
        .filter { it.isFile && !isVideoName(it.name) && !isSoundName(it.name) && (only == null || it.name in only) }
        .associateBy { it.name }

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
