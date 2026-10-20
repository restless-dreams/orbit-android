package com.hyuchia.orbit.map

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.hyuchia.orbit.data.TRASH_DAYS
import androidx.core.net.toUri
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.home.TitleDialog
import com.hyuchia.orbit.home.tagCounts
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.MediaViewer
import com.hyuchia.orbit.media.VideoScreen
import com.hyuchia.orbit.nearby.SendSheet
import com.hyuchia.orbit.page.MapInsets
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapSettings
import com.hyuchia.orbit.page.SystemWebView
import com.hyuchia.orbit.present.CastingButton
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitPalette
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.theme.mapStyle
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.ColorPickerDialog
import com.hyuchia.orbit.ui.EmojiPickerSheet
import com.hyuchia.orbit.ui.offeredCustomColors
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.KeyboardShortcuts
import com.hyuchia.orbit.ui.LocalShortcuts
import com.hyuchia.orbit.ui.OldWebViewNotice
import com.hyuchia.orbit.ui.ShortcutListKeys
import com.hyuchia.orbit.ui.ShortcutPriority
import com.hyuchia.orbit.ui.hasHardwareKeyboard
import com.hyuchia.orbit.ui.offerShortcut
import com.hyuchia.orbit.ui.openLink
import com.hyuchia.orbit.ui.orbitViewModel
import com.hyuchia.orbit.ui.withdrawShortcut
import com.hyuchia.orbit.ui.TagsSheet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/** Panels show beside the map on screens at least this wide. */
private val SidePanelWidth = 380.dp
private val WideScreen = 840.dp
/** Under this width the toolbar leaves out Style and layout, which moves into the map's menu, so the idea's buttons fit. */
private val RoomForStyle = 400.dp
/** A button in the toolbar takes this much of its width. */
private val ToolbarButtonWidth = 48.dp
/** Shorter than this, as a phone on its side is, the map's tools stand in a rail at its side and the idea's buttons join the top bar. */
private val ShortScreen = 480.dp
/** On a screen wider than tall, panels go beside the map whenever it keeps at least this much width. */
private val MinimumMapWidth = 360.dp
/** The toolbar floats this far above the bottom edge. */
private val ToolbarMargin = 16.dp
/** A floating toolbar with a + is this tall, or as a rail this wide, whatever it holds: room for the + at its largest. */
private val ToolbarSize = 80.dp
/** The breadcrumbs' row, which floats over the map's top edge: as tall as a chip's touch target. */
private val BreadcrumbsHeight = 48.dp
/** From this width the top bar's Present button says its name. */
private val LabeledPresentWidth = 600.dp
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
 * a panel beside the map, its text, or a presentation. `present` starts
 * presenting the map from the top as soon as it's drawn.
 */
