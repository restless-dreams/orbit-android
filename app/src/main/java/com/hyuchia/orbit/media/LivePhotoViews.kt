package com.hyuchia.orbit.media

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.hyuchia.orbit.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * How a live photo's motion plays: once without sound, as its page comes up;
 * once with it, when asked; or with it while the picture is `held`. `round`
 * tells one play from the next, so asking again starts over.
 */
internal data class LivePlay(val sound: Boolean, val held: Boolean = false, val round: Long = System.nanoTime())

/**
 * A live photo's motion, under its still and fitted as the still is: the
 * still keeps every touch, and fades away over the motion (see
 * [animateStillAlpha]) as `onShowing` says the motion's first frame shows,
 * and back as it ends, or as `play` goes (the finger lifted). Its player is
 * made only once it's asked to play, starts once its texture is laid out,
 * so none of it plays unseen, and lets go of the phone's sound when it plays
 * without any.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LiveMotion(source: Any?, play: LivePlay?, onShowing: (Boolean) -> Unit, onEnded: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = remember(source) { (source as? File)?.toUri() ?: (source as? String)?.toUri() } ?: return
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var showing by remember(uri) { mutableStateOf(false) }
    var textured by remember(uri) { mutableStateOf(false) }
    val ended by rememberUpdatedState(onEnded)
    val shows by rememberUpdatedState(onShowing)
    val visible = showing && play != null

    LaunchedEffect(visible) {
        shows(visible)
    }

    DisposableEffect(uri) {
        onDispose {
            player?.release()
            player = null
            // Its still comes back whole, whatever took the motion away.
            shows(false)
        }
    }

    LaunchedEffect(uri, play) {
        val playing = player

        if (play == null) {
            showing = false
            // The still comes back over the motion's last frame before the motion stops.
            delay(FADE_OUT.toLong())
            playing?.pause()
            return@LaunchedEffect
        }

        val motion = playing ?: ExoPlayer.Builder(context).build().also { made ->
            made.addListener(
                object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        showing = true
                    }

                    override fun onSurfaceSizeChanged(width: Int, height: Int) {
                        textured = width > 0 && height > 0
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            ended()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        ended()
                    }
                },
            )
            made.setMediaItem(MediaItem.fromUri(uri))
            player = made
        }

        // Without sound, it leaves whatever else plays alone; with it, it takes the sound as a video does.
        motion.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), play.sound)
        motion.volume = if (play.sound) 1f else 0f
        motion.seekTo(0)
        motion.prepare()
        snapshotFlow { textured }.first { it }
        motion.play()
    }

    player?.let { playing ->
        MotionFrame(playing, modifier.clearAndSetSemantics {})
    }
}

/** How much of a live photo's still shows over its motion: none while the motion shows, all as it goes. */
@Composable
internal fun animateStillAlpha(motionShowing: Boolean): State<Float> =
    animateFloatAsState(if (motionShowing) 0f else 1f, tween(if (motionShowing) FADE_IN else FADE_OUT), label = "still")

/**
 * A player's video, fitted in its place, on a texture that may be clear:
 * an opaque one takes the place of what's under it outright, so where it
 * has no frame, it would show the window behind the viewer.
 */
@OptIn(UnstableApi::class)
@Composable
private fun MotionFrame(player: ExoPlayer, modifier: Modifier) {
    val context = LocalContext.current
    val presentation = rememberPresentationState(player)
    val texture = remember(context) { TextureView(context).apply { isOpaque = false } }

    DisposableEffect(player, texture) {
        player.setVideoTextureView(texture)
        onDispose { player.clearVideoTextureView(texture) }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { texture }, modifier = Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp))
    }
}

/**
 * A live photo's mark: its icon and "Live", in a soft pill over the picture.
 * With `onPlay`, it's a button that plays the motion with its sound, as
 * holding the picture does; screen readers hear it as that.
 */
@Composable
fun LiveMark(onPlay: (() -> Unit)?, modifier: Modifier = Modifier, container: Color = Color.Black.copy(alpha = 0.45f), content: Color = Color.White) {
    val play = stringResource(R.string.media_play_live_photo)
    val live = stringResource(R.string.media_live_photo)
    val pressable = if (onPlay != null) Modifier.clip(CircleShape).clickable(onClickLabel = play, role = Role.Button, onClick = onPlay) else Modifier

    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.then(pressable).semantics { contentDescription = live },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                .clearAndSetSemantics {},
        ) {
            Icon(Icons.Outlined.MotionPhotosOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.media_live), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** How long the still takes to give way to the motion, and to come back, in milliseconds. */
private const val FADE_IN = 160
private const val FADE_OUT = 320
