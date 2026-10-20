package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.Words
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

    private fun showMap(room: BrowserPresenting) {
        room.audience.send(AudienceCommand.Show("# Lisbon\n## Tram\n", AppTheme(dark = false, tokens = emptyMap()), focus = "lisbon"))
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
    fun picturesAndVideosGoToThoseWhoJoinedVideosInTheirParts() {
        val media = File(mapFolder, "media").apply { mkdirs() }
        val clip = "0123456789".repeat(1_000)
        listOf("clip.mp4", "clip.m4v", "clip.webm", "clip.mov", "notes.txt", "page.html").forEach { File(media, it).writeText(clip) }
        File(media, "beach.jpg").writeBytes(ByteArray(64))
        File(mapFolder, "map.md").writeText("# Map")
        val room = present()
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
}
