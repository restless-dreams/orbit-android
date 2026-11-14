package com.hyuchia.orbit.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayAndNight
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hyuchia.orbit.ACTION_OPEN_MAP
import com.hyuchia.orbit.EXTRA_MAP_ID
import com.hyuchia.orbit.EXTRA_OPEN_MARK
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.OpenMark
import com.hyuchia.orbit.R
import com.hyuchia.orbit.capture.captureIntent
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.home.describe
import com.hyuchia.orbit.theme.mapStyle
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.theme.orbitColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The map a widget shows, in its Glance state; without one, it shows the pinned map, or the last opened. */
val WidgetMapKey = stringPreferencesKey("map")

// The sizes a widget is drawn for. The launcher shows the one closest to the widget's size among
// those that fit in it, so each layout has sizes near the cells it's meant for.
/** One cell on a phone: the map's picture and its name. */
internal val OneCell = DpSize(48.dp, 48.dp)
/** A roomier cell, such as a tablet's: + joins the picture, to catch an idea into the map. */
internal val RoomyCell = DpSize(110.dp, 110.dp)
/** A row of two cells: the picture beside the map's name and progress. */
internal val ShortRow = DpSize(150.dp, 64.dp)
/** A row of three cells or more: + joins them. */
internal val LongRow = DpSize(240.dp, 64.dp)
/** Two rows or more: the name and + above the picture, the progress below. */
internal val TwoRows = DpSize(160.dp, 180.dp)
/** Two rows as long as a long row, so a wide widget of two rows isn't taken for a row. */
internal val TwoLongRows = DpSize(240.dp, 180.dp)
private val WidgetSizes = setOf(OneCell, RoomyCell, ShortRow, LongRow, TwoRows, TwoLongRows)

/**
 * A map on the home screen, as Orbit draws it: its picture, its name and
 * how many of its tasks are done. A tap opens it; + catches an idea into it
 * without opening it.
 */
class MapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes)
    override val previewSizeMode = SizeMode.Responsive(WidgetSizes)
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.container
        val maps = loadedMaps(context)
        val settings = app.settings.settings.first()

        // The picture is ready before the widget is drawn, so it never opens on a placeholder; a private map shows none.
        shownMap(maps, getAppWidgetState<Preferences>(context, id)[WidgetMapKey])?.takeUnless { it.metadata.isPrivate }?.let {
            picture(context, it, homeScreenIsDark(context), settings.dynamicColor)
        }

        provideContent {
            val chosen = currentState(WidgetMapKey)
            val all by app.maps.maps.collectAsState()
            // The wallpaper's colors, turned on or off while the widget shows, change its picture at once.
            val dynamicColor = app.settings.settings.collectAsState(settings).value.dynamicColor
            val map = all?.let { shownMap(it, chosen) }
            val glanceContext = LocalContext.current
            val dark = homeScreenIsDark(glanceContext)

            val shownPicture = map?.takeUnless { it.metadata.isPrivate }

            val picture by produceState(shownPicture?.let { decodedPicture(glanceContext, it, dark, dynamicColor) }, shownPicture?.id, shownPicture?.pictureKey, dark, dynamicColor) {
                value = shownPicture?.let { picture(glanceContext, it, dark, dynamicColor) }
            }

            GlanceTheme(widgetColors(glanceContext, dynamicColor)) {
                MapWidgetContent(map, picture)
            }
        }
    }

    /** What the launcher's list of widgets shows (Android 15 and later): the map a new widget shows, as it shows it. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val map = shownMap(loadedMaps(context), null)
        previewShows(map?.id)
        val dynamicColor = context.container.settings.settings.first().dynamicColor
        val picture = map?.takeUnless { it.metadata.isPrivate }?.let { picture(context, it, homeScreenIsDark(context), dynamicColor) }

        provideContent {
            GlanceTheme(widgetColors(context, dynamicColor)) {
                MapWidgetContent(map, picture)
            }
        }
    }
}

/** One map's widget with its picture at hand: what the launcher shows while asking where it goes. */
private class MapWidgetPreview(private val map: MapSummary, private val picture: Bitmap?, private val dynamicColor: Boolean) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(widgetColors(context, dynamicColor)) {
                MapWidgetContent(map, picture)
            }
        }
    }
}

class MapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MapWidget()
}

/**
 * Draws every map widget again, after a map changed; nothing to do without
 * any on the home screen. A drawing that fails (Glance's work, or its store,
 * refusing it) is logged, and the next change draws them again: it never stops
 * the widgets following the maps.
 */
