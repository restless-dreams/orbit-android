package com.hyuchia.orbit.map

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
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
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapFile
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.reminders.IdeaToShow
import com.hyuchia.orbit.study.StudyWrites
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.ui.TimeWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.Locale

/** How long the rows have to say what's here now, before the test fails. */
private const val WAIT_MILLISECONDS = 10_000L

/**
 * Whether an idea's files are on this device, as its rows say: looked at
 * again as a file the map's text named comes, from the folder maps are kept
 * in or a zip, and as one is tapped, never only when the rows are opened.
 */
@RunWith(AndroidJUnit4::class)
class IdeaFilesHereTest {
    @get:Rule
    val compose = createComposeRule()

    private val root: File = Files.createTempDirectory("orbit-idea-files").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
    private val settings = SettingsRepository(settingsStoreIn(File(root, "settings.preferences_pb")))
    private val app = QuietApp(words)
    private var model: MapViewModel? = null

    /** A map whose idea "Plans" holds a PDF that hasn't come yet, as one another device sent before its files. */
    private val plans = """
        # Trip
        ## Plans
        <!-- orbit
        files:
          - src: media/file-plan.pdf
            name: "Plan.pdf"
            type: "application/pdf"
            size: 4
        -->
    """.trimIndent() + "\n"

    private val node = MapNode("plans", "Plans", files = listOf(MapFile("media/file-plan.pdf", name = "Plan.pdf", type = "application/pdf", size = 4)))

    @After
    fun cleanUp() {
        model?.viewModelScope?.cancel()
        app.applicationScope.cancel()
        root.deleteRecursively()
    }

    private fun notHere(): Boolean = compose.onAllNodesWithText("Not on this device", substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a file that comes says it's here, and one that went says it isn't once it's tapped`() {
        val id = runBlocking {
            maps.load()
            maps.add(plans)
        }

        val opened = MapViewModel(id, maps, settings, app).also { model = it }

        compose.setContent {
            OrbitTheme(dark = false) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                DetailsFiles(node, remember { FileOpening(context, scope, opened) })
            }
        }

        compose.waitUntil(WAIT_MILLISECONDS) { notHere() }

        // The folder maps are kept in brings it, while the details are open.
        runBlocking { maps.addMediaFile(id, "file-plan.pdf", files = listOf("file-plan.pdf")) { output -> output.write("%PDF".toByteArray()); true } }
        compose.waitUntil(WAIT_MILLISECONDS) { !notHere() }

        // Gone from the device meanwhile, as another app cleared it: the tap that finds it gone says so in its row too.
        assertTrue(File(maps.folder(id), "media/file-plan.pdf").delete())
        compose.onNodeWithText("Plan.pdf", substring = true).performClick()
        compose.waitUntil(WAIT_MILLISECONDS) { notHere() }
    }
}

/** A page that draws nothing and answers nothing: the rows under test need none. */
private class QuietPage : MapPage {
    override var listener: ((MapMessage) -> Unit)? = null
    override var incognito = false

    override fun send(command: MapCommand) = Unit

    override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T = awaitCancellation()

    override suspend fun drawn() = Unit
}

/** The rest of the app, as far as an idea's files reach for it. */
private class QuietApp(override val words: Words) : MapApp {
    override val privateLock = PrivateLock(clock = { 0L })
    override val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val studyWrites = StudyWrites(applicationScope, { _, _ -> error("Nothing is studied in these tests") })
    override val deletedMaps = Channel<TrashedMap>(Channel.CONFLATED)
    override val ideaToShow = MutableStateFlow<IdeaToShow?>(null)

    override fun takePage(): MapPage = QuietPage()

    override fun givePage(page: MapPage) = Unit

    override fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?) = SoundPlayback(scope, resolve) { error("No sound plays in these tests") }

    override fun timeWords(): TimeWords = TimeWords(Locale.US, is24Hour = false)

    override fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

    override val clipboard: IdeasClipboard get() = error("Nothing is copied in these tests")
    override val pictures: MapPictures get() = error("No picture is drawn in these tests")
    override val importer: Importer get() = error("No file is let go on the map in these tests")
    override val folderSync: FolderSync get() = error("No folder syncs in these tests")
}
