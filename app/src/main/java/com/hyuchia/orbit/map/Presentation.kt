package com.hyuchia.orbit.map

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.MediaViewer
import com.hyuchia.orbit.media.VideoScreen
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.present.BrowserAddressDialog
import com.hyuchia.orbit.present.CastButton
import com.hyuchia.orbit.present.rememberAudience
import com.hyuchia.orbit.present.rememberBrowserPresenting
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.MarkdownNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Windows at least this wide, and wider than tall, show the captions beside the map, this wide. */
private val CaptionsBesideWidth = 640.dp
private val CaptionsBeside = 360.dp
private val CaptionMargin = 16.dp
/** The controls' row, which keeps its room while they're hidden, so the caption above stays put. */
private val ControlsHeight = 56.dp
/** The controls step aside after this long without a touch, leaving the map and its caption. */
private const val CONTROLS_LINGER_MILLISECONDS = 3_500L
/** A swipe at least this long across the screen steps to the next or previous idea. */
private val SwipeToStep = 72.dp
/** Within this many seconds of the last rehearsal's pace, the presenter is keeping to it. */
private const val PACE_SLACK_SECONDS = 5

/**
 * The map for an audience: full screen, one idea at a time along the map's
 * path or in reading order, each note as its caption. Arrow keys, page keys,
 * volume keys and presentation clickers step through it, as do swipes, and
 * the screen stays on. With a second screen or a TV, the audience watches
 * there and this screen becomes the presenter's ([PresenterView]), which
 * shows them an idea's picture or video too; on this screen alone, they
 * open full screen over the presentation, and a step closes them.
 */
@Composable
fun PresentationMode(model: MapViewModel, ui: MapUi, background: Color, map: @Composable () -> Unit) {
    val presentation = ui.presentation ?: return
    val seconds = LocalAppSettings.current.presentationSeconds
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var holdsFocus by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        // After a touch, Android gives no view the keys until one asks for them in touch mode, and it spends
        // the first arrow, page or space key on finding one: the presentation takes them at once instead.
        val focusableInTouchMode = view.isFocusableInTouchMode
        view.isFocusableInTouchMode = true
        view.requestFocus()

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            view.isFocusableInTouchMode = focusableInTouchMode
        }
    }

    val audience = rememberAudience(model.folder(), model::setAudience)
    var inBrowsers by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    val browsers = rememberBrowserPresenting(inBrowsers, model.folder(), model::setBrowserAudience)

    fun showInBrowsers() {
        inBrowsers = true
        showAddress = true
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                // The presentation keeps the keys, so a clicker's first click steps: wherever the screen puts it as
                // it starts, on a tablet a pane of its own, it takes the focus back once it's there.
                .onPlaced {
                    if (!holdsFocus) {
                        scope.launch { focus.requestFocus() }
                    }
                }
                .onFocusChanged { holdsFocus = it.hasFocus }
                .focusRequester(focus)
                .focusable()
                // Before the keyboard and Android's touch mode see them, so the first key after a touch steps too;
                // and as usual, for keys that come while the map's page has the focus.
                .onPreInterceptKeyBeforeSoftKeyboard { event -> stepKeys(event, model, presentation, seconds) }
                .onPreviewKeyEvent { event -> stepKeys(event, model, presentation, seconds) },
        ) {
            if (audience != null || browsers != null) {
                PresenterView(
                    model,
                    ui,
                    presentation,
                    audience,
                    playsMapVideos = audience?.playsMapVideos != false,
                    browsers,
                    seconds,
                    background,
                    map,
                    onBrowsers = ::showInBrowsers,
                )
            } else {
                OnThisScreen(model, ui, presentation, seconds, background, map, onBrowsers = ::showInBrowsers)
            }
        }
    }

    // The idea's picture or video, full screen over the presentation on this screen alone.
    if (audience == null && browsers == null) {
        ui.viewing?.let { viewing ->
            MediaViewer(
                items = viewing.items,
                start = viewing.start,
                source = model::mediaModel,
                onPlay = model::play,
                onClosed = model::closeViewer,
                onKey = { event -> fullScreenStepKeys(event, model, seconds, volume = true) },
            )
        }

        // A video plays its sound: there, the volume keys are the volume's.
        ui.playing?.let { item ->
            VideoScreen(item, resolve = model::mediaModel, onClosed = model::stopPlaying, onKey = { event -> fullScreenStepKeys(event, model, seconds, volume = false) })
        }
    }

    if (showAddress && browsers != null) {
        BrowserAddressDialog(
            browsers,
            onStop = {
                inBrowsers = false
                showAddress = false
            },
            onDismiss = { showAddress = false },
        )
    }
}

