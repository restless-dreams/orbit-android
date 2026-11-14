package com.hyuchia.orbit.nearby

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.data.TestMedia
import com.hyuchia.orbit.page.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * A map open on a computer, as the send sheet's computer share shows it:
 * the real page, written from the app's own assets into a file and sent
 * from it, and its zip, over the loopback.
 */
@RunWith(AndroidJUnit4::class)
class SharedMapPageTest {
    private val english = FileWords(Locale.US)
    private val files: File = Files.createTempDirectory("orbit-shared-page").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The app's own assets, in folders of the test's own, and no system services: no advertisement. */
    private val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getCacheDir(): File = File(files, "cache").apply { mkdirs() }

        override fun getFilesDir(): File = File(files, "files").apply { mkdirs() }

        override fun getSystemService(name: String): Any? = null
    }

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome() = StarterMap("# Welcome\n")
    }

    private val maps = MapRepository(File(files, "maps"), File(files, "trash"), starters, english)
    private lateinit var host: NearbyHost

    @Before
    fun start() = runBlocking<Unit> {
        maps.load()
        host = NearbyHost(context, maps, scope, anyone, english, listenOn = { null })
        // It clears what an earlier launch left in its folders as it starts: that's done before anything is asked of it.
        scope.coroutineContext.job.children.toList().joinAll()
        host.start(accepting = false)
    }

    @After
    fun stop() {
        host.stop()
        scope.cancel()
        files.deleteRecursively()
    }

    /** The share's zip as `browser` downloads it: the answer's head, and the zip. */
    private fun download(browser: Browser, code: String): Pair<String, ByteArray> = Socket("127.0.0.1", host.port).use { socket ->
        val cookies = browser.cookieHeaders.joinToString("; ")
        socket.getOutputStream().write("GET /$code/map.zip HTTP/1.1\r\nHost: 127.0.0.1:${host.port}\r\nCookie: $cookies\r\n\r\n".toByteArray())
        val answer = socket.getInputStream().readBytes()
        val end = String(answer, Charsets.ISO_8859_1).indexOf("\r\n\r\n")
        String(answer, 0, end, Charsets.ISO_8859_1) to answer.copyOfRange(end + 4, answer.size)
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> =
        ZipInputStream(zip.inputStream()).use { input -> generateSequence { input.nextEntry }.associate { it.name to input.readBytes() } }

    @Test
    fun `a computer opening a shared map that asks nothing is in at once, for its zip and as it loads the page again`() = runBlocking<Unit> {
        val id = maps.add("# Trip\n\n- Lisbon\n\n  ![Tram](media/photo.jpg)\n", pictures = mapOf("photo.jpg" to TestMedia.picture(600, 400, noise = 1)))
        val share = host.share(id, AppTheme(dark = false, tokens = emptyMap()))
        val browser = Browser(host.port)

        // The page comes from the file it's made in: the session comes with it.
        val page = browser.get("/${share.code}/")
        assertEquals(200, page.status)
        assertTrue(page.body.contains("Trip"))
        assertNotNull(page.header("set-cookie"))
        assertEquals(listOf("Guest 1"), share.joining.members.value.map { it.name })

        // The page's Download button, and the page loaded again: the same browser, in the room once.
        val (head, zip) = download(browser, share.code)
        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(entries(zip).getValue("map.md").decodeToString().startsWith("# Trip"))
        assertEquals(200, browser.get("/${share.code}/").status)
        assertEquals(listOf("Guest 1"), share.joining.members.value.map { it.name })
    }
}
