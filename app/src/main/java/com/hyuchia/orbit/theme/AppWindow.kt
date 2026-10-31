package com.hyuchia.orbit.theme

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.ThemeMode

/**
 * Paints the window behind the screens in the theme's background, so screens
 * that fade into each other show it rather than the system's light or dark:
 * with Settings → Appearance set against the system, nothing flashes.
 */
@Composable
fun ThemedWindowBackground() {
    val window = LocalActivity.current?.window
    val background = MaterialTheme.colorScheme.background.toArgb()

    DisposableEffect(window, background) {
        window?.setBackgroundDrawable(ColorDrawable(background))
        onDispose {}
    }
}

/**
 * The status and navigation bars' icons, light or dark as Orbit is, not as
 * the system is. A sheet over another app (`overOtherApps`) leaves the
 * status bar's as the system has them: it's over that app's screen.
 */
@Composable
fun ThemedSystemBars(dark: Boolean, overOtherApps: Boolean = false) {
    val activity = LocalActivity.current as? ComponentActivity

    DisposableEffect(activity, dark, overOtherApps) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        val statusBar = if (overOtherApps) SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) else style
        activity?.enableEdgeToEdge(statusBarStyle = statusBar, navigationBarStyle = style)
        onDispose {}
    }
}

/**
 * The splash Android shows as Orbit starts next, light or dark as Settings →
 * Appearance says, or as the system is. Android 13 and later keep it for
 * Orbit; earlier versions draw the splash as the system is.
 */
fun Activity.startNextIn(mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }

    val theme = when (mode) {
        ThemeMode.System -> Resources.ID_NULL
        ThemeMode.Light -> R.style.Theme_Orbit_Starting_Light
        ThemeMode.Dark -> R.style.Theme_Orbit_Starting_Dark
    }

    splashScreen.setSplashScreenTheme(theme)
}
