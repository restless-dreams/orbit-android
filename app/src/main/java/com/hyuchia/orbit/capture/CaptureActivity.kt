package com.hyuchia.orbit.capture

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.KeyboardsDontLearn
import com.hyuchia.orbit.privacy.LockEmblem
import com.hyuchia.orbit.privacy.LockedLook
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.PrivateView
import com.hyuchia.orbit.privacy.ShowPrivateMapsButton
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.coverTitle
import com.hyuchia.orbit.privacy.keepPrivateMapsOutOfRecents
import com.hyuchia.orbit.privacy.lockedLook
import com.hyuchia.orbit.privacy.privateSheetProperties
import com.hyuchia.orbit.privacy.rememberUnlocking
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.ThemedSystemBars
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.orbitViewModel
import com.hyuchia.orbit.ui.systemReason
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The map a capture adds to, when one asks for it (a home screen widget's). */
const val EXTRA_CAPTURE_MAP = "capture_map"

/**
 * Opens the sheet to catch ideas into a map: `mapId`'s, or the one ideas
 * went to last. A sheet left open, in a task of its own, comes back as it
 * was, with what was typed in it, even after Android stopped Orbit: it
 * takes `mapId`, if any, and keeps the draft ([CaptureActivity.onNewIntent]).
 */
fun captureIntent(context: Context, mapId: String? = null): Intent =
    Intent(context, CaptureActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply { mapId?.let { putExtra(EXTRA_CAPTURE_MAP, it) } }

/** The sheet opened again while it's there, by the tile or a widget's +, which asks for `mapId`'s map, if any: each one is new, even for the same map. */
internal class CaptureAgain(val mapId: String?)

/**
 * What another app shared, to catch as ideas: its subject and its text, a
 * line each, once when they're the same. Text with styles, as a notes app
 * or a browser shares it, comes as a CharSequence that isn't a String: its
 * words come all the same.
 */
internal fun sharedText(intent: Intent): String =
    listOfNotNull(intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT), intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        .map { it.toString() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n")

/**
 * Catches ideas into a map without opening it: a sheet over whatever is on
 * screen, from the Quick Settings tile, a home screen widget's +, or
 * "Add to an Orbit map" in another app's share sheet. Each line is an idea,
 * nested as the lines are indented, as pasting nests them.
 */
class CaptureActivity : ComponentActivity() {
    /** The latest time the sheet was opened again while it was there ([onNewIntent]). */
    private var again by mutableStateOf<CaptureAgain?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Its list names private maps while they're unlocked: Recents gets no picture of it then, as of Orbit itself.
        keepPrivateMapsOutOfRecents()
        val app = container
        val settings = app.settings.settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)

        lifecycleScope.launch {
            if (app.maps.maps.value == null) {
                app.maps.load()
            }
        }

        // A page warms while the idea is typed, so adding it is quick.
        app.pages.warm()
        val shared = sharedText(intent)
        val requested = intent.getStringExtra(EXTRA_CAPTURE_MAP)

        setContent {
            val current by settings.collectAsStateWithLifecycle()
            val maps by app.maps.maps.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalAppSettings provides (current ?: AppSettings())) {
                val dark = appIsDark()
                ThemedSystemBars(dark, overOtherApps = true)

                OrbitTheme(dark = dark) {
                    val loaded = maps
                    val chosen = current

                    if (loaded != null && chosen != null) {
                        CaptureSheet(
                            maps = loaded,
                            initialMapId = requested ?: chosen.captureMapId,
                            initialText = shared,
                            again = again,
                            onDone = ::finish,
                        )
                    }
                }
            }
        }
    }

    /** Opened again while it's there: the sheet stays as it is, with its draft, and takes the map a widget's + asks for. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        again = CaptureAgain(intent.getStringExtra(EXTRA_CAPTURE_MAP))
    }
}

/** Maps to catch ideas into: pinned ones first, then the most recently opened. */
private fun captureOrder(maps: List<MapSummary>): List<MapSummary> =
    maps.sortedWith(compareByDescending<MapSummary> { it.metadata.pinned }.thenByDescending { it.metadata.opened })

/**
 * What quick capture offers: the maps ideas can go to, and a way to private maps while they're
 * locked. Locked private maps aren't offered nor named; "Unlock private maps" in the list brings them
 * in (`unlockInList`), only when there are some. While Settings hides them, nothing says there are:
 * the open lock beside the title (`showButton`) brings them in, offered whether there are any or not.
 */
internal data class CaptureChoices(val maps: List<MapSummary>, val unlockInList: Boolean, val showButton: Boolean) {
    /** Whether the list names private maps: they're unlocked, and Recents gets no picture of it then. */
    val namesPrivateMaps: Boolean get() = maps.any { it.metadata.isPrivate }
}

internal fun captureChoices(maps: List<MapSummary>, privacy: PrivateView, hiding: Boolean): CaptureChoices {
    val concealing = privacy.conceals(hiding)
    return CaptureChoices(captureOrder(maps.filterNot(privacy::hides)), unlockInList = !concealing && maps.any(privacy::hides), showButton = concealing)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSheet(maps: List<MapSummary>, initialMapId: String?, initialText: String, again: CaptureAgain?, onDone: () -> Unit) {
    val context = LocalContext.current

    // Adding outlasts the sheet made again as the phone turns: it goes on, and is said once, by the sheet there is then.
    val model = orbitViewModel { container ->
        CaptureModel { target, ideas, text ->
            // Said as private maps are once the ideas are in, which takes a moment: the map as it is then, and the lock.
            val hidesNow = { container.privateMaps.view.value.hides(container.maps.summary(target.id) ?: target) }
            captureInto(target, ideas, container.application.resources, hidesNow) { container.capture.add(target.id, text) }
        }
    }

    // While private maps are locked, they aren't offered, nor named: Unlock brings them in (see captureChoices). As they
    // are now, not as last heard: the sheet coming back is drawn, from its first frame, as they are then, and so is a
    // draft under their lock (below).
    val heard by context.container.privateMaps.view.collectAsStateWithLifecycle()
    var starts by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { starts += 1 }
    val privacy = remember(heard, starts) { context.container.privateMaps.view.value }
    val unlocking = rememberUnlocking()
    val hiding = LocalAppSettings.current.hideLockedPrivateMaps
    val choices = remember(maps, privacy, hiding) { captureChoices(maps, privacy, hiding) }
    val ordered = choices.maps
    fun lockedPrivate(id: String?): Boolean = maps.any { it.id == id && privacy.hides(it) }

    // Ideas never quietly go to another map than the one meant: while that's a private map that's locked, none is
    // chosen (`waitingFor` is it), until the person picks one or unlocks.
    var waitingFor by rememberSaveable { mutableStateOf(initialMapId?.takeIf(::lockedPrivate)) }
    var mapId by rememberSaveable { mutableStateOf(if (waitingFor != null) null else initialMapId?.takeIf { id -> ordered.any { it.id == id } } ?: ordered.firstOrNull()?.id) }

    // Shared text arrives with the cursor after it, ready for a line more.
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }

    // A draft for a private map stays as private maps lock, but under a lock of its own in the sheet, worked out as it's
    // drawn: never shown, nor added anywhere, until they're unlocked, and never thrown away. What's typed for one while
    // it's locked shows as it's typed, and as the phone turns (`shownLocked`), but not once the sheet comes back after
    // being away; once covered, it stays covered for its map (`coveredFor`), whatever map is chosen then. Typed for a
    // private map, the sheet is kept out of Recents and screenshots, as the map is.
    var shownLocked by rememberSaveable { mutableStateOf(initialText.isNotBlank()) }
    var coveredFor by rememberSaveable { mutableStateOf<String?>(null) }
    fun hidesNow(id: String?): Boolean = id != null && lockedPrivate(id)
    val target = waitingFor ?: mapId
    val coveredNow = text.text.isNotBlank() && !shownLocked && hidesNow(target)
    val covered = target?.takeIf { coveredNow } ?: coveredFor?.takeIf(::hidesNow)

    SideEffect {
        if (coveredNow) {
            coveredFor = target
        }
    }

    // Away, but to be made again as the phone turns: what was typed while it was locked shows no more once it's back.
    val activity = LocalActivity.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            shownLocked = false
        }
    }

    LaunchedEffect(privacy.locked) {
        if (!privacy.locked) {
            shownLocked = false
            coveredFor = null
        }
    }

    PrivateOnScreen(choices.namesPrivateMaps || maps.any { it.id == mapId && it.metadata.isPrivate } || waitingFor != null || covered != null)

    // Unlocked, private maps come in: the one ideas went to last, or were going to, when it's one of them.
    LaunchedEffect(ordered) {
        val chosen = mapId

        when {
            // A private map chosen that locks goes, and so does what went wrong adding to it, which names it.
            chosen != null && ordered.none { it.id == chosen } && lockedPrivate(chosen) -> {
                model.problem = null
                waitingFor = chosen
                mapId = null
            }
            waitingFor != null && ordered.any { it.id == waitingFor } -> {
                mapId = waitingFor
                waitingFor = null
            }
            waitingFor == null && (chosen == null || ordered.none { it.id == chosen }) -> {
                mapId = initialMapId?.takeIf { id -> ordered.any { it.id == id } } ?: ordered.firstOrNull()?.id
            }
        }
    }

    // Opened again over itself, it keeps its draft: a widget's + chooses its map, named above the draft, so it's seen where the
    // ideas will go; a private one that's locked is waited for, as at the start.
    LaunchedEffect(again) {
        val requested = again?.mapId ?: return@LaunchedEffect

        when {
            lockedPrivate(requested) -> {
                waitingFor = requested
                mapId = null
            }
            ordered.any { it.id == requested } -> {
                mapId = requested
                waitingFor = null
            }
        }
    }

    val adding = model.adding
    val ideas = ideasFromText(text.text)
    val map = ordered.firstOrNull { it.id == mapId }
    val focus = remember { FocusRequester() }
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    fun add() {
        val into = map?.takeIf { covered == null } ?: return

        if (ideas.isNotEmpty()) {
            model.add(into, ideas.size, text.text)
        }
    }

    // In, the ideas are said once, by the sheet there is then, which closes.
    LaunchedEffect(model.added) {
        model.added?.let { said ->
            Toast.makeText(context.applicationContext, said, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state, properties = privateSheetProperties()) {
        EscapeCloses(onDone)

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))

                if (choices.showButton) {
                    ShowPrivateMapsButton(onShow = { unlocking.ask() })
                }
            }

            if (ordered.isEmpty() && !choices.unlockInList) {
                Text(
                    stringResource(R.string.capture_no_maps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.common_close)) }
                }

                return@Column
            }

            MapChooser(
                ordered,
                map,
                onChoose = {
                    mapId = it.id
                    waitingFor = null
                },
                onUnlock = if (choices.unlockInList) ({ unlocking.ask() }) else null,
            )

            if (covered != null) {
                DraftLock(cover = maps.firstOrNull { it.id == covered }?.metadata?.cover, look = lockedLook(covered), onUnlock = { unlocking.ask() })
            } else {
                // Ideas for a private map, chosen or waited for while it's locked, ask keyboards not to learn from them, as
                // what's typed into the map does.
                KeyboardsDontLearn(enabled = map?.metadata?.isPrivate == true || waitingFor != null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it

                            // Typed for a private map while it's locked, by whoever has the phone now: it shows to them.
                            if (hidesNow(target)) {
                                shownLocked = true
                            }
                        },
                        label = {
                            val label = if (ideas.size > 1) {
                                pluralStringResource(R.plurals.capture_ideas_one_on_each_line, ideas.size, ideas.size)
                            } else {
                                stringResource(R.string.capture_your_idea)
                            }

                            Text(label)
                        },
                        placeholder = { Text(stringResource(R.string.capture_one_idea_on_each_line)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        minLines = 2,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .focusRequester(focus)
                            // With a keyboard, Ctrl+Enter adds, as Enter starts a new line, a new idea.
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && event.isCtrlPressed) {
                                    add()
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                }
            }

            // The sheet has a window of its own, focused once it's up (from Quick Settings, once the panel
            // folds away) and again after a map is chosen: the keyboard comes up then, ready for the idea.
            val window = LocalWindowInfo.current
            val keyboard = LocalSoftwareKeyboardController.current

            // Under its lock, the draft has no field to type in: none comes up.
            LaunchedEffect(window.isWindowFocused, covered) {
                if (window.isWindowFocused && !adding && covered == null) {
                    focus.requestFocus()
                    keyboard?.show()
                }
            }

            // Screen readers hear what went wrong as it shows: the ideas are still here.
            model.problem?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))

                // Adding, it keeps its place and says so, beside a small indicator.
                Button(
                    onClick = ::add,
                    enabled = ideas.isNotEmpty() && map != null && !adding && covered == null,
                    contentPadding = if (adding) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding,
                ) {
                    if (adding) {
                        LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.capture_adding))
                    } else if (ideas.size > 1) {
                        Text(pluralStringResource(R.plurals.capture_add_ideas, ideas.size, ideas.size))
                    } else {
                        Text(stringResource(R.string.capture_add))
                    }
                }
            }
        }
    }
}