suspend fun updateMapWidgets(context: Context): Boolean = updateMapWidgets {
    if (GlanceAppWidgetManager(context).getGlanceIds(MapWidget::class.java).isNotEmpty()) {
        MapWidget().updateAll(context)
    }
}

/** Draws the widgets with `draw`, as [updateMapWidgets] does: a failure is logged, never thrown. Whether they were drawn. */
internal suspend fun updateMapWidgets(draw: suspend () -> Unit): Boolean = try {
    draw()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failed: Exception) {
    Logs.warn("Widgets", "Couldn't draw the widgets again", failed)
    false
}

/** How long the widgets wait to be drawn again after a drawing failed, each time it fails again. */
val WIDGET_RETRIES = listOf(5.seconds, 30.seconds, 2.minutes)

/**
 * Keeps the home screen's widgets drawn with `draw`, which says whether it
 * drew them, as the maps change ([follow]). A drawing that fails is tried
 * again after each of `retries` until one takes, so a map made private never
 * keeps its name and picture on a widget because one drawing failed; the next
 * change stops the retries and draws them itself. Should the last fail too,
 * they're drawn again as Orbit comes back on screen ([cameBack]).
 */
class WidgetRedrawing(private val draw: suspend () -> Boolean, private val retries: List<Duration> = WIDGET_RETRIES) {
    /** Whether the widgets may show what changed since they were last drawn: the last drawing failed. */
    private val behind = AtomicBoolean(false)

    /** Draws the widgets each time `redraws` says to ([widgetRedraws]), until it's cancelled. */
    suspend fun follow(redraws: Flow<Unit>) = redraws.collectLatest { redraw() }

    /** Draws the widgets now, and again after each of the retries while it fails. */
    suspend fun redraw() {
        for (wait in listOf(Duration.ZERO) + retries) {
            delay(wait)

            if (drawn()) {
                return
            }
        }
    }

    /** Orbit came back on screen: widgets the last drawing left behind are drawn again. */
    suspend fun cameBack() {
        if (behind.get()) {
            drawn()
        }
    }

    private suspend fun drawn(): Boolean = draw().also { behind.set(!it) }
}

/** The private maps as the widgets are drawn, and whether a map became private since they were last drawn. */
private class WidgetDrawing(val private: Set<String>, val madePrivate: Boolean)

/**
 * When the widgets are drawn again as `maps` change, the maps as they're
 * first read left out: once a change has settled for `settle`, so typing
 * doesn't draw them again on every letter, and at once when a map becomes
 * private, so its widget shows its lock right away rather than its name and
 * picture a moment longer.
 */
@OptIn(FlowPreview::class)
fun widgetRedraws(maps: Flow<List<MapSummary>?>, settle: Duration): Flow<Unit> =
    maps.filterNotNull()
        .distinctUntilChanged()
        .runningFold<List<MapSummary>, WidgetDrawing?>(null) { last, now ->
            val private = now.filter { it.metadata.isPrivate }.map { it.id }.toSet()
            WidgetDrawing(private, madePrivate = last != null && !last.private.containsAll(private))
        }
        .filterNotNull()
        .drop(1)
        .debounce { if (it.madePrivate) Duration.ZERO else settle }
        .map {}

/** The widget for `map`, as the launcher shows it while asking where it goes. */
suspend fun mapWidgetPreview(context: Context, map: MapSummary): GlanceAppWidget {
    val dynamicColor = context.container.settings.settings.first().dynamicColor
    val picture = if (map.metadata.isPrivate) null else picture(context, map, homeScreenIsDark(context), dynamicColor)
    return MapWidgetPreview(map, picture, dynamicColor)
}

/** The size the launcher gives a new widget on a phone, three cells by two: the layout its preview shows. */
val MapWidgetPreviewSize = TwoRows

internal suspend fun loadedMaps(context: Context): List<MapSummary> {
    val maps = context.container.maps

    if (maps.maps.value == null) {
        maps.load()
    }

    return maps.maps.value.orEmpty()
}

/**
 * The map a widget shows: its own, or without one, the pinned map, or the
 * one opened last. A private map is shown only by a widget that chose it,
 * and then locked (see [LockedWidget]).
 */
internal fun shownMap(maps: List<MapSummary>, chosen: String?): MapSummary? {
    maps.firstOrNull { it.id == chosen }?.let { return it }
    val open = maps.filterNot { it.metadata.isPrivate }
    return open.filter { it.metadata.pinned }.maxByOrNull { it.metadata.opened } ?: open.maxByOrNull { it.metadata.opened }
}

/**
 * Orbit's colors for a widget, light and dark, as the app has them: Material
 * You's from the wallpaper while Settings has the wallpaper's colors on, and
 * Orbit's own otherwise. The launcher picks light or dark as the home screen
 * is; its frame is a Home card's.
 */
