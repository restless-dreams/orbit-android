package com.hyuchia.orbit.map

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.privacy.CountsOverTheMap
import com.hyuchia.orbit.sketch.SketchCanvas
import com.hyuchia.orbit.sketch.SketchKeys
import com.hyuchia.orbit.sketch.SketchPens
import com.hyuchia.orbit.sketch.SketchSession
import com.hyuchia.orbit.sketch.SketchToolbar
import com.hyuchia.orbit.ui.Hint

/**
 * Over the map while an idea's planet is sketched on: the canvas, laid over
 * the planet's circle once the page holds it up close; the way out, in the
 * corner the planet leaves free; the toolbar, under the planet, or beside it
 * on a `short` screen, clear of the bars (`clearOfBars`); and the pens over
 * it. The description, which comes up as Done is tapped, is over the whole
 * window instead ([SketchDescription]). Asking first whether a sketch may
 * take a picture's place, and whether to let strokes go, are dialogs here.
 */
@Composable
internal fun SketchOverlay(model: MapViewModel, ui: MapUi, short: Boolean, clearOfBars: PaddingValues, modifier: Modifier = Modifier) {
    val sketching = ui.sketch ?: return
    val session by model.sketchSession.collectAsState()

    if (sketching.phase == SketchPhase.Asking) {
        ReplacePictureDialog(ui.nodes[sketching.nodeId]?.label(stringResource(R.string.common_untitled)).orEmpty(), onSketch = model::replaceAndSketch, onCancel = { model.cancelSketch() })
        return
    }

    val drawing = session ?: return

    // Waiting for the map to be drawn again: nothing over the map's place, which says why, and loads it again. Back still
    // asks before strokes go.
    if (sketching.held) {
        BackHandler { model.cancelSketch() }

        if (sketching.discarding) {
            DiscardSketchDialog(onDiscard = { model.cancelSketch(letGo = true) }, onKeep = model::keepSketching)
        }

        return
    }

    var pensOpen by rememberSaveable(sketching.nodeId) { mutableStateOf(false) }
    val phase = sketching.phase
    val idea = ui.nodes[sketching.nodeId]?.label(stringResource(R.string.common_untitled)).orEmpty()
    val focus = remember { FocusRequester() }

    // Back steps out of what's open first: the pens, the description, then the sketch, asking before strokes go. While the
    // sketch is kept and the map flies back, it waits.
    BackHandler {
        when {
            phase == SketchPhase.Saving || phase == SketchPhase.Closing -> Unit
            pensOpen -> pensOpen = false
            phase == SketchPhase.Describing -> model.keepSketching()
            else -> model.cancelSketch()
        }
    }

    LaunchedEffect(phase) {
        if (phase != SketchPhase.Drawing) {
            pensOpen = false
        }

        // The sketch takes the keyboard from the map, for its keys.
        if (phase == SketchPhase.Drawing) {
            focus.requestFocus()
        }
    }

    Box(modifier.fillMaxSize()) {
        val circle = sketching.circle

        if (circle != null) {
            val density = LocalDensity.current

            SketchCanvas(
                session = drawing,
                center = with(density) { Offset(circle.x.dp.toPx(), circle.y.dp.toPx()) },
                radius = with(density) { circle.radius.dp.toPx() },
                shown = phase == SketchPhase.Drawing || phase == SketchPhase.Describing || phase == SketchPhase.Saving,
                drawing = phase == SketchPhase.Drawing,
                // The keyboard's focus leaves the map's page for the sketch: its keys, and Tab through its tools.
                paperModifier = Modifier
                    .focusRequester(focus)
                    .focusable(),
                description = stringResource(R.string.sketch_canvas, idea),
                strokesSaid = pluralStringResource(R.plurals.sketch_strokes, drawing.strokes.size, drawing.strokes.size),
            )
        } else {
            // Until the planet rests, presses go nowhere.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }

        // With the pens open, a press on the canvas or the map puts them away rather than drawing, as a menu's would.
        if (pensOpen && phase == SketchPhase.Drawing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }

                                if (event.type == PointerEventType.Press) {
                                    pensOpen = false
                                }
                            }
                        }
                    },
            )
        }

        AnimatedVisibility(
            visible = phase == SketchPhase.Drawing || phase == SketchPhase.Describing,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(clearOfBars)
                .padding(12.dp),
        ) {
            val close = stringResource(R.string.sketch_close)

            Hint(close, SketchKeys.Close) {
                FilledTonalIconButton(onClick = { model.cancelSketch() }) {
                    Icon(Icons.Filled.Close, contentDescription = close)
                }
            }
        }

        SketchTools(drawing, phase, short, clearOfBars, pensOpen, onPens = { pensOpen = !pensOpen }, onDone = model::finishSketch)
    }

    if (sketching.discarding) {
        DiscardSketchDialog(onDiscard = { model.cancelSketch(letGo = true) }, onKeep = model::keepSketching)
    }

    if (sketching.confirming) {
        ReplaceLandedDialog(idea, onReplace = model::replaceLandedPicture, onKeep = model::keepSketching)
    }
}

