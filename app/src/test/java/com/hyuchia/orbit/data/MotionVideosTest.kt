package com.hyuchia.orbit.data

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec
import org.robolectric.shadows.ShadowMediaExtractor
import org.robolectric.shadows.util.DataSource
import java.io.File

/**
 * An iPhone's live photo movie made an MP4 ([MotionVideos]), its
 * uncompressed sound made AAC by Robolectric's stand-in for the encoder,
 * which counts what it's given: each piece of sound, and the empty one
 * that ends it. One given more empty pieces in a row than any sound needs
 * stops the encoding, as the review's probe did, rather than loop.
 */
@RunWith(AndroidJUnit4::class)
class MotionVideosTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** How many bytes of sound each piece given to the encoder held, in order. */
    private val given = mutableListOf<Int>()

    /** Called with each piece the encoder is given, after it's counted. */
    private var onPiece: () -> Unit = {}

    @After
    fun cleanUp() {
        ShadowMediaCodec.clearCodecs()
        ShadowMediaExtractor.reset()
        StuckCodec.stuck = null
    }

    /** An encoder whose pieces of sound take `room` bytes. */
    private fun encoder(room: Int = 512) {
        ShadowMediaCodec.addEncoder(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            ShadowMediaCodec.CodecConfig(room, room) { input, _ ->
                given += input.remaining()
                onPiece()

                if (given.size >= 6 && given.takeLast(5).all { it == 0 }) {
                    throw IllegalStateException("Five empty pieces of sound in a row")
                }
            },
        )
    }

    /** A live photo's movie of `samples`, whose video Android's reader finds, as Robolectric's stand-in for it says. */
    private fun movie(samples: ByteArray, frames: Int, name: String = "IMG_0911.MOV"): File {
        val file = temporary.newFile(name).apply { writeBytes(TestMedia.liveMovie(samples, frames)) }
        ShadowMediaExtractor.addTrack(DataSource.toDataSource(file.path), MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 64, 48), ByteArray(64) { it.toByte() })
        return file
    }

    @Test
    fun `a live photo whose stereo sound was cut short partway through a frame is made an MP4, without an empty piece before its end`() {
        encoder()
        // A frame and a half of two channels: the review's probe found the encoder given empty pieces, forever, for the half.
        val output = File(temporary.root, "motion.mp4")
        val made = runBlocking { MotionVideos.toMp4(movie(ByteArray(6) { 1 }, frames = 3), output) }

        assertTrue(made)
        assertTrue(output.length() > 0)
        assertEquals(listOf(4, 0), given)
    }

    @Test
    fun `an encoder with no room for a whole frame stops the making, and the movie stays as it is`() {
        // Pieces of two bytes, and frames of four: no piece can hold any of the sound.
        encoder(room = 2)
        val output = File(temporary.root, "motion.mp4")
        val made = runBlocking { MotionVideos.toMp4(movie(ByteArray(16) { 1 }, frames = 4), output) }

        assertFalse(made)
        assertFalse(output.exists())
        assertTrue("given $given", given.none { it == 0 })
    }

    @Test
    fun `making a live photo's MP4 stops as soon as its import is cancelled`() {
        encoder()
        // Enough sound for many pieces: the import is cancelled as the encoder takes the first.
        val input = movie(ByteArray(48_000) { 1 }, frames = 12_000)
        val output = File(temporary.root, "motion.mp4")

        try {
            runBlocking {
                val job = coroutineContext.job
                onPiece = { job.cancel() }
                MotionVideos.toMp4(input, output)
            }

            fail("The making should have stopped with the import")
        } catch (cancelled: CancellationException) {
            // The import goes on stopping: the making doesn't say it failed, which would keep the movie as it is.
        }

        assertEquals(listOf(512), given)
        assertFalse(output.exists())
    }

    @Test
    @Config(shadows = [StuckCodec::class])
    fun `an encoder that takes no sound, or never says it has ended, stops the making after a few seconds`() {
        encoder()

        for (stuck in StuckCodec.Stuck.entries) {
            StuckCodec.stuck = stuck
            val output = File(temporary.root, "motion-$stuck.mp4")
            val made = runBlocking { MotionVideos.toMp4(movie(ByteArray(4096) { 1 }, frames = 1024, name = "$stuck.MOV"), output) }

            assertFalse("$stuck", made)
            assertFalse("$stuck", output.exists())
        }
    }

    @Test
    fun `a failure that comes of the import being cancelled stops the making as the cancellation it is`() {
        encoder()
        val input = movie(ByteArray(48_000) { 1 }, frames = 12_000)
        val output = File(temporary.root, "motion.mp4")
        var outcome: Result<Boolean>? = null

        runCatching {
            runBlocking {
                val job = coroutineContext.job

                // As the muxer fails when it's let go before its first sample, once the import is cancelled.
                onPiece = {
                    job.cancel()
                    throw IllegalStateException("Failed to stop the muxer")
                }

                outcome = runCatching { MotionVideos.toMp4(input, output) }
            }
        }

        assertTrue("$outcome", outcome?.exceptionOrNull() is CancellationException)
        assertFalse(output.exists())
    }
}

/**
 * Robolectric's stand-in for the encoder, stuck as [stuck] says: it never
 * has room to be given sound, or it takes the end of the sound and never
 * says it has ended. Without either, it's Robolectric's own.
 */
@Implements(MediaCodec::class)
class StuckCodec : ShadowMediaCodec() {
    enum class Stuck { NoRoom, NoEnd }

    @Implementation
    override fun native_dequeueInputBuffer(timeoutUs: Long): Int = if (stuck == Stuck.NoRoom) MediaCodec.INFO_TRY_AGAIN_LATER else super.native_dequeueInputBuffer(timeoutUs)

    @Implementation
    override fun native_queueInputBuffer(index: Int, offset: Int, size: Int, presentationTimeUs: Long, flags: Int) {
        if (stuck == Stuck.NoEnd && flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            return
        }

        super.native_queueInputBuffer(index, offset, size, presentationTimeUs, flags)
    }

    companion object {
        var stuck: Stuck? = null
    }
}
