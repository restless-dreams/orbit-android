package com.hyuchia.orbit.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.log10

/** The microphone's level is read this often while recording (ms): a bar of the waveform each time. */
const val RECORDER_LEVEL_MILLISECONDS = 70L

/** A take shorter than this (ms) is nothing: a tap on Finish as it starts. */
private const val SHORTEST_TAKE_MILLISECONDS = 600L

/** The recorder's sound: AAC, one channel, as a phone's voice memos are. */
private const val SAMPLING_RATE = 48_000
private const val BIT_RATE = 96_000

/** The loudest level `MediaRecorder.getMaxAmplitude` reports. */
private const val LOUDEST = 32_767.0

/**
 * What a take's file still needs when it stops, past what's in it as it
 * records: its index, about 200 bytes for each second of AAC, and what the
 * recorder still holds. A take stops this short of its limit.
 */
private const val INDEX_BYTES_A_SECOND = 256L
private const val UNWRITTEN_BYTES = 64L * 1024

/** What the recorder is doing. */
enum class RecorderPhase {
    /** Nothing recorded yet. */
    Ready,

    Recording,
    Paused,

    /** The take is whole, in its file, to listen to and keep. */
    Done,
}

/**
 * A voice memo as it's recorded: AAC from the microphone, in an `.mp4`
 * (every release of Orbit carries those in its zips, backups and synced
 * folder), into `file`, at most `limit()` bytes. It reads the microphone's
 * level as it goes, for the waveform, and pauses when another app needs the
 * phone's sound, such as a call. `scope` runs its clock.
 */
@Stable
class VoiceRecorder(private val context: Context, private val file: File, private val limit: () -> Long, private val scope: CoroutineScope) {
    var phase by mutableStateOf(RecorderPhase.Ready)
        private set

    /** The microphone's level, from 0 to 1, every [RECORDER_LEVEL_MILLISECONDS] of the take. */
    val levels = mutableStateListOf<Float>()

    /** How long the take is so far (ms). */
    var elapsed by mutableLongStateOf(0L)
        private set

    /** Why the recorder stopped, when something went wrong. */
    @get:StringRes
    var problem by mutableStateOf<Int?>(null)
        private set

    /** The take reached the most a voice memo can be, and finished there. */
    var full by mutableStateOf(false)
        private set

    /** The take, once it's done. */
    val take: File? get() = file.takeIf { phase == RecorderPhase.Done && it.isFile && it.length() > 0 }

    private var recorder: MediaRecorder? = null
    private var clock: Job? = null

    /** The take's time before it was last paused (ms), and when it went on again ([SystemClock.elapsedRealtime]). */
    private var before = 0L

    private var since = 0L
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null

