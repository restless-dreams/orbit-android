package com.hyuchia.orbit.journeys

import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.eventually
import com.hyuchia.orbit.journeys.Phone.fling
import com.hyuchia.orbit.journeys.Phone.hideKeyboard
import com.hyuchia.orbit.journeys.Phone.keyboardShows
import com.hyuchia.orbit.journeys.Phone.longPress
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import java.util.regex.Pattern

/**
 * The phone's launcher, as people put Orbit's widget on the home screen with it: its list of widgets, the widgets
 * on the home screen and their settings. On the emulators it's Pixel's launcher (with Google's apps) or Android's
 * own Launcher3 (Android 10), which say the same things in English and lay their list of widgets out apart: Pixel's
 * has a search and an entry for each app to open, Launcher3's every widget under its app.
 */
object Launcher {
    /** What the launcher calls Orbit's widget: its name in the list, and each one's description on the home screen. */
    const val WIDGET = "Orbit map"

    /** The launcher's package, whichever launcher the phone has. */
    val name: String by lazy {
        val home = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        home.lines().last { it.contains('/') }.substringBefore('/').trim()
    }

    /** A widget's size in the list, such as "3 × 2", beside its name. */
    private val size = Pattern.compile("\\d+ × \\d+")

    /** Goes to the home screen, and waits for it. */
    fun home() {
        device.pressHome()
        shows(By.pkg(name), "the home screen")
    }

    /**
     * Opens the launcher's list of widgets as people do: a long press on an empty part of the home screen, then
     * Widgets.
     */
    fun openWidgetList() {
        home()
        val empty = emptySpot()
        val widgets = By.text("Widgets").pkg(name)
        longPress(empty.x, empty.y, "Widgets in the home screen's menu") { device.hasObject(widgets) }
        shows(widgets, "Widgets in the home screen's menu").click()
        shows(By.pkg(name).scrollable(true), "the launcher's list of widgets")
    }

