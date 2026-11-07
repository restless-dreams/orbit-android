package com.hyuchia.orbit.journeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Orbit.WELCOME
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.addIdea
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.closeWithBack
import com.hyuchia.orbit.journeys.Orbit.described
import com.hyuchia.orbit.journeys.Orbit.drawn
import com.hyuchia.orbit.journeys.Orbit.home
import com.hyuchia.orbit.journeys.Orbit.label
import com.hyuchia.orbit.journeys.Orbit.onTheMap
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.search
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A map's everyday journeys on the build installed, each from a fresh install: an idea added on the map, edited,
 * found with the map's search, and there again once Orbit starts anew; and a panel that stays open as the phone
 * turns on its side and back.
 */
@RunWith(AndroidJUnit4::class)
class MapJourneys {
    @get:Rule
    val captures = CapturesFailures()

    @Before
    fun startFresh() {
        clear()
        start()
        home()
    }

    @After
    fun upright() {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Test
    fun anIdeaAddedOnTheMapIsEditedFoundAndThereAgainAfterARestart() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        addIdea(WELCOME, "Pasteis de nata")

        // Its editor renames the map's center, the map's title with it.
        described("Edit $WELCOME").click()
        shows(By.clazz("android.widget.EditText").text(WELCOME).pkg(ORBIT), "the editor's text").text = "Trip to Lisbon"
        closeWithBack(By.text("The map's center").pkg(ORBIT), "the editor")
        drawn("Trip to Lisbon")

        search("Pasteis de nata")

        // Back on Home, under its new name; then Orbit starts anew, and the map holds both changes.
        described("Back to your maps").click()
        label("👋 Trip to Lisbon")
        shell("am force-stop $ORBIT")
        start()
        openMap("👋 Trip to Lisbon")
        onTheMap("Pasteis de nata")
    }

    @Test
    fun aPanelStaysOpenAsThePhoneTurnsOnItsSideAndBack() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        described("Outline").click()
        label("Outline")

        // On its side, a phone is too short for a sheet: the outline goes beside the map.
        device.setOrientationLeft()
        described("Close")
        label("Outline")
        drawn(WELCOME)

        // Upright again, it's a sheet over the map, which TalkBack can't reach behind it until it closes.
        device.setOrientationNatural()
        described("Close sheet")
        label("Outline")

        closeWithBack(By.clazz("android.widget.TextView").text("Outline").pkg(ORBIT), "the outline")
        drawn(WELCOME)
    }
}
