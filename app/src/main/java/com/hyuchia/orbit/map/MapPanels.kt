package com.hyuchia.orbit.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.privacy.privateSheetProperties
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.OrbitSnackbarHost
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hears where a sheet over the map is heading as soon as it starts to move:
 * the top edge it will rest at, on screen, in pixels, or null when it's on
 * its way out. The map glides into the room above it alongside the sheet.
 */
val LocalSheetRest = staticCompositionLocalOf<(Float?) -> Unit> { {} }

/**
 * Where a sheet over the map rests, as it says ([LocalSheetRest]): its top on
 * screen, in pixels, or null with no sheet there. A sheet taken away at once,
 * rather than slid down, as the search's is when a result is picked, never
 * says it's on its way out: once no panel is open over the map as a sheet
 * (`over` false, [MapLayout.panelOver]), there's none, and the map's messages,
 * which keep out from under a sheet, show over the map again.
 */
@Composable
fun rememberSheetTop(over: Boolean): MutableState<Float?> {
    val top = remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(over) {
        if (!over) {
            top.value = null
        }
    }

    return top
}

/**
 * Raises a sheet that stops partway to its full height: for a field low in
 * it, which the keyboard would otherwise cover as it comes up. Beside the
 * map, there's nothing to raise.
 */
val LocalSheetRaise = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The map's messages, while a sheet is over the map: the map's own place for
 * them is under the sheet, out of sight and out of reach, so the sheet shows
 * them over itself (see [SheetMessages]).
 */
val LocalSheetMessages = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * The field a panel beside the map keeps above the keyboard while it's in
 * use ([keptAboveKeyboard]): whether it has the focus, and where it is in the
 * panel, in pixels from the panel's top.
 */
@Stable
class KeptField {
    var focused by mutableStateOf(false)
        internal set

    var top by mutableFloatStateOf(0f)
        internal set

    var bottom by mutableFloatStateOf(0f)
        internal set

    /** The panel's own place, which the field's is measured from. */
    internal var panel: LayoutCoordinates? = null
}

/** The field the panel beside the map keeps above the keyboard; none over the map, where a sheet rises with the keyboard by itself. */
val LocalKeptField = staticCompositionLocalOf<KeptField?> { null }

/**
 * Marks the field a panel keeps in view above the keyboard while it's in
 * use, `kept` ([LocalKeptField]): beside the map, the panel rises over the
 * top bar for it ([PanelBeside]).
 */
fun Modifier.keptAboveKeyboard(kept: KeptField?): Modifier = if (kept == null) this else this then KeptFieldElement(kept)

private data class KeptFieldElement(val kept: KeptField) : ModifierNodeElement<KeptFieldNode>() {
    override fun create() = KeptFieldNode(kept)

    override fun update(node: KeptFieldNode) {
        if (node.kept !== kept) {
            node.kept.focused = false
            node.kept = kept
        }
    }
}

/** Says where the field is in the panel, and whether it has the focus: no longer once it's gone, as when another panel takes its place. */
private class KeptFieldNode(var kept: KeptField) :
    Modifier.Node(),
    FocusEventModifierNode,
    GlobalPositionAwareModifierNode {
    override fun onFocusEvent(focusState: FocusState) {
        kept.focused = focusState.hasFocus
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        kept.panel?.takeIf { it.isAttached && coordinates.isAttached }?.let { panel ->
            val bounds = panel.localBoundingBoxOf(coordinates, clipBounds = false)
            kept.top = bounds.top
            kept.bottom = bounds.bottom
        }
    }

    override fun onDetach() {
        kept.focused = false
    }
}

/** Space between the field a panel keeps in view and the keyboard's top. */
private val KeyboardGap = 4.dp

/**
 * A panel beside the map, with the divider at its side. While the keyboard
 * is up for its field ([keptAboveKeyboard]) and would cover it, as on a
 * phone on its side, the panel rises over the top bar, `topBar` tall under
 * the status bar, as far as the field needs ([panelRise]), gliding up with
 * the keyboard and back down as it goes, as a search covers the top bar.
 * `onRise` hears whether it's risen, so what holds it draws it over the top
 * bar; the map stays as it is under it.
 */
