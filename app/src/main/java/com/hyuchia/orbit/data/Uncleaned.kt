package com.hyuchia.orbit.data

/**
 * A map's own files that can't go to other people clean: Orbit can't read
 * them through to take out what they say beyond what they show or play
 * ([OutgoingMedia]), as with a picture or a video cut short, or a file of
 * no kind Orbit knows, as most of ideas' files are (a PDF, a spreadsheet);
 * a sound cut short at its end goes clean. Every
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

    /** Ideas' files: a PDF, a spreadsheet, any file an idea holds, which Orbit can't read through to clean. */
    val files: Set<String> = emptySet(),

    /** The names ideas' files came with, by the names they're kept under: what the person knows them by. */
    val labels: Map<String, String> = emptyMap(),

    /** Each one as it was when Orbit found it can't clean it ([FileStamp]): what the person is asked about, and answers for. */
    val stamps: Map<String, FileStamp> = emptyMap(),
) {
    /** Every one's name. */
    val names: Set<String> get() = pictures + videos + sounds + motions + files

    val isEmpty: Boolean get() = names.isEmpty()

    /** Those a web page would carry inside it: its pictures, sounds and motions, but never a video or an idea's file, which it only lists. */
    val inPage: Uncleaned get() = copy(videos = emptySet(), files = emptySet())

    /** Those paper, a drawing or a TV would carry: the pictures. */
    val inPictures: Uncleaned get() = only(pictures)

    /** Those a presentation's browsers would get: everything but the sounds, which play from the phone, and ideas' files, which stay there too. */
    val inRoom: Uncleaned get() = copy(sounds = emptySet(), files = emptySet())

    /** Only those named in `names`. */
    fun only(names: Set<String>): Uncleaned {
        val kept = Uncleaned(pictures intersect names, videos intersect names, sounds intersect names, motions intersect names, files intersect names, labels)
        return kept.copy(stamps = stamps.filterKeys { it in kept.names })
    }

    /** What the person knows a file of theirs by: an idea's file by the name it came with, any other by its own. */
    fun label(name: String): String = labels[name] ?: name

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
         * `.mp4` is, and `files` those its ideas hold as files, which are
         * files whatever their kind, known by their `labels`. `stamps` says
         * what each was as Orbit found it can't clean it.
         */
        fun of(
            uncleaned: Collection<String>,
            all: Collection<String>,
            sounds: Set<String> = emptySet(),
            files: Set<String> = emptySet(),
            labels: Map<String, String> = emptyMap(),
            stamps: Map<String, FileStamp> = emptyMap(),
        ): Uncleaned {
            val ideaFiles = uncleaned.filter { it in files && it !in sounds }.toSet()
            val media = uncleaned.filter { it !in ideaFiles }
            val pictureStems = all.filter { it !in sounds && it !in files && mediaType(it).startsWith("image/") }.map { it.substringBeforeLast('.') }.toSet()
            val (asSounds, others) = media.partition { it in sounds || mediaType(it).startsWith("audio/") }
            val motions = others.filter { name -> MediaNames.stillOf(name)?.let { it in pictureStems } == true }

            return Uncleaned(
                pictures = others.filter { mediaType(it).startsWith("image/") }.toSet(),
                videos = others.filter { mediaType(it).startsWith("video/") && it !in motions }.toSet(),
                sounds = asSounds.toSet(),
                motions = motions.toSet(),
                files = ideaFiles,
                labels = labels.filterKeys { it in ideaFiles },
                stamps = stamps.filterKeys { it in uncleaned },
            )
        }

        private fun stemOf(motion: String): String? = MediaNames.stillOf(motion)
    }
}
