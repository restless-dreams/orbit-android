package com.hyuchia.orbit.map

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.MapStart
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.SoundBar
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.SystemWebView
import com.hyuchia.orbit.present.CastingButton
import com.hyuchia.orbit.privacy.CoverMenuItem
import com.hyuchia.orbit.privacy.KeyboardsDontLearn
import com.hyuchia.orbit.privacy.LocalMapLocked
import com.hyuchia.orbit.privacy.PrivateMapGate
import com.hyuchia.orbit.privacy.PrivateMenuItem
import com.hyuchia.orbit.privacy.WhenPrivateMapsLock
import com.hyuchia.orbit.privacy.rememberMakePrivate
import com.hyuchia.orbit.study.AnswersUnsaved
import com.hyuchia.orbit.study.StudyMode
import com.hyuchia.orbit.study.UnsavedAnswers
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.theme.mapStyle
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.KeyboardShortcuts
import com.hyuchia.orbit.ui.LocalShortcuts
import com.hyuchia.orbit.ui.OldWebViewNotice
import com.hyuchia.orbit.ui.OrbitSnackbarHost
import com.hyuchia.orbit.ui.ShortcutListKeys
import com.hyuchia.orbit.ui.ShortcutPriority
import com.hyuchia.orbit.ui.UnsavedNote
import com.hyuchia.orbit.ui.hasHardwareKeyboard
import com.hyuchia.orbit.ui.lastOf
import com.hyuchia.orbit.ui.offerShortcut
import com.hyuchia.orbit.ui.offeredCustomColors
import com.hyuchia.orbit.ui.openLink
import com.hyuchia.orbit.ui.orbitViewModel
import com.hyuchia.orbit.ui.showsMenuKeys
import com.hyuchia.orbit.ui.withdrawShortcut
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/** Between the small player for a sound and the toolbar under it, or the map beside it. */
private val SoundBarGap = 10.dp
/** On a short screen the small player stands beside the map, at most this wide: the map keeps its height. */
private val SoundBarBesideWidth = 340.dp
/** The toolbar floats this far above the bottom edge. */
private val ToolbarMargin = 16.dp
/** A floating toolbar with a + is this tall, or as a rail this wide, whatever it holds: room for the + at its largest. */
private val ToolbarSize = 80.dp
/** The breadcrumbs' row, which floats over the map's top edge: as tall as a chip's touch target. */
private val BreadcrumbsHeight = 48.dp
/** The map keeps this much room clear above the toolbar or a sheet. */
private val Clearance = 8.dp
/** Between the talk's panel and the toolbar under it, or the map above or beside it. */
private val TalkGap = 8.dp
/** The talk's panel keeps this far from the screen's edges. */
private val TalkMargin = 16.dp
/** On a short screen the talk's panel stands beside the map, at most this wide. */
private val TalkPanelBesideWidth = 340.dp
/** A sheet takes at most this share of the map's height, so one pulled all the way up doesn't shrink the map beneath it to a dot. */
private const val MostSheetRoom = 0.6f

/**
 * A map, with the screen around it in the map's own style. `onWantsRoom`
 * hears whether it wants the whole window, beside the list on a tablet: for
 * a panel beside the map, its text, a presentation, studying or a sketch. `present`
 * starts presenting the map from the top as soon as it's drawn, and `study`
 * studying it. `start` is a presentation or study session a map's card
 * asks of the map once it's open already: it starts from whatever the map
 * is doing, and `onStarted` hears it did. `onImported` shows the maps that
 * map files dropped on it bring, as "Open a file" would.
 */
@Composable
fun MapScreen(
    id: String,
    onClose: () -> Unit,
    onWantsRoom: (Boolean) -> Unit = {},
    besideList: Boolean = false,
    present: Boolean = false,
    study: Boolean = false,
    start: MapStart? = null,
    onStarted: () -> Unit = {},
    onImported: (ImportResult) -> Unit = {},
) {
    val summaries by LocalContext.current.container.maps.maps.collectAsStateWithLifecycle()
    val isPrivate = summaries?.firstOrNull { it.id == id }?.metadata?.isPrivate == true

    // Beside the list, Back closes the map once nothing on it is left to close, its lock too: first, so it goes last.
    val closeScale = remember { Animatable(1f) }
    BesideListBackHandler(besideList, closeScale, onClose)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = closeScale.value
                scaleY = closeScale.value
            },
    ) {
        // A private map stays behind its lock while private maps are locked, and what's typed into it stays out of
        // keyboards' learning.
        PrivateMapGate(id, onBack = onClose) {
            KeyboardsDontLearn(enabled = isPrivate) {
                UnlockedMapScreen(id, onClose, onWantsRoom, present, study, start, onStarted, onImported)
            }
        }
    }
}

@Composable
private fun UnlockedMapScreen(
    id: String,
    onClose: () -> Unit,
    onWantsRoom: (Boolean) -> Unit,
    present: Boolean,
    study: Boolean,
    start: MapStart?,
    onStarted: () -> Unit,
    onImported: (ImportResult) -> Unit,
) {
    val model = orbitViewModel(key = "map-$id") { MapViewModel(id, it.maps, it.settings, it) }
    val ui by model.ui.collectAsStateWithLifecycle()
    var presented by rememberSaveable { mutableStateOf(false) }
    var studied by rememberSaveable { mutableStateOf(false) }
    val presentSeconds = LocalAppSettings.current.presentationSeconds

    LaunchedEffect(model) {
        model.drops.openedMaps.collect { onImported(it) }
    }

    // Files, or text, shared to Orbit while this map is open join its idea in focus, with Undo, once the map's ready.
    val resolver = LocalContext.current.contentResolver
    val sharedFiles = LocalContext.current.container.sharedFiles

    LaunchedEffect(model) {
        model.takeShares(sharedFiles) { resolver }
    }

    LaunchedEffect(start, ui.ready) {
        if (start != null && ui.ready) {
            onStarted()
            model.startFromCard(start.study, presentSeconds)
        }
    }

    LaunchedEffect(present, ui.ready) {
        if (present && ui.ready && !presented) {
            presented = true
            model.startPresentation(presentSeconds, fromStart = true)
        }
    }

    LaunchedEffect(study, ui.ready) {
        if (study && ui.ready && !studied) {
            studied = true
            model.startStudy(fromTop = true)
        }
    }

    // A sketch takes the whole window too: a bigger canvas, and no list beside it whose maps would close it.
    val wantsRoom = ui.panel.showsBeside() || ui.source != null || ui.presentation != null || ui.study != null || ui.sketching
    LaunchedEffect(wantsRoom) { onWantsRoom(wantsRoom) }
    DisposableEffect(Unit) { onDispose { onWantsRoom(false) } }
    // Maps without a style of their own take the app's colors, from the app's theme.
    val theme = MaterialTheme.colorScheme.mapTheme()
    val app = LocalAppSettings.current
    val settings = MapSettings(haptics = app.haptics, snap = app.snapPlanets, longPressDelay = ViewConfiguration.getLongPressTimeout())
    LaunchedEffect(theme, settings) { model.configure(theme, settings) }
    val context = LocalContext.current
    val title = ui.map?.title?.takeIf { it.isNotBlank() } ?: ui.map?.let { model.summary()?.title }
    val summaries by context.container.maps.maps.collectAsStateWithLifecycle()
    val summary = summaries?.firstOrNull { it.id == id }
    val isPrivate = summary?.metadata?.isPrivate == true
    // Named as Home and the widgets name it, as the shortcuts follow the maps (matchShortcuts), its icon included.
    val shortcutTitle = title?.let { summary?.title?.takeIf { it.isNotBlank() } ?: it }

    // A private map stays out of the launcher's shortcuts.
    LaunchedEffect(shortcutTitle, isPrivate) {
        if (shortcutTitle != null && !isPrivate) {
            offerShortcut(context, id, shortcutTitle)
        }
    }

    val style = ui.map?.style
    val dark = if (style != null) mapStyle(style).dark else appIsDark()
    val windowWidth = LocalWindowInfo.current.containerSize.width

    OrbitTheme(dark = dark) {
        BoxWithConstraints {
            // Beside the list on a tablet, the map shares the status bar with it: the bar keeps the app's icons.
            if (constraints.maxWidth >= windowWidth - 1) {
                SystemBarIcons(dark)
            }

            MapScreenContent(model, ui, onClose)
        }
    }
}

