package com.hyuchia.orbit.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
import androidx.compose.runtime.Stable
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
 * When a live photo's motion plays on the viewer's page for its picture
 * ([play]): once without sound as the page comes up and its still shows,
 * with its sound while the picture is held or its mark is pressed, and
 * never while it's zoomed in. Zooming in ends it, and so does Orbit leaving
 * the screen, the picture held or not, so the still is back either way.
 */
@Stable
internal class LivePhotoPlay {
    var play by mutableStateOf<LivePlay?>(null)
        private set

    /** The picture is held: the motion plays, with its sound, until the finger lifts. */
    var held by mutableStateOf(false)
        private set

    /**
     * The page is `current` or not, with Android's animations on (`moving`),
     * its still `shown` and `fitted`, not zoomed in: on screen so, it plays
     * once without sound; otherwise it doesn't.
     */
    fun arrive(current: Boolean, moving: Boolean, shown: Boolean, fitted: Boolean) {
        held = false
        play = if (current && moving && shown && fitted) LivePlay(sound = false) else null
    }

    /** Its mark, or a key, plays it with its sound, unless it's zoomed in. */
    fun press(fitted: Boolean) {
        if (fitted) {
            play = LivePlay(sound = true)
        }
    }

    /** Held, it plays with its sound until the finger lifts, unless it's zoomed in. */
    fun hold(fitted: Boolean) {
        if (fitted) {
            held = true
            play = LivePlay(sound = true, held = true)
        }
    }

    /** The finger that held it lifted: the still is back. */
    fun lift() {
        held = false
        play = null
    }

    /** It played through: the still is back, unless it's held, when the motion keeps its last frame until the finger lifts. */
    fun ended() {
        if (!held) {
            play = null
        }
    }

    /** Zoomed in, or Orbit left the screen: it ends there, held or not, and the still is back. */
    fun stop() {
        held = false
        play = null
    }
}

/**
 * A live photo's motion, under its still and fitted as the still is: the
 * still keeps every touch, and fades away over the motion (see
 * [animateStillAlpha]) as `onShowing` says the motion's first frame shows,
 * and back as it ends, or as `play` goes (the finger lifted). Its player is
 * made only once it's asked to play (`newPlayer`: Media3's ExoPlayer, or a
 * stand-in in tests), starts once its texture is laid out, so none of it
 * plays unseen, and lets go of the phone's sound when it plays without any.
 * Orbit leaving the screen ends the motion there and then: its sound
 * doesn't go on out of sight, and the still is back when Orbit is. So do
 * headphones coming out while it plays with its sound.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LiveMotion(
    source: Any?,
    play: LivePlay?,
    onShowing: (Boolean) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
    newPlayer: (Context) -> Player = { context -> ExoPlayer.Builder(context).build() },
) {
    val context = LocalContext.current
    val uri = remember(source) { (source as? File)?.toUri() ?: (source as? String)?.toUri() } ?: return
    var player by remember(uri) { mutableStateOf<Player?>(null) }
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

    // Orbit leaving the screen ends the motion at once, sound and all, as a sound or a video pauses then, and the still is back
    // whatever's asked of it: even while the picture is held, no frame of the motion stays over it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (play != null) {
            player?.pause()
            showing = false
            ended()
        }
    }

    // Headphones coming out end a motion playing with its sound in the same way, as a sound or a video pauses then: its sound
    // never goes on out loud. One playing without sound plays on.
    val sounding = play?.sound == true

    DisposableEffect(uri, sounding) {
        if (!sounding) {
            return@DisposableEffect onDispose {}
        }

        val unplugged = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    player?.pause()
                    showing = false
                    ended()
                }
            }
        }

        ContextCompat.registerReceiver(context, unplugged, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(unplugged) } }
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

        val motion = playing ?: newPlayer(context).also { made ->
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
private fun MotionFrame(player: Player, modifier: Modifier) {
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
