package com.hyuchia.orbit.home

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.SortOrder
import com.hyuchia.orbit.nearby.SendSheet
import com.hyuchia.orbit.page.SystemWebView
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.KeyboardShortcuts
import com.hyuchia.orbit.ui.Keys
import com.hyuchia.orbit.ui.OldWebViewNotice
import com.hyuchia.orbit.ui.OrbitGlyph
import com.hyuchia.orbit.ui.Shortcut
import com.hyuchia.orbit.ui.ShortcutGroup
import com.hyuchia.orbit.ui.exportFile
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.orbitViewModel
import com.hyuchia.orbit.ui.shareFile
import com.hyuchia.orbit.ui.withdrawShortcut
import com.hyuchia.orbit.widget.canPinMapWidget
import com.hyuchia.orbit.widget.pinMapWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shorter than this, as a phone on its side is, the home's title and search share one slim bar, so the maps get the room. */
private val ShortScreen = 480.dp
private val BarSearchWidth = 360.dp

/** What the file picker offers when importing: maps as markdown, YAML or a zip with their pictures. */
val ImportTypes = arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/yaml", "text/yaml", "application/x-yaml", "application/zip", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onPresent: (String) -> Unit,
    onNewMap: () -> Unit,
    onImported: (ImportResult) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.container
    val model = orbitViewModel { HomeViewModel(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    // A map deleted from its own screen is offered back here, once the list shows.
    LaunchedEffect(container) {
        for (item in container.deletedMaps) {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(
                resources.getString(R.string.home_deleted_map, item.title),
                actionLabel = resources.getString(R.string.common_undo),
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed) {
                model.restore(item)
            }
        }
    }

    var naming by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MapSummary?>(null) }
    var sending by remember { mutableStateOf<MapSummary?>(null) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { onImported(container.importer.importFile(uri, context.contentResolver)) }
        }
    }

    val widgetsPinnable = remember { canPinMapWidget(context) }

    fun actions(map: MapSummary) = MapCardActions(
        onOpen = { onOpen(map.id) },
        onPresent = { onPresent(map.id) },
        onPin = { model.setPinned(map.id, !map.metadata.pinned) },
        onRename = { renaming = map },
        onDuplicate = { model.duplicate(map.id) },
        onShare = { scope.launch { shareMap(context, container, map) } },
        onSend = { sending = map },
        onDelete = {
            scope.launch {
                val deleted = model.delete(map.id) ?: return@launch
                withdrawShortcut(context, map.id)
                snackbars.currentSnackbarData?.dismiss()
                val result = snackbars.showSnackbar(
                    resources.getString(R.string.home_deleted_map, map.title),
                    actionLabel = resources.getString(R.string.common_undo),
                    duration = SnackbarDuration.Long,
                )

                if (result == SnackbarResult.ActionPerformed) {
                    model.restore(deleted)
                }
            }
        },
        onAddToHomeScreen = if (widgetsPinnable) {
            {
                scope.launch {
                    if (!pinMapWidget(context, map)) {
                        snackbars.showSnackbar(resources.getString(R.string.home_add_widget_from_list))
                    }
                }
            }
        } else {
            null
        },
    )

    BackHandler(enabled = fabOpen) { fabOpen = false }
    BackHandler(enabled = query.isNotEmpty() && !fabOpen) { model.search("") }

    val mapCount = state.pinned.size + state.others.size
    val scrim by animateFloatAsState(if (fabOpen) 1f else 0f, label = "scrim")
    val grid = rememberLazyGridState()
    val searchFocus = remember { FocusRequester() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val short = maxHeight < ShortScreen
    KeyboardShortcuts(
        listOf(
            ShortcutGroup(
                stringResource(R.string.common_your_maps),
                listOf(
                    Shortcut(stringResource(R.string.common_new_map), listOf(Keys(KeyEvent.KEYCODE_N, ctrl = true)), anywhere = true) { naming = true },
                    Shortcut(
                        stringResource(R.string.home_shortcut_new_map_from_template),
                        listOf(Keys(KeyEvent.KEYCODE_N, ctrl = true, shift = true)),
                        anywhere = true,
                        run = onNewMap,
                    ),
                    Shortcut(stringResource(R.string.common_open_a_file), listOf(Keys(KeyEvent.KEYCODE_O, ctrl = true)), anywhere = true) { importer.launch(ImportTypes) },
                    Shortcut(stringResource(R.string.common_search_your_maps), listOf(Keys(KeyEvent.KEYCODE_F, ctrl = true)), anywhere = true) {
                        scope.launch {
                            // Above the maps on a tall screen: scrolled back into view first.
                            if (!short) {
                                grid.scrollToItem(0)
                                withFrameNanos {}
                            }

                            searchFocus.requestFocus()
                        }
                    },
                    Shortcut(stringResource(R.string.common_settings), listOf(SettingsKeys), anywhere = true, run = onSettings),
                ),
            ),
            ShortcutGroup(
                stringResource(R.string.home_shortcut_group_map_card),
                listOf(
                    Shortcut(stringResource(R.string.home_shortcut_open_it), listOf(Keys(KeyEvent.KEYCODE_ENTER))),
                    Shortcut(stringResource(R.string.home_shortcut_its_menu), listOf(Keys(KeyEvent.KEYCODE_MENU), Keys(KeyEvent.KEYCODE_F10, shift = true))),
                    Shortcut(stringResource(R.string.home_shortcut_delete_it), listOf(Keys(KeyEvent.KEYCODE_FORWARD_DEL))),
                ),
            ),
        ),
    )
    val scrollBehavior = if (short) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val count = if (state.loading) "" else pluralStringResource(R.plurals.home_map_count, mapCount, mapCount)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (short) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    subtitle = { Text(count) },
                    actions = {
                        SearchField(query, model::search, searchFocus, Modifier.width(BarSearchWidth))

                        Hint(stringResource(R.string.common_settings), SettingsKeys) {
                            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.common_settings)) }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    subtitle = { Text(count) },
                    actions = {
                        Hint(stringResource(R.string.common_settings), SettingsKeys) {
                            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.common_settings)) }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbars, Modifier.padding(bottom = 88.dp)) },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        // Reading the phone's WebView starts it: off the main thread, the first time.
        val webView by produceState<SystemWebView?>(null) { value = withContext(Dispatchers.Default) { SystemWebView.of(context) } }
        var crash by rememberUnseenCrash()
        val release = rememberAnnouncedRelease()

        LazyVerticalGrid(
            state = grid,
            // One wide column on phones, so the maps show big; more beside each other on tablets,
            // and on a phone on its side, where they're smaller so a whole row fits on the screen.
            columns = GridCells.Adaptive(minSize = if (short) 240.dp else 300.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                bottom = padding.calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!short) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    SearchField(query, model::search, searchFocus, Modifier.fillMaxWidth())
                }
            }

            webView?.takeIf { !it.drawsMaps }?.let { old ->
                item(key = "old-webview", span = { GridItemSpan(maxLineSpan) }) { OldWebViewNotice(old) }
            }

            crash?.let { report ->
                item(key = "crash", span = { GridItemSpan(maxLineSpan) }) { CrashCard(report, onDone = { crash = null }, Modifier.animateItem()) }
            }

            release?.let { new ->
                item(key = "update", span = { GridItemSpan(maxLineSpan) }) { UpdateCard(new, Modifier.animateItem()) }
            }

            val results = state.results

            when {
                state.loading -> items(4, key = { "placeholder-$it" }) { MapCardPlaceholder() }
                results != null && results.isEmpty() -> item(key = "no-results", span = { GridItemSpan(maxLineSpan) }) {
                    Message(stringResource(R.string.home_no_maps_match, query), stringResource(R.string.home_search_looks_in))
                }
                results != null -> items(results, key = { it.map.id }) { hit ->
                    MapCard(hit.map, actions(hit.map), snippet = hit.snippet, modifier = Modifier.animateItem())
                }
                state.isEmpty -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHome(onBlank = { naming = true }, onTemplates = onNewMap)
                }
                else -> {
                    if (state.pinned.isNotEmpty()) {
                        header("pinned-header") { SectionHeader(stringResource(R.string.home_pinned_maps)) }
                        items(state.pinned, key = { it.id }) { map -> MapCard(map, actions(map), Modifier.animateItem()) }
                    }

                    if (state.others.isNotEmpty()) {
                        header("all-header") {
                            SectionHeader(stringResource(if (state.pinned.isEmpty()) R.string.common_your_maps else R.string.home_everything_else)) {
                                SortMenu(state.sortOrder, model::sort)
                            }
                        }

                        items(state.others, key = { it.id }) { map -> MapCard(map, actions(map), Modifier.animateItem()) }
                    }
                }
            }
        }
    }

        // While the + menu is open, the screen behind it dims, and a tap there closes it.
        if (scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.home_close_the_menu)) { fabOpen = false },
            )
        }

        NewMapMenu(
            open = fabOpen,
            onOpenChange = { fabOpen = it },
            onBlank = { naming = true },
            onTemplates = onNewMap,
            onImport = { importer.launch(ImportTypes) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
    }

    if (naming) {
        TitleDialog(
            heading = stringResource(R.string.common_new_map),
            confirm = stringResource(R.string.home_create),
            initial = "",
            onConfirm = { title ->
                naming = false
                model.create(title, onOpen)
            },
            onDismiss = { naming = false },
        )
    }

    sending?.let { map ->
        SendSheet(map.id, map.title, onDismiss = { sending = null })
    }

    renaming?.let { map ->
        TitleDialog(
            heading = stringResource(R.string.home_rename_map),
            confirm = stringResource(R.string.common_rename),
            initial = map.title,
            onConfirm = { title ->
                renaming = null
                model.rename(map.id, title)
            },
            onDismiss = { renaming = null },
        )
    }
}

