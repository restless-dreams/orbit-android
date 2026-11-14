package com.hyuchia.orbit

import android.net.Uri
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.WaitingArrivals
import com.hyuchia.orbit.home.HomeScreen
import com.hyuchia.orbit.home.MapPlaceholder
import com.hyuchia.orbit.home.NewMapScreen
import com.hyuchia.orbit.map.MapScreen
import com.hyuchia.orbit.nearby.Accepted
import com.hyuchia.orbit.nearby.Arrival
import com.hyuchia.orbit.nearby.ArrivalDialog
import com.hyuchia.orbit.nearby.IncomingDialog
import com.hyuchia.orbit.nearby.SendSheet
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.WhenPrivateMapsLock
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.mentionedAlreadyHere
import com.hyuchia.orbit.privacy.mentionedFilledIn
import com.hyuchia.orbit.reminders.IdeaToShow
import com.hyuchia.orbit.reminders.KeepIdeaToShow
import com.hyuchia.orbit.settings.AboutScreen
import com.hyuchia.orbit.settings.LicensesScreen
import com.hyuchia.orbit.settings.PrivacyScreen
import com.hyuchia.orbit.settings.SettingsScreen
import com.hyuchia.orbit.settings.TrashScreen
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MinimumMapWidth
import com.hyuchia.orbit.ui.ShortScreen
import com.hyuchia.orbit.ui.WideScreen
import com.hyuchia.orbit.ui.rememberDefaultButton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

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
 * size, as `remember` isn't, so the question never goes unanswered. Files
 * of maps that are here already wait on disk too (`waiting`), and the
 * screen's saved state (`saved`) says which: Android may stop Orbit while
 * one is asked about, as the screen lock is asked for to update a private
 * map, and the screens it brings back ask about them again, as they were,
 * once the maps are read (`mapsRead` returns then): until then nothing says
 * which maps are private, and the question would name one. Those no screen
 * will ask about again go as a new screen starts.
 *
 * Files opened with Orbit and things shared to it are imported by
 * `intake`, and the saved state lists them until what they brought is
 * taken ([importing], [took]): a screen Android brings back imports again
 * what it stopped in the middle of, or shows what it brought.
 *
 * One answered Update or Keep both goes to `intake`, which takes it in
 * whatever happens to the screen ([answer]). The saved state lists it until
 * its answer is kept beside its files, so a screen Android brings back
 * before then asks it again. One that couldn't be taken in comes back, once
 * why is said ([askAgain]), and every screen that starts asks it again.
 */
class Arrivals(private val waiting: WaitingArrivals, private val intake: Intake, private val saved: SavedStateHandle, private val mapsRead: suspend () -> Unit) : ViewModel() {
    var problem by mutableStateOf<String?>(null)

    private var notTakenInBecause by mutableStateOf<String?>(null)

    /**
     * Why a map answered Update or Keep both couldn't be taken in, said
     * once nothing is asked; the questions that come back after it
     * ([askAgain]) are asked once it's said.
     */
    var notTakenIn: String?
        get() = notTakenInBecause
        set(value) {
            notTakenInBecause = value

            if (value == null && returning.isNotEmpty()) {
                asking += returning
                returning = emptyList()
                remember(asking)
            }
        }

    var many by mutableStateOf<ImportResult.ImportedMany?>(null)
    var notes by mutableStateOf<List<String>?>(null)

    private var asking by mutableStateOf(OtherVersions())

    /** Questions answered Update or Keep both whose answers aren't kept on disk yet: the saved state still lists them. */
    private var answering = emptyList<OtherVersion>()

    /** Questions that couldn't be taken in, asked again once why is said ([notTakenIn]). */
    private var returning = emptyList<OtherVersion>()

    /** Keeps and lets go of what waits on disk one at a time, in the order asked, so nothing outlasts its answer. */
    private val disk = Mutex()

    /**
     * Files of maps that are here already, asked about one after another
     * (see [OtherVersions]): each one that joins is kept on disk until it's
     * answered ([answer], [decline]).
     */
    var otherVersions: OtherVersions
        get() = asking
        set(value) {
            val before = asking.waiting
            asking = value
            remember(value)

            for (joined in value.waiting.filter { question -> before.none { it === question } }) {
                onDisk { waiting.keep(joined.arrival, joined.opens) }
            }
        }

