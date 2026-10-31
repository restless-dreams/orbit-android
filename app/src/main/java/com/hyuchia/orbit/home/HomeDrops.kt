package com.hyuchia.orbit.home

import android.content.ClipData
import android.content.ContentResolver
import android.view.DragEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.DropKind
import com.hyuchia.orbit.data.DroppedItem
import com.hyuchia.orbit.data.DroppedText
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MAP_TEXT_LIMIT
import com.hyuchia.orbit.data.dropKinds
import com.hyuchia.orbit.data.droppedItems
import com.hyuchia.orbit.data.droppedText
import com.hyuchia.orbit.data.fromProvider
import com.hyuchia.orbit.map.droppedMaps
import com.hyuchia.orbit.page.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the list of maps makes of a drag over it: maps from map files, or a map from text. */
enum class HomeDrop { Maps, Text }

/** What the list of maps would make of a drag offering `kinds`; null for one it doesn't take, such as pictures alone. */
fun homeDrop(kinds: Set<DropKind>): HomeDrop? = when {
    DropKind.Maps in kinds -> HomeDrop.Maps
    DropKind.Ideas in kinds -> HomeDrop.Text
    else -> null
}

/**
 * Maps and text dragged onto the list of maps from other apps: map files
 * (markdown, YAML, zips, OPML, FreeMind and Freeplane, XMind) come in as
 * "Open a file" brings them, several at once too, and text becomes a new
 * map, as text shared with Orbit does. Files are read with the permissions
 * the drag carries, held until they're in.
 */
@Stable
class HomeDrops internal constructor(private val start: (DragEvent, ClipData, () -> Unit) -> Unit) {
    /** What a drag going on would make here, while there's one the list takes. */
    var offered by mutableStateOf<HomeDrop?>(null)
        private set

    /** Whether that drag is over the list. */
    var over by mutableStateOf(false)
        private set

    /** Whether what was let go is coming in. */
    var bringingIn by mutableStateOf(false)
        private set

    /** Where the list is in its window: a drag is over it, and is let go on it, only there. */
    internal var place: Rect? = null

    internal fun accepts(event: DragAndDropEvent): Boolean {
        offered = homeDrop(dropKinds(event.toAndroidDragEvent().clipDescription))
        return offered != null
    }

    /** Whether the drag is over the list now, by where it is. */
    private fun isOver(event: DragAndDropEvent): Boolean {
        val drag = event.toAndroidDragEvent()
        return place?.contains(Offset(drag.x, drag.y)) ?: true
    }

    // Compose hands a drop to the last target the drag was over, which can still be the list when a quick drag goes
    // on to a map beside it: the light follows where the drag is, and only a drop let go over the list is the list's.
    internal val target = object : DragAndDropTarget {
        override fun onEntered(event: DragAndDropEvent) {
            over = isOver(event)
        }

        override fun onMoved(event: DragAndDropEvent) {
            over = isOver(event)
        }

        override fun onExited(event: DragAndDropEvent) {
            over = false
        }

        override fun onEnded(event: DragAndDropEvent) {
            over = false
            offered = null
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            over = false

            // Let go beside the list, on a locked private map next to it say, it's not the list's to take.
            if (!isOver(event)) {
                return false
            }

            val dragEvent = event.toAndroidDragEvent()
            val clip = dragEvent.clipData ?: return false
            bringingIn = true
            start(dragEvent, clip) { bringingIn = false }
            return true
        }
    }
}

/** Drops of maps and text on the list of maps, brought in and shown as `onImported` shows what "Open a file" brings. */
@Composable
fun rememberHomeDrops(onImported: (ImportResult) -> Unit): HomeDrops {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val imported by rememberUpdatedState(onImported)

    return remember(context, container) {
        HomeDrops { event, clip, done ->
            // Android lends the drag's files to the activity they were let go in, until they're released. Lending
            // them goes across processes too: when it fails, they're read without, and fail as unreadable files do.
            val permissions = fromProvider { context.findActivity()?.requestDragAndDropPermissions(event) }

            scope.launch {
                try {
                    bringIn(clip, context.contentResolver, container.importer, container.words)?.let(imported)
                } finally {
                    permissions?.release()
                    done()
                }
            }
        }
    }
}

/**
 * What was let go on the list, brought in: map files as maps, or else text
 * as a new map. A text file comes whole, as long as a map's text may be, as
 * one opened does ([MAP_TEXT_LIMIT]); a longer one is refused, saying so.
 */
