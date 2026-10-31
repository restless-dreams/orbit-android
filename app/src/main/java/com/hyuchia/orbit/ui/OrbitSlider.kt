package com.hyuchia.orbit.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.theme.LocalAppSettings
import kotlin.math.floor
import kotlin.math.min

/** Material 3 Expressive's slider, in its sizes: a track 16 dp tall, and a handle 4 dp wide (2 while held or focused) and 44 tall. */
private val TrackHeight = 16.dp
private val HandleWidth = 4.dp
private val HeldHandleWidth = 2.dp
private val HandleHeight = 44.dp
private val HandleGap = 6.dp
private val InsideCorner = 2.dp
private val StopSize = 4.dp

/** A gradient's track, thinner, under a round thumb. */
private val GradientTrackHeight = 14.dp
private val ThumbSize = 28.dp

/** As tall as a finger needs, as Material's sliders are. */
private val SliderHeight = 48.dp

/** Arrow keys move a slider by a hundredth of its range, and Page Up and Page Down by a tenth, as Material's do. */
private const val KEY_STEPS = 100
private const val PAGE_STEPS = 10

/** How much of itself a slider that isn't enabled shows, as Material's disabled controls do. */
private const val DISABLED_ALPHA = 0.38f

/**
 * How a slider looks: Material's, a track filled up to a handle with a stop at its end; or a
 * gradient under a round thumb in `thumbColor`, as the color picker's hue.
 */
sealed interface SliderLook {
    data object Material : SliderLook

    data class Gradient(val colors: List<Color>, val thumbColor: Color) : SliderLook
}

/** What a key does to a slider. */
internal enum class SliderStep { Back, Forward, PageBack, PageForward, Start, End }

/**
 * A slider in Material 3 Expressive's look, for panels that can sit beside the map on a
 * tablet. Material's Slider reads an alignment line of its track as it measures, and that
 * throws while the map's pane animates: the pane measures in a lookahead pass, where the line
 * has nothing to come from (as `ListItem`'s baselines didn't, see [ListRow]). This one draws
 * its track and thumb itself, so nothing asks.
 *
 * A tap moves it there and a drag sideways follows the finger; a drag up or down is left to
 * the panel's scrolling. The arrow keys move it by a hundredth, or by `keyStep` when it's
 * given (a sound's timeline moves five seconds), Page Up and Page Down by a tenth, and Home
 * and End to the ends. Screen readers adjust it by a twentieth and say `valueDescription` for
 * its value. With haptics on, it ticks as a finger takes it past each of `ticks` stops along
 * the way. `onValueChangeFinished` hears once a change is done: a drag let go, a tap, a key
 * let up. One that isn't `enabled` shows faded and takes no change: a sound's timeline, until
 * it knows how long the sound is. Material's look draws its handle and its track up to it in
 * `color`, and the rest of the track in `trackColor` (in white over a sound's page, full screen).
 */
