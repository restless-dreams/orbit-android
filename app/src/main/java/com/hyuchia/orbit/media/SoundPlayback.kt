package com.hyuchia.orbit.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.hyuchia.orbit.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** The buttons and the J and L keys go this far back or on (ms). */
const val SOUND_SKIP_MILLISECONDS = 10_000L

/** While a sound plays, where it is is told this often (ms): enough for a slider and a clock to move smoothly. */
private const val SOUND_TICK_MILLISECONDS = 120L

/** An idea's sound, as it plays. */
data class SoundState(
    /** Whose sound it is, and the sound. */
    val item: GalleryItem,

    val playing: Boolean = false,

    /** Waiting for more of itself, from the web. */
    val waiting: Boolean = false,

    /** Where it is, from its start (ms). */
    val position: Long = 0,

    /** How long it is (ms), once that's known. */
    val duration: Long? = null,

    val ended: Boolean = false,
    val muted: Boolean = false,

    /** It can't be played: a file that isn't there any more, or one the phone can't read. */
    val failed: Boolean = false,
) {
    /** How far along it is, from 0 to 1. */
    val progress: Float get() = duration?.takeIf { it > 0 }?.let { (position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}

/** Where a sound plays from: a file in the map's folder, or an address on the web. */
internal sealed interface SoundSource {
    data class InFolder(val file: File) : SoundSource

    data class OnTheWeb(val address: String) : SoundSource
}

/**
 * What [SoundPlayback] plays its sounds with: Media3's ExoPlayer on the
 * phone ([MediaSoundPlayer]), and a stand-in in tests. It tells its
 * [SoundPlayer.Events] as it changes.
 */
internal interface SoundPlayer {
    val playing: Boolean

    /** Waiting for more of the sound, from the web. */
    val waiting: Boolean

    val ended: Boolean

    /** Where it is, from the sound's start (ms). */
    val position: Long

    /** How long the sound is (ms), once that's known. */
    val duration: Long?

    var volume: Float

    /** Readies `source` from `from` ms, in place of the sound it had. */
    fun load(source: SoundSource, from: Long)

    fun play()

    fun pause()

    fun seekTo(position: Long)

    /** Stops, and lets go of its sound: nothing plays until another is loaded. */
    fun stop()

    fun release()

    /** What a player tells as it changes. */
    interface Events {
        fun playingChanged(playing: Boolean)

        /** Anything else of what it says: whether it's waiting or ended, its volume. */
        fun changed()

        /** Its sound can't be played. */
        fun failed()
    }
}

/**
 * The map's sounds, one at a time, in Media3's ExoPlayer: the player is made
 * the first time a sound plays, and let go with the map. A sound goes on
 * while the map is flown around, its panels open and close and the viewer
 * comes and goes; it pauses for the phone's other sounds (audio focus) and
 * when headphones come out. Starting another sound stops the one before,
 * whether the new one plays or not. `resolve` says what a sound's address
 * is: a file in the map's folder, or a web address (an http one over https).
 */
class SoundPlayback internal constructor(
    private val scope: CoroutineScope,
    private val resolve: (String) -> Any?,
    private val newPlayer: (SoundPlayer.Events) -> SoundPlayer,
) : SoundControls {
    constructor(context: Context, scope: CoroutineScope, resolve: (String) -> Any?) : this(scope, resolve, { events -> MediaSoundPlayer(context, events) })

    private val state = MutableStateFlow<SoundState?>(null)

    /** The sound playing or paused, or null when there's none. */
    val sound: StateFlow<SoundState?> = state.asStateFlow()

    private var player: SoundPlayer? = null
    private var ticker: Job? = null

    private val events = object : SoundPlayer.Events {
        override fun playingChanged(playing: Boolean) {
            refresh()
            tick(playing)
        }

        override fun changed() {
            refresh()
        }

        override fun failed() {
            state.update { it?.copy(playing = false, waiting = false, failed = true) }
        }
    }

    /**
     * Plays `item`: from where it was when it's the sound already here, or
     * from `from` ms when given, else from its start. Another sound stops the
     * one before first, so a sound that can't play never leaves the last one
     * playing under its "can't be played".
     */
    override fun play(item: GalleryItem, from: Long?) {
        val current = state.value

        if (current != null && current.item.key == item.key && current.item.media.src == item.media.src && !current.failed) {
            // The same sound: the item may have a new caption or name.
            state.update { it?.copy(item = item) }
            val player = player ?: return

            if (from != null) {
                player.seekTo(from)
            } else if (player.ended) {
                player.seekTo(0)
            }

            player.play()
            return
        }

        silence()
        val source = sourceOf(item.media.src)
        state.value = SoundState(item, failed = source == null)

        if (source == null) {
            return
        }

        val player = player ?: newPlayer(events).also { player = it }
        player.load(source, from ?: 0L)
        player.play()
    }

    /** Plays `item` if it isn't playing, and pauses it if it is. */
    override fun toggle(item: GalleryItem) {
        val current = state.value

        if (current != null && current.item.key == item.key && current.playing) {
            pause()
        } else {
            play(item)
        }
    }

    override fun pause() {
        player?.pause()
    }

    /** Goes on with the sound here, from its start once it's over. */
    fun resume() {
        state.value?.item?.let { play(it) }
    }

    override fun seekTo(position: Long) {
        val player = player ?: return
        val duration = state.value?.duration
        player.seekTo(position.coerceIn(0, duration ?: Long.MAX_VALUE))
        refresh()
    }

    /** Goes `by` ms back (below 0) or on. */
    override fun skip(by: Long) = seekTo((state.value?.position ?: 0) + by)

    override fun toggleMute() {
        val player = player ?: return
        player.volume = if (player.volume > 0f) 0f else 1f
    }

    /** Stops the sound and lets it go: nothing plays, and the small player goes. */
    fun stop() {
        silence()
        state.value = null
    }

    /** Lets go of the player, with the map. */
    fun release() {
        stop()
        player?.release()
        player = null
    }

    /** Nothing plays, and the player keeps no sound: what it said of the one it had goes into that one's state. */
    private fun silence() {
        tick(false)
        player?.stop()
    }

    private fun sourceOf(src: String): SoundSource? = when (val source = resolve(src)) {
        is File -> source.takeIf { it.isFile }?.let(SoundSource::InFolder)
        is String -> source.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(SoundSource::OnTheWeb)
        else -> null
    }

    /** What the player says now, into the state. */
    private fun refresh() {
        val player = player ?: return

        state.update { current ->
            current?.copy(
                playing = player.playing,
                waiting = player.waiting,
                position = player.position,
                duration = player.duration,
                ended = player.ended,
                muted = player.volume == 0f,
            )
        }
    }

    /** While it plays, where it is moves on in the state. */
    private fun tick(playing: Boolean) {
        ticker?.cancel()
        ticker = null

        if (playing) {
            ticker = scope.launch {
                while (isActive) {
                    refresh()
                    delay(SOUND_TICK_MILLISECONDS)
                }
            }
        }
    }
}

/**
 * Sounds in Media3's ExoPlayer: with the phone's audio focus, as music is,
 * pausing when headphones come out.
 */
@OptIn(UnstableApi::class)
private class MediaSoundPlayer(context: Context, events: SoundPlayer.Events) : SoundPlayer {
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = events.playingChanged(isPlaying)

        override fun onPlaybackStateChanged(playbackState: Int) = events.changed()

        override fun onVolumeChanged(volume: Float) = events.changed()

        override fun onPlayerError(error: PlaybackException) {
            Logs.warn("OrbitSound", "The sound couldn't be played: ${error.errorCodeName}")
            events.failed()
        }
    }

    private val player: ExoPlayer = run {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(SOUND_SKIP_MILLISECONDS)
            .setSeekForwardIncrementMs(SOUND_SKIP_MILLISECONDS)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(listener) }
    }

    override val playing: Boolean get() = player.isPlaying
    override val waiting: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override val ended: Boolean get() = player.playbackState == Player.STATE_ENDED
    override val position: Long get() = player.currentPosition.coerceAtLeast(0)
    override val duration: Long? get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }

    override var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    override fun load(source: SoundSource, from: Long) {
        val uri = when (source) {
            is SoundSource.InFolder -> Uri.fromFile(source.file)
            is SoundSource.OnTheWeb -> source.address.toUri()
        }

        player.setMediaItem(MediaItem.fromUri(uri), from)
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(position: Long) = player.seekTo(position)

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun release() {
        player.removeListener(listener)
        player.release()
    }
}
