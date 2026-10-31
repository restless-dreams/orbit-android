package com.hyuchia.orbit.ui

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.data.megabytes
import java.util.Locale

/**
 * What goes with a map, in words, wherever it's shared, sent or shown: the
 * same counts and phrases everywhere, so people learn them once.
 */
object MediaWords {
    /**
     * The pictures, live photos, videos and audio in `media`, as a list
     * people read: "3 pictures, a live photo, a video and 2 audio files".
     * Empty when there are none.
     */
    fun list(media: MediaSummary, words: Words): String = joined(parts(media, words), words)

    /** Each kind in `media`, counted: a live photo is named as one, its motion and all, and the other pictures as pictures. */
    private fun parts(media: MediaSummary, words: Words): List<String> {
        val livePhotos = media.motions.coerceIn(0, media.pictures)

        return listOfNotNull(
            (media.pictures - livePhotos).takeIf { it > 0 }?.let { words.plural(R.plurals.sharing_pictures, it, it) },
            livePhotos.takeIf { it > 0 }?.let { words.plural(R.plurals.sharing_live_photos, it, it) },
            media.videos.takeIf { it > 0 }?.let { words.plural(R.plurals.sharing_videos, it, it) },
            media.audio.takeIf { it > 0 }?.let { words.plural(R.plurals.sharing_audio, it, it) },
        )
    }

    private fun joined(parts: List<String>, words: Words): String = when (parts.size) {
        0 -> ""
        1 -> parts.single()
        2 -> words.string(R.string.sharing_list_two, parts[0], parts[1])
        3 -> words.string(R.string.sharing_list_three, parts[0], parts[1], parts[2])
        else -> words.string(R.string.sharing_list_two, parts.dropLast(1).joinToString(", "), parts.last())
    }

    /** "With 3 pictures and a video · 12.4 MB": all of it goes, as in a zip. Null when there's nothing to say. */
    fun with(media: MediaSummary, words: Words): String? =
        media.takeIf { !it.isEmpty }?.let { words.string(R.string.sharing_with, list(it, words), size(it.bytes, words)) }

    /** A size as people read it, "12.4 MB", that never breaks between the number and its unit. */
    fun size(bytes: Long, words: Words): String = megabytes(bytes, words).replace(' ', '\u00A0')

    /**
     * "With 3 pictures, a live photo and an audio file inside": a page's
     * pictures, the live photos whose motions it holds, and its sounds,
     * which go inside it. Null without any.
     */
    fun inside(media: MediaSummary, words: Words): String? =
        MediaSummary(pictures = media.pictures, audio = media.audio, motions = media.motions).takeIf { !it.isEmpty }?.let { words.string(R.string.sharing_inside, list(it, words)) }

    /** "With 3 pictures": a printed map's pictures, which are printed in it, a live photo as its still. Null without any. */
    fun printed(media: MediaSummary, words: Words): String? =
        media.pictures.takeIf { it > 0 }?.let { words.string(R.string.sharing_with_pictures, list(pictures(media), words)) }

    /**
     * "A video stays on this device": what doesn't go, such as a page's
     * videos and audio, and the `motions` of live photos whose pictures go
     * without them. Null when nothing stays.
     */
    fun staysHere(stays: MediaSummary, words: Words, motions: Int = 0): String? {
        val count = stays.count + motions

        if (count == 0) {
            return null
        }

        val parts = parts(stays, words) + listOfNotNull(motions.takeIf { it > 0 }?.let { words.plural(R.plurals.sharing_motions, it, it) })
        return capitalized(words.plural(R.plurals.sharing_stays_here, count, joined(parts, words)), words)
    }

    /**
     * The map's files that can't go clean in a way out ([Uncleaned]), named
     * rather than counted with the rest: "A video stays on this device:
     * Orbit can't take out where it was recorded." Once the person chose to
     * share them as they are (`asTheyAre`), what goes with them: "A video
     * goes as it is, with where it was recorded." Null without any.
     */
    fun uncleaned(files: Uncleaned, words: Words, asTheyAre: Boolean): String? {
        val (list, count) = uncleanedList(files, words) ?: return null

        val resource = when (how(files)) {
            Made.Taken -> if (asTheyAre) R.plurals.sharing_uncleaned_go_taken else R.plurals.sharing_uncleaned_stay_taken
            Made.Recorded -> if (asTheyAre) R.plurals.sharing_uncleaned_go_recorded else R.plurals.sharing_uncleaned_stay_recorded
            Made.TakenOrRecorded -> if (asTheyAre) R.plurals.sharing_uncleaned_go_taken_or_recorded else R.plurals.sharing_uncleaned_stay_taken_or_recorded
        }

        return capitalized(words.plural(resource, count, list), words)
    }

    /**
     * Files that can't go clean as a list people read, "a picture and a
     * video", with how many things it names, for the verb; null without
     * any. A live photo whose picture and motion are both here is named as
     * one, and a motion alone as a live photo's motion.
     */
    fun uncleanedList(files: Uncleaned, words: Words): Pair<String, Int>? {
        val livePhotos = files.livePhotos
        val motions = files.motions.size - livePhotos
        val media = MediaSummary(pictures = files.pictures.size, videos = files.videos.size, audio = files.sounds.size, motions = livePhotos)
        val count = media.count + motions

        if (count == 0) {
            return null
        }

        val named = parts(media, words) + listOfNotNull(motions.takeIf { it > 0 }?.let { words.plural(R.plurals.sharing_motions, it, it) })
        return joined(named, words) to count
    }

    /** How files that can't go clean were made: what the words say would go with them. */
    fun how(files: Uncleaned): Made {
        val recorded = files.videos.isNotEmpty() || files.sounds.isNotEmpty() || files.motions.size > files.livePhotos

        return when {
            files.pictures.isNotEmpty() && recorded -> Made.TakenOrRecorded
            files.pictures.isNotEmpty() -> Made.Taken
            else -> Made.Recorded
        }
    }

    /** How a file was made, as the words name it: a picture taken, or a video or sound recorded. */
    enum class Made { Taken, Recorded, TakenOrRecorded }

    /** File types as a list: ".mp3", ".mp3 and .ogg", ".mp3, .ogg and .opus". */
    fun types(extensions: Collection<String>, words: Words): String {
        val named = extensions.sorted().map { ".$it" }

        return when (named.size) {
            0 -> ""
            1 -> named.single()
            else -> words.string(R.string.sharing_list_two, named.dropLast(1).joinToString(", "), named.last())
        }
    }

    private fun pictures(media: MediaSummary) = MediaSummary(pictures = media.pictures)

    /** A sentence that starts with a list, as "a video stays…" does, with its first letter a capital in the language of `words`. */
    private fun capitalized(text: String, words: Words): String = text.replaceFirstChar { it.titlecase(Locale.forLanguageTag(words.language)) }
}
