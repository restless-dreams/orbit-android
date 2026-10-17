package com.hyuchia.orbit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a page of the viewer shows: an idea's picture, or its video; `image` and `video` to Orbit and an audience's screen. */
@Serializable
enum class MediaKind {
    @SerialName("image")
    Picture,

    @SerialName("video")
    Video,
}

/** A picture or video of the map, as the viewer shows it: whose it is, and that idea's name. */
data class GalleryItem(val nodeId: String, val kind: MediaKind, val media: Media, val label: String) {
    /** Stays the same for the same picture or video, whatever comes before it. */
    val key: String get() = "$nodeId/$kind"
}

/**
 * The map's pictures and videos in reading order, the outline's: the top
 * first, then each idea before the ideas under it, its picture before its
 * video. `nodes` are the map's in that order, as the page reports them;
 * `untitled` names ideas without text.
 */
fun galleryOf(nodes: List<MapNode>, untitled: String): List<GalleryItem> = nodes.flatMap { node ->
    listOfNotNull(
        node.image?.takeIf { it.src.isNotBlank() }?.let { GalleryItem(node.id, MediaKind.Picture, it, node.label(untitled)) },
        node.video?.takeIf { it.src.isNotBlank() }?.let { GalleryItem(node.id, MediaKind.Video, it, node.label(untitled)) },
    )
}

/** Where `nodeId`'s picture or video is, preferring `kind`; the start when it has neither. */
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
}

/** What the map says a page is, when it says: a picture's description, else the caption. */
fun GalleryItem.givenDescription(): String? = when (kind) {
    MediaKind.Picture -> media.alt?.takeIf { it.isNotBlank() } ?: media.caption?.takeIf { it.isNotBlank() }
    MediaKind.Video -> media.caption?.takeIf { it.isNotBlank() }
}
