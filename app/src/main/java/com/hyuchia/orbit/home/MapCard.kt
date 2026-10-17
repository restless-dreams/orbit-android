package com.hyuchia.orbit.home

import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.theme.mapStyle
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.ui.OrbitGlyph
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * A map as Orbit draws it when it opens, in its own style or the app's
 * colors. Drawn off screen the first time, then kept until the map changes.
 */
@Composable
fun MapThumbnail(
    id: String,
    contentKey: String,
    style: String?,
    branches: Int,
    modifier: Modifier = Modifier,
    markdown: suspend () -> String,
) {
    val context = LocalContext.current
    val pictures = context.container.pictures
    val theme: AppTheme = MaterialTheme.colorScheme.mapTheme()
    val look = mapStyle(style)
    val background = if (look.name == null) MaterialTheme.colorScheme.surfaceContainerHighest else look.background

    val file by produceState<File?>(pictures.cachedThumbnail(id, contentKey, style, theme), id, contentKey, style, theme) {
        if (value == null) {
            value = pictures.thumbnail(id, contentKey, style, theme, markdown)
        }
    }

    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        AnimatedVisibility(file == null, enter = fadeIn(), exit = fadeOut()) {
            OrbitGlyph(branches.coerceAtLeast(3), Modifier.size(56.dp))
        }

        file?.let { picture ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(picture).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** What a card's menu can do with its map. */
data class MapCardActions(
    val onOpen: () -> Unit,
    val onPresent: () -> Unit,
    val onPin: () -> Unit,
    val onRename: () -> Unit,
    val onDuplicate: () -> Unit,
    val onShare: () -> Unit,
    val onSend: () -> Unit,
    val onDelete: () -> Unit,

    /** Puts a widget showing the map on the home screen; none where the launcher can't be asked to. */
    val onAddToHomeScreen: (() -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapCard(map: MapSummary, actions: MapCardActions, modifier: Modifier = Modifier, snippet: String? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    // Where a right click opened the menu, from the card's bottom start, as the menu places itself.
    var clickedMenu by remember { mutableStateOf<DpOffset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val pinned = map.metadata.pinned

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .onSizeChanged { size = it }
            // A right click opens the menu where it was clicked, and doesn't open the map.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            val at = event.changes.first().position
                            val x = if (direction == LayoutDirection.Ltr) at.x else size.width - at.x
                            clickedMenu = with(density) { DpOffset(x.toDp(), (at.y - size.height).toDp()) }
                        }
                    }
                }
            }
            // From the keyboard: the menu key or Shift+F10 open the menu, and Delete deletes.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }

                when {
                    event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed) -> {
                        menuOpen = true
                        true
                    }
                    event.key == Key.Delete -> {
                        actions.onDelete()
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                onClickLabel = stringResource(R.string.common_open),
                onLongClickLabel = stringResource(R.string.home_more_options),
                onClick = actions.onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(resources.getString(if (pinned) R.string.home_unpin else R.string.home_pin)) { actions.onPin(); true },
                    CustomAccessibilityAction(resources.getString(R.string.common_rename)) { actions.onRename(); true },
                    CustomAccessibilityAction(resources.getString(R.string.common_duplicate)) { actions.onDuplicate(); true },
                    CustomAccessibilityAction(resources.getString(R.string.home_share)) { actions.onShare(); true },
                    CustomAccessibilityAction(resources.getString(R.string.common_delete)) { actions.onDelete(); true },
                ) + listOfNotNull(
                    actions.onAddToHomeScreen?.let { add -> CustomAccessibilityAction(resources.getString(R.string.home_add_to_home_screen)) { add(); true } },
                )
            },
    ) {
        Column {
            MapCardMenu(clickedMenu != null, pinned, actions, offset = clickedMenu ?: DpOffset.Zero) { clickedMenu = null }

            Box {
                val frame = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(19.dp))

                if (map.unreadable) {
                    UnreadableThumbnail(frame)
                } else {
                    MapThumbnail(
                        id = map.id,
                        contentKey = map.contentKey,
                        style = map.outline.style,
                        branches = map.outline.branches,
                        modifier = frame,
                    ) { context.container.maps.read(map.id) }
                }

                if (pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.home_pinned),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(6.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 12.dp)) {
                Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(map.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        snippet ?: if (map.unreadable) stringResource(R.string.home_unreadable_map) else describe(resources, map),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (snippet != null) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val progress = map.outline.progress

                    if (progress.total > 0 && snippet == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            // As the map's rings fill: goals by how far along they are.
                            LinearWavyProgressIndicator(progress = { progress.share.toFloat() }, modifier = Modifier.weight(1f))
                            // Goals are reached, to-dos done: when every one is a goal, they're all reached.
                            val allGoals = progress.goals == progress.total
                            Text(
                                if (progress.finished == progress.total) {
                                    pluralStringResource(if (allGoals) R.plurals.home_progress_all_reached else R.plurals.home_progress_all_done, progress.total, progress.total)
                                } else {
                                    // The words agree with how many are finished: "1 de 3 hecha".
                                    pluralStringResource(
                                        if (allGoals) R.plurals.home_progress_reached else R.plurals.home_progress_done,
                                        progress.finished,
                                        progress.finished,
                                        progress.total,
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.home_more_options_for_map, map.title))
                    }

                    MapCardMenu(menuOpen, pinned, actions) { menuOpen = false }
                }
            }
        }
    }
}

