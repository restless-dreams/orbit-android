package com.hyuchia.orbit.map

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.PendingShares
import com.hyuchia.orbit.R
import com.hyuchia.orbit.SharedFiles
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
import com.hyuchia.orbit.data.TRASHED_HISTORY
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.data.WriteResult
import com.hyuchia.orbit.data.settingsStoreIn
import com.hyuchia.orbit.media.SoundPlayback
import com.hyuchia.orbit.nearby.BrowserPresenting
import com.hyuchia.orbit.nearby.JoiningOptions
import com.hyuchia.orbit.nearby.anyone
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.CheckedMap
import com.hyuchia.orbit.page.CopiedIdea
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
import com.hyuchia.orbit.page.ViewMode
import com.hyuchia.orbit.page.mapBaseUrl
import com.hyuchia.orbit.page.protocolJson
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.ScreenTrouble
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
import com.hyuchia.orbit.study.AnswersUnsaved
import com.hyuchia.orbit.study.StudyCard
import com.hyuchia.orbit.study.StudyWrites
import com.hyuchia.orbit.ui.NoteCheck
import com.hyuchia.orbit.ui.TimeWords
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** How long a test waits for the view model to do what it should, before it fails. */
private const val WAIT_MILLISECONDS = 10_000L

/** YAML, as the test page's conversions write it: the markdown with this in front. */
private const val YAML = "yaml: "

