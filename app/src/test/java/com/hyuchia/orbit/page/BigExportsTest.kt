package com.hyuchia.orbit.page

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.data.motionsFitting
import com.hyuchia.orbit.data.requireRoom
import com.hyuchia.orbit.data.soundsFitting
import com.hyuchia.orbit.nearby.Joining
import com.hyuchia.orbit.nearby.JoiningOptions
import com.hyuchia.orbit.nearby.LocalServer
import com.hyuchia.orbit.nearby.Response
import com.hyuchia.orbit.nearby.RoomKind
import com.hyuchia.orbit.nearby.SharedMap
import com.hyuchia.orbit.nearby.anyone
import com.hyuchia.orbit.nearby.pagePolicy
import com.hyuchia.orbit.ui.makeExport
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Big maps exported as a web page and as a zip, and opened on a computer,
 * each in a JVM of its own whose heap is a fraction of what it makes. A map
 * at today's limits: 25 MB of voice memos and 25 MB of live photos' motions
 * among 90 MB of the map's own files make a page of 120 MB. And a picture
 * far bigger than the heap, as maps hold once their limits rise. Each is
 * written, and sent, a piece at a time; before, the page took 1.5 GB of
 * heap, the zip 256 MB, and a picture twice its size.
 */
class BigExportsTest {
    /** What `scenario` of [BigExports] said, for the map `map` (or the zip at that path, for `arrive`), within [HEAP], and the file it made. */
    private fun export(scenario: String, map: String = limits): Pair<String, File> {
        val name = map.substringAfterLast('/')
        val out = File(root, "made-$scenario-$name").apply { mkdirs() }
        val said = File(root, "said-$scenario-$name")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-Xmx$HEAP", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), BigExports::class.java.name, scenario, root.path, map, out.path)
            .redirectErrorStream(true)
            .redirectOutput(said)
            .start()

        assertTrue("$scenario took too long", process.waitFor(5, TimeUnit.MINUTES))
        val text = said.readText()
        println("$scenario within -Xmx$HEAP: ${text.lines().firstOrNull { it.startsWith("PEAK") }}")
        assertEquals(text, 0, process.exitValue())
        return text to File(Regex("MADE (.*)").find(text)!!.groupValues[1])
    }

    @Test
    fun `a web page of a map at its limits is written whole within a small heap`() {
        val (_, page) = export("page")

        // Every picture, sound and motion, in Base64.
        assertTrue(page.length() > File(root, "maps/$limits/media").listFiles()!!.sumOf { it.length() } * 4 / 3)
        val found = count(page, "data:audio/mp4;base64,", "data:video/mp4;base64,", "data:image/jpeg;base64,", "</html>")
        assertEquals(listOf(SOUNDS, MOTIONS, AtTheLimits.pictures(root, limits), 1), found)
        // Its sounds, motions and pictures were copied in one at a time, and none is left behind.
        assertEquals(emptyList<String>(), File(root, "spool").list().orEmpty().toList())
    }

    @Test
    fun `a zip of a map at its limits is written whole within a small heap`() {
        val (_, zip) = export("zip")
        val media = File(root, "maps/$limits/media")

        val entries = ZipInputStream(zip.inputStream().buffered()).use { input -> generateSequence { input.nextEntry }.associate { it.name to input.copyTo(OutputStream.nullOutputStream()) } }
        // Every file of the map's, its sounds and motions byte for byte (they say nothing beyond what they play), and its text and identity.
        assertEquals(media.list()!!.map { "media/$it" }.toSet() + setOf("map.md", "orbit.json"), entries.keys)
        media.listFiles()!!.filter { it.extension == "mp4" }.forEach { file -> assertEquals(file.name, file.length(), entries.getValue("media/${file.name}")) }
    }

