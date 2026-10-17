package com.hyuchia.orbit.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.ThemeMode

/** The settings every screen reads: colors, haptics and how maps behave. */
val LocalAppSettings = compositionLocalOf { AppSettings() }

// Without wallpaper colors: Orbit's own night-sky blue, with the sun's yellow for containers.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5BA9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFAD86F),
    onPrimaryContainer = Color(0xFF3A2F00),
    inversePrimary = Color(0xFFB3C5FF),
    secondary = Color(0xFF595E72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1F9),
    onSecondaryContainer = Color(0xFF161B2C),
    tertiary = Color(0xFF8F4A50),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DB),
    onTertiaryContainer = Color(0xFF3B0712),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF3D5BA9),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3C5FF),
    onPrimary = Color(0xFF082B70),
    primaryContainer = Color(0xFF574500),
    onPrimaryContainer = Color(0xFFFFE08A),
    inversePrimary = Color(0xFF3D5BA9),
    secondary = Color(0xFFC1C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF41465A),
    onSecondaryContainer = Color(0xFFDDE1F9),
    tertiary = Color(0xFFFFB2B8),
    onTertiary = Color(0xFF561D25),
    tertiaryContainer = Color(0xFF72333A),
    onTertiaryContainer = Color(0xFFFFD9DB),
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF12131A),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceTint = Color(0xFFB3C5FF),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF12131A),
    surfaceContainerLowest = Color(0xFF0D0E14),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F26),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
)

/** Material You (the wallpaper's colors, on Android 12 and later) or Orbit's own. */
fun orbitColorScheme(context: Context, dark: Boolean, dynamicColor: Boolean): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
    dark -> DarkColors
    else -> LightColors
}

/** Whether the app is dark: the system's choice, unless the settings pick one. */
@Composable
fun appIsDark(): Boolean = when (LocalAppSettings.current.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun OrbitTheme(dark: Boolean = appIsDark(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = orbitColorScheme(LocalContext.current, dark, LocalAppSettings.current.dynamicColor),
        motionScheme = MotionScheme.expressive(),
        typography = OrbitTypography,
        content = content,
    )
}
