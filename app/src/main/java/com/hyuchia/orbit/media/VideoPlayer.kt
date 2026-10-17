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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import androidx.media3.ui.compose.material3.buttons.MuteButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.material3.indicator.DurationText
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import coil3.compose.AsyncImage
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.cleanLink
import com.hyuchia.orbit.ui.linkScheme
import com.hyuchia.orbit.ui.openLink
import kotlinx.coroutines.delay

/** How long the controls stay after a touch or a key while the video plays. */
private const val CONTROLS_LINGER_MILLISECONDS = 3_000L

/** The buttons and the arrow keys go this far back or on. */
private const val SEEK_MILLISECONDS = 10_000L

/**
 * The idea's video, full screen, played by the player for where it's from:
 * a file in the map's folder or on the web plays in Media3's ExoPlayer,
 * YouTube's and Vimeo's in their own players, and anything else opens in
 * the app for it. `resolve` says what an address is, as for pictures.
 * `onKey` takes keys first, as a presentation does.
 */
@Composable
fun VideoScreen(item: GalleryItem, resolve: (String) -> Any?, onClosed: () -> Unit, onKey: (android.view.KeyEvent) -> Boolean = { false }) {
    val context = LocalContext.current
    val source = remember(item) { videoSource(item.media, resolve) }
    val title = item.media.caption?.takeIf { it.isNotBlank() } ?: item.label
    val poster = remember(item) { posterOf(item.media)?.let(resolve) }

    when (source) {
        is VideoSource.InMap -> if (source.file.isFile) {
            VideoPlayer(title, Uri.fromFile(source.file), poster, onElsewhere = { openVideoElsewhere(context, source) }, onClosed = onClosed, onKey = onKey)
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
 * Escape closes. With no `uri`, the video isn't on this phone, and its
 * poster shows, saying so. `onKey` takes keys first, as a presentation does.
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

    var controls by remember { mutableStateOf(true) }
    // Goes up with every touch and key, so the controls stay a moment longer.
    var touched by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    // Why the video doesn't play, as a string resource, when it doesn't.
    var problem by remember { mutableStateOf(R.string.media_video_missing.takeIf { uri == null }) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        // Back on screen: the frame it stopped at shows again, a surface may have gone meanwhile, with the controls to go on.
        if (player.playbackState != Player.STATE_IDLE && !player.isPlaying) {
            player.seekTo(player.currentPosition)
            controls = true
        }
    }

    LaunchedEffect(controls, playing, touched) {
        if (controls && playing) {
            delay(CONTROLS_LINGER_MILLISECONDS)
            controls = false
        }
    }

    fun keys(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return false
        }

        when (event.key) {
            Key.Spacebar, Key.K, Key.MediaPlayPause, Key.Enter, Key.NumPadEnter -> Util.handlePlayPauseButtonAction(player)
            Key.MediaPlay -> player.play()
            Key.MediaPause -> player.pause()
            Key.DirectionLeft, Key.J, Key.MediaRewind -> player.seekBack()
            Key.DirectionRight, Key.L, Key.MediaFastForward -> player.seekForward()
            Key.M -> player.volume = if (player.volume > 0f) 0f else 1f
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

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            controls = !controls
                            touched += 1
                        })
                    }
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent(::keys),
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
                    PlayerTopBar(player, title, onClose = window::close, onElsewhere = onElsewhere)
                }

                val failed = problem

                if (failed != null) {
                    PlayerProblem(stringResource(failed), onElsewhere, Modifier.align(Alignment.Center))
                } else {
                    AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            val round = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
                            // Media3's own descriptions read "Skip back %d seconds", without the number.
                            SeekBackButton(
                                player,
                                modifier = round.size(56.dp),
                                contentDescription = {
                                    val seconds = (seekBackAmountMs / 1000).toInt()
                                    pluralStringResource(R.plurals.media_seek_back, seconds, seconds)
                                },
                            )
                            PlayPauseButton(player, modifier = round.size(80.dp), iconSize = 44.dp)
                            SeekForwardButton(
                                player,
                                modifier = round.size(56.dp),
                                contentDescription = {
                                    val seconds = (seekForwardAmountMs / 1000).toInt()
                                    pluralStringResource(R.plurals.media_seek_forward, seconds, seconds)
                                },
                            )
                        }
                    }
                }

                AnimatedVisibility(controls && problem == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    PlayerProgress(player)
                }
            }
        }
    }
}

/** Closing, the video's name, its sound, and opening it in another app, over a shade. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerTopBar(player: Player, title: String, onClose: () -> Unit, onElsewhere: (() -> Unit)?) {
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
        MuteButton(player)

        if (onElsewhere != null) {
            val elsewhere = stringResource(R.string.media_open_in_another_app)

            Hint(elsewhere) {
                IconButton(onClick = onElsewhere) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = elsewhere) }
            }
        }
    }
}

/** Where the video is, to drag, between the time it's at and how long it is, over a shade. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerProgress(player: Player) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 12.dp),
    ) {
        PositionText(player, color = Color.White)
        ProgressSlider(player, modifier = Modifier.weight(1f))
        DurationText(player, color = Color.White)
    }
}

/** Why the video doesn't play, on a shade that keeps it readable over the poster, and what to do when the phone can't play it: another app may. */
@Composable
private fun PlayerProblem(message: String, onElsewhere: (() -> Unit)?, modifier: Modifier = Modifier) {
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
            FilledTonalButton(onClick = onElsewhere) { Text(stringResource(R.string.media_open_in_another_app)) }
        }
    }
}

/**
 * Opens a video outside Orbit: a file in the map's folder in the phone's
 * video app, YouTube's and Vimeo's in their apps or the browser, and any
 * other in the app that takes its address, else a tab.
 */
fun openVideoElsewhere(context: Context, source: VideoSource) {
    val address = when (source) {
        is VideoSource.InMap -> {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source.file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                context.startActivity(intent)
            } catch (missing: ActivityNotFoundException) {
                Logs.warn("OrbitVideo", "No app plays videos")
            }

            return
        }
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
