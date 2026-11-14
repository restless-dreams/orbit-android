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
import com.hyuchia.orbit.journeys.Orbit.findOnHome
import com.hyuchia.orbit.journeys.Orbit.home
import com.hyuchia.orbit.journeys.Orbit.label
import com.hyuchia.orbit.journeys.Orbit.onTheMap
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.search
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A map's everyday journeys on the build installed, each from a fresh install: an idea typed on the map with the
 * keyboard's keys, edited, found with the map's search, and there again once Orbit starts anew; a map closed and
 * opened again at once, which has its last idea and takes the next; and a panel that stays open as the phone turns on
 * its side and back.
 */
@RunWith(AndroidJUnit4::class)
class MapJourneys {
    @get:Rule
    val captures = CapturesFailures {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Before
    fun startFresh() {
        clear()
        start()
        home()
    }

    @Test
    fun anIdeaAddedOnTheMapIsEditedFoundAndThereAgainAfterARestart() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        // Typed key by key through the keyboard app, as a keyboard's keys reach the map's page: the other journeys put
        // their words in at once.
        addIdea(WELCOME, "Pasteis de nata", typing = Orbit::typeKeys)

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
    fun aMapClosedAndOpenedAgainAtOnceHasItsLastIdeaAndTakesTheNext() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        addIdea(WELCOME, "Pasteis de nata")

        // Back to Home and straight into the map again, as people do: what they see of the map is its last edit.
        // How fast a phone is decides whether that edit is still being written as the map opens again, so the moment
        // it's written while the map is open is MapViewModelTest's, which holds the write until then.
        described("Back to your maps").click()
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        onTheMap("Pasteis de nata")
        addIdea(WELCOME, "Ginjinha")

        // Both are in the map's file once Home's search finds the map by the second: then Orbit starts anew.
        described("Back to your maps").click()
        findOnHome(WELCOME_CARD, "Ginjinha")
        shell("am force-stop $ORBIT")
        start()
        openMap(WELCOME_CARD)
        onTheMap("Pasteis de nata")
        onTheMap("Ginjinha")
    }

    @Test
    fun aPanelStaysOpenAsThePhoneTurnsOnItsSideAndBack() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        described("Outline").click()
        outlineOpen()

        // On its side, a phone is too short for a sheet, and the outline goes beside the map; a tablet, upright then,
        // is too narrow for it to, and it's a sheet.
        device.setOrientationLeft()
        outlineOpen()

        device.setOrientationNatural()
        outlineOpen()

        closeWithBack(By.clazz("android.widget.TextView").text("Outline").pkg(ORBIT), "the outline")
        drawn(WELCOME)
    }

    /**
     * The outline, open as the screen's shape has it: beside the map, with its own Close, which leaves the map to
     * TalkBack too; or a sheet over the map, which TalkBack can't reach behind until it closes.
     */
    private fun outlineOpen() {
        device.waitForIdle()

        if (panelsBesideTheMap()) {
            described("Close")
            label("Outline")
            drawn(WELCOME)
        } else {
            described("Close sheet")
            label("Outline")
        }
    }

    /**
     * Whether the map's panels open beside it on this screen, the way it's turned now: where the map keeps a phone's
     * width (360 dp) beside a panel (380 dp) on a screen wider than tall, or on a screen 840 dp wide or more.
     */
    private fun panelsBesideTheMap(): Boolean {
        val size = device.displaySizeDp
        return size.x >= 840 || (size.x > size.y && size.x - 381 >= 360)
    }
}
