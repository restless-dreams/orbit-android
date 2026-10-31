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
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
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
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.home.HomeScreen
import com.hyuchia.orbit.home.MapPlaceholder
import com.hyuchia.orbit.home.NewMapScreen
import com.hyuchia.orbit.map.MapScreen
import com.hyuchia.orbit.nearby.Arrival
import com.hyuchia.orbit.nearby.ArrivalDialog
import com.hyuchia.orbit.nearby.IncomingDialog
import com.hyuchia.orbit.nearby.SendSheet
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.WhenPrivateMapsLock
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.mentionedAlreadyHere
import com.hyuchia.orbit.privacy.mentionedFilledIn
import com.hyuchia.orbit.settings.AboutScreen
import com.hyuchia.orbit.settings.LicensesScreen
import com.hyuchia.orbit.settings.PrivacyScreen
import com.hyuchia.orbit.settings.SettingsScreen
import com.hyuchia.orbit.settings.TrashScreen
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.ShortScreen
import com.hyuchia.orbit.ui.WideScreen
import com.hyuchia.orbit.ui.orbitViewModel
import com.hyuchia.orbit.ui.rememberDefaultButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data object HomeKey : NavKey

/** A map; `present` opens it straight into presenting, and `study` into studying it. */
@Serializable
data class MapKey(val id: String, val present: Boolean = false, val study: Boolean = false) : NavKey

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

/** A map's card asks the map open beside the list to start presenting, or with `study`, studying (see `mapOpening`). */
data class MapStart(val id: String, val study: Boolean)

/** The back stack that shows a map, and whether the map that's open already starts there (see `mapOpening`). */
data class MapOpening(val backStack: List<NavKey>, val startsInPlace: Boolean)

/**
 * How `backStack` shows map `id`: presented, studied, or only shown. The
 * map takes the place of the one beside the list, and of anything over
 * them. A map that's open already stays to present or study it in place,
 * whatever it was doing: shown again under the same key it would start
 * nothing, as its screen starts what its key asks for once.
 */
fun mapOpening(backStack: List<NavKey>, id: String, present: Boolean, study: Boolean): MapOpening {
    val open = backStack.lastOrNull { it is MapKey } as MapKey?
    val kept = backStack.filterNot { it is MapKey || it is NewMapKey || it is SettingsKey || it is AboutKey || it is TrashKey || it is PrivacyKey || it is LicensesKey }

    return if (open?.id == id && (present || study)) {
        MapOpening(kept + open, startsInPlace = true)
    } else {
        MapOpening(kept + MapKey(id, present, study), startsInPlace = false)
    }
}

/** Turns this back stack into `wanted`, its keys in `wanted`'s order: the keys both have stay as they are, and so do their screens. */
fun MutableList<NavKey>.becomes(wanted: List<NavKey>) {
    removeAll { it !in wanted }
    wanted.filterNot { it in this }.forEach(::add)
}

/**
 * What came in and waits for an answer or a look: why a file couldn't be
 * opened, what a zip or another app's map brought, maps that are here
 * already. Kept while the window is made again for a new language or font
 * size, as `remember` isn't, so the question never goes unanswered.
 */
class Arrivals : ViewModel() {
    var problem by mutableStateOf<String?>(null)
    var many by mutableStateOf<ImportResult.ImportedMany?>(null)
    var notes by mutableStateOf<List<String>?>(null)
    var otherVersions by mutableStateOf(OtherVersions())
}

/**
 * A file of a map that's here already, in another version, waiting for an
 * answer: whether to update the map, keep both, or neither. `opens` the map
 * once it's in, as a file on its own does; one of several doesn't, so the
 * screen stays put while the rest are asked about.
 */
data class OtherVersion(val arrival: ImportResult.AlreadyHere, val opens: Boolean)

/**
 * Files of maps that are here already in other versions, asked about one
 * after another, in the order they came: the first `waiting` is asked now.
 * `answered` counts those answered since the first of them came, so each
 * question says where it is among them ("2 of 3").
 */
data class OtherVersions(val waiting: List<OtherVersion> = emptyList(), val answered: Int = 0) {
    /** The one asked about now, if any. */
    val asked: OtherVersion? get() = waiting.firstOrNull()

    /** Which of them the one asked about is, from 1. */
    val number: Int get() = answered + 1

    /** How many have come since the first, the answered ones too. */
    val count: Int get() = answered + waiting.size

