package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Material's snackbar in dp, at a density of 1: a line of 20 with 6 above and below, buttons 48 tall with their touch targets, and a baseline 15 down a line. */
class OrbitSnackbarTest {
    private val heights = SnackbarHeights(singleLine = 48, twoLines = 68, toFirstLine = 30)
    private val baselineInLine = 15

    /** A message of so many lines, with an action whose label (a line as tall, in the button's middle) has its baseline `labelBaseline` down its line. */
    private fun message(lines: Int, lineHeight: Int = 20, labelBaseline: Int = baselineInLine) = SnackbarParts(
        textHeight = lines * lineHeight + 12,
        oneLine = lines == 1,
        textBaseline = 6 + baselineInLine,
        actionHeight = 48,
        dismissHeight = null,
        actionBaseline = (48 - lineHeight) / 2 + labelBaseline,
    )

    @Test
    fun `a line of text and its action sit in the middle of 48 dp`() {
        assertEquals(SnackbarPlacement(height = 48, textY = 8, actionY = 0, dismissY = 0), placeSnackbar(message(lines = 1), heights))
    }

    @Test
    fun `the action's label sits on the text's baseline, as Material lines them up`() {
        // A label whose type puts its baseline a pixel lower in its line: the button goes up a pixel.
        assertEquals(-1, placeSnackbar(message(lines = 1, labelBaseline = baselineInLine + 1), heights).actionY)

        // Larger text on a line still sits in the middle, its action level with it.
        val larger = placeSnackbar(message(lines = 1, lineHeight = 26), heights)
        assertEquals(SnackbarPlacement(height = 48, textY = 5, actionY = 0, dismissY = 0), larger)
    }

    @Test
    fun `several lines put the first one's baseline 30 dp down, in at least 68 dp, the action in the middle`() {
        assertEquals(SnackbarPlacement(height = 68, textY = 9, actionY = 10, dismissY = 0), placeSnackbar(message(lines = 2), heights))
        assertEquals(SnackbarPlacement(height = 81, textY = 9, actionY = 16, dismissY = 0), placeSnackbar(message(lines = 3), heights))
    }

    @Test
    fun `without an action, or with a dismiss button, the parts are placed the same way`() {
        assertEquals(SnackbarPlacement(height = 48, textY = 8, actionY = 0, dismissY = 0), placeSnackbar(message(lines = 1).copy(actionHeight = null, actionBaseline = null), heights))
        assertEquals(SnackbarPlacement(height = 48, textY = 8, actionY = 0, dismissY = 0), placeSnackbar(message(lines = 1).copy(dismissHeight = 48), heights))
        assertEquals(SnackbarPlacement(height = 68, textY = 9, actionY = 10, dismissY = 10), placeSnackbar(message(lines = 2).copy(dismissHeight = 48), heights))
    }
}