@Composable
fun OrbitSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: () -> Unit = {},
    label: String? = null,
    valueDescription: String? = null,
    ticks: Int = 20,
    look: SliderLook = SliderLook.Material,
    enabled: Boolean = true,
    keyStep: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val view = LocalView.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics by rememberUpdatedState(LocalAppSettings.current.haptics)
    val current by rememberUpdatedState(value)
    val range by rememberUpdatedState(valueRange)
    val stops by rememberUpdatedState(ticks)
    val arrowStep by rememberUpdatedState(keyStep)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var held by remember { mutableStateOf(false) }
    // The stop a finger last took it past, to tick once for each.
    var passed by remember { mutableIntStateOf(0) }
    val handleWidth by animateDpAsState(if (held || focused) HeldHandleWidth else HandleWidth, label = "handle")
    // How far in from each side the thumb's middle goes: the ends of its track.
    val inset = if (look is SliderLook.Gradient) ThumbSize / 2 else HandleWidth / 2

    // A finger at `x` along the slider, `width` wide: the value there, ticking past stops.
    fun touch(x: Float, width: Float, insetPixels: Float) {
        val next = valueAt(fractionAt(x, width, insetPixels, rtl), range)
        val stop = stopOf(next, range, stops)

        if (haptics && stop != passed) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        passed = stop

        if (next != current) {
            change(next)
        }
    }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pointerInput(rtl, inset, enabled) {
                if (!enabled) {
                    return@pointerInput
                }

                detectTapGestures { position ->
                    passed = stopOf(current, range, stops)
                    touch(position.x, size.width.toFloat(), inset.toPx())
                    finish()
                }
            }
            .pointerInput(rtl, inset, enabled) {
                if (!enabled) {
                    return@pointerInput
                }

                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        held = true
                        passed = stopOf(current, range, stops)
                        touch(position.x, size.width.toFloat(), inset.toPx())
                    },
                    onDragEnd = {
                        held = false
                        finish()
                    },
                    onDragCancel = {
                        held = false
                        finish()
                    },
                ) { moved, _ ->
                    moved.consume()
                    touch(moved.position.x, size.width.toFloat(), inset.toPx())
                }
            }
            .onKeyEvent { event ->
                if (!enabled) {
                    return@onKeyEvent false
                }

                val step = when (event.key) {
                    Key.DirectionRight -> if (rtl) SliderStep.Back else SliderStep.Forward
                    Key.DirectionLeft -> if (rtl) SliderStep.Forward else SliderStep.Back
                    Key.DirectionUp -> SliderStep.Forward
                    Key.DirectionDown -> SliderStep.Back
                    Key.PageUp -> SliderStep.PageForward
                    Key.PageDown -> SliderStep.PageBack
                    Key.MoveHome -> SliderStep.Start
                    Key.MoveEnd -> SliderStep.End
                    else -> return@onKeyEvent false
                }

                when (event.type) {
                    KeyEventType.KeyDown -> change(stepped(current, range, step, arrowStep))
                    KeyEventType.KeyUp -> finish()
                }

                true
            }
            .focusable(enabled, interactionSource = interactions)
            .semantics {
                label?.let { contentDescription = it }
                valueDescription?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)

                if (!enabled) {
                    disabled()
                    return@semantics
                }

                setProgress { target ->
                    val next = target.coerceIn(range.start, range.endInclusive)

                    if (next == current) {
                        false
                    } else {
                        change(next)
                        finish()
                        true
                    }
                }
            }
            .drawBehind {
                val fraction = fractionOf(value, valueRange)

                // Drawn from the start, and turned around for languages written right to left.
                scale(if (rtl) -1f else 1f, 1f) {
                    when (look) {
                        SliderLook.Material -> drawMaterial(fraction, handleWidth, color, trackColor)
                        is SliderLook.Gradient -> drawGradient(fraction, look, focused, color)
                    }
                }
            },
    )
}

