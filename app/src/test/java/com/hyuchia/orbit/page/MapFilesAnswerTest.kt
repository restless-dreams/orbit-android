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

        val answer = files.answer(address("trip", "orbit.gif"), null) { it == "trip" }

        assertEquals(200, answer.statusCode)
        assertEquals("image/png", answer.mimeType)
        assertArrayEquals("first frame".toByteArray(), answer.data.use { it.readBytes() })
    }

    @Test
    fun aPictureThatWentIsMissingOnThePage() {
        val maps = folder.newFolder("maps")
        File(maps, "trip/media").mkdirs()
        val files = MapFiles(maps, FirstFrames(folder.newFolder("first-frames")) { null })

        assertEquals(404, files.answer(address("trip", "gone.jpg"), null) { true }.statusCode)
        // Nor does a map the page may not read give anything.
        File(maps, "trip/media/alfama.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(404, files.answer(address("trip", "alfama.jpg"), null) { false }.statusCode)
        assertEquals(200, files.answer(address("trip", "alfama.jpg"), null) { true }.statusCode)
    }
}
