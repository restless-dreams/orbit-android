package com.hyuchia.orbit.capture

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.PrivateLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Locale

/**
 * What quick capture says once the ideas are in, or not: as private maps
 * are by then, which can be locked since the ideas began to go.
 */
@RunWith(AndroidJUnit4::class)
class CaptureIntoTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val lock = PrivateLock(clock = { 0 })

    private fun map(title: String, private: Boolean) = MapSummary(title.lowercase(), MapOutline(title, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    private val journal = map("Secret journal", private = true)
    private val groceries = map("Groceries", private = false)

    @Test
    fun `ideas added to a private map that locked meanwhile don't name it`() = runTest {
        lock.unlock()
        val adding = CompletableDeferred<Unit>()
        val outcome = async { captureInto(journal, ideas = 3, resources, { lock.view.value.hides(journal) }) { adding.await() } }
        runCurrent()

        // The screen turns off while the page adds them.
        lock.screenOff()
        adding.complete(Unit)
        assertEquals(CaptureOutcome.Added("Added 3 ideas"), outcome.await())

        // One idea, the same.
        assertEquals(CaptureOutcome.Added("Added your idea"), captureInto(journal, ideas = 1, resources, { lock.view.value.hides(journal) }) {})
    }

    @Test
    fun `what went wrong adding to a private map that locked meanwhile goes unsaid, as the map leaves the list`() = runTest {
        lock.unlock()
        val adding = CompletableDeferred<Unit>()
        val outcome = async { captureInto(journal, ideas = 2, resources, { lock.view.value.hides(journal) }) { adding.await() } }
        runCurrent()

        lock.lock()
        adding.completeExceptionally(CaptureException(CaptureFailure.NotWritten))
        assertEquals(CaptureOutcome.NotAdded(null), outcome.await())
    }

    @Test
    fun `unlocked, or not private, the map is named as ever`() = runTest {
        lock.unlock()
        assertEquals(CaptureOutcome.Added("Added 2 ideas to Secret journal"), captureInto(journal, ideas = 2, resources, { lock.view.value.hides(journal) }) {})
        assertEquals(CaptureOutcome.Added("Added to Secret journal"), captureInto(journal, ideas = 1, resources, { lock.view.value.hides(journal) }) {})

        lock.lock()
        assertEquals(CaptureOutcome.Added("Added to Groceries"), captureInto(groceries, ideas = 1, resources, { lock.view.value.hides(groceries) }) {})

        val failed = captureInto(groceries, ideas = 1, resources, { lock.view.value.hides(groceries) }) { throw IOException("No room left") }
        assertEquals(CaptureOutcome.NotAdded("Orbit couldn't add to Groceries. No room left. Your ideas are still here."), failed)
        val missing = captureInto(groceries, ideas = 1, resources, { lock.view.value.hides(groceries) }) { throw CaptureException(CaptureFailure.Missing) }
        assertEquals(CaptureOutcome.NotAdded("Orbit couldn't add to Groceries. The map isn't here anymore. Your ideas are still here."), missing)
    }

    @Test
    fun `text with no idea in it says so, in the app's language, rather than say ideas were added`() = runTest {
        val said = listOf("en", "es", "pt-BR").map { language ->
            val words = ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(language)) }).resources
            captureInto(groceries, ideas = 2, words, { false }) { throw CaptureException(CaptureFailure.NoIdeas) }
        }

        assertEquals(
            listOf("Orbit found no ideas in this text.", "Orbit no encontró ideas en este texto.", "O Orbit não encontrou ideias neste texto.").map { CaptureOutcome.NotAdded(it) },
            said,
        )
    }
}