/** Where along a slider `width` wide a finger at `x` is, from 0 at its start to 1 at its end, the thumb's middle going `inset` in from each side. */
internal fun fractionAt(x: Float, width: Float, inset: Float, rtl: Boolean): Float {
    val length = width - 2 * inset

    if (length <= 0f) {
        return 0f
    }

    val fraction = ((x - inset) / length).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

/** How far along `range` `value` is, from 0 to 1. */
internal fun fractionOf(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val length = range.endInclusive - range.start
    return if (length <= 0f) 0f else ((value - range.start) / length).coerceIn(0f, 1f)
}

/** The value `fraction` of the way along `range`. */
internal fun valueAt(fraction: Float, range: ClosedFloatingPointRange<Float>): Float = range.start + fraction * (range.endInclusive - range.start)

/** Which of `stops` evenly spaced stops `value` is at or past: 0 at the start, `stops` at the end. */
internal fun stopOf(value: Float, range: ClosedFloatingPointRange<Float>, stops: Int): Int = if (stops <= 0) 0 else floor(fractionOf(value, range) * stops).toInt()

/** `value` after a key's `step`, kept within `range`: an arrow key's is `keyStep`, or a hundredth of the range. */
internal fun stepped(value: Float, range: ClosedFloatingPointRange<Float>, step: SliderStep, keyStep: Float? = null): Float {
    val length = range.endInclusive - range.start
    val arrow = keyStep ?: (length / KEY_STEPS)

    val next = when (step) {
        SliderStep.Back -> value - arrow
        SliderStep.Forward -> value + arrow
        SliderStep.PageBack -> value - length / PAGE_STEPS
        SliderStep.PageForward -> value + length / PAGE_STEPS
        SliderStep.Start -> range.start
        SliderStep.End -> range.endInclusive
    }

    return next.coerceIn(range.start, range.endInclusive)
}

/**
 * Material's look: the track filled up to the handle in `filled`, the rest in `empty`, a gap
 * on each side of the handle, and a stop at the end while the handle isn't there. Focus thins
 * the handle, as holding it does: that's in `handleWidth`.
 */
private fun DrawScope.drawMaterial(fraction: Float, handleWidth: Dp, filled: Color, empty: Color) {
    val trackHeight = TrackHeight.toPx()
    val top = (size.height - trackHeight) / 2
    val outside = trackHeight / 2
    val inside = InsideCorner.toPx()
    val half = HandleWidth.toPx() / 2
    val handle = half + fraction * (size.width - 2 * half)
    val handleHalf = handleWidth.toPx() / 2
    val gap = HandleGap.toPx()
    val filledEnd = handle - handleHalf - gap
    val emptyStart = handle + handleHalf + gap

    // The track ends where the handle's middle can go, half a handle in from each side.
    if (filledEnd > half) {
        drawTrack(half, filledEnd, top, trackHeight, startCorner = outside, endCorner = inside, filled)
    }

    if (emptyStart < size.width - half) {
        drawTrack(emptyStart, size.width - half, top, trackHeight, startCorner = inside, endCorner = outside, empty)
        val stop = size.width - half - trackHeight / 2

        if (emptyStart < stop - StopSize.toPx()) {
            drawCircle(filled, radius = StopSize.toPx() / 2, center = Offset(stop, size.height / 2))
        }
    }

    val handleHeight = HandleHeight.toPx()

    drawRoundRect(
        filled,
        topLeft = Offset(handle - handleHalf, (size.height - handleHeight) / 2),
        size = Size(handleHalf * 2, handleHeight),
        cornerRadius = CornerRadius(handleHalf),
    )
}

/** A gradient's look: its colors along the track, and a raised white thumb with the color at its middle, ringed while focused. */
private fun DrawScope.drawGradient(fraction: Float, look: SliderLook.Gradient, focused: Boolean, ring: Color) {
    val radius = ThumbSize.toPx() / 2
    val trackHeight = GradientTrackHeight.toPx()
    val center = Offset(radius + fraction * (size.width - 2 * radius), size.height / 2)
    val shadow = center + Offset(0f, 1.dp.toPx())

    drawRoundRect(
        Brush.horizontalGradient(look.colors, startX = radius, endX = size.width - radius),
        topLeft = Offset(radius, (size.height - trackHeight) / 2),
        size = Size(size.width - 2 * radius, trackHeight),
        cornerRadius = CornerRadius(trackHeight / 2),
    )
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), center = shadow, radius = radius + 3.dp.toPx()), radius = radius + 3.dp.toPx(), center = shadow)
    drawCircle(Color.White, radius = radius, center = center)
    drawCircle(look.thumbColor, radius = radius - 4.dp.toPx(), center = center)

    if (focused) {
        drawCircle(ring, radius = radius + 4.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    }
}

/** A stretch of track from `start` to `end`, its corners rounded by `startCorner` and `endCorner`, never more than it's wide. */
private fun DrawScope.drawTrack(start: Float, end: Float, top: Float, height: Float, startCorner: Float, endCorner: Float, color: Color) {
    val width = end - start
    val first = CornerRadius(min(startCorner, width / 2), min(startCorner, height / 2))
    val last = CornerRadius(min(endCorner, width / 2), min(endCorner, height / 2))
    val shape = RoundRect(start, top, end, top + height, topLeftCornerRadius = first, topRightCornerRadius = last, bottomRightCornerRadius = last, bottomLeftCornerRadius = first)
    drawPath(Path().apply { addRoundRect(shape) }, color)
}
