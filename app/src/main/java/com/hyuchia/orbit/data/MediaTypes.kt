package com.hyuchia.orbit.data

/** The types of pictures a map's media folder keeps, by extension. */
private val PICTURE_TYPES = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "heic" to "image/heic",
    "heif" to "image/heif",
    "bmp" to "image/bmp",
    "svg" to "image/svg+xml",
)

/** The types of videos a map's media folder keeps, by extension. */
private val VIDEO_TYPES = mapOf(
    "mp4" to "video/mp4",
    "m4v" to "video/mp4",
    "webm" to "video/webm",
    "mov" to "video/quicktime",
)

/**
 * The pictures, videos and sounds a map's media folder keeps (sounds are in
 * MapSounds.kt). A map from someone else can't bring a page or a script
 * along: the map's page shares an origin with its media, so a file it could
 * open as a page would be on the app's side.
 */
private val MEDIA_EXTENSIONS = PICTURE_TYPES.keys + VIDEO_TYPES.keys + SOUND_TYPES.keys

/** The extensions of the files a map's media folder keeps: what this version carries in zips, backups and the folder maps are kept in. */
internal fun carriedMediaTypes(): Set<String> = MEDIA_EXTENSIONS

/** Whether a file may be in a map's media folder: a safe name, and a picture's, video's or sound's type. */
internal fun isMediaName(name: String): Boolean =
    MediaNames.NAME.matches(name) && name.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS

/** Whether a name in a map's media folder is a video's. */
fun isVideoName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_TYPES

/** The media type of a file in a map's media folder, by its name. */
fun mediaType(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return PICTURE_TYPES[extension] ?: VIDEO_TYPES[extension] ?: SOUND_TYPES[extension] ?: "application/octet-stream"
}