private fun LazyGridScope.header(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val description = stringResource(R.string.common_search_your_maps)
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(description) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_clear_the_search)) }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun SortMenu(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labels = mapOf(
        SortOrder.Edited to stringResource(R.string.home_sort_last_edited),
        SortOrder.Opened to stringResource(R.string.home_sort_last_opened),
        SortOrder.Created to stringResource(R.string.home_sort_newest_first),
        SortOrder.Title to stringResource(R.string.home_sort_by_title),
    )

    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(labels.getValue(current))
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEach { (order, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (order == current) {
                            Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.home_sort_current))
                        }
                    },
                    onClick = {
                        open = false
                        onSort(order)
                    },
                )
            }
        }
    }
}

/** The + that opens into the ways to start a map. */
@Composable
private fun NewMapMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onBlank: () -> Unit,
    onTemplates: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.common_new_map)
    val menuState = stringResource(if (open) R.string.home_menu_open else R.string.home_menu_closed)

    FloatingActionButtonMenu(
        expanded = open,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = onOpenChange,
                modifier = Modifier.semantics {
                    contentDescription = description
                    stateDescription = menuState
                },
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
                Icon(rememberVectorPainter(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onOpenChange(false); onImport() },
            text = { Text(stringResource(R.string.common_open_a_file)) },
            icon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = { onOpenChange(false); onTemplates() },
            text = { Text(stringResource(R.string.home_from_a_template)) },
            icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = { onOpenChange(false); onBlank() },
            text = { Text(stringResource(R.string.common_blank_map)) },
            icon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null) },
        )
    }
}

