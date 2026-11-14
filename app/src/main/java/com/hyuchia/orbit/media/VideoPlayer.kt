package com.hyuchia.orbit.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import coil3.compose.AsyncImage
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.FileStamp
import com.hyuchia.orbit.ui.AskedAgain
import com.hyuchia.orbit.ui.ControlsStepAside
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.cleanLink
import com.hyuchia.orbit.ui.controlsLinger
import com.hyuchia.orbit.ui.linkScheme
import com.hyuchia.orbit.ui.openLink
import com.hyuchia.orbit.ui.viewKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** How long the controls stay after a touch or a key while the video plays. */
private const val CONTROLS_LINGER_MILLISECONDS = 3_000L

/** The buttons and the arrow keys go this far back or on. */
private const val SEEK_MILLISECONDS = 10_000L

/** How often the time and the slider follow the video as it plays. */
private const val PROGRESS_TICK_MILLISECONDS = 250L

/** The video's big buttons in a row, with room at its sides: narrower, they're smaller. */
private val TransportRoom = 296.dp

/**
 * The idea's video, full screen, played by the player for where it's from:
 * a file in the folder of the map `mapId` or on the web plays in Media3's
 * ExoPlayer, YouTube's and Vimeo's in their own players, and anything else
 * opens in the app for it. `resolve` says what an address is, as for
 * pictures. `onKey` takes keys first, as a presentation does.
 */
@Composable
fun VideoScreen(mapId: String, item: GalleryItem, resolve: (String) -> Any?, onClosed: () -> Unit, onKey: (android.view.KeyEvent) -> Boolean = { false }) {
    val context = LocalContext.current
    val source = remember(item) { videoSource(item.media, resolve) }
    val title = item.media.caption?.takeIf { it.isNotBlank() } ?: item.label
    val poster = remember(item) { posterOf(item.media)?.let(resolve) }

    when (source) {
        is VideoSource.InMap -> if (source.file.isFile) {
            val elsewhere = rememberOpeningElsewhere(mapId, source.file)
            VideoPlayer(title, Uri.fromFile(source.file), poster, onElsewhere = elsewhere::start, onClosed = onClosed, onKey = onKey, opening = elsewhere)
        } else {
            // One a backup left out, say: only its poster is here.
            VideoPlayer(title, null, poster, onElsewhere = null, onClosed = onClosed, onKey = onKey)
        }
        is VideoSource.OnTheWeb -> VideoPlayer(title, source.url.toUri(), poster, onElsewhere = { openVideoElsewhere(context, source) }, onClosed = onClosed, onKey = onKey)
        is VideoSource.YouTube, is VideoSource.Vimeo -> WebVideoPlayer(source, title, onClosed, onKey)
        else -> LaunchedEffect(item) {
            source?.let { openVideoElsewhere(context, it) }
            onClosed()
        }
    }
}

