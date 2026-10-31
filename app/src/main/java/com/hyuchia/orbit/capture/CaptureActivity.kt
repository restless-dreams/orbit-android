package com.hyuchia.orbit.capture

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalResources
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.PrivateView
import com.hyuchia.orbit.privacy.ShowPrivateMapsButton
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.keepPrivateMapsOutOfRecents
import com.hyuchia.orbit.privacy.rememberUnlocking
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.ThemedSystemBars
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.systemReason
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The map a capture adds to, when one asks for it (a home screen widget's). */
const val EXTRA_CAPTURE_MAP = "capture_map"

/** Opens the sheet to catch ideas into a map: `mapId`'s, or the one ideas went to last. */
fun captureIntent(context: Context, mapId: String? = null): Intent =
    Intent(context, CaptureActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .apply { mapId?.let { putExtra(EXTRA_CAPTURE_MAP, it) } }

/**
 * Catches ideas into a map without opening it: a sheet over whatever is on
 * screen, from the Quick Settings tile, a home screen widget's +, or
 * "Add to an Orbit map" in another app's share sheet. Each line is an idea,
 * nested as the lines are indented, as pasting nests them.
 */
class CaptureActivity : ComponentActivity() {
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
        val shared = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
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
                            onDone = ::finish,
                        )
                    }
                }
            }
        }
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
private fun CaptureSheet(maps: List<MapSummary>, initialMapId: String?, initialText: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // While private maps are locked, they aren't offered, nor named: Unlock brings them in (see captureChoices).
    val privacy by context.container.privateMaps.view.collectAsStateWithLifecycle()
    val unlocking = rememberUnlocking()
    val hiding = LocalAppSettings.current.hideLockedPrivateMaps
    val choices = remember(maps, privacy, hiding) { captureChoices(maps, privacy, hiding) }
    val ordered = choices.maps
    PrivateOnScreen(choices.namesPrivateMaps)
    fun lockedPrivate(id: String?): Boolean = maps.any { it.id == id && privacy.hides(it) }

    // Ideas never quietly go to another map than the one meant: while that's a private map that's locked, none is
    // chosen (`waitingFor` is it), until the person picks one or unlocks.
    var waitingFor by rememberSaveable { mutableStateOf(initialMapId?.takeIf(::lockedPrivate)) }
    var mapId by rememberSaveable { mutableStateOf(if (waitingFor != null) null else initialMapId?.takeIf { id -> ordered.any { it.id == id } } ?: ordered.firstOrNull()?.id) }
    // Ideas that couldn't be added stay here, with what went wrong, to try again or copy elsewhere.
    var problem by remember { mutableStateOf<String?>(null) }

    // Unlocked, private maps come in: the one ideas went to last, or were going to, when it's one of them.
    LaunchedEffect(ordered) {
        val chosen = mapId

        when {
            // A private map chosen that locks goes, and so does what went wrong adding to it, which names it.
            chosen != null && ordered.none { it.id == chosen } && lockedPrivate(chosen) -> {
                problem = null
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

    // Shared text arrives with the cursor after it, ready for a line more.
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var adding by remember { mutableStateOf(false) }
    val ideas = ideasFromText(text.text)
    val map = ordered.firstOrNull { it.id == mapId }
    val focus = remember { FocusRequester() }
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    fun add() {
        val target = map ?: return

        if (ideas.isEmpty() || adding) {
            return
        }

        adding = true
        problem = null

        scope.launch {
            try {
                context.container.capture.add(target.id, text.text)
            } catch (failed: Exception) {
                adding = false
                problem = captureProblem(resources, target.title, failed)
                return@launch
            }

            val message = if (ideas.size == 1) {
                resources.getString(R.string.capture_added_idea, target.title)
            } else {
                resources.getQuantityString(R.plurals.capture_added_ideas, ideas.size, ideas.size, target.title)
            }

            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state) {
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
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
            // The sheet has a window of its own, focused once it's up (from Quick Settings, once the panel
            // folds away) and again after a map is chosen: the keyboard comes up then, ready for the idea.
            val window = LocalWindowInfo.current
            val keyboard = LocalSoftwareKeyboardController.current

            LaunchedEffect(window.isWindowFocused) {
                if (window.isWindowFocused && !adding) {
                    focus.requestFocus()
                    keyboard?.show()
                }
            }

            // Screen readers hear what went wrong as it shows: the ideas are still here.
            problem?.let { reason ->
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
                    enabled = ideas.isNotEmpty() && map != null && !adding,
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

/** What the sheet says when ideas didn't join the map called `title`: why, when that's known, and that they're still here. */
private fun captureProblem(resources: Resources, title: String, failure: Exception): String {
    if (failure is CaptureException) {
        val words = when (failure.failure) {
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
