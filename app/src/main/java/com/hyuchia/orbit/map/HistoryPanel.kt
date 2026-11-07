package com.hyuchia.orbit.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.data.VersionChanges
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.ui.TimeWords
import com.hyuchia.orbit.ui.rememberTimeWords
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

/** Past this many ideas of one kind, a change is counted rather than named: "Added 24 ideas". */
private const val NAMED = 10

/** The longest an idea's name gets in a summary; a longer one is cut short. */
private const val NAME_LENGTH = 40

/** Between the kinds of change in a summary, as written and as TalkBack reads it. */
private const val SHOWN_SEPARATOR = " · "
private const val SPOKEN_SEPARATOR = ". "

/** The timeline's width beside each version, and its line's. */
private val TimelineWidth = 56.dp
private val LineWidth = 2.dp

/**
 * The map's earlier versions, by day, each answering at a glance when it's
 * from, why it was kept and what changed from the version before it.
 * Choosing one shows it on the map, to look around in; Restore makes it the
 * map again, and closing the panel brings back the map as it is now.
 */
@Composable
fun HistoryPanel(model: MapViewModel, ui: MapUi, sheet: Boolean) {
    val history = ui.history ?: return

    PanelFrame(
        title = stringResource(R.string.common_version_history),
        sheet = sheet,
        onDismiss = { model.show(null) },
        partial = true,
        scrim = false,
        actions = {
            // Restore comes and goes in a header already its height, so the list under it stays put.
            Spacer(Modifier.height(48.dp))

            AnimatedVisibility(
                visible = history.showing != null,
                enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f),
            ) {
                FilledTonalButton(onClick = { history.showing?.let(model::restoreVersion) }, modifier = Modifier.padding(end = 4.dp)) { Text(stringResource(R.string.common_restore)) }
            }
        },
    ) {
        val versions = history.versions

        if (versions == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            VersionList(versions, history, onShow = model::showVersion, onSummarize = model::summarizeVersion)
        }
    }
}