/**
 * A video file full screen in ExoPlayer: it plays at once, fitted to the
 * screen whichever way the phone turns, with the poster until its first
 * frame. Play and pause, ten seconds back and on, the time and where the
 * video is (to drag), and sound on or off; the controls go after a moment
 * of playing, and a tap brings them back. The screen stays on while it
 * plays, it pauses when the app leaves the screen, and the player goes
 * when the window closes. Keys: space, K or the media keys play and pause,
 * the arrows, J and L go back and on, M turns the sound off or on, and
 * Escape closes. Tab brings the controls back too, and goes through them:
 * a button it went to takes Space and Enter, as it takes a tap, and the
 * controls stay while one has the focus. With no `uri`, the video isn't on
 * this phone, and its poster shows, saying so. `onKey` takes keys first,
 * as a presentation does. `opening`, for a video in a map's folder, is how
 * it's opening in another app, as `onElsewhere` starts it: the button
 * waits for its copy, and what it asks or says shows over the player.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    title: String,
    uri: Uri?,
    poster: Any?,
    onElsewhere: (() -> Unit)?,
    onClosed: () -> Unit,
    onKey: (android.view.KeyEvent) -> Boolean = { false },
    opening: OpeningElsewhere? = null,
) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(SEEK_MILLISECONDS)
            .setSeekForwardIncrementMs(SEEK_MILLISECONDS)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, uri) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }

    VideoPlayer(player, title, missing = uri == null, poster, onElsewhere, onClosed, onKey, opening)
}

/**
 * [VideoPlayer]'s screen, with `player` playing the video: or, `missing`,
 * with the video's poster and why it doesn't play. The player is the
 * caller's to make and let go.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
    player: Player,
    title: String,
    missing: Boolean,
    poster: Any?,
    onElsewhere: (() -> Unit)?,
    onClosed: () -> Unit,
    onKey: (android.view.KeyEvent) -> Boolean = { false },
    opening: OpeningElsewhere? = null,
) {
    var controls by remember { mutableStateOf(true) }
    // Goes up with every touch and key, so the controls stay a moment longer.
    var touched by remember { mutableIntStateOf(0) }
    // One of the controls has the keyboard's focus (Tab went to it): they stay while it does.
    var controlFocused by remember { mutableStateOf(false) }
    // Tab or Shift+Tab with the controls gone: where the focus goes once they're back, the first of them or the last.
    var tabbing by remember { mutableStateOf<FocusDirection?>(null) }
    var playing by remember { mutableStateOf(false) }
    // Why the video doesn't play, as a string resource, when it doesn't.
    var problem by remember { mutableStateOf(R.string.media_video_missing.takeIf { missing }) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        // Back on screen: the frame it stopped at shows again, a surface may have gone meanwhile, with the controls to go on.
        if (player.playbackState != Player.STATE_IDLE && !player.isPlaying) {
            player.seekTo(player.currentPosition)
            controls = true
        }
    }

    // A screen reader can't tap hidden controls back: while one explores the screen, they stay.
    val linger = controlsLinger(CONTROLS_LINGER_MILLISECONDS)
    val lingering by rememberUpdatedState(linger)

    ControlsStepAside(controls, linger, focused = controlFocused, running = playing, moment = touched) { controls = it }

    fun keys(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return false
        }

        // With the controls there, Tab goes through them; gone, it brings them back first, as the other keys do.
        if (event.key == Key.Tab) {
            if (controls) {
                return false
            }

            tabbing = if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
        }

        when (event.key) {
            Key.Spacebar, Key.K, Key.MediaPlayPause, Key.Enter, Key.NumPadEnter -> Util.handlePlayPauseButtonAction(player)
            Key.MediaPlay -> player.play()
            Key.MediaPause -> player.pause()
            Key.DirectionLeft, Key.J, Key.MediaRewind -> player.seekBack()
            Key.DirectionRight, Key.L, Key.MediaFastForward -> player.seekForward()
            Key.M -> player.volume = if (player.volume > 0f) 0f else 1f
            Key.Tab -> Unit
            else -> return false
        }

        controls = true
        touched += 1
        return true
    }

    FullScreenWindow(onClosed = onClosed, immersive = !controls && problem == null, onKey = onKey) { window ->
        val view = LocalView.current
        val focus = remember { FocusRequester() }

        // The screen stays on while the video plays; the window's own view keeps it so.
        DisposableEffect(player, view) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                    view.keepScreenOn = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // At its end, the controls come back, to play it again.
                    if (playbackState == Player.STATE_ENDED) {
                        controls = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Logs.warn("OrbitVideo", "The video couldn't be played: ${error.errorCodeName}")
                    problem = R.string.media_video_unplayable
                    controls = true
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
                view.keepScreenOn = false
            }
        }

        // After a touch, Android spends the first key on finding a view to focus: the player has it already.
        DisposableEffect(view) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
            onDispose {}
        }

        LaunchedEffect(Unit) { focus.requestFocus() }

        val focusManager = LocalFocusManager.current

        LaunchedEffect(tabbing) {
            val direction = tabbing ?: return@LaunchedEffect
            // Once the controls are laid out again.
            withFrameNanos {}
            focusManager.moveFocus(direction)
            tabbing = null
        }

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            controls = !controls || lingering == null
                            touched += 1
                        })
                    }
                    .onFocusChanged { controlFocused = it.hasFocus && !it.isFocused }
                    .focusRequester(focus)
                    .focusable()
                    .viewKeys(::keys),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContentFrame(
                        player = player,
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = ContentScale.Fit,
                        shutter = {
                            Box(Modifier.fillMaxSize().background(Color.Black)) {
                                if (poster != null) {
                                    AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                            }
                        },
                    )
                }

                AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                    PlayerTopBar(player, title, onClose = window::close, onElsewhere = onElsewhere, busy = opening?.busy == true)
                }

                val failed = problem

                if (failed != null) {
                    PlayerProblem(stringResource(failed), onElsewhere, busy = opening?.busy == true, Modifier.align(Alignment.Center))
                } else {
                    AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                        PlayerTransport(player)
                    }
                }

                AnimatedVisibility(controls && problem == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    PlayerProgress(player)
                }
            }
        }

        opening?.let { OpeningElsewhereDialogs(it) }
    }
}

/**
 * Ten seconds back, play or pause, and ten seconds on, side by side in the
 * middle: big where there's room, and in a window too narrow for them, as
 * narrow as Android makes one, smaller, and still a touch's size.
 */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerTransport(player: Player) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val roomy = maxWidth >= TransportRoom
        val round = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
        val seek = if (roomy) 56.dp else 48.dp

        Row(horizontalArrangement = Arrangement.spacedBy(if (roomy) 28.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Media3's own descriptions read "Skip back %d seconds", without the number.
            SeekBackButton(
                player,
                modifier = round.size(seek),
                contentDescription = {
                    val seconds = (seekBackAmountMs / 1000).toInt()
                    pluralStringResource(R.plurals.media_seek_back, seconds, seconds)
                },
            )
            PlayPauseButton(player, modifier = round.size(if (roomy) 80.dp else 64.dp), iconSize = if (roomy) 44.dp else 36.dp)
            SeekForwardButton(
                player,
                modifier = round.size(seek),
                contentDescription = {
                    val seconds = (seekForwardAmountMs / 1000).toInt()
                    pluralStringResource(R.plurals.media_seek_forward, seconds, seconds)
                },
            )
        }
    }
}

