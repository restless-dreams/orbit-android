package com.hyuchia.orbit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.data.textHash
import com.hyuchia.orbit.page.PagePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Another version of a map here, answered Update or Keep both, is taken in
 * exactly once, or its question comes back, whatever stops Orbit along the
 * way: the screen made again, Android stopping Orbit before the answer is
 * kept, after it, or at any step of taking it in, or taking it in failing.
 * A run of Orbit is a repository and an intake of its own over the same
 * files; one stopped at a step is the files as that step leaves them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AcceptedArrivalsTest {
    private val files: File = Files.createTempDirectory("orbit-accepted").toFile()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = FileWords(Locale.US)
    private var now = 1_000_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** The main thread, as Android has one: the screen's work runs on it, one piece at a time. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /** The app's scope of each run of Orbit, stopped with it. */
    private val scopes = mutableListOf<CoroutineScope>()

    private val picture = ByteArray(1_500) { (it % 251).toByte() }
    private val sketch = ByteArray(900) { (it % 13).toByte() }
    private val before = "# Lisbon\n\n- Alfama\n"
    private val after = "# Lisbon\n\n- Alfama ![](media/picture-1.jpg)\n- Belém ![](media/sketch-1.png)\n"
    private val identity = "4b0c9f6e-3f7a-4a59-9d55-1f2a7c0e8b11"

    /** The arrival's text without its files. */
    private val plain = "# Lisbon\n\n- Alfama\n- Belém\n"

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        Dispatchers.resetMain()
        main.close()
        files.deleteRecursively()
    }

    /** The maps, as each run of Orbit reads them. */
    private suspend fun loaded() = MapRepository(File(files, "maps"), File(files, "trash"), starters, words, clock = { now }).also { it.load() }

    /** The intake of a run of Orbit over `maps`, in an app scope of its own. */
    private fun intake(maps: MapRepository, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }, queue: Mutex = Mutex()) =
        Intake(maps, Importer(maps, PagePool(context, File(files, "maps")), words), context.contentResolver, scope, words, ImportRequests(File(files, "requests")), queue)

    /** An intake whose app scope never runs anything: Orbit stopped right as the answer was given. */
    private fun stopped(maps: MapRepository) = intake(maps, CoroutineScope(Executor { }.asCoroutineDispatcher()))

    /** A screen of a run of Orbit, and where it's kept, which a screen made again or closed for good clears. */
    private class Screen(val arrivals: Arrivals, val store: ViewModelStore, val saved: SavedStateHandle)

    private suspend fun screen(maps: MapRepository, intake: Intake, saved: SavedStateHandle = SavedStateHandle()): Screen = withContext(Dispatchers.Main) {
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { Arrivals(maps.waitingArrivals, intake, saved, mapsRead = {}) } }
        Screen(ViewModelProvider.create(store, factory)[Arrivals::class], store, saved)
    }

    /** The screen's saved state as Android keeps it while Orbit is stopped, and gives it back to the screen made again. */
    private fun keptByAndroid(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** Lisbon, here as `before`, and the same map from another device as `after`, to ask about: its zip read. */
    private suspend fun lisbon(maps: MapRepository, withFiles: Boolean = true): Pair<String, OtherVersion> {
        val existing = maps.add(before)
        val info = """{"uuid":"$identity","focus":"alfama","view":"focus","path":["lisbon","alfama"]}"""
        val media = if (withFiles) arrayOf("media/picture-1.jpg" to picture, "media/sketch-1.png" to sketch) else emptyArray()
        val bundle = maps.readBundle(ByteArrayInputStream(zip("map.md" to (if (withFiles) after else plain).toByteArray(), *media, "orbit.json" to info.toByteArray())))!!
        return existing to OtherVersion(ImportResult.AlreadyHere(bundle, existing, "lisbon.zip"), opens = true)
    }

    private suspend fun until(done: suspend () -> Boolean) {
        withTimeout(10_000) {
            while (!done()) {
                delay(20)
            }
        }
    }

    private val OtherVersion.folder: File get() = arrival.bundle.unpacked!!

    /** Whether map `id` is the arrival, taken in whole: its text, its place and both its files. */
    private suspend fun updated(maps: MapRepository, id: String): Boolean {
        val folder = maps.folder(id)
        val metadata = maps.summary(id)?.metadata

        return maps.read(id) == after &&
            metadata?.focus == "alfama" &&
            metadata.path == listOf("lisbon", "alfama") &&
            File(folder, "media/picture-1.jpg").readBytes().contentEquals(picture) &&
            File(folder, "media/sketch-1.png").readBytes().contentEquals(sketch)
    }

    /** The maps here with the arrival's text, `text`, which Keep both adds once. */
    /** Another version of map `existing` with `text` and no files, as another zip brings it. */
    private suspend fun version(maps: MapRepository, existing: String, text: String): OtherVersion {
        val bundle = maps.readBundle(ByteArrayInputStream(zip("map.md" to text.toByteArray(), "orbit.json" to """{"uuid":"$identity"}""".toByteArray())))!!
        return OtherVersion(ImportResult.AlreadyHere(bundle, existing, "lisbon.zip"), opens = false)
    }

    /** The maps here with identity `uuid`. */
    private fun withIdentity(maps: MapRepository, uuid: String): List<String> = maps.maps.value.orEmpty().filter { it.metadata.uuid == uuid }.map { it.id }

    private fun copies(maps: MapRepository, text: String = after): List<String> =
        maps.maps.value.orEmpty().filter { it.id != "lisbon" && File(maps.folder(it.id), "map.md").readText() == text }.map { it.id }

    @Test
    fun `answered just as Orbit stops, before the answer is kept, the question comes back and the map is as it was`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        val screen = screen(maps, stopped(maps))
        withContext(Dispatchers.Main) { screen.arrivals.otherVersions += listOf(question) }
        withContext(Dispatchers.Main) { screen.arrivals.answer(question, keepBoth = false) }

        // The answer isn't on disk: the saved state still lists the question.
        assertEquals(listOf(question.folder.name), screen.saved.get<ArrayList<String>>("waiting"))
        assertNull(withContext(Dispatchers.Main) { screen.arrivals.otherVersions.asked })

        // A new run of Orbit, its screen brought back with what Android kept.
        val again = loaded()
        val restored = screen(again, intake(again).also { it.resume() }, keptByAndroid(screen.saved))
        until { withContext(Dispatchers.Main) { restored.arrivals.otherVersions.asked != null } }

        assertEquals(existing, withContext(Dispatchers.Main) { restored.arrivals.otherVersions.asked!!.arrival.existing })
        assertTrue(question.folder.isDirectory)
        assertEquals(before, again.read(existing))
    }

    @Test
    fun `answered Update, the map is updated once, the question goes, and the screen made again meanwhile opens it`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        val intake = intake(maps)
        val screen = screen(maps, intake)
        withContext(Dispatchers.Main) { screen.arrivals.otherVersions += listOf(question) }
        withContext(Dispatchers.Main) { screen.arrivals.answer(question, keepBoth = false) }

        // The screen is made again right away, as for a new language: what it was doing stops, the taking in doesn't.
        withContext(Dispatchers.Main) { screen.store.clear() }
        until { !question.folder.exists() && intake.outcomes.value.isNotEmpty() }

        assertTrue(updated(maps, existing))
        assertEquals(1, maps.versions(existing).size)
        val opened = intake.outcomes.value.single() as IntakeOutcome.Opened
        assertEquals(existing, opened.id)
    }

    @Test
    fun `stopped after the answer is kept, before any of it is taken in, the next run takes it in once and asks nothing`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        val saved = SavedStateHandle().apply { set("waiting", arrayListOf(question.folder.name)) }
        maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)

        val again = loaded()
        val intake = intake(again).also { it.resume() }
        val restored = screen(again, intake, keptByAndroid(saved))
        until { !question.folder.exists() }

        assertTrue(updated(again, existing))
        assertEquals(1, again.versions(existing).size)
        delay(200)
        assertNull(withContext(Dispatchers.Main) { restored.arrivals.otherVersions.asked })
        // Nothing asked for its map since: it doesn't open.
        assertEquals(emptyList<IntakeOutcome>(), intake.outcomes.value)
    }

    @Test
    fun `an Update stopped after its files moved into the map, before its text, finishes once`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        // The step it stopped after: the files moved, the map's text not written yet.
        val media = File(maps.folder(existing), "media").apply { mkdirs() }
        question.arrival.bundle.pictures.forEach { (name, file) -> assertTrue(file.renameTo(File(media, name))) }

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertTrue(updated(again, existing))
        assertEquals(1, again.versions(existing).size)
    }

    @Test
    fun `an Update stopped after its text, before the map's details, finishes them and keeps no second version`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        val media = File(maps.folder(existing), "media").apply { mkdirs() }
        question.arrival.bundle.pictures.forEach { (name, file) -> assertTrue(file.renameTo(File(media, name))) }
        File(maps.folder(existing), "map.md").writeText(after)
        val versions = maps.versions(existing).size

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertTrue(updated(again, existing))
        assertEquals(versions, again.versions(existing).size)
    }

    @Test
    fun `an Update stopped once it was in, before its files were let go, isn't done twice`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        val accepted = maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        maps.receive(question.arrival.bundle, replacing = existing, base = accepted.base, copy = accepted.copy)
        assertTrue(question.folder.isDirectory)

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertTrue(updated(again, existing))
        assertEquals(1, again.versions(existing).size)
    }

    @Test
    fun `answered Keep both, it's added once, beside the map here, with every file`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        val intake = intake(maps)
        val screen = screen(maps, intake)
        withContext(Dispatchers.Main) { screen.arrivals.otherVersions += listOf(question) }
        withContext(Dispatchers.Main) { screen.arrivals.answer(question, keepBoth = true) }
        until { !question.folder.exists() && intake.outcomes.value.isNotEmpty() }

        val copy = copies(maps).single()
        assertEquals(before, maps.read(existing))
        assertTrue(updated(maps, copy))
        assertEquals(copy, (intake.outcomes.value.single() as IntakeOutcome.Opened).id)
        until { screen.saved.get<ArrayList<String>>("waiting")?.isEmpty() == true }
    }

    @Test
    fun `a Keep both stopped while its map was being made, a week back, is made once with every file`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        maps.waitingArrivals.accept(question.arrival, keepBoth = true, base = null, opens = true)
        // The step it stopped at: its map half made in the arrival's folder, one of its files moved there.
        val made = File(question.folder, ".copy/media").apply { mkdirs() }
        File(question.folder, ".copy/map.md").writeText(after.take(10))
        assertTrue(File(question.folder, "picture-1.jpg").renameTo(File(made, "picture-1.jpg")))
        question.folder.setLastModified(now)
        now += 8 * day

        // A start's cleaning of leftovers, and a new screen, keep it.
        val again = loaded()
        screen(again, stopped(again))
        delay(200)
        assertTrue(File(made, "picture-1.jpg").isFile)

        intake(again).resume()
        until { !question.folder.exists() }

        assertEquals(before, again.read(existing))
        assertTrue(updated(again, copies(again).single()))
    }

    @Test
    fun `a Keep both stopped once its map was made, before its files were let go, isn't added twice`() = runBlocking {
        val maps = loaded()
        val (_, question) = lisbon(maps)
        val accepted = maps.waitingArrivals.accept(question.arrival, keepBoth = true, base = null, opens = true)
        maps.receive(question.arrival.bundle, copy = accepted.copy) { maps.waitingArrivals.addsAs(question.arrival, it) }
        assertEquals(1, copies(maps).size)

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertEquals(1, copies(again).size)
    }

    @Test
    fun `a Keep both without files, stopped once its map was made, isn't added twice`() = runBlocking {
        val maps = loaded()
        val (_, question) = lisbon(maps, withFiles = false)
        val accepted = maps.waitingArrivals.accept(question.arrival, keepBoth = true, base = null, opens = true)
        maps.receive(question.arrival.bundle, copy = accepted.copy) { maps.waitingArrivals.addsAs(question.arrival, it) }
        assertEquals(1, copies(maps, plain).size)

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertEquals(1, copies(again, plain).size)
    }

    @Test
    fun `a Keep both that can't be added is asked about again, its files kept, then Neither lets it go`() = runBlocking {
        val maps = loaded()
        val (_, question) = lisbon(maps)
        val intake = intake(maps)
        val screen = screen(maps, intake)
        withContext(Dispatchers.Main) { screen.arrivals.otherVersions += listOf(question) }
        // The maps' folder takes no new map.
        File(files, "maps").setWritable(false)

        try {
            withContext(Dispatchers.Main) { screen.arrivals.answer(question, keepBoth = true) }
            until { intake.outcomes.value.isNotEmpty() }
        } finally {
            File(files, "maps").setWritable(true)
        }

        val back = intake.outcomes.value.single() as IntakeOutcome.AskAgain
        intake.take(back)
        assertEquals(emptyList<String>(), copies(maps))
        assertNotNull(maps.waitingArrivals.read(question.folder.name))

        // Why is said first, then the question is asked again, its files all there; and every screen that starts asks it.
        withContext(Dispatchers.Main) { screen.arrivals.askAgain(back.question, back.reason) }
        assertNull(withContext(Dispatchers.Main) { screen.arrivals.otherVersions.asked })
        assertNotNull(withContext(Dispatchers.Main) { screen.arrivals.notTakenIn })
        // Said under its own title, not as a file that couldn't be opened.
        assertNull(withContext(Dispatchers.Main) { screen.arrivals.problem })
        withContext(Dispatchers.Main) { screen.arrivals.notTakenIn = null }
        val asked = withContext(Dispatchers.Main) { screen.arrivals.otherVersions.asked!! }
        assertArrayEquals(picture, asked.arrival.bundle.pictures.getValue("picture-1.jpg").readBytes())
        assertArrayEquals(sketch, asked.arrival.bundle.pictures.getValue("sketch-1.png").readBytes())

        val fresh = screen(maps, intake)
        until { withContext(Dispatchers.Main) { fresh.arrivals.otherVersions.asked != null } }

        withContext(Dispatchers.Main) { screen.arrivals.decline(asked) }
        until { !question.folder.exists() }
    }

    @Test
    fun `a map changed after its Update was answered isn't written over, and the question comes back`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        // Changed meanwhile, as by the folder maps are kept in, before Orbit took it in.
        val changed = "# Lisbon\n\n- Alfama\n- Sintra\n"
        maps.write(existing, changed, base = before)

        val again = loaded()
        val intake = intake(again).also { it.resume() }
        until { intake.outcomes.value.isNotEmpty() }

        val back = intake.outcomes.value.single() as IntakeOutcome.AskAgain
        assertEquals(words.string(R.string.data_map_changed_since_answered), back.reason)
        assertEquals(changed, again.read(existing))
        assertEquals(existing, back.question.arrival.existing)
        assertNotNull(again.waitingArrivals.read(question.folder.name))
    }

    @Test
    fun `an Update whose map went before it was taken in adds it with its own identity, so its next version finds it`() = runBlocking {
        val maps = loaded()
        val (existing, question) = lisbon(maps)
        maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        maps.delete(existing)

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertTrue(updated(again, withIdentity(again, identity).single()))
    }

    @Test
    fun `an Update whose map went, stopped once it was added with its own identity, isn't added twice`() = runBlocking {
        val maps = loaded()
        val existing = maps.add(before)
        val question = version(maps, existing, plain)
        val accepted = maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        maps.delete(existing)
        // Taken in up to adding the map, as Orbit does, before its files were let go.
        maps.receive(question.arrival.bundle, existing, base = accepted.base, copy = accepted.copy) { maps.waitingArrivals.addsAs(question.arrival, it) }
        assertEquals(1, withIdentity(maps, identity).size)

        val again = loaded()
        intake(again).resume()
        until { !question.folder.exists() }

        assertEquals(1, withIdentity(again, identity).size)
    }

    @Test
    fun `an answer that can't be kept is asked again, and the next Update of its map isn't held to it`() = runBlocking {
        val maps = loaded()
        val existing = maps.add(before)
        val first = version(maps, existing, plain)
        val second = version(maps, existing, "# Lisbon\n\n- Alfama\n- Sintra\n")
        val intake = intake(maps)

        // Its folder takes no record, as a full storage wouldn't.
        first.folder.mkdirs()
        first.folder.setWritable(false)

        try {
            intake.accept(first, keepBoth = false).await()
            until { intake.outcomes.value.isNotEmpty() }
        } finally {
            first.folder.setWritable(true)
        }

        val back = intake.outcomes.value.single() as IntakeOutcome.AskAgain
        intake.take(back)
        assertEquals(first.folder, back.question.arrival.bundle.unpacked)
        assertEquals(before, maps.read(existing))

        // The next Update of the map is for the text it has, not for the one the first would have given it.
        intake.accept(second, keepBoth = false).await()
        until { !second.folder.exists() }
        assertEquals(second.arrival.bundle.markdown, maps.read(existing))
        assertEquals(emptyList<IntakeOutcome>(), intake.outcomes.value)
    }

    @Test
    fun `an Update answered after one that couldn't be taken in isn't said to be for a map that changed`() = runBlocking {
        val maps = loaded()
        val existing = maps.add(before)
        val (_, withFiles) = lisbon(maps)
        val first = OtherVersion(ImportResult.AlreadyHere(withFiles.arrival.bundle, existing, "lisbon.zip"), opens = false)
        val second = version(maps, existing, "# Lisbon\n\n- Alfama\n- Sintra\n")
        val queue = Mutex()
        val intake = intake(maps, queue = queue)

        queue.lock()
        intake.accept(first, keepBoth = false).await()
        intake.accept(second, keepBoth = false).await()
        // The first can't be taken in: one of its files went, as when there's no room to copy it.
        File(first.folder, "picture-1.jpg").delete()
        queue.unlock()
        until { !second.folder.exists() }

        assertEquals(second.arrival.bundle.markdown, maps.read(existing))
        assertTrue(intake.outcomes.value.none { it is IntakeOutcome.AskAgain && it.question.arrival.bundle.markdown == second.arrival.bundle.markdown })
    }

    @Test
    fun `two Updates of a map answered one right after the other both go in, the second over the first`() = runBlocking {
        val maps = loaded()
        val existing = maps.add(before)
        val first = version(maps, existing, plain)
        val second = version(maps, existing, "# Lisbon\n\n- Alfama\n- Sintra\n")
        val queue = Mutex()
        val intake = intake(maps, queue = queue)

        // Both answered before either is in: the second is for the text the first gives the map.
        queue.lock()
        intake.accept(first, keepBoth = false).await()
        intake.accept(second, keepBoth = false).await()
        queue.unlock()
        until { !first.folder.exists() && !second.folder.exists() }

        assertEquals(second.arrival.bundle.markdown, maps.read(existing))
        assertEquals(2, maps.versions(existing).size)
        assertEquals(emptyList<IntakeOutcome>(), intake.outcomes.value)
    }

    @Test
    fun `Updates Orbit stopped before they were in are taken in in the order they were answered`() = runBlocking {
        val maps = loaded()
        val existing = maps.add(before)
        var text = before
        val texts = (1..5).map { "# Lisbon\n\n- Stop $it\n" }

        for (next in texts) {
            maps.waitingArrivals.accept(version(maps, existing, next).arrival, keepBoth = false, base = textHash(text), opens = false)
            text = next
        }

        val again = loaded()
        val intake = intake(again).also { it.resume() }
        until { again.read(existing) == texts.last() && File(files, "unpacking").list().orEmpty().isEmpty() }

        assertEquals(5, again.versions(existing).size)
        assertEquals(emptyList<IntakeOutcome>(), intake.outcomes.value)
    }

    @Test
    fun `a new screen and a start's cleaning of leftovers a week on keep an answered arrival`() = runBlocking {
        val maps = loaded()
        val (_, question) = lisbon(maps)
        maps.waitingArrivals.accept(question.arrival, keepBoth = false, base = textHash(before), opens = true)
        question.folder.setLastModified(now)
        now += 8 * day

        val again = loaded()
        screen(again, stopped(again))
        delay(200)

        assertTrue(File(question.folder, "picture-1.jpg").isFile)
        assertTrue(File(question.folder, ".arrival.json").isFile)
    }
}
