package com.hyuchia.orbit.page

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The build of Orbit the map's pages carry, as `web/build.ts` writes it into
 * `assets/map/orbit.json`: its version, its commit, and the newest map format
 * it reads (its `SUPPORTED_SCHEMA_VERSION`, which [com.hyuchia.orbit.data.MAP_FORMAT] matches).
 */
@Serializable
data class OrbitBuild(val version: String, val commit: String = "", val format: Int? = null) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The build in the app's assets, or null when its file can't be read. */
        fun bundled(context: Context): OrbitBuild? = runCatching {
            json.decodeFromString(serializer(), context.assets.open("map/orbit.json").bufferedReader().use { it.readText() })
        }.getOrNull()
    }
}
