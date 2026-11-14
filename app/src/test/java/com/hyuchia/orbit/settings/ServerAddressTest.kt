package com.hyuchia.orbit.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.theme.OrbitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The address Settings shows for this device, while it listens: the one that works now. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class ServerAddressTest {
    @get:Rule
    val compose = createComposeRule()

    @Volatile
    private var address: String? = "192.168.1.5:8766"

    private fun shows(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the address follows the phone from one network to another while it listens all along`() {
        var listening by mutableStateOf(true)
        compose.setContent { OrbitTheme(dark = false) { ServerAddress(listening) { address } } }
        compose.onNodeWithText("This device's address is 192.168.1.5:8766").assertExists()

        // From the Wi-Fi straight to the phone's own hotspot: it listens throughout.
        address = "10.0.0.7:8766"
        compose.waitUntil(10_000) { shows("This device's address is 10.0.0.7:8766") }

        // Off every network the others share, it says how to reach them.
        address = null
        compose.waitUntil(10_000) { shows("Connect it to Wi-Fi to reach your other devices.") }

        // Not listening, it says nothing.
        listening = false
        compose.waitForIdle()
        compose.onNodeWithText("Connect it to Wi-Fi to reach your other devices.").assertDoesNotExist()
    }
}