/** Closing, the video's name, its sound, and opening it in another app, `busy` while its copy is made, over a shade. */
@Composable
private fun PlayerTopBar(player: Player, title: String, onClose: () -> Unit, onElsewhere: (() -> Unit)?, busy: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
    ) {
        val close = stringResource(R.string.common_close)

        Hint(close) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = close) }
        }

        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        PlayerMuteButton(player)

        if (onElsewhere != null) {
            val elsewhere = stringResource(R.string.media_open_in_another_app)

            Hint(elsewhere) {
                IconButton(onClick = onElsewhere, enabled = !busy, modifier = Modifier.semantics { contentDescription = elsewhere }) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    }
                }
            }
        }
    }
}

/** The video's sound on or off, as a sound's own page turns it: the same button and words. */
@Composable
private fun PlayerMuteButton(player: Player) {
    var muted by remember(player) { mutableStateOf(player.volume == 0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                muted = volume == 0f
            }
        }

        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val switch = stringResource(if (muted) R.string.audio_unmute else R.string.audio_mute)

    Hint(switch) {
        IconButton(onClick = { player.volume = if (muted) 1f else 0f }) {
            Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = switch)
        }
    }
}

/**
 * Where the video is, to drag, with the time it's at and how long it is,
 * over a shade: a sound's own timeline, so both read and sound the same,
 * screen readers included.
 */
@Composable
private fun PlayerProgress(player: Player) {
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableStateOf<Long?>(null) }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            delay(PROGRESS_TICK_MILLISECONDS)
        }
    }

    MediaTimeline(
        position,
        duration,
        onSeek = { at ->
            player.seekTo(at)
            position = at
        },
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.24f),
        textColor = Color.White.copy(alpha = 0.78f),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 12.dp),
    )
}

/** Why the video doesn't play, on a shade that keeps it readable over the poster, and what to do when the phone can't play it: another app may. */
@Composable
private fun PlayerProblem(message: String, onElsewhere: (() -> Unit)?, busy: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .widthIn(max = 420.dp)
            .padding(24.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(44.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)

        if (onElsewhere != null) {
            FilledTonalButton(onClick = onElsewhere, enabled = !busy) { Text(stringResource(R.string.media_open_in_another_app)) }
        }
    }
}

/** A video Orbit couldn't clean, as it was then (`stamp`), asked about: again when it `changed` after the person answered. */
data class AskedVideo(val stamp: FileStamp, val changed: Boolean = false)

/**
 * "Open in another app" for a video in the folder of a map: a clean copy of
 * it opens ([openVideoInAnotherApp]), `busy` while it's made. One Orbit
 * can't clean waits for the person to say whether it goes as it is
 * (`asking`), as it was then: one that changed before it went is asked
 * about again, as it is now. One no copy could be made of says so
 * (`failed`).
 */