/**
 * Keys that step through a presentation: arrows, page keys, volume keys,
 * clickers, and Escape to stop. While the audience sees a picture or video,
 * Escape takes them back to the map first, and the media key plays and
 * pauses the video.
 */
private fun stepKeys(event: ComposeKeyEvent, model: MapViewModel, presentation: Presentation, seconds: Int): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }

    val media = presentation.media
    val first = event.nativeKeyEvent.repeatCount == 0

    // A key held down repeats: a press does one thing, as a clicker's click does, and its repeats are taken for nothing.
    fun once(action: () -> Unit): Boolean {
        if (first) {
            action()
        }

        return true
    }

    return when (event.key) {
        Key.DirectionRight, Key.PageDown, Key.Spacebar, Key.Enter, Key.VolumeDown -> once { model.step(1, seconds) }
        Key.DirectionLeft, Key.PageUp, Key.VolumeUp -> once { model.step(-1, seconds) }
        Key.Escape -> once {
            if (media != null) {
                model.closeMedia()
            } else {
                model.stopPresentation()
            }
        }
        else -> event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && once {
            if (media?.isVideo == true) {
                model.playMedia(!media.playingAt(SystemClock.elapsedRealtime()))
            } else {
                model.setPlaying(!presentation.playing, seconds.coerceAtLeast(8))
            }
        }
    }
}

/**
 * The presentation's steps over a picture or video full screen on this
 * screen: the arrows, page keys and clickers close it and step, and so do
 * the volume keys with `volume`, while no video plays its sound. Space and
 * Enter stay the picture's and video's own.
 */
private fun fullScreenStepKeys(event: KeyEvent, model: MapViewModel, seconds: Int, volume: Boolean): Boolean {
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
        return false
    }

    val by = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> 1
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> -1
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (volume) 1 else 0
        KeyEvent.KEYCODE_VOLUME_UP -> if (volume) -1 else 0
        else -> 0
    }

    if (by == 0) {
        return false
    }

    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        model.step(by, seconds)
    }

    return true
}

/**
 * Touches belong to the presentation, not the map under it: a tap, and a
 * swipe across, which steps to the next idea or back, the way pages turn.
 */
fun Modifier.presentationGestures(rightToLeft: Boolean, onTap: () -> Unit, onStep: (Int) -> Unit): Modifier = this
    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(rightToLeft) {
        val threshold = SwipeToStep.toPx()
        val forward = if (rightToLeft) 1 else -1
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (abs(travelled) >= threshold) {
                    onStep(if (travelled * forward > 0) 1 else -1)
                }
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                travelled += amount
            },
        )
    }

