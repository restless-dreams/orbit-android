package com.hyuchia.orbit

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the device tests share: the app as it runs on the phone (its maps, its pages), the phone's screen as people
 * use it, through UiAutomator, and the map's page itself, whose document the tests read and whose planets they tap
 * where they are on screen. The page's own accessibility tree isn't the tests' way in: a WebView made anew as a
 * map loads again can keep an empty one in UiAutomator's cache for good, which TalkBack never meets. Maps take a
 * while to draw on an emulator without a GPU, and longer on a busy machine: every wait is long, and checks often.
 */
internal object OnDevice {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    val app: AppContainer get() = context.container

    val screen: UiDevice by lazy {
        // Orbit's map moves now and then: the tests wait for what they look for, not for a still screen.
        Configurator.getInstance().waitForIdleTimeout = 500
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /** The longest a map's page may take to draw, on a slow emulator under load. */
    val PAGE_WAIT: Duration = 90.seconds

    /** Adds a map with `markdown`, as an import would, and returns its id. */
    fun addMap(markdown: String): String = runBlocking {
        app.maps.load()
        app.maps.add(markdown)
    }

    /** Deletes a map the test added, for good. */
    fun deleteMap(id: String) = runBlocking {
        app.maps.delete(id)?.let { app.maps.deleteForever(it) }
    }

    /** The map's text in its file, as Orbit wrote it last. */
    fun mapText(id: String): String = File(app.maps.folder(id), "map.md").readText()

    /** Opens Orbit on map `id`, as the map's own shortcut does. */
    fun openMap(id: String): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_MAP)
            .putExtra(EXTRA_MAP_ID, id)
            .putExtra(EXTRA_OPEN_MARK, OpenMark.of(context)),
    )

    /** Waits until something of Orbit's own screens shows exactly this text, and returns it. */
    fun shows(text: String, timeout: Duration = PAGE_WAIT): UiObject2 = waitFor("\"$text\" to show", timeout) { screen.findObject(By.text(text)) }

    /** Waits until `find` finds something, checking a few times a second, and fails saying what didn't come after `timeout`. */
    fun <T : Any> waitFor(what: String, timeout: Duration = PAGE_WAIT, find: () -> T?): T {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            find()?.let { return it }
            Thread.sleep(200)
        }

        return find() ?: throw AssertionError("Waited ${timeout.inWholeSeconds} seconds for $what")
    }

    /** Waits until `condition` holds, as [waitFor]. */
    fun eventually(what: String, timeout: Duration = PAGE_WAIT, condition: () -> Boolean) {
        waitFor(what, timeout) { if (condition()) Unit else null }
    }

    /**
     * The WebView showing the map on the activity's screen, once there is one: the only one there, on a phone.
     * After `before` stopped, the one that took its place.
     */
    fun ActivityScenario<MainActivity>.mapPage(before: WebView? = null): WebView = waitFor("the map's WebView on screen") {
        var found: WebView? = null

        onActivity { activity ->
            found = webViewsIn(activity.window.decorView).singleOrNull { it !== before && it.isAttachedToWindow && it.isShown }
        }

        found
    }

    private fun webViewsIn(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViewsIn(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** Runs `block` on the main thread, where everything about a page happens; a page off screen never runs what's posted to its view. */
    fun onMainThread(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    /** Runs `script` in the page of `webView`, as DevTools would, and returns what it comes to, as JSON. */
    suspend fun evaluate(webView: WebView, script: String): String = suspendCancellableCoroutine { going ->
        onMainThread { webView.evaluateJavascript(script) { result -> going.resume(result) } }
    }

    /** What `script` comes to on `page`, as JSON, from the test's thread. */
    fun onPage(page: WebView, script: String): String = runBlocking { evaluate(page, script) }

    /** A script that finds what the page labels exactly `label` for TalkBack: a planet, the + in an orbit. */
    private fun labelled(label: String): String =
        "[...document.querySelectorAll('[aria-label]')].find((element) => element.getAttribute('aria-label') === ${JSONObject.quote(label)})"

    /** Whether the page shows what it labels exactly `label`. */
    fun pageHas(page: WebView, label: String): Boolean = onPage(page, "Boolean(${labelled(label)})") == "true"

    /**
     * Waits until the map's page has drawn `idea` in the middle of the map: the + in its orbit is there, labelled
     * "Add an idea to …" for TalkBack.
     */
    fun mapDrawn(page: WebView, idea: String) = eventually("the page to draw $idea") { pageHas(page, "Add an idea to $idea") }

    /** Whether the map's page holds an idea named `idea`: its outline for screen readers lists every idea. */
    fun mapHolds(page: WebView, idea: String): Boolean =
        onPage(page, "[...document.querySelectorAll('.orbit-outline button')].some((button) => button.textContent.trim() === ${JSONObject.quote(idea)})") == "true"

    /** Taps what the page labels exactly `label`, where it is on screen, as a finger does. */
    fun tapOnPage(page: WebView, label: String) {
        val place = onPage(page, "(() => { const box = ${labelled(label)}.getBoundingClientRect(); return [box.left + box.width / 2, box.top + box.height / 2, devicePixelRatio].join(' '); })()")
        val (x, y, ratio) = place.trim('"').split(' ').map(String::toDouble)
        val onScreen = IntArray(2)
        val placed = CountDownLatch(1)

        onMainThread {
            page.getLocationOnScreen(onScreen)
            placed.countDown()
        }

        placed.await()
        screen.click((onScreen[0] + x * ratio).roundToInt(), (onScreen[1] + y * ratio).roundToInt())
    }

    /** Whether a field to type an idea's name in has the focus on the page. */
    private fun typing(page: WebView): Boolean = onPage(page, "document.activeElement?.tagName ?? null") == "\"TEXTAREA\""

    /**
     * Types a new idea under `idea`, in the middle of the map, as people do: the + in its orbit opens a planet to type
     * in, and the words go in as a keyboard's keys. The planet stays open, with the words in it.
     */
    fun typeAnIdea(page: WebView, idea: String, words: String) {
        tapOnPage(page, "Add an idea to $idea")
        eventually("the new planet's field") { typing(page) }
        screen.executeShellCommand("input text ${words.replace(" ", "%s")}")
        eventually("$words in the new planet") { onPage(page, "document.activeElement?.value ?? null") == JSONObject.quote(words) }
    }

    /**
     * Adds an idea to `idea`, in the middle of the map, as people do: typed in a new planet ([typeAnIdea]), then
     * Enter adds it, and opens the next one. Escape hides the keyboard, then closes that one.
     */
    fun addIdeaOnTheMap(page: WebView, idea: String, words: String) {
        typeAnIdea(page, idea, words)
        screen.pressEnter()
        eventually("$words on the map") { mapHolds(page, words) }

        repeat(3) {
            if (typing(page)) {
                screen.pressKeyCode(KeyEvent.KEYCODE_ESCAPE)
                Thread.sleep(500)
            }
        }

        eventually("the field for the next idea closed") { !typing(page) }
    }
}

/**
 * Leaves a screenshot and the screen's layout of a test that fails where scripts/device-tests.sh collects them:
 * Orbit's own media folder, /sdcard/Android/media/com.hyuchia.orbit/failures.
 */
class CapturesFailures : TestWatcher() {
    override fun failed(error: Throwable, description: Description) {
        @Suppress("DEPRECATION")
        val folder = File(OnDevice.context.externalMediaDirs.first(), "failures").apply { mkdirs() }
        val name = "${description.testClass.simpleName}-${description.methodName}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        OnDevice.screen.takeScreenshot(File(folder, "$name.png"))
        OnDevice.screen.dumpWindowHierarchy(File(folder, "$name.xml"))
    }
}
