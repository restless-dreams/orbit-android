package com.hyuchia.orbit.privacy

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.hyuchia.orbit.container
import kotlinx.coroutines.launch

/**
 * Says that this screen shows something private while `active`: a private
 * map open, or one on Home or in the trash, unlocked. Recents then gets no
 * picture of Orbit (see [keepPrivateMapsOutOfRecents]).
 */
@Composable
fun PrivateOnScreen(active: Boolean) {
    val privateMaps = LocalContext.current.container.privateMaps

    DisposableEffect(active) {
        if (!active) {
            return@DisposableEffect onDispose {}
        }

        privateMaps.shown()
        onDispose { privateMaps.hidden() }
    }
}

/**
 * While something private is on screen, Recents gets no picture of Orbit,
 * and Android shows Orbit's icon in its place, there and as Orbit comes back.
 *
 * Android 13 and later let an app keep only that picture back
 * (`setRecentsScreenshotEnabled`). Before, the only way is to mark the
 * window secure, which also keeps screenshots, screen recordings and the
 * phone's own screen casting from showing it: so it's marked only while
 * something private is on screen, and private maps that are locked show
 * nothing.
 */
fun ComponentActivity.keepPrivateMapsOutOfRecents() {
    lifecycleScope.launch {
        container.privateMaps.showing.collect { showing ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(!showing)
            } else if (showing) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
