package com.hyuchia.orbit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.isVoiceMemo
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a page of the viewer shows: an idea's picture, its video, or its
 * sound; `image`, `video` and `audio` to Orbit. An audience's screen is only
 * ever sent pictures and videos: a sound plays from the presenter's phone.
 */
@Serializable
enum class MediaKind {
    @SerialName("image")
    Picture,

    @SerialName("video")
    Video,

    @SerialName("audio")
    Sound,
}

/**
 * A picture, video or sound of the map, as the viewer shows it: whose it is,
 * that idea's name, and for a sound, how its idea looks, for its art.
 */
data class GalleryItem(val nodeId: String, val kind: MediaKind, val media: Media, val label: String, val look: IdeaLook? = null) {
    /** Stays the same for the same picture, video or sound, whatever comes before it. */
    val key: String get() = "$nodeId/$kind"
}

/** How an idea looks, for its sound's art: the color the map draws it with, its icon, and its picture's address. */
data class IdeaLook(val color: String?, val icon: String?, val picture: String?)

private fun MapNode.look(): IdeaLook = IdeaLook(drawnColor, icon, image?.src?.takeIf { it.isNotBlank() })

/**
 * The map's pictures, videos and sounds in reading order, the outline's: the
 * top first, then each idea before the ideas under it, its picture before
 * its video, and its video before its sound. `nodes` are the map's in that
 * order, as the page reports them; `untitled` names ideas without text.
 */
fun galleryOf(nodes: List<MapNode>, untitled: String): List<GalleryItem> = nodes.flatMap { node ->
    listOfNotNull(
        node.image?.takeIf { it.src.isNotBlank() }?.let { GalleryItem(node.id, MediaKind.Picture, it, node.label(untitled)) },
        node.video?.takeIf { it.src.isNotBlank() }?.let { GalleryItem(node.id, MediaKind.Video, it, node.label(untitled)) },
        node.audio?.takeIf { it.src.isNotBlank() }?.let { GalleryItem(node.id, MediaKind.Sound, it, node.label(untitled), node.look()) },
    )
}

/** `node`'s sound, as the viewer and the players show it; null when it has none. */
fun soundOf(node: MapNode, untitled: String): GalleryItem? =
    node.audio?.takeIf { it.src.isNotBlank() }?.let { GalleryItem(node.id, MediaKind.Sound, it, node.label(untitled), node.look()) }

/** Where `nodeId`'s picture, video or sound is, preferring `kind`; the start when it has none. */
fun List<GalleryItem>.indexOf(nodeId: String, kind: MediaKind): Int {
    val exact = indexOfFirst { it.nodeId == nodeId && it.kind == kind }
    return if (exact >= 0) exact else indexOfFirst { it.nodeId == nodeId }.coerceAtLeast(0)
}

/** What a page says it is, for screen readers: its description, else its caption, else whose it is. */
@Composable
@ReadOnlyComposable
fun GalleryItem.description(): String = givenDescription() ?: when (kind) {
    MediaKind.Picture -> stringResource(R.string.media_picture_of, label)
    MediaKind.Video -> stringResource(R.string.media_video_of, label)
    MediaKind.Sound -> stringResource(R.string.audio_sound_of, label)
}

/** What the map says a page is, when it says: a picture's description, else the caption. */
fun GalleryItem.givenDescription(): String? = when (kind) {
    MediaKind.Picture -> media.alt?.takeIf { it.isNotBlank() } ?: media.caption?.takeIf { it.isNotBlank() }
    MediaKind.Video, MediaKind.Sound -> media.caption?.takeIf { it.isNotBlank() }
}

/** What a sound is called: its caption, else "Voice memo" for one recorded in Orbit, else "Sound". */
@Composable
@ReadOnlyComposable
fun GalleryItem.soundTitle(): String = media.caption?.takeIf { it.isNotBlank() }
    ?: stringResource(if (isVoiceMemo(media.src)) R.string.audio_voice_memo else R.string.audio_sound)
