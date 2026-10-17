package com.hyuchia.orbit.data

import com.hyuchia.orbit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatesAddressTest {
    @Test
    fun thePlayBuildNeverLooksForReleases() {
        // Google Play updates the app, and doesn't allow it to be updated any other way.
        assertEquals("", BuildConfig.UPDATES_URL)
    }
}
