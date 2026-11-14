package com.hyuchia.orbit.home

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.MapTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
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
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * The tag a new map starts with, the one whose maps Home shows: never one
 * only private maps that locked since it was picked have, which the new map
 * would carry where anyone sees it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class)
class HomeNewMapTagTest {
    /** The main thread, as Android has one: the view model's work runs on it. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun cleanUp() {
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        Dispatchers.resetMain()
        main.close()
    }

    /** Home's view model, with private maps' tag `classified` picked while they were unlocked, and locked since. */
    private fun pickedThenLocked(): HomeViewModel = runBlocking {
        val container = app.container
        container.maps.load()
        container.maps.add(MapTags.write("# Secret\n", listOf("classified")), isPrivate = true)
        container.privateMaps.lock.screenLockIs(true)
        container.privateMaps.lock.unlock()
        container.shownTag.value = "classified"
        container.privateMaps.lock.lock()
        assertTrue(container.privateMaps.view.value.locked)
        withContext(Dispatchers.Main) { ViewModelProvider.create(store, viewModelFactory { initializer { HomeViewModel(container) } })[HomeViewModel::class] }
    }

    /** Makes a map with `make`, and waits for its id. */
    private fun made(make: ((String) -> Unit) -> Unit): String = runBlocking {
        var created: String? = null
        withContext(Dispatchers.Main) { make { created = it } }

        withTimeout(10_000) {
            while (created == null) {
                delay(20)
            }
        }

        created!!
    }

    /** The tags in map `id`'s text. */
    private fun tags(id: String): List<String> = runBlocking { MapTags.read(app.container.maps.read(id)) }

    @Test
    fun `a new map doesn't start with a tag only locked private maps have`() {
        val model = pickedThenLocked()
        val id = made { model.create("Public map", it) }

        assertFalse(app.container.maps.summary(id)!!.metadata.isPrivate)
        assertEquals(emptyList<String>(), tags(id))
    }

    @Test
    fun `nor does one from a template`() {
        val model = pickedThenLocked()
        val id = made { model.start("# Weekly plan\n\n- Monday\n", it) }

        assertEquals(emptyList<String>(), tags(id))
    }

    @Test
    fun `unlocked again, the tag is shown, and a new map starts with it`() {
        val model = pickedThenLocked()
        app.container.privateMaps.lock.unlock()
        val id = made { model.create("Another secret", it) }

        assertEquals(listOf("classified"), tags(id))
    }
}
