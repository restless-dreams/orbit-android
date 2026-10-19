package com.hyuchia.orbit.settings

import com.hyuchia.orbit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportLinkTest {
    @Test
    fun thePlayBuildNeverLinksToDonations() {
        // Google Play doesn't allow leading people to pay outside its billing, so About has no "Support Orbit".
        assertEquals("", BuildConfig.SUPPORT_URL)
    }

    @Test
    fun thePlayBuildsWebsiteLinkLeavesOutTheSupportSection() {
        // The home page asks for support, so About opens the app's own page instead.
        assertEquals("https://orbit.hyuchia.com/android", BuildConfig.WEBSITE_URL)
    }
}
