package com.hyuchia.orbit.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** A voice memo's take, as it plays: speech, among the phone's media. */
private val TakeSound: AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

/**
 * The take, to listen to before keeping it: the phone's own player, enough
 * for a file of its own. While it plays it has the phone's sound, so music
 * pauses for it and comes back after, and it pauses when another app takes
 * the sound, as a call or directions do, and when headphones come out, so
 * it never goes on out loud. `scope` runs its clock.
 */
@Stable
internal class TakePlayer(private val context: Context, private val scope: CoroutineScope) {
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
    private val audio = context.getSystemService(AudioManager::class.java)

    /** The phone's sound, while the take plays. */
    private var focus: AudioFocusRequest? = null

    /** Hears headphones coming out, while the take plays. */
    private var unplugging: BroadcastReceiver? = null

    fun load(file: File) {
        release()

        val player = try {
            MediaPlayer().apply {
                setAudioAttributes(TakeSound)
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
            letGoOfTheSound()
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

        // While a call has the phone's sound, the take waits.
        if (!takeTheSound()) {
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
        letGoOfTheSound()
    }

    fun release() {
        clock?.cancel()
        letGoOfTheSound()
        player?.release()
        player = null
        playing = false
        position = 0
        duration = 0
    }

    /**
     * Keeps the take with `save`: the player lets go of it meanwhile, and
     * has it again, to listen to and save again, when it couldn't be kept.
     * Returns why not, or null once it's kept.
     */
    suspend fun keep(take: File, save: suspend (File) -> ShownText?): ShownText? {
        release()
        val refusal = save(take)

        if (refusal != null) {
            load(take)
        }

        return refusal
    }

    /** Asks for the phone's sound, and hears headphones coming out: false while another app can't share it, as in a call. */
    private fun takeTheSound(): Boolean {
        if (focus == null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(TakeSound)
                // Words are lost under another sound: the take pauses rather than playing on quieter.
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { change ->
                    if (change < 0) {
                        pause()
                    }
                }
                .build()

            if (audio != null && audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return false
            }

            focus = request
        }

        if (unplugging == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        pause()
                    }
                }
            }

            ContextCompat.registerReceiver(context, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            unplugging = receiver
        }

        return true
    }

    /** Gives the phone's sound back, for what paused for the take to go on. */
    private fun letGoOfTheSound() {
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        unplugging?.let { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        unplugging = null
    }
}
