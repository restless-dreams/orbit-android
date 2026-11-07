package com.hyuchia.orbit.data

/**
 * What a video says of itself, read from its headers without its frames:
 * how long it plays, in milliseconds; its picture's width and height as
 * they're kept, before any rotation; how many bytes its file is; how many
 * frames it shows a second and the bits a second its whole file comes to,
 * when it says; and whether it has sound.
 */
data class VideoFacts(
    val durationMilliseconds: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val frameRate: Float? = null,
    val bitrate: Long? = null,
    val sound: Boolean = true,
)

/**
 * How Orbit would make a video smaller: its picture at most `shortSide`
 * pixels on its shorter side (null when it's that or smaller already, and
 * keeps its size), `width` by `height` as it's encoded, the longer side
 * first, in H.264 at `videoBitrate` bits a second, with its sound, when it
 * has one, as AAC; and about how many bytes it would come to (`estimate`).
 */
data class SmallerVideo(
    val shortSide: Int?,
    val width: Int,
    val height: Int,
    val videoBitrate: Int,
    val sound: Boolean,
    val estimate: Long,
)

/**
 * Big videos made smaller on the phone as they come into a map: when Orbit
 * offers to, and what it makes. A video of more than [OFFER_FROM] is
 * offered smaller: at most 1080p ([SHORT_SIDE]), in H.264 at a bitrate for
 * its size and frames, its sound in AAC, only when that comes to well under
 * what it was ([WORTH_IT]).
 */
object SmallerVideos {
    /** A video of more than this is offered smaller: about half a minute of a phone's 1080p. */
    const val OFFER_FROM = 50_000_000L

    /** A video made smaller is at most 1080p: this many pixels on its shorter side, a phone's or a TV's own. */
    const val SHORT_SIDE = 1080

    /** The bits a second of 1080p at 30 frames a second, in H.264: under half of what phones record it at, and sharp still. */
    private const val FULL_HD_BITRATE = 8_000_000.0

    private const val FULL_HD_PIXELS = 1920.0 * 1080

    /** The frames a second the bitrate above is for: more take more, up to 60's. */
    private const val FULL_HD_FRAMES = 30.0

    /** A video made smaller has at least this many bits a second, however small its picture, and at most the next. */
    private const val LEAST_BITRATE = 1_000_000.0

    private const val MOST_BITRATE = 12_000_000.0

    /** The bits a second of a video's sound, AAC as phones record it, for the estimate. */
    private const val SOUND_BITRATE = 192_000L

    /** What an MP4 takes besides its frames and its sound: its boxes and its index. */
    private const val FILE_SHARE = 1.02

    /** Made smaller, a video comes to at most this much of what it was, or it isn't worth the wait, and isn't offered. */
    private const val WORTH_IT = 0.7

    /** Of the bits a second a video's own frames have, the most one made smaller takes: it always comes out smaller. */
    private const val OWN_SHARE = 0.8

    /**
     * How the video `facts` describe would be made smaller, or null when it
     * wouldn't come out much smaller (a video at a low bitrate already), or
     * its headers don't say enough to tell.
     */
    fun plan(facts: VideoFacts): SmallerVideo? {
        if (facts.durationMilliseconds <= 0 || facts.width <= 0 || facts.height <= 0 || facts.size <= 0) {
            return null
        }

        val shorter = minOf(facts.width, facts.height)
        val scale = minOf(1.0, SHORT_SIDE.toDouble() / shorter)
        // As it's encoded: the longer side first, each an even number of pixels, as encoders take them.
        val longSide = even(maxOf(facts.width, facts.height) * scale)
        val shortSide = even(shorter * scale)
        val frames = (facts.frameRate?.toDouble()?.takeIf { it > 0 } ?: FULL_HD_FRAMES).coerceIn(24.0, 60.0)
        val wanted = (FULL_HD_BITRATE * longSide * shortSide / FULL_HD_PIXELS * frames / FULL_HD_FRAMES).coerceIn(LEAST_BITRATE, MOST_BITRATE)
        val sound = if (facts.sound) SOUND_BITRATE else 0L
        // What the video's own frames take a second, from its file's bitrate: one made smaller takes less.
        val own = (facts.bitrate?.takeIf { it > 0 } ?: (facts.size * 8_000 / facts.durationMilliseconds)) - sound
        val bitrate = minOf(wanted, own * OWN_SHARE).toLong()

        if (bitrate <= 0) {
            return null
        }

        val estimate = ((bitrate + sound) * (facts.durationMilliseconds / 8_000.0) * FILE_SHARE).toLong()

        return SmallerVideo(SHORT_SIDE.takeIf { scale < 1 }, longSide, shortSide, bitrate.toInt(), facts.sound, estimate)
            .takeIf { estimate <= facts.size * WORTH_IT }
    }

    /** `pixels`, rounded to the nearest even number, at least 2. */
    private fun even(pixels: Double): Int = maxOf(2, (Math.round(pixels / 2) * 2).toInt())
}