internal fun widgetColors(context: Context, dynamicColor: Boolean): ColorProviders {
    val day = orbitColorScheme(context, dark = false, dynamicColor)
    val night = orbitColorScheme(context, dark = true, dynamicColor)

    fun both(role: ColorScheme.() -> Color) = DayAndNight(day = day.role(), night = night.role())

    return colorProviders(
        primary = both { primary },
        onPrimary = both { onPrimary },
        primaryContainer = both { primaryContainer },
        onPrimaryContainer = both { onPrimaryContainer },
        secondary = both { secondary },
        onSecondary = both { onSecondary },
        secondaryContainer = both { secondaryContainer },
        onSecondaryContainer = both { onSecondaryContainer },
        tertiary = both { tertiary },
        onTertiary = both { onTertiary },
        tertiaryContainer = both { tertiaryContainer },
        onTertiaryContainer = both { onTertiaryContainer },
        error = both { error },
        errorContainer = both { errorContainer },
        onError = both { onError },
        onErrorContainer = both { onErrorContainer },
        background = both { background },
        onBackground = both { onBackground },
        surface = both { surface },
        onSurface = both { onSurface },
        surfaceVariant = both { surfaceVariant },
        onSurfaceVariant = both { onSurfaceVariant },
        outline = both { outline },
        inverseOnSurface = both { inverseOnSurface },
        inverseSurface = both { inverseSurface },
        inversePrimary = both { inversePrimary },
        widgetBackground = both { surfaceContainerLow },
    )
}

/**
 * The widget's frame in `color`, rounded as Home's cards are: Android 12 and
 * later round it themselves, as the launcher's widgets are, and earlier
 * versions draw it from a rounded shape.
 */
private fun GlanceModifier.frame(color: ColorProvider): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(24.dp).background(color)
    } else {
        background(ImageProvider(R.drawable.widget_frame), colorFilter = ColorFilter.tint(color))
    }

private fun homeScreenIsDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Pictures decoded for widgets, by thumbnail: a widget drawn again has its picture from the first frame. */
private val decodedPictures = LruCache<String, Bitmap>(6)

/** The map's picture if it was decoded before, without waiting. */
private fun decodedPicture(context: Context, map: MapSummary, dark: Boolean, dynamicColor: Boolean): Bitmap? {
    val theme = orbitColorScheme(context, dark, dynamicColor).mapTheme()
    val file = context.container.pictures.cachedThumbnail(map.id, map.pictureKey, map.outline.style, theme) ?: return null
    return decodedPictures[file.path]
}

/** The map's picture, drawn by Orbit in the home screen's colors: the thumbnail the app keeps, at half its size. */
private suspend fun picture(context: Context, map: MapSummary, dark: Boolean, dynamicColor: Boolean): Bitmap? {
    val app = context.container
    val theme = orbitColorScheme(context, dark, dynamicColor).mapTheme()
    val file = app.pictures.thumbnail(map.id, map.pictureKey, map.outline.style, theme) { app.maps.read(map.id) } ?: return null
    decodedPictures[file.path]?.let { return it }

    val decoded = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })
    } ?: return null

    decodedPictures.put(file.path, decoded)
    return decoded
}

private fun openMap(context: Context, mapId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_MAP)
        .putExtra(EXTRA_MAP_ID, mapId)
        .putExtra(EXTRA_OPEN_MARK, OpenMark.of(context))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