@Composable
fun PanelBeside(topBar: Dp, onRise: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val kept = remember { KeptField() }
    val keyboard = WindowInsets.ime
    val onRiseNow by rememberUpdatedState(onRise)
    // How far the panel's foot is from the window's: the keyboard covers the panel only once it's taller than that.
    var below by remember { mutableIntStateOf(0) }

    // Gone while risen, as when the source editor takes its place, it lets what held it go back under the top bar.
    DisposableEffect(Unit) {
        onDispose { onRiseNow(false) }
    }

    Row(
        modifier
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                val root = coordinates.findRootCoordinates()
                below = (root.size.height - (coordinates.positionInRoot().y + coordinates.size.height)).roundToInt().coerceAtLeast(0)
            }
            .layout { measurable, constraints ->
                val height = constraints.maxHeight
                val covered = (keyboard.getBottom(this) - below).coerceAtLeast(0)

                val rise = if (kept.focused && covered > 0 && constraints.hasBoundedHeight) {
                    panelRise(kept.top, kept.bottom, room = (height - covered).toFloat(), topBar = topBar.toPx(), gap = KeyboardGap.toPx()).roundToInt()
                } else {
                    0
                }

                onRiseNow(rise > 0)
                val placeable = measurable.measure(if (rise > 0) constraints.copy(minHeight = height + rise, maxHeight = height + rise) else constraints)
                layout(placeable.width, if (rise > 0) height else placeable.height) { placeable.place(0, -rise) }
            },
    ) {
        VerticalDivider()

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(SidePanelWidth).fillMaxHeight()) {
            Box(Modifier.onGloballyPositioned { kept.panel = it }) {
                CompositionLocalProvider(LocalKeptField provides kept) {
                    content()
                }
            }
        }
    }
}

/** A sheet's height, and the height and top on screen of the space it slides up in. */
private data class SheetSpace(val sheet: Float, val height: Float, val top: Float)

/**
 * A sheet's content with the map's messages over it, at the foot of the
 * screen, as they come up on the map: a box checked in a note's checklist,
 * say, with its Undo. They're in the sheet's own window, after its content,
 * so a keyboard's Tab reaches their buttons and TalkBack reads them with the
 * sheet; on a sheet stopped partway, whose foot is below the screen's, they
 * rise to the screen's, above the navigation bar and the keyboard.
 */
@Composable
private fun WithSheetMessages(messages: SnackbarHostState, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val below = WindowInsets.navigationBars.union(WindowInsets.ime).getBottom(density)
    // How far the content reaches below the screen's foot, in pixels, for the messages to rise by.
    var underneath by remember { mutableIntStateOf(0) }

    Box(
        Modifier.onGloballyPositioned { coordinates ->
            val bottom = coordinates.positionInWindow().y + coordinates.size.height
            underneath = (bottom - (window - below)).roundToInt().coerceAtLeast(0)
        },
    ) {
        content()
        OrbitSnackbarHost(messages, Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -underneath) })
    }
}

/**
 * The open panel: as a bottom sheet on phones, or beside the map on wide
 * screens (`sheet` false). The map's `search` and `outline` are kept above it,
 * so each goes on as it was as the panel moves between the two when the
 * screen turns.
 */