@Composable
private fun MapCardMenu(open: Boolean, pinned: Boolean, actions: MapCardActions, offset: DpOffset = DpOffset.Zero, onDismiss: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, offset = offset) {
        MenuItem(stringResource(R.string.common_open), Icons.AutoMirrored.Outlined.OpenInNew) { onDismiss(); actions.onOpen() }
        MenuItem(stringResource(R.string.common_present), Icons.Outlined.Slideshow) { onDismiss(); actions.onPresent() }
        MenuItem(stringResource(if (pinned) R.string.home_unpin else R.string.home_pin_to_the_top), Icons.Outlined.PushPin) { onDismiss(); actions.onPin() }

        actions.onAddToHomeScreen?.let { add ->
            MenuItem(stringResource(R.string.home_add_to_home_screen), Icons.Outlined.Widgets) { onDismiss(); add() }
        }

        MenuItem(stringResource(R.string.common_rename), Icons.Outlined.DriveFileRenameOutline) { onDismiss(); actions.onRename() }
        MenuItem(stringResource(R.string.common_duplicate), Icons.Outlined.ContentCopy) { onDismiss(); actions.onDuplicate() }
        MenuItem(stringResource(R.string.home_send_to_a_device), Icons.Outlined.Devices) { onDismiss(); actions.onSend() }
        MenuItem(stringResource(R.string.home_share), Icons.Outlined.Share) { onDismiss(); actions.onShare() }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_delete)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error),
            onClick = { onDismiss(); actions.onDelete() },
        )
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

/** In place of the picture of a map whose file can't be read. */
@Composable
private fun UnreadableThumbnail(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(R.string.home_unreadable_map_file),
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** "12 ideas · 2 hours ago", in the app's language. */
fun describe(resources: Resources, map: MapSummary): String {
    val ideas = map.outline.ideas
    return resources.getQuantityString(R.plurals.home_map_summary, ideas, ideas, timeAgo(resources, map.modified).text)
}

/** A moment in the past in words for the middle of a sentence; `isDate` when they're its date rather than how long ago it was. */
class TimeAgo(val text: String, val isDate: Boolean)

/**
 * How long ago `time` was, in the app's language: "just now", "5 minutes
 * ago", "yesterday", "3 days ago", or the date once it's a week or more.
 * Days are counted as the calendar counts them, as Android's own relative
 * times are.
 */
fun timeAgo(resources: Resources, time: Long, now: Long = System.currentTimeMillis()): TimeAgo {
    val elapsed = now - time
    val zone = ZoneId.systemDefault()

    return when {
        elapsed < DateUtils.MINUTE_IN_MILLIS -> TimeAgo(resources.getString(R.string.home_just_now), isDate = false)
        elapsed < DateUtils.HOUR_IN_MILLIS -> {
            val minutes = (elapsed / DateUtils.MINUTE_IN_MILLIS).toInt()
            TimeAgo(resources.getQuantityString(R.plurals.home_minutes_ago, minutes, minutes), isDate = false)
        }
        elapsed < DateUtils.DAY_IN_MILLIS -> {
            val hours = (elapsed / DateUtils.HOUR_IN_MILLIS).toInt()
            TimeAgo(resources.getQuantityString(R.plurals.home_hours_ago, hours, hours), isDate = false)
        }
        elapsed < DateUtils.WEEK_IN_MILLIS -> {
            val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(time).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate()).toInt()

            if (days <= 1) {
                TimeAgo(resources.getString(R.string.home_yesterday), isDate = false)
            } else {
                TimeAgo(resources.getQuantityString(R.plurals.home_days_ago, days, days), isDate = false)
            }
        }
        else -> {
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0])
            TimeAgo(format.format(Instant.ofEpochMilli(time).atZone(zone)), isDate = true)
        }
    }
}

/** The empty card shape shown while maps load. */
@Composable
fun MapCardPlaceholder(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Box(
                Modifier
                    .padding(16.dp)
                    .size(width = 120.dp, height = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
            )
        }
    }
}