/** The toolbar and, over it, the pens: under the planet, or beside it on a short screen. */
@Composable
private fun BoxScope.SketchTools(session: SketchSession, phase: SketchPhase, short: Boolean, clearOfBars: PaddingValues, pensOpen: Boolean, onPens: () -> Unit, onDone: () -> Unit) {
    val side = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val drawing = phase == SketchPhase.Drawing

    AnimatedVisibility(
        visible = drawing,
        enter = fadeIn() + if (short) slideInHorizontally { side * it } else slideInVertically { it },
        exit = fadeOut() + if (short) slideOutHorizontally { side * it } else slideOutVertically { it },
        modifier = Modifier
            .align(if (short) Alignment.CenterEnd else Alignment.BottomCenter)
            .padding(clearOfBars)
            .padding(if (short) PaddingValues(end = 16.dp) else PaddingValues(bottom = 16.dp)),
    ) {
        SketchToolbar(session, vertical = short, pensOpen = pensOpen, onPens = onPens, onDone = onDone)
    }

    AnimatedVisibility(
        visible = drawing && pensOpen,
        enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = if (short) androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f) else androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = Modifier
            .align(if (short) Alignment.CenterEnd else Alignment.BottomCenter)
            .padding(clearOfBars)
            .padding(if (short) PaddingValues(end = 104.dp) else PaddingValues(bottom = 104.dp, start = 16.dp, end = 16.dp)),
    ) {
        SketchPens(session)
    }
}

/**
 * The description of the sketch, which comes up as Done is tapped, over the
 * whole window rather than the map's part of it. The keyboard comes up under
 * it, and on a phone on its side it leaves less room above it than the map
 * has under the top bar: so the description lies over the bars, which are
 * dimmed and take nothing while a planet is sketched on, and the map and the
 * canvas stay where they are behind it.
 */
@Composable
internal fun SketchDescription(model: MapViewModel, ui: MapUi, short: Boolean, modifier: Modifier = Modifier) {
    val sketching = ui.sketch ?: return
    val session by model.sketchSession.collectAsState()

    // From when there's a drawing, as the canvas has it, so a sketch drawn on further starts with the description it had.
    if (sketching.phase == SketchPhase.Asking || session == null) {
        return
    }

    var description by rememberSaveable(sketching.nodeId) { mutableStateOf(sketching.description) }

    DescriptionLayer(
        shown = sketching.phase == SketchPhase.Describing,
        short = short,
        description = description,
        onChange = { description = it },
        onBack = model::keepSketching,
        onDone = { model.saveSketch(description) },
        modifier = modifier,
    )
}

/**
 * Where the description goes: at the foot of the window, above the keyboard
 * and clear of the bars and cutouts. On a short screen (a phone on its side),
 * or wherever the keyboard leaves it less room than it takes, its title, field
 * and buttons go in one row, low enough to fit there.
 */
@Composable
internal fun DescriptionLayer(
    shown: Boolean,
    short: Boolean,
    description: String,
    onChange: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val compact = short || maxHeight < RoomForDescription * LocalDensity.current.fontScale.coerceAtLeast(1f)

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(if (compact) 8.dp else 16.dp),
        ) {
            DescribeSketch(description, compact, onChange = onChange, onBack = onBack, onDone = onDone)
        }
    }
}

/** The room the description takes in its column, with the space around it: where there's less, it's one row. */
private val RoomForDescription = 224.dp

