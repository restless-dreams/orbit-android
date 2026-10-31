package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a map names the files in its media folder, and points at them. */
class MediaNamesTest {
    @Test
    fun `pictures are found by their place in the media folder`() {
        val markdown = "# Trip\n- Beach ![](media/picture-abc.jpg)\n- <!-- orbit image: media/map_2.png -->\n- media/../secret"
        assertEquals(setOf("picture-abc.jpg", "map_2.png"), MediaNames.references(markdown).toSet())
    }

    @Test
    fun `sounds are found there too, so they go to the folder and come back from it`() {
        val markdown = "# Trip\n## Beach <!-- orbit audio: media/memo-mf2k1x.mp4 -->\n## Song\n<!-- orbit\naudio:\n  src: media/sound-mf2k1x.mp3\n  caption: Waves\n-->"
        assertEquals(setOf("memo-mf2k1x.mp4", "sound-mf2k1x.mp3"), MediaNames.references(markdown).toSet())
        assertTrue(MediaNames.references(markdown).all(::isMediaName))
    }

    @Test
    fun `a map's text says which pictures and videos it points to`() {
        assertEquals(
            listOf("picture-1.jpg", "video-2.mp4", "poster-2.jpg"),
            MediaNames.references("![](media/picture-1.jpg) <!-- orbit video: media/video-2.mp4, poster: media/poster-2.jpg --> ![](media/picture-1.jpg) multimedia/x.jpg"),
        )
    }

    @Test
    fun `a web address's media folder, or another folder ending in media, isn't the map's own`() {
        val markdown = "![](https://example.com/media/photo.jpg) see multimedia/x.jpg, my-media/y.jpg and ![](./media/z.jpg)"
        assertEquals(listOf("z.jpg"), MediaNames.references(markdown))
    }

    @Test
    fun `text in a web address is never one of the map's own files`() {
        val markdown = """
            # Trip
            - Search [the results](https://example.com/?q=media/photo.jpg)
            - See https://example.com/#media/beach.jpg, or <https://example.com/a=media/sea.jpg>
            - [The film](https://en.wikipedia.org/wiki/Orbit_(media/film.jpg))
            - Write to mailto:me@example.com?subject=media/note.jpg <!-- orbit link: "https://example.com/(media/link.jpg)" -->
            - www.example.com/?media/www.jpg and //example.com/(media/relative.jpg)
            - Here ![](media/here.jpg)
        """.trimIndent()
        assertEquals(listOf("here.jpg"), MediaNames.references(markdown))
    }

    @Test(timeout = 10_000)
    fun `a long text is read in one pass, its web addresses too`() {
        val word = "a".repeat(2_000_000)
        val addresses = (1..20_000).joinToString(" ") { "https://example.com/(media/search-$it.jpg) ![](media/picture-$it.jpg)" }
        assertEquals(listOf("photo.jpg"), MediaNames.references("$word ![](media/photo.jpg) $word: https://example.com/?q=media/other.jpg"))
        assertEquals(20_000, MediaNames.references(addresses).size)
    }

    @Test
    fun `media written where no address starts isn't one of the map's own files`() {
        // Code, a node's link, a query, a list or a mapping's key: Orbit reads none of them as a file of the map's.
        val markdown = "# Trip\n- `media/code.jpg` [Up](#media/node) [Ask](?media/query.jpg)\n- <!-- orbit tags: [media/tag.jpg], other: {media/key.jpg} -->\n- a=media/equals.jpg"
        assertEquals(emptyList<String>(), MediaNames.references(markdown))
    }

    @Test
    fun `every place Orbit reads one of the map's files from is found`() {
        val markdown = """
            # Trip
            ## Beach
            ![The beach](media/beach.jpg)
            ## Trail
            [![Its poster](media/trail-poster.jpg)](media/trail.mp4)
            ## Summit <!-- orbit image: media/summit.jpg -->
            ## Lake
            <!-- orbit
            image:
              src: "media/lake.jpg"
              motion:
                src: './media/lake-motion.mp4'
                still: 1.5
            video: { src: media/lake-video.mp4, thumbnail: media/lake-poster.jpg }
            audio: {"src":media/lake-memo.mp4}
            -->
            Notes with ![a picture](media/note.jpg) and [a guide]( media/guide.pdf "The guide").
            ### [Its report](media/report.pdf)
        """.trimIndent()
        val names = listOf(
            "beach.jpg", "trail-poster.jpg", "trail.mp4", "summit.jpg", "lake.jpg", "lake-motion.mp4", "lake-video.mp4", "lake-poster.jpg",
            "lake-memo.mp4", "note.jpg", "guide.pdf", "report.pdf",
        )
        assertEquals(names, MediaNames.references(markdown))

        val yaml = "title: Trip\nnodes:\n  - text: Trip\n    image: media/trip.jpg\n    children:\n      - text: Talk\n        audio:\n          src: media/talk.mp4\n" +
            "        note: \"See ![it](media/note.jpg)\"\n      - text: Walk\n        video: \"media/walk.mp4\"\n        link: 'media/walk.pdf'\n"
        assertEquals(listOf("trip.jpg", "talk.mp4", "note.jpg", "walk.mp4", "walk.pdf"), MediaNames.references(yaml))
    }

    @Test
    fun `a renamed picture is renamed where the map points at it, and nowhere else`() {
        val markdown = "![](media/photo.jpg) ![](media/photo.jpg.old) ![](https://example.com/media/photo.jpg) ![](./media/photo.jpg) [Search](https://example.com/?q=media/photo.jpg)"
        assertEquals(
            "![](media/photo-2.jpg) ![](media/photo.jpg.old) ![](https://example.com/media/photo.jpg) ![](./media/photo-2.jpg) [Search](https://example.com/?q=media/photo.jpg)",
            MediaNames.renamed(markdown, "photo.jpg", "photo-2.jpg"),
        )
    }

    @Test
    fun `a live photo's motion is named after its still, and known by that name`() {
        assertEquals("picture-1-motion.mp4", MediaNames.motionName("picture-1", "mp4"))
        assertTrue(MediaNames.isMotion("picture-1-motion.mov"))
        assertEquals("picture-1", MediaNames.stillOf("picture-1-motion.mp4"))
        assertFalse(MediaNames.isMotion("picture-1.mp4"))
        assertNull(MediaNames.stillOf("video-2.mp4"))
    }
}