@Stable
class OpeningElsewhere internal constructor(
    private val scope: CoroutineScope,
    private val open: suspend () -> Shared,

    /** Opens the video as it is, the one the person was asked about (its stamp), once they chose to. */
    private val openAsItIs: suspend (FileStamp) -> Shared,
) {
    /** A copy is being made, clean or as it is: another tap waits for it. */
    var busy by mutableStateOf(false)
        private set

    var asking by mutableStateOf<AskedVideo?>(null)
        private set

    var failed by mutableStateOf(false)
        private set

    fun start() {
        if (busy) {
            return
        }

        busy = true

        scope.launch {
            try {
                cleanly(changed = false)
            } finally {
                busy = false
            }
        }
    }

    /** Opens a clean copy, or asks about the video as it is, saying when it `changed` after the person answered. */
    private suspend fun cleanly(changed: Boolean) {
        when (val opened = open()) {
            is Shared.NotClean -> asking = AskedVideo(opened.stamp, changed)
            Shared.Failed, Shared.Changed -> failed = true
            Shared.Clean -> Unit
        }
    }

    /** The person's answer: whether the video goes as it is. */
    fun answer(asItIs: Boolean) {
        val asked = asking ?: return
        asking = null

        if (asItIs) {
            // Its copy takes a while: a tap meanwhile makes no second one.
            busy = true

            scope.launch {
                try {
                    when (openAsItIs(asked.stamp)) {
                        // Another file in its place, or the same one written again: it opens clean, or is asked about again, once.
                        Shared.Changed -> {
                            if (asked.changed) {
                                failed = true
                            } else {
                                cleanly(changed = true)
                            }
                        }
                        Shared.Failed -> failed = true
                        Shared.Clean, is Shared.NotClean -> Unit
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    fun dismissFailure() {
        failed = false
    }
}

/** How the video `file` in the folder of the map `mapId` opens in another app: see [OpeningElsewhere]. */
@Composable
fun rememberOpeningElsewhere(mapId: String, file: File): OpeningElsewhere {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return remember(mapId, file) {
        OpeningElsewhere(scope, open = { openVideoInAnotherApp(context, mapId, file) }, openAsItIs = { stamp -> openVideoAsItIs(context, mapId, file, stamp) })
    }
}

/**
 * Asks whether a video Orbit couldn't clean opens in another app as it is,
 * with where and when it was recorded and on what, as sharing one asks: a
 * risk the person takes knowingly, never one Orbit takes for them. And says
 * so when no copy of it could be made.
 */
@Composable
private fun OpeningElsewhereDialogs(opening: OpeningElsewhere) {
    opening.asking?.let { asked ->
        AlertDialog(
            onDismissRequest = { opening.answer(asItIs = false) },
            title = {
                EscapeCloses { opening.answer(asItIs = false) }
                Text(stringResource(R.string.media_open_video_as_it_is_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Asked again, as it changed after the person answered: said first.
                    if (asked.changed) {
                        AskedAgain(1)
                    }

                    Text(stringResource(R.string.media_open_video_as_it_is_text))
                }
            },
            confirmButton = { TextButton(onClick = { opening.answer(asItIs = true) }) { Text(stringResource(R.string.media_open_as_it_is)) } },
            dismissButton = { TextButton(onClick = { opening.answer(asItIs = false) }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (opening.failed) {
        AlertDialog(
            onDismissRequest = opening::dismissFailure,
            text = {
                EscapeCloses(opening::dismissFailure)
                Text(stringResource(R.string.media_open_video_failed))
            },
            confirmButton = { TextButton(onClick = opening::dismissFailure) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

/**
 * Opens a video on the web outside Orbit: YouTube's and Vimeo's in their
 * apps or the browser, and any other in the app that takes its address,
 * else a tab. A video in a map's folder never goes from here, with all its
 * file says: it opens as a clean copy ([openVideoInAnotherApp]), or as it
 * is only once the person chose to ([openVideoAsItIs]).
 */
fun openVideoElsewhere(context: Context, source: VideoSource) {
    val address = when (source) {
        is VideoSource.InMap -> return
        is VideoSource.OnTheWeb -> source.url
        is VideoSource.YouTube -> watchUrl(source)
        is VideoSource.Vimeo -> watchUrl(source)
        is VideoSource.Elsewhere -> source.url
    }

    val scheme = linkScheme(address)

    if (scheme != "http" && scheme != "https") {
        return
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, cleanLink(address).toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (missing: ActivityNotFoundException) {
        openLink(context, address)
    }
}