/** The presentation on this screen: the map, its caption, and controls that step aside after a moment. */
@Composable
private fun OnThisScreen(
    model: MapViewModel,
    ui: MapUi,
    presentation: Presentation,
    seconds: Int,
    background: Color,
    map: @Composable () -> Unit,
    onBrowsers: () -> Unit,
) {
    val node = ui.nodes[presentation.current]
    var controls by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    // The swipes outlive this composition: they step at the pace set now, not the one when they began.
    val pace by rememberUpdatedState(seconds)

    LaunchedEffect(controls, touches, presentation.playing) {
        if (controls) {
            delay(CONTROLS_LINGER_MILLISECONDS)
            controls = false
        }
    }

    fun touch() {
        controls = true
        touches += 1
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // On a wide screen the captions sit beside the map, elsewhere under it; either way the map fits around them.
        val beside = maxWidth > maxHeight && maxWidth >= CaptionsBesideWidth
        val captioned = presentation.captions
        val density = LocalDensity.current
        val besideAtLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
        var captions by remember { mutableStateOf(IntSize.Zero) }
        var tallest by remember(beside, captioned) { mutableIntStateOf(0) }

        LaunchedEffect(beside, besideAtLeft, captioned, captions) {
            if (!captioned) {
                // The map alone: it has the whole screen, and the controls float over it.
                model.setInsets(MapInsets())
                return@LaunchedEffect
            }

            if (captions == IntSize.Zero) {
                return@LaunchedEffect
            }

            // Under the map, the room only grows: steps with shorter captions don't move the map.
            tallest = maxOf(tallest, captions.height)
            val room = (with(density) { (if (beside) captions.width else tallest).toDp() } + CaptionMargin * 2).value
            model.setInsets(
                when {
                    !beside -> MapInsets(bottom = room)
                    besideAtLeft -> MapInsets(left = room)
                    else -> MapInsets(right = room)
                },
            )
        }

        map()
        Box(
            Modifier
                .fillMaxSize()
                .presentationGestures(
                    rightToLeft = besideAtLeft,
                    onTap = {
                        if (controls) {
                            controls = false
                        } else {
                            touch()
                        }
                    },
                    onStep = { by -> model.step(by, pace) },
                ),
        )
        LinearProgressIndicator(
            progress = { (presentation.index + 1f) / presentation.order.size },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        if (presentation.rehearsing) {
            Rehearsing(
                presentation,
                Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(12.dp),
            )
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(420)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The idea's picture or video, full screen over the presentation.
                if (node?.image != null) {
                    val showThePicture = stringResource(R.string.common_show_the_picture)

                    Hint(showThePicture) {
                        IconButton(onClick = { model.view(node.id, MediaKind.Picture) }) {
                            Icon(Icons.Outlined.Image, contentDescription = showThePicture)
                        }
                    }
                }

                if (node?.video != null) {
                    val playTheVideo = stringResource(R.string.common_play_the_video)

                    Hint(playTheVideo) {
                        IconButton(onClick = { model.play(node.id) }) {
                            Icon(Icons.Outlined.PlayCircle, contentDescription = playTheVideo)
                        }
                    }
                }

                CaptionsButton(captioned) {
                    model.setCaptions(!captioned)
                    touch()
                }

                CastButton(dark = background.luminance() < 0.5f, screenCast = true)

                IconButton(onClick = onBrowsers) {
                    Icon(Icons.Outlined.Language, contentDescription = stringResource(R.string.present_show_in_a_browser))
                }

                IconButton(onClick = model::stopPresentation) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_stop_presenting))
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, if (beside && captioned) Alignment.CenterVertically else Alignment.Bottom),
            modifier = Modifier
                .align(if (beside && captioned) Alignment.CenterEnd else Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(CaptionMargin)
                .then(if (beside && captioned) Modifier.width(CaptionsBeside) else Modifier.widthIn(max = 640.dp))
                .onSizeChanged { captions = it },
        ) {
            if (captioned) {
                Caption(model, node)
            }

            Box(Modifier.height(ControlsHeight), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = controls, enter = fadeIn(tween(160)), exit = fadeOut(tween(420))) {
                    StepButtons(model, presentation, seconds, onTouch = ::touch)
                }
            }
        }
    }
}

