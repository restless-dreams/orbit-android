package com.hyuchia.orbit.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/*
 * The lines screens change their layout at ("Phones and tablets, both ways up" in AGENTS.md),
 * where Android's window size classes draw them, so every screen draws them alike.
 */

/** Shorter than this, as a phone on its side is, a screen keeps its height for the map or the list: a side rail, a slim bar. */
val ShortScreen = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp

/** At least this wide, as a tablet on its side is, a screen has room for two panes, such as a panel beside the map. */
val WideScreen = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.dp

/** A map keeps at least a phone's width beside what stands next to it: the list of maps, a panel or its text. */
val MinimumMapWidth = 360.dp

/** Whether a screen of `height` is a short one ([ShortScreen]). */
fun isShort(height: Dp): Boolean = height < ShortScreen
