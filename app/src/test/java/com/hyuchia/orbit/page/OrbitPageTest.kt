package com.hyuchia.orbit.page

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapTextEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `an answer the app can't read fails the job as the page's, for whoever asked to say so`() {
        val page = OrbitPage(context, maps)
        val sent = sayHello(page)
        var failure: Throwable? = null
        scope.launch { failure = runCatching { page.request(EditedMap.serializer()) { MapCommand.EditMap(it, "# Trip", "Voyage", null) } }.exceptionOrNull() }
        idle()
        val job = commands(sent).filterIsInstance<MapCommand.EditMap>().single().job

        // A map's edit answered with text where the app reads an object, as a page that disagrees with the app would.
        page.receive("""{"type":"result","job":$job,"value":"# Voyage"}""") {}
        idle()

        assertTrue("Failed with $failure", failure is PageException)
        assertEquals(context.getString(R.string.page_sent_unreadable_answer), failure?.message)
        assertFalse(page.busy)
        page.destroy()
    }

    /** A request for `url` from the page itself, as WebView asks for a picture it loads. */
    private fun asked(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = url.toUri()

        override fun isForMainFrame() = false

        override fun isRedirect() = false

        override fun hasGesture() = false

        override fun getMethod() = "GET"

        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    /** What `page` answers for `url`, as it would WebView: the file's status, or null when it isn't one of the maps'. */
    private fun answer(page: OrbitPage, url: String): Int? {
        val client = shadowOf(page.webView).webViewClient as WebViewClientCompat
        return client.shouldInterceptRequest(page.webView, asked(url))?.statusCode
    }

    /** Draws a picture of map `mapId` on `page`, as an export does, until whoever asked gives up; what went wrong goes to `failed`. */
    private fun picture(page: OrbitPage, mapId: String, failed: (Throwable) -> Unit = {}) = scope.launch {
        try {
            page.request(String.serializer()) { MapCommand.Snapshot(it, "svg", markdown = "# Trip\n![](media/alfama.jpg)\n", baseUrl = mapBaseUrl(mapId)) }
        } catch (failure: PageException) {
            failed(failure)
        }
    }

    private fun picture(map: String, name: String) = File(maps, "$map/media/$name").apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a picture's job reads its own map's pictures, at an address of its own, while it lasts`() {
        picture("trip", "alfama.jpg")
        picture("journal", "secret.jpg")
        val page = OrbitPage(context, maps)
        val sent = sayHello(page)
        val trip = picture(page, "trip")
        val journal = picture(page, "journal")
        idle()
        val (tripJob, journalJob) = commands(sent).filterIsInstance<MapCommand.Snapshot>()
        val tripBase = requireNotNull(tripJob.baseUrl)
        val journalBase = requireNotNull(journalJob.baseUrl)

        // Each job's address is its own, and says nothing of its map.
        assertTrue(tripBase, tripBase.startsWith("https://$PAGE_HOST/jobs/"))
        assertFalse(tripBase.contains("trip"))
        assertNotEquals(tripBase, journalBase)
        assertEquals(200, answer(page, "${tripBase}media/alfama.jpg"))
        assertEquals(404, answer(page, "${tripBase}media/secret.jpg"))
        assertEquals(200, answer(page, "${journalBase}media/secret.jpg"))
        // A map's own address gives none of either, however a map's text points at it: neither map is on the page.
        assertEquals(404, answer(page, "https://$PAGE_HOST/maps/journal/media/secret.jpg"))
        assertEquals(404, answer(page, "https://$PAGE_HOST/maps/trip/media/alfama.jpg"))
        // Nor does a key no job was given.
        assertEquals(404, answer(page, "https://$PAGE_HOST/jobs/0123456789abcdef0123456789abcdef/media/secret.jpg"))

        // Answered, or given up on, a job reads nothing more.
        page.receive("""{"type":"result","job":${tripJob.job},"value":"<svg/>"}""") {}
        journal.cancel()
        idle()

        assertEquals(404, answer(page, "${tripBase}media/alfama.jpg"))
        assertEquals(404, answer(page, "${journalBase}media/secret.jpg"))
        assertFalse(trip.isActive)
        page.destroy()
    }

    @Test
    fun `ideas caught on the go read their map's pictures as a picture's job does, at an address of their own, while it lasts`() {
        picture("trip", "alfama.jpg")
        val page = OrbitPage(context, maps)
        val sent = sayHello(page)

        val adding = scope.launch {
            page.request(String.serializer()) { MapCommand.AddIdeas(it, "# Trip\n![](media/alfama.jpg)\n", "Call the hotel", baseUrl = mapBaseUrl("trip")) }
        }

        idle()
        val job = commands(sent).filterIsInstance<MapCommand.AddIdeas>().single()
        val base = requireNotNull(job.baseUrl)

        // The map drawn out of sight asks for its pictures at the job's own address, not at the map's.
        assertTrue(base, base.startsWith("https://$PAGE_HOST/jobs/"))
        assertEquals(200, answer(page, "${base}media/alfama.jpg"))
        assertEquals(404, answer(page, "https://$PAGE_HOST/maps/trip/media/alfama.jpg"))

        page.receive("""{"type":"result","job":${job.job},"value":"# Trip\n- Call the hotel\n"}""") {}
        idle()

        assertEquals(404, answer(page, "${base}media/alfama.jpg"))
        assertFalse(adding.isActive)
        page.destroy()
    }

    @Test
    fun `a private map that locks calls off the pictures being drawn of it, on every page`() {
        picture("trip", "alfama.jpg")
        picture("journal", "secret.jpg")
        val pool = PagePool(context, maps)
        var failure: Throwable? = null
        var sent = emptyList<String>()
        var journalBase = ""
        var tripBase = ""
        var spare: OrbitPage? = null

        scope.launch {
            pool.work { page ->
                spare = page
                sent = sayHello(page)
                picture(page, "journal") { failure = it }
                picture(page, "trip")
            }
        }

        idle()

        commands(sent).filterIsInstance<MapCommand.Snapshot>().let { (journal, trip) ->
            journalBase = journal.baseUrl!!
            tripBase = trip.baseUrl!!
        }

        pool.revokeJobs { it == "journal" }
        idle()

        val page = requireNotNull(spare)
        assertEquals(context.getString(R.string.page_stopped), failure?.message)
        assertEquals(404, answer(page, "${journalBase}media/secret.jpg"))
        assertTrue(commands(sent).any { it is MapCommand.Cancel })
        // The other map's picture goes on.
        assertEquals(200, answer(page, "${tripBase}media/alfama.jpg"))
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
        // No page loads for a while, as a tag renamed in every map knows: it doesn't wait on the page for each map.
        assertEquals(PageException.Kind.DidNotLoad, (failure as? PageException)?.kind)
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

    /** Orbit in sight, or not, as Android says it: the page's watchdogs count only the time it is. */
    private val sight = LifecycleRegistry.createUnsafe(object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
    }).also { it.currentState = Lifecycle.State.STARTED }

    /** A page whose watchdogs follow [sight]. */
    private fun watchedPage() = OrbitPage(context, maps, sight = sight)

    /** WebView says the page itself loaded, its script with it. */
    private fun finishLoading(page: OrbitPage) {
        val client = shadowOf(page.webView).webViewClient as WebViewClientCompat
        client.onPageFinished(page.webView, "https://$PAGE_HOST/assets/map/index.html")
    }

    /** Lets the main thread's clock run on by `seconds`, doing what's due meanwhile. */
    private fun pass(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    /** A map the screen sends the page to draw. */
    private val trip = MapCommand.Load(markdown = "# Trip", theme = AppTheme(dark = false, tokens = emptyMap()), settings = MapSettings(haptics = false, snap = false, longPressDelay = 400), mapId = "trip")

    @Test
    fun `a page that loads but never says hello fails what waits for it, and says it didn't load`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        var failure: Throwable? = null
        scope.launch { failure = runCatching { page.request(String.serializer()) { MapCommand.Convert(it, "# Trip", "yaml") } }.exceptionOrNull() }
        idle()

        // Its script broke before it said hello: the page loaded all the same.
        finishLoading(page)
        pass(14)

        assertTrue(page.isAlive)
        assertEquals(null, failure)

        pass(2)

        assertEquals(listOf(MapMessage.DidNotLoad), heard.filter { it == MapMessage.DidNotLoad })
        assertFalse(page.isAlive)
        assertEquals(context.getString(R.string.page_did_not_load), failure?.message)
        page.destroy()
    }

    @Test
    fun `a page that never finishes loading is given up on after a minute and a half`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }

        pass(89)

        assertTrue(page.isAlive)

        pass(2)

        assertTrue(MapMessage.DidNotLoad in heard)
        assertFalse(page.isAlive)
        page.destroy()
    }

    @Test
    fun `a page that says hello in time is never given up on for it`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        finishLoading(page)
        sayHello(page)

        pass(200)

        assertTrue(page.isAlive)
        assertFalse(MapMessage.DidNotLoad in heard)
        page.destroy()
    }

    @Test
    fun `a map the page never says is ready is given up on, as a page that didn't load, with the way to load it again`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        page.send(trip)
        // The map waited for the hello: its time starts as it reaches the page.
        pass(60)
        sayHello(page)
        pass(89)

        assertTrue(page.isAlive)

        pass(2)

        assertTrue(MapMessage.DidNotLoad in heard)
        assertFalse(page.isAlive)
        page.destroy()
    }

    @Test
    fun `a map the page says is ready, or can't draw, or that's unloaded, is waited for no more`() {
        val ready = watchedPage()
        sayHello(ready)
        ready.send(trip)
        pass(30)
        ready.receive("""{"type":"ready","map":"trip","state":{"title":"Trip","nodes":[]}}""") {}

        val failed = watchedPage()
        sayHello(failed)
        failed.send(trip)
        failed.receive("""{"type":"error","map":"trip","message":"Orbit couldn't read it"}""") {}

        val unloaded = watchedPage()
        sayHello(unloaded)
        unloaded.send(trip)
        unloaded.send(MapCommand.Unload)

        pass(200)

        assertTrue(ready.isAlive)
        assertTrue(failed.isAlive)
        assertTrue(unloaded.isAlive)
        listOf(ready, failed, unloaded).forEach { it.destroy() }
    }

    @Test
    fun `a page destroyed before it started is left alone by what waited for it`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        finishLoading(page)
        page.send(trip)

        page.destroy()
        pass(200)

        assertEquals(emptyList<MapMessage>(), heard)
        assertTrue(shadowOf(page.webView).wasDestroyCalled())
    }

    @Test
    fun `a spare page that failed a job is let go, so another try has a new page, once its other work is done`() {
        val pool = PagePool(context, maps)
        var spare: OrbitPage? = null
        // A thumbnail the spare is drawing meanwhile.
        val working = busy(pool) { spare = it }
        idle()
        val page = requireNotNull(spare)

        pool.retire(page)

        assertTrue(page.isAlive)
        assertNotSame(page, pool.take().also { it.destroy() })

        working.cancel()
        idle()

        assertFalse(page.isAlive)
        assertTrue(shadowOf(page.webView).wasDestroyCalled())
    }

    @Test
    fun `a page given up on says nothing more, so a map it says is ready after all reaches no one`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        sayHello(page)
        heard.clear()
        page.send(trip)
        pass(91)

        assertEquals(listOf(MapMessage.DidNotLoad), heard)

        // A big map on a slow WebView, ready at last, and a save: the screen has given the page up, and hears neither.
        page.receive("""{"type":"ready","map":"trip","state":{"title":"Trip","nodes":[]}}""") {}
        page.receive("""{"type":"saved","map":"trip","kind":"add","markdown":"# Trip\n- Lisbon","state":{"title":"Trip","nodes":[]}}""") {}

        assertEquals(listOf(MapMessage.DidNotLoad), heard)
        assertFalse(page.isAlive)
        page.destroy()
    }

    @Test
    fun `time Orbit spends out of sight doesn't count against a page, as Android may freeze it there`() {
        val page = watchedPage()
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        pass(30)

        // Orbit leaves sight, and its page may be frozen with it for as long as it's away.
        sight.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        pass(600)

        assertTrue(page.isAlive)

        // Back in sight, the page has what it had left: a minute, not none.
        sight.handleLifecycleEvent(Lifecycle.Event.ON_START)
        pass(58)

        assertTrue(page.isAlive)
        assertEquals(emptyList<MapMessage>(), heard)

        pass(3)

        assertEquals(listOf(MapMessage.DidNotLoad), heard)
        page.destroy()
    }

    @Test
    fun `a map's change the spare page refuses fails as that change's alone, and lets the page go for a new one`() {
        val pool = PagePool(context, maps)
        var spare: OrbitPage? = null
        scope.launch { pool.work { spare = it } }
        idle()
        val page = requireNotNull(spare)
        val sent = sayHello(page)
        var failure: Throwable? = null
        scope.launch { failure = runCatching { MapTextEditor.onPages(pool).edit("# Trip", "Voyage", null) }.exceptionOrNull() }
        idle()
        val job = commands(sent).filterIsInstance<MapCommand.EditMap>().single().job

        // Orbit refuses this map's change, in its own words.
        page.receive("""{"type":"result","job":$job,"error":"Nothing to rename"}""") {}
        idle()

        val refused = requireNotNull(failure as? PageException) { "Failed with $failure" }
        assertEquals(PageException.Kind.Refused, refused.kind)
        assertFalse(refused.kind.lasting)
        assertTrue(shadowOf(page.webView).wasDestroyCalled())

        // Another try has a page of its own.
        var next: OrbitPage? = null
        scope.launch { pool.work { next = it } }
        idle()

        val other = requireNotNull(next)

        assertNotSame(page, other)
        other.destroy()
    }
}
