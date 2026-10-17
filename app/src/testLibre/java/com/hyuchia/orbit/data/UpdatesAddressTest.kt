package com.hyuchia.orbit.data

import com.hyuchia.orbit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatesAddressTest {
    @Test
    fun onlyTheBuildForDownloadsLooksForReleases() {
        // F-Droid's and AppGallery's builds leave updates to their stores: only the APK for the website and GitHub
        // looks, built with -Porbit.checkForUpdates=true.
        assertEquals("", BuildConfig.UPDATES_URL)
    }
}
