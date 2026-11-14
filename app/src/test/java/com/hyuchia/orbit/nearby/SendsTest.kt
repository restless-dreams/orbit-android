package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.AsTheyAreFiles
import com.hyuchia.orbit.data.FileChanged
import com.hyuchia.orbit.data.FileStamp
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.ui.AsTheyAre
import com.hyuchia.orbit.ui.AsTheyAreChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The send sheet's sends: one at a time to each device, and nothing of a map left behind however its send stops. */
@OptIn(ExperimentalCoroutinesApi::class)
class SendsTest {
    private val folder: File = File(Files.createTempDirectory("orbit-sends").toFile(), "sending")

    @After
    fun clean() {
        folder.parentFile?.deleteRecursively()
    }

    private val left: List<String> get() = folder.list().orEmpty().toList()

    @Test
    fun `tapping a device again while its map is made ready sends it once`() = runTest {
        val sends = Sends(backgroundScope)
        val ready = CompletableDeferred<Unit>()
        var made = 0
        var sent = 0

        val send: suspend ((SendState) -> Unit) -> Unit = { onState ->
            made += 1
            // A big map's zip takes a while.
            ready.await()
            sent += 1
            onState(SendState.Opened)
        }

        assertTrue(sends.start("192.168.1.7:8766", send))
        runCurrent()
        // It shows it's sending from the tap, and the taps after it send nothing more.
        assertEquals(SendState.Sending, sends.states["192.168.1.7:8766"])
        assertFalse(sends.start("192.168.1.7:8766", send))
        assertFalse(sends.start("192.168.1.7:8766", send))
        runCurrent()

        ready.complete(Unit)
        runCurrent()
        assertEquals(1, made)
        assertEquals(1, sent)
        assertEquals(SendState.Opened, sends.states["192.168.1.7:8766"])

        // Once it went, a tap sends it again.
        assertTrue(sends.start("192.168.1.7:8766", send))
        runCurrent()
        assertEquals(2, sent)
    }

    @Test
    fun `a send under way takes no tap while the codes show or the answer is awaited, and one that failed takes the next`() = runTest {
        val sends = Sends(backgroundScope)
        val step = CompletableDeferred<SendState>()
        var started = 0

        val send: suspend ((SendState) -> Unit) -> Unit = { onState ->
            started += 1
            onState(SendState.Confirming("123456"))
            onState(step.await())
            awaitCancellation()
        }

        sends.start("192.168.1.7:8766", send)
        runCurrent()
        assertFalse(sends.start("192.168.1.7:8766", send))
        step.complete(SendState.Waiting("123456"))
        runCurrent()
        assertFalse(sends.start("192.168.1.7:8766", send))
        assertEquals(1, started)

        // Another device can be sent to meanwhile.
        assertTrue(sends.start("192.168.1.9:8766") { onState -> onState(SendState.Failed(R.string.nearby_send_no_room_here)) })
        runCurrent()
        assertEquals(SendState.Failed(R.string.nearby_send_no_room_here), sends.states["192.168.1.9:8766"])
        assertTrue(sends.start("192.168.1.9:8766") { onState -> onState(SendState.Opened) })
        runCurrent()
        assertEquals(SendState.Opened, sends.states["192.168.1.9:8766"])
    }

