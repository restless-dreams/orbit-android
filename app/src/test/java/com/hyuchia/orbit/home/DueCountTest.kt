package com.hyuchia.orbit.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.study.StudyCard
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a map's card on Home says is due: the cards due today, here or on
 * another device, but not ideas that aren't cards anymore, and more once
 * the day moves on.
 */
@RunWith(AndroidJUnit4::class)
// A phone's size (Pixel 8's), with the app's container, which the card's picture comes from.
@Config(application = OrbitApplication::class, qualifiers = "w411dp-h891dp-port-mdpi")
class DueCountTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = MapCardActions(
        onOpen = {},
        onPresent = {},
        onStudy = {},
        onPin = {},
        onRename = {},
        onTags = {},
        onDuplicate = {},
        onShare = {},
        onSend = {},
        onDelete = {},
    )

    @Test
    fun `a map's card counts its cards due today, but no idea that isn't a card, and moves on with the day`() {
        val today = 20_000L

        val metadata = MapMetadata(
            // Ser is due tomorrow; Soy, overdue, isn't a card anymore; Estar is due today on the tablet.
            study = mapOf("ser" to StudyCard(box = 1, due = today + 1), "soy" to StudyCard(due = today - 3)),
            studyElsewhere = mapOf("tablet" to mapOf("estar" to StudyCard(due = today))),
            studyNotCards = listOf("soy"),
        )

        val map = MapSummary("verbs", MapOutline("Spanish verbs", branches = 2, ideas = 4), 0, metadata, "", "verbs")
        var day by mutableLongStateOf(today)

        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false)) {
                OrbitTheme(dark = false) {
                    MapCard(map, actions, day)
                }
            }
        }

        compose.onNodeWithText("1 to study").assertExists()

        // Midnight.
        day = today + 1
        compose.waitForIdle()
        compose.onNodeWithText("2 to study").assertExists()
    }
}
