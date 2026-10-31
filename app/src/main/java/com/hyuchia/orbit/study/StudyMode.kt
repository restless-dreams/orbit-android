package com.hyuchia.orbit.study

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.map.MapViewModel
import com.hyuchia.orbit.media.SoundPlayButton
import com.hyuchia.orbit.media.of
import com.hyuchia.orbit.media.soundOf
import com.hyuchia.orbit.media.soundTime
import com.hyuchia.orbit.media.soundTitle
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.privacy.LocalMapLocked
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.MarkdownNote
import com.hyuchia.orbit.ui.WavyProgress
import com.hyuchia.orbit.ui.isShort
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Windows at least this wide, and wider than tall, put the card's panel beside the map. */
private val PanelBesideWidth = 640.dp
private val PanelBeside = 360.dp
private val PanelWidest = 560.dp
private val PanelMargin = 16.dp
/** Room kept between the map and the bar over it, for the names over its ideas. */
private val Clearance = 12.dp
/** The answers' buttons, two lines tall: the answer, and when the card comes back after it. */
private val AnswerHeight = 64.dp
/** From this font scale the answers stack, rather than sharing a row. */
private const val STACKED_ANSWERS_FONT_SCALE = 1.5f
private val ShowHeight = 56.dp
/** A swipe at least this long across the map answers a revealed card: toward Knew it, or toward Again. */
private val SwipeToAnswer = 72.dp

/**
 * Study mode: the map full screen, one card at a time. The map flies to
 * each card with the ideas under it hidden, their places faint in its
 * orbit; a tap on the map, Space or Show reveals them, and the card's note.
 * Again or Knew it, by button, key or swipe, answers it, and the next card
 * comes. A summary closes the session, or says why there's nothing to
 * study, while the map shows the whole branch again.
 */