@Composable
fun MapScreen(id: String, onClose: () -> Unit, onWantsRoom: (Boolean) -> Unit = {}, present: Boolean = false) {
    val model = orbitViewModel(key = "map-$id") { MapViewModel(it, id) }
    val ui by model.ui.collectAsStateWithLifecycle()
    var presented by rememberSaveable { mutableStateOf(false) }
    val presentSeconds = LocalAppSettings.current.presentationSeconds

    LaunchedEffect(present, ui.ready) {
        if (present && ui.ready && !presented) {
            presented = true
            model.startPresentation(presentSeconds, fromStart = true)
        }
    }

    val wantsRoom = ui.panel.showsBeside() || ui.source != null || ui.presentation != null
    LaunchedEffect(wantsRoom) { onWantsRoom(wantsRoom) }
    DisposableEffect(Unit) { onDispose { onWantsRoom(false) } }
    // Maps without a style of their own take the app's colors, from the app's theme.
    val theme = MaterialTheme.colorScheme.mapTheme()
    val app = LocalAppSettings.current
    val settings = MapSettings(haptics = app.haptics, snap = app.snapPlanets, longPressDelay = ViewConfiguration.getLongPressTimeout())
    LaunchedEffect(theme, settings) { model.configure(theme, settings) }
    val context = LocalContext.current
    val title = ui.map?.title?.takeIf { it.isNotBlank() } ?: ui.map?.let { model.summary()?.title }

    LaunchedEffect(title) {
        if (title != null) {
            offerShortcut(context, id, title)
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

/** Light or dark status and navigation bar icons, while the screen is shown. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    val appDark = appIsDark()

    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark

        onDispose {
            controller?.isAppearanceLightStatusBars = !appDark
            controller?.isAppearanceLightNavigationBars = !appDark
        }
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
    var pickingIcon by remember { mutableStateOf<String?>(null) }
    var pickingColor by remember { mutableStateOf<String?>(null) }
    // The goal an amount is being added to, taken off or paid, from its long-press menu.
    var changingGoal by rememberSaveable { mutableStateOf<String?>(null) }
    var goalChange by rememberSaveable { mutableStateOf<GoalChange?>(null) }
    // The toolbar's room is known before it first shows, so the map opens already fitted around it,
    // and kept while presenting or typing hides it, so the map doesn't move to fill its place and back.
    val toolbarSize = with(LocalDensity.current) { ToolbarSize.roundToPx() }
    var toolbarHeight by remember { mutableIntStateOf(toolbarSize) }
    // The path's strip, at its tallest while the path is made: the map keeps that room, so it holds still as the strip changes.
    var pathBarHeight by remember { mutableIntStateOf(0) }
    // The talk's panel, at its largest during the talk, for the same reason.
    var talkPanelSize by remember { mutableStateOf(IntSize.Zero) }
    var railWidth by remember { mutableIntStateOf(toolbarSize) }
    // Where a sheet over the map rests, and where the map ends, on screen in pixels.
    var sheetTop by remember { mutableStateOf<Float?>(null) }
    val onSheetRest = remember { { top: Float? -> sheetTop = top } }
    var mapBottom by remember { mutableFloatStateOf(0f) }
    var mapHeight by remember { mutableFloatStateOf(0f) }
    var renamingMap by rememberSaveable { mutableStateOf(false) }
    var taggingMap by rememberSaveable { mutableStateOf(false) }
    // The note over a map that can only be looked at, in pixels: the map makes room for it.
    var readOnlyBannerHeight by remember { mutableIntStateOf(0) }
    var deletingMap by rememberSaveable { mutableStateOf(false) }
    var sending by rememberSaveable { mutableStateOf(false) }
    val app = LocalAppSettings.current

    val talk = rememberTalk(model) { message, action, onAction ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(message, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)

            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }

    // A presentation has the map and the microphone to itself: a talk still going ends as it starts, the idea being said left out.
    LaunchedEffect(ui.presentation != null) {
        if (ui.presentation != null) {
            talk.cancel()
        }
    }

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
                is MapEvent.Snackbar -> scope.launch {
                    snackbars.currentSnackbarData?.dismiss()
                    val result = snackbars.showSnackbar(
                        effect.message.text(resources),
                        actionLabel = if (effect.undo) resources.getString(R.string.common_undo) else null,
                        duration = if (effect.undo) SnackbarDuration.Long else SnackbarDuration.Short,
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        effect.onUndo?.invoke() ?: model.undo()
                    }
                }
                MapEvent.Deleted -> {
                    withdrawShortcut(context, model.id)
                    onClose()
                }
            }
        }
    }

    fun act(node: MapNode, action: NodeAction) {
        if (action is NodeAction.StepGoal) {
            // A step keeps the menu open, for the next one; the ring behind it moves with each.
            model.addToGoal(node.id, BigDecimal(action.by))
            return
        }

        model.show(null)
        val parent = node.parentId?.let { ui.nodes[it] }

        when (action) {
            NodeAction.AddChild -> model.addIdea(node.id)
            NodeAction.AddSibling -> model.addIdea(node.parentId ?: return, node.index + 1)
            NodeAction.Rename -> model.rename(node.id)
            NodeAction.Edit -> model.show(Panel.Editor(node.id))
            NodeAction.PickIcon -> pickingIcon = node.id
            NodeAction.PickColor -> pickingColor = node.id
            is NodeAction.SetStatus -> model.update(node.id, "status", action.status?.let(::JsonPrimitive))
            is NodeAction.SetColor -> model.update(node.id, "color", action.color?.let(::JsonPrimitive))
            is NodeAction.SetCollapsed -> model.update(node.id, "collapsed", if (action.collapsed) JsonPrimitive(true) else null)
            NodeAction.Copy -> model.copy(node.id)
            NodeAction.Paste -> model.paste(node.id)
            NodeAction.Duplicate -> model.duplicate(node.id)
            NodeAction.MoveEarlier -> model.move(node.id, node.parentId ?: return, node.index - 1)
            NodeAction.MoveLater -> model.move(node.id, node.parentId ?: return, node.index + 1)
            NodeAction.MoveOut -> {
                val grandparent = parent?.parentId ?: return
                model.move(node.id, grandparent, parent.index + 1)
            }
            NodeAction.MoveIn -> {
                val before = ui.map?.nodes?.firstOrNull { it.parentId == node.parentId && it.index == node.index - 1 } ?: return
                model.move(node.id, before.id, null)
            }
            NodeAction.Delete -> model.remove(node.id)
            is NodeAction.ChangeGoal -> {
                changingGoal = node.id
                goalChange = action.change
            }
            is NodeAction.StepGoal -> Unit
        }
    }

    // Swiping back flies up a level, with the map shrinking under the finger like any screen does.
    val backScale = remember { Animatable(1f) }
    val canGoUp = ui.path.size > 1 || ui.mode == "all"

    PredictiveBackHandler(enabled = canGoUp && !ui.editing && ui.source == null && ui.presentation == null && ui.panel == null) { events ->
        try {
            events.collect { event -> backScale.snapTo(1f - 0.06f * event.progress) }
            model.goUp()
        } catch (cancelled: CancellationException) {
            // Swiped back in: stay.
        } finally {
            scope.launch { backScale.animateTo(1f, tween(220)) }
        }
    }

    // Back while typing keeps what was typed and closes the field.
    BackHandler(enabled = ui.editing) { model.stopEditing(save = true) }
    BackHandler(enabled = ui.source != null && !ui.editing) { model.closeSource() }
    BackHandler(enabled = ui.presentation != null) { model.stopPresentation() }
    BackHandler(enabled = talk.active && ui.presentation == null) { talk.stop() }
    // Making a path, back flies up the map like always, and at its top keeps the path.
    BackHandler(enabled = ui.pathMaking && !canGoUp && !ui.editing && ui.panel == null) { model.finishPath() }

    val mapAlpha by animateFloatAsState(if (ui.ready) 1f else 0f, tween(260), label = "map")
    val style = mapStyle(ui.map?.style)
    val background = parseCssColor(ui.map?.surface) ?: style.background.takeIf { ui.map?.style != null && it.isSpecified } ?: colors.background
    // A map Orbit couldn't read has no title on the page: the one its file's first heading gives stands in.
    val title = ui.map?.title?.takeIf { it.isNotBlank() } ?: model.summary()?.title.orEmpty()

    // With nothing over it, the map holds the keyboard's focus, so its keys work as it opens and once a panel closes.
    val mapClear = ui.ready && ui.panel == null && ui.source == null && ui.presentation == null

    // The keyboard: the map's shortcuts, or while presenting, the presentation's own keys, listed.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    KeyboardShortcuts(
        if (ui.presentation != null) {
            presentingShortcuts()
        } else {
            mapShortcuts(model, ui, present = { model.startPresentation(app.presentationSeconds) }, back = { backDispatcher?.onBackPressed() })
        },
        priority = ShortcutPriority.Map,
    )

    if (ui.presentation != null) {
        PresentationMode(model, ui, background) { MapCanvas(model, Modifier.fillMaxSize().graphicsLayer { alpha = mapAlpha }) }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < ShortScreen
        val wide = maxWidth >= WideScreen || (maxWidth > maxHeight && maxWidth >= SidePanelWidth + MinimumMapWidth)
        // A map with pictures or videos keeps room for the button that shows them, whichever idea is in focus,
        // so Style and layout doesn't come and go as the map is flown through.
        val showStyle = maxWidth >= RoomForStyle + (if (ui.hasMedia) ToolbarButtonWidth else 0.dp) && !short
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val idea = ui.sun.takeIf { ui.mode == "focus" }
        val onDetails = idea?.takeIf { ui.hasDetails(it) }?.let { node -> { model.show(Panel.Details(node.id)) } }
        val onEdit = idea?.let { node -> { model.show(Panel.Editor(node.id)) } }

        val onMedia = idea?.takeIf { it.image != null || it.video != null }?.let { node ->
            { if (node.image != null) model.view(node.id, MediaKind.Picture) else model.play(node.id) }
        }

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
        val lifted = !wide && ui.panel.liftsMap()

        LaunchedEffect(lifted) {
            if (!lifted) {
                sheetTop = null
            }
        }

        val sheetRoom = sheetTop?.takeIf { lifted }?.let { top -> with(density) { (mapBottom - top).coerceAtLeast(0f).toDp() } + Clearance }
        // Where nothing covers the map's bottom edge, it still ends behind the navigation bar.
        val navigation = with(density) { bars.getBottom(density).toDp() }.value
        // The breadcrumbs' room is kept even at the top level, so flying in and out never resizes the map.
        val crumbsRoom = BreadcrumbsHeight.value
        // Making a path, the breadcrumbs float over the map on every screen: the top bar is the path's.
        val crumbsOver = !short || ui.pathMaking

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
                val panelRoom = if (talking) (with(density) { panelSide.toDp() } + TalkMargin + talkRoom).value else 0f

                if (railAtLeft) {
                    MapInsets(top = bannerRoom.value, left = railRoom.value, right = panelRoom, bottom = navigation)
                } else {
                    MapInsets(top = bannerRoom.value, left = panelRoom, right = railRoom.value, bottom = navigation)
                }
            }
            else -> MapInsets(top = topRoom, bottom = (toolbarRoom + if (talking) talkRoom else 0.dp).value)
        }

        LaunchedEffect(insets) { model.setInsets(insets) }

        Scaffold(
            containerColor = background,
            contentColor = colors.onBackground,
            topBar = {
                if (ui.source != null) {
                    SourceTopBar(model, ui.source, background)
                } else if (ui.pathMaking) {
                    PathTopBar(ui, background, onSearch = { model.show(Panel.Search) }, onClear = model::clearPath, onDone = model::finishPath)
                } else {
                    MapTopBar(
                        title = title,
                        ui = ui,
                        background = background,
                        onBack = onClose,
                        onUndo = model::undo,
                        onRedo = model::redo,
                        onRename = if (ui.readOnly == null) ({ renamingMap = true }) else null,
                        onTags = if (ui.readOnly == null) ({ taggingMap = true }) else null,
                        onSource = model::openSource,
                        onPresent = { model.startPresentation(app.presentationSeconds) },
                        onPath = model::startPath,
                        onRehearse = { model.startPresentation(0, fromStart = true, rehearse = true) },
                        onHistory = model::openHistory,
                        onExport = { model.show(Panel.Export) },
                        onSend = { sending = true },
                        onDelete = { deletingMap = true },
                        onFocus = { model.focus(it.id) },
                        onStyle = if (showStyle || ui.readOnly != null) null else ({ model.show(Panel.Style) }),
                        compact = short,
                        idea = idea,
                        onMedia = if (short) onMedia else null,
                        onDetails = if (short) onDetails else null,
                        onEdit = if (short) onEdit else null,
                        labeledPresent = maxWidth >= LabeledPresentWidth,
                    )
                }
            },
            snackbarHost = {
                // Messages come up above the toolbar, the path's strip or the keyboard, rather than under them.
                val lift = when {
                    ui.source != null || !crumbsOver || ui.editing -> 0.dp
                    ui.pathMaking -> with(density) { pathBarHeight.toDp() } + ToolbarMargin
                    else -> 88.dp
                }

                SnackbarHost(
                    snackbars,
                    Modifier
                        .padding(bottom = lift)
                        .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                )
            },
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .consumeWindowInsets(padding),
            ) {
                val sourceBeside = ui.source != null && wide

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        // The map makes room for the keyboard at once, so the field being typed in stays above it.
                        Box(
                            Modifier
                                .weight(if (ui.source != null && !wide) 0.42f else 1f)
                                .fillMaxWidth()
                                .then(if (ui.source == null) Modifier.windowInsetsPadding(WindowInsets.imeAnimationTarget) else Modifier),
                        ) {
                            MapCanvas(
                                model,
                                keyboardFocus = mapClear,
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
                            MapOverlays(model, ui, ::act, hint = !talk.active, onBannerHeight = { readOnlyBannerHeight = it })

                            // On a short screen the breadcrumbs are in the top bar instead, unless it's the path's.
                            if (crumbsOver && ui.source == null) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = ui.path.size > 1 && ui.mode == "focus",
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

                    if (ui.source == null) {
                        MapToolbar(
                            ui = ui,
                            visible = !ui.editing && ui.ready && !ui.pathMaking && ui.panel !is Panel.History,
                            vertical = short,
                            idea = idea,
                            onOutline = { model.show(Panel.Outline) },
                            onSearch = { model.show(Panel.Search) },
                            onToggleView = model::toggleView,
                            onStyle = if (showStyle && ui.readOnly == null) ({ model.show(Panel.Style) }) else null,
                            onMedia = if (short) null else onMedia,
                            onDetails = if (short) null else onDetails,
                            onEdit = if (short || ui.readOnly != null) null else onEdit,
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

                if (sourceBeside) {
                    VerticalDivider()
                    SourceEditor(model, ui.source, ui.readOnly, Modifier.width(SidePanelWidth + 80.dp).fillMaxHeight(), clearOfBars.calculateBottomPadding())
                } else if (wide && ui.panel.showsBeside()) {
                    VerticalDivider()

                    Surface(color = colors.surfaceContainerLow, modifier = Modifier.width(SidePanelWidth).fillMaxHeight()) {
                        MapPanels(model, ui, sheet = false, onPickIcon = { pickingIcon = it }, onPickColor = { pickingColor = it })
                    }
                }
            }
        }

        if (!wide || !ui.panel.showsBeside()) {
            CompositionLocalProvider(LocalSheetRest provides onSheetRest) {
                MapPanels(model, ui, sheet = true, onPickIcon = { pickingIcon = it }, onPickColor = { pickingColor = it })
            }
        }
    }

    pickingIcon?.let { nodeId ->
        val node = ui.nodes[nodeId]
        EmojiPickerSheet(
            title = stringResource(R.string.map_icon_for, node?.text.orEmpty()),
            hasIcon = node?.icon != null,
            onPick = { emoji ->
                pickingIcon = null
                model.update(nodeId, "icon", emoji?.let(::JsonPrimitive))
            },
            onDismiss = { pickingIcon = null },
        )
    }

    pickingColor?.let { nodeId ->
        val node = ui.nodes[nodeId]

        if (node == null) {
            pickingColor = null
        } else {
            ColorPickerDialog(
                title = stringResource(R.string.map_color_for, node.text),
                label = node.label(stringResource(R.string.common_untitled)),
                initial = parseCssColor(node.color) ?: parseCssColor(node.drawnColor) ?: OrbitPalette.first(),
                onPick = { hex ->
                    pickingColor = null
                    model.update(nodeId, "color", JsonPrimitive(hex))
                },
                onDismiss = { pickingColor = null },
            )
        }
    }

    val goalNode = changingGoal?.let { ui.nodes[it] }
    val goal = goalNode?.let(Goal::of)
    val change = goalChange

    if (goalNode != null && goal != null && change != null) {
        GoalAmountDialog(
            goal = goal,
            change = change,
            onDone = { amount ->
                changingGoal = null
                model.addToGoal(goalNode.id, goal.signed(change, amount))
            },
            onDismiss = { changingGoal = null },
        )
    }

    if (renamingMap) {
        TitleDialog(
            heading = stringResource(R.string.map_rename_map),
            confirm = stringResource(R.string.common_rename),
            initial = ui.map?.root?.text ?: title,
            onConfirm = { text ->
                renamingMap = false
                ui.map?.root?.let { root -> model.update(root.id, "text", JsonPrimitive(text)) }
            },
            onDismiss = { renamingMap = false },
        )
    }

    if (taggingMap) {
        // Every tag the maps have, to pick from: the map's own come from the page, which writes them.
        val summaries by LocalContext.current.container.maps.maps.collectAsStateWithLifecycle()
        val known = remember(summaries) { tagCounts(summaries.orEmpty()).map { it.name } }

        TagsSheet(
            title = title,
            tags = ui.map?.options?.tags.orEmpty(),
            known = known,
            onChange = { tags -> model.setOptions(mapOf("tags" to JsonArray(tags.map(::JsonPrimitive)))) },
            onDismiss = { taggingMap = false },
        )
    }

    if (sending) {
        SendSheet(model.id, title, onDismiss = { sending = false })
    }

    ui.viewing?.let { viewing ->
        MediaViewer(
            items = viewing.items,
            start = viewing.start,
            source = model::mediaModel,
            onPlay = model::play,
            onClosed = model::closeViewer,
        )
    }

    // After the pictures, so a video started from them plays over them, and they're still there after it.
    ui.playing?.let { item -> VideoScreen(item, resolve = model::mediaModel, onClosed = model::stopPlaying) }

    ui.rehearsal?.let { rehearsal ->
        RehearsalSheet(
            rehearsal,
            ui.nodes,
            onAgain = {
                model.closeRehearsal()
                model.startPresentation(0, fromStart = true, rehearse = true)
            },
            onDismiss = model::closeRehearsal,
        )
    }

    if (deletingMap) {
        AlertDialog(
            onDismissRequest = { deletingMap = false },
            title = {
                EscapeCloses { deletingMap = false }
                Text(stringResource(R.string.map_delete_dialog_title, title))
            },
            text = { Text(pluralStringResource(R.plurals.map_delete_dialog_body, TRASH_DAYS, TRASH_DAYS)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingMap = false
                    model.deleteMap()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingMap = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** Sheets that stop halfway over the map, with the map lifted into the space above them. */
private fun Panel?.liftsMap(): Boolean =
    this is Panel.Editor || this is Panel.Details || this is Panel.Outline || this is Panel.Style || this is Panel.History

/** Panels that sit beside the map on wide screens; menus and exports stay over it. */
private fun Panel?.showsBeside(): Boolean =
    this is Panel.Outline || this is Panel.Editor || this is Panel.Details || this is Panel.Search || this is Panel.Style || this is Panel.History

/** The text, in the app's language. */
@Composable
fun ShownText.text(): String = text(LocalResources.current)

/** Over the map: the long-press menu, the first idea's hint where `hint` allows it, problems, and the way back after the page stopped. */
@Composable
private fun MapOverlays(model: MapViewModel, ui: MapUi, act: (MapNode, NodeAction) -> Unit, hint: Boolean, onBannerHeight: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        if (!ui.ready && !ui.stopped && ui.error == null) {
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
                    onDismiss = { model.show(null) },
                    onAction = { action -> act(node, action) },
                )
            }
        }

        val root = ui.map?.root

        AnimatedVisibility(
            visible = hint && ui.ready && ui.readOnly == null && root != null && root.childCount == 0 && !ui.editing && ui.mode == "focus",
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

        if (ui.stopped) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.map_stopped),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = model::reload) { Text(stringResource(R.string.map_load_it_again)) }
            }
        }

        ReadOnlyBanner(
            model,
            ui,
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = ReadOnlyBannerTop)
                .widthIn(max = 560.dp)
                .onSizeChanged { onBannerHeight(it.height) },
        )
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
        val problem = ui.readProblem

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

