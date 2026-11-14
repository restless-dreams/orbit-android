package com.hyuchia.orbit.journeys

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/** Orbit's application id, the same in both builds. */
const val ORBIT = "com.hyuchia.orbit"

/**
 * The phone the journeys run on, as people use it: its screen, through UiAutomator, which reads what Orbit's map
 * page shows TalkBack too, and its shell. Maps take a while to draw on an emulator without a GPU, and longer on a
 * busy machine: every wait is long, and checks often, so a journey waits for what it needs and no longer.
 */
object Phone {
    /** The longest Orbit may take to start, or a map's page to draw, on a slow emulator under load. */
    const val PATIENCE = 90_000L

    /** How long a finger rests on the screen for a tap, as UiAutomator's own taps do. */
    private const val TAP_MILLISECONDS = 100L

    /** How long a screen that has drawn nothing new has stopped moving. */
    private const val STILL_MILLISECONDS = 500L

    /** A fling's moves, and the time between them (ms): a tenth of a second in all. */
    private const val FLING_STEPS = 10

    private const val FLING_STEP = 10L

    val device: UiDevice by lazy {
        // Orbit's map moves now and then, and the screen is never idle for long: the journeys wait for what they
        // look for, not for a still screen.
        Configurator.getInstance().waitForIdleTimeout = 500
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /** The journeys' own app, which hands Orbit files as another app would. */
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun shell(command: String): String = device.executeShellCommand(command)

    /** Waits until something matching `selector` shows, and returns it; fails saying what didn't come. */
    fun shows(selector: BySelector, what: String = selector.toString(), timeout: Long = PATIENCE): UiObject2 =
        device.wait(Until.findObject(selector), timeout) ?: throw AssertionError("Waited ${timeout / 1_000} seconds for $what")

    /** Waits until nothing matching `selector` shows. */
    fun gone(selector: BySelector, what: String = selector.toString(), timeout: Long = PATIENCE) {
        if (device.wait(Until.gone(selector), timeout) != true) {
            throw AssertionError("Waited ${timeout / 1_000} seconds for $what to go")
        }
    }

    /** Waits until `condition` holds, checking a few times a second. */
    fun eventually(what: String, timeout: Long = PATIENCE, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout

        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Waited ${timeout / 1_000} seconds for $what")
            }

            Thread.sleep(250)
        }
    }

    /** Orbit's process, or null while it isn't running. */
    fun orbitProcess(): String? = shell("pidof $ORBIT").trim().ifEmpty { null }

    /**
     * Where a node of Orbit's WebView with exactly this text is on screen now, or null when there's none, read
     * afresh from the WebView: UiAutomator's cache of a WebView's nodes can stay as they were before the page drew,
     * for good, once the WebView is laid out again (the phone turned) or made anew (Orbit's process started again).
     * A screen reader is told of every change; a test isn't. A field isn't looked in: the words typed in a planet
     * are there before anything holds them.
     */
    fun onThePage(text: String): Rect? {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        return automation.windows.mapNotNull { it.root }.firstNotNullOfOrNull { root -> findInWebViews(root, text, inWebView = false) }
    }

    private fun findInWebViews(node: AccessibilityNodeInfo, text: String, inWebView: Boolean): Rect? {
        val within = inWebView || (node.className?.toString() == "android.webkit.WebView" && node.packageName?.toString() == ORBIT)

        // Inside the WebView, each node is asked for again rather than taken from the cache.
        if (within && (!node.refresh() || node.isEditable)) {
            return null
        }

        // An idea off the map's orbit is still in its outline for screen readers, with no place on screen.
        if (within && node.text?.toString() == text) {
            return Rect().also(node::getBoundsInScreen)
        }

        return (0 until node.childCount).firstNotNullOfOrNull { index -> node.getChild(index)?.let { findInWebViews(it, text, within) } }
    }

    /** Waits until the map's page shows something with exactly this text, and returns where it is. */
    fun showsOnThePage(text: String, what: String = "\"$text\" on the map's page", timeout: Long = PATIENCE): Rect {
        val deadline = System.currentTimeMillis() + timeout

        while (System.currentTimeMillis() < deadline) {
            onThePage(text)?.let { return it }
            Thread.sleep(250)
        }

        return onThePage(text) ?: throw AssertionError("Waited ${timeout / 1_000} seconds for $what")
    }

    /**
     * Taps what `selector` finds once two readings a moment apart find it in the same place: a keyboard coming up
     * moves a sheet's buttons, and a tap aimed before it moved would land elsewhere. Each reading finds it afresh,
     * and one Orbit has made again since it was found (a list beside the map on a tablet starts afresh once the
     * panes stop moving) is found again on the screen as it is now (see [forgetWhatWasSeen]); it's tapped once,
     * where it stayed.
     */
    fun tapWhenStill(selector: BySelector, what: String = selector.toString()) {
        var before: Rect? = null

        eventually("$what to stay where it is") {
            Thread.sleep(300)

            val now = try {
                shows(selector, what).visibleBounds
            } catch (_: StaleObjectException) {
                forgetWhatWasSeen()
                null
            }

            (now != null && now == before).also { before = now }
        }

        before?.let { tap(it.centerX(), it.centerY()) }
    }

    /**
     * Taps (x, y) as a finger does: down, and up a tenth of a second later, as long as a finger rests on the screen for
     * a tap, each sent as it happens, without waiting for Orbit to take it. UiDevice.click waits for Orbit to have
     * taken the down before its tenth of a second starts: while Orbit's main thread waits on a slow emulator's GPU (a
     * frame of a map's first can take two seconds to reach the screen), its up then reaches the map's page after
     * Chromium's half-second long press has gone off, a long press where a finger's tap, its up already waiting behind
     * its down, would have been a tap. A stall that starts while the finger rests makes a long press of either, as it
     * does of a person's tap.
     */
    fun tap(x: Int, y: Int) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, x, y)
        // How long a finger rests on the screen for a tap, not a wait for Orbit: nothing between down and up waits on it.
        SystemClock.sleep(TAP_MILLISECONDS)
        touch(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x, y)
    }

    /**
     * Waits until Orbit's window has drawn no new frame for half a second: a map that has stopped moving, which people
     * wait for before they tap it. On a slow emulator a map's first frames can each take seconds to reach the screen,
     * and Orbit's main thread waits on one that's on its way: a finger's down taken just before, its up waits too, and
     * reaches the page after Chromium's half-second long press has gone off. Android's count of the frames Orbit has
     * drawn, the map's page's among them, is read through the shell.
     */
    fun still(what: String) {
        var before: String? = null

        eventually(what) {
            Thread.sleep(STILL_MILLISECONDS)
            val now = framesDrawn()
            val isStill = now != null && now == before
            before = now
            isStill
        }
    }

    /**
     * How many frames Orbit's windows have drawn since its process started, as Android counts them, or null while
     * Android doesn't say (the line is missing now and then on a slow emulator, or the shell call fails).
     */
    private fun framesDrawn(): String? =
        try {
            shell("dumpsys gfxinfo $ORBIT").lineSequence().firstOrNull { it.trimStart().startsWith("Total frames rendered") }
        } catch (exception: Exception) {
            null
        }

    /**
     * Presses (x, y) and holds it as a finger does a long press, until the app shows it has taken the press as a long
     * one (`taken`: its menu shows, say, or what was pressed lifts), then lets go. A press held for a set time can
     * reach an app whose main thread stalled all along as a down and an up at once, which it takes for a tap.
     */
    fun longPress(x: Int, y: Int, what: String, taken: () -> Boolean) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, x, y)

        try {
            eventually(what, condition = taken)
        } finally {
            touch(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x, y)
        }
    }

    /**
     * Flings a finger from (fromX, fromY) to (toX, toY), its events sent at once with the times a quick fling has. A
     * swipe sent a step at a time, as UiAutomator's is, can reach a busy app with a pause after its down, which it
     * takes for a long press.
     */
    fun fling(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, fromX, fromY)

        for (step in 1..FLING_STEPS) {
            touch(MotionEvent.ACTION_MOVE, down, down + step * FLING_STEP, fromX + (toX - fromX) * step / FLING_STEPS, fromY + (toY - fromY) * step / FLING_STEPS)
        }

        touch(MotionEvent.ACTION_UP, down, down + FLING_STEPS * FLING_STEP, toX, toY)
    }

    /**
     * Sends a finger's event on the touch screen to whichever app is there, without waiting for it to take it: a
     * finger, as UiAutomator's own taps say, which a page sees as a pointer of type "touch".
     */
    private fun touch(action: Int, down: Long, time: Long, x: Int, y: Int) {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)

        val finger = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }

        val where = MotionEvent.PointerCoords().apply {
            this.x = x.toFloat()
            this.y = y.toFloat()
            pressure = 1f
            size = 1f
        }

        val event = MotionEvent.obtain(down, time, action, 1, arrayOf(finger), arrayOf(where), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)

        try {
            if (!automation.injectInputEvent(event, false)) {
                throw AssertionError("A touch at ($x, $y) didn't reach the screen")
            }
        } finally {
            event.recycle()
        }
    }

    /** Whether the keyboard is on screen. */
    fun keyboardShows(): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        return automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }

    /** Puts the keyboard away with Back, as people do, when it's on screen; Back does nothing else then. */
    fun hideKeyboard() {
        if (keyboardShows()) {
            device.pressBack()
            eventually("the keyboard to go") { !keyboardShows() }
        }
    }

    /**
     * Forgets the nodes the journeys have read, so the next search reads the screen as it is. Compose tells of its
     * changes only when a screen reader or another accessibility service is on, which UiAutomator isn't: until then
     * UiAutomator goes on finding a node Orbit has replaced, whose every reading fails. TalkBack is told, and reads
     * the new one. Android 14 and later can be asked to forget; the journeys run on tablets with Android 15.
     */
    fun forgetWhatWasSeen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags).clearCache()
        }
    }
}

/**
 * Leaves a screenshot and the screen's layout of a journey that fails, as the screen was when it failed, where the
 * scripts collect them: the journeys' own media folder, /sdcard/Android/media/com.hyuchia.orbit.journeys/failures.
 * Then, failed or not, `cleanUp` puts the phone back as the next journey expects it (upright, without a screen
 * lock): in an `@After`, it would run first, and the screenshot would show the phone put back instead.
 */
class CapturesFailures(private val cleanUp: () -> Unit = {}) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                // A capture that fails too still leaves the journey's own failure to say what went wrong.
                runCatching { capture(description) }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            } finally {
                cleanUp()
            }
        }
    }

    private fun capture(description: Description) {
        @Suppress("DEPRECATION")
        val folder = File(Phone.context.externalMediaDirs.first(), "failures").apply { mkdirs() }
        val name = "${description.testClass.simpleName}-${description.methodName}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        Phone.device.takeScreenshot(File(folder, "$name.png"))
        Phone.device.dumpWindowHierarchy(File(folder, "$name.xml"))
    }
}
