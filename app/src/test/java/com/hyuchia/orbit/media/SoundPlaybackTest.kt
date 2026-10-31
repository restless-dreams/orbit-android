package com.hyuchia.orbit.media

import com.hyuchia.orbit.page.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SoundPlaybackTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** A player that plays nothing aloud, and tells what it's asked as ExoPlayer would. */
    private class StandInPlayer(private val events: SoundPlayer.Events) : SoundPlayer {
        var loaded: SoundSource? = null
        var released = false

        override var playing = false
        override val waiting = false
        override var ended = false
        override var position = 0L
        override val duration: Long? get() = loaded?.let { 60_000L }
        override var volume = 1f

        override fun load(source: SoundSource, from: Long) {
            loaded = source
            position = from
            ended = false
            events.changed()
        }

        override fun play() {
            if (loaded != null && !playing) {
                playing = true
                events.playingChanged(true)
            }
        }

        override fun pause() {
            if (playing) {
                playing = false
                events.playingChanged(false)
            }
        }

        override fun seekTo(position: Long) {
            this.position = position
        }

        override fun stop() {
            loaded = null
            pause()
            events.changed()
        }

        override fun release() {
            released = true
        }

        /** The sound is there, but the phone can't read it. */
        fun fail() {
            playing = false
            events.playingChanged(false)
            events.failed()
        }
    }

    private val players = mutableListOf<StandInPlayer>()
    private val player: StandInPlayer get() = players.single()

    private val folder: File by lazy { temporary.newFolder("trip") }

    private fun sound(nodeId: String, src: String) = GalleryItem(nodeId, MediaKind.Sound, Media(src), nodeId)

    private fun playback(scope: CoroutineScope) = SoundPlayback(
        scope,
        resolve = { src -> if (src.startsWith("https://")) src else File(folder, src) },
        newPlayer = { events -> StandInPlayer(events).also { players += it } },
    )

    private val fado = sound("alfama", "media/sound-fado.mp3")
    private val bells = sound("belem", "media/sound-bells.ogg")
    private val gone = sound("tram", "media/memo-gone.mp4")

    private fun keep(item: GalleryItem) {
        File(folder, item.media.src).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(8))
    }

    @Test
    fun `a sound that can't play stops the one playing, and says it can't`() = runTest {
        keep(fado)
        val sounds = playback(backgroundScope)
        sounds.play(fado)
        runCurrent()
        assertTrue(player.playing)

        // Its file isn't there any more.
        sounds.play(gone)
        advanceTimeBy(1_000)

        assertFalse(player.playing)
        assertNull(player.loaded)
        val state = sounds.sound.value!!
        assertEquals(gone, state.item)
        assertTrue(state.failed)
        // Nothing ticks on to say otherwise.
        assertFalse(state.playing)
        assertEquals(0L, state.position)
    }

    @Test
    fun `a sound the phone can't read stops the one before too, and the next one plays`() = runTest {
        keep(fado)
        keep(bells)
        val sounds = playback(backgroundScope)
        sounds.play(fado)
        player.position = 42_000
        sounds.play(bells)
        assertEquals(SoundSource.InFolder(File(folder, bells.media.src)), player.loaded)
        assertEquals(0L, player.position)

        player.fail()
        advanceTimeBy(1_000)
        assertTrue(sounds.sound.value!!.failed)
        assertFalse(sounds.sound.value!!.playing)

        // Played again, it's tried again; another sound plays as any would.
        sounds.play(fado)
        runCurrent()
        assertEquals(fado, sounds.sound.value!!.item)
        assertFalse(sounds.sound.value!!.failed)
        assertTrue(sounds.sound.value!!.playing)
    }

    @Test
    fun `the same sound goes on where it was, and a sound on the web plays from there`() = runTest {
        keep(fado)
        val sounds = playback(backgroundScope)
        sounds.play(fado)
        player.position = 12_000
        sounds.pause()
        sounds.play(fado.copy(label = "Fado at night"))
        assertEquals(12_000L, player.position)
        assertEquals("Fado at night", sounds.sound.value!!.item.label)

        val radio = sound("radio", "https://example.com/live.mp3")
        sounds.play(radio, from = 5_000)
        assertEquals(SoundSource.OnTheWeb("https://example.com/live.mp3"), player.loaded)
        assertEquals(5_000L, player.position)
        // One player for the map, made once.
        assertEquals(1, players.size)

        sounds.release()
        assertNull(sounds.sound.value)
        assertTrue(player.released)
    }
}
