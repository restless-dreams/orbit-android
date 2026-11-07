package com.hyuchia.orbit.map

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hyuchia.orbit.R
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.VideoSource
import com.hyuchia.orbit.media.posterOf
import com.hyuchia.orbit.media.soundTime
import com.hyuchia.orbit.media.videoSource
import com.hyuchia.orbit.nearby.BrowserPresenting
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.CastButton
import com.hyuchia.orbit.present.ShownMedia
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.MarkdownNote
import com.hyuchia.orbit.ui.WavyProgress
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The stage's buttons for pictures and videos take this much of its foot; a map with any keeps the room free. */
private val StageBarRoom = 64.dp
private val StageCorner = 12.dp
private val StageProgressWidth = 132.dp
/** Behind a picture or poster on the stage, as behind the audience's. */
private val StageDark = Color(0xFF0B0C0F)
/** How often a playing video's time on the stage moves on. */
private const val MEDIA_TICK_MILLISECONDS = 250L
/** Narrower than this, as on a phone, the presenter's bar takes two lines: where the audience watches has one of its own. */
private val PresenterBarOneLine = 600.dp

/**
 * The presenter's screen while the audience watches others: a second
 * screen or TV, `screen`, or `browsers` on the local network. It
 * shows the map as they see it, the idea's notes, what comes next, a clock,
 * and big buttons to step through the ideas. Swipes across the notes step too.
 * The idea's picture or video goes to the audience from the map's preview,
 * which then shows it, with the video's controls. `playsMapVideos` is false
 * when the screen can't play the map's own video files, as a TV can't.
 */
@Composable
fun PresenterView(
    model: MapViewModel,
    ui: MapUi,
    presentation: Presentation,
    screen: Audience?,
    playsMapVideos: Boolean,
    browsers: BrowserPresenting?,
    seconds: Int,
    background: Color,
    map: @Composable () -> Unit,
    onBrowsers: () -> Unit,
) {
    // This screen's map is a preview: all of it shows, above the buttons for pictures and videos when the map has them.
    val hasMedia = ui.hasMedia
    LaunchedEffect(hasMedia) { model.setInsets(if (hasMedia) MapInsets(bottom = StageBarRoom.value) else MapInsets()) }
    val node = ui.nodes[presentation.current]
    val next = presentation.next?.let { ui.nodes[it] }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            PresenterBar(model, presentation, screen, browsers, dark = background.luminance() < 0.5f, onBrowsers = onBrowsers)

            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Stage(model, node, presentation, playsMapVideos, map, Modifier.weight(1f).fillMaxHeight())

                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Notes(model, node, next, presentation, seconds)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Stage(model, node, presentation, playsMapVideos, map, Modifier.fillMaxWidth().weight(0.42f))
                    Spacer(Modifier.size(16.dp))

                    Column(Modifier.fillMaxWidth().weight(0.58f)) {
                        Notes(model, node, next, presentation, seconds)
                    }
                }
            }
        }
    }
}

