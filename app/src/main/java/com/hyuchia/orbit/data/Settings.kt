package com.hyuchia.orbit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.study.NEW_CARDS_A_DAY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

enum class ThemeMode { System, Light, Dark }

/** How the home screen orders maps. */
enum class SortOrder { Edited, Opened, Created, Title }

/** How long Orbit can be off screen before private maps lock again. */
enum class PrivateLockDelay(val seconds: Long) {
    RightAway(0),
    OneMinute(60),
    FiveMinutes(5 * 60),
    FifteenMinutes(15 * 60),
}

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

    /** Copies no store updates (the APK from the website and GitHub) look for new versions once a day: see [Updates]. */
    val checkForUpdates: Boolean = true,

    /** How long Orbit can be off screen before private maps lock again; the screen turning off locks them at once. */
    val lockPrivateMapsAfter: PrivateLockDelay = PrivateLockDelay.OneMinute,

    /** Locked, private maps are left out of Home altogether, rather than keeping their place there locked. */
    val hideLockedPrivateMaps: Boolean = false,

    /** Home's card saying the maps stay on this device, unless a folder syncs them, was put away. */
    val storageNoteDismissed: Boolean = false,

    /** The notices about other devices in the folder that were put away (see [FolderNotice.key]), so only new ones show. */
    val folderNoticesDismissed: Set<String> = emptySet(),

    /** How many new cards a map brings into each day's study, after the ones that are due: the learner's choice. */
    val newCardsADay: Int = NEW_CARDS_A_DAY,
)

