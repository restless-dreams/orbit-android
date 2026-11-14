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
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.media.VideoMaker
import com.hyuchia.orbit.page.DropPlace
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapFile
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.protocolJson
import com.hyuchia.orbit.sketch.InkSample
import com.hyuchia.orbit.sketch.SketchSession
import com.hyuchia.orbit.sketch.SketchTouch
import com.hyuchia.orbit.sketch.TouchTool
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pictures, sounds and files take a while to copy in, from a cloud drive
 * say, and meanwhile their idea may be renamed (new text, a new id), another
 * idea may take its old id, or it may be deleted. What lands late lands on
 * the idea it was for, under the id it has then, or says it couldn't, and
 * its copy goes: never "Added" for nothing. And a picture that lands while
 * its idea is sketched on is asked about before the sketch takes its place.
 */
@RunWith(AndroidJUnit4::class)
class MediaTargetsTest {
    /** Another app's files, each read only once the test lets it ([HELD]), as a slow provider's are. */
    class SlowGallery : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val file = FILES[uri.lastPathSegment] ?: return null
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(file.name, file.length())) }
        }

        override fun getType(uri: Uri): String? = FILES[uri.lastPathSegment]?.let { TYPES[it.extension] }

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

    private val root: File = Files.createTempDirectory("orbit-media-targets").toFile()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ui = MutableStateFlow(MapUi())
    private val pictures = PictureTurns()
    private val ideas = FollowedIdeas()
    private val said: MutableList<MapEvent> = Collections.synchronizedList(mutableListOf())

    /** The page, as far as these copies reach it: which ideas it has, and what it took onto them. */
    private val page = TestPage()

    private lateinit var id: String

    private val adding by lazy {
        MediaAdding(
            scope = scope,
            ui = ui,
            maps = maps,
            id = id,
            bigVideos = BigVideos({ VideoMaker.NONE }, SmallerOffers()),
            maker = { VideoMaker.NONE },
            words = words,
            change = ::change,
            giveMotion = { nodeId, _, motion -> change(nodeId, "image", motion) },
            pictures = pictures,
            addFiles = { nodeId, files -> page.addFiles(nodeId, files) },
            say = { said += it },
            untitled = { "Untitled" },
            ideas = ideas,
        )
    }

    private val drops by lazy {
        MapDrops(
            scope = scope,
            mapId = id,
            maps = maps,
            importer = { error("No map is let go here") },
            words = words,
            ui = ui,
            locked = MutableStateFlow(false),
            page = { page },
            change = ::change,
            tell = { said += it },
            pictures = pictures,
            ideas = ideas,
        )
    }

    @Before
    fun start() = runBlocking {
        Robolectric.setupContentProvider(SlowGallery::class.java, AUTHORITY)
        FILES.clear()
        HELD.clear()
        REACHED.clear()
        maps.load()
        id = maps.add("# Trip\n\n- Tokio\n- Kyoto\n")
        maps.showing(id, true)
        page.ideas += listOf("trip", "tokio", "kyoto")
        showMap()
    }

    @After
    fun cleanUp() {
        HELD.values.forEach { it.countDown() }
        runBlocking { scope.coroutineContext.job.children.toList().joinAll() }
        root.deleteRecursively()
    }

    /** The map as the page has it, as the screen's state hears it. */
    private fun showMap(images: Map<String, Media> = emptyMap()) {
        val nodes = page.ideas.toList().map { idea -> MapNode(idea, idea.replaceFirstChar(Char::uppercase), parentId = "trip".takeIf { idea != "trip" }, depth = if (idea == "trip") 0 else 1, image = images[idea]) }
        ui.update { it.copy(ready = true, map = MapState("Trip", nodes, path = listOf("trip"))) }
    }

    /** A change to an idea the page answers, as the map's screen makes it (MapViewModel.change): a picture set ends the turns on it. */
    private fun change(nodeId: String, key: String, value: JsonElement?): Boolean {
        if (key == "image") {
            pictures.changed(nodeId, value)
        }

        return page.change(nodeId, key, value)
    }

    /** A sound file, as an MP3 starts. */
    private fun sound(name: String): File = File(root, name).apply { writeBytes("ID3".toByteArray() + ByteArray(4_096)) }

    /** New text gives idea `from` the id `to`, as the page says in a save: the map's screen follows it (MapViewModel.follow). */
    private fun renamed(from: String, to: String) {
        page.ideas -= from
        page.ideas += to
        ideas.renamed(from, to)
        pictures.renamed(from, to)
        showMap()
    }

    /** Idea `id` is removed, as a save says by leaving it out: the map's screen ends what was on its way to it (MapViewModel.saved). */
    private fun removed(id: String) {
        page.ideas -= id
        ideas.removed(listOf(id))
        pictures.removed(listOf(id))
        showMap()
    }

    /** Kyoto is removed while what's copied in for it is held, and Tokio renamed Kyoto after it: then the copy is let through. */
    private suspend fun removedThenReused(key: String) {
        reached(key)
        removed("kyoto")
        renamed("tokio", "kyoto")
        released(key)
    }

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/files/$key")

    private fun slow(key: String, file: File) {
        FILES[key] = file
        HELD[key] = CountDownLatch(1)
        REACHED[key] = CountDownLatch(1)
    }

    private suspend fun reached(key: String) = withContext(Dispatchers.IO) { assertTrue(REACHED.getValue(key).await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)) }

    private suspend fun released(key: String) {
        HELD.getValue(key).countDown()
        scope.coroutineContext.job.children.toList().joinAll()
    }

    private fun media(): List<String> = File(maps.folder(id), "media").list().orEmpty().sorted()

    private fun messages(): List<ShownText> = synchronized(said) { said.filterIsInstance<MapEvent.Snackbar>().map { it.message } }

    @Test
    fun `a picture copied in while its idea is renamed lands on it under its new id, never on the idea that took its old one`() = runBlocking {
        slow("lake", File("src/test/resources/animations/orbit.webp"))
        adding.addPicture("tokio", address("lake"), context.contentResolver, pasted = true)
        reached("lake")

        // Tokio is spelled Tokyo now, and Kyoto is renamed Tokio: the old id is another idea's.
        renamed("tokio", "tokyo")
        renamed("kyoto", "tokio")
        released("lake")

        val (nodeId, key, value) = page.taken.single()
        assertEquals("tokyo" to "image", nodeId to key)
        assertTrue(media().contains(value!!.jsonObject.getValue("src").jsonPrimitive.content.removePrefix("media/")))
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.map_pasted_the_picture)), messages())
    }

    @Test
    fun `a picture whose idea was deleted while it copied in says so, and its copy goes`() = runBlocking {
        slow("lake", File("src/test/resources/animations/orbit.webp"))
        adding.addPicture("kyoto", address("lake"), context.contentResolver, pasted = true)
        reached("lake")

        page.ideas -= "kyoto"
        showMap()
        released("lake")

        assertTrue(page.taken.isEmpty())
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.map_picture_idea_gone)), messages())
        assertEquals(emptyList<String>(), media())
    }

    @Test
    fun `a sound for an idea removed meanwhile never lands on the idea renamed to its id after`() = runBlocking {
        slow("song", sound("song.mp3"))
        adding.addSound("kyoto", address("song"), context.contentResolver)
        removedThenReused("song")

        assertTrue(page.taken.isEmpty())
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.map_sound_idea_gone)), messages())
        assertEquals(emptyList<String>(), media())
    }

    @Test
    fun `files for an idea removed meanwhile never join the idea renamed to its id after`() = runBlocking {
        slow("ticket", File("src/test/resources/animations/still.gif").copyTo(File(root, "ticket.pdf")))
        adding.addFiles("kyoto", listOf(address("ticket")), context.contentResolver)
        removedThenReused("ticket")

        assertTrue(page.filesAdded.isEmpty())
        assertEquals(listOf<ShownText>(ShownText.Plural(R.plurals.files_idea_gone, 1, listOf(1))), messages())
        assertEquals(emptyList<String>(), media())
    }

    @Test
    fun `a picture for an idea removed meanwhile says so, rather than stay out unsaid, and never lands on the idea renamed to its id`() = runBlocking {
        slow("lake", File("src/test/resources/animations/orbit.webp"))
        adding.addPicture("kyoto", address("lake"), context.contentResolver, pasted = true)
        removedThenReused("lake")

        assertTrue(page.taken.isEmpty())
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.map_picture_idea_gone)), messages())
        assertEquals(emptyList<String>(), media())
    }

    @Test
    fun `a sound let go on an idea removed meanwhile never lands on the idea renamed to its id after`() = runBlocking {
        slow("song", sound("song.mp3"))
        page.dropOn = "kyoto"
        val landing = scope.async { drops.land(ClipData.newRawUri("song", address("song")), context.contentResolver, own = null, x = 10f, y = 20f) }
        reached("song")
        removed("kyoto")
        renamed("tokio", "kyoto")
        HELD.getValue("song").countDown()
        withTimeout(WAIT_MILLISECONDS) { landing.await() }

        assertTrue(page.taken.isEmpty())
        assertFalse(said.contains(MapEvent.Added))
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.map_sound_idea_gone)), messages())
    }

    @Test
    fun `a sound whose page doesn't answer says why, and leaves its copy for the tidying up`() = runBlocking {
        slow("song", sound("song.mp3"))
        adding.addSound("kyoto", address("song"), context.contentResolver)
        reached("song")

        page.answers = false
        released("song")

        assertEquals(listOf<ShownText>(ShownText.Plain("The page didn't answer")), messages())
        // Not deleted: a page that answers late may yet have put it on the idea.
        assertEquals(1, media().size)
    }

    @Test
    fun `files copied in for an idea renamed meanwhile join it, and files for one deleted say so and go`() = runBlocking {
        slow("ticket", File("src/test/resources/animations/still.gif").copyTo(File(root, "ticket.pdf")))
        adding.addFiles("tokio", listOf(address("ticket")), context.contentResolver)
        reached("ticket")
        renamed("tokio", "tokyo")
        released("ticket")

        assertEquals(listOf("tokyo"), page.filesAdded.map { it.first })

        slow("visa", File("src/test/resources/animations/still.gif").copyTo(File(root, "visa.pdf")))
        adding.addFiles("kyoto", listOf(address("visa")), context.contentResolver)
        reached("visa")
        page.ideas -= "kyoto"
        released("visa")

        assertEquals(1, page.filesAdded.size)
        assertEquals(ShownText.Plural(R.plurals.files_idea_gone, 1, listOf(1)), messages().single())
        assertEquals(1, media().size)
    }

    @Test
    fun `a sound let go on an idea deleted while it copied in says so, and leaves the map's room as it was`() = runBlocking {
        slow("song", sound("song.mp3"))
        page.dropOn = "kyoto"
        val before = maps.mediaSizeNow(id)
        val landing = scope.async { drops.land(ClipData.newRawUri("song", address("song")), context.contentResolver, own = null, x = 10f, y = 20f) }
        reached("song")

        page.ideas -= "kyoto"
        HELD.getValue("song").countDown()
        withTimeout(WAIT_MILLISECONDS) { landing.await() }

        assertTrue(page.taken.isEmpty())
        assertFalse(said.contains(MapEvent.Added))
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.map_sound_idea_gone)), messages())
        assertEquals(before, maps.mediaSizeNow(id))
        assertEquals(emptyList<String>(), media())
    }

    @Test
    fun `a file let go on an idea deleted while it copied in says so, and leaves the map's room as it was`() = runBlocking {
        slow("ticket", File("src/test/resources/animations/still.gif").copyTo(File(root, "ticket.pdf")))
        page.dropOn = "kyoto"
        val before = maps.mediaSizeNow(id)
        val landing = scope.async { drops.land(ClipData.newRawUri("ticket", address("ticket")), context.contentResolver, own = null, x = 10f, y = 20f) }
        reached("ticket")

        page.ideas -= "kyoto"
        HELD.getValue("ticket").countDown()
        withTimeout(WAIT_MILLISECONDS) { landing.await() }

        assertTrue(page.filesAdded.isEmpty())
        assertFalse(said.contains(MapEvent.Added))
        assertEquals(listOf<ShownText>(ShownText.Plural(R.plurals.files_idea_gone, 1, listOf(1))), messages())
        assertEquals(before, maps.mediaSizeNow(id))
    }

    @Test
    fun `a picture let go on an idea renamed while it copied in lands on it under its new id`() = runBlocking {
        slow("lake", File("src/test/resources/animations/orbit.webp"))
        page.dropOn = "tokio"
        val landing = scope.async { drops.land(ClipData.newRawUri("lake", address("lake")), context.contentResolver, own = null, x = 10f, y = 20f) }
        reached("lake")

        renamed("tokio", "tokyo")
        renamed("kyoto", "tokio")
        HELD.getValue("lake").countDown()
        withTimeout(WAIT_MILLISECONDS) { landing.await() }

        assertEquals(listOf("tokyo" to "image"), page.taken.map { (nodeId, key) -> nodeId to key })
        assertEquals(listOf<ShownText>(ShownText.Resource(R.string.drop_added_the_picture, listOf("Tokyo"))), messages())
    }

    @Test
    fun `a picture that lands while its idea is sketched on is asked about before the sketch takes its place`() = runBlocking {
        slow("lake", File("src/test/resources/animations/orbit.webp"))
        val sketching = Sketching(scope, ui, maps, id, { page }, ::change, { said += it }, { src -> File(maps.folder(id), src) }, pictures)

        // Chosen in the editor, the picture takes a while; meanwhile the idea is sketched on, with no picture yet.
        adding.addPicture("kyoto", address("lake"), context.contentResolver)
        reached("lake")
        sketching.sketch("kyoto")
        sketching.spotlight(MapMessage.Spotlight("kyoto", "rest", 200f, 300f, 150f))
        val session = sketching.session.value!!
        session.stroke()

        // The picture lands, under the canvas.
        released("lake")
        val late = page.taken.single().third!!.jsonObject.getValue("src").jsonPrimitive.content
        showMap(mapOf("kyoto" to Media(late, caption = "The lake")))

        // Done asks first, and the picture stays until the person says so.
        sketching.save("A line")
        until { ui.value.sketch?.confirming == true }
        assertEquals(SketchPhase.Drawing, ui.value.sketch?.phase)
        assertEquals(1, page.taken.size)

        // Keep drawing: still asked about, on the next Done.
        sketching.keepDrawing()
        sketching.save("A line")
        until { ui.value.sketch?.confirming == true }
        assertEquals(1, page.taken.size)

        // Replace: the sketch takes its place, saying so, with Undo.
        sketching.replaceLanded()
        until { page.taken.size == 2 }
        val sketch = page.taken.last().third!!.jsonObject
        assertTrue(sketch.getValue("src").jsonPrimitive.content.startsWith("media/sketch-"))
        assertEquals("A line", sketch.getValue("alt").jsonPrimitive.content)
        until { messages().contains(ShownText.Resource(R.string.sketch_replaced_the_picture)) }
        assertTrue(said.filterIsInstance<MapEvent.Snackbar>().single { it.message == ShownText.Resource(R.string.sketch_replaced_the_picture) }.undo)
    }

    private suspend fun until(done: () -> Boolean) {
        withTimeout(WAIT_MILLISECONDS) {
            while (!done()) {
                delay(20)
            }
        }
    }

    private fun SketchSession.stroke() {
        touch(SketchTouch.Down(1, TouchTool.Finger, InkSample(200f, 700f, null, 0f, 0)))
        val samples = (1..20).map { InkSample(200f + it * 30f, 700f, null, 0f, it * 8L) }
        touch(SketchTouch.Move(1, samples))
        touch(SketchTouch.Up(1, samples.last()))
    }

    /**
     * The page as the copies reach it: it takes a change onto an idea it has,
     * and says it can't for one it doesn't, as the real page answers a change
     * with a job; it says where a drop goes, and that a picture shows.
     */
    private class TestPage : MapPage {
        override var listener: ((MapMessage) -> Unit)? = null
        override var incognito = false

        val ideas: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        /** What the page took onto its ideas, in order. */
        val taken: MutableList<Triple<String, String, JsonElement?>> = Collections.synchronizedList(mutableListOf())

        val filesAdded: MutableList<Pair<String, List<MapFile>>> = Collections.synchronizedList(mutableListOf())

        /** Whether the page answers at all, as one that stopped doesn't. */
        @Volatile
        var answers = true

        /** The idea a drop lands on. */
        @Volatile
        var dropOn = "tokio"

        fun change(nodeId: String, key: String, value: JsonElement?): Boolean {
            if (!answers) {
                throw PageException("The page didn't answer", PageException.Kind.DidNotAnswer)
            }

            if (nodeId !in ideas) {
                return false
            }

            taken += Triple(nodeId, key, value)
            return true
        }

        fun addFiles(nodeId: String, files: List<MapFile>): Boolean {
            if (!answers) {
                throw PageException("The page didn't answer", PageException.Kind.DidNotAnswer)
            }

            if (nodeId !in ideas) {
                return false
            }

            filesAdded += nodeId to files
            return true
        }

        override fun send(command: MapCommand) = Unit

        override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
            val value: JsonElement = when (val sent = command(1)) {
                is MapCommand.DropAt -> protocolJson.encodeToJsonElement(DropPlace.serializer(), DropPlace(dropOn))
                is MapCommand.AddFiles -> JsonPrimitive(addFiles(sent.id, sent.files))
                is MapCommand.PictureShown -> JsonPrimitive(true)
                else -> error("This page answers no ${sent::class.simpleName}")
            }

            return protocolJson.decodeFromJsonElement(answer, value)
        }

        override suspend fun drawn() = Unit
    }

    private companion object {
        const val AUTHORITY = "com.example.mediatargets"
        const val WAIT_MILLISECONDS = 10_000L

        val TYPES = mapOf("webp" to "image/webp", "gif" to "image/gif", "mp3" to "audio/mpeg", "pdf" to "application/pdf")

        /** The other app's files, by the last part of their address. */
        val FILES = mutableMapOf<String, File>()

        /** The files the other app holds back until the test lets them go. */
        val HELD = mutableMapOf<String, CountDownLatch>()

        /** The files the test waits for the other app to be asked for. */
        val REACHED = mutableMapOf<String, CountDownLatch>()
    }
}
