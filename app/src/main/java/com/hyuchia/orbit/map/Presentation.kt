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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PauseCircle
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.MediaViewer
import com.hyuchia.orbit.media.VideoScreen
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.BrowserAddressSheet
import com.hyuchia.orbit.present.CastButton
import com.hyuchia.orbit.present.browserPresenting
import com.hyuchia.orbit.present.rememberAudience
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.ControlsStepAside
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.MarkdownNote
import com.hyuchia.orbit.ui.controlsLinger
import com.hyuchia.orbit.ui.pressesButton
import com.hyuchia.orbit.ui.rememberAsTheyAre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Windows at least this wide, and wider than tall, show the captions beside the map, this wide. */
private val CaptionsBesideWidth = 640.dp
private val CaptionsBeside = 360.dp
private val CaptionMargin = 16.dp
/** The controls' row, at the least: it keeps its room while they're hidden, so the caption above stays put. */
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
 * open full screen over the presentation, and a step closes them. `map`
 * draws the map, asked to take no keyboard focus (`focusable` false): the
 * presentation keeps the keys, and Tab goes through its controls.
 */
@Composable
fun PresentationMode(model: MapViewModel, ui: MapUi, background: Color, map: @Composable (focusable: Boolean) -> Unit) {
    val presentation = ui.presentation ?: return
    val seconds = LocalAppSettings.current.presentationSeconds
    val view = LocalView.current

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

    // Files that can't go clean stay on this phone unless the presenter chooses, as browsers or a TV join, to show them as
    // they are: a choice the map keeps while the presentation lasts, as it keeps the room in browsers. A question still
    // open goes with this screen, and what its answer would start here: a screen made again asks afresh.
    val asTheyAre = rememberAsTheyAre(model.presentingAsTheyAre)
    val uncleaned by produceState(Uncleaned(), ui.revision) { value = model.uncleaned() }
    // The TV that last joined: it gets the map once the presenter has answered for its pictures, if it's still there.
    val joiningTv = remember { mutableStateOf<Audience?>(null) }

    // A TV can't be asked: a picture that changes while it watches stays on this phone, unless it goes clean.
    val audience = rememberAudience(model.folder(), asTheyAre = { asTheyAre.chosen.lasting() }) { made ->
        joiningTv.value = made

        if (made != null && made.carriesPictures) {
            asTheyAre.then(uncleaned.inPictures) {
                if (joiningTv.value === made) {
                    model.setAudience(made)
                }
            }
        } else {
            model.setAudience(made)
        }
    }

    var showAddress by remember { mutableStateOf(false) }
    val browsers by model.browserRoom.presenting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = LocalAppSettings.current

    fun showInBrowsers() = asTheyAre.then(uncleaned.inRoom) {
        val choice = model.presentingAsTheyAre
        // Nor can a browser: a file that changes meanwhile stays here, unless it goes clean.
        model.browserRoom.start { browserPresenting(context, settings, model.folder(), model.viewModelScope, asTheyAre = { choice.files.lasting() }) }
        showAddress = true
    }

    // The map only shows the presentation: the keyboard's focus stays with the presentation and its controls, and Tab
    // goes from one control to the next rather than into the map's page.
    val shownMap: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().focusProperties { canFocus = false }) {
            map(false)
        }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        PresentationKeys(keys = { event -> stepKeys(event, model, presentation, seconds) }, modifier = Modifier.fillMaxSize().background(background)) { focus ->
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
                    shownMap,
                    onBrowsers = ::showInBrowsers,
                )
            } else {
                OnThisScreen(model, ui, presentation, seconds, background, shownMap, focus, onBrowsers = ::showInBrowsers)
            }
        }
    }

    // The idea's picture or video, full screen over the presentation on this screen alone.
    if (audience == null && browsers == null) {
        ui.viewing?.let { viewing ->
            val sound by model.sound.collectAsStateWithLifecycle()

            MediaViewer(
                mapId = model.id,
                items = viewing.items,
                start = viewing.start,
                source = model::mediaModel,
                onPlay = model::play,
                onClosed = model::closeViewer,
                sound = sound,
                sounds = model.sounds,
                onKey = { event -> fullScreenStepKeys(event, model, seconds, volume = true) },
                soundLength = model::soundLength,
            )
        }

        // A video plays its sound: there, the volume keys are the volume's.
        ui.playing?.let { item ->
            VideoScreen(model.id, item, resolve = model::mediaModel, onClosed = model::stopPlaying, onKey = { event -> fullScreenStepKeys(event, model, seconds, volume = false) })
        }
    }

    val room = browsers

    if (showAddress && room != null) {
        // The room gets the map's pictures and videos: its sounds play from this phone. Those that can't go clean are named.
        val chosen = asTheyAre.chosen.chosenIn(uncleaned)
        val media by produceState(MediaSummary(), ui.revision, uncleaned, chosen) { value = model.mediaSummary(leavingOut = uncleaned.names - chosen).copy(audio = 0) }
        BrowserAddressSheet(
            room,
            media,
            uncleaned.inRoom,
            chosen,
            onStop = {
                model.browserRoom.stop()
                showAddress = false
            },
            onDismiss = { showAddress = false },
        )
    }
}

