package com.hyuchia.orbit.home

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.CoverDialog
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** What's typed for a private map on Home, its title, tags and cover, asks keyboards not to learn from it, as the map does. */
@RunWith(AndroidJUnit4::class)
// The cover dialog saves through the app's container.
@Config(application = OrbitApplication::class)
class HomeDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun map(title: String, private: Boolean) = MapSummary(title.lowercase(), MapOutline(title, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    private fun show(dialog: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppSettings provides AppSettings(dynamicColor = false)) {
                OrbitTheme(dark = false) { dialog() }
            }
        }
    }

    /** Whether keyboards may learn from the field with the keyboard's focus, in the dialog or sheet that's up. */
    private fun keyboardsLearn(): Boolean {
        compose.waitForIdle()
        val focused = ShadowDialog.getLatestDialog().window!!.decorView.findFocus()!!
        val asked = EditorInfo()
        compose.runOnUiThread { focused.onCreateInputConnection(asked) }
        return asked.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
    }

    @Test
    fun `a private map's title, renamed on Home, asks keyboards not to learn from it`() {
        show { RenameMapDialog(map("Secret journal", private = true), onRename = {}, onDismiss = {}) }
        assertFalse(keyboardsLearn())
    }

    @Test
    fun `another map's title is learned from, as keyboards do`() {
        show { RenameMapDialog(map("Groceries", private = false), onRename = {}, onDismiss = {}) }
        assertTrue(keyboardsLearn())
    }

    @Test
    fun `a private map's tags ask keyboards not to learn from them`() {
        show { MapTagsSheet(map("Secret journal", private = true), known = listOf("Work"), onChange = {}, onDismiss = {}) }
        compose.onNodeWithText("New tag").performClick()
        assertFalse(keyboardsLearn())
    }

    @Test
    fun `a private map's cover asks keyboards not to learn from it`() {
        show { CoverDialog(map("Secret journal", private = true), onDismiss = {}) }
        compose.onNodeWithText("Name").performClick()
        assertFalse(keyboardsLearn())
    }
}
