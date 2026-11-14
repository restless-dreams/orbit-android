package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Pictures, videos, sounds and files taken back from a map's folder go under
 * the lock, as every deletion of a map's files does: Export all lists a
 * map's files and opens them under it, so it never finds one of them gone
 * and stops over a file that never joined the map.
 */
class TakeBackTest {
    private val root: File = Files.createTempDirectory("orbit-take-back").toFile()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a file taken back waits for the lock, as Export all holds it from listing a map's files to opening them`() = runBlocking {
        val store = MapStore(File(root, "maps"), File(root, "trash"), File(root, "history"), File(root, "folder-deletions.json"), FileWords(Locale.US), System::currentTimeMillis, 0, { true })
        val intake = MapMediaIntake(store) { Long.MAX_VALUE }
        val folder = File(root, "maps/trip").apply { mkdirs() }
        File(folder, MAP_FILE).writeText("# Trip\n- Day one\n")

        val picture = File(folder, "$MEDIA_DIRECTORY/picture-1.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }

        store.mutex.lock()
        val taking = launch(Dispatchers.IO) { intake.takeBack("trip", listOf("$MEDIA_DIRECTORY/picture-1.jpg")) }

        // Held by Export all, the lock keeps the picture where it was listed.
        assertNull(withTimeoutOrNull(500) { taking.join() })
        assertTrue(picture.exists())

        store.mutex.unlock()
        taking.join()
        assertFalse(picture.exists())
    }
}