/**
 * What a presentation's keys tell what it shows ([PresentationKeys]):
 * whether one of its controls has the keyboard's focus, and Tab or
 * Shift+Tab with the focus on the presentation itself, for controls that
 * stepped aside to come back with.
 */
@Stable
internal class PresentationFocus {
    /** One of the presentation's controls has the keyboard's focus (Tab went to it). */
    var controlFocused by mutableStateOf(false)
        internal set

    /**
     * Hears Tab (`forward`) or Shift+Tab with the focus on the presentation
     * itself, and says whether it took the key: controls that stepped aside
     * come back, the focus on the first of them or the last. Left as it is,
     * Tab goes on to whatever control is there.
     */
    var onTab: (forward: Boolean) -> Boolean = { false }
}

/**
 * Where a presentation takes the keys, around `content`. It keeps the
 * keyboard's focus, so a clicker's first click steps: wherever the screen
 * puts it as it starts, on a tablet a pane of its own, it takes the focus
 * back once it's there. `keys` sees each key before the keyboard and
 * Android's touch mode do, so the first key after a touch steps too. With
 * one of the presentation's controls focused (Tab went to it), Space and
 * Enter press that control instead, as a tap would, and the other keys
 * stay the presentation's. `content` hears that one is, to keep it on
 * screen, and Tab with the focus on the presentation itself
 * ([PresentationFocus]).
 */
