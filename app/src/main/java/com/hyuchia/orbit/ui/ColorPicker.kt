package com.hyuchia.orbit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.DialogProperties
import com.hyuchia.orbit.R
import java.text.NumberFormat

/** The dark text Orbit puts on light colors, as the color choices' check marks are drawn. */
private const val DARK_TEXT = 0xFF1F2733.toInt()

/** Saturation and brightness move this far for each arrow key, and five times as far with Shift. */
private const val KEY_STEP = 0.02f

/** And this far for each of a screen reader's actions. */
private const val ACTION_STEP = 0.1f

/** Every hue, all the way around: the slider's track, and the custom color choice's wheel. */
internal val HueColors = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(HsvColor(it, 1f, 1f).toArgb()) }

private val HsvSaver = listSaver<HsvColor, Float>(
    save = { listOf(it.hue, it.saturation, it.value) },
    restore = { HsvColor(it[0], it[1], it[2]) },
)

/**
 * Any color for an idea: an area for saturation and brightness, a slider for the hue and the
 * color's hex code, beside the idea in that color. The color is kept once, on Done, since each
 * change to a map is a step to undo. `label` is the idea's text, to preview it with.
 */
@Composable
fun ColorPickerDialog(title: String, label: String, initial: Color, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var color by rememberSaveable(stateSaver = HsvSaver) { mutableStateOf(HsvColor.fromArgb(initial.toArgb())) }
    var hexText by rememberSaveable { mutableStateOf(color.hex().removePrefix("#")) }
    val typed = parseHexColor(hexText)

    fun change(next: HsvColor) {
        color = next
        hexText = next.hex().removePrefix("#")
    }

    fun done() {
        if (typed != null) {
            onPick(color.hex())
        }
    }

    // As wide as Material lets a dialog be, rather than Android's narrower default, so a phone on its side fits it all.
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = {
            EscapeCloses(onDismiss)
            Text(title)
        },
        text = {
            BoxWithConstraints {
                // A phone on its side has the room beside the area rather than under it.
                val beside = maxHeight < 360.dp && maxWidth > 420.dp
                val besideHeight = min(170.dp, maxHeight)

                val hexField = @Composable { modifier: Modifier ->
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            val digits = text.removePrefix("#").filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase()
                            hexText = digits
                            parseHexColor(digits)?.let { color = HsvColor.fromArgb(it) }
                        },
                        prefix = { Text("#") },
                        label = { Text(stringResource(R.string.ui_color_hex_code)) },
                        isError = typed == null,
                        supportingText = if (typed == null) ({ Text(stringResource(R.string.ui_color_hex_code_help)) }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { done() }),
                        modifier = modifier,
                    )
                }

                if (beside) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SaturationAndBrightness(color, ::change, Modifier.width(220.dp).height(besideHeight))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                IdeaPreview(label, color, 64.dp)
                                hexField(Modifier.weight(1f))
                            }

                            HueSlider(color, ::change)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IdeaPreview(label, color, 72.dp)
                            hexField(Modifier.weight(1f))
                        }

                        SaturationAndBrightness(color, ::change, Modifier.fillMaxWidth().height(180.dp))
                        HueSlider(color, ::change)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::done, enabled = typed != null) { Text(stringResource(R.string.common_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** The idea as the map would draw it in the color: a planet with its name in the text that reads best on it. */
@Composable
private fun IdeaPreview(label: String, color: HsvColor, size: Dp) {
    val fill = color.toArgb()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(Color(fill), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(size / 10)
            .clearAndSetSemantics {},
    ) {
        Text(
            label,
            color = Color(if (prefersLightText(fill, DARK_TEXT)) 0xFFFFFFFF.toInt() else DARK_TEXT),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Saturation from left to right and brightness from bottom to top, in the hue: tap or drag to
 * choose. With a keyboard the arrows move the mark, and screen readers have actions for each way.
 */
@Composable
private fun SaturationAndBrightness(color: HsvColor, onChange: (HsvColor) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(color)
    val change by rememberUpdatedState(onChange)
    var focused by remember { mutableStateOf(false) }
    val focusRing = MaterialTheme.colorScheme.primary
    val percent = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0])
    val hue = Color(HsvColor(color.hue, 1f, 1f).toArgb())

    fun moved(saturation: Float = color.saturation, value: Float = color.value) = color.copy(saturation = saturation.coerceIn(0f, 1f), value = value.coerceIn(0f, 1f))

    val steps = listOf(
        R.string.ui_color_more_vivid to { moved(saturation = color.saturation + ACTION_STEP) },
        R.string.ui_color_less_vivid to { moved(saturation = color.saturation - ACTION_STEP) },
        R.string.ui_color_brighter to { moved(value = color.value + ACTION_STEP) },
        R.string.ui_color_darker to { moved(value = color.value - ACTION_STEP) },
    )

    val actions = steps.map { (label, next) ->
        CustomAccessibilityAction(stringResource(label)) {
            onChange(next())
            true
        }
    }

    val description = stringResource(R.string.ui_color_saturation_and_brightness)
    val state = stringResource(R.string.ui_color_saturation_and_brightness_now, percent.format(color.saturation), percent.format(color.value))

    Canvas(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }

                val step = if (event.isShiftPressed) KEY_STEP * 5 else KEY_STEP

                val next = when (event.key) {
                    Key.DirectionLeft -> moved(saturation = color.saturation - step)
                    Key.DirectionRight -> moved(saturation = color.saturation + step)
                    Key.DirectionUp -> moved(value = color.value + step)
                    Key.DirectionDown -> moved(value = color.value - step)
                    else -> return@onKeyEvent false
                }

                onChange(next)
                true
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()

                    fun place(position: Offset) {
                        val saturation = (position.x / size.width).coerceIn(0f, 1f)
                        val value = 1f - (position.y / size.height).coerceIn(0f, 1f)
                        change(current.copy(saturation = saturation, value = value))
                    }

                    place(down.position)
                    down.consume()

                    drag(down.id) { moving ->
                        place(moving.position)
                        moving.consume()
                    }
                }
            }
            .semantics {
                contentDescription = description
                stateDescription = state
                customActions = actions
            },
    ) {
        drawRect(hue)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val mark = Offset(color.saturation * size.width, (1 - color.value) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 12.dp.toPx(), center = mark, style = Stroke(5.dp.toPx()))
        drawCircle(Color.White, radius = 12.dp.toPx(), center = mark, style = Stroke(3.dp.toPx()))

        if (focused) {
            drawRoundRect(focusRing, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(4.dp.toPx()))
        }
    }
}

/** The hue, around the color wheel from red to red. Screen readers say the color it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HueSlider(color: HsvColor, onChange: (HsvColor) -> Unit) {
    val pure = HsvColor(color.hue, 1f, 1f)
    val description = stringResource(R.string.ui_color_hue)
    val state = colorNameText(colorName(pure.toArgb()))
    val slider = remember { SliderState(color.hue, 0, 0f..360f) }

    // The hue also changes as a hex code is typed: the slider follows it.
    SideEffect { slider.value = color.hue }

    Slider(
        state = slider,
        onValueChange = { onChange(color.copy(hue = it)) },
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .padding(4.dp)
                    .background(Color(pure.toArgb()), CircleShape),
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(HueColors)),
            )
        },
        modifier = Modifier.semantics {
            contentDescription = description
            stateDescription = state
        },
    )
}

/** A color's rough name in the app's language: "light blue", "azul claro". */
@Composable
fun colorNameText(name: ColorName): String {
    val family = stringResource(
        when (name.family) {
            ColorFamily.Red -> R.string.ui_color_family_red
            ColorFamily.Orange -> R.string.ui_color_family_orange
            ColorFamily.Yellow -> R.string.ui_color_family_yellow
            ColorFamily.Green -> R.string.ui_color_family_green
            ColorFamily.Teal -> R.string.ui_color_family_teal
            ColorFamily.Blue -> R.string.ui_color_family_blue
            ColorFamily.Purple -> R.string.ui_color_family_purple
            ColorFamily.Pink -> R.string.ui_color_family_pink
            ColorFamily.Brown -> R.string.ui_color_family_brown
            ColorFamily.Gray -> R.string.ui_color_family_gray
            ColorFamily.Black -> R.string.ui_color_family_black
            ColorFamily.White -> R.string.ui_color_family_white
        },
    )

    return when (name.shade) {
        ColorShade.Light -> stringResource(R.string.ui_color_light, family)
        ColorShade.Dark -> stringResource(R.string.ui_color_dark, family)
        ColorShade.Plain -> family
    }
}