@Composable
fun MapPanels(
    model: MapViewModel,
    ui: MapUi,
    sheet: Boolean,
    search: MapSearch,
    outline: MapOutline,
    onPickIcon: (String) -> Unit,
    onPickColor: (String) -> Unit,
    onPickDate: (DatePick) -> Unit,
) {
    when (val panel = ui.panel) {
        is Panel.Editor -> ui.nodes[panel.id]?.let { node -> NodeEditor(model, ui, node, panel.opened, sheet, onPickIcon, onPickColor, onPickDate, afterSketch = panel.afterSketch) }
        is Panel.Details -> ui.nodes[panel.id]?.let { node -> DetailsPanel(model, ui, node, sheet) }
        Panel.Outline -> OutlinePanel(model, ui, sheet, outline)
        Panel.Search -> SearchPanel(model, search, sheet)
        Panel.Style -> StylePanel(model, ui, sheet)
        Panel.Export -> ExportPanel(model, ui)
        Panel.History -> HistoryPanel(model, ui, sheet)
        is Panel.Menu, null -> Unit
    }
}

/**
 * A panel's frame: a bottom sheet with a title, or a column with a title and
 * a close button beside the map. `partial` lets the sheet stop halfway, so the
 * map stays in view; `scrim` false keeps the map fully visible behind it.
 * Either way, screen readers hear the title as the panel opens, and as one
 * takes another's place, and find it among the headings, as on a sheet's
 * page ([com.hyuchia.orbit.ui.SheetPage]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelFrame(
    title: String,
    sheet: Boolean,
    onDismiss: () -> Unit,
    partial: Boolean = false,
    scrim: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (sheet) {
        val state = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = if (partial) setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded) else setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        val onRest = LocalSheetRest.current
        var space by remember { mutableStateOf<SheetSpace?>(null) }

        LaunchedEffect(state) {
            var shown = false

            snapshotFlow { state.targetValue to space }.collect { (target, space) ->
                if (target == SheetValue.Hidden) {
                    // Only a sheet on its way out, not one that hasn't come up yet.
                    if (shown) {
                        onRest(null)
                    }
                } else if (space != null) {
                    shown = true
                    // Where Material rests a sheet: no higher than halfway when it stops partway, its whole height up when expanded.
                    val showing = if (target == SheetValue.PartiallyExpanded) minOf(space.height / 2, space.sheet) else minOf(space.height, space.sheet)
                    onRest(space.top + space.height - showing)
                }
            }
        }

        val scope = rememberCoroutineScope()

        val raise = remember(state) {
            {
                if (state.currentValue == SheetValue.PartiallyExpanded) {
                    scope.launch { state.expand() }
                }

                Unit
            }
        }

        ModalBottomSheet(
            properties = privateSheetProperties(),
            onDismissRequest = onDismiss,
            sheetState = state,
            scrimColor = if (scrim) MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f) else Color.Transparent,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                coordinates.parentLayoutCoordinates?.let { parent ->
                    space = SheetSpace(coordinates.size.height.toFloat(), parent.size.height.toFloat(), parent.positionOnScreen().y)
                }
            },
        ) {
            EscapeCloses(onDismiss)

            val sheetContent = @Composable {
                Column(Modifier.fillMaxWidth().semantics { paneTitle = title }) {
                    PanelHeader(title, onClose = null, actions)

                    CompositionLocalProvider(LocalSheetRaise provides raise) {
                        content()
                    }
                }
            }

            when (val messages = LocalSheetMessages.current) {
                null -> sheetContent()
                else -> WithSheetMessages(messages, sheetContent)
            }
        }
    } else {
        // A field in the panel lets go of the focus before the panel closes: Compose would otherwise
        // take the focus back as the field leaves, from the map that just got it.
        val focusManager = LocalFocusManager.current

        val dismiss = {
            focusManager.clearFocus(force = true)
            onDismiss()
        }

        // Beside the map, Back closes the panel first, as it does a sheet.
        BackHandler(onBack = dismiss)

        Column(Modifier.fillMaxSize().semantics { paneTitle = title }) {
            PanelHeader(title, onClose = dismiss, actions)
            content()
        }
    }
}

@Composable
private fun PanelHeader(title: String, onClose: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = if (onClose != null) 12.dp else 0.dp, bottom = 4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()

        if (onClose != null) {
            val close = stringResource(R.string.common_close)

            Hint(close) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = close) }
            }
        }
    }
}
