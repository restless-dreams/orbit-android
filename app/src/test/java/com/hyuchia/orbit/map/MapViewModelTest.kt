package com.hyuchia.orbit.map

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.FolderSync
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.data.settingsStoreIn
import com.hyuchia.orbit.media.SoundPlayback
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.CheckedMap
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.Renamed
import com.hyuchia.orbit.page.protocolJson
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.study.StudyCard
import com.hyuchia.orbit.ui.TimeWords
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.Executors

/** How long a test waits for the view model to do what it should, before it fails. */
private const val WAIT_MILLISECONDS = 10_000L

/** YAML, as the test page's conversions write it: the markdown with this in front. */
private const val YAML = "yaml: "

/**
 * The map's view model, made through its seam: real maps and settings on
 * files, a page that keeps what it's sent and says what the test tells it to,
 * from the examples the pages' build writes (page-protocol.json).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val root: File = Files.createTempDirectory("orbit-map-view-model").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))

    /** The main thread, as Android has one: the view model's work runs on it, one piece at a time. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val app = TestApp(words)

    /** The view models the test made: each one's work ends before the test does. */
    private val models = mutableListOf<MapViewModel>()

    /** The page's messages the pages' build wrote as examples, by their type. */
    private val messages: Map<String, MapMessage> =
        protocolJson.parseToJsonElement(
            requireNotNull(MapViewModelTest::class.java.classLoader?.getResource("page-protocol.json")) { "No page-protocol.json on the test classpath: build web/" }.readText(),
        ).jsonObject.getValue("map").jsonObject.getValue("messages").jsonArray.associate { example ->
            example.jsonObject.getValue("type").jsonPrimitive.content to protocolJson.decodeFromJsonElement(MapMessage.serializer(), example)
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun cleanUp() {
        // What the view models and the app's scope are still doing ends first, while the test's main thread is there to
        // finish it on. Left running, it would end after the test with no main thread to go back to, a failure on a
        // thread of its own that ends the whole test run, or write into a folder that's gone.
        runBlocking {
            for (model in models) {
                model.viewModelScope.coroutineContext.job.cancelAndJoin()
            }

            app.applicationScope.coroutineContext.job.cancelAndJoin()
        }

        Dispatchers.resetMain()
        main.close()
        root.deleteRecursively()
    }

    /** The page's ready message as the pages' build wrote it: for a map from a newer Orbit, which can only be looked at. */
    private val newerReady: MapMessage.Ready get() = messages.getValue("ready") as MapMessage.Ready

    /** The page's ready message for a map that can be changed. */
    private val ready: MapMessage.Ready get() = newerReady.copy(state = newerReady.state.copy(readOnly = null, problem = null))

    /** A map the page saved: the build's example, made on the map as the page last drew it, which can be changed. */
    private fun saved(markdown: String, revision: Int?): MapMessage.Saved {
        val example = messages.getValue("saved") as MapMessage.Saved
        return example.copy(markdown = markdown, state = example.state.copy(readOnly = null, problem = null), revision = revision)
    }

    /** A map open on its screen, its page, what it was first drawn with, and where its screen keeps it, which closes it. */
    private class Opened(val model: MapViewModel, val page: TestPage, val load: MapCommand.Load, val store: ViewModelStore)

    /**
     * Opens map `id` as its screen does once it's laid out, saying what it
     * covers, then its colors and settings, and waits for its page to be sent
     * the map. The page says it's ready, unless `ready` is false.
     */
    private suspend fun open(id: String, ready: Boolean = true): Opened {
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { MapViewModel(id, maps, settings, app) } }
        val model = withContext(Dispatchers.Main) { ViewModelProvider.create(store, factory)[MapViewModel::class] }
        models += model
        val page = app.pages.last()

        withContext(Dispatchers.Main) {
            model.setInsets(MapInsets(top = 64f, bottom = 96f))
            model.configure(AppTheme(dark = false, tokens = emptyMap()), MapSettings(haptics = true, snap = true, longPressDelay = 400))
        }

        val load = withTimeout(WAIT_MILLISECONDS) { page.commands.receiveAsFlow().filterIsInstance<MapCommand.Load>().first() }

        if (ready) {
            withContext(Dispatchers.Main) { page.say(this@MapViewModelTest.ready) }
        }

        return Opened(model, page, load, store)
    }

    /** Closes the map's screen, as Back does, a tablet's close, or another map opened beside the list. */
    private suspend fun close(opened: Opened) {
        withContext(Dispatchers.Main) { opened.store.clear() }
    }

    /** Opens the map's text in the source editor, as YAML with `yaml`, once Orbit has converted it. */
    private suspend fun editAsText(opened: Opened, yaml: Boolean = false) {
        withContext(Dispatchers.Main) { opened.model.openSource() }

        if (yaml) {
            withContext(Dispatchers.Main) { opened.model.setSourceYaml(true) }
            until { withContext(Dispatchers.Main) { opened.model.ui.value.source?.yaml == true } }
        }
    }

    /** Waits until `done`, or fails after [WAIT_MILLISECONDS]: the view model writes off the main thread. */
    private suspend fun until(done: suspend () -> Boolean) {
        withTimeout(WAIT_MILLISECONDS) {
            while (!done()) {
                delay(20)
            }
        }
    }

    @Test
    fun `a map opened is drawn from its text, and is ready once its page says so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id, ready = false)

        assertEquals("# Trip to Japan\n\n- Tokyo\n", opened.load.markdown)
        assertEquals(id, opened.load.mapId)
        assertEquals(MapInsets(top = 64f, bottom = 96f), opened.load.insets)

        withContext(Dispatchers.Main) { opened.page.say(ready) }

        assertTrue(opened.model.ui.value.ready)
        assertEquals(ready.state, opened.model.ui.value.map)
    }

    @Test
    fun `an edit the page saves is written to the map's file, and undo brings the map as it was back`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)

        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n" }
        assertTrue(opened.model.ui.value.canUndo)

        withContext(Dispatchers.Main) { opened.model.undo() }

        // The page draws the map as it was again, at the revision it's on, and the file follows.
        val restored = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
        assertEquals("# Trip to Japan\n\n- Tokio\n", restored.markdown)
        assertEquals(opened.load.revision, restored.revision)
        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n" }
        assertTrue(opened.model.ui.value.canRedo)
    }

    @Test
    fun `a save made on the map as it was before a change from elsewhere goes into its history, never over the map`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }

        try {
            // Saved by the page on text older than what the screen caught up with since.
            withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Kyoto\n", opened.load.revision + 1)) }
            until { withContext(Dispatchers.Main) { MapEvent.KeptInHistory in effects } }

            assertEquals("# Trip to Japan\n\n- Tokio\n", maps.read(id))
            assertTrue(maps.versions(id).any { maps.readVersion(id, it.time) == "# Trip to Japan\n\n- Kyoto\n" })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a map that can only be looked at takes no save, and has nothing to undo`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id, ready = false)

        // The page says the map comes from a newer Orbit: it's only shown, and nothing it says to save is taken.
        withContext(Dispatchers.Main) { opened.page.say(newerReady) }
        assertEquals(ReadOnly.Newer, opened.model.ui.value.readOnly)

        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }

        assertFalse(opened.model.ui.value.canUndo)
        assertEquals("# Trip to Japan\n\n- Tokio\n", withContext(Dispatchers.Main) { opened.model.currentMarkdown() })
        assertEquals("# Trip to Japan\n\n- Tokio\n", maps.read(id))
    }

    @Test
    fun `text typed in the source editor right before the map closes is kept`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        editAsText(opened)

        // Typed, and the map closed before the typing's pause was over.
        withContext(Dispatchers.Main) { opened.model.editSource("# Trip to Japan\n\n- Tokio\n- Kyoto\n") }
        close(opened)

        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Kyoto\n" }
    }

    @Test
    fun `YAML typed right before the map closes is kept, converted by the page before it goes back`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        editAsText(opened, yaml = true)

        withContext(Dispatchers.Main) { opened.model.editSource("$YAML# Trip to Japan\n\n- Tokio\n- Kyoto\n") }
        close(opened)

        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Kyoto\n" }
    }

    @Test
    fun `the source editor opened again while its last YAML converts opens on the map that YAML makes`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        editAsText(opened, yaml = true)
        opened.page.holding = true

        withContext(Dispatchers.Main) {
            opened.model.editSource("$YAML# Trip to Japan\n\n- Tokio\n- Kyoto\n")
            opened.model.closeSource()
            opened.model.openSource()
        }

        withTimeout(WAIT_MILLISECONDS) { opened.page.held.receive() }.complete(Unit)
        until { withContext(Dispatchers.Main) { opened.model.ui.value.source?.text == "# Trip to Japan\n\n- Tokio\n- Kyoto\n" } }
    }

    @Test
    fun `an idea that's only been studied keeps its answers when new text gives it a new id`() = runBlocking {
        maps.load()
        val id = maps.add("# Verbs\n\n## Ser\n\n- soy\n")
        val known = StudyCard(box = 3, due = 20_007, first = 20_000)
        maps.saveStudy(id, mapOf("ser" to known))
        val opened = open(id)

        // Its text changed in the idea's editor: the page says the idea's new id.
        withContext(Dispatchers.Main) { opened.page.say(saved("# Verbs\n\n## Ser to be\n\n- soy\n", opened.load.revision).copy(renamed = Renamed("ser", "ser-to-be"))) }

        until { maps.summary(id)?.metadata?.study == mapOf("ser-to-be" to known) }
    }
}

