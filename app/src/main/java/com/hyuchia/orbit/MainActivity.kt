package com.hyuchia.orbit

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.ui.KeyboardShortcutsDialog
import com.hyuchia.orbit.ui.LocalShortcuts
import com.hyuchia.orbit.ui.ShortcutRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The action of the "New map" shortcut. */
const val ACTION_NEW_MAP = "com.hyuchia.orbit.NEW_MAP"

/** The action of a map's own shortcut; the map's id is in [EXTRA_MAP_ID]. */
const val ACTION_OPEN_MAP = "com.hyuchia.orbit.OPEN_MAP"
const val EXTRA_MAP_ID = "map"

class MainActivity : ComponentActivity() {
    // Each request is handled once, even when it arrives before the screens are up.
    private val requests = Channel<AppRequest>(Channel.BUFFERED)
    private val incoming = requests.receiveAsFlow()

    /** The keyboard shortcuts of the screens on display. */
    private val shortcuts = ShortcutRegistry()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = container
        val settings = app.settings.settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        // The splash stays until the maps and settings are read, so the first screen is the real one.
        splash.setKeepOnScreenCondition { app.maps.maps.value == null || settings.value == null }
        lifecycleScope.launch { app.maps.load() }

        // A page ready before any map opens, made once the first screen is up.
        Looper.myQueue().addIdleHandler {
            app.pages.warm()
            false
        }

        if (savedInstanceState == null) {
            handle(intent)
        }

        // While Orbit is open, nearby devices can find it, by the name chosen, and offer it maps, as the settings allow.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    settings.filterNotNull().map { it.receiveFromNearby to it.nearbyName }.distinctUntilChanged().collect { (accepting, name) ->
                        // Named first, so the device's own name is never announced when another was chosen.
                        app.nearby.rename(name)
                        app.nearby.start(accepting = accepting)
                    }
                } finally {
                    app.nearby.stop()
                }
            }
        }

        setContent {
            val current by settings.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalAppSettings provides (current ?: AppSettings()), LocalShortcuts provides shortcuts) {
                val dark = appIsDark()

                DisposableEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }

                OrbitTheme(dark = dark) {
                    OrbitApp(incoming)

                    if (shortcuts.listOpen) {
                        KeyboardShortcutsDialog(shortcuts.groups, onDismiss = { shortcuts.listOpen = false })
                    }
                }
            }
        }
    }

    /**
     * Shortcuts whose keys no text field uses come first, wherever the focus
     * is; the others get the keys nothing on screen used. Keys the map's page
     * doesn't use come back through [OrbitPage]'s own route.
     *
     * `Activity.dispatchKeyEvent` is the platform's own; AndroidX only marks
     * its override restricted, and the keys still go through it.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        shortcuts.dispatch(event, anywhere = true) || super.dispatchKeyEvent(event) || shortcuts.dispatch(event, anywhere = false)

    /** Keys nothing on screen used, such as those the map's page passed on. */
    fun dispatchUnusedKey(event: KeyEvent): Boolean = shortcuts.dispatch(event, anywhere = false)

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += shortcuts.forSystem()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Shortcuts, files opened with Orbit and text shared to it. */
    private fun handle(intent: Intent?) {
        val request = when (intent?.action) {
            ACTION_NEW_MAP -> AppRequest.NewMap
            ACTION_OPEN_MAP -> intent.getStringExtra(EXTRA_MAP_ID)?.let(AppRequest::OpenMap)
            Intent.ACTION_VIEW -> intent.data?.let(AppRequest::ImportFile)
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                when {
                    stream != null -> AppRequest.ImportFile(stream)
                    text != null -> AppRequest.ImportText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
                    else -> null
                }
            }
            else -> null
        } ?: return

        requests.trySend(request)
    }
}
