package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R

/**
 * A notice over the maps: an icon and a title TalkBack hears as a heading,
 * what it says, and its buttons in a row that wraps when they don't fit, on
 * a card shaped like the maps'. A problem shows in the error colors, and its
 * buttons in them too ([NoticeButton], [NoticeTextButton]). `onDismiss` puts
 * a close button beside the title, for a notice whose buttons don't put it
 * away themselves.
 */
@Composable
fun NoticeCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    problem: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable FlowRowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (problem) colors.errorContainer else colors.surfaceContainerHighest,
        contentColor = if (problem) colors.onErrorContainer else colors.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // A close button is taller than the title: the card's top gives it the room.
            modifier = Modifier.padding(start = 20.dp, top = if (onDismiss != null) 8.dp else 16.dp, end = 20.dp, bottom = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })

                if (onDismiss != null) {
                    val dismiss = stringResource(R.string.home_dismiss)

                    Hint(dismiss) {
                        // Its cross lines up with the words' end, as the button's own padding would push it in.
                        IconButton(onClick = onDismiss, modifier = Modifier.offset(x = 12.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = dismiss)
                        }
                    }
                }
            }

            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                body()
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** A notice's main button: tonal, or in the error colors on a problem's card. */
@Composable
fun NoticeButton(text: String, onClick: () -> Unit, problem: Boolean = false, icon: ImageVector? = null) {
    val colors = if (problem) {
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }

    Button(onClick = onClick, colors = colors, contentPadding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }

        Text(text)
    }
}

/** A notice's other buttons: the app's own color, or the card's on a problem's. */
@Composable
fun NoticeTextButton(text: String, onClick: () -> Unit, problem: Boolean = false) {
    val colors = if (problem) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer) else ButtonDefaults.textButtonColors()
    TextButton(onClick = onClick, colors = colors) { Text(text) }
}
