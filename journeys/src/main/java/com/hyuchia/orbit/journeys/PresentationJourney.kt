package com.hyuchia.orbit.journeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Orbit.WELCOME
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.described
import com.hyuchia.orbit.journeys.Orbit.drawn
import com.hyuchia.orbit.journeys.Orbit.home
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.eventually
import com.hyuchia.orbit.journeys.Phone.shell
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The welcome map presented on the phone alone, on the build installed, from a fresh install: Present starts it on
 * its first idea, of the eight its path goes through, Next idea goes on to the next, and Stop presenting leaves the
 * map on the idea it reached, to add ideas to again.
 */
@RunWith(AndroidJUnit4::class)
class PresentationJourney {
    @get:Rule
    val captures = CapturesFailures()

    @Before
    fun startFresh() {
        // Android 10 tells of full screen the first time an app goes full screen, over the presentation's controls,
        // until it's dismissed: as on a phone where that was seen once.
        shell("settings put secure immersive_mode_confirmations confirmed")
        clear()
        start()
        home()
    }

    @Test
    fun theWelcomeMapIsPresentedIdeaByIdeaAndLeftAsItWas() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)

        // Present is an icon on a phone, and says its name beside it on a tablet.
        val icon = By.desc("Present").pkg(ORBIT)
        val named = By.text("Present").pkg(ORBIT)
        eventually("the map's Present") { device.hasObject(icon) || device.hasObject(named) }
        (device.findObject(icon) ?: device.findObject(named)).click()

        described("Idea 1 of 8")
        described("Next idea").click()
        described("Idea 2 of 8")

        // Stopped, the map stays on the idea the talk reached, to add ideas to.
        described("Stop presenting").click()
        drawn("Fly around")
    }
}
