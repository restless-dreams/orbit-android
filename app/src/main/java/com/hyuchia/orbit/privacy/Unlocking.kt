package com.hyuchia.orbit.privacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.theme.LocalAppSettings

/** How asking for the screen lock went. */
sealed interface Unlocked {
    /** It was the phone's owner: private maps are open. */
    data object Yes : Unlocked

    /** They closed the prompt, or Orbit left the screen meanwhile: nothing changed. */
    data object Cancelled : Unlocked

    /** The phone couldn't ask, and says why in its own words: too many tries, say. */
    data class Failed(val reason: String) : Unlocked
}

/**
 * Asks for the phone's own fingerprint, face or screen lock, with Android's
 * own prompt: Orbit never sees any of them, only whether it was the phone's
 * owner. The platform's prompt works in every activity and on every Android
 * Orbit runs on (10 and later), with no library and no Google services.
 *
 * Face unlock counts, and a PIN, pattern or password always works when the
 * fingerprint or face doesn't. Without a screen lock, there's nothing to
 * ask: private maps aren't locked then (see [PrivateMaps]).
 */
@Stable
class Unlocking internal constructor(private val activity: Activity?, private val maps: PrivateMaps) {
    private var cancel: CancellationSignal? = null

    /** Whether the prompt is up now. */
    var asking by mutableStateOf(false)
        private set

    /** Opens private maps: at once when they're open already, or there's nothing to open them with; otherwise after the prompt. */
    fun ask(then: (Unlocked) -> Unit = {}) {
        if (!maps.view.value.locked) {
            then(Unlocked.Yes)
            return
        }

        if (activity == null || asking) {
            return
        }

        val signal = CancellationSignal()
        cancel = signal
        asking = true
        maps.lock.asking = true

        fun finish(result: Unlocked) {
            asking = false
            maps.lock.asking = false
            cancel = null

            if (result == Unlocked.Yes) {
                maps.lock.unlock()
            } else if (result is Unlocked.Failed) {
                // A screen lock removed meanwhile leaves nothing to unlock with: the maps then show as any other.
                maps.refreshScreenLock()
            }

            then(result)
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(Unlocked.Yes)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED || errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED

                finish(if (cancelled) Unlocked.Cancelled else Unlocked.Failed(errString.toString()))
            }
        }

        val builder = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.private_unlock_private_maps))
            // Face unlock opens them without a tap more, as the phone's own lock screen does.
            .setConfirmationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        try {
            builder.build().authenticate(signal, activity.mainExecutor, callback)
        } catch (failed: RuntimeException) {
            // A phone whose prompt refuses to start (a screen lock removed a moment ago, say) is asked nothing.
            finish(Unlocked.Failed(failed.message.orEmpty()))
        }
    }

    /** Takes the prompt away, as the screen asking for it goes. */
    internal fun stop() {
        cancel?.cancel()
    }
}

/** A way to ask for the screen lock from this screen; the prompt goes with the screen. */
@Composable
fun rememberUnlocking(): Unlocking {
    val context = LocalContext.current
    val unlocking = remember(context) { Unlocking(context.activity(), context.container.privateMaps) }
    DisposableEffect(unlocking) { onDispose { unlocking.stop() } }
    return unlocking
}

/**
 * For what takes every map out of Orbit, private ones included, such as Export all or a folder to keep maps in:
 * returns what runs it, at once while no private map is locked, and otherwise once the phone's screen lock says it's
 * the owner, which opens private maps as their own lock does (see [asksBeforeEveryMap]). A locked private map goes
 * nowhere unasked. `onFailed` hears why the phone couldn't ask, in its own words.
 */
@Composable
fun rememberUnlockedForEveryMap(onFailed: (String) -> Unit): (() -> Unit) -> Unit {
    val container = LocalContext.current.container
    val unlocking = rememberUnlocking()
    val view by container.privateMaps.view.collectAsStateWithLifecycle()
    val maps by container.maps.maps.collectAsStateWithLifecycle()
    val hiding = LocalAppSettings.current.hideLockedPrivateMaps
    val failed by rememberUpdatedState(onFailed)

    return { action ->
        if (!view.asksBeforeEveryMap(maps.orEmpty(), hiding)) {
            action()
        } else {
            unlocking.ask { result ->
                when (result) {
                    Unlocked.Yes -> action()
                    is Unlocked.Failed -> if (result.reason.isNotBlank()) {
                        failed(result.reason)
                    }
                    Unlocked.Cancelled -> Unit
                }
            }
        }
    }
}

/** The phone's settings for its screen lock: to set one, so maps can be made private. */
fun screenLockSettings(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

/** Opens the phone's settings for its screen lock, or its security settings where that screen isn't there. */
fun openScreenLockSettings(context: Context) {
    val intent = screenLockSettings().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(if (intent.resolveActivity(context.packageManager) != null) intent else fallback)
    } catch (missing: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