/** Where the audience is watching, the clock, how far along, and the buttons for the whole presentation. */
@Composable
private fun PresenterBar(model: MapViewModel, presentation: Presentation, screen: Audience?, browsers: BrowserPresenting?, dark: Boolean, onBrowsers: () -> Unit) {
    val words = rememberWords()
    val watching = browsers?.watching?.collectAsState()?.value
    val notice = screen?.notice?.collectAsState()?.value
    val where = listOfNotNull(
        stringResource(R.string.present_rehearsing).takeIf { presentation.rehearsing },
        screen?.let { audience -> audience.name?.let { stringResource(R.string.present_on_screen, it) } ?: stringResource(R.string.present_on_the_tv) },
        watching?.let { pluralStringResource(R.plurals.present_browsers_watching, it, it) },
    ).joinToString(" · ")

    Column {
        val progress = stringResource(R.string.present_progress, presentation.index + 1, presentation.order.size)
        LinearProgressIndicator(
            progress = { (presentation.index + 1f) / presentation.order.size },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = progress },
        )

        BoxWithConstraints {
            val now = rememberNow(presentation.stepStartedAt)
            val pace = presentation.takeIf { !it.rehearsing }?.pace(now, words)
            val behind = presentation.isBehind(now)

            // On a phone, where the audience watches has a line of its own, under the clock and the buttons; wider, it's all one line.
            if (maxWidth < PresenterBarOneLine) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Clock(presentation.startedAt)

                        if (pace != null) {
                            Pace(pace, behind)
                        }

                        Spacer(Modifier.weight(1f))
                        PresenterButtons(model, presentation, dark, onBrowsers)
                    }

                    Watching(where, maxLines = 2, modifier = Modifier.padding(start = 4.dp, end = 12.dp, bottom = 4.dp))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                ) {
                    Watching(where, maxLines = 1, modifier = Modifier.weight(1f))

                    if (pace != null) {
                        Pace(pace, behind)
                    }

                    Clock(presentation.startedAt)
                    PresenterButtons(model, presentation, dark, onBrowsers)
                }
            }
        }

        notice?.let {
            Text(
                stringResource(it.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            )
        }
    }
}