/** How far below the top of the map the note over a map that can't change sits: below the breadcrumbs. */
private val ReadOnlyBannerTop = 56.dp

/** Text ending as a sentence does, for another to follow it. */
private fun sentence(text: String): String = text.trim().let { if (it.endsWith('.') || it.endsWith('!') || it.endsWith('?')) it else "$it." }

/** Orbit's page in the phone's app store, when there's one that opens it. */
private fun orbitStorePage(context: android.content.Context): android.content.Intent? =
    android.content.Intent(android.content.Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
    onSource: () -> Unit,
    onPresent: () -> Unit,
    onPath: () -> Unit,
    onRehearse: () -> Unit,
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
) {
    val crumbs = ui.path.size > 1 && ui.mode == "focus"
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
            if (idea != null && onEdit != null) {
                AnimatedVisibility(onMedia != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    MediaButton(idea, onClick = { onMedia?.invoke() })
                }

                AnimatedVisibility(onDetails != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    val holds = stringResource(R.string.map_what_the_idea_holds, idea.text)

                    Hint(holds) {
                        IconButton(onClick = { onDetails?.invoke() }) {
                            Icon(Icons.Outlined.Description, contentDescription = holds)
                        }
                    }
                }

                val edit = stringResource(R.string.map_edit_the_idea, idea.text)

                Hint(edit, MapKeys.Edit) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = edit) }
                }

                VerticalDivider(Modifier.padding(horizontal = 6.dp).height(24.dp))
            }

            val undo = stringResource(R.string.common_undo)

            Hint(undo, MapKeys.Undo) {
                IconButton(onClick = onUndo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = undo) }
            }

            val redo = stringResource(R.string.common_redo)

            Hint(redo, MapKeys.Redo) {
                IconButton(onClick = onRedo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = redo) }
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

            MapMenu(ui.presentPath.count { it in ui.nodes }, onPath, onRehearse, onHistory, onRename, onTags, onSource, onExport, onSend, onDelete, onStyle)
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
    onHistory: () -> Unit,
    onRename: (() -> Unit)?,
    onTags: (() -> Unit)?,
    onSource: () -> Unit,
    onExport: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
    onStyle: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.map_more)) }

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

            val shortcuts = LocalShortcuts.current

            if (shortcuts != null && hasHardwareKeyboard()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_keyboard_shortcuts)) },
                    leadingIcon = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
                    trailingIcon = { Text(ShortcutListKeys.caps.joinToString("+"), style = MaterialTheme.typography.labelLarge) },
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
            FilterChip(
                selected = current,
                onClick = {
                    if (!current) {
                        onPick(node)
                    }
                },
                label = { Text(node.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

        val buttons = @Composable { ToolbarButtons(ui, vertical, idea, onOutline, onSearch, onToggleView, onStyle, onMedia, onDetails, onEdit) }

        if (onAdd == null) {
            if (vertical) {
                VerticalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) { buttons() }
            } else {
                HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) { buttons() }
            }
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

    if (ui.mode == "all") {
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

    if (idea != null && onEdit != null) {
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

        val edit = stringResource(R.string.map_edit_the_idea, idea.text)

        Hint(edit, MapKeys.Edit) {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = edit) }
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
