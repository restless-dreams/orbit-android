package com.hyuchia.orbit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class UpdatesTest {
    private val folder: File = Files.createTempDirectory("orbit-updates").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder, "updates.preferences_pb") })

    private var now = 1_760_000_000_000L
    private var enabled = true

    /** What GitHub answers next: its text, null for no release yet, or a failure to throw. */
    private var answer: () -> String? = { release("v0.1.2") }

    private var asked = 0

    private val updates = Updates(
        store = store,
        enabled = { enabled },
        address = "https://api.github.com/repos/restless-dreams/orbit-android/releases/latest",
        current = "0.1.1",
        clock = { now },
        io = Dispatchers.Unconfined,
        fetch = { address ->
            assertEquals("https://api.github.com/repos/restless-dreams/orbit-android/releases/latest", address)
            asked++
            answer()
        },
    )

    @After
    fun cleanUp() {
        // The store's work ends before its folder goes, rather than finding it gone on a thread of its own.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        folder.deleteRecursively()
    }

    /** GitHub's description of a release, with more than Orbit reads. */
    private fun release(tag: String, page: String = "https://github.com/restless-dreams/orbit-android/releases/tag/$tag", draft: Boolean = false, prerelease: Boolean = false) = """
        {
          "url": "https://api.github.com/repos/restless-dreams/orbit-android/releases/1",
          "html_url": "$page",
          "id": 1,
          "tag_name": "$tag",
          "name": "Orbit for Android ${tag.removePrefix("v")}",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-10-20T12:00:00Z",
          "assets": [
            {
              "name": "orbit-${tag.removePrefix("v")}.apk",
              "size": 9398037,
              "digest": "sha256:0f6d80e9d258d7f03260fbccb8d35d321aab12650da53f58481fa7dc0e312c59",
              "browser_download_url": "https://github.com/restless-dreams/orbit-android/releases/download/$tag/orbit.apk"
            }
          ],
          "body": "What's new"
        }
    """.trimIndent()

    @Test
    fun `versions are plain numbers, and later ones are newer`() {
        assertEquals(listOf(0, 1, 10), versionNumbers("0.1.10"))
        assertNull(versionNumbers("0.2.0-beta.1"))
        assertNull(versionNumbers("v0.1.1"))
        assertNull(versionNumbers("+1.0"))
        assertNull(versionNumbers(""))
        assertNull(versionNumbers("1.2.3.4.5"))

        assertTrue(isNewer("0.1.2", "0.1.1"))
        assertTrue(isNewer("0.1.10", "0.1.9"))
        assertTrue(isNewer("0.2", "0.1.9"))
        assertTrue(isNewer("1.0.0", "0.9.9"))
        assertFalse(isNewer("0.1.1", "0.1.1"))
        assertFalse(isNewer("0.1.1.0", "0.1.1"))
        assertFalse(isNewer("0.1.0", "0.1.1"))
        assertFalse(isNewer("nightly", "0.1.1"))
        assertFalse(isNewer("0.2.0-alpha.2", "0.2.0-alpha.1"))
    }

    @Test
    fun `a copy from a pre-release hears about the release, never about other pre-releases`() {
        assertTrue(isNewer("0.2.0", "0.2.0-alpha.1"))
        assertTrue(isNewer("0.2.1", "0.2.0-alpha.1"))
        assertFalse(isNewer("0.1.9", "0.2.0-alpha.1"))
        assertNull(parseRelease(release("v0.2.0-alpha.2")))
        assertNull(parseRelease(release("v0.2.0", prerelease = true)))
    }

    @Test
    fun `a look is due a day after the last one, or when the clock went back`() {
        assertTrue(isDue(checkedAt = 0, now = now))
        assertFalse(isDue(checkedAt = now, now = now + 23 * 3_600_000L))
        assertTrue(isDue(checkedAt = now, now = now + 24 * 3_600_000L))
        assertTrue(isDue(checkedAt = now, now = now - 1))
    }

    @Test
    fun `only published releases with a version and a secure page are offered`() {
        assertEquals(Release("0.1.2", "https://github.com/restless-dreams/orbit-android/releases/tag/v0.1.2"), parseRelease(release("v0.1.2")))
        assertEquals("0.1.2", parseRelease(release("0.1.2"))?.version)
        assertNull(parseRelease(release("v0.1.2", draft = true)))
        assertNull(parseRelease(release("v0.2.0", prerelease = true)))
        assertNull(parseRelease(release("nightly")))
        assertNull(parseRelease(release("v0.1.2", page = "http://example.com/orbit.apk")))
        // Only GitHub's page of Orbit's releases, whatever else the answer points to.
        assertNull(parseRelease(release("v0.1.2", page = "https://example.com/orbit.apk")))
        assertNull(parseRelease(release("v0.1.2", page = "https://github.com.example.com/restless-dreams/orbit-android/releases/tag/v0.1.2")))
        assertNull(parseRelease(release("v0.1.2", page = "https://github.com/someone-else/orbit-android/releases/tag/v0.1.2")))
        assertNull(parseRelease("""{"message": "Not Found"}"""))
        assertNull(parseRelease("<html>Unicorn!</html>"))
    }

    @Test
    fun `a look keeps the latest release, and announces it until it's put away`() = runBlocking {
        updates.checkIfDue()
        val state = updates.state.first()

        assertEquals(1, asked)
        assertEquals(Release("0.1.2", "https://github.com/restless-dreams/orbit-android/releases/tag/v0.1.2"), state.latest)
        assertEquals(now, state.checkedAt)
        assertFalse(state.failed)
        assertEquals("0.1.2", updates.announced(state)?.version)

        updates.dismiss("0.1.2")
        val dismissed = updates.state.first()

        assertNull(updates.announced(dismissed))
        assertEquals("0.1.2", updates.newer(dismissed)?.version)

        answer = { release("v0.1.3") }
        now += 24 * 3_600_000L
        updates.checkIfDue()

        assertEquals("0.1.3", updates.announced(updates.state.first())?.version)
    }

    @Test
    fun `it looks at most once a day, and never when it's off`() = runBlocking {
        updates.checkIfDue()
        now += 3_600_000L
        updates.checkIfDue()

        assertEquals(1, asked)

        now += 24 * 3_600_000L
        enabled = false
        updates.checkIfDue()

        assertEquals(1, asked)

        updates.check()

        assertEquals(2, asked)
    }

    @Test
    fun `a build without an address never looks`() = runBlocking {
        val inert = Updates(store = store, enabled = { true }, address = "", current = "0.1.1", fetch = { error("asked") })
        inert.checkIfDue()

        assertFalse(inert.available)
        assertEquals(0L, inert.state.first().checkedAt)
    }

    @Test
    fun `the same version or an older one is nothing new`() = runBlocking {
        answer = { release("v0.1.1") }
        updates.check()

        assertNull(updates.newer(updates.state.first()))

        answer = { release("v0.1.0") }
        updates.check()

        assertNull(updates.announced(updates.state.first()))
    }

    @Test
    fun `a failed look keeps the last release found, and no release at all forgets it`() = runBlocking {
        updates.check()
        answer = { throw IOException("No connection") }
        val failed = updates.check()

        assertTrue(failed.failed)
        assertEquals("0.1.2", failed.latest?.version)

        answer = { "<html>Unicorn!</html>" }
        val unreadable = updates.check()

        assertTrue(unreadable.failed)
        assertEquals("0.1.2", unreadable.latest?.version)

        answer = { null }
        val none = updates.check()

        assertFalse(none.failed)
        assertNull(none.latest)
    }

    @Test
    fun `looks that overlap ask once, as two windows showing the home screen at once do`() = runTest {
        // The looks run on the test's clock: each waits its turn where it would go off to ask.
        val io = StandardTestDispatcher(testScheduler)
        var asks = 0
        val updates = Updates(MemoryStore(), enabled = { true }, address = "https://releases", current = "0.1.1", clock = { now }, io = io, fetch = { asks++; release("v0.1.2") })

        val first = launch { updates.checkIfDue() }
        val second = launch { updates.checkIfDue() }
        first.join()
        second.join()

        assertEquals(1, asks)
        assertEquals("0.1.2", updates.state.first().latest?.version)
    }

    @Test
    fun `a look from About whose storage fails says what it found, and never throws`() = runTest {
        val store = MemoryStore().apply { failing = true }
        var asks = 0
        var answer: () -> String? = { release("v0.1.2") }
        val updates = Updates(store, enabled = { true }, address = "https://releases", current = "0.1.1", clock = { now }, io = Dispatchers.Unconfined, fetch = { asks++; answer() })

        val found = updates.check()

        assertEquals(UpdateState(Release("0.1.2", "https://github.com/restless-dreams/orbit-android/releases/tag/v0.1.2"), checkedAt = now), found)
        assertEquals(found, updates.state.first())
        assertEquals("0.1.2", updates.announced(found)?.version)

        // It counts as the day's look while Orbit runs.
        updates.checkIfDue()

        assertEquals(1, asks)

        // One that can't reach the releases says so, and keeps what the last one found.
        answer = { throw IOException("No connection") }
        val failed = updates.check()

        assertTrue(failed.failed)
        assertEquals("0.1.2", failed.latest?.version)

        // Once the storage works again, a look is kept, and what's kept is what shows.
        store.failing = false
        answer = { release("v0.1.3") }
        now += 1_000
        val kept = updates.check()

        assertEquals(UpdateState(Release("0.1.3", "https://github.com/restless-dreams/orbit-android/releases/tag/v0.1.3"), checkedAt = now), kept)
        assertEquals(kept, updates.state.first())
        assertEquals(1, store.writes)
    }
}

/** Updates' storage in memory, which fails to write while `failing`, as a full phone's does. */
private class MemoryStore : DataStore<Preferences> {
    private val preferences = MutableStateFlow(emptyPreferences())

    @Volatile
    var failing = false

    /** How many writes were kept. */
    var writes = 0

    override val data: Flow<Preferences> = preferences

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (failing) {
            throw IOException("No space left on device")
        }

        writes++
        return transform(preferences.value).also { preferences.value = it }
    }
}
