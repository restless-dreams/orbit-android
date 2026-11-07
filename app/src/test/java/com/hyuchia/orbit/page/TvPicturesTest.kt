package com.hyuchia.orbit.page

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import javax.imageio.ImageIO

/** A map cast to a TV, its pictures inside its text: up to what the TV is sent of them, read only as they fit. */
@RunWith(AndroidJUnit4::class)
class TvPicturesTest {
    private val folder: File = Files.createTempDirectory("orbit-tv-pictures").toFile()
    private val media = File(folder, "media").apply { mkdirs() }

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    private fun picture(): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    private fun pictures(text: String): Int = Regex(Regex.escape("data:image/png;base64,")).findAll(text).count()

    @Test
    fun aTvGetsTheMapsPicturesUpToWhatItsSentOfThemInTheMapsOrder() {
        val picture = picture()
        listOf("beach.png", "tram.png", "fado.png").forEach { File(media, it).writeBytes(picture) }
        val markdown = "# Trip\n![Beach](media/beach.png)\n## Tram\n![Tram](media/tram.png)\n## Fado\n![Fado](media/fado.png)\n"
        val one = inlinePictures("![](media/beach.png)", folder, maxSide = 1280).removePrefix("![](").removeSuffix(")")

        assertEquals(3, pictures(inlinePictures(markdown, folder, maxSide = 1280)))

        // Room for two of them: the third stays a name the TV can't reach, and its idea shows without it.
        val two = inlinePictures(markdown, folder, maxSide = 1280, most = one.length * 2L + 10)
        assertEquals(2, pictures(two))
        assertTrue(two, two.contains("![Fado](media/fado.png)"))
    }

    @Test
    fun aPictureThatWouldGoAsItsFileIsIsntReadWhenItsBiggerThanWhatsLeft() {
        File(media, "beach.png").writeBytes(picture())
        // A file far bigger than what's left, whatever it holds: never read, as a 250 MB animation would fill the phone's memory.
        RandomAccessFile(File(media, "loop.gif"), "rw").use { it.setLength(300L * 1024 * 1024) }
        val markdown = "# Trip\n![Loop](media/loop.gif)\n## Beach\n![Beach](media/beach.png)\n"

        val sent = inlinePictures(markdown, folder, maxSide = 1280, most = 1024 * 1024)
        assertTrue(sent, sent.contains("![Loop](media/loop.gif)"))
        assertEquals(1, pictures(sent))
    }
}
