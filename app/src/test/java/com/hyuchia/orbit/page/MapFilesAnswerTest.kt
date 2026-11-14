package com.hyuchia.orbit.page

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/** What the map's page gets when it asks for a map's picture, as WebView asks, on a thread where an exception would stop the app. */
@RunWith(AndroidJUnit4::class)
class MapFilesAnswerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun address(map: String, name: String): Uri = Uri.parse("https://$PAGE_HOST/maps/$map/media/$name")

    @Test
    fun anAnimationAskedForWhileTheCacheCantKeepItsFirstFrameGetsTheFrameAllTheSame() {
        val maps = folder.newFolder("maps")
        File(maps, "trip/media").mkdirs()
        File("src/test/resources/animations/orbit.gif").copyTo(File(maps, "trip/media/orbit.gif"))
        // Where the cache's folder would be, there's a file: nothing can be kept in it, as on a full disk.
        val cache = folder.newFile("first-frames")
        val files = MapFiles(maps, FirstFrames(cache) { "first frame".toByteArray() })

        val answer = files.answer(address("trip", "orbit.gif"), null) { path -> mapMedia(path)?.takeIf { it.mapId == "trip" } }

        assertEquals(200, answer.statusCode)
        assertEquals("image/png", answer.mimeType)
        assertArrayEquals("first frame".toByteArray(), answer.data.use { it.readBytes() })
    }

    @Test
    fun theAudiencesScreenPlaysAnAnimationAsItIsAndDrawsAHeicPictureAsAJpeg() {
        val maps = folder.newFolder("maps")
        File(maps, "trip/media").mkdirs()
        File("src/test/resources/animations/orbit.gif").copyTo(File(maps, "trip/media/orbit.gif"))
        File("src/test/resources/live/iphone-live.heic").copyTo(File(maps, "trip/media/tower.heic"))
        val gif = File(maps, "trip/media/orbit.gif").readBytes()
        val stills = FirstFrames(folder.newFolder("first-frames"), drawn = { "a jpeg".toByteArray() }) { "first frame".toByteArray() }
        val audience = MapFiles(maps, stills, animations = false)

        val animation = audience.answer(address("trip", "orbit.gif"), null, ::mapMedia)
        assertEquals("image/gif", animation.mimeType)
        assertArrayEquals(gif, animation.data.use { it.readBytes() })

        val heic = audience.answer(address("trip", "tower.heic"), null, ::mapMedia)
        assertEquals("image/jpeg", heic.mimeType)
        assertArrayEquals("a jpeg".toByteArray(), heic.data.use { it.readBytes() })
    }

    @Test
    fun aPictureThatWentIsMissingOnThePage() {
        val maps = folder.newFolder("maps")
        File(maps, "trip/media").mkdirs()
        val files = MapFiles(maps, FirstFrames(folder.newFolder("first-frames")) { null })

        assertEquals(404, files.answer(address("trip", "gone.jpg"), null, ::mapMedia).statusCode)
        // Nor does a map the page may not read give anything.
        File(maps, "trip/media/alfama.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(404, files.answer(address("trip", "alfama.jpg"), null) { null }.statusCode)
        assertEquals(200, files.answer(address("trip", "alfama.jpg"), null, ::mapMedia).statusCode)
    }

    @Test
    fun aLinkInAMapsFolderLeadsNowhereElse() {
        val maps = folder.newFolder("maps")
        File(maps, "trip/media").mkdirs()
        File(maps, "journal/media").mkdirs()
        File(maps, "journal/media/secret.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(maps, "trip/media/mine.jpg").writeBytes(byteArrayOf(4, 5, 6))
        // As a folder prepared outside Orbit may have them: a picture that leads to another map's, a media folder that's
        // another map's, and a picture that leads to the map's own text.
        Files.createSymbolicLink(File(maps, "trip/media/linked.jpg").toPath(), File(maps, "journal/media/secret.jpg").toPath())
        File(maps, "away").mkdirs()
        Files.createSymbolicLink(File(maps, "away/media").toPath(), File(maps, "journal/media").toPath())
        File(maps, "trip/map.md").writeText("# Trip")
        Files.createSymbolicLink(File(maps, "trip/media/text.jpg").toPath(), File(maps, "trip/map.md").toPath())
        val files = MapFiles(maps)
        val onlyTrip = { path: String -> mapMedia(path)?.takeIf { it.mapId == "trip" || it.mapId == "away" } }

        assertEquals(200, files.answer(address("trip", "mine.jpg"), null, onlyTrip).statusCode)
        assertEquals(404, files.answer(address("trip", "linked.jpg"), null, onlyTrip).statusCode)
        assertEquals(404, files.answer(address("away", "secret.jpg"), null, onlyTrip).statusCode)
        assertEquals(404, files.answer(address("trip", "text.jpg"), null, onlyTrip).statusCode)
    }
}
