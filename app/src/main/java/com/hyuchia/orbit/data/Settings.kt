package com.hyuchia.orbit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { System, Light, Dark }

/** How the home screen orders maps. */
enum class SortOrder { Edited, Opened, Created, Title }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,

    /** Material You: the wallpaper's colors, on Android 12 and later. */
    val dynamicColor: Boolean = true,

    val haptics: Boolean = true,

    /** A flicked orbit brings the nearest planet to the top as it stops. */
    val snapPlanets: Boolean = true,

    /** The style new maps pick, or null for the app's colors. */
    val newMapStyle: String? = null,

    val sortOrder: SortOrder = SortOrder.Edited,

    /** Seconds a presentation stays on each idea before moving on; 0 waits for a tap. */
    val presentationSeconds: Int = 0,

    /** Other devices with Orbit open on the same Wi-Fi can offer maps here; each still has to be accepted. */
    val receiveFromNearby: Boolean = true,

    /** What devices on the same Wi-Fi see this one called, or null for the device's own name. */
    val nearbyName: String? = null,

    /** The map ideas were last caught into, from the tile or another app: where the next ones go. */
    val captureMapId: String? = null,

    /** The folder the maps are kept in as markdown files (see [FolderSync]), as the system's address for it. */
    val syncFolder: String? = null,

    /** Joining a presentation in browsers asks for a name; the last choice is the next presentation's. */
    val presentAskNames: Boolean = true,

    /** Joining a presentation in browsers takes a PIN besides the code. */
    val presentRequirePin: Boolean = false,

    /** How many browsers may follow a presentation at once; 0 for no limit. */
    val presentLimit: Int = 0,

    /** Opening a map on a computer takes a PIN besides the code. */
    val shareRequirePin: Boolean = false,

    /** How many devices may open a shared map at once; 0 for no limit. */
    val shareLimit: Int = 0,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    // A settings file that can't be read starts over instead of stopping the app: settings are quick to choose
    // again, and no map is in them.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private object Keys {
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val haptics = booleanPreferencesKey("haptics")
    val snapPlanets = booleanPreferencesKey("snap_planets")
    val newMapStyle = stringPreferencesKey("new_map_style")
    val sortOrder = stringPreferencesKey("sort_order")
    val presentationSeconds = intPreferencesKey("presentation_seconds")
    val receiveFromNearby = booleanPreferencesKey("receive_from_nearby")
    val nearbyName = stringPreferencesKey("nearby_name")
    val captureMapId = stringPreferencesKey("capture_map_id")
    val syncFolder = stringPreferencesKey("sync_folder")
    val presentAskNames = booleanPreferencesKey("present_ask_names")
    val presentRequirePin = booleanPreferencesKey("present_require_pin")
    val presentLimit = intPreferencesKey("present_limit")
    val shareRequirePin = booleanPreferencesKey("share_require_pin")
    val shareLimit = intPreferencesKey("share_limit")
}

class SettingsRepository(context: Context) {
    private val store = context.settingsStore

    val settings: Flow<AppSettings> = store.data
        // A read that fails (the disk, say) shows the defaults, and the next read tries again.
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map(::read)

    suspend fun update(change: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            val next = change(read(preferences))
            preferences[Keys.themeMode] = next.themeMode.name
            preferences[Keys.dynamicColor] = next.dynamicColor
            preferences[Keys.haptics] = next.haptics
            preferences[Keys.snapPlanets] = next.snapPlanets
            preferences[Keys.newMapStyle] = next.newMapStyle.orEmpty()
            preferences[Keys.sortOrder] = next.sortOrder.name
            preferences[Keys.presentationSeconds] = next.presentationSeconds
            preferences[Keys.receiveFromNearby] = next.receiveFromNearby
            preferences[Keys.nearbyName] = next.nearbyName.orEmpty()
            preferences[Keys.captureMapId] = next.captureMapId.orEmpty()
            preferences[Keys.syncFolder] = next.syncFolder.orEmpty()
            preferences[Keys.presentAskNames] = next.presentAskNames
            preferences[Keys.presentRequirePin] = next.presentRequirePin
            preferences[Keys.presentLimit] = next.presentLimit
            preferences[Keys.shareRequirePin] = next.shareRequirePin
            preferences[Keys.shareLimit] = next.shareLimit
        }
    }

    private fun read(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = preferences[Keys.themeMode]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            dynamicColor = preferences[Keys.dynamicColor] ?: defaults.dynamicColor,
            haptics = preferences[Keys.haptics] ?: defaults.haptics,
            snapPlanets = preferences[Keys.snapPlanets] ?: defaults.snapPlanets,
            newMapStyle = preferences[Keys.newMapStyle]?.ifEmpty { null },
            sortOrder = preferences[Keys.sortOrder]?.let { name -> SortOrder.entries.firstOrNull { it.name == name } } ?: defaults.sortOrder,
            presentationSeconds = preferences[Keys.presentationSeconds] ?: defaults.presentationSeconds,
            receiveFromNearby = preferences[Keys.receiveFromNearby] ?: defaults.receiveFromNearby,
            nearbyName = preferences[Keys.nearbyName]?.ifEmpty { null },
            captureMapId = preferences[Keys.captureMapId]?.ifEmpty { null },
            syncFolder = preferences[Keys.syncFolder]?.ifEmpty { null },
            presentAskNames = preferences[Keys.presentAskNames] ?: defaults.presentAskNames,
            presentRequirePin = preferences[Keys.presentRequirePin] ?: defaults.presentRequirePin,
            presentLimit = preferences[Keys.presentLimit] ?: defaults.presentLimit,
            shareRequirePin = preferences[Keys.shareRequirePin] ?: defaults.shareRequirePin,
            shareLimit = preferences[Keys.shareLimit] ?: defaults.shareLimit,
        )
    }
}
