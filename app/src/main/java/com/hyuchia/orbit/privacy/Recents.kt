package com.hyuchia.orbit.privacy

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.lifecycleScope
import com.hyuchia.orbit.OrbitApplication
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
 * nothing. Windows of Orbit's own over it, as for a picture full screen,
 * are marked with it ([privateWindowPolicy]).
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

/**
 * How a window of Orbit's own over its screen, such as a picture full
 * screen, keeps out of Recents, screenshots and screen casting: on Android
 * 12 and earlier, it's marked secure while something private is on screen,
 * as Orbit's main window is ([keepPrivateMapsOutOfRecents]). Compose gives
 * such a window the main window's mark only as it opens, and again only when
 * its call is made again: one open since before something private showed,
 * or made again while private maps were locked, would keep what's private
 * unmarked once it shows. Android 13 and later keep the whole of Orbit, its
 * windows included, out of Recents alone.
 */
@Composable
fun privateWindowPolicy(): SecureFlagPolicy {
    val privateMaps = LocalContext.current.container.privateMaps
    val showing by remember(privateMaps) { privateMaps.showing }.collectAsState(privateMaps.showingNow)

    return if (showing && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit
}

/**
 * A sheet's properties, under [privateWindowPolicy]: on Android 12 and
 * earlier it's marked secure while something private is on screen, from the
 * moment that is, not only if it was as the sheet opened. Quick capture's
 * sheet comes up before its list names private maps, and a map's sheet can
 * stay open as the map is made private.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun privateSheetProperties(): ModalBottomSheetProperties {
    // Outside Orbit's own app, as a sheet a test draws by itself, there are no private maps to keep out: it takes its screen's mark.
    val policy = if (LocalContext.current.applicationContext is OrbitApplication) privateWindowPolicy() else SecureFlagPolicy.Inherit
    return ModalBottomSheetProperties(securePolicy = policy)
}