/** A map screen setting the bars' icons ([SystemBarIcons]), dark or light as its map is. */
private class BarIconScreen(var dark: Boolean = false)

/**
 * The map screens setting the bars' icons, window by window, in the order
 * they came: the last one's show. A map opened over another, from a widget
 * say, comes before the one under it goes.
 */
private val barIconScreens = WeakHashMap<Window, MutableList<BarIconScreen>>()

/**
 * Light or dark status and navigation bar icons, while the screen is shown,
 * and as it goes, those of the map screen under it, or the app's: as the app
 * is then, which the system's theme may have changed meanwhile under a map
 * whose style doesn't. The app sets its own bars again as its theme changes,
 * and the map's are set again after. A map screen under another sets none.
 */
@Composable
internal fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    val appDark = appIsDark()
    val appDarkNow by rememberUpdatedState(appDark)
    val screen = remember { BarIconScreen() }
    val window = (view.context as? Activity)?.window
    val controller = remember(window, view) { window?.let { WindowCompat.getInsetsController(it, view) } }

    // Light icons over a dark screen, dark ones over a light one.
    fun iconsFor(darkScreen: Boolean) {
        controller?.isAppearanceLightStatusBars = !darkScreen
        controller?.isAppearanceLightNavigationBars = !darkScreen
    }

    DisposableEffect(window) {
        val screens = window?.let { barIconScreens.getOrPut(it) { mutableListOf() } }
        screens?.add(screen)

        onDispose {
            val top = screens?.lastOrNull() === screen
            screens?.remove(screen)

            if (top) {
                iconsFor(screens?.lastOrNull()?.dark ?: appDarkNow)
            }
        }
    }

    DisposableEffect(dark, appDark) {
        screen.dark = dark

        if (window == null || barIconScreens[window]?.lastOrNull() === screen) {
            iconsFor(dark)
        }

        onDispose {}
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MapScreenContent(model: MapViewModel, ui: MapUi, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val snackbars = remember { SnackbarHostState() }
    val messages = remember(scope) { MapMessages(snackbars, scope) }
    // Pickers, a goal's amount, and the map's own dialogs and sheets, over the map.
    val dialogs = rememberMapDialogs()
    // The search and the outline, kept here while they're open, over the map or beside it: turning the screen moves
    // their panels from one to the other.
    val search = rememberMapSearch(ui.map?.nodes.orEmpty(), open = ui.panel == Panel.Search)
    val outline = rememberMapOutline(open = ui.panel == Panel.Outline)
    // The toolbar's room is known before it first shows, so the map opens already fitted around it,
    // and kept while presenting or typing hides it, so the map doesn't move to fill its place and back.
    val toolbarSize = with(LocalDensity.current) { ToolbarSize.roundToPx() }
    var toolbarHeight by remember { mutableIntStateOf(toolbarSize) }
    // The path's strip, at its tallest while the path is made: the map keeps that room, so it holds still as the strip changes.
    var pathBarHeight by remember { mutableIntStateOf(0) }
    // The talk's panel, at its largest during the talk, for the same reason.
    var talkPanelSize by remember { mutableStateOf(IntSize.Zero) }
    var railWidth by remember { mutableIntStateOf(toolbarSize) }
    // Where the map ends, on screen in pixels.
    var mapBottom by remember { mutableFloatStateOf(0f) }
    var mapHeight by remember { mutableFloatStateOf(0f) }
    // The panel beside the map has risen over the top bar, for its field above the keyboard (PanelBeside).
    var panelRisen by remember { mutableStateOf(false) }
    // The note over a map that can only be looked at, in pixels: the map makes room for it.
    var readOnlyBannerHeight by remember { mutableIntStateOf(0) }
    val app = LocalAppSettings.current
    val mapSummaries by context.container.maps.maps.collectAsStateWithLifecycle()
    val isPrivate = mapSummaries?.firstOrNull { it.id == model.id }?.metadata?.isPrivate == true
    val cover = mapSummaries?.firstOrNull { it.id == model.id }?.metadata?.cover

    // As a private map locks, its send sheet goes, and with it what was shown to computers, as Home's does. Its messages go
    // too, the Undo for an idea just deleted with them (the deletion stays): kept under the lock, one would offer it back
    // once the map is unlocked, long after. The lock is heard as it happens, even while Orbit is off screen and draws
    // nothing, so whether the map is private is read as it is then too.
    WhenPrivateMapsLock {
        if (isPrivate || model.isPrivate()) {
            dialogs.sending = false
            messages.clear()
        }
    }

    val makePrivate = rememberMakePrivate { message -> messages.show(message) }

    val sound by model.sound.collectAsStateWithLifecycle()
    // The small player's size, for the map to fit above it, or beside it on a short screen.
    var soundBarSize by remember { mutableStateOf(IntSize.Zero) }

    // A sound pauses as Orbit leaves the screen, as videos do, and stops with the map.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.pauseSound() }
    DisposableEffect(model) { onDispose { model.stopSound() } }

    // A sound whose idea lost it, or went, stops.
    LaunchedEffect(ui.nodes, sound?.item) {
        val playing = sound?.item ?: return@LaunchedEffect

        if (ui.map != null && ui.nodes[playing.nodeId]?.audio?.src != playing.media.src) {
            model.stopSound()
        }
    }

    val talk = rememberTalk(model) { message, action, onAction ->
        messages.show(message, action) { result ->
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }

    // A presentation, a study session, the recorder or the map's lock ends a talk still going as it starts, the idea being
    // said left out: rememberTalk follows MapViewModel.talkable.
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                MapEvent.Tick -> if (app.haptics) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                MapEvent.LongPress -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                MapEvent.Added, MapEvent.Moved, MapEvent.GoalReached -> if (app.haptics) {
                    confirm(view)
                }
                is MapEvent.Open -> openLink(context, effect.url)
                // An Undo that comes as the map is locked would wait under the lock to offer it back: what was done stays done.
                is MapEvent.Snackbar -> if (!effect.undo || !model.lockedNow()) {
                    messages.show(effect.message.text(resources), if (effect.undo) resources.getString(R.string.common_undo) else null) { result ->
                        if (result == SnackbarResult.ActionPerformed) {
                            effect.onUndo?.invoke() ?: model.undo()
                        }
                    }
                }
                MapEvent.KeptInHistory -> messages.show(resources.getString(R.string.map_change_kept_in_history), resources.getString(R.string.common_version_history)) { result ->
                    if (result == SnackbarResult.ActionPerformed) {
                        model.openHistory()
                    }
                }
                MapEvent.Deleted -> {
                    withdrawShortcut(context, model.id)
                    onClose()
                }
                MapEvent.PresentAgain -> messages.show(resources.getString(R.string.private_presentation_stopped), resources.getString(R.string.private_present_again)) { result ->
                    if (result == SnackbarResult.ActionPerformed) {
                        model.startPresentation(app.presentationSeconds)
                    }
                }
            }
        }
    }

    // Swiping back flies up a level, with the map shrinking under the finger like any screen does.
    val backScale = remember { Animatable(1f) }
    MapBackHandlers(model, ui, talk, backScale)

    // The map fades in once its page has drawn it, over its own colors from the start: a dark map doesn't open through
    // the app's light screen, then its empty surface, then itself painting from the top down. A page given up on
    // never shows behind what the map's place says of it.
    val mapAlpha by animateFloatAsState(if (ui.ready && ui.drawn && ui.pageProblem == null) 1f else 0f, tween(260), label = "map")
    // Until the page says which style the map has, its file does.
    val styleName = if (ui.map != null) ui.map?.style else model.summary()?.outline?.style
    val style = mapStyle(styleName)
    val background = parseCssColor(ui.map?.surface) ?: style.background.takeIf { styleName != null && it.isSpecified } ?: colors.background
    // A map Orbit couldn't read has no title on the page: the one its file's first heading gives stands in.
    val title = ui.map?.title?.takeIf { it.isNotBlank() } ?: model.summary()?.title.orEmpty()

    // With nothing over it, the map holds the keyboard's focus, so its keys work as it opens and once a panel closes.
    val mapClear = ui.ready && ui.panel == null && ui.source == null && ui.presentation == null && ui.study == null && !ui.sketching

    // The keyboard: the map's shortcuts, or while presenting, the presentation's own keys, listed. Under a private
    // map's lock, none: the keyboard can't reach the map.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val coveredByLock = LocalMapLocked.current
    KeyboardShortcuts(
        when {
            coveredByLock -> emptyList()
            ui.presentation != null -> presentingShortcuts()
            ui.study != null -> studyShortcuts()
            ui.sketching -> sketchShortcuts(model, back = { backDispatcher?.onBackPressed() })
            else -> mapShortcuts(
                model,
                ui,
                present = { model.startPresentation(app.presentationSeconds) },
                back = { backDispatcher?.onBackPressed() },
                lockNow = if (isPrivate) model::lockPrivateMapsNow else null,
            )
        },
        priority = ShortcutPriority.Map,
    )

    if (ui.presentation != null) {
        PresentationMode(model, ui, background) { focusable -> MapCanvas(model, Modifier.fillMaxSize().graphicsLayer { alpha = mapAlpha }, focusable = focusable) }
        return
    }

    if (ui.study != null) {
        StudyMode(model, ui, background) { MapCanvas(model, Modifier.fillMaxSize().graphicsLayer { alpha = mapAlpha }, focusable = false) }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = mapLayout(maxWidth, maxHeight, ui, fieldOver = dialogs.fieldOver)
        // Where a sheet over the map rests, on screen in pixels, while one is open over it.
        var sheetTop by rememberSheetTop(over = layout.panelOver)
        val onSheetRest = remember { { top: Float? -> sheetTop = top } }
        val short = layout.short
        val wide = layout.wide
        val showStyle = layout.styleInToolbar
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val idea = ui.sun.takeIf { ui.focused }
        val onDetails = idea?.takeIf { ui.hasDetails(it) }?.let { node -> { model.show(Panel.Details(node.id)) } }
        // A map that can only be looked at has no editor to open: its ideas' details, pictures and sounds stay.
        val onEdit = idea?.takeIf { ui.readOnly == null }?.let { node -> { model.show(Panel.Editor(node.id)) } }

        val onMedia = idea?.takeIf { it.image != null || it.video != null }?.let { node ->
            { if (node.image != null) model.view(node.id, MediaKind.Picture) else model.play(node.id) }
        }

        // The idea in focus's sound plays in the small player, and the map can be flown on meanwhile.
        val onSound = idea?.takeIf { it.audio != null }?.let { node -> { model.toggleSound(node.id) } }
        val soundPlaying = sound?.playing == true && sound?.item?.nodeId == idea?.id
        val soundBar = sound != null && ui.recording == null && !ui.editing && ui.source == null && !ui.pathMaking && ui.study == null
        val soundRoom = if (soundBar) with(density) { (if (short) soundBarSize.width else soundBarSize.height).toDp() } + SoundBarGap else 0.dp

        // The toolbar floats over the map's bottom edge, or stands at its side on a short screen, and
        // a sheet that stops partway covers its lower part: Orbit fits its views into the room they
        // leave, gliding there alongside the sheet, so the idea being read or edited stays in view.
        val bars = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        val railAtLeft = layoutDirection == LayoutDirection.Rtl
        val toolbarRoom = with(density) { (toolbarHeight + bars.getBottom(density)).toDp() } + ToolbarMargin + Clearance
        val railSide = if (railAtLeft) bars.getLeft(density, layoutDirection) else bars.getRight(density, layoutDirection)

        // The map reaches under the navigation bar, and the screen's insets are used up by then: the
        // toolbar, the rail and the path's strip keep clear of the bar's buttons by its measure from here.
        val clearOfBars = with(density) {
            PaddingValues.Absolute(
                left = bars.getLeft(density, layoutDirection).toDp(),
                right = bars.getRight(density, layoutDirection).toDp(),
                bottom = bars.getBottom(density).toDp(),
            )
        }

        val railRoom = with(density) { (railWidth + railSide).toDp() } + ToolbarMargin + Clearance

        LaunchedEffect(ui.pathMaking) {
            if (!ui.pathMaking) {
                pathBarHeight = 0
            }
        }

        val pathRoom = with(density) { (maxOf(pathBarHeight, toolbarHeight) + bars.getBottom(density)).toDp() } + ToolbarMargin + Clearance
        val lifted = layout.lifted

        LaunchedEffect(lifted) {
            if (!lifted) {
                sheetTop = null
            }
        }

        val sheetRoom = sheetTop?.takeIf { lifted }?.let { top -> with(density) { (mapBottom - top).coerceAtLeast(0f).toDp() } + Clearance }

        // A keyboard that came up for a field off the map stays someone else's until it goes (keyboardElsewhere), unless
        // the map's own field opens and takes it.
        var keyboardOffMap by remember { mutableStateOf(false) }
        val roomForKeyboard by rememberUpdatedState(layout.roomForKeyboard)
        val keyboard = WindowInsets.imeAnimationTarget

        LaunchedEffect(keyboard, density) {
            snapshotFlow { roomForKeyboard to (keyboard.getBottom(density) > 0) }.collect { (room, up) ->
                keyboardOffMap = keyboardElsewhere(keyboardOffMap, room, up)
            }
        }

        LaunchedEffect(ui.editing) {
            if (ui.editing) {
                keyboardOffMap = false
            }
        }

        // Where nothing covers the map's bottom edge, it still ends behind the navigation bar.
        val navigation = with(density) { bars.getBottom(density).toDp() }.value
        // The breadcrumbs' room is kept even at the top level, so flying in and out never resizes the map.
        val crumbsRoom = BreadcrumbsHeight.value
        // Making a path, the breadcrumbs float over the map on every screen: the top bar is the path's.
        val crumbsOver = layout.crumbsOver

        LaunchedEffect(talk.active) {
            if (!talk.active) {
                talkPanelSize = IntSize.Zero
            }
        }

        // While a talk goes on, the map makes room for its panel, so each idea said lands in view: above it,
        // or beside it on a short screen, which has width to spare and no height.
        val talking = talk.active && talkPanelSize != IntSize.Zero
        val talkRoom = with(density) { if (short) talkPanelSize.width.toDp() else talkPanelSize.height.toDp() } + TalkGap
        // A map that can only be looked at fits below the note saying why.
        val bannerRoom = if (readOnlyBannerHeight > 0) ReadOnlyBannerTop + with(density) { readOnlyBannerHeight.toDp() } + Clearance else 0.dp
        val topRoom = maxOf(crumbsRoom, bannerRoom.value)

        val insets = when {
            // Beside its text the map reaches the bottom edge; above it, it ends where the text begins.
            ui.source != null -> if (wide) MapInsets(bottom = navigation) else MapInsets()
            sheetRoom != null -> MapInsets(top = topRoom, bottom = minOf(sheetRoom, with(density) { mapHeight.toDp() } * MostSheetRoom).value)
            // The path's strip takes the toolbar's place.
            ui.pathMaking -> MapInsets(top = topRoom, bottom = pathRoom.value)
            short -> {
                // The panel stands at the side the rail doesn't, clear of the bars there.
                val panelSide = if (railAtLeft) bars.getRight(density, layoutDirection) else bars.getLeft(density, layoutDirection)

                // The small player stands there too: a talk stops the sound, so they never meet.
                val panelRoom = when {
                    talking -> (with(density) { panelSide.toDp() } + TalkMargin + talkRoom).value
                    soundBar -> (with(density) { panelSide.toDp() } + TalkMargin + soundRoom).value
                    else -> 0f
                }

                if (railAtLeft) {
                    MapInsets(top = bannerRoom.value, left = railRoom.value, right = panelRoom, bottom = navigation)
                } else {
                    MapInsets(top = bannerRoom.value, left = panelRoom, right = railRoom.value, bottom = navigation)
                }
            }
            else -> MapInsets(top = topRoom, bottom = (toolbarRoom + soundRoom + if (talking) talkRoom else 0.dp).value)
        }

        // The talk's panel coming or going is a change made once: the map glides to make room for it, with the panel,
        // rather than jumping. Every other change, such as the keyboard sliding, moves it at once. A talk that leaves
        // the insets as they were (behind a sheet) still counts, so the next change isn't taken for the panel's.
        var talkingBefore by remember { mutableStateOf(talking) }

        LaunchedEffect(insets, talking) {
            model.setInsets(insets, glide = talking != talkingBefore)
            talkingBefore = talking
        }

        // A planet sketched on is held in the room the sketch's toolbar leaves: under it, or beside it on a short screen.
        val sketchRoom = when {
            !short -> MapInsets(bottom = toolbarRoom.value)
            railAtLeft -> MapInsets(left = railRoom.value, bottom = navigation)
            else -> MapInsets(right = railRoom.value, bottom = navigation)
        }

        LaunchedEffect(sketchRoom) {
            model.sketchRoom(sketchRoom)
        }

        Scaffold(
            containerColor = background,
            contentColor = colors.onBackground,
            topBar = {
                // The bars dim with the map while a planet is sketched on, and screen readers pass them by.
                Box(if (ui.sketching) Modifier.clearAndSetSemantics {} else Modifier) {
                    if (ui.source != null) {
                        SourceTopBar(model, ui.source, background)
                    } else if (ui.pathMaking) {
                        PathTopBar(ui, background, onSearch = { model.show(Panel.Search) }, onClear = model::clearPath, onDone = model::finishPath)
                    } else {
                        MapTopBar(
                            title = title,
                            ui = ui,
                            background = background,
                            // What's being typed is kept before the map closes, as Back keeps it.
                            onBack = { scope.launch { model.finishEditing(); onClose() } },
                            onUndo = model::undo,
                            onRedo = model::redo,
                            onRename = if (ui.readOnly == null) ({ dialogs.renamingMap = true }) else null,
                            onTags = if (ui.readOnly == null) ({ dialogs.taggingMap = true }) else null,
                            isPrivate = isPrivate,
                            onPrivate = { private -> model.summary()?.let { map -> makePrivate(map, private) } },
                            cover = cover,
                            onCover = if (isPrivate) ({ dialogs.coveringMap = true }) else null,
                            onLockNow = if (isPrivate) model::lockPrivateMapsNow else null,
                            onSource = model::openSource,
                            onPresent = { model.startPresentation(app.presentationSeconds) },
                            onPath = model::startPath,
                            onRehearse = { model.startPresentation(0, fromStart = true, rehearse = true) },
                            onStudy = { model.startStudy() },
                            onHistory = model::openHistory,
                            onExport = { model.show(Panel.Export) },
                            onSend = { dialogs.sending = true },
                            onDelete = { dialogs.deletingMap = true },
                            onFocus = { model.focus(it.id) },
                            onStyle = if (showStyle || ui.readOnly != null) null else ({ model.show(Panel.Style) }),
                            compact = short,
                            idea = idea,
                            onMedia = if (short) onMedia else null,
                            onDetails = if (short) onDetails else null,
                            onEdit = if (short) onEdit else null,
                            labeledPresent = layout.labeledPresent,
                            roomy = layout.roomyTopBar,
                            onSound = if (short) onSound else null,
                            soundPlaying = soundPlaying,
                        )
                    }

                    SketchScrim(ui)
                }
            },
            snackbarHost = {
                // Messages come up above the toolbar, the path's strip or the keyboard, rather than under them.
                val lift = when {
                    ui.source != null || ui.editing -> 0.dp
                    // On a short screen they come up above the small player, beside the map.
                    !crumbsOver -> if (soundBar) with(density) { soundBarSize.height.toDp() } + ToolbarMargin + SoundBarGap else 0.dp
                    ui.pathMaking -> with(density) { pathBarHeight.toDp() } + ToolbarMargin
                    else -> 88.dp + soundRoom
                }

                // With a panel or the map's text beside the map, they sit over the map they're about, clear of it (and on a
                // short screen of the rail at the map's side), gliding there as it comes and goes.
                val clearOfBeside by animateDpAsState(
                    besideRoom(
                        beside = layout.beside,
                        // The scaffold keeps them clear of the bars at the screen's end already, which what's beside reaches over.
                        scaffoldEnd = with(density) {
                            if (layoutDirection == LayoutDirection.Rtl) {
                                ScaffoldDefaults.contentWindowInsets.getLeft(density, layoutDirection).toDp()
                            } else {
                                ScaffoldDefaults.contentWindowInsets.getRight(density, layoutDirection).toDp()
                            }
                        },
                        rail = if (short && ui.source == null) with(density) { (railWidth + railSide).toDp() } + ToolbarMargin else 0.dp,
                    ),
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "clearOfBeside",
                )

                // While a sheet is over the map, it shows them over itself (LocalSheetMessages): here, they'd be under it.
                if (sheetTop == null || layout.panelBeside) {
                    OrbitSnackbarHost(
                        snackbars,
                        Modifier
                            // The glide's spring may overshoot on its way back to nothing.
                            .padding(bottom = lift, end = clearOfBeside.coerceAtLeast(0.dp))
                            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                            // Over the map, which comes over the top bar with the panel risen beside it.
                            .zIndex(2f),
                    )
                }
            },
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .consumeWindowInsets(padding)
                    // A panel beside the map risen for the keyboard comes over the top bar.
                    .zIndex(if (panelRisen && layout.panelBeside) 1f else 0f),
            ) {
                // Beside the map, its text: the layout says so only while there is one.
                val sourceBeside = ui.source?.takeIf { layout.sourceBeside }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        // The map makes room for the keyboard at once, so the field being typed in on it stays above it. A
                        // planet sketched on stays where it is, the sketch's description riding over the keyboard instead, and
                        // so does the map under the search, or a sheet or dialog with a field of its own (MapLayout.roomForKeyboard).
                        Box(
                            Modifier
                                .weight(if (ui.source != null && !wide) 0.42f else 1f)
                                .fillMaxWidth()
                                .then(
                                    if (layout.roomForKeyboard && !keyboardOffMap) {
                                        Modifier.windowInsetsPadding(WindowInsets.imeAnimationTarget)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            MapCanvas(
                                model,
                                keyboardFocus = mapClear,
                                heard = !ui.sketching,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { coordinates ->
                                        mapBottom = coordinates.positionOnScreen().y + coordinates.size.height
                                        mapHeight = coordinates.size.height.toFloat()
                                    }
                                    .graphicsLayer {
                                        alpha = mapAlpha
                                        scaleX = backScale.value
                                        scaleY = backScale.value
                                    },
                            )
                            MapOverlays(
                                model,
                                ui,
                                { node, action -> actOnIdea(model, ui, dialogs, node, action) },
                                hint = !talk.active,
                                clearOfBars = clearOfBars,
                                onBannerHeight = { readOnlyBannerHeight = it },
                            )
                            // A planet sketched on, its canvas and the sketch's tools, over the map.
                            SketchOverlay(model, ui, short = short, clearOfBars = clearOfBars)
                            // What a drag from another app would do: under the breadcrumbs, or above the toolbar while the finger is high.
                            DropFeedback(
                                model.drops,
                                ui,
                                top = if (crumbsOver && ui.path.size > 1 && ui.focused) BreadcrumbsHeight + 8.dp else 16.dp,
                                bottom = insets.bottom.dp + 8.dp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            // Big videos coming in, chosen in the editor or let go on the map: made smaller on the phone, or kept as they are.
                            val smallerOffer by model.smallerOffers.offer.collectAsStateWithLifecycle()
                            smallerOffer?.let { offer -> SmallerOfferDialog(offer, onAnswer = model.smallerOffers::answer) }

                            // On a short screen the breadcrumbs are in the top bar instead, unless it's the path's.
                            if (crumbsOver && ui.source == null) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = ui.path.size > 1 && ui.focused && !ui.sketching,
                                    enter = fadeIn() + slideInVertically(),
                                    exit = fadeOut() + slideOutVertically(),
                                    modifier = Modifier.align(Alignment.TopStart),
                                ) {
                                    Breadcrumbs(
                                        ui.path,
                                        onPick = { model.focus(it.id) },
                                        modifier = Modifier
                                            .height(BreadcrumbsHeight)
                                            .background(Brush.verticalGradient(0.6f to background, 1f to background.copy(alpha = 0f))),
                                    )
                                }
                            }
                        }

                        if (ui.source != null && !wide) {
                            HorizontalDivider()
                            SourceEditor(model, ui.source, ui.readOnly, Modifier.weight(0.58f).fillMaxWidth(), clearOfBars.calculateBottomPadding())
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = ui.pathMaking && ui.source == null && !ui.editing,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(clearOfBars)
                            .padding(start = 16.dp, end = 16.dp, bottom = ToolbarMargin),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = ToolbarSize)
                                .onSizeChanged { pathBarHeight = maxOf(pathBarHeight, it.height) },
                        ) {
                            PathBar(
                                ui = ui,
                                onFly = { model.focus(it) },
                                onRemove = model::removePathStep,
                                onMove = model::movePathStep,
                                onAdd = model::addToPath,
                            )
                        }
                    }

                    TalkPanel(
                        talk,
                        Modifier
                            .align(if (short) Alignment.BottomStart else Alignment.BottomCenter)
                            .padding(clearOfBars)
                            .then(
                                if (short) {
                                    Modifier.padding(start = TalkMargin, bottom = ToolbarMargin).widthIn(max = TalkPanelBesideWidth)
                                } else {
                                    Modifier.padding(start = TalkMargin, end = TalkMargin, bottom = ToolbarMargin + ToolbarSize + TalkGap)
                                },
                            )
                            .onSizeChanged {
                                talkPanelSize = IntSize(maxOf(talkPanelSize.width, it.width), maxOf(talkPanelSize.height, it.height))
                            },
                    )

                    // A planet sketched on keeps the player's room, so the map doesn't move under the canvas.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = soundBar && !ui.sketching,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(if (short) Alignment.BottomStart else Alignment.BottomCenter)
                            .padding(clearOfBars)
                            .then(
                                if (short) {
                                    Modifier.padding(start = TalkMargin, bottom = ToolbarMargin).widthIn(max = SoundBarBesideWidth)
                                } else {
                                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = ToolbarMargin + with(density) { toolbarHeight.toDp() } + SoundBarGap)
                                },
                            )
                            .onSizeChanged { if (it.height > 0) soundBarSize = it },
                    ) {
                        sound?.let { playing ->
                            SoundBar(
                                sound = playing,
                                controls = model.sounds,
                                source = model::mediaModel,
                                onOpen = { model.viewSound(playing.item.nodeId) },
                                onStop = model::stopSound,
                            )
                        }
                    }

                    if (ui.source == null) {
                        MapToolbar(
                            ui = ui,
                            visible = !ui.editing && ui.ready && !ui.pathMaking && ui.panel !is Panel.History && !ui.sketching,
                            vertical = short,
                            idea = idea,
                            onOutline = { model.show(Panel.Outline) },
                            onSearch = { model.show(Panel.Search) },
                            onToggleView = model::toggleView,
                            onStyle = if (showStyle && ui.readOnly == null) ({ model.show(Panel.Style) }) else null,
                            onMedia = if (short) null else onMedia,
                            onSound = if (short) null else onSound,
                            soundPlaying = soundPlaying,
                            onDetails = if (short) null else onDetails,
                            onEdit = if (short) null else onEdit,
                            onAdd = if (ui.readOnly == null) ({ model.addIdea() }) else null,
                            talk = talk,
                            onHold = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                            modifier = Modifier
                                .align(if (short) Alignment.CenterEnd else Alignment.BottomCenter)
                                .padding(clearOfBars)
                                .padding(if (short) PaddingValues(end = ToolbarMargin) else PaddingValues(bottom = ToolbarMargin))
                                .onSizeChanged {
                                    if (short && it.width > 0) {
                                        railWidth = it.width
                                    } else if (!short && it.height > 0) {
                                        toolbarHeight = it.height
                                    }
                                },
                        )
                    }
                }

                // Beside the map, the text and the panels reach the screen's edge, under the navigation bar or the
                // cutout there on a phone on its side, and under the taskbar: what they hold keeps clear of them.
                val besideEdge = with(density) { railSide.toDp() }

                if (sourceBeside != null) {
                    VerticalDivider()

                    Box(Modifier.width(layout.sourceWidth).fillMaxHeight().background(colors.surfaceContainerLow)) {
                        SourceEditor(model, sourceBeside, ui.readOnly, Modifier.fillMaxSize().padding(end = besideEdge), clearOfBars.calculateBottomPadding())
                    }
                } else if (layout.panelBeside) {
                    // The keyboard for a field in it can leave less room under the top bar than the panel's title takes, on a
                    // phone on its side: it rises over the top bar, up to the status bar, as far as its field needs.
                    val statusBar = with(density) { WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density).toDp() }

                    PanelBeside(topBar = (padding.calculateTopPadding() - statusBar).coerceAtLeast(0.dp), onRise = { panelRisen = it }) {
                        Box(Modifier.padding(end = besideEdge, bottom = clearOfBars.calculateBottomPadding())) {
                            MapPanels(model, ui, sheet = false, search = search, outline = outline, onPickIcon = { dialogs.pickingIcon = it }, onPickColor = { dialogs.pickingColor = it }, onPickDate = { dialogs.pickingDate = it })
                        }
                    }
                }
            }
        }

        // The sketch's description, over the whole window: above the keyboard, which on a phone on its side leaves it less
        // room than the map has under the top bar.
        SketchDescription(model, ui, short = short)

        if (!layout.panelBeside) {
            CompositionLocalProvider(LocalSheetRest provides onSheetRest, LocalSheetMessages provides snackbars) {
                MapPanels(model, ui, sheet = true, search = search, outline = outline, onPickIcon = { dialogs.pickingIcon = it }, onPickColor = { dialogs.pickingColor = it }, onPickDate = { dialogs.pickingDate = it })
            }
        }
    }

    MapDialogs(model, ui, dialogs, title)
    MapFullScreen(model, ui, sound)
}

