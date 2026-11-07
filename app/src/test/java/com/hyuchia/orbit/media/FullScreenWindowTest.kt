package com.hyuchia.orbit.media

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

/**
 * A picture full screen is a window of its own, which Compose marks as
 * Orbit's own window only as it opens: on Android 12 and earlier, it's kept
 * out of Recents and screenshots itself, following what's private on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class)
class FullScreenWindowTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: OrbitApplication get() = ApplicationProvider.getApplicationContext()
    private val android = Build.VERSION.SDK_INT

    @After
    fun androidAsItWas() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", android)
    }

    /** The Android the code sees, as on a phone that runs it: the tests' own is Android 16's. */
    private fun onAndroid(version: Int) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", version)
    }

    private fun picture() {
        compose.setContent { FullScreenWindow(onClosed = {}) { Box(Modifier.fillMaxSize()) } }
        compose.waitForIdle()
    }

    private fun kept(): Boolean = ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    /** Something private comes on screen under it, or goes, as a private map unlocked or locked again under it. */
    private fun onScreen(private: Boolean) {
        compose.runOnUiThread {
            if (private) {
                app.container.privateMaps.shown()
            } else {
                app.container.privateMaps.hidden()
            }
        }

        compose.waitForIdle()
    }

    @Test
    fun `on Android 12 and earlier, an open picture is kept out as soon as something private shows, and only while it does`() {
        onAndroid(Build.VERSION_CODES.S_V2)
        picture()
        assertFalse(kept())

        // As a private map under it is unlocked again, say.
        onScreen(private = true)
        assertTrue(kept())

        onScreen(private = false)
        assertFalse(kept())
    }

    @Test
    fun `on Android 10, a picture opened over something private is kept out from the start`() {
        onAndroid(Build.VERSION_CODES.Q)
        onScreen(private = true)
        picture()
        assertTrue(kept())
    }

    @Test
    fun `on Android 13 and later, Recents alone is kept from it, as from all of Orbit, and screenshots aren't`() {
        onAndroid(Build.VERSION_CODES.TIRAMISU)
        onScreen(private = true)
        picture()
        assertFalse(kept())
    }
}
