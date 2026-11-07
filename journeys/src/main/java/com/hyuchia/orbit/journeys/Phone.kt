package com.hyuchia.orbit.journeys

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.rules.TestWatcher
import org.junit.runner.Description
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
     * A screen reader is told of every change; a test isn't.
     */
    fun onThePage(text: String): Rect? {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        return automation.windows.mapNotNull { it.root }.firstNotNullOfOrNull { root -> findInWebViews(root, text, inWebView = false) }
    }

    private fun findInWebViews(node: AccessibilityNodeInfo, text: String, inWebView: Boolean): Rect? {
        val within = inWebView || (node.className?.toString() == "android.webkit.WebView" && node.packageName?.toString() == ORBIT)

        // Inside the WebView, each node is asked for again rather than taken from the cache.
        if (within && !node.refresh()) {
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
     * Taps what `selector` finds once it stays where it is: a keyboard coming up moves a sheet's buttons, and a tap
     * aimed before it moved would land elsewhere.
     */
    fun tapWhenStill(selector: BySelector, what: String = selector.toString()) {
        var before = shows(selector, what).visibleBounds

        eventually("$what to stay where it is") {
            Thread.sleep(300)
            val now = shows(selector, what).visibleBounds
            (now == before).also { before = now }
        }

        device.click(before.centerX(), before.centerY())
    }
}

/**
 * Leaves a screenshot and the screen's layout of a journey that fails where the scripts collect them: the
 * journeys' own media folder, /sdcard/Android/media/com.hyuchia.orbit.journeys/failures.
 */
class CapturesFailures : TestWatcher() {
    override fun failed(error: Throwable, description: Description) {
        @Suppress("DEPRECATION")
        val folder = File(Phone.context.externalMediaDirs.first(), "failures").apply { mkdirs() }
        val name = "${description.testClass.simpleName}-${description.methodName}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        Phone.device.takeScreenshot(File(folder, "$name.png"))
        Phone.device.dumpWindowHierarchy(File(folder, "$name.xml"))
    }
}
