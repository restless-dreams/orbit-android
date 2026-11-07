package com.hyuchia.orbit.privacy

import android.app.KeyguardManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What says a map was made private names it as private maps are once it's done, not as they were when it began. */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class)
class MakePrivateTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()

    /** A map called "Secret journal", and the phone's screen lock with private maps unlocked. */
    private fun journal(): MapSummary = runBlocking {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        app.container.privateMaps.refreshScreenLock()
        app.container.privateMaps.lock.unlock()
        app.container.maps.load()
        app.container.maps.summary(app.container.maps.create("Secret journal"))!!
    }

    /** Makes `map` private, and what says so. */
    private fun makePrivate(map: MapSummary): List<String> {
        val said = mutableListOf<String>()
        lateinit var change: (MapSummary, Boolean) -> Unit
        compose.setContent { change = rememberMakePrivate { said += it } }
        compose.runOnUiThread { change(map, true) }
        // Saving the map's details runs off the main thread, and CI runs beside other builds.
        compose.waitUntil(timeoutMillis = 30_000) { app.container.maps.summary(map.id)?.metadata?.isPrivate == true }
        compose.waitForIdle()
        return said
    }

    @Test
    fun `a map made private as they lock isn't named in what says so`() {
        val journal = journal()

        // The screen turns off just as its details are written private, before what says so.
        val locking = CoroutineScope(Dispatchers.Unconfined)

        locking.launch {
            app.container.maps.maps.first { maps -> maps.orEmpty().any { it.id == journal.id && it.metadata.isPrivate } }
            app.container.privateMaps.lock.screenOff()
        }

        assertEquals(emptyList<String>(), makePrivate(journal))
        locking.cancel()
    }

    @Test
    fun `made private while they're unlocked, it's named as ever`() {
        assertEquals(listOf("“Secret journal” is private now: it opens with your screen lock"), makePrivate(journal()))
    }
}
