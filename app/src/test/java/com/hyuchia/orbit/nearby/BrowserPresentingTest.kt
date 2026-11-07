package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.OutgoingCopies
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.present.ShownMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class BrowserPresentingTest {
    /** What a phone in English shows. */
    private val english = FileWords(Locale.US)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mapFolder: File = Files.createTempDirectory("orbit-map").toFile()
    private val rooms = mutableListOf<BrowserPresenting>()
    private val sockets = mutableListOf<Socket>()

    /** The presentation's clock, in milliseconds, which the tests move on. */
    @Volatile private var now = 100_000L

    /** A presentation in browsers, served over the loopback from the app's own pages, that anyone can join: in English, or in `words`. */
    private fun present(words: Words = english): BrowserPresenting =
        BrowserPresenting(::asset, words, mapFolder, scope, JoiningOptions(askNames = false), preferredPort = 0, policy = anyone, listenOn = { null }, clock = { now }).also {
            rooms += it
            it.start()
        }

    private fun asset(name: String): String = File("src/main/assets/map/$name").readText()

    @After
    fun stop() {
        sockets.forEach { runCatching { it.close() } }
        rooms.forEach { it.stop() }
        scope.cancel()
        mapFolder.deleteRecursively()
    }

    /** A browser joins, and reads what its stream catches up with, up to its step: the map, the captions if they changed, the idea. */
    private fun join(room: BrowserPresenting): BufferedReader {
        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val (socket, lines) = browser.follow("/${room.code}/events")
        sockets += socket
        lines.head()
        assertTrue(lines.readLine().startsWith("data: {\"type\":\"show\""))
        assertEquals("", lines.readLine())
        var line = lines.readLine()

        if (line.startsWith("data: {\"type\":\"captions\"")) {
            assertEquals("", lines.readLine())
            line = lines.readLine()
        }

        assertTrue(line, line.startsWith("data: {\"type\":\"step\""))
        assertEquals("", lines.readLine())
        return lines
    }

    /** Shows a map that points at the files the tests ask for: a picture, videos of each kind, and files of other kinds, as links. */
    private fun showMap(room: BrowserPresenting) {
        val markdown = """
            # Lisbon
            ## Tram
            <!-- orbit video: media/clip.mp4 -->
            ![The beach](media/beach.jpg)
            Cut as [one](media/clip.m4v), [two](media/clip.webm) and [three](media/clip.mov), with [notes](media/notes.txt), [a page](media/page.html) and [one lost](media/missing.mp4).
        """.trimIndent()
        room.audience.send(AudienceCommand.Show(markdown, AppTheme(dark = false, tokens = emptyMap()), focus = "lisbon"))
        room.audience.send(AudienceCommand.Step("tram", 1, 2))
    }

    @Test
    fun theAudiencesPageComesInThePresentersLanguageWithItsWords() {
        val inEnglish = present()
        val english = Browser(inEnglish.port).get("/${inEnglish.code}/").body
        assertTrue(english.contains("<html lang=\"en\">"))
        assertTrue(english.contains("<div class=\"audience__waiting\">Waiting for the presenter</div>"))
        assertTrue(english.contains("<script type=\"application/json\" id=\"orbit-words\">{\"language\":\"en\","))
        assertFalse(english.contains("<!--"))

        val inSpanish = present(SpanishWords)
        val spanish = Browser(inSpanish.port).get("/${inSpanish.code}/").body
        assertTrue(spanish.contains("<html lang=\"es-MX\">"))
        // Before its script comes over the network, the page already waits in Spanish.
        assertTrue(spanish.contains("<div class=\"audience__waiting\">Esperando a quien presenta</div>"))
        assertTrue(spanish.contains("\"milestone\":\"Hito\""))
        assertTrue(spanish.contains("\"tapForSound\":\"Toca para activar el sonido\""))
        assertFalse(spanish.contains("<!--"))
    }

    @Test
    fun aBrowserThatJoinsWhileAVideoPlaysOpensItWhereItIsNow() {
        val room = present()
        showMap(room)
        room.audience.send(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 0.0))
        now += 42_500
        assertEquals("data: {\"type\":\"media\",\"id\":\"tram\",\"kind\":\"video\",\"playing\":true,\"at\":42.5}", join(room).readLine())

        // Started over a minute later, it's where the new start puts it.
        now += 60_000
        room.audience.send(AudienceCommand.Seek(0.0))
        room.audience.send(AudienceCommand.Playback(true))
        now += 3_000
        assertEquals("data: {\"type\":\"media\",\"id\":\"tram\",\"kind\":\"video\",\"playing\":true,\"at\":3.0}", join(room).readLine())
    }

    @Test
    fun aBrowserCatchingUpAsThePresenterStepsOnHearsThatStep() {
        // The clock is read as a joining browser catches up with a video playing, before it follows the room: there,
        // the presenter steps on, from another thread, as the phone's main thread does.
        val stepping = AtomicBoolean(false)
        var stepper: Thread? = null
        lateinit var room: BrowserPresenting

        val clock = {
            if (stepping.compareAndSet(true, false)) {
                val step = thread { room.audience.send(AudienceCommand.Step("lisbon", 0, 2)) }
                stepper = step
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)

                // Until the step has gone out, or waits for the browser to finish catching up.
                while (step.state !in setOf(Thread.State.TERMINATED, Thread.State.BLOCKED, Thread.State.WAITING) && System.nanoTime() < until) {
                    Thread.sleep(5)
                }
            }

            now
        }

        room = BrowserPresenting(::asset, english, mapFolder, scope, JoiningOptions(askNames = false), preferredPort = 0, policy = anyone, listenOn = { null }, clock = clock)
        rooms += room
        room.start()
        showMap(room)
        room.audience.send(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 0.0))
        stepping.set(true)
        val lines = join(room)
        assertEquals("data: {\"type\":\"media\",\"id\":\"tram\",\"kind\":\"video\",\"playing\":true,\"at\":0.0}", lines.readLine())
        assertEquals("", lines.readLine())
        stepper!!.join(10_000)

        // What comes next: the step it was catching up as, then what the presenter does after.
        room.audience.send(AudienceCommand.Captions(false))
        assertEquals("data: {\"type\":\"step\",\"id\":\"lisbon\",\"index\":0,\"total\":2}", lines.readLine())
        assertEquals("", lines.readLine())
        assertEquals("data: {\"type\":\"captions\",\"on\":false}", lines.readLine())
    }

    @Test
    fun aBrowserThatJoinsHalfwayStartsWhereTheBigScreenSaysTheVideoIs() {
        val room = present()
        showMap(room)
        room.audience.send(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 0.0))
        now += 20_000
        // The second screen took three seconds to start: the phone's reckoning, corrected.
        room.audience.reckon(ShownMedia("tram", MediaKind.Video, playing = true, at = 17.0, since = now))
        now += 5_000
        assertEquals("data: {\"type\":\"media\",\"id\":\"tram\",\"kind\":\"video\",\"playing\":true,\"at\":22.0}", join(room).readLine())
    }

    @Test
    fun aPausedVideoCatchesUpWhereItPausedAndAPictureAsItIs() {
        val room = present()
        showMap(room)
        room.audience.send(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 5.0))
        now += 10_000
        room.audience.send(AudienceCommand.Playback(false))
        now += 60_000
        assertEquals("data: {\"type\":\"media\",\"id\":\"tram\",\"kind\":\"video\",\"playing\":false,\"at\":15.0}", join(room).readLine())

        room.audience.send(AudienceCommand.Media("tram", MediaKind.Picture))
        now += 60_000
        assertEquals("data: {\"type\":\"media\",\"id\":\"tram\",\"kind\":\"image\",\"playing\":false,\"at\":0.0}", join(room).readLine())
    }

    @Test
    fun afterTheMapIsBackABrowserCatchesUpWithTheMapAlone() {
        val room = present()
        showMap(room)

        listOf(AudienceCommand.CloseMedia, AudienceCommand.Step("lisbon", 0, 2)).forEach { back ->
            room.audience.send(AudienceCommand.Media("tram", MediaKind.Video, playing = true))
            room.audience.send(back)
            val lines = join(room)
            // The next thing it hears is what comes next, not the video.
            room.audience.send(AudienceCommand.Captions(false))
            assertEquals("data: {\"type\":\"captions\",\"on\":false}", lines.readLine())
        }
    }

    @Test
    fun theRoomGetsVideosWithoutWhereTheyWereFilmedStillInTheirParts() {
        val place = "+38.7139-009.1394/"
        val media = File(mapFolder, "media").apply { mkdirs() }
        File("src/test/resources/outgoing/located.mp4").copyTo(File(media, "clip.mp4"))
        File(mapFolder, "map.md").writeText("# Map")
        val copies = OutgoingCopies(Files.createTempDirectory("orbit-outgoing").toFile())
        val room = BrowserPresenting(::asset, english, mapFolder, scope, JoiningOptions(askNames = false), preferredPort = 0, policy = anyone, listenOn = { null }, clock = { now }, outgoing = copies::of)
        rooms += room
        room.start()
        showMap(room)

        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val whole = browser.get("/${room.code}/media/clip.mp4")
        assertEquals(200, whole.status)
        assertFalse(whole.body.contains(place))
        assertTrue(File(media, "clip.mp4").readText(Charsets.ISO_8859_1).contains(place))

        // The same size, so a browser playing it in parts finds each where it asks.
        val part = browser.get("/${room.code}/media/clip.mp4", headers = mapOf("Range" to "bytes=100-199"))
        assertEquals(206, part.status)
        assertEquals("bytes 100-199/${File(media, "clip.mp4").length()}", part.header("content-range"))
    }

    @Test
    fun aPictureStillBeingMadeReadyAsThePresentationEndsNeverGoes() {
        val media = File(mapFolder, "media").apply { mkdirs() }
        File(media, "beach.jpg").writeBytes(ByteArray(64))
        File(mapFolder, "map.md").writeText("# Map")
        val copying = CountDownLatch(1)
        val ended = CountDownLatch(1)

        // Its copy without what its file says takes a while, as a big video's does.
        val outgoing = { file: File ->
            copying.countDown()
            ended.await(5, TimeUnit.SECONDS)
            file
        }

        val room = BrowserPresenting(::asset, english, mapFolder, scope, JoiningOptions(askNames = false), preferredPort = 0, policy = anyone, listenOn = { null }, clock = { now }, outgoing = outgoing)
        rooms += room
        room.start()
        showMap(room)
        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val asked = CompletableFuture.supplyAsync { browser.get("/${room.code}/media/beach.jpg") }

        // The map is private, and locks as its picture is made ready: the presentation ends.
        assertTrue(copying.await(5, TimeUnit.SECONDS))
        room.audience.send(AudienceCommand.End)
        ended.countDown()
        assertEquals(404, asked.get(5, TimeUnit.SECONDS).status)
    }

    @Test
    fun picturesAndVideosGoToThoseWhoJoinedVideosInTheirParts() {
        val media = File(mapFolder, "media").apply { mkdirs() }
        val clip = "0123456789".repeat(1_000)
        listOf("clip.mp4", "clip.m4v", "clip.webm", "clip.mov", "notes.txt", "page.html").forEach { File(media, it).writeText(clip) }
        File(media, "beach.jpg").writeBytes(ByteArray(64))
        File(mapFolder, "map.md").writeText("# Map")
        val room = present()
        showMap(room)
        val path = "/${room.code}/media"

        // Not without joining.
        assertEquals(403, request(room.port, "$path/clip.mp4", headers = mapOf("Range" to "bytes=0-99")).status)

        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val part = browser.get("$path/clip.mp4", headers = mapOf("Range" to "bytes=100-199"))
        assertEquals(206, part.status)
        assertEquals("video/mp4", part.header("content-type"))
        assertEquals("bytes 100-199/10000", part.header("content-range"))
        assertEquals(clip.substring(100, 200), part.body)
        assertEquals(416, browser.get("$path/clip.mp4", headers = mapOf("Range" to "bytes=20000-")).status)

        mapOf("clip.m4v" to "video/mp4", "clip.webm" to "video/webm", "clip.mov" to "video/quicktime", "beach.jpg" to "image/jpeg").forEach { (name, type) ->
            val answer = browser.get("$path/$name")
            assertEquals(name, 200, answer.status)
            assertEquals(name, type, answer.header("content-type"))
            assertEquals(name, "bytes", answer.header("accept-ranges"))
        }

        // Nothing but pictures and videos, and nothing outside the map's media folder.
        listOf("notes.txt", "page.html", "missing.mp4", "..%2Fmap.md", "%2E%2E", ".hidden.mp4").forEach { name ->
            assertEquals(name, 404, browser.get("$path/$name").status)
        }
    }

    @Test
    fun theRoomGetsOnlyTheFilesTheMapPointsAt() {
        val media = File(mapFolder, "media").apply { mkdirs() }
        val shown = listOf("beach.jpg", "trail-poster.jpg", "trail.mp4", "tram.mp4", "tram.jpg", "lake.jpg", "lake-motion.mp4", "boat.jpg", "fado.jpg")
        // What the map's folder keeps for its earlier versions: a picture taken off it, one that came in a zip, and Orbit's own.
        val kept = listOf("photo.jpg", "IMG_1234.jpg", "picture-mf2k1x.jpg", "video-mf2k1x.mp4")
        (shown + kept).forEach { File(media, it).writeBytes(ByteArray(64)) }
        File(mapFolder, "map.md").writeText("# Map")
        val room = present()
        val markdown = """
            # Lisbon
            ## Beach
            ![The beach](media/beach.jpg)
            ## Trail
            [![Its poster](media/trail-poster.jpg)](media/trail.mp4)
            ## Tram
            <!-- orbit video: { src: media/tram.mp4, thumbnail: media/tram.jpg } -->
            ## Lake
            <!-- orbit
            image:
              src: media/lake.jpg
              motion: { src: media/lake-motion.mp4, still: 1.5 }
            -->
            Seen from ![the boat](media/boat.jpg).
            ## Fado <!-- orbit image: ./media/fado.jpg -->
        """.trimIndent()
        room.audience.send(AudienceCommand.Show(markdown, AppTheme(dark = false, tokens = emptyMap()), focus = "lisbon"))

        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val path = "/${room.code}/media"

        // Its pictures, a video with its poster, another with its thumbnail, a live photo's motion, a picture in a note.
        shown.forEach { name -> assertEquals(name, 200, browser.get("$path/$name").status) }
        kept.forEach { name -> assertEquals(name, 404, browser.get("$path/$name").status) }

        // A picture taken off the map as it's shown goes no more; one put on it, from its folder, goes.
        room.audience.send(AudienceCommand.Show(markdown.replace("media/beach.jpg", "media/photo.jpg"), AppTheme(dark = false, tokens = emptyMap()), focus = "lisbon"))
        assertEquals(404, browser.get("$path/beach.jpg").status)
        assertEquals(200, browser.get("$path/photo.jpg").status)

        // A YAML map's files, as Orbit reads them there.
        room.audience.send(AudienceCommand.Show("title: Lisbon\nnodes:\n  - text: Lisbon\n    image: media/IMG_1234.jpg\n", AppTheme(dark = false, tokens = emptyMap())))
        assertEquals(200, browser.get("$path/IMG_1234.jpg").status)
        assertEquals(404, browser.get("$path/photo.jpg").status)
    }

    @Test
    fun theMapsSoundsStayOnThePresentersPhoneThoughAVoiceMemoIsAnMp4() {
        val media = File(mapFolder, "media").apply { mkdirs() }
        listOf("memo-1.mp4", "sound-2.mp4", "talk.mp4", "clip.mp4", "song.mp3").forEach { File(media, it).writeText("0123456789".repeat(100)) }
        File(mapFolder, "map.md").writeText("# Map")
        val room = present()
        // Each of them pointed at, so it's as a sound that each stays: even an `.mp4` Orbit named a sound, shown as a video.
        val markdown = """
            # Lisbon
            ## Fado
            <!-- orbit audio: media/memo-1.mp4 -->
            ## The talk
            <!-- orbit
            sound:
              src: media/talk.mp4
              caption: Before the tram
            -->
            ## Tram
            <!-- orbit video: media/clip.mp4 -->
            ## Songs
            <!-- orbit video: media/sound-2.mp4 -->
            [The song](media/song.mp3)
        """.trimIndent()
        room.audience.send(AudienceCommand.Show(markdown, AppTheme(dark = false, tokens = emptyMap()), focus = "lisbon"))

        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val path = "/${room.code}/media"

        // The map's sounds play from the presenter's phone: a voice memo, an `.mp4` Orbit named a sound, or one the map plays as one.
        listOf("memo-1.mp4", "sound-2.mp4", "talk.mp4", "song.mp3").forEach { name ->
            assertEquals(name, 404, browser.get("$path/$name").status)
        }

        assertEquals(200, browser.get("$path/clip.mp4").status)
    }

    @Test
    fun onceThePresentationEndedNoneOfTheMapsPicturesOrVideosGo() {
        val media = File(mapFolder, "media").apply { mkdirs() }
        listOf("clip.mp4", "beach.jpg").forEach { File(media, it).writeText("0123456789".repeat(100)) }
        File(mapFolder, "map.md").writeText("# Map")
        val room = present()
        showMap(room)
        val browser = Browser(room.port)
        browser.get("/${room.code}/")
        val path = "/${room.code}/media"
        assertEquals(200, browser.get("$path/beach.jpg").status)

        // As when a private map locks with the phone's screen off: the room hears the end before Orbit stops listening.
        room.audience.send(AudienceCommand.End)

        listOf("clip.mp4", "beach.jpg").forEach { name ->
            assertEquals(name, 404, browser.get("$path/$name").status)
        }
    }
}