// A settings file that can't be read starts over instead of stopping the app: settings are quick to choose
// again, and no map is in them.
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The settings in `file` (`datastore/settings.preferences_pb` in the app's files), as the app reads them: for tests. */
internal fun settingsStoreIn(file: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    produceFile = { file },
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
    val checkForUpdates = booleanPreferencesKey("check_for_updates")
    val lockPrivateMapsAfter = stringPreferencesKey("lock_private_maps_after")
    val hideLockedPrivateMaps = booleanPreferencesKey("hide_locked_private_maps")
    val storageNoteDismissed = booleanPreferencesKey("storage_note_dismissed")
    val folderNoticesDismissed = stringSetPreferencesKey("folder_notices_dismissed")
    val newCardsADay = intPreferencesKey("new_cards_a_day")
}

/** The value at `key`; null without one, or with one of another type. */
private inline fun <reified T> Preferences.valueOf(key: Preferences.Key<T>): T? = asMap()[key] as? T

class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsStore)

    val settings: Flow<AppSettings> = store.data
        // A read that fails (the disk, say) shows the defaults, and the next read tries again.
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map(::read)

    /**
     * Changes the settings. Only the keys whose value changes are written:
     * one holding a choice this version can't read, which reads as the
     * default, stays as the newer version that chose it left it. Storage
     * that's full or gone leaves them as they were, which the log says,
     * rather than stopping Orbit.
     */
    suspend fun update(change: (AppSettings) -> AppSettings) {
        try {
            write(change)
        } catch (failed: IOException) {
            Logs.warn("Settings", "Couldn't write the settings", failed)
        }
    }

    private suspend fun write(change: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            val current = read(preferences)
            val next = change(current)

            fun <T> keep(key: Preferences.Key<T>, value: (AppSettings) -> T) {
                if (value(next) != value(current)) {
                    preferences[key] = value(next)
                }
            }

            keep(Keys.themeMode) { it.themeMode.name }
            keep(Keys.dynamicColor) { it.dynamicColor }
            keep(Keys.haptics) { it.haptics }
            keep(Keys.snapPlanets) { it.snapPlanets }
            keep(Keys.newMapStyle) { it.newMapStyle.orEmpty() }
            keep(Keys.sortOrder) { it.sortOrder.name }
            keep(Keys.presentationSeconds) { it.presentationSeconds }
            keep(Keys.receiveFromNearby) { it.receiveFromNearby }
            keep(Keys.nearbyName) { it.nearbyName.orEmpty() }
            keep(Keys.captureMapId) { it.captureMapId.orEmpty() }
            keep(Keys.syncFolder) { it.syncFolder.orEmpty() }
            keep(Keys.presentAskNames) { it.presentAskNames }
            keep(Keys.presentRequirePin) { it.presentRequirePin }
            keep(Keys.presentLimit) { it.presentLimit }
            keep(Keys.shareRequirePin) { it.shareRequirePin }
            keep(Keys.shareLimit) { it.shareLimit }
            keep(Keys.checkForUpdates) { it.checkForUpdates }
            keep(Keys.lockPrivateMapsAfter) { it.lockPrivateMapsAfter.name }
            keep(Keys.hideLockedPrivateMaps) { it.hideLockedPrivateMaps }
            keep(Keys.storageNoteDismissed) { it.storageNoteDismissed }
            keep(Keys.folderNoticesDismissed) { it.folderNoticesDismissed }
            keep(Keys.newCardsADay) { it.newCardsADay }
        }
    }

    /** The settings `preferences` hold: a key missing, or holding a value of another type (a newer version's, say), reads as its default. */
    private fun read(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = preferences.valueOf(Keys.themeMode)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            dynamicColor = preferences.valueOf(Keys.dynamicColor) ?: defaults.dynamicColor,
            haptics = preferences.valueOf(Keys.haptics) ?: defaults.haptics,
            snapPlanets = preferences.valueOf(Keys.snapPlanets) ?: defaults.snapPlanets,
            newMapStyle = preferences.valueOf(Keys.newMapStyle)?.ifEmpty { null },
            sortOrder = preferences.valueOf(Keys.sortOrder)?.let { name -> SortOrder.entries.firstOrNull { it.name == name } } ?: defaults.sortOrder,
            presentationSeconds = preferences.valueOf(Keys.presentationSeconds) ?: defaults.presentationSeconds,
            receiveFromNearby = preferences.valueOf(Keys.receiveFromNearby) ?: defaults.receiveFromNearby,
            nearbyName = preferences.valueOf(Keys.nearbyName)?.ifEmpty { null },
            captureMapId = preferences.valueOf(Keys.captureMapId)?.ifEmpty { null },
            syncFolder = preferences.valueOf(Keys.syncFolder)?.ifEmpty { null },
            presentAskNames = preferences.valueOf(Keys.presentAskNames) ?: defaults.presentAskNames,
            presentRequirePin = preferences.valueOf(Keys.presentRequirePin) ?: defaults.presentRequirePin,
            presentLimit = preferences.valueOf(Keys.presentLimit) ?: defaults.presentLimit,
            shareRequirePin = preferences.valueOf(Keys.shareRequirePin) ?: defaults.shareRequirePin,
            shareLimit = preferences.valueOf(Keys.shareLimit) ?: defaults.shareLimit,
            checkForUpdates = preferences.valueOf(Keys.checkForUpdates) ?: defaults.checkForUpdates,
            lockPrivateMapsAfter = preferences.valueOf(Keys.lockPrivateMapsAfter)?.let { name -> PrivateLockDelay.entries.firstOrNull { it.name == name } }
                ?: defaults.lockPrivateMapsAfter,
            hideLockedPrivateMaps = preferences.valueOf(Keys.hideLockedPrivateMaps) ?: defaults.hideLockedPrivateMaps,
            storageNoteDismissed = preferences.valueOf(Keys.storageNoteDismissed) ?: defaults.storageNoteDismissed,
            folderNoticesDismissed = (preferences.asMap()[Keys.folderNoticesDismissed] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: defaults.folderNoticesDismissed,
            newCardsADay = preferences.valueOf(Keys.newCardsADay)?.takeIf { it > 0 } ?: defaults.newCardsADay,
        )
    }
}
