package com.hyuchia.orbit.page

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.OutgoingFile
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.data.OutputFailed
import com.hyuchia.orbit.data.PAGE_PICTURES_LIMIT
import com.hyuchia.orbit.data.PageSound
import com.hyuchia.orbit.data.TestMedia
import com.hyuchia.orbit.data.motionsFitting
import com.hyuchia.orbit.data.picturesFitting
import com.hyuchia.orbit.data.requireRoom
import com.hyuchia.orbit.data.soundsFitting
import com.hyuchia.orbit.nearby.scriptHash
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.Base64
import java.util.Locale

class WebPageTest {
    private val words = FileWords(Locale.US).pageWords()

    /** A map's folder, with its media folder, for the tests that need one. */
    private val folder: File = Files.createTempDirectory("orbit-web-page").toFile()

    private val media = File(folder, "media").apply { mkdirs() }

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    private fun asset(name: String): String = File("src/main/assets/map/$name").readText()

    /** The page [WebPage.write] writes from the app's own template and `script`, and the scripts it says it runs. */
    private fun written(
        markdown: String = "# Trip\n",
        script: String = "",
        theme: AppTheme = AppTheme(dark = false, tokens = emptyMap()),
        print: PrintRequest? = null,
        media: PageMedia? = null,
        generator: String = "Orbit for Android",
    ): Pair<String, List<String>> {
        val output = ByteArrayOutputStream()
        val scripts = WebPage.write(output, asset("web-page.html"), script, "Trip", markdown, theme, emptyList(), null, words, print, media, generator)
        return output.toString(Charsets.UTF_8.name()) to scripts
    }

    private fun page(markdown: String = "# Trip\n", theme: AppTheme = AppTheme(dark = false, tokens = emptyMap()), print: PrintRequest? = null, media: PageMedia? = null): String =
        written(markdown, theme = theme, print = print, media = media).first

    /** What a page tells its script, from its #orbit-map element. */
    private fun dataOf(page: String): JsonObject = protocolJson.parseToJsonElement(
        Regex("<script type=\"application/json\" id=\"orbit-map\">(.*?)</script>").find(page)!!.groupValues[1],
    ).jsonObject

    /** The map's text as a page carries it, its pictures inside it. */
    private fun markdownIn(page: String): String = dataOf(page).getValue("markdown").jsonPrimitive.content

    /** What a page carries in `field`, `sounds` or `motions`: a data: URL for each, by the address the map gives it. */
    private fun inside(page: String, field: String): Map<String, String> = dataOf(page).getValue(field).jsonObject.mapValues { it.value.jsonPrimitive.content }

    /** The sounds a page of `markdown` carries inside, with those of `asTheyAre` that can't go clean, by the address the map gives them. */
    private fun sounds(markdown: String, asTheyAre: Set<String> = emptySet()): Map<String, String> =
        inside(page(markdown, media = PageMedia(folder, asTheyAre, sounds = soundsFitting(markdown, folder, asTheyAre = asTheyAre))), "sounds")

    private fun picture(): ByteArray = ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    @Test
    fun aPageToPrintSaysWhatItsPictureShows() {
        val theme = AppTheme(dark = false, tokens = mapOf("text" to "#1B1B21"), seed = "#4F5B92")
        val page = page(theme = theme, print = PrintRequest(view = "focus", focus = "pack"))
        val print = dataOf(page)["print"]!!.jsonObject
        assertEquals("focus", print["view"]!!.jsonPrimitive.content)
        assertEquals("pack", print["focus"]!!.jsonPrimitive.content)
        // The layout has its place, shown only in print.
        assertTrue(page.contains("<div id=\"print\" class=\"page__print\"></div>"))
        assertTrue(page.contains("@media print"))
    }

