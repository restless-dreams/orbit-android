package com.hyuchia.orbit.page

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import com.hyuchia.orbit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.nio.file.Files
import java.time.Duration

/**
 * Orbit's pages, each a WebView, as the screens and the pool use them, on
 * Robolectric's WebView: what happens when a page doesn't load, to a job
 * no one waits for any more, and to the spare page as Orbit leaves the
 * screen. The views here never load the page itself, so the test says what
 * WebView, or the page, would.
 */
@RunWith(AndroidJUnit4::class)
class OrbitPageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val maps: File = Files.createTempDirectory("orbit-pages").toFile()

    /** Where the test's jobs run: the main thread, as the app's do. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun cleanUp() {
        scope.cancel()
        idle()
        maps.deleteRecursively()
    }

    /** Lets the main thread do what's waiting on it. */
    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The page itself, as WebView asks for it. */
    private val mainFrame = object : WebResourceRequest {
        override fun getUrl(): Uri = "https://$PAGE_HOST/assets/map/index.html".toUri()

        override fun isForMainFrame() = true

        override fun isRedirect() = false

        override fun hasGesture() = false

        override fun getMethod() = "GET"

        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private val unreachable = object : WebResourceErrorCompat() {
        override fun getErrorCode() = WebViewClient.ERROR_UNKNOWN

        override fun getDebugCode() = 0

        override fun getDescription(): CharSequence = "net::ERR_FAILED"
    }

    /** WebView says `page` didn't load. */
    private fun failToLoad(page: OrbitPage) {
        val client = shadowOf(page.webView).webViewClient as WebViewClientCompat
        client.onReceivedError(page.webView, mainFrame, unreachable)
    }

    /** The page says hello on its channel: what it's sent from then on, in order, goes into the list returned. */
    private fun sayHello(page: OrbitPage): List<String> {
        val sent = mutableListOf<String>()
        page.receive("""{"type":"hello"}""") { sent += it }
        return sent
    }

    /** The map's commands among what the page was sent: its words, a command of their own, left out. */
    private fun commands(sent: List<String>): List<MapCommand> =
        sent.mapNotNull { runCatching { protocolJson.decodeFromString(MapCommand.serializer(), it) }.getOrNull() }

    /** Asks `page` to convert a map, as a screen would, until whoever asked gives up, or the page takes too long. */
    private fun convert(page: OrbitPage) = scope.launch {
        try {
            page.request(String.serializer()) { MapCommand.Convert(it, "# Trip", "yaml") }
        } catch (late: PageException) {
            // The page didn't answer in time, as a test means it not to: what it was sent is what's looked at.
        }
    }

    @Test
    fun `a job whoever asked gives up on is called off on the page`() {
        val page = OrbitPage(context, maps)
        val sent = sayHello(page)
        val asking = convert(page)
        idle()
        val job = commands(sent).filterIsInstance<MapCommand.Convert>().single().job

        asking.cancel()
        idle()

        assertEquals(MapCommand.Cancel(job), commands(sent).last())
        assertFalse(page.busy)
        page.destroy()
    }

    @Test
    fun `a job the page doesn't answer in time is called off on it too`() {
        val page = OrbitPage(context, maps)
        val sent = sayHello(page)
        convert(page)
        idle()
        val job = commands(sent).filterIsInstance<MapCommand.Convert>().single().job

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))

        assertEquals(MapCommand.Cancel(job), commands(sent).last())
        page.destroy()
    }

    @Test
    fun `a job the page answered isn't called off`() {
        val page = OrbitPage(context, maps)
        val sent = sayHello(page)
        convert(page)
        idle()
        val job = commands(sent).filterIsInstance<MapCommand.Convert>().single().job

        page.receive("""{"type":"result","job":$job,"value":"title: Trip"}""") {}
        idle()

        assertTrue(commands(sent).none { it is MapCommand.Cancel })
        assertFalse(page.busy)
        page.destroy()
    }

    @Test
    fun `a job given up on before the page said hello is never sent, nor called off`() {
        val page = OrbitPage(context, maps)
        val asking = convert(page)
        idle()
        asking.cancel()
        idle()

        val sent = sayHello(page)

        assertEquals(emptyList<MapCommand>(), commands(sent))
        page.destroy()
    }

    /** Work on the pool's spare page that waits for its answer, as a thumbnail's does; the spare goes to `on`. */
    private fun busy(pool: PagePool, on: (OrbitPage) -> Unit) = scope.launch {
        pool.work { page ->
            on(page)
            page.request(String.serializer()) { MapCommand.Convert(it, "# Trip", "yaml") }
        }
    }

    @Test
    fun `a spare page let go of while it worked goes once its last job ends`() {
        val pool = PagePool(context, maps)
        var spare: OrbitPage? = null
        val working = busy(pool) { spare = it }
        idle()

        // Orbit leaves the screen while the spare draws a thumbnail: it stays until that's done.
        pool.release()
        val page = requireNotNull(spare)

        assertTrue(page.isAlive)

        working.cancel()
        idle()

        assertFalse(page.isAlive)
        assertTrue(shadowOf(page.webView).wasDestroyCalled())
    }

    @Test
    fun `a spare page let go of while it worked stays when Orbit is back before its job ends`() {
        val pool = PagePool(context, maps)
        var spare: OrbitPage? = null
        val working = busy(pool) { spare = it }
        idle()
        pool.release()
        pool.warm()

        working.cancel()
        idle()
        val page = requireNotNull(spare)

        assertTrue(page.isAlive)
        // The next map draws on it at once.
        assertSame(page, pool.take())
        page.destroy()
    }

    @Test
    fun `a page that doesn't load tells the screen showing it, and fails what's asked of it at once`() {
        val page = OrbitPage(context, maps)
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }

        failToLoad(page)

        assertTrue("Heard: $heard", MapMessage.DidNotLoad in heard)
        assertFalse(page.isAlive)

        var failure: Throwable? = null
        scope.launch { failure = runCatching { page.request(String.serializer()) { MapCommand.Convert(it, "# Trip", "yaml") } }.exceptionOrNull() }
        idle()

        assertEquals(context.getString(R.string.page_did_not_load), failure?.message)
        page.destroy()
    }

    @Test
    fun `a map never gets a page that didn't load, as the pool lets it go and makes another`() {
        val pool = PagePool(context, maps)
        val page = pool.take()
        // Given back, it waits as the spare for the next map.
        pool.give(page)

        failToLoad(page)
        val next = pool.take()

        assertNotSame(page, next)
        assertTrue(next.isAlive)
        assertTrue(shadowOf(page.webView).wasDestroyCalled())
        next.destroy()
    }

    @Test
    fun `a page that didn't load, given back by its map, is let go rather than kept as the spare`() {
        val pool = PagePool(context, maps)
        val page = pool.take()
        failToLoad(page)

        pool.give(page)
        val next = pool.take()

        assertTrue(shadowOf(page.webView).wasDestroyCalled())
        assertNotSame(page, next)
        assertTrue(next.isAlive)
        next.destroy()
    }
}