/** The text, in the app's language. */
@Composable
fun ShownText.text(): String = text(LocalResources.current)

/**
 * Over the map: the long-press menu, the first idea's hint where `hint`
 * allows it, problems, and the way back after the page stopped or didn't
 * load, which keeps `clearOfBars`, as the map reaches under the bars.
 */
@Composable
private fun MapOverlays(model: MapViewModel, ui: MapUi, act: (MapNode, NodeAction) -> Unit, hint: Boolean, clearOfBars: PaddingValues, onBannerHeight: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sound by model.sound.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        if (!(ui.ready && ui.drawn) && ui.pageProblem == null && ui.error == null) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(56.dp))
        }

        (ui.panel as? Panel.Menu)?.let { menu ->
            val node = ui.nodes[menu.id]

            if (node != null) {
                NodeMenu(
                    node = node,
                    x = menu.x,
                    y = menu.y,
                    parent = node.parentId?.let { ui.nodes[it] },
                    under = ui.progress.under(node),
                    ideasUnder = remember(ui.map?.nodes, node.id) { ideasUnder(ui.map?.nodes.orEmpty(), node.id) },
                    mapColors = remember(ui.map?.nodes, node.color) { offeredCustomColors(ui.map?.nodes.orEmpty().map { it.color }, node.color) },
                    pasteable = remember(menu) { model.pasteable() },
                    soundPlaying = sound?.playing == true && sound?.item?.nodeId == node.id,
                    onDismiss = { model.show(null) },
                    onAction = { action -> act(node, action) },
                )
            }
        }

        val root = ui.map?.root

        AnimatedVisibility(
            visible = hint && ui.ready && ui.readOnly == null && root != null && root.childCount == 0 && !ui.editing && ui.focused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                stringResource(R.string.map_first_idea_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 40.dp, end = 40.dp, bottom = 132.dp),
            )
        }

        ui.pageProblem?.let { problem ->
            PageProblemNotice(problem, onLoadAgain = model::reload, modifier = Modifier.align(Alignment.Center).padding(clearOfBars), sketch = ui.sketch?.held == true)
        }

        val banner = Modifier
            .align(Alignment.TopCenter)
            .padding(start = 16.dp, end = 16.dp, top = ReadOnlyBannerTop)
            .widthIn(max = 560.dp)
            .onSizeChanged { onBannerHeight(it.height) }

        // A map that can't be changed is never saved: only one of them says something.
        if (ui.readOnly != null) {
            ReadOnlyBanner(model, ui, banner)
        } else {
            UnsavedBanner(model, ui, banner)
        }

        val webView = SystemWebView.of(LocalContext.current)

        if (!webView.drawsMaps) {
            OldWebViewNotice(webView, Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(24.dp))
        } else {
            ui.error?.let { message ->
                Text(
                    message.text(),
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )
            }
        }
    }
}