    @Test
    fun aPageRunsOnlyItsOwnScriptWhereverItsOpened() {
        val script = "document.title = 'map';"
        val (page, scripts) = written(script = script)
        val policy = Regex("<meta http-equiv=\"Content-Security-Policy\" content=\"([^\"]*)\">").find(page)!!.groupValues[1]
        val directives = policy.split(';').map { it.trim() }

        // Its own script by its hash, and nothing else: no inline code, no javascript: address, no other site's script.
        assertEquals("script-src '${scriptHash(script)}'", directives.single { it.startsWith("script-src") })
        assertTrue(policy, "default-src 'none'" in directives)
        assertTrue(policy, "base-uri 'none'" in directives)
        assertTrue(policy, "frame-src https:" in directives)
        // Before any script it covers.
        assertTrue(page.indexOf("Content-Security-Policy") < page.indexOf("<script"))
        // And it says which scripts it runs, for a server's policy: its own, and not its data.
        assertEquals(listOf(script), scripts)
    }

    @Test
    fun aPageSaysWhichBuildsMadeIt() {
        val generator = WebPage.generator(OrbitBuild("0.3.0", "00c0099"), app = "0.4.0")
        assertEquals("Orbit for Android 0.4.0 (Orbit 0.3.0, 00c0099)", generator)
        val page = written(generator = generator).first
        assertTrue(page.contains("<meta name=\"generator\" content=\"Orbit for Android 0.4.0 (Orbit 0.3.0, 00c0099)\">"))
        // Without Orbit's record, the app's version alone.
        assertEquals("Orbit for Android 0.4.0", WebPage.generator(null, app = "0.4.0"))
    }

    @Test
    fun aPageToShareHasNothingToPrintAsked() {
        val page = page(theme = AppTheme(dark = true, tokens = emptyMap()))
        assertEquals(JsonNull, dataOf(page)["print"])
    }

    @Test
    fun aPageWrittenAPieceAtATimeReadsAsTheDataItsScriptExpects() {
        // Everything JSON escapes, a < that could end the element, and characters of every width.
        val markdown = "# Trip \"2026\" \\ path\n\t- Café <b> ✈️ 🗺 \u0001</script><!--\r\n"
        val theme = AppTheme(dark = true, tokens = mapOf("text" to "#E3E1EC"), seed = "#4F5B92")
        val page = page(markdown, theme = theme)
        val data = dataOf(page)

        assertEquals(markdown, data.getValue("markdown").jsonPrimitive.content)
        assertEquals(protocolJson.encodeToJsonElement(AppTheme.serializer(), theme), data["theme"])
        assertEquals(words.untitledMap, data.getValue("words").jsonObject.getValue("untitledMap").jsonPrimitive.content)
        assertEquals(emptyMap<String, String>(), inside(page, "sounds"))
        assertEquals(emptyMap<String, String>(), inside(page, "motions"))
        // Nothing in the data ends its element or opens a comment, whatever the map says.
        val element = Regex("<script type=\"application/json\" id=\"orbit-map\">(.*?)</script>").find(page)!!.groupValues[1]
        assertFalse(element.contains("<"))
        assertFalse(page.contains("<!-- data -->"))
    }

    @Test
    fun eachPlaceholderIsFilledOnceEvenWhenAValueMentionsAnother() {
        val page = fill(
            "<title><!-- title --></title><!-- data --><!-- script -->",
            mapOf(
                "<!-- title -->" to "Notes about <!-- script -->",
                "<!-- data -->" to "[data]",
                "<!-- script -->" to "[script]",
            ),
        )
        assertEquals("<title>Notes about <!-- script --></title>[data][script]", page)
    }

    @Test
    fun scriptsKeepTheirMeaningButCantEndTheirElement() {
        val safe = scriptSafe("""const a = "</script><!-- x"; const b = /<SCRIPT/i;""")
        assertFalse(safe.contains("</script", ignoreCase = true))
        assertFalse(safe.contains("<!--"))
        assertFalse(safe.contains("<script", ignoreCase = true))
        assertEquals("""const a = "\x3c/script>\x3c!-- x"; const b = /\x3cSCRIPT/i;""", safe)
    }

    @Test
    fun titlesAreText() {
        assertEquals("Q&amp;A &lt;draft&gt; &quot;v2&quot;", escapeHtml("Q&A <draft> \"v2\""))
    }