/** A map presented along its path, and the same map once its second idea has a new name, so a new id. */
private const val TRIP = "# Trip\n## Old\n## Next\n"
private const val RENAMED_TRIP = "# Trip\n## Renamed\n## Next\n"

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

    /** Holds a paste's copy of pictures into a map while the test does something else meanwhile (see [pasteWhile]). */
    private val copies = CopyGate()

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words, room = { copies.room() }, clockTrusted = slow::clockTrusted)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))

    /** The main thread, as Android has one: the view model's work runs on it, one piece at a time. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val app = TestApp(words, maps)

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

    /**
     * What the screen hears, from now on, until `hearing` stops. It's heard on
     * the main thread and read on the test's, so `effects` is a list each read
     * takes as it stands ([CopyOnWriteArrayList]): a plain one can throw as an
     * effect comes in while `until` goes through it.
     */
    private fun CoroutineScope.hear(opened: Opened, effects: CopyOnWriteArrayList<MapEvent>) = launch(Dispatchers.Main) { opened.model.effects.collect { effects += it } }

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
    fun `a page given up on says nothing more to the screen, so its map stays hidden and nothing it saves is written`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id, ready = false)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.DidNotLoad)
            // The map ready after all, and an edit on it, from a page that no longer takes the screen's commands.
            opened.page.say(ready)
            opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", opened.load.revision))
        }

        assertEquals(PageProblem.DidNotLoad, opened.model.ui.value.pageProblem)
        assertFalse(opened.model.ui.value.ready)
        assertNull(opened.model.ui.value.map)
        delay(500)
        assertEquals("# Trip to Japan\n\n- Tokyo\n", maps.read(id))
    }

    @Test
    fun `a page that stops during a presentation is loaded again presenting, at the idea on screen`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n- Kyoto\n")
        val opened = open(id)

        withContext(Dispatchers.Main) {
            opened.model.startPresentation(seconds = 0, fromStart = true)
            opened.model.step(1)
        }

        val current = ui(opened).presentation!!.current
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Stopped) }
        assertEquals(PageProblem.Stopped, ui(opened).pageProblem)
        // The presentation goes on: the page's problem shows over it, with the way to load the map again.
        assertNotNull(ui(opened).presentation)

        withContext(Dispatchers.Main) { opened.model.reload() }

        val again = app.pages.last()
        assertTrue(again !== opened.page)

        val sent = withTimeout(WAIT_MILLISECONDS) {
            again.commands.receiveAsFlow().takeWhile { it != MapCommand.Focus(current) }.toList()
        }

        assertTrue(sent.toString(), sent.any { it is MapCommand.Load })
        assertTrue(sent.toString(), MapCommand.Present(true) in sent)
        assertTrue(sent.toString(), MapCommand.View(ViewMode.FOCUS) in sent)
        assertEquals(current, ui(opened).presentation?.current)
    }

    /** The map a presentation follows, with its second idea called Old, or, once its name changed, Renamed: a new id. */
    private fun trip(renamed: Boolean): MapState = MapState(
        "Trip",
        listOf(
            MapNode("trip", "Trip", childCount = 2),
            MapNode(if (renamed) "renamed" else "old", if (renamed) "Renamed" else "Old", parentId = "trip", depth = 1),
            MapNode("next", "Next", parentId = "trip", depth = 1, index = 1),
        ),
        path = listOf("trip"),
    )

    /** Opens the trip along its path, Trip, Old, Next, and presents it from the start on `screen`. */
    private suspend fun presentTrip(screen: Audience): Pair<String, Opened> {
        maps.load()
        val id = maps.add(TRIP)
        maps.savePath(id, listOf("trip", "old", "next"))
        val opened = open(id, ready = false)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Ready(trip(renamed = false)))
            opened.model.setAudience(screen)
            opened.model.startPresentation(seconds = 0, fromStart = true)
        }

        assertEquals(listOf(TRIP), screen.shown())
        return id to opened
    }

    @Test
    fun `Load it again on the phone loads the second screen again too, when its page stopped with the map's`() = runBlocking {
        val screen = TestScreen()
        val (_, opened) = presentTrip(screen)
        withContext(Dispatchers.Main) { opened.model.step(1) }

        // Android stops the process the app's pages share: the map's and the second screen's go together.
        withContext(Dispatchers.Main) {
            screen.trouble.value = ScreenTrouble.Stopped
            opened.page.say(MapMessage.Stopped)
            opened.model.reload()
        }

        assertEquals(1, screen.loadedAgain)
        assertNull(screen.trouble.value)
        // Its new page gets the map, at the idea on screen.
        assertEquals(listOf(TRIP, TRIP), screen.shown())
        assertEquals(AudienceCommand.Step("old", 1, 3), screen.heard.last())

        // A second screen that's well isn't loaded again with the map's page.
        withContext(Dispatchers.Main) {
            app.pages.last().say(MapMessage.Stopped)
            opened.model.reload()
        }

        assertEquals(1, screen.loadedAgain)
    }

    @Test
    fun `a save that crosses the presentation's start reaches the audience, and the presentation follows its new ids`() = runBlocking {
        val screen = TestScreen()
        val (_, opened) = presentTrip(screen)

        // Old was renamed on the map as the presentation started: its save comes after.
        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Saved(kind = "update", markdown = RENAMED_TRIP, state = trip(renamed = true), renamed = Renamed("old", "renamed"), revision = opened.load.revision))
        }

        assertEquals(listOf(TRIP, RENAMED_TRIP), screen.shown())
        assertEquals(listOf("trip", "renamed", "next"), ui(opened).presentation?.order)
        assertEquals(listOf("trip", "renamed", "next"), ui(opened).presentPath)

        withContext(Dispatchers.Main) { opened.model.step(1) }
        assertEquals(AudienceCommand.Step("renamed", 1, 3), screen.heard.last())
        assertTrue(MapCommand.Focus("renamed") in sentSince(opened))
    }

    @Test
    fun `a change from elsewhere during a presentation reaches the audience, and the presentation follows its new ids`() = runBlocking {
        val screen = TestScreen()
        val (id, opened) = presentTrip(screen)

        // The folder brings a version where Old has a new name, as another device wrote it.
        assertEquals(WriteResult.Written, maps.change(id, RENAMED_TRIP, base = TRIP, renamed = mapOf("old" to "renamed")))
        val restored = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
        assertEquals(RENAMED_TRIP, restored.markdown)
        // The page draws it, and says what the map is now.
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.State(trip(renamed = true))) }

        assertEquals(listOf(TRIP, RENAMED_TRIP), screen.shown())
        assertEquals(listOf("trip", "renamed", "next"), ui(opened).presentation?.order)

        withContext(Dispatchers.Main) { opened.model.step(1) }
        assertEquals(AudienceCommand.Step("renamed", 1, 3), screen.heard.last())
    }

    @Test
    fun `an idea on screen renamed on another device is still the one presented, and a removed one gives way to the next step`() = runBlocking {
        val screen = TestScreen()
        val (id, opened) = presentTrip(screen)
        withContext(Dispatchers.Main) { opened.model.step(1) }
        assertEquals("old", ui(opened).presentation?.current)

        // A laptop renames Old: the folder brings the new text, with no word of the rename.
        assertEquals(WriteResult.Written, maps.change(id, RENAMED_TRIP, base = TRIP))
        withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.State(trip(renamed = true))) }

        assertEquals(listOf("trip", "renamed", "next"), ui(opened).presentation?.order)
        assertEquals("renamed", ui(opened).presentation?.current)
        assertEquals(listOf("trip", "renamed", "next"), ui(opened).presentPath)
        assertEquals(listOf(AudienceCommand.Show(RENAMED_TRIP, AppTheme(dark = false, tokens = emptyMap()), mapBaseUrl(id), "renamed"), AudienceCommand.Step("renamed", 1, 3)), screen.heard.takeLast(2))
        assertTrue(MapCommand.Focus("renamed") in sentSince(opened))

        // Then it's removed there: the presentation goes on from the step in its place.
        assertEquals(WriteResult.Written, maps.change(id, "# Trip\n## Next\n", base = RENAMED_TRIP))
        withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
        val shorter = trip(renamed = true).let { state -> state.copy(nodes = listOf(state.nodes[0].copy(childCount = 1), state.nodes[2].copy(index = 0))) }
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.State(shorter)) }

        assertEquals(listOf("trip", "next"), ui(opened).presentation?.order)
        assertEquals("next", ui(opened).presentation?.current)
        assertEquals(AudienceCommand.Step("next", 1, 2), screen.heard.last())
    }

    @Test
    fun `the presentation's room in browsers is the map's, not its screen's, and stops with the presentation`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.model.startPresentation(seconds = 0) }

        val room = BrowserPresenting({ name -> File("src/main/assets/map/$name").readText() }, words, maps.folder(id), app.applicationScope, JoiningOptions(), preferredPort = 0, policy = anyone, listenOn = { null })
        withContext(Dispatchers.Main) { opened.model.browserRoom.start { room } }
        assertSame(room, opened.model.browserRoom.presenting.value)
        val port = room.port

        withContext(Dispatchers.Main) { opened.model.stopPresentation() }

        assertNull(opened.model.browserRoom.presenting.value)
        assertTrue(runCatching { Socket("127.0.0.1", port).close() }.isFailure)
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
    fun `an idea typed on the map as a newer or unreadable version arrives from elsewhere goes into its history, never over the version`() = runBlocking {
        maps.load()
        val original = "# Trip\n- Tokio\n"
        val newer = "---\nschemaVersion: 999\n---\n# Trip\n- Kyoto\n"
        val unreadable = "---\ntheme: [night\n---\n# Trip\n- Kyoto\n"

        // A new idea typed under the map's center, or an idea renamed, each as a version a newer Orbit wrote, or one Orbit
        // can't read all of, arrives: the page saves what was typed on the map as it was before it draws the version.
        val typings = listOf(
            MapMessage.EditorOpen(parentId = "trip") to saved("# Trip\n- Tokio\n- Kyoto draft\n", null).copy(kind = "add", added = "kyoto-draft"),
            MapMessage.EditorOpen(id = "tokio") to saved("# Trip\n- Tokyo draft\n", null).copy(kind = "update", renamed = Renamed("tokio", "tokyo-draft")),
        )

        for ((editing, typed) in typings) {
            for ((incoming, lock) in listOf(newer to ReadOnly.Newer, unreadable to ReadOnly.Unreadable)) {
                val case = "${typed.kind} as a version that's $lock arrives"
                val id = maps.add(original)
                val opened = open(id)
                val effects = CopyOnWriteArrayList<MapEvent>()
                val hearing = hear(opened, effects)

                try {
                    withContext(Dispatchers.Main) { opened.page.say(editing) }
                    assertTrue(case, ui(opened).editing)

                    assertEquals(case, WriteResult.Written, maps.change(id, incoming, base = original, reason = VersionReason.Elsewhere))
                    val restore = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
                    assertEquals(case, incoming, restore.markdown)
                    assertEquals(case, opened.load.revision + 1, restore.revision)

                    withContext(Dispatchers.Main) {
                        opened.page.say(typed.copy(revision = opened.load.revision))
                        opened.page.say(MapMessage.EditorClose)
                        opened.page.say(MapMessage.State(ready.state.copy(readOnly = lock.name.lowercase())))
                    }

                    until { withContext(Dispatchers.Main) { MapEvent.KeptInHistory in effects } }
                    until { inHistory(id, typed.markdown) }
                    assertEquals(case, incoming, maps.read(id))
                    assertEquals(case, lock, ui(opened).readOnly)
                    assertFalse(case, ui(opened).editing)
                    assertEquals(case, incoming, withContext(Dispatchers.Main) { opened.model.currentMarkdown() })
                    // The map before the change waits on undo's stack, but a map that can only be looked at offers no Undo.
                    assertTrue(case, ui(opened).canUndo)
                    assertFalse(case, ui(opened).undoable)
                } finally {
                    hearing.cancel()
                    close(opened)
                }
            }
        }
    }

    /** The commands sent from now on, up to the first `Restore`, which is the last of them. */
    private suspend fun sentUntilRestore(opened: Opened): List<MapCommand> =
        withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().transformWhile { emit(it); it !is MapCommand.Restore }.toList() }

    /**
     * Opens a field on the map, as the page says it did, whose typing the page
     * saves as `typed` when the app closes it, at the revision the screen is
     * on (`revision`), then says it closed.
     */
    private suspend fun typing(opened: Opened, editing: MapMessage.EditorOpen, typed: MapMessage.Saved) {
        opened.page.stopping = { save ->
            if (save) {
                opened.page.say(typed)
            }

            opened.page.say(MapMessage.EditorClose)
            opened.page.stopping = null
        }

        withContext(Dispatchers.Main) { opened.page.say(editing) }
        sentSince(opened)
    }

    @Test
    fun `an idea typed on the map as it goes to the trash is in the history it took there, which restoring it brings back`() = runBlocking {
        maps.load()
        val original = "# Trip\n- Tokio\n"
        val typed = "# Trip\n- Tokio\n- Kyoto\n"
        val id = maps.add(original)
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            withContext(Dispatchers.Main) { opened.page.say(MapMessage.EditorOpen(parentId = "trip")) }

            // Deleted from the list beside it, or by the folder, as a new idea is typed: the page is locked, and says the
            // save of what was typed first, as it does.
            assertNotNull(maps.delete(id))
            assertEquals(MapCommand.Lock("deleted"), nextSent<MapCommand.Lock>(opened))

            withContext(Dispatchers.Main) {
                opened.page.say(saved(typed, opened.load.revision).copy(kind = "add", added = "kyoto"))
                opened.page.say(MapMessage.EditorClose)
                opened.page.say(MapMessage.State(ready.state.copy(readOnly = "deleted")))
            }

            until { withContext(Dispatchers.Main) { MapEvent.Snackbar(ShownText.Resource(R.string.map_change_kept_in_trash)) in effects } }

            // Restored, the map is as it was, and its history holds what was typed.
            assertEquals(id, maps.restore(maps.trash.value.single { it.id == id }))
            assertEquals(original, maps.read(id))
            assertTrue(inHistory(id, typed))
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `an idea typed on the map as a newer Orbit takes the storage stays on the screen, unsaved, and is written once it can be`() = runBlocking {
        maps.load()
        val original = "# Trip\n- Tokio\n"
        val typed = "# Trip\n- Tokyo\n"
        val id = maps.add(original)
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)
        val layout = File(root, "maps/.layout.json")
        val before = layout.readText()

        try {
            withContext(Dispatchers.Main) { opened.page.say(MapMessage.EditorOpen(id = "tokio")) }

            // The storage's layout as a newer Orbit left it, read as Orbit starts again: its maps are only shown now. The
            // page is locked, and says the save of what was typed first.
            layout.writeText("{ a newer Orbit's layout")
            maps.load()
            assertEquals(MapCommand.Lock("storage"), nextSent<MapCommand.Lock>(opened))

            withContext(Dispatchers.Main) {
                opened.page.say(saved(typed, opened.load.revision).copy(kind = "update", renamed = Renamed("tokio", "tokyo")))
                opened.page.say(MapMessage.EditorClose)
                opened.page.say(MapMessage.State(ready.state.copy(readOnly = "storage")))
            }

            // Not taken for text that would empty the map: it stays on the screen, which says it isn't saved yet.
            until { ui(opened).unsaved }
            assertEquals(typed, withContext(Dispatchers.Main) { opened.model.currentMarkdown() })
            assertEquals(original, maps.read(id))
            assertFalse(withContext(Dispatchers.Main) { MapEvent.Snackbar(ShownText.Resource(R.string.map_change_not_saved)) in effects })

            // The storage Orbit's own again, tried again, it's written.
            layout.writeText(before)
            maps.load()
            withContext(Dispatchers.Main) { opened.model.saveAgain() }
            until { maps.read(id) == typed }
        } finally {
            hearing.cancel()
            layout.writeText(before)
        }
    }

    @Test
    fun `an Undo as an idea is renamed on the map keeps the typing first, and takes it back, which Redo brings again`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip\n- Tokyo\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip\n- Tokyo\n" }

        // Tokyo renamed on the map, which the map Undo goes back to doesn't have: drawn with the field open, it would
        // close it and keep nothing.
        val typed = "# Trip\n- Tokyo Tower\n"
        typing(opened, MapMessage.EditorOpen(id = "tokyo"), saved(typed, opened.load.revision).copy(kind = "update", renamed = Renamed("tokyo", "tokyo-tower")))
        withContext(Dispatchers.Main) { opened.model.undo() }

        // The field closes first, its typing saved, and Undo takes that back.
        val sent = sentUntilRestore(opened)
        assertEquals(sent.toString(), MapCommand.StopEditing(true), sent.first())
        assertEquals("# Trip\n- Tokyo\n", (sent.last() as MapCommand.Restore).markdown)
        until { maps.read(id) == "# Trip\n- Tokyo\n" }

        withContext(Dispatchers.Main) { opened.model.redo() }
        assertEquals(typed, nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.read(id) == typed }
    }

    @Test
    fun `an Undo to text Orbit can't read all of, as an idea is typed on the map, keeps the typing first, and takes it back`() = runBlocking {
        maps.load()
        val original = "# Trip\n- Tokio\n"
        val unreadable = "---\ntheme: [night\n---\n# Trip\n- Kyoto\n"
        val fixed = "# Trip\n- Kyoto\n"
        val id = maps.add(original)
        val opened = open(id)

        // Text Orbit can't read all of arrived from elsewhere, then text it can: Undo goes back to the first.
        assertEquals(WriteResult.Written, maps.change(id, unreadable, base = original, reason = VersionReason.Elsewhere))
        assertEquals(unreadable, nextSent<MapCommand.Restore>(opened).markdown)
        assertEquals(WriteResult.Written, maps.change(id, fixed, base = unreadable, reason = VersionReason.Elsewhere))
        val caughtUp = nextSent<MapCommand.Restore>(opened)
        assertEquals(fixed, caughtUp.markdown)

        // A new idea typed under the map's center: drawn with the field open, text that can't be changed would close
        // it, and keep nothing.
        val typed = "# Trip\n- Kyoto\n- Nara\n"
        typing(opened, MapMessage.EditorOpen(parentId = "trip"), saved(typed, caughtUp.revision).copy(kind = "add", added = "nara"))
        withContext(Dispatchers.Main) { opened.model.undo() }

        // The typing is saved, and Undo takes that back first; the text Orbit can't read is the next step back.
        val sent = sentUntilRestore(opened)
        assertEquals(sent.toString(), MapCommand.StopEditing(true), sent.first())
        assertEquals(fixed, (sent.last() as MapCommand.Restore).markdown)
        until { maps.read(id) == fixed }

        withContext(Dispatchers.Main) { opened.model.undo() }
        assertEquals(unreadable, nextSent<MapCommand.Restore>(opened).markdown)

        // Nothing typed is lost: Redo brings it back.
        withContext(Dispatchers.Main) {
            opened.model.redo()
            opened.model.redo()
        }

        until { maps.read(id) == typed }
    }

    @Test
    fun `a Redo as an idea is typed on the map keeps the typing as the map's latest change, with nothing left to redo`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip\n- Tokio\n- Kyoto\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip\n- Tokio\n- Kyoto\n" }
        withContext(Dispatchers.Main) { opened.model.undo() }
        assertEquals("# Trip\n- Tokio\n", nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.read(id) == "# Trip\n- Tokio\n" }

        // A new idea typed under the map's center, then Redo: the typing is a change of its own, made after the undo.
        val typed = "# Trip\n- Tokio\n- Nara\n"
        typing(opened, MapMessage.EditorOpen(parentId = "trip"), saved(typed, opened.load.revision).copy(kind = "add", added = "nara"))
        withContext(Dispatchers.Main) { opened.model.redo() }

        until { maps.read(id) == typed }
        assertFalse(ui(opened).canRedo)
        assertEquals(listOf(MapCommand.StopEditing(true)), sentSince(opened).filter { it is MapCommand.StopEditing || it is MapCommand.Restore })
    }

    @Test
    fun `an Undo as an idea is typed on a page too busy to answer in time isn't made, and the typing it saves late stays the map's`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip\n- Tokyo\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip\n- Tokyo\n" }

        // Tokyo renamed on a page busy for longer than the app waits: it doesn't answer the check, and saves the typing
        // only after, at the revision an undo keeps, so over it.
        opened.page.checksHang = true
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.EditorOpen(id = "tokyo")) }
        sentSince(opened)
        withContext(Dispatchers.Main) { opened.model.undo() }
        withTimeout(WAIT_MILLISECONDS) { opened.page.checksGivenUp.receive() }

        val typed = "# Trip\n- Tokyo Tower\n"

        withContext(Dispatchers.Main) {
            opened.page.say(saved(typed, opened.load.revision).copy(kind = "update", renamed = Renamed("tokyo", "tokyo-tower")))
            opened.page.say(MapMessage.EditorClose)
        }

        // No undo was drawn: the page, the screen and the file all have the typing, and Undo takes it back now.
        until { maps.read(id) == typed }
        assertEquals(listOf(MapCommand.StopEditing(true)), sentSince(opened).filter { it is MapCommand.StopEditing || it is MapCommand.Restore })
        assertEquals(typed, withContext(Dispatchers.Main) { opened.model.currentMarkdown() })
        opened.page.checksHang = false
        withContext(Dispatchers.Main) { opened.model.undo() }
        assertEquals("# Trip\n- Tokyo\n", nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.read(id) == "# Trip\n- Tokyo\n" }
    }

    @Test
    fun `a save that finds the map gone keeps nothing in an older map in the trash that had its id`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip\n- Kyoto\n")

        // An older map in the trash with this one's id, deleted before the screen opened, as a map whose id was taken
        // again after it went might leave one.
        val other = requireNotNull(maps.delete(maps.add("# Plans\n- Someday\n")))
        val trashed = File(other.folder.parentFile, "$id-${other.deleted}")
        assertTrue(other.folder.renameTo(trashed))
        maps.load()
        assertEquals(trashed, maps.trash.value.single { it.id == id }.folder)
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            // Its folder gone without going to the trash, as storage taken away can leave it: a save finds it missing.
            assertTrue(maps.folder(id).deleteRecursively())
            withContext(Dispatchers.Main) { opened.page.say(saved("# Trip\n- Kyoto\n- Nara\n", opened.load.revision)) }
            assertEquals(MapCommand.Lock("deleted"), nextSent<MapCommand.Lock>(opened))

            // The map deleted before the screen opened is another one: it keeps nothing of this one, and nothing says so.
            assertFalse(withContext(Dispatchers.Main) { MapEvent.Snackbar(ShownText.Resource(R.string.map_change_kept_in_trash)) in effects })
            assertTrue(File(trashed, TRASHED_HISTORY).listFiles().orEmpty().none { "Nara" in it.readText() })
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
    fun `a read-only version preview doesn't lose the current map's last edit on close`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        val last = "# Trip to Japan\n\n- Tokyo\n- Kyoto\n"
        slow.arm()
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        slow.awaitStopped()

        withContext(Dispatchers.Main) {
            opened.page.say(saved(last, opened.load.revision))
            opened.model.openHistory()
        }

        until { ui(opened).history?.versions?.size == 1 }
        val version = ui(opened).history!!.versions!!.single().time

        withContext(Dispatchers.Main) { opened.model.showVersion(version) }
        until { ui(opened).showingVersion }
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.State(newerReady.state)) }
        assertEquals(ReadOnly.Newer, ui(opened).readOnly)

        close(opened)
        slow.release()

        until { maps.read(id) == last }
    }

    @Test
    fun `a map opened again as its last edit is still being written catches up with it, and its next edit lands on it`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val first = open(id)
        val last = "# Trip to Japan\n\n- Tokyo\n"
        slow.arm()
        withContext(Dispatchers.Main) { first.page.say(saved(last, first.load.revision)) }
        slow.awaitStopped()

        // Closed and opened again at once, as the last edit is still being written: the map is read as it was.
        close(first)
        val again = open(id)
        assertEquals("# Trip to Japan\n\n- Tokio\n", again.load.markdown)
        slow.release()

        // The write lands: the screen opened again draws the map as it left it.
        val restored = withTimeout(WAIT_MILLISECONDS) { again.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
        assertEquals(last, restored.markdown)
        until { maps.read(id) == last }

        withContext(Dispatchers.Main) { again.page.say(saved("$last- Kyoto\n", restored.revision)) }
        until { maps.read(id) == "$last- Kyoto\n" }
        assertFalse(inHistory(id, "# Trip to Japan\n\n- Tokio\n- Kyoto\n"))
    }

    @Test
    fun `a save that would empty the map isn't written, and the screen shows the map as it is, kept before the next edit`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            // Every idea taken off, as typing over the text by hand can: the map stays as it was, and so does the screen.
            withContext(Dispatchers.Main) { opened.page.say(saved("# \n", opened.load.revision)) }
            val restored = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().filterIsInstance<MapCommand.Restore>().first() }
            assertEquals("# Trip to Japan\n\n- Tokio\n", restored.markdown)
            until { withContext(Dispatchers.Main) { MapEvent.Snackbar(ShownText.Resource(R.string.map_change_not_saved)) in effects } }
            assertEquals("# Trip to Japan\n\n- Tokio\n", maps.read(id))

            // The next edit, on the map as it shows again, keeps the map as it was in its history first.
            withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", restored.revision)) }
            until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n" }
            assertTrue(inHistory(id, "# Trip to Japan\n\n- Tokio\n"))
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a closing screen's last edit that finds the map saved by another screen goes into the history, and that screen says so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val closing = open(id)
        val showing = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(showing, effects)

        try {
            // The screen that stays saves first, as one opened again at once may while the other's last word is slow.
            withContext(Dispatchers.Main) { showing.page.say(saved("# Trip to Japan\n\n- Tokio\n- Osaka\n", showing.load.revision)) }
            until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Osaka\n" }

            // The other closes with an edit made on the map as it was: written as it closes, it can't go over the first.
            withContext(Dispatchers.Main) {
                closing.page.say(saved("# Trip to Japan\n\n- Tokyo\n", closing.load.revision))
                closing.store.clear()
            }

            until { inHistory(id, "# Trip to Japan\n\n- Tokyo\n") }
            until { withContext(Dispatchers.Main) { MapEvent.KeptInHistory in effects } }
            assertEquals("# Trip to Japan\n\n- Tokio\n- Osaka\n", maps.read(id))
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a read begun before the screen's own save landed is read again, rather than taken for a change from elsewhere`() = runBlocking {
        var written = "# Trip to Japan\n\n- Tokio\n"
        val reads = mutableListOf<String>()

        // The screen's save lands as the file is read: the read has the text before it.
        val settled = readSettled({
            reads += written
            val text = if (reads.size == 1) "# Trip to Japan\n\n- Tokio\n" else "# Trip to Japan\n\n- Tokyo\n"

            if (reads.size == 1) {
                written = "# Trip to Japan\n\n- Tokyo\n"
            }

            text
        }, { written })

        assertEquals("# Trip to Japan\n\n- Tokyo\n", settled)
        assertEquals(2, reads.size)

        // Read while nothing of the screen's landed, once is enough.
        reads.clear()
        assertEquals("# Trip to Japan\n\n- Tokyo\n", readSettled({ reads += written; written }, { written }))
        assertEquals(1, reads.size)
        assertNull(readSettled({ null }, { written }))
    }

    @Test
    fun `a version restored as the source editor is open shows in it, and what's typed next is typed on it`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n" }
        editAsText(opened)
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.size == 1 }
        val version = ui(opened).history!!.versions!!.single().time

        withContext(Dispatchers.Main) { opened.model.restoreVersion(version) }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n" }
        until { ui(opened).source?.text == "# Trip to Japan\n\n- Tokio\n" }

        // Typed on the version brought back: the restore stays, with what was typed.
        withContext(Dispatchers.Main) { opened.model.editSource("# Trip to Japan\n\n- Tokio\n- Kyoto\n") }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n- Kyoto\n" }
    }

    @Test
    fun `a version restored again once it's the map changes nothing more, and leaves one step to undo`() = runBlocking {
        maps.load()
        val original = "# Trip to Japan\n\n- Tokio\n"
        val current = "# Trip to Japan\n\n- Tokyo\n"
        val id = maps.add(original)
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved(current, opened.load.revision)) }
        until { maps.read(id) == current }
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.size == 1 }
        val version = ui(opened).history!!.versions!!.single().time
        withContext(Dispatchers.Main) { opened.model.restoreVersion(version) }
        assertEquals(original, nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.read(id) == original }

        // Restore tapped again, as a second tap lands: that version is on show, and it's the map already.
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.any { it.time == version } == true }
        withContext(Dispatchers.Main) { opened.model.showVersion(version) }
        until { ui(opened).showingVersion }
        sentSince(opened)
        withContext(Dispatchers.Main) { opened.model.restoreVersion(version) }
        until { ui(opened).history == null }

        // The page shows the map as it is now again, which takes changes, and nothing else changes.
        assertEquals(listOf(MapCommand.Preview(original, editable = true)), sentSince(opened))
        assertNull(ui(opened).panel)

        // One undo takes the restore back.
        withContext(Dispatchers.Main) { opened.model.undo() }
        assertEquals(current, nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.read(id) == current }
    }

    @Test
    fun `text typed in the source editor as an earlier version is on show is drawn on the map as it is now, which takes it`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n" }
        editAsText(opened)
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.size == 1 }
        withContext(Dispatchers.Main) { opened.model.showVersion(ui(opened).history!!.versions!!.single().time) }
        until { ui(opened).showingVersion }
        sentSince(opened)

        withContext(Dispatchers.Main) { opened.model.editSource("# Trip to Japan\n\n- Tokyo\n- Kyoto\n") }

        // The map as it is now comes back first, as before any change, and the typing is drawn on it.
        val sent = withTimeout(WAIT_MILLISECONDS) { opened.page.commands.receiveAsFlow().takeWhile { it !is MapCommand.Source }.toList() }
        assertEquals(listOf(MapCommand.Preview("# Trip to Japan\n\n- Tokyo\n", editable = true)), sent)
        assertFalse(ui(opened).showingVersion)
        assertNotNull(ui(opened).history)
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Kyoto\n" }
    }

    @Test
    fun `text typed in the source editor right before a version is restored goes into the history, and Undo brings it back`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n", opened.load.revision)) }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n" }
        editAsText(opened)
        withContext(Dispatchers.Main) { opened.model.openHistory() }
        until { ui(opened).history?.versions?.size == 1 }
        val version = ui(opened).history!!.versions!!.single().time

        // Typed, and the version restored before the typing's pause was over.
        withContext(Dispatchers.Main) {
            opened.model.editSource("# Trip to Japan\n\n- Tokyo\n- Osaka\n")
            opened.model.restoreVersion(version)
        }

        until { maps.read(id) == "# Trip to Japan\n\n- Tokio\n" }
        until { ui(opened).source?.text == "# Trip to Japan\n\n- Tokio\n" }
        assertTrue(inHistory(id, "# Trip to Japan\n\n- Tokyo\n- Osaka\n"))

        withContext(Dispatchers.Main) { opened.model.undo() }
        until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Osaka\n" }
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
    fun `undoing a rename takes the path and the rehearsal back to the idea's old id, and redo takes them on again`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokio\n- Kyoto\n")
        maps.savePath(id, listOf("trip-to-japan", "tokio", "kyoto"))
        maps.saveRehearsal(id, mapOf("trip-to-japan" to 11, "tokio" to 22, "kyoto" to 33))
        val opened = open(id)
        until { ui(opened).presentPath == listOf("trip-to-japan", "tokio", "kyoto") }

        // Typed on the map: the new text gives the idea a new id, and its place on the path and in the rehearsal follow it.
        withContext(Dispatchers.Main) { opened.page.say(saved("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", opened.load.revision).copy(renamed = Renamed("tokio", "tokyo"))) }
        until { maps.summary(id)?.metadata?.path == listOf("trip-to-japan", "tokyo", "kyoto") }
        sentSince(opened)

        // Undo: the old text, and with it the old id, for the path and the rehearsal too.
        withContext(Dispatchers.Main) { opened.model.undo() }
        assertEquals("# Trip to Japan\n\n- Tokio\n- Kyoto\n", nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.summary(id)?.metadata?.path == listOf("trip-to-japan", "tokio", "kyoto") }
        assertEquals(listOf("trip-to-japan", "tokio", "kyoto"), ui(opened).presentPath)
        until { maps.summary(id)?.metadata?.rehearsal == mapOf("trip-to-japan" to 11, "tokio" to 22, "kyoto" to 33) }

        // Redo: the new text again, and its id.
        withContext(Dispatchers.Main) { opened.model.redo() }
        assertEquals("# Trip to Japan\n\n- Tokyo\n- Kyoto\n", nextSent<MapCommand.Restore>(opened).markdown)
        until { maps.summary(id)?.metadata?.path == listOf("trip-to-japan", "tokyo", "kyoto") }
        assertEquals(listOf("trip-to-japan", "tokyo", "kyoto"), ui(opened).presentPath)
        until { maps.summary(id)?.metadata?.rehearsal == mapOf("trip-to-japan" to 11, "tokyo" to 22, "kyoto" to 33) }
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
    fun `an answer the storage can't take says so, and is written with Try again once there's room`() = runBlocking {
        maps.load()
        val id = maps.add("# Verbs\n\n## Ser\n\n- soy\n\n## Estar\n\n- estoy\n")
        val opened = open(id, ready = false)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Ready(verbs)) }
        // Where the map's details go there's a folder: nothing can be written there, as on a full disk.
        val details = File(maps.folder(id), "meta.json")
        details.delete()
        File(details, "full").apply { parentFile!!.mkdirs() }.writeText("")

        withContext(Dispatchers.Main) {
            opened.model.startStudy(fromTop = true)
            opened.model.revealCard()
            opened.model.answerCard(knew = true)
        }

        until { ui(opened).answersUnsaved == AnswersUnsaved.Storage }
        assertNull(maps.summary(id)?.metadata?.study)

        // Room again: Try again writes it at once, and the note goes.
        details.deleteRecursively()
        withContext(Dispatchers.Main) { opened.model.tryAnswersAgain() }
        until { maps.summary(id)?.metadata?.study?.keys == setOf("ser") }
        until { ui(opened).answersUnsaved == null }
    }

    @Test
    fun `answers given while a newer Orbit keeps the maps say why they wait, rather than seeming kept`() = runBlocking {
        maps.load()
        val id = maps.add("# Verbs\n\n## Ser\n\n- soy\n\n## Estar\n\n- estoy\n")
        File(root, "maps/.layout.json").writeText("""{"version":999,"compatible":999}""")
        maps.load()
        assertFalse(maps.writable.value)
        val opened = open(id, ready = false)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Ready(verbs))
            opened.model.startStudy(fromTop = true)
            opened.model.revealCard()
            opened.model.answerCard(knew = true)
        }

        until { ui(opened).answersUnsaved == AnswersUnsaved.Newer }
        assertNull(maps.summary(id)?.metadata?.study)
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
    fun `an idea copied from a private map that locks meanwhile goes on no clipboard, however quickly it's unlocked`() = runBlocking {
        maps.load()
        val id = maps.add("# Diary\n\n- Monday\n", isPrivate = true)
        withContext(Dispatchers.Main) { app.privateLock.unlock() }
        val opened = open(id)
        val sun = withContext(Dispatchers.Main) { opened.model.ui.value.sun!!.id }

        // The page writes the idea out as the screen turns off, and the phone's owner unlocks Orbit again.
        withContext(Dispatchers.Main) { opened.model.copy(sun) }
        val writing = withTimeout(WAIT_MILLISECONDS) { opened.page.copying.receive() }

        withContext(Dispatchers.Main) {
            app.privateLock.screenOff()
            app.privateLock.unlock()
        }

        writing.complete(CopiedIdea("# Diary while it locked", "Diary"))

        // Copied again, unlocked all along, it goes.
        withContext(Dispatchers.Main) { opened.model.copy(sun) }
        withTimeout(WAIT_MILLISECONDS) { opened.page.copying.receive() }.complete(CopiedIdea("# Diary", "Diary"))
        until { app.clipboard.copied.isNotEmpty() }

        assertEquals(listOf("# Diary"), app.clipboard.copied.toList())
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
        val effects = CopyOnWriteArrayList<MapEvent>()
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

    @Test
    fun `pictures copied in for a paste the page didn't answer take none of the map's room, and stay for the page's late paste`() = runBlocking {
        maps.load()
        val from = maps.add("# Trip to Japan\n\n- Kyoto\n\n  ![](media/temple.jpg)\n")
        File(maps.folder(from), "media").mkdirs()
        File(maps.folder(from), "media/temple.jpg").writeBytes(ByteArray(1_000) { 1 })
        val id = maps.add("# Plans\n\n- Autumn\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            val target = withContext(Dispatchers.Main) { opened.model.ui.value.sun!!.id }
            opened.page.pastesFail = true
            withContext(Dispatchers.Main) { opened.model.pasteIdeas(target, Clip.Ideas("- Kyoto\n\n  ![](media/temple.jpg)\n", sourceMap = from)) }
            until { withContext(Dispatchers.Main) { effects.any { it is MapEvent.Snackbar } } }

            // The copy stays, should the page paste it late, but the map's room is as it was.
            assertEquals(1_000L, File(maps.folder(id), "media/temple.jpg").length())
            assertEquals(0L, maps.mediaSizeNow(id))
        } finally {
            hearing.cancel()
        }
    }

    /**
     * Pastes `clip` under the idea in focus, and while its files are being
     * copied, off the main thread, does `meanwhile` on it: the paste's work
     * is given back, to wait for.
     */
    private suspend fun pasteWhile(opened: Opened, clip: Clip.Ideas, meanwhile: () -> Unit): Job {
        val copy = copies.hold()

        val paste = withContext(Dispatchers.Main) {
            val scope = opened.model.viewModelScope.coroutineContext.job
            val target = opened.model.ui.value.sun!!.id
            val before = scope.children.toSet()
            opened.model.pasteIdeas(target, clip)
            (scope.children.toSet() - before).single()
        }

        // The copy is under way off the main thread, held before its first file: however fast the disk, it's not back yet.
        copy.awaitReached()
        withContext(Dispatchers.Main) { meanwhile() }
        copy.release()
        return paste
    }

    /** Map `id`, from Trip to Japan's Kyoto, which shows its temple, and that idea as copied, ready to paste. */
    private suspend fun temple(isPrivate: Boolean = false): Pair<String, Clip.Ideas> {
        val from = maps.add("# Trip to Japan\n\n- Kyoto\n\n  ![](media/temple.jpg)\n", isPrivate = isPrivate)
        File(maps.folder(from), "media").mkdirs()
        File(maps.folder(from), "media/temple.jpg").writeBytes(ByteArray(1_000) { 1 })
        return from to Clip.Ideas("- Kyoto\n\n  ![](media/temple.jpg)\n", sourceMap = from)
    }

    @Test
    fun `ideas from a private map that locks while their pictures are copied paste as text, and none of its pictures stay in this one`() = runBlocking {
        maps.load()
        val (_, copied) = temple(isPrivate = true)
        val id = maps.add("# Plans\n\n- Autumn\n")
        withContext(Dispatchers.Main) { app.privateLock.unlock() }
        val opened = open(id)
        sentSince(opened)
        // The page saves the map with what it pasted before it answers, as the real one does: the text points at temple.jpg by
        // the name its copy would have, before the paste is done.
        var fileWhenPasted: Boolean? = null

        opened.page.pasting = { paste ->
            fileWhenPasted = File(maps.folder(id), "media/temple.jpg").exists()
            maps.write(id, maps.read(id) + "\n" + paste.text)
        }

        val paste = pasteWhile(opened, copied) { app.privateLock.lock() }
        withTimeout(WAIT_MILLISECONDS) { paste.join() }

        // As when the private map was locked to begin with: its text, and none of its files, gone before the text landed.
        assertEquals(copied.text, sentSince(opened).filterIsInstance<MapCommand.Paste>().single().text)
        assertEquals(false, fileWhenPasted)
        assertFalse(File(maps.folder(id), "media/temple.jpg").exists())
        assertEquals(0L, maps.mediaSizeNow(id))
    }

    @Test
    fun `a private map that locks while pasted pictures are copied into it takes nothing, and keeps none of them`() = runBlocking {
        maps.load()
        val (_, copied) = temple()
        val id = maps.add("# Secret plans\n\n- Autumn\n", isPrivate = true)
        withContext(Dispatchers.Main) { app.privateLock.unlock() }
        val opened = open(id)
        sentSince(opened)

        val paste = pasteWhile(opened, copied) { app.privateLock.lock() }
        withTimeout(WAIT_MILLISECONDS) { paste.join() }

        assertTrue(sentSince(opened).none { it is MapCommand.Paste })
        assertFalse(File(maps.folder(id), "media/temple.jpg").exists())
        assertEquals(0L, maps.mediaSizeNow(id))
        assertEquals("# Secret plans\n\n- Autumn\n", maps.read(id))
    }

    @Test
    fun `pictures copied for a paste the page never had, as its process stopped, go again, but stay for one it may yet make`() = runBlocking {
        maps.load()
        val (_, copied) = temple(isPrivate = true)
        val id = maps.add("# Plans\n\n- Autumn\n")
        withContext(Dispatchers.Main) { app.privateLock.unlock() }
        val opened = open(id)
        sentSince(opened)

        for (kind in listOf(PageException.Kind.Stopped, PageException.Kind.DidNotLoad, PageException.Kind.TooOld)) {
            opened.page.pasteUnsent = kind
            val paste = pasteWhile(opened, copied) {}
            withTimeout(WAIT_MILLISECONDS) { paste.join() }

            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
            assertFalse(kind.name, File(maps.folder(id), "media/temple.jpg").exists())
            assertEquals(kind.name, 0L, maps.mediaSizeNow(id))
        }
    }

    @Test
    fun `pictures copied for a paste whose map closes before it lands go again`() = runBlocking {
        maps.load()
        val (_, copied) = temple()
        val id = maps.add("# Plans\n\n- Autumn\n")
        val opened = open(id)
        sentSince(opened)

        val paste = pasteWhile(opened, copied) { opened.store.clear() }
        withTimeout(WAIT_MILLISECONDS) { paste.join() }

        assertTrue(sentSince(opened).none { it is MapCommand.Paste })
        assertFalse(File(maps.folder(id), "media/temple.jpg").exists())
        assertEquals(0L, maps.mediaSizeNow(id))
    }

    /** The page's save of a change to the example's map: its ideas given by `nodes` (id to text), `renamed` if one was. */
    private fun savedWith(opened: Opened, kind: String, nodes: List<Pair<String, String>>, renamed: Renamed? = null): MapMessage.Saved {
        val example = saved("# Trip to Japan\n\n" + nodes.drop(1).joinToString("") { "- ${it.second}\n" }, opened.load.revision)
        val ideas = nodes.mapIndexed { index, (id, text) -> MapNode(id, text, parentId = "trip-to-japan".takeIf { index > 0 }, depth = if (index == 0) 0 else 1, index = maxOf(index - 1, 0)) }
        return example.copy(kind = kind, state = example.state.copy(nodes = ideas), renamed = renamed)
    }

    @Test
    fun `ideas pasted with pictures that take a while go under their idea renamed meanwhile, and under no other that takes its id after it's removed`() = runBlocking {
        maps.load()
        val from = maps.add("# Trip to Japan\n\n- Kyoto\n\n  ![](media/temple.jpg)\n")
        File(maps.folder(from), "media").mkdirs()
        File(maps.folder(from), "media/temple.jpg").writeBytes(ByteArray(1_000) { 1 })
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n- Someday\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)
        val copied = Clip.Ideas("- Kyoto\n\n  ![](media/temple.jpg)\n", sourceMap = from)

        try {
            sentSince(opened)

            // Pasted into Someday, which is renamed as its picture is copied in: the ideas go under it, by its new id.
            withContext(Dispatchers.Main) {
                opened.model.pasteIdeas("someday", copied)
                opened.page.say(savedWith(opened, "update", listOf("trip-to-japan" to "Trip to Japan", "tokyo" to "Tokyo", "soon" to "Soon"), Renamed("someday", "soon")))
            }

            assertEquals("soon", nextSent<MapCommand.Paste>(opened).parentId)

            // Pasted into Soon, which is removed, and Tokyo renamed Soon after it: the ideas go nowhere, and that's said.
            withContext(Dispatchers.Main) {
                opened.model.pasteIdeas("soon", copied)
                opened.page.say(savedWith(opened, "remove", listOf("trip-to-japan" to "Trip to Japan", "tokyo" to "Tokyo")))
                opened.page.say(savedWith(opened, "update", listOf("trip-to-japan" to "Trip to Japan", "soon" to "Soon"), Renamed("tokyo", "soon")))
            }

            until { effects.any { it is MapEvent.Snackbar && it.message == ShownText.Resource(R.string.map_paste_idea_gone) } }
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
            assertTrue(effects.none { it is MapEvent.Snackbar && it.message is ShownText.Plain })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `ideas pasted into an idea that Undo takes away while their pictures copy in go nowhere, and say so`() = runBlocking {
        maps.load()
        val from = maps.add("# Trip to Japan\n\n- Kyoto\n\n  ![](media/temple.jpg)\n")
        File(maps.folder(from), "media").mkdirs()
        File(maps.folder(from), "media/temple.jpg").writeBytes(ByteArray(1_000) { 1 })
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)
        val tokyo = listOf("trip-to-japan" to "Trip to Japan", "tokyo" to "Tokyo")

        try {
            // Soon is added, then pasted into; Undo takes Soon away as the pasted ideas' picture copies in.
            withContext(Dispatchers.Main) { opened.page.say(savedWith(opened, "add", tokyo + ("soon" to "Soon"))) }
            sentSince(opened)

            withContext(Dispatchers.Main) {
                opened.model.pasteIdeas("soon", Clip.Ideas("- Kyoto\n\n  ![](media/temple.jpg)\n", sourceMap = from))
                opened.model.undo()
                opened.page.say(MapMessage.State(savedWith(opened, "update", tokyo).state))
            }

            until { effects.any { it is MapEvent.Snackbar && it.message == ShownText.Resource(R.string.map_paste_idea_gone) } }
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `ideas pasted into an idea that a change from elsewhere takes away while their pictures copy in go nowhere, and say so`() = runBlocking {
        maps.load()
        val from = maps.add("# Trip to Japan\n\n- Kyoto\n\n  ![](media/temple.jpg)\n")
        File(maps.folder(from), "media").mkdirs()
        File(maps.folder(from), "media/temple.jpg").writeBytes(ByteArray(1_000) { 1 })
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n- Soon\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            withContext(Dispatchers.Main) { opened.page.say(savedWith(opened, "update", listOf("trip-to-japan" to "Trip to Japan", "tokyo" to "Tokyo", "soon" to "Soon"))) }
            until { maps.read(id) == "# Trip to Japan\n\n- Tokyo\n- Soon\n" }
            sentSince(opened)

            // Another device takes Soon away: the page is told to draw the map as it is now.
            assertEquals(WriteResult.Written, maps.change(id, "# Trip to Japan\n\n- Tokyo\n", base = "# Trip to Japan\n\n- Tokyo\n- Soon\n"))
            assertEquals("# Trip to Japan\n\n- Tokyo\n", nextSent<MapCommand.Restore>(opened).markdown)

            // Pasted into Soon just before the page has drawn it anew, without Soon, as the picture copies in.
            withContext(Dispatchers.Main) {
                opened.model.pasteIdeas("soon", Clip.Ideas("- Kyoto\n\n  ![](media/temple.jpg)\n", sourceMap = from))
                opened.page.say(MapMessage.State(savedWith(opened, "update", listOf("trip-to-japan" to "Trip to Japan", "tokyo" to "Tokyo")).state))
            }

            until { effects.any { it is MapEvent.Snackbar && it.message == ShownText.Resource(R.string.map_paste_idea_gone) } }
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
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
    fun `text shared to an idea joins the one in focus as ideas, with Undo, and a map that can't change takes none`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            withContext(Dispatchers.Main) { opened.page.say(MapMessage.Focus(listOf("trip-to-japan", "tokyo"), "focus")) }
            sentSince(opened)

            // Shared from a browser to "Attach to an Orbit idea": its page's title, and its address.
            withContext(Dispatchers.Main) { opened.model.addSharedText("Ramen in Shinjuku\nhttps://example.com/ramen") }
            val pasted = nextSent<MapCommand.Paste>(opened)
            assertEquals("tokyo", pasted.parentId)
            assertEquals("Ramen in Shinjuku\nhttps://example.com/ramen", pasted.text)
            until { effects.any { it is MapEvent.Snackbar } }
            val said = effects.filterIsInstance<MapEvent.Snackbar>().single()
            assertEquals(ShownText.Plural(R.plurals.drop_added_ideas, 1, listOf(1, "🗼 Tokyo")), said.message)
            assertTrue(said.undo)

            // Only looked at, as a map from a newer Orbit is: nothing's pasted, and that's said.
            withContext(Dispatchers.Main) { opened.page.say(newerReady) }
            withContext(Dispatchers.Main) { opened.model.addSharedText("Ramen in Shinjuku") }
            until { effects.filterIsInstance<MapEvent.Snackbar>().size == 2 }
            assertEquals(ShownText.Resource(R.string.drop_not_added_now), effects.filterIsInstance<MapEvent.Snackbar>().last().message)
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `text shared to a map before it's ready waits for it, and joins it once, as what's shared to it later does`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Tokyo\n")
        val shares = PendingShares()
        // Shared as Android brings Orbit back with it: before the map's screen takes anything, and before its page is ready.
        shares.offer(SharedFiles(id, emptyList(), text = "Ramen in Shinjuku"))
        shares.offer(SharedFiles("another-map", emptyList(), text = "Not for this map"))
        val opened = open(id, ready = false)
        val taking = launch(Dispatchers.Main) { opened.model.takeShares(shares) { error("No files are shared here") } }

        try {
            delay(200)
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
            assertEquals(2, shares.waiting.value.size)

            withContext(Dispatchers.Main) { opened.page.say(ready) }
            assertEquals("Ramen in Shinjuku", nextSent<MapCommand.Paste>(opened).text)
            assertEquals(listOf("another-map"), shares.waiting.value.map { it.mapId })

            shares.offer(SharedFiles(id, emptyList(), text = "Sushi in Ginza"))
            assertEquals("Sushi in Ginza", nextSent<MapCommand.Paste>(opened).text)

            // The same again, at once, as the one before is being taken: it joins too.
            withContext(Dispatchers.Main) {
                shares.offer(SharedFiles(id, emptyList(), text = "Sushi in Ginza"))
                shares.offer(SharedFiles(id, emptyList(), text = "Sushi in Ginza"))
            }

            assertEquals("Sushi in Ginza", nextSent<MapCommand.Paste>(opened).text)
            assertEquals("Sushi in Ginza", nextSent<MapCommand.Paste>(opened).text)
            delay(200)
            assertTrue(sentSince(opened).none { it is MapCommand.Paste })
        } finally {
            taking.cancel()
        }
    }

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
        val effects = CopyOnWriteArrayList<MapEvent>()
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
    fun `a stroke still being drawn as Done is tapped is kept, and once back to drawing the next one draws`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }
        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }
        val session = withContext(Dispatchers.Main) { opened.model.sketchSession.value!! }

        // A finger still drawing as another taps Done: what it drew is the sketch, to describe.
        withContext(Dispatchers.Main) {
            session.touch(SketchTouch.Down(0, TouchTool.Finger, InkSample(200f, 500f, null, 0f, 0)))
            session.touch(SketchTouch.Move(0, (1..10).map { step -> InkSample(200f + step * 60f, 500f, null, 0f, step * 8L) }))
            opened.model.finishSketch()
        }

        assertEquals(SketchPhase.Describing, ui(opened).sketch?.phase)
        assertEquals(1, session.strokes.size)

        // Back to drawing from the description: that finger draws nothing more, and the next stroke draws.
        withContext(Dispatchers.Main) {
            session.touch(SketchTouch.Up(0, InkSample(900f, 500f, null, 0f, 200)))
            opened.model.keepSketching()
            session.stroke(200f, 800f, at = 700f, time = 1_000)
        }

        assertEquals(2, session.strokes.size)
        withContext(Dispatchers.Main) { opened.model.saveSketch("Two lines") }
        val src = nextSent<MapCommand.Update>(opened).changes.getValue("image").jsonObject.getValue("src").jsonPrimitive.content
        assertEquals(2, SketchSvg.read(File(maps.folder(id), src).readBytes())!!.strokes.size)
    }

    @Test
    fun `closing a sketch while its first stroke is still being drawn asks first`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }
        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f))
            val session = opened.model.sketchSession.value!!
            session.touch(SketchTouch.Down(0, TouchTool.Finger, InkSample(200f, 500f, null, 0f, 0)))
            session.touch(SketchTouch.Move(0, (1..10).map { step -> InkSample(200f + step * 60f, 500f, null, 0f, step * 8L) }))
            opened.model.cancelSketch()
        }

        assertTrue(ui(opened).sketch!!.discarding)
        assertEquals(1, withContext(Dispatchers.Main) { opened.model.sketchSession.value!!.strokes.size })
    }

    @Test
    fun `a sketch the page can't show stays off its idea, back to drawing, and leaves no file behind`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }
            withContext(Dispatchers.Main) { opened.model.sketch("someday") }
            nextSent<MapCommand.Spotlight>(opened)
            withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }
            sentSince(opened)

            // The page can't load the sketch's file.
            opened.page.picturesShow = false

            withContext(Dispatchers.Main) {
                opened.model.sketchSession.value!!.stroke(200f, 800f)
                opened.model.finishSketch()
                opened.model.saveSketch("A line")
            }

            nextSent<MapCommand.PictureShown>(opened)
            until { ui(opened).sketch?.phase == SketchPhase.Drawing }
            until { effects.any { it is MapEvent.Snackbar } }
            assertEquals(ShownText.Resource(R.string.sketch_not_kept), effects.filterIsInstance<MapEvent.Snackbar>().single().message)
            assertTrue(sentSince(opened).none { it is MapCommand.Update || it is MapCommand.EndSpotlight })
            assertEquals(emptyList<String>(), File(maps.folder(id), "media").list().orEmpty().toList())

            // Once it can, Done keeps the same drawing.
            opened.page.picturesShow = true
            withContext(Dispatchers.Main) { opened.model.saveSketch("A line") }
            assertEquals("someday", nextSent<MapCommand.Update>(opened).id)
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a sketch whose picture the page doesn't answer about in time still joins its idea`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }
        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }

        // A page off screen draws no frames, so it answers late, or not at all: that's no picture it can't show.
        opened.page.picturesShow = null

        withContext(Dispatchers.Main) {
            opened.model.sketchSession.value!!.stroke(200f, 800f)
            opened.model.finishSketch()
            opened.model.saveSketch("A line")
        }

        val src = nextSent<MapCommand.Update>(opened).changes.getValue("image").jsonObject.getValue("src").jsonPrimitive.content
        assertTrue(File(maps.folder(id), src).isFile)
    }

    /** Opens a sketch on idea `someday`, which has no picture, up close, with a stroke drawn: what's on the canvas. */
    private suspend fun sketchedOn(opened: Opened): SketchSession {
        withContext(Dispatchers.Main) { opened.page.say(withPicture("someday", null)) }
        withContext(Dispatchers.Main) { opened.model.sketch("someday") }
        nextSent<MapCommand.Spotlight>(opened)
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }
        val session = withContext(Dispatchers.Main) { opened.model.sketchSession.value!! }
        withContext(Dispatchers.Main) { session.stroke(200f, 800f) }
        sentSince(opened)
        return session
    }

    @Test
    fun `a sketch under way waits, whole, while the page is stopped, and Load again puts it back on its planet to keep`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        val session = sketchedOn(opened)

        withContext(Dispatchers.Main) { opened.model.finishSketch() }
        assertEquals(SketchPhase.Describing, ui(opened).sketch?.phase)

        // Android stops the page as the sketch is described: the planet goes with it, the drawing waits.
        withContext(Dispatchers.Main) { opened.page.say(MapMessage.Stopped) }
        val waiting = ui(opened).sketch!!
        assertEquals(PageProblem.Stopped, ui(opened).pageProblem)
        assertTrue(waiting.held)
        assertEquals(SketchPhase.Opening, waiting.phase)
        assertNull(waiting.circle)
        assertSame(session, withContext(Dispatchers.Main) { opened.model.sketchSession.value })
        assertEquals(1, session.strokes.size)

        // Load again: a new page draws the map, and brings the planet up close again, in the room the toolbar leaves.
        withContext(Dispatchers.Main) {
            opened.model.sketchRoom(MapInsets(bottom = 112f))
            opened.model.reload()
        }

        val again = app.pages.last()
        assertTrue(again !== opened.page)
        val reopened = Opened(opened.model, again, opened.load, opened.store)
        nextSent<MapCommand.Load>(reopened)
        assertTrue(ui(opened).sketch!!.held)
        withContext(Dispatchers.Main) { again.say(withPicture("someday", null)) }
        assertEquals(MapCommand.Spotlight("someday", MapInsets(bottom = 112f)), nextSent<MapCommand.Spotlight>(reopened))
        withContext(Dispatchers.Main) { again.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }

        assertEquals(SketchPhase.Drawing, ui(opened).sketch?.phase)
        assertFalse(ui(opened).sketch!!.held)
        assertSame(session, withContext(Dispatchers.Main) { opened.model.sketchSession.value })

        // Done keeps it on the new page, as on the first.
        withContext(Dispatchers.Main) { opened.model.saveSketch("A line") }
        val update = nextSent<MapCommand.Update>(reopened)
        assertEquals("someday", update.id)
        assertNotNull(update.job)
        val src = update.changes.getValue("image").jsonObject.getValue("src").jsonPrimitive.content
        assertEquals(1, SketchSvg.read(File(maps.folder(id), src).readBytes())!!.strokes.size)
        assertEquals(MapCommand.EndSpotlight, nextSent<MapCommand.EndSpotlight>(reopened))
    }

    @Test
    fun `Load again after the sketched idea went ends the sketch, saying so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            sketchedOn(opened)

            withContext(Dispatchers.Main) {
                opened.page.say(MapMessage.Stopped)
                opened.model.reload()
            }

            val again = app.pages.last()
            withContext(Dispatchers.Main) { again.say(ready.copy(state = ready.state.copy(nodes = ready.state.nodes.filter { it.id != "someday" }))) }

            assertNull(ui(opened).sketch)
            assertNull(withContext(Dispatchers.Main) { opened.model.sketchSession.value })
            until { effects.any { it is MapEvent.Snackbar } }
            assertEquals(ShownText.Resource(R.string.sketch_lost), effects.filterIsInstance<MapEvent.Snackbar>().single().message)
            assertTrue(generateSequence { again.commands.tryReceive().getOrNull() }.none { it is MapCommand.Spotlight })
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a drawing brought back to a page too slow to hold it yet waits, and comes up close once the page can`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        val session = sketchedOn(opened)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Stopped)
            opened.model.reload()
        }

        val again = app.pages.last()
        val reopened = Opened(opened.model, again, opened.load, opened.store)
        withContext(Dispatchers.Main) { again.say(withPicture("someday", null)) }
        nextSent<MapCommand.Spotlight>(reopened)

        // Not laid out yet, the page can't bring the planet up close, and says so at once: the idea is there, so the drawing waits.
        withContext(Dispatchers.Main) { again.say(MapMessage.Spotlight("someday", "end")) }
        assertSame(session, withContext(Dispatchers.Main) { opened.model.sketchSession.value })
        assertEquals(SketchPhase.Opening, ui(opened).sketch?.phase)

        // Back meanwhile asks before its strokes go, however long bringing it back takes; kept, it comes back.
        withContext(Dispatchers.Main) { opened.model.cancelSketch() }
        assertTrue(ui(opened).sketch!!.discarding)
        withContext(Dispatchers.Main) { opened.model.keepSketching() }
        assertSame(session, withContext(Dispatchers.Main) { opened.model.sketchSession.value })

        // A moment later it's brought up again, and this time the page holds it.
        assertEquals(MapCommand.Spotlight("someday", MapInsets()), nextSent<MapCommand.Spotlight>(reopened))
        withContext(Dispatchers.Main) { again.say(MapMessage.Spotlight("someday", "rest", 206f, 300f, 177f)) }
        assertEquals(SketchPhase.Drawing, ui(opened).sketch?.phase)
        assertEquals(1, session.strokes.size)
    }

    @Test
    fun `closing a sketch that waits for the map asks first, as on its planet`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        sketchedOn(opened)

        withContext(Dispatchers.Main) {
            opened.page.say(MapMessage.Stopped)
            opened.model.cancelSketch()
        }

        assertTrue(ui(opened).sketch!!.discarding)
        withContext(Dispatchers.Main) { opened.model.keepSketching() }
        assertTrue(ui(opened).sketch!!.held)
        assertFalse(ui(opened).sketch!!.discarding)

        // Let go, it ends at once: there's no planet up close to fly back from.
        withContext(Dispatchers.Main) { opened.model.cancelSketch(letGo = true) }
        assertNull(ui(opened).sketch)
        assertNull(withContext(Dispatchers.Main) { opened.model.sketchSession.value })
    }

    @Test
    fun `Done on a page that stopped keeps the canvas, its drawing whole, and leaves no file behind`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            val session = sketchedOn(opened)

            // The page's process is gone, before Android has said so.
            opened.page.stopped = true
            withContext(Dispatchers.Main) { opened.model.saveSketch("A line") }

            until { ui(opened).sketch?.phase == SketchPhase.Drawing }
            until { effects.any { it is MapEvent.Snackbar } }
            assertEquals(ShownText.Resource(R.string.sketch_not_kept), effects.filterIsInstance<MapEvent.Snackbar>().single().message)
            assertSame(session, withContext(Dispatchers.Main) { opened.model.sketchSession.value })
            assertTrue(sentSince(opened).none { it is MapCommand.Update || it is MapCommand.EndSpotlight })
            assertEquals(emptyList<String>(), File(maps.folder(id), "media").list().orEmpty().toList())
        } finally {
            hearing.cancel()
        }
    }

    @Test
    fun `a sketch the page doesn't say it took stays on the canvas, to keep again`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip to Japan\n\n- Someday\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)

        try {
            val session = sketchedOn(opened)

            // The page loads the picture, then doesn't answer whether the idea took it.
            opened.page.changesTaken = null
            withContext(Dispatchers.Main) { opened.model.saveSketch("A line") }

            assertEquals("someday", nextSent<MapCommand.Update>(opened).id)
            until { ui(opened).sketch?.phase == SketchPhase.Drawing }
            until { effects.any { it is MapEvent.Snackbar } }
            assertEquals(ShownText.Resource(R.string.sketch_not_kept), effects.filterIsInstance<MapEvent.Snackbar>().single().message)
            assertSame(session, withContext(Dispatchers.Main) { opened.model.sketchSession.value })
            assertTrue(sentSince(opened).none { it is MapCommand.EndSpotlight })

            // The idea's gone from the page meanwhile: its file goes, nothing points at it.
            opened.page.changesTaken = false
            withContext(Dispatchers.Main) { opened.model.saveSketch("A line") }
            val src = nextSent<MapCommand.Update>(opened).changes.getValue("image").jsonObject.getValue("src").jsonPrimitive.content
            until { ui(opened).sketch?.phase == SketchPhase.Drawing && !File(maps.folder(id), src).exists() }
            assertTrue(sentSince(opened).none { it is MapCommand.EndSpotlight })

            // Once the page takes it, the canvas lifts.
            opened.page.changesTaken = true
            withContext(Dispatchers.Main) { opened.model.saveSketch("A line") }
            assertEquals(MapCommand.EndSpotlight, nextSent<MapCommand.EndSpotlight>(opened))
        } finally {
            hearing.cancel()
        }
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

    @Test
    fun `a map deleted while open that can't come back from the trash stays there, and its screen says so`() = runBlocking {
        maps.load()
        val id = maps.add("# Trip\n- Beach\n")
        val opened = open(id)
        val effects = CopyOnWriteArrayList<MapEvent>()
        val hearing = hear(opened, effects)
        val trashed = maps.delete(id)!!
        // Its details can't be read now: it waits in the trash rather than have them written over.
        val details = File(trashed.folder, "meta.json")
        assertTrue(details.isFile)
        details.setReadable(false)
        assumeFalse(details.canRead())

        try {
            withContext(Dispatchers.Main) { opened.model.restoreFromTrash() }
            until { withContext(Dispatchers.Main) { MapEvent.Snackbar(ShownText.Resource(R.string.settings_could_not_restore, listOf("Trip"))) in effects } }
            assertFalse(maps.exists(id))
            assertTrue(maps.trash.value.any { it.folder == trashed.folder })
        } finally {
            details.setReadable(true)
            hearing.cancel()
        }

        withContext(Dispatchers.Main) { opened.model.restoreFromTrash() }
        until { maps.exists(id) }
    }
}

