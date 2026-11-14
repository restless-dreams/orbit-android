package com.hyuchia.orbit.journeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Orbit.WELCOME
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.addIdea
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.drawn
import com.hyuchia.orbit.journeys.Orbit.home
import com.hyuchia.orbit.journeys.Orbit.onTheMap
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.search
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Orbit.stopInTheBackground
import com.hyuchia.orbit.journeys.Phone.orbitProcess
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A map's screen as Android brings it back after it stopped Orbit's process in the background, as it does to free
 * memory: the map opens again where it was, in a new process, its page drawn anew, with an idea added the moment
 * before.
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathJourney {
    @get:Rule
    val captures = CapturesFailures()

    @Before
    fun startFresh() {
        clear()
        start()
        home()
    }

    @Test
    fun aMapComesBackWhereItWasAfterAndroidStoppedOrbit() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        // Somewhere in the map, found with its search.
        search("Saturn")

        val before = stopInTheBackground()
        start()

        drawn("Saturn")
        shows(By.text(WELCOME).pkg(ORBIT), "the map's title")
        assertNotEquals(before, orbitProcess())
    }

    @Test
    fun anIdeaAddedRightBeforeAndroidStopsOrbitIsThereWhenItComesBack() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        // Added, and Orbit out of sight and stopped at once: nothing waits for it to be written.
        addIdea(WELCOME, "Pasteis de nata")

        val before = stopInTheBackground()
        start()

        drawn(WELCOME)
        onTheMap("Pasteis de nata")
        assertNotEquals(before, orbitProcess())
    }
}