private suspend fun bringIn(clip: ClipData, resolver: ContentResolver, importer: Importer, words: Words): ImportResult? {
    val items = withContext(Dispatchers.IO) { droppedItems(clip, resolver) }
    val files = items.filterIsInstance<DroppedItem.MapFile>()

    if (files.isNotEmpty()) {
        return droppedMaps(files.map { it.name to importer.importFile(it.address.toUri(), resolver) }, words)
    }

    val dropped = withContext(Dispatchers.IO) {
        droppedText(items, MAP_TEXT_LIMIT.toInt(), words.string(R.string.data_file_too_big_for_map), words) { address -> resolver.openInputStream(address.toUri()) }
    }

    return when {
        dropped is DroppedText.Refused -> ImportResult.Failed(dropped.reason)
        dropped is DroppedText.Text && dropped.text.isNotBlank() -> importer.importShared(dropped.text, subject = null)
        else -> ImportResult.Failed(words.string(R.string.drop_nothing_to_add))
    }
}

/** Takes drags of maps and text from other apps (see [HomeDrops]). */
fun Modifier.homeDropTarget(drops: HomeDrops): Modifier =
    onGloballyPositioned { drops.place = it.boundsInRoot() }
        .dragAndDropTarget(shouldStartDragAndDrop = drops::accepts, target = drops.target)

/**
 * Over the list while a drag it takes goes on: a zone that says what letting
 * go makes, quiet while the drag is elsewhere and lit up once it's over the
 * list, then turning while what was let go comes in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeDropZone(drops: HomeDrops, modifier: Modifier = Modifier) {
    val offered = drops.offered
    // The last thing offered stays while the zone leaves, and while what was let go comes in.
    val last = remember { LastOffered() }

    if (offered != null) {
        last.drop = offered
    }

    AnimatedVisibility(
        visible = offered != null || drops.bringingIn,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(240)),
        modifier = modifier,
    ) {
        val drop = last.drop ?: HomeDrop.Maps
        val lit = drops.over || drops.bringingIn
        val colors = MaterialTheme.colorScheme
        // Opaque enough that its words read over the maps' pictures, which still show faintly through.
        val fill by animateColorAsState(if (lit) colors.primaryContainer.copy(alpha = 0.96f) else colors.surfaceContainerHigh.copy(alpha = 0.93f), label = "fill")
        val outline by animateColorAsState(if (lit) colors.primary else colors.outline, label = "outline")
        val scale by animateFloatAsState(if (lit) 1f else 0.97f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "scale")
        val badge by animateColorAsState(if (lit) colors.primary else colors.secondaryContainer, label = "badge")
        val ink by animateColorAsState(if (lit) colors.onPrimary else colors.onSecondaryContainer, label = "ink")
        // The badge turns slowly once the drag is over the list, as planets drift.
        val turning = rememberInfiniteTransition(label = "turning")
        val turn by turning.animateFloat(0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart), label = "turn")

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(fill, RoundedCornerShape(32.dp))
                .drawBehind {
                    val width = (if (lit) 3 else 2).dp.toPx()
                    val dash = if (lit) null else PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx()))
                    drawRoundRect(outline, cornerRadius = CornerRadius(32.dp.toPx()), style = Stroke(width = width, pathEffect = dash))
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp).widthIn(max = 440.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer { rotationZ = if (lit && !drops.bringingIn) turn else 0f }
                        .background(badge, MaterialShapes.Cookie9Sided.toShape()),
                ) {
                    if (drops.bringingIn) {
                        LoadingIndicator(color = ink, modifier = Modifier.size(48.dp))
                    } else {
                        Icon(
                            if (drop == HomeDrop.Maps) Icons.Outlined.FileOpen else Icons.AutoMirrored.Outlined.NoteAdd,
                            contentDescription = null,
                            tint = ink,
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = if (lit) -turn else 0f },
                        )
                    }
                }

                Text(
                    stringResource(
                        when {
                            drops.bringingIn -> R.string.drop_home_bringing_in
                            drop == HomeDrop.Maps -> R.string.drop_home_maps_title
                            else -> R.string.drop_home_text_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (lit) colors.onPrimaryContainer else colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                if (!drops.bringingIn) {
                    Text(
                        stringResource(if (drop == HomeDrop.Maps) R.string.drop_home_maps_body else R.string.drop_home_text_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (lit) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** What the zone offered last, kept for it to leave with. */
private class LastOffered {
    var drop: HomeDrop? = null
}
