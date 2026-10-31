package com.hyuchia.orbit.map

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.VIDEO_SIZE_LIMIT
import com.hyuchia.orbit.data.mediaRoom
import com.hyuchia.orbit.data.megabytes
import com.hyuchia.orbit.media.RecorderPhase
import com.hyuchia.orbit.media.SoundPlayButton
import com.hyuchia.orbit.media.VoiceRecorder
import com.hyuchia.orbit.media.barsOf
import com.hyuchia.orbit.media.soundTime
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Each bar of the waveform, and the room after it. */
private val BarWidth = 3.dp
private val BarGap = 2.5.dp

/** A screen this short has the recorder's waveform beside its buttons, which take this much of its width. */
private val ShortRecorder = 520.dp
private val ShortRecorderControls = 360.dp

/** The take's buttons, half a phone's width each, keep less room around their words than Material's: "Grabar de nuevo" fits beside its icon. */
private val TakeButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/** The least room a map needs left for a voice memo (bytes): about 20 seconds of one. */
private const val SMALLEST_MEMO = 256L * 1024

/**
 * The recorder for a voice memo on `node`: a big button that records,
 * pauses and goes on, the waveform of what's said as it's said, its time,
 * and once it's finished, the take to listen to before saving it, or to
 * record again. Leaving Orbit pauses it, and so does a call. Closing with a
 * take asks first. The memo joins the idea with Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderSheet(model: MapViewModel, node: MapNode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalAppSettings.current.haptics
    val scope = rememberCoroutineScope()
    val file = remember { File(File(context.cacheDir, "recordings"), "take-${System.currentTimeMillis()}.mp4") }
    // What the map's pictures, videos and sounds come to: a memo may take the room that's left.
    val used = produceState<Long?>(null) { value = model.mediaSize() }
    val room = used.value?.let(::mediaRoom)
    val mapFull = room != null && room < SMALLEST_MEMO
    val recorder = remember { VoiceRecorder(context.applicationContext, file, { used.value?.let(::mediaRoom) ?: 0L }, scope) }
    val words = rememberWords()
    val take = remember { TakePlayer(scope) }
    var asking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<ShownText?>(null) }
    // The microphone was refused: Settings is the way to allow it now.
    var refused by remember { mutableStateOf(false) }
    val hasMicrophone = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) }
    val holdsTake = recorder.phase != RecorderPhase.Ready

    fun close() {
        take.release()
        recorder.discard()
        onDismiss()
    }

    val state = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { value ->
            // A swipe down with a take asks first.
            if (value == SheetValue.Hidden && (recorder.phase != RecorderPhase.Ready || saving)) {
                recorder.pause()
                asking = !saving
                false
            } else {
                true
            }
        },
    )

    DisposableEffect(recorder) {
        onDispose {
            take.release()
            recorder.release()
        }
    }

    // Leaving Orbit pauses the take: the microphone isn't kept in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        recorder.pause()
        take.pause()
    }

    LaunchedEffect(recorder.phase) {
        if (recorder.phase == RecorderPhase.Done) {
            recorder.take?.let(take::load)
        } else {
            take.release()
        }
    }

    fun feel() {
        if (haptics) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun start() {
        problem = null

        if (recorder.record()) {
            feel()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            refused = false
            start()
        } else {
            refused = true
        }
    }

    fun record() {
        when {
            recorder.phase == RecorderPhase.Recording -> {
                recorder.pause()
                feel()
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (holdsTake) {
                recorder.pause()
                asking = true
            } else {
                close()
            }
        },
        sheetState = state,
    ) {
        EscapeCloses {
            if (holdsTake) {
                recorder.pause()
                asking = true
            } else {
                close()
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val short = maxHeight < ShortRecorder && maxWidth > maxHeight

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.audio_voice_memo), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.audio_recorder_for, node.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val closeLabel = stringResource(R.string.common_close)

                    Hint(closeLabel) {
                        IconButton(onClick = { if (holdsTake) asking = true else close() }) { Icon(Icons.Rounded.Close, contentDescription = closeLabel) }
                    }
                }

                Spacer(Modifier.height(if (short) 8.dp else 20.dp))

                val stage = @Composable { modifier: Modifier ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
                        Waveform(
                            levels = recorder.levels,
                            phase = recorder.phase,
                            progress = if (recorder.phase == RecorderPhase.Done) take.progress else null,
                            color = if (recorder.phase == RecorderPhase.Recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (short) 84.dp else 112.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        Spacer(Modifier.height(16.dp))
                        RecorderClock(recorder, take, short)
                    }
                }

                val controls = @Composable { modifier: Modifier ->
                    Box(modifier, contentAlignment = Alignment.Center) {
                        if (recorder.phase == RecorderPhase.Done) {
                            TakeControls(
                                take = take,
                                ready = recorder.take != null,
                                saving = saving,
                                onAgain = {
                                    take.release()
                                    recorder.discard()
                                    record()
                                },
                                onSave = {
                                    val recorded = recorder.take ?: return@TakeControls
                                    take.release()
                                    saving = true

                                    scope.launch {
                                        val refusal = model.saveRecording(node.id, recorded)
                                        saving = false
                                        problem = refusal
                                    }
                                },
                            )
                        } else {
                            RecordingControls(
                                recorder = recorder,
                                enabled = hasMicrophone && room != null && (!mapFull || holdsTake),
                                onRecord = ::record,
                                onDiscard = { asking = true },
                                onFinish = {
                                    recorder.finish()
                                    feel()
                                },
                            )
                        }
                    }
                }

                if (short) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        stage(Modifier.weight(1f))
                        controls(Modifier.width(ShortRecorderControls))
                    }
                } else {
                    stage(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    controls(Modifier.fillMaxWidth())
                }

                RecorderProblem(
                    message = when {
                        !hasMicrophone -> stringResource(R.string.audio_no_microphone)
                        refused -> stringResource(R.string.audio_needs_the_microphone)
                        recorder.full && room != null && room < VIDEO_SIZE_LIMIT -> stringResource(R.string.audio_map_room_reached)
                        recorder.full -> stringResource(R.string.audio_longest_memo)
                        mapFull && !holdsTake -> stringResource(R.string.audio_map_full, megabytes(used.value ?: 0L, words))
                        else -> recorder.problem?.let { stringResource(it) } ?: problem?.text()
                    },
                    action = if (refused && hasMicrophone) stringResource(R.string.common_settings) else null,
                    onAction = { openAppSettings(context) },
                )
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.audio_discard_title)) },
            text = { Text(stringResource(R.string.audio_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    close()
                }) { Text(stringResource(R.string.audio_discard)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.audio_keep_it)) } },
        )
    }
}

/** The take's time, big, and what the recorder is doing, for everyone and for TalkBack. */
@Composable
private fun RecorderClock(recorder: VoiceRecorder, take: TakePlayer, short: Boolean) {
    val phase = recorder.phase
    val style = if (short) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium
    val listening = phase == RecorderPhase.Done && take.loaded
    val length = MaterialTheme.colorScheme.onSurfaceVariant

    // Listening back, where the take is, and how long it is, quieter.
    val time = if (listening) {
        buildAnnotatedString {
            append(soundTime(take.position))

            withStyle(SpanStyle(color = length, fontSize = style.fontSize * 0.5f)) {
                append(" / ${soundTime(take.duration)}")
            }
        }
    } else {
        AnnotatedString(soundTime(recorder.elapsed))
    }

    val spoken = if (listening) stringResource(R.string.audio_time_of, soundTime(take.position), soundTime(take.duration)) else time.text

    Text(
        time,
        style = style.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { contentDescription = spoken },
    )

    val status = when (phase) {
        RecorderPhase.Ready -> stringResource(R.string.audio_tap_to_record)
        RecorderPhase.Recording -> stringResource(R.string.audio_recording)
        RecorderPhase.Paused -> stringResource(R.string.audio_paused)
        RecorderPhase.Done -> stringResource(R.string.audio_ready_to_save)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (phase == RecorderPhase.Recording || phase == RecorderPhase.Paused) {
            RecordingDot(on = phase == RecorderPhase.Recording)
        }

        Text(status, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The red light of a recorder: it breathes while recording, and dims when paused. */
@Composable
private fun RecordingDot(on: Boolean) {
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(on) {
        if (on) {
            while (isActive) {
                pulse.animateTo(0.35f, tween(700))
                pulse.animateTo(1f, tween(700))
            }
        } else {
            pulse.snapTo(0.45f)
        }
    }

    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { alpha = pulse.value }
            .background(MaterialTheme.colorScheme.error, CircleShape),
    )
}

/** Throw it away, the big button that records and pauses, and finish. */
@Composable
private fun RecordingControls(recorder: VoiceRecorder, enabled: Boolean, onRecord: () -> Unit, onDiscard: () -> Unit, onFinish: () -> Unit) {
    val holds = recorder.phase != RecorderPhase.Ready

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        val discard = stringResource(R.string.audio_discard)
        val finish = stringResource(R.string.audio_finish_recording)

        Hint(discard) {
            OutlinedIconButton(onClick = onDiscard, enabled = holds, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = discard)
            }
        }

        RecordButton(
            phase = recorder.phase,
            level = recorder.levels.lastOrNull() ?: 0f,
            enabled = enabled,
            onClick = onRecord,
        )

        Hint(finish) {
            FilledTonalIconButton(onClick = onFinish, enabled = holds, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = finish)
            }
        }
    }
}

