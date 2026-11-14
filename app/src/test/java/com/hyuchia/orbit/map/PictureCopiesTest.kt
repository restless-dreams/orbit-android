package com.hyuchia.orbit.map

import android.content.ClipData
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
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.media.VideoMaker
import com.hyuchia.orbit.page.DropPlace
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.protocolJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pictures and live photos' movies from the phone take a while to copy in,
 * from a cloud provider say: one that finishes after a newer picture went on
 * its idea never puts itself over it, nor gives its motion to it.
 */
@RunWith(AndroidJUnit4::class)
class PictureCopiesTest {
    /** Another app's files, each read only once the test lets it ([HELD]), as a slow provider's are. */
    class SlowGallery : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val file = FILES[uri.lastPathSegment] ?: return null
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(file.name, file.length())) }
        }

        override fun getType(uri: Uri): String? = FILES[uri.lastPathSegment]?.let { if (it.name.endsWith(".mov")) "video/quicktime" else "image/${it.extension}" }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = FILES[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())
            REACHED[uri.lastPathSegment]?.countDown()
            HELD[uri.lastPathSegment]?.await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private val root: File = Files.createTempDirectory("orbit-picture-copies").toFile()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)

    /** Where the copies run, as the map's screen runs them: each ends before the test does. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** What the copies changed on the map: each idea's attribute and its value, and the picture a motion was for. */
    private val updates = mutableListOf<Triple<String, String, JsonElement?>>()

    private val motions = mutableListOf<Pair<String, JsonObject>>()

    /** Keeps a change the page takes, as the page says once the idea has it. */
    private fun taken(nodeId: String, key: String, value: JsonElement?): Boolean {
        synchronized(updates) { updates += Triple(nodeId, key, value) }
        return true
    }

    private val ui = MutableStateFlow(MapUi())
    private val pictures = PictureTurns()

    private val adding = MediaAdding(
        scope = scope,
        ui = ui,
        maps = maps,
        id = "trip",
        bigVideos = BigVideos({ VideoMaker.NONE }, SmallerOffers()),
        maker = { VideoMaker.NONE },
        words = words,
        change = { nodeId, key, value -> taken(nodeId, key, value) },
        giveMotion = { _, picture, change ->
            synchronized(motions) { motions += picture to change }
            true
        },
        pictures = pictures,
        addFiles = { _, _ -> true },
        say = {},
        untitled = { "Untitled" },
    )

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/files/$key")

    private fun animation(name: String): File = File("src/test/resources/animations/$name")

    private fun media(): List<String> = File(maps.folder("trip"), "media").list().orEmpty().sorted()

    /** The lake as the page says it is: with `image` for its picture. */
    private fun lake(image: String?) {
        ui.update { MapUi(ready = true, map = MapState("Trip", listOf(MapNode("trip", "Trip"), MapNode("lake", "Lake", parentId = "trip", depth = 1, image = image?.let(::Media))))) }
    }

    private suspend fun until(done: () -> Boolean) {
        withTimeout(WAIT_MILLISECONDS) {
            while (!done()) {
                delay(20)
            }
        }
    }

    @Before
    fun start() = runBlocking {
        Robolectric.setupContentProvider(SlowGallery::class.java, AUTHORITY)
        FILES.clear()
        HELD.clear()
        REACHED.clear()
        maps.load()
        File(maps.folder("trip").also { it.mkdirs() }, "map.md").writeText("# Trip\n\n- Lake\n\n  ![](media/picture-1.jpg)\n")
        File(maps.folder("trip"), "media").mkdirs()
        File(maps.folder("trip"), "media/picture-1.jpg").writeBytes(ByteArray(1_000))
        maps.load()
    }

    @After
    fun cleanUp() {
        HELD.values.forEach { it.countDown() }
        runBlocking { scope.coroutineContext.job.children.toList().joinAll() }
        root.deleteRecursively()
    }

    @Test
    fun `a picture whose copy finishes after a newer one went on its idea stays out, and so does its copy`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["slow"] = animation("orbit.gif")
        FILES["quick"] = animation("orbit.webp")
        HELD["slow"] = CountDownLatch(1)

        // The first picture chosen comes from far away; the second, chosen next, is copied in at once.
        adding.addPicture("lake", address("slow"), context.contentResolver)
        adding.addPicture("lake", address("quick"), context.contentResolver)
        until { synchronized(updates) { updates.size == 1 } }
        val quick = updates.single().third!!.jsonObject.getValue("src").jsonPrimitive.content
        assertTrue(quick, quick.endsWith(".webp"))

        // The first one's copy finishes now: the idea keeps the newer picture, and the late copy goes.
        HELD.getValue("slow").countDown()
        scope.coroutineContext.job.children.toList().joinAll()

        assertEquals(listOf("lake" to "image"), updates.map { (id, key) -> id to key })
        assertEquals(listOf("picture-1.jpg", quick.removePrefix("media/")), media())
    }

    @Test
    fun `a picture let go on an idea whose copy finishes after a newer one went on it stays out too`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["slow"] = animation("orbit.gif")
        FILES["quick"] = animation("orbit.webp")
        HELD["slow"] = CountDownLatch(1)
        REACHED["slow"] = CountDownLatch(1)

        val drops = MapDrops(
            scope = scope,
            mapId = "trip",
            maps = maps,
            importer = { error("No map is let go here") },
            words = words,
            ui = ui,
            locked = MutableStateFlow(false),
            page = { DropPage },
            change = { nodeId, key, value -> taken(nodeId, key, value) },
            tell = {},
            pictures = pictures,
        )

        // Let go on the lake from a cloud drive, it takes a while; meanwhile a picture from the phone goes on the lake.
        val landing = scope.async { drops.land(ClipData.newRawUri("picture", address("slow")), context.contentResolver, own = null, x = 120f, y = 300f) }
        withContext(Dispatchers.IO) { REACHED.getValue("slow").await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS) }
        adding.addPicture("lake", address("quick"), context.contentResolver)
        until { synchronized(updates) { updates.size == 1 } }
        val quick = updates.single().third!!.jsonObject.getValue("src").jsonPrimitive.content

        HELD.getValue("slow").countDown()
        withTimeout(WAIT_MILLISECONDS) { landing.await() }
        scope.coroutineContext.job.children.toList().joinAll()

        assertEquals(1, updates.size)
        assertEquals(listOf("picture-1.jpg", quick.removePrefix("media/")), media())
    }

    @Test
    fun `a caption or description typed while a picture copies in keeps the picture coming`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["slow"] = animation("orbit.gif")
        HELD["slow"] = CountDownLatch(1)
        REACHED["slow"] = CountDownLatch(1)

        adding.addPicture("lake", address("slow"), context.contentResolver)
        withContext(Dispatchers.IO) { REACHED.getValue("slow").await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS) }

        // The editor saves the description as it's typed, and the caption: changes to the picture there is, as the map's
        // screen sends them (MapViewModel.update).
        pictures.changed("lake", mediaChange("alt", "The lake at dawn"))
        pictures.changed("lake", mediaChange("caption", null))
        HELD.getValue("slow").countDown()
        scope.coroutineContext.job.children.toList().joinAll()

        val picture = updates.single().third!!.jsonObject.getValue("src").jsonPrimitive.content
        assertTrue(picture, picture.endsWith(".gif"))
        assertTrue(media().contains(picture.removePrefix("media/")))

        // Taken off, or another picture, it's over.
        assertFalse(pictures.current(pictures.now("lake").also { pictures.changed("lake", null) }))
    }

    @Test
    fun `a live photo's movie chosen while a new picture copies in leaves that picture coming`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["slow"] = animation("orbit.gif")
        FILES["movie"] = File("src/test/resources/live/iphone-live.mov")
        HELD["slow"] = CountDownLatch(1)
        REACHED["slow"] = CountDownLatch(1)

        adding.addPicture("lake", address("slow"), context.contentResolver)
        withContext(Dispatchers.IO) { REACHED.getValue("slow").await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS) }
        adding.addMotion("lake", address("movie"), context.contentResolver)
        until { synchronized(motions) { motions.size == 1 } }

        HELD.getValue("slow").countDown()
        scope.coroutineContext.job.children.toList().joinAll()

        // The movie went onto the picture it was chosen for, and the new picture still came.
        assertEquals("media/picture-1.jpg", motions.single().first)
        assertTrue(updates.single().third!!.jsonObject.getValue("src").jsonPrimitive.content.endsWith(".gif"))
    }

    @Test
    fun `a picture let go on the map that the map no longer takes once it's in leaves the map's room, and its file for the tidying up`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["slow"] = animation("orbit.gif")
        HELD["slow"] = CountDownLatch(1)
        REACHED["slow"] = CountDownLatch(1)
        val before = maps.mediaSizeNow("trip")

        val drops = MapDrops(
            scope = scope,
            mapId = "trip",
            maps = maps,
            importer = { error("No map is let go here") },
            words = words,
            ui = ui,
            locked = MutableStateFlow(false),
            page = { DropPage },
            change = { nodeId, key, value -> taken(nodeId, key, value) },
            tell = {},
            pictures = pictures,
        )

        val landing = scope.async { drops.land(ClipData.newRawUri("picture", address("slow")), context.contentResolver, own = null, x = 120f, y = 300f) }
        withContext(Dispatchers.IO) { REACHED.getValue("slow").await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS) }

        // A presentation's path begins to be made while it's copied in: the map takes no drop by the time it's in.
        ui.update { it.copy(pathMaking = true) }
        HELD.getValue("slow").countDown()
        withTimeout(WAIT_MILLISECONDS) { landing.await() }

        assertTrue(updates.isEmpty())
        assertEquals(before, maps.mediaSizeNow("trip"))
        assertTrue(media().any { it.endsWith(".gif") })
    }

    @Test
    fun `a live photo's movie copied in for a picture that was replaced meanwhile isn't given to the new one`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["movie"] = File("src/test/resources/live/iphone-live.mov")
        FILES["quick"] = animation("orbit.webp")
        HELD["movie"] = CountDownLatch(1)

        adding.addMotion("lake", address("movie"), context.contentResolver)
        adding.addPicture("lake", address("quick"), context.contentResolver)
        until { synchronized(updates) { updates.size == 1 } }
        val quick = updates.single().third!!.jsonObject.getValue("src").jsonPrimitive.content
        lake(quick)

        HELD.getValue("movie").countDown()
        scope.coroutineContext.job.children.toList().joinAll()

        assertTrue(motions.isEmpty())
        assertFalse(media().toString(), media().any { it.contains("-motion.") })
    }

    @Test
    fun `a live photo's movie goes onto its picture, and only while it's still that picture`() = runBlocking {
        lake("media/picture-1.jpg")
        FILES["movie"] = File("src/test/resources/live/iphone-live.mov")
        HELD["movie"] = CountDownLatch(1)

        adding.addMotion("lake", address("movie"), context.contentResolver)
        HELD.getValue("movie").countDown()
        scope.coroutineContext.job.children.toList().joinAll()

        val (picture, change) = motions.single()
        assertEquals("media/picture-1.jpg", picture)
        assertTrue(change.getValue("motion").jsonObject.getValue("src").jsonPrimitive.content.startsWith("media/picture-1-motion."))

        // A picture set on the idea some other way, as a sketch's, ends the turn of a movie still being copied in.
        HELD["movie"] = CountDownLatch(1)
        adding.addMotion("lake", address("movie"), context.contentResolver)
        pictures.changed("lake", buildJsonObject { put("src", "media/sketch-1.svg") })
        HELD.getValue("movie").countDown()
        scope.coroutineContext.job.children.toList().joinAll()
        assertEquals(1, motions.size)
    }

    /** A page that says a drop lands on the lake. */
    private object DropPage : MapPage {
        override var listener: ((MapMessage) -> Unit)? = null
        override var incognito = false

        override fun send(command: MapCommand) = Unit

        override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
            val sent = command(1)
            check(sent is MapCommand.DropAt) { "This page answers no ${sent::class.simpleName}" }
            return protocolJson.decodeFromJsonElement(answer, protocolJson.encodeToJsonElement(DropPlace.serializer(), DropPlace("lake")))
        }

        override suspend fun drawn() = Unit
    }

    private companion object {
        const val AUTHORITY = "com.example.slowgallery"
        const val WAIT_MILLISECONDS = 10_000L

        /** The other app's files, by the last part of their address. */
        val FILES = mutableMapOf<String, File>()

        /** The files the other app holds back until the test lets them go. */
        val HELD = mutableMapOf<String, CountDownLatch>()

        /** The files the test waits for the other app to be asked for. */
        val REACHED = mutableMapOf<String, CountDownLatch>()
    }
}
