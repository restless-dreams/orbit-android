package com.hyuchia.orbit.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.OrbitSnackbarHost
import kotlinx.coroutines.launch

/**
 * Hears where a sheet over the map is heading as soon as it starts to move:
 * the top edge it will rest at, on screen, in pixels, or null when it's on
 * its way out. The map glides into the room above it alongside the sheet.
 */
val LocalSheetRest = staticCompositionLocalOf<(Float?) -> Unit> { {} }

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

/** A sheet's height, and the height and top on screen of the space it slides up in. */
private data class SheetSpace(val sheet: Float, val height: Float, val top: Float)

/**
 * At the foot of a window `height` pixels tall, across its middle: where a
 * phone's messages come up. A popup is told the window's size less the
 * system's bars, but is placed in the whole window, so its foot is the
 * window's own height.
 */
private class WindowFoot(private val height: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset((windowSize.width - popupContentSize.width) / 2, height - popupContentSize.height)
}

/**
 * The map's messages over a sheet, at the foot of the screen, as they come
 * up on the map: a box checked in a note's checklist, say, with its Undo. A
 * window of their own, above the sheet's, so they can be read and pressed.
 */
@Composable
private fun SheetMessages(messages: SnackbarHostState) {
    // The sheet's window reaches under the navigation bar and the keyboard; the messages' own doesn't know them.
    val bar = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
    val height = LocalWindowInfo.current.containerSize.height
    val foot = remember(height) { WindowFoot(height) }

    Popup(popupPositionProvider = foot, properties = PopupProperties(focusable = false)) {
        OrbitSnackbarHost(messages, Modifier.padding(bottom = bar))
    }
}

/** The open panel: as a bottom sheet on phones, or beside the map on wide screens (`sheet` false). */
@Composable
fun MapPanels(model: MapViewModel, ui: MapUi, sheet: Boolean, onPickIcon: (String) -> Unit, onPickColor: (String) -> Unit, onPickDate: (DatePick) -> Unit) {
    when (val panel = ui.panel) {
        is Panel.Editor -> ui.nodes[panel.id]?.let { node -> NodeEditor(model, ui, node, panel.opened, sheet, onPickIcon, onPickColor, onPickDate, afterSketch = panel.afterSketch) }
        is Panel.Details -> ui.nodes[panel.id]?.let { node -> DetailsPanel(model, ui, node, sheet) }
        Panel.Outline -> OutlinePanel(model, ui, sheet)
        Panel.Search -> SearchPanel(model, ui, sheet)
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

            Column(Modifier.fillMaxWidth().semantics { paneTitle = title }) {
                PanelHeader(title, onClose = null, actions)

                CompositionLocalProvider(LocalSheetRaise provides raise) {
                    content()
                }
            }

            LocalSheetMessages.current?.let { messages -> SheetMessages(messages) }
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
