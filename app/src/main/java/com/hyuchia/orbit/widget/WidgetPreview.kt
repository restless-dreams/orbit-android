package com.hyuchia.orbit.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.writeAtomically
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/** The file in `noBackupFilesDir` that says which maps the launcher's preview may show ([PreviewRecord]). */
private const val PREVIEW_RECORD = "widget-preview.json"

private const val HOME_SCREEN = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN

/** Offers of the preview, one at a time ([offerWidgetPreview]). */
private val previewOffers = Mutex()

/** The map the preview being drawn shows, none for "No maps yet", once it's drawn ([previewShows]): null before. */
@Volatile
private var previewDrawn: Set<String>? = null

/**
 * The maps the launcher's preview may show: the one it was drawn with, or,
 * when it's not known whether the launcher took the one drawn after it, both;
 * none for "No maps yet". Without the file, or one that can't be read, it's
 * not known: an earlier version's preview, or Orbit stopped while the
 * launcher took one.
 */
@Serializable
private data class PreviewRecord(val maps: Set<String> = emptySet())

private val recordJson = Json { ignoreUnknownKeys = true }

/** The preview being drawn shows the map `map`, or none, for "No maps yet": [offerWidgetPreview] keeps it once the launcher takes it. */
internal fun previewShows(map: String?) {
    previewDrawn = setOfNotNull(map)
}

/**
 * When the launcher's list of widgets is offered a preview ([offerWidgetPreview])
 * as `maps` change: as they're first read, as Orbit starts, and whenever which
 * maps are private changes, however it changed (a map's switch, a `meta.json`
 * that can't be read), so the preview never shows one made private. Nothing
 * else: the launcher takes a few an hour. An offer still drawing as the next
 * comes draws maps that changed since, and can stop (`collectLatest`).
 */
fun widgetPreviewOffers(maps: Flow<List<MapSummary>?>): Flow<Unit> =
    maps.filterNotNull()
        .map { all -> all.filter { it.metadata.isPrivate }.mapTo(HashSet()) { it.id } }
        .distinctUntilChanged()
        .map {}

/**
 * Shows the launcher's list of widgets the map a new widget would show, on
 * Android 15 and later; before, the list shows Orbit's icon. The launcher
 * keeps it across Orbit's restarts, and takes a new one a few times an hour,
 * so it's offered only as [widgetPreviewOffers] says. Whether the launcher
 * took it.
 *
 * It never shows a private map, whatever order things happen in. Offers go
 * one at a time, each drawing the maps as they are once the one before has
 * landed, so a preview drawn before a map was made private never lands after
 * one drawn since. When the launcher won't take a new one, the one it has goes
 * if it may show a map that's private now, as [PreviewRecord] says, or if any
 * map is private when that isn't known, and the list shows Orbit's icon until
 * the next.
 */
suspend fun offerWidgetPreview(context: Context): Boolean = offerWidgetPreview(context, MapWidgetReceiver::class) { loadedMaps(context) }

/**
 * [offerWidgetPreview] for `receiver`'s widget, with the maps as `maps` reads
 * them now, one offer at a time as `offers` lets it, and what it keeps read and
 * written in `files`.
 */
internal suspend fun offerWidgetPreview(
    context: Context,
    receiver: KClass<out GlanceAppWidgetReceiver>,
    offers: Mutex = previewOffers,
    files: CoroutineContext = Dispatchers.IO,
    maps: suspend () -> List<MapSummary>,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return false
    }

    return offers.withLock {
        val record = File(context.noBackupFilesDir, PREVIEW_RECORD)
        val before = withContext(files) { readRecord(record) }
        // While the launcher may be taking a new one, which maps it may show isn't known.
        withContext(files) { record.delete() }
        previewDrawn = null

        val taken = try {
            GlanceAppWidgetManager(context).setWidgetPreviews(receiver, intSetOf(HOME_SCREEN)) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            Logs.warn("Widgets", "Couldn't offer the launcher a preview", failed)
            false
        }

        val drawn = previewDrawn

        if (taken) {
            drawn?.let { withContext(files) { keepRecord(record, it) } }
            return@withLock true
        }

        // The launcher keeps the one it had, or, should it have taken the one drawn after all, that one.
        val mayShow = before?.let { it + drawn.orEmpty() }

        val private = try {
            maps().filter { it.metadata.isPrivate }.mapTo(HashSet()) { it.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unread: Exception) {
            Logs.warn("Widgets", "Couldn't read the maps for the launcher's preview", unread)
            null
        }

        val safe = when {
            mayShow?.isEmpty() == true -> true
            private == null -> false
            mayShow == null -> private.isEmpty()
            else -> mayShow.none { it in private }
        }

        if (safe) {
            mayShow?.let { withContext(files) { keepRecord(record, it) } }
        } else {
            try {
                AppWidgetManager.getInstance(context).removeWidgetPreview(ComponentName(context, receiver.java), HOME_SCREEN)
                withContext(files) { keepRecord(record, emptySet()) }
            } catch (refused: RuntimeException) {
                Logs.warn("Widgets", "Couldn't take the launcher's preview away", refused)
            }
        }

        false
    }
}

private fun readRecord(file: File): Set<String>? = try {
    recordJson.decodeFromString<PreviewRecord>(file.readText()).maps
} catch (missing: IOException) {
    null
} catch (unreadable: SerializationException) {
    null
} catch (unreadable: IllegalArgumentException) {
    null
}

/** Keeps `maps` as what the preview may show; one that can't be kept leaves it not known. */
private fun keepRecord(file: File, maps: Set<String>) {
    try {
        writeAtomically(file, recordJson.encodeToString(PreviewRecord.serializer(), PreviewRecord(maps)))
    } catch (failed: IOException) {
        Logs.warn("Widgets", "Couldn't keep what the launcher's preview shows", failed)
    }
}
