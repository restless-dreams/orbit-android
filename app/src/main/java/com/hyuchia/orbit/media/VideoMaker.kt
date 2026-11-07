package com.hyuchia.orbit.media

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.data.SmallerVideo
import com.hyuchia.orbit.data.VideoFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Makes big videos smaller on the phone as they come into a map (see
 * [com.hyuchia.orbit.data.SmallerVideos]): what a video says of itself,
 * whether this phone can make it, and the making.
 */
interface VideoMaker {
    /** What the video at `uri`, of `size` bytes, says of itself; null when Android can't read it. */
    suspend fun facts(uri: Uri, size: Long): VideoFacts?

    /** Whether this phone can make a video of `width` by `height` pixels in H.264, with an encoder in its hardware. */
    fun canMake(width: Int, height: Int): Boolean

    /**
     * Makes the video at `uri` smaller into `output`, an MP4, as `plan`
     * says, telling `onProgress` how far along it is, from 0 to 1. Stopped
     * by its coroutine's cancellation; whatever stops it, `output` is the
     * caller's to delete.
     */
    suspend fun make(uri: Uri, plan: SmallerVideo, output: File, onProgress: (Float) -> Unit)

    companion object {
        /** No videos made smaller: as on a phone that can't, and where there's no phone, in tests. */
        val NONE: VideoMaker = object : VideoMaker {
            override suspend fun facts(uri: Uri, size: Long): VideoFacts? = null

            override fun canMake(width: Int, height: Int): Boolean = false

            override suspend fun make(uri: Uri, plan: SmallerVideo, output: File, onProgress: (Float) -> Unit) = throw IOException("This phone doesn't make videos smaller")
        }
    }
}

/**
 * Videos made smaller with AndroidX Media3's Transformer, open source and in
 * every build: H.264 and AAC in an MP4, at most 1080p on the shorter side,
 * which way up the video is kept as it was (it's encoded on its side, saying
 * so, as most encoders take a video), and HDR made SDR, which H.264 can't
 * carry. Only an encoder in the phone's hardware makes them, unless
 * `software` allows the phone's software one, as on an emulator: a phone's
 * own is quick, and spares its battery.
 */
@OptIn(UnstableApi::class)
class TransformerVideoMaker(private val context: Context, private val software: Boolean = BuildConfig.SOFTWARE_VIDEO_ENCODING) : VideoMaker {
    override suspend fun facts(uri: Uri, size: Long): VideoFacts? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.number(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: return@withContext null
            val width = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: return@withContext null
            val height = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: return@withContext null
            val frames = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)

            VideoFacts(
                durationMilliseconds = duration,
                width = width,
                height = height,
                size = size,
                frameRate = frames?.takeIf { duration > 0 }?.let { it * 1_000f / duration },
                bitrate = retriever.number(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                sound = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
            )
        } catch (unreadable: RuntimeException) {
            // Android can't read the video, or the app that gave it took it back.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.number(key: Int): Long? = extractMetadata(key)?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    override fun canMake(width: Int, height: Int): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder &&
                (software || info.isHardwareAccelerated) &&
                info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H264, ignoreCase = true) } &&
                info.getCapabilitiesForType(MimeTypes.VIDEO_H264).videoCapabilities?.isSizeSupported(maxOf(width, height), minOf(width, height)) == true
        }
    } catch (unreadable: RuntimeException) {
        // A phone whose codecs can't be listed makes nothing.
        false
    }

    override suspend fun make(uri: Uri, plan: SmallerVideo, output: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { going ->
            // Transformer is driven from the thread it was made on, the main one here, which hears how it went.
            val handler = Handler(Looper.getMainLooper())
            val progress = ProgressHolder()
            var transformer: Transformer? = null

            val follow = object : Runnable {
                override fun run() {
                    if (transformer?.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progress.progress / 100f)
                    }

                    handler.postDelayed(this, PROGRESS_EVERY_MILLISECONDS)
                }
            }

            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build())
                .setEnableFallback(true)
                .build()

            val made = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoders)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(follow)
                            onProgress(1f)

                            if (going.isActive) {
                                going.resume(Unit)
                            }
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacks(follow)

                            if (going.isActive) {
                                going.resumeWithException(IOException("Couldn't make the video smaller", exportException))
                            }
                        }
                    },
                )
                .build()

            transformer = made
            val effects = plan.shortSide?.let { Effects(emptyList(), listOf(Presentation.createForShortSide(it))) } ?: Effects.EMPTY
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build()
            val sequence = if (plan.sound) EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item)) else EditedMediaItemSequence.withVideoFrom(listOf(item))
            val composition = Composition.Builder(sequence).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()

            going.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(follow)
                    made.cancel()
                }
            }

            made.start(composition, output.path)
            handler.post(follow)
        }
    }

    private companion object {
        /** How often the making's progress is looked at: often enough for a bar to move smoothly. */
        const val PROGRESS_EVERY_MILLISECONDS = 250L
    }
}
