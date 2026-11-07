package com.hyuchia.orbit.privacy

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.ui.hideShortcut
import com.hyuchia.orbit.ui.offerShortcut
import com.hyuchia.orbit.widget.refreshWidgetPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Private maps: hidden on Home, in widgets, shortcuts and Recents, and
 * opened with the phone's own fingerprint, face or screen lock, which
 * Android checks (see [Unlocking]). They're locked in Orbit, not encrypted:
 * their files stay plain markdown, for sync apps and backups.
 *
 * Without a screen lock on the phone there's nothing to open them with, so
 * they show as any other map, and making a map private asks for one first.
 */
class PrivateMaps(
    private val context: Context,
    private val maps: MapRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    // The time since the phone started, which no one can set back: the wall clock can be, without a screen lock.
    clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val secure = MutableStateFlow(hasScreenLock())

    val lock = PrivateLock(clock, screenLock = secure.value)

    /**
     * This run of Orbit, from its start until Android closes it. A private map shown in an earlier run
     * starts over in this one, behind its lock (see [PrivateMapGate]): screens that come back after
     * Android closed Orbit remember having shown it, but nothing of it is loaded anymore.
     */
    val run: String = UUID.randomUUID().toString()

    /** Whether the phone has a screen lock (a PIN, pattern or password) to open private maps with. */
    val screenLock: StateFlow<Boolean> = secure.asStateFlow()

    /** What private maps show now; never locked while the phone has no screen lock. */
    val view: StateFlow<PrivateView> = lock.view

    /** How many of the screens showing now show something private (see [PrivateOnScreen]). */
    private val screens = MutableStateFlow(0)

    /**
     * Whether something private is on screen now: a private map open, or
     * one on Home or in the trash. Recents then gets no picture of Orbit
     * (see [keepPrivateMapsOutOfRecents]).
     */
    val showing: Flow<Boolean> = screens.map { it > 0 }.distinctUntilChanged()

    /** Whether something private is on screen right now (see [showing]). */
    val showingNow: Boolean get() = screens.value > 0

    /** A screen started showing something private; [hidden] when it stops. */
    internal fun shown() {
        screens.update { it + 1 }
    }

    internal fun hidden() {
        screens.update { it - 1 }
    }

    /** Whether `map` shows locked now. */
    fun hides(map: MapSummary): Boolean = view.value.hides(map)

    /**
     * Whether the map `id` shows locked now, as its details and the lock are
     * now, not as they were when something began: what's on its way out of
     * it, a file to share say, which can take seconds to make, asks right
     * before it goes ([com.hyuchia.orbit.ui.shareExport]). One that's gone
     * counts as locked, so nothing of it goes.
     */
    fun hidesNow(id: String): Boolean = maps.summary(id)?.let(::hides) ?: true

    /**
     * Follows Orbit on and off screen, and the screen turning off, to lock
     * private maps again as the settings say; and the phone's screen lock,
     * which can be set or removed while Orbit is away.
     */
    fun start(lifecycle: Lifecycle) {
        scope.launch {
            settings.settings.map { it.lockPrivateMapsAfter }.distinctUntilChanged().collect { lock.delay = it.seconds * 1000 }
        }

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    refreshScreenLock()
                    lock.returned()
                }

                override fun onStop(owner: LifecycleOwner) {
                    lock.left()
                }
            },
        )

        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    lock.screenOff()
                }
            }
        }

        ContextCompat.registerReceiver(context, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** The phone's screen lock, looked at again: after its settings were opened from Orbit, say. */
    fun refreshScreenLock() {
        secure.value = hasScreenLock()
        lock.screenLockIs(secure.value)
    }

    /**
     * Makes a map private, or not. One made private while the others are
     * locked stays as it is until they lock again: it's on screen, its
     * owner's. A private map leaves the launcher's shortcuts, and one pinned
     * to the home screen loses its name; it gets it back once it isn't private.
     */
    suspend fun setPrivate(map: MapSummary, private: Boolean) {
        if (private) {
            lock.keepOpen(map.id)
        }

        maps.setPrivate(map.id, private)

        if (private) {
            hideShortcut(context, map.id, context.resources.coverTitle(map.metadata.cover))
        } else {
            offerShortcut(context, map.id, map.title)
        }

        // The launcher's list of widgets may show this map: drawn again, it shows it private, or as it is now.
        scope.launch { refreshWidgetPreview(context, madePrivate = private) }
    }

    private fun hasScreenLock(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}
