package com.hyuchia.orbit.settings

import com.hyuchia.orbit.page.OrbitBuild
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LicensesTest {
    @Test
    fun `Orbit's version on the licenses screen is the one the pages are built with`() {
        val orbit = OrbitBuild(version = "0.4.0", commit = "abc1234")
        assertEquals("0.4.0", shownVersion(ORBIT_LIBRARY, null, orbit))
        assertEquals("2.11.0", shownVersion("androidx.lifecycle:lifecycle-runtime", "2.11.0", orbit))
        // Without the pages' build to read, Orbit's row shows only its license.
        assertNull(shownVersion(ORBIT_LIBRARY, null, null))
    }

    @Test
    fun `Orbit's entry keeps no version of its own to go stale`() {
        val entry = Json.parseToJsonElement(File("config/libraries/orbit.json").readText()).jsonObject
        assertEquals(ORBIT_LIBRARY, entry.getValue("uniqueId").jsonPrimitive.content)
        assertFalse("A version here would be shown instead of the pages' Orbit", "artifactVersion" in entry)
    }
}
