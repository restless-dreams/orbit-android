package com.hyuchia.orbit.data

import java.time.Duration

/**
 * Something coming in together with other things (picked, dropped, shared or
 * pasted at once) that may be half of an iPhone's live photo: a picture and
 * what it says ([still]), or a movie and what it says ([video]). `name` is
 * its file's, when it has one.
 */
internal data class LiveHalf<T>(val item: T, val name: String?, val still: StillFacts? = null, val video: LiveVideo? = null)

/** The live photos found among what came in together, as (still, movie), and a picture and movie to offer joining when nothing else matched them. */
internal data class LiveMatches<T>(val pairs: List<Pair<T, T>>, val offer: Pair<T, T>? = null)

/**
 * Matches an iPhone's stills with their movies. A movie counts when it says
 * it's a live photo's, or carries the pair's id. In order:
 *
 * 1. **The id,** when both have it: the original HEIC and its movie do.
 * 2. **The same name** (`IMG_0911.HEIC` and `IMG_0911.MOV`).
 * 3. **When they were taken,** within [NEAR] of each other, the nearest
 *    first: a JPEG the phone made on the way out loses the id, and keeps the
 *    time.
 *
 * When one picture and one such movie are left, with nothing to say they
 * belong together, they're offered to be joined: the person knows.
 */
internal object LivePairs {
    /** How far apart a still and its movie were taken, at most, by the phone's own clock. */
    val NEAR: Duration = Duration.ofSeconds(3)

    fun <T> match(stills: List<LiveHalf<T>>, videos: List<LiveHalf<T>>): LiveMatches<T> {
        val pictures = stills.toMutableList()
        val movies = videos.filter { it.video != null && (it.video.live || it.video.identifier != null) }.toMutableList()
        val pairs = mutableListOf<Pair<T, T>>()

        fun join(still: LiveHalf<T>, movie: LiveHalf<T>) {
            pairs += still.item to movie.item
            pictures -= still
            movies -= movie
        }

        for (movie in movies.toList()) {
            val identifier = movie.video?.identifier ?: continue
            pictures.firstOrNull { it.still?.identifier.equals(identifier, ignoreCase = true) }?.let { join(it, movie) }
        }

        for (movie in movies.toList()) {
            val stem = movie.name?.let(::stem) ?: continue
            pictures.firstOrNull { picture -> picture.name?.let(::stem) == stem }?.let { join(it, movie) }
        }

        // The nearest in time first, so two live photos a second apart each find their own.
        val near = pictures.flatMap { picture -> movies.mapNotNull { movie -> gap(picture, movie)?.let { Triple(picture, movie, it) } } }.sortedBy { it.third }

        for ((picture, movie, apart) in near) {
            if (apart <= NEAR && picture in pictures && movie in movies) {
                join(picture, movie)
            }
        }

        val offer = if (pictures.size == 1 && movies.size == 1) pictures.single().item to movies.single().item else null
        return LiveMatches(pairs, offer)
    }

    private fun stem(name: String): String = name.substringBeforeLast('.').lowercase()

    private fun <T> gap(picture: LiveHalf<T>, movie: LiveHalf<T>): Duration? {
        val taken = picture.still?.taken ?: return null
        val made = movie.video?.created ?: return null
        return Duration.between(taken, made).abs()
    }
}
