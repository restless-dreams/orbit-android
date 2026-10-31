package com.hyuchia.orbit.page

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.TestMedia
import com.hyuchia.orbit.nearby.scriptHash
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Locale

class WebPageTest {
    private val words = FileWords(Locale.US).pageWords()

    private fun asset(name: String): String = File("src/main/assets/map/$name").readText()

    /** What a page tells its script, from its #orbit-map element. */
    private fun dataOf(page: String) = protocolJson.parseToJsonElement(
        Regex("<script type=\"application/json\" id=\"orbit-map\">(.*?)</script>").find(page)!!.groupValues[1],
    ).jsonObject

    @Test
    fun aPageToPrintSaysWhatItsPictureShows() {
        val theme = AppTheme(dark = false, tokens = mapOf("text" to "#1B1B21"), seed = "#4F5B92")
        val page = WebPage.page(asset("web-page.html"), "", "Trip", "# Trip\n", theme, emptyList(), null, words, PrintRequest(view = "focus", focus = "pack"))
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
        val page = WebPage.page(asset("web-page.html"), script, "Trip", "# Trip\n", AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, words)
        val policy = Regex("<meta http-equiv=\"Content-Security-Policy\" content=\"([^\"]*)\">").find(page)!!.groupValues[1]
        val directives = policy.split(';').map { it.trim() }

        // Its own script by its hash, and nothing else: no inline code, no javascript: address, no other site's script.
        assertEquals("script-src '${scriptHash(script)}'", directives.single { it.startsWith("script-src") })
        assertTrue(policy, "default-src 'none'" in directives)
        assertTrue(policy, "base-uri 'none'" in directives)
        assertTrue(policy, "frame-src https:" in directives)
        // Before any script it covers.
        assertTrue(page.indexOf("Content-Security-Policy") < page.indexOf("<script"))
    }

    @Test
    fun aPageSaysWhichBuildsMadeIt() {
        val generator = WebPage.generator(OrbitBuild("0.3.0", "00c0099"), app = "0.4.0")
        assertEquals("Orbit for Android 0.4.0 (Orbit 0.3.0, 00c0099)", generator)
        val page = WebPage.page(asset("web-page.html"), "", "Trip", "# Trip\n", AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, words, generator = generator)
        assertTrue(page.contains("<meta name=\"generator\" content=\"Orbit for Android 0.4.0 (Orbit 0.3.0, 00c0099)\">"))
        // Without Orbit's record, the app's version alone.
        assertEquals("Orbit for Android 0.4.0", WebPage.generator(null, app = "0.4.0"))
    }

    @Test
    fun aPageToShareHasNothingToPrintAsked() {
        val page = WebPage.page(asset("web-page.html"), "", "Trip", "# Trip\n", AppTheme(dark = true, tokens = emptyMap()), emptyList(), null, words)
        assertEquals(JsonNull, dataOf(page)["print"])
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
        val url = "data:image/jpeg;base64,AAAA"
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
        val pointed = pointAt(markdown, listOf("belem.jpg")) { url }
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
        val markdown = "# Trip\n![](media/photo.jpg) [Search](https://example.com/?q=media/photo.jpg) ![](https://example.com/media/photo.jpg) multimedia/photo.jpg ![](media/photo.jpg.old)"
        assertEquals(
            "# Trip\n![](data:x) [Search](https://example.com/?q=media/photo.jpg) ![](https://example.com/media/photo.jpg) multimedia/photo.jpg ![](media/photo.jpg.old)",
            pointAt(markdown, listOf("photo.jpg")) { "data:x" },
        )
    }

