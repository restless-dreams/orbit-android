package com.hyuchia.orbit.map

import android.content.ClipData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * A drop takes a while to land: the page says where it goes, its files are
 * read and copied. A map that meanwhile begins to be presented or made into
 * a path takes nothing of it, and says so, however late it comes.
 */
@RunWith(AndroidJUnit4::class)
class DropsInFlightTest {
    private val root: File = Files.createTempDirectory("orbit-drops").toFile()
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** A page that says where a drop goes once the test lets it ([answer]), and keeps what it's asked to change. */
    private class SlowPage : MapPage {
        override var listener: ((MapMessage) -> Unit)? = null
        override var incognito = false
        val answer = CompletableDeferred<Unit>()
        val asked = mutableListOf<MapCommand>()

        override fun send(command: MapCommand) {
            asked += command
        }

        override suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
            val sent = command(1)
            asked += sent

            val value = when (sent) {
                is MapCommand.DropAt -> {
                    this.answer.await()
                    protocolJson.encodeToJsonElement(DropPlace.serializer(), DropPlace("trip"))
                }
                is MapCommand.Paste -> protocolJson.encodeToJsonElement(PastedIdeas.serializer(), PastedIdeas(1))
                else -> error("This page answers no ${sent::class.simpleName}")
            }

            return protocolJson.decodeFromJsonElement(answer, value)
        }

        override suspend fun drawn() = Unit
    }

    @Test
    fun `text let go just as a path begins to be made is not added, and the screen says so`() = runBlocking {
        val maps = MapRepository(File(root, "maps"), File(root, "trash"), starters, words)
        val ui = MutableStateFlow(MapUi(ready = true, map = MapState("Trip", listOf(MapNode("trip", "Trip")))))
        val page = SlowPage()
        val told = mutableListOf<MapEvent>()
        val updated = mutableListOf<String>()

        val drops = MapDrops(
            scope = CoroutineScope(Dispatchers.Unconfined),
            mapId = "trip",
            maps = maps,
            importer = { error("No map is let go here") },
            words = words,
            ui = ui,
            locked = MutableStateFlow(false),
            page = { page },
            change = { id, _, _ ->
                updated += id
                true
            },
            tell = { told += it },
        )

        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val landing = async(Dispatchers.Default) { drops.land(ClipData.newPlainText("ideas", "Kyoto\nOsaka"), resolver, own = null, x = 120f, y = 300f) }

        // The page is still saying where the drop goes as "Presentation path" starts.
        ui.value = ui.value.copy(pathMaking = true)
        page.answer.complete(Unit)
        withTimeout(10_000) { landing.await() }

        assertEquals(emptyList<MapCommand>(), page.asked.filter { it is MapCommand.Paste || it is MapCommand.AddNodes })
        assertEquals(emptyList<String>(), updated)
        assertEquals(listOf(MapEvent.Snackbar(ShownText.Resource(R.string.drop_not_added_now))), told)
    }
}