/**
 * Over the top of a map that can only be looked at: why, and what to do,
 * whether that's fixing its text, bringing back a version, restoring it from
 * the trash or updating Orbit.
 */
@Composable
private fun ReadOnlyBanner(model: MapViewModel, ui: MapUi, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val readOnly = ui.readOnly

    AnimatedVisibility(readOnly != null && ui.source == null && ui.presentation == null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val shown = readOnly ?: return@AnimatedVisibility
        // Orbit says what it couldn't read in English: in another language, the note says what to do without it.
        val english = LocalConfiguration.current.locales[0].language == "en"
        val problem = ui.readProblem.takeIf { english }

        val (title, body) = when (shown) {
            ReadOnly.Unreadable -> stringResource(R.string.map_read_only_unreadable_title) to if (problem != null) {
                stringResource(R.string.map_read_only_unreadable_body_with_problem, sentence(problem))
            } else {
                stringResource(R.string.map_read_only_unreadable_body)
            }
            ReadOnly.Newer -> stringResource(R.string.map_read_only_newer_title) to stringResource(R.string.map_read_only_newer_body)
            ReadOnly.Deleted -> stringResource(R.string.map_read_only_deleted_title) to stringResource(R.string.map_read_only_deleted_body)
            ReadOnly.Storage -> stringResource(R.string.map_read_only_storage_title) to stringResource(R.string.map_read_only_storage_body)
        }

        val store = remember { orbitStorePage(context) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }

                Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (shown) {
                        ReadOnly.Unreadable -> {
                            TextButton(onClick = model::openHistory) { Text(stringResource(R.string.common_version_history)) }
                            TextButton(onClick = model::openSource) { Text(stringResource(R.string.map_edit_the_text)) }
                        }
                        ReadOnly.Deleted -> TextButton(onClick = model::restoreFromTrash) { Text(stringResource(R.string.common_restore)) }
                        ReadOnly.Newer, ReadOnly.Storage -> if (store != null) {
                            TextButton(onClick = { runCatching { context.startActivity(store) } }) { Text(stringResource(R.string.map_update_orbit)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Over the top of a map whose latest changes the device's storage couldn't
 * take: they're kept on this screen and saved as soon as there's room, which
 * it says, with the way to the phone's storage settings and to try again now.
 * Answers a study session couldn't write say so the same way, once it's over
 * (it says so itself while it goes), unless the map's changes do already.
 */
@Composable
private fun UnsavedBanner(model: MapViewModel, ui: MapUi, modifier: Modifier = Modifier) {
    val answers = ui.answersUnsaved?.takeIf { ui.study == null }
    // Which one it says, and why answers wait, as it was while it fades away.
    val changes = lastOf(if (ui.unsaved) true else if (answers != null) false else null) ?: true
    val problem = lastOf(answers) ?: AnswersUnsaved.Storage

    AnimatedVisibility((ui.unsaved || answers != null) && ui.source == null && ui.presentation == null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (changes) {
            UnsavedNote(stringResource(R.string.map_unsaved_title), stringResource(R.string.map_unsaved_body), onTryAgain = model::saveAgain)
        } else {
            UnsavedAnswers(problem, onTryAgain = model::tryAnswersAgain)
        }
    }
}

/** How far below the top of the map the note over a map that can't change sits: below the breadcrumbs. */
private val ReadOnlyBannerTop = 56.dp

/** Text ending as a sentence does, for another to follow it. */
private fun sentence(text: String): String = text.trim().let { if (it.endsWith('.') || it.endsWith('!') || it.endsWith('?')) it else "$it." }

/** Orbit's page in the phone's app store, when there's one that opens it. */
private fun orbitStorePage(context: Context): Intent? =
    Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .takeIf { it.resolveActivity(context.packageManager) != null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapTopBar(
    title: String,
    ui: MapUi,
    background: Color,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRename: (() -> Unit)?,
    onTags: (() -> Unit)?,
    isPrivate: Boolean,
    onPrivate: (Boolean) -> Unit,
    cover: LockedCover?,
    onCover: (() -> Unit)?,
    onLockNow: (() -> Unit)?,
    onSource: () -> Unit,
    onPresent: () -> Unit,
    onPath: () -> Unit,
    onRehearse: () -> Unit,
    onStudy: () -> Unit,
    onHistory: () -> Unit,
    onExport: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
    onFocus: (MapNode) -> Unit,
    onStyle: (() -> Unit)?,
    compact: Boolean,
    idea: MapNode?,
    onMedia: (() -> Unit)?,
    onDetails: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    labeledPresent: Boolean,
    roomy: Boolean,
    onSound: (() -> Unit)?,
    soundPlaying: Boolean,
) {
    val crumbs = ui.path.size > 1 && ui.focused
    TopAppBar(
        title = {
            // On a short screen the way to the idea takes the title's place, rather than a row of its own.
            if (compact && crumbs) {
                Breadcrumbs(ui.path, onFocus)
            } else {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onRename != null) Modifier.clickable(onClickLabel = stringResource(R.string.map_rename_the_map), onClick = onRename) else Modifier,
                )
            }
        },
        navigationIcon = {
            val back = stringResource(R.string.map_back_to_your_maps)

            Hint(back) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = back) }
            }
        },
        actions = {
            if (idea != null && (onEdit != null || onMedia != null || onSound != null || onDetails != null)) {
                AnimatedVisibility(onMedia != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    MediaButton(idea, onClick = { onMedia?.invoke() })
                }

                AnimatedVisibility(onSound != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    SoundButton(idea, soundPlaying, onClick = { onSound?.invoke() })
                }

                AnimatedVisibility(onDetails != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    val holds = stringResource(R.string.map_what_the_idea_holds, idea.text)

                    Hint(holds) {
                        IconButton(onClick = { onDetails?.invoke() }) {
                            Icon(Icons.Outlined.Description, contentDescription = holds)
                        }
                    }
                }

                onEdit?.let { edit ->
                    val editIdea = stringResource(R.string.map_edit_the_idea, idea.text)

                    Hint(editIdea, MapKeys.Edit) {
                        IconButton(onClick = edit) { Icon(Icons.Outlined.Edit, contentDescription = editIdea) }
                    }
                }

                VerticalDivider(Modifier.padding(horizontal = 6.dp).height(24.dp))
            }

            val undo = stringResource(R.string.common_undo)

            Hint(undo, MapKeys.Undo) {
                IconButton(onClick = onUndo, enabled = ui.undoable) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = undo) }
            }

            val redo = stringResource(R.string.common_redo)

            // On a phone, or a phone's width of map beside the list, Redo leaves its room to the map's name until something
            // has been undone. Undo stays.
            AnimatedVisibility(ui.redoable || roomy, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Hint(redo, MapKeys.Redo) {
                    IconButton(onClick = onRedo, enabled = ui.redoable) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = redo) }
                }
            }

            // A private map locks at once from here on tablets, where the bar has room; on a phone, from the menu.
            if (onLockNow != null && roomy) {
                LockNowButton(onLockNow)
            }

            // A TV the phone casts to, after a presentation: the way to stop it. Looking for TVs starts only with presenting.
            CastingButton(dark = background.luminance() < 0.5f)
            // Presenting is what maps are for: a button of its own, with its name where there's room.
            val present = stringResource(R.string.common_present)

            if (labeledPresent) {
                FilledTonalButton(
                    onClick = onPresent,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(Icons.Outlined.Slideshow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(present)
                }
            } else {
                Hint(present, MapKeys.Present) {
                    IconButton(onClick = onPresent) { Icon(Icons.Outlined.Slideshow, contentDescription = present) }
                }
            }

            MapMenu(ui.presentPath.count { it in ui.nodes }, onPath, onRehearse, onStudy, onHistory, onRename, onTags, isPrivate, onPrivate, cover, onCover, onLockNow, onSource, onExport, onSend, onDelete, onStyle)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
    )
}

