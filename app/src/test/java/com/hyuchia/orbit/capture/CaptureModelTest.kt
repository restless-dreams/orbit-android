package com.hyuchia.orbit.capture

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Quick capture's add outlasts its sheet made again, as when the phone
 * turns mid-add: it goes on, and once the ideas are in, nothing adds them
 * again, however many times Add is pressed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureModelTest {
    private val main = StandardTestDispatcher()
    private val groceries = MapSummary("groceries", MapOutline("Groceries", branches = 0, ideas = 0), 0, MapMetadata(), "", "")

    /** Each add the model asked for, waiting for the test to say how it went. */
    private val asked = mutableListOf<CompletableDeferred<CaptureOutcome>>()

    /** Where the sheet's activity keeps its model, which a sheet made again finds it in. */
    private val store = ViewModelStore()

    private val factory = viewModelFactory { initializer { CaptureModel { _, _, _ -> CompletableDeferred<CaptureOutcome>().also { asked += it }.await() } } }

    /** The model a sheet gets, as quick capture's sheet asks for it. */
    private fun sheet(): CaptureModel = ViewModelProvider.create(store, factory)[CaptureModel::class]

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun cleanUp() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `an add under way goes on through the sheet made again, and Add again adds nothing more`() = runTest(main) {
        val model = sheet()
        model.add(groceries, 2, "Milk\nEggs")
        runCurrent()
        assertTrue(model.adding)

        // The phone turns: the sheet made again finds the same add, still going, and pressing Add there adds nothing.
        val again = sheet()
        assertTrue(again.adding)
        again.add(groceries, 2, "Milk\nEggs")
        runCurrent()
        assertEquals(1, asked.size)

        asked.single().complete(CaptureOutcome.Added("Added 2 ideas to Groceries"))
        runCurrent()
        assertEquals("Added 2 ideas to Groceries", again.added)

        // In, they're in: Add, from a sheet made again before it closes, adds nothing.
        again.add(groceries, 2, "Milk\nEggs")
        runCurrent()
        assertEquals(1, asked.size)
    }

    @Test
    fun `ideas that couldn't go are still there, to try again, and what went wrong goes as they're tried`() = runTest(main) {
        val model = sheet()
        model.add(groceries, 1, "Milk")
        runCurrent()
        asked.single().complete(CaptureOutcome.NotAdded("Orbit couldn't add to Groceries. Your ideas are still here."))
        runCurrent()

        assertFalse(model.adding)
        assertNull(model.added)
        assertEquals("Orbit couldn't add to Groceries. Your ideas are still here.", model.problem)

        model.add(groceries, 1, "Milk")
        runCurrent()
        assertNull(model.problem)
        assertEquals(2, asked.size)
    }
}
