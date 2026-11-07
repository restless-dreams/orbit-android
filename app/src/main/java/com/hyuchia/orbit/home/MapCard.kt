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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
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
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.privacy.CoverMenuItem
import com.hyuchia.orbit.privacy.PrivateBadge
import com.hyuchia.orbit.privacy.PrivateMenuItem
import com.hyuchia.orbit.study.dueCount
import com.hyuchia.orbit.theme.mapStyle
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.OrbitGlyph
import com.hyuchia.orbit.ui.TimeWords
import com.hyuchia.orbit.ui.WavyProgress
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A map as Orbit draws it when it opens, in its own style or the app's
 * colors. Drawn off screen the first time, then kept until the map changes:
 * its text, or the media it has, by `pictureKey` ([com.hyuchia.orbit.data.MapSummary.pictureKey]).
 */
@Composable
fun MapThumbnail(
    id: String,
    pictureKey: String,
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

    val file by rememberThumbnail(
        listOf(id, pictureKey, style, theme),
        cached = { pictures.cachedThumbnail(id, pictureKey, style, theme) },
        draw = { pictures.thumbnail(id, pictureKey, style, theme, markdown) },
    )

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

/**
 * The picture of a map for `key`: the one drawn for it before, else drawn
 * now. A new key, as when the map changes while it shows, draws it again,
 * and the picture drawn last stays until the new one is ready, so the map
 * never flashes its glyph meanwhile.
 */
@Composable
internal fun rememberThumbnail(key: Any, cached: () -> File?, draw: suspend () -> File?): State<File?> {
    val ready = remember(key) { cached() }
    return produceState(ready, key) { value = ready ?: draw() }
}

/** What a card's menu can do with its map. */
data class MapCardActions(
    val onOpen: () -> Unit,
    val onPresent: () -> Unit,
    val onStudy: () -> Unit,
    val onPin: () -> Unit,
    val onRename: () -> Unit,
    val onTags: () -> Unit,
    val onDuplicate: () -> Unit,
    val onShare: () -> Unit,
    val onSend: () -> Unit,
    val onDelete: () -> Unit,

    /** Puts a widget showing the map on the home screen; none where the launcher can't be asked to. */
    val onAddToHomeScreen: (() -> Unit)? = null,

    /** Makes the map private, or not: see [com.hyuchia.orbit.privacy.PrivateMaps]. */
    val onPrivate: ((Boolean) -> Unit)? = null,

    /** Chooses what the map shows while it's locked, while it's private (see [com.hyuchia.orbit.privacy.CoverDialog]). */
    val onCover: (() -> Unit)? = null,

    /** Locks every private map now, offered while the map is private: its card is unlocked, or it wouldn't be this one. */
    val onLockNow: (() -> Unit)? = null,
)

/** A card's corners on Home, its locked card's too. */
internal val CardShape = RoundedCornerShape(24.dp)

/**
 * A map's card on Home: its picture, its name and what it holds, and how
 * many of its cards are due `today`, as study counts days (see
 * [com.hyuchia.orbit.study.rememberStudyDay]). A search's card shows where
 * it found the words (`snippet`) instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapCard(map: MapSummary, actions: MapCardActions, today: Long, modifier: Modifier = Modifier, snippet: String? = null) {
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
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
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
                interactionSource = null,
                // With the keyboard, the focus ring follows the card's corners.
                indication = ripple(focusRingShape = CardShape),
                onClickLabel = stringResource(R.string.common_open),
                onLongClickLabel = stringResource(R.string.home_more_options),
                onClick = actions.onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            )
            .semantics {
                // A group, so screen readers hear the card before its menu button rather than after it.
                isTraversalGroup = true
                // What the menu offers, in its order, Open aside: that's the card's own tap.
                customActions = listOfNotNull(
                    CustomAccessibilityAction(resources.getString(R.string.common_present)) { actions.onPresent(); true },
                    CustomAccessibilityAction(resources.getString(R.string.study_study)) { actions.onStudy(); true },
                    CustomAccessibilityAction(resources.getString(if (pinned) R.string.home_unpin else R.string.home_pin)) { actions.onPin(); true },
                    actions.onAddToHomeScreen?.let { add -> CustomAccessibilityAction(resources.getString(R.string.home_add_to_home_screen)) { add(); true } },
                    CustomAccessibilityAction(resources.getString(R.string.common_rename)) { actions.onRename(); true },
                    CustomAccessibilityAction(resources.getString(R.string.common_tags)) { actions.onTags(); true },
                    actions.onPrivate?.let { change ->
                        CustomAccessibilityAction(resources.getString(if (map.metadata.isPrivate) R.string.private_make_not_private else R.string.private_make_private)) {
                            change(!map.metadata.isPrivate)
                            true
                        }
                    },
                    actions.onCover?.takeIf { map.metadata.isPrivate }?.let { cover ->
                        CustomAccessibilityAction(resources.getString(R.string.private_cover_while_locked)) { cover(); true }
                    },
                    actions.onLockNow?.takeIf { map.metadata.isPrivate }?.let { lock ->
                        CustomAccessibilityAction(resources.getString(R.string.private_lock_private_maps_now)) { lock(); true }
                    },
                    CustomAccessibilityAction(resources.getString(R.string.common_duplicate)) { actions.onDuplicate(); true },
                    CustomAccessibilityAction(resources.getString(R.string.home_send_to_a_device)) { actions.onSend(); true },
                    CustomAccessibilityAction(resources.getString(R.string.home_share)) { actions.onShare(); true },
                    CustomAccessibilityAction(resources.getString(R.string.common_delete)) { actions.onDelete(); true },
                )
            },
    ) {
        // The badges are drawn over the picture but come after the words, so screen readers hear the map's name first.
        Box {
            Column {
                MapCardMenu(clickedMenu != null, pinned, map.metadata.isPrivate, map.metadata.cover, actions, offset = clickedMenu ?: DpOffset.Zero) { clickedMenu = null }

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
                        pictureKey = map.pictureKey,
                        style = map.outline.style,
                        branches = map.outline.branches,
                        modifier = frame,
                    ) { context.container.maps.read(map.id) }
                }

                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 12.dp)) {
                    Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Two lines each, so a long title or larger text still says what the map is.
                        Text(map.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            snippet ?: if (map.unreadable) stringResource(R.string.home_unreadable_map) else describe(resources, map),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val tags = map.outline.tags

                        if (tags.isNotEmpty()) {
                            val said = stringResource(R.string.ui_tags_list, tags.joinToString(", "))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clearAndSetSemantics { contentDescription = said },
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Cards studied on the other devices in the folder count too: each is due as its latest answer says. Ideas
                        // that aren't cards anymore don't, as no session studies them; and `today` moves on at midnight.
                        val due = remember(map.metadata.study, map.metadata.studyElsewhere, map.metadata.studyNotCards, today) { dueCount(map.metadata.studyCounted, today) }

                        if (due > 0 && snippet == null) {
                            DueCards(due)
                        }

                        val progress = map.outline.progress

                        if (progress.total > 0 && snippet == null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                // As the map's rings fill: goals by how far along they are. Screen readers hear the words beside
                                // it with the card, rather than stopping again on the line.
                                WavyProgress(progress = { progress.share.toFloat() }, modifier = Modifier.weight(1f).clearAndSetSemantics {})
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
                        Hint(stringResource(R.string.home_more_options)) {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.home_more_options_for_map, map.title))
                            }
                        }

                        MapCardMenu(menuOpen, pinned, map.metadata.isPrivate, map.metadata.cover, actions) { menuOpen = false }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) {
                if (map.metadata.isPrivate) {
                    PrivateBadge()
                }

                if (pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.home_pinned),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

/** How many of the map's cards are due in study mode, under its title: a light reminder, never a badge to clear. */
@Composable
private fun DueCards(due: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
        Text(pluralStringResource(R.plurals.study_due, due, due), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
    }
}

@Composable
private fun MapCardMenu(open: Boolean, pinned: Boolean, private: Boolean, cover: LockedCover?, actions: MapCardActions, offset: DpOffset = DpOffset.Zero, onDismiss: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, offset = offset) {
        MenuItem(stringResource(R.string.common_open), Icons.AutoMirrored.Outlined.OpenInNew) { onDismiss(); actions.onOpen() }
        MenuItem(stringResource(R.string.common_present), Icons.Outlined.Slideshow) { onDismiss(); actions.onPresent() }
        MenuItem(stringResource(R.string.study_study), Icons.Outlined.School) { onDismiss(); actions.onStudy() }
        MenuItem(stringResource(if (pinned) R.string.home_unpin else R.string.home_pin_to_the_top), Icons.Outlined.PushPin) { onDismiss(); actions.onPin() }

        actions.onAddToHomeScreen?.let { add ->
            MenuItem(stringResource(R.string.home_add_to_home_screen), Icons.Outlined.Widgets) { onDismiss(); add() }
        }

        MenuItem(stringResource(R.string.common_rename), Icons.Outlined.DriveFileRenameOutline) { onDismiss(); actions.onRename() }
        MenuItem(stringResource(R.string.common_tags), Icons.AutoMirrored.Outlined.Label) { onDismiss(); actions.onTags() }

        actions.onPrivate?.let { change ->
            PrivateMenuItem(private, onChange = change, onDismiss = onDismiss)
        }

        actions.onCover?.takeIf { private }?.let { choose ->
            CoverMenuItem(cover) { onDismiss(); choose() }
        }

        actions.onLockNow?.takeIf { private }?.let { lock ->
            val everyPrivateMap = stringResource(R.string.private_lock_private_maps_now)

            DropdownMenuItem(
                text = { Text(stringResource(R.string.private_menu_lock_now)) },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                onClick = { onDismiss(); lock() },
                // Every private map locks, not this one alone: screen readers say so.
                modifier = Modifier.semantics { contentDescription = everyPrivateMap },
            )
        }

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
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
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
        // A day has no clock, so the phone's 12- or 24-hour setting doesn't matter here.
        else -> TimeAgo(TimeWords(resources.configuration.locales[0], is24Hour = false, zone).date(time), isDate = true)
    }
}

/** The empty card shape shown while maps load. */
@Composable
fun MapCardPlaceholder(modifier: Modifier = Modifier) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
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
