package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.page.DropPlace
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.PastedIdeas
import com.hyuchia.orbit.page.protocolJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A drop of several things stopped partway (the pill's Cancel): what came
 * in before it stopped joins nothing, so it takes none of the map's room
 * from then on, while the map is still open, rather than until its last
 * screen closes.
 */
@RunWith(AndroidJUnit4::class)
class DropCancelTest {
    private val root: File = Files.createTempDirectory("orbit-drop-cancel").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, FileWords(Locale.US))
    private val page = Page()
    private lateinit var id: String
    private lateinit var drops: MapDrops

    @Before
    fun setUp() {
        Shared.reset(root)
        Robolectric.setupContentProvider(Shared::class.java, AUTHORITY)

        id = runBlocking {
            maps.load()
            maps.add("# Trip\n- Target\n")
        }

        val map = MapState("Trip", listOf(MapNode("trip", "Trip", childCount = 1), MapNode("target", "Target", parentId = "trip")), path = listOf("trip"))

        drops = MapDrops(
            scope = scope,
            mapId = id,
            maps = maps,
            importer = { error("No map is let go here") },
            words = FileWords(Locale.US),
            ui = MutableStateFlow(MapUi(ready = true, map = map)),
            locked = MutableStateFlow(false),
            page = { page },
            change = { _, _, _ -> true },
            tell = {},
        )

        // The map is open, and stays open: nothing here closes its screen.
        maps.showing(id, true)
    }

    @After
    fun cleanUp() {
        Shared.release.countDown()
        page.adding.cancel()
        scope.cancel()
        root.deleteRecursively()
    }

    /** A page that says the drop lands on "Target", and adds ideas once the test lets it ([adding]). */
    private class Page : MapPage {
        override var listener: ((MapMessage) -> Unit)? = null
        override var incognito = false
        val adding = CompletableDeferred<Unit>()
        val asked = CountDownLatch(1)
        val commands = mutableListOf<MapCommand>()

        override fun send(command: MapCommand) {
            commands += command
        }

        override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
            val sent = command(1)
            commands += sent

            val value = when (sent) {
                is MapCommand.DropAt -> protocolJson.encodeToJsonElement(DropPlace.serializer(), DropPlace("target"))
                is MapCommand.AddNodes -> {
                    asked.countDown()
                    adding.await()
                    protocolJson.encodeToJsonElement(PastedIdeas.serializer(), PastedIdeas(sent.nodes.size))
                }
                else -> error("This page answers no ${sent::class.simpleName}")
            }

            return protocolJson.decodeFromJsonElement(answer, value)
        }

        override suspend fun drawn() = Unit
    }

    /** What another app lends the drop: each file by its key, one of them held back until the test lets it go. */
    class Shared : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val file = files[uri.lastPathSegment] ?: return null

            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(file.name, file.bytes.size.toLong()))
            }
        }

        override fun getType(uri: Uri): String? = files[uri.lastPathSegment]?.type

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val key = uri.lastPathSegment
            val file = files[key] ?: throw FileNotFoundException(uri.toString())

            if (key == held) {
                opened.countDown()
                release.await(10, TimeUnit.SECONDS)
            }

            val copy = File(scratch, "lent-$key").apply { writeBytes(file.bytes) }
            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        data class Lent(val name: String, val type: String, val bytes: ByteArray)

        companion object {
            val files = ConcurrentHashMap<String, Lent>()

            @Volatile var held: String? = null

            @Volatile var opened = CountDownLatch(1)

            @Volatile var release = CountDownLatch(1)

            private lateinit var scratch: File

            fun reset(root: File) {
                files.clear()
                held = null
                opened = CountDownLatch(1)
                release = CountDownLatch(1)
                scratch = File(root, "lent").apply { mkdirs() }
            }
        }
    }

    private fun clip(vararg keys: String): ClipData {
        val clip = ClipData(ClipDescription("drop", arrayOf(Shared.files.getValue(keys.first()).type)), ClipData.Item(address(keys.first())))
        keys.drop(1).forEach { key -> clip.addItem(ClipData.Item(address(key))) }
        return clip
    }

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/$key")

    /** The files copied into the map's folder so far. */
    private fun copied(): List<File> = File(maps.folder(id), "media").listFiles().orEmpty().filter { it.isFile }

    /** Lets go of `keys` on the map, and cancels the drop as the held one starts to be copied: the first is in by then. */
    private fun cancelledAtTheSecond(vararg keys: String) = runBlocking {
        Shared.held = keys[1]
        val landing = async(Dispatchers.Default) { drops.land(clip(*keys), resolver, own = null, x = 10f, y = 20f) }

        try {
            assertTrue("The second copy never started", Shared.opened.await(10, TimeUnit.SECONDS))
            drops.cancel()
        } finally {
            Shared.release.countDown()
        }

        withTimeout(10_000) { landing.await() }
    }

    @Test
    fun `sounds cancelled as the second is copied leave the map's room as it was`() {
        Shared.files["one"] = Shared.Lent("One.mp3", "audio/mpeg", ByteArray(64_000))
        Shared.files["two"] = Shared.Lent("Two.mp3", "audio/mpeg", ByteArray(96_000))

        cancelledAtTheSecond("one", "two")

        // The first sound came in, and stays for the map's tidying up, but it's no idea's and takes none of the room.
        assertEquals(listOf(64_000L), copied().map { it.length() })
        assertFalse(page.commands.any { it is MapCommand.AddNodes })
        assertEquals(0L, maps.mediaSize(id))
    }

    @Test
    fun `files cancelled as the second is copied leave the map's room as it was`() {
        Shared.files["one"] = Shared.Lent("One.pdf", "application/pdf", ByteArray(64_000))
        Shared.files["two"] = Shared.Lent("Two.pdf", "application/pdf", ByteArray(96_000))

        cancelledAtTheSecond("one", "two")

        assertEquals(listOf(64_000L), copied().map { it.length() })
        assertFalse(page.commands.any { it is MapCommand.AddFiles })
        assertEquals(0L, maps.mediaSize(id))
    }

    @Test
    fun `sounds cancelled while the page adds their ideas leave the map's room as it was`() = runBlocking {
        Shared.files["one"] = Shared.Lent("One.mp3", "audio/mpeg", ByteArray(64_000))
        Shared.files["two"] = Shared.Lent("Two.mp3", "audio/mpeg", ByteArray(96_000))
        val landing = async(Dispatchers.Default) { drops.land(clip("one", "two"), resolver, own = null, x = 10f, y = 20f) }

        assertTrue("The page was never asked to add the ideas", page.asked.await(10, TimeUnit.SECONDS))
        drops.cancel()
        withTimeout(10_000) { landing.await() }

        // The page may yet add them, late: their files stay, and take none of the map's room as coming in.
        assertEquals(setOf(64_000L, 96_000L), copied().map { it.length() }.toSet())
        assertEquals(0L, maps.mediaSize(id))
    }

    @Test
    fun `animated pictures cancelled as the second is read leave the map's room as it was`() {
        Shared.files["one"] = Shared.Lent("One.gif", "image/gif", animation(48_000))
        Shared.files["two"] = Shared.Lent("Two.gif", "image/gif", animation(96_000))

        // The second goes on being copied once it's let read, past the moment it would notice it was stopped.
        cancelledAtTheSecond("one", "two")

        assertEquals(setOf(48_000L, 96_000L), copied().map { it.length() }.toSet())
        assertFalse(page.commands.any { it is MapCommand.AddNodes })
        assertEquals(0L, maps.mediaSize(id))
    }

    @Test
    fun `a video made smaller, stopped right as it's made, takes none of the map's room`() = runBlocking {
        val making = scope.launch {
            maps.addMadeVideo(id, estimate = 50_000) { output ->
                output.writeBytes(ByteArray(50_000))
                // Stop, pressed as the last of it was written: nothing after this notices.
                currentCoroutineContext().cancel()
            }
        }

        making.join()

        assertTrue(making.isCancelled)
        assertEquals(listOf(50_000L), copied().filter { it.extension == "mp4" }.map { it.length() })
        assertEquals(0L, maps.mediaSize(id))
    }

    /** An animated GIF of two frames, `size` bytes long with what follows its end. */
    private fun animation(size: Int): ByteArray {
        val frame = byteArrayOf(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0)
        val gif = "GIF89a".toByteArray() + byteArrayOf(1, 0, 1, 0, 0x80.toByte(), 0, 0, 0, 0, 0, -1, -1, -1) + frame + frame + byteArrayOf(0x3B)
        return gif + ByteArray(size - gif.size)
    }

    private companion object {
        const val AUTHORITY = "com.hyuchia.orbit.test.dropcancel"
    }
}