@Composable
fun StudyMode(model: MapViewModel, ui: MapUi, background: Color, map: @Composable () -> Unit) {
    val study = ui.study ?: return
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val haptics = LocalAppSettings.current.haptics
    var holdsFocus by remember { mutableStateOf(false) }
    // One of the session's buttons has keyboard focus (tabbed to): Space and Enter press it.
    var buttonFocused by remember { mutableStateOf(false) }
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The gestures outlive this composition: they answer the session as it is now.
    val current by rememberUpdatedState(study)
    // A private map that locks covers the session too: it waits there, as it was, until the map is unlocked.
    val covered = LocalMapLocked.current
    val coveredNow by rememberUpdatedState(covered)

    DisposableEffect(covered) {
        if (covered) {
            return@DisposableEffect onDispose {}
        }

        view.keepScreenOn = true
        // After a touch, Android gives no view the keys until one asks for them in touch mode: the session takes them at once.
        val focusableInTouchMode = view.isFocusableInTouchMode
        view.isFocusableInTouchMode = true
        view.requestFocus()

        onDispose {
            view.keepScreenOn = false
            view.isFocusableInTouchMode = focusableInTouchMode
        }
    }

    fun answer(knew: Boolean) {
        if (haptics) {
            answered(view, knew)
        }

        model.answerCard(knew)
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(background)
                .onPlaced {
                    if (!holdsFocus && !coveredNow) {
                        scope.launch { focus.requestFocus() }
                    }
                }
                .onFocusChanged {
                    holdsFocus = it.hasFocus
                    buttonFocused = it.hasFocus && !it.isFocused
                }
                .focusRequester(focus)
                .focusable()
                .onPreInterceptKeyBeforeSoftKeyboard { event -> !coveredNow && studyKeys(event, current, model, rightToLeft, buttonFocused, ::answer) }
                .onPreviewKeyEvent { event -> !coveredNow && studyKeys(event, current, model, rightToLeft, buttonFocused, ::answer) }
                // Behind a private map's lock, nothing of the session is read out.
                .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            val beside = maxWidth > maxHeight && maxWidth >= PanelBesideWidth
            // A short screen (a phone on its side) keeps its height for the map: the bar joins the panel at the side.
            val barBeside = beside && isShort(maxHeight)
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val bars = WindowInsets.safeDrawing
            val aboveMap = with(density) { bars.getTop(density).toDp() }
            val belowPanel = with(density) { bars.getBottom(density).toDp() }
            val besidePanel = with(density) { (if (rightToLeft) bars.getLeft(density, layoutDirection) else bars.getRight(density, layoutDirection)).toDp() }
            // The bar is measured with the status bar and its margin.
            var topBar by remember { mutableIntStateOf(0) }
            // A card's two faces take the room of the tallest one a session has shown, so the map holds still as cards are
            // revealed and answered; the summary takes its own.
            var cardFaces by remember(beside, study.start, study.anyway) { mutableIntStateOf(0) }
            var summaryFace by remember { mutableIntStateOf(0) }

            LaunchedEffect(beside, barBeside, rightToLeft, cardFaces, summaryFace, topBar, aboveMap, belowPanel, besidePanel, study.ending) {
                val face = if (study.ending == null) cardFaces else summaryFace

                if (face == 0) {
                    return@LaunchedEffect
                }

                val top = ((if (barBeside) aboveMap else with(density) { topBar.toDp() }) + Clearance).value
                val room = if (beside) PanelBeside + PanelMargin * 2 + besidePanel else with(density) { face.toDp() } + PanelMargin * 2 + belowPanel
                model.setInsets(
                    when {
                        !beside -> MapInsets(top = top, bottom = room.value)
                        rightToLeft -> MapInsets(top = top, left = room.value)
                        else -> MapInsets(top = top, right = room.value)
                    },
                )
            }

            // The map only shows the session: keyboard focus stays on the session's buttons, and Tab goes from one to the next.
            Box(Modifier.fillMaxSize().focusProperties { canFocus = false }) {
                map()
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .studyGestures(
                        rightToLeft = rightToLeft,
                        onTap = { model.revealCard() },
                        onSwipe = { knew ->
                            if (current.revealed && current.ending == null) {
                                answer(knew)
                            }
                        },
                    ),
            )

            val bar = @Composable { modifier: Modifier ->
                StudyTopBar(study, ui, onStop = model::stopStudy, onUndo = model::undoAnswer, modifier = modifier)
            }

            val panel = @Composable { modifier: Modifier ->
                StudyPanel(
                    study,
                    ui,
                    model,
                    onAnswer = ::answer,
                    short = barBeside,
                    onFaceHeight = { face, height ->
                        if (face == PanelFace.Summary) {
                            summaryFace = height
                        } else {
                            cardFaces = maxOf(cardFaces, height)
                        }
                    },
                    modifier = modifier,
                )
            }

            if (barBeside) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .safeDrawingPadding()
                        .padding(PanelMargin)
                        .width(PanelBeside)
                        .verticalScroll(rememberScrollState()),
                ) {
                    bar(Modifier.fillMaxWidth())
                    panel(Modifier.fillMaxWidth())
                }
            } else {
                // Over the map: beside the panel, the bar is centered on what's left of the window.
                val panelSide = if (beside) PanelBeside + PanelMargin * 2 + besidePanel else 0.dp

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (rightToLeft) panelSide else 0.dp, end = if (rightToLeft) 0.dp else panelSide),
                ) {
                    bar(
                        Modifier
                            .onSizeChanged { topBar = it.height }
                            .safeDrawingPadding()
                            .padding(top = 8.dp, start = PanelMargin, end = PanelMargin),
                    )
                }

                panel(
                    Modifier
                        .align(if (beside) Alignment.CenterEnd else Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(PanelMargin)
                        .then(if (beside) Modifier.width(PanelBeside) else Modifier.widthIn(max = PanelWidest).fillMaxWidth()),
                )
            }
        }
    }
}

/** A light tick for Again, and a confirming one for Knew it. */
private fun answered(view: View, knew: Boolean) {
    val feedback = when {
        !knew -> HapticFeedbackConstants.CLOCK_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
        else -> HapticFeedbackConstants.KEYBOARD_TAP
    }

    view.performHapticFeedback(feedback)
}

/** What a key does in a session (see `studyKey`). */
internal enum class StudyKey { Undo, Reveal, Knew, Again, Stop }

/**
 * What `key` does in a session as `study` stands, or null when it isn't
 * the session's: Space or Enter reveal a card and then say it was known,
 * 1 or the arrow toward Again says Again, 2 or the arrow toward Knew it
 * says Knew it, Ctrl+Z takes the last answer back, and Escape ends the
 * session. On the summary, Space and Enter close it too. With
 * `buttonFocused`, one of the session's buttons has keyboard focus, and
 * Space and Enter press it, whatever it is; the other keys stay the session's.
 */