    @Test
    fun aPictureInsideAnIdeasMappingKeepsItsWholeDataAddressInQuotes() {
        File(media, "belem.jpg").writeBytes(TestMedia.picture())
        val url = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(OutgoingMedia.picture(TestMedia.picture()))
        val markdown = """
            # Trip
            ## Belém <!-- orbit image: { src: media/belem.jpg, caption: "Belém, at dusk" } -->
            ## Lake <!-- orbit image: { src: ./media/belem.jpg, motion: media/lake-motion.mp4 } -->
            ## Note <!-- orbit image: { caption: It's the river, src: media/belem.jpg } -->
            ## Quoted <!-- orbit image: { src: "media/belem.jpg" } -->
            ## Tower <!-- orbit image: media/belem.jpg -->
            ## Block
            <!-- orbit
            image:
              src: media/belem.jpg
            -->
            ![Belém](media/belem.jpg) and { src: media/belem.jpg } in a note
        """.trimIndent()
        val pointed = markdownIn(page(markdown, media = PageMedia(folder)))
        // Its comma would end a plain value in a mapping on one line: there, the address goes in quotes, as a whole.
        assertTrue(pointed.contains("""{ src: "$url", caption: "Belém, at dusk" }"""))
        assertTrue(pointed.contains("""{ src: "$url", motion: media/lake-motion.mp4 }"""))
        assertTrue(pointed.contains("""{ caption: It's the river, src: "$url" }"""))
        // Already in quotes, or anywhere a comma ends nothing, it goes as it is.
        assertTrue(pointed.contains("""{ src: "$url" }"""))
        assertTrue(pointed.contains("image: $url -->"))
        assertTrue(pointed.contains("  src: $url\n"))
        assertTrue(pointed.contains("![Belém]($url) and { src: $url } in a note"))
    }

    @Test
    fun aWebAddressAndALongerNameKeepTheirOwnTextInThePage() {
        File(media, "photo.jpg").writeBytes(TestMedia.picture())
        val url = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(OutgoingMedia.picture(TestMedia.picture()))
        val markdown = "# Trip\n![](media/photo.jpg) [Search](https://example.com/?q=media/photo.jpg) ![](https://example.com/media/photo.jpg) multimedia/photo.jpg ![](media/photo.jpg.old)"
        assertEquals(
            "# Trip\n![]($url) [Search](https://example.com/?q=media/photo.jpg) ![](https://example.com/media/photo.jpg) multimedia/photo.jpg ![](media/photo.jpg.old)",
            markdownIn(page(markdown, media = PageMedia(folder))),
        )
    }

    @Test
    fun soundsGoInsideThePageUpToTheirLimitAndVideosStayOut() {
        val intro = TestMedia.movie(500).also { File(media, "memo-intro.mp4").writeBytes(it) }
        val fado = TestMedia.mp3().also { File(media, "sound-fado.mp3").writeBytes(it) }
        File(media, "video-tram.mp4").writeBytes(TestMedia.movie(300))
        File(media, "sound-long.flac").writeBytes(TestMedia.flac())
        // Room for the memo and the fado, and not for the FLAC after them.
        val limit = intro.size + fado.size + 100L
        val markdown = """
            # Trip <!-- orbit audio: media/memo-intro.mp4 -->
            ## Alfama
            <!-- orbit
            audio:
              src: ./media/sound-fado.mp3
              caption: Fado
            -->
            [video](media/video-tram.mp4)
            ## Belém <!-- orbit audio: media/sound-long.flac -->
            ## Gone <!-- orbit audio: media/memo-gone.mp4 -->
        """.trimIndent()

        val page = page(markdown, media = PageMedia(folder, sounds = soundsFitting(markdown, folder, limit = limit)))
        val sounds = inside(page, "sounds")
        assertTrue(sounds.getValue("media/memo-intro.mp4").startsWith("data:audio/mp4;base64,"))
        // `./media/…` is the same place, and each sound goes in once.
        assertTrue(sounds.getValue("media/sound-fado.mp3").startsWith("data:audio/mpeg;base64,"))
        // A video stays out, and so does a sound past the limit, and one that isn't there.
        assertEquals(setOf("media/memo-intro.mp4", "media/sound-fado.mp3"), sounds.keys)
        assertFalse(markdownIn(page).contains("data:audio"))
        assertFalse(markdownIn(page).contains("data:video"))
    }