@Composable
internal fun PresentationKeys(keys: (ComposeKeyEvent) -> Boolean, modifier: Modifier = Modifier, content: @Composable (PresentationFocus) -> Unit) {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var holdsFocus by remember { mutableStateOf(false) }
    val presentationFocus = remember { PresentationFocus() }

    fun presentationKeys(event: ComposeKeyEvent): Boolean {
        val controlFocused = presentationFocus.controlFocused
        val tab = event.type == KeyEventType.KeyDown && event.key == Key.Tab && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed

        if (tab && holdsFocus && !controlFocused) {
            return presentationFocus.onTab(!event.isShiftPressed)
        }

        return !(controlFocused && event.pressesButton()) && keys(event)
    }

    Box(
        modifier
            .onPlaced {
                if (!holdsFocus) {
                    scope.launch { focus.requestFocus() }
                }
            }
            .onFocusChanged {
                holdsFocus = it.hasFocus
                presentationFocus.controlFocused = it.hasFocus && !it.isFocused
            }
            .focusRequester(focus)
            .focusable()
            // Before the keyboard and Android's touch mode see them, and as usual once they've let them by.
            .onPreInterceptKeyBeforeSoftKeyboard(::presentationKeys)
            .onPreviewKeyEvent(::presentationKeys),
    ) {
        content(presentationFocus)
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
                model.setPlaying(!presentation.playing, seconds)
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
    .stepSwipes(rightToLeft, onStep)

/**
 * A swipe across steps to the next idea or back, the way pages turn: on the
 * presentation, and on what's over it and takes touches of its own, as its
 * caption, whose taps, links and scrolling stay as they are. A swipe that
 * goes up or down first is the caption's to scroll.
 */
fun Modifier.stepSwipes(rightToLeft: Boolean, onStep: (Int) -> Unit): Modifier = pointerInput(rightToLeft) {
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

/**
 * The presentation on this screen: the map, its caption, and controls that
 * step aside after a moment, unless one of them has the keyboard's focus
 * (`focus`). A tap brings them back, and so does Tab or Shift+Tab, which
 * goes on to the first of them or the last.
 */
@Composable
private fun OnThisScreen(
    model: MapViewModel,
    ui: MapUi,
    presentation: Presentation,
    seconds: Int,
    background: Color,
    map: @Composable () -> Unit,
    focus: PresentationFocus,
    onBrowsers: () -> Unit,
) {
    val node = ui.nodes[presentation.current]
    var controls by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    // The swipes outlive this composition: they step at the pace set now, not the one when they began.
    val pace by rememberUpdatedState(seconds)
    // A screen reader can't tap hidden controls back: while one explores the screen, they stay.
    val linger = controlsLinger(CONTROLS_LINGER_MILLISECONDS)
    // The tap outlives this composition too.
    val lingering by rememberUpdatedState(linger)

    // Their moment starts afresh with each touch, and as the presentation starts or stops playing on its own.
    ControlsStepAside(controls, linger, focused = focus.controlFocused, running = true, moment = touches to presentation.playing) { controls = it }

    fun touch() {
        controls = true
        touches += 1
    }

    // Tab or Shift+Tab, with the controls stepped aside and the focus on the presentation: where it goes once they're back.
    var tabbing by remember { mutableStateOf<FocusDirection?>(null) }
    val focusManager = LocalFocusManager.current

    DisposableEffect(focus) {
        focus.onTab = { forward ->
            if (controls) {
                false
            } else {
                touch()
                tabbing = if (forward) FocusDirection.Next else FocusDirection.Previous
                true
            }
        }

        onDispose { focus.onTab = { false } }
    }

    LaunchedEffect(tabbing) {
        val direction = tabbing ?: return@LaunchedEffect
        // Once they're laid out again.
        withFrameNanos {}
        focusManager.moveFocus(direction)
        tabbing = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // On a wide screen the captions sit beside the map, elsewhere under it; either way the map fits around them.
        val beside = maxWidth > maxHeight && maxWidth >= CaptionsBesideWidth
        val captioned = presentation.captions
        val density = LocalDensity.current
        val besideAtLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
        var captions by remember { mutableStateOf(IntSize.Zero) }
        var tallest by remember(beside, captioned) { mutableIntStateOf(0) }
        var controlsHeight by remember { mutableStateOf(ControlsHeight) }

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
                        if (controls && lingering != null) {
                            controls = false
                        } else {
                            touch()
                        }
                    },
                    onStep = { by -> model.step(by, pace) },
                ),
        )

        // A page that stopped, or didn't load, says so in the map's place, with the way to load it again: the
        // presentation goes on from the idea on screen.
        ui.pageProblem?.let { problem ->
            PageProblemNotice(problem, onLoadAgain = model::reload, modifier = Modifier.align(Alignment.Center).safeDrawingPadding())
        }

        val progress = stringResource(R.string.present_progress, presentation.index + 1, presentation.order.size)
        LinearProgressIndicator(
            progress = { (presentation.index + 1f) / presentation.order.size },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .semantics { contentDescription = progress },
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

                // The idea's sound plays from the phone, over the presentation, which goes on.
                if (node?.audio != null) {
                    val sound by model.sound.collectAsStateWithLifecycle()
                    val playing = sound?.playing == true && sound?.item?.nodeId == node.id
                    val playTheSound = stringResource(if (playing) R.string.audio_pause_the_sound else R.string.audio_play_the_sound)

                    Hint(playTheSound) {
                        IconButton(onClick = {
                            model.toggleSound(node.id)
                            touch()
                        }) {
                            Icon(if (playing) Icons.Outlined.PauseCircle else Icons.Outlined.GraphicEq, contentDescription = playTheSound)
                        }
                    }
                }

                CaptionsButton(captioned) {
                    model.setCaptions(!captioned)
                    touch()
                }

                CastButton(dark = background.luminance() < 0.5f, screenCast = true)

                BrowsersAndStopButtons(model, onBrowsers)
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
                Caption(node, rightToLeft = besideAtLeft, onStep = { by -> model.step(by, pace) }, onNodeLink = { id -> model.focus(id) })
            }

            // As tall as the controls have been, larger type and the pace under the count included.
            Box(Modifier.heightIn(min = controlsHeight), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = controls, enter = fadeIn(tween(160)), exit = fadeOut(tween(420))) {
                    StepButtons(
                        model,
                        presentation,
                        seconds,
                        onTouch = ::touch,
                        modifier = Modifier.onSizeChanged { size -> controlsHeight = maxOf(controlsHeight, with(density) { size.height.toDp() }) },
                    )
                }
            }
        }
    }
}

