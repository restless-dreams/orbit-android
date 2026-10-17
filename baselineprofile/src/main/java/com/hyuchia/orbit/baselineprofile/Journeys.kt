package com.hyuchia.orbit.baselineprofile

import android.view.accessibility.AccessibilityNodeInfo
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.boundsInScreen
import androidx.test.uiautomator.scrollToElement
import androidx.test.uiautomator.textAsString

/** Orbit's application id, the same in both builds. */
const val PACKAGE_NAME = "com.hyuchia.orbit"

/** One of the samples a fresh install has: small, so it draws quickly on any device. */
const val SAMPLE_MAP = "Weekend in Lisbon"

/** Starting the map's WebView and drawing a map takes a while on an emulator without a GPU. */
private const val PAGE_TIMEOUT = 30_000L

/** Enough flings to reach either end of the maps a fresh install has, and then some. */
private const val FLING_LIMIT = 8

/** Starts Orbit with its launcher intent and waits for the maps' cards, which show once the maps are read. */
fun MacrobenchmarkScope.startOnYourMaps() {
    startActivityAndWait()
    onElement(PAGE_TIMEOUT) { isMapCard() }
}

/** Flings the list of maps to its end and back to the top. */
fun MacrobenchmarkScope.scrollYourMaps() {
    val maps = yourMaps()
    flingToEnd(maps, Direction.DOWN)
    flingToEnd(maps, Direction.UP)
}

/** Opens the map with this title from the list and waits for it to be drawn. */
fun MacrobenchmarkScope.openMap(title: String) {
    // A card low on the screen can sit under the new map button or the navigation bar.
    val reachable = device.displayHeight * 2 / 3
    yourMaps()
        .scrollToElement(Direction.DOWN) { textAsString()?.contains(title) == true && boundsInScreen().bottom < reachable }
        .click()
    waitForMapToolbar()
}

/** Opens the map's styles, waits for them, and closes them again. */
fun MacrobenchmarkScope.openStyleAndLayout() {
    // A button on the toolbar where the screen is wide enough, otherwise an item in the map's menu.
    val button = onElementOrNull(timeoutMs = 0) { isDescribedAs("Style and layout") }

    if (button != null) {
        button.click()
    } else {
        onElement { isDescribedAs("More") }.click()
        onElement { textAsString() == "Style and layout" }.click()
    }

    onElement(PAGE_TIMEOUT) { textAsString() == "Colors" }
    // Lets the sheet finish sliding up before it closes.
    waitForStableInActiveWindow(requireStableScreenshot = false)
    pressBack()
    device.wait(Until.gone(By.text("Colors")), PAGE_TIMEOUT)
    waitForMapToolbar()
}

/** Leaves the map for the list of maps. */
fun MacrobenchmarkScope.backToYourMaps() {
    onElement { isDescribedAs("Back to your maps") }.click()
    onElement(PAGE_TIMEOUT) { isMapCard() }
}

private fun MacrobenchmarkScope.yourMaps(): UiObject2 {
    val maps = onElement { isScrollable && belongsToOrbit() }
    // Swipes keep clear of the app bar and the navigation bar.
    maps.setGestureMarginPercentage(0.2f)
    return maps
}

/** The map's toolbar shows once the page has drawn the map. */
private fun MacrobenchmarkScope.waitForMapToolbar() {
    onElement(PAGE_TIMEOUT) { isDescribedAs("Show the whole map") || isDescribedAs("Focus on one idea") }
}

private fun flingToEnd(list: UiObject2, direction: Direction) {
    repeat(FLING_LIMIT) {
        // A fling reports whether the list can go further.
        if (!list.fling(direction)) {
            return
        }
    }
}

/** A card on the list of maps: tapping it opens the map. */
private fun AccessibilityNodeInfo.isMapCard(): Boolean =
    actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == "Open" }

private fun AccessibilityNodeInfo.isDescribedAs(description: String): Boolean = contentDescription?.toString() == description

private fun AccessibilityNodeInfo.belongsToOrbit(): Boolean = packageName?.toString() == PACKAGE_NAME
