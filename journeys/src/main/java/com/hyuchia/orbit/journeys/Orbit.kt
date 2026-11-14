package com.hyuchia.orbit.journeys

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.eventually
import com.hyuchia.orbit.journeys.Phone.gone
import com.hyuchia.orbit.journeys.Phone.keyboardShows
import com.hyuchia.orbit.journeys.Phone.orbitProcess
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import com.hyuchia.orbit.journeys.Phone.showsOnThePage
import com.hyuchia.orbit.journeys.Phone.still
import com.hyuchia.orbit.journeys.Phone.tap
import com.hyuchia.orbit.journeys.Phone.tapWhenStill

/**
 * Orbit as people use it, in English, as the emulators speak it: its screens by what they say, and its maps by
 * what their page says TalkBack, which doesn't change with a release build's renamed code.
 */
object Orbit {
    /** The map a fresh install has, as its card on Home names it. */
    const val WELCOME_CARD = "👋 Welcome to Orbit"

    /** The welcome map's top idea, as its page names it. */
    const val WELCOME = "Welcome to Orbit"

    /** Orbit as a fresh install has it, with the welcome map and nothing else, and not running. */
    fun clear() {
        shell("pm clear $ORBIT")
    }

    /** Starts Orbit as the launcher does: anew, or its screen brought back as Android left it. */
    fun start() {
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $ORBIT/.MainActivity")
    }

    /** Waits for Home, once its maps are read: its search. */
    fun home(): UiObject2 = shows(By.text("Search your maps").pkg(ORBIT), "Home")

    /**
     * Finds the map with this card with Home's search for `words` (the card's own, unless they're words a keyboard
     * can't type, such as an emoji), wherever its card is in the list, and waits for its card: Enter searches, and
     * the keyboard goes, which on a small screen covers the card's name.
     */
    fun findOnHome(card: String, words: String = card): UiObject2 {
        home().click()
        focusedField()
        type(words)
        device.pressEnter()
        return shows(By.clazz("android.widget.TextView").text(card).pkg(ORBIT), "the card of $card")
    }

    /** Opens the map with this card on Home. */
    fun openMap(card: String) {
        home()
        shows(By.text(card).pkg(ORBIT), "the card of $card").click()
    }

    /**
     * Waits until the map's page has drawn `idea` in the middle, and returns where its + is: the page gives each
     * planet to TalkBack, and the + in the orbit reads "Add an idea to …" as text (the toolbar's + says it in its
     * description).
     */
    fun drawn(idea: String, addTo: String = ADD_TO): Rect = showsOnThePage("$addTo $idea", "the map's page to draw $idea")

    /** What the + in an orbit says before the idea's name, in English. */
    const val ADD_TO = "Add an idea to"

    /** Waits until the map holds an idea with exactly this name, on the page or in its outline for screen readers. */
    fun onTheMap(idea: String): Rect = showsOnThePage(idea, "$idea on the map")

    /**
     * Taps the + in the orbit of `idea`, in the middle of the map, which says `addTo` before its name, once the map
     * has stopped moving, as people wait for it to before they tap ([still]).
     */
    fun tapThePlus(idea: String, addTo: String = ADD_TO) {
        drawn(idea, addTo)
        still("the map of $idea to stop moving")
        val plus = drawn(idea, addTo)

        if (plus.isEmpty) {
            throw AssertionError("The + in the orbit of $idea isn't on screen")
        }

        tap(plus.centerX(), plus.centerY())
    }

    /** Words Orbit's own screens show (a row, a title, a button's label), not a field, nor the map's page. */
    fun label(words: String): UiObject2 = shows(By.clazz("android.widget.TextView").text(words).pkg(ORBIT), "the label \"$words\"")

    /** Finds `words` with the map's search, and flies to what it found. */
    fun search(words: String) {
        described("Search the map").click()
        focusedField()
        type(words)
        tapWhenStill(By.clazz("android.widget.TextView").text(words).pkg(ORBIT), "the search's result \"$words\"")
        drawn(words)
    }