@Composable
private fun EmptyHome(onBlank: () -> Unit, onTemplates: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
    ) {
        OrbitGlyph(branches = 5, modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onTemplates) { Text(stringResource(R.string.home_templates)) }
            FilledTonalButton(onClick = onBlank) { Text(stringResource(R.string.common_blank_map)) }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Ctrl+, opens the settings, as in most apps. */
private val SettingsKeys = Keys(KeyEvent.KEYCODE_COMMA, ctrl = true, character = ',')

/** Asks for a map's title. */
@Composable
fun TitleDialog(heading: String, confirm: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(heading)
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.home_what_is_it_about)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim())
                    }
                }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Shares a map as its markdown file, or as a zip when it has pictures of its own. */
suspend fun shareMap(context: Context, container: AppContainer, map: MapSummary) {
    if (container.maps.hasPictures(map.id)) {
        val uri = exportFile(context, exportName(context, map.title, "zip")) { container.maps.writeZip(map.id, it) }
        shareFile(context, uri, "application/zip", map.title)
    } else {
        val markdown = container.maps.read(map.id)
        val uri = exportFile(context, exportName(context, map.title, "md")) { it.write(markdown.toByteArray()) }
        shareFile(context, uri, "text/markdown", map.title)
    }
}

/** Beside the list on tablets, before a map is picked. */
@Composable
fun MapPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrbitGlyph(branches = 6, modifier = Modifier.size(160.dp))
            Text(stringResource(R.string.home_pick_a_map), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
