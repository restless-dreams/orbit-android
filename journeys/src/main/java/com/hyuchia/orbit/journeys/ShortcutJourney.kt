package com.hyuchia.orbit.journeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Launcher.openOrbitShortcuts
import com.hyuchia.orbit.journeys.Orbit.WELCOME
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.described
import com.hyuchia.orbit.journeys.Orbit.drawn
import com.hyuchia.orbit.journeys.Orbit.focusedField
import com.hyuchia.orbit.journeys.Orbit.label
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Orbit.type
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Orbit's launcher shortcuts, a way into Orbit, on the build installed: a map opened is among those a long press on
 * Orbit's icon shows, named as its card on Home is; renamed there, without being opened again, its shortcut takes the
 * new name; and it opens the map.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutJourney {
    @get:Rule
    val captures = CapturesFailures()

    @Before
    fun startFresh() {
        clear()
        start()
        Orbit.home()
    }

    @Test
    fun aMapsShortcutTakesTheNameItsGivenOnHomeAndOpensIt() {
        // Opened, the welcome map joins Orbit's shortcuts.
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        device.pressBack()
        Orbit.home()
        openOrbitShortcuts()
        shows(By.text(WELCOME_CARD).pkg(Launcher.name), "the welcome map's shortcut")

        // Renamed from its card on Home, which keeps its icon.
        start()
        Orbit.home()
        described("More options for $WELCOME_CARD").click()
        label("Rename").click()
        focusedField()
        type(RENAMED)
        label("Rename").click()
        label(RENAMED_CARD)

        openOrbitShortcuts()
        shows(By.text(RENAMED_CARD).pkg(Launcher.name), "the map's shortcut, renamed").click()
        drawn(RENAMED)
    }

    private companion object {
        const val RENAMED = "Trip to Lisbon"
        const val RENAMED_CARD = "👋 $RENAMED"
    }
}