    /** Something on screen in Orbit described as this: an icon button. */
    fun described(description: String): UiObject2 = shows(By.desc(description).pkg(ORBIT), "the button \"$description\"")

    /** The field that has the focus in Orbit, once one has it. */
    fun focusedField(): UiObject2 = shows(By.clazz("android.widget.EditText").focused(true).pkg(ORBIT), "a field to type in")

    /**
     * Adds an idea to the idea in the middle of the map as people do: the + in the orbit opens a planet to type in,
     * the words go in (`typing` them: [type] by default, or [typeKeys]), Enter adds it (and opens the next one), and
     * once the map holds it, Escape hides the keyboard, then closes that one. The planet's own field, which reads the
     * words before Enter, isn't what's looked for on the map: an Enter that added nothing doesn't pass.
     */
    fun addIdea(to: String, words: String, addTo: String = ADD_TO, typing: (String) -> Unit = ::type) {
        tapThePlus(to, addTo)
        focusedField()
        typing(words)
        device.pressEnter()
        onTheMap(words)
        val field = By.clazz("android.widget.EditText").focused(true).pkg(ORBIT)

        repeat(3) {
            if (device.hasObject(field)) {
                device.pressKeyCode(KeyEvent.KEYCODE_ESCAPE)
                device.wait(Until.gone(field), 3_000)
            }
        }

        gone(field, "the field for the next idea")
    }

    /** Closes what `shown` finds (a sheet, a panel) with Back, as people do: the first Back may only hide the keyboard. */
    fun closeWithBack(shown: BySelector, what: String) {
        repeat(3) {
            if (device.hasObject(shown)) {
                device.pressBack()
                device.wait(Until.gone(shown), 3_000)
            }
        }

        gone(shown, what)
    }

    /**
     * Puts `words` in the field with the focus at once, through what the field offers accessibility services (its
     * "set text"), and waits until the field reads them. That's not a keyboard's way: on the map's page the planet's
     * field gets an `input` event alone, without the keys, `beforeinput` or composing a keyboard brings, which
     * [typeKeys] goes through. Keys sent to the phone go through its keyboard app first, which on a busy emulator, as
     * it starts, gives up on some of them (Gboard drops a key when the field takes over half a second to answer it),
     * or hands them on after an Enter sent later: the words would be cut short by the keyboard, not by Orbit.
     */
    fun type(words: String) {
        val field = By.clazz("android.widget.EditText").focused(true)
        shows(field, "a field to type in").text = words
        shows(By.clazz("android.widget.EditText").focused(true).text(words), "the field to read \"$words\"")
    }

    /**
     * Types `words` in the field with the focus as a keyboard's keys do, through the phone's keyboard app, which hands
     * each on as it does what's typed on it: on the map's page, the keys, `beforeinput` and `input`. Each key waits for
     * what the keyboard app needs to take it: the keyboard on screen (the keyboard app started, and taking the field's
     * text), and the field reading every key before it, so none goes while the field is still answering the last.
     */
    fun typeKeys(words: String) {
        eventually("the keyboard to type \"$words\" on") { keyboardShows() }
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        val keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

        for (typed in 1..words.length) {
            for (key in keys.getEvents(charArrayOf(words[typed - 1]))) {
                automation.injectInputEvent(KeyEvent.changeTimeRepeat(key, SystemClock.uptimeMillis(), 0), true)
            }

            val soFar = words.substring(0, typed)
            shows(By.clazz("android.widget.EditText").focused(true).text(soFar), "the field to read \"$soFar\"")
        }
    }

    /**
     * Stops Orbit's process as Android does to free memory once Orbit is in the background: Home, then the kill
     * Android's own low memory killer makes, which leaves Orbit's task and its saved screens for it to come back to.
     */
    fun stopInTheBackground(): String {
        val before = orbitProcess() ?: throw AssertionError("Orbit isn't running")
        device.pressHome()

        // Android kills only a process in the background: Orbit's is once it's stopped, a moment after Home shows.
        eventually("Orbit's process stopped in the background") {
            shell("am kill $ORBIT")
            orbitProcess() == null
        }

        return before
    }
}
