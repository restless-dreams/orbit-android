package com.hyuchia.orbit.map

import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.CapturesFailures
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OnDevice.addMap
import com.hyuchia.orbit.OnDevice.deleteMap
import com.hyuchia.orbit.OnDevice.eventually
import com.hyuchia.orbit.OnDevice.mapDrawn
import com.hyuchia.orbit.OnDevice.mapPage
import com.hyuchia.orbit.OnDevice.onPage
import com.hyuchia.orbit.OnDevice.openMap
import com.hyuchia.orbit.OnDevice.screen
import com.hyuchia.orbit.OnDevice.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * The map's search on the phone's own WebView, as people use it: its sheet comes up with the keyboard, the words go
 * in as keys, and the idea found is picked, with the keyboard still up. The map under the sheet, or beside the
 * search on a tablet, keeps its height all the while, and as it flies to the idea. Drawn anew at another
 * size as the keyboard came up, it held Orbit up for seconds on a slow phone, just as the first keys went in; as the
 * search closed, it made room for the keyboard for a moment, then grew back as it went.
 */
@RunWith(AndroidJUnit4::class)
class MapSearchTest {
    @get:Rule
    val captures = CapturesFailures()

    /** A test that waits for something that never comes fails, rather than holding up the rest. */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(300)

    private lateinit var id: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun addTheMap() {
        id = addMap(MAP)
    }

    @After
    fun removeTheMap() {
        scenario?.close()
        deleteMap(id)
    }

    @Test
    fun theMapKeepsItsHeightFromTheSearchOpeningThroughTheKeysToTheIdeaFound() {
        val opened = openMap(id).also { scenario = it }
        val page = opened.mapPage()
        mapDrawn(page, TITLE)
        val height = pageHeight(page)
        // The page hears each time it's resized, and is drawn anew at its new size then.
        onPage(page, "window.heightsHeard = []; addEventListener('resize', () => { window.heightsHeard.push(innerHeight); }); true")

        waitFor("the button \"Search the map\"") { screen.findObject(By.desc("Search the map")) }.click()
        waitFor("the search's field") { screen.findObject(By.clazz("android.widget.EditText").focused(true)) }
        screen.executeShellCommand("input text Pasteis")
        // The way to the idea found, over its name: only the search's list says it.
        val found = waitFor("the search's result") { screen.findObject(By.text("$TITLE › Food")) }
        eventually("the keyboard up") { keyboardUp(opened) }

        // Picked, the idea is flown to as the search closes and its keyboard goes.
        found.click()
        mapDrawn(page, IDEA)
        eventually("the keyboard gone") { !keyboardUp(opened) }
        eventually("the map as tall as it was, if it changed") { pageHeight(page) == height }

        // Beside the map, on a tablet, the search takes some of the map's width as it opens, and gives it back: the
        // map's height says whether it made room for the keyboard.
        assertEquals(
            "The map's other heights, from the search opening to the idea found",
            "\"\"",
            onPage(page, "heightsHeard.filter((heard) => heard !== $height).join(' ')"),
        )
    }

    /** How tall the map's page is, as the page sees it. */
    private fun pageHeight(page: WebView): String = onPage(page, "innerHeight")

    /** Whether the keyboard is up over Orbit's screen. */
    private fun keyboardUp(opened: ActivityScenario<MainActivity>): Boolean {
        var up = false

        opened.onActivity { activity ->
            up = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        return up
    }

    private companion object {
        const val TITLE = "Trip to Lisbon"
        const val IDEA = "Pasteis de nata"
        const val MAP = "# $TITLE\n\n## Alfama\n\n## Food\n\n### $IDEA\n"
    }
}
