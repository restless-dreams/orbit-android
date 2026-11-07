package com.hyuchia.orbit.sketch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.colorName
import com.hyuchia.orbit.ui.colorNameText

/**
 * The sketch's floating toolbar: the pen (its color and width, which a
 * second tap offers), the eraser, undo and redo, and clear, with Done as its
 * button. In a row under the canvas, or a column beside it on a short screen.
 * Its buttons close up on a narrow one, so the row always fits.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SketchToolbar(session: SketchSession, vertical: Boolean, pensOpen: Boolean, onPens: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // Five buttons and Done: 48 dp each where the toolbar has room, 40 on a narrow phone.
        val room = if (vertical) maxHeight else maxWidth
        val button = if (room < 344.dp) 40.dp else 48.dp

        val done = @Composable {
            val name = stringResource(R.string.common_done)

            Hint(name, SketchKeys.Done) {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onDone) {
                    Icon(Icons.Filled.Check, contentDescription = name)
                }
            }
        }

        if (vertical) {
            VerticalFloatingToolbar(expanded = true, floatingActionButton = done, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                SketchTools(session, button, pensOpen, onPens)
            }
        } else {
            HorizontalFloatingToolbar(expanded = true, floatingActionButton = done, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                SketchTools(session, button, pensOpen, onPens)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SketchTools(session: SketchSession, size: Dp, pensOpen: Boolean, onPens: () -> Unit) {
    val penName = stringResource(R.string.sketch_pen_named, colorNameText(colorName(session.color)), stringResource(session.size.word))

    Hint(stringResource(R.string.sketch_pen), SketchKeys.Pen) {
        // Chosen already, the pen offers its colors and widths.
        IconToggleButton(
            checked = !session.erasing,
            onCheckedChange = {
                if (session.erasing) {
                    session.erasing = false
                } else {
                    onPens()
                }
            },
            colors = IconButtonDefaults.iconToggleButtonVibrantColors(),
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = penName },
        ) {
            PenSwatch(Color(session.color), session.size, open = pensOpen)
        }
    }

    val eraser = stringResource(R.string.sketch_eraser)

    Hint(eraser, SketchKeys.Eraser) {
        IconToggleButton(
            checked = session.erasing,
            onCheckedChange = { session.erasing = true },
            colors = IconButtonDefaults.iconToggleButtonVibrantColors(),
            modifier = Modifier.size(size),
        ) {
            Icon(EraserIcon, contentDescription = eraser)
        }
    }

    val undo = stringResource(R.string.common_undo)

    Hint(undo, SketchKeys.Undo) {
        IconButton(onClick = session::undo, enabled = session.canUndo, modifier = Modifier.size(size)) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = undo)
        }
    }

    val redo = stringResource(R.string.common_redo)

    Hint(redo, SketchKeys.Redo) {
        IconButton(onClick = session::redo, enabled = session.canRedo, modifier = Modifier.size(size)) {
            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = redo)
        }
    }

    val clear = stringResource(R.string.sketch_clear)

    Hint(clear) {
        IconButton(onClick = session::clear, enabled = session.strokes.isNotEmpty(), modifier = Modifier.size(size)) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = clear)
        }
    }
}

/** The pen's color as a dot as wide as it draws, in a ring: open, the ring is the toolbar's own color. */
@Composable
private fun PenSwatch(color: Color, size: PenSize, open: Boolean) {
    val dot = when (size) {
        PenSize.Fine -> 10.dp
        PenSize.Medium -> 14.dp
        PenSize.Bold -> 20.dp
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .border(if (open) 2.dp else 1.5.dp, if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        Box(
            Modifier
                .size(dot)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

/**
 * The pens: the map's colors, then black and white, and three widths. A
 * card over the toolbar, which a tap on the pen opens and closes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SketchPens(session: SketchSession, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier.widthIn(max = 320.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.selectableGroup()) {
                for (color in session.colors) {
                    InkSwatch(Color(color), selected = color == session.color) {
                        session.color = color
                        session.erasing = false
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.selectableGroup()) {
                PenSize.entries.forEachIndexed { index, width ->
                    val word = stringResource(width.word)

                    ToggleButton(
                        checked = session.size == width,
                        onCheckedChange = {
                            session.size = width
                            session.erasing = false
                        },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            PenSize.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        // One of the widths, as TalkBack hears a choice among them.
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = word
                            },
                    ) {
                        Box(Modifier.size(width.units.dp / 2).clip(CircleShape).background(LocalContentColor.current))
                    }
                }
            }
        }
    }
}

/** One of the pens' colors: a dot, ringed when it's the pen's, which screen readers hear by its name. */
@Composable
private fun InkSwatch(color: Color, selected: Boolean, onPick: () -> Unit) {
    val name = colorNameText(colorName(color.toArgb()))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = name },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (selected) 40.dp else 34.dp)
                .clip(CircleShape)
                .background(color)
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape),
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The words for a pen's width. */
val PenSize.word: Int
    get() = when (this) {
        PenSize.Fine -> R.string.sketch_fine
        PenSize.Medium -> R.string.sketch_medium
        PenSize.Bold -> R.string.sketch_bold
    }

/** An eraser, tilted, on the line it rubs out: Material's own icons have none. */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Eraser", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            // The eraser's block, tilted.
            moveTo(14.5f, 3.6f)
            lineTo(20.4f, 9.5f)
            lineTo(11.4f, 18.5f)
            lineTo(7.6f, 18.5f)
            lineTo(3.6f, 14.5f)
            close()
            // Where its rubber ends.
            moveTo(9.1f, 9f)
            lineTo(15f, 14.9f)
            // The line it leaves.
            moveTo(11.4f, 18.5f)
            lineTo(20.4f, 18.5f)
        }
    }.build()
}
