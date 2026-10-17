package com.hyuchia.orbit.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.collection.intSetOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.R
import com.hyuchia.orbit.capture.captureIntent
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.home.describe
import com.hyuchia.orbit.theme.mapStyle
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.theme.orbitColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The map a widget shows, in its Glance state; without one, it shows the pinned map, or the last opened. */
val WidgetMapKey = stringPreferencesKey("map")

// The sizes a widget is drawn for. The launcher shows the one closest to the widget's size among
// those that fit in it, so each layout has sizes near the cells it's meant for.
/** One cell on a phone: the map's picture and its name. */
private val OneCell = DpSize(48.dp, 48.dp)
/** A roomier cell, such as a tablet's: + joins the picture, to catch an idea into the map. */
private val RoomyCell = DpSize(110.dp, 110.dp)
/** A row of two cells: the picture beside the map's name and progress. */
private val ShortRow = DpSize(150.dp, 64.dp)
/** A row of three cells or more: + joins them. */
private val LongRow = DpSize(240.dp, 64.dp)
/** Two rows or more: the name and + above the picture, the progress below. */
private val TwoRows = DpSize(160.dp, 180.dp)
/** Two rows as long as a long row, so a wide widget of two rows isn't taken for a row. */
private val TwoLongRows = DpSize(240.dp, 180.dp)
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

        // The picture is ready before the widget is drawn, so it never opens on a placeholder.
        shownMap(maps, getAppWidgetState<Preferences>(context, id)[WidgetMapKey])?.let {
            picture(context, it, homeScreenIsDark(context), settings.dynamicColor)
        }

        provideContent {
            val chosen = currentState(WidgetMapKey)
            val all by app.maps.maps.collectAsState()
            val map = all?.let { shownMap(it, chosen) }
            val glanceContext = LocalContext.current
            val dark = homeScreenIsDark(glanceContext)

            val picture by produceState(map?.let { decodedPicture(glanceContext, it, dark, settings.dynamicColor) }, map?.id, map?.contentKey, dark) {
                value = map?.let { picture(glanceContext, it, dark, settings.dynamicColor) }
            }

            GlanceTheme {
                MapWidgetContent(map, picture)
            }
        }
    }

    /** What the launcher's list of widgets shows (Android 15 and later): the map a new widget shows, as it shows it. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val map = shownMap(loadedMaps(context), null)
        val picture = map?.let { picture(context, it, homeScreenIsDark(context), context.container.settings.settings.first().dynamicColor) }

        provideContent {
            GlanceTheme {
                MapWidgetContent(map, picture)
            }
        }
    }
}

/** One map's widget with its picture at hand: what the launcher shows while asking where it goes. */
private class MapWidgetPreview(private val map: MapSummary, private val picture: Bitmap?) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                MapWidgetContent(map, picture)
            }
        }
    }
}

class MapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MapWidget()
}

/** Draws every map widget again, after a map changed; nothing to do without any on the home screen. */
suspend fun updateMapWidgets(context: Context) {
    if (GlanceAppWidgetManager(context).getGlanceIds(MapWidget::class.java).isNotEmpty()) {
        MapWidget().updateAll(context)
    }
}

/**
 * Shows the launcher's list of widgets the map a new widget would show, on
 * Android 15 and later; before, the list shows Orbit's icon. The launcher
 * allows this a few times an hour, so it's done as Orbit starts. Whether the
 * launcher took it.
 */
suspend fun offerWidgetPreview(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return false
    }

    val result = GlanceAppWidgetManager(context).setWidgetPreviews(MapWidgetReceiver::class, intSetOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN))
    return result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
}

/** The widget for `map`, as the launcher shows it while asking where it goes. */
suspend fun mapWidgetPreview(context: Context, map: MapSummary): GlanceAppWidget {
    val picture = picture(context, map, homeScreenIsDark(context), context.container.settings.settings.first().dynamicColor)
    return MapWidgetPreview(map, picture)
}

/** The size the launcher gives a new widget on a phone, three cells by two: the layout its preview shows. */
val MapWidgetPreviewSize = TwoRows

private suspend fun loadedMaps(context: Context): List<MapSummary> {
    val maps = context.container.maps

    if (maps.maps.value == null) {
        maps.load()
    }

    return maps.maps.value.orEmpty()
}

/** The map a widget shows: its own, or without one, the pinned map, or the one opened last. */
private fun shownMap(maps: List<MapSummary>, chosen: String?): MapSummary? =
    maps.firstOrNull { it.id == chosen }
        ?: maps.filter { it.metadata.pinned }.maxByOrNull { it.metadata.opened }
        ?: maps.maxByOrNull { it.metadata.opened }

