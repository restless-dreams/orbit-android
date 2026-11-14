package com.hyuchia.orbit.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pictures, live photos' motions, videos, sounds, files, voice memos and
 * sketches coming into one map at the same moment: each copy is held where
 * its name is given until every other copy has one too, so none is written
 * before all are named, and all are named in the same millisecond. Each
 * gets a name of its own and keeps its own bytes; none goes over another.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaAtOnceTest {
    /** Another app's files, by the last part of their address, with their names and types. */
    class Gallery : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val file = FILES[uri.lastPathSegment] ?: return null
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(file.name, file.bytes.size.toLong())) }
        }

        override fun getType(uri: Uri): String? = FILES[uri.lastPathSegment]?.type

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = FILES[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())

            val copy = File.createTempFile("shared", ".bin").apply {
                deleteOnExit()
                writeBytes(file.bytes)
            }

            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    class Shared(val name: String, val type: String, val bytes: ByteArray)

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** The moment every copy is named in: names from the clock are the same for each. */
    private val now = 1_763_042_400_000L

    /** Hears each name held for a file coming in, as it's held: where the test holds a copy. */
    @Volatile
    private var naming: (String) -> Unit = {}

    private lateinit var maps: MapRepository
    private lateinit var id: String

    private val media: File get() = File(maps.folder(id), MEDIA_DIRECTORY)

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/files/$key")

    private fun resource(path: String): ByteArray = File("src/test/resources/$path").readBytes()

    private fun share(key: String, name: String, type: String, bytes: ByteArray): Uri {
        FILES[key] = Shared(name, type, bytes)
        return address(key)
    }

    private fun file(src: String): File = File(maps.folder(id), src)

    @Before
    fun start() = runBlocking {
        Robolectric.setupContentProvider(Gallery::class.java, AUTHORITY)
        FILES.clear()
        maps = MapRepository(folder.newFolder("maps"), folder.newFolder("trash"), starters, FileWords(Locale.US), clock = { now }, naming = { naming(it) })
        maps.load()
        id = maps.add("# Trip\n")
    }

    /**
     * Runs `copies` side by side, each held at the first name it's given
     * until every one of them has one: none writes a file before all are
     * named. Returns what each gave back, in their order.
     */
    private fun <T> atOnce(vararg copies: suspend () -> T): List<T> = runBlocking {
        val named = CountDownLatch(copies.size)
        val holding = ConcurrentHashMap.newKeySet<String>()

        naming = { name ->
            holding += name

            if (named.count > 0) {
                named.countDown()

                if (!named.await(10, TimeUnit.SECONDS)) {
                    throw AssertionError("Not every copy was named: $holding")
                }
            }
        }

        try {
            copies.map { copy -> async(Dispatchers.IO) { copy() } }.awaitAll()
        } finally {
            naming = {}
        }
    }

    private fun assertKept(expected: List<ByteArray>, sources: List<String>) {
        assertEquals("Each has a name of its own: $sources", sources.size, sources.toSet().size)
        sources.zip(expected).forEach { (src, bytes) -> assertArrayEquals(src, bytes, file(src).readBytes()) }
    }

    @Test
    fun `two pictures at once each get a name of their own`() {
        val first = share("first", "IMG_1.jpg", "image/jpeg", TestMedia.picture(24, 16))
        val second = share("second", "IMG_2.jpg", "image/jpeg", TestMedia.picture(40, 30))

        val added = atOnce(
            { maps.addPicture(id, first, context.contentResolver) },
            { maps.addPicture(id, second, context.contentResolver) },
        )

        val sources = added.map { it.src }
        assertEquals(sources.size, sources.toSet().size)
        assertEquals(sources.map { it.removePrefix("media/") }.sorted(), media.list().orEmpty().sorted())
        assertFalse(file(sources[0]).readBytes().contentEquals(file(sources[1]).readBytes()))
    }

    @Test
    fun `two live photos at once keep each motion beside its own still`() {
        val google = share("google", "PXL_1.MP.jpg", "image/jpeg", resource("live/google-motion-photo.jpg"))
        val samsung = share("samsung", "20251102_1.jpg", "image/jpeg", resource("live/samsung-motion-photo.jpg"))

        val added = atOnce(
            { maps.addPicture(id, google, context.contentResolver) },
            { maps.addPicture(id, samsung, context.contentResolver) },
        )

        val names = added.flatMap { listOf(it.src, it.motion!!.src) }.map { it.removePrefix("media/") }
        assertEquals(names.sorted(), media.list().orEmpty().sorted())

        // Each motion is named for its own still, so it's still known for its motion.
        for (picture in added) {
            assertEquals(picture.src.removePrefix("media/").substringBeforeLast('.'), MediaNames.stillOf(picture.motion!!.src.removePrefix("media/")))
        }
    }

    @Test
    fun `two animations at once each get a name of their own`() {
        val first = resource("animations/orbit.gif")
        // The same animation, with a byte past its end that no reader reads: another file all the same.
        val second = first + byteArrayOf(0)

        val added = atOnce(
            { maps.addPicture(id, share("first", "orbit.gif", "image/gif", first), context.contentResolver) },
            { maps.addPicture(id, share("second", "orbit-2.gif", "image/gif", second), context.contentResolver) },
        )

        assertKept(listOf(first, second), added.map { it.src })
        assertEquals(2, media.list().orEmpty().size)
    }

    @Test
    fun `two motions given to one picture at once each get a name of their own`() = runBlocking {
        val picture = maps.addPicture(id, share("still", "IMG_0911.jpg", "image/jpeg", TestMedia.picture()), context.contentResolver)
        val movie = share("movie", "IMG_0911.MOV", "video/quicktime", resource("live/iphone-live.mov"))
        val older = share("older", "IMG_0912.MOV", "video/quicktime", resource("live/iphone-live-older.mov"))

        val added = atOnce(
            { maps.addMotion(id, picture.src, movie, context.contentResolver) },
            { maps.addMotion(id, picture.src, older, context.contentResolver) },
        )

        val motions = added.map { it.src.removePrefix("media/") }
        assertEquals(2, motions.toSet().size)
        assertTrue(motions.toString(), motions.all { MediaNames.isMotion(it) && file("media/$it").length() > 0 })
        assertEquals((motions + picture.src.removePrefix("media/")).sorted(), media.list().orEmpty().sorted())
    }

    @Test
    fun `two videos at once, one copied and one made smaller, each get a name of their own`() {
        val copied = TestMedia.movie(30_000)
        val other = TestMedia.movie(50_000)
        val made = TestMedia.movie(40_000)

        val added = atOnce(
            { maps.addVideo(id, share("copied", "Waves.mp4", "video/mp4", copied), context.contentResolver) {} },
            { maps.addVideo(id, share("other", "Gulls.mp4", "video/mp4", other), context.contentResolver) {} },
            { maps.addMadeVideo(id, estimate = made.size.toLong()) { it.writeBytes(made) } },
        )

        assertKept(listOf(copied, other, made), added.map { it.src })
    }

    @Test
    fun `two sounds at once each get a name of their own`() {
        val first = TestMedia.mp3()
        val second = TestMedia.mp3() + ByteArray(4_000)

        val added = atOnce(
            { maps.addSound(id, share("first", "Interview.mp3", "audio/mpeg", first), context.contentResolver) {} },
            { maps.addSound(id, share("second", "Birds.mp3", "audio/mpeg", second), context.contentResolver) {} },
        )

        assertKept(listOf(first, second), added.map { it.src })
    }

    @Test
    fun `two files of one name at once each keep their own bytes`() {
        val tickets = ByteArray(20_000) { 1 }
        val others = ByteArray(30_000) { 2 }

        val added = atOnce(
            { maps.addFile(id, share("tickets", "Tickets.pdf", "application/pdf", tickets), context.contentResolver) {} },
            { maps.addFile(id, share("others", "Tickets.pdf", "application/pdf", others), context.contentResolver) {} },
        )

        assertEquals(setOf("media/file-tickets.pdf", "media/file-tickets-2.pdf"), added.map { it.src }.toSet())
        assertKept(listOf(tickets, others), added.map { it.src })
    }

    @Test
    fun `a picture pasted from another map as one of that name comes in keeps its own`() = runBlocking {
        // A picture added to another map in the same millisecond has the name a picture coming into this one is given.
        val from = maps.add("# Elsewhere\n")
        val name = "picture-${now.toString(36)}.jpg"
        val pasted = TestMedia.picture(32, 32, noise = 7)
        File(maps.folder(from), MEDIA_DIRECTORY).apply { mkdirs() }.resolve(name).writeBytes(pasted)
        val picture = share("picture", "IMG_3.jpg", "image/jpeg", TestMedia.picture(24, 16))

        val (text, added) = atOnce(
            { maps.copyMedia(from, id, "- ![](media/$name)\n").text },
            { maps.addPicture(id, picture, context.contentResolver).src },
        )

        val pastedName = MediaNames.references(text).single()
        assertNotEquals(pastedName, added.removePrefix("media/"))
        assertArrayEquals(pasted, file("media/$pastedName").readBytes())
        assertEquals(listOf(pastedName, added.removePrefix("media/")).sorted(), media.list().orEmpty().sorted())
    }

    @Test
    fun `two voice memos at once each get a name of their own`() {
        val first = TestMedia.movie(6_000)
        val second = TestMedia.movie(9_000)
        val takes = folder.newFolder("recordings")
        val firstTake = File(takes, "take-1.mp4").apply { writeBytes(first) }
        val secondTake = File(takes, "take-2.mp4").apply { writeBytes(second) }

        val added = atOnce(
            { maps.addRecording(id, firstTake) },
            { maps.addRecording(id, secondTake) },
        )

        assertKept(listOf(first, second), added)
        assertFalse(firstTake.exists() || secondTake.exists())
    }

    @Test
    fun `two sketches at once each get a name of their own`() {
        val first = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path d="M1 1L9 9"/></svg>""".toByteArray()
        val second = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path d="M9 1L1 9"/></svg>""".toByteArray()

        val added = atOnce(
            { maps.addSketch(id, first) },
            { maps.addSketch(id, second) },
        )

        assertKept(listOf(first, second), added)
    }

    /** The map with its idea's file `file-tickets.pdf` in its text, holding `tickets` when given: as the folder's sync finds it. */
    private suspend fun withTickets(tickets: ByteArray?): String {
        tickets?.let { File(media.apply { mkdirs() }, "file-tickets.pdf").writeBytes(it) }
        val markdown = maps.read(id)
        val file = AddedFile("media/file-tickets.pdf", "Tickets.pdf", "application/pdf", tickets?.size?.toLong() ?: 20_000)
        assertEquals(WriteResult.Written, maps.write(id, "$markdown- Tickets\n${filesAttribute(listOf(file))}\n", base = markdown))
        return maps.read(id)
    }

    @Test
    fun `a file attached while the folder's sync copies another under a new name gets one of its own`() = runBlocking {
        // The folder holds another file-tickets.pdf, so the sync copies this map's under a name of its own, file-tickets-2.pdf;
        // a Tickets.pdf attached meanwhile would be named so too.
        val tickets = ByteArray(20_000) { 1 }
        val text = withTickets(tickets)
        val attached = ByteArray(30_000) { 2 }
        val renaming = CountDownLatch(1)
        val attaching = CountDownLatch(1)

        naming = { name ->
            if (name == "file-tickets-2.pdf") {
                // The sync's copy holds its new name, and waits for the attached file to be given one.
                renaming.countDown()
                attaching.await(10, TimeUnit.SECONDS)
            } else {
                attaching.countDown()
            }
        }

        val renamed = async(Dispatchers.IO) { maps.renameMedia(id, "file-tickets.pdf", "file-tickets-2.pdf", expected = text) }
        assertTrue(renaming.await(10, TimeUnit.SECONDS))
        val added = async(Dispatchers.IO) { maps.addFile(id, share("attached", "Tickets.pdf", "application/pdf", attached), context.contentResolver) {} }

        assertTrue(renamed.await()!!.contains("media/file-tickets-2.pdf"))
        assertEquals("media/file-tickets-3.pdf", added.await().src)
        assertArrayEquals(tickets, file("media/file-tickets-2.pdf").readBytes())
        assertArrayEquals(attached, file("media/file-tickets-3.pdf").readBytes())
        naming = {}
    }

    @Test
    fun `the folder's sync names nothing after a file on its way in, nor copies over it`() = runBlocking {
        val tickets = ByteArray(20_000) { 1 }
        val text = withTickets(tickets)
        val attached = ByteArray(30_000) { 2 }
        val held = CountDownLatch(1)
        val synced = CountDownLatch(1)

        // A Tickets.pdf attached holds file-tickets-2 as it's copied in.
        naming = {
            held.countDown()
            synced.await(10, TimeUnit.SECONDS)
        }

        val added = async(Dispatchers.IO) { maps.addFile(id, share("attached", "Tickets.pdf", "application/pdf", attached), context.contentResolver) {} }
        assertTrue(held.await(10, TimeUnit.SECONDS))
        naming = {}

        // Meanwhile the sync numbers past it, and won't copy over it under the name it holds.
        assertTrue(maps.hasMediaNamed(id, "file-tickets-2.pdf"))
        assertNull(maps.renameMedia(id, "file-tickets.pdf", "file-tickets-2.pdf", expected = text))
        synced.countDown()

        assertEquals("media/file-tickets-2.pdf", added.await().src)
        assertArrayEquals(attached, file("media/file-tickets-2.pdf").readBytes())
    }

    @Test
    fun `a file attached while the folder's sync brings one of its name gets one of its own`() = runBlocking {
        // The map's text points at the folder's file-tickets.pdf, which isn't here yet; the sync brings it in as Tickets.pdf
        // is attached.
        withTickets(null)
        val folders = ByteArray(20_000) { 1 }
        val attached = ByteArray(30_000) { 2 }
        var added: AddedFile? = null

        val size = maps.addMediaFile(id, "file-tickets.pdf", files = listOf("file-tickets.pdf")) { output ->
            added = runBlocking { maps.addFile(id, share("attached", "Tickets.pdf", "application/pdf", attached), context.contentResolver) {} }
            output.write(folders)
            true
        }

        assertEquals(folders.size.toLong(), size)
        assertEquals("media/file-tickets-2.pdf", added?.src)
        assertArrayEquals(folders, file("media/file-tickets.pdf").readBytes())
        assertArrayEquals(attached, file("media/file-tickets-2.pdf").readBytes())
    }

    @Test
    fun `a file that takes a held name meanwhile, from outside Orbit, is never written over`() = runBlocking {
        val outside = "Not Orbit's".toByteArray()
        val take = File(folder.newFolder("recordings"), "take-1.mp4").apply { writeBytes(TestMedia.movie(6_000)) }

        // A file put in the map's folder under the memo's name once it's held, as another app could.
        naming = { stem -> File(media, "$stem.mp4").writeBytes(outside) }

        try {
            maps.addRecording(id, take)
            fail("The memo should not have gone over the file there")
        } catch (taken: IOException) {
            // Refused, and the take left where it is, to save again.
        } finally {
            naming = {}
        }

        assertArrayEquals(outside, media.listFiles().orEmpty().single().readBytes())
        assertTrue(take.isFile)
        assertTrue(media.list().orEmpty().none { it.endsWith(".partial") })

        // Saved again, it gets a name of its own.
        val saved = maps.addRecording(id, take)
        assertArrayEquals(TestMedia.movie(6_000), file(saved).readBytes())
    }

    @Test
    fun `a file that takes a live photo's held motion name meanwhile stays, and the still comes alone`() = runBlocking {
        val outside = "Not Orbit's".toByteArray()

        // A file put under the motion's name once it's held, as another app could.
        naming = { stem ->
            if (MediaNames.isMotion("$stem.mp4")) {
                File(media.apply { mkdirs() }, "$stem.mp4").writeBytes(outside)
            }
        }

        val added = try {
            maps.addPicture(id, share("google", "PXL_1.MP.jpg", "image/jpeg", resource("live/google-motion-photo.jpg")), context.contentResolver)
        } finally {
            naming = {}
        }

        assertNull(added.motion)
        assertTrue(file(added.src).isFile)
        val motion = media.listFiles().orEmpty().single { MediaNames.isMotion(it.name) }
        assertArrayEquals(outside, motion.readBytes())
    }

    private companion object {
        const val AUTHORITY = "com.example.gallery.atonce"

        /** The files the other app shares, by the last part of their address. */
        val FILES = ConcurrentHashMap<String, Shared>()
    }
}
