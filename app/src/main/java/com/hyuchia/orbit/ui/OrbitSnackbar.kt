package com.hyuchia.orbit.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/** Material 3's snackbar measures: at most 600 dp wide, 48 dp tall for a line and 68 for more, the first line's baseline 30 dp down. */
private val ContainerMaxWidth = 600.dp
private val SingleLineHeight = 48.dp
private val TwoLinesHeight = 68.dp
private val HeightToFirstLine = 30.dp
private val HorizontalSpacing = 16.dp
private val HorizontalSpacingButtonSide = 8.dp
private val TextVerticalPadding = 6.dp
private val TextEndExtraSpacing = 8.dp
private val Elevation = 6.dp

/** How far a snackbar is swiped, as a share of its width, for it to go; or how fast, per second, flung. */
private const val SWIPE_AWAY = 0.4f
private val SwipeVelocity = 125.dp

private const val TEXT = "text"
private const val ACTION = "action"
private const val DISMISS = "dismiss"

/**
 * Snackbars for any screen: Material's host, with its timing, its way in and
 * out, and what TalkBack hears and can do (it reads each one out, and can
 * dismiss it), showing [OrbitSnackbar]s.
 */
@Composable
fun OrbitSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> OrbitSnackbar(data) }
}

/**
 * Material 3's snackbar, laid out as Material lays it out, for screens that
 * can sit in a tablet's pane. Material's snackbar lines its action up with its
 * text's baselines as it measures, and that throws while a pane animates (a
 * panel opening beside the map): the pane measures in a lookahead pass, where
 * text has no baselines to give. This one learns where the message's lines
 * fall by measuring the message itself, so nothing asks. It can also be
 * swiped away to either side.
 */