/**
 * A page that keeps what it's sent, and says what a test tells it to. It
 * answers a map's last word as it closes, and converts as Orbit would, as
 * SourceEditingTest fakes it: YAML is the markdown with [YAML] in front.
 * Given back, it answers no more.
 */
private class TestPage : MapPage {
    override var listener: ((MapMessage) -> Unit)? = null
    override var incognito = false

    /** Every command sent, in order. */
    val commands = Channel<MapCommand>(Channel.UNLIMITED)

    /** Whether the map gave the page back, done with it. */
    var givenBack = false

    /** While true, each conversion waits in [held] until the test lets it through, as a big map's takes a while. */
    @Volatile
    var holding = false

    val held = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)

    private var jobs = 0

    override fun send(command: MapCommand) {
        commands.trySend(command)
    }

    override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
        if (givenBack) {
            throw PageException("The page went back")
        }

        val sent = command(++jobs)

        if (sent is MapCommand.Convert && holding) {
            CompletableDeferred<Unit>().also { held.trySend(it) }.await()
        }

        val value = when (sent) {
            is MapCommand.Check -> protocolJson.encodeToJsonElement(CheckedMap.serializer(), CheckedMap("", 0, emptyList(), sent.text))
            is MapCommand.Convert -> JsonPrimitive(if (sent.to == "yaml") YAML + sent.text.removePrefix(YAML) else sent.text.removePrefix(YAML))
            else -> error("This test's page answers no ${sent::class.simpleName} jobs")
        }

        return protocolJson.decodeFromJsonElement(answer, value)
    }

    override suspend fun drawn() = Unit

    /** The page says `message`, as the real one does from its map. */
    fun say(message: MapMessage) {
        listener?.invoke(message)
    }
}

/** The rest of the app, as far as a test's map reaches for it: pages, words and the private maps' lock. */
private class TestApp(override val words: Words) : MapApp {
    /** The pages the view model took, in order. */
    val pages = mutableListOf<TestPage>()

    override val privateLock = PrivateLock(clock = { 0L })

    /** The test's own: what's started in it ends with the test (see cleanUp). */
    override val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val deletedMaps = Channel<TrashedMap>(Channel.CONFLATED)

    override fun takePage(): MapPage = TestPage().also { pages += it }

    override fun givePage(page: MapPage) {
        (page as TestPage).givenBack = true
    }

    override fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?) = SoundPlayback(scope, resolve) { error("No sound plays in these tests") }

    override fun timeWords(): TimeWords = error("No version is restored in these tests")

    override val clipboard: MapClipboard get() = error("Nothing is copied in these tests")
    override val pictures: MapPictures get() = error("No picture is drawn in these tests")
    override val importer: Importer get() = error("No file is let go on the map in these tests")
    override val folderSync: FolderSync get() = error("No folder syncs in these tests")
}
