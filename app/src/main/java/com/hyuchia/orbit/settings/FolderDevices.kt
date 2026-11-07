package com.hyuchia.orbit.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.FolderDevice
import com.hyuchia.orbit.data.FolderNotice
import com.hyuchia.orbit.data.FolderSyncState
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.TimeWords
import com.hyuchia.orbit.ui.deviceIcon
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The devices that keep their maps in the folder, as their records there
 * say (see [FolderDevice]): which Orbit each has, when it last synced, and
 * what one with another version can't open, or keeps only part of (`partly`
 * says which maps, for a device, as a note names them). Only devices with
 * this version or a later one keep records, and it says so.
 */
@Composable
internal fun FolderDevices(sync: FolderSyncState, now: Long, partly: (FolderDevice) -> String? = { null }) {
    val me = sync.device ?: return

    if (sync.devices.isEmpty()) {
        DeviceNote(stringResource(R.string.folder_devices_only_this_one))
        return
    }

    val notices = sync.notices.associateBy { it.device.id }

    Text(
        stringResource(R.string.folder_devices_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 0.dp)
            .semantics { heading() },
    )
    DeviceRow(me, headline = stringResource(R.string.folder_devices_this_device, me.name), lines = listOf(stringResource(R.string.folder_devices_version, me.appVersion)))

    sync.devices.forEach { device ->
        DeviceRow(device, headline = device.name, lines = listOf(synced(LocalResources.current, device, now)), notes = notes(notices[device.id]), meanings = listOfNotNull(partly(device)))
    }

    DeviceNote(stringResource(R.string.folder_devices_note))
}

/** What a device with another version means here, line by line. */
@Composable
private fun notes(notice: FolderNotice?): List<String> {
    val words = rememberWords()

    return when (notice) {
        null -> emptyList()
        is FolderNotice.Newer -> listOfNotNull(
            stringResource(R.string.folder_devices_newer),
            notice.unopened.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.folder_devices_this_cannot_open, MediaWords.types(it, words)) },
            stringResource(R.string.folder_devices_saves_newer).takeIf { notice.newerFormat },
        )
        is FolderNotice.Older -> listOfNotNull(
            stringResource(R.string.folder_devices_older),
            notice.unopened.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.folder_devices_cannot_open, MediaWords.types(it, words)) },
            stringResource(R.string.folder_devices_shows_only).takeIf { notice.showsOnly },
        )
    }
}

/** A device's row: its `lines`, then `notes` on how its version differs, and what that means here, `meanings` among it. */
@Composable
private fun DeviceRow(device: FolderDevice, headline: String, lines: List<String>, notes: List<String> = emptyList(), meanings: List<String> = emptyList()) {
    ListItem(
        supportingContent = {
            Column {
                lines.forEach { Text(it) }
                // The first note says how the versions differ; what it means stands out.
                notes.forEachIndexed { index, note -> Text(note, color = if (index == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary) }
                meanings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            }
        },
        leadingContent = { Icon(deviceIcon(device.kind), contentDescription = null) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(headline)
    }
}

@Composable
private fun DeviceNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/**
 * Its version and the day it last synced. A device writes its record at most
 * once a day when nothing changed, so the day is all its record can tell.
 */
private fun synced(resources: Resources, device: FolderDevice, now: Long): String {
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(device.synced).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())

    return when {
        days <= 0 -> resources.getString(R.string.folder_devices_synced_today, device.appVersion)
        days == 1L -> resources.getString(R.string.folder_devices_synced, device.appVersion, resources.getString(R.string.home_yesterday))
        days < 7 -> resources.getString(R.string.folder_devices_synced, device.appVersion, resources.getQuantityString(R.plurals.home_days_ago, days.toInt(), days.toInt()))
        // A day has no clock, so the phone's 12- or 24-hour setting doesn't matter here.
        else -> resources.getString(R.string.folder_devices_synced_on, device.appVersion, TimeWords(resources.configuration.locales[0], is24Hour = false, zone).date(device.synced))
    }
}
