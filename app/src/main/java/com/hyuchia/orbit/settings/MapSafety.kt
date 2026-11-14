package com.hyuchia.orbit.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapFileTypes
import com.hyuchia.orbit.data.TRASH_DAYS
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.rememberUnlockedForEveryMap
import com.hyuchia.orbit.theme.LocalAppSettings
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Keeping maps safe: the trash, and every map in one zip to keep anywhere
 * and bring back on any device (Android's own backup needs Google's, and
 * stops at 25 MB). A backup shows as under way on its row and goes on if
 * Settings closes; what it came to is said in `snackbars`.
 */
@Composable
internal fun MapSafety(onTrash: () -> Unit, snackbars: SnackbarHostState) {
    val context = LocalContext.current
    // A backup outlives the screen, so it reads and writes through the app's resolver rather than the screen's.
    val application = context.applicationContext
    val resources = LocalResources.current
    val maps = context.container.maps
    val backups = context.container.backups
    val words = context.container.words
    val fileImporter = context.container.importer
    val privateMaps = context.container.privateMaps
    // While private maps hide, nothing here counts them: not the trash, nor what a backup came to, worded as it's said.
    val privacy by context.container.privateMaps.view.collectAsStateWithLifecycle()
    val allTrash by maps.trash.collectAsStateWithLifecycle()
    val trash = shownTrash(allTrash, privacy, LocalAppSettings.current.hideLockedPrivateMaps)
    val writable by maps.writable.collectAsStateWithLifecycle()
    val working by backups.work.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun say(text: String, problem: Boolean) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(text, withDismissAction = problem, duration = if (problem) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }

    // What a backup came to, said once: as it ends, or as Settings opens again after it, worded as private maps are then.
    val hiding by rememberUpdatedState(LocalAppSettings.current.hideLockedPrivateMaps)

    LaunchedEffect(backups) {
        backups.outcome.filterNotNull().collect { outcome ->
            backups.said(outcome)
            say(outcome.said(context.container.privateMaps.view.value.conceals(hiding)), outcome.problem)
        }
    }

    val unlockedForEveryMap = rememberUnlockedForEveryMap { reason -> say(reason, problem = true) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        backups.start(MapBackups.Work.Exporting) {
            // Half a backup isn't one: the file goes, and what's said is why. A file Orbit couldn't open is left as it was, and so is
            // one it never opens because a map's file can't be read.
            val exported = exportInto(application.contentResolver, uri, check = { maps.checkExportAll() }) { maps.exportAll(it) }
            exportOutcome(exported, maps::summary, resources)
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        backups.start(MapBackups.Work.Importing) {
            // Maps from other apps come in as Open a file brings them; anything else is read as a zip of every map, a locked
            // private map found by its identity alone, so a zip of text someone guessed tells nothing of it.
            importInto(
                application.contentResolver,
                uri,
                words,
                resources,
                fromOtherApp = { fileImporter.importFromOtherAppFile(uri, application.contentResolver) },
                importAll = { input -> maps.importAll(input, locked = privateMaps::hides) },
            )
        }
    }

    if (!writable) {
        Text(
            stringResource(R.string.settings_newer_version_keeps_maps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }

    WhereMapsAre()

    ListItem(
        onClick = onTrash,
        supportingContent = {
            Text(
                if (trash.isEmpty()) {
                    pluralStringResource(R.plurals.settings_trash_wait_there, TRASH_DAYS, TRASH_DAYS)
                } else {
                    pluralStringResource(
                        R.plurals.settings_trash_maps_kept,
                        trash.size,
                        trash.size,
                        pluralStringResource(R.plurals.settings_day_count, TRASH_DAYS, TRASH_DAYS),
                    )
                },
            )
        },
        leadingContent = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.settings_trash))
    }

    BackupRow(
        title = stringResource(R.string.settings_export_all_maps),
        description = stringResource(R.string.settings_export_all_maps_description),
        busy = stringResource(R.string.settings_exporting),
        icon = Icons.Outlined.Archive,
        work = MapBackups.Work.Exporting,
        working = working,
        // Private maps go in too, as they are: while one is locked, the phone's owner says so first. The name the file
        // picker offers is in the app's language; the extension isn't translated.
        onClick = { unlockedForEveryMap { exporter.launch("${resources.getString(R.string.settings_all_maps_file_name, LocalDate.now().toString())}.zip") } },
    )

    BackupRow(
        title = stringResource(R.string.settings_import_maps),
        description = stringResource(R.string.settings_import_maps_description),
        busy = stringResource(R.string.settings_importing),
        icon = Icons.Outlined.Unarchive,
        work = MapBackups.Work.Importing,
        working = working,
        onClick = { importer.launch(MapFileTypes.ZIPS_PICKER.toTypedArray()) },
    )
}

/**
 * Export all or Import maps. While either runs, neither can start again:
 * the one running says so beside an indicator, still easy to read, and
 * TalkBack hears it as it starts; the other dims.
 */
@Composable
private fun BackupRow(title: String, description: String, busy: String, icon: ImageVector, work: MapBackups.Work, working: MapBackups.Work?, onClick: () -> Unit) {
    val running = working == work
    val colors = ListItemDefaults.colors()

    ListItem(
        onClick = onClick,
        enabled = working == null,
        supportingContent = {
            Text(if (running) busy else description, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (running) {
            { LoadingIndicator(Modifier.size(40.dp)) }
        } else {
            null
        },
        // The one running isn't dimmed: it isn't unavailable, it's busy.
        colors = if (running) {
            ListItemDefaults.colors(
                disabledContentColor = colors.contentColor,
                disabledLeadingContentColor = colors.leadingContentColor,
                disabledSupportingContentColor = colors.supportingContentColor,
            )
        } else {
            colors
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(title)
    }
}
