package com.hyuchia.orbit.map

import com.hyuchia.orbit.FileWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PresentationTest {
    private val rehearsed = mapOf("intro" to 30, "problem" to 60, "plan" to 20)
    private val words = FileWords(Locale.US)

    private fun presentation(index: Int, stepStartedAt: Long, firstIndex: Int = 0, planned: Map<String, Int>? = rehearsed) = Presentation(
        order = listOf("intro", "problem", "plan"),
        index = index,
        startedAt = 0,
        stepStartedAt = stepStartedAt,
        planned = planned,
        firstIndex = firstIndex,
    )

    @Test
    fun anIdeaPutsThePresenterBehindOnlyOnceItRunsOverItsRehearsedTime() {
        // The intro took 40 seconds of its 30.
        val onProblem = presentation(index = 1, stepStartedAt = 40_000)
        assertEquals(10, onProblem.behind(70_000))
        // 70 seconds into the problem's 60: 10 more.
        assertEquals(20, onProblem.behind(110_000))
    }

    @Test
    fun movingFasterThanTheRehearsalIsBeingAhead() {
        // The intro and the problem took 50 seconds of their 90.
        val onPlan = presentation(index = 2, stepStartedAt = 50_000)
        assertEquals(-40, onPlan.behind(50_000))
        assertEquals(-40, onPlan.behind(60_000))
    }

    @Test
    fun thePaceCountsFromWhereThePresentationBegan() {
        val fromProblem = presentation(index = 2, stepStartedAt = 60_000, firstIndex = 1)
        assertEquals(0, fromProblem.behind(60_000))
    }

    @Test
    fun withoutARehearsalThereIsNoPace() {
        assertNull(presentation(index = 0, stepStartedAt = 0, planned = null).behind(5_000))
        assertNull(presentation(index = 0, stepStartedAt = 0, planned = mapOf("gone" to 10)).behind(5_000))
    }

    @Test
    fun theIdeaOnScreenCountsDownItsRehearsedTime() {
        val onIntro = presentation(index = 0, stepStartedAt = 0)
        assertEquals("0:18 left", onIntro.stepTime(12_000, words))
        assertEquals("0:05 over", onIntro.stepTime(35_000, words))
        assertEquals("0:12", presentation(index = 0, stepStartedAt = 0, planned = null).stepTime(12_000, words))
        val spanish = FileWords(Locale.forLanguageTag("es-MX"))
        assertEquals("Quedan 0:18", onIntro.stepTime(12_000, spanish))
        assertEquals("0:05 de más", onIntro.stepTime(35_000, spanish))
    }

    @Test
    fun thePaceIsSaidInWords() {
        assertEquals("0:10 behind", presentation(index = 1, stepStartedAt = 40_000).pace(70_000, words))
        // A few seconds either way is keeping to it.
        assertEquals("On pace", presentation(index = 1, stepStartedAt = 32_000).pace(62_000, words))
        assertEquals("0:40 ahead", presentation(index = 2, stepStartedAt = 50_000).pace(50_000, words))
        assertNull(presentation(index = 0, stepStartedAt = 0, planned = null).pace(5_000, words))
    }

    @Test
    fun timesReadLikeAClock() {
        assertEquals("0:42", clock(42))
        assertEquals("12:05", clock(725))
        assertEquals("1:02:03", clock(3_723))
    }
}