    /** These, then `more`. */
    operator fun plus(more: List<OtherVersion>): OtherVersions = copy(waiting = waiting + more)

    /**
     * Once `question` is answered: the next one is asked, and when none is
     * left, counting starts again. Unchanged when `question` isn't the one
     * asked, as for a second answer to it.
     */
    fun after(question: OtherVersion): OtherVersions = when {
        asked !== question -> this
        waiting.size == 1 -> OtherVersions()
        else -> OtherVersions(waiting.drop(1), answered + 1)
    }
}

/** What shortcuts and other apps ask Orbit to do. */
sealed interface AppRequest {
    /** A map to open: `ownMark` when the intent came from Orbit's own shortcut or widget ([OpenMark]). */
    data class OpenMap(val id: String, val ownMark: Boolean = true) : AppRequest

    data object NewMap : AppRequest
    data class ImportFile(val uri: Uri) : AppRequest
    data class ImportText(val text: String, val subject: String?) : AppRequest
}

/** Whether a window has room for the list beside a map one way up or the other, as a tablet's does and a phone's doesn't. */
internal fun roomForTwoPanes(window: DpSize): Boolean =
    maxOf(window.width, window.height) >= WideScreen && minOf(window.width, window.height) >= ShortScreen

/**
 * How Home's list and the map share a window with room for both (`directive`).
 *
 * On a tablet, Home stays in the list-detail scene whether the map's pane is
 * beside it or it has the window to itself, so turning the tablet only
 * resizes it. In a scene of its own when upright, it moved from one scene to
 * the other in the middle of every turn's transition, and Compose measured
 * its focused search field while it moved, and threw. A map on its own still
 * gets a scene of its own, so it grows out of Home and shrinks back.
 *
 * Back goes back until the panes change, the scaffold's own way, which
 * leaves it to the map beside the list ([listBesideMap]): closing the map
 * there leaves the list beside its placeholder, the same two panes, so the
 * scene takes no Back, and the map closes itself, once nothing on it or over
 * it is left to close ([com.hyuchia.orbit.map.BesideListBackHandler]). Had
 * the scene taken Back there, as popping one screen at a time has it do, it
 * would come before the map's own after a panel beside the map closed, as
 * the scenes add theirs again, and Back would close the map instead of
 * flying up it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun <T : Any> rememberListDetailScenes(homeOnTop: Boolean, roomForTwo: Boolean, directive: PaneScaffoldDirective): ListDetailSceneStrategy<T> =
    rememberListDetailSceneStrategy(
        shouldHandleSinglePaneLayout = roomForTwo && homeOnTop,
        backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
        directive = directive,
    )

/** Whether the list shows beside the map the window has room for (`directive`): on a tablet, unless the map wants the room. */
internal fun listBesideMap(directive: PaneScaffoldDirective): Boolean = directive.maxHorizontalPartitions >= 2

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
    val roomForTwo = roomForTwoPanes(LocalWindowInfo.current.containerDpSize)

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

    // A presentation or a study session a map's card asked of the map open beside the list, until that map starts it.
    var mapStart by remember { mutableStateOf<MapStart?>(null) }

    // A map Home is sending: its sheet is drawn here, over every pane, so it stays as the list steps aside, as standing a
    // tablet upright with a map open has it do.
    var sending by remember { mutableStateOf<MapSummary?>(null) }

    /** Shows a map, presents it or studies it: on tablets it replaces the one beside the list, or starts there if it's the same map. */
    fun openMap(id: String, present: Boolean = false, study: Boolean = false) {
        // A map opening takes the screen from the list the sheet was sending from.
        sending = null
        val opening = mapOpening(backStack, id, present, study)
        backStack.becomes(opening.backStack)
        mapStart = if (opening.startsInPlace) MapStart(id, study) else null
    }

    // Hidden while locked, as Settings can choose, a private map open beside the list goes with the others as they
    // lock: there, it's on Home. One open on a screen of its own stays under its lock, as it was.
    val hidingPrivateMaps = LocalAppSettings.current.hideLockedPrivateMaps

    WhenPrivateMapsLock {
        sending = sending?.takeUnless { it.metadata.isPrivate }

        if (hidingPrivateMaps && listBesideMap(directive)) {
            backStack.removeAll { key -> key is MapKey && container.maps.summary(key.id)?.metadata?.isPrivate == true }
        }
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

        // A private map that's locked isn't shown unasked: the one before it comes back instead.
        val maps = container.maps.maps.filterNotNull().first().filterNot(container.privateMaps::hides)
        val last = maps.filter { it.metadata.opened > 0 }.maxByOrNull { it.metadata.opened } ?: maps.firstOrNull { it.metadata.pinned }

        // A shortcut or a share may have opened something while the maps loaded.
        if (last != null && backStack.size == 1) {
            openMap(last.id)
        }
    }

    // Files of maps that are already here wait for an answer, one after another: update each, or keep both.
    var otherVersions by arrivals::otherVersions

    fun imported(result: ImportResult) {
        when (result) {
            is ImportResult.Imported -> {
                openMap(result.id)
                importNotes = result.notes.ifEmpty { null }
            }
            is ImportResult.ImportedMany -> {
                importedMany = result

                // The maps it brought are on Home: Start a map, which may have opened the file, steps aside for them.
                if (result.added > 0) {
                    backStack.remove(NewMapKey)
                }
            }
            is ImportResult.AlreadyHere -> otherVersions += listOf(OtherVersion(result, opens = true))
            is ImportResult.Failed -> importProblem = result.reason
            is ImportResult.SeveralFiles -> {
                // What the rest brought is said once these are answered (see below).
                otherVersions += result.otherVersions.map { OtherVersion(it, opens = false) }
                result.summary?.let(::imported)
            }
        }
    }

    // Another app can ask for a map by its id too, but not with Orbit's own mark ([OpenMark]): a private map hidden
    // while locked isn't opened for it, so it can't tell one is there.
    suspend fun hiddenPrivate(id: String): Boolean {
        val summary = container.maps.maps.filterNotNull().first().firstOrNull { it.id == id } ?: return false
        val hiding = container.settings.settings.first().hideLockedPrivateMaps
        return summary.metadata.isPrivate && container.privateMaps.view.value.conceals(hiding)
    }

    LaunchedEffect(requests) {
        requests.collect { request ->
            when (request) {
                is AppRequest.OpenMap -> if (container.maps.exists(request.id) && (request.ownMark || !hiddenPrivate(request.id))) {
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
        sceneStrategies = listOf(rememberListDetailScenes(homeOnTop = backStack.lastOrNull() == HomeKey, roomForTwo = roomForTwo, directive = directive)),
        transitionSpec = { forward() },
        popTransitionSpec = { backward() },
        predictivePopTransitionSpec = { backward() },
        entryProvider = entryProvider {
            entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { MapPlaceholder() })) {
                HomeScreen(
                    onOpen = { openMap(it) },
                    onPresent = { openMap(it, present = true) },
                    onStudy = { openMap(it, study = true) },
                    onNewMap = { backStack.add(NewMapKey) },
                    onImported = ::imported,
                    onSettings = { backStack.add(SettingsKey) },
                    onTrash = { backStack.add(TrashKey) },
                    onSend = { sending = it },
                )
            }

            entry<MapKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                MapScreen(
                    id = key.id,
                    onClose = { backStack.remove(key) },
                    onWantsRoom = { mapWantsRoom = it },
                    besideList = listBesideMap(directive),
                    present = key.present,
                    study = key.study,
                    start = mapStart?.takeIf { it.id == key.id },
                    onStarted = { mapStart = null },
                    onImported = ::imported,
                )
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

    sending?.let { map ->
        // Recents gets no picture of a private map's name, as on Home, also once Home has stepped aside.
        PrivateOnScreen(map.metadata.isPrivate)
        SendSheet(map.id, map.title, onDismiss = { sending = null })
    }

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
            confirmButton = { TextButton(onClick = { olderSenderSeen = olderSender }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.main_ok)) } },
        )
    }

    // A map that arrives as a private one's new version doesn't say its name while private maps are locked, nor lands in it.
    val privacy by container.privateMaps.view.collectAsStateWithLifecycle()

    // Asked of the lock itself, so it's right even the moment after they unlock: `privacy`, read with it, draws the dialogs again as they change.
    fun hides(id: String?): Boolean = privacy.locked && id?.let(container.maps::summary)?.let(container.privateMaps::hides) == true

    // A private map here is named by the cover its owner chose, if it has one, while its name isn't said.
    fun cover(id: String?) = id?.let(container.maps::summary)?.metadata?.cover

    // Unlocked, a dialog about another version of a private map names it: Recents gets no picture of Orbit then, as of the map.
    fun namesPrivate(id: String?): Boolean = id?.let(container.maps::summary)?.metadata?.isPrivate == true && !hides(id)

    offers.firstOrNull()?.let { offer ->
        PrivateOnScreen(namesPrivate(offer.existing))

        ArrivalDialog(
            offer.from,
            offer.bundle,
            alreadyHere = offer.existing != null,
            address = offer.address,
            check = offer.check,
            hidden = hides(offer.existing),
            concealed = privacy.conceals(hidingPrivateMaps),
            cover = cover(offer.existing),
        ) { answer ->
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

    otherVersions.asked?.let { question ->
        val arrival = question.arrival

        // Updating a locked private map asks for the screen lock in the dialog itself, before it answers.
        PrivateOnScreen(namesPrivate(arrival.existing))

        ArrivalDialog(
            from = null,
            arrival.bundle,
            alreadyHere = true,
            hidden = hides(arrival.existing),
            concealed = privacy.conceals(hidingPrivateMaps),
            cover = cover(arrival.existing),
            number = otherVersions.number,
            count = otherVersions.count,
        ) { answer ->
            if (otherVersions.asked !== question) {
                return@ArrivalDialog
            }

            otherVersions = otherVersions.after(question)

            if (answer != Arrival.Decline) {
                scope.launch {
                    try {
                        val replacing = if (answer == Arrival.KeepBoth) null else arrival.existing
                        val id = container.maps.receive(arrival.bundle, replacing = replacing, reason = VersionReason.FromFile(arrival.name))

                        if (question.opens) {
                            openMap(id)
                        }
                    } catch (failed: IOException) {
                        importProblem = failed.message ?: resources.getString(R.string.common_could_not_keep_that_map)
                    }
                }
            }
        }
    }

    // One thing at a time: what files brought, and why they couldn't come in, are said once their questions are answered.
    val answering = otherVersions.asked != null

    importedMany?.takeUnless { answering }?.let { result ->
        AlertDialog(
            onDismissRequest = { importedMany = null },
            title = {
                EscapeCloses { importedMany = null }
                Text(stringResource(if (result.added == 0) R.string.main_nothing_new else R.string.main_maps_added))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // While private maps hide, the ones already here aren't counted, nor files brought into them.
                    val concealing = privacy.conceals(hidingPrivateMaps)
                    val alreadyHere = mentionedAlreadyHere(result.alreadyHere, result.privateAlreadyHere, concealing)
                    val filledIn = mentionedFilledIn(result.filledIn, result.privateFilledIn, concealing)

                    Text(
                        when {
                            result.added == 0 && result.alreadyHere == 0 -> stringResource(R.string.main_no_maps_in_zip)
                            result.added == 0 -> stringResource(R.string.main_every_map_in_zip_already_here)
                            alreadyHere == 0 -> pluralStringResource(R.plurals.common_added_maps, result.added, result.added)
                            alreadyHere == 1 -> pluralStringResource(R.plurals.common_added_maps_with_one_already_here, result.added, result.added)
                            else -> pluralStringResource(R.plurals.common_added_maps_with_several_already_here, result.added, result.added, alreadyHere)
                        },
                    )

                    ImportNotes(listOfNotNull(pluralStringResource(R.plurals.data_zip_files_filled_in, filledIn, filledIn).takeIf { filledIn > 0 }) + result.notes)
                }
            },
            confirmButton = { TextButton(onClick = { importedMany = null }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.main_ok)) } },
        )
    }

    importNotes?.takeUnless { answering }?.let { notes ->
        AlertDialog(
            onDismissRequest = { importNotes = null },
            title = {
                EscapeCloses { importNotes = null }
                Text(stringResource(R.string.main_import_notes_title))
            },
            text = { ImportNotes(notes) },
            confirmButton = { TextButton(onClick = { importNotes = null }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.main_ok)) } },
        )
    }

    importProblem?.takeUnless { answering }?.let { reason ->
        AlertDialog(
            onDismissRequest = { importProblem = null },
            title = {
                EscapeCloses { importProblem = null }
                Text(stringResource(R.string.main_could_not_open_that))
            },
            text = { Text(reason) },
            confirmButton = { TextButton(onClick = { importProblem = null }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.main_ok)) } },
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