/** What the sketch shows, for screen readers: offered as Done is tapped, kept with the sketch. In one row when `compact`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescribeSketch(description: String, compact: Boolean, onChange: (String) -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    val field = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val keyboard = configuration.keyboard != Configuration.KEYBOARD_NOKEYS && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    val title = stringResource(R.string.sketch_describe_title)

    // With a keyboard, the field takes the focus as the panel comes up: typing goes straight into it, and Enter keeps
    // the sketch. A touch screen's keyboard waits for a tap on the field, as the description is only offered.
    LaunchedEffect(Unit) {
        if (keyboard) {
            field.requestFocus()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier
            .widthIn(max = if (compact) 720.dp else 480.dp)
            .fillMaxWidth()
            // Screen readers say the panel as it comes up.
            .semantics { paneTitle = title },
    ) {
        // The same title, field and buttons either way, so a phone turned with the field in use keeps it, and the keyboard.
        DescriptionParts(
            compact = compact,
            modifier = Modifier.padding(if (compact) PaddingValues(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp) else PaddingValues(20.dp)),
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            field = { DescriptionField(description, onChange, onDone, field, compact) },
            buttons = {
                // Side by side, or Done under Keep drawing where a narrow phone hasn't room for both in a language's words.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    DescriptionButtons(onBack, onDone)
                }
            },
        )
    }
}

/**
 * Lays the description's parts out: the title, the field under it and the
 * buttons at the end, under the field; or, `compact`, in one row, the title
 * at most [CompactTitleWidth] wide, the buttons at the end and the field
 * taking the room between them. A row too narrow for the field to keep
 * [LeastFieldWidth] beside them all, in a window beside another app's or in
 * larger words, leaves the title out (the field says what it's for, and
 * screen readers hear the title as the panel comes up), then puts Done
 * under Keep drawing, and narrower still gives the field the row and the
 * buttons one under it, where the room above the keyboard is tall enough
 * for both. Where it isn't, the field narrows beside the buttons, down to
 * [NarrowestFieldWidth], so their words stay whole and all of it shows.
 */
@Composable
private fun DescriptionParts(compact: Boolean, modifier: Modifier, title: @Composable () -> Unit, field: @Composable () -> Unit, buttons: @Composable () -> Unit) {
    Layout(contents = listOf(title, field, buttons), modifier = modifier) { (titles, fields, buttonRows), constraints ->
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)

        if (compact) {
            val least = (LeastFieldWidth * fontScale.coerceAtLeast(1f)).roundToPx()
            val headingWidth = minOf(titles.first().maxIntrinsicWidth(constraints.maxHeight), CompactTitleWidth.roundToPx(), width)
            // The buttons side by side, or Done under Keep drawing: as wide as the widest of them. Asked with no limit
            // on their height, as a row asked to fit a short room would say it can't stack them.
            val inOneRow = buttonRows.first().maxIntrinsicWidth(Constraints.Infinity)
            val stacked = buttonRows.first().minIntrinsicWidth(Constraints.Infinity)
            val beside = width - stacked - gap
            val below = 8.dp.roundToPx()
            // Too narrow for the field beside the buttons: the field across, the buttons under it at the end, where
            // the room is tall enough for that. Above the keyboard on a phone on its side it isn't: there the field
            // narrows beside the buttons instead, as long as it keeps some room to type.
            val across = beside < least &&
                (beside < NarrowestFieldWidth.roundToPx() || fields.first().minIntrinsicHeight(width) + below + buttonRows.first().minIntrinsicHeight(width) <= constraints.maxHeight)
            // The buttons as tall as they need: a row of them told to fit a shorter room leaves Done out.
            val tall = loose.copy(maxHeight = Constraints.Infinity)

            if (across) {
                val input = fields.first().measure(loose.copy(minWidth = width, maxWidth = width))
                val actions = buttonRows.first().measure(tall)

                return@Layout layout(width, input.height + below + actions.height) {
                    input.placeRelative(0, 0)
                    actions.placeRelative(width - actions.width, input.height + below)
                }
            }

            val withTitle = width - headingWidth - inOneRow - 2 * gap >= least
            // The buttons as wide as the field leaves them, but never narrower than their words: the field narrows first.
            val actions = buttonRows.first().measure(tall.copy(maxWidth = if (width - inOneRow - gap >= least) width else maxOf(width - least - gap, stacked)))
            val heading = if (withTitle) titles.first().measure(loose.copy(maxWidth = headingWidth)) else null
            val start = heading?.let { it.width + gap } ?: 0
            val fieldWidth = (width - start - actions.width - gap).coerceAtLeast(0)
            val input = fields.first().measure(loose.copy(minWidth = fieldWidth, maxWidth = fieldWidth))
            val height = maxOf(heading?.height ?: 0, input.height, actions.height)

            layout(width, height) {
                heading?.placeRelative(0, (height - heading.height) / 2)
                input.placeRelative(start, (height - input.height) / 2)
                actions.placeRelative(width - actions.width, (height - actions.height) / 2)
            }
        } else {
            val heading = titles.first().measure(loose)
            val input = fields.first().measure(loose.copy(minWidth = width))
            val actions = buttonRows.first().measure(loose)
            val height = heading.height + gap + input.height + gap + actions.height

            layout(width, height) {
                heading.placeRelative(0, 0)
                input.placeRelative(0, heading.height + gap)
                actions.placeRelative(width - actions.width, heading.height + gap + input.height + gap)
            }
        }
    }
}