/** The map's menu. `pathLength` is how many ideas its presentation path has, if it has one. */
@Composable
private fun MapMenu(
    pathLength: Int,
    onPath: () -> Unit,
    onRehearse: () -> Unit,
    onStudy: () -> Unit,
    onHistory: () -> Unit,
    onRename: (() -> Unit)?,
    onTags: (() -> Unit)?,
    isPrivate: Boolean,
    onPrivate: (Boolean) -> Unit,
    cover: LockedCover?,
    onCover: (() -> Unit)?,
    onLockNow: (() -> Unit)?,
    onSource: () -> Unit,
    onExport: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
    onStyle: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        val more = stringResource(R.string.map_more)

        Hint(more) {
            IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = more) }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            onStyle?.let { style ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_style_and_layout)) },
                    leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                    onClick = { open = false; style() },
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_presentation_path)) },
                leadingIcon = { Icon(Icons.Outlined.Route, contentDescription = null) },
                trailingIcon = if (pathLength > 1) ({ Text("$pathLength", style = MaterialTheme.typography.labelLarge) }) else null,
                onClick = { open = false; onPath() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_rehearse)) },
                leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                onClick = { open = false; onRehearse() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_study)) },
                leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null) },
                trailingIcon = if (showsMenuKeys()) ({ Text(MapKeys.Study.caps.joinToString("+"), style = MaterialTheme.typography.labelLarge) }) else null,
                onClick = { open = false; onStudy() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_version_history)) },
                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                onClick = { open = false; onHistory() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_edit_as_text)) },
                leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                onClick = { open = false; onSource() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_send_to_a_device)) },
                leadingIcon = { Icon(Icons.Outlined.Devices, contentDescription = null) },
                onClick = { open = false; onSend() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_export_and_share)) },
                leadingIcon = { Icon(Icons.Outlined.IosShare, contentDescription = null) },
                onClick = { open = false; onExport() },
            )

            onRename?.let { rename ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.map_rename_map)) },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                    onClick = { open = false; rename() },
                )
            }

            onTags?.let { tags ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_tags)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                    onClick = { open = false; tags() },
                )
            }

            PrivateMenuItem(isPrivate, onChange = onPrivate, onDismiss = { open = false })

            onCover?.let { choose ->
                CoverMenuItem(cover) { open = false; choose() }
            }

            onLockNow?.let { lock ->
                val everyPrivateMap = stringResource(R.string.private_lock_private_maps_now)

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.private_menu_lock_now)) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = if (hasHardwareKeyboard()) ({ Text(MapKeys.LockNow.caps.joinToString("+"), style = MaterialTheme.typography.labelLarge) }) else null,
                    onClick = { open = false; lock() },
                    // Every private map locks, not this one alone: screen readers say so.
                    modifier = Modifier.semantics { contentDescription = everyPrivateMap },
                )
            }

            val shortcuts = LocalShortcuts.current

            if (shortcuts != null && hasHardwareKeyboard()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_keyboard_shortcuts)) },
                    leadingIcon = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
                    trailingIcon = if (showsMenuKeys()) ({ Text(ShortcutListKeys.caps.joinToString("+"), style = MaterialTheme.typography.labelLarge) }) else null,
                    onClick = { open = false; shortcuts.listOpen = true },
                )
            }

            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_delete_map)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/**
 * Locks every private map now, from a private map's top bar on a tablet, as
 * Home's lock does: its shape answers the press, and the phone a confirming
 * tap, as the map goes behind its lock.
 */