    /** Waits, on the test's own thread, for what its sends do on theirs. */
    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) {
            delay(10)
        }
    }

    @Test
    fun `a file that changed as its map was made ready is asked about again, leaving nothing behind, and the answer sends once`() = runBlocking<Unit> {
        val address = "192.168.1.7:8766"
        val sends = Sends(this)
        val asTheyAre = AsTheyAre(AsTheyAreChoice())
        val photo = FileStamp("media/photo.jpg", 1_000, 1, 1, null)
        val before = Uncleaned(pictures = setOf("photo.jpg"), stamps = mapOf("photo.jpg" to photo))
        // The photo is written again after the person said to send it as it is.
        val changed = photo.copy(size = 2_000, modified = 2, changed = 2)
        val now = before.copy(stamps = mapOf("photo.jpg" to changed))
        val delivered = CompletableDeferred<Unit>()
        val sent = mutableListOf<AsTheyAreFiles>()
        var askedAgain = 0

        // As the sheet sends: the zip is written with the answers, and a file chosen as it was, and changed since, stops it.
        fun sendTo(files: AsTheyAreFiles, again: Boolean = false) {
            val askAgain = if (again) {
                null
            } else {
                suspend {
                    askedAgain += 1
                    asTheyAre.askAgain(now) { answers -> sendTo(answers, again = true) }
                }
            }

            val prepare: suspend () -> File = {
                preparedBundle(folder) { output ->
                    output.write("half a zip".toByteArray())

                    if (changed !in files.chosen) {
                        throw FileChanged("photo.jpg")
                    }
                }
            }

            sends.sendBundle(address, prepare, askAgain) { bundle, onState ->
                assertTrue(bundle.isFile)
                sent += files
                delivered.await()
                onState(SendState.Opened)
            }
        }

        // A tap, as the sheet takes it, with the files as the sheet knew them: nothing while a send there is under way.
        fun tap() {
            if (!sends.underWay(address)) {
                asTheyAre.then(before) { answers -> sendTo(answers) }
            }
        }

        tap()
        asTheyAre.answer(asTheyAre.question!!, share = true)
        assertEquals(SendState.Sending, sends.states[address])

        // The photo changed as the zip was made: nothing went, the device isn't sending, and the question is back, as it is now.
        until { asTheyAre.question != null }
        assertEquals(null, sends.states[address])
        assertTrue(asTheyAre.question!!.changed)
        assertEquals(setOf(changed), asTheyAre.question!!.files.stamps.values.toSet())
        assertEquals(emptyList<String>(), left)
        assertEquals(1, askedAgain)

        // A second tap while the question shows: the photo as the sheet knew it was answered already, so the question stays, about it as it
        // is now, and still nothing goes.
        tap()
        until { askedAgain == 2 }
        assertTrue(asTheyAre.question!!.changed)
        assertEquals(setOf(changed), asTheyAre.question!!.files.stamps.values.toSet())
        assertEquals(null, sends.states[address])
        assertEquals(emptyList<String>(), left)
        assertTrue(sent.isEmpty())

        // The answer sends it, once, with the photo as it is now; taps meanwhile send nothing more.
        asTheyAre.answer(asTheyAre.question!!, share = true)
        until { sent.size == 1 }
        assertTrue(changed in sent.single().chosen)
        tap()
        tap()
        delay(200)
        assertEquals(1, sent.size)
        assertEquals(2, askedAgain)
        assertEquals(null, asTheyAre.question)

        delivered.complete(Unit)
        until { sends.states[address] == SendState.Opened }
        assertEquals(emptyList<String>(), left)
    }

    @Test
    fun `a map made ready to send is in a file of its own`() = runBlocking<Unit> {
        val bundle = preparedBundle(folder) { output -> output.write("zip".toByteArray()) }
        assertEquals("zip", bundle.readText())
        assertEquals(listOf(bundle.name), left)
    }

    @Test
    fun `a map whose making is cancelled leaves nothing behind, even once it's written`() = runBlocking<Unit> {
        // The sheet closes while the zip is being written, and writing it then ends as it would.
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)

        val making = launch(Dispatchers.IO) {
            preparedBundle(folder) { output ->
                writing.countDown()
                release.await(5, TimeUnit.SECONDS)
                output.write("zip".toByteArray())
            }

            fail("A cancelled map isn't handed back")
        }

        assertTrue(writing.await(5, TimeUnit.SECONDS))
        making.cancel()
        release.countDown()
        withTimeout(5_000) { making.join() }
        assertEquals(emptyList<String>(), left)
    }

    @Test
    fun `a map whose making fails, in any way, leaves nothing behind`() = runBlocking<Unit> {
        listOf(IOException("No room"), IllegalStateException("A private map locked")).forEach { failure ->
            try {
                preparedBundle(folder) { output ->
                    output.write("half a zip".toByteArray())
                    throw failure
                }

                fail("$failure")
            } catch (thrown: Exception) {
                assertEquals(failure.message, thrown.message)
            }

            assertEquals(emptyList<String>(), left)
        }
    }

    @Test
    fun `a map's file is cleared away whatever stops its send`() = runBlocking<Unit> {
        val sent = CompletableDeferred<File>()

        // As the sheet sends it: deleted once sending ends, here as the sheet closes while the code shows.
        val sending = launch {
            val bundle = preparedBundle(folder) { output -> output.write("zip".toByteArray()) }

            try {
                sent.complete(bundle)
                awaitCancellation()
            } finally {
                bundle.delete()
            }
        }

        assertTrue(sent.await().isFile)
        sending.cancelAndJoin()
        assertEquals(emptyList<String>(), left)
    }

    @Test
    fun `maps an earlier send left, when Orbit was stopped halfway, are cleared away by the next`() = runBlocking<Unit> {
        folder.mkdirs()
        val old = File(folder, "map1.zip").apply { writeText("old") }
        old.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
        val recent = File(folder, "map2.zip").apply { writeText("another send's") }

        val bundle = preparedBundle(folder) { output -> output.write("zip".toByteArray()) }
        assertEquals(setOf(recent.name, bundle.name), left.toSet())
    }
}
