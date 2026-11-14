package com.hyuchia.orbit.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The launcher's preview of the map widget (Android 15 and later), which it keeps across Orbit's restarts and takes anew
 * only a few times an hour: it never shows a private map, whatever order things happen in.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun map(id: String, private: Boolean = false) =
        MapSummary(id, MapOutline(id, branches = 0, ideas = 0), 0, MapMetadata(isPrivate = private), "", "")

    /** One at a time, as the app's offers are, apart from those of the app other tests start. */
    private val offers = Mutex()

    /** Offers the preview of [PreviewReceiver]'s widget, with the maps `maps` gives, its file kept as the test runs. */
    private suspend fun offer(maps: suspend () -> List<MapSummary>): Boolean =
        offerWidgetPreview(context, PreviewReceiver::class, offers, files = EmptyCoroutineContext, maps)

    @Before
    fun startOver() {
        PreviewWidget.reset()
    }

    @After
    fun releaseAll() {
        PreviewWidget.release.complete(Unit)
    }

    @Test
    fun `a preview drawn before a map was made private never lands after the one offered since`() = runTest {
        var maps = listOf(map("trip"), map("garden"))
        PreviewWidget.waits = true

        // As Orbit starts, the preview is drawn from the maps as they are, showing Trip, and its picture takes a while.
        val first = async { offer { maps } }
        advanceUntilIdle()
        assertEquals(1, PreviewWidget.started)

        // Meanwhile Trip is made private, and the preview is offered again, now showing Garden.
        maps = listOf(map("trip", private = true), map("garden"))
        PreviewWidget.shows = "garden"
        val since = async { offer { maps } }
        advanceUntilIdle()

        // Its picture ready, the first lands: the one offered since waited for it, and lands after it.
        PreviewWidget.release.complete(Unit)
        advanceUntilIdle()
        assertTrue(first.await())
        assertTrue(since.await())
        assertEquals("garden", published())
    }

    @Test
    fun `a preview the launcher won't replace goes when the map it shows is private now`() = runTest {
        assertTrue(offer { listOf(map("trip")) })
        assertEquals("trip", published())

        // Orbit stopped before it offered the preview again, or offering it fails: as it starts again with Trip private.
        PreviewWidget.refuses = true
        assertFalse(offer { listOf(map("trip", private = true)) })
        assertNull(preview())
    }

    @Test
    fun `a preview the launcher won't replace stays while the map it shows isn't private`() = runTest {
        assertTrue(offer { listOf(map("trip")) })

        // Another map is private, not the one the preview shows.
        PreviewWidget.refuses = true
        assertFalse(offer { listOf(map("trip"), map("journal", private = true)) })
        assertEquals("trip", published())
    }

    @Test
    fun `one whose map isn't known, as an earlier version's, goes once any map is private`() = runTest {
        assertTrue(offer { listOf(map("trip")) })
        File(context.noBackupFilesDir, "widget-preview.json").delete()
        PreviewWidget.refuses = true

        assertFalse(offer { listOf(map("trip")) })
        assertEquals("trip", published())

        assertFalse(offer { listOf(map("trip"), map("journal", private = true)) })
        assertNull(preview())
    }

    @Test
    fun `the preview is offered as the maps are first read, and again whenever which maps are private changes`() = runTest {
        val maps = MutableStateFlow<List<MapSummary>?>(null)
        var offers = 0
        val offering = launch { widgetPreviewOffers(maps).collect { offers++ } }
        runCurrent()
        assertEquals(0, offers)

        maps.value = listOf(map("journal"), map("garden"))
        runCurrent()
        assertEquals(1, offers)

        // An edit, a map added: the launcher allows a few previews an hour, kept for privacy.
        maps.value = listOf(map("journal"), map("garden"), map("talk"))
        runCurrent()
        assertEquals(1, offers)

        // Made private by its switch, by a meta.json that can't be read, or anything else.
        maps.value = listOf(map("journal", private = true), map("garden"), map("talk"))
        runCurrent()
        assertEquals(2, offers)

        maps.value = listOf(map("journal"), map("garden"), map("talk"))
        runCurrent()
        assertEquals(3, offers)
        offering.cancel()
    }

    private fun preview() =
        AppWidgetManager.getInstance(context).getWidgetPreview(ComponentName(context, PreviewReceiver::class.java), null, HOME_SCREEN)

    /** What the preview the launcher has says. */
    private fun published(): String = requireNotNull(preview()).apply(context, null).text()

    private fun View.text(): String {
        if (this is TextView) {
            return text.toString()
        }

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).text().takeIf { it.isNotEmpty() }?.let { return it }
            }
        }

        return ""
    }

    private companion object {
        const val HOME_SCREEN = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
    }
}

class PreviewReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PreviewWidget()
}

/**
 * A preview showing the map [shows], drawn as the map widget's is. With [waits], the first one drawn waits for [release], as a
 * picture can take a while; with [refuses], the launcher doesn't take it, as it allows a few an hour.
 */
class PreviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Text("") }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        if (refuses) {
            throw IllegalStateException("Rate limited")
        }

        val map = shows
        previewShows(map)

        if (started++ == 0 && waits) {
            release.await()
        }

        provideContent { Text(map) }
    }

    companion object {
        var shows = "trip"
        var started = 0
        var waits = false
        var refuses = false
        var release = CompletableDeferred<Unit>()

        fun reset() {
            shows = "trip"
            started = 0
            waits = false
            refuses = false
            release = CompletableDeferred()
        }
    }
}