internal fun studyKey(key: Key, ctrl: Boolean, study: Study, rightToLeft: Boolean, buttonFocused: Boolean): StudyKey? {
    if (ctrl) {
        return if (key == Key.Z) StudyKey.Undo else null
    }

    val pressing = key == Key.Spacebar || key == Key.Enter || key == Key.NumPadEnter

    if (pressing && buttonFocused) {
        return null
    }

    if (study.ending != null) {
        return if (pressing || key == Key.Escape) StudyKey.Stop else null
    }

    val towardAgain = if (rightToLeft) Key.DirectionRight else Key.DirectionLeft
    val towardKnown = if (rightToLeft) Key.DirectionLeft else Key.DirectionRight

    return when (key) {
        Key.Spacebar, Key.Enter, Key.NumPadEnter -> if (study.revealed) StudyKey.Knew else StudyKey.Reveal
        Key.One, Key.NumPad1, towardAgain -> StudyKey.Again.takeIf { study.revealed }
        Key.Two, Key.NumPad2, towardKnown -> StudyKey.Knew.takeIf { study.revealed }
        Key.Escape -> StudyKey.Stop
        else -> null
    }
}

/** Does what a key press means to the session (see `studyKey`), and says whether the key was the session's. */
private fun studyKeys(event: KeyEvent, study: Study, model: MapViewModel, rightToLeft: Boolean, buttonFocused: Boolean, answer: (Boolean) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }

    val action = studyKey(event.key, event.isCtrlPressed, study, rightToLeft, buttonFocused) ?: return false

    // A key held down repeats: a press acts once.
    if (event.nativeKeyEvent.repeatCount == 0) {
        when (action) {
            StudyKey.Undo -> model.undoAnswer()
            StudyKey.Reveal -> model.revealCard()
            StudyKey.Knew -> answer(true)
            StudyKey.Again -> answer(false)
            StudyKey.Stop -> model.stopStudy()
        }
    }

    return true
}

/**
 * Touches belong to the session, not the map under it: a tap reveals the
 * card, and once it's revealed, a swipe across answers it, toward the side
 * its answer's button is on.
 */
private fun Modifier.studyGestures(rightToLeft: Boolean, onTap: () -> Unit, onSwipe: (knew: Boolean) -> Unit): Modifier = this
    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(rightToLeft) {
        val threshold = SwipeToAnswer.toPx()
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (abs(travelled) >= threshold) {
                    onSwipe((travelled > 0) != rightToLeft)
                }
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                travelled += amount
            },
        )
    }

/** Where the session is, the card's place in the map, and the ways out: Stop, and Undo for the last answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyTopBar(study: Study, ui: MapUi, onStop: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        if (study.total == 0) 0f else study.known.toFloat() / study.total,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "progress",
    )
    val card = study.current?.takeIf { study.ending == null }
    val place = card?.let { cardPlace(ui, study.start, it) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.widthIn(max = PanelWidest),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
            val stop = stringResource(R.string.study_stop)

            Hint(stop) {
                FilledTonalIconButton(onClick = onStop) { Icon(Icons.Outlined.Close, contentDescription = stop) }
            }

            val position = if (study.ending == null) stringResource(R.string.study_position_description, study.position, study.total) else null
            val branch = ui.nodes[study.start]?.label(stringResource(R.string.study_study)) ?: stringResource(R.string.study_study)

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 120.dp)
                    .padding(horizontal = 12.dp)
                    .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(position, place ?: branch.takeIf { position == null }).joinToString(", ") },
            ) {
                // "Studying anyway" goes under the count where both don't fit on one line, at large font sizes.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (study.ending == null) stringResource(R.string.study_position, study.position, study.total) else branch,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )

                    if (study.anyway && study.ending == null) {
                        Text(
                            stringResource(R.string.study_studying_anyway),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (study.total > 0) {
                    WavyProgress(progress = { progress }, modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth())
                }

                if (place != null) {
                    Text(place, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            AnimatedVisibility(study.last != null) {
                val undo = stringResource(R.string.study_undo_answer)

                Hint(undo) {
                    FilledTonalIconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = undo) }
                }
            }
        }
    }
}

/** Where a card sits in the map: the ideas from where studying began down to the card's own, "Biology › Cells". */
private fun cardPlace(ui: MapUi, start: String, card: String): String? {
    val above = generateSequence(ui.nodes[card]?.parentId) { id -> ui.nodes[id]?.parentId.takeIf { id != start } }
        .mapNotNull { ui.nodes[it] }
        .toList()
        .asReversed()

    return above.joinToString(" › ") { it.text }.takeIf { it.isNotBlank() }
}

