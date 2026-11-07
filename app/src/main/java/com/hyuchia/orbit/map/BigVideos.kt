package com.hyuchia.orbit.map

import android.net.Uri
import com.hyuchia.orbit.data.MAP_MEDIA_LIMIT
import com.hyuchia.orbit.data.MEDIA_FILE_LIMIT
import com.hyuchia.orbit.data.SmallerVideo
import com.hyuchia.orbit.data.SmallerVideos
import com.hyuchia.orbit.media.VideoMaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Orbit asks about big videos coming into a map: how many, how big they
 * are, and about how big they'd be made smaller; `keepable` says they fit
 * the map as they are, so keeping them is offered too.
 */
data class SmallerOffer(val videos: Int, val size: Long, val estimate: Long, val keepable: Boolean)

/** What the person says to a [SmallerOffer]: make them smaller, keep them as they are, or neither, adding nothing. */
enum class SmallerAnswer { Smaller, AsTheyAre, Neither }

/**
 * Asks about big videos one offer at a time, for the map's screen to show
 * ([SmallerOfferDialog]): an offer asked while another waits answers that
 * one with neither.
 */
class SmallerOffers {
    private val state = MutableStateFlow<SmallerOffer?>(null)
    private var waiting: CompletableDeferred<SmallerAnswer>? = null

    /** The offer waiting for an answer, while one is. */
    val offer: StateFlow<SmallerOffer?> = state.asStateFlow()

    /** Asks `offer`, and waits for the answer; its asker no longer waiting takes it off the screen. */
    suspend fun ask(offer: SmallerOffer): SmallerAnswer {
        val answer = CompletableDeferred<SmallerAnswer>()
        waiting?.complete(SmallerAnswer.Neither)
        waiting = answer
        state.value = offer

        return try {
            answer.await()
        } finally {
            if (waiting === answer) {
                waiting = null
                state.value = null
            }
        }
    }

    fun answer(choice: SmallerAnswer) {
        waiting?.complete(choice)
    }
}

/** How a video from the phone comes into a map, as [BigVideos] works it out. */
sealed interface VideoWay {
    /** As it is: a small one, one that wouldn't come out much smaller, or one the person chose to keep so. */
    data object AsItIs : VideoWay

    /** Made smaller, as `plan` says, from its `size`. */
    data class Smaller(val plan: SmallerVideo, val size: Long) : VideoWay

    /** A big one this phone can't make smaller: kept as it is, which is said. */
    data object AsItIsHere : VideoWay

    /** The person chose neither: it doesn't come in. */
    data object Neither : VideoWay
}

/**
 * Works out how big videos from the phone come into a map: one past
 * [SmallerVideos.OFFER_FROM] that would come out much smaller is offered
 * smaller through `offers`, or as it is when it fits ([SmallerOffer.keepable]);
 * one this phone can't make smaller (`maker`) comes as it is, and that's
 * said; and one that wouldn't fit even smaller isn't offered, and is
 * refused as it's copied.
 */
class BigVideos(private val maker: () -> VideoMaker, private val offers: SmallerOffers) {
    /** A big video: its address and size, and how it would be made smaller, when it would fit then. */
    class Big(val uri: Uri, val size: Long, val plan: SmallerVideo?)

    /**
     * How the video at `uri`, of `size` bytes (negative when unknown), goes
     * into a map whose pictures, videos and sounds come to `mapMedia`;
     * `asking` hears when the person is about to be asked.
     */
    suspend fun way(uri: Uri, size: Long, mapMedia: Long, asking: () -> Unit = {}): VideoWay {
        val big = big(uri, size, mapMedia) ?: return VideoWay.AsItIs
        return ways(listOf(big), mapMedia, asking).getValue(uri)
    }

    /** The video, when it's big, with how it would be made smaller; null for one that comes as it is without a word. */
    suspend fun big(uri: Uri, size: Long, mapMedia: Long): Big? {
        if (size <= SmallerVideos.OFFER_FROM) {
            return null
        }

        val plan = maker().facts(uri, size)?.let(SmallerVideos::plan)
            // Even smaller, past what a map keeps: nothing to offer, and its copy is refused, saying why.
            ?.takeIf { it.estimate <= MEDIA_FILE_LIMIT && mapMedia + it.estimate <= MAP_MEDIA_LIMIT }

        return Big(uri, size, plan)
    }

    /**
     * How each of `videos`, big ones together coming into a map whose
     * pictures, videos and sounds come to `mapMedia`, goes: asked once for
     * all those that would come out smaller, by their address. `asking`
     * hears when the person is about to be asked.
     */
    suspend fun ways(videos: List<Big>, mapMedia: Long, asking: () -> Unit = {}): Map<Uri, VideoWay> {
        val offered = videos.filter { it.plan != null }

        if (offered.isEmpty()) {
            return videos.associate { it.uri to VideoWay.AsItIs }
        }

        if (offered.any { video -> video.plan.let { it != null && !maker().canMake(it.width, it.height) } }) {
            return videos.associate { it.uri to VideoWay.AsItIsHere }
        }

        val size = videos.sumOf { it.size }
        val keepable = videos.all { it.size <= MEDIA_FILE_LIMIT } && mapMedia + size <= MAP_MEDIA_LIMIT
        val estimate = offered.sumOf { it.plan!!.estimate } + (videos - offered.toSet()).sumOf { it.size }
        asking()

        return when (offers.ask(SmallerOffer(videos.size, size, estimate, keepable))) {
            SmallerAnswer.Smaller -> videos.associate { video -> video.uri to (video.plan?.let { VideoWay.Smaller(it, video.size) } ?: VideoWay.AsItIs) }
            SmallerAnswer.AsTheyAre -> videos.associate { it.uri to VideoWay.AsItIs }
            SmallerAnswer.Neither -> videos.associate { it.uri to VideoWay.Neither }
        }
    }
}