private fun homeScreenIsDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Pictures decoded for widgets, by thumbnail: a widget drawn again has its picture from the first frame. */
private val decodedPictures = LruCache<String, Bitmap>(6)

/** The map's picture if it was decoded before, without waiting. */
private fun decodedPicture(context: Context, map: MapSummary, dark: Boolean, dynamicColor: Boolean): Bitmap? {
    val theme = orbitColorScheme(context, dark, dynamicColor).mapTheme()
    val file = context.container.pictures.cachedThumbnail(map.id, map.contentKey, map.outline.style, theme) ?: return null
    return decodedPictures[file.path]
}

/** The map's picture, drawn by Orbit in the home screen's colors: the thumbnail the app keeps, at half its size. */
private suspend fun picture(context: Context, map: MapSummary, dark: Boolean, dynamicColor: Boolean): Bitmap? {
    val app = context.container
    val theme = orbitColorScheme(context, dark, dynamicColor).mapTheme()
    val file = app.pictures.thumbnail(map.id, map.contentKey, map.outline.style, theme) { app.maps.read(map.id) } ?: return null
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
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun MapWidgetContent(map: MapSummary?, picture: Bitmap?) {
    val context = LocalContext.current
    val size = LocalSize.current
    // A map in its own style keeps its own sky; the others take the home screen's colors.
    val look = mapStyle(map?.outline?.style)
    val ownSky = look.name != null
    val background = if (ownSky) ColorProvider(look.background) else GlanceTheme.colors.widgetBackground
    val text = if (ownSky) ColorProvider(if (look.background.luminance() < 0.5f) Color.White else Color(0xFF1B1F3B)) else GlanceTheme.colors.onSurface
    val quiet = if (ownSky) text else GlanceTheme.colors.onSurfaceVariant

    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(background)
            .then(if (map != null) GlanceModifier.clickable(actionStartActivity(openMap(context, map.id))) else GlanceModifier)
            .padding(if (size.width < RoomyCell.width) 8.dp else 12.dp),
    ) {
        if (map == null) {
            Text(
                context.getString(R.string.widget_no_maps),
                style = TextStyle(color = quiet, fontSize = 14.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
            )
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
                        AddButton(map, add, small = true)
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

/** A row: the picture, the name and progress beside it, and + when there's room for it. */
@Composable
private fun MapRow(map: MapSummary, picture: Bitmap?, text: ColorProvider, quiet: ColorProvider, pictureWidth: Dp, add: Action?) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Picture(picture, map, GlanceModifier.width(pictureWidth).fillMaxHeight(), whole = false)
        Spacer(GlanceModifier.width(12.dp))

        Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            // A progress bar leaves the name a line of its own.
            Text(
                map.title,
                maxLines = if (map.outline.progress.total > 0) 1 else 2,
                style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(2.dp))
            Progress(map, quiet)
        }

        if (add != null) {
            Spacer(GlanceModifier.width(8.dp))
            AddButton(map, add, small = false)
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
            AddButton(map, add, small = true)
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
                contentDescription = context.getString(R.string.widget_map_picture, map.title),
                contentScale = if (whole) ContentScale.Fit else ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            Image(provider = ImageProvider(R.drawable.ic_shortcut_map), contentDescription = null, modifier = GlanceModifier.size(48.dp))
        }
    }
}

/** How far along the map's to-dos and goals are, or how many ideas it has without any. */
@Composable
private fun Progress(map: MapSummary, color: ColorProvider) {
    val context = LocalContext.current
    val progress = map.outline.progress

    if (progress.total > 0) {
        // Goals are reached, and to-dos done: when they're all goals, the words say so.
        val words = if (progress.goals == progress.total) {
            R.plurals.widget_progress_reached
        } else {
            R.plurals.widget_progress_done
        }

        Text(
            context.resources.getQuantityString(words, progress.finished, progress.finished, progress.total),
            maxLines = 1,
            style = TextStyle(color = color, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            // As the map's rings fill: goals by how far along they are.
            progress = progress.share.toFloat(),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    } else {
        Text(describe(context.resources, map), maxLines = 1, style = TextStyle(color = color, fontSize = 12.sp))
    }
}

@Composable
private fun AddButton(map: MapSummary, add: Action, small: Boolean) {
    CircleIconButton(
        imageProvider = ImageProvider(R.drawable.ic_widget_add),
        contentDescription = LocalContext.current.getString(R.string.widget_add_idea, map.title),
        onClick = add,
        backgroundColor = GlanceTheme.colors.primaryContainer,
        contentColor = GlanceTheme.colors.onPrimaryContainer,
        modifier = if (small) GlanceModifier.size(36.dp) else GlanceModifier.size(40.dp),
    )
}
