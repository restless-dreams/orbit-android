package com.hyuchia.orbit.data

/**
 * A map's own files that can't go to other people clean: Orbit can't read
 * them through to take out what they say beyond what they show or play
 * ([OutgoingMedia]), as with a picture or a video cut short, or a file of
 * no kind Orbit knows; a sound cut short at its end goes clean. Every
 * way out leaves them on this device, unless the person sharing chose, as
 * it started, to share them as they are; a browser is never asked, and
 * never gets them. They're by the kind people know them as, for the
 * question and the line that name them.
 */
data class Uncleaned(
    val pictures: Set<String> = emptySet(),
    val videos: Set<String> = emptySet(),
    val sounds: Set<String> = emptySet(),

    /** Live photos' motions (`picture-1-motion.mp4`): a live photo with its picture here too, the motion alone when its picture goes clean. */
    val motions: Set<String> = emptySet(),
) {
    /** Every one's name. */
    val names: Set<String> get() = pictures + videos + sounds + motions

    val isEmpty: Boolean get() = names.isEmpty()

    /** Those a web page would carry inside it: its pictures, sounds and motions, but never a video. */
    val inPage: Uncleaned get() = copy(videos = emptySet())

    /** Those paper, a drawing or a TV would carry: the pictures. */
    val inPictures: Uncleaned get() = Uncleaned(pictures = pictures)

    /** Those a presentation's browsers would get: everything but the sounds, which play from the phone. */
    val inRoom: Uncleaned get() = copy(sounds = emptySet())

    /** Only those named in `names`. */
    fun only(names: Set<String>): Uncleaned = Uncleaned(pictures intersect names, videos intersect names, sounds intersect names, motions intersect names)

    /** The live photos among them: a picture whose motion is here too. */
    val livePhotos: Int
        get() {
            val stems = pictures.map { it.substringBeforeLast('.') }.toSet()
            return motions.count { stemOf(it) in stems }
        }

    companion object {
        /**
         * The files of `all`, a map's, that are `uncleaned`, by kind:
         * `sounds` names those its text plays as sounds, as a voice memo's
         * `.mp4` is.
         */
        fun of(uncleaned: Collection<String>, all: Collection<String>, sounds: Set<String> = emptySet()): Uncleaned {
            val pictureStems = all.filter { it !in sounds && mediaType(it).startsWith("image/") }.map { it.substringBeforeLast('.') }.toSet()
            val (asSounds, others) = uncleaned.partition { it in sounds || mediaType(it).startsWith("audio/") }
            val motions = others.filter { name -> MediaNames.stillOf(name)?.let { it in pictureStems } == true }

            return Uncleaned(
                pictures = others.filter { mediaType(it).startsWith("image/") }.toSet(),
                videos = others.filter { mediaType(it).startsWith("video/") && it !in motions }.toSet(),
                sounds = asSounds.toSet(),
                motions = motions.toSet(),
            )
        }

        private fun stemOf(motion: String): String? = MediaNames.stillOf(motion)
    }
}
