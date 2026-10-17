package com.hyuchia.orbit.media

import com.hyuchia.orbit.page.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourcesTest {
    @Test
    fun `a YouTube video's id comes from any of its links`() {
        for (link in listOf(
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
            "https://www.youtube.com/watch?feature=share&v=aqz-KE-bpKQ&t=42",
            "https://m.youtube.com/watch?v=aqz-KE-bpKQ",
            "https://youtu.be/aqz-KE-bpKQ?si=abc",
            "youtu.be/aqz-KE-bpKQ",
            "https://www.youtube.com/embed/aqz-KE-bpKQ",
            "https://www.youtube-nocookie.com/embed/aqz-KE-bpKQ",
            "https://www.youtube.com/shorts/aqz-KE-bpKQ",
            "https://www.youtube.com/live/aqz-KE-bpKQ",
        )) {
            assertEquals(link, "aqz-KE-bpKQ", youtubeId(link))
        }
    }

    @Test
    fun `other links have no YouTube id`() {
        assertNull(youtubeId("https://vimeo.com/76979871"))
        assertNull(youtubeId("https://www.youtube.com/channel/UCxyz"))
        assertNull(youtubeId("https://notyoutube.com/watch?v=aqz-KE-bpKQ"))
        assertNull(youtubeId("https://www.youtube.com/watch?v=a"))
        assertNull(youtubeId("javascript:alert(1)//youtu.be/aqz-KE-bpKQ"))
        assertNull(youtubeId("media/video-abc.mp4"))
    }

    @Test
    fun `a video's poster is its own, or YouTube's for a YouTube video`() {
        assertEquals("media/video-abc.jpg", posterOf(Media("media/video-abc.mp4", provider = "native", thumbnail = "media/video-abc.jpg")))
        assertEquals("https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg", posterOf(Media("https://youtu.be/aqz-KE-bpKQ")))
        assertEquals("media/mine.jpg", posterOf(Media("https://youtu.be/aqz-KE-bpKQ", thumbnail = "media/mine.jpg")))
        assertNull(posterOf(Media("https://vimeo.com/76979871")))
        assertNull(posterOf(Media("media/video-abc.mp4", provider = "native")))
    }

    private val folder = java.io.File("/maps/trip")

    /** As the map's screen resolves addresses: files in the map's folder, web addresses as they are. */
    private fun resolve(src: String): Any? = when {
        src.contains("://") -> src
        src.startsWith("media/") -> java.io.File(folder, src)
        else -> null
    }

    private fun source(src: String, provider: String? = null) = videoSource(Media(src, provider = provider), ::resolve)

    @Test
    fun `a video in the map's folder plays from its file`() {
        assertEquals(VideoSource.InMap(java.io.File(folder, "media/video-abc.mp4")), source("media/video-abc.mp4", provider = "native"))
        // One that isn't in the map's folder has nothing to play.
        assertNull(source("../other/video.mp4"))
    }

    @Test
    fun `a video file on the web plays in the app at a secure address, and opens elsewhere otherwise`() {
        val webm = "https://upload.wikimedia.org/wikipedia/commons/8/87/Lisboa_Carris_%2840267618395%29.webm"
        assertEquals(VideoSource.OnTheWeb(webm), source(webm))
        assertEquals(VideoSource.OnTheWeb("https://example.com/stream"), source("https://example.com/stream", provider = "native"))
        assertEquals(VideoSource.Elsewhere("http://example.com/clip.mp4"), source("http://example.com/clip.mp4"))
        assertEquals(VideoSource.Elsewhere("https://example.com/watch/42"), source("https://example.com/watch/42"))
    }

    @Test
    fun `YouTube and Vimeo videos play in their own players, from where their links start`() {
        assertEquals(VideoSource.YouTube("aqz-KE-bpKQ"), source("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        assertEquals(VideoSource.YouTube("aqz-KE-bpKQ", start = 90), source("https://youtu.be/aqz-KE-bpKQ?t=90"))
        assertEquals(VideoSource.YouTube("aqz-KE-bpKQ", start = 3_725), source("https://www.youtube.com/watch?v=aqz-KE-bpKQ&t=1h2m5s"))
        assertEquals(VideoSource.Vimeo("76979871"), source("https://vimeo.com/76979871"))
        assertEquals(VideoSource.Vimeo("76979871", "8272103f6e"), source("https://vimeo.com/76979871/8272103f6e"))
        assertEquals(VideoSource.Vimeo("76979871", "8272103f6e"), source("https://player.vimeo.com/video/76979871?h=8272103f6e"))
        assertEquals(VideoSource.Vimeo("76979871"), source("https://vimeo.com/channels/staffpicks/76979871"))
        assertEquals(VideoSource.Vimeo("76979871"), source("vimeo.com/showcase/123/video/76979871"))
        // A Vimeo page that isn't a video's opens elsewhere.
        assertEquals(VideoSource.Elsewhere("https://vimeo.com/channels/staffpicks"), source("https://vimeo.com/channels/staffpicks"))
    }

    @Test
    fun `a video's provider says how to play it, whatever its address looks like`() {
        assertEquals(VideoSource.OnTheWeb("https://youtu.be/aqz-KE-bpKQ"), source("https://youtu.be/aqz-KE-bpKQ", provider = "native"))
        assertEquals(VideoSource.Elsewhere("https://vimeo.com/76979871"), source("https://vimeo.com/76979871", provider = "youtube"))
    }

    @Test
    fun `a video's page on its site opens in its app when the phone has it`() {
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", watchUrl(VideoSource.YouTube("aqz-KE-bpKQ")))
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ&t=90s", watchUrl(VideoSource.YouTube("aqz-KE-bpKQ", 90)))
        assertEquals("https://vimeo.com/76979871/8272103f6e", watchUrl(VideoSource.Vimeo("76979871", "8272103f6e")))
    }

    private val origin = playerOrigin("com.hyuchia.orbit")

    @Test
    fun `the players' page goes by the app's id, as YouTube asks of apps`() {
        assertEquals("https://com.hyuchia.orbit", origin)
    }

    @Test
    fun `YouTube plays in its privacy-enhanced player and Vimeo's is told not to track, both at once`() {
        assertEquals(
            "https://www.youtube-nocookie.com/embed/aqz-KE-bpKQ?autoplay=1&playsinline=1&rel=0&enablejsapi=1&origin=https%3A%2F%2Fcom.hyuchia.orbit&start=90",
            embedUrl(VideoSource.YouTube("aqz-KE-bpKQ", 90), origin),
        )
        assertEquals("https://player.vimeo.com/video/76979871?autoplay=1&dnt=1&h=8272103f6e", embedUrl(VideoSource.Vimeo("76979871", "8272103f6e"), origin))
        assertNull(embedUrl(VideoSource.OnTheWeb("https://example.com/clip.mp4"), origin))
    }

    @Test
    fun `the players' page frames only the player, and keeps the video's name as text`() {
        val page = playerPage(VideoSource.YouTube("aqz-KE-bpKQ"), "Bunny <b>&</b> \"friends\"", origin)!!
        assertTrue(page.contains("<iframe id=\"player\" src=\"https://www.youtube-nocookie.com/embed/aqz-KE-bpKQ?"))
        assertTrue(page.contains("frame-src https://www.youtube-nocookie.com;"))
        assertTrue(page.contains("default-src 'none'"))
        assertTrue(page.contains("title=\"Bunny &lt;b&gt;&amp;&lt;/b&gt; &quot;friends&quot;\""))
        assertTrue(page.contains("allowfullscreen"))
        assertTrue(page.contains("window.orbitPlayer"))
        assertTrue(page.contains("event: 'listening'"))
        val vimeo = playerPage(VideoSource.Vimeo("76979871"), "Office", origin)!!
        assertTrue(vimeo.contains("frame-src https://player.vimeo.com;"))
        assertTrue(vimeo.contains("const origin = 'https://player.vimeo.com';"))
        assertTrue(vimeo.contains("call('getPaused')"))
        assertTrue(vimeo.contains("window.orbitPlayer"))
        assertNull(playerPage(VideoSource.Elsewhere("https://example.com"), "Page", origin))
    }

    @Test
    fun `only the players' own sites load inside the player`() {
        assertTrue(isPlayerHost("www.youtube-nocookie.com"))
        assertTrue(isPlayerHost("player.vimeo.com"))
        assertTrue(isPlayerHost("WWW.YOUTUBE.COM"))
        assertTrue(!isPlayerHost("accounts.google.com"))
        assertTrue(!isPlayerHost("example.com"))
        assertTrue(!isPlayerHost(null))
    }
}
