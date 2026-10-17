package com.hyuchia.orbit.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the app's code that the journeys below run, for Android to compile
 * ahead of time instead of as the app runs. `./gradlew :app:generateBaselineProfile`
 * runs it and writes the result to app/src/main/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Each recording starts as a fresh install does, with the welcome map and the samples and nothing else. */
    @Before
    fun startFresh() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("pm clear $PACKAGE_NAME")
    }

    /**
     * Opening Orbit from the launcher. Also kept as the startup profile, which
     * the build uses to lay out the app's code for a quicker start.
     */
    @Test
    fun startup() {
        rule.collect(PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startOnYourMaps()
        }
    }

    /** Looking through the maps, opening one, its styles, and back. */
    @Test
    fun journeys() {
        rule.collect(PACKAGE_NAME) {
            pressHome()
            startOnYourMaps()
            scrollYourMaps()
            openMap(SAMPLE_MAP)
            openStyleAndLayout()
            backToYourMaps()
        }
    }
}
