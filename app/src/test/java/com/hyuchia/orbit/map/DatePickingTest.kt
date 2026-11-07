package com.hyuchia.orbit.map

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.IdeaDate
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Material's pickers for an idea's date: the time's opens on its dial, turns
 * to typing and back, and stays as it was through a screen made again, as
 * Android makes it for a new language or font size.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class DatePickingTest {
    @get:Rule
    val compose = createComposeRule()

    private val restoration = StateRestorationTester(compose)
    private val flights = IdeaDate.parse("2026-11-20 18:30")!!

    @Test
    fun `the time is typed or set on the dial, and stays typed through a screen made again`() {
        var picked: IdeaDate? = null
        restoration.setContent { OrbitTheme(dark = false) { IdeaTimePicker(flights, onPick = { picked = it }, onDismiss = {}) } }

        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Switch to clock mode").assertExists()

        compose.onNodeWithText("Done").performClick()
        assertEquals(flights, picked)
    }

    @Test
    fun `a time can be taken off, leaving the day`() {
        var picked: IdeaDate? = null
        compose.setContent { OrbitTheme(dark = false) { IdeaTimePicker(flights, onPick = { picked = it }, onDismiss = {}) } }

        compose.onNodeWithText("No time").performClick()
        assertEquals(IdeaDate(flights.day), picked)
    }
}
