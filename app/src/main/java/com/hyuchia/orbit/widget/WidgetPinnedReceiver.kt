package com.hyuchia.orbit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.launch

/** The map a widget put on the home screen from a map's menu shows. */
private const val EXTRA_WIDGET_MAP = "widget_map"

/** Whether the launcher can put a widget on the home screen when asked, rather than only from its own list of widgets. */
fun canPinMapWidget(context: Context): Boolean =
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/**
 * Asks the launcher to put a widget showing `map` on the home screen. The
 * launcher shows the widget as it will look and lets the person decide; once
 * it's placed, [WidgetPinnedReceiver] tells it which map to show.
 */
suspend fun pinMapWidget(context: Context, map: MapSummary): Boolean {
    val placed = Intent(context, WidgetPinnedReceiver::class.java).putExtra(EXTRA_WIDGET_MAP, map.id)
    // Mutable, so the launcher can add the new widget's id to it.
    val callback = PendingIntent.getBroadcast(
        context,
        map.id.hashCode(),
        placed,
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
        receiver = MapWidgetReceiver::class.java,
        preview = mapWidgetPreview(context, map),
        previewSize = MapWidgetPreviewSize,
        successCallback = callback,
    )
}

/** Hears from the launcher once a widget asked for by [pinMapWidget] is on the home screen, and gives it its map. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val mapId = intent.getStringExtra(EXTRA_WIDGET_MAP)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || mapId == null) {
            return
        }

        val pending = goAsync()

        context.container.applicationScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { it[WidgetMapKey] = mapId }
                MapWidget().update(context, glanceId)
            } finally {
                pending.finish()
            }
        }
    }
}