/** The idea's name and note, fading from one idea to the next. The top of the map without a note needs none. */
@Composable
private fun Caption(model: MapViewModel, node: MapNode?) {
    AnimatedContent(
        targetState = node,
        transitionSpec = { fadeIn(tween(320, delayMillis = 180)) togetherWith fadeOut(tween(160)) },
        label = "caption",
    ) { current ->
        if (current != null && (!current.note.isNullOrBlank() || current.depth > 0)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    current.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Box(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            MarkdownNote(note, plain = current.noteFormat == "plain", onNodeLink = { id -> model.focus(id) })
                        }
                    }
                }
            }
        }
    }
}

/** Rehearsing: a light that pulses like a recording's, and how long the rehearsal has taken. */
@Composable
private fun Rehearsing(presentation: Presentation, modifier: Modifier = Modifier) {
    val now = rememberNow(presentation.startedAt)
    val pulse by rememberInfiniteTransition(label = "rehearsing").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "light",
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer { alpha = pulse }
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Text(stringResource(R.string.present_rehearsing), style = MaterialTheme.typography.labelLarge)
            Text(
                clock(((now - presentation.startedAt) / 1_000).toInt()),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [SystemClock.elapsedRealtime], again each whole second after `since`. */
@Composable
fun rememberNow(since: Long): Long {
    val now by produceState(SystemClock.elapsedRealtime(), since) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1_000L - (SystemClock.elapsedRealtime() - since).mod(1_000L))
        }
    }

    return now
}

/** How the presentation keeps to the last rehearsal's pace at `now`, in words: on it, behind or ahead; null without one. */
fun Presentation.pace(now: Long, words: Words): String? {
    val behind = behind(now) ?: return null

    return when {
        abs(behind) < PACE_SLACK_SECONDS -> words.string(R.string.present_on_pace)
        behind > 0 -> words.string(R.string.present_behind, clock(behind))
        else -> words.string(R.string.present_ahead, clock(-behind))
    }
}

/** Whether the presentation has fallen behind the last rehearsal's pace at `now`. */
fun Presentation.isBehind(now: Long): Boolean = (behind(now) ?: 0) >= PACE_SLACK_SECONDS

/** The idea on screen's time at `now`, in words: what's left of its rehearsed time, or how long it's been up. */
fun Presentation.stepTime(now: Long, words: Words): String {
    val onStep = onStep(now)
    val planned = plannedForStep ?: return clock(onStep)
    val left = planned - onStep
    return if (left >= 0) words.string(R.string.present_time_left, clock(left)) else words.string(R.string.present_time_over, clock(-left))
}

/** Back, play or pause, where the presentation is with its pace, and forward. */
@Composable
fun StepButtons(model: MapViewModel, presentation: Presentation, seconds: Int, onTouch: () -> Unit = {}) {
    val now = rememberNow(presentation.stepStartedAt)
    val words = rememberWords()
    val pace = presentation.takeIf { !it.rehearsing }?.pace(now, words)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalIconButton(
            onClick = {
                model.step(-1, seconds)
                onTouch()
            },
            enabled = presentation.index > 0,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.present_previous_idea))
        }

        FilledTonalIconButton(
            onClick = {
                model.setPlaying(!presentation.playing, seconds.coerceAtLeast(8))
                onTouch()
            },
        ) {
            Icon(
                if (presentation.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (presentation.playing) R.string.present_pause else R.string.present_play_on_its_own),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.present_position, presentation.index + 1, presentation.order.size), style = MaterialTheme.typography.labelLarge)

            if (pace != null) {
                Text(
                    pace,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (presentation.isBehind(now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FilledIconButton(
            onClick = {
                model.step(1, seconds)
                onTouch()
            },
            enabled = presentation.index < presentation.order.lastIndex,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.present_next_idea))
        }
    }
}

/** Shows the captions, or hides them for the map alone. */
@Composable
fun CaptionsButton(captioned: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (captioned) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
            contentDescription = stringResource(if (captioned) R.string.present_hide_the_captions else R.string.present_show_the_captions),
        )
    }
}
