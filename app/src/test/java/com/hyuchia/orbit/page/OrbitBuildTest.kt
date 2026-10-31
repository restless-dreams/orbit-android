package com.hyuchia.orbit.page

import com.hyuchia.orbit.data.MAP_FORMAT
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The Orbit the pages are built with, as `web/build.ts` writes it, against what the app says of it. */
class OrbitBuildTest {
    private val build = Json { ignoreUnknownKeys = true }.decodeFromString(OrbitBuild.serializer(), File("src/main/assets/map/orbit.json").readText())

    @Test
    fun `the folder's records say the app reads the map format its pages' Orbit reads`() {
        // Raised by hand, MAP_FORMAT would have this device tell the others it reads what its Orbit can't, or the reverse.
        assertEquals("Rebuild the pages, or raise MAP_FORMAT with Orbit's SUPPORTED_SCHEMA_VERSION", build.format, MAP_FORMAT)
    }
}