    init {
        val kept = saved.get<ArrayList<String>>(WAITING_KEY).orEmpty()
        val opens = saved.get<BooleanArray>(OPENS_KEY)
        val answered = saved.get<Int>(ANSWERED_KEY) ?: 0

        // What this screen was importing before Android stopped Orbit is imported again, or what it brought shown; a new
        // screen, whose task was closed and with it the files' permissions, lets them go.
        intake.resumeImports(saved.get<ArrayList<String>>(REQUESTS_KEY).orEmpty())

        // The screens Android brought back ask again what their screen asked before it stopped Orbit; a new one, nothing,
        // but what couldn't be taken in once answered, which every screen asks again.
        viewModelScope.launch {
            val back = disk.withLock {
                withContext(NonCancellable) {
                    waiting.letGoAllBut(kept.toSet())
                    val listed = kept.mapIndexedNotNull { index, id -> waiting.read(id)?.let { OtherVersion(it, opens = opens?.getOrNull(index) == true) } }
                    val again = waiting.askedAgain().filter { (arrival, _) -> listed.none { it.arrival.folder == arrival.folder } }
                    listed + again.map { (arrival, opens) -> OtherVersion(arrival, opens) }
                }
            }

            // Asked once Orbit knows which maps are private, so a private map's question says nothing of it.
            mapsRead()

            val fresh = back.filter { question -> (asking.waiting + returning).none { it.arrival.folder == question.arrival.folder } }

            if (fresh.isNotEmpty()) {
                asking = OtherVersions(fresh + asking.waiting, answered)
                remember(asking)
            }
        }
    }

    /**
     * `question`, the one asked, was answered Update, or Keep both
     * (`keepBoth`): it's taken in whatever happens to this screen, and the
     * next question is asked. The saved state lists it until its answer is
     * kept on disk.
     */
    fun answer(question: OtherVersion, keepBoth: Boolean) {
        if (asking.asked !== question) {
            return
        }

        answering = answering + question
        asking = asking.after(question)
        remember(asking)
        val written = intake.accept(question, keepBoth)

        viewModelScope.launch {
            written.await()
            answering = answering.filterNot { it === question }
            remember(asking)
        }
    }

    /** `question`, the one asked, was answered Neither: what its file was unpacked to goes now, as a map's can come to a gigabyte. */
    fun decline(question: OtherVersion) {
        if (asking.asked !== question) {
            return
        }

        asking = asking.after(question)
        remember(asking)
        onDisk { waiting.letGo(question.arrival) }
    }

    /** Import `id` ([Intake.importFile]) runs for this screen: the saved state lists it until what it brought is taken ([took]). */
    fun importing(id: String) {
        saved[REQUESTS_KEY] = ArrayList(saved.get<ArrayList<String>>(REQUESTS_KEY).orEmpty() + id)
    }

    /** What import `id` brought is taken: no screen Android brings back asks for it again. */
    fun took(id: String?) {
        if (id != null) {
            saved[REQUESTS_KEY] = ArrayList(saved.get<ArrayList<String>>(REQUESTS_KEY).orEmpty() - id)
        }
    }

    /** Questions about maps here already that `result` brought join those asked, but those asked already, as a file a screen that started meanwhile asks too. */
    fun join(questions: List<OtherVersion>) {
        otherVersions += questions.filter { question -> (asking.waiting + returning).none { it.arrival.folder == question.arrival.folder } }
    }

    /** `question` was answered, but couldn't be taken in, for `reason`: why is said, then it's asked again. */
    fun askAgain(question: OtherVersion, reason: String) {
        if ((asking.waiting + returning).any { it.arrival.folder == question.arrival.folder }) {
            return
        }

        returning = returning + question

        if (notTakenInBecause == null) {
            notTakenInBecause = reason
        }
    }

    /** Says which files wait, in order, whether each opens its map, and how many were answered before them, for the screen's saved state. */
    private fun remember(versions: OtherVersions) {
        val kept = (answering + versions.waiting).mapNotNull { question -> question.arrival.bundle.unpacked?.let { it.name to question.opens } }
        saved[WAITING_KEY] = ArrayList(kept.map { it.first })
        saved[OPENS_KEY] = kept.map { it.second }.toBooleanArray()
        saved[ANSWERED_KEY] = versions.answered
    }

    private fun onDisk(work: suspend () -> Unit) {
        viewModelScope.launch {
            disk.withLock { withContext(NonCancellable) { work() } }
        }
    }

    private companion object {
        const val WAITING_KEY = "waiting"
        const val OPENS_KEY = "opens"
        const val ANSWERED_KEY = "answered"
        const val REQUESTS_KEY = "requests"
    }
}

/** The folder an arrival was unpacked to, which says which one it is wherever it's read from. */
private val ImportResult.AlreadyHere.folder: String? get() = bundle.unpacked?.name

