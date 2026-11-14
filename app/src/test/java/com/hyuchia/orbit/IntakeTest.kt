package com.hyuchia.orbit

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.ImportProgress
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.StarterMap
import com.hyuchia.orbit.data.StarterMaps
import com.hyuchia.orbit.page.ImportedFileMap
import com.hyuchia.orbit.page.PagePool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What comes into Orbit from other apps outlives the screen that was there
 * as it came: a request made before a screen takes requests, or as the
 * screen is made again, reaches the next one, once; and a file opened with
 * Orbit is imported once, whatever happens to the screen meanwhile, what it
 * brought waiting for the screen there is then.
 */
@RunWith(AndroidJUnit4::class)
class IntakeTest {
    private val files: File = Files.createTempDirectory("orbit-intake").toFile()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val words = FileWords(Locale.US)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private lateinit var maps: MapRepository
    private lateinit var intake: Intake

    /** The intake of a run of Orbit, in app scope `scope`, over `maps` and the imports kept on disk. */
    private fun intake(scope: CoroutineScope, maps: MapRepository = this.maps) =
        Intake(maps, Importer(maps, PagePool(context, File(files, "maps")), words), context.contentResolver, scope, words, ImportRequests(File(files, "requests")))

    /** The maps, as a new run of Orbit reads them. */
    private suspend fun loaded() = MapRepository(File(files, "maps"), File(files, "trash"), starters, words).also { it.load() }

    /** Waits for the first thing `intake` did. */
    private suspend fun firstOutcome(intake: Intake): IntakeOutcome = withTimeout(10_000) {
        while (intake.outcomes.value.isEmpty()) {
            delay(20)
        }

        intake.outcomes.value.first()
    }