/** The panel under the map, or beside it: the card's prompt, its note and the answers, or the session's summary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StudyPanel(
    study: Study,
    ui: MapUi,
    model: MapViewModel,
    onAnswer: (Boolean) -> Unit,
    short: Boolean,
    onFaceHeight: (PanelFace, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val node = study.current?.let { ui.nodes[it] }

    val face = when {
        study.ending != null -> PanelFace.Summary
        study.revealed -> PanelFace.Back
        else -> PanelFace.Front
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        // Each face keeps the card and session it was made for: as the next card comes in, the one going out still shows its own.
        AnimatedContent(
            targetState = PanelContent(face, node, study),
            contentKey = { Triple(it.face, it.node?.id, it.study.ending) },
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.98f)) using SizeTransform(clip = false)
            },
            label = "card",
        ) { shown ->
            Column(
                // A short screen (a phone on its side) packs the panel tighter, so a summary's buttons stay in view.
                verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 16.dp),
                modifier = Modifier
                    .onSizeChanged { onFaceHeight(shown.face, it.height) }
                    .padding(if (short) 16.dp else 20.dp),
            ) {
                when (shown.face) {
                    PanelFace.Front -> Front(shown.node, onReveal = model::revealCard)
                    PanelFace.Back -> Back(shown.node, shown.study, model, short, onAnswer)
                    PanelFace.Summary -> Summary(shown.study, ui, model, short)
                }
            }
        }
    }
}

/** What the panel shows: a card before it's revealed, the card revealed, or the session's summary. */
private enum class PanelFace { Front, Back, Summary }

/** A face of the panel, with the card and the session it shows. */
private data class PanelContent(val face: PanelFace, val node: MapNode?, val study: Study)

/** Before the card is revealed: what to recall, and Show. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Front(node: MapNode?, onReveal: () -> Unit) {
    val ideas = node?.childCount ?: 0
    val note = !node?.note.isNullOrBlank()

    val prompt = when {
        ideas > 0 && note -> pluralStringResource(R.plurals.study_recall_ideas_and_note, ideas, ideas)
        ideas > 0 -> pluralStringResource(R.plurals.study_recall_ideas, ideas, ideas)
        else -> stringResource(R.string.study_recall_note)
    }

    Text(prompt, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    val show = stringResource(R.string.study_show_description, node?.text.orEmpty())

    Button(
        onClick = onReveal,
        shapes = ButtonDefaults.shapesFor(ShowHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ShowHeight),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShowHeight)
            .semantics { contentDescription = show },
    ) {
        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(ShowHeight)))
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ShowHeight)))
        Text(stringResource(R.string.study_show), style = ButtonDefaults.textStyleFor(ShowHeight))
    }
}

/** The card revealed: its note and its sound, if it has them, and the answers, each saying when the card comes back after it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Back(node: MapNode?, study: Study, model: MapViewModel, short: Boolean, onAnswer: (Boolean) -> Unit) {
    val note = node?.note?.takeIf { it.isNotBlank() }

    if (note != null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(max = if (short) 120.dp else 220.dp)
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(node.label(""), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MarkdownNote(note, plain = node.noteFormat == "plain", onNodeLink = {})
        }
    }

    if (node?.audio != null) {
        CardSound(node, model)
    }

    val again = comesBack(0)
    val known = comesBack(study.ifKnown)
    val againSaid = stringResource(R.string.study_again_description, again)
    val knownSaid = stringResource(R.string.study_knew_it_description, known)

    val againButton = @Composable { modifier: Modifier ->
        FilledTonalButton(
            onClick = { onAnswer(false) },
            shapes = ButtonDefaults.shapesFor(AnswerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(AnswerHeight),
            modifier = modifier
                .heightIn(min = AnswerHeight)
                .semantics { contentDescription = againSaid },
        ) {
            AnswerLabel(Icons.Outlined.Replay, stringResource(R.string.study_again), again)
        }
    }

    val knewButton = @Composable { modifier: Modifier ->
        Button(
            onClick = { onAnswer(true) },
            shapes = ButtonDefaults.shapesFor(AnswerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(AnswerHeight),
            modifier = modifier
                .heightIn(min = AnswerHeight)
                .semantics { contentDescription = knownSaid },
        ) {
            AnswerLabel(Icons.Outlined.Check, stringResource(R.string.study_knew_it), known)
        }
    }

    // At large font sizes the answers stack, each as wide as the card, so their words fit whole.
    if (LocalDensity.current.fontScale >= STACKED_ANSWERS_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            againButton(Modifier.fillMaxWidth())
            knewButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            againButton(Modifier.weight(1f))
            knewButton(Modifier.weight(1f))
        }
    }
}

/**
 * A revealed card's sound, to hear: a word said aloud on a language's
 * cards, a bird's call. It plays here, from the map's own player (the small
 * player stays out of a session), and stops as the next card comes.
 */
