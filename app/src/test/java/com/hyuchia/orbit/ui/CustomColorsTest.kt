package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorsTest {
    private fun parse(text: String): Int? = parseHexColor(text)

    @Test
    fun colorsGoToHueSaturationAndValueAndBack() {
        for (hex in listOf("#FF0000", "#00FF00", "#0000FF", "#3366FF", "#8B4513", "#FEBAB6", "#000000", "#FFFFFF", "#808080", "#E5484D")) {
            assertEquals(hex, HsvColor.fromArgb(parse(hex)!!).hex())
        }
    }

    @Test
    fun huesGoAroundTheWheel() {
        assertEquals(0f, HsvColor.fromArgb(parse("#FF0000")!!).hue, 0.01f)
        assertEquals(120f, HsvColor.fromArgb(parse("#00FF00")!!).hue, 0.01f)
        assertEquals(240f, HsvColor.fromArgb(parse("#0000FF")!!).hue, 0.01f)
        assertEquals(330f, HsvColor.fromArgb(parse("#FF0080")!!).hue, 0.5f)
        assertEquals("#FF0000", HsvColor(360f, 1f, 1f).hex())
        assertEquals("#000000", HsvColor(200f, 0.5f, 0f).hex())
    }

    @Test
    fun hexCodesAreReadAsPeopleTypeThem() {
        assertEquals(0xFF3366FF.toInt(), parse("#3366ff"))
        assertEquals(0xFF3366FF.toInt(), parse("3366FF"))
        assertEquals(0xFF3366FF.toInt(), parse(" #3366ff "))
        assertNull(parse("#36f"))
        assertNull(parse("#3366f"))
        assertNull(parse("#GG0000"))
        assertNull(parse(""))
        assertNull(parse("#80FF0000"))
    }

    @Test
    fun colorsGetRoughNames() {
        assertEquals(ColorName(ColorFamily.Red), colorName(parse("#E5484D")!!))
        assertEquals(ColorName(ColorFamily.Blue), colorName(parse("#0090FF")!!))
        assertEquals(ColorName(ColorFamily.Blue, ColorShade.Light), colorName(parse("#9ED5FD")!!))
        assertEquals(ColorName(ColorFamily.Green, ColorShade.Dark), colorName(parse("#14532D")!!))
        assertEquals(ColorName(ColorFamily.Brown), colorName(parse("#8B4513")!!))
        assertEquals(ColorName(ColorFamily.Purple), colorName(parse("#6E56CF")!!))
        assertEquals(ColorName(ColorFamily.Pink, ColorShade.Light), colorName(parse("#FCB6D6")!!))
        assertEquals(ColorName(ColorFamily.Gray), colorName(parse("#8E8C99")!!))
        assertEquals(ColorName(ColorFamily.Black), colorName(parse("#050505")!!))
        assertEquals(ColorName(ColorFamily.White), colorName(parse("#FBFBFB")!!))
    }

    @Test
    fun theMapsOwnColorsComeMostUsedFirstAsWritten() {
        val palette = setOf("#E5484D", "#0090FF")
        val colors = listOf(null, "#123456", "#E5484D", "#abcdef", "#ABCDEF", "#123456", "#123456", "tomato", "not a color")
        val parse = { text: String -> if (text == "tomato") 0xFFFF6347.toInt() else parseHexColor(text) }

        assertEquals(listOf("#123456", "#abcdef", "tomato"), customColors(colors, null, palette, limit = 7, parse = parse))
    }

    @Test
    fun theIdeasOwnColorIsAlwaysOffered() {
        val colors = listOf("#111111", "#111111", "#222222", "#222222", "#333333")
        val shown = customColors(colors, current = "#333333", palette = emptySet(), limit = 2, parse = ::parse)

        assertEquals(listOf("#111111", "#333333"), shown)
        assertEquals(listOf("#111111", "#222222"), customColors(colors, current = "#111111", palette = emptySet(), limit = 2, parse = ::parse))
        assertEquals(listOf("#111111", "#222222"), customColors(colors, current = "#E5484D", palette = setOf("#E5484D"), limit = 2, parse = ::parse))
    }

    @Test
    fun labelsTakeTheTextThatReadsBest() {
        val dark = 0xFF1F2733.toInt()

        assertTrue(prefersLightText(parse("#0B1120")!!, dark))
        assertTrue(prefersLightText(parse("#E5484D")!!, dark) || contrastRatio(parse("#E5484D")!!, dark) >= 4.5)
        assertFalse(prefersLightText(parse("#FEBAB6")!!, dark))
        assertFalse(prefersLightText(parse("#FFC53D")!!, dark))
        assertEquals(21.0, contrastRatio(parse("#000000")!!, parse("#FFFFFF")!!), 0.01)
    }
}
