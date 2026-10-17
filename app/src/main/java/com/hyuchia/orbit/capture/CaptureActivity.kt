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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.ui.EscapeCloses
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
 * "Add to an Orbit map" in another app's share sheet. Each line is an idea.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                OrbitTheme(dark = appIsDark()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSheet(maps: List<MapSummary>, initialMapId: String?, initialText: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val ordered = remember(maps) { captureOrder(maps) }
    var mapId by rememberSaveable { mutableStateOf(initialMapId?.takeIf { id -> maps.any { it.id == id } } ?: ordered.firstOrNull()?.id) }
    // Shared text arrives with the cursor after it, ready for a line more.
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var adding by remember { mutableStateOf(false) }
    // Ideas that couldn't be added stay here, with what went wrong, to try again or copy elsewhere.
    var problem by remember { mutableStateOf<String?>(null) }
    val ideas = ideasFromText(text.text)
    val map = maps.firstOrNull { it.id == mapId }
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
                context.container.capture.add(target.id, ideas)
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
            Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleLarge)

            if (ordered.isEmpty()) {
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

            MapChooser(ordered, map, onChoose = { mapId = it.id })
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
                    .focusRequester(focus),
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

            problem?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))

                Button(onClick = ::add, enabled = ideas.isNotEmpty() && map != null && !adding) {
                    if (adding) {
                        LoadingIndicator(Modifier.size(20.dp))
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
        }

        return resources.getString(words, title)
    }

    val reason = failure.message ?: return resources.getString(R.string.capture_problem, title)
    return resources.getString(R.string.capture_problem_with_reason, title, reason.trimEnd('.'))
}

/** Which map the ideas go to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapChooser(maps: List<MapSummary>, chosen: MapSummary?, onChoose: (MapSummary) -> Unit) {
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
                    onClick = {
                        onChoose(map)
                        open = false
                    },
                )
            }
        }
    }
}