    @Test
    fun `a map's zip at its limits comes back in, unpacked into files a piece at a time within a small heap`() {
        val (_, zip) = export("zip")
        val (_, arrived) = export("arrive", map = zip.path)

        // Every file of the map's came into the map it became, as big as it was, and nothing was left unpacked.
        val media = File(root, "maps/$limits/media")
        assertEquals(media.list()!!.toSet(), File(arrived, "media").list()!!.toSet())
        media.listFiles()!!.filter { it.extension == "mp4" }.forEach { file -> assertEquals(file.name, file.length(), File(arrived, "media/${file.name}").length()) }
        assertEquals(emptyList<String>(), File(root, "unpacking").list().orEmpty().toList())
    }

    @Test
    fun `a map at its limits opens on a computer, its page sent from its file within a small heap`() {
        val (said, received) = export("computer")
        val page = File(Regex("PAGE (.*)").find(said)!!.groupValues[1])
        val head = File(received.path + ".head").readText()

        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(head, head.contains("Content-Length: ${page.length()}\r\n"))
        // With the page's own policy, its script by its hash, not that of a file, which runs nothing.
        assertTrue(head, Regex("Content-Security-Policy: default-src 'none'; script-src 'self' 'sha256-[^']+'").containsMatchIn(head))
        assertEquals(digest(page), digest(received))
    }

    @Test
    fun `a picture far bigger than the heap goes in a zip and a web page, without where it was taken`() {
        val original = File(root, "maps/$picture/media/${AtTheLimits.BIG_PICTURE}")
        val (_, zip) = export("zip", map = picture)

        // In the zip: the whole picture but its EXIF, read through as it went.
        val counted = ZipInputStream(zip.inputStream().buffered()).use { input ->
            generateSequence { input.nextEntry }.first { it.name == "media/${AtTheLimits.BIG_PICTURE}" }
            Counted(input).also { it.copyTo(OutputStream.nullOutputStream()) }
        }

        assertEquals(original.length() - AtTheLimits.EXIF_SEGMENT, counted.count)
        assertFalse(counted.said)

        // In the page: the picture, in Base64.
        val (_, page) = export("page", map = picture)
        assertTrue(page.length() > (original.length() - AtTheLimits.EXIF_SEGMENT) * 4 / 3)
        assertEquals(listOf(1, 1), count(page, "data:image/jpeg;base64,", "</html>"))
    }

    /** A stream as it's read: how many bytes it gave, and whether they said where the big picture was taken. */
    private class Counted(input: InputStream) : FilterInputStream(input) {
        var count = 0L
        var said = false
        private var carried = ""

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(bytes, offset, length)

            if (read > 0) {
                count += read
                val window = carried + String(bytes, offset, read, Charsets.ISO_8859_1)
                said = said || window.contains(AtTheLimits.PLACE)
                carried = window.takeLast(AtTheLimits.PLACE.length)
            }

