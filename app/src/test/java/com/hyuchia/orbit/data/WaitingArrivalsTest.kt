package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A map's file that's here already in another version, kept on disk while
 * it waits for an answer, in Orbit's own files beside what its zip unpacked
 * to: read back as it came, let go once it's answered or no longer asked
 * about, and kept through the cleaning of leftovers a new start does, for a
 * while.
 */
class WaitingArrivalsTest {
    private val files: File = Files.createTempDirectory("orbit-waiting").toFile()
    private val maps = File(files, "maps")
    private val unpacking = File(files, "unpacking")
    private var now = 1_000_000_000_000L
    private val hour = 60 * 60 * 1000L

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    private val words = FileWords(Locale.US)
    private val picture = ByteArray(2_000) { (it % 251).toByte() }

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    private suspend fun loaded() = MapRepository(maps, File(files, "trash"), starters, words, clock = { now }).also { it.load() }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** Another version of map `existing`, as a file opened with Orbit brings it: its zip read, waiting for an answer. */
    private suspend fun arrival(repository: MapRepository, existing: String): ImportResult.AlreadyHere {
        val text = "# Lisbon\n\n- Alfama ![](media/picture-1.jpg)\n- Belém\n"
        val info = """{"uuid":"4b0c9f6e-3f7a-4a59-9d55-1f2a7c0e8b11","focus":"alfama","view":"focus","path":["lisbon","alfama"]}"""
        val bundle = repository.readBundle(ByteArrayInputStream(zip("map.md" to text.toByteArray(), "media/picture-1.jpg" to picture, "orbit.json" to info.toByteArray())))!!
        return ImportResult.AlreadyHere(bundle, existing, "lisbon.zip")
    }

    @Test
    fun `a file waiting for an answer is read back as it came, its files where they were unpacked`() = runTest {
        val repository = loaded()
        val existing = repository.add("# Lisbon\n\n- Alfama\n")
        val arrival = arrival(repository, existing)
        val id = arrival.bundle.unpacked!!.name

        assertTrue(repository.waitingArrivals.keep(arrival))
        // As a new run of Orbit reads it.
        val back = loaded().waitingArrivals.read(id)!!

        assertEquals(existing, back.existing)
        assertEquals("lisbon.zip", back.name)
        assertEquals(arrival.bundle.markdown, back.bundle.markdown)
        assertEquals("Lisbon", back.bundle.title)
        assertEquals(arrival.bundle.uuid, back.bundle.uuid)
        assertEquals("alfama", back.bundle.focus)
        assertEquals("focus", back.bundle.view)
        assertEquals(listOf("lisbon", "alfama"), back.bundle.path)
        assertEquals(arrival.bundle.media, back.bundle.media)
        assertEquals(1, back.bundle.media.pictures)
        assertEquals(arrival.bundle.unpacked, back.bundle.unpacked)
        assertArrayEquals(picture, back.bundle.pictures.getValue("picture-1.jpg").readBytes())

        // Answered with Update, it lands in the map as one never kept would.
        repository.receive(back.bundle, replacing = existing)
        assertEquals(arrival.bundle.markdown, repository.read(existing))
        assertArrayEquals(picture, File(maps, "$existing/media/picture-1.jpg").readBytes())
        repository.waitingArrivals.letGo(back)
        assertFalse(arrival.bundle.unpacked!!.exists())
        assertNull(repository.waitingArrivals.read(id))
    }

    @Test
    fun `one declined goes with every file it was unpacked to`() = runTest {
        val repository = loaded()
        val arrival = arrival(repository, repository.add("# Lisbon\n"))
        repository.waitingArrivals.keep(arrival)

        repository.waitingArrivals.letGo(arrival)

        assertEquals(emptyList<String>(), unpacking.list().orEmpty().toList())
    }

    @Test
    fun `those no screen will ask about again go, and the rest stay`() = runTest {
        val repository = loaded()
        val existing = repository.add("# Lisbon\n")
        val asked = arrival(repository, existing).also { repository.waitingArrivals.keep(it) }
        val forgotten = arrival(repository, existing).also { repository.waitingArrivals.keep(it) }
        // A zip being read right now, not waiting for anything yet, isn't one of them.
        val reading = File(unpacking, "reading").apply { mkdirs() }

        repository.waitingArrivals.letGoAllBut(setOf(asked.bundle.unpacked!!.name))

        assertTrue(asked.bundle.unpacked!!.isDirectory)
        assertFalse(forgotten.bundle.unpacked!!.exists())
        assertTrue(reading.isDirectory)
    }

    @Test
    fun `one being taken in stays while its files move into the map, whatever screen starts meanwhile, and goes after`() = runTest {
        val repository = loaded()
        val arrival = arrival(repository, repository.add("# Lisbon\n"))
        repository.waitingArrivals.keep(arrival)
        val folder = arrival.bundle.unpacked!!

        repository.waitingArrivals.takeIn(arrival)
        // A new screen starts (Back closed the last, the process stayed), and so does a new reading of the maps, a week on.
        repository.waitingArrivals.letGoAllBut(emptySet())
        folder.setLastModified(now)
        now += 8 * 24 * hour
        repository.load()
        assertTrue(File(folder, "picture-1.jpg").isFile)

        repository.waitingArrivals.letGo(arrival)
        assertFalse(folder.exists())
        assertFalse(repository.waitingArrivals.isTakingIn(folder.name))
    }

    @Test
    fun `one whose files went, or that isn't one, reads as none`() = runTest {
        val repository = loaded()
        val arrival = arrival(repository, repository.add("# Lisbon\n"))
        repository.waitingArrivals.keep(arrival)
        arrival.bundle.pictures.getValue("picture-1.jpg").delete()

        assertNull(repository.waitingArrivals.read(arrival.bundle.unpacked!!.name))
        assertNull(repository.waitingArrivals.read("../maps"))
        assertNull(repository.waitingArrivals.read(""))
    }

    @Test
    fun `a new start clears zips left unpacked, but keeps one waiting for an answer for a week`() = runTest {
        val repository = loaded()
        val existing = repository.add("# Lisbon\n")
        val waiting = arrival(repository, existing).also { repository.waitingArrivals.keep(it) }.bundle.unpacked!!
        val left = arrival(repository, existing).bundle.unpacked!!
        waiting.setLastModified(now)
        left.setLastModified(now)

        now += 2 * hour
        loaded()
        assertTrue(waiting.isDirectory)
        assertFalse(left.exists())

        now += 7 * 24 * hour
        loaded()
        assertFalse(waiting.exists())
    }
}
