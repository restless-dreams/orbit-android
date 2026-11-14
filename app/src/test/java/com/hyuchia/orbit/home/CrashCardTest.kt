package com.hyuchia.orbit.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.CrashLog
import com.hyuchia.orbit.data.CrashReport
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** How long the card has to answer a tap, before the test fails. */
private const val WAIT_MILLISECONDS = 5_000L

/** The home screen's card about a crash: Delete takes the report and the card, or says it couldn't and both stay. */
@RunWith(AndroidJUnit4::class)
class CrashCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val folder: File = Files.createTempDirectory("orbit-crash-card").toFile()
    private val crashes = CrashLog(folder, "Orbit 1.0 (10), debug build")

    private var done = false
    private val said = mutableListOf<String>()

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun show(report: CrashReport) {
        compose.setContent { OrbitTheme(dark = false) { CrashCard(report, onDone = { done = true }, say = { said += it }, crashes = crashes) } }
        compose.waitForIdle()
    }

    /** The latest report, as one that won't delete: storage that fails, or a file that won't go. */
    private fun undeletable(): CrashReport {
        crashes.record("main", IllegalStateException("broken"))
        val kept = crashes.unseen()!!

        return CrashReport(
            kept.time,
            object : File(kept.file.path) {
                override fun delete(): Boolean = false
            },
        )
    }

    @Test
    fun `a report deleted takes its card with it, without a word`() {
        crashes.record("main", IllegalStateException("broken"))
        val report = crashes.unseen()!!
        show(report)

        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(WAIT_MILLISECONDS) { done }

        assertFalse(report.file.exists())
        assertNull(crashes.unseen())
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun `a report that won't delete says so, and stays with its card`() {
        val report = undeletable()
        show(report)

        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(WAIT_MILLISECONDS) { said.isNotEmpty() }

        assertEquals(listOf("Orbit couldn’t delete the crash report. It’s still on this device."), said)
        assertFalse(done)
        assertTrue(report.file.exists())
        assertEquals(report.time, crashes.unseen()?.time)
    }

    @Test @Config(qualifiers = "es")
    fun `it says so in the app's language`() {
        show(undeletable())

        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(WAIT_MILLISECONDS) { said.isNotEmpty() }

        assertEquals(listOf("Orbit no pudo eliminar el informe del fallo. Sigue en este dispositivo."), said)
    }
}
