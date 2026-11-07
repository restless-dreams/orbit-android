package com.hyuchia.orbit.map

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.FolderSync
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapEditResult
import com.hyuchia.orbit.data.MapEdits
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.data.WriteResult
import com.hyuchia.orbit.data.settingsStoreIn
import com.hyuchia.orbit.media.SoundPlayback
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.CheckedMap
import com.hyuchia.orbit.page.EditedMap
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PastedIdeas
import com.hyuchia.orbit.page.Renamed
import com.hyuchia.orbit.page.protocolJson
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.reminders.IdeaToShow
import com.hyuchia.orbit.sketch.InkSample
import com.hyuchia.orbit.sketch.Sketch
import com.hyuchia.orbit.sketch.SketchSession
import com.hyuchia.orbit.sketch.SketchStroke
import com.hyuchia.orbit.sketch.SketchSvg
import com.hyuchia.orbit.sketch.SketchTouch
import com.hyuchia.orbit.sketch.StrokeOutline
import com.hyuchia.orbit.sketch.StrokePoint
import com.hyuchia.orbit.sketch.TouchTool
import com.hyuchia.orbit.study.StudyCard
import com.hyuchia.orbit.ui.NoteCheck
import com.hyuchia.orbit.ui.TimeWords
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    /** A storage that's slow when the test says, to hold a write as it's made. */
    private val slow = SlowStorage()

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words, clockTrusted = slow::clockTrusted)
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

    /** Whether the map's history holds `text`. */
    private suspend fun inHistory(id: String, text: String): Boolean = maps.versions(id).any { maps.readVersion(id, it.time) == text }

    /** The view model's screen state, read on the main thread. */
    private suspend fun ui(opened: Opened): MapUi = withContext(Dispatchers.Main) { opened.model.ui.value }

    /** What the screen hears, from now on, until `hearing` stops. */
    private fun CoroutineScope.hear(opened: Opened, effects: MutableList<MapEvent>) = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }

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
    fun `a page that didn't load says so in the map's place, and loading it again draws the map on a new page`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id, ready = false)

        withContext(Dispatchers.Main) { opened.page.say(MapMessage.DidNotLoad) }

        assertEquals(PageProblem.DidNotLoad, opened.model.ui.value.pageProblem)
        assertFalse(opened.model.ui.value.ready)

        withContext(Dispatchers.Main) { opened.model.reload() }

        // The page that didn't load goes back, and a new one is sent the map.
        val again = app.pages.last()
        val load = withTimeout(WAIT_MILLISECONDS) { again.commands.receiveAsFlow().filterIsInstance<MapCommand.Load>().first() }

        assertTrue(opened.page.givenBack)
        assertTrue(again !== opened.page)
        assertEquals("# Trip to Japan\n\n- Tokyo\n", load.markdown)
        assertNull(opened.model.ui.value.pageProblem)

        withContext(Dispatchers.Main) { again.say(ready) }

        assertTrue(opened.model.ui.value.ready)
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
    fun `a map that can only be looked at opens no editor or style, and closes the one open as it becomes so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        val idea = ready.state.nodes.first().id

        withContext(Dispatchers.Main) { opened.model.show(Panel.Editor(idea)) }
        assertTrue(opened.model.ui.value.panel is Panel.Editor)

        // Its text changed elsewhere to a newer Orbit's, say: the page says it can only be shown now, and the editor goes.
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.State(newerReady.state)) }
        assertEquals(ReadOnly.Newer, opened.model.ui.value.readOnly)
        assertNull(opened.model.ui.value.panel)

        // Ctrl+E, a row of the outline held, or a stray button: nothing that would change it opens.
        withContext(Dispatchers.Main) { opened.model.show(Panel.Editor(idea)) }
        assertNull(opened.model.ui.value.panel)
        withContext(Dispatchers.Main) { opened.model.show(Panel.Style) }
        assertNull(opened.model.ui.value.panel)

        // What only reads it still opens.
        withContext(Dispatchers.Main) { opened.model.show(Panel.Details(idea)) }
        assertEquals(Panel.Details(idea), opened.model.ui.value.panel)
    }

    @Test
    fun `a map made into a path takes nothing pasted, and doesn't even read the clipboard`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        val idea = ready.state.nodes.first().id

        // This test's app has no clipboard: reading it would fail the test. A presentation takes none the same way (see takesDrops).
        withContext(Dispatchers.Main) { opened.model.startPath() }
        withContext(Dispatchers.Main) { opened.model.paste(idea) }

        assertTrue(sentSince(opened).none { it is MapCommand.Paste || it is MapCommand.AddNodes })
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
    fun `a YAML map exports as its own YAML, and as the markdown Orbit writes of it`() = runBlocking {
        maps.load()
        // A map that came as YAML, as one does through the folder maps are kept in.
        val yaml = "title: Trip to Japan\nnodes:\n  - text: Trip to Japan\n    children: [Tokio]\n"
        val opened = open(maps.add(yaml))
        opened.page.holding = true

        assertTrue(opened.model.isYaml())
        // Its YAML is its text as it is, with nothing for the page to convert.
        assertEquals(yaml, withTimeout(WAIT_MILLISECONDS) { opened.model.yaml() })
        assertTrue(opened.page.held.isEmpty)
        // Its markdown is the page's: Orbit writes it from the map.
        val markdown = async { opened.model.markdownText() }
        withTimeout(WAIT_MILLISECONDS) { opened.page.held.receive() }.complete(Unit)
        assertEquals(yaml, markdown.await())

        // A markdown map the other way around: its markdown as it is, its YAML the page's.
        val other = open(maps.add("# Trip to Japan\n\n- Tokio\n"))
        assertFalse(other.model.isYaml())
        assertEquals("# Trip to Japan\n\n- Tokio\n", other.model.markdownText())
        assertEquals("$YAML# Trip to Japan\n\n- Tokio\n", other.model.yaml())
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

    @Test
    fun `an edit behind a slow write is written, and a save made before a change elsewhere coming after it is kept in the history`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)

        // The visit's first edit keeps the map as it was opened in its history, where the storage is slow.
        slow.arm()
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        slow.awaitStopped()

        withContext(Dispatchers.Main) {
            // The next edit waits for it; then comes a save the page made on text older than what the screen caught up with.
            opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", opened.load.revision))
            opened.page.say(saved("# Trip to Japan\n\n- Osaka\n", opened.load.revision + 1))
        }

        slow.release()
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Kyoto\n" }
        until { inHistory(id, "# Trip to Japan\n\n- Osaka\n") }
    }

    @Test
    fun `a save made before a change elsewhere behind a slow write, and an edit coming after it, are both kept`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        slow.arm()
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        slow.awaitStopped()

        withContext(Dispatchers.Main) {
            opened.page.say(saved("# Trip to Japan\n\n- Osaka\n", opened.load.revision + 1))
            opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", opened.load.revision))
        }

        slow.release()
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Kyoto\n" }
        until { inHistory(id, "# Trip to Japan\n\n- Osaka\n") }
    }

    @Test
    fun `a map that closes with saves waiting behind a slow write keeps each, the stale one in its history`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        slow.arm()
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        slow.awaitStopped()

        // The map closes as the write goes on, with a stale save waiting behind it, and an edit after that.
        withContext(Dispatchers.Main) {
            opened.page.say(saved("# Trip to Japan\n\n- Osaka\n", opened.load.revision + 1))
            opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", opened.load.revision))
        }

        close(opened)
        slow.release()

        until { inHistory(id, "# Trip to Japan\n\n- Osaka\n") }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Kyoto\n" }
    }

    @Test
    fun `a map changed elsewhere as it waits to be drawn is drawn as it is then, and its first edit lands on that`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        // A path, which the screen takes from the map's details as soon as it's read the map: what tells the test it has.
        maps.savePath(id, listOf("trip-to-japan", "tokio"))
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { MapViewModel(id, maps, settings, app) } }
        val model = withContext(Dispatchers.Main) { ViewModelProvider.create(store, factory)[MapViewModel::class] }
        models += model
        val page = app.pages.last()

        // Its colors and settings come before it hears what the screen covers: it reads the map, then waits to draw it.
        withContext(Dispatchers.Main) { model.configure(AppTheme(dark = false, tokens = emptyMap()), MapSettings(haptics = true, snap = true, longPressDelay = 400)) }
        until { withContext(Dispatchers.Main) { model.ui.value.presentPath.isNotEmpty() } }

        // Changed elsewhere meanwhile, as another device's version would: the screen catches up before the map is drawn.
        assertEquals(WriteResult.Written, maps.change(id, "# Trip to Japan\n\n- Tokio\n- Kyoto\n", base = "# Trip to Japan\n\n- Tokio\n"))
        val restored = withTimeout(WAIT_MILLISECONDS) { page.commands.receiveAsFlow().filter { it is MapCommand.Restore || it is MapCommand.Load }.first() }
        assertTrue("The map was drawn before the change came: the screen waited less than the test took", restored is MapCommand.Restore)
        withContext(Dispatchers.Main) { model.setInsets(MapInsets(top = 64f, bottom = 96f)) }
        val load = withTimeout(WAIT_MILLISECONDS) { page.commands.receiveAsFlow().filterIsInstance<MapCommand.Load>().first() }

        assertEquals("# Trip to Japan\n\n- Tokio\n- Kyoto\n", (restored as MapCommand.Restore).markdown)
        assertEquals("# Trip to Japan\n\n- Tokio\n- Kyoto\n", load.markdown)

        // An edit on what the page drew lands on the change, at the revision it was drawn at.
        withContext(Dispatchers.Main) {
            page.say(ready)
            page.say(saved(load.markdown + "- Osaka\n", load.revision))
        }

        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Kyoto\n- Osaka\n" }
    }

    @Test
    fun `the last edit, written as the map closes, keeps the map as it was opened, saying so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)

        // Saved by the page as the map closes, before the screen's own writing got to it.
        withContext(Dispatchers.Main) {
            opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision))
            opened.store.clear()
        }

        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n" }
        val kept = maps.versions(id).single()
        assertEquals(VersionReason.Opened, kept.reason)
        assertEquals("# Trip to Japan\n\n- Tokio\n", maps.readVersion(id, kept.time))
    }

    @Test
    fun `text typed by hand and written as the map closes keeps the map as it was before, saying so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        editAsText(opened)
        withContext(Dispatchers.Main) { opened.model.editSource("# Trip to Japan\n\n- Tokio\n- Kyoto\n") }
        close(opened)

        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Kyoto\n" }
        assertEquals(listOf<VersionReason?>(VersionReason.Text), maps.versions(id).map { it.reason })
    }

    @Test
    fun `the history open beside the map lists the versions its edits keep, and what the map is now`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.isEmpty() == true && ui(opened).history?.now != null }

        // An idea dragged on the map beside it keeps the history open; the visit's first edit keeps a version.
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }

        until { ui(opened).history?.versions?.size == 1 }
        until { (ui(opened).history?.now as? VersionSummary.Changed)?.changes?.renamed == listOf("Tokio" to "Tokyo") }
    }

    @Test
    fun `the history open as the map changes elsewhere lists the change's version, and what the map is now`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.now != null }

        // Ideas caught on the go: the map as it was is kept first, and the screen catches up.
        assertEquals(WriteResult.Written, maps.change(id, "# Trip to Japan\n\n- Tokio\n- Kyoto\n", base = "# Trip to Japan\n\n- Tokio\n"))

        until { ui(opened).history?.versions?.map { it.reason } == listOf(VersionReason.Caught) }
        until { (ui(opened).history?.now as? VersionSummary.Changed)?.changes?.added == listOf("Kyoto") }
    }

    @Test
    fun `looking around an earlier version leaves where the map reopens as it was`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n- Kyoto\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Kyoto\n", opened.load.revision)) }
        until { maps.versions(id).size == 1 }
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.size == 1 }
        val version = ui(opened).history!!.versions!!.single().time

        withContext(Dispatchers.Main) { opened.model.showVersion(version) }
        until { ui(opened).showingVersion }

        // The reader flies to an idea the map as it is no longer has, on the version.
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Focus(listOf("trip-to-japan", "tokio"), "focus")) }
        // Longer than the screen waits before it remembers a place.
        delay(1_500)

        assertEquals(null, maps.summary(id)?.metadata?.focus)

        // Back on the map as it is, where the reader is is remembered again.
        withContext(Dispatchers.Main) {
            opened.model.showVersion(null)
            opened.page.say(MapMessage.Focus(listOf("trip-to-japan", "kyoto"), "focus"))
        }

        until { maps.summary(id)?.metadata?.focus == "kyoto" }
    }

    @Test
    fun `a map renamed from Home as it's open keeps its path on its center's new id`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        maps.savePath(id, listOf("trip-to-japan", "tokio"))
        val opened = open(id)
        until { ui(opened).presentPath == listOf("trip-to-japan", "tokio") }

        // Renamed from Home beside it, on a tablet: the page says the new name gave the center a new id.
        val edits = MapEdits(maps, { markdown, title, _ -> EditedMap(markdown = MapText.retitle(markdown, title!!, words), renamed = listOf(Renamed("trip-to-japan", "japan-in-spring"))) }, words)
        assertEquals(MapEditResult.Written, edits.rename(id, "Japan in spring"))

        until { ui(opened).presentPath == listOf("japan-in-spring", "tokio") }
        assertEquals(listOf("japan-in-spring", "tokio"), maps.summary(id)?.metadata?.path)
    }

    @Test
    fun `text typed as the source editor switches to markdown, after an edit on the map, lands on that edit`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        editAsText(opened, yaml = true)
        withContext(Dispatchers.Main) { opened.model.editSource("$YAML# Trip to Japan\n\n- Tokio\n- Nara\n") }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Nara\n" }
        opened.page.holding = true

        // Back to markdown: the switch converts the YAML as an idea is added on the map beside the text.
        withContext(Dispatchers.Main) { opened.model.setSourceYaml(false) }
        val switch = withTimeout(WAIT_MILLISECONDS) { opened.page.held.receive() }
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokio\n- Nara\n- Kyoto\n", opened.load.revision)) }

        // The switch, from the YAML before the idea was added, comes back first; the next keystroke goes on from what the text shows.
        switch.complete(Unit)
        until { ui(opened).source?.converting == false }
        withContext(Dispatchers.Main) { opened.model.editSource(opened.model.ui.value.source!!.text + "- Osaka\n") }
        opened.page.holding = false
        opened.page.held.tryReceive().getOrNull()?.complete(Unit)

        close(opened)
        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Nara\n- Kyoto\n- Osaka\n" }
    }

    @Test
    fun `text typed by hand right before an edit on the map goes into the map's history, not over the edit`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        editAsText(opened)
        val effects = mutableListOf<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            withContext(Dispatchers.Main) {
                opened.model.editSource("# Trip to Japan\n\n- Tokio\n- Osaka\n")
                // An idea added on the map beside the text before the typing's pause was over.
                opened.page.say(saved("# Trip to Japan\n\n- Tokio\n- Kyoto\n", opened.load.revision))
            }

            until { withContext(Dispatchers.Main) { MapEvent.KeptInHistory in effects } }
            assertTrue(inHistory(id, "# Trip to Japan\n\n- Tokio\n- Osaka\n"))
            until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Kyoto\n" }
            assertEquals("# Trip to Japan\n\n- Tokio\n- Kyoto\n", ui(opened).source?.text)
        } finally {
            hearing.cancel()
        }
    }

    /** Spanish verbs as the page reads it: two cards, Ser and Estar, each with an idea under it. */
    private val verbs = MapState(
        "Verbs",
        listOf(
            MapNode("verbs", "Verbs", depth = 0, childCount = 2),
            MapNode("ser", "Ser", parentId = "verbs", depth = 1, childCount = 1),
            MapNode("soy", "soy", parentId = "ser", depth = 2),
            MapNode("estar", "Estar", parentId = "verbs", depth = 1, childCount = 1),
            MapNode("estoy", "estoy", parentId = "estar", depth = 2),
        ),
    )

    @Test
    fun `an answer given right as the map closes is kept`() = runBlocking {
        maps.load()
        val id = maps.add("# Verbs\n\n## Ser\n\n- soy\n\n## Estar\n\n- estoy\n")
        val opened = open(id, ready = false)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Ready(verbs))
            opened.model.startStudy(fromTop = true)
            opened.model.revealCard()
            opened.model.answerCard(knew = true)
            // Closed at once, as Back does, before the screen had a moment to write the answer.
            opened.store.clear()
        }

        until { maps.summary(id)?.metadata?.study?.keys == setOf("ser") }
    }

    @Test
    fun `a studied map, as its page reads it, says which ideas with answers aren't cards, which Home doesn't count`() = runBlocking {
        maps.load()
        val id = maps.add("# Verbs\n\n## Ser\n\n- soy\n\n## Estar\n\n- estoy\n")
        val due = StudyCard(box = 1, due = 20_000, first = 19_990)
        // Soy was a card once, with a note; Ir went from the map elsewhere.
        maps.saveStudy(id, mapOf("ser" to due, "soy" to due, "ir" to due))
        maps.saveStudyElsewhere(id, mapOf("tablet" to mapOf("estar" to due)))
        val opened = open(id, ready = false)

        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Ready(verbs)) }

        until { maps.summary(id)?.metadata?.studyNotCards == listOf("ir", "soy") }
        assertEquals(setOf("ser", "estar"), maps.summary(id)?.metadata?.studyCounted?.keys)
        // Their answers stay, for when they're cards again.
        assertEquals(setOf("ser", "soy", "ir"), maps.summary(id)?.metadata?.study?.keys)

        // Soy gets a note on the map: it's a card again, and counts.
        val noted = verbs.copy(nodes = verbs.nodes.map { if (it.id == "soy") it.copy(note = "I am") else it })
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.State(noted)) }
        until { maps.summary(id)?.metadata?.studyNotCards == listOf("ir") }
    }

    /** What was said in a pause, as the map asks the page to add it: the test answers it with [added]. */
    private suspend fun said(opened: Opened): Pair<MapCommand.AddNodes, CompletableDeferred<Int>> = withTimeout(WAIT_MILLISECONDS) { opened.page.adding.receive() }

    /** The page added what was said, making the map `markdown`, and says `count` ideas joined: its save first, as the real page does. */
    private suspend fun added(opened: Opened, asked: Pair<MapCommand.AddNodes, CompletableDeferred<Int>>, markdown: String, count: Int) {
        withContext(Dispatchers.Main) { opened.page.say(saved(markdown, opened.load.revision).copy(kind = "add")) }
        asked.second.complete(count)
    }

    /** The ideas a change asks the page to add, by their text. */
    private val Pair<MapCommand.AddNodes, CompletableDeferred<Int>>.texts: List<String> get() = first.nodes.map { it.getValue("text").jsonPrimitive.content }

    /** The commands sent to the page since the last look, as Undo would send a Restore. */
    private fun sentSince(opened: Opened): List<MapCommand> = generateSequence { opened.page.commands.tryReceive().getOrNull() }.toList()

    @Test
    fun `what's said after a private map locks never joins it, while what was said before is still saved`() = runBlocking {
        maps.load()
        val id = maps.add("# Diary\n\n- Monday\n", isPrivate = true)
        withContext(Dispatchers.Main) { app.privateLock.unlock() }
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }

        try {
            assertTrue(withContext(Dispatchers.Main) { opened.model.startTalking() })
            assertTrue(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Call the bakery")) })
            val before = said(opened)
            assertEquals(listOf("Call the bakery"), before.texts)

            // Locked now, from its menu, while the page is still adding what was said before.
            withContext(Dispatchers.Main) { app.privateLock.lock() }

            // A speech service that answers late, or another app's listening screen, says more: the map takes none of it.
            assertFalse(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Read my diary aloud")) })
            until { !withContext(Dispatchers.Main) { opened.model.talkable.value } }
            assertFalse(withContext(Dispatchers.Main) { opened.model.startTalking() })

            // What was said before the lock still joins the map, written to its file.
            added(opened, before, "# Diary\n\n- Monday\n- Call the bakery\n", 1)
            until { maps.read(id) == "# Diary\n\n- Monday\n- Call the bakery\n" }
            withContext(Dispatchers.Main) { opened.model.stopTalking() }

            assertTrue(opened.page.adding.isEmpty)
            // Behind the lock, the talk is offered to no one to undo.
            assertTrue(withContext(Dispatchers.Main) { effects.none { it is MapEvent.Snackbar } })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `opening the recorder ends a talk, and nothing said after it joins the map`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        assertTrue(withContext(Dispatchers.Main) { opened.model.startTalking() })

        // A voice memo for the idea in focus: the microphone is the recorder's.
        withContext(Dispatchers.Main) { opened.model.recordMemo(opened.model.ui.value.sun!!.id) }
        until { !withContext(Dispatchers.Main) { opened.model.talkable.value } }
        assertFalse(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Said as the recorder opened")) })
        assertFalse(withContext(Dispatchers.Main) { opened.model.startTalking() })

        // Closed, the map can be talked onto again.
        withContext(Dispatchers.Main) { opened.model.closeRecorder() }
        until { withContext(Dispatchers.Main) { opened.model.talkable.value } }
        assertTrue(opened.page.adding.isEmpty)
    }

    @Test
    fun `a talk's Undo comes once its ideas are saved, and takes back the talk alone`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }

        try {
            // An edit just before the talk: the talk's Undo leaves it.
            withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", opened.load.revision)) }

            assertTrue(withContext(Dispatchers.Main) { opened.model.startTalking() })
            assertTrue(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Osaka")) })
            added(opened, said(opened), "# Trip to Japan\n\n- Tokyo\n- Kyoto\n- Osaka\n", 1)
            assertTrue(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Nara", "Kobe")) })
            val last = said(opened)
            assertEquals(listOf("Nara", "Kobe"), last.texts)

            // Done while the page is still adding the last ideas: there's nothing of the talk to undo yet, so no Undo shows.
            withContext(Dispatchers.Main) { opened.model.stopTalking() }
            withContext(Dispatchers.Main) {}
            assertTrue(withContext(Dispatchers.Main) { effects.none { it is MapEvent.Snackbar } })

            added(opened, last, "# Trip to Japan\n\n- Tokyo\n- Kyoto\n- Osaka\n- Nara\n- Kobe\n", 2)
            until { withContext(Dispatchers.Main) { effects.any { it is MapEvent.Snackbar } } }
            val offered = withContext(Dispatchers.Main) { effects.filterIsInstance<MapEvent.Snackbar>().single() }
            assertEquals(ShownText.Plural(R.plurals.map_added_ideas, 3), offered.message)
            assertTrue(offered.undo)
            sentSince(opened)

            // Its Undo takes the whole talk back in one step, to the map just before it, the edit before it kept.
            withContext(Dispatchers.Main) { offered.onUndo!!.invoke() }
            val restored = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
            assertEquals("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", restored.markdown)
            until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Kyoto\n" }
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a talk's Undo takes nothing back once the map changed after the talk`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }

        try {
            assertTrue(withContext(Dispatchers.Main) { opened.model.startTalking() })
            assertTrue(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Osaka")) })
            added(opened, said(opened), "# Trip to Japan\n\n- Tokyo\n- Osaka\n", 1)
            withContext(Dispatchers.Main) { opened.model.stopTalking() }
            until { withContext(Dispatchers.Main) { effects.any { it is MapEvent.Snackbar } } }
            val offered = withContext(Dispatchers.Main) { effects.filterIsInstance<MapEvent.Snackbar>().single() }

            // A word fixed in the new idea before Undo is pressed: Undo would take that back, not the talk, so it does nothing.
            withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Ōsaka\n", opened.load.revision)) }
            until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Ōsaka\n" }
            sentSince(opened)
            withContext(Dispatchers.Main) { offered.onUndo!!.invoke() }
            withContext(Dispatchers.Main) {}

            assertTrue(sentSince(opened).none { it is MapCommand.Restore })
            assertEquals("# Trip to Japan\n\n- Tokyo\n- Ōsaka\n", maps.read(id))
        } finally {
            hearing.cancel()
        }
    }

    /** The page's map with idea `id`'s note as `note`: the build's example map, which has Tokyo. */
    private fun withNote(state: MapState, id: String, note: String): MapState = state.copy(nodes = state.nodes.map { if (it.id == id) it.copy(note = note) else it })

    @Test
    fun `a box checked in a note shows checked at once, goes to the page by its line, and its save offers it back`() = runBlocking {
        maps.load()
        val before = "# Trip to Japan\n\n- Tokyo\n  > - [ ] Pack\n  > - [ ] Book\n"
        val after = "# Trip to Japan\n\n- Tokyo\n  > - [x] Pack\n  > - [ ] Book\n"
        val id = maps.add(before)
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            sentSince(opened)
            withContext(Dispatchers.Main) { opened.model.checkNote("tokyo", NoteCheck(0, true, "Pack")) }

            assertEquals(listOf<MapCommand>(MapCommand.CheckNote("tokyo", 0, true)), sentSince(opened))
            assertEquals(mapOf("tokyo" to mapOf(0 to true)), ui(opened).noteChecks)

            // The page saves the map with the box checked: the note shows it now, and a message says so, with Undo.
            val saved = saved(after, opened.load.revision)
            withContext(Dispatchers.Main) { opened.page.say(saved.copy(kind = "update", state = withNote(saved.state, "tokyo", "- [x] Pack\n- [ ] Book"))) }
            until { maps.read(id) == after }
            assertEquals(emptyMap<String, Map<Int, Boolean>>(), ui(opened).noteChecks)
            until { withContext(Dispatchers.Main) { effects.any { it is MapEvent.Snackbar } } }
            val offered = withContext(Dispatchers.Main) { effects.filterIsInstance<MapEvent.Snackbar>().single() }
            assertEquals(ShownText.Resource(R.string.map_checked_item, listOf("Pack")), offered.message)
            assertTrue(offered.undo)
            assertTrue(withContext(Dispatchers.Main) { effects.contains(MapEvent.Tick) })

            // Undo takes the box back: the map as it was before it.
            withContext(Dispatchers.Main) { offered.onUndo!!.invoke() }
            val restored = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
            assertEquals(before, restored.markdown)
            until { maps.read(id) == before }
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a checked box's Undo takes nothing back once the map changed after it`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n  > - [ ] Pack\n")
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            withContext(Dispatchers.Main) { opened.model.checkNote("tokyo", NoteCheck(0, true, "Pack")) }
            val saved = saved("# Trip to Japan\n\n- Tokyo\n  > - [x] Pack\n", opened.load.revision)
            withContext(Dispatchers.Main) { opened.page.say(saved.copy(kind = "update", state = withNote(saved.state, "tokyo", "- [x] Pack"))) }
            until { withContext(Dispatchers.Main) { effects.any { it is MapEvent.Snackbar } } }
            val offered = withContext(Dispatchers.Main) { effects.filterIsInstance<MapEvent.Snackbar>().single() }

            // Another edit before Undo is pressed: Undo would take that back, not the box, so it does nothing.
            withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tōkyō\n  > - [x] Pack\n", opened.load.revision)) }
            until { maps.read(id) == "# Trip to Japan\n\n- Tōkyō\n  > - [x] Pack\n" }
            sentSince(opened)
            withContext(Dispatchers.Main) { offered.onUndo!!.invoke() }
            withContext(Dispatchers.Main) {}

            assertTrue(sentSince(opened).none { it is MapCommand.Restore })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a box the page can't find shows as the note has it, and one on a map that can't change isn't sent`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n  > - [ ] Pack\n")
        val opened = open(id)

        withContext(Dispatchers.Main) { opened.model.checkNote("tokyo", NoteCheck(0, true, "Pack")) }
        assertEquals(mapOf("tokyo" to mapOf(0 to true)), ui(opened).noteChecks)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Declined("checkNote", "The note has no box on that line")) }
        assertEquals(emptyMap<String, Map<Int, Boolean>>(), ui(opened).noteChecks)

        // Only looked at: a map from a newer Orbit takes no box, and nothing goes to the page.
        withContext(Dispatchers.Main) { opened.page.say(newerReady) }
        sentSince(opened)
        withContext(Dispatchers.Main) { opened.model.checkNote("tokyo", NoteCheck(0, true, "Pack")) }

        assertTrue(sentSince(opened).none { it is MapCommand.CheckNote })
        assertEquals(emptyMap<String, Map<Int, Boolean>>(), ui(opened).noteChecks)
    }

    @Test
    fun `nothing said after a talk is over joins the map`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)

        assertTrue(withContext(Dispatchers.Main) { opened.model.startTalking() })
        withContext(Dispatchers.Main) { opened.model.stopTalking() }

        // A speech service that answers once the talk is over, as one that took too long to say the last idea.
        assertFalse(withContext(Dispatchers.Main) { opened.model.addSpoken(listOf("Too late")) })
        assertTrue(opened.page.adding.isEmpty)
    }

    @Test
    fun `ideas pasted from another map whose pictures can't be copied in, as on a full disk, paste nothing and say so`() = runBlocking {
        maps.load()
        val from = maps.add("# Trip to Japan\n\n- Kyoto\n\n  ![](media/temple.jpg)\n")
        File(maps.folder(from), "media").mkdirs()
        File(maps.folder(from), "media/temple.jpg").writeBytes(ByteArray(1_000) { 1 })
        val id = maps.add("# Plans\n\n- Autumn\n")
        // Where this map's media folder goes there's a file: nothing can be copied into it, as on a full disk.
        val media = File(maps.folder(id), "media").apply { writeText("") }
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }
        val copied = Clip.Ideas("- Kyoto\n\n  ![](media/temple.jpg)\n", sourceMap = from)

        try {
            val target = withContext(Dispatchers.Main) { opened.model.ui.value.sun!!.id }
            sentSince(opened)
            withContext(Dispatchers.Main) { opened.model.pasteIdeas(target, copied) }

            until { withContext(Dispatchers.Main) { effects.any { it is MapEvent.Snackbar } } }
            assertEquals(ShownText.Resource(R.string.map_paste_media_failed), withContext(Dispatchers.Main) { effects.filterIsInstance<MapEvent.Snackbar>().single().message })
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })

            // With room again, the same ideas paste, their picture with them.
            media.delete()
            withContext(Dispatchers.Main) { opened.model.pasteIdeas(target, copied) }
            val pasted = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Paste>().first() }
            assertEquals(copied.text, pasted.text)
            assertEquals(1_000L, File(maps.folder(id), "media/temple.jpg").length())
        } finally {
            hearing.cancel()
        }
    }

    /** The map as the page reads it, with idea `id`'s picture `image`: the example's, which can be changed. */
    private fun withPicture(id: String, image: Media?): MapMessage.Ready =
        ready.copy(state = ready.state.copy(nodes = ready.state.nodes.map { if (it.id == id) it.copy(image = image) else it }))

    /** Draws a stroke across the canvas, as a finger does. */
    private fun SketchSession.stroke(from: Float, to: Float, at: Float = 500f, time: Long = 0) {
        touch(SketchTouch.Down(0, TouchTool.Finger, InkSample(from, at, null, 0f, time)))
        touch(SketchTouch.Move(0, (1..10).map { step -> InkSample(from + (to - from) * step / 10, at, null, 0f, time + step * 8L) }))
        touch(SketchTouch.Up(0, InkSample(to, at, null, 0f, time + 88)))
    }

    /** Waits for the next command of `type` the page is sent. */
    private suspend inline fun <reified Type : MapCommand> nextSent(opened: Opened): Type =
        withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<Type>().first() }

    @Test
    fun `a sketch brings its planet up close, keeps the drawing as the idea's picture in one change, and lets the planet go once it shows`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }
        sentSince(opened)

        withContext(Dispatchers.Main) {
            opened.model.sketchRoom(MapInsets(bottom = 112f))
            opened.model.sketch("someday")
        }

        // The page brings the planet up close, in the room the toolbar leaves.
        assertEquals(MapCommand.Spotlight("someday", MapInsets(bottom = 112f)), nextSent<MapCommand.Spotlight>(opened))
        assertEquals(SketchPhase.Opening, ui(opened).sketch?.phase)
        assertTrue(ui(opened).sketching)
        assertFalse(ui(opened).takesEdits)

        // It rests there, and the canvas comes over it, in the planet's own color.
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f, "rgba(15, 23, 42, 0.45)")) }
        assertEquals(SketchPhase.Drawing, ui(opened).sketch?.phase)
        assertEquals(PlanetCircle(206f, 300f, 177f), ui(opened).sketch?.circle)
        val session = withContext(Dispatchers.Main) { opened.model.sketchSession.value!! }
        assertEquals(Sketching.paperOf(ui(opened).nodes.getValue("someday")), session.paper)

        withContext(Dispatchers.Main) {
            session.stroke(200f, 800f)
            opened.model.finishSketch()
        }

        assertEquals(SketchPhase.Describing, ui(opened).sketch?.phase)
        withContext(Dispatchers.Main) { opened.model.saveSketch("  A sun over the sea ") }

        // One change: a new SVG in the map's folder, described.
        val update = nextSent<MapCommand.Update>(opened)
        val image = update.changes.getValue("image").jsonObject
        val src = image.getValue("src").jsonPrimitive.content
        assertEquals("someday", update.id)
        assertTrue(src, src.matches(Regex("""media/sketch-[0-9a-z]+\.svg""")))
        assertEquals("A sun over the sea", image.getValue("alt").jsonPrimitive.content)
        val kept = SketchSvg.read(File(maps.folder(id), src).readBytes())!!
        assertEquals(session.paper, kept.paper)
        assertEquals(session.strokes.map { it.outline }, kept.strokes.map { it.outline })

        // The canvas lifts once the planet shows the sketch, and only then does the map fly back.
        assertEquals(src, nextSent<MapCommand.PictureShown>(opened).src)
        nextSent<MapCommand.EndSpotlight>(opened)
        assertEquals(SketchPhase.Closing, ui(opened).sketch?.phase)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "end", 120f, 300f, 30f)) }
        assertNull(ui(opened).sketch)
        assertNull(withContext(Dispatchers.Main) { opened.model.sketchSession.value })
        assertTrue(ui(opened).takesEdits)

        // The page saves the change, and one Undo takes the whole sketch back.
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Someday\n\n  ![A sun over the sea]($src)\n", opened.load.revision)) }
        until { maps.read(id).contains(src) }
        withContext(Dispatchers.Main) { opened.model.undo() }
        assertEquals("# Trip to Japan\n\n- Someday\n", nextSent<MapCommand.Restore>(opened).markdown)
    }

    @Test
    fun `a sketch reopens with its strokes, to draw on further, and keeps its caption`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val earlier = Sketch(0xFF3F51B5.toInt(), listOf(SketchStroke(0xFFFFFFFF.toInt(), StrokeOutline.pathData(listOf(StrokePoint(500f, 500f, 40f))))))
        File(maps.folder(id), "media").mkdirs()
        File(maps.folder(id), "media/sketch-earlier.svg").writeText(SketchSvg.write(earlier))
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", Media("media/sketch-earlier.svg", alt = "A dot", caption = "Our logo"))) }
        sentSince(opened)

        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }

        val session = withContext(Dispatchers.Main) { opened.model.sketchSession.value!! }
        assertEquals(earlier.paper, session.paper)
        assertEquals(earlier.strokes.map { it.outline }, session.strokes.map { it.outline })
        assertEquals("A dot", ui(opened).sketch?.description)

        withContext(Dispatchers.Main) {
            session.stroke(200f, 800f)
            opened.model.saveSketch("A dot and a line")
        }

        val image = nextSent<MapCommand.Update>(opened).changes.getValue("image").jsonObject
        assertEquals("Our logo", image.getValue("caption").jsonPrimitive.content)
        assertTrue(image.getValue("src").jsonPrimitive.content != "media/sketch-earlier.svg")
        // The sketch earlier versions point at stays as it was.
        assertEquals(SketchSvg.write(earlier), File(maps.folder(id), "media/sketch-earlier.svg").readText())
    }

    @Test
    fun `a sketch cleared of every stroke comes off its idea as Done is tapped, with nothing to describe`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val earlier = Sketch(0xFF3F51B5.toInt(), listOf(SketchStroke(0xFFFFFFFF.toInt(), StrokeOutline.pathData(listOf(StrokePoint(500f, 500f, 40f))))))
        File(maps.folder(id), "media").mkdirs()
        File(maps.folder(id), "media/sketch-earlier.svg").writeText(SketchSvg.write(earlier))
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", Media("media/sketch-earlier.svg", alt = "A dot"))) }
        sentSince(opened)

        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }
        val session = withContext(Dispatchers.Main) { opened.model.sketchSession.value!! }

        withContext(Dispatchers.Main) {
            session.clear()
            opened.model.finishSketch()
        }

        // No description is asked for: the picture comes off, in one change.
        assertTrue(ui(opened).sketch?.phase != SketchPhase.Describing)
        val update = nextSent<MapCommand.Update>(opened)
        assertEquals("someday", update.id)
        assertEquals(JsonNull, update.changes.getValue("image"))
    }

    @Test
    fun `a sketch takes the place of another picture only once the person says so, with Undo after`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        val effects = mutableListOf<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            sentSince(opened)
            withContext(Dispatchers.Main) { opened.model.sketch("tokyo") }

            until { ui(opened).sketch?.phase == SketchPhase.Asking }
            assertTrue(sentSince(opened).none { it is MapCommand.Spotlight })

            withContext(Dispatchers.Main) { opened.model.replaceAndSketch() }
            nextSent<MapCommand.Spotlight>(opened)
            withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("tokyo", "rest", 206f, 300f, 177f)) }

            // A new sketch: none of the photo's strokes, nor its description.
            assertTrue(withContext(Dispatchers.Main) { opened.model.sketchSession.value!!.strokes.isEmpty() })
            assertEquals("", ui(opened).sketch?.description)

            withContext(Dispatchers.Main) {
                opened.model.sketchSession.value!!.stroke(200f, 800f)
                opened.model.saveSketch("")
            }

            val image = nextSent<MapCommand.Update>(opened).changes.getValue("image").jsonObject
            assertFalse(image.containsKey("alt"))
            assertFalse(image.containsKey("caption"))
            until { effects.any { it is MapEvent.Snackbar } }
            val said = effects.filterIsInstance<MapEvent.Snackbar>().single()
            assertEquals(ShownText.Resource(R.string.sketch_replaced_the_picture), said.message)
            assertTrue(said.undo)
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `closing a sketch with strokes asks first, then leaves nothing behind`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }

        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f))
            opened.model.sketchSession.value!!.stroke(200f, 800f)
            opened.model.cancelSketch()
        }

        assertTrue(ui(opened).sketch!!.discarding)
        withContext(Dispatchers.Main) { opened.model.keepSketching() }
        assertFalse(ui(opened).sketch!!.discarding)
        assertEquals(SketchPhase.Drawing, ui(opened).sketch?.phase)

        sentSince(opened)
        withContext(Dispatchers.Main) { opened.model.cancelSketch(letGo = true) }
        nextSent<MapCommand.EndSpotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "end")) }

        assertNull(ui(opened).sketch)
        assertTrue(sentSince(opened).none { it is MapCommand.Update })
        assertFalse(File(maps.folder(id), "media").exists() && File(maps.folder(id), "media").list().orEmpty().isNotEmpty())
    }

    @Test
    fun `a sketch begun in the editor comes back to it, for a keyboard's focus to go back where it was`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }

        withContext(Dispatchers.Main) {
            opened.model.show(Panel.Editor("someday"))
            opened.model.sketch("someday")
        }

        assertNull(ui(opened).panel)
        nextSent<MapCommand.Spotlight>(opened)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f))
            opened.model.finishSketch()
        }

        nextSent<MapCommand.EndSpotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "end")) }
        val back = ui(opened).panel as Panel.Editor
        assertEquals("someday", back.id)
        assertTrue(back.afterSketch)
    }

    @Test
    fun `a new sketch left blank, or one the page can't bring up close, leaves nothing behind`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }

        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f))
            // Nothing drawn, so nothing to describe: Done ends it.
            opened.model.finishSketch()
        }

        assertTrue(ui(opened).sketch?.phase != SketchPhase.Describing)
        nextSent<MapCommand.EndSpotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "end")) }
        assertTrue(sentSince(opened).none { it is MapCommand.Update })

        // The page says at once that it can't, as for an idea it doesn't draw now.
        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "end")) }
        assertNull(ui(opened).sketch)
    }
}