            return read
        }
    }

    companion object {
        /**
         * The heap each export has: half of what a page at today's limits
         * comes to. Each works in 48 MB, and so does the picture far bigger
         * than it.
         */
        const val HEAP = "64m"

        /** The voice memos the map at its limits plays, and the live photos' motions it has: each 5.2 MB. */
        const val SOUNDS = 5

        const val MOTIONS = 5

        private lateinit var root: File

        /** The map at today's limits. */
        private lateinit var limits: String

        /** The map with one picture far bigger than the heap. */
        private lateinit var picture: String

        @BeforeClass
        @JvmStatic
        fun makeTheMaps() {
            root = Files.createTempDirectory("orbit-big-exports").toFile()
            limits = AtTheLimits.make(root)
            picture = AtTheLimits.makeBigPicture(root)
        }

        @AfterClass
        @JvmStatic
        fun removeThem() {
            root.deleteRecursively()
        }

        /** How many times each of `texts` is in `file`, read a megabyte at a time. */
        private fun count(file: File, vararg texts: String): List<Int> {
            val counts = IntArray(texts.size)
            val overlap = texts.maxOf { it.length } - 1
            var carried = ""

            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)

                while (true) {
                    val read = input.read(buffer)

                    if (read < 0) {
                        break
                    }

                    // Each text is counted where it starts, in what's new or across the edge with what came before.
                    val window = carried + String(buffer, 0, read, Charsets.ISO_8859_1)
                    texts.forEachIndexed { index, text -> counts[index] += Regex(Regex.escape(text)).findAll(window).count { it.range.last >= carried.length } }
                    carried = window.takeLast(overlap)
                }
            }

            return counts.toList()
        }

        private fun digest(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(file.inputStream().buffered(), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Big maps made on disk for [BigExportsTest]. One at Orbit's media limits:
 * five voice memos and five live photos' motions of 5.2 MB each, which fill
 * a page's 25 MiB of each, the live photos' stills, a camera's 12-megapixel
 * original as one arrives through the folder maps are kept in, and photos as
 * they come from the phone, 90 MB in all. And one with a picture of 100 MB,
 * bigger than a map may hold today. Movies and pictures are made of noise,
 * which doesn't compress, as real ones don't.
 */
internal object AtTheLimits {
    /** Where the big picture was taken, as its EXIF says. */
    const val PLACE = "Taken at 38.7139 N, 9.1394 W"

    /** The big picture's name in its map's media folder. */
    const val BIG_PICTURE = "panorama.jpg"

    /** What the big picture's EXIF holds: a TIFF with no fields, and where it was taken after them, as a camera's own note. */
    private val EXIF = "Exif\u0000\u0000II*\u0000\u0008\u0000\u0000\u0000\u0000\u0000$PLACE".toByteArray(Charsets.ISO_8859_1)

    /** How big the big picture's EXIF segment is: its marker, its length, and what it holds. */
    val EXIF_SEGMENT: Long get() = 4L + EXIF.size

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome() = StarterMap("# Welcome\n")
    }

    fun repository(root: File) = MapRepository(File(root, "maps"), File(root, "trash"), starters, FileWords(Locale.US))

    /** Makes the map at the limits in a repository in `root`, and gives its id. */
    fun make(root: File): String = runBlocking {
        val maps = repository(root)
        maps.load()
        val photo = jpeg(1600, 1200, seed = 1)
        val original = jpeg(4000, 3000, seed = 2)
        val each = 5_200_000L
        val photos = ((90_000_000L - (BigExportsTest.SOUNDS + BigExportsTest.MOTIONS) * each - original.size) / photo.size).toInt() - BigExportsTest.MOTIONS

        val markdown = buildString {
            append("# Trip\n")
            (1..BigExportsTest.SOUNDS).forEach { append("## Memo $it <!-- orbit audio: media/memo-$it.mp4 -->\n") }
            (1..BigExportsTest.MOTIONS).forEach { append("## Live $it <!-- orbit image: { src: media/picture-$it.jpg, motion: media/picture-$it-motion.mp4 } -->\n") }
            append("## Summit\n![](media/original.jpg)\n")
            (1..photos).forEach { append("## Photo $it\n![](media/photo-$it.jpg)\n") }
        }

        val id = maps.add(markdown)
        val media = File(maps.folder(id), "media").apply { mkdirs() }
        (1..BigExportsTest.SOUNDS).forEach { movie(File(media, "memo-$it.mp4"), each, seed = it.toLong()) }
        (1..BigExportsTest.MOTIONS).forEach { movie(File(media, "picture-$it-motion.mp4"), each, seed = 100L + it) }
        (1..BigExportsTest.MOTIONS).forEach { File(media, "picture-$it.jpg").writeBytes(photo) }
        File(media, "original.jpg").writeBytes(original)
        (1..photos).forEach { File(media, "photo-$it.jpg").writeBytes(photo) }
        check(media.listFiles()!!.sumOf { it.length() } in 85_000_000L..90_000_000L) { "The map isn't at its limits" }
        id
    }

    /** Makes a map with one JPEG of 100 MB, as big as a map's files may be once their limits rise, and gives its id. */
    fun makeBigPicture(root: File): String = runBlocking {
        val maps = repository(root)
        maps.load()
        val id = maps.add("# Coast\n## Panorama\n![The whole coast](media/$BIG_PICTURE)\n")
        val media = File(maps.folder(id), "media").apply { mkdirs() }
        bigJpeg(File(media, BIG_PICTURE), 100_000_000L)
        id
    }

    /** How many pictures a map shows, stills included. */
    fun pictures(root: File, id: String): Int = File(root, "maps/$id/media").list()!!.count { it.endsWith(".jpg") }

    /** A movie of `size` bytes, as a voice memo or a motion is: its type, an empty movie, and its media data, noise. */
    private fun movie(file: File, size: Long, seed: Long) {
        file.outputStream().buffered().use { output ->
            output.write(ByteBuffer.allocate(16).putInt(16).put("ftypisom".toByteArray(Charsets.ISO_8859_1)).putInt(0).array())
            output.write(ByteBuffer.allocate(8).putInt(8).put("moov".toByteArray(Charsets.ISO_8859_1)).array())
            output.write(ByteBuffer.allocate(8).putInt((size - 24).toInt()).put("mdat".toByteArray(Charsets.ISO_8859_1)).array())
            noise(output, size - 32, seed)
        }
    }

    /** A JPEG of noise, `width` by `height` pixels. */
    private fun jpeg(width: Int, height: Int, seed: Long): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val random = Random(seed)

        for (index in pixels.indices) {
            pixels[index] = random.nextInt()
        }

        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    /** A JPEG's segment: its marker, and its length before `data`. */
    private fun segment(marker: Int, data: ByteArray): ByteArray = byteArrayOf(0xFF.toByte(), marker.toByte(), ((data.size + 2) shr 8).toByte(), (data.size + 2).toByte()) + data

    /**
     * A JPEG of `size` bytes as a camera writes a big one, as Orbit reads it
     * to share it: JFIF, EXIF saying where it was taken, its tables and
     * frame, then one scan of noise, which is never a marker, and its end.
     */
    private fun bigJpeg(file: File, size: Long) {
        file.outputStream().buffered().use { output ->
            val head = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
                segment(0xE0, "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.ISO_8859_1)) +
                segment(0xE1, EXIF) +
                segment(0xDB, ByteArray(65)) +
                segment(0xC0, byteArrayOf(8, 0x0B, 0xB8.toByte(), 0x0F, 0xA0.toByte(), 1, 1, 0x11, 0)) +
                segment(0xC4, ByteArray(18)) +
                segment(0xDA, byteArrayOf(1, 1, 0, 0, 0x3F, 0))
            output.write(head)
            noise(output, size - head.size - 2, seed = 7, markers = false)
            output.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        }
    }

    /** `count` bytes of noise; without `markers`, never 0xFF, so they're a JPEG's scan as a whole. */
    private fun noise(output: OutputStream, count: Long, seed: Long, markers: Boolean = true) {
        val random = Random(seed)
        val chunk = ByteArray(64 * 1024)
        var left = count

        while (left > 0) {
            random.nextBytes(chunk)

            if (!markers) {
                for (index in chunk.indices) {
                    if (chunk[index] == 0xFF.toByte()) {
                        chunk[index] = 0xFE.toByte()
                    }
                }
            }

            val write = minOf(left, chunk.size.toLong()).toInt()
            output.write(chunk, 0, write)
            left -= write
        }
    }
}

/**
 * One export of a big map ([AtTheLimits]), in a JVM of its own with a small
 * heap ([BigExportsTest.HEAP]), as the app makes it: `page`, a web page as
 * Export makes it; `zip`, a zip as Export makes it; `computer`, the page a
 * computer opens from a shared map, fetched from the phone's server; and
 * `arrive`, a map's zip coming back in, as Open a file takes it. It says the
 * most heap it used, and where it put what it made.
 */
internal object BigExports {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val (scenario, root, id, out) = arguments
        ManagementFactory.getMemoryPoolMXBeans().forEach { it.resetPeakUsage() }

        try {
            val maps = AtTheLimits.repository(File(root))
            runBlocking { maps.load() }
            // `arrive` is given a zip, not a map.
            val folder by lazy { maps.folder(id) }

            val made = when (scenario) {
                "page" -> runBlocking {
                    makeExport(File(out), "Trip.html") { output ->
                        page(folder, File(root, "spool"), output, download = null, room = this)
                        true
                    }
                }
                "zip" -> runBlocking {
                    makeExport(File(out), "Trip.zip") { output ->
                        maps.writeZip(id, output, room = this)
                        true
                    }
                }
                // A map's zip, at `id`'s path, coming back in as a map of its own, as Open a file takes it.
                "arrive" -> runBlocking {
                    val bundle = File(id).inputStream().use { maps.readBundle(it) }!!
                    maps.folder(maps.receive(bundle))
                }
                else -> computer(folder, File(root), File(out, "received"))
            }

            val peak = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.sumOf { it.peakUsage.used }
            println("PEAK ${peak / (1024 * 1024)} MiB")
            println("MADE ${made!!.path}")
            System.out.flush()
            exitProcess(0)
        } catch (memory: OutOfMemoryError) {
            println("OUT OF MEMORY")
            memory.printStackTrace(System.out)
            System.out.flush()
            exitProcess(3)
        }
    }

    /**
     * Writes the map's page to `output`, as the app writes it, but for the
     * template and words it reads from the app's files, once `room` has room
     * for it, and gives the scripts the page runs.
     */
    private fun page(folder: File, spool: File, output: OutputStream, download: String?, room: (Long) -> Unit): List<String> {
        val markdown = File(folder, "map.md").readText()
        spool.mkdirs()
        val media = PageMedia(folder, sounds = soundsFitting(markdown, folder), motions = motionsFitting(markdown, folder), spool = spool)
        val template = File("src/main/assets/map/web-page.html").readText()
        val script = File("src/main/assets/map/web-page.js").readText()
        return WebPage.write(output, template, script, "Trip", markdown, AppTheme(dark = false, tokens = emptyMap()), emptyList(), download, FileWords(Locale.US).pageWords(), media = media, room = room)
    }

    /** The map shared with computers: its page made in a file, as the share makes it, and fetched from the server, its answer in `received` and its head beside it. */
    private fun computer(folder: File, root: File, received: File): File {
        val share = SharedMap("123456", folder.name, AppTheme(dark = false, tokens = emptyMap()), Joining("123456", RoomKind.Share, JoiningOptions(askNames = false), template = { "" }, words = FileWords(Locale.US)), File(root, "pages"))

        val page = runBlocking {
            share.page("1") { file ->
                file.outputStream().use { output -> page(folder, File(root, "spool"), output, download = "map.zip") { size -> requireRoom(file.parentFile!!, size) } }
            }
        }

        println("PAGE ${page.file.path}")
        val server = LocalServer({ _, _ -> 1_000 }, policy = anyone) { Response.pageFile(page.file, pagePolicy(page.scripts), mapOf("Referrer-Policy" to "strict-origin")) }
        server.start(0)

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", server.port), 5_000)
                socket.getOutputStream().write("GET /123456/ HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\n\r\n".toByteArray())
                val input = socket.getInputStream().buffered()
                val head = StringBuilder()

                while (!head.endsWith("\r\n\r\n")) {
                    head.append(input.read().toChar())
                }

                File(received.path + ".head").writeText(head.toString())
                received.outputStream().use { input.copyTo(it) }
            }
        } finally {
            server.stop()
        }

        return received
    }
}
