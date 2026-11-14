package com.hyuchia.orbit.journeys

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Launcher.addOrbitFromWidgetList
import com.hyuchia.orbit.journeys.Launcher.openWidgetList
import com.hyuchia.orbit.journeys.Launcher.orbitWidgetIds
import com.hyuchia.orbit.journeys.Launcher.widgetShowing
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.closeWithBack
import com.hyuchia.orbit.journeys.Orbit.described
import com.hyuchia.orbit.journeys.Orbit.findOnHome
import com.hyuchia.orbit.journeys.Orbit.label
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.eventually
import com.hyuchia.orbit.journeys.Phone.gone
import com.hyuchia.orbit.journeys.Phone.longPress
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Orbit's widget put on the home screen each way people put it there, on the build installed, from a fresh install
 * with two more maps: added from a map's menu, it shows that map without asking; added from the launcher's list, it
 * asks which map to show, stays, and shows that one; and its settings change it.
 *
 * The list's widget is added with the launcher's own Add, not dragged in: with Android's animations off, as the
 * scripts leave them and "Remove animations" does, Pixel's launcher (Android 15 and later) drops a widget dragged
 * in before the chooser of any app's widget can start, and leaves its settings button behind, which then closes the
 * launcher. Its Add button, and the list's Add for TalkBack, ask Orbit either way.
 *
 * The widgets stay on the home screen afterwards, until Orbit is uninstalled, as the scripts do before they install
 * a build: the launchers' Remove for TalkBack doesn't always take one away when it's asked from a test.
 */
@RunWith(AndroidJUnit4::class)
class WidgetJourneys {
    @get:Rule
    val captures = CapturesFailures()

    @Before
    fun startFresh() {
        clear()
        start()
        Orbit.home()

        // Two more maps, which a new widget shows only when it's given one: the welcome map is pinned, and a widget
        // without a map shows the pinned one.
        described("More options for $WELCOME_CARD").click()
        label("Duplicate").click()
        label("2 maps")
        findOnHome(COPY_CARD, "copy")
        described("More options for $COPY_CARD").click()
        label("Duplicate").click()
        label(SECOND_COPY_CARD)
        closeWithBack(By.clazz("android.widget.EditText").text("copy").pkg(ORBIT), "Home's search")
        label("3 maps")
    }

    @Test
    fun theMapWidgetShowsTheMapItWasAddedForOrTheOneChosenAsItsPlaced() {
        val before = orbitWidgetIds()

        // From a map's menu, offered where the launcher can put a widget on the home screen when an app asks, as both
        // emulators' can. Pixel's launcher says "Add to home screen"; Launcher3 on Android 10, "Add automatically".
        // The newest copy is the search's first card, on screen on the smallest phone too.
        findOnHome(SECOND_COPY_CARD, "copy")
        described("More options for $SECOND_COPY_CARD").click()
        label("Add to home screen").click()
        val add = By.clazz("android.widget.Button").text(Pattern.compile("add to home screen|add automatically", Pattern.CASE_INSENSITIVE)).pkg(Launcher.name)
        shows(add, "the launcher's Add").click()
        gone(add, "the launcher's Add")

        // The launcher goes back to Orbit, without asking which map: the widget shows the one it was added from.
        described("More options for $SECOND_COPY_CARD")
        assertFalse("Orbit asked which map a widget for $SECOND_COPY_CARD shows", device.hasObject(By.text("Choose a map").pkg(ORBIT)))
        Launcher.home()
        widgetShowing(SECOND_COPY_CARD)
        val pinned = orbitWidgetIds() - before
        assertEquals("Orbit's widgets the launcher added from the map's menu", 1, pinned.size)

        // From the launcher's list, the launcher asks Orbit which map it shows, and keeps it once one is picked.
        openWidgetList()
        addOrbitFromWidgetList()
        shows(By.text("Choose a map").pkg(ORBIT), "Orbit asking which map the widget shows")
        label(COPY_CARD).click()
        val listed = widgetShowing(COPY_CARD).visibleBounds
        val added = orbitWidgetIds() - before - pinned
        assertEquals("Orbit's widgets the launcher added from its list", 1, added.size)

        // Its settings, in the launcher on Android 12 and later, ask again, with the map it shows marked, and change it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Held until the launcher lifts the widget from its place, and let go there: its settings button shows.
            val placed = By.desc(Launcher.WIDGET).hasDescendant(By.text(COPY_CARD).pkg(ORBIT))

            longPress(listed.centerX(), listed.centerY(), "the launcher lifting the widget") {
                device.findObjects(placed).none { it.visibleBounds == listed }
            }

            shows(By.desc("Change widget settings").pkg(Launcher.name), "the widget's settings").click()
            shows(By.text("Choose a map").pkg(ORBIT), "Orbit asking which map the widget shows")
            described("Shown now")
            label(WELCOME_CARD).click()

            eventually("the widget from the list to show $WELCOME_CARD") {
                device.findObjects(By.desc(Launcher.WIDGET).hasDescendant(By.text(WELCOME_CARD).pkg(ORBIT))).any { it.visibleBounds == listed }
            }

            assertEquals("Orbit's widgets after changing one's map", pinned + added, orbitWidgetIds() - before)
        }
    }

    private companion object {
        /** The welcome map's copy, as its card on Home names it. */
        const val COPY_CARD = "$WELCOME_CARD (copy)"

        /** The copy's own copy. */
        const val SECOND_COPY_CARD = "$COPY_CARD (copy)"
    }
}