internal fun MapWidgetContent(map: MapSummary?, picture: Bitmap?) {
    val context = LocalContext.current
    val size = LocalSize.current
    // A map in its own style keeps its own sky; the others take the home screen's colors, and so do private maps.
    val look = mapStyle(map?.takeUnless { it.metadata.isPrivate }?.outline?.style)
    val ownSky = look.name != null
    val background = if (ownSky) ColorProvider(look.background) else GlanceTheme.colors.widgetBackground
    val text = if (ownSky) ColorProvider(if (look.background.luminance() < 0.5f) Color.White else Color(0xFF1B1F3B)) else GlanceTheme.colors.onSurface
    val quiet = if (ownSky) text else GlanceTheme.colors.onSurfaceVariant

    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .frame(background)
            .then(if (map != null) GlanceModifier.clickable(actionStartActivity(openMap(context, map.id))) else GlanceModifier)
            .padding(widgetPadding(size)),
    ) {
        if (map == null) {
            Text(
                context.getString(R.string.widget_no_maps),
                style = TextStyle(color = quiet, fontSize = 14.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
            )
            return@Box
        }

        // The home screen is anyone's to see: a private map's widget shows its lock, always, with the cover its owner
        // chose if any, and opens to Orbit's.
        if (map.metadata.isPrivate) {
            LockedWidget(map.metadata.cover, text, quiet)
            return@Box
        }

        val add = actionStartActivity(captureIntent(context, map.id))

        when {
            size.width >= TwoRows.width && size.height >= TwoRows.height -> {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            map.title,
                            maxLines = 1,
                            style = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        AddButton(map, add)
                    }

                    Picture(picture, map, GlanceModifier.fillMaxWidth().defaultWeight().padding(vertical = 4.dp), whole = true)
                    Progress(map, quiet)
                }
            }
            size.width >= LongRow.width && size.height >= LongRow.height -> MapRow(map, picture, text, quiet, pictureWidth = 80.dp, add = add)
            size.width >= RoomyCell.width && size.height >= RoomyCell.height -> MapCell(map, picture, text, add)
            size.width >= ShortRow.width && size.height >= ShortRow.height -> MapRow(map, picture, text, quiet, pictureWidth = 56.dp, add = null)
            else -> MapCell(map, picture, text, add = null)
        }
    }
}

/**
 * A private map's widget: its lock and "Private map", or the `cover` its
 * owner chose (its emoji in the lock, its name), where the map's picture and
 * name would be, at every size; a tap opens Orbit, which asks for the screen
 * lock. No +, which would say the map's name.
 */
@Composable
private fun LockedWidget(cover: LockedCover?, text: ColorProvider, quiet: ColorProvider) {
    val context = LocalContext.current
    val size = LocalSize.current
    val name = cover?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.private_map)
    val icon = cover?.icon?.takeIf { it.isNotBlank() }
    val hint = context.getString(R.string.private_widget_hint)

    when {
        size.width >= TwoRows.width && size.height >= TwoRows.height -> {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                WidgetLock(56.dp, null, icon)
                Spacer(GlanceModifier.height(10.dp))
                Text(name, maxLines = 1, style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                Text(hint, maxLines = 1, style = TextStyle(color = quiet, fontSize = 12.sp))
            }
        }
        size.width >= ShortRow.width && size.height < RoomyCell.height -> {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                WidgetLock(44.dp, null, icon)
                Spacer(GlanceModifier.width(12.dp))

                Column(GlanceModifier.defaultWeight()) {
                    Text(name, maxLines = 1, style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                    Text(hint, maxLines = 1, style = TextStyle(color = quiet, fontSize = 12.sp))
                }
            }
        }
        size.width >= RoomyCell.width && size.height >= RoomyCell.height -> {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                WidgetLock(48.dp, null, icon)
                Spacer(GlanceModifier.height(8.dp))
                Text(name, maxLines = 1, style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
        else -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { WidgetLock(36.dp, name, icon) }
    }
}

/**
 * The lock on a private map's widget, round, in the home screen's colors, or its cover's emoji with a small lock
 * beside it. It says what it is only where no name shows beside it.
 */
@Composable
private fun WidgetLock(size: Dp, description: String?, icon: String? = null) {
    Box(
        GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(GlanceTheme.colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_lock),
                contentDescription = description,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(size / 2),
            )
        } else {
            Text(icon, maxLines = 1, style = TextStyle(fontSize = (size.value * 0.5f).sp))

            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_lock),
                    contentDescription = description,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    modifier = GlanceModifier.size(size / 3),
                )
            }
        }
    }
}

/** A row: the picture, the name and progress beside it, and + when there's room for it. */
@Composable
private fun MapRow(map: MapSummary, picture: Bitmap?, text: ColorProvider, quiet: ColorProvider, pictureWidth: Dp, add: Action?) {
    // Large text in a row leaves room for the name and the bar alone. The phone's text size, as it is now: a widget's
    // session can outlast the configuration it started with.
    val words = rowHoldsWords(Resources.getSystem().configuration.fontScale)

    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Picture(picture, map, GlanceModifier.width(pictureWidth).fillMaxHeight(), whole = false)
        Spacer(GlanceModifier.width(12.dp))

        Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            // A progress bar leaves the name a line of its own.
            Text(
                map.title,
                maxLines = if (map.outline.progress.total > 0 || !words) 1 else 2,
                style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(2.dp))
            Progress(map, quiet, words = words)
        }

        if (add != null) {
            Spacer(GlanceModifier.width(8.dp))
            AddButton(map, add)
        }
    }
}