@Composable
private fun LockNowButton(onLockNow: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.private_lock_private_maps_now)

    Hint(label, MapKeys.LockNow) {
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onLockNow()
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = label)
        }
    }
}

/** Where the map is: tap a level to fly back to it. */
@Composable
private fun Breadcrumbs(path: List<MapNode>, onPick: (MapNode) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(path, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        path.forEachIndexed { index, node ->
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            val current = index == path.lastIndex
            val here = stringResource(R.string.map_breadcrumb_here)
            val name = node.label
            FilterChip(
                selected = current,
                onClick = {
                    if (!current) {
                        onPick(node)
                    }
                },
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // The way back up, not a choice: screen readers hear each level as a button, and the last as where the map is.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = name

                    if (current) {
                        stateDescription = here
                    } else {
                        role = Role.Button
                        onClick { onPick(node); true }
                    }
                },
            )
        }
    }
}

/**
 * The map's tools, floating over its bottom edge or, `vertical`, in a rail at
 * its side, then the buttons of the idea in focus: what it holds (when it
 * holds something) and its editor, and + to add an idea to its orbit.
 * `onStyle` is null where the toolbar has no room for it, and the map's menu
 * offers it instead; the idea's buttons are null where the top bar has them.
 */
@Composable
private fun MapToolbar(
    ui: MapUi,
    visible: Boolean,
    vertical: Boolean,
    idea: MapNode?,
    onOutline: () -> Unit,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onStyle: (() -> Unit)?,
    onMedia: (() -> Unit)?,
    onSound: (() -> Unit)?,
    soundPlaying: Boolean,
    onDetails: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    /** Adds an idea; none on a map that can't change, and the toolbar has no + then. */
    onAdd: (() -> Unit)?,
    talk: Talk,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The rail slides in from the side it stands at.
    val side = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + if (vertical) slideInHorizontally { side * it / 2 } else slideInVertically { it / 2 },
        exit = fadeOut() + if (vertical) slideOutHorizontally { side * it / 2 } else slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val add = @Composable {
            // A tap adds an idea to type; holding talks ideas onto the map.
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { onAdd?.invoke() }, modifier = Modifier.holdToTalk(talk, onHold)) {
                val sun = ui.sun?.text
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (sun != null) {
                        stringResource(R.string.map_add_an_idea_to_the_idea_or_talk, sun)
                    } else {
                        stringResource(R.string.map_add_an_idea_to_the_map_or_talk)
                    },
                )
            }
        }

        val buttons = @Composable { ToolbarButtons(ui, vertical, idea, onOutline, onSearch, onToggleView, onStyle, onMedia, onSound, soundPlaying, onDetails, onEdit) }

        if (onAdd == null) {
            ToolbarWithoutAdd(vertical, buttons)
        } else if (vertical) {
            VerticalFloatingToolbar(expanded = true, floatingActionButton = add, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                buttons()
            }
        } else {
            HorizontalFloatingToolbar(expanded = true, floatingActionButton = add, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                buttons()
            }
        }
    }
}

