package com.hyuchia.orbit.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A color as the color picker moves it: its hue (0 to 360), saturation and value (0 to 1). */
data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    /** The color as an opaque ARGB int. */
    fun toArgb(): Int {
        val chroma = value * saturation
        val sector = (hue.coerceIn(0f, 360f) % 360f) / 60f
        val second = chroma * (1 - abs(sector % 2 - 1))

        val (red, green, blue) = when (sector.toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }

        val lightest = value - chroma
        return argb(red + lightest, green + lightest, blue + lightest)
    }

    /** The color as people write it in a map: `#RRGGBB`. */
    fun hex(): String = hexOf(toArgb())

    companion object {
        fun fromArgb(argb: Int): HsvColor {
            val red = (argb shr 16 and 0xFF) / 255f
            val green = (argb shr 8 and 0xFF) / 255f
            val blue = (argb and 0xFF) / 255f
            val highest = max(red, max(green, blue))
            val lowest = min(red, min(green, blue))
            val chroma = highest - lowest

            val hue = when {
                chroma == 0f -> 0f
                highest == red -> 60f * (((green - blue) / chroma) % 6)
                highest == green -> 60f * ((blue - red) / chroma + 2)
                else -> 60f * ((red - green) / chroma + 4)
            }

            return HsvColor((hue + 360f) % 360f, if (highest == 0f) 0f else chroma / highest, highest)
        }
    }
}

private fun argb(red: Float, green: Float, blue: Float): Int {
    fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255).roundToInt()
    return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

/** `#RRGGBB`, the way the app writes colors in maps. */
fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private val HexCode = Regex("[0-9a-fA-F]{6}")

/**
 * A hex code as people type it, `#3366ff` or `3366FF`. Null for anything else, the short `#36f`
 * included: while a code is typed or deleted, its first three digits would flash a color of their own.
 */
fun parseHexColor(text: String): Int? {
    val digits = text.trim().removePrefix("#")

    if (!HexCode.matches(digits)) {
        return null
    }

    return (0xFF shl 24) or digits.toInt(16)
}

/** What a color looks like, roughly, for screen readers, which can't show it: "light blue", "brown", "gray". */
enum class ColorFamily { Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink, Brown, Gray, Black, White }

enum class ColorShade { Light, Plain, Dark }

data class ColorName(val family: ColorFamily, val shade: ColorShade = ColorShade.Plain)

/** A color's rough name, by its hue and how light and vivid it is. */
fun colorName(argb: Int): ColorName {
    val red = (argb shr 16 and 0xFF) / 255f
    val green = (argb shr 8 and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val highest = max(red, max(green, blue))
    val lowest = min(red, min(green, blue))
    val lightness = (highest + lowest) / 2
    val saturation = if (highest == lowest) 0f else (highest - lowest) / (1 - abs(2 * lightness - 1))
    val hue = HsvColor.fromArgb(argb).hue

    val shade = when {
        lightness > 0.72f -> ColorShade.Light
        lightness < 0.3f -> ColorShade.Dark
        else -> ColorShade.Plain
    }

    return when {
        lightness < 0.1f -> ColorName(ColorFamily.Black)
        lightness > 0.95f -> ColorName(ColorFamily.White)
        saturation < 0.15f -> ColorName(ColorFamily.Gray, shade)
        hue >= 15f && hue < 50f && lightness < 0.4f -> ColorName(ColorFamily.Brown, if (lightness < 0.2f) ColorShade.Dark else ColorShade.Plain)
        hue < 15f || hue >= 340f -> ColorName(ColorFamily.Red, shade)
        hue < 40f -> ColorName(ColorFamily.Orange, shade)
        hue < 65f -> ColorName(ColorFamily.Yellow, shade)
        hue < 160f -> ColorName(ColorFamily.Green, shade)
        hue < 195f -> ColorName(ColorFamily.Teal, shade)
        hue < 245f -> ColorName(ColorFamily.Blue, shade)
        hue < 290f -> ColorName(ColorFamily.Purple, shade)
        else -> ColorName(ColorFamily.Pink, shade)
    }
}

/**
 * The colors a map's ideas have of their own besides the ready-made ones (`palette`, as
 * `#RRGGBB`), to offer again: the most used first, as each was written, and never more than
 * `limit`. The idea's own color (`current`) is always among them when it's one of these.
 * `parse` reads a color, and leaves out what it can't.
 */
fun customColors(colors: List<String?>, current: String?, palette: Set<String>, limit: Int, parse: (String) -> Int?): List<String> {
    val counts = linkedMapOf<Int, Pair<String, Int>>()

    for (color in colors) {
        val argb = color?.let(parse) ?: continue

        if (hexOf(argb) in palette) {
            continue
        }

        val (written, count) = counts[argb] ?: (color to 0)
        counts[argb] = written to count + 1
    }

    // A stable sort: colors used as often keep the order they first appear in.
    val ordered = counts.values.sortedByDescending { it.second }.map { it.first }
    val own = current?.let(parse)?.let { argb -> counts[argb]?.first }

    if (own == null || ordered.indexOf(own) < limit) {
        return ordered.take(limit)
    }

    return ordered.take(limit - 1) + own
}

/** WCAG's relative luminance of an opaque color, from 0 for black to 1 for white. */
fun relativeLuminance(argb: Int): Double {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)
}

/** WCAG's contrast ratio between two opaque colors, from 1 to 21. */
fun contrastRatio(first: Int, second: Int): Double {
    val lighter = max(relativeLuminance(first), relativeLuminance(second))
    val darker = min(relativeLuminance(first), relativeLuminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

/** Whether white text reads better than `dark` text on a color, the way Orbit picks a label's color. */
fun prefersLightText(argb: Int, dark: Int): Boolean = contrastRatio(argb, 0xFFFFFFFF.toInt()) > contrastRatio(argb, dark)