/** How adding ideas to a map went, in the words quick capture says it with. */
internal sealed interface CaptureOutcome {
    /** They're in the map: `said` says so. */
    data class Added(val said: String) : CaptureOutcome

    /** They aren't: `problem` says why, or is null for a private map that locked meanwhile, which isn't named. */
    data class NotAdded(val problem: String?) : CaptureOutcome
}

/**
 * Adds `ideas` ideas to `target` with `add`, and says how it went as private
 * maps are once it's done (`hidesNow`, asked then), not as they were when
 * it began: adding takes a moment, and they can lock meanwhile, as when the
 * screen turns off. A private map that's locked by then isn't named: the
 * ideas are said to be added, to no map named, and what went wrong goes
 * unsaid, as when they lock while it shows (the map leaves the list, and
 * the ideas stay, to add once it's unlocked).
 */
internal suspend fun captureInto(target: MapSummary, ideas: Int, resources: Resources, hidesNow: () -> Boolean, add: suspend () -> Unit): CaptureOutcome {
    val failure = try {
        add()
        null
    } catch (failed: Exception) {
        // Unless the sheet itself went, anything that stopped the ideas is what went wrong.
        currentCoroutineContext().ensureActive()
        failed
    }

    val hidden = hidesNow()

    if (failure != null) {
        return CaptureOutcome.NotAdded(if (hidden) null else captureProblem(resources, target.title, failure))
    }

    val said = when {
        hidden && ideas == 1 -> resources.getString(R.string.capture_added_idea_to_locked_map)
        hidden -> resources.getQuantityString(R.plurals.capture_added_ideas_to_locked_map, ideas, ideas)
        ideas == 1 -> resources.getString(R.string.capture_added_idea, target.title)
        else -> resources.getQuantityString(R.plurals.capture_added_ideas, ideas, ideas, target.title)
    }

    return CaptureOutcome.Added(said)
}

