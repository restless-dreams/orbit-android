package com.hyuchia.orbit.present

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AUDIENCE_URL
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.OrbitPage
import com.hyuchia.orbit.page.mapBaseUrl
import com.hyuchia.orbit.page.protocolJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplayManager
import java.io.File
import java.nio.file.Files

/**
 * The audience's page on a second screen, as the app drives it: what it's
 * sent waits for its hello and goes in order, it plays the map's videos from
 * the phone only while that map is presented, what it says of a video
 * reaches the presenter, as `ScreenAudience` hears it, and a page that
 * stops or doesn't load is said, and loaded again. The page's own side is
 * web/second-screen.test.ts. Robolectric's WebView never loads the page, so
 * the test says what the page, or WebView, would.
 */
@RunWith(AndroidJUnit4::class)
class SecondScreenPageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val maps: File = Files.createTempDirectory("orbit-second-screen").toFile()

    @After
    fun cleanUp() {
        maps.deleteRecursively()
    }

    private val show = AudienceCommand.Show("# Talk\n## Tram 28", AppTheme(dark = false, tokens = emptyMap()), baseUrl = mapBaseUrl("talk"), focus = "talk")

    /** The kinds of commands among what the page was sent, in order. */
    private fun kinds(sent: List<String>): List<String> = sent.map { (protocolJson.parseToJsonElement(it).jsonObject["type"] as JsonPrimitive).content }

    /** What the page gets when it asks for the map's video, as its `<video>` does. */
    private fun video(page: OrbitPage): Int {
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = "${mapBaseUrl("talk")}media/tram.mp4".toUri()

            override fun isForMainFrame() = false

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

        val client = shadowOf(page.webView).webViewClient as WebViewClient
        return client.shouldInterceptRequest(page.webView, request)!!.statusCode
    }

    @Test
    fun `what the app sends waits for the page's hello, then goes in order`() {
        val page = OrbitPage(context, maps, AUDIENCE_URL)
        page.send(show)
        page.send(AudienceCommand.Step("tram", 1, 2))
        page.send(AudienceCommand.Media("tram", MediaKind.Video, playing = true, at = 4.0))

        val sent = mutableListOf<String>()
        page.receive("""{"type":"hello"}""") { sent += it }
        page.send(AudienceCommand.Playback(false))

        assertEquals(listOf("language", "show", "step", "media", "playback"), kinds(sent))
        page.destroy()
    }

    @Test
    fun `the page plays the map's videos from the phone only while that map is presented`() {
        File(maps, "talk/media").mkdirs()
        File(maps, "talk/media/tram.mp4").writeBytes(ByteArray(64))
        val page = OrbitPage(context, maps, AUDIENCE_URL)

        assertEquals(404, video(page))
        page.send(show)
        assertEquals(200, video(page))
        page.send(AudienceCommand.End)
        assertEquals(404, video(page))
        page.destroy()
    }

    /** Android stops the process `page` runs in, as it does to free memory: every page of the app's goes with it. */
    private fun stop(page: OrbitPage) {
        // Only WebView makes these: its constructor is there for tests like this one.
        @Suppress("DEPRECATION")
        val gone = object : RenderProcessGoneDetail() {
            override fun didCrash() = false

            override fun rendererPriorityAtExit() = 0
        }

        (shadowOf(page.webView).webViewClient as WebViewClient).onRenderProcessGone(page.webView, gone)
    }

    /** A second screen beside the phone, as one on HDMI, whose pages go into `pages` as they're made. */
    private fun secondScreen(pages: MutableList<OrbitPage>): ScreenAudience {
        val id = ShadowDisplayManager.addDisplay("w1920dp-h1080dp")
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(id)
        return ScreenAudience(context, display) { screenContext -> OrbitPage(screenContext, maps, AUDIENCE_URL).also(pages::add) }
    }

    @Test
    fun `a second screen whose page stops says so, and loading it again puts a new page there for the presentation`() {
        val pages = mutableListOf<OrbitPage>()
        val screen = secondScreen(pages)
        val heard = mutableListOf<MapMessage.MediaState>()
        screen.listen { heard += it }
        screen.send(show)
        val first = pages.single()
        first.receive("""{"type":"hello"}""") {}
        assertNull(screen.trouble.value)

        stop(first)
        assertEquals(ScreenTrouble.Stopped, screen.trouble.value)

        screen.loadAgain()
        assertNull(screen.trouble.value)
        val second = pages.last()
        assertNotSame(first, second)
        assertFalse(first.isAlive)

        // The presentation catches the new page up, and it says how its video goes, as the first one did.
        screen.send(show)
        screen.send(AudienceCommand.Step("tram-28", 1, 2))
        val sent = mutableListOf<String>()
        second.receive("""{"type":"hello"}""") { sent += it }
        assertEquals(listOf("language", "show", "step"), kinds(sent))
        second.receive("""{"type":"mediaState","id":"tram","playing":true,"time":3.5,"duration":190.2,"ended":false,"muted":false}""") {}
        assertEquals(listOf(MapMessage.MediaState("tram", playing = true, time = 3.5, duration = 190.2)), heard)
        screen.close()
    }

    @Test
    fun `a second screen whose page doesn't load says so too`() {
        val pages = mutableListOf<OrbitPage>()
        val screen = secondScreen(pages)
        val page = pages.single()

        val mainFrame = object : WebResourceRequest {
            override fun getUrl(): Uri = AUDIENCE_URL.toUri()

            override fun isForMainFrame() = true

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

        val unreachable = object : WebResourceErrorCompat() {
            override fun getErrorCode() = WebViewClient.ERROR_UNKNOWN

            override fun getDebugCode() = 0

            override fun getDescription(): CharSequence = "net::ERR_FAILED"
        }

        (shadowOf(page.webView).webViewClient as WebViewClientCompat).onReceivedError(page.webView, mainFrame, unreachable)
        assertEquals(ScreenTrouble.DidNotLoad, screen.trouble.value)

        screen.loadAgain()
        assertNull(screen.trouble.value)
        assertEquals(2, pages.size)
        screen.close()
    }

    @Test
    fun `what the page says of its video reaches the presenter`() {
        val page = OrbitPage(context, maps, AUDIENCE_URL)
        val heard = mutableListOf<MapMessage>()
        page.listener = { heard += it }
        page.receive("""{"type":"hello"}""") {}

        page.receive("""{"type":"mediaState","id":"tram","playing":true,"time":12.75,"duration":190.2,"ended":false,"muted":true}""") {}
        page.receive("""{"type":"mediaState","id":"tram","playing":false,"time":190.2,"duration":190.2,"ended":true,"muted":true}""") {}

        assertEquals(
            listOf(
                MapMessage.Hello,
                MapMessage.MediaState("tram", playing = true, time = 12.75, duration = 190.2, muted = true),
                MapMessage.MediaState("tram", playing = false, time = 190.2, duration = 190.2, ended = true, muted = true),
            ),
            heard,
        )
        page.destroy()
    }
}
