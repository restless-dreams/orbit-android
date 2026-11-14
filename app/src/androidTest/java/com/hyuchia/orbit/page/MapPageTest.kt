package com.hyuchia.orbit.page

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.webkit.WebViewCompat
import com.hyuchia.orbit.CapturesFailures
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OnDevice.addIdeaOnTheMap
import com.hyuchia.orbit.OnDevice.addMap
import com.hyuchia.orbit.OnDevice.app
import com.hyuchia.orbit.OnDevice.context
import com.hyuchia.orbit.OnDevice.deleteMap
import com.hyuchia.orbit.OnDevice.eventually
import com.hyuchia.orbit.OnDevice.mapDrawn
import com.hyuchia.orbit.OnDevice.mapHolds
import com.hyuchia.orbit.OnDevice.mapPage
import com.hyuchia.orbit.OnDevice.mapText
import com.hyuchia.orbit.OnDevice.onMainThread
import com.hyuchia.orbit.OnDevice.openMap
import com.hyuchia.orbit.OnDevice.screen
import com.hyuchia.orbit.OnDevice.shows
import com.hyuchia.orbit.OnDevice.typeAnIdea
import com.hyuchia.orbit.OnDevice.waitFor
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.data.WriteResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * A map on the phone's own WebView, as people use it: it loads and draws, an idea typed on the map is saved to the
 * map's file, or to its history when a version only to look at arrives as it's typed, and when the page fails to
 * load or its process dies, the map says so in its place and comes back with Load it again, losing nothing. The
 * failures are the WebView's own: its page navigates where nothing answers, and its renderer is killed as Android
 * kills it to free memory.
 */
@RunWith(AndroidJUnit4::class)
class MapPageTest {
    @get:Rule
    val captures = CapturesFailures()

    /** A test that waits for something that never comes fails, rather than holding up the rest. */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(300)

    private lateinit var id: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun addTheMap() {
        val webView = WebViewCompat.getCurrentWebViewPackage(context)
        Log.i(TAG, "WebView: ${webView?.packageName} ${webView?.versionName}")
        id = addMap(MAP)
    }

    @After
    fun removeTheMap() {
        scenario?.close()
        deleteMap(id)
    }

    @Test
    fun aMapDrawsOnTheWebViewAndAnIdeaTypedOnItIsSavedToItsFile() {
        val opened = openMap(id).also { scenario = it }
        val page = opened.mapPage()
        mapDrawn(page, TITLE)
        assertTrue(mapHolds(page, "Alfama"))

        addIdeaOnTheMap(page, TITLE, "Pasteis de nata")

        eventually("the idea saved to the map's file") { "Pasteis de nata" in mapText(id) }
        // Saved as Orbit writes it: the map as it was, and the idea under its top idea.
        assertTrue(mapText(id).startsWith("# $TITLE"))
        assertTrue("Alfama" in mapText(id))
    }

    @Test
    fun anIdeaBeingTypedAsANewerVersionArrivesFromElsewhereGoesIntoTheMapsHistory() {
        val opened = openMap(id).also { scenario = it }
        val page = opened.mapPage()
        mapDrawn(page, TITLE)
        typeAnIdea(page, TITLE, "Pasteis de nata")

        // A version a newer Orbit wrote arrives as the idea is typed, as the synced folder or another screen brings it.
        val newer = "---\nschemaVersion: 999\n---\n# $TITLE\n\n## Alfama\n\n## Belem\n"
        assertEquals(WriteResult.Written, runBlocking { app.maps.change(id, newer, base = mapText(id), reason = VersionReason.Elsewhere) })

        // The version shows, only to look at; what was typed is in the map's history, as a message says.
        eventually("the newer version on the map") { mapHolds(page, "Belem") }
        shows("This map changed elsewhere, so your last change is in its history.")
        eventually("what was typed in the map's history") { inHistory("Pasteis de nata") }
        assertEquals(newer, mapText(id))
        // The map before the change could be undone to, but a map only to look at offers no Undo: its button is off.
        assertFalse(waitFor("Undo in the top bar") { screen.findObject(By.clickable(true).hasChild(By.desc("Undo"))) }.isEnabled)
    }

    @Test
    fun aPageThatDoesntLoadSaysSoAndLoadsAgainWithTheMapAsItWas() {
        val opened = openMap(id).also { scenario = it }
        val page = opened.mapPage()
        mapDrawn(page, TITLE)
        addIdeaOnTheMap(page, TITLE, "Tram 28")
        eventually("the idea saved") { "Tram 28" in mapText(id) }
        val saved = mapText(id)

        // The page's main frame goes where nothing answers: WebView says it didn't load.
        onMainThread { page.loadUrl("https://127.0.0.1:9/") }

        shows("The map didn't load")
        shows("The page that draws it couldn't start. Your map is safe.")
        shows("Load it again").click()

        val again = opened.mapPage(before = page)
        mapDrawn(again, TITLE)
        assertTrue(mapHolds(again, "Tram 28"))
        assertEquals(saved, mapText(id))
    }

    @Test
    fun aPageWhoseProcessDiesSaysSoAndTheMapComesBackWithNothingLost() {
        val opened = openMap(id).also { scenario = it }
        val page = opened.mapPage()
        mapDrawn(page, TITLE)
        addIdeaOnTheMap(page, TITLE, "Belem tower")
        eventually("the idea saved") { "Belem tower" in mapText(id) }
        val saved = mapText(id)

        // Android stops the pages' process to free memory, as it may while Orbit is in the background.
        onMainThread { page.loadUrl("chrome://kill") }

        shows("The map stopped")
        shows("Android freed memory while it was open. Your changes are saved.")
        shows("Load it again").click()

        val again = opened.mapPage(before = page)
        mapDrawn(again, TITLE)
        assertTrue(mapHolds(again, "Belem tower"))
        assertEquals(saved, mapText(id))

        // The new page edits and saves as the first did.
        addIdeaOnTheMap(again, TITLE, "Sintra")
        eventually("the idea saved on the new page") { "Sintra" in mapText(id) }

        // Orbit itself lived on: its process is the test's, and it's still here.
        assertTrue(screen.executeShellCommand("pidof ${context.packageName}").isNotBlank())
    }

    /** Whether a version in the map's history holds `words`. */
    private fun inHistory(words: String): Boolean = runBlocking {
        app.maps.versions(id).any { version -> words in app.maps.readVersion(id, version.time).orEmpty() }
    }

    private companion object {
        const val TAG = "MapPageTest"
        const val TITLE = "Trip to Lisbon"
        const val MAP = "# $TITLE\n\n## Alfama\n"
    }
}
