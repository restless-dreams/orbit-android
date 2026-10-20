package com.hyuchia.orbit

import android.net.Uri
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.home.HomeScreen
import com.hyuchia.orbit.home.MapPlaceholder
import com.hyuchia.orbit.home.NewMapScreen
import com.hyuchia.orbit.map.MapScreen
import com.hyuchia.orbit.nearby.Arrival
import com.hyuchia.orbit.nearby.ArrivalDialog
import com.hyuchia.orbit.nearby.IncomingDialog
import com.hyuchia.orbit.settings.AboutScreen
import com.hyuchia.orbit.settings.LicensesScreen
import com.hyuchia.orbit.settings.PrivacyScreen
import com.hyuchia.orbit.settings.SettingsScreen
import com.hyuchia.orbit.settings.TrashScreen
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.orbitViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data object HomeKey : NavKey

/** A map; `present` opens it straight into presenting. */
@Serializable
data class MapKey(val id: String, val present: Boolean = false) : NavKey

@Serializable
data object NewMapKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object TrashKey : NavKey

@Serializable
data object PrivacyKey : NavKey

@Serializable
data object LicensesKey : NavKey

/**
 * What came in and waits for an answer or a look: why a file couldn't be
 * opened, what a zip or another app's map brought, a map that's here
 * already. Kept while the window is made again for a new language or font
 * size, as `remember` isn't, so the question never goes unanswered.
 */
class Arrivals : ViewModel() {
    var problem by mutableStateOf<String?>(null)
    var many by mutableStateOf<ImportResult.ImportedMany?>(null)
    var notes by mutableStateOf<List<String>?>(null)
    var alreadyHere by mutableStateOf<ImportResult.AlreadyHere?>(null)
}

/** What shortcuts and other apps ask Orbit to do. */
sealed interface AppRequest {
    data class OpenMap(val id: String) : AppRequest
    data object NewMap : AppRequest
    data class ImportFile(val uri: Uri) : AppRequest
    data class ImportText(val text: String, val subject: String?) : AppRequest
}

/** Screens step forward by growing out of the one before, and back by shrinking into it. */
private fun forward(): ContentTransform =
    ContentTransform(fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.94f), fadeOut(tween(120)))