    @Test
    fun picturesGoInsideThePageUpToTheirBudgetInTheMapsOrderEachAsManyTimesAsItsShown() {
        val picture = picture()
        listOf("beach.png", "tram.png", "big.png", "fado.png").forEach { File(media, it).writeBytes(picture) }
        // Room for the beach twice and the tram, not for the big one after them; the small one after it still fits.
        java.io.RandomAccessFile(File(media, "big.png"), "rw").use { it.setLength(1_000_000) }
        val limit = picture.size * 4L
        val markdown = "# Trip\n![Beach](media/beach.png)\n## Again\n![Beach](media/beach.png)\n## Tram\n![Tram](media/tram.png)\n" +
            "## Big\n![Big](media/big.png)\n## Fado\n![Fado](media/fado.png)\n## Sound <!-- orbit audio: media/sound-1.mp3 -->\n"

        assertEquals(setOf("beach.png", "tram.png", "fado.png"), picturesFitting(markdown, folder, limit))

        val page = page(markdown, media = PageMedia(folder, pictures = picturesFitting(markdown, folder, limit)))
        val inside = markdownIn(page)
        assertEquals(4, Regex(Regex.escape("data:image/png;base64,")).findAll(inside).count())
        // The one that stays out keeps its place in the map, and its idea shows without it.
        assertTrue(inside, inside.contains("![Big](media/big.png)"))

        // A map's pictures within the budget, as every map before maps took a gigabyte was, all go.
        assertEquals(setOf("beach.png", "tram.png", "big.png", "fado.png"), picturesFitting(markdown, folder))
        assertTrue(PAGE_PICTURES_LIMIT > 90_000_000L)
    }

    @Test
    fun picturesGoInsideOnceEachWhereverTheMapPointsToThem() {
        val picture = picture()
        File(media, "tram.png").writeBytes(picture)
        File(media, "tram.png.png").writeBytes(picture)
        // Cut short, as a picture still arriving from a sync app is: Orbit can't read it through.
        File(media, "broken.png").writeBytes(picture.copyOfRange(0, picture.size / 3))
        val markdown = "# Trip\n![Tram](media/tram.png)\n## Again\n![Tram](./media/tram.png)\n## Long\n![Twice](media/tram.png.png)\n## Broken\n![Broken](media/broken.png)\n"

        val inside = markdownIn(page(markdown, media = PageMedia(folder)))
        val url = "data:image/png;base64,"
        assertFalse(inside, inside.contains("media/tram.png"))
        assertEquals(3, Regex(Regex.escape(url)).findAll(inside).count())
        // A name that starts another's is never taken for it, and one Orbit can't read stays where it was.
        assertTrue(inside.contains("![Twice]($url"))
        assertTrue(inside.contains("![Broken](media/broken.png)"))
    }

