package com.hyuchia.orbit.settings

import com.hyuchia.orbit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportLinkTest {
    @Test
    fun theLibreBuildLinksToTheSupportPage() {
        assertEquals("https://orbit.hyuchia.com/support", BuildConfig.SUPPORT_URL)
    }
}
