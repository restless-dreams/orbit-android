package com.hyuchia.orbit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.FileStamp
import com.hyuchia.orbit.data.Uncleaned
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A way out that outlives its screen, as a presentation does, keeps the
 * presenter's answers about files that can't go clean, but not a question
 * still open: that goes with the screen that asked it, with what its answer
 * would have started there, so a screen made again (a new language, say)
 * asks afresh and starts its own way out.
 */
@RunWith(AndroidJUnit4::class)
class AsTheyAreTest {
    @get:Rule
    val compose = createComposeRule()

    /** The street's picture as Orbit found it couldn't clean it, and another put in its place under its name since, the same size. */
    private val asked = FileStamp("/maps/lisbon/media/street.heic", 2_048, 1_000, 1_000, "1:10")

    private val replaced = FileStamp("/maps/lisbon/media/street.heic", 2_048, 1_000, 2_000, "1:11")

    private val street = Uncleaned(pictures = setOf("street.heic"), stamps = mapOf("street.heic" to asked))

    /** Which screen is up: a new number is the screen made again, with the same choice. */
    private var screen by mutableIntStateOf(1)

    /** Each screen's questions, by its number. */
    private val screens = mutableMapOf<Int, AsTheyAre>()

    /** What each screen started, and with which files as they are. */
    private val started = mutableListOf<Pair<Int, Set<FileStamp>>>()

    private fun show(choice: AsTheyAreChoice) {
        compose.setContent {
            key(screen) {
                screens[screen] = rememberAsTheyAre(choice)
            }
        }
    }

    private fun ask(on: Int, files: Uncleaned = street) {
        compose.runOnIdle { screens.getValue(on).then(files) { asTheyAre -> started += on to asTheyAre.chosen } }
    }

    @Test
    fun `a question still open goes with its screen, and the screen made again asks afresh`() {
        val choice = AsTheyAreChoice()
        show(choice)
        ask(on = 1)
        compose.onNodeWithText("Share it as it is").assertExists()

        compose.runOnIdle { screen = 2 }
        compose.onNodeWithText("Share it as it is").assertDoesNotExist()
        compose.onNodeWithText("Leave it out").assertDoesNotExist()

        ask(on = 2)
        compose.onNodeWithText("Share it as it is").performClick()

        compose.runOnIdle {
            assertEquals(listOf(2 to setOf(asked)), started)
            assertEquals(setOf(asked), choice.chosen)
        }
    }

    @Test
    fun `an answer holds through a screen made again`() {
        val choice = AsTheyAreChoice()
        show(choice)
        ask(on = 1)
        compose.onNodeWithText("Leave it out").performClick()

        compose.runOnIdle { screen = 2 }
        ask(on = 2)
        compose.onNodeWithText("Leave it out").assertDoesNotExist()

        compose.runOnIdle {
            assertEquals(listOf(1 to emptySet<FileStamp>(), 2 to emptySet()), started)
            assertEquals(emptySet<FileStamp>(), choice.chosen)
        }
    }

    @Test
    fun `an answer is about that very file, and another put in its place under its name is asked about again`() {
        // As the review's probe found: "Share it as it is" for one file held for another put in its place, which went unasked.
        val choice = AsTheyAreChoice()
        show(choice)
        ask(on = 1)
        compose.onNodeWithText("Share it as it is").performClick()
        compose.runOnIdle { assertEquals(listOf(1 to setOf(asked)), started) }

        // The same name, another file: nothing starts until the person answers for it.
        ask(on = 1, files = street.copy(stamps = mapOf("street.heic" to replaced)))
        compose.onNodeWithText("Share it as it is").assertExists()
        compose.runOnIdle { assertEquals(1, started.size) }

        compose.onNodeWithText("Leave it out").performClick()

        compose.runOnIdle {
            // What it started with carries the first file's answer, never the second's.
            assertEquals(listOf(1 to setOf(asked), 1 to setOf(asked)), started)
            assertEquals(setOf(asked, replaced), choice.asked)
        }
    }

    @Test
    fun `a way out that stopped as a file changed asks again about it alone, saying it changed`() {
        val choice = AsTheyAreChoice()
        show(choice)
        val tram = FileStamp("/maps/lisbon/media/video-tram.mp4", 4_096, 1_000, 1_000, "1:12")
        val both = Uncleaned(pictures = setOf("street.heic"), videos = setOf("video-tram.mp4"), stamps = mapOf("street.heic" to asked, "video-tram.mp4" to tram))
        ask(on = 1, files = both)
        compose.onNodeWithText("Share them as they are").performClick()

        // The street's picture changed as the way out was made: asked about again, alone, and the question says why.
        compose.runOnIdle { screens.getValue(1).askAgain(both.copy(stamps = mapOf("street.heic" to replaced, "video-tram.mp4" to tram))) { asTheyAre -> started += 1 to asTheyAre.chosen } }
        compose.onNodeWithText("It changed after you answered, so Orbit asks again.").assertExists()
        compose.onNodeWithText("Share it as it is").performClick()
        compose.runOnIdle { assertEquals(listOf(1 to setOf(asked, tram), 1 to setOf(asked, tram, replaced)), started) }

        // Nothing changed since: it starts again at once, without a question.
        compose.runOnIdle { screens.getValue(1).askAgain(both.copy(stamps = mapOf("street.heic" to replaced, "video-tram.mp4" to tram))) { asTheyAre -> started += 1 to asTheyAre.chosen } }
        compose.onNodeWithText("It changed after you answered, so Orbit asks again.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, started.size) }
    }

    @Test
    fun `a first question never says the file changed, even asked from a way out that stopped`() {
        // A tap before the screen knew of the files starts a way out that meets one no one was asked about: it stops, and asks.
        show(AsTheyAreChoice())
        compose.runOnIdle { screens.getValue(1).askAgain(street) { asTheyAre -> started += 1 to asTheyAre.chosen } }

        compose.onNodeWithText("Share it as it is").assertExists()
        compose.onNodeWithText("It changed after you answered, so Orbit asks again.").assertDoesNotExist()
    }
}
