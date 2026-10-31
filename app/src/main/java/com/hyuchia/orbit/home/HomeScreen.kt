package com.hyuchia.orbit.home

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.MapFileTypes
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.SortOrder
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.data.forgetPrivateMapsLeftOutOfRestore
import com.hyuchia.orbit.page.SystemWebView
import com.hyuchia.orbit.privacy.CoverDialog
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.Unlocked
import com.hyuchia.orbit.privacy.WhenPrivateMapsLock
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.coverTitle
import com.hyuchia.orbit.privacy.lockedLook
import com.hyuchia.orbit.privacy.rememberMakePrivate
import com.hyuchia.orbit.privacy.rememberUnlocking
import com.hyuchia.orbit.settings.noteEntries
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.KeyboardShortcuts
import com.hyuchia.orbit.ui.Keys
import com.hyuchia.orbit.ui.OldWebViewNotice
import com.hyuchia.orbit.ui.OrbitGlyph
import com.hyuchia.orbit.ui.OrbitSnackbarHost
import com.hyuchia.orbit.ui.Shortcut
import com.hyuchia.orbit.ui.ShortcutGroup
import com.hyuchia.orbit.ui.TagsSheet
import com.hyuchia.orbit.ui.TitleDialog
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.isShort
import com.hyuchia.orbit.ui.orbitViewModel
import com.hyuchia.orbit.ui.shareMapExport
import com.hyuchia.orbit.ui.withdrawShortcut
import com.hyuchia.orbit.widget.canPinMapWidget
import com.hyuchia.orbit.widget.pinMapWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val BarSearchWidth = 360.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onPresent: (String) -> Unit,
    onStudy: (String) -> Unit,
    onNewMap: () -> Unit,
    onImported: (ImportResult) -> Unit,
    onSettings: () -> Unit,
    onTrash: () -> Unit,
    onSend: (MapSummary) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.container
    val model = orbitViewModel { HomeViewModel(it) }
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val folder by container.folderSync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    // A map deleted from its own screen is offered back here, once the list shows: a private one named only while they're
    // unlocked, and not mentioned while they hide (it waits in the trash).
    val hiding by rememberUpdatedState(settings.hideLockedPrivateMaps)

    LaunchedEffect(container) {
        for (item in container.deletedMaps) {
            snackbars.currentSnackbarData?.dismiss()
            val privacy = container.privateMaps.view.value
            val hidden = item.isPrivate && privacy.hides(item.id)

            if (hidden && privacy.conceals(hiding)) {
                continue
            }

            // Hidden, it's named by its cover, if it has one.
            val named = when {
                !hidden -> item.title
                item.cover != null -> resources.coverTitle(item.cover)
                else -> null
            }

            val result = snackbars.showSnackbar(
                named?.let { resources.getString(R.string.home_deleted_map, it) } ?: resources.getString(R.string.private_deleted_map),
                actionLabel = resources.getString(R.string.common_undo),
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed) {
                model.restore(item)
            }
        }
    }

    // A map that couldn't be made or duplicated says why.
    LaunchedEffect(model) {
        model.problems.collect { problem ->
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(problem)
        }
    }

    var naming by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MapSummary?>(null) }
    var tagging by remember { mutableStateOf<MapSummary?>(null) }
    var managingTags by rememberSaveable { mutableStateOf(false) }
    // A map with pictures, videos or audio, and what goes with it, while its sheet asks how to share it.
    var sharing by remember { mutableStateOf<SharingMap?>(null) }
    // A private map whose cover is being chosen.
    var covering by remember { mutableStateOf<MapSummary?>(null) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }

    // When private maps lock, what named one here goes with them: a message, and the map being renamed or tagged.
    WhenPrivateMapsLock {
        snackbars.currentSnackbarData?.dismiss()
        renaming = renaming?.takeUnless { it.metadata.isPrivate }
        tagging = tagging?.takeUnless { it.metadata.isPrivate }
        sharing = sharing?.takeUnless { it.map.metadata.isPrivate }
        covering = null
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { onImported(container.importer.importFile(uri, context.contentResolver)) }
        }
    }

    val widgetsPinnable = remember { canPinMapWidget(context) }
    val unlocking = rememberUnlocking()

    fun say(message: String) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }

    val makePrivate = rememberMakePrivate(::say)

    /** Asks for the phone's screen lock: every private map opens, its card giving way to the map's own. */
    fun unlock() {
        unlocking.ask { result ->
            if (result is Unlocked.Failed && result.reason.isNotBlank()) {
                say(result.reason)
            }
        }
    }

    suspend fun share(map: MapSummary, withFiles: Boolean, asTheyAre: Set<String> = emptySet()) {
        try {
            shareMap(context, container, map, withFiles, asTheyAre)
        } catch (failed: IOException) {
            Logs.warn("OrbitHome", "Couldn't make a file of a map to share", failed)
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(resources.getString(R.string.home_could_not_share_map))
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    /** Home gives way to a map: the search's keyboard goes first, rather than staying up over the map's first frames. */
    fun leave(open: () -> Unit) {
        focusManager.clearFocus()
        keyboard?.hide()
        open()
    }

    fun actions(map: MapSummary) = MapCardActions(
        onOpen = { leave { onOpen(map.id) } },
        onPresent = { leave { onPresent(map.id) } },
        onStudy = { leave { onStudy(map.id) } },
        onPin = { model.setPinned(map.id, !map.metadata.pinned) },
        onRename = { renaming = map },
        onTags = { tagging = map },
        onDuplicate = { model.duplicate(map.id) },
        onShare = {
            scope.launch {
                val media = container.maps.mediaSummary(map.id)
                val uncleaned = container.maps.uncleaned(map.id)
                val clean = container.maps.mediaSummary(map.id, leavingOut = uncleaned.names)

                // A private map that locked meanwhile isn't named, or counted, after all.
                if (container.privateMaps.hides(map)) {
                    return@launch
                }

                // Just text goes straight to the share sheet; a map with files of its own first says they go too.
                if (media.isEmpty) {
                    share(map, withFiles = false)
                } else {
                    sharing = SharingMap(map, media, clean, uncleaned)
                }
            }
        },
        onSend = { onSend(map) },
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
        onPrivate = { private -> makePrivate(map, private) },
        onCover = { covering = map },
        onLockNow = model::lockPrivateMaps,
    )

    val privacy by container.privateMaps.view.collectAsStateWithLifecycle()
    // Every map, private ones that hide included, for the folder's cards to name them, or count them while they're locked.
    val allMaps by container.maps.maps.collectAsStateWithLifecycle()
    // While private maps hide, the folder's cards don't mention them at all.
    val concealing = privacy.conceals(settings.hideLockedPrivateMaps)

    fun quoted(title: String): String = resources.getString(R.string.settings_quoted_map_title, title)

    // A list's last entry after the others: “Trip”, “Plan” and “Notes”.
    fun listed(before: String, end: String): String = resources.getString(R.string.home_list_last, before, end)

    /**
     * How a card comes and goes in the grid. Private maps that hide while locked fade away as they lock
     * and back as they're unlocked; locked while Orbit was away, they're gone before it shows again.
     */
    @Composable
    fun LazyGridItemScope.moving(map: MapSummary): Modifier =
        Modifier.animateItem(fadeOutSpec = if (map.metadata.isPrivate && privacy.lockedAway) null else spring(stiffness = Spring.StiffnessMediumLow))

    /**
     * A map's card, or its locked card while it's private and private maps are locked. Asked of the lock
     * itself, which changes at once, rather than of Home's state, which follows a moment later: a card
     * locks the moment the maps do, and one that hides then fades away locked.
     */
    @Composable
    fun HomeCard(map: MapSummary, modifier: Modifier = Modifier) {
        val locked = privacy.hides(map)

        PrivateOrOpenCard(
            locked = locked,
            modifier = modifier,
            lockedCard = { LockedMapCard(map.metadata.pinned, onUnlock = ::unlock, open = !privacy.hides(map), cover = map.metadata.cover, look = lockedLook(map.id)) },
            card = { MapCard(map, actions(map)) },
        )
    }

    BackHandler(enabled = fabOpen) { fabOpen = false }
    BackHandler(enabled = query.isNotEmpty() && !fabOpen) { model.search("") }

    val mapCount = state.pinned.size + state.others.size
    // A private map's card, open, keeps Orbit's picture out of Recents.
    PrivateOnScreen((state.pinned + state.others).any { it.metadata.isPrivate && it.id !in state.locked } || state.results.orEmpty().any { it.map.metadata.isPrivate })
    val scrim by animateFloatAsState(if (fabOpen) 1f else 0f, label = "scrim")
    val grid = rememberLazyGridState()
    // Private maps that were hidden come back where they belong, the pinned ones and the latest above the rest:
    // a list at its top stays there to show them, rather than keeping the card that was first.
    val wasHiding = remember { booleanArrayOf(false) }
    val hidingNow = state.canShowPrivate

    SideEffect {
        if (wasHiding[0] && !hidingNow && grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0) {
            grid.requestScrollToItem(0)
        }

        wasHiding[0] = hidingNow
    }

    val searchFocus = remember { FocusRequester() }
    // Map files and text dragged here from other apps come in as maps (see HomeDrops).
    val drops = rememberHomeDrops(onImported)

    BoxWithConstraints(Modifier.fillMaxSize().homeDropTarget(drops)) {
    // A phone on its side: the home's title and search share one slim bar, so the maps get the room.
    val short = isShort(maxHeight)
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
                    Shortcut(stringResource(R.string.common_open_a_file), listOf(Keys(KeyEvent.KEYCODE_O, ctrl = true)), anywhere = true) { importer.launch(MapFileTypes.FILE_PICKER.toTypedArray()) },
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

    // While the + menu is open, screen readers have only it, as the screen behind it dims.
    val behindTheMenu = if (fabOpen) Modifier.clearAndSetSemantics {} else Modifier

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BarWithNewMapMenu(
                menu = {
                    // While the + menu is open, the screen behind it dims, and a tap there closes it. It's no button to
                    // screen readers or the keyboard, which close the menu with the + itself, Back or Escape.
                    if (scrim > 0f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = scrim }
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                                .pointerInput(Unit) { detectTapGestures { fabOpen = false } },
                        )
                    }

                    NewMapMenu(
                        open = fabOpen,
                        onOpenChange = { fabOpen = it },
                        onBlank = { naming = true },
                        onTemplates = onNewMap,
                        onImport = { importer.launch(MapFileTypes.FILE_PICKER.toTypedArray()) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            // Clear of the navigation bar, and of a cutout at that side on a phone on its side.
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
                            .padding(bottom = 8.dp),
                    )
                },
            ) {
                if (short) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        subtitle = { Text(count) },
                        actions = {
                            SearchField(query, model::search, searchFocus, Modifier.width(BarSearchWidth))

                            PrivateMapsButton(state.canLock, state.canShowPrivate, onLock = model::lockPrivateMaps, onShow = ::unlock)

                            Hint(stringResource(R.string.common_settings), SettingsKeys) {
                                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.common_settings)) }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        modifier = behindTheMenu,
                    )
                } else {
                    LargeFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        subtitle = { Text(count) },
                        actions = {
                            PrivateMapsButton(state.canLock, state.canShowPrivate, onLock = model::lockPrivateMaps, onShow = ::unlock)

                            Hint(stringResource(R.string.common_settings), SettingsKeys) {
                                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.common_settings)) }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        modifier = behindTheMenu,
                    )
                }
            }
        },
        snackbarHost = { OrbitSnackbarHost(snackbars, Modifier.padding(bottom = 88.dp)) },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        // Reading the phone's WebView starts it: off the main thread, the first time.
        val webView by produceState<SystemWebView?>(null) { value = withContext(Dispatchers.Default) { SystemWebView.of(context) } }
        var crash by rememberUnseenCrash()
        val release = rememberAnnouncedRelease()
        var privateLeftOut by rememberPrivateMapsLeftOutOfRestore()

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
            modifier = Modifier
                .fillMaxSize()
                .then(behindTheMenu),
        ) {
            if (!short) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    SearchField(query, model::search, searchFocus, Modifier.fillMaxWidth())
                }
            }

            if (state.tags.isNotEmpty()) {
                item(key = "tags", span = { GridItemSpan(maxLineSpan) }) {
                    TagFilter(state.tags, state.tag, onShow = model::showTag, onEdit = { managingTags = true })
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

            // A problem with the folder that has lasted more than one sync: Settings says it too, but this is where it's seen.
            folder.problem?.takeIf { folder.failures >= LASTING_FOLDER_FAILURES }?.let { problem ->
                item(key = "folder-problem", span = { GridItemSpan(maxLineSpan) }) {
                    FolderProblemCard(problem, onTryAgain = container.folderSync::request, onSettings = onSettings, modifier = Modifier.animateItem())
                }
            }

            // After a restore from a backup that left private maps out: how they come back, said once.
            if (privateLeftOut > 0) {
                item(key = "private-restore", span = { GridItemSpan(maxLineSpan) }) {
                    PrivateRestoreCard(
                        privateLeftOut,
                        withFolder = settings.syncFolder != null,
                        onDismiss = {
                            privateLeftOut = 0
                            container.applicationScope.launch(Dispatchers.IO) { forgetPrivateMapsLeftOutOfRestore(context.filesDir) }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Many maps whose files left the folder at once: they wait for a yes before following them into the trash. While
            // private maps hide, the card leaves them out, and they wait until they're unlocked.
            val held = allMaps.orEmpty().filter { map -> map.id in folder.held && !(privacy.hides(map) && concealing) }

            if (held.isNotEmpty()) {
                item(key = "folder-held", span = { GridItemSpan(maxLineSpan) }) {
                    val named = noteEntries(held, privacy::hides, { quoted(it.title) }, last = ::listed) { count ->
                        resources.getQuantityString(R.plurals.private_maps_in_a_note, count, count)
                    }

                    FolderRemovalsCard(
                        named,
                        held.size,
                        folder.name ?: stringResource(R.string.settings_your_folder),
                        onKeep = { container.folderSync.keepHeld(held.map { it.id }) },
                        onRemove = { container.folderSync.removeHeld(held.map { it.id }) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Maps the folder sent to the trash: they stay there until this is read.
            fun hiddenRemoved(title: String): Boolean = privacy.locked && title in folder.notes.private
            val removed = folder.notes.removed.filterNot { hiddenRemoved(it) && concealing }

            if (removed.isNotEmpty()) {
                item(key = "folder-removed", span = { GridItemSpan(maxLineSpan) }) {
                    val named = noteEntries(removed, ::hiddenRemoved, ::quoted, last = ::listed) { count ->
                        resources.getQuantityString(R.plurals.private_maps_in_a_note, count, count)
                    }

                    FolderRemovedCard(
                        named,
                        removed.size,
                        folder.name ?: stringResource(R.string.settings_your_folder),
                        onOpenTrash = onTrash,
                        onDismiss = container.folderSync::dismissNotes,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Another device in the folder whose version matters here, once for each new version it runs.
            folder.notices.firstOrNull { it.matters && it.key !in settings.folderNoticesDismissed }?.let { notice ->
                item(key = "folder-notice", span = { GridItemSpan(maxLineSpan) }) {
                    FolderNoticeCard(
                        notice,
                        onDismiss = {
                            // Only the devices still in the folder keep their put-away notices, so the list never grows past them.
                            val devices = folder.devices.map { it.id }

                            scope.launch {
                                container.settings.update { current ->
                                    val kept = current.folderNoticesDismissed.filter { key -> devices.any { key.contains(":$it:") } }
                                    current.copy(folderNoticesDismissed = kept.toSet() + notice.key)
                                }
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            val results = state.results

            // Where the maps are, once: until a folder keeps them in step, or the person says they've read it. It
            // comes after the pinned maps, so a first Home leads with the welcome map, and the + rests clear of it.
            val storage = !settings.storageNoteDismissed && settings.syncFolder == null && !state.loading && results == null

            fun LazyGridScope.storageNote() {
                item(key = "storage", span = { GridItemSpan(maxLineSpan) }) {
                    StorageCard(
                        onDismiss = { scope.launch { container.settings.update { it.copy(storageNoteDismissed = true) } } },
                        onProblem = { problem ->
                            scope.launch {
                                snackbars.currentSnackbarData?.dismiss()
                                snackbars.showSnackbar(problem)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            when {
                state.loading -> items(4, key = { "placeholder-$it" }) { MapCardPlaceholder() }
                results != null -> {
                    item(key = "search-found", span = { GridItemSpan(maxLineSpan) }) { SearchFound(results.size, state.searched) }

                    items(results, key = { it.map.id }) { hit ->
                        MapCard(hit.map, actions(hit.map), snippet = hit.snippet, modifier = Modifier.animateItem())
                    }
                }
                state.isEmpty -> {
                    if (storage) {
                        storageNote()
                    }

                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHome(onBlank = { naming = true }, onTemplates = onNewMap)
                    }
                }
                else -> {
                    if (state.pinned.isNotEmpty()) {
                        header("pinned-header") { SectionHeader(stringResource(R.string.home_pinned_maps)) }
                        items(state.pinned, key = { it.id }) { map -> HomeCard(map, moving(map)) }
                    }

                    if (storage) {
                        storageNote()
                    }

                    if (state.others.isNotEmpty()) {
                        header("all-header") {
                            SectionHeader(stringResource(if (state.pinned.isEmpty()) R.string.common_your_maps else R.string.home_everything_else)) {
                                SortMenu(state.sortOrder, model::sort)
                            }
                        }

                        items(state.others, key = { it.id }) { map -> HomeCard(map, moving(map)) }
                    }
                }
            }
        }
    }

        HomeDropZone(drops, Modifier.fillMaxSize().safeDrawingPadding())
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

    sharing?.let { shared ->
        ShareMapSheet(
            shared.map.title,
            shared.media,
            shared.clean,
            shared.uncleaned,
            onShare = { withFiles, asTheyAre ->
                sharing = null
                scope.launch { share(shared.map, withFiles, asTheyAre) }
            },
            onDismiss = { sharing = null },
        )
    }

    tagging?.let { map ->
        TagsSheet(
            title = map.title,
            tags = map.outline.tags,
            known = state.tags.map { it.name },
            onChange = { tags -> model.setTags(map.id, tags) },
            onDismiss = { tagging = null },
        )
    }

    // Removing the last tag leaves nothing to edit.
    LaunchedEffect(state.tags.isEmpty()) {
        if (state.tags.isEmpty() && !state.loading) {
            managingTags = false
        }
    }

    if (managingTags) {
        ManageTagsSheet(state.tags, onRename = model::renameTag, onRemove = model::removeTag, onDismiss = { managingTags = false })
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

    covering?.let { map ->
        CoverDialog(map, onDismiss = { covering = null })
    }
}

private fun LazyGridScope.header(key: String, content: @Composable () -> Unit) {
    // Comes and goes with the maps under it: the pinned ones' heading, as hidden private maps lock, say.
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.animateItem()) { content() } }
}

/** The +'s width and a gap: the maps scroll under the +, at the bottom right, so Home's headings stop short of it. */
private val ClearOfThePlus = 56.dp + 8.dp

/**
 * A section's title, and right after it what goes with it (how its maps are
 * sorted), rather than at the far end, where the + would cover it at times.
 * Large text puts it under the title.
 */
@Composable
private fun SectionHeader(title: String, beside: @Composable () -> Unit = {}) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, end = ClearOfThePlus),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        beside()
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
                val clear = stringResource(R.string.home_clear_the_search)

                Hint(clear) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = clear) }
                }
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

    // The button says it sorts as well as how: "Sort: Last edited", without the label again after it.
    val sorted = stringResource(R.string.home_sorted_by, labels.getValue(current))

    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = sorted }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(labels.getValue(current), modifier = Modifier.clearAndSetSemantics {})
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEach { (order, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (order == current) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        open = false
                        onSort(order)
                    },
                    modifier = Modifier.semantics { selected = order == current },
                )
            }
        }
    }
}

/**
 * Home's bar, with `menu`, the + and the dimming behind its menu, over the
 * whole screen under it. Tab goes in the order things are placed, so placed
 * with the bar, the + comes right after the bar's buttons and before the
 * maps, though it's drawn at the bottom. The scaffold keeps only the bar's
 * height for itself.
 */
@Composable
private fun BarWithNewMapMenu(menu: @Composable BoxScope.() -> Unit, bar: @Composable () -> Unit) {
    Layout(contents = listOf(bar, { Box(Modifier.fillMaxSize(), content = menu) })) { (bars, menus), constraints ->
        val placedBars = bars.map { it.measure(constraints) }
        val placedMenus = menus.map { it.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight)) }

        layout(constraints.maxWidth, placedBars.maxOfOrNull { it.height } ?: 0) {
            placedBars.forEach { it.place(0, 0) }
            placedMenus.forEach { it.place(0, 0) }
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
    val iconColor = ToggleFloatingActionButtonDefaults.iconColor()
    val iconSize = remember { ToggleFloatingActionButtonDefaults.iconSize() }

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
                // Material's animateIcon draws the icon into a layer once, and turning the screen can empty that layer
                // without the icon drawing again, leaving the + blank. Its size and color follow the toggle here instead.
                val progress = checkedProgress
                Icon(if (progress > 0.5f) Icons.Filled.Close else Icons.Filled.Add, contentDescription = null, tint = iconColor(progress), modifier = Modifier.size(iconSize(progress)))
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

        // A row that wraps: at large text, or in Spanish, the two don't fit side by side.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onTemplates) { Text(stringResource(R.string.home_templates)) }
            FilledTonalButton(onClick = onBlank) { Text(stringResource(R.string.common_blank_map)) }
        }
    }
}

/**
 * What a search found, above the maps it found: how many match, as the
 * pinned maps' heading says what's under it, or that none do and where it
 * looks. Screen readers hear it as it changes, without leaving the field.
 */
@Composable
private fun SearchFound(count: Int, searched: String) {
    val none = count == 0
    val found = if (none) stringResource(R.string.home_no_maps_match, searched) else pluralStringResource(R.plurals.home_maps_match, count, count)
    val said = if (none) stringResource(R.string.home_no_maps_match_said) else found

    Column(
        horizontalAlignment = if (none) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (none) PaddingValues(32.dp) else PaddingValues(start = 4.dp, top = 12.dp)),
    ) {
        Text(
            found,
            style = MaterialTheme.typography.titleMedium,
            color = if (none) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            textAlign = if (none) TextAlign.Center else TextAlign.Start,
            // Said without what was typed, which the field says itself, so a search that goes on finding nothing is heard
            // once: the words on screen change with every key, but not what screen readers hear, nor the line's size.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = said
            },
        )

        if (none) {
            Text(
                stringResource(R.string.home_search_looks_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Ctrl+, opens the settings, as in most apps. */
private val SettingsKeys = Keys(KeyEvent.KEYCODE_COMMA, ctrl = true, character = ',')

/** A map whose sheet asks how to share it: all its own files (`media`), those that go clean, and those that can't. */
private class SharingMap(val map: MapSummary, val media: MediaSummary, val clean: MediaSummary, val uncleaned: Uncleaned)

/**
 * Shares a map as a zip with its own pictures, videos and audio (see
 * [MapRepository.writeZip]), with those that can't go clean in `asTheyAre`
 * as they are, or as its markdown file alone. A private map that locked
 * while its zip was written, which takes seconds with its videos, opens
 * nothing ([shareMapExport]).
 */
suspend fun shareMap(context: Context, container: AppContainer, map: MapSummary, withFiles: Boolean, asTheyAre: Set<String> = emptySet()) {
    if (withFiles) {
        shareMapExport(context, map.id, exportName(context, map.title, "zip"), "application/zip", map.title) { container.maps.writeZip(map.id, it, asTheyAre) }
    } else {
        val markdown = container.maps.read(map.id)
        shareMapExport(context, map.id, exportName(context, map.title, "md"), "text/markdown", map.title) { it.write(markdown.toByteArray()) }
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
