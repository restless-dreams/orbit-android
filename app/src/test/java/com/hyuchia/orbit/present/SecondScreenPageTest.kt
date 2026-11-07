package com.hyuchia.orbit.present

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.nio.file.Files

/**
 * The audience's page on a second screen, as the app drives it: what it's
 * sent waits for its hello and goes in order, it plays the map's videos from
 * the phone only while that map is presented, and what it says of a video
 * reaches the presenter, as `ScreenAudience` hears it. The page's own side is
 * web/second-screen.test.ts. Robolectric's WebView never loads the page, so
 * the test says what the page would.
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
