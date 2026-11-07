package com.hyuchia.orbit.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.OrbitSlider
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** What a sound's buttons do: the map's player ([SoundPlayback]) does each. */
interface SoundControls {
    fun play(item: GalleryItem, from: Long? = null)
    fun toggle(item: GalleryItem)
    fun pause()
    fun seekTo(position: Long)
    fun skip(by: Long)
    fun toggleMute()
}

/** A time as players write it: 0:42, 3:05, 1:02:09; null and the unknown as --:--. */
fun soundTime(milliseconds: Long?): String {
    if (milliseconds == null || milliseconds < 0) {
        return "--:--"
    }

    val seconds = milliseconds / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val rest = (seconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$rest" else "$minutes:$rest"
}

/** `item`'s state, when it's the sound the player has. */
fun SoundState?.of(item: GalleryItem): SoundState? = this?.takeIf { it.item.key == item.key && it.item.media.src == item.media.src }

/**
 * A sound's art: its idea's picture, or art made of the idea's color and
 * icon: a planet's light, an orbit, and a moon on it, turning while the
 * sound plays and resting where it is when it stops.
 */
@Composable
fun SoundArt(look: IdeaLook?, playing: Boolean, source: (String) -> Any?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(28.dp)) {
    val picture = look?.picture?.let(source)
    val color = parseCssColor(look?.color) ?: MaterialTheme.colorScheme.primaryContainer

    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            OrbitArt(color, look?.icon, playing)
        }
    }
}

/** Art made of an idea's color and icon, for a sound without a picture. */
@Composable
private fun OrbitArt(color: Color, icon: String?, playing: Boolean) {
    // The orbit turns while the sound plays, and holds where it is when it stops, rather than jumping back.
    val turn = remember { Animatable(0f) }
    // Lit from the top left, as the map's planets are, and only a little darker away from the light, so pastels stay clean.
    val light = lerp(color, Color.White, 0.55f)
    val shade = lerp(color, Color.Black, 0.2f)

    LaunchedEffect(playing) {
        while (playing) {
            turn.animateTo(turn.value + 360f, tween(durationMillis = 18_000, easing = LinearEasing))
        }
    }

    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight)

        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(light, color, shade), center = Offset(size.width * 0.3f, size.height * 0.24f), radius = size.maxDimension * 1.05f))
            val center = Offset(size.width / 2, size.height / 2)
            val small = minOf(size.width, size.height)
            val outer = small * 0.43f
            val inner = small * 0.29f
            val angle = Math.toRadians((turn.value - 90.0))
            val back = Math.toRadians((-turn.value * 0.3 - 90.0))
            drawCircle(Color.White.copy(alpha = 0.32f), outer, center, style = Stroke(width = small * 0.008f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(small * 0.02f, small * 0.025f), phase = (back * outer).toFloat())))
            drawCircle(Color.White.copy(alpha = 0.5f), inner, center, style = Stroke(width = small * 0.01f))
            val moon = Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat())
            drawCircle(Color.White.copy(alpha = 0.25f), small * 0.05f, moon)
            drawCircle(Color.White.copy(alpha = 0.95f), small * 0.03f, moon)
        }

        if (icon != null) {
            Text(icon, style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * (side.value / 240f).coerceIn(0.35f, 1.6f)), textAlign = TextAlign.Center)
        } else {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(side * 0.3f))
        }
    }
}

/**
 * Play and pause, in Material's expressive way: a circle while the sound
 * waits to play, a rounded square while it plays, and a moving ring while
 * it waits for more of itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundPlayButton(playing: Boolean, waiting: Boolean, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.primary, contentColor: Color = MaterialTheme.colorScheme.onPrimary, label: String? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(if (playing) 30 else 50, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "corner")
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    val name = label ?: stringResource(if (playing) R.string.audio_pause else R.string.audio_play)

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        if (waiting) {
            LoadingIndicator(color = containerColor, modifier = Modifier.size(size * 1.28f))
        }

        Surface(
            shape = RoundedCornerShape(corner),
            color = if (enabled) containerColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            shadowElevation = if (enabled) 2.dp else 0.dp,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(corner))
                .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, onClickLabel = name, onClick = onClick)
                .semantics { contentDescription = name },
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = playing,
                    transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                    label = "icon",
                ) { shown ->
                    Icon(if (shown) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(size * 0.5f))
                }
            }
        }
    }
}

/**
 * Where a sound is, to drag or tap, between the time it's at and how long
 * it is: Orbit's own slider, which a tablet's side pane can measure, as it
 * can't Material's (see [OrbitSlider]). While the handle is held, the time
 * follows it, and the sound jumps there once it's let go; the arrow keys
 * move it five seconds. Until the sound's length is known, it waits, faded.
 */
@Composable
fun SoundTimeline(state: SoundState?, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, length: Long? = null) {
    // Before it plays, how long it is, when that's known: the timeline waits for it to play to move.
    MediaTimeline(state?.position ?: 0L, state?.duration ?: length, onSeek, modifier, color, trackColor, textColor, enabled = state?.duration?.let { it > 0 } == true)
}