@Composable
private fun CardSound(node: MapNode, model: MapViewModel) {
    val untitled = stringResource(R.string.common_untitled)
    val item = remember(node) { soundOf(node, untitled) } ?: return
    val sound by model.sound.collectAsStateWithLifecycle()
    val state = sound.of(item)
    val playing = state?.playing == true
    val length by produceState<Long?>(null, item.media.src) { value = model.soundLength(item.media.src) }

    val time = when {
        state?.failed == true -> stringResource(R.string.audio_unplayable)
        state != null -> stringResource(R.string.audio_time_of, soundTime(state.position), soundTime(state.duration))
        else -> length?.let(::soundTime)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(6.dp),
    ) {
        SoundPlayButton(
            playing = playing,
            waiting = state?.waiting == true,
            onClick = { model.sounds.toggle(item) },
            size = 48.dp,
            label = stringResource(if (playing) R.string.audio_pause_the_sound else R.string.audio_play_the_sound),
        )

        Column(Modifier.weight(1f)) {
            Text(item.soundTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

            time?.let {
                Text(it, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** An answer, and under it when the card comes back after it: at large font sizes both wrap, and the button grows. */
@Composable
private fun AnswerLabel(icon: ImageVector, answer: String, comesBack: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(answer, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Text(
            capitalized(comesBack),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The session's end, or why it has nothing to study: the whole branch shows on the map behind it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Summary(study: Study, ui: MapUi, model: MapViewModel, short: Boolean) {
    val start = ui.nodes[study.start]?.text.orEmpty()
    val next = study.nextIn?.let { comesBack(it) }

    when (study.ending) {
        StudyEnding.Finished -> {
            SummaryHeading(Icons.Outlined.TaskAlt, stringResource(R.string.study_finished_title), short)

            // On a short screen with new cards to offer, the counts below say how it went: the room goes to the choice.
            if (!short || study.newLeft == 0) {
                SummaryText(pluralStringResource(R.plurals.study_finished, study.total, study.total), short)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tally(Icons.Outlined.Check, pluralStringResource(R.plurals.study_first_try, study.knewFirst, study.knewFirst), MaterialTheme.colorScheme.primaryContainer)

                if (study.missed.isNotEmpty()) {
                    Tally(Icons.Outlined.Replay, pluralStringResource(R.plurals.study_another_look, study.missed.size, study.missed.size), MaterialTheme.colorScheme.secondaryContainer)
                }
            }

            if (next != null) {
                SummaryText(stringResource(R.string.study_next, next), short)
            }

            if (study.newLeft > 0) {
                NewCards(study.newLeft, model, short)
            }

            SummaryButtons(
                secondary = stringResource(R.string.study_keep_going).takeIf { study.newLeft > 0 },
                short = short,
                onSecondary = model::keepStudying,
                onDone = model::stopStudy,
            )
        }
        StudyEnding.CaughtUp -> {
            SummaryHeading(Icons.Outlined.TaskAlt, stringResource(R.string.study_caught_up_title), short)
            SummaryText(stringResource(R.string.study_caught_up, start, next ?: stringResource(R.string.study_tomorrow)), short)

            if (study.newLeft > 0) {
                // The day's new cards are studied: more a day, or "Keep going", brings in the ones waiting.
                NewCards(study.newLeft, model, short)
                SummaryButtons(secondary = stringResource(R.string.study_keep_going), short = short, onSecondary = model::keepStudying, onDone = model::stopStudy)

                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = model::studyAnyway, modifier = Modifier.heightIn(min = if (short) 36.dp else ButtonDefaults.MinHeight)) {
                        Text(stringResource(R.string.study_anyway))
                    }
                }
            } else {
                SummaryText(stringResource(R.string.study_anyway_explained), short, small = true)
                SummaryButtons(secondary = stringResource(R.string.study_anyway), short = short, onSecondary = model::studyAnyway, onDone = model::stopStudy)
            }
        }
        StudyEnding.NoCards, null -> {
            SummaryHeading(Icons.Outlined.School, stringResource(R.string.study_no_cards_title), short)
            SummaryText(stringResource(R.string.study_no_cards, start), short)
            SummaryButtons(secondary = null, short = short, onSecondary = {}, onDone = model::stopStudy)
        }
    }
}

/**
 * The new cards `left` for another day, and how many a day, to change on
 * the spot where a session runs out of them: it's Settings' choice too, and
 * "Keep going" brings in what the day then has room for. A short screen
 * (a phone on its side) puts both on one line, the choice in a menu.
 */
@Composable
private fun NewCards(left: Int, model: MapViewModel, short: Boolean) {
    val chosen by model.newCardsADay.collectAsStateWithLifecycle()
    val waiting = pluralStringResource(R.plurals.study_new_left, left, left)

    if (short) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(waiting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            NewCardsADayMenu(chosen, model::chooseNewCardsADay)
        }

        return
    }

    SummaryText(waiting, short)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            stringResource(R.string.study_new_cards_a_day),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )

        NewCardsADayChoice(chosen, model::chooseNewCardsADay, Modifier.fillMaxWidth())
    }
}

/** The summary's icon in a soft disc, and its title: on a short screen, the icon beside a smaller title. */
@Composable
private fun SummaryHeading(icon: ImageVector, title: String, short: Boolean) {
    val said = Modifier.semantics {
        heading()
        liveRegion = LiveRegionMode.Polite
    }

    if (short) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = said)
        }

        return
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }

        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = said)
    }
}