/**
 * Whether a question about another version of map `id` keeps which map it
 * is to itself: while private maps are `locked`, for one `hides` says is
 * locked, and for one Orbit has no details of (`summary` gives none), as
 * before the maps are read, since it may be a private one.
 */
internal fun arrivalHides(id: String?, locked: Boolean, summary: (String) -> MapSummary?, hides: (MapSummary) -> Boolean): Boolean {
    if (id == null || !locked) {
        return false
    }

    return summary(id)?.let(hides) ?: true
}

/**
 * Whether a question about another version of map `id` is about a private
 * map, which Recents then gets no picture of: one Orbit has no details of
 * counts, as it may be one.
 */
internal fun arrivalNamesPrivate(id: String?, summary: (String) -> MapSummary?): Boolean = id != null && summary(id)?.metadata?.isPrivate != false

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
    /**
     * A map to open: `ownMark` when the intent came from Orbit's own shortcut,
     * widget or reminder ([OpenMark]), and a reminder's `idea` to show there.
     */
    data class OpenMap(val id: String, val ownMark: Boolean = true, val idea: IdeaToShow? = null) : AppRequest

    data object NewMap : AppRequest
    data class ImportFile(val uri: Uri) : AppRequest
    data class ImportText(val text: String, val subject: String?) : AppRequest

    /** Files of any kind shared to Orbit, to join an idea: the one in focus on the map that's open, or a new map's. */
    data class AttachFiles(val uris: List<Uri>) : AppRequest

    /**
     * Text shared to Orbit to join an idea, with no file: ideas under the one
     * in focus on the map that's open, as pasting there makes them, or a new
     * map, as text shared to Orbit makes one, with its `subject`.
     */
    data class AttachText(val text: String, val subject: String?) : AppRequest
}

/** Whether a window has room for the list beside a map one way up or the other, as a tablet's does and a phone's doesn't. */
internal fun roomForTwoPanes(window: DpSize): Boolean =
    maxOf(window.width, window.height) >= WideScreen && minOf(window.width, window.height) >= ShortScreen

/**
 * How Home's list and the map share `window`: side by side on a tablet,
 * whichever way up it is, and one at a time on a phone, in a window too short
 * for both (a phone on its side), and while the map wants the room
 * (`mapWantsRoom`).
 *
 * Android's own directive has two panes from an expanded width, a tablet's on
 * its side: upright, a tablet is only medium width, and the list would step
 * aside there for good. On a tablet, two panes start at a medium width, as
 * long as the map keeps a phone's width beside the list ([MinimumMapWidth]):
 * a small tablet upright shows the map on its own.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun listAndMapDirective(adaptiveInfo: WindowAdaptiveInfo, window: DpSize, mapWantsRoom: Boolean): PaneScaffoldDirective {
    val upright = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo)
    val roomBesideList = window.width - upright.defaultPanePreferredWidth - upright.horizontalPartitionSpacerSize >= MinimumMapWidth
    val calculated = if (roomForTwoPanes(window) && roomBesideList) upright else calculatePaneScaffoldDirective(adaptiveInfo)
    // A window too short for the list beside a map, such as a phone on its side, shows one pane at a time.
    val short = !adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    return PaneScaffoldDirective(
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

/**
 * How Home's list and the map share a window with room for both (`directive`).
 *
 * On a tablet, Home stays in the list-detail scene whether the map's pane is
 * beside it or it has the window to itself, so turning the tablet only
 * resizes it. In a scene of its own when upright, it moved from one scene to
 * the other in the middle of every turn's transition, and Compose measured
 * its focused search field while it moved, and threw. A map on its own still
 * gets a scene of its own, so it grows out of Home and shrinks back, unless
 * it's on its own only for wanting the whole window (`listFits`, for a
 * sketch or a panel beside it): it stays in this scene then, its pane
 * growing over the list's and shrinking back. Moved to a scene of its own
 * and back, its page was drawn blank, and the window white, for a moment.
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
internal fun <T : Any> rememberListDetailScenes(homeOnTop: Boolean, roomForTwo: Boolean, listFits: Boolean, directive: PaneScaffoldDirective): ListDetailSceneStrategy<T> =
    rememberListDetailSceneStrategy(
        shouldHandleSinglePaneLayout = roomForTwo && (homeOnTop || listFits),
        backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
        directive = directive,
    )

/** Whether the list shows beside the map the window has room for (`directive`): on a tablet, unless the map wants the room. */
internal fun listBesideMap(directive: PaneScaffoldDirective): Boolean = directive.maxHorizontalPartitions >= 2