    @Test
    fun soundsGoInsideThePageUpToTheirLimitAndVideosStayOut() {
        val folder = java.nio.file.Files.createTempDirectory("orbit-sounds").toFile()

        try {
            val media = File(folder, "media").apply { mkdirs() }
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

            val sounds = soundsInside(markdown, folder, limit = limit)
            assertTrue(sounds.getValue("media/memo-intro.mp4").startsWith("data:audio/mp4;base64,"))
            // `./media/…` is the same place, and each sound goes in once.
            assertTrue(sounds.getValue("media/sound-fado.mp3").startsWith("data:audio/mpeg;base64,"))
            // A video stays out, and so does a sound past the limit, and one that isn't there.
            assertEquals(setOf("media/memo-intro.mp4", "media/sound-fado.mp3"), sounds.keys)
            assertFalse(inlinePictures(markdown, folder).contains("data:audio"))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun picturesGoInsideOnceEachWhereverTheMapPointsToThem() {
        val folder = java.nio.file.Files.createTempDirectory("orbit-pictures").toFile()

        try {
            val media = File(folder, "media").apply { mkdirs() }
            val picture = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
            File(media, "tram.png").writeBytes(picture)
            File(media, "tram.png.png").writeBytes(picture)
            // Cut short, as a picture still arriving from a sync app is: Orbit can't read it through.
            File(media, "broken.png").writeBytes(picture.copyOfRange(0, picture.size / 3))
            val markdown = "# Trip\n![Tram](media/tram.png)\n## Again\n![Tram](./media/tram.png)\n## Long\n![Twice](media/tram.png.png)\n## Broken\n![Broken](media/broken.png)\n"

            val inside = inlinePictures(markdown, folder)
            val url = "data:image/png;base64,"
            assertFalse(inside, inside.contains("media/tram.png"))
            assertEquals(3, Regex(Regex.escape(url)).findAll(inside).count())
            // A name that starts another's is never taken for it, and one Orbit can't read stays where it was.
            assertTrue(inside.contains("![Twice]($url"))
            assertTrue(inside.contains("![Broken](media/broken.png)"))
        } finally {
            folder.deleteRecursively()
        }
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
    fun aPictureThatCantGoCleanGoesInsideThePageOnlyAsThePersonChose() {
        val folder = java.nio.file.Files.createTempDirectory("orbit-uncleaned").toFile()

        try {
            val image = java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val whole = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "jpg", it) }.toByteArray()
            val cut = whole.copyOfRange(0, whole.size / 3)
            val media = File(folder, "media").apply { mkdirs() }
            File(media, "picture-whole.jpg").writeBytes(whole)
            File(media, "picture-cut.jpg").writeBytes(cut)
            val markdown = "# Trip\n## Beach\n![](media/picture-whole.jpg)\n## Pier\n![](media/picture-cut.jpg)\n"

            // Left out, its address as it was, unless the person sharing chose to share it as it is.
            val left = inlinePictures(markdown, folder)
            assertTrue(left.contains("![](media/picture-cut.jpg)"))
            assertFalse(left.contains("media/picture-whole.jpg"))

            val chosen = inlinePictures(markdown, folder, asTheyAre = setOf("picture-cut.jpg"))
            assertTrue(chosen.contains("data:image/jpeg;base64,${Base64.getEncoder().encodeToString(cut)}"))
            assertFalse(chosen.contains("media/picture-cut.jpg"))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun soundsGoInsideThePageWithoutWhenWhereAndOnWhatTheyWereRecorded() {
        val folder = java.nio.file.Files.createTempDirectory("orbit-memos").toFile()

        try {
            val media = File(folder, "media").apply { mkdirs() }
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

            val sounds = soundsInside(markdown, folder)
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
            val chosen = soundsInside(markdown, folder, asTheyAre = setOf("memo-cut.mp4"))
            assertTrue(inside(chosen, "memo-cut.mp4").contentEquals(File(media, "memo-cut.mp4").readBytes()))
            assertFalse(inside(chosen, "memo-intro.mp4").says(place))
        } finally {
            folder.deleteRecursively()
        }
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
        val folder = java.nio.file.Files.createTempDirectory("orbit-memo").toFile()

        try {
            File(folder, "media").mkdirs()
            File(folder, "media/memo-mf2k1x.mp4").writeBytes(recorded)
            val memo = inside(soundsInside("# Trip <!-- orbit audio: media/memo-mf2k1x.mp4 -->", folder), "memo-mf2k1x.mp4")

            for (header in listOf("mvhd", "tkhd", "mdhd")) {
                assertEquals(header, 0L, memo.madeIn(header))
            }

            assertFalse(memo.says("com.android.version"))
            // Every frame where it was: only what's around the sound changed.
            assertEquals(recorded.size, memo.size)
            val sound = String(recorded, Charsets.ISO_8859_1).indexOf("mdat")
            assertTrue(recorded.copyOfRange(sound, recorded.size).contentEquals(memo.copyOfRange(sound, memo.size)))
        } finally {
            folder.deleteRecursively()
        }
    }
}
