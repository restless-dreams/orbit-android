package com.hyuchia.orbit.journeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Orbit.WELCOME
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.described
import com.hyuchia.orbit.journeys.Orbit.drawn
import com.hyuchia.orbit.journeys.Orbit.home
import com.hyuchia.orbit.journeys.Orbit.label
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.search
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Orbit.stopInTheBackground
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.onThePage
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A private map's screen as Android brings it back after it stopped Orbit's process: having been unlocked counts
 * only while Orbit runs, so it comes back behind the lock, nothing of the map on screen, until the phone's owner
 * unlocks it with the screen lock; then it's where it was.
 */
@RunWith(AndroidJUnit4::class)
class PrivateMapJourney {
    @get:Rule
    val captures = CapturesFailures { shell("locksettings clear --old $PIN") }

    @Before
    fun startFresh() {
        // Private maps lock only on a phone with a screen lock.
        shell("locksettings set-pin $PIN")
        clear()
        start()
        home()
    }

    @Test
    fun aPrivateMapAndroidBringsBackStaysLockedUntilItsOwnerUnlocksIt() {
        described("More options for $WELCOME_CARD").click()
        label("Private").click()
        shows(By.textContains("is private now").pkg(ORBIT), "the map made private")

        // Made private here, it opens without asking while Orbit runs.
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        search("Saturn")

        stopInTheBackground()
        start()

        // The phone asks for its screen lock before anything of the map shows.
        shows(By.text("Unlock private maps"), "the screen lock's prompt")
        assertNull(onThePage("Add an idea to Saturn"))
        shows(By.clazz("android.widget.EditText").focused(true), "the PIN's field")
        shell("input text $PIN")
        device.pressEnter()

        drawn("Saturn")
    }

    private companion object {
        const val PIN = "1234"
    }
}