/** The widest the title is in one row with the field: longer, it takes two lines. */
private val CompactTitleWidth = 168.dp

/** The narrowest the field is in one row with the buttons, at the font's own size: room for a few words as they're typed. */
private val LeastFieldWidth = 160.dp

/** The narrowest the field goes beside the buttons where the room is too short for it to have a row of its own. */
private val NarrowestFieldWidth = 112.dp

/** The description's field: up to three lines, or one when `compact`, where the row it's in keeps its height. */
@Composable
private fun DescriptionField(description: String, onChange: (String) -> Unit, onDone: () -> Unit, focus: FocusRequester, compact: Boolean, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = description,
        onValueChange = onChange,
        // On one line in a row, where the field can be narrow: a label over several would make it taller than the room.
        label = { Text(stringResource(R.string.sketch_describe_label), maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis) },
        singleLine = compact,
        maxLines = if (compact) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .focusRequester(focus)
            // A keyboard's Enter keeps the sketch, as the screen keyboard's Done does, rather than start a new line.
            .onPreviewKeyEvent { event ->
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter

                if (enter && event.type == KeyEventType.KeyDown) {
                    onDone()
                }

                enter
            },
    )
}

/**
 * Keep drawing, then Done, which keeps the sketch. Each as wide as its words
 * on one line, even where it's asked how narrow it can be: a Text's narrowest
 * is its longest word, which would let the row give Keep drawing room for
 * "Continuar" alone.
 */
@Composable
private fun DescriptionButtons(onBack: () -> Unit, onDone: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.width(IntrinsicSize.Max)) { Text(stringResource(R.string.sketch_keep_drawing), maxLines = 1) }

    Button(onClick = onDone, modifier = Modifier.width(IntrinsicSize.Max)) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  ${stringResource(R.string.common_done)}", maxLines = 1)
    }
}

/** Whether a sketch may take the place of idea `idea`'s picture: Undo brings the picture back after. */
@Composable
internal fun ReplacePictureDialog(idea: String, onSketch: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            // It names the idea: a lock covers it.
            CountsOverTheMap()
            Text(stringResource(R.string.sketch_replace_title))
        },
        text = { Text(stringResource(R.string.sketch_replace_text, idea)) },
        confirmButton = { TextButton(onClick = onSketch) { Text(stringResource(R.string.sketch_sketch)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Whether the sketch, as Done is tapped, may take the place of the picture
 * that landed on idea `idea` while it was drawn, as one chosen before that
 * took a while to copy in: Undo brings the picture back after.
 */
@Composable
internal fun ReplaceLandedDialog(idea: String, onReplace: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = {
            // It names the idea: a lock covers it.
            CountsOverTheMap()
            Text(stringResource(R.string.sketch_replace_title))
        },
        text = { Text(stringResource(R.string.sketch_replace_new_text, idea)) },
        confirmButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.sketch_replace)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.sketch_keep_drawing)) } },
    )
}

/** Whether to let the strokes drawn go: nothing of the sketch stays. */
@Composable
internal fun DiscardSketchDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = {
            CountsOverTheMap()
            Text(stringResource(R.string.sketch_discard_title))
        },
        text = { Text(stringResource(R.string.sketch_discard_text)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.sketch_discard)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.sketch_keep_drawing)) } },
    )
}

/**
 * The map's bars dimmed with the map while a planet is sketched on, in the
 * color the page dims the map with, so the screen dims as one; they take no
 * presses meanwhile.
 */
@Composable
internal fun BoxScope.SketchScrim(ui: MapUi) {
    val sketching = ui.sketching && ui.sketch?.phase != SketchPhase.Closing
    // The page's color, kept as the sketch ends, so the bars brighten in it.
    val reported = ui.sketch?.scrim
    var color by remember { mutableStateOf(Color.Black.copy(alpha = 0.32f)) }
    // In with the flight to the planet; out with the flight back, once the canvas has lifted.
    val shown by animateFloatAsState(
        if (sketching) 1f else 0f,
        if (sketching) tween(420) else tween(520, delayMillis = Sketching.CANVAS_FADE.toInt()),
        label = "scrim",
    )

    LaunchedEffect(reported) {
        if (reported != null) {
            color = Color(reported)
        }
    }

    if (shown > 0f) {
        Box(
            Modifier
                .matchParentSize()
                .background(color.copy(alpha = color.alpha * shown))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )
    }
}