    /** Starts a take, or goes on with a paused one. False when the microphone couldn't be had. */
    fun record(): Boolean {
        problem = null

        when (phase) {
            RecorderPhase.Paused -> {
                val recorder = recorder ?: return false

                return try {
                    takeFocus()
                    recorder.resume()
                    running()
                    true
                } catch (failed: IllegalStateException) {
                    fail(failed)
                    false
                }
            }
            RecorderPhase.Recording -> return true
            RecorderPhase.Ready, RecorderPhase.Done -> Unit
        }

        // A take done and then started over: the last one goes, and so do
        // takes Orbit left behind when it closed while recording.
        discard()
        file.parentFile?.mkdirs()
        file.parentFile?.listFiles { other -> other != file }?.forEach { it.delete() }
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(SAMPLING_RATE)
            recorder.setAudioEncodingBitRate(BIT_RATE)
            // The limit is kept by the clock, not `setMaxFileSize`: with it, the
            // recorder saves room for an index as big as the limit allows, 300 KB
            // of nothing in every memo.
            recorder.setOutputFile(file.path)

            recorder.setOnErrorListener { _, what, extra ->
                Logs.warn("OrbitRecorder", "The recorder stopped: $what, $extra")
                problem = R.string.audio_recording_failed
                release()
                phase = if (file.isFile && file.length() > 0) RecorderPhase.Done else RecorderPhase.Ready
            }

            recorder.prepare()
            takeFocus()
            recorder.start()
            this.recorder = recorder
            levels.clear()
            before = 0L
            elapsed = 0L
            running()
            true
        } catch (failed: IOException) {
            recorder.release()
            fail(failed)
            false
        } catch (failed: RuntimeException) {
            // The microphone is another app's, or the phone has none Orbit can use.
            recorder.release()
            fail(failed)
            false
        }
    }

    /** Pauses the take: it goes on from here with [record]. */
    fun pause() {
        val recorder = recorder ?: return

        if (phase != RecorderPhase.Recording) {
            return
        }

        try {
            recorder.pause()
        } catch (failed: IllegalStateException) {
            fail(failed)
            return
        }

        before += SystemClock.elapsedRealtime() - since
        elapsed = before
        clock?.cancel()
        phase = RecorderPhase.Paused
        giveFocus()
    }

    /** Ends the take, whole in its file. One too short to hear is nothing, and goes. */
    fun finish() {
        val recorder = recorder ?: return

        if (phase == RecorderPhase.Recording) {
            before += SystemClock.elapsedRealtime() - since
        }

        elapsed = before
        clock?.cancel()
        this.recorder = null

        val kept = try {
            recorder.stop()
            elapsed >= SHORTEST_TAKE_MILLISECONDS
        } catch (nothing: RuntimeException) {
            // Stopped before the recorder had anything: there's no take.
            false
        } finally {
            recorder.release()
            giveFocus()
        }

        if (kept && file.isFile && file.length() > 0) {
            phase = RecorderPhase.Done
        } else {
            discard()
        }
    }

    /** Throws the take away. */
    fun discard() {
        release()
        file.delete()
        levels.clear()
        elapsed = 0L
        before = 0L
        full = false
        phase = RecorderPhase.Ready
    }

    /** Lets go of the microphone, keeping a take that's done. */
    fun release() {
        clock?.cancel()
        clock = null

        recorder?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }

        recorder = null
        giveFocus()
    }

    private fun running() {
        since = SystemClock.elapsedRealtime()
        phase = RecorderPhase.Recording
        clock?.cancel()

        clock = scope.launch {
            while (isActive) {
                delay(RECORDER_LEVEL_MILLISECONDS)
                elapsed = before + SystemClock.elapsedRealtime() - since
                levels.add(level(recorder?.let { runCatching { it.maxAmplitude }.getOrDefault(0) } ?: 0))

                if (file.length() + elapsed / 1000 * INDEX_BYTES_A_SECOND + UNWRITTEN_BYTES >= limit()) {
                    full = true
                    finish()
                }
            }
        }
    }

    /** The phone's sound, for the take: music and other sounds pause, and a call pauses the take. */
    private fun takeFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change < 0) {
                    pause()
                }
            }
            .build()

        focus = request
        audio?.requestAudioFocus(request)
    }

    private fun giveFocus() {
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun fail(failed: Exception) {
        Logs.warn("OrbitRecorder", "Couldn't record", failed)
        problem = R.string.audio_recording_failed
        clock?.cancel()
        recorder?.let { runCatching { it.release() } }
        recorder = null
        giveFocus()
        phase = RecorderPhase.Ready
        file.delete()
    }
}

/** A level the waveform draws, from 0 to 1, for an amplitude from the microphone: on a logarithmic scale, as ears hear. */
internal fun level(amplitude: Int): Float {
    if (amplitude <= 0) {
        return 0f
    }

    // From the noise of a quiet room (about 1/300 of the loudest) to the loudest.
    val decibels = 20 * log10(amplitude / LOUDEST)
    return ((decibels + 50) / 50).toFloat().coerceIn(0f, 1f)
}

/**
 * `levels` as `count` bars, for a take drawn across the width it has: each
 * bar the loudest of the levels it stands for, so no word is lost, and a
 * short take's levels spread over several bars each.
 */
internal fun barsOf(levels: List<Float>, count: Int): List<Float> {
    if (count <= 0 || levels.isEmpty()) {
        return emptyList()
    }

    if (levels.size <= count) {
        return List(count) { bar -> levels[bar * levels.size / count] }
    }

    return List(count) { bar ->
        val from = bar * levels.size / count
        val to = maxOf(from + 1, (bar + 1) * levels.size / count)
        levels.subList(from, to).max()
    }
}
