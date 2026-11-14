package com.hyuchia.orbit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.nearby.ArrivalAsk
import com.hyuchia.orbit.nearby.arrivalAsk
import com.hyuchia.orbit.page.PagePool
import com.hyuchia.orbit.privacy.PrivateLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Files of maps that are here already, asked about one after another, as
 * Android stops Orbit while they're asked (to ask for the screen lock, say)
 * and brings its screen back: the questions come back as they were, from
 * the screen's saved state and what waits on disk, which holds nothing of
 * the maps; a new screen lets them go.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ArrivalsRestoreTest {
    private val files: File = Files.createTempDirectory("orbit-arrivals").toFile()

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** The main thread, as Android has one: the screen's work runs on it, one piece at a time. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    /** The app's scope, ended with the test. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun cleanUp() {
        scope.cancel()
        Dispatchers.resetMain()
        main.close()
        files.deleteRecursively()
    }

    /** The intake of a run of Orbit over `maps`, in an app scope the test ends. */
    private fun intake(maps: MapRepository) = Intake(
        maps,
        Importer(maps, PagePool(ApplicationProvider.getApplicationContext(), File(files, "maps")), FileWords(Locale.US)),
        ApplicationProvider.getApplicationContext<Context>().contentResolver,
        scope,
        FileWords(Locale.US),
        ImportRequests(File(files, "requests")),
    )

    /** The maps, as each run of Orbit reads them. */
    private suspend fun loaded() = MapRepository(File(files, "maps"), File(files, "trash"), starters, FileWords(Locale.US)).also { it.load() }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** Another version of `existing`, its zip opened with Orbit, as the screen asks about it: `opens` it once it's in. */
    private suspend fun question(maps: MapRepository, existing: String, title: String, opens: Boolean): OtherVersion {
        val text = "# $title\n\n- Alfama ![](media/picture-1.jpg)\n"
        val bundle = maps.readBundle(ByteArrayInputStream(zip("map.md" to text.toByteArray(), "media/picture-1.jpg" to ByteArray(500) { 7 })))!!
        return OtherVersion(ImportResult.AlreadyHere(bundle, existing, "$title.zip"), opens)
    }

    /** Waits until `done`, or fails after ten seconds: what waits is written off the main thread. */
    private suspend fun until(done: suspend () -> Boolean) {
        withTimeout(10_000) {
            while (!done()) {
                delay(20)
            }
        }
    }

    /** The screen's saved state as Android keeps it while Orbit is stopped, and gives it back to the screen made again. */
    private fun keptByAndroid(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })

    @Test
    fun `questions about maps here already come back as they were after Android stopped Orbit, and go once answered`() = runBlocking {
        val maps = loaded()
        val lisbon = maps.add("# Lisbon\n")
        val porto = maps.add("# Porto\n")
        val first = question(maps, lisbon, "Lisbon", opens = true)
        val second = question(maps, porto, "Porto", opens = false)
        val saved = SavedStateHandle()
        val screen = withContext(Dispatchers.Main) { Arrivals(maps.waitingArrivals, intake(maps), saved, mapsRead = {}) }
        withContext(Dispatchers.Main) { screen.otherVersions += listOf(first, second) }
        until { File(second.arrival.bundle.unpacked, ".arrival.json").isFile }

        // What Android keeps of the screen says nothing of the maps: which files wait, in order, and no more.
        val kept = saved.keys().joinToString { "$it=${saved.get<Any>(it).let { value -> (value as? BooleanArray)?.toList() ?: value }}" }
        assertFalse(kept, listOf("Lisbon", "Porto", lisbon, porto, "Alfama").any { it in kept })

        // A new run of Orbit, its screen brought back with what Android kept.
        val again = withContext(Dispatchers.Main) { loaded().let { Arrivals(it.waitingArrivals, intake(it), keptByAndroid(saved), mapsRead = {}) } }
        until { withContext(Dispatchers.Main) { again.otherVersions.count == 2 } }
        val asked = withContext(Dispatchers.Main) { again.otherVersions.asked!! }
        assertEquals(lisbon, asked.arrival.existing)
        assertEquals("Lisbon.zip", asked.arrival.name)
        assertEquals(first.arrival.bundle.markdown, asked.arrival.bundle.markdown)
        assertEquals(1, asked.arrival.bundle.media.pictures)
        assertTrue(asked.opens)
        assertEquals(1 to 2, withContext(Dispatchers.Main) { again.otherVersions.number to again.otherVersions.count })

        // Answered Neither: it goes, and the next one is asked, which doesn't open its map.
        withContext(Dispatchers.Main) { again.decline(asked) }

        until { !first.arrival.bundle.unpacked!!.exists() }
        val next = withContext(Dispatchers.Main) { again.otherVersions.asked!! }
        assertEquals(porto, next.arrival.existing)
        assertFalse(next.opens)
        assertEquals(2 to 2, withContext(Dispatchers.Main) { again.otherVersions.number to again.otherVersions.count })
        assertTrue(second.arrival.bundle.unpacked!!.isDirectory)
    }

    @Test
    fun `a private map's question comes back only once the maps are read, and says nothing of the map while it's locked`() = runBlocking {
        val maps = loaded()
        val diary = maps.add("# Diary\n")
        maps.setPrivate(diary, true)
        val waiting = question(maps, diary, "Diary", opens = true)
        val saved = SavedStateHandle()
        val screen = withContext(Dispatchers.Main) { Arrivals(maps.waitingArrivals, intake(maps), saved, mapsRead = {}) }
        withContext(Dispatchers.Main) { screen.otherVersions += listOf(waiting) }
        until { File(waiting.arrival.bundle.unpacked, ".arrival.json").isFile }

        // A new run of Orbit, its screen brought back before it has read the maps: private maps are locked, as it starts.
        val unread = MapRepository(File(files, "maps"), File(files, "trash"), starters, FileWords(Locale.US))
        val lock = PrivateLock(clock = { 0L }).view.value
        val asking = CompletableDeferred<Unit>()
        val read = CompletableDeferred<Unit>()

        val again = withContext(Dispatchers.Main) {
            Arrivals(unread.waitingArrivals, intake(unread), keptByAndroid(saved)) {
                asking.complete(Unit)
                read.await()
            }
        }

        // It waits for the maps: until then nothing says the map is private, and the question would name it.
        withTimeout(10_000) { asking.await() }
        assertNull(withContext(Dispatchers.Main) { again.otherVersions.asked })
        // Asked about meanwhile all the same, a map Orbit has no details of keeps which it is to itself, Recents too.
        assertTrue(arrivalHides(diary, lock.locked, unread::summary, lock::hides))
        assertTrue(arrivalNamesPrivate(diary, unread::summary))

        unread.load()
        read.complete(Unit)
        until { withContext(Dispatchers.Main) { again.otherVersions.asked != null } }
        val asked = withContext(Dispatchers.Main) { again.otherVersions.asked!! }
        assertEquals(diary, asked.arrival.existing)
        val hidden = arrivalHides(asked.arrival.existing, lock.locked, unread::summary, lock::hides)
        assertEquals(ArrivalAsk.UpdateLocked, arrivalAsk(alreadyHere = true, hidden = hidden, concealed = false))
        assertEquals(ArrivalAsk.NewUntilUnlocked, arrivalAsk(alreadyHere = true, hidden = hidden, concealed = true))

        // Unlocked, it's asked about by its name, and Recents gets no picture of it.
        val open = lock.copy(locked = false)
        assertFalse(arrivalHides(diary, open.locked, unread::summary, open::hides))
        assertTrue(arrivalNamesPrivate(diary, unread::summary))
        val elsewhere = unread.add("# Lisbon\n")
        assertFalse(arrivalHides(elsewhere, lock.locked, unread::summary, lock::hides))
        assertFalse(arrivalNamesPrivate(elsewhere, unread::summary))
    }

    @Test
    fun `a new screen lets go of every question no screen will ask again`() = runBlocking {
        val maps = loaded()
        val lisbon = maps.add("# Lisbon\n")
        val waiting = question(maps, lisbon, "Lisbon", opens = true)
        val screen = withContext(Dispatchers.Main) { Arrivals(maps.waitingArrivals, intake(maps), SavedStateHandle(), mapsRead = {}) }
        withContext(Dispatchers.Main) { screen.otherVersions += listOf(waiting) }
        until { File(waiting.arrival.bundle.unpacked, ".arrival.json").isFile }

        // Orbit started anew, as after its task was swiped away: nothing asks about it again, and its files go.
        val fresh = withContext(Dispatchers.Main) { loaded().let { Arrivals(it.waitingArrivals, intake(it), SavedStateHandle(), mapsRead = {}) } }
        until { !waiting.arrival.bundle.unpacked!!.exists() }
        assertNull(withContext(Dispatchers.Main) { fresh.otherVersions.asked })
    }
}