/** What the sheet says when ideas didn't join the map called `title`: why, when that's known, and that they're still here. */
private fun captureProblem(resources: Resources, title: String, failure: Exception): String {
    if (failure is CaptureException) {
        val words = when (failure.failure) {
            // Nothing to add: the text holds no idea, which no map's name helps with.
            CaptureFailure.NoIdeas -> return resources.getString(R.string.pages_no_ideas)
            CaptureFailure.Refused -> R.string.capture_problem_refused
            CaptureFailure.Missing -> R.string.capture_problem_missing
            CaptureFailure.KeptChanging -> R.string.capture_problem_kept_changing
            CaptureFailure.NotWritten -> R.string.capture_problem_not_written
        }

        return resources.getString(words, title)
    }

    val reason = resources.systemReason(failure) ?: return resources.getString(R.string.capture_problem, title)
    return resources.getString(R.string.capture_problem_with_reason, title, reason.trimEnd('.'))
}

/** Which map the ideas go to; `onUnlock` brings private maps in while they're locked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapChooser(maps: List<MapSummary>, chosen: MapSummary?, onChoose: (MapSummary) -> Unit, onUnlock: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = chosen?.title.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.capture_map)) },
            leadingIcon = { Icon(Icons.Outlined.Hub, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            maps.forEach { map ->
                DropdownMenuItem(
                    text = { Text(map.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (map.metadata.isPrivate) {
                        { Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.private_badge)) }
                    } else {
                        null
                    },
                    onClick = {
                        onChoose(map)
                        open = false
                    },
                )
            }

            onUnlock?.let { unlock ->
                if (maps.isNotEmpty()) {
                    HorizontalDivider()
                }

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.private_unlock_private_maps)) },
                    leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
                    onClick = {
                        open = false
                        unlock()
                    },
                )
            }
        }
    }
}

/**
 * A draft for a private map, under a lock of its own while private maps are
 * locked: the map's lock, as its own cover shows it (`cover`, `look`), that
 * the ideas are kept, and Unlock, which asks for the screen lock as the
 * map's own lock does.
 */
@Composable
private fun DraftLock(cover: LockedCover?, look: LockedLook, onUnlock: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            LockEmblem(open = false, modifier = Modifier.size(48.dp), iconSize = 22.dp, look = look, icon = cover?.icon)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(coverTitle(cover), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.capture_draft_locked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            FilledTonalButton(onClick = onUnlock) {
                Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.private_unlock))
            }
        }
    }
}