/**
 * A storage that's slow when the test arms it: the next write that keeps a
 * version stops inside the repository, as its history is thinned (where it
 * asks whether the clock can be trusted), until the test lets it go.
 */
/**
 * Holds the next copy into a map as it asks for room for its files, before
 * the first is copied, until the test lets it through ([Held.release]): a
 * copy that takes a while, for what happens meanwhile. Any other copy has
 * all the room it wants.
 */
private class CopyGate {
    class Held {
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        /** Waits for the copy to get here. */
        fun awaitReached() = assertTrue("No copy began", reached.await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS))

        /** Lets the copy go on. */
        fun release() = released.countDown()

        fun pass() {
            reached.countDown()
            released.await(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)
        }
    }

    private val next = AtomicReference<Held?>(null)

    /** Holds the next copy. */
    fun hold(): Held = Held().also { next.set(it) }

    fun room(): Long {
        next.getAndSet(null)?.pass()
        return Long.MAX_VALUE
    }
}

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

    /** The ideas the map asks the page to write out for the clipboard, each waiting for the test to answer with it. */
    val copying = Channel<CompletableDeferred<CopiedIdea>>(Channel.UNLIMITED)

    /** Whether the pictures the map asks about load and show, as the real page says ([MapCommand.PictureShown]); null answers nothing in time. */
    @Volatile
    var picturesShow: Boolean? = true

    /** Whether the page fails to paste, as one that doesn't answer in time. */
    @Volatile
    var pastesFail = false

    /** What the page does with ideas it pastes before it answers, as the real page saves the map first. */
    @Volatile
    var pasting: (suspend (MapCommand.Paste) -> Unit)? = null

    /** Why the page fails a paste before it has it, as one whose process stopped does; null: it has it. */
    @Volatile
    var pasteUnsent: PageException.Kind? = null

    /**
     * Whether the ideas the map changes and waits to hear about take the
     * change, as the real page says once it has posted the change's save
     * ([MapCommand.Update] with a job): false for an idea the map no longer
     * has; null answers nothing in time.
     */
    @Volatile
    var changesTaken: Boolean? = true

    /** Whether the page's process stopped: it answers no job, as the real one, whose jobs end as it stops. */
    @Volatile
    var stopped = false

    private var jobs = 0

    /**
     * What the page says as the app closes the field open on the map, as the
     * real one says the save of what was typed in it (`save`) and that the
     * field closed: none when no field is open.
     */
    var stopping: ((save: Boolean) -> Unit)? = null

    /** While true, the page is too busy to answer a check: it never does, and says so in [checksGivenUp] once the app stops waiting. */
    @Volatile
    var checksHang = false

    val checksGivenUp = Channel<Unit>(Channel.UNLIMITED)

    override fun send(command: MapCommand) {
        commands.trySend(command)

        if (command is MapCommand.StopEditing) {
            stopping?.invoke(command.save)
        }
    }

    override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
        if (givenBack) {
            throw PageException("The page went back")
        }

        if (stopped) {
            throw PageException("The page stopped", PageException.Kind.Stopped)
        }

        val sent = command(++jobs)

        if (sent is MapCommand.Paste) {
            pasteUnsent?.let { kind -> throw PageException("The page stopped", kind) }
        }

        if (sent is MapCommand.Convert && holding) {
            CompletableDeferred<Unit>().also { held.trySend(it) }.await()
        }

        if (sent is MapCommand.Check && checksHang) {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                checksGivenUp.trySend(Unit)
            }
        }

        val value = when (sent) {
            is MapCommand.Check -> protocolJson.encodeToJsonElement(CheckedMap.serializer(), CheckedMap("", 0, emptyList(), sent.text))
            is MapCommand.Convert -> JsonPrimitive(if (sent.to == "yaml") YAML + sent.text.removePrefix(YAML) else sent.text.removePrefix(YAML))
            is MapCommand.AddNodes -> {
                val count = CompletableDeferred<Int>().also { adding.trySend(sent to it) }.await()
                protocolJson.encodeToJsonElement(PastedIdeas.serializer(), PastedIdeas(count))
            }
            is MapCommand.Copy -> protocolJson.encodeToJsonElement(CopiedIdea.serializer(), CompletableDeferred<CopiedIdea>().also { copying.trySend(it) }.await())
            is MapCommand.Paste -> {
                commands.trySend(sent)

                if (pastesFail) {
                    throw PageException("The page didn't answer")
                }

                pasting?.invoke(sent)

                protocolJson.encodeToJsonElement(PastedIdeas.serializer(), PastedIdeas(1))
            }
            // A sketch's picture shows on the planet, as the real page says once it has loaded it, unless the test says it can't.
            is MapCommand.PictureShown -> {
                commands.trySend(sent)
                JsonPrimitive(picturesShow ?: throw PageException("The page didn't answer"))
            }
            is MapCommand.Update -> {
                commands.trySend(sent)
                JsonPrimitive(changesTaken ?: throw PageException("The page didn't answer", PageException.Kind.DidNotAnswer))
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

/** A clipboard that keeps the ideas copied onto it, by their markdown, and fails a test that reads it: nothing is pasted in these tests. */
private class TestClipboard : IdeasClipboard {
    val copied: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override val resolver: android.content.ContentResolver get() = error("Nothing is pasted in these tests")

    override fun pasteable(): Pasteable? = error("Nothing is pasted in these tests")

    override fun read(): Clip? = error("Nothing is pasted in these tests")

    override fun copy(label: String, text: String, markdown: String, mapId: String, sensitive: Boolean): Boolean {
        copied += markdown
        return true
    }
}

/** A second screen that keeps what it's told, and goes wrong when a test says, as one whose page stopped. */
private class TestScreen : Audience {
    override val name = "HDMI screen"

    /** Everything it was told, in order. */
    val heard = mutableListOf<AudienceCommand>()

    override val trouble = MutableStateFlow<ScreenTrouble?>(null)

    /** How many times it was loaded again. */
    var loadedAgain = 0

    override fun send(command: AudienceCommand) {
        heard += command
    }

    override fun loadAgain() {
        loadedAgain += 1
        trouble.value = null
    }

    override fun close() = Unit
}

/** The maps an audience was shown, in order. */
private fun Audience.shown(): List<String> = (this as TestScreen).heard.filterIsInstance<AudienceCommand.Show>().map { it.markdown }

/** The rest of the app, as far as a test's map reaches for it: pages, words, the private maps' lock and study mode's answers on their way to `maps`. */
private class TestApp(override val words: Words, maps: MapRepository) : MapApp {
    /** The pages the view model took, in order. */
    val pages = mutableListOf<TestPage>()

    override val privateLock = PrivateLock(clock = { 0L })

    /** The test's own: what's started in it ends with the test (see cleanUp). */
    override val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val studyWrites = StudyWrites(applicationScope, maps::changeStudy) { if (maps.writable.value) AnswersUnsaved.Storage else AnswersUnsaved.Newer }

    override val deletedMaps = Channel<TrashedMap>(Channel.CONFLATED)

    override val ideaToShow = MutableStateFlow<IdeaToShow?>(null)

    /** The test's clock for a presentation's times: Android's isn't there on the JVM. */
    override fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

    override fun takePage(): MapPage = TestPage().also { pages += it }

    override fun givePage(page: MapPage) {
        (page as TestPage).givenBack = true
    }

    override fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?) = SoundPlayback(scope, resolve) { error("No sound plays in these tests") }

    override fun timeWords(): TimeWords = TimeWords(Locale.US, is24Hour = false)

    override val clipboard = TestClipboard()
    override val pictures: MapPictures get() = error("No picture is drawn in these tests")
    override val importer: Importer get() = error("No file is let go on the map in these tests")
    override val folderSync: FolderSync get() = error("No folder syncs in these tests")
}