    /**
     * Adds Orbit's widget from the open list with the launcher's own Add: on Pixel's launcher (Android 15 and later),
     * the Add button a tap on the widget shows; on older ones, which only drag a widget in, what the list offers
     * TalkBack, "Add to Home screen". Orbit's widget is found with the list's search, where it has one, and Orbit's
     * entry opened; else by scrolling down to it.
     */
    fun addOrbitFromWidgetList() {
        val search = device.findObject(By.clazz("android.widget.EditText").pkg(name))

        if (search != null) {
            search.click()
            Orbit.type("Orbit")
            // Orbit's entry among the results, not the search, which reads "Orbit" too: a tap there brings the
            // keyboard back.
            val entry = By.clazz("android.widget.TextView").text("Orbit").pkg(name)
            shows(entry, "Orbit in the list's search")
            // The keyboard the search brought up goes, as people put it away to see the list: on a tablet, the list
            // shows Orbit's widgets beside the search, and the keyboard would be over their names.
            eventually("the keyboard for the list's search") { keyboardShows() }
            hideKeyboard()
            shows(entry, "Orbit in the list's search").click()
        } else {
            // Down to Orbit's widgets, or as far as the list goes: Orbit's are near its end, where its last row can
            // stay under the navigation bar. The list is laid out anew as it scrolls, so it's found again each time.
            while (!device.hasObject(widgetName())) {
                val list = shows(By.pkg(name).scrollable(true).res(Pattern.compile(".*widgets_list_view")), "the launcher's list of widgets")

                if (!list.scroll(Direction.DOWN, 0.8f)) {
                    break
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            shows(widgetName(), "Orbit's widget in the list").click()
            shows(By.desc("Add $WIDGET widget").pkg(name), "the list's Add for Orbit's widget").click()
            return
        }

        val entry = withAction(adding) { node -> node.findAccessibilityNodeInfosByText(WIDGET).isNotEmpty() }
            ?: throw AssertionError("The launcher's list offers no Add to Home screen for Orbit's widget")
        entry.performAction(entry.actionList.first { adding.matcher(it.label ?: "").matches() }.id)
    }

    /**
     * Shows Orbit's shortcuts as people bring them up: a long press on Orbit's icon among the launcher's apps, a swipe
     * up from the home screen away, found with the search both launchers have atop them, wherever Orbit's icon is in
     * the list.
     */
    fun openOrbitShortcuts() {
        home()
        fling(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 5)
        shows(By.clazz("android.widget.EditText").pkg(name), "the search atop the launcher's apps").click()
        Orbit.type("Orbit")
        val icon = shows(By.clazz("android.widget.TextView").text("Orbit").pkg(name), "Orbit among the launcher's apps").visibleCenter
        longPress(icon.x, icon.y, "Orbit's shortcuts") { device.hasObject(By.text(newMap).pkg(name)) }
    }

    /** Orbit's own shortcut for a new map, by its long label, or its short one where the long one doesn't fit. */
    private val newMap = Pattern.compile("Start a new map|New map")

    /**
     * A part of the home screen with nothing on it, well clear of its icons and widgets, as the launcher lays them
     * out. Both launchers leave the row above their apps empty on a fresh phone, under where a first widget goes, and
     * that's looked at first; a tablet's has a calendar there, and space beside it.
     */
    private fun emptySpot(): Point {
        val screen = device.displayWidth * device.displayHeight
        // What's on the home screen, but for the pages that hold it all.
        val things = device.findObjects(By.pkg(name)).map { it.visibleBounds }.filter { it.width() * it.height() < screen / 3 }
        val clear = (48 * Phone.context.resources.displayMetrics.density).toInt()
        val across = listOf(75, 60, 50, 40, 25, 85, 15)
        val down = listOf(55, 45, 65, 35, 25, 75)

        return down.flatMap { y -> across.map { x -> Point(device.displayWidth * x / 100, device.displayHeight * y / 100) } }
            .firstOrNull { spot -> things.none { thing -> Rect(thing).apply { inset(-clear, -clear) }.contains(spot.x, spot.y) } }
            ?: throw AssertionError("The home screen has no empty part to press: ${things.size} things on it")
    }

    /** The launcher's Add, in what it offers TalkBack: Pixel's launcher says "Add to home screen", Launcher3 "Add to Home screen". */
    private val adding = Pattern.compile("add to home screen", Pattern.CASE_INSENSITIVE)

    /** The name of a widget in the list, with its size beside it (Pixel's entry for an app names its widgets too, without). */
    private fun widgetName(): BySelector = By.text(WIDGET).pkg(name).hasParent(By.hasChild(By.text(size)))

    /** The first node on screen offering TalkBack an action labelled as `label` says, among those `holds` accepts. */
    private fun withAction(label: Pattern, holds: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        val nodes = automation.windows.mapNotNull { it.root }.flatMap { root -> descendants(root) }
        return nodes.firstOrNull { node -> node.actionList.any { label.matcher(it.label ?: "").matches() } && holds(node) }
    }

    private fun descendants(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> =
        sequenceOf(node) + (0 until node.childCount).asSequence().mapNotNull { node.getChild(it) }.flatMap { descendants(it) }

    /** Orbit's widget on the home screen showing the map with this card. */
    fun widgetShowing(card: String): UiObject2 = shows(By.desc(WIDGET).hasDescendant(By.text(card).pkg(ORBIT)), "Orbit's widget showing $card")

    /**
     * The ids of Orbit's widgets Android keeps for the launcher. A widget the launcher drops goes from them; Pixel's
     * launcher may keep a removed one's a while, in case it's put back, so they aren't what the home screen shows.
     */
    fun orbitWidgetIds(): Set<Int> {
        val widgets = shell("dumpsys appwidget").substringAfter("Widgets:").substringBefore("Hosts:")
        return Regex("\\[\\d+] id=(\\d+)\\s+host=[^\\n]*\\s+provider=ProviderId\\{[^}]*cmp:ComponentInfo\\{$ORBIT/")
            .findAll(widgets).map { it.groupValues[1].toInt() }.toSet()
    }
}