/**
 * The recorder's big button, in Material's expressive shapes: a circle to
 * record, which becomes a soft nine-sided cookie that turns slowly and grows
 * with the voice while it records, and a circle again when paused.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordButton(phase: RecorderPhase, level: Float, enabled: Boolean, onClick: () -> Unit, size: Dp = 96.dp) {
    val recording = phase == RecorderPhase.Recording
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val shapeProgress by animateFloatAsState(if (recording) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "shape")
    val voice by animateFloatAsState(if (recording) level else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "voice")
    val turn = remember { Animatable(0f) }
    val color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val content = if (enabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    LaunchedEffect(recording) {
        while (recording) {
            turn.animateTo(turn.value + 360f, tween(9_000, easing = LinearEasing))
        }
    }

    val name = stringResource(
        when (phase) {
            RecorderPhase.Recording -> R.string.audio_pause_recording
            RecorderPhase.Paused -> R.string.audio_resume_recording
            else -> R.string.audio_record
        },
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = name, onClick = onClick)
            .semantics { contentDescription = name },
    ) {
        Box(
            Modifier
                .size(size * 0.92f)
                .graphicsLayer {
                    rotationZ = turn.value
                    val grow = 1f + voice * 0.1f
                    scaleX = grow
                    scaleY = grow
                }
                .background(color, MorphShape(morph) { shapeProgress }),
        )

        AnimatedContent(
            targetState = recording,
            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
            label = "icon",
        ) { shown ->
            Icon(if (shown) Icons.Rounded.Pause else Icons.Rounded.Mic, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** A shape between two of Material's, `progress` of the way. */
