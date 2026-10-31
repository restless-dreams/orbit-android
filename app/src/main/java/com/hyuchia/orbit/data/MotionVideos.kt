package com.hyuchia.orbit.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.hyuchia.orbit.Logs
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * An iPhone's live photo movie made the MP4 a live photo's motion is kept
 * as: its video as it was, frame for frame (HEVC or H.264), its sound made
 * AAC when it's uncompressed, as iPhones record it, so browsers play it too,
 * and nothing else of the movie: not its metadata tracks, place or make.
 */
internal object MotionVideos {
    /** Writes `input` as an MP4 to `output`; false, and no `output`, when it can't be made one. */
    fun toMp4(input: File, output: File): Boolean {
        // Android's reader won't open a movie without an `ftyp`, as older iPhones write theirs: it reads a copy given one.
        val typed = File(output.parentFile, "${output.name}.typed")

        return try {
            convert(if (QuickTimeMovies.withFileType(input, typed)) typed else input, output)
            true
        } catch (failure: Exception) {
            Logs.warn("OrbitMotion", "A live photo's movie couldn't be made an MP4", failure)
            output.delete()
            false
        } finally {
            typed.delete()
        }
    }

    private fun convert(input: File, output: File) {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(input.path)
            val tracks = (0 until extractor.trackCount).associateWith { extractor.getTrackFormat(it) }
            val (videoTrack, videoFormat) = tracks.entries.firstOrNull { it.value.mime.startsWith("video/") } ?: throw IOException("A movie without a video")
            // Android doesn't read QuickTime's descriptions of uncompressed sound: that's read here, AAC by Android.
            val sound = QuickTimeMovies.sound(input)?.let(::encode)
                ?: tracks.entries.firstOrNull { it.value.mime == MediaFormat.MIMETYPE_AUDIO_AAC }?.let { aac(input, it.key) }
            val rotation = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) videoFormat.getInteger(MediaFormat.KEY_ROTATION) else 0
            val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            try {
                muxer.setOrientationHint(rotation)
                val video = muxer.addTrack(videoFormat)
                val audio = sound?.let { muxer.addTrack(it.format) }
                muxer.start()
                copySamples(extractor, videoTrack, videoFormat, muxer, video)

                if (sound != null && audio != null) {
                    for ((bytes, info) in sound.samples) {
                        muxer.writeSampleData(audio, bytes, info)
                    }
                }

                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            extractor.release()
        }
    }

    /** A track's samples, as they are, into the muxer's track `into`. */
    private fun copySamples(extractor: MediaExtractor, track: Int, format: MediaFormat, muxer: MediaMuxer, into: Int) {
        extractor.selectTrack(track)
        val buffer = ByteBuffer.allocate(if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else SAMPLE_ROOM)
        val info = MediaCodec.BufferInfo()

        while (true) {
            val size = extractor.readSampleData(buffer, 0)

            if (size < 0) {
                break
            }

            val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
            info.set(0, size, extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(into, buffer, info)
            extractor.advance()
        }
    }

    /** A movie's sound, in memory: a live photo's is a few seconds. */
    private class Sound(val format: MediaFormat, val samples: List<Pair<ByteBuffer, MediaCodec.BufferInfo>>)

    /** The AAC sound of `input`'s `track`, as it is. */
    private fun aac(input: File, track: Int): Sound {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(input.path)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            return Sound(format, readAll(extractor, format))
        } finally {
            extractor.release()
        }
    }

    private fun readAll(extractor: MediaExtractor, format: MediaFormat): List<Pair<ByteBuffer, MediaCodec.BufferInfo>> {
        val buffer = ByteBuffer.allocate(if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else SAMPLE_ROOM)
        val samples = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()

        while (true) {
            val size = extractor.readSampleData(buffer, 0)

            if (size < 0) {
                return samples
            }

            buffer.position(0).limit(size)
            val copy = ByteBuffer.allocate(size).put(buffer).flip() as ByteBuffer
            samples += copy to MediaCodec.BufferInfo().apply { set(0, size, extractor.sampleTime, 0) }
            buffer.clear()
            extractor.advance()
        }
    }

    /** Uncompressed 16-bit sound, as an iPhone records a live photo's, encoded as AAC. */
    private fun encode(sound: Pcm): Sound? {
        val rate = sound.rate
        val channels = sound.channels
        val frame = 2 * channels

        val target = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE * channels)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, SAMPLE_ROOM)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)

        try {
            encoder.configure(target, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val pending = ByteBuffer.wrap(sound.samples)
            val info = MediaCodec.BufferInfo()
            val samples = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
            var encoded: MediaFormat? = null
            var frames = 0L
            var fed = false

            while (true) {
                if (!fed) {
                    val index = encoder.dequeueInputBuffer(WAIT)

                    if (index >= 0) {
                        val buffer = encoder.getInputBuffer(index) ?: throw IOException("No room to encode the sound")
                        buffer.clear()

                        if (!pending.hasRemaining()) {
                            encoder.queueInputBuffer(index, 0, 0, frames * 1_000_000 / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            fed = true
                        } else {
                            val count = minOf(buffer.remaining(), pending.remaining()) / frame * frame
                            val chunk = pending.slice().limit(count) as ByteBuffer
                            buffer.put(chunk)
                            pending.position(pending.position() + count)
                            encoder.queueInputBuffer(index, 0, count, frames * 1_000_000 / rate, 0)
                            frames += count / frame
                        }
                    }
                }

                val out = encoder.dequeueOutputBuffer(info, WAIT)

                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> encoded = encoder.outputFormat
                    out >= 0 -> {
                        val buffer = encoder.getOutputBuffer(out) ?: throw IOException("No sound came out of the encoder")

                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            buffer.position(info.offset).limit(info.offset + info.size)
                            val copy = ByteBuffer.allocate(info.size).put(buffer).flip() as ByteBuffer
                            samples += copy to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                        }

                        encoder.releaseOutputBuffer(out, false)

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return encoded?.let { Sound(it, samples) }
                        }
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
        }
    }

    private val MediaFormat.mime: String get() = getString(MediaFormat.KEY_MIME).orEmpty()

    /** Room for one sample of a live photo's video or sound. */
    private const val SAMPLE_ROOM = 4 * 1024 * 1024

    /** AAC's bit rate for each channel: plenty for a few seconds of a moment's sound. */
    private const val AAC_BIT_RATE = 64_000

    /** How long to wait for the encoder each time, in microseconds. */
    private const val WAIT = 10_000L
}