@Composable
private fun VersionList(versions: List<MapVersion>, history: HistoryUi, onShow: (Long?) -> Unit, onSummarize: (Long) -> Unit) {
    // Times, and the days they're grouped by, follow the phone's time zone as it changes.
    val words = rememberTimeWords()
    val zone = words.zone

    // Times within the hour say how long ago: they move on while the panel is open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE / 2)
            value = System.currentTimeMillis()
        }
    }

    val today = day(now, zone)
    val days = versions.groupBy { day(it.time, zone) }
    val last = versions.lastOrNull()?.time

    // Beside the map on a tablet, the list starts afresh once the panes stop moving (see PaneListState).
    val pane = rememberPaneListState()

    key(pane.list) {
        LazyColumn(state = pane.list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.paneList(pane).selectableGroup()) {
            item(key = "now") {
                val summary = history.now
                // A file as it came in reads differently: the map now is compared with Orbit's own versions only.
                val compared = versions.any { !it.original }

                // What changed since the latest version, said the way each version says what it changed.
                fun detail(separator: String): ShownText? = when (summary) {
                    is VersionSummary.Changed -> if (summary.changes.none) ShownText.Resource(R.string.map_history_no_changes) else ShownText.Joined(changeWords(summary.changes), separator)
                    is VersionSummary.Holds -> ShownText.Plural(R.plurals.map_ideas, summary.ideas)
                    else -> null
                }

                VersionRow(
                    time = stringResource(R.string.map_history_now),
                    reason = if (compared) stringResource(R.string.map_history_since_latest) else null,
                    detail = detail(SHOWN_SEPARATOR)?.text(),
                    spokenDetail = detail(SPOKEN_SEPARATOR)?.text(),
                    working = versions.isNotEmpty() && summary == null,
                    icon = Icons.Outlined.Hub,
                    selected = history.showing == null,
                    top = true,
                    bottom = versions.isEmpty(),
                    onClick = { onShow(null) },
                )
            }

            if (versions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.map_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = TimelineWidth + 8.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                    )
                }
            }

            days.forEach { (date, dayVersions) ->
                val day = dayHeading(date, today, words)

                stickyHeader(key = "day-$date") {
                    DayHeading(day.text())
                }

                items(dayVersions, key = { it.time }) { version ->
                    LaunchedEffect(version.time) { onSummarize(version.time) }
                    val summary = history.summaries[version.time]
                    val unreadable = version.time in history.unreadable || summary == VersionSummary.Unreadable

                    fun detail(separator: String): ShownText? = when (summary) {
                        is VersionSummary.Changed -> summaryWords(summary.changes, separator)
                        is VersionSummary.Holds -> ShownText.Plural(R.plurals.map_ideas, summary.ideas)
                        else -> null
                    }

                    VersionRow(
                        time = whenWords(version.time, now, words).text(),
                        day = day.text(),
                        reason = reasonWords(knownReason(version), now, words)?.text(),
                        detail = detail(SHOWN_SEPARATOR)?.text(),
                        spokenDetail = detail(SPOKEN_SEPARATOR)?.text(),
                        problem = if (unreadable) stringResource(R.string.map_history_version_unreadable) else null,
                        working = summary == null && !unreadable,
                        icon = reasonIcon(version),
                        selected = history.showing == version.time,
                        bottom = version.time == last,
                        onClick = { onShow(version.time) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** A day's heading, staying at the top while its versions scroll under it. */
@Composable
private fun DayHeading(day: String) {
    val colors = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .timeline(colors.outlineVariant, top = true, bottom = true),
    ) {
        Text(
            day,
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier
                .padding(start = TimelineWidth + 8.dp, end = 24.dp, top = 14.dp, bottom = 6.dp)
                .semantics { heading() },
        )
    }
}

/**
 * A version on the timeline: when (`time`), why it was kept (`reason`), and
 * what changed (`detail`, as TalkBack reads it `spokenDetail`, or a quiet bar
 * while it's `working` out), or the `problem` that keeps it from being shown.
 * The selected one is on the map: its badge grows into a cookie, and its row
 * takes the selection's color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionRow(
    time: String,
    reason: String?,
    detail: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spokenDetail: String? = detail,
    day: String? = null,
    problem: String? = null,
    working: Boolean = false,
    top: Boolean = false,
    bottom: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, label = "container")
    val corner by animateDpAsState(if (selected) 28.dp else 16.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "corner")
    val badge by animateDpAsState(if (selected) 40.dp else 32.dp, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "badge")
    val badgeColor by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, label = "badgeColor")
    val iconColor by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, label = "iconColor")
    val shown = stringResource(R.string.map_history_shown)
    val show = stringResource(R.string.map_history_show)
    val workingOut = stringResource(R.string.map_history_working_out)
    // Read as one: when, the day, why, and what changed.
    val spoken = listOfNotNull(time, day, reason, problem ?: spokenDetail ?: workingOut.takeIf { working }).joinToString(SPOKEN_SEPARATOR)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .timeline(colors.outlineVariant, top = !top, bottom = !bottom),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TimelineWidth, end = 8.dp)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    stateDescription = if (selected) shown else ""

                    onClick(label = show) {
                        onClick()
                        true
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(time, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onSecondaryContainer else colors.onSurface)

                if (reason != null) {
                    Text(reason, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onSecondaryContainer else colors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                AnimatedContent(
                    targetState = Triple(problem, detail, working),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "detail",
                ) { (problem, detail, working) ->
                    when {
                        problem != null -> Text(problem, style = MaterialTheme.typography.bodyMedium, color = colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        detail != null -> Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        working -> WorkingBar()
                        else -> Box(Modifier.height(0.dp))
                    }
                }
            }
        }

        // On the timeline, over its line, in the middle of the row.
        Box(Modifier.width(TimelineWidth).align(Alignment.CenterStart), contentAlignment = Alignment.Center) {
            TimelineBadge(icon, badge, badgeColor, iconColor, selected)
        }
    }
}

/** A quiet bar where what changed will show, breathing while it's worked out. */
@Composable
private fun WorkingBar() {
    val breathing = rememberInfiniteTransition(label = "working")
    val alpha by breathing.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")

    Box(
        Modifier
            .padding(top = 6.dp, bottom = 4.dp)
            .width(160.dp)
            .height(10.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** The version's badge on the timeline: why it was kept, in its icon; the selected one, a cookie in the primary color. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimelineBadge(icon: ImageVector, size: Dp, color: Color, iconColor: Color, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
            .background(color),
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
    }
}

/** The timeline's line down the start of an item: from its top (`top`) and to its bottom (`bottom`), through its middle. */
private fun Modifier.timeline(color: Color, top: Boolean, bottom: Boolean): Modifier = drawBehind {
    val x = (TimelineWidth / 2).toPx()
    val width = LineWidth.toPx()
    val middle = size.height / 2

    if (top) {
        drawLine(color, Offset(x, 0f), Offset(x, middle), width)
    }

    if (bottom) {
        drawLine(color, Offset(x, middle), Offset(x, size.height), width)
    }
}

/** The icon for why a version was kept; an earlier version's without a reason is history's own. */
private fun reasonIcon(version: MapVersion): ImageVector = when (version.reason) {
    VersionReason.Opened -> Icons.Outlined.FileOpen
    VersionReason.Editing -> Icons.Outlined.Edit
    is VersionReason.Removing -> Icons.Outlined.DeleteSweep
    is VersionReason.Restoring -> Icons.Outlined.Restore
    VersionReason.Text -> Icons.Outlined.Code
    VersionReason.Caught -> Icons.Outlined.Bolt
    VersionReason.Tags -> Icons.AutoMirrored.Outlined.Label
    VersionReason.Renaming -> Icons.Outlined.DriveFileRenameOutline
    is VersionReason.FromDevice -> Icons.Outlined.Devices
    is VersionReason.FromFile -> Icons.Outlined.FolderZip
    VersionReason.FromFolder -> Icons.Outlined.Sync
    VersionReason.Elsewhere -> Icons.Outlined.Update
    VersionReason.Aside -> Icons.Outlined.SaveAs
    is VersionReason.CameIn -> Icons.Outlined.Description
    null -> if (version.original) Icons.Outlined.Description else Icons.Outlined.History
}

/** Why a version was kept, where it's known: as recorded, or a file as it came in, which its name says even from before reasons were kept. */
internal fun knownReason(version: MapVersion): VersionReason? = version.reason ?: VersionReason.CameIn(null).takeIf { version.original }

/** When a version was kept: "Just now" and "12 minutes ago" within the hour, then its time ("4:49 PM", "16:49"), under its day. */
internal fun whenWords(time: Long, now: Long, words: TimeWords): ShownText {
    val elapsed = now - time

    return when {
        elapsed in 0 until MINUTE -> ShownText.Resource(R.string.map_history_just_now)
        elapsed in MINUTE until HOUR -> (elapsed / MINUTE).toInt().let { ShownText.Plural(R.plurals.map_history_minutes_ago, it) }
        else -> ShownText.Plain(words.clock(time))
    }
}

/** Why a version was kept, in words, as of `now`; null for one from before reasons were kept. */
internal fun reasonWords(reason: VersionReason?, now: Long, words: TimeWords, zone: ZoneId = words.zone): ShownText? = when (reason) {
    VersionReason.Opened -> ShownText.Resource(R.string.map_history_why_opened)
    VersionReason.Editing -> ShownText.Resource(R.string.map_history_why_editing)
    is VersionReason.Removing -> if (reason.under > 0) {
        ShownText.Plural(R.plurals.map_history_why_removing, reason.under, listOf(reason.under, short(reason.idea)))
    } else {
        ShownText.Resource(R.string.map_history_why_removing_alone, listOf(short(reason.idea)))
    }
    is VersionReason.Restoring -> restoringWords(reason.from, now, words, zone)
    VersionReason.Text -> ShownText.Resource(R.string.map_history_why_text)
    VersionReason.Caught -> ShownText.Resource(R.string.map_history_why_caught)
    VersionReason.Tags -> ShownText.Resource(R.string.map_history_why_tags)
    VersionReason.Renaming -> ShownText.Resource(R.string.map_history_why_renaming)
    is VersionReason.FromDevice -> ShownText.Resource(R.string.map_history_why_from, listOf(reason.device))
    is VersionReason.FromFile -> reason.file?.let { ShownText.Resource(R.string.map_history_why_from, listOf(it)) } ?: ShownText.Resource(R.string.map_history_why_from_a_file)
    VersionReason.FromFolder -> ShownText.Resource(R.string.map_history_why_folder)
    VersionReason.Elsewhere -> ShownText.Resource(R.string.map_history_why_elsewhere)
    VersionReason.Aside -> ShownText.Resource(R.string.map_history_why_aside)
    is VersionReason.CameIn -> reason.file?.let { ShownText.Resource(R.string.map_history_why_came_in, listOf(it)) } ?: ShownText.Resource(R.string.map_history_why_came_in_unnamed)
    null -> null
}

/** What a version changed, in a line: each kind of change, by the ideas' names where few ("Added “Flights” and “Hotels”"), counted where many. */
internal fun summaryWords(changes: VersionChanges, separator: String = SHOWN_SEPARATOR): ShownText =
    if (changes.none) ShownText.Resource(R.string.map_history_same) else ShownText.Joined(changeWords(changes), separator)

/** Each kind of change a version made, in words: added, removed, renamed, moved, or else its notes and details. */
internal fun changeWords(changes: VersionChanges): List<ShownText> {
    val parts = listOfNotNull(
        changes.added.takeIf { it.isNotEmpty() }?.let { named(it, R.string.map_history_added_one, R.string.map_history_added_two, R.plurals.map_history_added_more, R.plurals.map_history_added_many) },
        changes.removed.takeIf { it.isNotEmpty() }?.let { named(it, R.string.map_history_removed_one, R.string.map_history_removed_two, R.plurals.map_history_removed_more, R.plurals.map_history_removed_many) },
        when (changes.renamed.size) {
            0 -> null
            1 -> changes.renamed.single().let { (from, to) -> ShownText.Resource(R.string.map_history_renamed_one, listOf(short(from), short(to))) }
            else -> ShownText.Plural(R.plurals.map_history_renamed_many, changes.renamed.size)
        },
        when (changes.moved.size) {
            0 -> null
            1 -> ShownText.Resource(R.string.map_history_moved_one, listOf(short(changes.moved.single())))
            else -> ShownText.Plural(R.plurals.map_history_moved_many, changes.moved.size)
        },
    )

    return parts.ifEmpty { listOf(ShownText.Resource(if (changes.details) R.string.map_history_details else R.string.map_history_same)) }
}

/** Ideas of one kind of change: one or two by name, a few as the first and how many more, and many counted. */
private fun named(names: List<String>, one: Int, two: Int, more: Int, many: Int): ShownText = when {
    names.size == 1 -> ShownText.Resource(one, listOf(short(names[0])))
    names.size == 2 -> ShownText.Resource(two, listOf(short(names[0]), short(names[1])))
    names.size < NAMED -> ShownText.Plural(more, names.size - 1, listOf(names.size - 1, short(names[0])))
    else -> ShownText.Plural(many, names.size)
}

private fun short(name: String): String = MapText.shorten(name, NAME_LENGTH)

/** A capital first: some languages, such as Spanish, write weekdays and months in small letters. */
private fun String.capitalized(): String = replaceFirstChar { it.titlecase() }

/** How long ago a day is, as the history names it: today, yesterday, this week (by its weekday), this year, or before. */
private enum class Ago { Today, Yesterday, ThisWeek, ThisYear, Before }

private fun ago(date: LocalDate, today: LocalDate): Ago = when {
    date == today -> Ago.Today
    date == today.minusDays(1) -> Ago.Yesterday
    date.isAfter(today.minusDays(7)) && !date.isAfter(today) -> Ago.ThisWeek
    date.year == today.year -> Ago.ThisYear
    else -> Ago.Before
}

private fun day(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

/** A day's heading in the history: Today, Yesterday, a weekday this week, its date this year, or its date and year. */
internal fun dayHeading(date: LocalDate, today: LocalDate, words: TimeWords): ShownText = when (ago(date, today)) {
    Ago.Today -> ShownText.Resource(R.string.map_history_today)
    Ago.Yesterday -> ShownText.Resource(R.string.map_history_yesterday)
    Ago.ThisWeek -> ShownText.Plain(words.weekday(date).capitalized())
    Ago.ThisYear -> ShownText.Plain(words.weekdayAndDay(date).capitalized())
    Ago.Before -> ShownText.Plain(words.longDate(date).capitalized())
}

/** Why a version was kept as an earlier one became the map again, by when that one is from: "Before you restored the version from 16:49". */
private fun restoringWords(from: Long?, now: Long, words: TimeWords, zone: ZoneId): ShownText {
    if (from == null) {
        return ShownText.Resource(R.string.map_history_why_restoring)
    }

    val date = day(from, zone)
    val at = words.clock(from)

    return when (ago(date, day(now, zone))) {
        Ago.Today -> ShownText.Resource(R.string.map_history_why_restoring_today, listOf(at))
        Ago.Yesterday -> ShownText.Resource(R.string.map_history_why_restoring_yesterday, listOf(at))
        Ago.ThisWeek -> ShownText.Resource(R.string.map_history_why_restoring_on, listOf(words.weekday(date), at))
        Ago.ThisYear -> ShownText.Resource(R.string.map_history_why_restoring_on, listOf(words.dayOfMonth(date), at))
        Ago.Before -> ShownText.Resource(R.string.map_history_why_restoring_on_date, listOf(words.longDate(date)))
    }
}

/**
 * What the map says as a version becomes the map again, by when it was the
 * map: "Restored the map as it was today at 14:32", or "on Monday at 9:14",
 * or "on October 3, 2025".
 */
internal fun restoredMessage(time: Long, words: TimeWords, now: Long = System.currentTimeMillis(), zone: ZoneId = words.zone): ShownText {
    val date = day(time, zone)
    val at = words.clock(time)

    return when (ago(date, day(now, zone))) {
        Ago.Today -> ShownText.Resource(R.string.map_restored_today, listOf(at))
        Ago.Yesterday -> ShownText.Resource(R.string.map_restored_yesterday, listOf(at))
        Ago.ThisWeek -> ShownText.Resource(R.string.map_restored_on_weekday, listOf(words.weekday(date), at))
        Ago.ThisYear -> ShownText.Resource(R.string.map_restored_on_date_at, listOf(words.dayOfMonth(date), at))
        Ago.Before -> ShownText.Resource(R.string.map_restored_on_date, listOf(words.longDate(date)))
    }
}