/** Where the audience watches: on a second screen or a TV, in browsers, and whether it's a rehearsal. */
@Composable
private fun Watching(where: String, maxLines: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Icon(Icons.Outlined.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(where, style = MaterialTheme.typography.titleSmall, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** How the presentation keeps to the last rehearsal's pace, in red when it's behind. */
@Composable
private fun Pace(pace: String, behind: Boolean) {
    Text(
        pace,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = if (behind) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        maxLines = 1,
    )
}

/** The buttons for the whole presentation: the captions, a TV, browsers, and stopping. */
@Composable
private fun PresenterButtons(model: MapViewModel, presentation: Presentation, dark: Boolean, onBrowsers: () -> Unit) {
    CaptionsButton(presentation.captions) { model.setCaptions(!presentation.captions) }
    CastButton(dark)
    BrowsersAndStopButtons(model, onBrowsers)
}

/** How long the presentation has been going: minutes and seconds, or hours past the first. */
@Composable
private fun Clock(startedAt: Long) {
    val elapsed by produceState(0L, startedAt) {
        while (true) {
            value = (SystemClock.elapsedRealtime() - startedAt) / 1_000
            delay(1_000L - (SystemClock.elapsedRealtime() - startedAt) % 1_000)
        }
    }

    val hours = elapsed / 3_600
    val minutes = elapsed / 60 % 60
    val seconds = elapsed % 60
    Text(
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds),
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * What the audience sees, smaller: the map, or over it the picture or the
 * video's poster the presenter shows them, with how the video is going. At
 * its foot, the idea's picture and video to show them, or the video's
 * controls and the way back to the map.
 */
@Composable
private fun Stage(
    model: MapViewModel,
    node: MapNode?,
    presentation: Presentation,
    playsMapVideos: Boolean,
    map: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(StageCorner)

    Box(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        map()
        val media = presentation.media?.takeIf { it.id == node?.id }

        AnimatedContent(
            targetState = media?.kind,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            contentAlignment = Alignment.Center,
            label = "stage",
            modifier = Modifier.fillMaxSize(),
        ) { kind ->
            if (kind != null && node != null && media != null) {
                OnStage(model, node, media, playsMapVideos)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        if (node != null && (node.image != null || node.video != null)) {
            StageButtons(model, node, media, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The picture, or the video's poster, as the audience sees it big, with the video's state and time. */
@Composable
private fun OnStage(model: MapViewModel, node: MapNode, media: ShownMedia, playsMapVideos: Boolean) {
    val item = if (media.isVideo) node.video else node.image
    val picture = item?.let { if (media.isVideo) posterOf(it) else it.src }?.let(model::mediaModel)
    // A video file on the phone can't reach a TV: the TV shows its poster.
    val notOnTv = media.isVideo && !playsMapVideos && item != null && videoSource(item, model::mediaModel) is VideoSource.InMap

    OrbitTheme(dark = true) {
        Box(Modifier.fillMaxSize().background(StageDark)) {
            if (picture != null) {
                AsyncImage(
                    model = picture,
                    contentDescription = if (media.isVideo) stringResource(R.string.present_video_of, node.label) else item.alt ?: stringResource(R.string.present_picture_of, node.label),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (media.isVideo) {
                Icon(
                    Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                if (notOnTv) {
                    StageNote(stringResource(R.string.present_video_not_on_tv))
                }

                if (media.isVideo) {
                    VideoState(media)
                }
            }
        }
    }
}

/** A word to the presenter over the stage. */
@Composable
private fun StageNote(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Whether the video plays, is paused or ended, and where it is: "Playing · 0:42 / 3:10" once its length is known. */
@Composable
private fun VideoState(media: ShownMedia) {
    val now = rememberMediaNow(media)
    val playing = media.playingAt(now)
    val ended = media.atEnd(now)
    val position = media.position(now)
    val length = media.duration
    val time = if (length != null) "${clock(position.toInt())} / ${clock(length.roundToInt())}" else clock(position.toInt())

    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.62f), contentColor = Color.White) {
        Column(Modifier.padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    when {
                        playing -> Icons.Filled.PlayArrow
                        ended -> Icons.Filled.Replay
                        else -> Icons.Filled.Pause
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(
                        when {
                            playing -> R.string.present_video_playing
                            ended -> R.string.present_video_ended
                            else -> R.string.present_video_paused
                        },
                        time,
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                )
            }

            if (length != null && length > 0) {
                val progress = { (position / length).toFloat().coerceIn(0f, 1f) }

                if (playing) {
                    WavyProgress(progress = progress, modifier = Modifier.width(StageProgressWidth))
                } else {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.width(StageProgressWidth))
                }
            }
        }
    }
}

/** [SystemClock.elapsedRealtime], again a few times a second while the video plays, for its time. */
@Composable
private fun rememberMediaNow(media: ShownMedia): Long {
    val now by produceState(SystemClock.elapsedRealtime(), media) {
        while (true) {
            value = SystemClock.elapsedRealtime()

            if (!media.playingAt(value)) {
                break
            }

            delay(MEDIA_TICK_MILLISECONDS)
        }
    }

    return now
}

/**
 * The idea's picture and video to show the audience, or, while one shows,
 * the way back to the map: with a video, it starts over, plays and pauses,
 * and with a live photo's picture, it plays its motion again. With big text
 * the buttons take two rows rather than lose their words.
 */
@Composable
private fun StageButtons(model: MapViewModel, node: MapNode, media: ShownMedia?, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StageBarRoom)
            .padding(8.dp),
    ) {
        if (media == null) {
            if (node.image != null) {
                StageButton(Icons.Outlined.Image, stringResource(R.string.common_show_the_picture)) { model.showMedia(MediaKind.Picture) }
            }

            if (node.video != null) {
                StageButton(Icons.Outlined.PlayCircle, stringResource(R.string.common_play_the_video)) { model.showMedia(MediaKind.Video) }
            }

            if (node.audio != null) {
                SoundStage(model, node)
            }

            return@FlowRow
        }

        StageButton(Icons.Outlined.Map, stringResource(R.string.present_back_to_the_map), model::closeMedia)

        if (media.isVideo) {
            val now = rememberMediaNow(media)
            val playing = media.playingAt(now)
            val startOver = stringResource(R.string.present_start_over)

            Hint(startOver) {
                FilledTonalIconButton(onClick = model::startMediaOver) {
                    Icon(Icons.Filled.Replay, contentDescription = startOver)
                }
            }

            val playOrPause = stringResource(if (playing) R.string.present_pause_the_video else R.string.common_play_the_video)

            Hint(playOrPause) {
                FilledIconButton(onClick = { model.playMedia(!playing) }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = playOrPause,
                    )
                }
            }
        } else {
            // A live photo played its motion once as it showed, on every screen: this plays it again there.
            if (node.image?.motion != null) {
                val again = stringResource(R.string.present_play_live_photo_again)

                Hint(again) {
                    FilledTonalIconButton(onClick = model::replayMotion) {
                        Icon(Icons.Filled.Replay, contentDescription = again)
                    }
                }
            }

            if (node.video != null) {
                StageButton(Icons.Outlined.PlayCircle, stringResource(R.string.common_play_the_video)) { model.showMedia(MediaKind.Video) }
            }
        }
    }
}

/**
 * The idea's sound, for the presenter: it plays from this phone, where its
 * sound goes (its speaker, headphones, or a screen on its cable), while the
 * audience's screens keep the map. Its time shows as it plays.
 */
@Composable
private fun SoundStage(model: MapViewModel, node: MapNode) {
    val sound by model.sound.collectAsStateWithLifecycle()
    val current = sound?.takeIf { it.item.nodeId == node.id }

    if (current == null) {
        StageButton(Icons.Outlined.GraphicEq, stringResource(R.string.audio_play_the_sound)) { model.toggleSound(node.id) }
        return
    }

    FilledTonalButton(
        onClick = { model.toggleSound(node.id) },
        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 3.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
    ) {
        Icon(if (current.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            "${stringResource(if (current.playing) R.string.audio_pause_the_sound else R.string.audio_play_the_sound)} · ${soundTime(current.position)} / ${soundTime(current.duration)}",
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        )
    }

    Text(stringResource(R.string.audio_plays_from_this_phone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A button on the stage, raised a little off the map or picture under it. */
@Composable
private fun StageButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 3.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1)
    }
}

/** The idea in focus with its whole note, what comes next, and the buttons to step. */
@Composable
private fun ColumnScope.Notes(model: MapViewModel, node: MapNode?, next: MapNode?, presentation: Presentation, seconds: Int) {
    // The swipes outlive this composition: they step at the pace set now, not the one when they began.
    val pace by rememberUpdatedState(seconds)

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .presentationGestures(
                rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl,
                onTap = {},
                onStep = { by -> model.step(by, pace) },
            ),
    ) {
        AnimatedContent(
            targetState = node,
            transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
            label = "notes",
        ) { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    current?.label.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                )
                val note = current?.note?.takeIf { it.isNotBlank() }

                if (note != null) {
                    MarkdownNote(note, plain = current.noteFormat == "plain", onNodeLink = { id -> model.focus(id) })
                } else {
                    Text(stringResource(R.string.present_no_notes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.present_up_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    next?.label ?: stringResource(R.string.present_the_end),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The idea's own clock: what's left of its rehearsed time, or how long it's been up.
            val now = rememberNow(presentation.stepStartedAt)
            val words = rememberWords()

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.present_this_idea), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val over = (presentation.plannedForStep ?: Int.MAX_VALUE) < presentation.onStep(now)
                Text(
                    presentation.stepTime(now, words),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    val previous = stringResource(R.string.present_previous_idea)
    val playOrPause = stringResource(if (presentation.playing) R.string.present_pause else R.string.present_play_on_its_own)
    val next = stringResource(R.string.present_next_idea)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Hint(previous, MapKeys.PreviousIdea) {
            FilledTonalIconButton(
                onClick = { model.step(-1, seconds) },
                enabled = presentation.index > 0,
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = previous)
            }
        }

        Hint(playOrPause) {
            FilledTonalIconButton(
                onClick = { model.setPlaying(!presentation.playing, seconds) },
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(if (presentation.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playOrPause)
            }
        }

        Text(stringResource(R.string.present_position, presentation.index + 1, presentation.order.size), style = MaterialTheme.typography.titleMedium)

        Hint(next, MapKeys.NextIdea) {
            FilledIconButton(
                onClick = { model.step(1, seconds) },
                enabled = presentation.index < presentation.order.lastIndex,
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = next)
            }
        }
    }
}
