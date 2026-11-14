package com.hyuchia.orbit.present

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.nearby.Browser
import com.hyuchia.orbit.nearby.BrowserPresenting
import com.hyuchia.orbit.nearby.JoiningOptions
import com.hyuchia.orbit.nearby.anyone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.Locale

/**
 * The presentation's room in browsers, as the map's view model keeps it:
 * the presentation's screen made again (for a new language or text size)
 * asks for the room as it starts, and finds it still there, with its code
 * and who's in it; stopping it tells the presentation, and stops listening.
 */
class BrowserRoomTest {
    private val english = FileWords(Locale.US)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mapFolder: File = Files.createTempDirectory("orbit-room").toFile()

    /** What the presentation was handed as its browsers' audience, in order. */
    private val audiences = mutableListOf<Audience?>()

    private val room = BrowserRoom { audiences += it }
    private var made = 0

    /** A room served over the loopback from the app's own pages, as the presentation's screen makes one. */
    private fun presenting(): BrowserPresenting {
        made += 1
        return BrowserPresenting({ name -> File("src/main/assets/map/$name").readText() }, english, mapFolder, scope, JoiningOptions(askNames = false), preferredPort = 0, policy = anyone, listenOn = { null })
    }

    @After
    fun stop() {
        room.stop()
        scope.cancel()
        mapFolder.deleteRecursively()
    }

    @Test
    fun `a screen made again finds the room still there, with its code and who's in it`() {
        room.start(::presenting)
        val first = room.presenting.value!!
        val browser = Browser(first.port)
        assertEquals(200, browser.get("/${first.code}/").status)

        // The presentation's screen is made again, and shows the presentation in browsers as it was.
        room.start(::presenting)

        assertSame(first, room.presenting.value)
        assertEquals(1, made)
        assertEquals(listOf<Audience?>(first.audience), audiences)
        assertEquals(1, first.joining.members.value.size)
        assertEquals("""{"state":"joined"}""", browser.get("/${first.code}/status").body)
    }

    @Test
    fun `stopping the room tells the presentation, and stops listening for browsers`() {
        room.start(::presenting)
        val first = room.presenting.value!!
        val port = first.port

        room.stop()

        assertNull(room.presenting.value)
        assertEquals(listOf(first.audience, null), audiences)
        assertTrue(runCatching { Socket("127.0.0.1", port).close() }.isFailure)
    }
}
