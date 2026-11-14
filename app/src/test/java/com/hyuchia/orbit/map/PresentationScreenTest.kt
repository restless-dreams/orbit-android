package com.hyuchia.orbit.map

import android.content.ContentResolver
import android.os.Looper
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.protocolJson
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.reminders.IdeaToShow
import com.hyuchia.orbit.study.StudyWrites
import com.hyuchia.orbit.ui.TimeWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * A presentation on this screen ([PresentationMode]) with a keyboard, as
 * the map's screen shows it: Tab goes through its controls and never into
 * the map, a control Tab is on takes Enter and Space, the controls stay
 * while one has the focus, and once they've stepped aside, Tab or
 * Shift+Tab brings them back with the focus on the first or the last.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w900dp-h900dp-mdpi")
class PresentationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val root: File = Files.createTempDirectory("orbit-presentation-keys").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))
    private val app = PresentingApp(words)
    private val store = ViewModelStore()
    private lateinit var model: MapViewModel

    /** Whether the map was asked to take the keyboard's focus, each time it was drawn. */
    private val mapFocusable = mutableListOf<Boolean>()

    /** The page's ready message as the pages' build wrote it: a trip to Japan, and Tokyo in it. */
    private val ready: MapMessage.Ready by lazy {
        val example = protocolJson.parseToJsonElement(
            requireNotNull(javaClass.classLoader?.getResource("page-protocol.json")) { "No page-protocol.json on the test classpath: build web/" }.readText(),
        ).jsonObject.getValue("map").jsonObject.getValue("messages").jsonArray.first { it.jsonObject.getValue("type").jsonPrimitive.content == "ready" }
        val message = protocolJson.decodeFromJsonElement(MapMessage.serializer(), example) as MapMessage.Ready
        message.copy(state = message.state.copy(readOnly = null, problem = null))
    }

    @After
    fun cleanUp() {
        // The map closes as its screen does; what it hands the app's scope as it goes needs this thread, so it's cancelled, not awaited.
        compose.runOnIdle { store.clear() }
        app.applicationScope.cancel()
        root.deleteRecursively()
    }

    /** The map opened as its screen opens it, presented from its top, and shown as the map's screen shows a presentation. */
    private fun present() {
        val id = runBlocking {
            maps.load()
            maps.add("# Trip to Japan\n\n- Tokyo\n")
        }

        compose.runOnIdle {
            model = ViewModelProvider.create(store, viewModelFactory { initializer { MapViewModel(id, maps, settings, app) } })[MapViewModel::class]
            model.setInsets(MapInsets())
            model.configure(AppTheme(dark = false, tokens = emptyMap()), MapSettings(haptics = true, snap = true, longPressDelay = 400))
        }

        until { app.pages.lastOrNull()?.loaded == true }
        compose.runOnIdle { app.pages.last().listener?.invoke(ready) }
        until { model.ui.value.map != null }
        compose.runOnIdle { model.startPresentation(0, fromStart = true) }

        compose.setContent {
            val ui by model.ui.collectAsState()

            PresentationMode(model, ui, Color.White) { focusable ->
                mapFocusable += focusable
                // The map's page, which takes the keyboard's focus unless it's asked not to.
                Box(Modifier.fillMaxSize().testTag("map").focusProperties { canFocus = focusable }.focusable())
            }
        }

        compose.waitForIdle()
    }

    /**
     * Waits until `done`, or fails after ten seconds: the map's view model
     * works on Android's main thread, which runs here as the test lets it.
     */
    private fun until(done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000

        while (!done()) {
            check(System.currentTimeMillis() < end) { "Still waiting after ten seconds" }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(10)
        }
    }

    private val presentation: Presentation get() = model.ui.value.presentation!!

    /** A key on the keyboard, to whatever has the focus: something has to. */
    private fun press(key: Key, shift: Boolean = false) {
        compose.onNode(isFocused()).performKeyInput {
            if (shift) {
                withKeyDown(Key.ShiftLeft) { pressKey(key) }
            } else {
                pressKey(key)
            }
        }

        compose.waitForIdle()
    }

    /** What the focus is on, never the map: a control's name, or null for the presentation itself. */
    private fun focused(): String? {
        val node = compose.onNode(isFocused()).fetchSemanticsNode()
        assertFalse("The map has the focus", node.config.getOrNull(SemanticsProperties.TestTag) == "map")
        return node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
    }

    private fun shown(name: String): Boolean = compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty()

    /** Lets the controls' moment pass, and more. */
    private fun later() {
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
    }

    @Test
    fun `Tab goes through the controls and never into the map, and Enter presses the one it's on`() {
        present()
        assertTrue(mapFocusable.isNotEmpty() && mapFocusable.none { it })

        press(Key.Tab)
        compose.onNodeWithContentDescription("Hide the captions").assertIsFocused()
        press(Key.Enter)
        assertFalse(presentation.captions)
        assertEquals(0, presentation.index)

        // Round all the controls and back: never the map.
        repeat(12) {
            press(Key.Tab)
            focused()
        }
    }

    @Test
    fun `with the presentation itself focused, Space and Enter step`() {
        present()

        press(Key.Spacebar)
        assertEquals(1, presentation.index)
        press(Key.PageUp)
        assertEquals(0, presentation.index)
        press(Key.Enter)
        assertEquals(1, presentation.index)
        assertTrue(presentation.captions)
    }

    @Test
    fun `the controls stay while one of them has the focus`() {
        present()
        press(Key.Tab)

        later()
        compose.onNodeWithContentDescription("Hide the captions").assertIsFocused()
        assertTrue(shown("Stop presenting"))
    }

    @Test
    fun `once the controls stepped aside, Tab brings them back on the first, and Shift+Tab on the last`() {
        present()
        later()
        assertFalse(shown("Stop presenting"))

        press(Key.Tab)
        assertTrue(shown("Stop presenting"))
        compose.onNodeWithContentDescription("Hide the captions").assertIsFocused()
        assertEquals(0, presentation.index)

        // Back on the presentation, they step aside again; Shift+Tab brings them back on the last, Next idea.
        repeat(12) {
            if (focused() != null) {
                press(Key.Tab)
            }
        }

        later()
        assertFalse(shown("Stop presenting"))
        press(Key.Tab, shift = true)
        compose.onNodeWithContentDescription("Next idea").assertIsFocused()
        press(Key.Enter)
        assertEquals(1, presentation.index)
    }
}

