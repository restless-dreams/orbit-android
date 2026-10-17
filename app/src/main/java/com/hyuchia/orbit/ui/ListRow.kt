package com.hyuchia.orbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A row in the proportions of Material's list items, for lists that can sit in a
 * tablet's side pane. Material's ListItem compares its supporting text's first and
 * last baselines as it measures, and that throws while the pane animates out: the
 * pane measures in a lookahead pass, where the leaving text has no baselines to give.
 * This row stacks its text in a plain column, so nothing asks.
 */
@Composable
fun ListRow(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overline: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .background(containerColor)
            .heightIn(min = if (supporting != null || overline != null) 72.dp else 56.dp)
            .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides leadingColor, content = leading)
        }

        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Styled(typography.labelSmall, colors.onSurfaceVariant, overline)
            }

            Styled(typography.bodyLarge, headlineColor, headline)

            if (supporting != null) {
                Styled(typography.bodyMedium, colors.onSurfaceVariant, supporting)
            }
        }

        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant, content = trailing)
        }
    }
}

@Composable
private fun Styled(style: TextStyle, color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color) {
        ProvideTextStyle(style, content)
    }
}
