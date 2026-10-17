package com.hyuchia.orbit.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hyuchia.orbit.R

/**
 * Google Sans Flex, Material 3 Expressive's typeface, shipped with the app
 * (trimmed to Latin and Vietnamese, the axes below, and the weights Orbit
 * uses), so it needs no downloads and no Google services. Text in other
 * scripts falls back to the phone's own font. Maps use the same file: the
 * map page loads it from the app's resources.
 */
private val WEIGHTS = listOf(300, 400, 500, 600, 700, 800)

/**
 * The family at one optical size: the font draws small text with more open
 * shapes and room, and large text tighter, as type designed for each size.
 */
private fun flexFamily(opticalSize: Float): FontFamily = FontFamily(
    WEIGHTS.map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

/** Optical sizes in four steps, as Material's type scale groups its sizes. */
private val SmallText = flexFamily(11f)
private val Text = flexFamily(14f)
private val Titles = flexFamily(22f)
private val Display = flexFamily(40f)

private fun familyFor(size: TextUnit): FontFamily = when {
    size.value <= 12f -> SmallText
    size.value <= 16f -> Text
    size.value <= 24f -> Titles
    else -> Display
}

/**
 * A Material style in Google Sans Flex. Material's tracking is Roboto's;
 * the optical sizes already space small text, so it stays closer to zero.
 */
private fun TextStyle.inFlex(): TextStyle = copy(
    fontFamily = familyFor(fontSize),
    letterSpacing = if (fontSize.value <= 12f) 0.2.sp else 0.sp,
)

/** Material's type scale, in Google Sans Flex. */
val OrbitTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.inFlex(),
        displayMedium = base.displayMedium.inFlex(),
        displaySmall = base.displaySmall.inFlex(),
        headlineLarge = base.headlineLarge.inFlex(),
        headlineMedium = base.headlineMedium.inFlex(),
        headlineSmall = base.headlineSmall.inFlex(),
        titleLarge = base.titleLarge.inFlex(),
        titleMedium = base.titleMedium.inFlex(),
        titleSmall = base.titleSmall.inFlex(),
        bodyLarge = base.bodyLarge.inFlex(),
        bodyMedium = base.bodyMedium.inFlex(),
        bodySmall = base.bodySmall.inFlex(),
        labelLarge = base.labelLarge.inFlex(),
        labelMedium = base.labelMedium.inFlex(),
        labelSmall = base.labelSmall.inFlex(),
        displayLargeEmphasized = base.displayLargeEmphasized.inFlex(),
        displayMediumEmphasized = base.displayMediumEmphasized.inFlex(),
        displaySmallEmphasized = base.displaySmallEmphasized.inFlex(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.inFlex(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.inFlex(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.inFlex(),
        titleLargeEmphasized = base.titleLargeEmphasized.inFlex(),
        titleMediumEmphasized = base.titleMediumEmphasized.inFlex(),
        titleSmallEmphasized = base.titleSmallEmphasized.inFlex(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.inFlex(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.inFlex(),
        bodySmallEmphasized = base.bodySmallEmphasized.inFlex(),
        labelLargeEmphasized = base.labelLargeEmphasized.inFlex(),
        labelMediumEmphasized = base.labelMediumEmphasized.inFlex(),
        labelSmallEmphasized = base.labelSmallEmphasized.inFlex(),
    )
}