private fun backward(): ContentTransform =
    ContentTransform(fadeIn(tween(260, delayMillis = 60)), fadeOut(tween(160)) + scaleOut(tween(260), targetScale = 0.94f))

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun OrbitApp(requests: Flow<AppRequest>) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(HomeKey)
    val arrivals = orbitViewModel { Arrivals() }
    var importProblem by arrivals::problem
    // What a zip of maps brought in.
    var importedMany by arrivals::many

    // What a map from another app had that Orbit keeps another way, or couldn't keep, said once it's open.
    var importNotes by arrivals::notes
    // A map with a panel beside it, its text or a presentation open takes the whole window: the list steps aside.
    var mapWantsRoom by remember { mutableStateOf(false) }
    val adaptiveInfo = currentWindowAdaptiveInfo()

    val directive = remember(adaptiveInfo, mapWantsRoom) {
        // A window too short for the list beside a map, such as a phone on its side, shows one pane at a time.
        val short = !adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
        val calculated = calculatePaneScaffoldDirective(adaptiveInfo)
        PaneScaffoldDirective(
            maxHorizontalPartitions = if (short || mapWantsRoom) 1 else calculated.maxHorizontalPartitions,
            horizontalPartitionSpacerSize = calculated.horizontalPartitionSpacerSize,
            maxVerticalPartitions = calculated.maxVerticalPartitions,
            verticalPartitionSpacerSize = calculated.verticalPartitionSpacerSize,
            defaultPanePreferredWidth = calculated.defaultPanePreferredWidth,
            defaultPanePreferredHeight = calculated.defaultPanePreferredHeight,
            excludedBounds = calculated.excludedBounds,
            // Focus moving into a pane as it opens lands on the search field, and the keyboard comes up unasked.
            shouldAutoFocusCurrentDestination = false,
        )
    }

    /** Shows a map, or presents it: on tablets it replaces the one beside the list. */
    fun openMap(id: String, present: Boolean = false) {
        backStack.removeAll { it is MapKey || it is NewMapKey || it is SettingsKey || it is AboutKey || it is TrashKey || it is PrivacyKey || it is LicensesKey }
        backStack.add(MapKey(id, present))
    }

    // With room beside the list from the start, the map opened last comes back there instead of an empty pane.
    var resumed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (resumed) {
            return@LaunchedEffect
        }

        resumed = true

        if (directive.maxHorizontalPartitions < 2 || backStack.size > 1) {
            return@LaunchedEffect
        }

        val maps = container.maps.maps.filterNotNull().first()
        val last = maps.filter { it.metadata.opened > 0 }.maxByOrNull { it.metadata.opened } ?: maps.firstOrNull { it.metadata.pinned }

        // A shortcut or a share may have opened something while the maps loaded.
        if (last != null && backStack.size == 1) {
            openMap(last.id)
        }
    }

    // A file of a map that's already here waits for an answer: update it, or keep both.
    var alreadyHere by arrivals::alreadyHere

    fun imported(result: ImportResult) {
        when (result) {
            is ImportResult.Imported -> {
                openMap(result.id)
                importNotes = result.notes.ifEmpty { null }
            }
            is ImportResult.ImportedMany -> importedMany = result
            is ImportResult.AlreadyHere -> alreadyHere = result
            is ImportResult.Failed -> importProblem = result.reason
        }
    }

    LaunchedEffect(requests) {
        requests.collect { request ->
            when (request) {
                is AppRequest.OpenMap -> if (container.maps.exists(request.id)) {
                    openMap(request.id)
                }
                AppRequest.NewMap -> if (backStack.lastOrNull() != NewMapKey) {
                    backStack.add(NewMapKey)
                }
                is AppRequest.ImportFile -> scope.launch { imported(container.importer.importFile(request.uri, context.contentResolver)) }
                is AppRequest.ImportText -> scope.launch { imported(container.importer.importShared(request.text, request.subject)) }
            }
        }
    }

    NavDisplay<NavKey>(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(rememberListDetailSceneStrategy(directive = directive)),
        transitionSpec = { forward() },
        popTransitionSpec = { backward() },
        predictivePopTransitionSpec = { backward() },
        entryProvider = entryProvider {
            entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { MapPlaceholder() })) {
                HomeScreen(
                    onOpen = { openMap(it) },
                    onPresent = { openMap(it, present = true) },
                    onNewMap = { backStack.add(NewMapKey) },
                    onImported = ::imported,
                    onSettings = { backStack.add(SettingsKey) },
                )
            }

            entry<MapKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                MapScreen(id = key.id, onClose = { backStack.remove(key) }, onWantsRoom = { mapWantsRoom = it }, present = key.present)
            }

            entry<NewMapKey> {
                NewMapScreen(
                    onCreated = ::openMap,
                    onImported = ::imported,
                    onBack = { backStack.remove(NewMapKey) },
                )
            }

            entry<SettingsKey> {
                SettingsScreen(onAbout = { backStack.add(AboutKey) }, onTrash = { backStack.add(TrashKey) }, onBack = { backStack.remove(SettingsKey) })
            }

            entry<TrashKey> {
                TrashScreen(onOpen = ::openMap, onBack = { backStack.remove(TrashKey) })
            }

            entry<AboutKey> {
                AboutScreen(
                    onOpenMap = ::openMap,
                    onPrivacy = { backStack.add(PrivacyKey) },
                    onLicenses = { backStack.add(LicensesKey) },
                    onBack = { backStack.remove(AboutKey) },
                )
            }

            entry<PrivacyKey> {
                PrivacyScreen(onBack = { backStack.remove(PrivacyKey) })
            }

            entry<LicensesKey> {
                LicensesScreen(onBack = { backStack.remove(LicensesKey) })
            }
        },
    )

    // Maps from nearby devices, one at a time, and a file of a map that's already here.
    val offers by container.nearby.offers.collectAsStateWithLifecycle()
    val incoming by container.nearby.incoming.collectAsStateWithLifecycle()
    val olderSender by container.nearby.olderSender.collectAsStateWithLifecycle()
    var olderSenderSeen by rememberSaveable { mutableLongStateOf(olderSender) }

    // Before a map comes, the code it'll come with: the person sending compares it with theirs.
    incoming.firstOrNull()?.takeIf { offers.isEmpty() }?.let { coming ->
        IncomingDialog(coming) { container.nearby.decline(coming) }
    }

    // An Orbit too old to check codes before sending tried to send a map, which this one doesn't take: the phone says why.
    if (olderSender > olderSenderSeen) {
        AlertDialog(
            onDismissRequest = { olderSenderSeen = olderSender },
            title = {
                EscapeCloses { olderSenderSeen = olderSender }
                Text(stringResource(R.string.nearby_older_sender_title))
            },
            text = { Text(stringResource(R.string.nearby_older_sender_text)) },
            confirmButton = { TextButton(onClick = { olderSenderSeen = olderSender }) { Text(stringResource(R.string.main_ok)) } },
        )
    }

    offers.firstOrNull()?.let { offer ->
        ArrivalDialog(offer.from, offer.bundle, alreadyHere = offer.existing != null, address = offer.address, check = offer.check) { answer ->
            if (answer == Arrival.Decline) {
                container.nearby.decline(offer)
            } else {
                scope.launch {
                    val id = container.nearby.accept(offer, keepBoth = answer == Arrival.KeepBoth)

                    if (id != null) {
                        openMap(id)
                    } else {
                        importProblem = resources.getString(R.string.main_could_not_keep_sent_map, offer.from)
                    }
                }
            }
        }
    }

    alreadyHere?.let { arrival ->
        ArrivalDialog(from = null, arrival.bundle, alreadyHere = true) { answer ->
            alreadyHere = null

            if (answer != Arrival.Decline) {
                scope.launch {
                    try {
                        openMap(container.maps.receive(arrival.bundle, replacing = if (answer == Arrival.KeepBoth) null else arrival.existing))
                    } catch (failed: IOException) {
                        importProblem = failed.message ?: resources.getString(R.string.common_could_not_keep_that_map)
                    }
                }
            }
        }
    }

    importedMany?.let { result ->
        AlertDialog(
            onDismissRequest = { importedMany = null },
            title = {
                EscapeCloses { importedMany = null }
                Text(stringResource(if (result.added == 0) R.string.main_nothing_new else R.string.main_maps_added))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when {
                            result.added == 0 && result.alreadyHere == 0 -> stringResource(R.string.main_no_maps_in_zip)
                            result.added == 0 -> stringResource(R.string.main_every_map_in_zip_already_here)
                            result.alreadyHere == 0 -> pluralStringResource(R.plurals.common_added_maps, result.added, result.added)
                            result.alreadyHere == 1 -> pluralStringResource(R.plurals.common_added_maps_with_one_already_here, result.added, result.added)
                            else -> pluralStringResource(R.plurals.common_added_maps_with_several_already_here, result.added, result.added, result.alreadyHere)
                        },
                    )

                    ImportNotes(result.notes)
                }
            },
            confirmButton = { TextButton(onClick = { importedMany = null }) { Text(stringResource(R.string.main_ok)) } },
        )
    }

    importNotes?.let { notes ->
        AlertDialog(
            onDismissRequest = { importNotes = null },
            title = {
                EscapeCloses { importNotes = null }
                Text(stringResource(R.string.main_import_notes_title))
            },
            text = { ImportNotes(notes) },
            confirmButton = { TextButton(onClick = { importNotes = null }) { Text(stringResource(R.string.main_ok)) } },
        )
    }

    importProblem?.let { reason ->
        AlertDialog(
            onDismissRequest = { importProblem = null },
            title = {
                EscapeCloses { importProblem = null }
                Text(stringResource(R.string.main_could_not_open_that))
            },
            text = { Text(reason) },
            confirmButton = { TextButton(onClick = { importProblem = null }) { Text(stringResource(R.string.main_ok)) } },
        )
    }
}

/** What maps from another app had that Orbit keeps another way, or couldn't keep: a line each. */
@Composable
private fun ImportNotes(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        notes.forEach { note -> Text("• $note") }
    }
}
