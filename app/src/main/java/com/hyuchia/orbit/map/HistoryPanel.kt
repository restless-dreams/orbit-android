package com.hyuchia.orbit.map

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.ui.ListRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The map's earlier versions, by day. Choosing one shows it on the map, to
 * look around in; Restore makes it the map again, and closing the panel
 * brings back the map as it is now.
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
            history.showing?.let { time ->
                FilledTonalButton(onClick = { model.restoreVersion(time) }, modifier = Modifier.padding(end = 4.dp)) { Text(stringResource(R.string.common_restore)) }
            }
        },
    ) {
        val versions = history.versions

        if (versions == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            VersionList(versions, history, onShow = model::showVersion)
        }
    }
}

@Composable
private fun VersionList(versions: List<MapVersion>, history: HistoryUi, onShow: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now(zone)
    val days = versions.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.selectableGroup()) {
        item(key = "now") {
            VersionRow(stringResource(R.string.map_history_now), ideas(history.ideasNow).text(), selected = history.showing == null, current = true) { onShow(null) }
        }

        if (versions.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.map_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }

        days.forEach { (date, dayVersions) ->
            item(key = "day-$date") {
                Text(
                    dayHeading(date, today, locale).text(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                )
            }

            items(dayVersions, key = { it.time }) { version ->
                val time = clock(version.time, zone, locale)
                VersionRow(
                    // The file as it came in from elsewhere, before Orbit wrote it: kept for good.
                    if (version.original) stringResource(R.string.map_history_original_file, time) else time,
                    listOfNotNull(ideas(version.ideas), difference(version.ideas, history.ideasNow)).map { it.text() }.joinToString(" · "),
                    selected = history.showing == version.time,
                    original = version.original,
                    problem = if (version.time in history.unreadable) stringResource(R.string.map_history_version_unreadable) else null,
                ) { onShow(version.time) }
            }
        }
    }
}

/** A version in the history; `problem` says why it couldn't be shown, in place of its details. */
@Composable
private fun VersionRow(
    name: String,
    detail: String,
    selected: Boolean,
    current: Boolean = false,
    original: Boolean = false,
    problem: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ListRow(
        headline = { Text(name) },
        supporting = {
            if (problem != null) {
                Text(problem, color = colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                Text(detail)
            }
        },
        leading = {
            Icon(
                when {
                    current -> Icons.Outlined.Hub
                    original -> Icons.Outlined.Description
                    else -> Icons.Outlined.History
                },
                contentDescription = null,
            )
        },
        containerColor = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        headlineColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        leadingColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

private fun ideas(count: Int): ShownText = ShownText.Plural(R.plurals.map_ideas, count)

/** How a version's ideas compare with the map's now: "3 fewer than now", or nothing when it has as many. */
internal fun difference(ideas: Int, now: Int): ShownText? = when {
    ideas > now -> ShownText.Plural(R.plurals.map_history_more_than_now, ideas - now)
    ideas < now -> ShownText.Plural(R.plurals.map_history_fewer_than_now, now - ideas)
    else -> null
}

private fun clock(time: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(Instant.ofEpochMilli(time).atZone(zone))

/**
 * The pattern a language writes a date with: `fields` in the order and with
 * the words it puts them in, "MMMMd" as "MMMM d" in English and "d 'de' MMMM"
 * in Spanish.
 */
private fun localPattern(locale: Locale, fields: String): String = DateFormat.getBestDateTimePattern(locale, fields)

/** A heading, starting with a capital: some languages, such as Spanish, write weekdays and months in small letters. */
private fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

/**
 * A day's heading in the history: Today, Yesterday, a weekday this week, or
 * its date. `pattern` gives the language's pattern for a date's fields.
 */
internal fun dayHeading(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
    pattern: (String) -> String = { fields -> localPattern(locale, fields) },
): ShownText = when {
    date == today -> ShownText.Resource(R.string.map_history_today)
    date == today.minusDays(1) -> ShownText.Resource(R.string.map_history_yesterday)
    date.isAfter(today.minusDays(7)) -> ShownText.Plain(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).capitalized(locale))
    date.year == today.year -> ShownText.Plain(DateTimeFormatter.ofPattern(pattern("EEEEMMMMd"), locale).format(date).capitalized(locale))
    else -> ShownText.Plain(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date).capitalized(locale))
}

/**
 * What the map says as a version becomes the map again, by when it was the
 * map: "Restored the map as it was today at 14:32", or "on Monday at 9:14",
 * or "on October 3, 2025". `pattern` gives the language's pattern for a
 * date's fields.
 */
internal fun restoredMessage(
    time: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    pattern: (String) -> String = { fields -> localPattern(locale, fields) },
): ShownText {
    val moment = Instant.ofEpochMilli(time).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = moment.toLocalDate()
    val at = clock(time, zone, locale)

    return when {
        date == today -> ShownText.Resource(R.string.map_restored_today, listOf(at))
        date == today.minusDays(1) -> ShownText.Resource(R.string.map_restored_yesterday, listOf(at))
        date.isAfter(today.minusDays(7)) -> ShownText.Resource(R.string.map_restored_on_weekday, listOf(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale), at))
        date.year == today.year -> ShownText.Resource(R.string.map_restored_on_date_at, listOf(DateTimeFormatter.ofPattern(pattern("MMMMd"), locale).format(date), at))
        else -> ShownText.Resource(R.string.map_restored_on_date, listOf(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)))
    }
}
