package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.hyuchia.orbit.R

/**
 * A button's name, shown as the mouse rests on it or the button is held:
 * with a keyboard connected, the keys that do the same follow, "Undo
 * (Ctrl+Z)".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hint(name: String, keys: Keys? = null, content: @Composable () -> Unit) {
    val text = if (keys != null && hasHardwareKeyboard()) {
        stringResource(R.string.ui_hint_with_keys, name, keys.caps.joinToString("+"))
    } else {
        name
    }

    TooltipBox(
        positionProvider = rememberHintPosition(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * Above its button, or below one too near the window's top for a hint above
 * to clear the status bar: a top bar's buttons, the lock cover's way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberHintPosition(): PopupPositionProvider {
    val above = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)
    val below = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below)
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)

    return remember(above, below, statusBar) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val position = above.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
                return if (position.y < statusBar) below.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize) else position
            }
        }
    }
}