    @Test
    fun aPictureThatCantGoCleanGoesInsideThePageOnlyAsThePersonChose() {
        val image = java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val whole = ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "jpg", it) }.toByteArray()
        val cut = whole.copyOfRange(0, whole.size / 3)
        File(media, "picture-whole.jpg").writeBytes(whole)
        File(media, "picture-cut.jpg").writeBytes(cut)
        val markdown = "# Trip\n## Beach\n![](media/picture-whole.jpg)\n## Pier\n![](media/picture-cut.jpg)\n"

        // Left out, its address as it was, unless the person sharing chose to share it as it is.
        val left = markdownIn(page(markdown, media = PageMedia(folder)))
        assertTrue(left.contains("![](media/picture-cut.jpg)"))
        assertFalse(left.contains("media/picture-whole.jpg"))

        val chosen = markdownIn(page(markdown, media = PageMedia(folder, asTheyAre = setOf("picture-cut.jpg"))))
        assertTrue(chosen.contains("data:image/jpeg;base64,${Base64.getEncoder().encodeToString(cut)}"))
        assertFalse(chosen.contains("media/picture-cut.jpg"))
    }

    @Test
    fun aHeicPictureGoesInsideAsTheJpegTheMapsOwnPageDraws() {
        // Chromium draws no HEIC: in a page, and on paper, the picture is the JPEG the map's page has of it.
        val jpeg = TestMedia.picture()
        val drawn = mutableListOf<String>()
        val stills = FirstFrames(File(folder, "frames"), drawn = { file -> drawn += file.name; jpeg }) { error("No animation here") }
        File("src/test/resources/live/iphone-live.heic").copyTo(File(media, "picture-1.heic"))
        File("src/test/resources/live/iphone-live.heic").copyTo(File(media, "picture-2.HEIF"))
        val markdown = "# Trip\n## Pier\n![](media/picture-1.heic)\n## Lake <!-- orbit image: { src: media/picture-2.HEIF, caption: Lake } -->\n"
        val url = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(OutgoingMedia.picture(jpeg))

        for (print in listOf(null, PrintRequest(view = "all"))) {
            val inside = markdownIn(page(markdown, print = print, media = PageMedia(folder, stills = stills)))
            assertTrue(inside, inside.contains("![]($url)"))
            assertTrue(inside, inside.contains("{ src: \"$url\", caption: Lake }"))
            assertFalse(inside.contains("image/heic"))
            assertFalse(inside.contains("media/picture-"))
        }

        // Made once for each picture as it is, and kept, as for the map's own page.
        assertEquals(listOf("picture-1.heic", "picture-2.HEIF"), drawn)

        // One Android can't read goes as it is, cleaned, which Safari draws.
        val unread = FirstFrames(File(folder, "unread"), drawn = { null }) { null }
        val asItIs = markdownIn(page(markdown, media = PageMedia(folder, stills = unread)))
        assertTrue(asItIs.contains("![](data:image/heic;base64,"))
    }

    @Test
    fun liveMotionsGoInsideThePageWithoutWhatTheirFilesSayBeyondThem() {
        File(media, "picture-1-motion.mp4").writeBytes(memo())
        File(media, "picture-2-motion.mp4").writeBytes(TestMedia.movie(300))
        val markdown = """
            # Trip
            - Lake <!-- orbit image: { src: media/picture-1.jpg, motion: media/picture-1-motion.mp4 } -->
            - Pier <!-- orbit image: { src: media/picture-2.jpg, motion: ./media/picture-2-motion.mp4 } -->
        """.trimIndent()

        val motions = inside(page(markdown, media = PageMedia(folder, motions = motionsFitting(markdown, folder))), "motions")
        assertEquals(setOf("media/picture-1-motion.mp4", "media/picture-2-motion.mp4"), motions.keys)
        assertTrue(motions.getValue("media/picture-1-motion.mp4").startsWith("data:video/mp4;base64,"))
        val lake = Base64.getDecoder().decode(motions.getValue("media/picture-1-motion.mp4").substringAfter(";base64,"))
        assertFalse(lake.says(place))
        assertEquals(memo().size, lake.size)
    }

    @Test
    fun aPageSaysTheMostItMayComeToBeforeAnyOfItIsWrittenAndStartsNowhereWithoutRoom() {
        File(media, "photo.jpg").writeBytes(TestMedia.picture())
        File(media, "memo-1.mp4").writeBytes(TestMedia.movie(5_000))
        // A picture the map names twice is in the page twice.
        val markdown = "# Trip <!-- orbit audio: media/memo-1.mp4 -->\n![](media/photo.jpg)\n## Again\n![](./media/photo.jpg)\n"
        val pageMedia = PageMedia(folder, sounds = soundsFitting(markdown, folder))
        val output = ByteArrayOutputStream()
        var most = 0L
        var writtenFirst = -1

        WebPage.write(output, asset("web-page.html"), "", "Trip", markdown, AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, words, media = pageMedia) { size ->
            most = size
            writtenFirst = output.size()
        }

        assertEquals(0, writtenFirst)
        assertTrue("${output.size()} of $most", output.size() in 1..most)
        assertEquals(2, Regex("data:image/jpeg;base64,").findAll(markdownIn(output.toString(Charsets.UTF_8.name()))).count())

        // A storage without room for it: nothing of the page is written, and its storage says why.
        val none = ByteArrayOutputStream()

        val full = runCatching {
            WebPage.write(none, asset("web-page.html"), "", "Trip", markdown, AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, words, media = pageMedia) { size ->
                requireRoom(folder, size + Long.MAX_VALUE / 4)
            }
        }

        assertTrue(full.exceptionOrNull().toString(), full.exceptionOrNull() is OutputFailed)
        assertEquals(0, none.size())
    }

    @Test
    fun aPagesPartsHaveAFolderOfTheirOwnAndThoseOfAPageNeverFinishedGo() {
        val cache = File(folder, "cache")
        val now = System.currentTimeMillis()
        val parts = pageParts(cache, now)
        // One left as Orbit stopped in the middle of a page, and one of a page being made now.
        val left = File(parts, "page-1.part")
        left.writeBytes(ByteArray(10))
        left.setLastModified(now - 2 * 60 * 60 * 1000L)
        val making = File(parts, "page-2.part")
        making.writeBytes(ByteArray(10))
        making.setLastModified(now - 5 * 60 * 1000L)

        assertEquals(parts, pageParts(cache, now))
        assertFalse(left.exists())
        assertTrue(making.exists())
        assertEquals(File(cache, "page-parts"), parts)
    }

    @Test
    fun aSoundWhoseFileChangesAsThePageIsMadeStaysOutAndThePageIsWhole() {
        // A sound that shrinks after it was planned, as a sync app writing it does: its copy fails after a megabyte.
        val changing = File(media, "memo-changing.mp4").apply { writeBytes(ByteArray(1024 * 1024) { (it % 251).toByte() }) }
        val fado = File(media, "sound-fado.mp3").apply { writeBytes(TestMedia.mp3()) }
        val sounds = listOf(
            PageSound(changing, OutgoingFile.Patched(changing, emptyList(), end = changing.length() + 1_000)),
            PageSound(fado, OutgoingMedia.of(fado)!!),
        )
        val spool = File(folder, "spool").apply { mkdirs() }

        val page = page("# Trip <!-- orbit audio: media/memo-changing.mp4 -->\n## Fado <!-- orbit audio: media/sound-fado.mp3 -->", media = PageMedia(folder, sounds = sounds, spool = spool))

        // The page reads whole, with the sound that could be read and none of the other, and nothing is left in the spool.
        assertEquals(setOf("media/sound-fado.mp3"), inside(page, "sounds").keys)
        assertTrue(page.trimEnd().endsWith("</html>"))
        assertEquals(emptyList<String>(), spool.list()!!.toList())
    }

    /** Where the memo below was recorded, as a phone writes it into the file. */
    private val place = "+38.7139-009.1394/"

    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = content.fold(ByteArray(0)) { all, part -> all + part }
        return ByteBuffer.allocate(8).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).array() + body
    }

    private fun full(vararg content: ByteArray): ByteArray = ByteArray(4) + content.fold(ByteArray(0)) { all, part -> all + part }

    private fun int(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun text(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)

    /** A movie's, track's or media's header, made and changed on 21 October 2026 (seconds since 1904). */
    private fun header(type: String): ByteArray = box(type, full(int(0xEC8B0E80.toInt()), int(0xEC8B0E80.toInt()), int(48_000), int(96_000)))

    private val frames = ByteArray(200) { (it * 11).toByte() }

    /** A voice memo as a phone records it: AAC in MP4, when it was recorded in its headers, where in `udta`, and the phone's Android in its keys. */
    private fun memo(): ByteArray = box("ftyp", text("mp42"), int(0), text("isommp42")) +
        box("mdat", frames) +
        box(
            "moov",
            header("mvhd"),
            box("trak", header("tkhd"), box("mdia", header("mdhd"), box("hdlr", full(int(0), text("soun"), ByteArray(12), text("SoundHandle\u0000"))))),
            box("udta", box("©xyz", text(place))),
            box("meta", full(box("hdlr", full(int(0), text("mdta"), ByteArray(13))), box("keys", full(int(1), int(27), text("mdta"), text("com.android.version"))))),
        )

    private fun ByteArray.says(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    private fun inside(sounds: Map<String, String>, name: String): ByteArray = Base64.getDecoder().decode(sounds.getValue("media/$name").substringAfter(";base64,"))

    @Test
    fun soundsGoInsideThePageWithoutWhenWhereAndOnWhatTheyWereRecorded() {
        File(media, "memo-intro.mp4").writeBytes(memo())
        File(media, "memo-cut.mp4").writeBytes(memo().let { it.copyOfRange(0, it.size - 24) })
        File("src/test/resources/outgoing/tagged.mp3").copyTo(File(media, "sound-fado.mp3"))
        File("src/test/resources/outgoing/located.mp4").copyTo(File(media, "video-walk.mp4"))
        val markdown = """
            # Trip <!-- orbit audio: media/memo-intro.mp4 -->
            ## Alfama <!-- orbit audio: media/memo-cut.mp4 -->
            ## Fado <!-- orbit audio: media/sound-fado.mp3 -->
            ## Walk <!-- orbit audio: media/video-walk.mp4 -->
        """.trimIndent()

        val sounds = sounds(markdown)
        val intro = inside(sounds, "memo-intro.mp4")
        assertTrue(sounds.getValue("media/memo-intro.mp4").startsWith("data:audio/mp4;base64,"))
        assertFalse(intro.says(place))
        assertFalse(intro.says("com.android.version"))
        // The sound itself is where it was, and when it was recorded is zero.
        assertEquals(memo().size, intro.size)
        assertTrue(intro.says(String(frames, Charsets.ISO_8859_1)))
        val movieHeader = String(intro, Charsets.ISO_8859_1).indexOf("mvhd")
        assertTrue(intro.copyOfRange(movieHeader + 8, movieHeader + 16).all { it == 0.toByte() })
        // The map's own file stays as it was.
        assertTrue(File(media, "memo-intro.mp4").readBytes().contentEquals(memo()))

        // A video played as a sound goes the same way, and an MP3 without its tags, its frames as they were.
        assertFalse(inside(sounds, "video-walk.mp4").says(place))
        val fado = inside(sounds, "sound-fado.mp3")
        assertFalse(fado.says("Test title"))
        assertFalse(fado.says(place))
        assertTrue(fado.says(String(File(media, "sound-fado.mp3").readBytes().let { it.copyOfRange(it.size - 1000, it.size - 128) }, Charsets.ISO_8859_1)))
        // One that can't be read through, as a memo cut short, stays out.
        assertEquals(setOf("media/memo-intro.mp4", "media/sound-fado.mp3", "media/video-walk.mp4"), sounds.keys)
        // Unless the person sharing chose to share it as it is: then it goes in, exactly as it is.
        val chosen = sounds(markdown, asTheyAre = setOf("memo-cut.mp4"))
        assertTrue(inside(chosen, "memo-cut.mp4").contentEquals(File(media, "memo-cut.mp4").readBytes()))
        assertFalse(inside(chosen, "memo-intro.mp4").says(place))
    }

    /** The seconds since 1904 a movie's, track's or media's header says it was made, from its version 0 fields. */
    private fun ByteArray.madeIn(header: String): Long {
        val at = String(this, Charsets.ISO_8859_1).indexOf(header) + 8
        return ByteBuffer.wrap(this, at, 4).int.toLong() and 0xFFFFFFFFL
    }

    @Test
    fun aVoiceMemoAsAPhoneRecordsItGoesInsideThePageWithoutWhenOrOnWhatItWasRecorded() {
        // Recorded by MediaRecorder on Android 15 as Orbit records memos: AAC in MP4, mono, 48 kHz, 96 kbps.
        val recorded = File("src/test/resources/outgoing/memo.mp4").readBytes()
        assertTrue(recorded.madeIn("mvhd") > 0)
        assertTrue(recorded.says("com.android.version"))
        File(media, "memo-mf2k1x.mp4").writeBytes(recorded)
        val memo = inside(sounds("# Trip <!-- orbit audio: media/memo-mf2k1x.mp4 -->"), "memo-mf2k1x.mp4")

        for (header in listOf("mvhd", "tkhd", "mdhd")) {
            assertEquals(header, 0L, memo.madeIn(header))
        }

        assertFalse(memo.says("com.android.version"))
        // Every frame where it was: only what's around the sound changed.
        assertEquals(recorded.size, memo.size)
        val sound = String(recorded, Charsets.ISO_8859_1).indexOf("mdat")
        assertTrue(recorded.copyOfRange(sound, recorded.size).contentEquals(memo.copyOfRange(sound, memo.size)))
    }
}