/** A page that keeps what it's sent and answers no jobs: a presentation asks it for none. */
private class PresentingPage : MapPage {
    override var listener: ((MapMessage) -> Unit)? = null
    override var incognito = false

    /** Whether the map was sent to it. */
    @Volatile
    var loaded = false

    override fun send(command: MapCommand) {
        if (command is MapCommand.Load) {
            loaded = true
        }
    }

    override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T = throw PageException("This test's page answers no jobs")

    override suspend fun drawn() = Unit
}

/** The rest of the app, as far as a presented map reaches for it. */
private class PresentingApp(override val words: Words) : MapApp {
    val pages = mutableListOf<PresentingPage>()

    override val privateLock = PrivateLock(clock = { 0L })
    override val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val studyWrites = StudyWrites(applicationScope, { _, _ -> error("Nothing is studied in these tests") })
    override val deletedMaps = Channel<TrashedMap>(Channel.CONFLATED)
    override val ideaToShow = MutableStateFlow<IdeaToShow?>(null)

    override fun takePage(): MapPage = PresentingPage().also { pages += it }

    override fun givePage(page: MapPage) = Unit

    override fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?) = SoundPlayback(scope, resolve) { error("No sound plays in these tests") }

    override fun timeWords(): TimeWords = TimeWords(Locale.US, is24Hour = false)

    override val clipboard = object : IdeasClipboard {
        override val resolver: ContentResolver get() = error("Nothing is pasted in these tests")

        override fun pasteable(): Pasteable? = null

        override fun read(): Clip? = null

        override fun copy(label: String, text: String, markdown: String, mapId: String, sensitive: Boolean) = false
    }

    override val pictures: MapPictures get() = error("No picture is drawn in these tests")
    override val importer: Importer get() = error("No file is let go on the map in these tests")
    override val folderSync: FolderSync get() = error("No folder syncs in these tests")
}
