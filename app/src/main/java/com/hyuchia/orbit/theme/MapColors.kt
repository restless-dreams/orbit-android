package com.hyuchia.orbit.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.AppTheme

/** Orbit's branch colors, for colors people pick and for the little orbits. */
val OrbitPalette = listOf(
    Color(0xFFFEBAB6),
    Color(0xFF99E0BA),
    Color(0xFF9ED5FD),
    Color(0xFFD6C2FE),
    Color(0xFFF9C191),
    Color(0xFFFCB6D6),
    Color(0xFF82E1E0),
)

/** Deeper colors for ideas that should stand out. */
val StrongPalette = listOf(
    Color(0xFFE5484D),
    Color(0xFFF76B15),
    Color(0xFFFFC53D),
    Color(0xFF30A46C),
    Color(0xFF0090FF),
    Color(0xFF6E56CF),
    Color(0xFF8E8C99),
)

fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** A CSS color as a Compose color, when it is one Android reads (hex, or a few names). */
fun parseCssColor(value: String?): Color? {
    if (value.isNullOrBlank()) {
        return null
    }

    return try {
        Color(value.trim().toColorInt())
    } catch (unreadable: IllegalArgumentException) {
        null
    }
}

val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

/**
 * The map in the app's colors: Orbit's Material theme grown from the
 * wallpaper's primary color (or Orbit's own blue), so the sun and every
 * branch take tones in harmony with it, on the app's background, with the
 * map's buttons and tooltips in the app's exact Material colors.
 */
fun ColorScheme.mapTheme(): AppTheme = AppTheme(
    dark = isDark,
    seed = primary.hex(),
    tokens = mapOf(
        "surface" to background.hex(),
        "surface-pattern" to "none",
        "text" to onBackground.hex(),
        "muted-text" to onSurfaceVariant.hex(),
        "focus-ring" to primary.hex(),
        "tooltip-background" to inverseSurface.hex(),
        "tooltip-text" to inverseOnSurface.hex(),
        "action-background" to surfaceContainerHigh.hex(),
        "action-border" to outlineVariant.hex(),
        "action-text" to onSurface.hex(),
        "chip-background" to secondaryContainer.hex(),
        "chip-text" to onSecondaryContainer.hex(),
        "link-text" to primary.hex(),
    ),
)

/** One of Orbit's styles, as the app shows it: its name, and the colors to preview it with. */
data class MapStyle(
    /** The name a map's frontmatter uses; null follows the app's colors. */
    val name: String?,

    @StringRes val labelResource: Int,
    @StringRes val descriptionResource: Int,
    val dark: Boolean,

    /** The screen behind the map. */
    val background: Color,
) {
    /** The style's name as people read it, in the app's language. */
    val label: String
        @Composable @ReadOnlyComposable get() = stringResource(labelResource)

    val description: String
        @Composable @ReadOnlyComposable get() = stringResource(descriptionResource)
}

val MapStyles = listOf(
    MapStyle(null, R.string.theme_style_app_colors_label, R.string.theme_style_app_colors_description, dark = false, background = Color.Unspecified),
    MapStyle("paper", R.string.theme_style_paper_label, R.string.theme_style_paper_description, dark = false, background = Color(0xFFFBFAF7)),
    MapStyle("night", R.string.theme_style_night_label, R.string.theme_style_night_description, dark = true, background = Color(0xFF0B1120)),
    MapStyle("flat", R.string.theme_style_flat_label, R.string.theme_style_flat_description, dark = false, background = Color(0xFFFFFFFF)),
    MapStyle("flat-night", R.string.theme_style_flat_night_label, R.string.theme_style_flat_night_description, dark = true, background = Color(0xFF121418)),
    MapStyle("material", R.string.theme_style_material_label, R.string.theme_style_material_description, dark = false, background = Color(0xFFFAF9FF)),
    MapStyle("material-night", R.string.theme_style_material_night_label, R.string.theme_style_material_night_description, dark = true, background = Color(0xFF141318)),
    MapStyle("liquid", R.string.theme_style_liquid_glass_label, R.string.theme_style_liquid_glass_description, dark = false, background = Color(0xFFE9EEF8)),
    MapStyle("liquid-night", R.string.theme_style_liquid_glass_night_label, R.string.theme_style_liquid_glass_night_description, dark = true, background = Color(0xFF070B1A)),
    MapStyle("glass", R.string.theme_style_glass_label, R.string.theme_style_glass_description, dark = false, background = Color(0xFFE7EAF8)),
    MapStyle("glass-night", R.string.theme_style_glass_night_label, R.string.theme_style_glass_night_description, dark = true, background = Color(0xFF0E1326)),
    MapStyle("cosmos", R.string.theme_style_cosmos_label, R.string.theme_style_cosmos_description, dark = true, background = Color(0xFF05060E)),
    MapStyle("blueprint", R.string.theme_style_blueprint_label, R.string.theme_style_blueprint_description, dark = true, background = Color(0xFF123A7A)),
    MapStyle("ink", R.string.theme_style_ink_label, R.string.theme_style_ink_description, dark = false, background = Color(0xFFFFFFFF)),
    MapStyle("contrast", R.string.theme_style_high_contrast_label, R.string.theme_style_high_contrast_description, dark = false, background = Color(0xFFFFFFFF)),
)

fun mapStyle(name: String?): MapStyle = MapStyles.firstOrNull { it.name == name } ?: MapStyles.first()

/** Color sets for a map's `palette`: the sun's color first, then one per branch. */
data class MapPalette(@StringRes val nameResource: Int, val colors: List<Color>?) {
    /** The set's name as people read it, in the app's language. */
    val name: String
        @Composable @ReadOnlyComposable get() = stringResource(nameResource)
}

val MapPalettes = listOf(
    MapPalette(R.string.theme_palette_style_own, null),
    MapPalette(R.string.theme_palette_sorbet, listOf(Color(0xFFFFD166), Color(0xFFFF8FA3), Color(0xFF8BE0C4), Color(0xFFA0C4FF), Color(0xFFCDB4DB), Color(0xFFFFC09F))),
    MapPalette(R.string.theme_palette_ocean, listOf(Color(0xFFFFE8A3), Color(0xFF8ECAE6), Color(0xFF219EBC), Color(0xFF7BDFF2), Color(0xFFB2F7EF), Color(0xFF5390D9))),
    MapPalette(R.string.theme_palette_forest, listOf(Color(0xFFF4E285), Color(0xFF8CB369), Color(0xFF5B8E7D), Color(0xFFBC4B51), Color(0xFFF4A259), Color(0xFFA7C957))),
    MapPalette(R.string.theme_palette_sunset, listOf(Color(0xFFFFE066), Color(0xFFFF6B6B), Color(0xFFFFA94D), Color(0xFFF783AC), Color(0xFFB197FC), Color(0xFFFFD8A8))),
    MapPalette(R.string.theme_palette_vivid, listOf(Color(0xFFFFC53D), Color(0xFFE5484D), Color(0xFF30A46C), Color(0xFF0090FF), Color(0xFF8E4EC6), Color(0xFFF76B15))),
    MapPalette(R.string.theme_palette_mono, listOf(Color(0xFF1F2733), Color(0xFF5B6474), Color(0xFF8A93A3), Color(0xFFB4BCC8), Color(0xFFD5DAE1))),
)