@Composable
fun OrbitSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val visuals = data.visuals
    // The message's style, as its Text has it, and the action's, as its button gives it: the same for measuring them.
    val textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
    val labelStyle = textStyle.merge(MaterialTheme.typography.labelLarge)
    val measurer = rememberTextMeasurer()
    val flingVelocity = with(LocalDensity.current) { SwipeVelocity.toPx() }
    var offset by remember(data) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .padding(12.dp)
            .onSizeChanged { width = it.width }
            .graphicsLayer {
                translationX = offset
                alpha = if (width > 0) 1f - (abs(offset) / width).coerceIn(0f, 1f) else 1f
            }
            .draggable(
                state = rememberDraggableState { delta -> offset += delta },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val side = if (offset != 0f) sign(offset) else sign(velocity)
                    val away = width > 0 && side != 0f && (abs(offset) > width * SWIPE_AWAY || (abs(velocity) > flingVelocity && sign(velocity) == side))

                    if (away) {
                        animate(offset, side * width, velocity, tween(150)) { value, _ -> offset = value }
                        data.dismiss()
                    } else {
                        animate(offset, 0f, velocity, spring()) { value, _ -> offset = value }
                    }
                },
            ),
        shape = SnackbarDefaults.shape,
        color = SnackbarDefaults.color,
        contentColor = SnackbarDefaults.contentColor,
        shadowElevation = Elevation,
    ) {
        Layout(
            content = {
                Box(Modifier.layoutId(TEXT).padding(vertical = TextVerticalPadding)) {
                    Text(visuals.message, style = textStyle)
                }

                visuals.actionLabel?.let { label ->
                    Box(Modifier.layoutId(ACTION)) {
                        TextButton(onClick = data::performAction, colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)) {
                            Text(label)
                        }
                    }
                }

                if (visuals.withDismissAction) {
                    Box(Modifier.layoutId(DISMISS)) {
                        CompositionLocalProvider(LocalContentColor provides SnackbarDefaults.dismissActionContentColor) {
                            val close = stringResource(R.string.common_close)

                            Hint(close) {
                                IconButton(onClick = data::dismiss) {
                                    Icon(Icons.Filled.Close, contentDescription = close)
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.padding(start = HorizontalSpacing, end = if (visuals.withDismissAction) 0.dp else HorizontalSpacingButtonSide),
        ) { measurables, constraints ->
            val containerWidth = min(constraints.maxWidth, ContainerMaxWidth.roundToPx())
            val action = measurables.firstOrNull { it.layoutId == ACTION }?.measure(constraints)
            val dismiss = measurables.firstOrNull { it.layoutId == DISMISS }?.measure(constraints)
            val extraSpacing = if (dismiss == null) TextEndExtraSpacing.roundToPx() else 0
            val textMaxWidth = (containerWidth - (action?.width ?: 0) - (dismiss?.width ?: 0) - extraSpacing).coerceAtLeast(constraints.minWidth)
            val text = measurables.first { it.layoutId == TEXT }.measure(constraints.copy(minHeight = 0, maxWidth = textMaxWidth))
            // Where the message's lines and the action's label fall: measured as their Texts measure them, rather than asked of their
            // baselines. The button keeps its label in its middle, rounded as Compose rounds a middle.
            val lines = measurer.measure(visuals.message, textStyle, constraints = Constraints(maxWidth = textMaxWidth))
            val label = visuals.actionLabel?.let { measurer.measure(it, labelStyle) }

            val actionBaseline = if (action != null && label != null) {
                ((action.height - label.size.height) / 2f).roundToInt() + label.firstBaseline.roundToInt()
            } else {
                null
            }

            val placement = placeSnackbar(
                SnackbarParts(
                    textHeight = text.height,
                    oneLine = lines.lineCount <= 1,
                    textBaseline = TextVerticalPadding.roundToPx() + lines.firstBaseline.roundToInt(),
                    actionHeight = action?.height,
                    actionBaseline = actionBaseline,
                    dismissHeight = dismiss?.height,
                ),
                SnackbarHeights(SingleLineHeight.roundToPx(), TwoLinesHeight.roundToPx(), HeightToFirstLine.roundToPx()),
            )

            layout(containerWidth, placement.height) {
                text.placeRelative(0, placement.textY)
                action?.placeRelative(containerWidth - (dismiss?.width ?: 0) - action.width, placement.actionY)
                dismiss?.placeRelative(containerWidth - dismiss.width, placement.dismissY)
            }
        }
    }
}

/**
 * A snackbar's measured parts: its message's height, whether it's a line,
 * and its first baseline (in its padding); its buttons' heights, if any, and
 * where the action's label has its baseline, from the button's top.
 */
internal data class SnackbarParts(
    val textHeight: Int,
    val oneLine: Boolean,
    val textBaseline: Int,
    val actionHeight: Int?,
    val dismissHeight: Int?,
    val actionBaseline: Int? = null,
)

/** Material's heights: for a line, for more, and to the first of several lines' baseline. */
internal data class SnackbarHeights(val singleLine: Int, val twoLines: Int, val toFirstLine: Int)

/** Where a snackbar's parts go, from its top: its height, and its message's, action's and dismiss button's places. */
internal data class SnackbarPlacement(val height: Int, val textY: Int, val actionY: Int, val dismissY: Int)

/**
 * Material's layout of a snackbar: a line of text sits in the middle of 48 dp,
 * its action's label on the same baseline and a dismiss button in the
 * middle; several lines put the first one's baseline 30 dp down, and the
 * buttons in the middle of at least 68 dp.
 */
internal fun placeSnackbar(parts: SnackbarParts, heights: SnackbarHeights): SnackbarPlacement {
    val buttons = maxOf(parts.actionHeight ?: 0, parts.dismissHeight ?: 0)

    val (height, textY) = if (parts.oneLine) {
        val height = maxOf(heights.singleLine, buttons)
        height to (height - parts.textHeight) / 2
    } else {
        val textY = heights.toFirstLine - parts.textBaseline
        maxOf(heights.twoLines, textY + parts.textHeight) to textY
    }

    return SnackbarPlacement(
        height = height,
        textY = textY,
        actionY = when {
            parts.actionHeight == null -> 0
            parts.oneLine && parts.actionBaseline != null -> textY + parts.textBaseline - parts.actionBaseline
            else -> (height - parts.actionHeight) / 2
        },
        dismissY = parts.dismissHeight?.let { (height - it) / 2 } ?: 0,
    )
}