/**
 * Where a sound or a video is, `position` of `duration` (ms; null until
 * it's known), as [SoundTimeline] shows it: the same slider, times and
 * words for both.
 */
@Composable
fun MediaTimeline(
    position: Long,
    duration: Long?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = duration != null && duration > 0,
) {
    var held by remember { mutableStateOf<Float?>(null) }
    val shown = held ?: duration?.takeIf { it > 0 }?.let { (position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val at = duration?.let { (shown * it).toLong() } ?: position

    Column(modifier) {
        OrbitSlider(
            value = shown,
            onValueChange = { held = it },
            onValueChangeFinished = {
                held?.let { fraction -> duration?.let { onSeek((fraction * it).toLong()) } }
                held = null
            },
            label = stringResource(R.string.audio_position),
            valueDescription = stringResource(R.string.audio_time_of, soundTime(at), soundTime(duration)),
            ticks = 0,
            enabled = enabled,
            keyStep = duration?.let { TIMELINE_KEY_STEP_MILLISECONDS.toFloat() / it },
            color = color,
            trackColor = trackColor,
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(soundTime(at), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = textColor)
            Spacer(Modifier.weight(1f))
            Text(soundTime(duration), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = textColor)
        }
    }
}

/** How far the arrow keys move a sound (ms). */
private const val TIMELINE_KEY_STEP_MILLISECONDS = 5_000L

/**
 * An idea's sound as a card, for its details and its editor: its art, its
 * name and how long it is, and play, with where it is once it plays. It
 * plays in the map's player, so it goes on when the card goes.
 */
@Composable
fun SoundCard(item: GalleryItem, sound: SoundState?, controls: SoundControls, source: (String) -> Any?, length: Long?, modifier: Modifier = Modifier, subtitle: String? = null) {
    val playing = sound.of(item)
    val title = item.soundTitle()

    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SoundArt(item.look, playing?.playing == true, source, Modifier.size(64.dp), RoundedCornerShape(20.dp))

                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    // How long it is, until the timeline under it says so.
                    val detail = when {
                        playing?.failed == true -> stringResource(if (source(item.media.src) is File) R.string.audio_missing else R.string.audio_unplayable)
                        playing != null -> subtitle
                        else -> listOfNotNull(subtitle, length?.let(::soundTime)).joinToString(" · ").ifEmpty { null }
                    }

                    detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = if (playing?.failed == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                SoundPlayButton(
                    playing = playing?.playing == true,
                    waiting = playing?.waiting == true,
                    onClick = { controls.toggle(item) },
                    size = 52.dp,
                    label = stringResource(if (playing?.playing == true) R.string.audio_pause_the_sound else R.string.audio_play_the_sound),
                )
            }

            // Once it plays, where it is, to drag.
            if (playing != null && !playing.failed) {
                SoundTimeline(playing, onSeek = controls::seekTo, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * The sound playing, small, over the map: its art and name, its time, play
 * and pause, and a way to stop it, over a line that waves while it plays. A
 * tap on it opens the sound full screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundBar(sound: SoundState, controls: SoundControls, source: (String) -> Any?, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val item = sound.item
    val wave by animateFloatAsState(if (sound.playing) 1f else 0f, tween(400), label = "wave")
    val open = stringResource(R.string.audio_open_full_screen)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
        modifier = modifier.widthIn(max = 480.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .clickable(onClickLabel = open, onClick = onOpen)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            ) {
                SoundArt(item.look, sound.playing, source, Modifier.size(44.dp), CircleShape)

                Column(Modifier.weight(1f)) {
                    Text(item.soundTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    if (sound.failed) {
                        Text(stringResource(R.string.audio_unplayable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        // Whose sound it is gives way to its time, which always shows whole.
                        Row {
                            Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text(
                                " · ${soundTime(sound.position)} / ${soundTime(sound.duration)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                SoundPlayButton(sound.playing, sound.waiting, onClick = { controls.toggle(item) }, size = 48.dp)

                val stop = stringResource(R.string.audio_stop)

                Hint(stop) {
                    IconButton(onClick = onStop) { Icon(Icons.Rounded.Close, contentDescription = stop) }
                }
            }

            // A wave while it plays; a still line once it pauses or ends.
            LinearWavyProgressIndicator(
                progress = { sound.progress },
                amplitude = if (sound.playing) ({ wave }) else ({ 0f }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                    .height(10.dp),
            )
        }
    }
}

/**
 * A sound's page in the viewer, on the dark: its art as big as the screen
 * allows (none on a phone held sideways, where the room goes to its
 * buttons), its name and caption, where it is, and ten seconds back, play
 * and on, and its sound off and on.
 */
@Composable
fun SoundPage(item: GalleryItem, sound: SoundState?, controls: SoundControls, source: (String) -> Any?, length: Long? = null, onTap: () -> Unit) {
    val state = sound.of(item)

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onTap),
        ) {
            // A phone on its side has the art beside the rest, clear of the bar above them.
            val sideways = maxWidth > maxHeight && maxHeight < SidewaysPage
            val top = if (sideways) 88.dp else 72.dp
            val bottom = if (sideways) 24.dp else 72.dp
            val height = maxHeight - top - bottom
            val width = maxWidth - 48.dp
            val art = if (sideways) minOf(height, width * 0.4f) else minOf(width, height - 220.dp, 360.dp)

            val controlsColumn = @Composable { width: Modifier ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = width) {
                    // The bar above names the idea: the page names the sound.
                    Text(item.soundTitle(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)

                    if (state?.failed == true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                            Text(stringResource(if (source(item.media.src) is File) R.string.audio_missing else R.string.audio_unplayable))
                        }
                    } else {
                        SoundTimeline(state, onSeek = controls::seekTo, color = Color.White, trackColor = Color.White.copy(alpha = 0.24f), textColor = Color.White.copy(alpha = 0.78f), modifier = Modifier.fillMaxWidth(), length = length)
                        SoundButtons(item, state, controls)
                    }
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = top, bottom = bottom)) {
                if (sideways) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        SoundArt(item.look, state?.playing == true, source, Modifier.size(art), RoundedCornerShape(32.dp))
                        controlsColumn(Modifier.widthIn(max = 420.dp))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        if (art >= 96.dp) {
                            SoundArt(item.look, state?.playing == true, source, Modifier.size(art).aspectRatio(1f), RoundedCornerShape(36.dp))
                        }

                        controlsColumn(Modifier.widthIn(max = 420.dp).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** A page shorter than this, wider than tall, is a phone on its side. */
private val SidewaysPage = 500.dp

/** The room between the sound's buttons, where there's room. */
private val SoundButtonGap = 20.dp

/** The least room between them, as a narrow window closes it up. */
private val SoundButtonLeastGap = 4.dp

/**
 * Ten seconds back, play and pause, and ten on, with the sound's switch
 * beside them: play in the middle, under the sound's name and time, and the
 * switch at the end. A narrower window closes up the room between them, and
 * one too narrow for that has all four in a row, so none ever covers
 * another.
 */
@Composable
internal fun SoundButtons(item: GalleryItem, state: SoundState?, controls: SoundControls) {
    val seconds = (SOUND_SKIP_MILLISECONDS / 1_000).toInt()
    val back = pluralStringResource(R.plurals.media_seek_back, seconds, seconds)
    val forward = pluralStringResource(R.plurals.media_seek_forward, seconds, seconds)
    val muted = state?.muted == true
    val switch = stringResource(if (muted) R.string.audio_unmute else R.string.audio_mute)

    Layout(
        content = {
            Hint(back) {
                IconButton(onClick = { controls.skip(-SOUND_SKIP_MILLISECONDS) }, enabled = state != null, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Replay10, contentDescription = back, modifier = Modifier.size(32.dp))
                }
            }

            SoundPlayButton(
                playing = state?.playing == true,
                waiting = state?.waiting == true,
                onClick = { controls.toggle(item) },
                size = 80.dp,
                containerColor = Color.White,
                contentColor = Color(0xFF0B0C0F),
            )

            Hint(forward) {
                IconButton(onClick = { controls.skip(SOUND_SKIP_MILLISECONDS) }, enabled = state != null, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Forward10, contentDescription = forward, modifier = Modifier.size(32.dp))
                }
            }

            Hint(switch) {
                IconButton(onClick = controls::toggleMute, enabled = state != null, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = switch)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val buttons = measurables.map { it.measure(Constraints()) }
        val (backButton, playButton, forwardButton, muteButton) = buttons
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else buttons.sumOf { it.width } + 3 * SoundButtonGap.roundToPx()
        val rowHeight = buttons.maxOf { it.height }
        val gap = SoundButtonGap.roundToPx()
        val least = SoundButtonLeastGap.roundToPx()
        // Play in the middle, with the room between it and the skips that leaves the switch its own at the end.
        val beside = minOf(gap, width / 2 - playButton.width / 2 - forwardButton.width - muteButton.width - least)

        layout(width, rowHeight) {
            fun Placeable.placeAt(x: Int) = placeRelative(x, (rowHeight - height) / 2)

            if (beside >= least) {
                val start = width / 2 - playButton.width / 2 - beside - backButton.width
                backButton.placeAt(start)
                playButton.placeAt(start + backButton.width + beside)
                forwardButton.placeAt(start + backButton.width + beside + playButton.width + beside)
                muteButton.placeAt(width - muteButton.width)
            } else {
                // Too narrow for that: the four in a row, as close as they must be, the row in the middle.
                val row = buttons.sumOf { it.width }
                val between = ((width - row) / 3).coerceIn(0, gap)
                var x = ((width - row - 3 * between) / 2).coerceAtLeast(0)

                for (button in buttons) {
                    button.placeAt(x)
                    x += button.width + between
                }
            }
        }
    }
}
