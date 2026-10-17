package com.hyuchia.orbit.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyuchia.orbit.R
import com.hyuchia.orbit.voice.Hearing
import com.hyuchia.orbit.voice.Listening
import com.hyuchia.orbit.voice.hearing
import com.hyuchia.orbit.voice.ideasFrom
import com.hyuchia.orbit.voice.listeningScreen

/** Slid this far up while holding the +, a talk keeps listening once it's let go. */
private val SlideToKeepListening = 72.dp

/**
 * A talk: ideas said aloud, each joining the orbit in focus as it's said.
 * Holding the + talks while it's held; sliding up keeps it listening,
 * hands-free, until Done.
 */
@Stable
class Talk internal constructor(private val listening: Listening) {
    /** A talk is going on, or finishing its last idea. */
    var active by mutableStateOf(false)
        internal set

    /** It listens until Done, rather than until the + is let go. */
    var kept by mutableStateOf(false)
        internal set

    /** Done, or the + let go: the idea being said is the last. */
    var stopping by mutableStateOf(false)
        internal set

    /** How many ideas joined the map in this talk. */
    var count by mutableIntStateOf(0)
        internal set

    val heard get() = listening.heard

    /** Begins a talk, if this phone can listen here and now; false when it went another way. */
    internal var begin: () -> Boolean = { false }

    /** Hears that the talk is over, with the reason when something went wrong. */
    internal var onFinished: (problem: String?) -> Unit = {}

    /** What TalkBack says a long press on the + does, in the screen's language. */
    internal var holdLabel = ""

    /** The + is held. */
    fun hold(): Boolean = begin()

    /** Slid up while holding: listen on after the + is let go. */
    fun keep() {
        kept = true
    }

    /** The + is let go: the talk ends, unless it's kept. */
    fun release() {
        if (!kept) {
            stop()
        }
    }

    fun stop() {
        if (active && !stopping) {
            stopping = true
            listening.stop()
        }
    }

    internal fun start(keep: Boolean) {
        count = 0
        kept = keep
        stopping = false
        active = true
        listening.start()
    }

    internal fun finished(problem: String?) {
        active = false
        stopping = false
        onFinished(problem)
    }

    internal fun close() = listening.close()
}

/**
 * Talking onto `model`'s map. Where the phone has a speech service, Orbit
 * listens itself, idea after idea; where it only has another app's
 * listening screen, that hears one go; and where it has neither, as on many
 * phones without Google, the new idea's field opens for the keyboard's
 * microphone. `onMessage` shows a message, with an action when it has one.
 */
@Composable
fun rememberTalk(model: MapViewModel, onMessage: (String, String?, (() -> Unit)?) -> Unit): Talk {
    val context = LocalContext.current

    val talk = remember(model) {
        lateinit var created: Talk
        val listening = Listening(
            context.applicationContext,
            onIdeas = { ideas ->
                created.count += ideas.size
                model.addSpoken(ideas)
            },
            onFinished = { problem -> created.finished(problem) },
        )
        Talk(listening).also { created = it }
    }

    DisposableEffect(talk) { onDispose { talk.close() } }
    // Leaving the app ends a talk: the microphone isn't kept open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { talk.stop() }

    fun start(keep: Boolean) {
        model.startTalking()
        talk.start(keep)
    }

    val needsTheMicrophone = stringResource(R.string.present_talk_needs_the_microphone)
    val settings = stringResource(R.string.common_settings)
    val noSpeechService = stringResource(R.string.present_talk_no_speech_service)
    talk.holdLabel = stringResource(R.string.present_talk_ideas_onto_the_map)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // The hold that asked is over by now: listen hands-free, as the + was held to.
            start(keep = true)
        } else {
            onMessage(needsTheMicrophone, settings) { openAppSettings(context) }
        }
    }

    val screen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val ideas = said?.let { ideasFrom(it) }.orEmpty()

        if (ideas.isNotEmpty()) {
            model.addSpoken(ideas)
        }

        model.stopTalking()
    }

    talk.begin = {
        when (hearing(context)) {
            Hearing.Service -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    start(keep = false)
                    true
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                    false
                }
            }
            Hearing.Screen -> {
                model.startTalking()
                screen.launch(listeningScreen(context))
                false
            }
            Hearing.None -> {
                model.addIdea()
                onMessage(noSpeechService, null, null)
                false
            }
        }
    }

    // The talk is over once its last idea is in, or when the speech service gave up.
    talk.onFinished = { problem ->
        model.stopTalking()
        problem?.let { onMessage(it, null, null) }
    }

    return talk
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Holding this talks through `talk`: a tap stays the element's own, a hold
 * talks while it lasts, and sliding up while holding keeps the talk going
 * after letting go. TalkBack's long press starts a talk that's kept.
 * `onHold` tells the hand the hold was felt.
 */
fun Modifier.holdToTalk(talk: Talk, onHold: () -> Unit): Modifier = this
    .semantics {
        onLongClick(label = talk.holdLabel) {
            if (talk.hold()) {
                talk.keep()
            }

            true
        }
    }
    .pointerInput(talk) {
        val keepDistance = SlideToKeepListening.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var lifted = false

            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
                lifted = true
            }

            if (lifted) {
                return@awaitEachGesture
            }

            onHold()
            val talking = talk.hold()
            var kept = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // The hold is the talk's: what's held doesn't take it for a tap.
                change.consume()

                if (talking && !kept && down.position.y - change.position.y > keepDistance) {
                    kept = true
                    talk.keep()
                }

                if (!change.pressed) {
                    break
                }
            }

            if (talking && !kept) {
                talk.release()
            }
        }
    }

/**
 * The talk as it goes, floating over the map: a microphone that swells with
 * the voice, the words being said, how many ideas joined, and Done when the
 * talk is kept going hands-free.
 */
@Composable
fun TalkPanel(talk: Talk, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val heard by talk.heard.collectAsState()

    AnimatedVisibility(
        visible = talk.active,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            ) {
                val level by animateFloatAsState(heard.level, label = "level")

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .graphicsLayer {
                                scaleX = 0.8f + level * 0.45f
                                scaleY = 0.8f + level * 0.45f
                                alpha = 0.3f
                            }
                            .background(colors.primary, CircleShape),
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.primary, CircleShape),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = colors.onPrimary)
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        heard.words.ifBlank {
                            when {
                                talk.stopping -> stringResource(R.string.present_talk_adding_the_last_idea)
                                talk.count == 0 -> stringResource(R.string.present_talk_say_an_idea)
                                else -> stringResource(R.string.present_talk_listening)
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            talk.kept -> pluralStringResource(R.plurals.present_talk_ideas_listening_until_done, talk.count, talk.count)
                            talk.count == 0 -> stringResource(R.string.present_talk_let_go_or_slide_up)
                            else -> pluralStringResource(R.plurals.present_talk_ideas_let_go_to_finish, talk.count, talk.count)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                if (talk.kept) {
                    Button(onClick = talk::stop, enabled = !talk.stopping) { Text(stringResource(R.string.common_done)) }
                }
            }
        }
    }
}
