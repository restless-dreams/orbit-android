package com.hyuchia.orbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.OrbitPalette
import com.hyuchia.orbit.theme.StrongPalette
import com.hyuchia.orbit.theme.hex

/** The colors' names, for screen readers: Orbit's soft palette, then the stronger one. */
private val ColorNames = listOf(
    R.string.ui_color_coral,
    R.string.ui_color_mint,
    R.string.ui_color_sky,
    R.string.ui_color_lilac,
    R.string.ui_color_apricot,
    R.string.ui_color_pink,
    R.string.ui_color_teal,
) + listOf(
    R.string.ui_color_red,
    R.string.ui_color_orange,
    R.string.ui_color_amber,
    R.string.ui_color_green,
    R.string.ui_color_blue,
    R.string.ui_color_violet,
    R.string.ui_color_gray,
)

/**
 * The colors an idea can take: automatic (its branch's color), Orbit's soft
 * palette and stronger ones. `current` is the node's own color, as hex.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(current: String?, automatic: Color?, onPick: (String?) -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    // Each swatch is 48 dp to tap, however small it's drawn: the room around it is part of it, so the gaps shrink to match.
    val target = max(size, MINIMUM_TARGET)
    val gap = max(0.dp, size + 10.dp - target)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Swatch(automatic, stringResource(R.string.ui_color_automatic), current == null, size, target, automatic = true) { onPick(null) }

        (OrbitPalette + StrongPalette).forEachIndexed { index, color ->
            val hex = color.hex()
            val name = ColorNames.getOrNull(index)?.let { stringResource(it) } ?: stringResource(R.string.ui_color_numbered, index + 1)
            Swatch(color, name, current.equals(hex, ignoreCase = true), size, target) { onPick(hex) }
        }
    }
}

private val MINIMUM_TARGET = 48.dp

@Composable
private fun Swatch(color: Color?, label: String, selected: Boolean, size: Dp, target: Dp, automatic: Boolean = false, onClick: () -> Unit) {
    val fill = color ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val mark = if (fill.luminance() > 0.5f) Color(0xFF1F2733) else Color.White

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(target)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .background(fill, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        ) {
            when {
                selected -> Icon(Icons.Outlined.Check, contentDescription = null, tint = mark, modifier = Modifier.size(18.dp))
                automatic -> Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = mark, modifier = Modifier.size(18.dp))
            }
        }
    }
}
