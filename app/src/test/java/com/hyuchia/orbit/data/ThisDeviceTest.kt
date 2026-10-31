package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThisDeviceTest {
    @Test
    fun `the install that made the id keeps it`() {
        assertEquals("5d6e7f80", keptDeviceId("5d6e7f80", keptInstall = "1000", installed = "1000"))
    }

    @Test
    fun `an id copied to another install with Orbit's files isn't that install's`() {
        assertNull(keptDeviceId("5d6e7f80", keptInstall = "1000", installed = "2000"))
    }

    @Test
    fun `an id kept from before installs were written down is this install's`() {
        assertEquals("5d6e7f80", keptDeviceId("5d6e7f80", keptInstall = null, installed = "2000"))
        assertNull(keptDeviceId(null, keptInstall = "1000", installed = "1000"))
    }
}