/**
 * A storage that's slow when the test arms it: the next write that keeps a
 * version stops inside the repository, as its history is thinned (where it
 * asks whether the clock can be trusted), until the test lets it go.
 */
private class SlowStorage {
    private val armed = AtomicBoolean(false)
    private val stopped = CountDownLatch(1)
    private val released = CountDownLatch(1)

    /** The next write that keeps a version stops. */
    fun arm() = armed.set(true)

    /** Waits for the write to stop. */
    fun awaitStopped() = assertTrue("No write stopped", stopped.await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS))

    /** Lets the write go on. */
    fun release() = released.countDown()

    fun clockTrusted(): Boolean {
        if (armed.compareAndSet(true, false)) {
            stopped.countDown()
            released.await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)
        }

        return true
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

    /**
     * The ideas the map asks the page to add as one change, each waiting for
     * the test to answer how many joined. As the real page does, the test
     * says the change's save first ([say]), then answers.
     */
    val adding = Channel<Pair<MapCommand.AddNodes, CompletableDeferred<Int>>>(Channel.UNLIMITED)

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
            is MapCommand.AddNodes -> {
                val count = CompletableDeferred<Int>().also { adding.trySend(sent to it) }.await()
                protocolJson.encodeToJsonElement(PastedIdeas.serializer(), PastedIdeas(count))
            }
            is MapCommand.Paste -> {
                commands.trySend(sent)
                protocolJson.encodeToJsonElement(PastedIdeas.serializer(), PastedIdeas(1))
            }
            // A sketch's picture shows on the planet, as the real page says once it has loaded it.
            is MapCommand.PictureShown -> {
                commands.trySend(sent)
                JsonPrimitive(true)
            }
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

    override val ideaToShow = MutableStateFlow<IdeaToShow?>(null)

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