    /** The main thread, as Android has one. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(main)
        Lent.reset()
        Robolectric.setupContentProvider(Lent::class.java, AUTHORITY)
        maps = MapRepository(File(files, "maps"), File(files, "trash"), starters, words)
        maps.load()
        intake = intake(scope)
    }

    @After
    fun cleanUp() {
        Lent.release.countDown()
        scope.cancel()
        Dispatchers.resetMain()
        main.close()
        files.deleteRecursively()
    }

    /** Another app's files, lent to Orbit: one is held back as it's opened until the test lets it go. */
    class Lent : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val key = uri.lastPathSegment ?: return null
            val bytes = bytesOf(key) ?: return null

            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(if (zips.containsKey(key)) "$key.zip" else "$key.md", bytes.size.toLong()))
            }
        }

        override fun getType(uri: Uri): String = if (zips.containsKey(uri.lastPathSegment.orEmpty())) "application/zip" else "text/markdown"

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val key = uri.lastPathSegment.orEmpty()
            val bytes = bytesOf(key) ?: throw FileNotFoundException(uri.toString())

            if (key == held) {
                opened.countDown()
                release.await(10, TimeUnit.SECONDS)
            }

            val copy = File.createTempFile("lent", ".bin").apply {
                deleteOnExit()
                writeBytes(bytes)
            }

            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        private fun bytesOf(key: String): ByteArray? = zips[key] ?: texts[key]?.toByteArray()

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            val texts = ConcurrentHashMap<String, String>()

            /** Zips of maps, by key. */
            val zips = ConcurrentHashMap<String, ByteArray>()

            @Volatile var held: String? = null

            @Volatile var opened = CountDownLatch(1)

            @Volatile var release = CountDownLatch(1)

            fun reset() {
                texts.clear()
                zips.clear()
                held = null
                opened = CountDownLatch(1)
                release = CountDownLatch(1)
            }
        }
    }

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/$key")

    @Test
    fun `a request made before a screen takes requests, or as one is made again, reaches the next screen once`() = runBlocking {
        // Asked as Orbit starts, before its screen is up.
        intake.request(AppRequest.NewMap)
        intake.request(AppRequest.OpenMap("lisbon"))

        // The first screen takes one, and is made again (its collecting stops) before it takes the other.
        val first = withTimeout(10_000) { intake.requested.first() }
        val next = withTimeout(10_000) { intake.requested.first() }

        assertEquals(AppRequest.NewMap, first)
        assertEquals(AppRequest.OpenMap("lisbon"), next)

        // Each reached one screen: a third starting now has nothing to do.
        val third = scope.async { intake.requested.take(1).toList() }
        delay(200)
        assertTrue(third.isActive)
        third.cancel()
    }

    @Test
    fun `a file opened with Orbit is imported once, whatever happens to its screen meanwhile, and waits for the next screen`() = runBlocking {
        Lent.texts["lisbon"] = "# Trip to Lisbon\n\n- Belém\n- Alfama\n"
        Lent.held = "lisbon"
        // The screen that was there, which used to import it in its own scope.
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        screen.launch { intake.importFile(address("lisbon")) }

        assertTrue("The file was never read", Lent.opened.await(10, TimeUnit.SECONDS))
        // Made again, as for a new language, while the file is read: what the screen was doing stops.
        screen.cancel()
        Lent.release.countDown()

        withTimeout(10_000) {
            while (intake.outcomes.value.isEmpty()) {
                delay(20)
            }
        }

        val outcome = intake.outcomes.value.single() as IntakeOutcome.Imported
        val imported = outcome.result as ImportResult.Imported
        assertEquals("# Trip to Lisbon\n\n- Belém\n- Alfama\n", maps.read(imported.id))
        assertEquals(1, maps.maps.value.orEmpty().size)

        // The next screen takes it, once.
        intake.take(outcome)
        assertEquals(emptyList<IntakeOutcome>(), intake.outcomes.value)
    }

    @Test
    fun `a file opened with Orbit as Android stops it is imported once, by the screen Android brings back`() = runBlocking {
        Lent.texts["lisbon"] = LISBON
        Lent.held = "lisbon"
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val id = intake(first).importFile(address("lisbon"))
        assertTrue("The file was never read", Lent.opened.await(10, TimeUnit.SECONDS))

        // Android stops Orbit while the file is read.
        first.cancel()
        Lent.held = null
        Lent.release.countDown()
        delay(200)
        assertTrue(File(files, "requests/$id.json").isFile)

        // The screen it brings back was waiting for it.
        val again = loaded()
        val next = intake(scope, again)
        next.resumeImports(listOf(id))
        val imported = (firstOutcome(next) as IntakeOutcome.Imported)
        assertEquals(id, imported.request)
        assertEquals(LISBON, again.read((imported.result as ImportResult.Imported).id))
        assertEquals(1, again.maps.value.orEmpty().size)
    }

    @Test
    fun `a file imported just before Android stopped Orbit opens its map on the screen it brings back, and isn't imported twice`() = runBlocking {
        Lent.texts["lisbon"] = LISBON
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val running = intake(first)
        val id = running.importFile(address("lisbon"))
        val made = ((firstOutcome(running) as IntakeOutcome.Imported).result as ImportResult.Imported).id

        // Stopped before any screen took what it brought.
        first.cancel()
        val again = loaded()
        val next = intake(scope, again)
        next.resumeImports(listOf(id))

        val shown = firstOutcome(next) as IntakeOutcome.Imported
        assertEquals(made, (shown.result as ImportResult.Imported).id)
        assertEquals(1, again.maps.value.orEmpty().size)

        // Taken: nothing's kept of it any more.
        next.take(shown)

        withTimeout(10_000) {
            while (File(files, "requests/$id.json").exists()) {
                delay(20)
            }
        }
    }

    @Test
    fun `a new screen lets go of the imports no screen will ask for, as their task was closed`() = runBlocking {
        Lent.texts["lisbon"] = LISBON
        Lent.held = "lisbon"
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val id = intake(first).importFile(address("lisbon"))
        assertTrue(Lent.opened.await(10, TimeUnit.SECONDS))
        first.cancel()
        Lent.release.countDown()

        val again = loaded()
        val next = intake(scope, again)
        next.resumeImports(emptyList())

        withTimeout(10_000) {
            while (File(files, "requests/$id.json").exists()) {
                delay(20)
            }
        }

        delay(200)
        assertEquals(emptyList<IntakeOutcome>(), next.outcomes.value)
        assertEquals(0, again.maps.value.orEmpty().size)
    }

    @Test
    fun `another version of a map here, opened just before Android stopped Orbit, is asked about by the next screen`() = runBlocking {
        val existing = maps.add("# Porto\n")
        val identity = maps.summary(existing)!!.metadata.uuid!!
        Lent.zips["porto"] = zip("map.md" to "# Porto\n\n- Ribeira\n".toByteArray(), "orbit.json" to """{"uuid":"$identity"}""".toByteArray())
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val running = intake(first)
        running.importFile(address("porto"))
        assertTrue((firstOutcome(running) as IntakeOutcome.Imported).result is ImportResult.AlreadyHere)

        // Stopped before any screen asked it: a new screen, even one whose task was closed, asks it.
        first.cancel()
        val again = loaded()
        val screen = withContext(Dispatchers.Main) { Arrivals(again.waitingArrivals, intake(scope, again), SavedStateHandle(), mapsRead = {}) }

        withTimeout(10_000) {
            while (withContext(Dispatchers.Main) { screen.otherVersions.asked } == null) {
                delay(20)
            }
        }

        val asked = withContext(Dispatchers.Main) { screen.otherVersions.asked!! }
        assertEquals(existing, asked.arrival.existing)
        assertTrue(asked.opens)
    }

    @Test
    fun `files shared for a new map as Android stops Orbit, mid-copy, end up in that one map, not a second`() = runBlocking {
        Lent.texts["one"] = "Tickets"
        Lent.texts["two"] = "Hotel"
        Lent.held = "two"
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val id = intake(first).newMapWithFiles(listOf(address("one"), address("two")))
        assertTrue("The second file was never read", Lent.opened.await(10, TimeUnit.SECONDS))

        // The map was made, and the first file copied, when Android stops Orbit.
        assertEquals(1, maps.maps.value.orEmpty().size)
        first.cancel()
        Lent.held = null
        Lent.release.countDown()
        delay(200)

        val again = loaded()
        val next = intake(scope, again)
        next.resumeImports(listOf(id))
        val made = ((firstOutcome(next) as IntakeOutcome.Imported).result as ImportResult.Imported).id

        assertEquals(listOf(made), again.maps.value.orEmpty().map { it.id })
        val text = again.read(made)
        assertTrue(text, "one.md" in text && "two.md" in text)
    }

    @Test
    fun `a workbook's sheets made just before Android stopped Orbit aren't made again`() = runBlocking {
        val importer = Importer(maps, PagePool(context, File(files, "maps")), words)
        val sheets = listOf(ImportedFileMap("# Lisbon\n\n- Alfama\n"), ImportedFileMap("# Porto\n\n- Ribeira\n"))
        val made = mutableListOf<String>()

        // Android stops Orbit right as the sheets' maps are there, as they're kept with the import.
        try {
            importer.addMaps(sheets, original = null, name = "trips.xmind", progress = ImportProgress(making = { maps -> made += maps; throw CancellationException("Stopped") }))
        } catch (stopped: CancellationException) {
            Unit
        }

        assertEquals(2, made.size)
        val ids = importer.addMaps(sheets, original = null, name = "trips.xmind", progress = ImportProgress(made.toList()))

        assertEquals(made, ids)
        assertEquals(2, maps.maps.value.orEmpty().size)
    }

    @Test
    fun `a new map for shared files that goes to the trash isn't kept as made, and a run again makes another`() = runBlocking {
        val importer = Importer(maps, PagePool(context, File(files, "maps")), words)
        val made = mutableListOf<String>()
        val unmade = mutableListOf<String>()

        // Neither file can be read: the map made for them goes to the trash.
        val failed = importer.newMapWithFiles(listOf(address("gone"), address("lost")), context.contentResolver, ImportProgress(making = { made += it }, unmaking = { unmade += it }))

        assertTrue(failed is ImportResult.Failed)
        assertEquals(made, unmade)
        assertEquals(0, maps.maps.value.orEmpty().size)

        // Run again with what it made then, its files there now: a map of its own, not the one in the trash.
        Lent.texts["gone"] = "Tickets"
        val again = importer.newMapWithFiles(listOf(address("gone")), context.contentResolver, ImportProgress(made.toList())) as ImportResult.Imported

        assertFalse(again.id in made)
        assertEquals(listOf(again.id), maps.maps.value.orEmpty().map { it.id })
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private companion object {
        const val AUTHORITY = "com.hyuchia.orbit.test.intake"
        const val LISBON = "# Trip to Lisbon\n\n- Belém\n- Alfama\n"
    }
}
