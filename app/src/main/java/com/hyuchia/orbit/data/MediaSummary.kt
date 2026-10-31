package com.hyuchia.orbit.data

/**
 * What goes with a map when it's shared, sent or shown: the pictures,
 * videos and audio its text points at in its media folder, by kind, and
 * how many bytes they come to. A picture named as a video is, with a
 * picture's extension (`video-1.jpg` beside `video-1.mp4`), is that video's
 * poster, and counts with its video. A live photo's motion
 * (`picture-1-motion.mp4` beside `picture-1.jpg`) is part of its picture
 * too: it's counted in `motions`, not as a video. A file's kind is its
 * extension's, unless the map's text says it's audio, as for audio kept in
 * an `.mp4`.
 */
data class MediaSummary(
    val pictures: Int = 0,
    val videos: Int = 0,
    val audio: Int = 0,
    val bytes: Long = 0,

    /** The live photos among the pictures: each one's motion goes with it, wherever it goes whole. */
    val motions: Int = 0,
) {
    val isEmpty: Boolean get() = count == 0

    /** How many files people would count: posters are part of their videos. */
    val count: Int get() = pictures + videos + audio

    companion object {
        /** The summary of a map's media files, by name, with their sizes; `audio` names those its text plays as audio. */
        fun of(files: Map<String, Long>, audio: Set<String> = emptySet()): MediaSummary {
            val kinds = files.keys.associateWith { name -> if (name in audio) MediaKindOfFile.Audio else kindOf(name) }
            val pictureStems = kinds.filterValues { it == MediaKindOfFile.Picture }.keys.map { it.substringBeforeLast('.') }.toSet()
            // A live photo's motion beside its picture: the picture counts it.
            val motions = kinds.filter { (name, kind) -> kind == MediaKindOfFile.Video && MediaNames.stillOf(name)?.let { it in pictureStems } == true }.keys
            val videos = kinds.filterValues { it == MediaKindOfFile.Video }.keys - motions
            val stems = videos.map { it.substringBeforeLast('.') }.toSet()
            val posters = kinds.filter { (name, kind) -> kind == MediaKindOfFile.Picture && name.substringBeforeLast('.') in stems }.keys

            return MediaSummary(
                pictures = kinds.count { (name, kind) -> kind == MediaKindOfFile.Picture && name !in posters },
                videos = videos.size,
                audio = kinds.count { it.value == MediaKindOfFile.Audio },
                bytes = files.values.filter { it > 0 }.sum(),
                motions = motions.size,
            )
        }

        /** What kind of file `name` is, by the media type its extension gives. */
        private fun kindOf(name: String): MediaKindOfFile? {
            val type = mediaType(name)

            return when {
                type.startsWith("image/") -> MediaKindOfFile.Picture
                type.startsWith("video/") -> MediaKindOfFile.Video
                type.startsWith("audio/") -> MediaKindOfFile.Audio
                else -> null
            }
        }
    }
}

private enum class MediaKindOfFile { Picture, Video, Audio }
