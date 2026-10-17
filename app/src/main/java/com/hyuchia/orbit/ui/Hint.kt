package com.hyuchia.orbit.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}