/**
 * The idea's name and note, fading from one idea to the next. The top of the
 * map without a note needs none. It's over the presentation, and a swipe
 * across it steps as one across the map does (`onStep`), in the direction
 * `rightToLeft` reading turns pages; a long note scrolls, and its links go
 * to their ideas (`onNodeLink`).
 */
@Composable
internal fun Caption(node: MapNode?, rightToLeft: Boolean, onStep: (Int) -> Unit, onNodeLink: (String) -> Unit) {
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
                // A surface keeps touches from what's under it: swipes across it step here.
                modifier = Modifier.fillMaxWidth().stepSwipes(rightToLeft, onStep),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    current.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Box(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            MarkdownNote(note, plain = current.noteFormat == "plain", onNodeLink = onNodeLink)
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
fun StepButtons(model: MapViewModel, presentation: Presentation, seconds: Int, modifier: Modifier = Modifier, onTouch: () -> Unit = {}) {
    val now = rememberNow(presentation.stepStartedAt)
    val words = rememberWords()
    val pace = presentation.takeIf { !it.rehearsing }?.pace(now, words)

    val previous = stringResource(R.string.present_previous_idea)
    val playOrPause = stringResource(if (presentation.playing) R.string.present_pause else R.string.present_play_on_its_own)
    val next = stringResource(R.string.present_next_idea)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        Hint(previous, MapKeys.PreviousIdea) {
            FilledTonalIconButton(
                onClick = {
                    model.step(-1, seconds)
                    onTouch()
                },
                enabled = presentation.index > 0,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = previous)
            }
        }

        Hint(playOrPause) {
            FilledTonalIconButton(
                onClick = {
                    model.setPlaying(!presentation.playing, seconds)
                    onTouch()
                },
            ) {
                Icon(if (presentation.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playOrPause)
            }
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

        Hint(next, MapKeys.NextIdea) {
            FilledIconButton(
                onClick = {
                    model.step(1, seconds)
                    onTouch()
                },
                enabled = presentation.index < presentation.order.lastIndex,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = next)
            }
        }
    }
}

/** Shows the captions, or hides them for the map alone. */
@Composable
fun CaptionsButton(captioned: Boolean, onClick: () -> Unit) {
    val captions = stringResource(if (captioned) R.string.present_hide_the_captions else R.string.present_show_the_captions)

    Hint(captions) {
        IconButton(onClick = onClick) {
            Icon(if (captioned) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled, contentDescription = captions)
        }
    }
}

/** Shows the presentation in browsers too, and stops presenting: the last of the presentation's buttons, wherever it shows them. */
@Composable
fun BrowsersAndStopButtons(model: MapViewModel, onBrowsers: () -> Unit) {
    val browsers = stringResource(R.string.present_show_in_a_browser)
    val stop = stringResource(R.string.common_stop_presenting)

    Hint(browsers) {
        IconButton(onClick = onBrowsers) { Icon(Icons.Outlined.Language, contentDescription = browsers) }
    }

    Hint(stop, MapKeys.StopPresenting) {
        IconButton(onClick = model::stopPresentation) { Icon(Icons.Outlined.Close, contentDescription = stop) }
    }
}
