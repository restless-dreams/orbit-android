package com.hyuchia.orbit.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Files

class SettingsTest {
    private val folder: File = Files.createTempDirectory("orbit-settings").toFile()
    private val store = settingsStoreIn(File(folder, "settings.preferences_pb"))

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `private maps lock again after a minute until another time is chosen, which is kept by its name`() = runTest {
        val settings = SettingsRepository(store)
        assertEquals(PrivateLockDelay.OneMinute, settings.settings.first().lockPrivateMapsAfter)

        settings.update { it.copy(lockPrivateMapsAfter = PrivateLockDelay.RightAway) }
        assertEquals(PrivateLockDelay.RightAway, settings.settings.first().lockPrivateMapsAfter)

        // A choice a newer version added reads as the default here.
        store.edit { it[stringPreferencesKey("lock_private_maps_after")] = "AfterAnHour" }
        assertEquals(PrivateLockDelay.OneMinute, settings.settings.first().lockPrivateMapsAfter)
    }

    @Test
    fun `a choice a newer version made stays as it was while other settings change here`() = runTest {
        val settings = SettingsRepository(store)
        store.edit { it[stringPreferencesKey("theme_mode")] = "Sepia" }
        assertEquals(ThemeMode.System, settings.settings.first().themeMode)

        settings.update { it.copy(haptics = false) }
        assertEquals("Sepia", store.data.first()[stringPreferencesKey("theme_mode")])
        assertEquals(false, settings.settings.first().haptics)

        // Chosen here, it's this version's choice.
        settings.update { it.copy(themeMode = ThemeMode.Dark) }
        assertEquals("Dark", store.data.first()[stringPreferencesKey("theme_mode")])
    }

    @Test
    fun `a setting a newer version keeps as another type reads as the default, and stays as it was`() = runTest {
        val settings = SettingsRepository(store)
        store.edit { it[stringPreferencesKey("presentation_seconds")] = "slow" }
        assertEquals(AppSettings().presentationSeconds, settings.settings.first().presentationSeconds)

        settings.update { it.copy(snapPlanets = !it.snapPlanets) }
        assertEquals("slow", store.data.first()[stringPreferencesKey("presentation_seconds")])
    }

    @Test
    fun `a map brings 20 new cards a day until the learner chooses how many, and a number that makes no sense reads as 20`() = runTest {
        val settings = SettingsRepository(store)
        assertEquals(20, settings.settings.first().newCardsADay)

        settings.update { it.copy(newCardsADay = 5) }
        assertEquals(5, settings.settings.first().newCardsADay)
        // Other settings changing leave it as chosen.
        settings.update { it.copy(haptics = false) }
        assertEquals(5, settings.settings.first().newCardsADay)

        // A number another version chose, Settings' choices or not, is kept as it is.
        store.edit { it[intPreferencesKey("new_cards_a_day")] = 15 }
        assertEquals(15, settings.settings.first().newCardsADay)

        store.edit { it[intPreferencesKey("new_cards_a_day")] = 0 }
        assertEquals(20, settings.settings.first().newCardsADay)
    }

    @Test
    fun `locked private maps keep their place on Home until they're chosen to hide`() = runTest {
        val settings = SettingsRepository(store)
        assertEquals(false, settings.settings.first().hideLockedPrivateMaps)

        settings.update { it.copy(hideLockedPrivateMaps = true) }
        assertEquals(true, settings.settings.first().hideLockedPrivateMaps)
        assertEquals(true, SettingsRepository(store).settings.first().hideLockedPrivateMaps)

        // Settings another version wrote keep this one as it was.
        settings.update { it.copy(haptics = false) }
        assertEquals(true, settings.settings.first().hideLockedPrivateMaps)
    }

    @Test
    fun `every setting chosen is kept, and read back as it was chosen`() = runTest {
        val chosen = AppSettings(
            themeMode = ThemeMode.Dark,
            dynamicColor = false,
            haptics = false,
            snapPlanets = false,
            newMapStyle = "night",
            sortOrder = SortOrder.Title,
            presentationSeconds = 8,
            receiveFromNearby = false,
            nearbyName = "Ana's tablet",
            captureMapId = "lisbon",
            syncFolder = "content://com.android.externalstorage.documents/tree/primary%3AMaps",
            presentAskNames = false,
            presentRequirePin = true,
            presentLimit = 12,
            shareRequirePin = true,
            shareLimit = 3,
            checkForUpdates = false,
            lockPrivateMapsAfter = PrivateLockDelay.FifteenMinutes,
            hideLockedPrivateMaps = true,
            storageNoteDismissed = true,
            folderNoticesDismissed = setOf("older:pixel:5:1"),
            newCardsADay = 30,
        )

        // Each one other than its default, so a setting added without its line in update() or read() fails here.
        val defaults = AppSettings()

        AppSettings::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { field ->
            field.isAccessible = true
            assertNotEquals("${field.name} is chosen here as its default", field.get(defaults), field.get(chosen))
        }

        val settings = SettingsRepository(store)
        settings.update { chosen }
        assertEquals(chosen, settings.settings.first())
        assertEquals(chosen, SettingsRepository(store).settings.first())

        // Changing one leaves the others as they were chosen.
        settings.update { it.copy(haptics = true) }
        assertEquals(chosen.copy(haptics = true), settings.settings.first())
    }
}