private class MorphShape(private val morph: Morph, private val progress: () -> Float) : Shape {
    private val path = Path()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        path.rewind()
        morph.toPath(progress(), path)
        // Material's shapes fit a unit square.
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * The take to listen to, where the record button was, and under it, record
 * again or keep it. Keeping it doesn't wait on the player: a take the phone's
 * player can't open is still the recorder's file.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TakeControls(take: TakePlayer, ready: Boolean, saving: Boolean, onAgain: () -> Unit, onSave: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SoundPlayButton(playing = take.playing, waiting = false, onClick = take::toggle, size = 88.dp, enabled = take.loaded)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onAgain, enabled = !saving, contentPadding = TakeButtonPadding, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.audio_record_again), textAlign = TextAlign.Center)
            }

            Button(onClick = onSave, enabled = ready && !saving, contentPadding = TakeButtonPadding, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                if (saving) {
                    val label = stringResource(R.string.audio_saving)
                    LoadingIndicator(Modifier.size(24.dp).semantics { contentDescription = label }, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.audio_save), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun RecorderProblem(message: String?, action: String?, onAction: () -> Unit) {
    if (message == null) {
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(Icons.Outlined.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))

        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * The take's waveform: a bar for each moment of it, the newest at the end,
 * moving along while it records. Once it's done, the whole take fits, and
 * the part listened to takes the color.
 */
@Composable
private fun Waveform(levels: List<Float>, phase: RecorderPhase, progress: Float?, color: Color, modifier: Modifier = Modifier) {
    // Room for what's still to be said, and a take's part not listened to yet.
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val ahead = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Canvas(modifier) {
        val bar = BarWidth.toPx()
        val step = bar + BarGap.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val middle = size.height / 2
        val shown = if (phase == RecorderPhase.Done) barsOf(levels, count) else levels.takeLast(count)
        // While recording, the newest bar is at the end, and the rest come in from it.
        val start = if (phase == RecorderPhase.Done) 0 else count - shown.size

        for (index in 0 until count) {
            val level = shown.getOrNull(index - start)
            val x = index * step
            val height = if (level == null) bar else maxOf(bar, level * size.height)
            val played = progress == null || index < count * progress

            val tint = when {
                level == null -> quiet
                phase == RecorderPhase.Done && !played -> ahead
                else -> color
            }

            drawRoundRect(tint, topLeft = Offset(x, middle - height / 2), size = Size(bar, height), cornerRadius = CornerRadius(bar / 2))
        }
    }
}

/** The take, to listen to before keeping it: the phone's own player, enough for a file of its own. */
@Stable
private class TakePlayer(private val scope: CoroutineScope) {
    var playing by mutableStateOf(false)
        private set

    var position by mutableLongStateOf(0L)
        private set

    var duration by mutableLongStateOf(0L)
        private set

    val loaded: Boolean get() = player != null
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    private var player by mutableStateOf<MediaPlayer?>(null)
    private var clock: Job? = null

    fun load(file: File) {
        release()

        val player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.path)
                prepare()
            }
        } catch (unreadable: Exception) {
            return
        }

        player.setOnCompletionListener {
            playing = false
            position = duration
            clock?.cancel()
        }

        this.player = player
        duration = player.duration.toLong().coerceAtLeast(0)
        position = 0
    }

    fun toggle() {
        val player = player ?: return

        if (player.isPlaying) {
            pause()
            return
        }

        if (position >= duration) {
            player.seekTo(0)
        }

        player.start()
        playing = true
        clock?.cancel()

        clock = scope.launch {
            while (isActive) {
                position = player.currentPosition.toLong()
                delay(80)
            }
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        playing = false
        clock?.cancel()
    }

    fun release() {
        clock?.cancel()
        player?.release()
        player = null
        playing = false
        position = 0
        duration = 0
    }
}