/**
 * Material's floating toolbar without a + (on a map that can only be looked
 * at), in a row or a `vertical` column. Without a +, it evens out its padding
 * by what it holds, read from alignment lines as it measures, and that throws
 * in the lookahead pass as a panel opens beside the map on a tablet, closing
 * Orbit. Ends of nothing on both sides leave its padding as it is, so it
 * reads none.
 */
@Composable
internal fun ToolbarWithoutAdd(vertical: Boolean, buttons: @Composable () -> Unit) {
    if (vertical) {
        VerticalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(), leadingContent = {}, trailingContent = {}) { buttons() }
    } else {
        HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(), leadingContent = {}, trailingContent = {}) { buttons() }
    }
}

/** The toolbar's buttons, in a row or a column. */
@Composable
private fun ToolbarButtons(
    ui: MapUi,
    vertical: Boolean,
    idea: MapNode?,
    onOutline: () -> Unit,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onStyle: (() -> Unit)?,
    onMedia: (() -> Unit)?,
    onSound: (() -> Unit)?,
    soundPlaying: Boolean,
    onDetails: (() -> Unit)?,
    onEdit: (() -> Unit)?,
) {
    val outline = stringResource(R.string.common_outline)

    Hint(outline, MapKeys.Outline) {
        IconButton(onClick = onOutline) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = outline) }
    }

    val search = stringResource(R.string.common_search_the_map)

    Hint(search, MapKeys.Search) {
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = search) }
    }

    if (!ui.focused) {
        val focus = stringResource(R.string.map_focus_on_one_idea)

        Hint(focus, MapKeys.Orbit) {
            IconButton(onClick = onToggleView) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = focus) }
        }
    } else {
        val wholeMap = stringResource(R.string.map_show_the_whole_map)

        Hint(wholeMap, MapKeys.WholeMap) {
            IconButton(onClick = onToggleView) { Icon(Icons.Outlined.Hub, contentDescription = wholeMap) }
        }
    }

    onStyle?.let { style ->
        val styleAndLayout = stringResource(R.string.common_style_and_layout)

        Hint(styleAndLayout) {
            IconButton(onClick = style) { Icon(Icons.Outlined.Palette, contentDescription = styleAndLayout) }
        }
    }

    if (idea != null && (onEdit != null || onMedia != null || onSound != null || onDetails != null)) {
        val divider = LocalContentColor.current.copy(alpha = 0.32f)

        if (vertical) {
            HorizontalDivider(color = divider, modifier = Modifier.padding(vertical = 6.dp).width(24.dp))
        } else {
            VerticalDivider(color = divider, modifier = Modifier.padding(horizontal = 6.dp).height(24.dp))
        }

        AnimatedVisibility(
            onMedia != null,
            enter = fadeIn() + if (vertical) expandVertically() else expandHorizontally(),
            exit = fadeOut() + if (vertical) shrinkVertically() else shrinkHorizontally(),
        ) {
            MediaButton(idea, onClick = { onMedia?.invoke() })
        }

        AnimatedVisibility(
            onSound != null,
            enter = fadeIn() + if (vertical) expandVertically() else expandHorizontally(),
            exit = fadeOut() + if (vertical) shrinkVertically() else shrinkHorizontally(),
        ) {
            SoundButton(idea, soundPlaying, onClick = { onSound?.invoke() })
        }

        AnimatedVisibility(
            onDetails != null,
            enter = fadeIn() + if (vertical) expandVertically() else expandHorizontally(),
            exit = fadeOut() + if (vertical) shrinkVertically() else shrinkHorizontally(),
        ) {
            val holds = stringResource(R.string.map_what_the_idea_holds, idea.text)

            Hint(holds) {
                IconButton(onClick = { onDetails?.invoke() }) {
                    Icon(Icons.Outlined.Description, contentDescription = holds)
                }
            }
        }

        onEdit?.let { edit ->
            val editIdea = stringResource(R.string.map_edit_the_idea, idea.text)

            Hint(editIdea, MapKeys.Edit) {
                IconButton(onClick = edit) { Icon(Icons.Outlined.Edit, contentDescription = editIdea) }
            }
        }
    }
}

/** The idea in focus's sound: plays it in the small player, or pauses it, the button saying which. */
@Composable
private fun SoundButton(idea: MapNode, playing: Boolean, onClick: () -> Unit) {
    val name = stringResource(if (playing) R.string.audio_pause_the_sound_in else R.string.audio_play_the_sound_in, idea.text)

    Hint(name) {
        IconButton(onClick = onClick) {
            Icon(if (playing) Icons.Outlined.PauseCircle else Icons.Outlined.GraphicEq, contentDescription = name)
        }
    }
}

/** The idea in focus's picture, full screen, or its video when it has no picture. */
@Composable
private fun MediaButton(idea: MapNode, onClick: () -> Unit) {
    val picture = idea.image != null
    val name = if (picture) stringResource(R.string.map_view_the_picture_in, idea.text) else stringResource(R.string.map_play_the_video_in, idea.text)

    Hint(name) {
        IconButton(onClick = onClick) {
            Icon(if (picture) Icons.Outlined.Image else Icons.Outlined.PlayCircle, contentDescription = name)
        }
    }
}

/** A light confirmation as an idea joins the map, lands where it was dragged, or reaches its goal. */
private fun confirm(view: View) {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    view.performHapticFeedback(feedback)
}