@Composable
private fun SummaryText(text: String, short: Boolean, small: Boolean = false) {
    Text(
        text,
        style = when {
            small -> MaterialTheme.typography.bodySmall
            short -> MaterialTheme.typography.bodyMedium
            else -> MaterialTheme.typography.bodyLarge
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One of the session's counts, in a soft pill. */
@Composable
private fun Tally(icon: ImageVector, text: String, color: Color) {
    Surface(shape = CircleShape, color = color) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * Done, and beside it what else there is to do: keep going, or study
 * anyway. When a label is too long for half the row ("Estudiar de todos
 * modos" beside the map, say), the two go one above the other, whole.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryButtons(secondary: String?, short: Boolean, onSecondary: () -> Unit, onDone: () -> Unit) {
    val height = if (short) ButtonDefaults.MinHeight else ShowHeight
    val style = ButtonDefaults.textStyleFor(height)
    val padding = ButtonDefaults.contentPaddingFor(height)
    val done = stringResource(R.string.common_done)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val half = (maxWidth - 12.dp) / 2
        val sides = padding.calculateStartPadding(direction) + padding.calculateEndPadding(direction)
        val stacked = secondary != null &&
            listOf(secondary, done).any { label -> with(density) { measurer.measure(label, style).size.width.toDp() } + sides > half }

        val secondaryButton = @Composable { modifier: Modifier ->
            FilledTonalButton(
                onClick = onSecondary,
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = padding,
                modifier = modifier.heightIn(min = height),
            ) {
                Text(secondary.orEmpty(), style = style, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }

        val doneButton = @Composable { modifier: Modifier ->
            Button(onClick = onDone, shapes = ButtonDefaults.shapesFor(height), contentPadding = padding, modifier = modifier.heightIn(min = height)) {
                Text(done, style = style, maxLines = 1)
            }
        }

        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                secondaryButton(Modifier.fillMaxWidth())
                doneButton(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (secondary != null) {
                    secondaryButton(Modifier.weight(1f))
                }

                doneButton(Modifier.weight(1f))
            }
        }
    }
}

/** When a card comes back, `days` from today, for the middle of a sentence: "in a moment", "tomorrow", "in 3 days". */
@Composable
fun comesBack(days: Long): String {
    val count = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    return when {
        count <= 0 -> stringResource(R.string.study_in_a_moment)
        count == 1 -> stringResource(R.string.study_tomorrow)
        count < 14 -> pluralStringResource(R.plurals.study_in_days, count, count)
        count < 60 -> ((count + 3) / 7).let { weeks -> pluralStringResource(R.plurals.study_in_weeks, weeks, weeks) }
        else -> ((count + 15) / 30).let { months -> pluralStringResource(R.plurals.study_in_months, months, months) }
    }
}

/** `text` with a capital first letter, in the app's language, for under the answers' buttons. */
@Composable
private fun capitalized(text: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return text.replaceFirstChar { it.titlecase(locale) }
}
