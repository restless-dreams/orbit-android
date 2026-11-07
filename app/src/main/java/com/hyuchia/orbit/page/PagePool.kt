package com.hyuchia.orbit.page

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How long after a map opens the pool makes the next page, once the map has settled. */
private const val REWARM_DELAY = 2_000L

/**
 * Keeps one Orbit page ready, so a map draws the moment it opens instead of
 * waiting for a WebView to start. The spare page also does the work no screen
 * shows: thumbnails, pictures to export and conversions.
 */
class PagePool(context: Context, private val mapsDirectory: File) {
    private val application = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var spare: OrbitPage? = null
    private val rewarm = Runnable { warm() }

    /** The spare page let go of while it was busy ([release]): it goes once its last job ends, unless Orbit is back first. */
    private var leaving: OrbitPage? = null

    /** Makes the spare page, if there isn't one. Call it when the app is idle. */
    fun warm() {
        // Back in sight: the spare stays, its work done or not.
        leaving = null
        spareReady()
    }

    /** The spare page, made anew when there's none, or the one there can't be used: its process stopped, or it didn't load. */
    private fun spareReady(): OrbitPage {
        spare?.takeIf { it.isAlive }?.let { return it }
        spare?.destroy()

        return OrbitPage(application, mapsDirectory).also { page ->
            spare = page
            // Posted, so the page isn't destroyed in the middle of its last job's last step.
            page.onIdle = { handler.post { leaveWhenDone(page) } }
        }
    }

    /**
     * Lets the spare page go while the app is out of sight: a page and its WebView hold tens
     * of megabytes that Android needs more then. One busy with work no screen shows, as a
     * thumbnail or a widget's picture, goes once its last job ends. The next [warm] makes another.
     */
    fun release() {
        handler.removeCallbacks(rewarm)
        val page = spare ?: return

        if (page.busy) {
            leaving = page
            return
        }

        spare = null
        page.destroy()
    }

    /** Lets `page` go if it was to go once its work was done, and is still the spare with none left. */
    private fun leaveWhenDone(page: OrbitPage) {
        if (leaving !== page || page.busy) {
            return
        }

        leaving = null

        if (spare === page) {
            spare = null
            page.destroy()
        }
    }

    /** A page for a screen to show, never one that can't be used. Give it back with [give] when the screen goes. */
    fun take(): OrbitPage {
        // A screen takes it: it's back in sight.
        leaving = null
        val page = spareReady()
        spare = null
        handler.removeCallbacks(rewarm)
        handler.postDelayed(rewarm, REWARM_DELAY)
        return page
    }

    /** Takes a page back: it becomes the spare if there isn't one, unless it can't be used again. */
    fun give(page: OrbitPage) {
        page.listener = null
        page.detach()

        if (!page.isAlive) {
            page.destroy()
            return
        }

        page.send(MapCommand.Unload)

        if (spare?.isAlive == true) {
            page.destroy()
        } else {
            spare = page
        }
    }

    /** A page for an audience to watch on another screen, made in that screen's context so it's sized for it. */
    fun audiencePage(screen: Context): OrbitPage = OrbitPage(screen, mapsDirectory, AUDIENCE_URL)

    /** Runs work on the spare page, making one if needed. */
    suspend fun <T> work(job: suspend (OrbitPage) -> T): T = withContext(Dispatchers.Main) {
        job(spareReady())
    }
}
