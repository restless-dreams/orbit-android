package com.hyuchia.orbit.map

import androidx.compose.material3.DividerDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.ui.MinimumMapWidth
import com.hyuchia.orbit.ui.WideScreen
import com.hyuchia.orbit.ui.isShort

/** A panel beside the map is this wide; [MapLayout.wide] says when one goes beside it. */
internal val SidePanelWidth = 380.dp

/** The map's text, beside the map, is wider than a panel by up to this much, as far as the map keeps its width ([MapLayout.sourceWidth]). */
internal val SourceExtraWidth = 80.dp

/** Under this width the toolbar leaves out Style and layout, which moves into the map's menu, so the idea's buttons fit. */
private val RoomForStyle = 400.dp

/** A button in the toolbar takes this much of its width. */
private val ToolbarButtonWidth = 48.dp

/** From this width the top bar's Present button says its name, and the bar has room for Redo and the lock ([MapLayout.roomyTopBar]). */
private val LabeledPresentWidth = 600.dp

/**
 * How the map's screen lays itself out, for a screen of its size and what's
 * open on it: what stands beside the map, what goes over it, and where the
 * tools go ("Phones and tablets, both ways up" in AGENTS.md).
 */
internal data class MapLayout(
    /** A phone on its side: the map's tools stand in a rail at its side, and the idea's buttons join the top bar. */
    val short: Boolean,

    /** Room for a panel or the map's text beside the map, rather than over or under it. */
    val wide: Boolean,

    /** Style and layout has a button in the toolbar, rather than a place in the map's menu. */
    val styleInToolbar: Boolean,

    /** A sheet that stops halfway over the map lifts the map into the room above it. */
    val lifted: Boolean,

    /** The map's text stands beside the map. */
    val sourceBeside: Boolean,

    /** The open panel stands beside the map, rather than over it as a sheet. */
    val panelBeside: Boolean,

    /**
     * How wide the map's text is beside the map, its divider left out: wider
     * than a panel where the map keeps its width beside it, and never
     * narrower than a panel.
     */
    val sourceWidth: Dp,

    /** How wide what stands beside the map is, its divider included, or none. */
    val beside: Dp,

    /** The breadcrumbs float over the map, rather than sit in the top bar: everywhere but a short screen, and while a path is made. */
    val crumbsOver: Boolean,

    /** The top bar's Present button says its name. */
    val labeledPresent: Boolean,

    /**
     * The top bar has room for Redo before anything is undone, and for a
     * private map's lock: a map a tablet's width has. A phone's, either way
     * up, and one a phone's width beside the list, leave that room to the
     * map's name.
     */
    val roomyTopBar: Boolean,
)

/**
 * The map screen's layout on a screen `width` by `height`, with what `ui`
 * has open. What stands beside the map leaves it at least a phone's width
 * ([MinimumMapWidth]), and the toolbar, which floats over the map, fits the
 * map's width beside it rather than the screen's.
 */
internal fun mapLayout(width: Dp, height: Dp, ui: MapUi): MapLayout {
    val short = isShort(height)
    val divider = DividerDefaults.Thickness
    val wide = width >= WideScreen || (width > height && width - SidePanelWidth - divider >= MinimumMapWidth)
    // The text takes what the map leaves, up to its own width: on a screen just wide enough for a panel, a panel's.
    val sourceWidth = (width - divider - MinimumMapWidth).coerceIn(SidePanelWidth, SidePanelWidth + SourceExtraWidth)
    val sourceBeside = ui.source != null && wide
    val panelBeside = wide && ui.panel.showsBeside()

    val beside = when {
        sourceBeside -> sourceWidth + divider
        panelBeside -> SidePanelWidth + divider
        else -> 0.dp
    }

    // A map with pictures or videos keeps room for the button that shows them, whichever idea is in focus,
    // so Style and layout doesn't come and go as the map is flown through.
    val styleRoom = RoomForStyle + (if (ui.hasMedia) ToolbarButtonWidth else 0.dp) + (if (ui.hasSounds) ToolbarButtonWidth else 0.dp)

    return MapLayout(
        short = short,
        wide = wide,
        styleInToolbar = width - beside >= styleRoom && !short,
        lifted = !wide && ui.panel.liftsMap(),
        sourceBeside = sourceBeside,
        panelBeside = panelBeside,
        sourceWidth = sourceWidth,
        beside = beside,
        crumbsOver = !short || ui.pathMaking,
        labeledPresent = width >= LabeledPresentWidth,
        roomyTopBar = width >= LabeledPresentWidth && !short,
    )
}

/** Sheets that stop halfway over the map, with the map lifted into the space above them. */
internal fun Panel?.liftsMap(): Boolean =
    this is Panel.Editor || this is Panel.Details || this is Panel.Outline || this is Panel.Style || this is Panel.History

/** Panels that sit beside the map on wide screens; menus and exports stay over it. */
internal fun Panel?.showsBeside(): Boolean =
    this is Panel.Outline || this is Panel.Editor || this is Panel.Details || this is Panel.Search || this is Panel.Style || this is Panel.History

/**
 * How far messages keep from the map screen's end, to sit over the map: what's
 * `beside` the map (a panel or its text, none for 0), less the bars at the
 * screen's end the scaffold keeps them from already (`scaffoldEnd`), and with
 * something beside, the room the `rail` takes at the map's side.
 */
internal fun besideRoom(beside: Dp, scaffoldEnd: Dp, rail: Dp): Dp = if (beside > 0.dp) (beside - scaffoldEnd).coerceAtLeast(0.dp) + rail else 0.dp