/**
 * Whether a change of scene keeps the same screen in front, as when a map on
 * a tablet takes the whole window from the list beside it, for a sketch or a
 * panel, and when it gives the list its room back: no step forward or back.
 */
internal fun showsSameScreen(initial: Scene<*>, target: Scene<*>): Boolean =
    initial.entries.lastOrNull()?.contentKey == target.entries.lastOrNull()?.contentKey

/**
 * The same screen in a new scene shows there at once. Its content moves
 * there as the scene changes, so fading the new scene in, a moment after the
 * old one began fading out, showed the window's bare background between them
 * for a fraction of a second, white across the whole window.
 */
private fun inPlace(): ContentTransform = ContentTransform(EnterTransition.None, ExitTransition.None)

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
    KeepIdeaToShow(container.ideaToShow)
    val arrivals = viewModel { Arrivals(container.maps.waitingArrivals, container.intake, createSavedStateHandle(), mapsRead = { container.maps.maps.filterNotNull().first() }) }
    var importProblem by arrivals::problem
    // What a zip of maps brought in.
    var importedMany by arrivals::many

    // What a map from another app had that Orbit keeps another way, or couldn't keep, said once it's open.
    var importNotes by arrivals::notes
    // A map with a panel beside it, its text or a presentation open takes the whole window: the list steps aside.
    var mapWantsRoom by remember { mutableStateOf(false) }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val window = LocalWindowInfo.current.containerDpSize
    val roomForTwo = roomForTwoPanes(window)
    val directive = remember(adaptiveInfo, window, mapWantsRoom) { listAndMapDirective(adaptiveInfo, window, mapWantsRoom) }
    // Whether the list has room beside a map that doesn't want the whole window.
    val listFits = remember(adaptiveInfo, window) { listBesideMap(listAndMapDirective(adaptiveInfo, window, mapWantsRoom = false)) }

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
            is ImportResult.AlreadyHere -> arrivals.join(listOf(OtherVersion(result, opens = true)))
            is ImportResult.Failed -> importProblem = result.reason
            is ImportResult.SeveralFiles -> {
                // What the rest brought is said once these are answered (see below).
                arrivals.join(result.otherVersions.map { OtherVersion(it, opens = false) })
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
                    container.ideaToShow.value = request.idea
                    openMap(request.id)
                }
                AppRequest.NewMap -> if (backStack.lastOrNull() != NewMapKey) {
                    backStack.add(NewMapKey)
                }
                // Imported by the app, not this screen: made again meanwhile, it's imported all the same, and what it brought is
                // said by the screen there is then (see below).
                is AppRequest.ImportFile -> arrivals.importing(container.intake.importFile(request.uri))
                is AppRequest.ImportText -> arrivals.importing(container.intake.importText(request.text, request.subject))
                is AppRequest.AttachFiles -> {
                    // The map that's open takes them, into its idea in focus, unless it's locked: a new map holds them then.
                    val open = (backStack.lastOrNull() as? MapKey)?.id?.takeIf { container.maps.exists(it) && !container.privateMaps.hidesNow(it) }

                    // They wait for its map to be ready, however soon they came (MapViewModel.takeShares).
                    if (open != null) {
                        container.sharedFiles.offer(SharedFiles(open, request.uris))
                    } else {
                        arrivals.importing(container.intake.newMapWithFiles(request.uris))
                    }
                }
                is AppRequest.AttachText -> {
                    // As files: the map that's open takes it, as ideas under its idea in focus, unless it's locked; a new map then.
                    val open = (backStack.lastOrNull() as? MapKey)?.id?.takeIf { container.maps.exists(it) && !container.privateMaps.hidesNow(it) }

                    if (open != null) {
                        val lines = listOfNotNull(request.subject, request.text).filter { it.isNotBlank() }.distinct().joinToString("\n")
                        container.sharedFiles.offer(SharedFiles(open, emptyList(), text = lines))
                    } else {
                        arrivals.importing(container.intake.importText(request.text, request.subject))
                    }
                }
            }
        }
    }

    // What the app imported, took in once answered, or couldn't take in, here or before this screen was made again: each
    // once, by the screen there is.
    val outcomes by container.intake.outcomes.collectAsStateWithLifecycle()

    LaunchedEffect(outcomes) {
        for (outcome in outcomes) {
            container.intake.take(outcome)

            when (outcome) {
                is IntakeOutcome.Imported -> {
                    arrivals.took(outcome.request)
                    imported(outcome.result)
                }
                is IntakeOutcome.Opened -> openMap(outcome.id)
                is IntakeOutcome.AskAgain -> arrivals.askAgain(outcome.question, outcome.reason)
            }
        }
    }

    // What's shared to a map waits for it while it's open, and goes as it closes.
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.filterIsInstance<MapKey>().map { it.id }.toSet() }.collect(container.sharedFiles::keepFor)
    }

    NavDisplay<NavKey>(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(rememberListDetailScenes(homeOnTop = backStack.lastOrNull() == HomeKey, roomForTwo = roomForTwo, listFits = listFits, directive = directive)),
        transitionSpec = { if (showsSameScreen(initialState, targetState)) inPlace() else forward() },
        popTransitionSpec = { if (showsSameScreen(initialState, targetState)) inPlace() else backward() },
        predictivePopTransitionSpec = { if (showsSameScreen(initialState, targetState)) inPlace() else backward() },
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
    fun hides(id: String?): Boolean = arrivalHides(id, privacy.locked, container.maps::summary, container.privateMaps::hides)

    // A private map here is named by the cover its owner chose, if it has one, while its name isn't said.
    fun cover(id: String?) = id?.let(container.maps::summary)?.metadata?.cover

    // Unlocked, a dialog about another version of a private map names it: Recents gets no picture of Orbit then, as of the map.
    fun namesPrivate(id: String?): Boolean = arrivalNamesPrivate(id, container.maps::summary) && !hides(id)

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
                    // A second tap, or one after the offer's time ran out, finds it answered: it does nothing.
                    when (val accepted = container.nearby.accept(offer, keepBoth = answer == Arrival.KeepBoth)) {
                        is Accepted.Opened -> openMap(accepted.id)
                        Accepted.NotKept -> importProblem = resources.getString(R.string.main_could_not_keep_sent_map, offer.from)
                        Accepted.AnsweredAlready -> Unit
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
            // Taken in by the app, not this screen: made again or closed meanwhile, it's still taken in, once, and its map
            // opens on the screen there is then.
            if (answer == Arrival.Decline) {
                arrivals.decline(question)
            } else {
                arrivals.answer(question, keepBoth = answer == Arrival.KeepBoth)
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

                Text(
                    stringResource(
                        when {
                            result.stopped != null -> R.string.main_import_stopped
                            result.added == 0 -> R.string.main_nothing_new
                            else -> R.string.main_maps_added
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // While private maps hide, the ones already here aren't counted, nor files brought into them.
                    val concealing = privacy.conceals(hidingPrivateMaps)
                    val alreadyHere = mentionedAlreadyHere(result.alreadyHere, result.privateAlreadyHere, concealing)
                    val filledIn = mentionedFilledIn(result.filledIn, result.privateFilledIn, concealing)
                    val pastRoom = mentionedFilledIn(result.pastRoom, result.privatePastRoom, concealing)

                    // One that stopped partway says why first, then only what came: nothing of the file as a whole.
                    result.stopped?.let { Text(it) }

                    when {
                        result.stopped != null && result.added == 0 -> null
                        result.added == 0 && result.alreadyHere == 0 -> stringResource(R.string.main_no_maps_in_zip)
                        result.added == 0 -> stringResource(R.string.main_every_map_in_zip_already_here)
                        alreadyHere == 0 -> pluralStringResource(R.plurals.common_added_maps, result.added, result.added)
                        alreadyHere == 1 -> pluralStringResource(R.plurals.common_added_maps_with_one_already_here, result.added, result.added)
                        else -> pluralStringResource(R.plurals.common_added_maps_with_several_already_here, result.added, result.added, alreadyHere)
                    }?.let { Text(it) }

                    val notes = listOfNotNull(
                        pluralStringResource(R.plurals.data_zip_files_filled_in, filledIn, filledIn).takeIf { filledIn > 0 },
                        Importer.pastRoomNote(pastRoom, container.words),
                    ) + result.notes

                    // Only when there are some, so what follows is spaced as the rest is.
                    if (notes.isNotEmpty()) {
                        ImportNotes(notes)
                    }

                    if (result.stopped != null) {
                        Text(stringResource(R.string.common_import_again_for_the_rest))
                    }
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

    // Why a map answered Update or Keep both couldn't be taken in: its question comes back once this is said.
    arrivals.notTakenIn?.takeUnless { answering }?.let { reason ->
        AlertDialog(
            onDismissRequest = { arrivals.notTakenIn = null },
            title = {
                EscapeCloses { arrivals.notTakenIn = null }
                Text(stringResource(R.string.main_could_not_take_map_in))
            },
            text = { Text(reason) },
            confirmButton = { TextButton(onClick = { arrivals.notTakenIn = null }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.main_ok)) } },
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