/** A cell: the picture with the name under it, and + in the corner when there's room for it. */
@Composable
private fun MapCell(map: MapSummary, picture: Bitmap?, text: ColorProvider, add: Action?) {
    Column(GlanceModifier.fillMaxSize()) {
        Picture(picture, map, GlanceModifier.fillMaxWidth().defaultWeight(), whole = false)
        Text(map.title, maxLines = 1, style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }

    if (add != null) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            AddButton(map, add)
        }
    }
}

/**
 * The map's picture: `whole` where there's room for all of it, otherwise its
 * middle, where the map is, filling the space without the margins around it.
 */
@Composable
private fun Picture(picture: Bitmap?, map: MapSummary, modifier: GlanceModifier, whole: Boolean) {
    val context = LocalContext.current

    Box(modifier, contentAlignment = Alignment.Center) {
        if (picture != null) {
            Image(
                provider = ImageProvider(picture),
                // The map's name is beside it, in the same tap: screen readers hear it once.
                contentDescription = null,
                contentScale = if (whole) ContentScale.Fit else ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            Image(provider = ImageProvider(R.drawable.ic_shortcut_map), contentDescription = null, modifier = GlanceModifier.size(48.dp))
        }
    }
}

/**
 * How far along the map's to-dos and goals are, or how many ideas it has
 * without any: without room for `words`, the progress bar alone.
 */
@Composable
private fun Progress(map: MapSummary, color: ColorProvider, words: Boolean = true) {
    val context = LocalContext.current
    val progress = map.outline.progress

    if (progress.total > 0) {
        // Goals are reached, and to-dos done: when they're all goals, the words say so.
        val counted = if (progress.goals == progress.total) {
            R.plurals.widget_progress_reached
        } else {
            R.plurals.widget_progress_done
        }

        val said = context.resources.getQuantityString(counted, progress.finished, progress.finished, progress.total)

        if (words) {
            Text(said, maxLines = 1, style = TextStyle(color = color, fontSize = 12.sp))
        }

        Spacer(GlanceModifier.height(4.dp))
        // As the map's rings fill: goals by how far along they are. Without room for the words, screen readers hear them from it.
        ProgressBar(progress.share.toFloat(), description = said.takeUnless { words })
    } else if (words) {
        Text(describe(context.resources, map), maxLines = 1, style = TextStyle(color = color, fontSize = 12.sp))
    }
}

/**
 * A map's progress bar in Orbit's colors. Before Android 12 a widget's bar
 * takes the launcher's colors whatever it's asked, so there it's a picture
 * of the bar, drawn again as the home screen turns light or dark, which says
 * `description` to screen readers, as Android 12 and later's bar says its
 * percentage.
 */
@Composable
private fun ProgressBar(share: Float, description: String?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        LinearProgressIndicator(
            progress = share,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        return
    }

    val context = LocalContext.current
    val fill = GlanceTheme.colors.primary.getColor(context).toArgb()
    val track = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb()

    Image(
        provider = ImageProvider(progressBarPicture(share, fill, track)),
        contentDescription = description,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
    )
}

/** A progress bar's picture, wide enough to be shrunk to any widget's width: `share` of it in `fill`, the rest in `track`. */
private fun progressBarPicture(share: Float, fill: Int, track: Int): Bitmap {
    val width = 1200f
    val height = 12f
    val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = track
    canvas.drawRoundRect(0f, 0f, width, height, height / 2, height / 2, paint)
    paint.color = fill
    canvas.drawRoundRect(0f, 0f, width * share.coerceIn(0f, 1f), height, height / 2, height / 2, paint)
    return bitmap
}

/** The space around a widget's content: less in a small one. */
private fun widgetPadding(size: DpSize): Dp = if (size.width < RoomyCell.width) 8.dp else 12.dp

/**
 * Whether a row widget holds the map's name and its progress's words, a line
 * of 15 and one of 12 sp, and the bar, at the phone's text size: up to
 * Android's Large (1.15). Glance says only which of [WidgetSizes] a widget
 * is drawn for, not how tall it is, and a row of a phone's home screen has
 * no room for more.
 */
internal fun rowHoldsWords(fontScale: Float): Boolean = fontScale <= 1.15f

/** + for an idea in the map, as big as a finger needs: it sits in a widget whose every other tap opens the map. */
@Composable
private fun AddButton(map: MapSummary, add: Action) {
    CircleIconButton(
        imageProvider = ImageProvider(R.drawable.ic_widget_add),
        contentDescription = LocalContext.current.getString(R.string.widget_add_idea, map.title),
        onClick = add,
        backgroundColor = GlanceTheme.colors.primaryContainer,
        contentColor = GlanceTheme.colors.onPrimaryContainer,
        modifier = GlanceModifier.size(48.dp),
    )
}
